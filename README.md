# FreeCell (Nexus Play Labs)

Original Android FreeCell prototype with package `com.nexusplaylabss.freecell`. The reference XAPKs and screen recording were used only to identify expected product behaviour; this repository contains an independent implementation and original programmatic visuals.

Version 0.4.4 includes the playable FreeCell board, embedded offline icons, traditional original court-card illustrations, animated tap and drag movement, multi-card moves, free cells, foundations, hints, undo, automatic foundation moves, random/winnable/numbered/daily games, tutorial, themes, six languages, persistent settings and game state, level progression, victory animation, the developer victory shortcut, and Remote Config-controlled banner, interstitial and app-open ad code. A new original loading screen now leads into a mandatory first-run tutorial, with ads blocked until onboarding is complete. Firebase Analytics instrumentation covers onboarding, gameplay, progression, personalization, Daily Challenge, and a provider-independent lifecycle for all three ad formats; see `docs/ANALYTICS_EVENTS.md`.

## Install the test APK

Build the debug APK (see Build), connect an Android phone by USB, enable Developer options and USB debugging, accept the phone's authorization prompt, then run from the repository root:

```sh
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed automatically with the local Android debug certificate and is intended only for testing. APK files are not committed (`*.apk` is in `.gitignore`).

## Build

From the repository root. `local.properties` (not committed) must point `sdk.dir` at the Android SDK, e.g. `sdk.dir=/opt/homebrew/share/android-commandlinetools`.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test lintDebug assembleDebug
```

## Signed release AAB

The owner runs `tools/build_release_aab.sh`: it reads the upload key password from the macOS Keychain, builds with the registered `freecell-upload` key, checks the signer fingerprint and 16 KB alignment, and copies the AAB to `~/Downloads/FreeCell-release-builds/`. Raise `versionCode` before every upload. The manual equivalent:

The Gradle project accepts release signing details through environment variables, keeping secrets outside the repository:

```sh
export UPLOAD_KEYSTORE="/absolute/path/upload-keystore.jks"
export UPLOAD_KEYSTORE_PASSWORD="..."
export UPLOAD_KEY_ALIAS="..."
export UPLOAD_KEY_PASSWORD="..."
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. Without those four values, Gradle can assemble the bundle but it remains unsigned and cannot be uploaded as a production release.
