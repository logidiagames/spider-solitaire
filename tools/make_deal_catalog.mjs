// Builds app/src/main/assets/deal_catalog.json for Spider: per tier, deal numbers the game's own hint
// solver wins from the start, so Classic games and the Daily can always be won. 4 Suits (hard) has
// no list: the solver rarely finishes a 4-suit deal within a phone's budget, so any number is used.
// Run: node tools/make_deal_catalog.mjs [easyCount] [mediumCount]
// Entries must only ever be appended (daily.<tier> freezes what the Daily draws from).
import fs from 'node:fs';
const assets = new URL('../app/src/main/assets/', import.meta.url);
const html = fs.readFileSync(new URL('freecell.html', assets), 'utf8');
const block = html.slice(html.indexOf('/* freecell-core:start'), html.indexOf('/* freecell-core:end */'));
const core = new Function(`${block}; return FreeCellCore;`)();
const [easyCount = 400, mediumCount = 200] = process.argv.slice(2).map(Number);
const wins = (seed, suits, maxNodes) => core.runToEnd(core.analyse(core.newBoard(seed, suits), { maxNodes })).status === 'solved';
function collect(suits, count, maxNodes) {
  const list = [];
  for (let n = 1; list.length < count; n++) {
    const seed = 1 + ((n * 2654435761) >>> 0) % core.MAX_DEAL_NUMBER; // spread over the whole range
    if (wins(seed, suits, maxNodes)) list.push(seed);
    if (n % 25 === 0) process.stderr.write(`${suits} suit(s): ${list.length}/${count} after ${n} deals\n`);
  }
  return list;
}
const easy = collect(1, easyCount, 100000);
const medium = collect(2, mediumCount, 150000);
// Warm-up deals: the first five that are won with both 1 and 2 suits.
const warmup = medium.filter(seed => easy.includes(seed) || wins(seed, 1, 100000)).slice(0, 5);
const catalog = { catalogVersion: 1, source: 'tools/make_deal_catalog.mjs (Spider hint solver)', easy, medium, hard: [], daily: { easy: easy.length, medium: medium.length, hard: 0 } };
fs.writeFileSync(new URL('deal_catalog.json', assets), JSON.stringify(catalog));
console.log(JSON.stringify({ easy: easy.length, medium: medium.length, warmup }));
