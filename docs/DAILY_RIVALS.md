# Daily Rivals (v1.3 Part 3)

A daily ranking against invented rivals, made on the phone. There is no online service, no Google Play Games and no real ranking: nothing leaves the device.

## Naming

- The feature is called "Daily Rivals" (never "worldwide ranking"), and nothing on screen calls the rivals real players.
- Owner decision (2026-10-29): no "Rivals are simulated players" note on the screen (it was under the title in the first build; the translated text `rivalsSimulated` is kept, unused).
- The 60 rivals in `app/src/main/assets/rivals_roster.json` have invented nicknames (no real people, brands or titles) and generic avatars: initials on a coloured circle, no photos.

Claiming real competitors would mislead players and could breach Google Play's deceptive-behaviour policy and EU consumer rules, so texts, store listing and screenshots must never present the rivals as real people.

## How it works (`FreeCellCore`, pure and deterministic, seeded random numbers only)

- **Roster** (`cleanRoster`): id, nickname, avatar colour, a fixed `skill` (multiplies a time: under 1 is fast) and `reliability` (the chance of playing on a given day).
- **Today's table** (`rivalsTable`): `rivals_shown` rivals (30) drawn from the roster by the local date; each one's time is log-normal around `median_seconds` (600) with spread `sigma` (0.35), times its skill and the Daily's tier multiplier (1 Suit 0.8, 2 Suits 1.0, 4 Suits 1.6), never under `min_seconds` (90). Every player sees the same rivals and times on the same date.
- **Place** (`rivalsRank`): 1 + the rivals strictly faster, so a tie shares the place; the total counts the player. The player's time is the best win of today's Daily on its own date.
- **Streaks** (`rivalStreak`, `rivalsStreakBoard`): a rival plays a date when a hash of (rival, date) falls under its reliability; its streak is the run of such days ending today. The player's own Daily streak is placed among them.
- **Results** (`recordRivalsResult`): the best place ever, the last place, and the number of days in the top 3 (each day once). They feed Statistics and the achievements "Top 3 in Daily Rivals" (1 and 10 times), which pay **no coins** in this version.

## Screens

- Entry points: the "Daily Rivals" button on the Daily Challenge screen (with today's place once won) and a chip with the place next to Share on the Daily victory panel. Back returns to the screen it came from.
- Today tab: before winning, "Win today's Daily Challenge to join the table", the top 8 and Play challenge; after winning, "You finished #7 of 31 today", the top 8 and the player's row in gold (added below when lower).
- Streaks tab: the top 8 streaks and the player's row.
- DEV panel row "Rivals": the roster with skill and reliability, force place #1, #7 or last, back to the real place; the date moves with the existing day buttons.

## `rivals_config` (Remote Config)

See `docs/REMOTE_CONFIG_GAME.md`. `rewards.enabled` is a switch kept for a later version: nothing reads it, and no rank pays coins.

## Privacy

Nothing to declare: the roster is in the app, the table is computed on the phone and no data is sent. Only the analytics event `rivals_opened` (`source`: `daily` or `victory`) records that the screen was opened.
