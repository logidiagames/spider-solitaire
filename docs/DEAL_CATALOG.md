# Deal catalog and difficulty tiers (v1.3 Part 2)

Deal difficulty data: FreeCell Solutions (freecellgamesolutions.com), https://freecellgamesolutions.com/stats.html.

The game rates deals only with these published lists; it never runs a solver for that (the hint solver is separate).

## Why the numbers match

The game deals with Microsoft's algorithm (`FreeCellCore.dealTableau`: LCG 214013 / 2531011, `>>16 & 0x7fff`, suits clubs, diamonds, hearts, spades, dealt left to right), so a game number on that site is the game's seed. Unit tests pin it: the first row of deal #1 is `JD 2D 9H JC 5D 7H 7C 5H` and of deal #617 `7D AD 5C 3S 5S 8C 2D AH`.

## Tiers

| Tier | Deals | Source |
| --- | --- | --- |
| Easy | 128, in the site's "sorted by complexity" order | Solvable with zero free cells: 69 in 1–32,000 and 59 in 32,001–64,000 |
| Hard | 675 | Need all four free cells (not solvable with three): 198 in 1–32,000, 221 in 32,001–64,000, 256 in 64,001–100,000 |
| Medium | 63,452, by rule | Every deal from 1 to 64,000 that is not Easy, Hard or impossible |
| Impossible | 8 | 11982, 146692, 186216, 455889, 495505, 512118, 517776, 781948 |

A numbered deal above 64,000 that is not in the Hard list is "unrated": no tier is shown and it pays as Medium. The five warm-up deals (25904, 5152, 11987, 13464, 9998) are the five easiest of the Easy list, in its order.

## `app/src/main/assets/deal_catalog.json`

Built by `tools/fetch_deal_catalog.js`, never by hand:

```sh
node --use-system-ca tools/fetch_deal_catalog.js
```

(`--use-system-ca` lets Node trust the Windows certificate store behind the antivirus HTTPS scanning; `--file <saved page>` reads a saved copy instead of downloading.)

The tool downloads the page once, keeps only the deal numbers, the URL and the date (never a copy of the page), and refuses to write anything unless: Easy has 128 deals and the sorted list equals the two Easy rows, Hard has 675, every list matches the count in its heading, Easy and Hard do not overlap, all numbers are in range, the impossible deals are the eight above and in no tier, and the spot checks hold (Easy has 164, 892 and 25904; Hard has 169 and 31465). The app never calls the site.

A published catalog is append-only: the tool keeps the existing order, adds new deals at the end (raising `catalogVersion`) and fails if a deal disappeared. `daily.easyCount` and `daily.hardCount` freeze the part of each list the Daily Challenge draws from, so a past Daily always deals the same cards.

The Android shell puts the catalog in the page before its script runs (`window.__freecellDealCatalog`, `MainActivity.dealCatalogScript`); the browser preview server does the same. Without it the page still deals (Medium only, with the opening deals as Easy).

## How deals are chosen

- **Classic (Random):** a new player first plays the five warm-up deals (`difficulty_config.first_games`), each until it is won, with "Warm-up 2 of 5" in place of the picker. Then the player picks Easy, Medium or Hard (`difficulty_config.default_tier`, Medium, until then), and each tier draws from a mix of pools (`difficulty_config.mixes`): `very_easy` (the first 64 Easy deals, `easy_split_index`), `easy` (the other 64), `medium` and `hard`. Defaults: Easy 60/40 very_easy/easy, Medium 70/30 medium/easy, Hard 60/40 medium/hard. A deal never repeats one of its pool's last 50; the lists prefer deals never played and repeat once all were (a repeat pays nothing). The game shows and pays the chosen tier; its pool is in the game log and in analytics (`deal_pool`).
- **Daily Challenge:** Medium Monday to Friday, Hard on Saturday and Sunday (`difficulty_config.daily_tiers_by_weekday`), each from its tier's mix. The pool and the deal come from the local date alone, the same for everyone: a list pool takes an entry of the frozen part of its list; Medium follows a hash chain of the date and skips every number that is not Medium. The warm-up deals are never a Daily.
- **Game by Number:** free-form; the tier of the number is shown on the game screen and the victory panel ("Unwinnable" for the impossible deals).
- **Winnable** (v1.1–v1.2, an artificial ordered deal) is gone: the Easy tier replaces it. A Winnable game saved in progress still plays and pays as Easy, and its Winnable missions now count Easy wins.

## For the owner

The data is factual, but the site and its pages are copyrighted. The tool reads the page once and keeps only the numbers, and the game credits the site in Settings. You may want to tell the site's owner that the game uses the lists.
