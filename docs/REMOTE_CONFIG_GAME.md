# Remote Config: game settings (v1.1)

All parameters are of type JSON. Each has a bundled default in `app/src/main/assets/<key>_defaults.json`; a missing or invalid value falls back to it. `RemoteJsonConfig` delivers the values once per launch (the ones downloaded during the previous launch), so a change applies on the next launch, never in the middle of a game. `GameConfig` validates them and the page reads the result once per launch through `AndroidBridge.getGameConfig()`, then validates again with the same bounds (`FreeCellCore.normalizeConfig`).

A number outside its range is clamped. A value of the wrong type (text instead of a number, for example) uses the default.

## `progression_config`

```json
{"unlock_levels":{"daily_challenge":2,"missions":3}}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `unlock_levels.daily_challenge` | 1–100 | 2 | Level at which the Daily Challenge opens |
| `unlock_levels.missions` | 1–100 | 3 | Level at which Missions open (Part 2) |

## `daily_challenge_config`

```json
{"history_days":400}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `history_days` | 31–1000 | 400 | Days of Daily history kept |

Owner decision (2026-10-29): no forgiveness day and no streak shields; a missed day ends the streak. Builds before that read `forgiveness_days_per_month`; this build ignores it.

## `economy_config`

```json
{"win_rewards":{"easy":10,"medium":20,"hard":30,"daily":50},
 "bonus_no_undo":10,"bonus_fast":{"seconds":600,"coins":10},
 "daily_streak_bonus_per_day":2,"daily_streak_bonus_cap":20,
 "pay_once_per_deal_seed":true}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `win_rewards.easy` / `medium` / `hard` | 0–1000 | 10 / 20 / 30 | Coins for a win, by the deal's tier (v1.3): Random and Game by Number alike. A numbered deal above 64,000 that is not in the Hard list pays as Medium; an impossible deal pays nothing |
| `win_rewards.daily` | 0–1000 | 50 | Coins for a Daily Challenge win |
| `bonus_no_undo` | 0–1000 | 10 | Extra coins when the win used no Undo |
| `bonus_fast.seconds` | 30–7200 | 600 | A win under this time is "fast" |
| `bonus_fast.coins` | 0–1000 | 10 | Extra coins for a fast win |
| `daily_streak_bonus_per_day` | 0–100 | 2 | Daily wins add this × current streak |
| `daily_streak_bonus_cap` | 0–1000 | 20 | Maximum streak bonus |
| `pay_once_per_deal_seed` | true/false | true | A deal pays only on its first win (Restart and replays pay nothing) |

"Same deal" means: the date for a Daily, and the deal number for every other game (v1.3; before, random games were keyed by the dealt game because their pools were small). A repeated Easy deal therefore pays nothing. The last 300 paid deals are remembered.

The v1.1–v1.2 keys `random`, `winnable` and `numbered` are ignored since v1.3: a console value that still has them gives Easy 10, Medium 20 and Hard 30, and keeps its `daily`.

## `themes_config`

```json
{"prices":{"neon":300,"moonstone":500,"emberwood":800},"free":["classic","aurora","sunlight"]}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `prices.<theme>` | 0–100000 | 300 / 500 / 800 | Price in coins of a locked theme |
| `free` | theme ids | Classic, Aurora, Bordeaux | Themes that never need buying; Classic is always free |

Theme ids are fixed: `classic`, `aurora`, `sunlight` (shown as Bordeaux), `neon`, `moonstone`, `emberwood`.

## `daily_bonus_config`

