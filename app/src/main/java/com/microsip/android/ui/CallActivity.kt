package com.microsip.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.microsip.android.sip.SipManager
import com.microsip.android.ui.screens.CallScreen
import com.microsip.android.ui.theme.MicroSipTheme
import kotlinx.coroutines.launch
import org.linphone.core.AudioDevice
import org.linphone.core.Call

/** Full-screen call UI. Shown over the lock screen for incoming calls. */
class CallActivity : ComponentActivity() {
    companion object {
        const val ACTION_ANSWER = "com.microsip.android.ANSWER"
        private const val TAG = "CallActivity"

        fun start(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(ctx, CallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (t: Throwable) {
                // Background activity starts are blocked on Android 10+; the full-screen notification covers that case.
                Log.w(TAG, "Cannot open call screen: ${t.message}")
            }
        }
    }

    private var proximityLock: PowerManager.WakeLock? = null
    private var answerAfterPermission = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && answerAfterPermission) answerIncoming()
        answerAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        SipManager.init(this)
        handle(intent)
        lifecycleScope.launch { SipManager.callsVersion.collect { updateProximity() } }
        setContent {
            MicroSipTheme {
                CallScreen(onFinished = { finish() }, onAnswer = { requestAnswer() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            intent.action = null
            requestAnswer()
        }
    }

    private fun requestAnswer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            answerIncoming()
        } else {
            answerAfterPermission = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun answerIncoming() {
        SipManager.calls.firstOrNull {
            it.state == Call.State.IncomingReceived || it.state == Call.State.IncomingEarlyMedia
        }?.let { SipManager.answer(it) }
    }

    /** Turn the screen off when the phone is held to the ear (earpiece route, active call). */
    private fun updateProximity() {
        val call = SipManager.currentCall
        val active = call != null && call.state in setOf(
            Call.State.StreamsRunning, Call.State.Connected, Call.State.OutgoingRinging,
            Call.State.OutgoingProgress, Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia,
        )
        val earpiece = SipManager.currentOutputType(call) == AudioDevice.Type.Earpiece
        val want = active && earpiece && call?.currentParams?.isVideoEnabled != true
        if (want && proximityLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "microsip:proximity")
                    .apply { acquire(4 * 60 * 60 * 1000L) }
            }
        } else if (!want) {
            proximityLock?.let { if (it.isHeld) it.release() }
            proximityLock = null
        }
    }

    override fun onDestroy() {
        proximityLock?.let { if (it.isHeld) it.release() }
        proximityLock = null
        super.onDestroy()
    }
}
