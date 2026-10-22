# Testing

## Automated

| Suite | Command | What it covers |
|-------|---------|----------------|
| JVM unit tests | `./gradlew testDebugUnitTest` | `DialString` (dial input → SIP URI, prefix, '+', tel:/sip:), `DialogInfo` (BLF XML parsing) |
| Android lint | `./gradlew lintDebug` | report uploaded as CI artifact `reports-<run>` (non-blocking) |
| Build | `./gradlew assembleDebug assembleRelease` | the app compiles against the real Linphone SDK |

| Real-server REGISTER | CI job `sip-smoke` (needs repo secret `SIP_PASSWORD`) or `SIP_PASSWORD=… python3 tools/sip_register_test.py --user 13811 --domain sip.sipup.org --proxy 162.19.138.146 --transport tls` | server reachable, TLS handshake/cert, digest credentials; registers a throw-away contact then removes it |

CI runs all of these on every push. Results: see the run summary and `reports-<run>` artifact.

## Manual test plan (real device + PBX)

Use any SIP server: Asterisk/FreePBX, 3CX, a hosted provider, or a free test service. Two accounts make most
tests possible with one phone + desktop MicroSIP.

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Add UDP account, correct password | Top bar dot turns green, "Online" |
| 2 | Wrong password | Red dot, "Failed: Unauthorized"/"Forbidden" toast |
| 3 | TLS account (port 5061) + SRTP | Registers; call shows lock icon and "SRTP" |
| 4 | Outgoing call to extension | Ringing… → duration counter, two-way audio |
| 5 | Incoming call with app in background / screen locked | Full-screen call UI or heads-up with Answer/Decline; ringtone plays |
| 6 | Hold / resume, mute | Remote hears hold music / silence |
| 7 | Speaker, Bluetooth headset | Audio moves to the chosen device |
| 8 | DTMF to IVR (each mode) | IVR reacts to digits |
| 9 | Blind transfer | Remote party is connected to target, our call ends |
| 10 | Attended transfer (Add call, Transfer → held call) | Both remote parties connected |
| 11 | Two calls + Merge | 3-way audio |
| 12 | Record a call | WAV appears in Settings › Recordings and plays |
| 13 | Missed call | Notification + badge on Calls tab; entry in red |
| 14 | SIP message both directions | Bubbles, delivery state, notification with Reply |
| 15 | Contact with BLF on a PBX extension | Dot green idle / orange ringing / red busy |
| 16 | Presence between two accounts with "Publish presence" | Contact dot shows online |
| 17 | DND on | Caller gets busy/DND; nothing rings |
| 18 | Reboot phone | Account registers again (service notification shows Online) |
| 19 | tel: link in browser | App offers to handle; number prefilled |
| 20 | Codec order: put G.711 first | Call info shows PCMU/PCMA |

## Results log

Record every run here (newest first): date, build/run number, device + Android version, server, results per #.

| Date | Build | Device | Server | Result |
|------|-------|--------|--------|--------|
| 2026-10-22 | CI run 6 | – | – | 18/18 unit tests pass, debug + release APKs build, lint runs. No device test yet. |
