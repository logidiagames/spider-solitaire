# Screen-recording behaviour analysis

Source reviewed: `Recording_20261020_164604.mp4` (7m48s, portrait phone capture). The recording was used as behavioural reference only. No frames, branding, copy, artwork, audio or proprietary assets are included in the application.

## Observed product flow

| Area | Behaviour observed | Original prototype counterpart |
|---|---|---|
| First launch | Splash, local/privacy notice, tutorial offer | Native launch, local-data notice, tutorial offer |
| Tutorial | Six guided topics covering the goal, foundations, cards, runs, free cells and completion | Six-step explanatory tutorial reachable on first launch and from Settings |
| Game HUD | Score, elapsed time and move count | Live three-column header |
| Board | Four free cells, four foundations, eight tableau columns, green felt | Custom Android Canvas board with three green treatments |
| Card control | Tap, drag, sequence movement and invalid-move feedback | Tap-to-move, drag/drop, capacity-checked runs, sound/haptic feedback |
| Bottom actions | Settings, theme, new game, hint and undo | Same five functional destinations with original styling |
| New game | Random, winnable, numbered, daily and replay | All five entry points; winnable uses a rotating prototype seed and needs a verified deal catalogue before production |
| Personalisation | Card/table themes and several play preferences | Three card treatments, three green tables and persistent settings |
| Help | Rules carousel and tutorial replay | Rules pages and tutorial replay |
| Progress | History, statistics and completed numbered games | Local history and statistics; completed games are marked in history |
| Monetisation | Banner/rewarded ads, currency/shop and remove-ads | Deliberately absent until account IDs, products, consent and privacy implementation are supplied |

## Clean-room decisions

- The table remains green because that is a generic convention of classic card tables and an explicit project requirement.
- Layout hierarchy and interaction categories are reproduced at a high level, while colours, typography, icons, dialog copy and rendering code are original.
- Online advertising, analytics, purchases, rating URLs, feedback endpoints and privacy links are represented only by safe local placeholders in this prototype.
- A production claim that every “resolvable” deal is verified requires either an audited deal catalogue or a solver-backed generator; the prototype does not yet make that guarantee.
