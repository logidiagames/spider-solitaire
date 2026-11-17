# Deal catalog and tiers (Spider)

The three tiers keep FreeCell's keys and mean the number of suits in the two decks:

| Tier key | On screen | Cards |
| --- | --- | --- |
| `easy` | 1 Suit | 8 × spades |
| `medium` | 2 Suits | 4 × spades and hearts |
| `hard` | 4 Suits | 2 × every suit |

A deal number (1–9,999,999) is the seed of Microsoft's shuffle (LCG 214013/2531011) over the 104 cards; the same number in the same tier always deals the same cards (`FreeCellCore.dealGame`).

`app/src/main/assets/deal_catalog.json` is made by `node tools/make_deal_catalog.mjs [easyCount] [mediumCount]`: deal numbers, spread over the whole range, that the game's own hint solver wins from the start (1 suit within 100,000 positions, 2 suits within 150,000). 4 Suits has no list: the solver rarely finishes a 4-suit deal within a phone's budget, so any number is used. `daily.<tier>` is the number of entries the Daily draws from, frozen so a past Daily always deals the same cards; entries are only ever appended.

- Classic games: a deal of the chosen tier's list, unplayed deals first, never one of that tier's last 50 (`pickDeal`, history `tierHistory`). With no list: any number not among the last 50.
- Daily: the weekday's tier (`difficulty_config.daily_tiers_by_weekday`, Sunday first; default 2 Suits at the weekend, 1 Suit on weekdays), then a deal of that tier's frozen Daily list by a hash of the date (`dailyDeal`). Never a warm-up deal.
- Warm-up: a new player's first Classic games are `difficulty_config.first_games.seeds`, deals the solver wins with 1 and 2 suits, dealt in the tier the player chose; one moves on only when it is won.
- Game by Number: any number, in the player's current tier.

FreeCell's catalog (FreeCell Solutions' lists, pools and mixes) is not used by Spider.

## Version 3 (2026-11-17): ease for a person

The lists are now sorted by `tools/rate_deal_ease.mjs`: a simple player plays every deal 24 times
and the share of wins is the ease (easiest first). The solver-effort order of version 2
(`tools/rank_deal_catalog.mjs`) did not match how hard a deal feels: the owner found the first
games too hard. 400 `easy` and 200 `medium` deals are spread over the whole range of deals the
simple player (or, for the old lists, the solver) wins, so the five adaptive bands run from deals
it wins almost every time to deals that need care. The five warm-up deals are the easiest ones.
