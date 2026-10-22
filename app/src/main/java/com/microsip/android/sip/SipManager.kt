package com.microsip.android.sip

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.microsip.android.service.Notifications
import com.microsip.android.service.SipService
import com.microsip.android.ui.CallActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Event
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.MediaEncryption
import org.linphone.core.PayloadType
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.SubscriptionState
import org.linphone.core.TransportType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Single owner of the Linphone [Core]. Everything SIP goes through here.
 *
 * Threading: the core is created on the main looper and auto-iterates there,
 * so every method in this object must be called on the main thread.
 *
 * UI observes the *Version flows; each bump means "re-read that part of the core".
 */
object SipManager {
    private const val TAG = "SipManager"

    lateinit var core: Core
        private set
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = ::core.isInitialized

    val accountsVersion = MutableStateFlow(0)
    val callsVersion = MutableStateFlow(0)
    val historyVersion = MutableStateFlow(0)
    val chatVersion = MutableStateFlow(0)
    val contactsVersion = MutableStateFlow(0)
    val blfStates = MutableStateFlow<Map<String, DialogInfo.BlfState>>(emptyMap())

    /** One-off user facing messages (registration errors, call failures). */
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Peer URI of the conversation currently on screen, so we don't notify for it. */
    @Volatile var visibleChatPeer: String? = null

    private val blfEvents = mutableMapOf<String, Event>()
    private val autoRecorded = mutableSetOf<String>()

    fun init(context: Context) {
        if (isReady) return
        app = context.applicationContext
        Prefs.init(app)
        Factory.instance().setDebugMode(Prefs.debugLogs, "MicroSIP")
        val configPath = File(app.filesDir, "linphonerc").absolutePath
        core = Factory.instance().createCore(configPath, null, app)
        core.addListener(listener)
        configureCore()
        core.start()
        ensureFriendList()
        Log.i(TAG, "Linphone core ${core.version} started")
    }

    // ---------------------------------------------------------------- configuration

    fun configureCore() {
        val c = core
        c.setUserAgent("MicroSIP-Android", com.microsip.android.BuildConfig.VERSION_NAME)
        c.isNativeRingingEnabled = true
        c.isVibrationOnIncomingCallEnabled = true
        c.isKeepAliveEnabled = true
        c.isIpv6Enabled = Prefs.ipv6
        c.isWifiOnlyEnabled = Prefs.wifiOnly
        c.isEchoCancellationEnabled = Prefs.echoCancellation
        c.isNoiseSuppressionEnabled = Prefs.noiseSuppression
        c.isAdaptiveRateControlEnabled = Prefs.adaptiveRate
        c.isAgcEnabled = Prefs.agc
        c.verifyServerCertificates(Prefs.verifyTls)
        c.verifyServerCn(Prefs.verifyTls)
        c.incTimeout = 60
        c.maxCalls = if (Prefs.callWaiting) 4 else 1
        when (Prefs.dtmfMode) {
            "info" -> { c.useInfoForDtmf = true; c.useRfc2833ForDtmf = false }
            "both" -> { c.useInfoForDtmf = true; c.useRfc2833ForDtmf = true }
            else -> { c.useInfoForDtmf = false; c.useRfc2833ForDtmf = true }
        }
        c.isVideoCaptureEnabled = Prefs.videoEnabled
        c.isVideoDisplayEnabled = Prefs.videoEnabled
        val vp = c.videoActivationPolicy
        vp.automaticallyAccept = Prefs.videoEnabled
        vp.automaticallyInitiate = false
        c.videoActivationPolicy = vp
        c.isFriendListSubscriptionEnabled = true
        // Random local ports for every transport avoids clashes with other SIP apps.
        val t = c.transports
        t.udpPort = -1
        t.tcpPort = -1
        t.tlsPort = -1
        c.setTransports(t)
        applyDefaultAccountEncryption()
    }

    private fun applyDefaultAccountEncryption() {
        val acc = core.defaultAccount ?: return
        val id = acc.params.idkey ?: return
        val enc = encryptionFor(id)
        core.setMediaEncryption(enc)
        core.isMediaEncryptionMandatory = enc != MediaEncryption.None &&
            Prefs.accountExtra(id, "enc_mandatory") == "1"
    }

