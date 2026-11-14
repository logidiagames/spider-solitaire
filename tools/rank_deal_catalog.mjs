// Sorts each list of app/src/main/assets/deal_catalog.json from easiest to hardest, by how many
// positions the game's solver needs to win the deal (fewer = easier; ties by the length of the
// winning line). The adaptive difficulty draws from a part of this order chosen by the player's
// results. Run: node tools/rank_deal_catalog.mjs
import fs from 'node:fs';
const assets = new URL('../app/src/main/assets/', import.meta.url);
const html = fs.readFileSync(new URL('freecell.html', assets), 'utf8');
const block = html.slice(html.indexOf('/* freecell-core:start'), html.indexOf('/* freecell-core:end */'));
const core = new Function(`${block}; return FreeCellCore;`)();
const file = new URL('deal_catalog.json', assets);
const catalog = JSON.parse(fs.readFileSync(file, 'utf8'));
const effort = {};
for (const [tier, suits, maxNodes] of [['easy', 1, 100000], ['medium', 2, 150000]]) {
  const rated = catalog[tier].map((seed, index) => {
    const result = core.runToEnd(core.analyse(core.newBoard(seed, suits), { maxNodes }));
    if (index % 25 === 0) process.stderr.write(`${tier}: ${index}/${catalog[tier].length}\n`);
    return { seed, expanded: result.status === 'solved' ? result.expanded : Infinity, moves: result.line.length };
  }).filter(deal => deal.expanded !== Infinity).sort((a, b) => a.expanded - b.expanded || a.moves - b.moves);
  catalog[tier] = rated.map(deal => deal.seed);
  effort[tier] = rated.map(deal => deal.expanded);
}
catalog.catalogVersion = 2;
catalog.order = 'easiest first (solver positions, tools/rank_deal_catalog.mjs)';
catalog.daily = { easy: catalog.easy.length, medium: catalog.medium.length, hard: 0 };
fs.writeFileSync(file, JSON.stringify(catalog));
const q = (list, f) => list[Math.floor((list.length - 1) * f)];
console.log(JSON.stringify({ easy: catalog.easy.length, medium: catalog.medium.length,
  easyEffort: [0, .25, .5, .75, 1].map(f => q(effort.easy, f)), mediumEffort: [0, .25, .5, .75, 1].map(f => q(effort.medium, f)),
  easiest: catalog.easy.slice(0, 6), mediumEasiest: catalog.medium.slice(0, 6), mediumMid: q(catalog.medium, .4) }));
