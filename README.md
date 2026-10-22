# MicroSIP for Android

A MicroSIP-style SIP softphone for Android: multiple SIP accounts, calls with hold/transfer/conference/recording,
DTMF, contacts with presence and BLF, call history, SIP messaging, codec control, TLS + SRTP/ZRTP, and a
background service so calls ring when the app is closed.

* **Download:** see the repository's *Releases* (or the latest *Actions* run artifact). Install
  `…-universal-release.apk`.
* **Engine:** [Linphone SDK](https://www.linphone.org/) 5.5 (GPLv3) with a Jetpack Compose UI.
* **Requirements:** Android 8.0+.

## Quick start

1. Install the APK and allow microphone + notifications.
2. Tap the top bar → *Manage accounts* → **+**. Enter SIP server/domain, username, password (and transport).
3. The dot in the top bar turns green when registered. Dial from the keypad.

## Documentation

Everything about how the app is built, tested and where work stands is in [`docs/`](docs/README.md) –
written so several people or AI agents can continue the work.

## Licence

Linphone SDK is GPLv3, so this app must be distributed under a GPL-compatible licence. The repository owner
should add a `LICENSE` file (GPL-3.0-or-later recommended).
