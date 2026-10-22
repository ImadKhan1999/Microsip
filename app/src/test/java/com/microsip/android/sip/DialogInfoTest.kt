package com.microsip.android.sip

import com.microsip.android.sip.DialogInfo.BlfState
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogInfoTest {
    private fun doc(body: String) =
        """<?xml version="1.0"?><dialog-info xmlns="urn:ietf:params:xml:ns:dialog-info" version="1" state="full" entity="sip:101@pbx">$body</dialog-info>"""

    @Test fun noDialogsIsIdle() = assertEquals(BlfState.IDLE, DialogInfo.parse(doc("")))
    @Test fun confirmedIsBusy() = assertEquals(BlfState.BUSY, DialogInfo.parse(doc("""<dialog id="a"><state>confirmed</state></dialog>""")))
    @Test fun earlyRecipientIsRinging() = assertEquals(
        BlfState.RINGING, DialogInfo.parse(doc("""<dialog id="a" direction="recipient"><state>early</state></dialog>"""))
    )
    @Test fun earlyInitiatorIsBusy() = assertEquals(
        BlfState.BUSY, DialogInfo.parse(doc("""<dialog id="a" direction="initiator"><state>early</state></dialog>"""))
    )
    @Test fun terminatedIsIdle() = assertEquals(BlfState.IDLE, DialogInfo.parse(doc("""<dialog id="a"><state>terminated</state></dialog>""")))
    @Test fun prefixedNamespace() = assertEquals(
        BlfState.BUSY, DialogInfo.parse("""<d:dialog-info xmlns:d="x"><d:dialog id="1"><d:state>confirmed</d:state></d:dialog></d:dialog-info>""")
    )
    @Test fun garbageIsUnknown() = assertEquals(BlfState.UNKNOWN, DialogInfo.parse("hello"))
}
