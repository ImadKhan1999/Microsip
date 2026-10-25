package com.imadlabs.icall.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imadlabs.icall.sip.SipManager
import org.linphone.core.PayloadType

/** Codec list like MicroSIP's "Codecs" settings: tick to enable, arrows to set priority. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecsScreen(onBack: () -> Unit) {
    val v by SipManager.accountsVersion.collectAsState()
    val audio = remember(v) { SipManager.audioCodecs }
    val video = remember(v) { SipManager.videoCodecs }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Codecs") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Header("Audio (top = preferred)") }
            itemsIndexed(audio) { i, pt -> CodecRow(pt, i, audio.size, false) }
            item { Header("Video") }
            itemsIndexed(video) { i, pt -> CodecRow(pt, i, video.size, true) }
        }
    }
}

@Composable
private fun Header(t: String) = Text(
    t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(16.dp),
)

@Composable
private fun CodecRow(pt: PayloadType, index: Int, count: Int, video: Boolean) {
    val usable = pt.isUsable
    ListItem(
        leadingContent = { Checkbox(checked = pt.enabled(), onCheckedChange = { SipManager.setCodecEnabled(pt, it) }) },
        headlineContent = { Text(pt.mimeType + if (pt.channels > 1) " stereo" else "") },
        supportingContent = {
            Text("${pt.clockRate / 1000.0} kHz" + if (!usable) " · not usable with current bandwidth" else "")
        },
        trailingContent = {
            Row {
                IconButton(onClick = { SipManager.moveCodec(video, index, -1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                }
                IconButton(onClick = { SipManager.moveCodec(video, index, 1) }, enabled = index < count - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                }
            }
        },
    )
}
