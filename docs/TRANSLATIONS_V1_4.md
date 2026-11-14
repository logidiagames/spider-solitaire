# v1.4 texts for review

## Part 1: smart hint

| Key | EN | PT | ES | FR | IT | DE |
| --- | --- | --- | --- | --- | --- | --- |
| `deadEndTitle` (replaces "No winning moves were found") | There is no way to win from here | Já não há forma de ganhar a partir daqui | Desde aquí ya no se puede ganar | Impossible de gagner à partir d’ici | Da qui non si può più vincere | Von hier aus ist kein Sieg mehr möglich |
| `deadEndHint` | Try Undo or start a New Game | Experimenta Desfazer ou começa um Novo jogo | Prueba Deshacer o empieza una Nueva partida | Essayez Annuler ou lancez une Nouvelle partie | Prova Annulla o inizia una Nuova partita | Versuche Rückgängig oder starte ein Neues Spiel |
| `tipFreeCells` | Tip: free cells are only for temporary storage. Try to keep them empty. | Dica: as células livres servem só para guardar cartas por pouco tempo. Tenta mantê-las vazias. | Consejo: las celdas libres solo sirven para guardar cartas un momento. Intenta mantenerlas vacías. | Astuce : les cellules libres ne servent qu’à poser une carte un moment. Essayez de les garder vides. | Suggerimento: le celle libere servono solo come deposito temporaneo. Cerca di tenerle vuote. | Tipp: Freie Zellen sind nur zum kurzen Ablegen da. Halte sie möglichst leer. |
| `gotIt` | Got it | Percebi | Entendido | Compris | Ho capito | Verstanden |
| `noHintsLeft` | No hints left in this game | Já não tens dicas neste jogo | No te quedan pistas en esta partida | Plus d’indice pour cette partie | Suggerimenti esauriti per questa partita | Keine Hinweise mehr in diesem Spiel |

## Part 2: card size

| Key | EN | PT | ES | FR | IT | DE |
| --- | --- | --- | --- | --- | --- | --- |
| `accessibility` | Accessibility | Acessibilidade | Accesibilidad | Accessibilité | Accessibilità | Barrierefreiheit |
| `cardSize` | Card size | Tamanho das cartas | Tamaño de las cartas | Taille des cartes | Dimensione delle carte | Kartengröße |
| `sizeNormal` | Normal | Normal | Normal | Normale | Normale | Normal |
| `sizeLarge` | Large | Grande | Grande | Grande | Grande | Groß |
| `sizeExtraLarge` | Extra large | Muito grande | Muy grande | Très grande | Molto grande | Sehr groß |

## Part 4: 18 languages

All texts of the game, in every language, are in `docs/translations_review.csv` (key, English, one
column per language; plural forms as "one: …; few: …; other: …"). Regenerate it with
`node tools/write_translations_csv.js` after changing a language file
(`app/src/main/assets/i18n/<code>.json`). The twelve new languages (pt-BR, nl, pl, tr, ru, uk, ja,
ko, zh-Hans, id, sv, cs) are first drafts and need a native speaker's review; a language can be
marked Beta in `languages_config.beta` meanwhile.

