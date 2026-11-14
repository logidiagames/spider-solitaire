# FreeCell v1.1 — Briefs for Claude Code

Save this file as `docs/ROADMAP_V1_1.md` in the repository. Work through the three parts **in order, one at a time**. After each part: build, run tests, give the owner a short summary in plain Portuguese plus what to test on a phone, and **wait for his approval before starting the next part**.

## Global rules (apply to all parts)

- The game is `app/src/main/assets/freecell.html` in a WebView, with the Android shell in Java. There is no Wise Owl, no Wheel and no Missions system today. Do not change game rules, the move animation flow (temporary clones), the hint logic, or ad cadence, unless a task below says so.
- All user-visible strings must exist in the 6 languages already supported (EN, PT, ES, FR, IT, DE). Write the translations yourself and list them for the owner to review.
- Every new number (prices, rewards, intervals, unlock levels) comes from Remote Config with bundled defaults, parsed with bounds and a safe fallback, exactly like `ads_config`. Do not duplicate the fetch code: create one small shared helper for "JSON Remote Config parameter + bundled default + validation" and reuse it. Values apply on the next launch, never mid-game.
- **One source of truth for time.** Create a single `today()` helper (local calendar date, YYYY-MM-DD). Store the latest date ever seen. If the device clock goes backwards, keep using the stored latest date. Use it for the Daily history, streak, forgiveness day, daily bonus and missions.
- **One saved-state migration.** Bump the saved-state schema from 1 to 2 once, covering everything in all three parts. A schema 1 state must still open without losing level, stats, theme or language.
- Native code is the source of truth for purchases and ad state. JavaScript must never be trusted to grant "ads removed".
- Keep economy data local (no server). Accept that a rooted device can edit it.
- Analytics: only add events through the existing allowlist, no personal data, and document each one in `docs/ANALYTICS_EVENTS.md`.
- Add a **debug-only** tools panel (hidden in release builds): set level, add or remove coins, shift "today" by N days, reset missions, daily bonus and popup counters. It must be fully unreachable in release.
- Extract new logic into pure functions and unit-test them. Run `./gradlew test lintDebug assembleDebug` after every task. Small commits, English messages.
- Do not change versionCode/versionName, do not generate an AAB, do not request new Android permissions (except what a task explicitly requires), and update `CLAUDE.md` at the end of each part.
- Anything that needs the owner (Firebase, Play Console, AdMob) goes in the summary as an explicit "owner task". Do not try to do it.

## Remote Config parameters introduced (all type JSON, bundled defaults in `app/src/main/assets/`)

```json
// progression_config
{"unlock_levels":{"daily_challenge":2,"missions":3}}

// daily_challenge_config
{"forgiveness_days_per_month":1,"history_days":400}

// economy_config
{"win_rewards":{"random":20,"winnable":5,"numbered":10,"daily":50},
 "bonus_no_undo":10,
 "bonus_fast":{"seconds":600,"coins":10},
 "daily_streak_bonus_per_day":2,"daily_streak_bonus_cap":20,
 "pay_once_per_deal_seed":true}

// themes_config
{"prices":{"neon":300,"moonstone":500,"emberwood":800},"free":["classic","aurora","sunlight"]}

// daily_bonus_config
{"enabled":true,"cycle_rewards":[20,20,30,30,40,40,100],"reset_on_miss":true}

// missions_config
{"enabled":true,"all_complete_bonus":40,
 "definitions":[
  {"id":"win_games","target":1,"reward":20},
  {"id":"win_no_undo","target":1,"reward":30},
  {"id":"win_fast","target":1,"seconds":600,"reward":30},
  {"id":"foundation_cards","target":30,"reward":25},
  {"id":"win_daily","target":1,"reward":30},
  {"id":"play_games","target":3,"reward":15}]}

// remove_ads_config
{"enabled":true,"interval_games":6,"min_games_played":4,"min_session":2,"cooldown_hours":12}

// rating_config
{"enabled":true,"interval_games":10,"min_wins":3,"min_session":2,"cooldown_days":45,"max_prompts":3}
```

