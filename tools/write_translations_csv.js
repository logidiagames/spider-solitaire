#!/usr/bin/env node
// Writes docs/translations_review.csv: one row per text (key, English, then every other language),
// for native speakers to review. Plural forms are written as "one: …; few: …; other: …".
//   node tools/write_translations_csv.js
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const html = fs.readFileSync(path.join(assets, 'freecell.html'), 'utf8');
const block = html.slice(html.indexOf('      const EN_STRINGS={'), html.indexOf('      let languagePack=null;'));
const english = new Function(`${block}; return {strings: EN_STRINGS, tutorial: EN_TUTORIAL, praise: EN_PRAISE};`)();
const codes = ['pt', 'pt-BR', 'es', 'fr', 'it', 'de', 'nl', 'pl', 'tr', 'ru', 'uk', 'ja', 'ko', 'zh-Hans', 'id', 'sv', 'cs'];
const packs = Object.fromEntries(codes.map(code => [code, JSON.parse(fs.readFileSync(path.join(assets, 'i18n', `${code}.json`), 'utf8'))]));
const text = value => value && typeof value === 'object' ? Object.entries(value).map(([form, words]) => `${form}: ${words}`).join('; ') : String(value ?? '');
const cell = value => `"${text(value).replace(/"/g, '""')}"`;
const rows = [['key', 'en', ...codes].map(cell).join(',')];
for (const [key, value] of Object.entries(english.strings)) rows.push([key, value, ...codes.map(code => packs[code].strings[key])].map(cell).join(','));
english.tutorial.forEach((step, index) => {
  rows.push([`tutorial.${index + 1}.title`, step[0], ...codes.map(code => packs[code].tutorial?.[index]?.[0])].map(cell).join(','));
  rows.push([`tutorial.${index + 1}.body`, step[1], ...codes.map(code => packs[code].tutorial?.[index]?.[1])].map(cell).join(','));
});
rows.push(['praise', english.praise.join(' | '), ...codes.map(code => (packs[code].praise || []).join(' | '))].map(cell).join(','));
fs.writeFileSync(path.join(root, 'docs/translations_review.csv'), '\ufeff' + rows.join('\r\n') + '\r\n');
console.log(`docs/translations_review.csv: ${rows.length - 1} rows × ${codes.length + 1} languages`);