```json
{"enabled":true,"cycle_rewards":[20,20,30,30,40,40,100],"reset_on_miss":true}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true/false | true | Turns the Daily Bonus on or off |
| `cycle_rewards` | 1–14 numbers, 0–10000 each | 20, 20, 30, 30, 40, 40, 100 | Coins for each day of the cycle; the last day is shown as the big reward. Any other list uses the default |
| `reset_on_miss` | true/false | true | After a missed day the cycle starts again at day 1 |

## `missions_config`

```json
{"enabled":true,"all_complete_bonus":40,"refresh_hours":12,"definitions":[{"id":"win_1","type":"win_games","target":1,"reward":20},{"id":"win_3","type":"win_games","target":3,"reward":45},{"id":"no_undo_1","type":"win_no_undo","target":1,"reward":30},{"id":"no_undo_2","type":"win_no_undo","target":2,"reward":55},{"id":"no_hint_1","type":"win_no_hint","target":1,"reward":25},{"id":"fast_10","type":"win_fast","target":1,"seconds":600,"reward":30},{"id":"fast_6","type":"win_fast","target":1,"seconds":360,"reward":45},{"id":"moves_120","type":"win_under_moves","target":1,"moves":120,"reward":35},{"id":"daily_1","type":"win_daily","target":1,"reward":30},{"id":"random_2","type":"win_random","target":2,"reward":35},{"id":"winnable_2","type":"win_winnable","target":2,"reward":20},{"id":"numbered_1","type":"win_numbered","target":1,"reward":30},{"id":"cards_30","type":"foundation_cards","target":30,"reward":20},{"id":"cards_80","type":"foundation_cards","target":80,"reward":40},{"id":"suits_4","type":"complete_suits","target":4,"reward":25},{"id":"suits_8","type":"complete_suits","target":8,"reward":40},{"id":"columns_3","type":"empty_columns","target":3,"reward":25},{"id":"play_3","type":"play_games","target":3,"reward":15},{"id":"play_5","type":"play_games","target":5,"reward":25}]}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true/false | true | Turns Missions on or off (the button disappears) |
| `all_complete_bonus` | 0–10000 | 40 | Extra coins once all three missions of a set are collected (credited automatically when the next set arrives, if left uncollected) |
| `refresh_hours` | 0–168 | 12 | Hours between collecting the third mission of a set and the next set |
| `definitions[].id` | a–z, 0–9, `_` | | Unique name of the mission |
| `definitions[].type` | see below | | What it counts |
| `definitions[].target` | 1–1000 | | Amount to reach |
| `definitions[].reward` | 0–10000 | | Coins |
| `definitions[].seconds` | 30–7200 | 600 | Time limit of `win_fast` |
| `definitions[].moves` | 20–1000 | 120 | Move limit of `win_under_moves` |

Types: `win_games`, `win_no_undo`, `win_no_hint`, `win_fast`, `win_under_moves`, `win_daily`, `win_random`, `win_winnable`, `win_numbered`, `foundation_cards`, `complete_suits`, `empty_columns`, `play_games`.

