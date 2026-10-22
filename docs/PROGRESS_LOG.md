# Progress log

Append-only. Newest entries at the bottom. Format: `## YYYY-MM-DD HH:MM UTC — agent — title`, then what/why,
commands, results, problems.

## 2026-10-22 14:22 UTC — Claude (cloud session) — Project start

* Request (Imad Khan): build a full-featured MicroSIP-like SIP softphone for Android from scratch, deliver an APK,
  document every step so several AIs can continue.
* Repo was empty. Created `main` with a README (initial commit `bf87c3a`) so PRs have a base. Work happens on
  branch `claude/project-thread-qrvfne`.

## 2026-10-22 14:23 UTC — Claude — Environment check

* Sandbox has JDK 21, Gradle 8.14.3, no Android SDK.
* `dl.google.com` (Android SDK + Google Maven) and `download.linphone.org` are **blocked** by the sandbox egress
  proxy (HTTP 403 on CONNECT). Maven Central and services.gradle.org work.
* Consequence: cannot build locally → all builds run on GitHub Actions (ARCHITECTURE.md DR-3).

## 2026-10-22 14:24 UTC — Claude — Linphone API probe on CI

* Pushed `.github/workflows/api-probe.yml` that downloads the newest Linphone SDK AAR and runs `javap` on the core
  classes; read the job log through the GitHub MCP.
* Results: latest stable `5.5.35`, pre-release `5.5.37-pre.1`; AAR is 61 MB with arm64-v8a, armeabi-v7a, x86,
  x86_64. Runner has platforms android-34…37 and build-tools 34–37.
* Saved the API surface to `docs/reference/linphone-sdk-5.5-api.txt`; added `tools/dump-linphone-api.sh`.
* The probe workflow exited 1 at the end (asked javap for a class that does not exist, `FirewallPolicy`) – harmless,
  output was complete. Workflow removed afterwards.
* First run sat in "queued" ~3 minutes before a runner picked it up.

## 2026-10-22 14:30–14:41 UTC — Claude — App implementation (milestone 1)

* Decisions: Linphone SDK 5.5.35 (DR-1), Compose UI (DR-2), shared signing key (DR-4), see ARCHITECTURE.md.
* Gradle: wrapper 8.11.1 (generated locally with `gradle wrapper`), AGP 8.7.3, Kotlin 2.0.21, compileSdk 35,
  minSdk 26, ABI splits + universal APK.
* Code written (see ARCHITECTURE.md for the map): SipManager, Prefs, AccountConfig, DialString, DialogInfo,
  DeviceContacts, SipService, Notifications, CallActionReceiver, BootReceiver, MainActivity/AppRoot,
  CallActivity/CallScreen, Dialpad, History, Contacts, Messages, Chat, Accounts, AccountEdit, Settings, Codecs,
  Recordings.
* Unit tests: DialStringTest (11 cases), DialogInfoTest (7 cases).
* CI: `.github/workflows/android.yml` – unit tests, assembleDebug + assembleRelease, lint (non-blocking),
  artifacts, pre-release on `main`.
* Commit `1724235` pushed; CI run #1 started.

## 2026-10-22 14:43 UTC — Claude — CI run 1 result

* `compileDebugKotlin` **succeeded on the first attempt** (only deprecation warnings: `Factory.setDebugMode`,
  `Call.isRecording`, `Core.getChatRoom`, `ChatRoom.getHistory(int)`, `ChatMessage.isFileTransfer`,
  `Content.stringBuffer`, two non-AutoMirrored icons). They still work in 5.5; migrate later.
* Unit tests: 18 run, 1 failed — `DialogInfoTest.earlyInitiatorIsBusy`. Cause: regex `dialog\b` also matched the
  `<dialog-info>` root element (`\b` sits between `g` and `-`), so the opening tag examined for `direction` was
  the root. Fix: `dialog(?=[\s>])`. Verified the corrected logic with an equivalent Python regex locally: all 7
  DialogInfo cases give the expected result.
* CI trigger changed: pushes to non-main branches no longer build on `push` (they build via their PR) to stop
  duplicate runs.

## 2026-10-22 14:55 UTC — Claude — CI run 5 result

* `compileDebugKotlin` failed: `onAccountRegistrationStateChanged`, `onCallStateChanged`, `onNotifyReceived`
  "override nothing". Cause: commit f2ee8a1 changed their `String` params to `String?`, but Linphone annotates them
  `@NonNull`, so the nullable signatures no longer match. Reverted to `String` (that was run 1's compiling state).

## 2026-10-22 15:03 UTC — Claude — CI run 6: GREEN

* Commit 9b8b629, PR #1 run 6 (https://github.com/ImadKhan1999/Microsip/actions/runs/39799930796): unit tests
  (18/18), assembleDebug + assembleRelease, lint all passed in ~7 minutes.
* Artifact `MicroSIP-apk-6` (283 MB zip: universal + 4 per-ABI release APKs + universal debug APK).
* Not yet run on a device. Next: manual test plan in TESTING.md.

## 2026-10-22 16:20 UTC — Claude — Real SIP server test harness

* Imad shared a real account (sipup.org: user 13811, domain sip.sipup.org, server/proxy 162.19.138.146, TLS) via the
  project chat (desktop MicroSIP screenshot). The password is NOT stored in the repo.
* The agent sandbox cannot open TCP to 162.19.138.146:5060/5061 (egress allows HTTPS proxying only), so the
  test must run on GitHub Actions.
* Added `tools/sip_register_test.py` (dependency-free REGISTER with digest auth over UDP/TCP/TLS; binds a
  throw-away contact then unregisters it) and CI job `sip-smoke` which runs it when the `SIP_PASSWORD`
  repository secret exists. Account details other than the password are in the workflow env.
* README: mapping table from desktop MicroSIP account fields to this app's fields.

## 2026-10-22 16:55 UTC — Claude — Real SIP server test: PASS on TLS, TCP, UDP

* Imad asked to "push for test then remove it": a manual workflow_dispatch workflow (`sip-smoke.yml`) was pushed
  to main (dispatch needs the file on the default branch), run once against the PR branch with the password as an
  input, then deleted from main and from this branch. Run: https://github.com/ImadKhan1999/Microsip/actions/runs/39814922970
* Results (server `sipswitch`, realm sip.sipup.org, MD5 digest, no qop):
  * TLS 162.19.138.146:5061: TLSv1.3, 401 -> 200 OK, registered and unregistered cleanly.
    The server certificate is NOT valid for the IP address, so TLS verification must stay off
    (app default `verifyTls=false`), or the account must use a hostname the certificate covers.
  * TCP 5060 and UDP 5060: 401 -> 200 OK, registered and unregistered cleanly.
* Lesson: GitHub prints a step's `env:` block before `::add-mask::` runs, so a password passed through a
  dispatch input shows in the log. Use the `SIP_PASSWORD` repository secret for future runs (CI job `sip-smoke`).
* Still to do: test the APK itself on a device with this account (registration, calls, BLF, chat).

## 2026-10-22 19:00 UTC — Claude — First install on a real phone

* CI `android.yml` now also publishes a pre-release on manual (workflow_dispatch) runs, so PR builds can be
  downloaded without a GitHub login. Dispatched on the PR branch -> release v0.1.12.
* Installed via adb from Imad's Windows PC (Remote Control session): Infinix X6873, Android 15,
  `MicroSIP-0.1.12-universal-release.apk`, `adb install -r -g` -> Success. Launched; process alive after 8 s,
  no FATAL/AndroidRuntime entries in logcat. Next: account setup + real calls on the device.
