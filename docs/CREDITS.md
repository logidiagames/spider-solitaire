# Credits and licences

## Sounds

Every sound in the game is synthesised at run time with the Web Audio API in
`app/src/main/assets/freecell.html` (oscillators and a short noise burst, shaped by code). There
are no recorded samples or sound files, nothing copied from other apps and nothing under a
third-party licence. Nexus Play Labs owns them.

| Sound | Made of | Length |
| --- | --- | --- |
| Card placed | one 510 Hz sine blip | 80 ms |
| Card slide (card flights) | filtered noise | 65 ms |
| Card to the foundations | two notes, one octave higher from the first card to the last | about 160 ms |
| Suit completed | C6, E6, G6 arpeggio | about 300 ms |
| Coins credited | three quick high notes | about 200 ms |
| Move not allowed | one 155 Hz blip | 80 ms |
| Win | C5, E5, G5, C6 fanfare | about 800 ms |

## Deal difficulty data

Deal tiers come from FreeCell Solutions (freecellgamesolutions.com), credited in Settings
(`docs/DEAL_CATALOG.md`).
