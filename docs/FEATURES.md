# Feature parity with MicroSIP (desktop)

Status legend: ✅ implemented · 🟡 partial / needs real-world testing · ❌ not yet · ➖ not applicable on Android

"Implemented" means the code path exists and compiles in CI. Items are only marked **verified** in
TESTING.md after a test against a real PBX.

| Area | Feature | Status | Where |
|------|---------|--------|-------|
| Accounts | Multiple SIP accounts, default account switcher | ✅ | AccountsScreen, AppRoot.AccountSwitcher |
| | Domain, username, password, auth ID, display name | ✅ | AccountEditScreen, SipManager.saveAccount |
| | Proxy / outbound proxy, register on/off, expiry | ✅ | " |
| | Transport UDP / TCP / TLS | ✅ | " |
| | STUN, ICE | ✅ | NatPolicy per account |
| | SRTP / ZRTP / DTLS media encryption, mandatory flag | ✅ | per-account, applied to calls |
| | Voicemail number (long-press 1), dial prefix, '+' replacement | ✅ | DialString, DialpadScreen |
| | Registration status indicator + error text | ✅ | top bar, Accounts list, service notification |
| Calls | Outgoing / incoming audio calls | ✅ | SipManager.call/answer |
| | Redial (Call on empty dialpad) | ✅ | DialpadScreen |
| | Hold / resume, mute | ✅ | CallScreen |
| | Speaker / earpiece / Bluetooth routing | ✅ | SipManager.routeAudio |
| | DTMF (RFC 2833 / SIP INFO / both) | ✅ | Settings + in-call keypad |
| | Blind transfer | ✅ | TransferDialog |
| | Attended transfer | ✅ | Add call → Transfer → pick held call |
| | Call waiting, multiple calls, swap | ✅ | CallScreen call cards |
| | Local conference (merge) | 🟡 | SipManager.mergeCalls (needs PBX testing) |
| | Call recording (manual + automatic) | ✅ | WAV in files/recordings, RecordingsScreen |
| | Auto answer (with delay) | ✅ | Settings |
| | Do not disturb, reject all incoming | ✅ | Settings |
| | Call forwarding (unconditional, local redirect) | ✅ | Settings |
| | Video calls | 🟡 | toggle in Settings; basic remote + preview views |
| | ZRTP SAS display and verification | ✅ | CallScreen MediaInfo |
| | Codec / encryption info during call | ✅ | CallScreen MediaInfo |
| | tel: / sip: links open the app | ✅ | MainActivity intent filters |
| | Proximity sensor screen-off | ✅ | CallActivity |
| History | Call log with missed filter, call back, delete, clear | ✅ | HistoryScreen |
| | Missed call notification + badge | ✅ | Notifications, AppRoot |
| Contacts | SIP contacts (add/edit/delete) | ✅ | ContactsScreen (Linphone friends) |
| | Phone address book (read-only) + name lookup for callers | ✅ | DeviceContacts |
| | Presence (SUBSCRIBE presence, PUBLISH own) | 🟡 | needs a presence-capable server |
| | BLF (dialog event package) | 🟡 | DialogInfo parser unit-tested; needs PBX test |
| Messaging | SIP MESSAGE one-to-one chat, history, unread badges | ✅ | MessagesScreen, ChatScreen |
| | Delivery state | ✅ | message bubbles |
| | Message notification with inline reply | ✅ | Notifications.showMessage |
| Settings | Codec enable + priority (audio & video) | ✅ | CodecsScreen |
| | Echo cancellation, noise suppression, AGC, adaptive rate | ✅ | Settings |
| | IPv6, Wi-Fi only, TLS verification | ✅ | Settings |
| Background | Foreground service keeps registration | ✅ | SipService |
| | Start on boot / after update | ✅ | BootReceiver |
| | Battery-optimisation and full-screen-intent shortcuts | ✅ | Settings |
| | Push notifications (FCM) | ❌ | needs a push-capable SIP proxy (Flexisip) – out of scope for now |
| Diagnostics | Share logs (logcat incl. SIP traces) | ✅ | Settings › Share logs |
| Desktop-only | Global hotkeys, tray icon, auto-update | ➖ | |
| Other | MWI voicemail indicator | ❌ | Linphone exposes onMessageWaitingIndicationChanged; not wired yet |
| | Contacts import/export (vCard/CSV) | ❌ | FriendList.importFriendsFromVcard4File exists |
| | Multiple languages | ❌ | strings are inline English |
| | Android Telecom (ConnectionService) integration | ❌ | would let calls show in the system dialer / car |
