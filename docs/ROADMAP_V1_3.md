# FreeCell v1.3 — Briefs for Claude Code (revised)

Save this file as `docs/ROADMAP_V1_3.md` (replace the earlier version). Work through the parts **in order, one at a time**. After each part: build, run tests, give the owner a short summary in plain Portuguese plus what to test on a phone, and **wait for his approval before the next part**.

This builds on v1.1 and v1.2. Read the current code and `CLAUDE.md` first and reuse what exists: the shared Remote Config JSON helper, the `today()` time helper, the popup coordinator, the debug tools panel, the pure-function + unit-test approach, and the 6 translations.

## Global rules

- Do not change game rules, the move animation flow (temporary clones), the economy numbers, or ad cadence unless a task says so.
- Every new string exists in EN, PT, ES, FR, IT, DE. List the translations for the owner to review.
- Every new number or switch comes from Remote Config with bundled defaults, bounds and a safe fallback. Values apply on the next launch.
- Debug builds must never send data to production services (no crash reports).
- Do not change versionCode/versionName, do not generate an AAB, do not add Android permissions. Update `CLAUDE.md` at the end of each part. Small commits, English messages.
- Anything that needs the owner (Firebase, Play Console, AdMob, privacy policy) goes in your summary as an explicit "owner task". Do not attempt it.

---

# PART 0 — Crashlytics (small)

1. Add Firebase Crashlytics (Gradle plugin and dependency, versions through the Firebase BoM already in use). Apply the plugin only when `google-services.json` exists, like the other Firebase plugin.
2. Release builds upload the R8 mapping file automatically; verify readable stack traces are possible.
3. Collection disabled in debug builds. In release, follow the same consent behaviour as Firebase Analytics (if Analytics waits for consent, so does Crashlytics; if not, collect by default). Document the choice in `CLAUDE.md`.
4. Report JavaScript errors from the WebView as **non-fatal** events: `window.onerror` and unhandled promise rejections call a new narrowly validated bridge method. Cap message length at about 300 characters, strip anything that looks like saved-state content, and rate-limit to a few per session. No personal data.
5. Add custom keys: app version, game mode, difficulty tier (once it exists), saved-state schema version.
6. Debug tools: a "Force test crash" button, hidden in release.
7. Tell the owner exactly what he must do: build a release-like APK, trigger the test crash, reopen the app, and then click "Enable Crashlytics" in the Firebase console when it offers it.
8. Privacy: list what changed for the privacy policy and the Data safety form (crash logs and diagnostics).

---

# PART 1 — Quick wins

