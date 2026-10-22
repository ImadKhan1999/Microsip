package com.microsip.android.ui.screens

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.core.content.FileProvider
import com.microsip.android.sip.SipManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) {
        SipManager.recordingsDir.listFiles()?.filter { it.length() > 0 }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun uriOf(f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Recordings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        if (files.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recordings. Use the Record button during a call.")
            }
        } else LazyColumn(Modifier.padding(pad)) {
            items(files) { f ->
                ListItem(
                    modifier = Modifier.clickable {
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(f), "audio/wav")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(i) }.onFailure {
                            Toast.makeText(ctx, "No audio player installed", Toast.LENGTH_LONG).show()
                        }
                    },
                    headlineContent = { Text(f.nameWithoutExtension) },
                    supportingContent = { Text(Formatter.formatShortFileSize(ctx, f.length())) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("audio/wav")
                                    .putExtra(Intent.EXTRA_STREAM, uriOf(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                ctx.startActivity(Intent.createChooser(send, "Share recording"))
                            }) { Icon(Icons.Filled.Share, "Share") }
                            IconButton(onClick = { f.delete(); tick++ }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    },
                )
            }
        }
    }
}
