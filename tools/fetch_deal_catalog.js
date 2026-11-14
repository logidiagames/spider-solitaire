#!/usr/bin/env node
// Builds app/src/main/assets/deal_catalog.json from the deal lists published by FreeCell Solutions
// (https://freecellgamesolutions.com/stats.html). Run it once when the catalog needs refreshing:
//
//   node --use-system-ca tools/fetch_deal_catalog.js            (downloads the page)
//   node tools/fetch_deal_catalog.js --file saved-stats.html     (reads a saved copy instead)
//
// It keeps only the extracted deal numbers, the URL and the date, never a copy of the page; the app
// never calls the site. It refuses to write anything if the page no longer looks as expected.
// Deal numbers on that site are the game's own seeds (Microsoft dealing; see core tests).
// --use-system-ca lets Node trust the Windows certificate store (needed behind antivirus HTTPS scanning).
'use strict';
const fs = require('fs');
const path = require('path');

const SOURCE = 'https://freecellgamesolutions.com/stats.html';
const OUTPUT = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'deal_catalog.json');
const IMPOSSIBLE = [11982, 146692, 186216, 455889, 495505, 512118, 517776, 781948];
const MEDIUM_MAX = 64000;

function fail(message) {
  console.error(`ERROR: ${message}`);
  console.error('Nothing was written. The page layout may have changed: check it by hand before updating the parser.');
  process.exit(1);
}

// Each list is a <div class="StatsGames"> of links; the heading above it says how many deals it has.
function parse(html) {
  const lists = [...html.matchAll(/<h2>([^<]*)<\/h2>\s*<div class="StatsGames">([\s\S]*?)<\/div>/g)].map(match => ({
    heading: match[1].replace(/&#\d+;/g, '').trim(),
    numbers: match[2].replace(/<[^>]+>/g, ' ').trim().split(/\s+/).filter(Boolean).map(Number)
  }));
  const find = pattern => {
    const found = lists.filter(list => pattern.test(list.heading));
    if (found.length !== 1) fail(`expected one list for ${pattern}, found ${found.length}`);
    const list = found[0], stated = Number((list.heading.match(/^(\d+)/) || [])[1]);
    if (list.numbers.some(n => !Number.isInteger(n))) fail(`a non-number in "${list.heading}"`);
    if (Number.isInteger(stated) && stated !== list.numbers.length) fail(`"${list.heading}" lists ${list.numbers.length} deals`);
    return list.numbers;
  };
  const easyLow = find(/easy FreeCell games between 1 and 32000/);
  const easyHigh = find(/easy FreeCell games between 32001 and 64000/);
  const easySorted = find(/^extra easy games/);
  const hard = [...find(/difficult FreeCell games between 1 and 32000/),
    ...find(/difficult FreeCell games between 32001 and 64000/),
    ...find(/difficult FreeCell games between 64001 and 100000/)];
  const impossibleText = (html.match(/Impossible FreeCell game numbers[^:]*:([^<]*)/) || [])[1] || '';
  const impossible = impossibleText.split(/[^\d]+/).filter(Boolean).map(Number);
  return { easyLow, easyHigh, easySorted, hard, impossible };
}

function check({ easyLow, easyHigh, easySorted, hard, impossible }) {
  const unique = list => new Set(list).size === list.length;
  const easyRows = new Set([...easyLow, ...easyHigh]);
  if (easySorted.length !== 128) fail(`Easy (sorted) has ${easySorted.length} deals, expected 128`);
  if (easyRows.size !== 128 || !unique(easySorted)) fail('Easy rows do not hold 128 distinct deals');
  if (!easySorted.every(n => easyRows.has(n))) fail('the sorted Easy list differs from the two Easy rows');
  if (hard.length !== 675 || !unique(hard)) fail(`Hard has ${hard.length} deals (${new Set(hard).size} distinct), expected 675`);
  if (hard.some(n => easyRows.has(n))) fail('a deal is both Easy and Hard');
  if (easySorted.some(n => n < 1 || n > MEDIUM_MAX)) fail(`an Easy deal is outside 1..${MEDIUM_MAX}`);
  if (hard.some(n => n < 1 || n > 100000)) fail('a Hard deal is outside 1..100000');
  if (impossible.join() !== IMPOSSIBLE.join()) fail(`impossible deals are ${impossible.join(', ')}`);
  if ([...easySorted, ...hard].some(n => IMPOSSIBLE.includes(n))) fail('an impossible deal is in a tier');
  for (const n of [164, 892, 25904]) if (!easyRows.has(n)) fail(`Easy is missing ${n}`);
  for (const n of [169, 31465]) if (!hard.includes(n)) fail(`Hard is missing ${n}`);
}

async function main() {
  const fileArg = process.argv.indexOf('--file');
  let html;
  if (fileArg > 0) html = fs.readFileSync(process.argv[fileArg + 1], 'utf8');
  else {
    const response = await fetch(SOURCE, { headers: { 'User-Agent': 'FreeCell deal catalog tool (Nexus Play Labs)' } });
    if (!response.ok) fail(`download failed: HTTP ${response.status}`);
    html = await response.text();
  }
  const lists = parse(html);
  check(lists);
  // A published catalog is only ever appended to: its entries keep their order (past Daily
  // Challenges depend on it) and a deal that left the page is an error, not a removal.
  const previous = fs.existsSync(OUTPUT) ? JSON.parse(fs.readFileSync(OUTPUT, 'utf8')) : null;
  const extend = (old, fresh) => {
    if (!old) return fresh;
    const now = new Set(fresh), gone = old.filter(n => !now.has(n));
    if (gone.length) fail(`deals left the published list: ${gone.slice(0, 10).join(', ')}`);
    const known = new Set(old);
    return [...old, ...fresh.filter(n => !known.has(n))];
  };
  const easy = extend(previous && previous.easy, lists.easySorted);
  const hard = extend(previous && previous.hard, [...lists.hard].sort((a, b) => a - b));
  const grew = previous && (easy.length !== previous.easy.length || hard.length !== previous.hard.length);
  const catalog = {
    source: SOURCE,
    retrievedAt: new Date().toISOString().slice(0, 10),
    catalogVersion: previous ? previous.catalogVersion + (grew ? 1 : 0) : 1,
    easy,
    hard,
    impossible: IMPOSSIBLE,
    medium: { rule: `every deal from 1 to ${MEDIUM_MAX} that is not easy, hard or impossible`, min: 1, max: MEDIUM_MAX },
    // The Daily draws only from these first entries, frozen at the first version.
    daily: previous && previous.daily ? previous.daily : { easyCount: easy.length, hardCount: hard.length }
  };
  fs.writeFileSync(OUTPUT, `${JSON.stringify(catalog)}\n`);
  const medium = MEDIUM_MAX - catalog.easy.length - catalog.hard.filter(n => n <= MEDIUM_MAX).length - IMPOSSIBLE.filter(n => n <= MEDIUM_MAX).length;
  console.log(`Easy ${catalog.easy.length} (sorted by complexity), Hard ${catalog.hard.length}, impossible ${IMPOSSIBLE.length}, Medium ${medium} by rule.`);
  console.log(`Written ${path.relative(process.cwd(), OUTPUT)} (catalog version ${catalog.catalogVersion}, ${catalog.retrievedAt}).`);
}

main().catch(error => fail(error.message));