## 1.1 Automatic backup (Android Auto Backup)
Goal: a player who changes or restores a phone keeps coins, unlocked items, Daily history, achievements, settings and progress.
- First find where the game state really lives. The authoritative copy must be in native storage (SharedPreferences or files in the app's own folders), not only in WebView localStorage, because WebView storage is not a reliable backup source. If needed, mirror the saved state to native storage.
- Enable backup with proper rules for all Android versions: `android:allowBackup`, `android:fullBackupContent` and `android:dataExtractionRules` (Android 12+), covering both cloud backup and device-to-device transfer.
- **Include:** game state, economy, unlocked and equipped items, history, achievements, settings, language, tutorial-completed flag.
- **Exclude (must not be restored):** UMP consent storage (the IABTCF keys), the cached "ads removed" entitlement (it must always be re-verified through Google Play Billing; never trust a restored value), Remote Config cache, Firebase installation and Analytics IDs, notification scheduling state, anything cached or temporary.
- After a restore: re-query purchases, re-schedule local notifications, re-run state migration if the schema is older, and never crash on a missing or partial state.
- Test it: `adb shell bmgr backupnow` and restore on an emulator or phone; verify that coins and unlocks come back and that consent and the ads-removed state do not. Document the commands in `docs/`.
- State the limits for the owner: backups run on Google's schedule (idle, charging, Wi-Fi), users can switch them off, and it restores only for the same Google account on a new install of the app. It is not live sync between two phones.
- Privacy: list what to mention in the privacy policy.

## 1.2 Tap to skip the victory celebration
- During the victory celebration (confetti and the transition to the result panel), a tap anywhere jumps straight to the final result panel, with coins and stars already counted.
- The win is committed (state, coins, stats, Daily history) **at the moment of the win**, never at the end of the animation, so skipping can never lose a reward. Verify this in the code and fix it if not.
- Cancel all pending timers and animations on skip, ignore double taps, and make sure the next steps (level-up panel, unlock celebrations, popup coordinator, interstitial rules) run exactly once and in the same order as without skipping. Skipping does not skip the level-up panel or unlock celebrations; they appear as before.
- Add a small hint ("Tap to skip") after about one second. Respect the system "reduce motion" setting by shortening the celebration.

## 1.3 Confirmation before abandoning a game in progress
- When a game has at least one move and is not won, ask for confirmation before: New Game, Restart, a Numbered game, the Daily, tapping a deal in the Collection, and the Android Back button from the game screen. Leaving the app by Home or closing it is not abandoning (state is saved and resumes).
- Dialog: clear title and text saying the current game will be lost, two buttons: "Keep playing" (the default, highlighted) and "Abandon". For the Daily, check how attempts work today and word the text accordingly.
- Keep exactly the same stats behaviour as before for an abandoned game (do not change how it counts).
- **No Settings toggle.** The confirmation is always on.
- Does not use the popup coordinator (it is user-initiated), and must never appear on top of a victory panel.

## Done criteria for Part 1
Backup restores what it should and nothing it should not; skipping never loses coins or stats; the confirmation appears in every listed entry point and not otherwise.

---

# PART 2 — Difficulty tiers from known deal numbers (no solver)

**Decision from the owner: do not invent anything.** Use the deal numbers already published by FreeCell Solutions (https://freecellgamesolutions.com/stats.html). Do not write or run a solver.

## 2.0 Facts already verified (do not re-litigate; add the tests below)
- The game's `shuffledTableau(seed)` uses the Microsoft FreeCell algorithm (LCG 214013 / 2531011, `>>16 & 0x7fff`, suit order clubs, diamonds, hearts, spades, dealt left to right). The first row of deal #1 is `JD 2D 9H JC 5D 7H 7C 5H` and of deal #617 is `7D AD 5C 3S 5S 8C 2D AH`, matching the known Microsoft deals. So **game numbers on that site are exactly the game's seeds.** Add unit tests for these two vectors.
- The site publishes: an **Easy** list (deals solvable using zero free cells; 69 deals in 1 to 32,000 and 59 in 32,001 to 64,000, so **128 in total**, also listed sorted by complexity), a **Difficult** list (deals that need all 4 free cells and cannot be solved with 3; 198 in 1 to 32,000, 221 in 32,001 to 64,000 and 256 in 64,001 to 100,000, so **675 in total**), and the **impossible** deals in the first million: 11982, 146692, 186216, 455889, 495505, 512118, 517776, 781948.
- Current game behaviour (to be replaced): `dealSeedForMode` draws Random games 60% from `easyDeals` (46 deals), 35% from `mediumDeals` (46 deals) and 5% from a fully random 32-bit seed. Both pools are slices of the site's *Easy* list, so almost every Random game is one of only 92 very easy deals, they repeat quickly, and the 5% random seeds are unverified. "Winnable" mode uses `guaranteedTableau`, an artificial ordered deal. All of this goes away.

## 2.1 One-time data import (owner-run tool, not part of the app)
- Write `tools/fetch_deal_catalog.js`: download the page once, parse it and produce `app/src/main/assets/deal_catalog.json`:
  - `easy`: the 128 deals in the order of the "sorted by complexity" list (verify this set equals the union of the two Easy rows).
  - `hard`: the 675 Difficult deals.
  - `impossible`: the eight numbers above.
  - `medium`: stored as a **rule**, not a list: any number from 1 to 64,000 that is not in `easy`, `hard` or `impossible` (about 63,200 deals). The range stops at 64,000 because the Easy list only exists up to there.
  - `source`, `retrievedAt`, `catalogVersion`.
- Parse defensively and **assert**: Easy count is 128, Hard count is 675, no overlap between Easy and Hard, all numbers in range, 11982 not in any tier, and spot checks (Easy contains 164, 892 and 25904; Hard contains 169 and 31465). Fail loudly if the page layout has changed.
- Store only the extracted numbers, the URL and the date, not a copy of the page. The app never calls the site at runtime.
- Add a credit line in Settings (About) and in `docs/`: "Deal difficulty data: FreeCell Solutions (freecellgamesolutions.com)". Mention to the owner that the data is factual but the site is copyrighted, that the tool downloads it once, and that he may want to contact the site owner to let him know.
- Unit tests: catalog counts, no overlap, Medium rule excludes everything it must, all seeds regenerate valid 52-card deals.

## 2.2 Game changes
- **Owner rule (2026-10-25), overrides anything below:** the first five Random games of a new player stay the five fixed deals 25904, 11987, 5152, 9998 and 13464, in that order (`FIRST_GAME_SEEDS`, counted in `introGamesStarted`), whatever tier is chosen. Only after them do Random games come from the FreeCell Solutions tiers. A Daily Challenge is never one of them and never uses them up: a player who opens the Daily first still gets the five fixed deals in Random afterwards.
- **Tiers:** Easy (the 128 list), Medium (the rule), Hard (the 675 list). Remove the old `easyDeals`, `mediumDeals`, the 5% random seed and the artificial `guaranteedTableau` for new games (saved games and replays keep working because the initial tableau is stored; migrate old state safely).
- **Random mode** lets the player choose Easy, Medium or Hard (default Medium). Draw a seed at random inside the chosen tier, avoiding the last 50 played seeds of that tier. The Easy tier only has 128 deals, so prefer unplayed ones and allow repeats once all were played (repeats pay no coins because of the "pay once per deal seed" rule).
- **"Winnable" mode** becomes the Easy tier: change the visible label, keep stored mode keys compatible, migrate old statistics and history without loss.
- **Numbered mode** stays free-form. If the number is in the Easy or Hard list, show that tier on the victory panel; if it is in the impossible list, keep or improve the existing warning.
- **Daily Challenge:** deterministic from the local date: Medium Monday to Friday and Hard on Saturday and Sunday (`difficulty_config.daily_tiers_by_weekday`, adjustable in Remote Config). The seed is picked from the tier by a fixed function of the date (for Medium, a deterministic hash chain with rejection of excluded numbers). Never reorder or remove entries in a published catalog; new versions only append, so past Dailies stay reproducible. Everyone with the same local date gets the same deal.
- Show the tier on the game screen and on the victory panel. Record the tier in history and statistics (wins by tier). Add it as an allowlisted analytics parameter.
- **Economy by tier** (in `economy_config`): replace the per-mode reward with a per-tier reward, default Easy 10, Medium 20, Hard 30; Daily unchanged. Keep "pay once per deal seed".
- Add `difficulty_config` to Remote Config: enabled tiers, default tier, `daily_tiers_by_weekday`, optional per-tier `unlock_level` (default 1 for all), with bundled defaults.

## Done criteria for Part 2
Test vectors for #1 and #617 pass; catalog asserts pass; the three tiers play correctly; old saves and stats migrate; the Daily is always winnable and reproducible; the credit line is visible.

---

# PART 3 — Daily Rivals (simulated ranking, no online service)

**Decision from the owner: no Google Play Games and no real online ranking.** Build a ranking against **simulated rivals** generated locally. Nothing leaves the device.

## 3.1 Honest labelling (requirement)
The rivals are invented, so the feature must never claim they are real players. Name it "Daily Rivals" (not "Worldwide ranking"), show a small, always-visible note "Rivals are simulated players" (translated), and use clearly fictional nicknames and generic avatars (initials on coloured circles; no photos, no real people's names, no brand names). Claiming real competitors would be misleading to users and could breach Google Play's deceptive-behaviour policy and EU consumer rules; this label avoids that and costs nothing.

## 3.2 Design
- A roster of about 60 fictional rivals in an asset (`rivals_roster.json`): id, nickname, avatar colour, a persistent **skill** factor (some are fast, some slow) and a persistent **reliability** (how often they play).
- **Today's table** (deterministic from the local date, so every player sees the same rivals and times that day): pick about 30 rivals from the roster by a seeded function of the date; each rival's finishing time is drawn from a log-normal distribution around a configurable median, scaled by their skill and by the tier of the day's deal (Hard slower than Medium), with a small daily variation. Times are never below a configured minimum.
- **Player placement:** after the player wins the Daily, show "You finished #7 of 31 today", the top rivals and the player's row highlighted. Before winning, show the table with a prompt to win today's Daily to join.
- **Streak board:** each rival has a streak computed by a deterministic function of (rival id, date, reliability): they "play" on a date if a hash falls under their reliability, and the streak is the run of consecutive days ending today. Show the player's current streak among them.
- Rank and best placement are stored locally and feed statistics and achievements (for example "Top 3 of the day").
- No coin rewards for rank in this version. Leave a disabled hook in `rivals_config` so rewards can be added later.
- Entry point: a "Rivals" button on the Daily screen and on the Daily victory panel.
- Pure functions for roster selection, time generation, rank and streak; deterministic with a seeded PRNG (no `Math.random`).
- `rivals_config` in Remote Config: `enabled`, number of rivals shown, `median_seconds` (default 420), `sigma`, tier multipliers (Medium 1.0, Hard 1.4), `min_seconds`. Bundled defaults and bounds.
- Debug tools: show the roster, change the date, force a rank.
- Tests: same date gives identical table, different dates differ, time distribution sanity (median and spread), rank computation including ties, streak function, 6 translations.

## Done criteria for Part 3
The table is identical for the same date, the "simulated" note is always visible, ranking and streak boards work offline, nothing is sent to any server.

---

## Owner tasks across this file

- Firebase: enable Crashlytics after the first test crash; create `difficulty_config` and `rivals_config` in Remote Config after the Code confirms them.
- Privacy policy and Data safety: crash logs and diagnostics, and backup. Rivals need nothing (no data leaves the device).
- Run `tools/fetch_deal_catalog.js` once when the Code asks, and review the counts it prints.
- Decide whether Hard on weekends for the Daily is right.