    private fun encryptionFor(id: String): MediaEncryption = when (Prefs.accountExtra(id, "encryption")) {
        "SRTP" -> MediaEncryption.SRTP
        "ZRTP" -> MediaEncryption.ZRTP
        "DTLS" -> MediaEncryption.DTLS
        else -> MediaEncryption.None
    }

    // ---------------------------------------------------------------- accounts

    val accounts: List<Account> get() = if (isReady) core.accountList.toList() else emptyList()

    fun accountId(a: Account): String = a.params.idkey ?: ""

    fun findAccount(id: String): Account? = accounts.firstOrNull { accountId(it) == id }

    fun loadAccountConfig(id: String): AccountConfig? {
        val a = findAccount(id) ?: return null
        val p = a.params
        val identity = p.identityAddress
        val server = p.serverAddress
        val username = identity?.username ?: ""
        val auth = core.findAuthInfo(null, username, identity?.domain)
        val domain = identity?.let { if (it.port > 0) "${it.domain}:${it.port}" else it.domain } ?: ""
        val proxyHost = server?.let { if (it.port > 0) "${it.domain}:${it.port}" else it.domain } ?: ""
        return AccountConfig(
            id = id,
            label = Prefs.accountExtra(id, "label"),
            domain = domain,
            username = username,
            authId = auth?.userid ?: "",
            password = auth?.password ?: "",
            displayName = identity?.displayName ?: "",
            proxy = if (proxyHost == domain) "" else proxyHost,
            transport = when (server?.transport) {
                TransportType.Tcp -> AccountConfig.Transport.TCP
                TransportType.Tls -> AccountConfig.Transport.TLS
                else -> AccountConfig.Transport.UDP
            },
            register = p.isRegisterEnabled,
            expires = p.expires,
            outboundProxy = p.isOutboundProxyEnabled,
            publishPresence = p.isPublishEnabled,
            stunServer = p.natPolicy?.stunServer ?: "",
            ice = p.natPolicy?.isIceEnabled ?: false,
            mediaEncryption = runCatching {
                AccountConfig.Encryption.valueOf(Prefs.accountExtra(id, "encryption", "NONE"))
            }.getOrDefault(AccountConfig.Encryption.NONE),
            encryptionMandatory = Prefs.accountExtra(id, "enc_mandatory") == "1",
            voicemailNumber = Prefs.accountExtra(id, "voicemail"),
            dialPrefix = Prefs.accountExtra(id, "dial_prefix"),
            plusReplacement = Prefs.accountExtra(id, "plus_replacement"),
        )
    }

