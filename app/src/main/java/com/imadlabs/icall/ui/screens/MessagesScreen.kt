package com.imadlabs.icall.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.Avatar
import com.imadlabs.icall.ui.formatWhen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(onOpen: (String) -> Unit) {
    val v by SipManager.chatVersion.collectAsState()
    val rooms = remember(v) { SipManager.chatRooms }
    var compose by remember { mutableStateOf(false) }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { compose = true }) { Icon(Icons.Filled.Edit, "New message") }
    }) { pad ->
        if (rooms.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { Text("No conversations") }
        } else LazyColumn(Modifier.padding(pad)) {
            items(rooms) { room ->
                val peer = room.peerAddress
                val name = SipManager.displayNameFor(peer)
                val last = room.lastMessageInHistory
                val unread = room.unreadMessagesCount
                var menu by remember { mutableStateOf(false) }
                Box {
                    ListItem(
                        modifier = Modifier.combinedClickable(onClick = { onOpen(peer.asStringUriOnly()) }, onLongClick = { menu = true }),
                        leadingContent = { Avatar(name) },
                        headlineContent = { Text(name, fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                last?.let { (if (it.isOutgoing) "You: " else "") + SipManager.messageText(it) } ?: "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatWhen(room.lastUpdateTime))
                                if (unread > 0) Badge { Text(unread.toString()) }
                            }
                        },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Call") }, onClick = { menu = false; SipManager.call(peer.asStringUriOnly()) })
                        DropdownMenuItem(text = { Text("Delete conversation") }, onClick = { menu = false; SipManager.deleteChat(room) })
                    }
                }
            }
        }
    }

    if (compose) {
        var to by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { compose = false },
            title = { Text("New message") },
            text = { OutlinedTextField(to, { to = it }, label = { Text("To (number or SIP address)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (to.isNotBlank()) { compose = false; onOpen(to.trim()) } }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { compose = false }) { Text("Cancel") } },
        )
    }
}
