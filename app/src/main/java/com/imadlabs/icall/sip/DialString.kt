package com.imadlabs.icall.sip

/**
 * Turns what the user typed in the dialpad (or got from a tel:/sip: link) into a SIP URI,
 * the way MicroSIP does: plain numbers/usernames get the account domain appended,
 * an optional dial prefix is applied to numeric input, and visual separators are dropped.
 *
 * Pure Kotlin so it can be unit tested without the SIP engine.
 */
object DialString {

    /** Characters people paste in phone numbers that are not part of the number. */
    private val separators = Regex("[\\s\\-().]")

    fun isNumeric(input: String): Boolean {
        val s = input.replace(separators, "")
        return s.isNotEmpty() && s.all { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /**
     * @param input text typed by the user, e.g. "100", "+1 (555) 123-4567", "alice@example.com", "sip:bob@x".
     * @param domain the account's SIP domain, e.g. "pbx.example.com" (may include ":port").
     * @param dialPrefix prefix added to numeric input (e.g. "9" for an outside line); empty for none.
     * @param stripPlus replace a leading "+" with this value when not null (e.g. "00").
     * @return a full "sip:user@domain" URI, or null when the input is empty.
     */
    fun toSipUri(input: String, domain: String, dialPrefix: String = "", stripPlus: String? = null): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("tel:", ignoreCase = true)) s = s.substring(4)
        if (s.startsWith("sip:", ignoreCase = true) || s.startsWith("sips:", ignoreCase = true)) {
            return if (s.substringAfter(':').contains('@')) s else "$s@$domain".let { if (domain.isEmpty()) s else it }
        }
        if (s.contains('@')) return "sip:$s"
        if (isNumeric(s)) {
            s = s.replace(separators, "")
            if (stripPlus != null && s.startsWith("+")) s = stripPlus + s.substring(1)
            s = dialPrefix + s
        }
        return if (domain.isEmpty()) "sip:$s" else "sip:$s@$domain"
    }

    /** The part of a SIP URI people care about: "sip:100@pbx" -> "100". */
    fun userPart(uri: String): String {
        val noScheme = uri.substringAfter("sip:").substringAfter("sips:")
        return noScheme.substringBefore('@').substringBefore(';')
    }
}
