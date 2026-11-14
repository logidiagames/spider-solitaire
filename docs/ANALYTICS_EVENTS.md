# Analytics event plan

The app uses a compact Firebase Analytics taxonomy. Event names and parameter keys
are lowercase English identifiers so that reports remain stable when the in-game
language changes. The Android bridge accepts only the allowlisted events and
parameters below; unexpected or malformed values are ignored.

No event contains card identities, deal layouts, free text, email addresses, or
other personal data. The developer victory shortcut is excluded from gameplay
analytics.

## App and onboarding

| Event | Trigger | Parameters |
| --- | --- | --- |
| `app_ready` | Loading screen finishes | `first_run`, `load_ms` |
| `tutorial_open` | Tutorial intro opens | `source` |
| `tutorial_start` | Guided tutorial starts | `source` |
| `tutorial_step` | A tutorial step is first displayed during that run | `source`, `step`, `total_steps` |
| `tutorial_skip` | Tutorial is skipped or closed | `source`, `step`, `total_steps` |
| `tutorial_complete` | Final tutorial step is completed | `source`, `step`, `total_steps` |

`source` is `first_run` for mandatory onboarding and `settings` when the tutorial
is opened later.

## Gameplay and progression

| Event | Trigger | Parameters |
| --- | --- | --- |
| `game_start` | A new or replayed deal starts | `game_mode`, `level`, `replay`, `difficulty_tier` |
| `game_win` | A genuine game reaches four complete foundations | `game_mode`, `level`, `moves`, `duration_seconds`, `score`, `difficulty_tier` |
| `game_abandon` | A started, unfinished game is replaced | `game_mode`, `level`, `moves`, `duration_seconds`, `difficulty_tier` |
| `hint_used` | Player requests a hint | `game_mode`, `level`, `moves` |
| `undo_used` | Player successfully undoes a move | `game_mode`, `level`, `moves` |
| `level_up` | A win advances the progression level | `previous_level`, `new_level` |

Automatic hints are not counted as `hint_used`. A failed Undo tap is not counted
as `undo_used`.

## Economy and help (v1.1)

| Event | Trigger | Parameters |
| --- | --- | --- |
| `coins_earned` | A win pays coins (the first win of that deal) | `game_mode`, `value` (coins paid), `source` (`win`) |
| `theme_unlocked` | A theme is bought with coins | `theme`, `value` (price in coins) |
| `no_moves_shown` | The "No more moves possible" or (since v1.4) "There is no way to win from here" message appears (before v1.4: "No winning moves were found") | `source` (`hint` or `auto`), `value` (`none`: no legal move; `unsolvable`: moves remain, but the solver tried every reachable position and none wins). Builds before 2026-10-25 sent `repeat` (every move returned to a position already seen) |

Wins from the developer tools are not sent, and `no_moves_shown` with `source`
`auto` is sent at most once per board position.

| Event | Trigger | Parameters |
| --- | --- | --- |
| `daily_bonus_claimed` | The Daily Bonus is collected | `value` (coins), `step` (day of the cycle) |
| `mission_claimed` | A completed mission is collected | `source` (mission id), `value` (coins) |
| `missions_all_claimed` | The all-missions bonus is collected | `value` (coins) |

## Remove Ads and rating (v1.1 Part 3)

