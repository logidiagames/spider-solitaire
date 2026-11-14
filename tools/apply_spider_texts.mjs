// Puts the Spider texts into assets/i18n/<code>.json: the translations in tools/spider_texts.json,
// and for languages not translated yet the English text of every new or changed key (so no pack
// keeps FreeCell's rules). Run: node tools/apply_spider_texts.mjs
import fs from 'node:fs';
const assets = new URL('../app/src/main/assets/', import.meta.url);
const html = fs.readFileSync(new URL('freecell.html', assets), 'utf8');
const texts = JSON.parse(fs.readFileSync(new URL('spider_texts.json', import.meta.url), 'utf8'));
const keys = Object.keys(texts.pt.strings);
const english = Object.fromEntries(keys.map(key => { const match = html.match(new RegExp(`"${key}":("(?:[^"\\\\]|\\\\.)*")`)); if (!match) throw new Error(`no English text for ${key}`); return [key, JSON.parse(match[1])]; }));
const tutorialStart = html.indexOf('const EN_TUTORIAL=') + 'const EN_TUTORIAL='.length;
const englishTutorial = JSON.parse(html.slice(tutorialStart, html.indexOf(';', html.indexOf(']]', tutorialStart)))).map(([title, body]) => [title, body]);
const dir = new URL('i18n/', assets);
for (const name of fs.readdirSync(dir).filter(file => file.endsWith('.json'))) {
  const code = name.slice(0, -5), file = new URL(name, dir), pack = JSON.parse(fs.readFileSync(file, 'utf8'));
  const own = texts[code];
  pack.strings = { ...pack.strings, ...(own ? own.strings : english) };
  pack.tutorial = own ? own.tutorial : englishTutorial;
  fs.writeFileSync(file, JSON.stringify(pack, null, 1) + '\n');
}
console.log('updated', fs.readdirSync(dir).length, 'packs');
