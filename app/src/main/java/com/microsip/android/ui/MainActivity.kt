package com.microsip.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.microsip.android.service.SipService
import com.microsip.android.sip.DeviceContacts
import com.microsip.android.sip.SipManager
import com.microsip.android.ui.theme.MicroSipTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_CHAT_PEER = "chat_peer"
    }

    /** Deep-link requests from intents, consumed by the Compose navigation. */
    val pendingDial = mutableStateOf<String?>(null)
    val pendingTab = mutableStateOf<String?>(null)
    val pendingChat = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.READ_CONTACTS] == true) DeviceContacts.load(this)
        SipService.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SipManager.init(this)
        requestRuntimePermissions()
        handleIntent(intent)
        lifecycleScope.launch {
            SipManager.toasts.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
        }
        setContent { MicroSipTheme { AppRoot(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        SipService.sync(this)
        SipManager.refreshRegistrations()
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TAB)?.let { pendingTab.value = it }
        intent.getStringExtra(EXTRA_CHAT_PEER)?.let { pendingChat.value = it }
        val data = intent.data ?: return
        val scheme = data.scheme ?: return
        val target = when (scheme) {
            "tel" -> data.schemeSpecificPart
            "sip", "sips" -> "$scheme:${data.schemeSpecificPart}"
            else -> null
        } ?: return
        if (intent.action == Intent.ACTION_CALL) {
            SipManager.call(target)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        } else {
            pendingDial.value = target
        }
        // Consume, so rotation does not redial.
        setIntent(Intent(this, MainActivity::class.java))
    }
}
