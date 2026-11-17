// Rates how easy Spider deals are for a person, not for the solver: a simple player (prefers
// moves that build same-suit runs, then moves that turn a face-down card, deals when nothing
// useful is left, never repeats a position) plays each deal many times with random tie-breaks.
// The share of those games it wins is the deal's ease (1 = it always wins). The solver's effort
// (tools/rank_deal_catalog.mjs) says little about this: a deal the solver wins at once can still
// need a long, exact line that a person does not find.
//
// Rewrites app/src/main/assets/deal_catalog.json: for `easy` (1 suit) and `medium` (2 suits), the
// candidates (the current list plus fresh deal numbers) are sorted by ease, easiest first; ties go
// to the shorter win. Only deals won at least once (by this player or, for the current list, by the
// solver) are kept, spread over the whole range so the five adaptive bands go from deals almost
// everyone wins to deals that need care.
//
// Run: node tools/rate_deal_ease.mjs [freshPerTier]
import fs from 'node:fs';

const assets = new URL('../app/src/main/assets/', import.meta.url);
const html = fs.readFileSync(new URL('freecell.html', assets), 'utf8');
const block = html.slice(html.indexOf('/* freecell-core:start'), html.indexOf('/* freecell-core:end */'));
const core = new Function(`${block}; return FreeCellCore;`)();
const file = new URL('deal_catalog.json', assets);
const catalog = JSON.parse(fs.readFileSync(file, 'utf8'));
const fresh = Number(process.argv[2] || 2500);
const PLAYS = 24, STEP_LIMIT = 900;

function mulberry(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

// One game by the simple player. Returns the number of moves of a win, or 0 for a loss.
function play(seed, suits, random) {
  let state = core.newBoard(seed, suits);
  const seen = new Set([core.positionKey(state)]);
  for (let step = 1; step <= STEP_LIMIT; step++) {
    const options = [];
    for (const move of core.listMoves(state)) {
      if (move.from.type === 'stock') continue;
      const column = state.tableau[move.from.pile], card = column[move.from.index];
      const under = column[move.from.index - 1], target = state.tableau[move.to.pile];
      const top = target[target.length - 1];
      const next = core.applyMove(state, move), key = core.positionKey(next);
      if (seen.has(key)) continue;
      let score = 0;
      if (core.runsDone(next) > core.runsDone(state)) score += 400;
      if (top && top.s === card.s) score += 100;
      if (core.turnsCard(state, move)) score += 60;
      if (move.from.index === 0 && top) score += 45;
      if (!top) score -= 25;
      // Moving a run from one card to another of the same rank gains nothing visible.
      if (top && under && under.up !== false && under.r === top.r && under.s === card.s) score -= 200;
      if (top && under && under.up !== false && under.r === card.r + 1 && top.s !== card.s) score -= 60;
      score += random() * 20;
      options.push({ move, next, key, score });
    }
    options.sort((a, b) => b.score - a.score);
    let pick = options.find(option => option.score > 15);
    if (!pick && core.canMove(state, core.DEAL)) {
      const next = core.applyMove(state, core.DEAL);
      pick = { next, key: core.positionKey(next) };
    }
    if (!pick) pick = options.find(option => option.score > -40);
    if (!pick) return 0;
    state = pick.next; seen.add(pick.key);
    if (core.won(state)) return step;
  }
  return 0;
}

function rate(seed, suits) {
  const random = mulberry(seed * 7919 + suits);
  let wins = 0, shortest = Infinity;
  for (let i = 0; i < PLAYS; i++) { const moves = play(seed, suits, random); if (moves) { wins++; shortest = Math.min(shortest, moves); } }
  return { seed, ease: wins / PLAYS, moves: shortest };
}

const pickFresh = (count, salt) => {
  const random = mulberry(salt), out = new Set();
  while (out.size < count) out.add(1 + Math.floor(random() * (core.MAX_DEAL_NUMBER - 1)));
  return [...out];
};

const report = {};
for (const [tier, suits, keep] of [['easy', 1, 400], ['medium', 2, 200]]) {
  const solverWon = new Set(catalog[tier]);
  const candidates = [...new Set([...catalog[tier], ...pickFresh(tier === 'easy' ? fresh : Math.round(fresh * 0.6), suits * 101)])];
  const rated = candidates.map((seed, i) => {
    if (i % 250 === 0) process.stderr.write(`${tier}: ${i}/${candidates.length}\n`);
    return rate(seed, suits);
  }).filter(deal => deal.ease > 0 || solverWon.has(deal.seed));
  rated.sort((a, b) => b.ease - a.ease || a.moves - b.moves || a.seed - b.seed);
  // Spread `keep` deals evenly over the rated range (easiest first).
  const list = rated.length <= keep ? rated : Array.from({ length: keep }, (_, i) => rated[Math.floor(i * rated.length / keep)]);
  catalog[tier] = list.map(deal => deal.seed);
  const q = f => list[Math.floor((list.length - 1) * f)];
  report[tier] = { rated: rated.length, kept: list.length, ease: [0, .2, .4, .6, .8, 1].map(f => q(f).ease),
    easiest: list.slice(0, 8).map(d => `${d.seed}:${d.ease}:${d.moves}`) };
}
catalog.catalogVersion = 3;
catalog.order = 'easiest first for a person (share of wins of a simple player, tools/rate_deal_ease.mjs)';
catalog.daily = { easy: catalog.easy.length, medium: catalog.medium.length, hard: 0 };
fs.writeFileSync(file, JSON.stringify(catalog));
console.log(JSON.stringify(report, null, 1));
