package com.microsip.android.sip

/**
 * Everything the account editor shows, mirroring MicroSIP's "Account" dialog.
 * Mapped to Linphone AccountParams/AuthInfo/NatPolicy by [SipManager.saveAccount].
 */
data class AccountConfig(
    /** Stable id (Linphone AccountParams.idkey). Empty for a new account. */
    val id: String = "",
    val label: String = "",
    /** SIP server / registrar domain, optionally with ":port". */
    val domain: String = "",
    val username: String = "",
    /** Login used for digest auth when it differs from the username. */
    val authId: String = "",
    val password: String = "",
    val displayName: String = "",
    /** Proxy host[:port]. Empty means "same as domain". */
    val proxy: String = "",
    val transport: Transport = Transport.UDP,
    val register: Boolean = true,
    val expires: Int = 300,
    /** Route all requests through the proxy (outbound proxy). */
    val outboundProxy: Boolean = false,
    val publishPresence: Boolean = false,
    val stunServer: String = "",
    val ice: Boolean = false,
    val mediaEncryption: Encryption = Encryption.NONE,
    val encryptionMandatory: Boolean = false,
    val voicemailNumber: String = "",
    val dialPrefix: String = "",
    /** Replace a leading '+' with this text when dialing (e.g. "00"). Empty keeps the '+'. */
    val plusReplacement: String = "",
) {
    enum class Transport { UDP, TCP, TLS }
    enum class Encryption { NONE, SRTP, ZRTP, DTLS }

    val isValid: Boolean get() = domain.isNotBlank() && username.isNotBlank()

    /** Domain without port, used for auth info matching. */
    val host: String get() = domain.substringBefore(':').trim()
}
