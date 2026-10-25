package com.imadlabs.icall.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.imadlabs.icall.R
import com.imadlabs.icall.sip.Prefs
import com.imadlabs.icall.ui.screens.ImadLabsWordmark
import com.imadlabs.icall.ui.screens.openTelegram

/**
 * Welcome popup with the ImadLabs Telegram link. Shown once per app launch until the user ticks
 * "Don't show again" (stored in [Prefs.hideWelcome]).
 */
@Composable
fun WelcomeDialog() {
    val ctx = LocalContext.current
    var visible by rememberSaveable { mutableStateOf(!Prefs.hideWelcome) }
    var dontShow by rememberSaveable { mutableStateOf(false) }
    if (!visible) return
    fun close() {
        if (dontShow) Prefs.hideWelcome = true
        visible = false
    }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { Image(painterResource(R.drawable.icall_mark), null, Modifier.size(64.dp)) },
        title = { Text("Welcome to iCall") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A SIP softphone by ")
                    ImadLabsWordmark(fontSize = 15)
                }
                Spacer(Modifier.height(8.dp))
                Text("Join our Telegram channel t.me/ImadLabs for updates, new releases and support.")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { dontShow = !dontShow },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show this again")
                }
            }
        },
        confirmButton = { Button(onClick = { openTelegram(ctx); close() }) { Text("Join Telegram") } },
        dismissButton = { TextButton(onClick = ::close) { Text("Close") } },
    )
}
