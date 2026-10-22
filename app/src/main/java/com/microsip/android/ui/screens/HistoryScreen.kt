package com.microsip.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.microsip.android.sip.SipManager
import com.microsip.android.ui.Avatar
import com.microsip.android.ui.formatDuration
import com.microsip.android.ui.formatWhen
import com.microsip.android.ui.theme.CallGreen
import com.microsip.android.ui.theme.HangupRed
import org.linphone.core.Call
import org.linphone.core.CallLog

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val v by SipManager.historyVersion.collectAsState()
    var missedOnly by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val logs = remember(v, missedOnly) {
        SipManager.callLogs.filter { !missedOnly || isMissed(it) }
    }
    LaunchedEffect(Unit) { SipManager.resetMissedCalls() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = !missedOnly, onClick = { missedOnly = false }, label = { Text("All") })
            FilterChip(selected = missedOnly, onClick = { missedOnly = true }, label = { Text("Missed") })
            Box(Modifier.weight(1f))
            IconButton(onClick = { confirmClear = true }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Filled.DeleteSweep, "Clear history")
            }
        }
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No calls yet") }
        } else LazyColumn {
            items(logs) { log ->
                var menu by remember { mutableStateOf(false) }
                val uri = log.remoteAddress.asStringUriOnly()
                val name = SipManager.displayNameFor(log.remoteAddress)
                Box {
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { SipManager.call(uri)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } },
                            onLongClick = { menu = true },
                        ),
                        leadingContent = { Avatar(name) },
                        headlineContent = {
                            Text(name, color = if (isMissed(log)) HangupRed else MaterialTheme.colorScheme.onSurface)
                        },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DirectionIcon(log)
                                Text(
                                    " " + formatWhen(log.startDate) +
                                        if (log.duration > 0) " · " + formatDuration(log.duration) else "",
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { SipManager.call(uri)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }) {
                                Icon(Icons.Filled.Call, "Call back", tint = CallGreen)
                            }
                        },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Call") }, onClick = { menu = false; SipManager.call(uri) })
                        DropdownMenuItem(text = { Text("Send message") }, onClick = { menu = false; onMessage(uri) })
                        DropdownMenuItem(text = { Text("Add to contacts") }, onClick = {
                            menu = false
                            SipManager.saveFriend(null, name, uri, presence = false, blf = false)
                                ?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                                ?: Toast.makeText(ctx, "Contact saved", Toast.LENGTH_SHORT).show()
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; SipManager.deleteCallLog(log) })
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear call history?") },
        confirmButton = { TextButton(onClick = { SipManager.clearCallLogs(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

private fun isMissed(log: CallLog) = log.dir == Call.Dir.Incoming &&
    (log.status == Call.Status.Missed || log.status == Call.Status.Aborted || log.status == Call.Status.EarlyAborted)

@Composable
private fun DirectionIcon(log: CallLog) {
    val (icon, tint) = when {
        isMissed(log) -> Icons.AutoMirrored.Filled.CallMissed to HangupRed
        log.status == Call.Status.Declined -> Icons.Filled.CallEnd to HangupRed
        log.dir == Call.Dir.Incoming -> Icons.AutoMirrored.Filled.CallReceived to CallGreen
        else -> Icons.AutoMirrored.Filled.CallMade to CallGreen
    }
    Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 2.dp))
}
