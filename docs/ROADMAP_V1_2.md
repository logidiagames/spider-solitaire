# FreeCell v1.2 — Briefs for Claude Code

Save this file as `docs/ROADMAP_V1_2.md`. Work through the three parts **in order, one task at a time**. After each task: build, run tests, give the owner a short summary in plain Portuguese plus what to test on a phone, and **wait for his approval before the next part**.

This builds on v1.1 (coins, Daily history, missions, Remove Ads, app rater, popup coordinator). Read the current code and `CLAUDE.md` first and reuse what exists: the shared Remote Config JSON helper, the `today()` time helper, the popup coordinator, the debug tools panel, the pure-function + unit-test approach, and the 6 translations.

## Global rules (same as v1.1, plus these)

- Do not change game rules, hint logic, the move animation flow, or ad cadence unless a task says so.
- Every new string exists in EN, PT, ES, FR, IT, DE. List the translations for the owner to review.
- Every new number comes from Remote Config with bundled defaults, bounds and a safe fallback. Values apply on the next launch.
- **Legibility is a hard rule for any new card face or background.** Rank and suit must be readable at a glance on every background: contrast ratio of at least 4.5:1 between rank/suit and the card, red and black suits distinguishable by more than hue alone, no blurred glow or diffuse shadows on text, and the centre suit must never blend into the card colour. Crisp thin outlines are fine; soft glows are not (the owner rejected a blurry neon look before).
- **Owner approves visuals before they ship.** For every batch of new cosmetics, generate a static preview page `tools/store_preview.html` (opens in a desktop browser, no build needed) showing each item on a sample board with both suit colours. Do not wire an item into the store until the owner approves its preview.
- Keep the APK small: draw backgrounds and backs with CSS gradients and inline SVG, no large images.
- Economy data is local and editable on rooted devices; accept that.
- Do not change versionCode/versionName, do not generate an AAB, and update `CLAUDE.md` at the end of each part. Small commits, English messages.
- Owner tasks (Firebase, AdMob, Play Console, privacy policy) go in your summary as explicit tasks. Do not attempt them.

## New Remote Config parameters (all JSON, bundled defaults in `app/src/main/assets/`)

```json
// store_config  (replaces the price part of themes_config; keep reading themes_config only for migration)
{"categories":["card_faces","card_backs","backgrounds"],
 "shield":{"price":150,"max_per_month":2,"max_held":1},
 "items":[
   {"id":"neon","cat":"card_faces","price":300},
   {"id":"moonstone","cat":"card_faces","price":500},
   {"id":"emberwood","cat":"card_faces","price":800},
   {"id":"neon","cat":"backgrounds","price":300}
 ]}   // Code writes the full default catalog; see Part A

// events_config
{"events":[{"id":"winter_2026","enabled":false,"start":"2026-12-01","end":"2027-01-06",
  "goal":{"type":"play_days","target":10},
  "reward_items":["winter_back","winter_table"]}]}

// rewarded_config
{"enabled":true,"placements":{"victory_double":true,"daily_bonus_double":true},
 "max_per_day":6,"min_games_played":2}

// notifications_config
{"enabled":true,"ask_after_daily_wins":1,"default_hour":19,
 "streak_risk_hour":20,"min_streak_for_risk":3}

// stars_config
{"two_star_max_undos":2,"three_star_max_undos":0,"three_star_seconds":480,"three_star_moves":140,
 "star_bonus_coins":{"two":5,"three":10}}

// achievements_config   (Code writes the full list; see Part C)
{"enabled":true,"items":[{"id":"wins_10","counter":"wins","target":10,"reward":30}]}
```

