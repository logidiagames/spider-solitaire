# Backup (Android Auto Backup), v1.3 Part 1.1

A player who moves to a new phone, or reinstalls, gets their game back from Android's own backup.

## What is backed up

Only `shared_prefs/freecell_web_state.xml`, the game's authoritative state (the page keeps nothing of its own in WebView storage on Android; it saves through `AndroidBridge.saveState`):

- `saved_state`: progress, current game, coins, owned and equipped items, Daily history, the warm-up position and the deals played per pool, tips already shown, card size, missions, achievements, stars, settings, language, statistics, reminder settings, event progress.
- `numbered_collection`: the numbered deals collection.
- `first_tutorial_completed`: the tutorial is not shown again.
- `rewarded_offered`: whether the last `rewarded_config` offered a rewarded video (harmless: it only matters together with the Remove Ads cache, which is not restored).

Rules: `res/xml/backup_rules.xml` (Android 11 and older) and `res/xml/data_extraction_rules.xml` (Android 12+, cloud backup and device-to-device transfer). `BackupRulesTest` fails if anything else is added.

## What is never restored

| Data | Where | Why |
| --- | --- | --- |
| Ads consent (IABTCF keys, UMP) | default shared preferences | Consent belongs to the device; UMP asks again |
| Remove Ads purchase cache | `freecell_billing` | Always re-checked with Google Play Billing at launch |
| Pretend store (test builds) | `freecell_fake_billing` | Test data |
| Ad cadence, sessions, pending rewarded offers | `freecell_ad_state` | Per install |
| Reminder scheduling | `freecell_reminders`, WorkManager database | Rebuilt at launch from the saved reminder settings |
| Remote Config cache, Firebase installation and Analytics IDs, Crashlytics | Firebase files and preferences | Per install |
| WebView data and caches | `app_webview`, cache | Temporary |

## After a restore

Nothing special runs; the normal launch already does it all:

- `RemoveAdsManager` queries Google Play purchases at every launch, so a real purchase comes back and a restored phone never shows "ads removed" on trust.
- The page sends the reminder settings with `setReminderState` at launch, which schedules the reminder again (Android 13+ still needs the notification permission on the new phone).
- `FreeCellCore.migrateState` checks every field of the saved state on every load and upgrades older schemas; a missing or partial state starts a fresh game without crashing.
- UMP shows the consent form again where it is required.

## How to test (emulator or phone, any build)

```sh
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow com.nexusplaylabss.freecell
adb shell pm clear com.nexusplaylabss.freecell
adb shell bmgr restore com.nexusplaylabss.freecell
```

The local transport keeps the backup on the device, so no Google account is needed. To check the real cloud path instead, keep the Google transport, run `backupnow`, uninstall the app and install it again: Android restores during the install.

Check after the restore: coins, items, level and Daily history are back and the tutorial does not show; in a test build, a pretend Remove Ads purchase is gone and the consent form comes back on an EEA test device.

## Limits (for the owner)

- Android backs up on its own schedule: about once a day, when the phone is idle, charging and on Wi-Fi. A game played an hour before losing the phone may not be in the backup.
- Players can turn backup off in Android settings.
- A restore happens only when the app is installed on a phone signed in with the same Google account (or during a phone-to-phone transfer). It is not live sync: two phones never share one game.

## Privacy policy text to add

"The game uses Android's own backup to keep your progress (coins, items, settings and game history) if you change or reset your phone. The backup is made and stored by Google in your Google account; we cannot see it. It contains no personal data and never includes advertising choices or purchases, which are checked again on the new phone. You can turn it off in your phone's backup settings."
