package com.imadlabs.icall.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.imadlabs.icall.R
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.CallActivity
import com.imadlabs.icall.ui.MainActivity

object Notifications {
    const val CH_SERVICE = "service"
    const val CH_INCOMING = "incoming_call"
    const val CH_ONGOING = "ongoing_call"
    const val CH_MISSED = "missed_call"
    const val CH_MESSAGES = "messages"

    const val ID_SERVICE = 1
    const val ID_INCOMING = 2
    const val ID_MISSED = 3
    private const val ID_MESSAGE_BASE = 1000

    const val KEY_REPLY = "reply_text"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CH_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps your SIP account registered"
                setShowBadge(false)
            },
            NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                // The SIP engine plays the ringtone itself, so the channel stays silent.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannels(channels)
    }

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun mainIntent(ctx: Context, extra: (Intent) -> Unit = {}): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        extra(i)
        return PendingIntent.getActivity(ctx, i.hashCode(), i, flags())
    }

    private fun callIntent(ctx: Context, action: String?): PendingIntent {
        val i = Intent(ctx, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action != null) i.action = action
        return PendingIntent.getActivity(ctx, action?.hashCode() ?: 0, i, flags())
    }

    private fun broadcast(ctx: Context, action: String, extra: (Intent) -> Unit = {}): PendingIntent {
        val i = Intent(ctx, CallActionReceiver::class.java).setAction(action)
        extra(i)
        val mutable = if (action == CallActionReceiver.ACTION_REPLY && Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else flags()
        return PendingIntent.getBroadcast(ctx, (action + i.dataString + i.extras?.toString()).hashCode(), i, mutable)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: android.app.Notification, tag: String? = null) {
        try {
            NotificationManagerCompat.from(ctx).notify(tag, id, n)
        } catch (_: SecurityException) {
            // Notifications permission not granted; nothing else to do.
        }
    }

    fun serviceNotification(ctx: Context, inCall: Boolean): android.app.Notification {
        val title: String
        val text: String
        if (inCall) {
            val call = SipManager.currentCall
            title = "Call in progress"
            text = SipManager.displayNameFor(call?.remoteAddress)
        } else {
            val acc = if (SipManager.isReady) SipManager.core.defaultAccount else null
            title = acc?.let { SipManager.accountLabel(it) } ?: "iCall"
            text = acc?.let { SipManager.registrationText(it) } ?: "No account configured"
        }
        return NotificationCompat.Builder(ctx, if (inCall) CH_ONGOING else CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(if (inCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(if (inCall) callIntent(ctx, null) else mainIntent(ctx))
            .apply {
                if (inCall) addAction(0, "Hang up", broadcast(ctx, CallActionReceiver.ACTION_HANGUP))
            }
            .build()
    }

    fun showIncomingCall(ctx: Context, name: String, uri: String) {
        val person = Person.Builder().setName(name).setUri(uri).setImportant(true).build()
        val answer = callIntent(ctx, CallActivity.ACTION_ANSWER)
        val decline = broadcast(ctx, CallActionReceiver.ACTION_DECLINE)
        val n = NotificationCompat.Builder(ctx, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(name)
            .setContentText("Incoming call · $uri")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(callIntent(ctx, null), true)
            .setContentIntent(callIntent(ctx, null))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
        post(ctx, ID_INCOMING, n)
    }

    fun cancelIncoming(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_INCOMING)

    fun showMissedCall(ctx: Context, name: String, count: Int) {
        val n = NotificationCompat.Builder(ctx, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(if (count > 1) "$count missed calls" else "Missed call")
            .setContentText(name)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(mainIntent(ctx) { it.putExtra(MainActivity.EXTRA_TAB, "history") })
            .build()
        post(ctx, ID_MISSED, n)
    }

    fun cancelMissedCalls(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_MISSED)

    fun showMessage(ctx: Context, peer: String, name: String, text: String) {
        val reply = NotificationCompat.Action.Builder(
            0, "Reply",
            broadcast(ctx, CallActionReceiver.ACTION_REPLY) { it.putExtra(CallActionReceiver.EXTRA_PEER, peer) },
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build()).build()
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(mainIntent(ctx) { it.putExtra(MainActivity.EXTRA_CHAT_PEER, peer) })
            .addAction(reply)
            .build()
        post(ctx, ID_MESSAGE_BASE, n, tag = peer)
    }

    fun cancelMessage(ctx: Context, peer: String) = NotificationManagerCompat.from(ctx).cancel(peer, ID_MESSAGE_BASE)
}
