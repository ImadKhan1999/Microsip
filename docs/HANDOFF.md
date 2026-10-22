# Handoff notes

_Last updated: 2026-10-22 by Claude (cloud session, project thread "MicroSIP Android app from scratch")._

## Where things are

* Branch with all work: `claude/project-thread-qrvfne` → PR into `main`.
* APKs: GitHub Actions artifact `MicroSIP-apk-<run>` on every push; pre-release `v0.1.<run>` on every push to `main`.
* Start reading: `docs/README.md`, then ARCHITECTURE.md (code map + decisions), FEATURES.md (status matrix).

## State

* Milestone 1 is written and **CI is green** (run 6, commit 9b8b629): app compiles against Linphone 5.5.35, unit tests pass, APKs are produced.
  See PROGRESS_LOG.md for the history of CI results.
* Nothing has been tested on a real phone or PBX yet. FEATURES.md marks code-complete features ✅ but TESTING.md
  holds the verified results. **The next most valuable step is running the manual test plan (TESTING.md) on a
  device with a real SIP account and logging results.**

## Suggested next tasks (in order)

1. Run TESTING.md manual plan on a device; fix what breaks (use Settings › Share logs for SIP traces).
2. MWI (voicemail waiting indicator): wire `CoreListener.onMessageWaitingIndicationChanged` to a badge on the
   dialpad voicemail key.
3. Android Telecom integration (self-managed `ConnectionService`) so calls interact properly with GSM calls,
   car Bluetooth and the system call log.
4. Contacts import/export (vCard via `FriendList.importFriendsFromVcard4File` / `exportFriendsAsVcard4File`).
5. Localisation: move inline strings to `res/values/strings.xml`.
6. Push notifications (needs a Flexisip-style proxy; out of scope for plain PBXs).
7. Replace the public signing key before any store release (BUILD.md › Signing).

## Gotchas

* The cloud sandbox cannot reach `dl.google.com` or `download.linphone.org`: build via CI, check signatures in
  `docs/reference/linphone-sdk-5.5-api.txt`.
* Linphone setters that return `int` must be called as `setX()` in Kotlin (DR-5).
* All `SipManager` calls must run on the main thread.
* Linphone listener `String` parameters are annotated `@NonNull`: override them as `String` (a `String?` override
  fails with "overrides nothing").
