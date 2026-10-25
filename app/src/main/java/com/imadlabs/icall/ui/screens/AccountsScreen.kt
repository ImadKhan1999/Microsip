package com.imadlabs.icall.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.imadlabs.icall.sip.AccountConfig
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.StatusDot
import com.imadlabs.icall.ui.registrationColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val v by SipManager.accountsVersion.collectAsState()
    val accounts = remember(v) { SipManager.accounts }
    val default = remember(v) { if (SipManager.isReady) SipManager.core.defaultAccount else null }
    var deleting by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { onEdit("") }) { Icon(Icons.Filled.Add, "Add account") } },
    ) { pad ->
        if (accounts.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No SIP accounts yet")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onEdit("") }) { Text("Add account") }
                }
            }
        } else LazyColumn(Modifier.padding(pad)) {
            items(accounts) { a ->
                val id = SipManager.accountId(a)
                ListItem(
                    modifier = Modifier.clickable { onEdit(id) },
                    leadingContent = {
                        RadioButton(selected = a == default, onClick = { SipManager.setDefaultAccount(id) })
                    },
                    headlineContent = { Text(SipManager.accountLabel(a)) },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(registrationColor(a.state)); Text("  " + SipManager.registrationText(a))
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = a.params.isRegisterEnabled, onCheckedChange = { SipManager.setRegisterEnabled(id, it) })
                            IconButton(onClick = { deleting = id }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    },
                )
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete account?") },
            confirmButton = { TextButton(onClick = { SipManager.deleteAccount(id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(id: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val initial = remember(id) { if (id.isBlank()) AccountConfig() else SipManager.loadAccountConfig(id) ?: AccountConfig() }
    var c by remember(id) { mutableStateOf(initial) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(id.isNotBlank()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id.isBlank()) "Add account" else "Edit account") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        val err = SipManager.saveAccount(c, makeDefault = id.isBlank())
                        if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show() else onDone()
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field("Account name (optional)", c.label) { c = c.copy(label = it) }
            Field("SIP server / domain *", c.domain, hint = "sip.example.com or sip.example.com:5060", type = KeyboardType.Uri) { c = c.copy(domain = it) }
            Field("Username *", c.username) { c = c.copy(username = it) }
            OutlinedTextField(
                value = c.password, onValueChange = { c = c.copy(password = it) },
                label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password")
                    }
                },
            )
            Field("Display name", c.displayName) { c = c.copy(displayName = it) }
            Choice("Transport", AccountConfig.Transport.entries.map { it.name }, c.transport.name) {
                c = c.copy(transport = AccountConfig.Transport.valueOf(it))
            }

            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Show advanced") }
            if (advanced) {
                Field("Auth ID / login (if different)", c.authId) { c = c.copy(authId = it) }
                Field("Proxy (host[:port])", c.proxy, hint = "Leave empty to use the domain", type = KeyboardType.Uri) { c = c.copy(proxy = it) }
                Toggle("Use proxy as outbound proxy", c.outboundProxy) { c = c.copy(outboundProxy = it) }
                Toggle("Register", c.register) { c = c.copy(register = it) }
                Field("Registration expiry (seconds)", c.expires.toString(), type = KeyboardType.Number) {
                    c = c.copy(expires = it.filter(Char::isDigit).toIntOrNull() ?: 0)
                }
                Choice("Media encryption", AccountConfig.Encryption.entries.map { it.name }, c.mediaEncryption.name) {
                    c = c.copy(mediaEncryption = AccountConfig.Encryption.valueOf(it))
                }
                Toggle("Encryption mandatory", c.encryptionMandatory) { c = c.copy(encryptionMandatory = it) }
                Toggle("Publish presence", c.publishPresence) { c = c.copy(publishPresence = it) }
                Field("STUN server", c.stunServer, hint = "stun.l.google.com:19302", type = KeyboardType.Uri) { c = c.copy(stunServer = it) }
                Toggle("ICE", c.ice) { c = c.copy(ice = it) }
                Field("Voicemail number", c.voicemailNumber, type = KeyboardType.Phone) { c = c.copy(voicemailNumber = it) }
                Field("Dial prefix", c.dialPrefix, type = KeyboardType.Phone) { c = c.copy(dialPrefix = it) }
                Field("Replace leading + with", c.plusReplacement, hint = "e.g. 00", type = KeyboardType.Phone) { c = c.copy(plusReplacement = it) }
            }
            if (c.transport == AccountConfig.Transport.TLS && c.mediaEncryption == AccountConfig.Encryption.NONE) {
                Text(
                    "Tip: with TLS signalling you usually also want SRTP media encryption.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, hint: String? = null, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit,
) = OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
    placeholder = if (hint != null) { { Text(hint) } } else null, modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(keyboardType = type),
)

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false }) }
        }
    }
}