Also add `rewarded` to the ad unit IDs in `ads_config` (production unit ID only; debug always uses Google's test unit).

---

# PART A — Store, cosmetics and events

## A1. Separate catalogs (card faces, card backs, backgrounds)
Today card and table share the same 6 theme IDs (`classic`, `aurora`, `sunlight` = Bordeaux, `neon`, `moonstone`, `emberwood`) and one purchase unlocks both. Split into three independent catalogs, each with its own equipped item:
- **Card faces** (6 existing + new), **Backgrounds** (6 existing + new), **Card backs** (new).
- Rework the Style screen into three tabs (or a horizontal selector). Locked items show a lock and the price; tapping opens a preview and a Buy button (confirm before spending, "Not enough coins — X missing"). Owned items show "Equip".
- **Migration (critical):** a player who owns theme X must own card face X and background X after the update, with the same equipped choices. Prices and the catalog come from `store_config`; fall back to `themes_config` only to read old data. Never rename the persisted theme IDs.
- Card backs are only visible in a few places: the **deal animation** (cards arrive face down and flip to reveal), the **victory card flight**, and the New Game menu preview. Implement them there. Do not change gameplay (all cards stay face up).

## A2. New items (propose first, build after approval)
- **Backgrounds, about 8 new:** subtle textures, gradients and patterns (for example linen, velvet, deep gradients, fine stripes). Quiet enough that slots, cards and the "no more moves" message stay readable.
- **Card faces, 4 new:** distinct designs, all passing the legibility rule. Do not copy the existing look.
- **Card backs, about 6:** one free default plus patterned designs.
- Suggested price tiers (set the final catalog in `store_config`): card faces 400 to 1500, backgrounds 300 to 1200, card backs 250 to 800. One free default per category.
- Optional, owner decides after seeing the previews: a small "victory effects" catalog (confetti shapes/colours) for the existing victory celebration.

## A3. Streak shield
- Buyable with coins (`store_config.shield`): protects the Daily streak from one extra missed day in a month, after the free monthly forgiveness day has been used. Limits: at most `max_held` shields held, at most `max_per_month` bought per month.
- It is consumed automatically **at the moment the player wins the Daily** after an isolated single missed day that would otherwise break the streak. Record the shield use date in state so the streak (still derived from history) stays deterministic. It cannot rescue a streak that has already ended.
- UI: when yesterday was missed, the free forgiveness is used up and today is not yet won, the Daily screen shows "Your streak is at risk" with a Buy shield button (price). Calendar shows shield days with their own icon. Show shields held.
- Tests: shield consumed once, not consumable twice, month limit, no rescue of an ended streak, clock moved back, month boundary.

## A4. Seasonal events (Christmas first)
- Build a small events framework driven by `events_config` (start/end dates, goal, reward items). Use `today()`. Disabled events never appear.
- A New Game menu banner shows the active event, progress (for example 6/10 days) and the real days left. No fake urgency.
- Goal type `play_days`: number of distinct local dates with at least one win during the event window.
- Reward items are exclusive to the event (not buyable) and appear in the Style tabs with an "Event" tag.
- Build the **Christmas/Winter content**: one card back, one background (subtle snow pattern), optional winter victory confetti. Ship it disabled (`enabled:false`); the owner enables it in Remote Config around 1 December.
- Timeline: this part must be approved and released by about 25 November so review and rollout finish before the event starts. Tell the owner if that is at risk.
- Debug tools: simulate event dates.

## Done criteria for Part A
Previews approved; old saves keep every unlocked theme and equipped choice; each catalog buys and equips correctly; shield rules pass the tests; the event appears only inside its dates and gives the reward at the target.

---

# PART B — Revenue and reach

## B1. Rewarded ad: "double your coins"
- Placements (from `rewarded_config`): the victory panel after the coin count-up, and the Daily Bonus screen at Collect. The extra equals the coins from that source (so total is doubled).
- It is voluntary. Show a clear button "Double coins (watch a short ad)". Credit the extra **only** after the SDK reports the reward earned; closing early gives nothing. Make crediting idempotent (a unique id per offer) so a crash cannot double-credit. Limit with `max_per_day` and `min_games_played`.
- If no ad is ready, hide or disable the button gracefully ("Not available right now"). Preload the ad at a quiet moment (not during animations).
- **Important change to Remove Ads:** a player who bought Remove Ads must keep this voluntary option. Remove Ads still disables banner, interstitial and app-open. The SDK and the consent flow must therefore still run for rewarded ads (consent is still required in the EEA and UK). Adjust the Remove Ads logic accordingly: if rewarded is disabled by Remote Config and ads are removed, then do not initialise ads at all.
- Debug builds always use Google's test rewarded unit. Production unit ID comes from `ads_config`.
- It is user-initiated, so it is not a "promo" for the popup coordinator, but never show the button during the first-run tutorial or before consent is resolved.
- Allowlisted analytics events: offered, started, completed, not available.

## B2. Social sharing (no server)
- **Challenge a friend:** on the victory panel add "Challenge a friend". Opens the Android share sheet with text like "FreeCell deal #12345, solved in 3:21 with 98 moves. Can you beat it?" plus the Play Store link (from `app_content_config.store_url` once it exists; until then the standard Play Store URL for the package). Show the deal number on the victory panel and in the game info. The friend types that number in Numbered mode. Numbered deals accept 0 to 9,999,999.
- **Share Daily result as an image:** generate a square PNG (about 1080 px) in the WebView with the title, date, time, moves, streak and the equipped background. Pass it to Android through a new, narrowly validated bridge method (PNG only, size limit about 2 MB) and share it with a FileProvider from the cache directory. No storage permission. Fall back to text only if image sharing fails.
- No personal data in the image or text. Add allowlisted analytics events.

## B3. Local notifications
- Android 13 and above needs the `POST_NOTIFICATIONS` runtime permission. Ask **in context only**: after the player's first Daily win (`ask_after_daily_wins`), with a short explanation card first ("Remind me about the daily challenge?"). If denied, do not ask again for a long time (and never nag).
- Use WorkManager (no exact alarms). Two notification types: a daily reminder at `default_hour` ("Today's Daily Challenge is ready") only if the Daily is not won yet that day, and a streak-at-risk reminder at `streak_risk_hour` only if the streak is at least `min_streak_for_risk` and today is not won. At most one notification per day. Cancel today's when the Daily is won.
- Settings: toggle and a time picker. Text in the app's selected language. Notification channel named "Daily reminders".
- Respect the shared `today()` helper. Debug tools: trigger a notification now.

## Done criteria for Part B
Rewarded works with test ads, never double-credits, still works after Remove Ads, and respects consent; share works for text and image; notifications schedule, cancel after a win, and never ask for permission outside the allowed moment.

---

# PART C — Depth

## C1. Stars per game (1 to 3)
- Rules from `stars_config`: 1 star for any win; 2 stars with at most `two_star_max_undos` Undos; 3 stars with zero Undos and time at most `three_star_seconds` and moves at most `three_star_moves`. State in the summary that difficulty varies per deal and a solver-based par is a future improvement.
- Show stars with a short animation on the victory panel. Store the best stars per Daily date and per numbered deal. Coin bonus for 2 and 3 stars (`star_bonus_coins`), paid only the first time that star level is reached for that deal (improving from 2 to 3 pays the difference).
- Feed stars into statistics and achievements.

## C2. Achievements
- About 16 achievements with tiers, coins on **Collect** (same pattern as missions), defined in `achievements_config`. Counters to track: total wins, wins without Undo, 3-star wins, longest Daily streak, Daily wins, numbered deals completed, items bought, fastest win.
- Examples: first win; 10, 50, 100 wins; 1, 10, 25 wins without Undo; Daily streak of 3, 7, 30; 10 and 50 Daily wins; 10, 50, 100 numbered deals; 10 and 50 three-star wins; win in under 5 minutes; buy your first item.
- Achievements screen reachable from the menu or Settings, with progress bars and a dot for uncollected rewards. Unlock notice is a small non-blocking toast, queued, shown at the next menu stage (never mid-move, never competing with promos).
- Tests: counters, tier thresholds, no double collect, state migration.

## C3. Numbered Games collection (replaces "Completed Numbered Games")
- The current entry is not a real count. Replace it with a Collection screen: a paged grid of 100 deals per page (1 to 100, 101 to 200, and so on) showing stars per deal (not played / 1 / 2 / 3), page progress ("37/100"), a "go to number" input, and tap-to-play on any cell.
- Store compactly (seed, stars, best time, best moves). Cap the entries (for example the most recent 5,000) and keep it out of the main saved-state blob if the size gets close to the 500,000-character bridge limit (use a separate storage key with its own limit and migration).
- Note on the screen that some numbered deals may not be winnable (the game already warns about this).
- Tests: compact storage and the size cap, page maths, star upgrades only improve.

## Done criteria for Part C
Stars appear and persist; achievements count correctly and cannot be collected twice; the Collection screen opens fast with thousands of entries; saved state stays under the size limit.

---

## Owner tasks (summarise in your replies, do not attempt)

- AdMob: create a **Rewarded** ad unit named for FreeCell and give the ID for `ads_config`.
- Firebase Remote Config: create `store_config`, `events_config`, `rewarded_config`, `notifications_config`, `stars_config`, `achievements_config` (JSON) after you confirm each is implemented. Enable the Christmas event around 1 December.
- Privacy policy and Data safety: list what changed (rewarded ads, local notifications, share intents) so the owner can update them.
- Approve each preview page before cosmetics ship. Test on a phone.
