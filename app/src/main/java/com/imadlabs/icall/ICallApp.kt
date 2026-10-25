package com.imadlabs.icall

import android.app.Application
import com.imadlabs.icall.service.Notifications
import com.imadlabs.icall.service.SipService
import com.imadlabs.icall.sip.DeviceContacts
import com.imadlabs.icall.sip.SipManager

class ICallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SipManager.init(this)
        DeviceContacts.load(this)
        SipService.sync(this)
    }
}
