# Spider Solitaire (Nexus Play Labs)

Android Spider Solitaire with package `com.nexusplaylabss.spider`, built from the FreeCell code base (same screens, themes, economy, missions, Daily Challenge, ads and analytics) with Spider rules: two decks, ten columns, a stock of five deals, runs of one suit from King to Ace leaving for eight foundations, and 1, 2 or 4 suits as the three difficulty tiers. Notes for development are in `CLAUDE.md`.

## Build

From the repository root, with `local.properties` (not committed) pointing `sdk.dir` at the Android SDK:

```sh
export JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot' PATH="/c/Program Files/nodejs:$PATH"
./gradlew test lintDebug assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`, signed with the local debug certificate and meant only for testing (Google's test ads, DEV tools). APK and AAB files are not committed.

## Install the test APK

Connect an Android phone by USB with USB debugging on, then:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release

Spider Solitaire has no AdMob app, Firebase project, upload key or Play Console listing of its own yet (see "Owner tasks" in `CLAUDE.md`). Until they exist the app uses Google's test ad IDs and builds without Firebase.
