// Plays the hint solver on fresh Spider deals and reports how often it finds a winning line
// within a budget. Run: node tools/spider_solver_check.mjs [deals] [suits] [maxNodes]
import fs from 'node:fs';
const html = fs.readFileSync(new URL('../app/src/main/assets/freecell.html', import.meta.url), 'utf8');
const block = html.slice(html.indexOf('/* freecell-core:start'), html.indexOf('/* freecell-core:end */'));
const core = new Function(`${block}; return FreeCellCore;`)();
const [deals = 20, suits = 1, maxNodes = 200000] = process.argv.slice(2).map(Number);
let solved = 0, total = 0;
for (let seed = 1; seed <= deals; seed++) {
  const board = core.newBoard(seed, suits);
  const started = Date.now();
  const result = core.runToEnd(core.analyse(board, { maxNodes }));
  const ms = Date.now() - started;
  // Replay the line with canMove to prove it is legal and wins.
  if (result.status === 'solved') {
    let state = board;
    for (const move of result.line) { if (!core.canMove(state, move)) throw new Error(`illegal move in line, deal ${seed}`); state = core.applyMove(state, move); }
    if (!core.won(state)) throw new Error(`line does not win, deal ${seed}`);
    solved++;
  }
  total += ms;
  console.log(`deal ${seed}: ${result.status} (${result.line.length} moves, ${result.expanded} positions, ${ms} ms)`);
}
console.log(`${solved}/${deals} solved, ${suits} suit(s), average ${Math.round(total / deals)} ms`);
