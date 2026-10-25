# Architecture

## Overview

```
┌──────────────────────────── UI (Jetpack Compose, Material 3) ───────────────────────────┐
│ MainActivity → AppRoot (bottom tabs + NavHost)        CallActivity → CallScreen          │
│   Dialpad · Calls · Contacts · Messages · Settings      incoming / active / multi-call   │
│   Accounts · AccountEdit · Codecs · Recordings · Chat   DTMF pad · transfer · video      │
└───────────────▲──────────────────────────────────────────────────────▲──────────────────┘
                │ observes *Version StateFlows, calls methods          │
┌───────────────┴──────────────── sip/ (app logic) ────────────────────┴──────────────────┐
│ SipManager (object) – owns the Linphone Core, all SIP operations, CoreListener          │
│ Prefs – app settings (SharedPreferences)   AccountConfig – account editor model         │
│ DialString – dial input → SIP URI (pure)   DialogInfo – BLF dialog-info parser (pure)   │
│ DeviceContacts – phone address book cache                                               │
└───────────────▲─────────────────────────────────────────────────────────────────────────┘
                │
┌───────────────┴──────── service/ ───────────┐   ┌────────── Linphone SDK 5.5 (AAR) ─────┐
│ SipService – foreground service             │   │ SIP UDP/TCP/TLS, SRTP/ZRTP/DTLS, ICE  │
│ Notifications – channels, call/msg notifs   │   │ Opus, G.722, G.711, GSM, Speex, iLBC… │
│ CallActionReceiver – decline/hangup/reply   │   │ presence, SIP MESSAGE, SUBSCRIBE      │
│ BootReceiver – start on boot                │   │ call logs + chat DB (sqlite)          │
└─────────────────────────────────────────────┘   └───────────────────────────────────────┘
```

Package and applicationId: `com.imadlabs.icall` (app/src/main/java/com/imadlabs/icall). Until 2026-10-25 it was `com.microsip.android` (see DR-7).

## How state flows

* `SipManager` creates the Linphone `Core` on the main looper (auto-iterate is on by default on Android).
  **Every SipManager call must be on the main thread.**
* Linphone objects are mutable and live in the core. Instead of copying them into view models, `SipManager`
  exposes counters: `accountsVersion`, `callsVersion`, `historyVersion`, `chatVersion`, `contactsVersion`
  and a `blfStates` map. The `CoreListenerStub` bumps the right counter on each event; composables do
  `val v by SipManager.callsVersion.collectAsState()` and `remember(v) { SipManager.calls }`.
* One-off errors go through `SipManager.toasts` (collected by MainActivity).

## Persistence

| Data | Where |
|------|-------|
| Accounts, auth info, codec enable/order, NAT policy | Linphone config `files/linphonerc` (written by the SDK) |
| Call history, chat messages, friends (contacts) | Linphone sqlite DB in app files (managed by the SDK) |
| App settings, per-account extras (label, dial prefix, encryption, voicemail), BLF list | SharedPreferences `icall` (`Prefs`) |
| Call recordings | `files/recordings/*.wav` (shared through FileProvider) |

Per-account extras are keyed by the Linphone `AccountParams.idkey`, which the app sets to a UUID on creation.

## Background & calls

* `SipService` is a foreground service. Idle it uses type `specialUse` (Android 14+), during calls it is
  re-started as `phoneCall|microphone` (falls back to `phoneCall`). It runs while there is at least one
  account and "Stay connected in background" is on, or while a call exists.
* Incoming call: `SipManager.onIncoming` applies DND / reject-all / call-waiting / forwarding rules, then posts a
  `CallStyle` full-screen notification and tries to open `CallActivity`. Linphone plays the ringtone
  (`isNativeRingingEnabled = true`), so the notification channel is silent.
* Answer from the notification opens `CallActivity` with `ACTION_ANSWER` (an activity, so the app is in the
  foreground and may use the microphone). Decline/hang-up use `CallActionReceiver`.
* Proximity wake lock turns the screen off while the earpiece is in use.

## Decision records

### DR-1: SIP engine = Linphone SDK (2026-10-22)
* Options: PJSIP (what desktop MicroSIP uses) built with the NDK; Linphone SDK prebuilt AAR; pure-Java JAIN-SIP + own RTP.
* Chosen: **Linphone SDK** `org.linphone:linphone-sdk-android` from `https://download.linphone.org/maven_repository`.
* Why: production-grade on Android, prebuilt for all ABIs, covers TLS, SRTP/ZRTP/DTLS-SRTP, ICE/STUN/TURN,
  Opus/G.722/G.711/GSM/Speex, video (VP8/H.264), presence, SIP MESSAGE chat, SUBSCRIBE/NOTIFY, call logs DB,
  audio routing (BT/speaker), conferencing. PJSIP would need a custom NDK build pipeline (hours of CI) and its
  own Java bindings; JAIN-SIP would need us to write the whole media stack.
* Licence impact: Linphone SDK is GPLv3 (commercial licence available from Belledonne). MicroSIP itself is GPL,
  so the app should be distributed under a GPL-compatible licence with source available. **The repo owner
  should add a LICENSE file (GPL-3.0-or-later recommended).**
* Version pinned: 5.5.35 (latest non-pre-release on 2026-10-22). API reference dumped to
  `docs/reference/linphone-sdk-5.5-api.txt` (from 5.5.37-pre.1, same API surface).

### DR-2: UI = Jetpack Compose + Material 3, no DI framework, no Room
* Linphone already persists accounts, history, chat and contacts, so a separate DB adds nothing.
* A single `SipManager` object keeps the code easy for several agents to follow.

### DR-3: Builds happen on GitHub Actions
* The agent sandbox that started this project cannot reach `dl.google.com` (Android SDK / Google Maven) or
  `download.linphone.org`, so it cannot build locally. CI (`.github/workflows/android.yml`) builds, tests and
  publishes APKs. Agents with a normal network can build locally (see BUILD.md).

### DR-4: Shared, committed signing key
* `app/debug-shared.keystore` (password `android`) signs debug *and* release so every CI build installs over the
  previous one. It is public; replace it with a secret key before publishing to a store.

### DR-5: Kotlin setters returning int
* Several Linphone setters return `int` (e.g. `AccountParams.setIdentityAddress`, `Core.setTransports`,
  `Friend.setName`, `Account.setParams`). Kotlin does not turn those into properties, so call them as
  `setX(...)` explicitly. Setters returning `void` can be used as properties.

### DR-6: TLS certificate verification default off
* Many PBXs use self-signed certificates; desktop MicroSIP does not verify by default either. Users can turn on
  "Verify TLS server certificates" in Settings.

## DR-7: Rebrand to iCall by ImadLabs (2026-10-25)

* Imad's request: the app is **iCall**, brand **ImadLabs**, Telegram https://t.me/ImadLabs, graphics from
  https://github.com/ImadKhan1999/Graphics.
* applicationId/package changed `com.microsip.android` → `com.imadlabs.icall`. Consequence: it installs as a new app
  next to the old test build (uninstall the old one); settings/accounts from the old build are not migrated (only
  test data existed).
* UI follows the ImadLabs design system: dark-first theme (Deep Void #070B14, Cyber Mint #00F5A0 primary, Sky
  Diamond #38BDF8 secondary), user can pick Dark/Light/System in Settings › Appearance.
* Fonts (Plus Jakarta Sans / Inter) are not bundled yet; the system font is used.
