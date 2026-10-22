package com.microsip.android

import android.app.Application
import com.microsip.android.service.Notifications
import com.microsip.android.service.SipService
import com.microsip.android.sip.DeviceContacts
import com.microsip.android.sip.SipManager

class MicroSipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SipManager.init(this)
        DeviceContacts.load(this)
        SipService.sync(this)
    }
}
