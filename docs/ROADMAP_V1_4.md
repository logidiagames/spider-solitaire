# FreeCell v1.4 — Briefs for Claude Code

Save this file as `docs/ROADMAP_V1_4.md`. Work through the parts **in order, one at a time**. After each part: build, run tests, give the owner a short summary in plain Portuguese plus what to test on a phone, and **wait for his approval before the next part**.

This builds on v1.1 to v1.3. Read the current code and `CLAUDE.md` first and reuse what exists: the shared Remote Config JSON helper, the `today()` helper, the popup coordinator, the "no more moves" message and its placement rules, the debug tools panel, the pure-function + unit-test approach, the translations system.

## Global rules

- Do not change game rules, the move animation flow (temporary clones), the economy numbers, ad cadence, difficulty tiers or the intro games unless a task says so.
- Every new switch or number comes from Remote Config with bundled defaults, bounds and a safe fallback. Values apply on the next launch.
- Share logic between the game and any new tool: the solver and the hint must use the **same move rules as the game** (one source of truth), never a parallel copy.
- Do not change versionCode/versionName, do not generate an AAB, do not add Android permissions. Update `CLAUDE.md` at the end of each part. Small commits, English messages.
- Anything that needs the owner (Firebase, Play Console) goes in your summary as an explicit "owner task". Do not attempt it.

## New Remote Config parameters (all JSON, bundled defaults in `app/src/main/assets/`)

```json
// hint_config
{"smart_hint":true,"time_budget_ms":300,"node_limit":400000,"background_analysis":true,
 "background_budget_ms":150,"max_hints_per_game":null}

// tips_config
{"enabled":true,"free_cells_tip_min_occupied":3}

// feel_config
{"sounds":true,"haptics":true,"sound_volume":0.6,"suit_complete_effect":true}

// performance_config
{"auto_reduce_effects":true,"low_end_memory_class_mb":128,"fps_sample_seconds":4,"low_fps_threshold":40}

// languages_config
{"enabled":["en","pt","pt-BR","es","fr","it","de","nl","pl","tr","ru","uk","ja","ko","zh-Hans","id","sv","cs"],
 "beta":[]}
```

---

# PART 1 — Smart hint (solver inside the game) and the first-time stuck tip

## 1.1 What it must do
Today the hint suggests a move but does not know whether the game is still winnable. Add a light solver that runs **inside the app** (no network, nothing external) so that:
1. Hints are the first move of a **verified winning line** whenever one can be found within the time budget.
2. The game can say with certainty when a position **cannot be won**.

## 1.2 Solver design
- Run it in a **Web Worker** (create it from an inline Blob so the single-file game keeps working) so the interface never freezes. If Workers are unavailable, fall back to time-sliced work on the main thread (small chunks with `setTimeout`).
- Search with a transposition table (hash of the position) and a good move ordering (prefer moves to the foundation that are safe, uncovering buried low cards, emptying columns, keeping free cells free). Stop at `time_budget_ms` or `node_limit`, whichever comes first.
- Use the game's own rules, including multi-card moves with the same maximum-run formula as the game. A hint must be a move the interface accepts **as one action** (not a sequence of single-card steps the player would not recognise).
- Three outcomes, never mixed up:
  - **Winning line found:** the hint is its first move. Keep the line; the next hint tap, while the player follows it, gives the next move of the same line (no recomputation).
  - **Budget ran out, no line found:** fall back to the existing heuristic hint. Do **not** claim the game is lost.
  - **Search space fully exhausted with no solution:** the position is verified lost. Only then show a message with certainty (see 1.4).
- If the player deviates from the line, recompute. Invalidate everything on Undo, Redo, Restart and New Game.
- **Background analysis:** after each player move, when the game screen is visible and the app is in the foreground, analyse in the background with `background_budget_ms` at low priority so the hint is usually ready instantly. Cancel on any new move, pause when the app goes to the background, and never run during animations or the victory sequence. Respect battery: no analysis when the game is won or idle for long.
- Keep the existing "same hint repeated" stuck logic from v1.1 consistent: a repeated hint that belongs to a verified winning line is not "stuck".
- Hints do not change scoring, stars or achievements today. Keep that, but `hint_config.max_hints_per_game` (default none) lets the owner cap hints later without a new build. Tell the owner in plain words that perfect hints make the game easier.

## 1.3 Tests (obligatory)
- Run the solver in Node (desktop) on the five intro deals and a sample of Easy, Medium and Hard deals from the catalog; every returned line must replay through the game's real move validator to a win. Record success rate and time per tier.
- Unit tests for: line following, deviation, invalidation on Undo/Redo, the three outcomes, a hand-built position that is provably lost, and the budget limits.
- Measure real times on the Android phone via a debug-only timing log. Tell the owner what budget works on a slow phone and tune the defaults.

