# Architecture and product roadmap

## Current architecture

```text
MainActivity
  ├─ persistence, preferences, tutorial and product dialogs
  ├─ StatsStore (history and aggregate statistics)
  └─ FreeCellView
       ├─ canvas rendering, tap and drag mapping
       ├─ sound, haptics, hints, auto-complete and celebration
       └─ FreeCellGame
            ├─ immutable Card and Location values
            ├─ rules and move validation
            ├─ bounded snapshot undo history
            └─ versioned compact state serialization
```

The rules module has no Android dependency, so it can be unit-tested on the JVM. Rendering uses Android Canvas and original programmatic shapes, avoiding a bitmap asset pipeline for the MVP. The activity owns lifecycle persistence and confirmation/win dialogs.

## Implemented rules

- A standard 52-card deck is deterministically shuffled from a recorded seed and dealt 7/6 cards across eight columns.
- Tableau builds downward in alternating colours.
- A free cell holds one card.
- Foundations build upward by suit from Ace to King.
- Sequence capacity is `(empty free cells + 1) × 2^(usable empty columns)`, with an empty destination excluded as a temporary column.
- Legal game states serialize with a schema version and validate that all 52 cards are represented exactly once.

## Implemented prototype surfaces

- Five-action game toolbar, green felt themes and three original card treatments.
- Tap-to-move, drag-and-drop, multi-card sequences, hint, undo and automatic foundation moves.
- Random, numbered, daily, replay and prototype resolvable game entry points.
- First-run notice, six-step tutorial, rules carousel, settings, language selector, history and statistics.
- Local game restoration, option persistence, sound, vibration, orientation, handedness and celebration controls.

## Recommended next increments

1. Product identity: approve final title, icon, card artwork, sound set and store positioning.
2. Deal quality: replace the prototype resolvable seed rotation with a verified offline catalogue or solver-backed generator.
3. Quality: add instrumentation tests, landscape/tablet layouts, accessibility semantics, performance QA and structured playtesting.
4. Progression: daily streak calendar, completed-number index and optional cloud backup.
5. Services only after approval: privacy/consent, analytics, crash reporting, ads and billing. Each SDK should justify its permissions and data disclosures.

