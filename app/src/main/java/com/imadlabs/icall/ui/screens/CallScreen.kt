package com.imadlabs.icall.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.imadlabs.icall.sip.Prefs
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.Avatar
import com.imadlabs.icall.ui.formatDuration
import com.imadlabs.icall.ui.theme.CallGreen
import com.imadlabs.icall.ui.theme.HangupRed
import kotlinx.coroutines.delay
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.MediaEncryption

private val Bg = Color(0xFF070B14) // ImadLabs Deep Void

private fun isIncoming(c: Call) = c.state == Call.State.IncomingReceived || c.state == Call.State.IncomingEarlyMedia

private fun statusText(c: Call): String = when (c.state) {
    Call.State.OutgoingInit, Call.State.OutgoingProgress -> "Calling…"
    Call.State.OutgoingRinging -> "Ringing…"
    Call.State.OutgoingEarlyMedia -> "Ringing (early media)…"
    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> "Incoming call"
    Call.State.Connected, Call.State.StreamsRunning, Call.State.Updating, Call.State.UpdatedByRemote ->
        formatDuration(c.duration)
    Call.State.Pausing, Call.State.Paused -> "On hold · " + formatDuration(c.duration)
    Call.State.PausedByRemote -> "Held by other party"
    Call.State.Resuming -> "Resuming…"
    Call.State.Referred -> "Transferring…"
    Call.State.End, Call.State.Released -> "Call ended"
    Call.State.Error -> "Call failed"
    else -> c.state?.name ?: ""
}

@Composable
fun CallScreen(onFinished: () -> Unit, onAnswer: () -> Unit) {
    val v by SipManager.callsVersion.collectAsState()
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now++ } }

    val calls = remember(v, now) { SipManager.calls }
    val current = remember(v, now) { SipManager.currentCall }

    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) { delay(800); onFinished() }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1930), Bg)))) {
        if (current == null) {
            Text("Call ended", color = Color.White, modifier = Modifier.align(Alignment.Center), fontSize = 22.sp)
            return@Box
        }
        val video = current.currentParams?.isVideoEnabled == true &&
            (current.state == Call.State.StreamsRunning || current.state == Call.State.Connected)
        if (video) VideoViews()
        if (isIncoming(current) && calls.size == 1) IncomingUi(current, onAnswer)
        else ActiveUi(current, calls, onAnswer, video)
    }
}

@Composable
private fun Header(call: Call, compact: Boolean = false) {
    val name = SipManager.displayNameFor(call.remoteAddress)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (!compact) { Avatar(name, 96.dp); Spacer(Modifier.height(16.dp)) }
        Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(call.remoteAddress.asStringUriOnly().removePrefix("sip:"), color = Color.White.copy(.7f), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(statusText(call), color = Color.White.copy(.9f), fontSize = 18.sp)
        MediaInfo(call)
    }
}

/** Codec / encryption line, like MicroSIP's call info. */
@Composable
private fun MediaInfo(call: Call) {
    val p = call.currentParams ?: return
    val pt = p.usedAudioPayloadType ?: return
    val enc = p.mediaEncryption
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        if (enc != null && enc != MediaEncryption.None) {
            Icon(Icons.Filled.Lock, null, tint = CallGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "${pt.mimeType} ${pt.clockRate / 1000}kHz" + (if (enc != null && enc != MediaEncryption.None) " · ${enc.name}" else ""),
            color = Color.White.copy(.6f), fontSize = 12.sp,
        )
    }
    if (enc == MediaEncryption.ZRTP) {
        val sas = call.authenticationToken
        if (!sas.isNullOrBlank()) {
            val verified = call.authenticationTokenVerified
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAS: $sas" + if (verified) " ✓" else "", color = Color.White, fontSize = 14.sp)
                if (!verified) TextButton(onClick = {
                    call.authenticationTokenVerified = true
                    SipManager.callsVersion.value++
                }) { Text("Mark verified") }
            }
        }
    }
}

@Composable
private fun IncomingUi(call: Call, onAnswer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(40.dp))
        Header(call)
        Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Filled.CallEnd, "Decline", HangupRed, 76.dp) { SipManager.decline(call) }
            if (Prefs.videoEnabled && call.remoteParams?.isVideoEnabled == true) {
                RoundButton(Icons.Filled.Videocam, "Video", CallGreen, 76.dp) { SipManager.answer(call, video = true) }
            }
            RoundButton(Icons.Filled.Call, "Answer", CallGreen, 76.dp, onAnswer)
        }
    }
}