Theme IDs are the persisted ones: `classic`, `aurora`, `sunlight` (shown as Bordeaux), `neon`, `moonstone`, `emberwood`. Never rename them.

---

# PART 1 — Foundations: economy, Daily history, hint message

## 1.1 Developer Victory
Hide it in release builds. It must not award progress, coins or stats in release. Keep it in the debug tools panel only.

## 1.2 Coins (the only currency)
- Replace the fixed "10" reward on the victory panel with real coins, shown with a coin icon and a small count-up animation. Coins also appear in the New Game menu header.
- Coins per win use `economy_config`: base by mode, plus `bonus_no_undo` if the win used no Undo, plus `bonus_fast` if the time is under the configured seconds. Daily wins also add a streak bonus (`daily_streak_bonus_per_day` × current streak, capped).
- Anti-farming: with `pay_once_per_deal_seed` the same deal seed pays coins only on its first win (Restart and replaying a numbered game pay nothing). Keep the last 300 paid seeds.
- Add "Coins earned" to the recent game log and statistics. Never let the balance go negative.

## 1.3 Themes unlocked with coins
- Replace the current "locked but testable" behaviour. Classic, Aurora and Bordeaux stay free. Neon, Moonstone and Emberwood are locked until bought with coins, at the prices in `themes_config`.
- One purchase unlocks that theme for both card face and table (they share the same ID); the two choices remain independent for equipping.
- Locked theme: show a lock and the price. Tapping opens a preview of the theme and a **Buy** button (disabled with a clear "Not enough coins" state, showing how many are missing). After buying, a short unlock animation and equip option. Confirm before spending.
- A saved state that references a still-locked theme falls back to Classic.
- Persist `unlockedThemes`. Level does not gate themes.

## 1.4 Daily Challenge: history, streak, forgiveness day, best time
The Daily Challenge already exists (one deal per local date, today only). Keep that. Add:
- **Daily Challenge unlocks at level 2** (`progression_config`). Before that, its entry shows a lock and "Level 2". The level-up panel for level 2 states what was unlocked.
- **History:** per local date store won/not won, time, moves, score, attempts. Limit to `history_days`.
- **Streak derived purely from history** (no mutable streak counter), so clock changes cannot corrupt it. Replace the hard-coded 3.
- **Forgiveness day:** once per calendar month (`forgiveness_days_per_month`) a single missed day does not break the streak. Rules: only an isolated single missed day, with a win on the day before and the day after, can be forgiven; two or more missed days in a row break the streak; the forgiven day does not add to the streak count; the month used is the month of the missed day. Show forgiven days in the calendar with a shield icon, and show "Forgiveness day available this month" or "used" in the Daily screen.
- **Best time and comparison:** in the Daily screen and on the Daily victory panel show "Today 3:21 · Yesterday 4:02 (−0:41)". If yesterday was not won, show a dash. Also show the all-time best Daily time and its date.
- **Calendar** for the current month: won, forgiven, today highlighted, future days inactive, previous-month navigation, "X/N days won".
- **Streak milestones** (3, 7, 14, 30): small visual highlight, no reward.
- Pure functions plus tests for: skipped day, forgiven day, two missed days, month boundary, DST change, clock moved back, empty history.

## 1.5 "No more moves" message (hint)
The game currently has no detection for being stuck. Add it:
- **Condition A (certain):** no legal move exists.
- **Condition B (heuristic):** the only moves available would repeat a position already seen in this game (for example shuffling the same card between cells).
- Trigger: when the player taps Hint and A or B is true, and also after a move that leaves the game in that state. Not on a won game, not during animations.
- **Message:** "No more moves possible" (translate to 6 languages). Never claim definitive defeat; add a gentle second line suggesting Undo or New Game.
- **Design:** a non-modal pill, larger text, semi-transparent background (no solid black), horizontally centred, vertically centred between the bottom of the last card and the toolbar buttons, and it must **never touch or overlap** cards or buttons (measure real positions, clamp with at least 10 px margin, and reserve safe space if the gap is small). Enter with a short expand animation; exit with a fade/scale-out. Animate with `opacity` and `scale` only (no transform conflicts with the centring). Auto-hide after about 3 seconds. Does not block play and ignores touches.
- While the message shows, make the **Undo button** do a brief expand-and-shrink pulse to guide the eye.