A set has three missions of different types, drawn at random (the next set avoids the previous set's types when it can; `win_daily` is left out while the Daily Challenge is locked). A set stays until all three are collected, so missions never change before the player finishes them; the next set comes `refresh_hours` later. A list with entries of fewer than three types is ignored and the bundled list is used. Anti-farming: `foundation_cards` and `complete_suits` count each card or suit once per deal, `empty_columns` each column once per deal, and `play_games` counts new deals, not restarts.

Default missions:

| Id | Type | Target | Coins | Limit |
| --- | --- | --- | --- | --- |
| `win_1` | `win_games` | 1 | 20 | |
| `win_3` | `win_games` | 3 | 45 | |
| `no_undo_1` | `win_no_undo` | 1 | 30 | |
| `no_undo_2` | `win_no_undo` | 2 | 55 | |
| `no_hint_1` | `win_no_hint` | 1 | 25 | |
| `fast_10` | `win_fast` | 1 | 30 | 600 s |
| `fast_6` | `win_fast` | 1 | 45 | 360 s |
| `moves_120` | `win_under_moves` | 1 | 35 | 120 moves |
| `daily_1` | `win_daily` | 1 | 30 | |
| `random_2` | `win_random` | 2 | 35 | |
| `winnable_2` | `win_winnable` | 2 | 20 | |
| `numbered_1` | `win_numbered` | 1 | 30 | |
| `cards_30` | `foundation_cards` | 30 | 20 | |
| `cards_80` | `foundation_cards` | 80 | 40 | |
| `suits_4` | `complete_suits` | 4 | 25 | |
| `suits_8` | `complete_suits` | 8 | 40 | |
| `columns_3` | `empty_columns` | 3 | 25 | |
| `play_3` | `play_games` | 3 | 15 | |
| `play_5` | `play_games` | 5 | 25 | |

## `remove_ads_config`

```json
{"enabled":true,"interval_games":6,"min_games_played":4,"min_session":2,"cooldown_hours":12}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true/false | true | Whether the Remove Ads screen may show by itself between games (the Settings card is always there) |
| `interval_games` | 1–1000 | 6 | New games since it last showed |
| `min_games_played` | 0–10000 | 4 | Games started in total before it can show |
| `min_session` | 2–10000 | 2 | App launch number from which it can show (never the first) |
| `cooldown_hours` | 0–8760 | 12 | Hours since it last showed |

## `rating_config`

```json
{"enabled":true,"interval_games":10,"min_wins":3,"min_session":2,"cooldown_days":45,"max_prompts":3}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true/false | true | Turns the rating card on or off |
| `interval_games` | 1–1000 | 10 | New games since it last showed |
| `min_wins` | 0–10000 | 3 | Wins in total before it can show |
| `min_session` | 2–10000 | 2 | App launch number from which it can show (never the first) |
| `cooldown_days` | 1–3650 | 45 | Days since it last showed |
| `max_prompts` | 0–100 | 3 | Times it may show in total |

Both popups only appear in the transition after a win (victory → level-up → menu stage → at most one popup → next game), the rating card first. A popup that loses a transition keeps its count and is due at the next one. No popup in a transition with a level-up or the Missions celebration, during the first-run tutorial or the consent form, or in the first session. When one shows, the next game starts without an interstitial.

## `store_config` (v1.2)

```json
{"categories":["card_faces","card_backs","backgrounds","victory_effects"],"items":[{"id":"classic","cat":"card_faces","price":0},{"id":"aurora","cat":"card_faces","price":0},{"id":"sunlight","cat":"card_faces","price":0},{"id":"neon","cat":"card_faces","price":300},{"id":"bold","cat":"card_faces","price":400},{"id":"moonstone","cat":"card_faces","price":500},{"id":"ivory_gold","cat":"card_faces","price":600},{"id":"emberwood","cat":"card_faces","price":800},{"id":"parchment","cat":"card_faces","price":800},{"id":"slate_night","cat":"card_faces","price":1200},{"id":"classic_back","cat":"card_backs","price":0},{"id":"navy_lattice","cat":"card_backs","price":250},{"id":"emerald_weave","cat":"card_backs","price":350},{"id":"checker","cat":"card_backs","price":450},{"id":"sunburst","cat":"card_backs","price":600},{"id":"night_sky","cat":"card_backs","price":800},{"id":"classic","cat":"backgrounds","price":0},{"id":"aurora","cat":"backgrounds","price":0},{"id":"sunlight","cat":"backgrounds","price":0},{"id":"neon","cat":"backgrounds","price":300},{"id":"linen","cat":"backgrounds","price":300},{"id":"fine_stripes","cat":"backgrounds","price":400},{"id":"moonstone","cat":"backgrounds","price":500},{"id":"slate","cat":"backgrounds","price":500},{"id":"forest_dusk","cat":"backgrounds","price":600},{"id":"midnight","cat":"backgrounds","price":700},{"id":"emberwood","cat":"backgrounds","price":800},{"id":"royal_velvet","cat":"backgrounds","price":800},{"id":"burgundy_velvet","cat":"backgrounds","price":900},{"id":"oak","cat":"backgrounds","price":1200},{"id":"classic_confetti","cat":"victory_effects","price":0},{"id":"gold_rain","cat":"victory_effects","price":300},{"id":"aurora_ribbons","cat":"victory_effects","price":400},{"id":"suit_shower","cat":"victory_effects","price":500},{"id":"winter_back","cat":"card_backs","price":0,"event":true},{"id":"winter_table","cat":"backgrounds","price":0,"event":true},{"id":"winter_confetti","cat":"victory_effects","price":0,"event":true}]}
```

Replaces the prices of `themes_config`, which is no longer used by the store. Card faces, card backs, backgrounds and victory effects are four separate catalogs: buying a card face no longer unlocks the matching table.

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `items[].id` | an item the app can draw | | Which item is sold |
| `items[].cat` | `card_faces`, `card_backs`, `backgrounds`, `victory_effects` | | Its catalog |
| `items[].price` | 0–100000 | | Price in coins; 0 means free |
| `items[].event` | true/false | false | Event reward: shown with an Event tag and never sold |

Streak shields were removed (owner decision 2026-10-29); a `shield` object left in the parameter is ignored.

Only items the app can draw are accepted; others are ignored, so new items need an app update first. Items the app can draw today:

- `card_faces`: `classic`, `aurora`, `sunlight`, `neon`, `moonstone`, `emberwood`, `bold`, `ivory_gold`, `parchment`, `slate_night`
- `card_backs`: `classic_back`, `navy_lattice`, `emerald_weave`, `checker`, `sunburst`, `night_sky`, `winter_back` (event)
- `backgrounds`: `classic`, `aurora`, `sunlight`, `neon`, `moonstone`, `emberwood`, `linen`, `fine_stripes`, `slate`, `forest_dusk`, `midnight`, `royal_velvet`, `burgundy_velvet`, `oak`, `winter_table` (event)
- `victory_effects`: `classic_confetti`, `gold_rain`, `aurora_ribbons`, `suit_shower`, `winter_confetti` (event)

Each catalog's default (`classic`, `classic_back`, `classic`, `classic_confetti`) is always listed and free. An item left out of `items` disappears from the store, but players who own it keep it.

## `events_config` (v1.2)

Seasonal events. Bundled default:

```json
{"events":[{"id":"winter_2026","enabled":false,"start":"2026-12-01","end":"2027-01-06","goal":{"type":"play_days","target":10},"reward_items":["winter_back","winter_table","winter_confetti"]}]}
```

| Field | Rule | Default | Meaning |
| --- | --- | --- | --- |
| `events[].id` | `a-z`, `0-9`, `_`, up to 40 characters, unique | `winter_2026` | Event id (also the analytics `source`) |
| `events[].enabled` | true / false | false | Master switch for this event |
| `events[].start`, `end` | `YYYY-MM-DD`, start not after end | 2026-12-01 to 2027-01-06 | Days the event runs, both included, in the player's local date |
| `events[].goal.type` | only `play_days` | `play_days` | Win at least one game (any mode) on different days |
| `events[].goal.target` | 1–366 | 10 | Days needed |
| `events[].reward_items` | up to 6 store item ids | `winter_back`, `winter_table`, `winter_confetti` | Items given when the goal is reached |

At most ten events are kept; an invalid event is dropped whole. While an event runs, the New Game button carries a small snowflake badge and the New Game menu shows a banner (name, days left, progress, rewards). The first time the player opens the game during the event, a notice announces it (once per event). Reaching the goal adds the reward items to the player's collection and shows a celebration before the next game. Reward items marked `event: true` in `store_config` are never sold; the store shows them with an Event tag. An id that is not in the store catalog is ignored, so a reward needs its store item first.

To run the Winter Festival: set `enabled` to true before 1 December 2026 (or change the dates) and publish.

## `rewarded_config` (v1.2)

The voluntary "double your coins" video. Bundled default:

```json
{"enabled":true,"placements":{"victory_double":true,"daily_bonus_double":true},"max_per_day":6,"min_games_played":2}
```

| Field | Range | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | true / false | true | Master switch for the rewarded video |
| `placements.victory_double` | true / false | true | "Double coins" on the victory panel, after the coin count-up (extra = that win's coins) |
| `placements.daily_bonus_double` | true / false | true | "Double coins" on the Daily Bonus screen, under Collect (extra = that day's bonus) |
| `max_per_day` | 0–100 | 6 | Rewards credited per day (0 turns the video off) |
| `min_games_played` | 0–10000 | 2 | Games started before the first offer |

Never shown during the first-run tutorial or before consent is answered. With no video loaded, the button shows disabled with "Not available right now". The coins are credited only when the video reports the reward (closing early pays nothing) and at most once per offer: each tap is an offer with its own id, and the Android side keeps an earned id until the page has saved the coins, so a crash in between is repaired at the next launch without paying twice. The ad unit comes from `ads_config.ad_unit_ids.rewarded` (see `docs/REMOTE_CONFIG_ADS.md`). With Remove Ads bought, this video still works.

## `notifications_config` (v1.2)

Daily Challenge reminders (local notifications, no server). Bundled default:

```json
{"enabled":true,"ask_after_daily_wins":1,"default_hour":19,"streak_risk_hour":20,"min_streak_for_risk":3}
```

| Field | Range | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | true / false | true | Offer reminders at all (off: no question, no setting, nothing scheduled) |
| `ask_after_daily_wins` | 1–100 | 1 | Daily wins before the game first asks |
| `default_hour` | 0–23 | 19 | Reminder hour until the player picks another in Settings |
| `streak_risk_hour` | 0–23 | 20 | Hour of the streak-at-risk message (never before the player's own hour) |
| `min_streak_for_risk` | 1–365 | 3 | Streak length that turns the day's reminder into the streak-at-risk message |

The game asks only after a Daily win, with its own card first ("Remind me about the Daily Challenge?"); Android's permission dialog (Android 13+) comes only after "Yes". Each card counts as asked; the next waits 30 days and there are two at most; if Android refuses, the game never asks by itself again (Settings can still turn reminders on, or open Android's notification settings). At most one notification a day: none once that day's Daily is won; the streak-at-risk message when the streak is at least `min_streak_for_risk` and was won yesterday; otherwise "Today's Daily Challenge is ready". Planned by WorkManager (no exact alarms); texts follow the app language.

## `stars_config` (v1.2)

Stars per win (1 to 3). Bundled default:

```json
{"two_star_max_undos":2,"three_star_max_undos":0,"three_star_seconds":480,"three_star_moves":140,"star_bonus_coins":{"two":5,"three":10}}
```

| Field | Range | Default | Meaning |
| --- | --- | --- | --- |
| `two_star_max_undos` | 0–1000 | 2 | Most Undos for 2 stars |
| `three_star_max_undos` | 0–`two_star_max_undos` | 0 | Most Undos for 3 stars |
| `three_star_seconds` | 30–7200 | 480 | Longest time for 3 stars |
| `three_star_moves` | 52–10000 | 140 | Most moves for 3 stars |
| `star_bonus_coins.two` | 0–10000 | 5 | Coins the first time a deal reaches 2 stars |
| `star_bonus_coins.three` | 0–10000 | 10 | Coins the first time a deal reaches 3 stars (from 2 stars, only the difference) |

Any win gives 1 star. The best stars per deal are kept (keyed like coin payments: the date for a Daily, the number for numbered and Winnable deals, the dealt game for random deals; the 5,000 most recent). Developer quick wins give no stars. Note: deals differ in difficulty and the limits are the same for all; a par per deal from a solver would be a future improvement.

## `achievements_config` (v1.2)

Achievements with coins on Collect (like missions). Bundled default (19 achievements):

```json
{"enabled":true,"items":[{"id":"first_win","counter":"wins","target":1,"reward":10},{"id":"wins_10","counter":"wins","target":10,"reward":30},{"id":"wins_50","counter":"wins","target":50,"reward":80},{"id":"wins_100","counter":"wins","target":100,"reward":150},{"id":"no_undo_1","counter":"no_undo_wins","target":1,"reward":15},{"id":"no_undo_10","counter":"no_undo_wins","target":10,"reward":50},{"id":"no_undo_25","counter":"no_undo_wins","target":25,"reward":100},{"id":"streak_3","counter":"daily_streak","target":3,"reward":30},{"id":"streak_7","counter":"daily_streak","target":7,"reward":70},{"id":"streak_30","counter":"daily_streak","target":30,"reward":250},{"id":"daily_10","counter":"daily_wins","target":10,"reward":60},{"id":"daily_50","counter":"daily_wins","target":50,"reward":200},{"id":"numbered_10","counter":"numbered_deals","target":10,"reward":40},{"id":"numbered_50","counter":"numbered_deals","target":50,"reward":120},{"id":"numbered_100","counter":"numbered_deals","target":100,"reward":200},{"id":"stars_10","counter":"three_star_wins","target":10,"reward":50},{"id":"stars_50","counter":"three_star_wins","target":50,"reward":150},{"id":"fast_5","counter":"fast_win","target":300,"reward":40},{"id":"first_item","counter":"items_bought","target":1,"reward":20}]}
```

| Field | Rule | Meaning |
| --- | --- | --- |
| `enabled` | true / false | Off: no unlocks, no trophy button, no Settings entry |
| `items[].id` | `a-z`, `0-9`, `_`, unique | Achievement id (also the analytics `source`) |
| `items[].counter` | `wins`, `no_undo_wins`, `three_star_wins`, `daily_streak`, `daily_wins`, `numbered_deals`, `items_bought`, `fast_win` | What it counts |
| `items[].target` | 1–100000 | Count to reach; for `fast_win`, the time in seconds to win under |
| `items[].reward` | 0–10000 | Coins on Collect |

At most 60 items; an empty or unusable list keeps the bundled one. The name shown is built from the counter and target in the player's language, so new tiers need no new texts. Counters only grow (`fast_win` keeps the fastest win). Each achievement unlocks once and is collected once; unlock notices are small toasts shown at the next menu stage. Older saves start with their wins and 3-star wins counted.

## `difficulty_config` (v1.3, Spider)

Tiers are the number of suits: `easy` 1 Suit, `medium` 2 Suits, `hard` 4 Suits (`docs/DEAL_CATALOG.md`). Bundled default:

```json
{"enabled_tiers":["easy","medium","hard"],"default_tier":"easy","daily_tiers_by_weekday":["medium","easy","easy","easy","easy","easy","medium"],"unlock_levels":{"easy":1,"medium":1,"hard":1},"first_games":{"enabled":true,"ignore_selected_mode":true,"advance_on":"win","seeds":[8340354,1181284,4553912,8989938,8458239]}}
```

| Field | Rule | Meaning |
| --- | --- | --- |
| `enabled_tiers` | any of `easy`, `medium`, `hard` | Tiers offered for Classic games; none valid → all three. With one tier the picker is hidden |
| `default_tier` | an enabled tier (default `easy`) | Tier of Classic games until the player picks one (also the victory "New Game") |
| `daily_tiers_by_weekday` | 7 tiers, Sunday first | Daily tier per weekday (default 2 Suits on Sunday and Saturday, 1 Suit otherwise); each wrong or missing entry falls back on its own default |
| `unlock_levels.<tier>` | 1–100 | Player level that unlocks the tier; a locked tier shows a lock |
| `first_games.enabled` | true / false | Off: no warm-up deals |
| `first_games.seeds` | 1–10 different deal numbers 1–9,999,999 (else the default five) | Warm-up deals, in this order: a new player's first Classic games, dealt in the chosen tier (the defaults are 1 Suit deals from the ranked list: an easy one, a harder one, then three easy ones) |
| `first_games.advance_on` | `win` (default) or `start` | `win`: the next warm-up deal comes only after the current one is won; `start`: each Classic game started moves on |
| `first_games.ignore_selected_mode` | true (default) / false | false: warm-up deals are dealt only while 1 Suit is the chosen tier |

Classic games draw a deal of their tier from `assets/deal_catalog.json` (deals the solver wins; any number for 4 Suits), never one of the tier's last 50. The Daily takes its weekday's tier and a deal of that tier's frozen Daily list chosen from the date, so everyone gets the same deal; it never deals a warm-up deal. FreeCell's `easy_split_index` and `mixes` are ignored.
## `rivals_config` (v1.3)

Daily Rivals, invented rivals computed on the phone (`docs/DAILY_RIVALS.md`). Bundled default:

```json
{"enabled":true,"rivals_shown":30,"median_seconds":420,"sigma":0.35,"min_seconds":90,"tier_multipliers":{"easy":0.7,"medium":1,"hard":1.4},"rewards":{"enabled":false}}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true / false | true | Off: no Daily Rivals button, chip or screen |
| `rivals_shown` | 5–60 | 30 | Rivals in today's table |
| `median_seconds` | 60–3600 | 420 | Typical rival time on a Medium Daily |
| `sigma` | 0.05–1.5 | 0.35 | Spread of rival times (log-normal) |
| `min_seconds` | 10–600 | 90 | No rival is faster than this |
| `tier_multipliers.<tier>` | 0.3–3 | 0.7 / 1 / 1.4 | Rival times on Easy, Medium and Hard Dailies |
| `rewards.enabled` | true / false | false | Kept for a later version; nothing uses it yet |

Changing a value changes today's table for everyone at the next launch.

`achievements_config` gained two items with the new counter `rivals_top3` (days in the top 3 of Daily Rivals): `rivals_top3_1` (1) and `rivals_top3_10` (10), with reward 0. An achievement with reward 0 shows Collect without coins.

## `hint_config` (v1.4)

The Hint's solver (`docs/ROADMAP_V1_4.md`, Part 1). Bundled default:

```json
{"smart_hint":true,"time_budget_ms":300,"node_limit":400000,"background_analysis":true,"background_budget_ms":150,"max_hints_per_game":null}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `smart_hint` | true / false | true | Off: the hint as before v1.4 (search on the main thread, no line, no background analysis) |
| `time_budget_ms` | 50–3000 | 300 | Search time for a Hint tap that has no answer ready |
| `node_limit` | 1000–2,000,000 | 400000 | Positions a search may expand, whichever limit comes first |
| `background_analysis` | true / false | true | Analyse each new position after a move, so the hint is ready |
| `background_budget_ms` | 0–1000 | 150 | Search time of that analysis (0: none) |
| `max_hints_per_game` | null or 0–1000 | null | Cap on hints asked for in a game ("No hints left in this game"); null: no cap. Automatic hints stop at the cap too |

A hint is the next move of a verified winning line; the player who follows it gets each next move
without a new search. When the budget runs out the hint is the best guess and nothing is claimed;
"There is no way to win from here" appears only after an exhaustive search. Benchmark:
`docs/SOLVER_BENCHMARK.md`.

## `tips_config` (v1.4)

```json
{"enabled":true,"free_cells_tip_min_occupied":3}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `enabled` | true / false | true | Off: no tips |
| `free_cells_tip_min_occupied` | 1–4 | 3 | The first time the player is stuck with at least this many free cells full, the message adds "Tip: free cells are only for temporary storage. Try to keep them empty." with Got it (once per install) |

## `feel_config` (v1.4)

```json
{"sounds":true,"haptics":true,"sound_volume":0.6,"suit_complete_effect":true}
```

| Field | Range | Default | Effect |
| --- | --- | --- | --- |
| `sounds` | true / false | true | Off: no sounds for anyone (the Sound setting still lets a player turn them off) |
| `haptics` | true / false | true | Off: no vibration for anyone (the Vibration setting still lets a player turn it off) |
| `sound_volume` | 0–1 | 0.6 | Master volume of every sound; 0.6 is the loudness before v1.4 |
| `suit_complete_effect` | true / false | true | The golden ring on a foundation when its suit is complete |

## `languages_config` (v1.4)

```json
{"enabled":["en","pt","pt-BR","es","fr","it","de","nl","pl","tr","ru","uk","ja","ko","zh-Hans","id","sv","cs"],"beta":[]}
```

| Field | Rule | Effect |
| --- | --- | --- |
| `enabled` | language codes from the list above | Languages offered in the selector (English always); a player whose language is switched off gets the next match from the device, else English |
| `beta` | enabled codes | Marked "Beta" in the selector (for languages not reviewed yet) |

## `performance_config` (v1.4)

```json
{"auto_reduce_effects":true,"low_end_memory_class_mb":128,"old_android_max_sdk":27,"fps_sample_seconds":4,"low_fps_threshold":40,"low_end_total_ram_mb":3072}
```

| Field | Rule | Effect |
| --- | --- | --- |
| `auto_reduce_effects` | boolean | With Settings → Reduce effects on Auto (the default), reduced effects for weak phones; false: full effects unless the player picks On |
| `low_end_memory_class_mb` | 0–4096 | A phone whose app memory class (Android's `getMemoryClass`) is below this is weak; 0 switches this check off |
| `old_android_max_sdk` | 0–40 | An Android version (API level) up to this one is weak; 27 = Android 8.1; 0 switches this check off |
| `fps_sample_seconds` | 1–15 | How long the first deal after install is watched |
| `low_fps_threshold` | 10–60 | Below this many frames per second in that sample, or as the middle value of the last 5 card moves during play (2026-11-05), effects are reduced from then on |
| `low_end_total_ram_mb` | 0–65536 | A phone with at most this much RAM in all (Android's `MemoryInfo.totalMem`; a 3 GB phone reports about 2.8 GB) is weak; 0 switches this check off. Added 2026-11-05 |

Android's low-RAM flag always counts as weak under Auto. The sample is taken once per install and kept
on the phone (not in the backup); a sample cut by the app going to the background is retried at the next deal.

## Owner task

Create the parameters in the Firebase console (project `freecell---nexus`) with the JSON defaults above, then publish. Until then the app uses the bundled defaults.