@Composable
private fun ActiveUi(call: Call, calls: List<Call>, onAnswer: () -> Unit, video: Boolean) {
    val ctx = LocalContext.current
    var keypad by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf(false) }
    var addCall by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) SipManager.toggleVideo(call)
    }
    val connected = call.state == Call.State.StreamsRunning || call.state == Call.State.Connected ||
        call.state == Call.State.Paused || call.state == Call.State.PausedByRemote
    val waiting = calls.firstOrNull { it != call && isIncoming(it) }
    val others = calls.filter { it != call && !isIncoming(it) }
    val outType = SipManager.currentOutputType(call)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(call, compact = keypad || video)

        waiting?.let { w ->
            CallCard("Waiting: " + SipManager.displayNameFor(w.remoteAddress)) {
                TextButton(onClick = { SipManager.decline(w) }) { Text("Decline", color = HangupRed) }
                TextButton(onClick = onAnswer) { Text("Answer", color = CallGreen) }
            }
        }
        others.forEach { o ->
            CallCard(SipManager.displayNameFor(o.remoteAddress) + " · " + statusText(o)) {
                TextButton(onClick = { SipManager.toggleHold(o) }) { Text("Swap") }
                TextButton(onClick = { SipManager.hangup(o) }) { Text("End", color = HangupRed) }
            }
        }

        Spacer(Modifier.weight(1f))

        if (keypad) {
            Text(dtmf, color = Color.White, fontSize = 26.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            KeyPad(onKey = { dtmf += it; SipManager.sendDtmf(call, it) }, dark = true)
            Spacer(Modifier.height(12.dp))
        } else {
            val rows = buildList {
                add(listOf(
                    Ctl(if (call.microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", call.microphoneMuted) { SipManager.toggleMute(call) },
                    Ctl(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", outType == AudioDevice.Type.Speaker) { SipManager.toggleSpeaker(call) },
                    Ctl(Icons.Filled.Dialpad, "Keypad", false) { keypad = true },
                ))
                add(listOf(
                    Ctl(if (SipManager.isOnHold(call)) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        if (SipManager.isOnHold(call)) "Resume" else "Hold", SipManager.isOnHold(call), connected) { SipManager.toggleHold(call) },
                    Ctl(Icons.Filled.PhoneForwarded, "Transfer", false, connected) { transfer = true },
                    Ctl(Icons.Filled.AddIcCall, "Add call", false, connected) { addCall = true },
                ))
                val third = mutableListOf(
                    Ctl(Icons.Filled.FiberManualRecord, if (call.isRecording) "Stop rec" else "Record", call.isRecording, connected) {
                        SipManager.toggleRecording(call)
                    },
                )
                if (calls.size >= 2) third += Ctl(Icons.Filled.CallMerge, "Merge", false) {
                    SipManager.mergeCalls()?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                }
                if (SipManager.hasBluetooth()) third += Ctl(Icons.Filled.Bluetooth, "Bluetooth", outType == AudioDevice.Type.Bluetooth) {
                    SipManager.routeAudio(call, if (outType == AudioDevice.Type.Bluetooth) AudioDevice.Type.Earpiece else AudioDevice.Type.Bluetooth)
                }
                if (Prefs.videoEnabled && third.size < 3) third += Ctl(
                    if (call.currentParams?.isVideoEnabled == true) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    "Video", call.currentParams?.isVideoEnabled == true, connected,
                ) {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        SipManager.toggleVideo(call)
                    } else camLauncher.launch(Manifest.permission.CAMERA)
                }
                add(third)
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { c -> ControlButton(c) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (keypad) TextButton(onClick = { keypad = false }) { Text("Hide", color = Color.White) }
            RoundButton(Icons.Filled.CallEnd, "Hang up", HangupRed, 72.dp) { SipManager.hangup(call) }
        }
    }

    if (transfer) TransferDialog(call, others, onDismiss = { transfer = false })
    if (addCall) NumberDialog("Add call", "Call") { target ->
        addCall = false
        if (target != null) SipManager.call(target)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }
}

private class Ctl(val icon: ImageVector, val label: String, val active: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
private fun ControlButton(c: Ctl) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        FilledIconButton(
            onClick = c.onClick, enabled = c.enabled, modifier = Modifier.size(60.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (c.active) Color.White else Color.White.copy(alpha = .14f),
                contentColor = if (c.active) Bg else Color.White,
                disabledContainerColor = Color.White.copy(alpha = .06f),
                disabledContentColor = Color.White.copy(alpha = .3f),
            ),
        ) { Icon(c.icon, c.label) }
        Text(c.label, color = Color.White.copy(if (c.enabled) .9f else .4f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, size: Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick, modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
        ) { Icon(icon, label, Modifier.size(size / 2.2f)) }
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CallCard(title: String, actions: @Composable () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = .1f), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

@Composable
private fun TransferDialog(call: Call, others: List<Call>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer call") },
        text = {
            Column {
                OutlinedTextField(target, { target = it }, label = { Text("Blind transfer to") }, singleLine = true)
                if (others.isNotEmpty()) {
                    Text("Or attended transfer to an existing call:", modifier = Modifier.padding(top = 12.dp))
                    others.forEach { o ->
                        TextButton(onClick = {
                            SipManager.attendedTransfer(call, o)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                            onDismiss()
                        }) { Text(SipManager.displayNameFor(o.remoteAddress)) }
                    }
                } else {
                    Text(
                        "For an attended transfer: tap Add call, talk to the new party, then Transfer and pick the held call.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SipManager.blindTransfer(call, target)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                onDismiss()
            }, enabled = target.isNotBlank()) { Text("Transfer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberDialog(title: String, action: String, onResult: (String?) -> Unit) {
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title) },
        text = { OutlinedTextField(target, { target = it }, label = { Text("Number or SIP address") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onResult(target.trim()) }, enabled = target.isNotBlank()) { Text(action) } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Cancel") } },
    )
}

/** Remote video full screen with the local camera preview in a corner. */
@Composable
private fun VideoViews() {
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx -> TextureView(ctx) }, modifier = Modifier.fillMaxSize(), update = { view ->
            if (SipManager.core.nativeVideoWindowId != view) SipManager.core.nativeVideoWindowId = view
        })
        AndroidView(
            factory = { ctx -> TextureView(ctx) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(width = 110.dp, height = 150.dp).clip(RoundedCornerShape(10.dp)),
            update = { view ->
                if (SipManager.core.nativePreviewWindowId != view) SipManager.core.nativePreviewWindowId = view
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            SipManager.core.nativeVideoWindowId = null
            SipManager.core.nativePreviewWindowId = null
        }
    }
}
