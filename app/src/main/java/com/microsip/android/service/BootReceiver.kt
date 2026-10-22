package com.microsip.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microsip.android.sip.Prefs
import com.microsip.android.sip.SipManager

/** Re-registers accounts after reboot or app update, like MicroSIP's "run at startup". */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        if (!Prefs.startOnBoot) return
        SipManager.init(context)
        SipService.sync(context)
    }
}
