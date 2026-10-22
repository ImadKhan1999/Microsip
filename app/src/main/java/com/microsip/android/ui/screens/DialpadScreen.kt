package com.microsip.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsip.android.sip.Prefs
import com.microsip.android.sip.SipManager
import com.microsip.android.ui.theme.CallGreen

private val keys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/**
 * A 12-key pad. Used by the dialpad and, for DTMF, by the call screen.
 * Long-press 0 types '+', long-press 1 calls voicemail (when [onLongOne] is given).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyPad(onKey: (Char) -> Unit, onLongOne: (() -> Unit)? = null, dark: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { (digit, letters) ->
                    Surface(
                        shape = CircleShape,
                        color = if (dark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(72.dp).clip(CircleShape).combinedClickable(
                            onClick = { onKey(digit[0]) },
                            onLongClick = {
                                when (digit) {
                                    "0" -> onKey('+')
                                    "1" -> onLongOne?.invoke() ?: onKey('1')
                                    else -> onKey(digit[0])
                                }
                            },
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(digit, fontSize = 28.sp, color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface)
                            if (letters.isNotEmpty()) Text(
                                letters, fontSize = 10.sp, letterSpacing = 1.sp,
                                color = if (dark) Color.White.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(prefill: String?, onPrefillConsumed: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefill) {
        if (prefill != null) { number = prefill; onPrefillConsumed() }
    }

    fun place(video: Boolean) {
        if (number.isBlank()) {
            // Redial, like pressing Call on an empty MicroSIP dialpad.
            val last = if (SipManager.isReady) SipManager.core.lastOutgoingCallLog?.remoteAddress?.asStringUriOnly() else null
            if (last != null) number = last
            return
        }
        val err = SipManager.call(number, video)
        if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show() else number = ""
    }

    fun voicemail() {
        val acc = if (SipManager.isReady) SipManager.core.defaultAccount else null
        val vm = acc?.let { Prefs.accountExtra(SipManager.accountId(it), "voicemail") }.orEmpty()
        if (vm.isBlank()) Toast.makeText(ctx, "Set a voicemail number in the account settings", Toast.LENGTH_LONG).show()
        else SipManager.call(vm)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TextField(
            value = number,
            onValueChange = { number = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Number or SIP address", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Light),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                if (number.isNotEmpty()) Box(
                    Modifier.combinedClickable(onClick = { number = number.dropLast(1) }, onLongClick = { number = "" }).padding(8.dp)
                ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") }
            },
        )
        Spacer(Modifier.height(8.dp))
        KeyPad(onKey = { number += it }, onLongOne = { voicemail() })
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = { if (number.isNotBlank()) onMessage(number) }, enabled = number.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Message, "Message")
            }
            FilledIconButton(
                onClick = { place(false) },
                modifier = Modifier.size(72.dp).aspectRatio(1f),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = CallGreen, contentColor = Color.White),
            ) { Icon(Icons.Filled.Call, "Call", Modifier.size(34.dp)) }
            IconButton(onClick = { place(true) }, enabled = Prefs.videoEnabled) {
                Icon(Icons.Filled.Videocam, "Video call")
            }
        }
    }
}
