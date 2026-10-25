package com.imadlabs.icall.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imadlabs.icall.sip.SipManager
import org.linphone.core.ChatMessage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(peer: String, onBack: () -> Unit) {
    val room = remember(peer) { SipManager.chatRoomFor(peer) }
    val v by SipManager.chatVersion.collectAsState()
    val messages = remember(v, room) { room?.getHistory(0)?.toList() ?: emptyList() }
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val roomPeer = room?.peerAddress?.asStringUriOnly()

    DisposableEffect(roomPeer) {
        SipManager.visibleChatPeer = roomPeer
        onDispose { SipManager.visibleChatPeer = null }
    }
    LaunchedEffect(messages.size) {
        room?.let { if (it.unreadMessagesCount > 0) SipManager.markRead(it) }
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(SipManager.displayNameFor(room?.peerAddress))
                        Text(roomPeer?.removePrefix("sip:") ?: peer, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = { IconButton(onClick = { SipManager.call(roomPeer ?: peer) }) { Icon(Icons.Filled.Call, "Call") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (room == null) {
                Text("Cannot open a conversation with \"$peer\". Check that an account is configured.", Modifier.padding(16.dp))
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages) { m -> Bubble(m) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4,
                )
                IconButton(onClick = { if (SipManager.sendMessage(room, text)) text = "" }, enabled = text.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val out = m.isOutgoing
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (out) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (out) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (out) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(SipManager.messageText(m))
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.time * 1000)) +
                        if (out) " · " + stateLabel(m.state) else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun stateLabel(s: ChatMessage.State?): String = when (s) {
    ChatMessage.State.InProgress, ChatMessage.State.Idle -> "sending"
    ChatMessage.State.Delivered -> "sent"
    ChatMessage.State.DeliveredToUser -> "delivered"
    ChatMessage.State.Displayed -> "read"
    ChatMessage.State.NotDelivered -> "failed"
    else -> ""
}
