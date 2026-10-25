package com.imadlabs.icall.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imadlabs.icall.sip.Prefs
import com.imadlabs.icall.sip.SipManager
import com.imadlabs.icall.ui.screens.AboutScreen
import com.imadlabs.icall.ui.screens.AccountEditScreen
import com.imadlabs.icall.ui.screens.AccountsScreen
import com.imadlabs.icall.ui.screens.ChatScreen
import com.imadlabs.icall.ui.screens.CodecsScreen
import com.imadlabs.icall.ui.screens.ContactsScreen
import com.imadlabs.icall.ui.screens.DialpadScreen
import com.imadlabs.icall.ui.screens.HistoryScreen
import com.imadlabs.icall.ui.screens.MessagesScreen
import com.imadlabs.icall.ui.screens.RecordingsScreen
import com.imadlabs.icall.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dialpad", "Dialpad", Icons.Filled.Dialpad),
    Tab("history", "Calls", Icons.Filled.History),
    Tab("contacts", "Contacts", Icons.Filled.Contacts),
    Tab("messages", "Messages", Icons.AutoMirrored.Filled.Chat),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

fun NavHostController.openChat(peer: String) = navigate("chat/" + Uri.encode(peer))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(activity: MainActivity) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dialpad"
    val isTab = tabs.any { it.route == route }

    val accountsV by SipManager.accountsVersion.collectAsState()
    val callsV by SipManager.callsVersion.collectAsState()
    val historyV by SipManager.historyVersion.collectAsState()
    val chatV by SipManager.chatVersion.collectAsState()
    val missed = remember(historyV) { if (SipManager.isReady) SipManager.core.missedCallsCount else 0 }
    val unread = remember(chatV) { if (SipManager.isReady) SipManager.core.unreadChatMessageCount else 0 }
    val hasCall = remember(callsV) { SipManager.calls.isNotEmpty() }

    // Deep links from intents / notifications.
    val dial by activity.pendingDial
    val tab by activity.pendingTab
    val chat by activity.pendingChat
    LaunchedEffect(tab) { tab?.let { nav.navigate(it) { launchSingleTop = true }; activity.pendingTab.value = null } }
    LaunchedEffect(chat) { chat?.let { nav.openChat(it); activity.pendingChat.value = null } }
    LaunchedEffect(dial) { if (dial != null) nav.navigate("dialpad") { launchSingleTop = true } }

    WelcomeDialog()

    Scaffold(
        topBar = {
            if (isTab) TopAppBar(title = { AccountSwitcher(accountsV, onManage = { nav.navigate("accounts") }) })
        },
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo("dialpad") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val count = when (t.route) { "history" -> missed; "messages" -> unread; else -> 0 }
                            BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) { Icon(t.icon, t.label) }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (hasCall) {
                val ctx = LocalContext.current
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary)
                        .clickable { CallActivity.start(ctx) }.padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { Text("Call in progress · tap to return", color = MaterialTheme.colorScheme.onPrimary) }
            }
            NavHost(nav, startDestination = "dialpad", modifier = Modifier.weight(1f)) {
                composable("dialpad") {
                    DialpadScreen(prefill = dial, onPrefillConsumed = { activity.pendingDial.value = null }, onMessage = { nav.openChat(it) })
                }
                composable("history") { HistoryScreen(onMessage = { nav.openChat(it) }) }
                composable("contacts") { ContactsScreen(onMessage = { nav.openChat(it) }) }
                composable("messages") { MessagesScreen(onOpen = { nav.openChat(it) }) }
                composable("settings") {
                    SettingsScreen(
                        onAccounts = { nav.navigate("accounts") },
                        onCodecs = { nav.navigate("codecs") },
                        onRecordings = { nav.navigate("recordings") },
                        onAbout = { nav.navigate("about") },
                    )
                }
                composable("accounts") {
                    AccountsScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate("account/" + it.ifBlank { "new" }) })
                }
                composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    val id = it.arguments?.getString("id").orEmpty()
                    AccountEditScreen(id = if (id == "new") "" else id, onDone = { nav.popBackStack() })
                }
                composable("codecs") { CodecsScreen(onBack = { nav.popBackStack() }) }
                composable("recordings") { RecordingsScreen(onBack = { nav.popBackStack() }) }
                composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                composable("chat/{peer}", arguments = listOf(navArgument("peer") { type = NavType.StringType })) {
                    val peer = Uri.decode(it.arguments?.getString("peer").orEmpty())
                    ChatScreen(peer = peer, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Top bar: current account with registration dot; tap to switch account (like MicroSIP's account menu). */
@Composable
private fun AccountSwitcher(version: Int, onManage: () -> Unit) {
    val accounts = remember(version) { SipManager.accounts }
    val current = remember(version) { if (SipManager.isReady) SipManager.core.defaultAccount else null }
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { open = true }) {
        StatusDot(registrationColor(current?.state))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                current?.let { SipManager.accountLabel(it) } ?: "iCall",
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    Prefs.doNotDisturb -> "Do not disturb"
                    current != null -> SipManager.registrationText(current)
                    else -> "Tap to add an account"
                },
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        accounts.forEach { a ->
            DropdownMenuItem(
                leadingIcon = { StatusDot(registrationColor(a.state)) },
                text = { Text(SipManager.accountLabel(a) + if (a == current) "  ✓" else "") },
                onClick = { SipManager.setDefaultAccount(SipManager.accountId(a)); open = false },
            )
        }
        DropdownMenuItem(text = { Text("Manage accounts…") }, onClick = { open = false; onManage() })
    }
}