## 1.6 Tests and done criteria for Part 1
Unit tests for economy rewards, pay-once logic, theme purchase rules, streak/forgiveness, best-time comparison and stuck detection. Manual checklist: win a game and see coins; try to buy a theme with too few coins; buy one and equip it; Daily locked at level 1 and unlocked at level 2; kill and relaunch the app mid-game (state restores); open an old schema 1 state.

---

# PART 2 — Daily Bonus and Missions

## 2.1 Daily Bonus screen (coins only)
- Decision: **coins, not XP.** The level system is based on wins and a second currency would add confusion.
- A full-screen but compact panel shown **once per calendar day**, on the first New Game menu visit after launch. Never during the first-run tutorial, never over an ad or the consent form, never in the middle of a game. It appears after any app-open ad has finished.
- Layout: title, a 7-day row (collected days with a check, today highlighted and slightly enlarged, future days dimmed, day 7 shown as the big reward), the amount for today, a prominent **Collect** button. On collect: coin count-up and a short coin-fly animation to the coin counter.
- Rewards from `daily_bonus_config.cycle_rewards`. After day 7 the cycle restarts. If a day is missed and `reset_on_miss` is true, the cycle returns to day 1 (this is separate from the Daily Challenge forgiveness day).
- Always reachable later from the New Game menu (a small gift button, with a dot when unclaimed).
- Uses the shared `today()` helper (clock-rollback safe). Persist `lastClaimDate` and `cycleDay`.

## 2.2 Missions
Missions are daily goals that pay coins.
- **Unlock at level 3** (`progression_config`). Before that the Missions button shows a lock icon and a darkened state (keep the same icon as unlocked), and tapping it explains "Unlocks at level 3". The level-up panel for level 3 announces it. The unlock celebration appears at the start of the next game's New Game menu, after the level-up panel, never over the victory panel. When it closes, the Missions button does a brief expand-and-shrink pulse to draw attention.
- **Only count progress after unlock.** The win that triggers level 3 does not count towards missions.
- **3 missions per day**, deterministic by local date (same for everyone): one always `win_games`; one from {`win_no_undo`, `win_fast`}; one from {`foundation_cards`, `win_daily` (only if the Daily is unlocked), `play_games`}. Definitions and rewards from `missions_config`.
- Progress is tracked from game events: wins by mode, undo used, win time, cards sent to foundations (counted across games that day), games started, Daily wins.
- UI: a Missions screen with 3 rows. Each row has a clean, minimal state indicator (not started / in progress with progress bar / completed with a check), the reward, and a **Collect** button once complete. A segmented progress bar across the top shows how many of the 3 are collected. When all 3 are collected a bonus (`all_complete_bonus`) can be collected.
- Reset at local midnight; if the app is resumed after midnight, refresh immediately.
- Persist today's missions, progress and claimed flags. Uses `today()`.
- Tests: deterministic selection per date, progress counting only after unlock, no double claim, midnight reset, Daily mission excluded when Daily is locked.

## 2.3 Part 2 done criteria
Daily bonus appears once per day and never in forbidden moments; missions locked at level 2 and working at level 3; claims are idempotent after killing the app; debug tools can shift the date to test a 7-day cycle and a missed day.

---

# PART 3 — Remove Ads (in-app purchase) and App Rater

