package com.microsip.android.ui.screens

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.microsip.android.BuildConfig
import com.microsip.android.service.SipService
import com.microsip.android.sip.Prefs
import com.microsip.android.sip.SipManager
import java.io.File

@Composable
fun SettingsScreen(onAccounts: () -> Unit, onCodecs: () -> Unit, onRecordings: () -> Unit) {
    val ctx = LocalContext.current
    // Bumped after every change so the switches re-read Prefs.
    var tick by remember { mutableIntStateOf(0) }
    fun changed() {
        tick++
        SipManager.configureCore()
        SipManager.accountsVersion.value++
        SipService.sync(ctx)
    }

    @Composable
    fun Pref(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        Toggle(label, get()) { set(it); changed() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Link("SIP accounts", "Add, edit and choose the default account", onAccounts)
        Link("Codecs", "Enable and order audio/video codecs", onCodecs)
        Link("Recordings", "Play or delete recorded calls", onRecordings)

        Section("Calls")
        Pref("Do not disturb", { Prefs.doNotDisturb }) { Prefs.doNotDisturb = it }
        Pref("Auto answer", { Prefs.autoAnswer }) { Prefs.autoAnswer = it }
        if (Prefs.autoAnswer || tick < 0) {
            var delay by remember { mutableStateOf(Prefs.autoAnswerDelaySec.toString()) }
            OutlinedTextField(delay, {
                delay = it.filter(Char::isDigit); Prefs.autoAnswerDelaySec = delay.toIntOrNull() ?: 0
            }, label = { Text("Auto answer delay (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Pref("Call waiting (allow a second call)", { Prefs.callWaiting }) { Prefs.callWaiting = it }
        Pref("Reject all incoming calls", { Prefs.denyIncoming }) { Prefs.denyIncoming = it }
        Pref("Forward incoming calls", { Prefs.forwardEnabled }) { Prefs.forwardEnabled = it }
        if (Prefs.forwardEnabled) {
            var fwd by remember { mutableStateOf(Prefs.forwardTo) }
            OutlinedTextField(fwd, { fwd = it; Prefs.forwardTo = it.trim() },
                label = { Text("Forward to") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Pref("Record every call automatically", { Prefs.autoRecord }) { Prefs.autoRecord = it }
        Choice("DTMF method", listOf("rfc2833", "info", "both"), Prefs.dtmfMode) { Prefs.dtmfMode = it; changed() }

        Section("Audio & video")
        Pref("Echo cancellation", { Prefs.echoCancellation }) { Prefs.echoCancellation = it }
        Pref("Noise suppression", { Prefs.noiseSuppression }) { Prefs.noiseSuppression = it }
        Pref("Automatic gain control", { Prefs.agc }) { Prefs.agc = it }
        Pref("Adaptive bitrate", { Prefs.adaptiveRate }) { Prefs.adaptiveRate = it }
        Pref("Enable video calls", { Prefs.videoEnabled }) { Prefs.videoEnabled = it }

        Section("Network & security")
        Pref("IPv6", { Prefs.ipv6 }) { Prefs.ipv6 = it }
        Pref("Wi-Fi only", { Prefs.wifiOnly }) { Prefs.wifiOnly = it }
        Pref("Verify TLS server certificates", { Prefs.verifyTls }) { Prefs.verifyTls = it }

        Section("Background")
        Pref("Stay connected in background", { Prefs.runInBackground }) { Prefs.runInBackground = it }
        Pref("Start on boot", { Prefs.startOnBoot }) { Prefs.startOnBoot = it }
        Link("Battery optimisation", "Allow MicroSIP to run unrestricted so calls can ring") { openBatterySettings(ctx) }
        if (Build.VERSION.SDK_INT >= 34) {
            Link("Full-screen incoming calls", "Needed to show the call screen over the lock screen") {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)))
                }
            }
        }
        Link("Notification settings", "Choose ring and message notification behaviour") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }

        Section("Troubleshooting")
        Pref("Detailed SIP logs", { Prefs.debugLogs }) { Prefs.debugLogs = it }
        Link("Share logs", "Send the app log (includes SIP traces) for debugging") { shareLogs(ctx) }
        Link("Re-register now", "Refresh all account registrations") { SipManager.refreshRegistrations() }

        Section("About")
        val linphone = if (SipManager.isReady) SipManager.core.version else "?"
        Text("MicroSIP for Android ${BuildConfig.VERSION_NAME}\nSIP engine: Linphone SDK $linphone",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun Link(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

@SuppressLint("BatteryLife")
private fun openBatterySettings(ctx: Context) {
    val pm = ctx.getSystemService(PowerManager::class.java)
    val intent = if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
    } else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { ctx.startActivity(intent) }
        .onFailure { Toast.makeText(ctx, "Open Settings > Apps > MicroSIP > Battery", Toast.LENGTH_LONG).show() }
}

/** Dumps this process's logcat (Linphone logs go there) to a file and opens the share sheet. */
private fun shareLogs(ctx: Context) {
    Thread {
        val file = File(ctx.cacheDir, "microsip-log.txt")
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=" + android.os.Process.myPid()))
            file.outputStream().use { out -> p.inputStream.copyTo(out) }
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.start()
}

@Suppress("unused")
private fun canUseFullScreen(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
