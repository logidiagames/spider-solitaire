# Remote Config — ads

Create one Firebase Remote Config parameter named `ads_config` with type `JSON`.

```json
{
  "enabled": true,
  "availability": {
    "interstitials": {
      "interval": 1,
      "skip_after_loss": true,
      "availability_conditions": {
        "session": 1,
        "games_played": 3
      }
    },
    "banners": {
      "availability_conditions": {
        "session": 1,
        "games_played": 0
      }
    },
    "app_open": {
      "cold_start_session": 1,
      "background_min_session": 1,
      "splash_timeout": 6,
      "background_time_to_show_min": 1
    }
  },
  "ad_unit_ids": {
    "banner": "ca-app-pub-1626295360729995/6507689900",
    "interstitial": "ca-app-pub-1626295360729995/5548540454",
    "app_open": "ca-app-pub-1626295360729995/1504212022",
    "rewarded": "ca-app-pub-1626295360729995/3060836279"
  },
  "enable_amazon_sdk": false,
  "enable_pubmatic_sdk": false
}
```

## Semantics

- A session is one app process start. Activity recreation and orientation changes do not increment it.
- A played game is counted when a new deal starts. Restarts count; the developer victory test does not.
- Restarting the same deal never shows an interstitial (it still counts as a played game for the cadence).
- `interstitials.interval` is the number of started games between successfully shown interstitials.
- `interstitials.skip_after_loss` (boolean, default `true`; owner request 2026-11-10): no interstitial when the new game replaces one the player did not win (abandoned, stuck, or switched before any move; the page passes `afterLoss` to `onGameStarted`). Only a new game after a win, or the first game of a launch, can show one. The game still counts as played, the interval and the session / games-played conditions are unchanged, so the next game started after a win can show the interstitial that was skipped. `false` brings back interstitials after a loss.
- `splash_timeout` is seconds. A cold-start app-open ad is not shown if it loads after this window.
- `background_time_to_show_min` is minutes spent in the background before an app-open ad is eligible.
- Invalid or missing numbers are bounded to safe values by the app.
- Remote values downloaded in one session are activated on the following launch, so cadence does not change during a game. The minimum fetch interval is 12 hours.
- Both parameters (`ads_config` and `config_admbob_mediator`) are delivered to the ad controller **once per launch**, after the previous session's download is activated (or from the bundled files when Firebase is not configured). Every ad unit decision in a run therefore uses the same values.

Debug builds always use Google's official test ad units. Release builds use the three production ad unit IDs, which are bundled in `app/src/main/assets/ad_config_defaults.json` (banner `…/6507689900`, interstitial `…/5548540454`, app open `…/1504212022`). A Remote Config `ads_config` value replaces that whole file, so it must carry the same IDs. The manifest uses the production AdMob App ID `ca-app-pub-1626295360729995~6971818248` from `strings.xml`.

`ad_unit_ids.rewarded` is the "double your coins" video (v1.2 B1). The owner created the Rewarded unit `ca-app-pub-1626295360729995/3060836279`; since 2026-11-10 it is also in the bundled default, so it works from the very first launch, before Remote Config has been read. When the value is empty, release builds never request a rewarded video and the buttons show "Not available right now". The mediator's `_RewardedData` is still only logged, so this unit always comes from `ads_config`.

### Rewarded video and Remove Ads

Remove Ads stops the banner, interstitial and app-open ads. The rewarded video is voluntary, so it stays after the purchase: with Remove Ads bought, the consent check and the Mobile Ads SDK still run, but only the rewarded video is ever loaded. If the last launch's `rewarded_config` offered no rewarded placement (`enabled` false, `max_per_day` 0, or both placements off), a launch with Remove Ads bought skips consent and the SDK entirely, as before. `ads_config.enabled` false turns every format off, the rewarded video included. Rules for the offer itself (placements, daily limit, minimum games): `docs/REMOTE_CONFIG_GAME.md`.

The Amazon and PubMatic booleans are parsed and exposed to the ad controller. Their mediation adapters still require the corresponding vendor dependencies, application/profile identifiers and account configuration before they can serve inventory.

## Firebase connection

Register the Android app `com.nexusplaylabss.freecell` in Firebase and place its `google-services.json` at `app/google-services.json`. The build applies the Google Services plugin automatically when that file exists. Without it, the app uses `app/src/main/assets/ad_config_defaults.json`.

## Consent (UMP)

