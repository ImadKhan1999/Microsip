package com.microsip.android.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialStringTest {
    @Test fun extensionGetsDomain() = assertEquals("sip:100@pbx.local", DialString.toSipUri("100", "pbx.local"))
    @Test fun separatorsRemoved() = assertEquals("sip:+15551234567@d", DialString.toSipUri("+1 (555) 123-4567", "d"))
    @Test fun prefixApplied() = assertEquals("sip:9555@d", DialString.toSipUri("555", "d", dialPrefix = "9"))
    @Test fun plusReplaced() = assertEquals("sip:0044123@d", DialString.toSipUri("+44123", "d", stripPlus = "00"))
    @Test fun fullUriKept() = assertEquals("sip:bob@x.org", DialString.toSipUri("sip:bob@x.org", "d"))
    @Test fun userAtHost() = assertEquals("sip:alice@example.com", DialString.toSipUri("alice@example.com", "d"))
    @Test fun telUri() = assertEquals("sip:123@d", DialString.toSipUri("tel:123", "d"))
    @Test fun sipWithoutHost() = assertEquals("sip:bob@d", DialString.toSipUri("sip:bob", "d"))
    @Test fun emptyIsNull() = assertNull(DialString.toSipUri("  ", "d"))
    @Test fun usernameNotPrefixed() = assertEquals("sip:alice@d", DialString.toSipUri("alice", "d", dialPrefix = "9"))
    @Test fun userPart() = assertEquals("100", DialString.userPart("sip:100@pbx;transport=tls"))
}
