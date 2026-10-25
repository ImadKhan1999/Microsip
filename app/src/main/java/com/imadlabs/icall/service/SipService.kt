package com.imadlabs.icall.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.imadlabs.icall.sip.Prefs
import com.imadlabs.icall.sip.SipManager

/**
 * Foreground service that keeps the process (and therefore the SIP registration) alive,
 * and holds the microphone during calls.
 *
 * Idle: type specialUse (Android 14+). In call: phoneCall|microphone.
 */
class SipService : Service() {

    companion object {
        private const val TAG = "SipService"
        @Volatile var running = false
            private set

        private fun shouldRun(): Boolean =
            SipManager.calls.isNotEmpty() || (Prefs.runInBackground && SipManager.accounts.isNotEmpty())

        /** Start or stop the service to match settings and accounts. */
        fun sync(ctx: Context) {
            if (shouldRun()) start(ctx) else stop(ctx)
        }

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SipService::class.java))
            } catch (t: Throwable) {
                // Android 12+ refuses to start foreground services from the background in some states.
                Log.w(TAG, "Could not start service", t)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SipService::class.java))
        }

        /** Updates the sticky notification text (registration state changed). */
        fun refresh(ctx: Context) {
            if (!running) return
            try {
                NotificationManagerCompat.from(ctx).notify(
                    Notifications.ID_SERVICE,
                    Notifications.serviceNotification(ctx, SipManager.calls.isNotEmpty()),
                )
            } catch (_: SecurityException) {}
        }

        fun onCallActive(ctx: Context) = start(ctx)

        fun onCallsEnded(ctx: Context) = sync(ctx)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SipManager.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inCall = SipManager.calls.isNotEmpty()
        val notification = Notifications.serviceNotification(this, inCall)
        if (Build.VERSION.SDK_INT >= 34) {
            val types = if (inCall) listOf(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            ) else listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            var ok = false
            for (t in types) {
                try {
                    ServiceCompat.startForeground(this, Notifications.ID_SERVICE, notification, t)
                    ok = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "startForeground type=$t refused: ${e.message}")
                }
            }
            if (!ok) { stopSelf(); return START_NOT_STICKY }
        } else {
            startForeground(Notifications.ID_SERVICE, notification)
        }
        running = true
        if (!inCall && !shouldRun()) stopSelf()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!Prefs.runInBackground && SipManager.calls.isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