`AdsConsent` runs Google's User Messaging Platform on every launch. The Mobile Ads SDK is not initialised, and no ad is loaded or shown, until that launch's check has finished and reports that ads may be requested. The loading screen holds its last step (three suits lit) until the check finishes, so where a form is required it always appears over the loading screen, and the tutorial or game only start after the user has answered. Where the user's region requires it, Settings → Privacy Policy shows a **Privacy options** button that reopens the form.

The form only appears once a GDPR message for this app is published in AdMob → Privacy & messaging. Without one, UMP reports consent as not required and ads load everywhere.

### Testing consent in debug builds

Debug builds can force the EEA geography for chosen test devices. Put the device's UMP hashed ID (logcat prints it as `addTestDeviceHashedId("…")` under the `UserMessagingPlatform` tag) in `~/.gradle/gradle.properties`, outside the repository:

```properties
freecellUmpTestDeviceIds=HASHED_ID_1,HASHED_ID_2
```

Release builds never set any consent debug settings (`BuildConfig.UMP_TEST_DEVICE_IDS` is empty in release).

## `config_admbob_mediator` (Android only)

A second Remote Config parameter, type JSON, named exactly `config_admbob_mediator`. Bundled default: `app/src/main/assets/config_admbob_mediator_defaults.json`. Parsed by `AdmobMediatorConfig`; an invalid or missing remote value falls back to the bundled file, like `ads_config`.

```json
{
  "_Version": 0,
  "_IsDisabled": false,
  "_Priority": 2,
  "_BannerConfig": {
    "Type": 0,
    "Position": 1,
    "AutoRefreshIntervalSec": 15,
    "FloorReloadDelaySec": 15
  },
  "_BannerData": { "AndroidCode": "ca-app-pub-1626295360729995/6507689900" },
  "_InterstitialData": { "AndroidCode": "ca-app-pub-1626295360729995/5548540454" },
  "_RewardedData": { "AndroidCode": "" },
  "_AppOpenData": { "AndroidCode": "ca-app-pub-1626295360729995/1504212022" },
  "_ReConsentIntervalMinutes": 10000
}
```

### Interpretation (assumptions)

- **Android only.** Only `AndroidCode` is read in each `_…Data` object. Keys for other platforms are ignored, and no other platform's codes are stored in this project.
- **`_IsDisabled`** `true`: this config has no effect at all — no banner auto-refresh, no retry backoff, and no ad unit override. `ads_config.enabled` remains the master switch for all ads.
- **`AutoRefreshIntervalSec`**: seconds between banner reloads, measured from the moment the previous banner finished loading. Always clamped in code to **15–120 s**, whatever Remote Config sends (5 → 15, 500 → 120). Owner decision: 15 s.
- **`FloorReloadDelaySec`**: assumed to be the wait before retrying after a banner **fails** to load. It is clamped to at least 15 s (and at most 120 s) and doubles on each consecutive failure up to 120 s (15, 30, 60, 120, 120…). A successful load resets the backoff. A failed reload keeps the previous ad on screen.
- **`Type`, `Position`, `_Priority`, `_Version`, `_RewardedData`**: parsed and logged (`RemoteAdConfig` tag) but they do not change the banner type, its position, or any behaviour. If one of them should mean something, the owner decides that first.
- **`_ReConsentIntervalMinutes`**: parsed and kept. It is **not** used to show the consent form more often; the UMP check on every launch decides when the form appears.
- Missing fields fall back to safe values: refresh 15 s, retry floor 15 s, empty codes, not disabled.

### Banner auto-refresh rules

The app reloads the banner itself (one `AdView`, reused) only when all of these hold: the activity is resumed, the banner is on screen (or its very first load is being retried), consent allows ads, `ads_config.enabled` is true, onboarding/tutorial is complete, no full-screen ad is showing and no banner request is in flight. Timers stop in `onPause` and resume in `onResume` (an overdue reload runs about 1 s after resuming); the `AdView` is paused/resumed with the activity and destroyed in `onDestroy`. After the first banner loads, the slot keeps that ad's height, so reloads never move the game layout. Debug builds use Google's test banner, and the refresh works the same.

**Refresh must be off for the Banner unit in the AdMob console.** Otherwise the SDK also refreshes the banner and the two add up. (Google's test banner unit refreshes itself every 60 s, which is visible in debug logs as loads the app did not request.)

### Ad unit precedence (one source per run)

Implemented in `AdUnitResolver`:

1. Debug builds always use Google's test units.
2. Release: if `config_admbob_mediator` is valid, not disabled and has a non-empty `AndroidCode` for the format, that unit wins.
3. Otherwise the unit from `ads_config.ad_unit_ids` is used.
4. If both are empty, that format is not requested.

Interstitial and app-open timing and eligibility are unchanged; they only take their unit from this resolver.