| Event | Trigger | Parameters |
| --- | --- | --- |
| `remove_ads_shown` | The Remove Ads screen opens | `source` (`settings` or `auto`) |
| `remove_ads_purchase_started` | The player taps Buy | `source` |
| `remove_ads_purchase_completed` | Google Play confirms the purchase | `source` |
| `remove_ads_purchase_failed` | The purchase ends without buying (not sent when the player cancels) | `value` (`pending`, `offline`, `failed`, `unavailable`) |
| `rating_prompt_shown` | The rating card appears | `source` (`after_win`) |
| `rating_choice` | The player answers the rating card | `source` (`rate`: opens the Play Store page; `later`; `feedback`: opens the feedback form; `feedback_sent`: the form handed the message to the mail app); since 2026-11-04 `value` with `rate` = the star tapped on the card (`1`–`5`; every star opens the store) |
| `rewarded_offered` | A "Double coins" button is shown with a video ready | `placement` (`victory_double`, `daily_bonus_double`), `value` (extra coins) |
| `rewarded_not_available` | The button is shown disabled (no video loaded), or the video could not be shown | `placement`, `value` |
| `rewarded_started` | The player taps the button | `placement`, `value` |
| `achievement_unlocked` | An achievement is reached | `source` (achievement id) |
| `achievement_claimed` | The player collects its coins | `source` (achievement id), `value` (coins) |
| `reminder_prompt_shown` | The "Remind me about the Daily Challenge?" card appears | `source` (`daily_win`) |
| `reminder_choice` | The player answers it, or Android answers the permission | `source` (`yes`, `later`, `granted`, `denied`) |
| `reminder_opened` | The app is opened from a reminder | `source` (`daily`, `risk`) |
| `share_challenge` | The player taps Challenge on the victory panel (random or numbered deal) | `game_mode` |
| `share_daily` | The player taps Share on a Daily win | `source` (`image`, or `text` when the image could not be made or handed over) |
| `rewarded_completed` | The video reported the reward and the extra coins were credited (once per offer) | `placement`, `value` |
| `event_completed` | The player reaches a seasonal event's goal | `source` (event id, e.g. `winter_2026`) |

No event contains prices, order ids or purchase tokens. `rating_choice` with `rate` only means the Play Store page was opened, not that a review was written. The feedback text itself is never sent to Analytics.

## Menus and personalization

