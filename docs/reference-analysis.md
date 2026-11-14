# Reference XAPK analysis

Analysed file: `FreeCell+Solitaire_3.19.1.20260617_APKPure.xapk`.

This was a clean-room product analysis. The reference binary remains outside this repository. No decompiled implementation, code, images, audio, fonts, text catalogue, advertising identifiers, or package identifiers were copied into the new project.

## Package structure

| Item | Observed value |
|---|---|
| XAPK format | APKPure XAPK v2 |
| Base APK | ~144.9 MB |
| ABI split | `armeabi-v7a`, ~24.5 MB |
| Installed payload represented by XAPK | ~169.4 MB |
| Reference package | `com.lemongame.freecell.solitaire` |
| Reference version | `3.19.1.20260617` (`31901`) |
| Minimum / target SDK | 24 / 35 |
| Main activity | `zhongbosoft.com.freecell.MyActivity` |
| Engine | Unity, IL2CPP native build |

Evidence for the engine includes Unity boot data, `global-metadata.dat`, `libunity.so`, `libil2cpp.so`, and `Assembly-CSharp.dll` in the scripting assembly inventory. The supplied XAPK contains only an ARMv7 split; that may be a property of the APKPure package rather than the complete Play delivery set.

## Broad feature inventory

The resource catalogue and package structure indicate these product areas:

- Core FreeCell: tableau, four free cells, four ascending suit foundations, hints, undo, timer, moves, score and win flow.
- Game selection: random, guaranteed-winnable, numbered, replay and quick games.
- Guidance: rules, a multi-step tutorial, invalid-move explanations and automatic hints/completion.
- Personalisation: card faces, table backgrounds, card highlighting, celebration, sound, vibration, handedness, orientation, tap-to-move and language.
- Retention: daily challenge, statistics, history, best time/move records and replay from history.
- Monetisation: ads, rewarded currency, a helper item/shop, in-app purchasing and remove-ads entitlement.
- Service surfaces: privacy policy, ratings and feedback.
- Localisation assets for about 40 languages.

## Game types observed

The five entries under **New Game** are distinct flows, not cosmetic labels:

| Type | Behaviour evidenced by the APK and recording |
|---|---|
| Random Game | Starts a newly shuffled deal. Unlike Winnable Game, the UI makes no solvability promise. |
| Winnable Game | Loads a deal from the reference app's dedicated winnable-board catalogue (`GetWinBoard` / `NewWinboardGame`). |
| Numbered Game | Recreates a deterministic deal from an entered number between 0 and 9,999,999. The reference explicitly warns that not every numbered deal is solvable and keeps a completed-numbered-games list. |
| Daily Challenge | Selects a date-specific deal from a calendar, records solved/unsolved status, allows retrying, and shows monthly cumulative progress with milestones. |
| Replay | Resets the current deal to its original layout instead of creating a different shuffle. |

`Quick Game` appears separately as a Settings switch. Metadata associates it with `SettingAutoPlay`, so it is an automation/speed preference rather than a sixth deal type. The recording does not expose enough interaction to reproduce its exact trigger policy; production behaviour should therefore be specified independently instead of guessed from the binary.

## Technical and product observations

- The reference bundles Firebase Analytics/Remote Config, Adjust, Facebook, Unity Purchasing and a very large advertising-mediation surface. This explains much of its size and manifest complexity.
- Its manifest requests network, advertising ID, billing, vibration, wake-lock and legacy external-storage permissions, among others.
- The new MVP intentionally requests no runtime or network permission and includes no tracking, ads or billing SDK. Those should be introduced only with an approved monetisation plan, consent flow and privacy review.
- A native custom-rendered Android UI is sufficient for a card game and produces a much smaller, easier-to-test application than the reference Unity package.

## Clean-room boundary

Allowed inputs from the reference were high-level behaviour, platform metadata and general visual conventions common to card games. The implementation uses independent rules, naming, structure, colours, vector art and interaction code. Future designers should continue to supply original card faces, backgrounds, sounds, animations, iconography and store media.

