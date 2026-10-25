package com.imadlabs.icall.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imadlabs.icall.BuildConfig
import com.imadlabs.icall.R
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.theme.CyberMint
import com.imadlabs.icall.ui.theme.TELEGRAM_URL

/** Opens the ImadLabs Telegram channel (Telegram app if installed, otherwise the browser). */
fun openTelegram(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "ImadLabs" wordmark: "Imad" in the text colour, "Labs" in Cyber Mint (brand guidelines §2). */
@Composable
fun ImadLabsWordmark(fontSize: Int = 18) {
    Text(
        buildAnnotatedString {
            append("Imad")
            withStyle(SpanStyle(color = CyberMint)) { append("Labs") }
        },
        fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val linphone = if (SipManager.isReady) SipManager.core.version else "?"
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("About") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.icall_mark), contentDescription = null, modifier = Modifier.size(120.dp))
            Spacer(Modifier.height(12.dp))
            Text("iCall", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("SIP softphone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("by ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Image(painterResource(R.drawable.imadlabs_symbol), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                ImadLabsWordmark()
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Follow ImadLabs on Telegram for updates, new releases and support.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Join t.me/ImadLabs")
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "SIP engine: Linphone SDK $linphone (GPLv3)\n© 2026 ImadLabs",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
