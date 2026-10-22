package com.microsip.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.microsip.android.sip.SipManager

/** Handles notification buttons that do not need to open the UI. */
class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DECLINE = "com.microsip.android.DECLINE"
        const val ACTION_HANGUP = "com.microsip.android.HANGUP"
        const val ACTION_REPLY = "com.microsip.android.REPLY"
        const val EXTRA_PEER = "peer"
    }

    override fun onReceive(context: Context, intent: Intent) {
        SipManager.init(context)
        when (intent.action) {
            ACTION_DECLINE -> SipManager.calls.firstOrNull {
                it.state == org.linphone.core.Call.State.IncomingReceived ||
                    it.state == org.linphone.core.Call.State.IncomingEarlyMedia
            }?.let { SipManager.decline(it) }
            ACTION_HANGUP -> SipManager.currentCall?.let { SipManager.hangup(it) }
            ACTION_REPLY -> {
                val peer = intent.getStringExtra(EXTRA_PEER) ?: return
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString()
                if (!text.isNullOrBlank()) {
                    SipManager.chatRoomFor(peer)?.let {
                        SipManager.sendMessage(it, text)
                        SipManager.markRead(it)
                    }
                }
            }
        }
    }
}
