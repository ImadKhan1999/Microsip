package com.imadlabs.icall.sip

import android.content.Context
import android.content.SharedPreferences

/**
 * App-level settings that are not stored by the Linphone core itself.
 * (Codec order/enablement and accounts are persisted by Linphone in its own config file.)
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("icall", Context.MODE_PRIVATE)
    }

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun setStr(key: String, v: String) = sp.edit().putString(key, v).apply()

    // Call handling (MicroSIP "Settings" dialog equivalents)
    var autoAnswer: Boolean get() = bool("auto_answer", false); set(v) = setBool("auto_answer", v)
    var autoAnswerDelaySec: Int get() = sp.getInt("auto_answer_delay", 0); set(v) = sp.edit().putInt("auto_answer_delay", v).apply()
    var doNotDisturb: Boolean get() = bool("dnd", false); set(v) = setBool("dnd", v)
    var forwardTo: String get() = str("forward_to", ""); set(v) = setStr("forward_to", v)
    var forwardEnabled: Boolean get() = bool("forward_enabled", false); set(v) = setBool("forward_enabled", v)
    var denyIncoming: Boolean get() = bool("deny_incoming", false); set(v) = setBool("deny_incoming", v)
    var autoRecord: Boolean get() = bool("auto_record", false); set(v) = setBool("auto_record", v)
    var callWaiting: Boolean get() = bool("call_waiting", true); set(v) = setBool("call_waiting", v)

    /** "rfc2833", "info" or "both". */
    var dtmfMode: String get() = str("dtmf_mode", "rfc2833"); set(v) = setStr("dtmf_mode", v)

    // Media
    var echoCancellation: Boolean get() = bool("ec", true); set(v) = setBool("ec", v)
    var noiseSuppression: Boolean get() = bool("ns", true); set(v) = setBool("ns", v)
    var adaptiveRate: Boolean get() = bool("adaptive_rate", true); set(v) = setBool("adaptive_rate", v)
    var agc: Boolean get() = bool("agc", false); set(v) = setBool("agc", v)
    var videoEnabled: Boolean get() = bool("video", false); set(v) = setBool("video", v)

    // Network / security
    var ipv6: Boolean get() = bool("ipv6", false); set(v) = setBool("ipv6", v)
    var verifyTls: Boolean get() = bool("verify_tls", false); set(v) = setBool("verify_tls", v)
    var wifiOnly: Boolean get() = bool("wifi_only", false); set(v) = setBool("wifi_only", v)

    // App behaviour
    var runInBackground: Boolean get() = bool("background", true); set(v) = setBool("background", v)
    var startOnBoot: Boolean get() = bool("boot", true); set(v) = setBool("boot", v)
    var debugLogs: Boolean get() = bool("debug_logs", true); set(v) = setBool("debug_logs", v)

    /** "dark" (default), "light" or "system". */
    var themeMode: String get() = str("theme", "dark"); set(v) = setStr("theme", v)

    /** Set when the user ticks "Don't show again" on the first-run welcome popup. */
    var hideWelcome: Boolean get() = bool("hide_welcome", false); set(v) = setBool("hide_welcome", v)

    /** Contacts (by SIP URI) whose extension state is monitored with BLF (dialog event package). */
    var blfUris: Set<String>
        get() = sp.getStringSet("blf_uris", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("blf_uris", v).apply()

    // Per-account extras keyed by the Linphone account idkey.
    fun accountExtra(id: String, key: String, def: String = ""): String = str("acct.$id.$key", def)
    fun setAccountExtra(id: String, key: String, value: String) = setStr("acct.$id.$key", value)
    fun clearAccount(id: String) {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("acct.$id.") }.forEach { e.remove(it) }
        e.apply()
    }
}