    /** Creates or updates an account. Returns an error message, or null on success. */
    fun saveAccount(cfg: AccountConfig, makeDefault: Boolean = false): String? {
        if (!cfg.isValid) return "Domain and username are required"
        val f = Factory.instance()
        val id = cfg.id.ifBlank { UUID.randomUUID().toString() }
        val existing = findAccount(id)

        val identity = f.createAddress("sip:${cfg.username.trim()}@${cfg.domain.trim()}")
            ?: return "Invalid username or domain"
        if (cfg.displayName.isNotBlank()) identity.setDisplayName(cfg.displayName.trim())

        val transport = when (cfg.transport) {
            AccountConfig.Transport.UDP -> TransportType.Udp
            AccountConfig.Transport.TCP -> TransportType.Tcp
            AccountConfig.Transport.TLS -> TransportType.Tls
        }
        val serverHost = cfg.proxy.trim().ifBlank { cfg.domain.trim() }
        val server = f.createAddress("sip:$serverHost") ?: return "Invalid proxy/domain"
        server.setTransport(transport)

        val params = existing?.params?.clone() ?: core.createAccountParams()
        params.setIdentityAddress(identity)
        params.setServerAddress(server)
        params.isRegisterEnabled = cfg.register
        params.expires = cfg.expires.coerceIn(30, 86400)
        params.isOutboundProxyEnabled = cfg.outboundProxy
        params.isPublishEnabled = cfg.publishPresence
        params.idkey = id

        val nat = params.natPolicy ?: core.createNatPolicy()
        nat.stunServer = cfg.stunServer.trim()
        nat.isStunEnabled = cfg.stunServer.isNotBlank()
        nat.isIceEnabled = cfg.ice && cfg.stunServer.isNotBlank()
        params.natPolicy = nat

        if (cfg.voicemailNumber.isNotBlank()) {
            DialString.toSipUri(cfg.voicemailNumber, cfg.domain.trim())?.let { uri ->
                f.createAddress(uri)?.let { params.voicemailAddress = it }
            }
        }

        // Replace credentials: drop whatever was stored for this identity before.
        existing?.findAuthInfo()?.let { core.removeAuthInfo(it) }
        val auth = f.createAuthInfo(
            cfg.username.trim(),
            cfg.authId.trim().ifBlank { null },
            cfg.password,
            null,
            null,
            cfg.host,
        )
        core.addAuthInfo(auth)

        val account: Account
        if (existing != null) {
            existing.setParams(params)
            account = existing
        } else {
            account = core.createAccount(params)
            if (core.addAccount(account) != 0) return "Linphone refused the account"
        }

        Prefs.setAccountExtra(id, "label", cfg.label.trim())
        Prefs.setAccountExtra(id, "encryption", cfg.mediaEncryption.name)
        Prefs.setAccountExtra(id, "enc_mandatory", if (cfg.encryptionMandatory) "1" else "0")
        Prefs.setAccountExtra(id, "voicemail", cfg.voicemailNumber.trim())
        Prefs.setAccountExtra(id, "dial_prefix", cfg.dialPrefix.trim())
        Prefs.setAccountExtra(id, "plus_replacement", cfg.plusReplacement.trim())

        if (makeDefault || core.defaultAccount == null) core.defaultAccount = account
        applyDefaultAccountEncryption()
        core.refreshRegisters()
        accountsVersion.value++
        SipService.sync(app)
        return null
    }

    fun deleteAccount(id: String) {
        val a = findAccount(id) ?: return
        a.findAuthInfo()?.let { core.removeAuthInfo(it) }
        core.removeAccount(a)
        Prefs.clearAccount(id)
        if (core.defaultAccount == null) core.accountList.firstOrNull()?.let { core.defaultAccount = it }
        accountsVersion.value++
        SipService.sync(app)
    }

    fun setDefaultAccount(id: String) {
        findAccount(id)?.let { core.defaultAccount = it }
        applyDefaultAccountEncryption()
        accountsVersion.value++
    }

    fun setRegisterEnabled(id: String, enabled: Boolean) {
        val a = findAccount(id) ?: return
        val p = a.params.clone()
        p.isRegisterEnabled = enabled
        a.setParams(p)
        accountsVersion.value++
    }

    fun refreshRegistrations() {
        if (isReady) core.refreshRegisters()
    }

    fun accountLabel(a: Account): String {
        val id = accountId(a)
        val label = Prefs.accountExtra(id, "label")
        if (label.isNotBlank()) return label
        val addr = a.params.identityAddress
        return addr?.let { "${it.username}@${it.domain}" } ?: "Account"
    }

    /** Short human status for the account, like MicroSIP's status bar. */
    fun registrationText(a: Account): String = when (a.state) {
        RegistrationState.Ok -> "Online"
        RegistrationState.Progress, RegistrationState.Refreshing -> "Connecting…"
        RegistrationState.Failed -> "Failed: " + (a.errorInfo?.phrase ?: a.error?.name ?: "error")
        RegistrationState.Cleared -> "Unregistered"
        else -> if (a.params.isRegisterEnabled) "Not registered" else "Disabled"
    }

    // ---------------------------------------------------------------- calls

    val calls: List<Call> get() = if (isReady) core.calls.toList() else emptyList()

    val currentCall: Call? get() = if (isReady) core.currentCall ?: core.calls.firstOrNull() else null

    private fun dialUri(input: String, account: Account?): String? {
        val id = account?.let { accountId(it) } ?: ""
        val domain = account?.params?.identityAddress?.let { a ->
            if (a.port > 0) "${a.domain}:${a.port}" else a.domain
        } ?: ""
        val plus = Prefs.accountExtra(id, "plus_replacement").ifBlank { null }
        return DialString.toSipUri(input, domain, Prefs.accountExtra(id, "dial_prefix"), plus)
    }

