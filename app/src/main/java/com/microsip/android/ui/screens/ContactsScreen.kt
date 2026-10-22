package com.microsip.android.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.microsip.android.sip.DeviceContacts
import com.microsip.android.sip.Prefs
import com.microsip.android.sip.SipManager
import com.microsip.android.ui.Avatar
import com.microsip.android.ui.StatusDot
import com.microsip.android.ui.blfColor
import com.microsip.android.ui.presenceColor
import com.microsip.android.ui.theme.CallGreen
import org.linphone.core.Friend

@Composable
fun ContactsScreen(onMessage: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Friend?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Add contact") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("SIP contacts") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Phone") })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search") },
            )
            if (tab == 0) SipContacts(query, onMessage, onEdit = { editing = it })
            else PhoneContacts(query, onMessage)
        }
    }

    if (creating || editing != null) ContactDialog(
        existing = editing,
        onDismiss = { creating = false; editing = null },
    )
}

@Composable
private fun SipContacts(query: String, onMessage: (String) -> Unit, onEdit: (Friend) -> Unit) {
    val ctx = LocalContext.current
    val v by SipManager.contactsVersion.collectAsState()
    val blf by SipManager.blfStates.collectAsState()
    val friends = remember(v, query) {
        SipManager.friends.filter {
            query.isBlank() || (it.name ?: "").contains(query, true) || SipManager.friendUri(it).contains(query, true)
        }
    }
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No contacts. Tap + to add one.") }
        return
    }
    LazyColumn {
        items(friends) { f ->
            val uri = SipManager.friendUri(f)
            val name = f.name ?: uri
            var menu by remember { mutableStateOf(false) }
            val isBlf = uri in Prefs.blfUris
            ListItem(
                modifier = Modifier.clickable { SipManager.call(uri)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } },
                leadingContent = { Avatar(name) },
                headlineContent = { Text(name) },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            isBlf -> { StatusDot(blfColor(blf[uri])); Text("  " + (blf[uri]?.name?.lowercase() ?: "unknown") + " · ") }
                            f.isSubscribesEnabled -> { StatusDot(presenceColor(f.consolidatedPresence)); Text("  ") }
                        }
                        Text(uri.removePrefix("sip:"))
                    }
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onMessage(uri) }) { Icon(Icons.AutoMirrored.Filled.Message, "Message") }
                        IconButton(onClick = { SipManager.call(uri) }) { Icon(Icons.Filled.Call, "Call", tint = CallGreen) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(f) })
                                DropdownMenuItem(text = { Text("Video call") }, onClick = { menu = false; SipManager.call(uri, video = true) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; SipManager.deleteFriend(f) })
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PhoneContacts(query: String, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val entries by DeviceContacts.entries.collectAsState()
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) DeviceContacts.load(ctx)
    }
    if (!DeviceContacts.hasPermission(ctx)) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Allow access to your phone contacts to call them over SIP.")
            Button(onClick = { asked = true; launcher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.padding(top = 12.dp)) {
                Text(if (asked) "Ask again" else "Allow")
            }
        }
        return
    }
    val list = remember(entries, query) {
        entries.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query) }
    }
    LazyColumn {
        items(list) { e ->
            ListItem(
                modifier = Modifier.clickable { SipManager.call(e.number)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } },
                leadingContent = { Avatar(e.name) },
                headlineContent = { Text(e.name) },
                supportingContent = { Text(e.number) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onMessage(e.number) }) { Icon(Icons.AutoMirrored.Filled.Message, "Message") }
                        IconButton(onClick = { SipManager.call(e.number) }) { Icon(Icons.Filled.Call, "Call", tint = CallGreen) }
                    }
                },
            )
        }
    }
}

@Composable
private fun ContactDialog(existing: Friend?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val existingUri = existing?.let { SipManager.friendUri(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var number by remember { mutableStateOf(existingUri ?: "") }
    var presence by remember { mutableStateOf(existing?.isSubscribesEnabled ?: false) }
    var blf by remember { mutableStateOf(existingUri != null && existingUri in Prefs.blfUris) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New contact" else "Edit contact") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(number, { number = it }, label = { Text("Number or SIP address") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(presence, { presence = it }); Text("Subscribe to presence")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(blf, { blf = it }); Text("Busy lamp field (BLF)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = SipManager.saveFriend(existing, name, number, presence, blf)
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show() else onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