## 1.4 Messages
- Verified lost: "There is no way to win from here. Try Undo or start a new game." Same visual rules as the existing "no more moves" message (non-modal, translucent, centred between the cards and the buttons, never overlapping, expand-then-shrink pulse on the Undo button).
- Not verified: keep the existing wording for "no more useful moves" and never state it as certain.
- Optional, if simple: when the position has just become verified lost and the previous position was known winnable, the Undo pulse is the cue to take back the last move.

## 1.5 First-time stuck tip
- The first time the player gets the stuck message (no legal move, or verified lost) **and** at least `free_cells_tip_min_occupied` free cells are occupied, show one extra short line or small card: "Tip: free cells are only for temporary storage. Try to keep them empty." Translated. A "Got it" button; shown once per install (persist the flag, include it in the backup).
- It must follow the same placement rules (never overlap cards or buttons), never block play, and never appear during the first-run tutorial or on top of a victory panel. Controlled by `tips_config.enabled`.

## Done criteria for Part 1
Hints come from verified lines in the large majority of games; a lost position is reported only when the search was exhaustive; the interface never freezes; timings are measured on a real phone; the tip appears once.

---

# PART 2 — Redo and larger cards and text

## 2.1 Redo
- Add **Redo** next to Undo. Keep a stack of undone moves, cleared as soon as the player makes a new move, and cleared on Restart, New Game and after a win. Do not persist the redo stack across app restarts (the Undo history already persists).
- Use the same animation and state-commit flow as moves and Undo (do not change that flow). The button is visibly disabled when there is nothing to redo.
- Counting rules, to keep everything consistent: Redo adds one to the move counter like Undo does; it does not change the Undo count used for stars; it must not double-count anything that is counted per game, such as cards sent to the foundations for missions and achievements (count a card the first time it reaches the foundation in this game, whatever the undo/redo history); coins are still credited only on a win.
- Invalidate hints and background analysis on Redo. The "repeated position" detection for stuck must not treat a Redo as a new repeated position.
- Tests: undo then redo restores the exact state; redo cleared by a new move; redo unavailable after a win or New Game; counters not double-counted.

## 2.2 Larger cards and text
- New Settings group "Accessibility": **Card size** (Normal, Large, Extra large) and **Text size** (Normal, Large, Extra large). Show a live preview.
- **Card size** cannot widen cards beyond the screen, so it works by: larger and bolder corner rank and suit ("jumbo index"), a bigger central pip, tighter gaps and overlap tuned so the 8 columns use the whole width, and cards as tall as the available height allows. Keep suits and ranks fully readable on every background and card face (same legibility rule as before). It must not break the layout on small phones, tablets or landscape.
- **Text size:** scale all UI text (menus, dialogs, panels, messages) to about 100%, 125% and 150%, without clipping: panels must wrap or scroll. Also respect the Android system font scale: on first launch, if the system font scale is 1.3 or more, default to Large. Test the whole UI at the largest settings in the longest languages.
- Touch targets at least 48 dp for toolbar buttons and dialog buttons; slightly generous tap tolerance on cards.
- Persist the choices (backed up). Debug tools: force each size.

## Done criteria for Part 2
Redo is consistent with Undo; larger sizes work in every screen without clipping; nothing breaks on small phones, tablets and landscape.

---

# PART 3 — Feel: sounds, haptics, suit-complete effect