    /** Starts an outgoing call. Returns an error message or null. */
    fun call(input: String, video: Boolean = false, account: Account? = null): String? {
        if (!isReady) return "SIP engine not started"
        val acc = account ?: core.defaultAccount
        if (acc == null && !input.contains('@')) return "Add a SIP account first"
        val uri = dialUri(input, acc) ?: return "Nothing to call"
        val address = core.interpretUrl(uri, false) ?: return "Invalid address: $uri"
        val params = core.createCallParams(null) ?: return "Could not create call"
        acc?.let { params.account = it }
        val enc = acc?.let { encryptionFor(accountId(it)) } ?: MediaEncryption.None
        if (core.isMediaEncryptionSupported(enc)) params.mediaEncryption = enc
        params.isVideoEnabled = video && Prefs.videoEnabled
        params.recordFile = newRecordingPath(address)
        val call = core.inviteAddressWithParams(address, params) ?: return "Call failed to start"
        Log.i(TAG, "Calling ${address.asStringUriOnly()} (call=$call)")
        return null
    }

    fun answer(call: Call, video: Boolean = false) {
        val params = core.createCallParams(call)
        if (params != null) {
            params.recordFile = newRecordingPath(call.remoteAddress)
            params.isVideoEnabled = video && Prefs.videoEnabled && (call.remoteParams?.isVideoEnabled == true)
            call.acceptWithParams(params)
        } else {
            call.accept()
        }
    }

    fun decline(call: Call) {
        if (call.state == Call.State.IncomingReceived || call.state == Call.State.IncomingEarlyMedia) {
            call.decline(Reason.Declined)
        } else {
            call.terminate()
        }
    }

    fun hangup(call: Call) { call.terminate() }

    fun hangupAll() { if (isReady) core.terminateAllCalls() }

    fun toggleHold(call: Call) {
        when (call.state) {
            Call.State.Paused -> {
                // Only one call can be active: hold the others first, like MicroSIP does.
                calls.filter { it != call && it.state == Call.State.StreamsRunning }.forEach { it.pause() }
                call.resume()
            }
            Call.State.StreamsRunning, Call.State.Connected, Call.State.PausedByRemote -> call.pause()
            else -> {}
        }
    }

    fun isOnHold(call: Call) = call.state == Call.State.Paused || call.state == Call.State.Pausing

    fun toggleMute(call: Call) { call.microphoneMuted = !call.microphoneMuted }

    fun sendDtmf(call: Call?, digit: Char) {
        if (call != null) call.sendDtmf(digit)
        if (isReady) core.playDtmf(digit, 150)
    }

    fun blindTransfer(call: Call, target: String): String? {
        val uri = dialUri(target, call.params?.account ?: core.defaultAccount) ?: return "Enter a number"
        val addr = core.interpretUrl(uri, false) ?: return "Invalid address"
        return if (call.transferTo(addr) == 0) null else "Transfer failed"
    }

    /** Attended transfer: connects [call] with [target] (both already established). */
    fun attendedTransfer(call: Call, target: Call): String? =
        if (call.transferToAnother(target) == 0) null else "Transfer failed"

    /** Merge all calls into a local audio conference. */
    fun mergeCalls(): String? {
        if (calls.size < 2) return "Need at least two calls"
        return try {
            val params = core.createConferenceParams(null)
            params.isVideoEnabled = false
            params.isLocalParticipantEnabled = true
            val conf = core.createConferenceWithParams(params) ?: return "Conference not available"
            if (conf.addParticipants(calls.toTypedArray()) == 0) null else "Could not merge calls"
        } catch (t: Throwable) {
            Log.e(TAG, "merge failed", t)
            "Could not merge calls"
        }
    }

    fun toggleRecording(call: Call) {
        if (call.isRecording) call.stopRecording() else call.startRecording()
        callsVersion.value++
    }

    fun toggleVideo(call: Call) {
        val params = core.createCallParams(call) ?: return
        params.isVideoEnabled = !(call.currentParams?.isVideoEnabled ?: false)
        call.update(params)
    }

    // Audio routing ---------------------------------------------------------

    fun audioDevices(): List<AudioDevice> = if (isReady) core.audioDevices.toList() else emptyList()

    fun currentOutputType(call: Call?): AudioDevice.Type? = (call?.outputAudioDevice ?: core.outputAudioDevice)?.type

