# Building

## Get an APK without building

* Every push runs **Android CI** (`.github/workflows/android.yml`). Open the run in the repo's *Actions* tab and
  download the `MicroSIP-apk-<run>` artifact (zip with the APKs).
* Every push to `main` also publishes a GitHub pre-release `v0.1.<run>` with the APKs attached
  (repo → *Releases*).
* Which file to install:
  * `MicroSIP-0.1.N-universal-release.apk` – works on every phone (biggest).
  * `MicroSIP-0.1.N-arm64-v8a-release.apk` – almost all phones from 2017 onwards (smaller).
  * `…-universal-debug.apk` – same app with debugging enabled.
* All APKs are signed with the shared key in the repo, so a newer build installs over an older one.

## Build locally

Requirements: JDK 17+, Android SDK with platform 35 (Android Studio Ladybug or newer is fine), network access to
`dl.google.com`, Maven Central and `download.linphone.org`.

```bash
./gradlew testDebugUnitTest          # pure Kotlin unit tests
./gradlew assembleDebug              # app/build/outputs/apk/debug/
./gradlew assembleRelease            # app/build/outputs/apk/release/ (signed with the shared key)
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

`local.properties` must point at the SDK (`sdk.dir=/path/to/Android/sdk`) or set `ANDROID_HOME`.

## Versions (pinned)

| Component | Version | Where |
|-----------|---------|-------|
| Gradle wrapper | 8.11.1 | gradle/wrapper/gradle-wrapper.properties |
| Android Gradle Plugin | 8.7.3 | build.gradle.kts |
| Kotlin + Compose compiler plugin | 2.0.21 | build.gradle.kts |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0) | app/build.gradle.kts |
| Compose BOM | 2024.12.01 | app/build.gradle.kts |
| Linphone SDK | 5.5.35 | app/build.gradle.kts |
| JDK on CI | Temurin 17 | android.yml |

`versionCode` comes from `-PversionCode=<GitHub run number>`; `versionName` is `0.1.<versionCode>`.

## Signing

`app/debug-shared.keystore`, alias `androiddebugkey`, store/key password `android`. Public on purpose (see
ARCHITECTURE.md DR-4). For a store release create a private keystore, add it as base64 CI secrets and switch the
`release` signing config to read them from environment variables.

## Notes for agents in restricted sandboxes

If `dl.google.com` or `download.linphone.org` is blocked (as in the Claude Code cloud sandbox used to start this
project), you cannot build locally. Push to a branch and read the CI logs instead (GitHub MCP:
`actions_list` → `list_workflow_jobs` → `get_job_logs`). The Linphone API is dumped in
`docs/reference/linphone-sdk-5.5-api.txt` so you can check signatures offline; regenerate it with
`tools/dump-linphone-api.sh <version>` from a machine with network access.