## 3.1 Remove Ads purchase
- Product: Google Play non-consumable in-app product with ID `remove_ads`. Use the latest stable Google Play Billing Library. Show the **localized price from Billing**, never a hard-coded price.
- Native Java owns the entitlement: query purchases at startup, handle pending purchases, **acknowledge the purchase** (otherwise Google refunds it after 3 days), cache the result locally for offline use, and keep it correct after reinstall via **Restore purchase**.
- When ads are removed: no banner, no interstitial, no app-open ad, and do not initialise or request ads at all. Hide the consent flow only if no ads can be shown.
- Bridge: add the minimum methods (for example get billing state, start purchase, restore purchases) with a callback to JS. Keep the allowlist approach and validate everything on the Java side.
- Debug builds: add a fake billing stub (shows a sample price, simulates success and failure) so the UI can be tested without Play. Real billing can only be tested from a Play testing track.
- Handle: user cancels, offline, pending payment, already owned, purchase failure, billing not available. Show friendly messages.
- Analytics events (allowlisted): screen shown, purchase started, purchase completed, purchase failed, restore.

## 3.2 Settings: visible Remove Ads section
- In Settings add a clearly visible, separate section at the top (distinct card with accent colour, not a plain row): "Remove ads", a one-line benefit, the price, and a **Restore purchase** link. Tapping opens the Remove Ads screen. After purchase it changes to "Ads removed — thank you".
- **Remove Ads screen:** attractive, consistent with the existing ornamental style of the game. Title, a short list of what disappears (banner, full-screen ads, app-open ads), a prominent **Buy** button with the price, **Restore purchase**, and a polite "Maybe later". No fake urgency, no countdowns.
- The same screen can be shown automatically every X games by `remove_ads_config` (see the popup coordinator below). Never when ads are already removed.

## 3.3 App rater
- Use the **Google Play In-App Review API**. Remember it is quota-limited and may show nothing; never assume a review was written and never say "thanks for rating".
- Show a short, attractive branded card **before** invoking the flow, with two equal paths for everyone: "Rate FreeCell" (starts the in-app review) and "Send feedback" (opens the support email). Plus "Not now". Do not ask a sentiment question first, do not route only happy users to the store, and never offer coins or any reward for rating (Google Play policy).
- Eligibility (`rating_config`): enabled, not before `min_session`, at least `min_wins` wins, every `interval_games` games, cooldown of `cooldown_days` after showing, at most `max_prompts` times in total. Only at a positive moment: right after a win (after the victory panel and any level-up panel), never mid-game, never on the first session.
- After "Rate" or "Send feedback", stop asking for the full cooldown.

## 3.4 Popup coordinator (important)
Several things now compete after a game. Create one small coordinator with a fixed priority and these rules:
1. Order after a game ends: victory panel → level-up panel → (at most one of) rating card, then Remove Ads screen → New Game menu.
2. **At most one promotional popup (rating or Remove Ads) per game transition.** An eligible popup that loses waits for the next transition without resetting its counter.
3. **No promo and interstitial in the same transition.** If a promo was shown, suppress the interstitial for the next game start (pass the information to the native ad logic through the existing game-start bridge call).
4. Never show promos during the first-run tutorial, during the consent form, in the first session, or when the game screen is mid-animation.
5. Daily Bonus (Part 2) and the unlock celebrations count as part of the menu stage and are shown before promos, but promos wait a full transition if a celebration was shown in the same stage.
6. Provide debug tools to force each popup and to reset all counters.

## 3.5 Privacy and store updates (list in the summary)
Updating the privacy policy text is on the owner, but list exactly what changed so he can update it: purchases (Google Play Billing), Firebase events added, and Review API usage. Also give the owner the data for the Play Console Data safety form and the in-app purchases declaration.

## 3.6 Owner tasks for Part 3 (Claude Code must not attempt these)
- Play Console: complete the **payments profile** (merchant account, tax and bank details); create the in-app product `remove_ads` as non-consumable, set the price, and activate it.
- Upload a build to a testing track (Internal testing) and add **license testers** to test the purchase.
- Create the Remote Config parameters (`remove_ads_config`, `rating_config`, and the others from Part 1 and 2) in Firebase and publish.
- Update the privacy policy page and the Data safety form.

## 3.7 Part 3 done criteria
Remove Ads purchase works end to end in a Play testing track; ads stop and stay off after restart and after reinstall + restore; Settings section is clearly visible; the rater card appears only at allowed moments and never together with another promo or an interstitial; every parameter changes behaviour when edited in Remote Config; all strings are translated.