    fun routeAudio(call: Call?, type: AudioDevice.Type) {
        val device = audioDevices().firstOrNull {
            it.type == type && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        } ?: return
        if (call != null) call.outputAudioDevice = device else core.outputAudioDevice = device
        callsVersion.value++
    }

    fun toggleSpeaker(call: Call?) {
        val onSpeaker = currentOutputType(call) == AudioDevice.Type.Speaker
        routeAudio(call, if (onSpeaker) AudioDevice.Type.Earpiece else AudioDevice.Type.Speaker)
    }

    fun hasBluetooth(): Boolean = audioDevices().any {
        it.type == AudioDevice.Type.Bluetooth && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
    }

    // Recording files ---------------------------------------------------------

    val recordingsDir: File get() = File(app.filesDir, "recordings").apply { mkdirs() }

    private fun newRecordingPath(peer: Address?): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val who = (peer?.username ?: "call").replace(Regex("[^A-Za-z0-9+_-]"), "_")
        return File(recordingsDir, "${stamp}_$who.wav").absolutePath
    }

    // ---------------------------------------------------------------- history

    val callLogs: List<CallLog> get() = if (isReady) core.callLogs.toList().sortedByDescending { it.startDate } else emptyList()

    fun deleteCallLog(log: CallLog) { core.removeCallLog(log); historyVersion.value++ }

    fun clearCallLogs() { core.clearCallLogs(); historyVersion.value++ }

    fun resetMissedCalls() {
        if (!isReady) return
        core.resetMissedCallsCount()
        core.accountList.forEach { it.resetMissedCallsCount() }
        Notifications.cancelMissedCalls(app)
        historyVersion.value++
    }

    // ---------------------------------------------------------------- messaging

    val chatRooms: List<ChatRoom>
        get() = if (isReady) core.chatRooms.toList().sortedByDescending { it.lastUpdateTime } else emptyList()

    fun chatRoomFor(peerInput: String): ChatRoom? {
        val acc = core.defaultAccount
        val uri = if (peerInput.startsWith("sip:")) peerInput else dialUri(peerInput, acc) ?: return null
        val peer = core.interpretUrl(uri, false) ?: return null
        val local = acc?.params?.identityAddress
        return if (local != null) core.getChatRoom(peer, local) else core.getChatRoom(peer)
    }

    fun sendMessage(room: ChatRoom, text: String): Boolean {
        if (text.isBlank()) return false
        val msg = room.createMessageFromUtf8(text.trim())
        msg.addListener(object : ChatMessageListenerStub() {
            override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {
                chatVersion.value++
                if (state == ChatMessage.State.NotDelivered) toasts.tryEmit("Message not delivered")
            }
        })
        msg.send()
        chatVersion.value++
        return true
    }

    fun markRead(room: ChatRoom) {
        room.markAsRead()
        Notifications.cancelMessage(app, room.peerAddress.asStringUriOnly())
        chatVersion.value++
    }

    fun deleteChat(room: ChatRoom) { core.deleteChatRoom(room); chatVersion.value++ }

    fun messageText(m: ChatMessage): String =
        m.utf8Text ?: m.contents.firstOrNull { it.isText }?.utf8Text ?: (if (m.isFileTransfer) "[file]" else "")

    // ---------------------------------------------------------------- contacts & presence

    private fun ensureFriendList(): FriendList {
        core.defaultFriendList?.let { return it }
        val list = core.createFriendList()
        list.displayName = "MicroSIP"
        core.addFriendList(list)
        return list
    }

    val friends: List<Friend>
        get() = if (isReady) (core.defaultFriendList?.friends?.toList() ?: emptyList()).sortedBy { (it.name ?: "").lowercase() }
        else emptyList()

    fun friendUri(f: Friend): String = f.address?.asStringUriOnly() ?: ""

    fun saveFriend(existing: Friend?, name: String, number: String, presence: Boolean, blf: Boolean): String? {
        val uri = dialUri(number, core.defaultAccount) ?: return "Enter a number or SIP address"
        val addr = core.interpretUrl(uri, false) ?: return "Invalid address"
        val list = ensureFriendList()
        val oldUri = existing?.let { friendUri(it) }
        val friend = existing ?: core.createFriend()
        friend.edit()
        friend.setName(name.ifBlank { addr.username ?: uri })
        friend.address?.let { if (existing != null) friend.removeAddress(it) }
        friend.setAddress(addr)
        friend.setSubscribesEnabled(presence)
        friend.done()
        if (existing == null) list.addFriend(friend)
        list.updateSubscriptions()

        val newUri = addr.asStringUriOnly()
        val set = Prefs.blfUris.toMutableSet()
        oldUri?.let { set.remove(it); unsubscribeBlf(it) }
        if (blf) set.add(newUri)
        Prefs.blfUris = set
        if (blf) subscribeBlf(newUri)
        contactsVersion.value++
        return null
    }

    fun deleteFriend(f: Friend) {
        val uri = friendUri(f)
        core.defaultFriendList?.removeFriend(f)
        Prefs.blfUris = Prefs.blfUris - uri
        unsubscribeBlf(uri)
        contactsVersion.value++
    }

    fun findFriend(uri: String): Friend? {
        val a = core.interpretUrl(uri, false) ?: return null
        return core.findFriend(a)
    }

    /** Display name for a remote address: saved contact name, else SIP display name, else user part. */
    fun displayNameFor(addr: Address?): String {
        if (addr == null) return "Unknown"
        if (isReady) core.findFriend(addr)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        DeviceContacts.nameForNumber(addr.username)?.let { return it }
        return addr.displayName?.takeIf { it.isNotBlank() } ?: addr.username ?: addr.asStringUriOnly()
    }

    fun setMyPresence(online: Boolean) {
        core.consolidatedPresence = if (online) org.linphone.core.ConsolidatedPresence.Online
        else org.linphone.core.ConsolidatedPresence.Busy
        contactsVersion.value++
    }

    // BLF (busy lamp field) via SUBSCRIBE Event: dialog ------------------------

    private fun subscribeBlf(uri: String) {
        if (blfEvents.containsKey(uri)) return
        val acc = core.defaultAccount ?: return
        if (acc.state != RegistrationState.Ok) return
        val addr = core.interpretUrl(uri, false) ?: return
        val ev = core.createSubscribe(addr, "dialog", 3600) ?: return
        ev.addCustomHeader("Accept", "application/dialog-info+xml")
        ev.sendSubscribe(null)
        blfEvents[uri] = ev
    }

    private fun unsubscribeBlf(uri: String) {
        blfEvents.remove(uri)?.terminate()
        blfStates.value = blfStates.value - uri
    }

    private fun resubscribeAllBlf() {
        blfEvents.values.forEach { runCatching { it.terminate() } }
        blfEvents.clear()
        Prefs.blfUris.forEach { subscribeBlf(it) }
    }

    // ---------------------------------------------------------------- codecs

    val audioCodecs: List<PayloadType> get() = if (isReady) core.audioPayloadTypes.toList() else emptyList()
    val videoCodecs: List<PayloadType> get() = if (isReady) core.videoPayloadTypes.toList() else emptyList()

    fun setCodecEnabled(pt: PayloadType, enabled: Boolean) { pt.enable(enabled); accountsVersion.value++ }

    fun moveCodec(video: Boolean, index: Int, delta: Int) {
        val list = (if (video) videoCodecs else audioCodecs).toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        val item = list.removeAt(index)
        list.add(to, item)
        if (video) core.setVideoPayloadTypes(list.toTypedArray()) else core.setAudioPayloadTypes(list.toTypedArray())
        accountsVersion.value++
    }

    // ---------------------------------------------------------------- listener

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(core: Core, account: Account, state: RegistrationState?, message: String?) {
            Log.i(TAG, "Registration ${accountLabel(account)}: $state $message")
            accountsVersion.value++
            if (state == RegistrationState.Failed) toasts.tryEmit("${accountLabel(account)}: ${registrationText(account)}")
            if (state == RegistrationState.Ok && account == core.defaultAccount) resubscribeAllBlf()
            SipService.refresh(app)
        }

        override fun onDefaultAccountChanged(core: Core, account: Account?) {
            accountsVersion.value++
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String?) {
            Log.i(TAG, "Call ${call.remoteAddress.asStringUriOnly()} -> $state ($message)")
            when (state) {
                Call.State.IncomingReceived -> onIncoming(call)
                Call.State.OutgoingInit -> {
                    SipService.onCallActive(app)
                    CallActivity.start(app)
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    Notifications.cancelIncoming(app)
                    SipService.onCallActive(app)
                    val key = call.callLog?.callId ?: call.toString()
                    if (state == Call.State.StreamsRunning && Prefs.autoRecord && autoRecorded.add(key) && !call.isRecording) {
                        call.startRecording()
                    }
                }
                Call.State.Error -> {
                    toasts.tryEmit("Call failed: ${call.errorInfo?.phrase ?: message}")
                }
                Call.State.End, Call.State.Released -> {
                    if (core.callsNb == 0) {
                        Notifications.cancelIncoming(app)
                        SipService.onCallsEnded(app)
                    }
                }
                else -> {}
            }
            callsVersion.value++
        }

        override fun onCallLogUpdated(core: Core, callLog: CallLog) {
            historyVersion.value++
            if (callLog.dir == Call.Dir.Incoming && callLog.status == Call.Status.Missed) {
                Notifications.showMissedCall(app, displayNameFor(callLog.remoteAddress), core.missedCallsCount)
            }
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            chatVersion.value++
            val peer = chatRoom.peerAddress.asStringUriOnly()
            if (peer != visibleChatPeer) {
                Notifications.showMessage(app, peer, displayNameFor(chatRoom.peerAddress), messageText(message))
            } else {
                chatRoom.markAsRead()
            }
        }

        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) { chatVersion.value++ }
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) { chatVersion.value++ }
        override fun onChatRoomStateChanged(core: Core, chatRoom: ChatRoom, state: ChatRoom.State?) { chatVersion.value++ }

        override fun onNotifyPresenceReceived(core: Core, linphoneFriend: Friend) { contactsVersion.value++ }

        override fun onNotifyReceived(core: Core, linphoneEvent: Event, notifiedEvent: String?, body: Content?) {
            if (notifiedEvent != "dialog") return
            val uri = blfEvents.entries.firstOrNull { it.value == linphoneEvent }?.key
                ?: linphoneEvent.resource?.asStringUriOnly() ?: return
            val state = DialogInfo.parse(body?.utf8Text ?: body?.stringBuffer)
            blfStates.value = blfStates.value + (uri to state)
        }

        override fun onSubscriptionStateChanged(core: Core, linphoneEvent: Event, state: SubscriptionState?) {
            if (linphoneEvent.name != "dialog") return
            if (state == SubscriptionState.Error || state == SubscriptionState.Terminated) {
                val uri = blfEvents.entries.firstOrNull { it.value == linphoneEvent }?.key ?: return
                blfEvents.remove(uri)
                blfStates.value = blfStates.value + (uri to DialogInfo.BlfState.UNKNOWN)
            }
        }

        override fun onAudioDevicesListUpdated(core: Core) { callsVersion.value++ }
        override fun onAudioDeviceChanged(core: Core, audioDevice: AudioDevice) { callsVersion.value++ }
        override fun onNetworkReachable(core: Core, reachable: Boolean) { accountsVersion.value++ }
    }

    private fun onIncoming(call: Call) {
        val others = core.callsNb > 1
        when {
            Prefs.denyIncoming -> { call.decline(Reason.Declined); return }
            Prefs.doNotDisturb -> { call.decline(Reason.DoNotDisturb); return }
            others && !Prefs.callWaiting -> { call.decline(Reason.Busy); return }
            Prefs.forwardEnabled && Prefs.forwardTo.isNotBlank() -> {
                val uri = dialUri(Prefs.forwardTo, call.params?.account ?: core.defaultAccount)
                val addr = uri?.let { core.interpretUrl(it, false) }
                if (addr != null) { call.redirectTo(addr); return }
            }
        }
        val name = displayNameFor(call.remoteAddress)
        Notifications.showIncomingCall(app, name, call.remoteAddress.asStringUriOnly())
        CallActivity.start(app)
        if (Prefs.autoAnswer && !others) {
            val delay = Prefs.autoAnswerDelaySec.coerceAtLeast(0) * 1000L
            main.postDelayed({
                if (call.state == Call.State.IncomingReceived) answer(call)
            }, delay)
        }
    }
}