| Event | Trigger | Parameters |
| --- | --- | --- |
| `menu_open` | Settings, Themes, New Game or Missions opens from the toolbar; since 2026-11-06 also a screen opened from Settings (`menu` `tutorial`, `rules`, `privacy`, `contact`, `levels`, `history`, `stats`, `achievements`, `numbered`; `source` `settings`), Game by Number (`game_by_number`, `source` `new_game`), the Numbered collection from it (`numbered`, `source` `game_by_number`), Achievements (`source` `new_game` or `toast`) and the Daily Bonus from the gift button (`daily_bonus`, `source` `new_game`) | `menu`, `source` |
| `autocomplete_used` | 2026-11-06: the player taps Autocomplete | `game_mode`, `level`, `moves` |
| `stats_reset` | 2026-11-06: the player confirms Reset statistics | none |
| `setting_changed` | A switch or FreeCell position changes; since v1.4 also `card_size` (`value` `normal`, `large`, `xl`) and `reduce_effects` (`value` `auto`, `on`, `off`) | `setting`, `enabled` or `value` |
| `effects_reduced` | v1.4: Auto turned the reduced effects on after a slow first deal, or (since 2026-11-05) after slow card moves during play | `value` (`low_fps`: first deal; `low_fps_play`: card moves) |
| `language_changed` | Player selects an app language | `language` (since v1.4 also `pt-BR`, `nl`, `pl`, `tr`, `ru`, `uk`, `ja`, `ko`, `zh-Hans`, `id`, `sv`, `cs`) |
| `theme_selected` | Player selects a table or card theme | `theme`, `theme_type` |
| `daily_challenge_open` | Daily Challenge calendar opens | `source` (since 2026-11-04 also `unlock`: Play on the "Daily Challenge unlocked" popup) |
| `daily_challenge_start` | A daily deal starts (today's, or a past day's from the calendar) | `level` |
| `daily_challenge_complete` | A daily deal (today's or a past day's) is completed | `level`, `moves`, `duration_seconds`, `score`; since 2026-11-06 `rank` (the Daily Rivals place, today's deal only) |

## Advertising

The same lifecycle events are used for all three ad formats, instead of creating
one event name per provider or network.

| Event | Trigger | Parameters |
| --- | --- | --- |
| `ad_request` | App asks the SDK to load an ad | `ad_format`, `placement`, `session_count`, `games_played` |
| `ad_loaded` | SDK returns a loadable ad | `ad_format`, `placement`, `session_count`, `games_played` |
| `ad_shown` | Banner impression or full-screen presentation occurs | `ad_format`, `placement`, `session_count`, `games_played` |
| `ad_dismissed` | Interstitial or app-open ad closes | `ad_format`, `placement`, `session_count`, `games_played` |
| `ad_failed` | Load or presentation fails | the common ad parameters plus `error_code`, `error_domain` |

`ad_format` is `banner`, `interstitial`, `app_open` or `rewarded`. Current placements are
`game_footer`, `between_games`, `app_foreground`, and for the rewarded video `double_coins`
(requests) or `victory_double` / `daily_bonus_double` (shown, dismissed).

Firebase and Google Mobile Ads may automatically collect events such as
`first_open`, `session_start`, `user_engagement`, `screen_view`, `ad_impression`,
and `ad_click`. The app deliberately does not log manual copies of those names.

## Google Ads app campaign goals (2026-11-06)

Each goal is sent **once per player** and never from a test build: Android drops them when
`BuildConfig.TEST_TOOLS` is true (debug and qa builds; `AnalyticsTracker.GOAL_EVENTS`). The page
keeps what it sent in `goals` in the saved state (`FreeCellCore.cleanGoals` / `goalsReached` /
`markGoals`), so the backup carries it and a reinstall does not send them again. A save from before
these goals (a game already started) starts with what it already reached marked as sent, and
`day2_return` too. Not sent for developer games or the DEV quick win. Each carries `level`.

| Event | When | Parameters |
| --- | --- | --- |
| `intro_complete` | The 5th warm-up deal is won (`introIndex` reaches the number of `first_games.seeds`) | `level` |
| `first_daily_win` | The first Daily Challenge win (today's or a past day's) | `level` |
| `wins_3` | The 3rd win (`stats.wins`) | `level` |
| `wins_10` | The 10th win | `level` |
| `day2_return` | The first session (launch or return to the app) on a calendar day after the first day the game was opened | `level` |

## User properties (2026-11-06)

For audiences; game state only, no personal data. Values are at most 36 characters.

| Property | Values | Set by |
| --- | --- | --- |
| `player_level` | the level, e.g. `7` | the page, at launch and after each win |
| `intro_complete` | `yes` / `no` | the page, at launch, after each win and when a goal is sent |
| `ads_removed` | `yes` / `no` | Android, at launch (cached purchase) and when the purchase state changes |
| `app_language` | the game language code, e.g. `pt`, `pt-BR` | the page, at launch and when the player picks a language |

## Consent and advertising ID (checked 2026-11-06)

- Consent Mode v2: UMP 4.0.0 (Google needs 3.2.0 or later) passes the GDPR answer to Google Analytics for Firebase as ad_storage, ad_user_data, ad_personalization and analytics_storage when EU rules apply; Firebase starts (its content provider) before the UMP flow in `MainActivity`. No consent-mode or TCF keys are set in the manifest, so outside the EU everything stays granted. Consent mode must be turned on in AdMob (Privacy & messaging).
- `com.google.android.gms.permission.AD_ID` is in the merged manifest (added by play-services-ads 25.5.0), with `ACCESS_ADSERVICES_AD_ID`.
- Nothing disables Analytics collection or the automatic `ad_impression` event (no `firebase_analytics_collection_*`, `google_analytics_*` or `google_analytics_tcf_data_enabled` keys); `ad_impression` needs the AdMob app linked to the Firebase project.

## Firebase setup

Register `com.nexusplaylabss.freecell` in the intended Firebase project and
place its `google-services.json` in `app/google-services.json`. The Google
Services plugin is applied automatically when the file exists. Without it, the
analytics layer safely becomes a no-op and the rest of the prototype continues
to work.

`difficulty_tier` (v1.3): `easy`, `medium` or `hard` (for Classic games, the tier the player chose; since 2026-11-04 warm-up deals too, before they were `easy`); `impossible` or `unrated` for a numbered deal outside the lists. `deal_pool` (since 2026-10-30, on `game_start`, `game_win` and `game_abandon` of Classic and Daily games): the pool the deal came from, `first_games` (warm-up), `very_easy`, `easy`, `medium` or `hard`, to compare how players do with each mix. Picking a tier in the New Game menu sends `setting_changed` with `setting` `difficulty_tier` and the tier as `value`.

`rivals_opened` (v1.3): the Daily Rivals screen opens; `source` is `daily` (Daily Challenge screen) or `victory` (Daily victory panel). Rivals are computed on the phone; no ranking data is sent.

`shield_bought` and `shield_used` were sent by v1.2 builds only: streak shields were removed on 2026-10-29.
