// Makes the small card-rank font embedded in the page: Nunito (SIL Open Font License 1.1, see
// art/fonts/OFL.txt) keeping only the glyphs of the card ranks (A 2-10 J Q K). Every other glyph
// is emptied (the glyph ids stay, so cmap, hmtx and maxp stay valid) and the layout tables
// (GSUB, GPOS, GDEF, kern...) are dropped. Writes art/fonts/<name>-ranks.ttf and prints its size.
//
// Run: node tools/subset_rank_font.mjs art/fonts/Nunito-ExtraBold.ttf
import fs from 'node:fs';
import path from 'node:path';

const KEEP_CHARS = 'A0123456789JQK';
const DROP_TABLES = new Set(['GSUB', 'GPOS', 'GDEF', 'kern', 'DSIG', 'STAT', 'HVAR', 'MVAR', 'fvar', 'gvar', 'avar', 'BASE', 'JSTF', 'MATH', 'meta', 'LTSH', 'VDMX', 'hdmx', 'PCLT']);
const input = process.argv[2];
if (!input) throw new Error('usage: node tools/subset_rank_font.mjs FONT.ttf');
const font = fs.readFileSync(input);

const numTables = font.readUInt16BE(4);
const tables = {};
for (let i = 0; i < numTables; i++) {
  const at = 12 + i * 16, tag = font.toString('latin1', at, at + 4);
  tables[tag] = { offset: font.readUInt32BE(at + 8), length: font.readUInt32BE(at + 12) };
}
const table = tag => font.subarray(tables[tag].offset, tables[tag].offset + tables[tag].length);
if (!tables.glyf || !tables.loca || !tables.cmap) throw new Error('needs a TrueType font with glyf, loca and cmap');

// cmap: the Windows Unicode BMP subtable (format 4).
function glyphFor(code) {
  const cmap = table('cmap'), count = cmap.readUInt16BE(2);
  for (let i = 0; i < count; i++) {
    const platform = cmap.readUInt16BE(4 + i * 8), encoding = cmap.readUInt16BE(6 + i * 8), offset = cmap.readUInt32BE(8 + i * 8);
    if (platform !== 3 || encoding !== 1 || cmap.readUInt16BE(offset) !== 4) continue;
    const sub = cmap.subarray(offset), segX2 = sub.readUInt16BE(6), segs = segX2 / 2;
    const ends = 14, starts = ends + segX2 + 2, deltas = starts + segX2, ranges = deltas + segX2;
    for (let s = 0; s < segs; s++) {
      const end = sub.readUInt16BE(ends + s * 2), start = sub.readUInt16BE(starts + s * 2);
      if (code < start || code > end) continue;
      const delta = sub.readInt16BE(deltas + s * 2), range = sub.readUInt16BE(ranges + s * 2);
      if (!range) return (code + delta) & 0xffff;
      const glyph = sub.readUInt16BE(ranges + s * 2 + range + (code - start) * 2);
      return glyph ? (glyph + delta) & 0xffff : 0;
    }
  }
  return 0;
}

const head = table('head'), longLoca = head.readInt16BE(50) === 1;
const numGlyphs = table('maxp').readUInt16BE(4), loca = table('loca'), glyf = table('glyf');
const glyphRange = id => longLoca ? [loca.readUInt32BE(id * 4), loca.readUInt32BE(id * 4 + 4)] : [loca.readUInt16BE(id * 2) * 2, loca.readUInt16BE(id * 2 + 2) * 2];

// Glyphs to keep: .notdef, the rank characters and every component of a composite glyph.
const keep = new Set([0]);
const add = id => {
  if (keep.has(id)) return; keep.add(id);
  const [start, end] = glyphRange(id); if (end <= start || glyf.readInt16BE(start) >= 0) return;
  let at = start + 10;
  for (;;) {
    const flags = glyf.readUInt16BE(at), component = glyf.readUInt16BE(at + 2); add(component);
    at += 4 + (flags & 1 ? 4 : 2) + (flags & 8 ? 2 : flags & 0x40 ? 4 : flags & 0x80 ? 8 : 0);
    if (!(flags & 0x20)) break;
  }
};
for (const char of KEEP_CHARS) { const id = glyphFor(char.charCodeAt(0)); if (!id) throw new Error(`no glyph for ${char}`); add(id); }

// New glyf and loca: kept glyphs copied (4-byte aligned), every other glyph empty.
const parts = [], offsets = [];
let size = 0;
for (let id = 0; id < numGlyphs; id++) {
  offsets.push(size);
  if (!keep.has(id)) continue;
  const [start, end] = glyphRange(id), data = glyf.subarray(start, end);
  const padded = Buffer.alloc((data.length + 3) & ~3); data.copy(padded);
  parts.push(padded); size += padded.length;
}
offsets.push(size);
const newGlyf = Buffer.concat(parts), newLoca = Buffer.alloc((numGlyphs + 1) * 4);
offsets.forEach((offset, i) => newLoca.writeUInt32BE(offset, i * 4));
const newHead = Buffer.from(head); newHead.writeInt16BE(1, 50); newHead.writeUInt32BE(0, 8);

const out = {};
for (const tag of Object.keys(tables)) if (!DROP_TABLES.has(tag)) out[tag] = Buffer.from(table(tag));
out.glyf = newGlyf; out.loca = newLoca; out.head = newHead;

const checksum = data => { const padded = Buffer.alloc((data.length + 3) & ~3); data.copy(padded); let sum = 0; for (let i = 0; i < padded.length; i += 4) sum = (sum + padded.readUInt32BE(i)) >>> 0; return sum; };
const tags = Object.keys(out).sort(), count = tags.length;
let searchRange = 1, entrySelector = 0; while (searchRange * 2 <= count) { searchRange *= 2; entrySelector++; }
const header = Buffer.alloc(12 + count * 16);
header.writeUInt32BE(font.readUInt32BE(0), 0); header.writeUInt16BE(count, 4);
header.writeUInt16BE(searchRange * 16, 6); header.writeUInt16BE(entrySelector, 8); header.writeUInt16BE(count * 16 - searchRange * 16, 10);
let offset = header.length; const bodies = [];
tags.forEach((tag, i) => {
  const data = out[tag], padded = Buffer.alloc((data.length + 3) & ~3); data.copy(padded);
  header.write(tag, 12 + i * 16, 'latin1'); header.writeUInt32BE(checksum(data), 16 + i * 16);
  header.writeUInt32BE(offset, 20 + i * 16); header.writeUInt32BE(data.length, 24 + i * 16);
  bodies.push(padded); offset += padded.length;
});
const result = Buffer.concat([header, ...bodies]);
const headAt = header.readUInt32BE(12 + tags.indexOf('head') * 16 + 8);
result.writeUInt32BE((0xB1B0AFBA - checksum(result)) >>> 0, headAt + 8);
const target = path.join('art', 'fonts', path.basename(input, '.ttf') + '-ranks.ttf');
fs.writeFileSync(target, result);
console.log(`${target}: ${result.length} bytes (${keep.size} glyphs kept of ${numGlyphs})`);