- **Sounds:** short, original or properly licensed sounds (record the licence in `docs/CREDITS.md`; nothing copied from other apps). Events: card placed, card to the foundation (pitch rises with the foundation count), coins credited, suit completed, invalid move (already exists), win. Each at most about 300 ms (win can be longer). Generate with Web Audio or ship small OGG files (keep each under 20 KB), preload, unlock on the first touch, limit simultaneous sounds. Respect the existing sound setting and `feel_config`. Never take audio focus (do not interrupt the player's music).
- **Haptics:** use the existing vibration bridge with Android's predefined effects where available (tick, click, double click) and a short fallback on older versions. Light tick on a card placed, firmer click on the foundation, double pulse on suit completion, a short pattern on a win. Durations about 10 to 30 ms, rate-limited, respect the in-app setting and the system haptics setting.
- **Suit-complete effect:** when a foundation reaches King, a brief golden ring or sparkle on that foundation plus the sound and haptic, at most about 600 ms, non-blocking, never delaying input or the auto-foundation animation. For the fourth suit, do not stack on top of the victory celebration (let the victory sequence own it). Under "reduce motion" skip the animation and keep sound and haptic.
- Settings: Sound effects and Vibration toggles (reuse existing ones if present). Everything from `feel_config`.
- Debug tools: play every sound and haptic.

## Done criteria for Part 3
Sounds and haptics are crisp and never overlap into noise; the suit effect never blocks play; all can be switched off.

---

# PART 4 — More languages, each shown in its own language

## 4.1 Language list
Currently EN, PT, ES, FR, IT, DE. Add, as first batch: **Português (Brasil)** as a separate language from Português (Portugal), **Nederlands, Polski, Türkçe, Русский, Українська, 日本語, 한국어, 简体中文, Bahasa Indonesia, Svenska, Čeština**. Later candidates (do not build now): Română, Ελληνικά, Magyar, Tiếng Việt, ไทย, हिन्दी, 繁體中文, and Arabic or Hebrew (right-to-left needs separate layout work).

## 4.2 Language selector (requirement)
- Every language is shown **in its own language and script, always**, whatever language is active: "English, Português (Portugal), Português (Brasil), Español, Français, Italiano, Deutsch, Nederlands, Polski, Türkçe, Русский, Українська, 日本語, 한국어, 简体中文, Bahasa Indonesia, Svenska, Čeština". The names are never translated into the active language.
- No flags (a flag is a country, not a language). Mark each item with the right `lang` attribute so fonts and shaping are correct. Fixed order, not alphabetical by the active language. A check mark on the current one.
- Available from Settings and from the first-run screen (a small "Language" link) so a wrong automatic choice can be fixed before the tutorial.
- Default from the device language with a sensible mapping: pt-BR to Português (Brasil), other Portuguese to Português (Portugal), zh-CN and zh-Hans to 简体中文, anything unsupported to English. Also declare the supported languages for Android 13+ per-app language (`locales_config.xml`) and keep the in-app choice in sync with it.
- `languages_config.enabled` and `beta` allow switching a language on or off, or marking it "beta", without a new build.

## 4.3 Translation work
- Keep strings out of the big HTML file if that would hurt startup: load one language file on demand (for example `assets/i18n/<lang>.json`) through a safe loader, and fall back to English **per missing key**.
- Write first-draft translations for all keys, including: notifications, share texts and the Daily share image, the store and shield texts, missions, achievements, tips, errors and toasts. Use `Intl.DateTimeFormat`, `Intl.NumberFormat` and the locale's first day of the week for the calendar. Avoid sentences that depend on plural forms, or use `Intl.PluralRules` (Slavic languages have more than two forms).
- Produce `docs/translations_review.csv` (key, English, one column per language) so native speakers can review. Mark unreviewed languages in `languages_config.beta` if the owner wants.
- Automated checks: every language has every key; placeholders such as `{n}` match the English ones; no HTML injection; a test page that renders the longest strings in each language in buttons and dialogs so overflow is caught; check glyph rendering (CJK and Cyrillic) on a real device.
- Prices always come from Google Play Billing (localised); never hard-code currencies.

## Done criteria for Part 4
All 18 languages selectable, names always in their own language, no clipped text at normal and large text sizes, English fallback works per key, review CSV delivered.

---

# PART 5 — Performance on weak phones (do this last)

- **Measure first.** Add a debug-only frame-time overlay and a log of frame times during card flights, the deal animation and the victory celebration. Targets on a low-end phone: at least 50 fps during card movement, a cold start to a playable screen in under 3 seconds, no long tasks that make the interface unresponsive.
- **Auto low-effects mode.** Detect weak devices (low-RAM flag, memory class below `low_end_memory_class_mb`, old Android version) and also sample the real frame rate in the first seconds of the first game (`fps_sample_seconds`; if below `low_fps_threshold`, switch on). In that mode reduce confetti count, shadows, blur and filters and shorten non-essential animations. Add a Settings option "Reduce effects" (Auto, On, Off). Controlled by `performance_config`.
- **Rendering hygiene:** animate only `transform` and `opacity`, avoid animating `box-shadow`, `filter` or layout properties, avoid layout thrash and full re-renders of the board on every move, use `will-change` sparingly, and do not run timers or background analysis when the app is not visible.
- **Startup:** the single HTML file keeps growing. Measure parse and init time; lazy-initialise heavy screens (Store, Achievements, Collection, Rivals) on first use, and consider build-time minification. Report APK size and check R8 and resource shrinking.
- Debug builds only: enable WebView remote debugging and StrictMode.
- Report numbers before and after.

## Owner tasks for Part 5
After the first upload to a testing track, open the **Pre-launch report** in Play Console (free automatic tests on real devices: crashes, slow starts, accessibility) and send the findings to the Code. Also test on the oldest Android phone available and tell the Code the model.

---

## Owner tasks across this file

- Firebase: create `hint_config`, `tips_config`, `feel_config`, `performance_config` and `languages_config` after the Code confirms each.
- Play Console: translate the store listing for each new language (title, short and long description, screenshots with text), after the languages are in the app.
- Have native speakers (or a translation service) review `docs/translations_review.csv`, starting with the largest markets.
- Test on a real weak phone and on a tablet.
