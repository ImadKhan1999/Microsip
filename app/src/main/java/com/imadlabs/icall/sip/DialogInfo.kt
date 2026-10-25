package com.imadlabs.icall.sip

/**
 * Minimal parser for RFC 4235 "dialog-info+xml" NOTIFY bodies, used for BLF (busy lamp field).
 * We only need the aggregate state of the monitored extension, so a tolerant regex
 * parser is enough and keeps us free of XML library quirks across Android versions.
 */
object DialogInfo {

    enum class BlfState { IDLE, RINGING, BUSY, UNKNOWN }

    // "dialog" must be followed by whitespace or '>' so the <dialog-info> root element is not matched.
    private val dialogRe = Regex("<(?:\\w+:)?dialog(?=[\\s>])[^>]*>(.*?)</(?:\\w+:)?dialog>", RegexOption.DOT_MATCHES_ALL)
    private val stateRe = Regex("<(?:\\w+:)?state\\b[^>]*>\\s*([a-zA-Z]+)\\s*</(?:\\w+:)?state>")
    private val dirRe = Regex("direction\\s*=\\s*\"(initiator|recipient)\"")

    fun parse(xml: String?): BlfState {
        if (xml.isNullOrBlank()) return BlfState.UNKNOWN
        if (!xml.contains("dialog-info")) return BlfState.UNKNOWN
        val dialogs = dialogRe.findAll(xml).toList()
        if (dialogs.isEmpty()) return BlfState.IDLE
        var result = BlfState.IDLE
        for (d in dialogs) {
            val state = stateRe.find(d.groupValues[1])?.groupValues?.get(1)?.lowercase() ?: continue
            when (state) {
                "confirmed" -> return BlfState.BUSY
                "early", "proceeding", "trying" -> {
                    // An early dialog where the monitored party is the callee means it is ringing.
                    val opening = d.value.substringBefore('>')
                    val dir = dirRe.find(opening)?.groupValues?.get(1)
                    result = if (dir == "initiator") BlfState.BUSY else BlfState.RINGING
                }
                "terminated" -> {}
            }
        }
        return result
    }
}
