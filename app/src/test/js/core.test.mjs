// Unit tests for the pure rules block of freecell.html (between the freecell-core markers).
// Run: node --test app/src/test/js   (also runs as part of ./gradlew test when Node is installed)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import url from 'node:url';

const here = path.dirname(url.fileURLToPath(import.meta.url));
const assets = path.resolve(here, '../../main/assets');
const html = fs.readFileSync(path.join(assets, 'freecell.html'), 'utf8');
const block = html.slice(html.indexOf('/* freecell-core:start'), html.indexOf('/* freecell-core:end */'));
const core = new Function(`${block}; return FreeCellCore;`)();
const config = core.normalizeConfig({});

test('JS defaults match the bundled Remote Config defaults', () => {
  for (const key of ['progression_config', 'daily_challenge_config', 'economy_config', 'themes_config', 'daily_bonus_config', 'missions_config', 'remove_ads_config', 'rating_config', 'store_config', 'events_config', 'rewarded_config', 'notifications_config', 'stars_config', 'achievements_config', 'difficulty_config', 'rivals_config', 'hint_config', 'tips_config', 'feel_config', 'languages_config', 'performance_config']) {
    const bundled = JSON.parse(fs.readFileSync(path.join(assets, `${key}_defaults.json`), 'utf8'));
    assert.deepEqual(core.DEFAULTS[key], bundled, key);
  }
});

test('docs/REMOTE_CONFIG_ALL.md shows every bundled default as it is (tools/write_remote_config_all.js)', () => {
  const doc = fs.readFileSync(path.resolve(here, '../../../../docs/REMOTE_CONFIG_ALL.md'), 'utf8');
  const files = fs.readdirSync(assets).filter(name => name.endsWith('_defaults.json'));
  assert.equal(files.length, 23);
  for (const name of files) assert.ok(doc.includes(JSON.stringify(JSON.parse(fs.readFileSync(path.join(assets, name), 'utf8')))), `${name} is out of date in REMOTE_CONFIG_ALL.md`);
});

test('config is clamped and falls back on wrong types', () => {
  const c = core.normalizeConfig({ economy_config: { win_rewards: { medium: -5, daily: 'x', random: 99 }, bonus_fast: { seconds: 1 } }, themes_config: { free: ['neon', 'bogus'] } });
  assert.equal(c.economy.win_rewards.medium, 0);
  assert.equal(c.economy.win_rewards.easy, 10);
  assert.equal(c.economy.win_rewards.random, undefined);
  assert.equal(c.economy.win_rewards.daily, 50);
  assert.equal(c.economy.bonus_fast.seconds, 30);
  assert.deepEqual(c.themes.free, ['classic', 'neon']);
});

test('win rewards: base by tier, no-undo, fast and capped daily streak bonus', () => {
  assert.equal(core.winReward(config, { mode: 'random', tier: 'medium', usedUndo: true, seconds: 900 }).total, 20);
  assert.equal(core.winReward(config, { mode: 'random', tier: 'medium', usedUndo: false, seconds: 599 }).total, 40);
  assert.equal(core.winReward(config, { mode: 'random', tier: 'easy', usedUndo: true, seconds: 900 }).total, 10);
  assert.equal(core.winReward(config, { mode: 'random', tier: 'hard', usedUndo: true, seconds: 900 }).total, 30);
  assert.equal(core.winReward(config, { mode: 'numbered', tier: 'hard', usedUndo: true, seconds: 900 }).total, 30);
  assert.equal(core.winReward(config, { mode: 'numbered', tier: 'unrated', usedUndo: true, seconds: 900 }).total, 20);
  assert.equal(core.winReward(config, { mode: 'numbered', tier: 'impossible', usedUndo: false, seconds: 1 }).total, 0);
  // An older Winnable game in progress pays as Easy.
  assert.equal(core.winReward(config, { mode: 'winnable', usedUndo: true, seconds: 600 }).total, 10);
  const daily = core.winReward(config, { mode: 'daily', usedUndo: true, seconds: 900, dailyStreak: 4 });
  assert.equal(daily.streak, 8);
  assert.equal(core.winReward(config, { mode: 'daily', usedUndo: true, seconds: 900, dailyStreak: 40 }).streak, 20);
  assert.equal(core.winReward(config, { mode: 'developer', usedUndo: false, seconds: 1 }).total, 0);
});

test('a deal pays only once; the list keeps the last 300 keys', () => {
  let paid = [];
  const key = core.payKey({ mode: 'numbered', seed: 42 });
  const first = core.claimPayment(paid, key, config);
  assert.equal(first.pays, true);
  assert.equal(core.claimPayment(first.paidKeys, key, config).pays, false);
  for (let i = 0; i < 310; i++) paid = core.claimPayment(paid, `random:${i}`, config).paidKeys;
  assert.equal(paid.length, 300);
  assert.equal(paid.includes('random:0'), false);
  const off = core.normalizeConfig({ economy_config: { pay_once_per_deal_seed: false } });
  assert.equal(core.claimPayment(['numbered:42'], 'numbered:42', off).pays, true);
  assert.equal(core.payKey({ mode: 'daily', dailyDate: '2026-10-23' }), 'daily:2026-10-23');
  assert.equal(core.payKey({ mode: 'random', seed: 5, dealId: 9 }), 'random:5');
});

test('balance never goes negative', () => {
  assert.equal(core.addCoins(10, -50), 0);
  assert.equal(core.addCoins(10, 15), 25);
});

test('theme purchase rules', () => {
  assert.deepEqual(core.buyTheme(config, { coins: 100, unlocked: [] }, 'neon'), { ok: false, reason: 'not_enough', missing: 200 });
  const bought = core.buyTheme(config, { coins: 350, unlocked: [] }, 'neon');
  assert.equal(bought.ok, true);
  assert.equal(bought.coins, 50);
  assert.deepEqual(bought.unlocked, ['neon']);
  assert.equal(core.buyTheme(config, { coins: 999, unlocked: ['neon'] }, 'neon').reason, 'owned');
  assert.equal(core.buyTheme(config, { coins: 999, unlocked: [] }, 'aurora').reason, 'owned');
  assert.equal(core.buyTheme(config, { coins: 999, unlocked: [] }, 'bogus').reason, 'unknown');
  assert.equal(core.equippableTheme(config, [], 'moonstone'), 'classic');
  assert.equal(core.equippableTheme(config, ['moonstone'], 'moonstone'), 'moonstone');
});

const win = (history, key, playedOn = key, seconds = 300) => core.recordDailyWin(history, key, { seconds, moves: 90, score: 400, playedOn });

test('streak: consecutive wins, today still pending keeps yesterday', () => {
  let h = {};
  for (const d of ['2026-10-20', '2026-10-21', '2026-10-22']) h = win(h, d);
  assert.equal(core.streakInfo(h, '2026-10-23', config).streak, 3);
  h = win(h, '2026-10-23');
  const info = core.streakInfo(h, '2026-10-23', config);
  assert.equal(info.streak, 4);
  assert.equal(info.milestone, null);
});

test('streak: a missed day ends it (no forgiveness, no shields)', () => {
  let h = {};
  for (const d of ['2026-10-18', '2026-10-19', '2026-10-21', '2026-10-22']) h = win(h, d);
  assert.equal(core.streakInfo(h, '2026-10-22').streak, 2);
  // Yesterday won, today not yet: the streak still counts until today ends.
  assert.equal(core.streakInfo(h, '2026-10-23').streak, 2);
  assert.equal(core.streakInfo(h, '2026-10-24').streak, 0);
  assert.equal(core.normalizeConfig({ daily_challenge_config: { forgiveness_days_per_month: 3 } }).daily.forgiveness_days_per_month, undefined);
});

test('streak: wins replayed on a later date never count', () => {
  let h = {};
  h = win(h, '2026-10-21');
  h = win(h, '2026-10-22', '2026-10-23');
  // The late win on 10-22 does not count, and 10-22 itself is a missed day: the streak ended.
  assert.equal(core.streakInfo(h, '2026-10-23', config).streak, 0);
  assert.equal(core.streakInfo(h, '2026-10-22', config).streak, 1);
  assert.equal(core.streakInfo(h, '2026-10-24', config).streak, 0);
});

test('streak: empty history and milestones', () => {
  assert.equal(core.streakInfo({}, '2026-10-23', config).streak, 0);
  let h = {};
  for (const d of ['2026-10-21', '2026-10-22', '2026-10-23']) h = win(h, d);
  assert.equal(core.streakInfo(h, '2026-10-23', config).milestone, 3);
});

test('today: clock moved back keeps the latest date; debug offset shifts it', () => {
  const now = new Date(2026, 9, 23, 12, 0);
  assert.equal(core.today(now), '2026-10-23');
  assert.equal(core.today(now, '2026-10-25'), '2026-10-25');
  assert.equal(core.today(now, '2026-10-20'), '2026-10-23');
  assert.equal(core.today(now, null, 3), '2026-10-26');
});

test('dates: month boundaries and daylight-saving changes', () => {
  assert.equal(core.addDays('2026-10-31', 1), '2026-11-01');
  assert.equal(core.addDays('2028-03-01', -1), '2028-02-29');
  assert.equal(core.addDays('2026-03-28', 2), '2026-03-30');
  assert.equal(core.addDays('2026-10-24', 2), '2026-10-26');
  const before = new Date(2026, 2, 29, 0, 30), after = new Date(2026, 2, 29, 23, 30);
  assert.equal(core.dateKey(before), core.dateKey(after));
});

test('best time comparison', () => {
  let h = {};
  h = win(h, '2026-10-21', '2026-10-21', 250);
  h = win(h, '2026-10-22', '2026-10-22', 242);
  h = win(h, '2026-10-23', '2026-10-23', 201);
  const c = core.dailyComparison(h, '2026-10-23');
  assert.equal(c.today, 201);
  assert.equal(c.yesterday, 242);
  assert.equal(c.delta, -41);
  assert.deepEqual(c.best, { seconds: 201, date: '2026-10-23' });
  assert.equal(core.formatDuration(201), '3:21');
  assert.equal(core.formatDelta(-41), '−0:41');
  assert.equal(core.dailyComparison({}, '2026-10-23').yesterday, null);
});

test('history keeps attempts, best time and the configured number of days', () => {
  let h = core.recordDailyAttempt({}, '2026-10-23');
  h = core.recordDailyAttempt(h, '2026-10-23');
  h = win(h, '2026-10-23', '2026-10-23', 400);
  h = win(h, '2026-10-23', '2026-10-23', 500);
  assert.equal(h['2026-10-23'].attempts, 2);
  assert.equal(h['2026-10-23'].seconds, 400);
  const pruned = core.pruneHistory({ ...h, '2025-01-01': { won: true } }, '2026-10-23', 400);
  assert.deepEqual(Object.keys(pruned), ['2026-10-23']);
});

const card = (rank, suit) => ({ r: rank, s: suit });
test('stuck: no legal move at all', () => {
  // Every column ends in a card that fits nowhere; all free cells are full.
  const state = {
    foundations: [0, 0, 0, 0],
    cells: [card(13, 0), card(13, 1), card(13, 2), card(13, 3)],
    tableau: [[card(5, 0)], [card(5, 3)], [card(9, 0)], [card(9, 3)], [card(3, 0)], [card(3, 3)], [card(7, 1)], [card(7, 2)]]
  };
  assert.equal(core.listMoves(state).length, 0);
  assert.equal(core.stuckReason(state, new Set()), 'none');
});

// A fresh game of a deal number, dealt by the game's own dealer.
const deal = seed => ({ tableau: core.dealTableau(seed), cells: [null, null, null, null], foundations: [0, 0, 0, 0] });
const sameMove = (a, b) => a.from.type === b.from.type && a.from.pile === b.from.pile && (a.from.index || 0) === (b.from.index || 0) && a.to.type === b.to.type && a.to.pile === b.to.pile;
const isLegal = (state, move) => core.listMoves(state).some(other => sameMove(other, move));

test('solver: a found solution is a sequence of legal moves that wins the deal', () => {
  let state = deal(25904);
  const result = core.solveBoard(state);
  assert.equal(result.status, 'solved');
  for (const move of result.moves) { assert.ok(isLegal(state, move)); state = core.applyMove(state, move); }
  assert.deepEqual(state.foundations, [13, 13, 13, 13]);
});

test('hints: following the hint every time wins the game', () => {
  let state = deal(11987);
  const seen = new Set([core.positionKey(state)]);
  for (let step = 0; step < 300 && !state.foundations.every(v => v === 13); step++) {
    const hint = core.suggestMove(state, { seen });
    assert.ok(hint.move && isLegal(state, hint.move), `step ${step}: ${hint.status}`);
    state = core.applyMove(state, hint.move); seen.add(core.positionKey(state));
  }
  assert.deepEqual(state.foundations, [13, 13, 13, 13]);
});

test('hints: a legal move is always suggested while one exists, even past the search budget', () => {
  const start = deal(1);
  const seen = new Set([core.positionKey(start)]);
  for (const move of core.listMoves(start)) seen.add(core.positionKey(core.applyMove(start, move)));
  // Every single move was already tried: the old check called this "no more moves".
  assert.equal(core.stuckReason(start), null);
  const hint = core.suggestMove(start, { seen, maxNodes: 1 });
  assert.equal(hint.status, 'guess');
  assert.ok(isLegal(start, hint.move));
});

test('stuck: moves left but none wins is reported as unsolvable, not as no moves', () => {
  // Reached by random play from a real deal; a plain exhaustive search agrees it cannot be won.
  const s = (r, suit) => card(r, suit);
  const state = {
    foundations: [3, 0, 1, 1],
    cells: [s(12, 3), s(8, 2), s(11, 2), s(8, 1)],
    tableau: [[s(13, 3), s(5, 1), s(1, 1), s(4, 2)], [s(2, 3), s(4, 3), s(11, 0), s(3, 1), s(2, 1)],
      [s(12, 1), s(13, 2), s(7, 3), s(2, 2), s(9, 0), s(12, 2), s(8, 3), s(7, 2)],
      [s(6, 0), s(10, 1), s(7, 0), s(11, 1), s(12, 0), s(4, 1), s(6, 2), s(5, 3)], [s(13, 0)],
      [s(6, 3), s(3, 3), s(9, 1), s(10, 0), s(5, 2), s(8, 0), s(7, 1)], [s(10, 3), s(4, 0), s(9, 3), s(6, 1)],
      [s(3, 2), s(5, 0), s(13, 1), s(9, 2), s(11, 3), s(10, 2)]]
  };
  assert.ok(core.listMoves(state).length > 0);
  assert.equal(core.stuckReason(state), 'unsolvable');
  assert.equal(core.suggestMove(state).status, 'unsolvable');
});

test('stuck: a won game, or one still to win, is never stuck', () => {
  assert.equal(core.stuckReason({ foundations: [12, 13, 13, 13], cells: [null, null, null, null], tableau: [[card(13, 0)], [], [], [], [], [], [], []] }), null);
  assert.equal(core.stuckReason({ foundations: [13, 13, 13, 13], cells: [null, null, null, null], tableau: [[], [], [], [], [], [], [], []] }), null);
});

test('solver: safe foundation moves only take cards nothing can be placed on any more', () => {
  const state = { foundations: [4, 3, 3, 4], cells: [null, null, null, null], tableau: [[card(5, 0)], [card(4, 1)], [card(5, 3)], [], [], [], [], []] };
  // 4♦ is safe (both black foundations hold the 3s); 5♣ is not yet (a red 4 could still go on it).
  const { moves } = core.playSafeMoves(state);
  assert.equal(moves[0].from.pile, 1);
});

test('saved state: schema 1 opens as schema 2 without losing progress', () => {
  const old = { version: 1, currentLanguage: 'pt', tableThemeName: 'aurora', cardThemeName: 'sunlight', playerLevel: 4, levelWins: 2, stats: { started: 9, wins: 5, totalMoves: 800 } };
  const state = core.migrateState(old);
  assert.equal(state.version, 2);
  assert.equal(state.currentLanguage, 'pt');
  assert.equal(state.tableThemeName, 'aurora');
  assert.equal(state.playerLevel, 4);
  assert.equal(state.stats.wins, 5);
  assert.equal(state.stats.coinsEarned, 0);
  assert.equal(state.coins, 0);
  assert.deepEqual(state.unlockedThemes, []);
  assert.deepEqual(core.migrateState({ version: 2, coins: -4, unlockedThemes: ['neon', 'x', 'neon'] }).unlockedThemes, ['neon']);
  assert.equal(core.migrateState({ version: 2, coins: -4 }).coins, 0);
  assert.equal(core.migrateState(null).version, 2);
});

// ---- Part 2: Daily Bonus and Missions ----
test('daily bonus: day 1, then the next day of the cycle, one claim per date', () => {
  let state = {};
  const first = core.claimBonus(state, '2026-10-23', config);
  assert.deepEqual([first.ok, first.amount, first.day], [true, 20, 1]);
  assert.equal(core.claimBonus(first.state, '2026-10-23', config).ok, false);
  const second = core.claimBonus(first.state, '2026-10-24', config);
  assert.deepEqual([second.amount, second.day], [20, 2]);
  assert.equal(core.bonusStatus(second.state, '2026-10-24', config).collectedDays, 2);
});

test('daily bonus: the cycle restarts after day 7 and after a missed day', () => {
  let state = {}, day = '2026-10-01';
  const amounts = [];
  for (let i = 0; i < 8; i++) { const claim = core.claimBonus(state, day, config); amounts.push(claim.amount); state = claim.state; day = core.addDays(day, 1); }
  assert.deepEqual(amounts, [20, 20, 30, 30, 40, 40, 100, 20]);
  const missed = core.bonusStatus({ lastClaimDate: '2026-10-03', cycleDay: 3 }, '2026-10-05', config);
  assert.equal(missed.day, 1);
  const keep = core.normalizeConfig({ daily_bonus_config: { reset_on_miss: false } });
  assert.equal(core.bonusStatus({ lastClaimDate: '2026-10-03', cycleDay: 3 }, '2026-10-05', keep).day, 4);
  const off = core.normalizeConfig({ daily_bonus_config: { enabled: false } });
  assert.equal(core.bonusStatus({}, '2026-10-05', off).available, false);
});






test('saved state keeps bonus and mission data, and drops broken missions', () => {
  const state = core.migrateState({ version: 2, dailyBonus: { lastClaimDate: '2026-10-23', cycleDay: 3 }, missions: { date: 'x', list: [] } });
  assert.equal(state.dailyBonus.cycleDay, 3);
  assert.equal(state.missions, null);
});

test('warm-up: saves without introIndex count Classic wins, 5 after five Classic games started', () => {
  assert.equal(core.migrateState({}).introIndex, 0);
  assert.equal(core.migrateState({ version: 2, introGamesStarted: 3, paidKeys: ['random:25904', 'daily:2026-10-20', 'numbered:7', 'random:11987'] }).introIndex, 2);
  assert.equal(core.migrateState({ version: 2, introGamesStarted: 2, paidKeys: [] }).introIndex, 0);
  assert.equal(core.migrateState({ version: 2, introGamesStarted: 5 }).introIndex, 5);
  assert.equal(core.migrateState({ version: 1, stats: { started: 40 } }).introIndex, 5);
  assert.equal(core.migrateState({ version: 1, stats: { started: 2 }, paidKeys: ['winnable:4'] }).introIndex, 1);
  assert.equal(core.migrateState({ version: 2, introIndex: 3, introGamesStarted: 5 }).introIndex, 3);
});

// ---- Part 3: promotional popups ----
const DAY = 864e5;
const ctx = (extra = {}) => ({ now: 1000 * DAY, sessionCount: 3, wins: 5, gamesPlayed: 20, adsRemoved: false, billingAvailable: true, afterWin: true, blocked: false, ...extra });
const games = (n, state = {}) => { let s = state; for (let i = 0; i < n; i++) s = core.countPromoGame(s); return s; };

test('promos: never in the first session or when blocked', () => {
  const due = games(20);
  assert.equal(core.choosePromo(due, ctx({ sessionCount: 1 }), config), null);
  assert.equal(core.choosePromo(due, ctx({ blocked: true }), config), null);
  assert.equal(core.choosePromo(due, ctx(), config), 'rating');
});

test('promos: the rating card wins a transition; Remove Ads keeps its count and comes next', () => {
  let state = games(10);
  assert.equal(core.choosePromo(state, ctx(), config), 'rating');
  state = core.recordPromoShown(state, 'rating', ctx().now);
  assert.equal(state.removeAdsGames, 10);
  assert.equal(core.choosePromo(state, ctx(), config), 'remove_ads');
});

test('rating: needs wins and games, respects the cooldown and the lifetime limit', () => {
  const due = games(10);
  assert.equal(core.choosePromo(due, ctx({ wins: 2, billingAvailable: false }), config), null);
  let state = core.recordPromoShown(due, 'rating', ctx().now);
  state = games(10, state);
  assert.equal(core.choosePromo(state, ctx({ now: ctx().now + 44 * DAY, billingAvailable: false }), config), null);
  assert.equal(core.choosePromo(state, ctx({ now: ctx().now + 45 * DAY, billingAvailable: false }), config), 'rating');
  let capped = { ...state, ratingPrompts: 3 };
  assert.equal(core.choosePromo(capped, ctx({ now: ctx().now + 400 * DAY, billingAvailable: false }), config), null);
});

test('remove ads: never once bought, needs billing, games and its cooldown', () => {
  const noRating = core.normalizeConfig({ rating_config: { enabled: false } });
  const due = games(6);
  assert.equal(core.choosePromo(due, ctx(), noRating), 'remove_ads');
  assert.equal(core.choosePromo(due, ctx({ adsRemoved: true }), noRating), null);
  assert.equal(core.choosePromo(due, ctx({ billingAvailable: false }), noRating), null);
  assert.equal(core.choosePromo(due, ctx({ gamesPlayed: 3 }), noRating), null);
  const shown = games(6, core.recordPromoShown(due, 'remove_ads', ctx().now));
  assert.equal(core.choosePromo(shown, ctx({ now: ctx().now + 11 * 36e5 }), noRating), null);
  assert.equal(core.choosePromo(shown, ctx({ now: ctx().now + 12 * 36e5 }), noRating), 'remove_ads');
});

test('rating card only in the transition right after a win', () => {
  const due = games(20);
  assert.equal(core.choosePromo(due, ctx(), config), 'rating');
  assert.equal(core.choosePromo(due, ctx({ afterWin: false, billingAvailable: false }), config), null);
  assert.equal(core.choosePromo(due, ctx({ afterWin: undefined, billingAvailable: false }), config), null);
});

// ---- Missions (v1.2 rework): varied sets and a 12-hour wait after completing one ----
const HOUR = 36e5;
const claimAll = (state, now) => { let s = state; for (const m of s.list) s = core.claimMission({ ...s, list: s.list.map(x => x.id === m.id ? { ...x, progress: x.target } : x) }, m.id, now).state; return s; };

test('missions: three different types, varied between seeds and sets', () => {
  const picked = core.pickMissions(config, { seed: 7 });
  assert.equal(picked.length, 3);
  assert.equal(new Set(picked.map(m => m.type)).size, 3);
  const sets = new Set(Array.from({ length: 30 }, (_, i) => core.pickMissions(config, { seed: i + 1 }).map(m => m.id).sort().join(',')));
  assert.ok(sets.size > 15, `only ${sets.size} different sets`);
  const firstTypes = new Set(Array.from({ length: 30 }, (_, i) => core.pickMissions(config, { seed: i + 1 })[0].type));
  assert.ok(firstTypes.size > 4);
});

test('missions: the next set avoids the previous types; Daily left out while locked', () => {
  const avoid = ['win_games', 'win_fast', 'play_games'];
  for (let seed = 1; seed < 40; seed++) {
    const next = core.pickMissions(config, { seed, avoidTypes: avoid });
    assert.ok(next.every(m => !avoid.includes(m.type)));
    assert.ok(core.pickMissions(config, { seed, dailyUnlocked: false }).every(m => m.type !== 'win_daily'));
  }
});

test('missions: a set stays until all three are collected, then new ones after 12 hours', () => {
  const start = 1000 * HOUR;
  let { state } = core.refreshMissions(null, start, config, { seed: 3 });
  assert.equal(core.refreshMissions(state, start + 100 * HOUR, config, { seed: 4 }).refreshed, false);
  const done = claimAll(state, start + 5 * HOUR);
  assert.equal(done.completedAt, start + 5 * HOUR);
  assert.equal(core.missionsNextAt(done, config), start + 17 * HOUR);
  assert.equal(core.refreshMissions(done, start + 16.9 * HOUR, config, { seed: 4 }).refreshed, false);
  const later = core.refreshMissions(done, start + 17 * HOUR, config, { seed: 4 });
  assert.equal(later.refreshed, true);
  assert.equal(later.autoBonus, 40);
  assert.ok(later.state.list.every(m => m.progress === 0 && !m.claimed));
  assert.ok(later.state.list.every(m => !done.list.some(old => old.type === m.type)));
  const bonusTaken = core.claimMissionsBonus(done, config).state;
  assert.equal(core.refreshMissions(bonusTaken, start + 17 * HOUR, config, { seed: 4 }).autoBonus, 0);
});

test('missions: progress from the new events, capped, and claimed once', () => {
  const list = ['win_no_hint', 'win_under_moves', 'empty_columns', 'complete_suits', 'win_numbered', 'foundation_cards']
    .map(type => config.missions.definitions.find(d => d.type === type)).map(d => ({ ...d, progress: 0, claimed: false }));
  let state = { list };
  state = core.applyMissionEvent(state, { type: 'win', mode: 'numbered', usedUndo: true, usedHint: false, seconds: 900, moves: 110 });
  state = core.applyMissionEvent(state, { type: 'win', mode: 'random', usedUndo: false, usedHint: true, seconds: 900, moves: 300 });
  state = core.applyMissionEvent(state, { type: 'column_emptied' });
  state = core.applyMissionEvent(state, { type: 'suit_complete', count: 2 });
  state = core.applyMissionEvent(state, { type: 'foundation', count: 52 });
  assert.deepEqual(state.list.map(m => m.progress), [1, 1, 1, 2, 1, 30]);
  const claim = core.claimMission(state, 'no_hint_1', 5);
  assert.deepEqual([claim.ok, claim.amount, claim.state.completedAt], [true, 25, null]);
  assert.equal(core.claimMission(claim.state, 'no_hint_1', 6).ok, false);
});

test('missions config: unknown types and bad ids dropped; too few types fall back to defaults', () => {
  const custom = core.normalizeConfig({ missions_config: { refresh_hours: 500, definitions: [
    { id: 'a', type: 'win_games', target: 2, reward: 10 }, { id: 'b', type: 'play_games', target: 4, reward: 5 },
    { id: 'c', type: 'empty_columns', target: 1, reward: 9 }, { id: 'BAD ID', type: 'win_games' }, { id: 'd', type: 'fly' }] } });
  assert.deepEqual(custom.missions.definitions.map(d => d.id), ['a', 'b', 'c']);
  assert.equal(custom.missions.refresh_hours, 168);
  const tooFew = core.normalizeConfig({ missions_config: { definitions: [{ id: 'a', type: 'win_games', target: 1, reward: 1 }] } });
  assert.equal(tooFew.missions.definitions.length, config.missions.definitions.length);
});

// ---- v1.2 A1: store catalogs ----
test('store: v1.1 themes become both a card face and a background', () => {
  const state = core.migrateState({ version: 2, unlockedThemes: ['neon', 'moonstone'], cardThemeName: 'neon', tableThemeName: 'moonstone' });
  assert.deepEqual(state.owned.card_faces.sort(), ['moonstone', 'neon']);
  assert.deepEqual(state.owned.backgrounds.sort(), ['moonstone', 'neon']);
  assert.deepEqual(state.owned.card_backs, []);
  assert.equal(state.cardThemeName, 'neon');
  assert.equal(state.tableThemeName, 'moonstone');
  assert.equal(state.cardBackName, 'classic_back');
  assert.equal(core.equippableItem(config, state.owned, 'card_faces', 'neon'), 'neon');
  assert.equal(core.equippableItem(config, state.owned, 'backgrounds', 'emberwood'), 'classic');
});

test('store: catalogs are independent; buying a face does not unlock the table', () => {
  const owned = core.migrateState({}).owned;
  const bought = core.buyItem(config, { coins: 400, owned }, 'card_faces', 'neon');
  assert.equal(bought.ok, true);
  assert.equal(bought.coins, 100);
  assert.equal(core.storeAccess(config, bought.owned, 'card_faces', 'neon').owned, true);
  assert.equal(core.storeAccess(config, bought.owned, 'backgrounds', 'neon').owned, false);
  assert.equal(core.buyItem(config, { coins: 999, owned: bought.owned }, 'card_faces', 'neon').reason, 'owned');
  assert.deepEqual(core.buyItem(config, { coins: 100, owned }, 'backgrounds', 'emberwood'), { ok: false, reason: 'not_enough', missing: 700 });
  assert.equal(core.storeAccess(config, owned, 'card_backs', 'classic_back').owned, true);
});

test('store config: unknown items dropped, defaults stay free and listed, event items not sold', () => {
  const c = core.normalizeConfig({ store_config: { items: [
    { id: 'neon', cat: 'card_faces', price: 99 }, { id: 'classic', cat: 'card_faces', price: 500 },
    { id: 'rainbow', cat: 'card_faces', price: 5 }, { id: 'neon', cat: 'hats', price: 5 },
    { id: 'moonstone', cat: 'backgrounds', price: 50, event: true }] } });
  assert.equal(core.storeAccess(c, {}, 'card_faces', 'neon').price, 99);
  assert.equal(core.storeAccess(c, {}, 'card_faces', 'classic').free, true);
  assert.equal(core.storeAccess(c, {}, 'card_faces', 'rainbow').known, false);
  assert.equal(core.storeAccess(c, {}, 'card_backs', 'classic_back').listed, true);
  assert.equal(core.buyItem(c, { coins: 999, owned: {} }, 'backgrounds', 'moonstone').reason, 'event');
  assert.equal(core.buyItem(c, { coins: 999, owned: {} }, 'backgrounds', 'aurora').reason, 'unknown');
  assert.equal(c.store.shield, undefined);
  assert.deepEqual(core.storeItems(c, { backgrounds: ['emberwood'] }, 'backgrounds'), ['classic', 'moonstone', 'emberwood']);
});

test('store: approved v1.2 items are sold, and victory effects are a fourth catalog', () => {
  const owned = core.migrateState({}).owned;
  for (const [cat, id, price] of [['card_faces', 'slate_night', 1200], ['backgrounds', 'oak', 1200], ['card_backs', 'night_sky', 800], ['victory_effects', 'suit_shower', 500]]) {
    assert.equal(core.storeAccess(config, owned, cat, id).price, price, id);
    assert.equal(core.buyItem(config, { coins: price, owned }, cat, id).ok, true, id);
  }
  assert.equal(core.storeAccess(config, owned, 'victory_effects', 'classic_confetti').owned, true);
  assert.equal(core.migrateState({}).victoryEffectName, 'classic_confetti');
  assert.equal(core.migrateState({ victoryEffectName: 'fireworks' }).victoryEffectName, 'classic_confetti');
  assert.equal(core.storeItems(config, owned, 'backgrounds').length, 15);
});

// ---- v1.2 A4: seasonal events ----
const eventConfig = (extra = {}) => core.normalizeConfig({ events_config: { events: [{ id: 'winter_2026', enabled: true, start: '2026-12-01', end: '2027-01-06', goal: { type: 'play_days', target: 3 }, reward_items: ['night_sky', 'oak'], ...extra }] } });

test('events: shipped disabled; disabled events and dates outside the window never appear', () => {
  assert.equal(config.events[0].enabled, false);
  assert.deepEqual(core.activeEvents(config, '2026-12-10'), []);
  const c = eventConfig();
  assert.equal(core.activeEvents(c, '2026-11-30').length, 0);
  assert.equal(core.activeEvents(c, '2026-12-01').length, 1);
  assert.equal(core.activeEvents(c, '2027-01-06').length, 1);
  assert.equal(core.activeEvents(c, '2027-01-07').length, 0);
  assert.equal(core.eventDaysLeft(c.events[0], '2027-01-06'), 1);
  assert.equal(core.eventDaysLeft(c.events[0], '2026-12-01'), 37);
});

test('events: play_days counts distinct winning dates and rewards once at the target', () => {
  const c = eventConfig();
  let state = {}, owned = { card_backs: [], backgrounds: [] }, r;
  r = core.recordEventWin(state, c, '2026-12-02', owned); state = r.state;
  r = core.recordEventWin(state, c, '2026-12-02', owned); state = r.state;
  assert.equal(core.eventProgress(state, c.events[0]).done, 1);
  r = core.recordEventWin(state, c, '2026-12-05', owned); state = r.state;
  assert.deepEqual(r.rewards, []);
  r = core.recordEventWin(state, c, '2026-12-09', owned); state = r.state;
  assert.deepEqual(r.rewards, [{ cat: 'card_backs', id: 'night_sky' }, { cat: 'backgrounds', id: 'oak' }]);
  assert.deepEqual(r.completed, ['winter_2026']);
  r = core.recordEventWin(state, c, '2026-12-10', owned);
  assert.deepEqual(r.rewards, []);
  assert.equal(core.eventProgress(r.state, c.events[0]).done, 3);
  assert.deepEqual(core.recordEventWin({}, c, '2027-02-01', owned).state, {});
});

test('events: the Winter Festival rewards are exclusive store items, never sold', () => {
  const rewards = config.events[0].reward_items;
  assert.deepEqual(rewards, ['winter_back', 'winter_table', 'winter_confetti']);
  for (const id of rewards) {
    const cat = core.itemCategory(id);
    const access = core.storeAccess(config, {}, cat, id);
    assert.equal(access.event, true, id);
    assert.equal(access.owned, false, id);
    assert.equal(core.buyItem(config, { coins: 99999, owned: {} }, cat, id).reason, 'event', id);
  }
  const winter = { ...config, events: config.events.map(e => ({ ...e, enabled: true, goal: { ...e.goal, target: 1 } })) };
  const r = core.recordEventWin({}, winter, '2026-12-01', {});
  assert.deepEqual(r.rewards, [{ cat: 'card_backs', id: 'winter_back' }, { cat: 'backgrounds', id: 'winter_table' }, { cat: 'victory_effects', id: 'winter_confetti' }]);
  assert.equal(core.storeAccess(config, { card_backs: ['winter_back'] }, 'card_backs', 'winter_back').owned, true);
});

test('events: the opening notice is due once per event and survives wins and saves', () => {
  const c = eventConfig();
  assert.equal(core.eventIntroDue({}, c, '2026-11-30'), null);
  assert.equal(core.eventIntroDue({}, c, '2026-12-01').id, 'winter_2026');
  let state = core.recordEventWin({}, c, '2026-12-02', {}).state;
  assert.equal(core.eventIntroDue(state, c, '2026-12-02').id, 'winter_2026');
  state = core.markEventIntro(state, 'winter_2026');
  assert.equal(core.eventIntroDue(state, c, '2026-12-03'), null);
  assert.deepEqual(state.winter_2026.days, ['2026-12-02']);
  state = core.recordEventWin(state, c, '2026-12-03', {}).state;
  assert.equal(state.winter_2026.introShown, true);
  assert.equal(core.migrateState({ version: 2, events: state }).events.winter_2026.introShown, true);
});

test('events config: bad dates, unknown goals and duplicates are dropped', () => {
  const c = core.normalizeConfig({ events_config: { events: [
    { id: 'a', enabled: true, start: '2026-12-10', end: '2026-12-01', goal: { type: 'play_days', target: 5 } },
    { id: 'b', enabled: true, start: '2026-12-01', end: '2026-12-31', goal: { type: 'fly' } },
    { id: 'c', enabled: 'yes', start: '2026-12-01', end: '2026-12-31', goal: { type: 'play_days', target: 999 }, reward_items: ['x', 'BAD'] },
    { id: 'c', enabled: true, start: '2026-12-01', end: '2026-12-31', goal: { type: 'play_days' } }] } });
  assert.deepEqual(c.events.map(e => e.id), ['c']);
  assert.equal(c.events[0].enabled, false);
  assert.equal(c.events[0].goal.target, 366);
  assert.deepEqual(c.events[0].reward_items, ['x']);
  assert.deepEqual(core.migrateState({ events: { winter_2026: { days: ['2026-12-02', 'bad'], rewarded: 1 } } }).events, { winter_2026: { days: ['2026-12-02'], rewarded: true, introShown: false } });
});

// ---- v1.2 B1: rewarded "double your coins" ----
test('rewarded: offered only when enabled, placed, after enough games and under the daily limit', () => {
  const base = { placement: 'victory_double', day: '2026-12-01', gamesPlayed: 2, amount: 30 };
  assert.equal(core.rewardedOffer({}, config, base).ok, true);
  assert.equal(core.rewardedOffer({}, config, { ...base, gamesPlayed: 1 }).reason, 'games');
  assert.equal(core.rewardedOffer({}, config, { ...base, amount: 0 }).reason, 'amount');
  const off = core.normalizeConfig({ rewarded_config: { placements: { victory_double: false } } });
  assert.equal(core.rewardedOffer({}, off, base).reason, 'disabled');
  assert.equal(core.rewardedOffer({}, off, { ...base, placement: 'daily_bonus_double' }).ok, true);
  assert.equal(core.rewardedOffer({ day: '2026-12-01', count: 6 }, config, base).reason, 'limit');
  assert.equal(core.rewardedOffer({ day: '2026-11-30', count: 6 }, config, base).ok, true);
  const c = core.normalizeConfig({ rewarded_config: { max_per_day: 999, min_games_played: -1, enabled: 'no' } });
  assert.deepEqual([c.rewarded.max_per_day, c.rewarded.min_games_played, c.rewarded.enabled], [100, 0, true]);
});

test('rewarded: an offer is credited once, then never again; unknown or closed offers pay nothing', () => {
  let state = core.openOffer({}, { id: 'v-1', placement: 'victory_double', amount: 30 });
  let r = core.creditOffer(state, 'v-1', '2026-12-01');
  assert.equal(r.amount, 30);
  assert.equal(core.rewardedToday(r.state, '2026-12-01'), 1);
  assert.equal(core.creditOffer(r.state, 'v-1', '2026-12-01').amount, 0);
  const reopened = core.openOffer(r.state, { id: 'v-1', placement: 'victory_double', amount: 30 });
  assert.equal(core.creditOffer(reopened, 'v-1', '2026-12-01').amount, 0);
  assert.equal(core.creditOffer(r.state, 'ghost', '2026-12-01').amount, 0);
  state = core.dropOffer(core.openOffer(r.state, { id: 'd-2', placement: 'daily_bonus_double', amount: 20 }), 'd-2');
  assert.equal(core.creditOffer(state, 'd-2', '2026-12-01').amount, 0);
  const saved = core.migrateState({ version: 2, rewarded: { ...r.state, offers: { 'x-1': { placement: 'bogus', amount: 5 } } } }).rewarded;
  assert.deepEqual(saved.offers, {});
  assert.deepEqual(saved.credited, ['v-1']);
});

// ---- v1.2 B3: Daily reminders ----
test('reminders: asked after the first Daily win, at most twice, 30 days apart, never after a refusal', () => {
  const now = Date.UTC(2026, 11, 3);
  assert.equal(core.reminderAskDue({}, config, { dailyWins: 0, now }), false);
  assert.equal(core.reminderAskDue({}, config, { dailyWins: 1, now }), true);
  let r = core.markReminderAsked({}, now);
  assert.equal(core.reminderAskDue(r, config, { dailyWins: 2, now: now + 29 * 864e5 }), false);
  assert.equal(core.reminderAskDue(r, config, { dailyWins: 2, now: now + 30 * 864e5 }), true);
  r = core.markReminderAsked(r, now + 30 * 864e5);
  assert.equal(core.reminderAskDue(r, config, { dailyWins: 9, now: now + 400 * 864e5 }), false);
  assert.equal(core.reminderAskDue({ denied: true }, config, { dailyWins: 9, now }), false);
  assert.equal(core.reminderAskDue({ enabled: true }, config, { dailyWins: 9, now }), false);
  const off = core.normalizeConfig({ notifications_config: { enabled: false } });
  assert.equal(core.reminderAskDue({}, off, { dailyWins: 9, now }), false);
  assert.equal(core.normalizeConfig({ notifications_config: { default_hour: 30, ask_after_daily_wins: 0 } }).notifications.default_hour, 23);
});

test('reminders: payload for Android uses the last Daily won on its own date and the chosen hour', () => {
  const history = { '2026-12-01': { won: true, onDay: true }, '2026-12-02': { won: true, onDay: false }, '2026-11-30': { won: false } };
  const payload = core.reminderPayload({ enabled: true, hour: 8 }, config, { history, streak: 4, texts: { channel: 'x' } });
  assert.deepEqual(payload, { enabled: true, hour: 8, riskHour: 20, minStreak: 3, streak: 4, lastDailyWin: '2026-12-01', texts: { channel: 'x' } });
  assert.equal(core.reminderPayload({}, config, { history: {}, streak: 0, texts: {} }).hour, 19);
  assert.equal(core.reminderPayload({ enabled: true }, core.normalizeConfig({ notifications_config: { enabled: false } }), { history: {}, streak: 0, texts: {} }).enabled, false);
  assert.equal(core.dailyWinCount(history), 2);
  assert.deepEqual(core.migrateState({ version: 2, reminders: { enabled: 1, hour: 25, asks: 99 } }).reminders, { enabled: true, hour: null, asks: 10, askedAt: 0, denied: false });
});

// ---- v1.2 C1: stars ----
test('stars: 1 for any win, 2 with few Undos, 3 with none inside the time and move limits', () => {
  assert.equal(core.starsFor(config, { undos: 0, seconds: 480, moves: 140 }), 3);
  assert.equal(core.starsFor(config, { undos: 0, seconds: 481, moves: 100 }), 2);
  assert.equal(core.starsFor(config, { undos: 0, seconds: 300, moves: 141 }), 2);
  assert.equal(core.starsFor(config, { undos: 2, seconds: 100, moves: 90 }), 2);
  assert.equal(core.starsFor(config, { undos: 3, seconds: 100, moves: 90 }), 1);
  const c = core.normalizeConfig({ stars_config: { two_star_max_undos: 1, three_star_max_undos: 9, three_star_seconds: 1 } });
  assert.deepEqual([c.stars.three_star_max_undos, c.stars.three_star_seconds], [1, 30]);
});

test('stars: best per deal only improves, and the bonus pays only the improvement', () => {
  let r = core.recordStars({}, 'numbered:123', 2, config);
  assert.deepEqual([r.previous, r.bonus], [0, 5]);
  r = core.recordStars(r.best, 'numbered:123', 1, config);
  assert.deepEqual([r.best['numbered:123'], r.bonus], [2, 0]);
  r = core.recordStars(r.best, 'numbered:123', 3, config);
  assert.deepEqual([r.best['numbered:123'], r.previous, r.bonus], [3, 2, 5]);
  assert.equal(core.recordStars(r.best, 'numbered:123', 3, config).bonus, 0);
  assert.equal(core.recordStars({}, 'daily:2026-12-01', 1, config).bonus, 0);
  assert.deepEqual(core.migrateState({ version: 2, starBest: { 'numbered:1': 3, 'numbered:2': 7, 'bogus': 2, 'daily:2026-12-01': 1 } }).starBest, { 'numbered:1': 3, 'daily:2026-12-01': 1 });
  const many = {}; for (let i = 0; i < 5100; i++) many[`random:${i}`] = 1;
  const kept = core.recordStars(many, 'random:9999', 2, config).best;
  assert.equal(Object.keys(kept).length, 5000);
  assert.equal(kept['random:9999'], 2);
  assert.equal(kept['random:0'], undefined);
});

// ---- v1.2 C2: achievements ----
test('achievements: counters only grow, tiers unlock at their targets and queue a notice once', () => {
  let r = core.updateAchievements({}, config, { wins: 1 });
  assert.deepEqual(r.newly, ['first_win']);
  r = core.updateAchievements(r.state, config, { wins: 9 });
  assert.deepEqual(r.newly, []);
  r = core.updateAchievements(r.state, config, { wins: 10, no_undo_wins: 1 });
  assert.deepEqual(r.newly.sort(), ['no_undo_1', 'wins_10']);
  r = core.updateAchievements(r.state, config, { wins: 3 });
  assert.equal(r.state.counters.wins, 10);
  assert.deepEqual(r.state.queue, ['first_win', 'wins_10', 'no_undo_1']);
  r = core.updateAchievements(r.state, config, { fast_win: 400 });
  assert.deepEqual(r.newly, []);
  r = core.updateAchievements(r.state, config, { fast_win: 290 });
  assert.deepEqual(r.newly, ['fast_5']);
  r = core.updateAchievements(r.state, config, { fast_win: 500 });
  assert.equal(r.state.counters.fast_win, 290);
  assert.equal(core.achievementProgress(config.achievements.items.find(i => i.id === 'wins_50'), r.state.counters).value, 10);
});

test('achievements: collected once only, never before unlocking, and old saves start from their wins', () => {
  let state = core.updateAchievements({}, config, { wins: 1 }).state;
  assert.equal(core.claimAchievement(state, config, 'wins_10').ok, false);
  let c = core.claimAchievement(state, config, 'first_win');
  assert.deepEqual([c.ok, c.amount], [true, 10]);
  assert.equal(core.claimAchievement(c.state, config, 'first_win').ok, false);
  assert.equal(core.achievementsToCollect(c.state, config).length, 0);
  const migrated = core.migrateState({ version: 2, stats: { wins: 12, threeStarWins: 2 } }).achievements;
  assert.deepEqual([migrated.counters.wins, migrated.counters.three_star_wins, migrated.unlocked.length], [12, 2, 0]);
  assert.deepEqual(core.migrateState({ version: 2, achievements: { unlocked: ['a'], claimed: ['a', 'b'] } }).achievements.claimed, ['a']);
  const off = core.normalizeConfig({ achievements_config: { enabled: false } });
  assert.deepEqual(core.updateAchievements({}, off, { wins: 100 }).newly, []);
  assert.equal(core.normalizeConfig({ achievements_config: { items: [{ id: 'x', counter: 'nope' }] } }).achievements.items.length, config.achievements.items.length);
});

// ---- v1.2 C3: numbered collection ----
test('collection: entries only improve, most recent last, and the cap keeps 5,000', () => {
  let c = core.recordNumbered([], { seed: 42, stars: 2, seconds: 300, moves: 120 });
  c = core.recordNumbered(c, { seed: 7, stars: 1, seconds: 500, moves: 200 });
  c = core.recordNumbered(c, { seed: 42, stars: 1, seconds: 400, moves: 100 });
  assert.deepEqual(c, [[7, 1, 500, 200], [42, 2, 300, 100]]);
  c = core.recordNumbered(c, { seed: 42, stars: 3, seconds: 250, moves: 130 });
  assert.deepEqual(c[1], [42, 3, 250, 100]);
  const big = Array.from({ length: 5100 }, (_, i) => [i + 1, 1 + (i % 3), 100 + i, 90 + (i % 60)]);
  const kept = core.cleanCollection(big);
  assert.equal(kept.length, 5000);
  assert.equal(kept[0][0], 101);
  assert.deepEqual(core.cleanCollection([[-1, 1, 0, 0], [10000000, 1, 0, 0], ['x'], [5, 9, -3, 1]]), [[5, 3, 0, 1]]);
});

test('collection: compact text stays small and round-trips; older stars migrate', () => {
  const full = Array.from({ length: 5000 }, (_, i) => [9999999 - i, 3, 7200, 999]);
  const text = core.encodeCollection(full);
  assert.ok(text.length < 130000, `encoded ${text.length}`);
  assert.deepEqual(core.decodeCollection(text), core.cleanCollection(full));
  assert.deepEqual(core.decodeCollection('12,2,90,80;bad;13,1,0,0'), [[12, 2, 90, 80], [13, 1, 0, 0]]);
  assert.deepEqual(core.decodeCollection(null), []);
  assert.deepEqual(core.collectionFromStars({ 'numbered:12': 3, 'random:4': 2, 'numbered:5': 1 }), [[12, 3, 0, 0], [5, 1, 0, 0]]);
});

test('collection: pages of 100 from deal 1, page progress and the last page', () => {
  assert.deepEqual([core.collectionPageOf(1), core.collectionPageOf(100), core.collectionPageOf(101), core.collectionPageOf(0)], [0, 0, 1, 0]);
  const index = new Map([[1, [1, 3, 0, 0]], [100, [100, 1, 0, 0]], [101, [101, 2, 0, 0]]]);
  const page = core.collectionPage(index, 0);
  assert.deepEqual([page.first, page.last, page.cells.length, page.done, page.cells[0].stars, page.cells[1].stars], [1, 100, 100, 2, 3, 0]);
  const last = core.collectionPage(new Map(), core.collectionPageCount() - 1);
  assert.equal(last.last, 9999999);
  assert.equal(core.collectionPageOf(9999999), core.collectionPageCount() - 1);
});

test('saved state stays far below the 500,000-character bridge limit when everything is full', () => {
  const starBest = {}; for (let i = 0; i < 6000; i++) starBest[`numbered:${9000000 + i}`] = 3;
  const dailyHistory = {}; for (let d = 0; d < 400; d++) dailyHistory[core.addDays('2026-12-31', -d)] = { won: true, onDay: true, seconds: 999, moves: 300, score: 520, attempts: 9 };
  const state = core.migrateState({ version: 2, starBest, dailyHistory, paidKeys: Array.from({ length: 400 }, (_, i) => `random:${i}`) });
  const size = JSON.stringify(state).length;
  assert.ok(size < 300000, `state ${size}`);
});

// ---- v1.3 Part 2: deal tiers ----
const catalog = core.cleanCatalog(JSON.parse(fs.readFileSync(path.join(assets, 'deal_catalog.json'), 'utf8')));
const firstRow = seed => core.dealTableau(seed).map(column => column[0]).map(c => `${'A23456789TJQK'[c.r - 1].replace('T', '10')}${'CDHS'[c.s]}`).join(' ');

test('deals: numbers are Microsoft deals (first rows of #1 and #617)', () => {
  assert.equal(firstRow(1), 'JD 2D 9H JC 5D 7H 7C 5H');
  assert.equal(firstRow(617), '7D AD 5C 3S 5S 8C 2D AH');
});

test('catalog: 128 Easy, 675 Hard, no overlap, impossible deals in no tier', () => {
  assert.equal(catalog.easy.length, 128);
  assert.equal(catalog.hard.length, 675);
  assert.equal(catalog.easy.filter(n => catalog.hardSet.has(n)).length, 0);
  for (const n of core.IMPOSSIBLE_DEALS) assert.equal(core.tierOf(catalog, n), 'impossible');
  for (const n of [164, 892, 25904]) assert.equal(core.tierOf(catalog, n), 'easy');
  for (const n of [169, 31465]) assert.equal(core.tierOf(catalog, n), 'hard');
  assert.ok(catalog.easy.every(n => n >= 1 && n <= 64000) && catalog.hard.every(n => n >= 1 && n <= 100000));
  // The five opening deals are the five easiest, in order.
  assert.deepEqual(catalog.easy.slice(0, 5), [25904, 5152, 11987, 13464, 9998]);
});

test('catalog: Medium is every other deal up to 64,000; above it a deal is unrated', () => {
  let medium = 0;
  for (let n = 1; n <= 64000; n++) if (core.tierOf(catalog, n) === 'medium') medium++;
  assert.equal(medium, 64000 - 128 - catalog.hard.filter(n => n <= 64000).length - 1);
  assert.equal(core.tierOf(catalog, 64001), 'unrated');
  assert.equal(core.tierOf(catalog, 64020), 'hard');
  assert.equal(core.tierOf(catalog, 0), 'unrated');
});

test('catalog: every tier deal deals 52 different cards', () => {
  for (const seed of [...catalog.easy, ...catalog.hard]) {
    const cards = core.dealTableau(seed).flat();
    assert.equal(new Set(cards.map(c => c.s * 13 + c.r)).size, 52, `deal ${seed}`);
  }
});

test('catalog: a broken file still gives a playable catalog', () => {
  const empty = core.cleanCatalog(null);
  assert.equal(core.tierOf(empty, 11982), 'impossible');
  const { seed, pool } = core.pickDeal(empty, config.difficulty, 'hard', { random: core.seededRandom(1) });
  assert.equal(pool, 'medium');
  assert.equal(core.tierOf(empty, seed), 'medium');
});

// ---- Owner change 2026-10-30: warm-up deals, then a mix of pools per tier ----
const difficulty = config.difficulty, WARMUP = [25904, 5152, 11987, 13464, 9998];
const lcg = (seed) => { let x = seed; return () => (x = (Math.imul(x, 1103515245) + 12345) >>> 0) / 4294967296; };

test('warm-up: the five deals in order, whatever the tier, moved on only by a win', () => {
  assert.deepEqual(difficulty.first_games.seeds, WARMUP);
  let index = 0;
  for (const seed of WARMUP) {
    assert.equal(core.firstGameSeed(difficulty, index, { selectedTier: 'hard' }), seed);
    // Starting (or abandoning and starting again) deals the same deal.
    index = core.advanceIntro(difficulty, index, { event: 'start', seed });
    assert.equal(core.firstGameSeed(difficulty, index, { selectedTier: 'medium' }), seed);
    // A win of another deal does not count.
    assert.equal(core.advanceIntro(difficulty, index, { event: 'win', seed: 42 }), index);
    index = core.advanceIntro(difficulty, index, { event: 'win', seed });
  }
  assert.equal(index, 5);
  assert.equal(core.firstGameSeed(difficulty, 5), null);
  assert.equal(core.warmupActive(difficulty, 4), true);
  assert.equal(core.warmupActive(difficulty, 5), false);
  // Only the New Game flow uses them.
  for (const mode of ['daily', 'numbered', 'developer']) assert.equal(core.firstGameSeed(difficulty, 0, { mode }), null);
});

test('warm-up: switches, advance on start, and the chosen tier when it is not ignored', () => {
  const off = core.normalizeConfig({ difficulty_config: { first_games: { enabled: false } } }).difficulty;
  assert.equal(core.firstGameSeed(off, 0), null);
  const start = core.normalizeConfig({ difficulty_config: { first_games: { advance_on: 'start' } } }).difficulty;
  assert.equal(core.advanceIntro(start, 0, { event: 'start', seed: 25904 }), 1);
  assert.equal(core.advanceIntro(start, 1, { event: 'win', seed: 5152 }), 1);
  const choice = core.normalizeConfig({ difficulty_config: { first_games: { ignore_selected_mode: false } } }).difficulty;
  assert.equal(core.firstGameSeed(choice, 0, { selectedTier: 'hard' }), null);
  assert.equal(core.firstGameSeed(choice, 0, { selectedTier: 'easy' }), 25904);
});

test('pools: very_easy is the first 64 Easy deals, easy the other 64; the warm-up deals are very_easy', () => {
  const pools = core.dealPools(catalog, difficulty);
  assert.deepEqual(pools.very_easy, catalog.easy.slice(0, 64));
  assert.deepEqual(pools.easy, catalog.easy.slice(64));
  assert.equal(pools.hard.length, 675);
  for (const seed of WARMUP) assert.equal(core.poolOf(catalog, difficulty, seed), 'very_easy');
  assert.equal(core.poolOf(catalog, difficulty, catalog.easy[100]), 'easy');
  assert.equal(core.poolOf(catalog, difficulty, 169), 'hard');
  assert.equal(core.poolOf(catalog, difficulty, 2), 'medium');
  const split = core.normalizeConfig({ difficulty_config: { easy_split_index: 10 } }).difficulty;
  assert.equal(core.dealPools(catalog, split).very_easy.length, 10);
});

test('pools: each tier follows its weights over many draws', () => {
  const random = lcg(777), draws = 20000;
  const expected = { easy: { very_easy: 0.6, easy: 0.4 }, medium: { medium: 0.7, easy: 0.3 }, hard: { medium: 0.6, hard: 0.4 } };
  for (const tier of ['easy', 'medium', 'hard']) {
    const counts = {};
    for (let i = 0; i < draws; i++) {
      const { seed, pool } = core.pickDeal(catalog, difficulty, tier, { random });
      counts[pool] = (counts[pool] || 0) + 1;
      assert.equal(core.poolOf(catalog, difficulty, seed), pool);
    }
    for (const [pool, share] of Object.entries(expected[tier])) assert.ok(Math.abs(counts[pool] / draws - share) < 0.02, `${tier}/${pool}: ${counts[pool] / draws}`);
    assert.equal(Object.keys(counts).length, Object.keys(expected[tier]).length, tier);
  }
});

test('pools: never one of the pool\'s last 50; lists play every deal before repeating one', () => {
  const random = lcg(4242);
  for (const tier of ['easy', 'medium', 'hard']) {
    let history = core.cleanTierHistory(null);
    const seen = {};
    for (let i = 0; i < 400; i++) {
      const { seed, pool } = core.pickDeal(catalog, difficulty, tier, { random, history });
      assert.ok(!history.recent[pool].includes(seed), `${tier}: ${pool} repeated ${seed} within 50`);
      seen[pool] = seen[pool] || new Set();
      if (pool !== 'medium') {
        const size = core.dealPools(catalog, difficulty)[pool].length;
        if (seen[pool].size < size) assert.ok(!seen[pool].has(seed), `${pool} repeated ${seed} before playing all ${size}`);
      }
      seen[pool].add(seed);
      history = core.rememberDeal(history, pool, seed);
    }
    for (const pool of Object.keys(seen)) assert.ok(history.recent[pool].length <= 50);
  }
});

test('pools: warm-up deals still to come are never drawn; won ones only once the pool is used up', () => {
  const random = lcg(99);
  let history = core.cleanTierHistory(null);
  for (const seed of WARMUP) history = core.rememberDeal(history, 'very_easy', seed);
  const drawn = new Set();
  for (let i = 0; i < 59 * 3; i++) {
    const { seed, pool } = core.pickDeal(catalog, difficulty, 'easy', { random, history });
    if (pool !== 'very_easy') continue;
    if (drawn.size < 59) assert.ok(!WARMUP.includes(seed), `warm-up deal ${seed} drawn early`);
    drawn.add(seed); history = core.rememberDeal(history, pool, seed);
  }
  for (let i = 0; i < 500; i++) assert.ok(!WARMUP.includes(core.pickDeal(catalog, difficulty, 'easy', { random, exclude: WARMUP }).seed));
});

test('difficulty config: a mix that does not add up to 100 falls back to its default', () => {
  const d = core.normalizeConfig({ difficulty_config: { mixes: { easy: { very_easy: 50, easy: 50 }, medium: { medium: 80, easy: 30 }, hard: { hard: 100, bogus: 0 } }, easy_split_index: -3, first_games: { seeds: [3, 3], advance_on: 'never' } } }).difficulty;
  assert.deepEqual(d.mixes.easy, { very_easy: 50, easy: 50 });
  assert.deepEqual(d.mixes.medium, { medium: 70, easy: 30 });
  assert.deepEqual(d.mixes.hard, { medium: 60, hard: 40 });
  assert.equal(d.easy_split_index, 0);
  assert.deepEqual(d.first_games.seeds, WARMUP);
  assert.equal(d.first_games.advance_on, 'win');
  // An empty pool is skipped: with no very_easy deals Easy draws only from easy.
  const empty = core.normalizeConfig({ difficulty_config: { easy_split_index: 0 } }).difficulty, random = lcg(5);
  for (let i = 0; i < 200; i++) assert.equal(core.pickDeal(catalog, empty, 'easy', { random }).pool, 'easy');
});

test('saved state: a history from before the pools keeps its deals', () => {
  const state = core.migrateState({ version: 2, tierHistory: { recent: { easy: [25904, 7], medium: [3], hard: [169] }, easyPlayed: [25904, 5152] } });
  assert.deepEqual(state.tierHistory.recent.very_easy, [25904, 7]);
  assert.deepEqual(state.tierHistory.recent.medium, [3]);
  assert.deepEqual(state.tierHistory.recent.hard, [169]);
  assert.deepEqual(state.tierHistory.played.very_easy, [25904, 5152]);
  assert.deepEqual(state.tierHistory.played.hard, []);
  const again = core.migrateState({ version: 2, tierHistory: state.tierHistory });
  assert.deepEqual(again.tierHistory, state.tierHistory);
});

test('Daily: same date, same deal from its tier\'s mix; Medium on weekdays, Hard at weekends; never a warm-up deal', () => {
  const monday = core.dailyDeal(catalog, config, '2026-10-26'), saturday = core.dailyDeal(catalog, config, '2026-10-31');
  assert.deepEqual(core.dailyDeal(catalog, config, '2026-10-26'), monday);
  assert.equal(monday.tier, 'medium');
  assert.equal(saturday.tier, 'hard');
  assert.equal(core.dailyDeal(catalog, config, '2026-11-01').tier, 'hard');
  const year = new Set(), pools = { medium: {}, hard: {} };
  for (let day = 0; day < 3650; day++) {
    const key = core.addDays('2027-01-01', day), daily = core.dailyDeal(catalog, config, key);
    if (day < 365) year.add(daily.seed);
    assert.deepEqual(core.dailyDeal(catalog, config, key), daily);
    assert.ok(!WARMUP.includes(daily.seed), `warm-up deal on ${key}`);
    assert.equal(core.poolOf(catalog, difficulty, daily.seed), daily.pool);
    pools[daily.tier][daily.pool] = (pools[daily.tier][daily.pool] || 0) + 1;
  }
  assert.ok(year.size > 300, 'Daily deals vary from day to day');
  const share = (tier, pool) => pools[tier][pool] / Object.values(pools[tier]).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(share('medium', 'medium') - 0.7) < 0.04 && Math.abs(share('medium', 'easy') - 0.3) < 0.04, JSON.stringify(pools.medium));
  assert.ok(Math.abs(share('hard', 'medium') - 0.6) < 0.05 && Math.abs(share('hard', 'hard') - 0.4) < 0.05, JSON.stringify(pools.hard));
  // Appending to the catalog never changes a past Daily.
  const grown = core.cleanCatalog({ ...JSON.parse(fs.readFileSync(path.join(assets, 'deal_catalog.json'), 'utf8')), hard: [...catalog.hard, 99999] });
  assert.deepEqual(core.dailyDeal(grown, config, '2026-10-31'), saturday);
});

test('difficulty config: known tiers only, default among them, a tier per weekday', () => {
  const d = core.normalizeConfig({ difficulty_config: { enabled_tiers: ['hard', 'bogus', 'easy'], default_tier: 'medium', daily_tiers_by_weekday: ['easy', 'x'], unlock_levels: { hard: 500 } } }).difficulty;
  assert.deepEqual(d.enabled_tiers, ['easy', 'hard']);
  assert.equal(d.default_tier, 'easy');
  assert.deepEqual(d.daily_tiers_by_weekday, ['easy', 'medium', 'medium', 'medium', 'medium', 'medium', 'hard']);
  assert.deepEqual(d.unlock_levels, { easy: 1, medium: 1, hard: 100 });
  assert.deepEqual(core.normalizeConfig({ difficulty_config: { enabled_tiers: [] } }).difficulty.enabled_tiers, ['easy', 'medium', 'hard']);
});

test('saved state: tiers migrate; an older Winnable mission counts Easy wins', () => {
  const state = core.migrateState({ version: 2, stats: { wins: 3 }, difficultyTier: 'bogus', tierHistory: { recent: { easy: [1, 'x', 2] } } });
  assert.equal(state.difficultyTier, null);
  assert.deepEqual(state.tierHistory.recent.easy, [1, 2]);
  assert.deepEqual(state.stats.winsByTier, { easy: 0, medium: 0, hard: 0 });
  const missions = { list: [{ id: 'winnable_2', type: 'win_winnable', target: 2, progress: 0, reward: 20 }] };
  const after = core.applyMissionEvent(missions, { type: 'win', mode: 'random', tier: 'easy' });
  assert.equal(after.list[0].progress, 1);
  assert.equal(core.applyMissionEvent(missions, { type: 'win', mode: 'random', tier: 'hard' }).list[0].progress, 0);
});

// ---- v1.3 Part 3: Daily Rivals ----
const roster = core.cleanRoster(JSON.parse(fs.readFileSync(path.join(assets, 'rivals_roster.json'), 'utf8')));

test('rivals: the roster has 60 fictional rivals with their own skill and reliability', () => {
  assert.equal(roster.length, 60);
  assert.equal(new Set(roster.map(r => r.nickname)).size, 60);
  assert.ok(roster.every(r => r.skill >= 0.5 && r.skill <= 2 && r.reliability > 0 && r.reliability <= 1 && /^#[0-9a-f]{6}$/i.test(r.color)));
  assert.deepEqual(core.cleanRoster({ rivals: [{ id: 'bad id', nickname: 'x' }, { id: 'ok', nickname: ' Fine ', skill: 9 }] }), [{ id: 'ok', nickname: 'Fine', color: '#3d5a9e', skill: 2, reliability: 0.6 }]);
});

test('rivals: the same date gives the same table, another date another table', () => {
  const a = core.rivalsTable(roster, config, '2026-10-26', 'medium');
  assert.deepEqual(core.rivalsTable(roster, config, '2026-10-26', 'medium'), a);
  assert.equal(a.length, 30);
  assert.notDeepEqual(core.rivalsTable(roster, config, '2026-10-27', 'medium'), a);
  assert.ok(a.every((row, i) => i === 0 || a[i - 1].seconds <= row.seconds));
  assert.ok(a.every(row => row.seconds >= config.rivals.min_seconds));
});

test('rivals: times spread around the median, slower on Hard days', () => {
  const times = tier => { const all = []; for (let d = 0; d < 120; d++) all.push(...core.rivalsTable(roster, config, core.addDays('2027-01-01', d), tier).map(r => r.seconds)); return all.sort((a, b) => a - b); };
  const medium = times('medium'), hard = times('hard'), at = (list, p) => list[Math.floor(list.length * p)];
  assert.ok(at(medium, 0.5) > 330 && at(medium, 0.5) < 520, `median ${at(medium, 0.5)}`);
  assert.ok(at(medium, 0.9) / at(medium, 0.1) > 1.8 && at(medium, 0.9) / at(medium, 0.1) < 4, 'spread');
  const ratio = at(hard, 0.5) / at(medium, 0.5);
  assert.ok(ratio > 1.3 && ratio < 1.5, `hard/medium ${ratio}`);
});

test('rivals: rank counts only strictly faster rivals, so ties share the place', () => {
  const table = [{ seconds: 100 }, { seconds: 200 }, { seconds: 200 }, { seconds: 300 }];
  assert.deepEqual(core.rivalsRank(table, 50), { rank: 1, total: 5 });
  assert.deepEqual(core.rivalsRank(table, 200), { rank: 2, total: 5 });
  assert.deepEqual(core.rivalsRank(table, 250), { rank: 4, total: 5 });
  assert.deepEqual(core.rivalsRank(table, 999), { rank: 5, total: 5 });
});

test('rivals: a streak is the run of days the rival played, ending today', () => {
  for (const rival of roster.slice(0, 10)) {
    const streak = core.rivalStreak(rival, '2026-10-26');
    // Walking back by hand: the days of the streak are played and the day before it is not.
    const board = core.rivalsStreakBoard([rival], '2026-10-26')[0];
    assert.equal(board.streak, streak);
    if (streak > 0) assert.equal(core.rivalStreak(rival, core.addDays('2026-10-26', -1)), streak - 1);
    assert.equal(core.rivalStreak(rival, core.addDays('2026-10-26', -streak)), 0);
  }
  const board = core.rivalsStreakBoard(roster, '2026-10-26');
  assert.ok(board.every((row, i) => i === 0 || board[i - 1].streak >= row.streak));
  assert.ok(board[0].streak > board[board.length - 1].streak, 'reliable rivals keep longer streaks');
});

test('rivals: results keep the best place and count each top-3 day once', () => {
  let r = core.cleanRivalsResults(null);
  r = core.recordRivalsResult(r, { date: '2026-10-26', rank: 5, total: 31 });
  r = core.recordRivalsResult(r, { date: '2026-10-26', rank: 2, total: 31 });
  r = core.recordRivalsResult(r, { date: '2026-10-26', rank: 1, total: 31 });
  r = core.recordRivalsResult(r, { date: '2026-10-26', rank: 9, total: 31 });
  assert.deepEqual(r.last, { date: '2026-10-26', rank: 1, total: 31 });
  assert.equal(r.top3, 1);
  r = core.recordRivalsResult(r, { date: '2026-10-27', rank: 3, total: 31 });
  assert.equal(r.top3, 2);
  assert.deepEqual(r.best, { date: '2026-10-26', rank: 1, total: 31 });
});

test('rivals config: bounded, rewards stay a switch only', () => {
  const c = core.normalizeConfig({ rivals_config: { rivals_shown: 500, sigma: 'x', tier_multipliers: { hard: 9 }, rewards: { enabled: true } } }).rivals;
  assert.equal(c.rivals_shown, 60);
  assert.equal(c.sigma, 0.35);
  assert.equal(c.tier_multipliers.hard, 3);
  assert.equal(c.rewards.enabled, true);
  assert.deepEqual(core.normalizeConfig({}).rivals.tier_multipliers, { easy: 0.7, medium: 1, hard: 1.4 });
});

test('rivals: streaks look like real habits, not long shared runs', () => {
  for (const day of ['2026-10-29', '2027-03-14', '2027-08-01']) {
    const board = core.rivalsStreakBoard(roster, day);
    // With at most 95% chance a day, a run beyond 120 days is about one in a million.
    assert.ok(board[0].streak < 120, `longest ${board[0].streak} on ${day}`);
    assert.ok(board.filter(r => r.streak === 0).length >= 3, 'some rivals did not play today');
  }
  // How often a rival plays matches its reliability over a year.
  const rival = roster.find(r => r.reliability > 0.8), played = Array.from({ length: 365 }, (_, i) => core.rivalStreak(rival, core.addDays('2027-01-01', i)) > 0).filter(Boolean).length / 365;
  assert.ok(Math.abs(played - rival.reliability) < 0.08, `played ${played} vs ${rival.reliability}`);
});

// ---- v1.4 Part 1: smart hint ----
// Replays a line through the game's own move rule (canMove) and returns the final position.
const replay = (state, line) => { for (const move of line) { assert.ok(core.canMove(state, move), JSON.stringify(move)); state = core.applyMove(state, move); } return state; };
const lostPosition = () => {
  const s = (r, suit) => card(r, suit);
  return {
    foundations: [3, 0, 1, 1],
    cells: [s(12, 3), s(8, 2), s(11, 2), s(8, 1)],
    tableau: [[s(13, 3), s(5, 1), s(1, 1), s(4, 2)], [s(2, 3), s(4, 3), s(11, 0), s(3, 1), s(2, 1)],
      [s(12, 1), s(13, 2), s(7, 3), s(2, 2), s(9, 0), s(12, 2), s(8, 3), s(7, 2)],
      [s(6, 0), s(10, 1), s(7, 0), s(11, 1), s(12, 0), s(4, 1), s(6, 2), s(5, 3)], [s(13, 0)],
      [s(6, 3), s(3, 3), s(9, 1), s(10, 0), s(5, 2), s(8, 0), s(7, 1)], [s(10, 3), s(4, 0), s(9, 3), s(6, 1)],
      [s(3, 2), s(5, 0), s(13, 1), s(9, 2), s(11, 3), s(10, 2)]]
  };
};

test('move rule: the solver lists only moves the game accepts, and every useful one', () => {
  const random = lcg(31);
  for (const seed of [25904, 164, 617, 1941, 31465]) {
    let state = deal(seed);
    for (let step = 0; step < 60; step++) {
      const listed = core.listMoves(state);
      for (const move of listed) assert.ok(core.canMove(state, move), `#${seed} step ${step}: listed but refused`);
      // Every accepted move to a foundation, a card or a free cell is listed (to some free cell,
      // to one empty column: the others are the same position).
      const sources = [...state.cells.map((c, pile) => c && { type: 'cell', pile, index: 0 }), ...state.tableau.flatMap((col, pile) => col.map((c, index) => ({ type: 'tableau', pile, index })))].filter(Boolean);
      const targets = [{ type: 'foundation', pile: 0 }, { type: 'foundation', pile: 1 }, { type: 'foundation', pile: 2 }, { type: 'foundation', pile: 3 }, ...state.tableau.map((col, pile) => ({ type: 'tableau', pile }))];
      for (const from of sources) for (const to of targets) {
        const move = { from, to };
        if (!core.canMove(state, move) || (to.type === 'tableau' && !state.tableau[to.pile].length)) continue;
        assert.ok(listed.some(other => sameMove(other, move)), `#${seed} step ${step}: accepted but not listed ${JSON.stringify(move)}`);
      }
      if (!listed.length) break;
      state = core.applyMove(state, listed[Math.floor(random() * listed.length)]);
    }
  }
});

test('move rule: a run moves at once only within (free cells + 1) × 2^(other empty columns)', () => {
  const run = [card(9, 0), card(8, 1), card(7, 0)];
  const base = { foundations: [0, 0, 0, 0], cells: [card(13, 0), card(13, 1), card(13, 2), null], tableau: [[card(10, 2), ...run], [card(10, 1)], [card(2, 2)], [card(2, 3)], [card(3, 2)], [card(3, 3)], [card(4, 2)], [card(4, 3)]] };
  const move = { from: { type: 'tableau', pile: 0, index: 1 }, to: { type: 'tableau', pile: 1 } };
  assert.equal(core.canMove(base, move), false, '3 cards, one free cell, no empty column: at most 2');
  const withColumn = { ...base, tableau: base.tableau.map((col, i) => i === 7 ? [] : col) };
  assert.equal(core.canMove(withColumn, move), true, 'one empty column doubles it to 4');
  // Into the empty column itself, that column does not count.
  assert.equal(core.canMove(withColumn, { from: move.from, to: { type: 'tableau', pile: 7 } }), false);
  assert.equal(core.canMove(base, { from: { type: 'tableau', pile: 0, index: 2 }, to: { type: 'cell', pile: 3 } }), false, 'a free cell takes one card');
});

test('smart hint: lines for the warm-up deals and a sample of every tier replay to a win', () => {
  const pick = (list, n) => Array.from({ length: n }, (_, i) => list[Math.floor(i * list.length / n)]);
  const medium = []; for (let n = 1; medium.length < 12; n += 4999) if (core.tierOf(catalog, n) === 'medium') medium.push(n);
  const groups = { warmup: WARMUP, easy: pick(catalog.easy, 12), medium, hard: pick(catalog.hard, 12) };
  for (const [group, seeds] of Object.entries(groups)) {
    let solved = 0;
    for (const seed of seeds) {
      const result = core.runToEnd(core.analyse(deal(seed), { maxNodes: 60000 }));
      if (result.status !== 'solved') continue;
      solved++;
      assert.deepEqual(replay(deal(seed), result.line).foundations, [13, 13, 13, 13], `#${seed}`);
    }
    assert.ok(solved >= Math.ceil(seeds.length * 0.9), `${group}: ${solved}/${seeds.length} solved`);
  }
});

test('smart hint: three outcomes, never mixed up', () => {
  // A winning line.
  assert.equal(core.runToEnd(core.analyse(deal(25904), {})).status, 'solved');
  // Budget spent: 'unknown', and the hint is a guess, never a claim that the game is lost.
  const short = core.runToEnd(core.analyse(deal(31465), { maxNodes: 2 }));
  assert.equal(short.status, 'unknown');
  const advice = core.hintFromSession(core.hintSession(null, { type: 'analysed', key: core.positionKey(deal(31465)), state: deal(31465), result: short, budget: 300 }), deal(31465), { budget: 300 });
  assert.equal(advice.kind, 'move'); assert.equal(advice.from, 'guess'); assert.ok(core.canMove(deal(31465), advice.move));
  // A provably lost position: only an exhaustive search says so.
  const lost = lostPosition();
  assert.ok(core.listMoves(lost).length > 0);
  assert.equal(core.runToEnd(core.analyse(lost, {})).status, 'unsolvable');
  assert.equal(core.runToEnd(core.analyse(lost, { maxNodes: 1 })).status, 'unknown', 'a cut-off search never claims a loss');
});

test('smart hint: the line is followed without a new search, in any free cell or empty column', () => {
  let state = deal(5152);
  const result = core.runToEnd(core.analyse(state, {}));
  let session = core.hintSession(null, { type: 'analysed', key: core.positionKey(state), state, result, budget: 300 });
  let swapped = 0;
  for (let step = 0; step < result.line.length && !state.foundations.every(v => v === 13); step++) {
    const advice = core.hintFromSession(session, state, { budget: 300 });
    assert.equal(advice.kind, 'move', `step ${step}`); assert.equal(advice.from, 'line');
    let move = advice.move;
    // Play a free-cell move into the last empty free cell instead, as a player might.
    if (move.to.type === 'cell') { const last = state.cells.lastIndexOf(null); if (last !== move.to.pile) { move = { ...move, to: { type: 'cell', pile: last } }; swapped++; } }
    assert.ok(core.canMove(state, move));
    state = core.applyMove(state, move);
  }
  assert.deepEqual(state.foundations, [13, 13, 13, 13]);
  assert.ok(swapped > 0, 'the line survived free-cell swaps');
});

test('smart hint: leaving the line asks for a new search; Undo, Restart and New Game forget it', () => {
  const start = deal(11987), result = core.runToEnd(core.analyse(start, {}));
  const session = core.hintSession(null, { type: 'analysed', key: core.positionKey(start), state: start, result, budget: 300 });
  const lineKeys = new Set(core.makeLine(start, result.line).map(step => step.key));
  const off = core.listMoves(start).map(move => core.applyMove(start, move)).find(next => !lineKeys.has(core.positionKey(next)));
  assert.ok(off, 'a move that leaves the line');
  assert.equal(core.hintFromSession(session, off, { budget: 300 }).kind, 'search');
  for (const type of ['undo', 'restart', 'new_game']) {
    const forgotten = core.hintSession(session, { type });
    assert.equal(forgotten.line, null); assert.equal(forgotten.key, null);
    assert.equal(core.hintFromSession(forgotten, start, { budget: 300 }).kind, 'search', type);
  }
  // An answer for another position is ignored.
  const stale = core.hintSession(null, { type: 'analysed', key: 'other', state: start, result, budget: 300 });
  assert.equal(stale.key, null);
  // A smaller budget that found nothing does not stop a Hint from searching longer.
  const unknown = core.hintSession(null, { type: 'analysed', key: core.positionKey(start), state: start, result: { status: 'unknown', line: [], expanded: 9 }, budget: 150 });
  assert.equal(core.hintFromSession(unknown, start, { budget: 300 }).kind, 'search');
  assert.equal(core.hintFromSession(unknown, start, { budget: 150 }).kind, 'move');
});

test('smart hint: budgets are kept, and a search run in slices gives the same answer', () => {
  const state = deal(31465);
  const capped = core.createSearch(state, { maxNodes: 500 }).run();
  assert.ok(capped.expanded <= 500);
  let clock = 0; const timed = core.createSearch(state, { maxNodes: 1e9, timeLimitMs: 50, now: () => (clock += 30) });
  assert.equal(timed.run().status, 'unknown');
  const whole = core.runToEnd(core.analyse(deal(9998), {}));
  const iterator = core.analyse(deal(9998), {}); let slices = 0, step;
  while (!(step = iterator.next()).done) slices++;
  assert.ok(slices >= 0); assert.deepEqual(step.value, whole);
  // Time spent between slices does not count.
  let now = 0; const sliced = core.createSearch(deal(31465), { maxNodes: 1e9, timeLimitMs: 100, now: () => now });
  sliced.run(10); now += 1000; assert.equal(sliced.run(200), null, 'paused time is not spent');
});

test('hint and tips config: bounds, and an optional hint cap', () => {
  const c = core.normalizeConfig({ hint_config: { smart_hint: 'yes', time_budget_ms: 9000, node_limit: 5, background_budget_ms: -4, max_hints_per_game: 3 }, tips_config: { enabled: false, free_cells_tip_min_occupied: 9 } });
  assert.deepEqual(c.hint, { smart_hint: true, time_budget_ms: 3000, node_limit: 1000, background_analysis: true, background_budget_ms: 0, max_hints_per_game: 3 });
  assert.deepEqual(c.tips, { enabled: false, free_cells_tip_min_occupied: 4 });
  assert.equal(core.normalizeConfig({ hint_config: { max_hints_per_game: 'none' } }).hint.max_hints_per_game, null);
  assert.equal(config.hint.max_hints_per_game, null);
  assert.equal(core.migrateState({}).tips.freeCells, false);
  assert.equal(core.migrateState({ tips: { freeCells: true } }).tips.freeCells, true);
});

// ---- v1.4 Part 2: card size ----
test('undo: a card reaching the foundations counts once, whatever Undo does', () => {
  let peak = 0, counted = 0;
  // Totals after each event: move up (1), Undo (0), up again (1), Undo (0), another way up (1), up (2).
  for (const total of [1, 0, 1, 0, 1, 2]) { const gain = core.foundationGain(peak, [total, 0, 0, 0]); peak = gain.peak; counted += gain.gained; }
  assert.equal(counted, 2);
});

test('sizes: Normal cards by default (owner decision), only a size picked in Settings is kept', () => {
  assert.deepEqual(core.displaySizes({}), { cardSize: 'normal', cardSizeChosen: false });
  assert.deepEqual(core.displaySizes({ cardSize: 'large' }), { cardSize: 'normal', cardSizeChosen: false }, 'the old Large default');
  assert.deepEqual(core.displaySizes({ cardSize: 'xl', cardSizeChosen: true }), { cardSize: 'xl', cardSizeChosen: true });
  assert.deepEqual(core.displaySizes({ cardSize: 'bogus', cardSizeChosen: true, textSize: 'xl' }), { cardSize: 'normal', cardSizeChosen: false });
});

// ---- v1.4 Part 3: feel ----
test('feel: foundation notes rise one octave from the first card to the last', () => {
  assert.equal(core.foundationPitch(620, 1), 620);
  assert.equal(core.foundationPitch(620, 52), 1240);
  assert.ok(core.foundationPitch(620, 26) > 620 && core.foundationPitch(620, 26) < 1240);
  assert.equal(core.foundationPitch(620, 0), 620); assert.equal(core.foundationPitch(620, 99), 1240);
});

test('feel: a King completes a suit; the fourth King is the win, celebrated by the victory', () => {
  assert.equal(core.landingFeel([5, 0, 0, 0], 0), 'foundation');
  assert.equal(core.landingFeel([13, 4, 0, 0], 0), 'suit');
  assert.equal(core.landingFeel([13, 13, 13, 13], 2), 'win');
});

test('feel config: switches, a volume between 0 and 1, the suit effect', () => {
  assert.deepEqual(config.feel, { sounds: true, haptics: true, sound_volume: 0.6, suit_complete_effect: true });
  const c = core.normalizeConfig({ feel_config: { sounds: false, haptics: 'no', sound_volume: 4, suit_complete_effect: false } }).feel;
  assert.deepEqual(c, { sounds: false, haptics: true, sound_volume: 1, suit_complete_effect: false });
});

// ---- v1.4 Part 4: languages ----
const enBlock = html.slice(html.indexOf('      const EN_STRINGS={'), html.indexOf('      let languagePack=null;'));
const english = new Function(`${enBlock}; return {strings: EN_STRINGS, tutorial: EN_TUTORIAL, praise: EN_PRAISE};`)();
const packOf = code => JSON.parse(fs.readFileSync(path.join(assets, 'i18n', `${code}.json`), 'utf8'));

test('languages: 18, in the fixed order, each named in its own language', () => {
  assert.deepEqual(core.LANGUAGES.map(l => l.name), ['English', 'Português (Portugal)', 'Português (Brasil)', 'Español', 'Français', 'Italiano', 'Deutsch', 'Nederlands', 'Polski', 'Türkçe', 'Русский', 'Українська', '日本語', '한국어', '简体中文', 'Bahasa Indonesia', 'Svenska', 'Čeština']);
  assert.deepEqual(config.languages, { enabled: core.LANGUAGE_CODES, beta: [] });
  const c = core.normalizeConfig({ languages_config: { enabled: ['ja', 'xx', 'pt-BR'], beta: ['ja', 'en', 'ko'] } }).languages;
  assert.deepEqual(c, { enabled: ['en', 'pt-BR', 'ja'], beta: ['ja'] });
});

test('languages: device tags map to ours; Android per-app language, then the saved choice, then the device', () => {
  const cases = { 'pt-BR': 'pt-BR', 'pt-PT': 'pt', 'pt': 'pt', 'pt_BR': 'pt-BR', 'zh-CN': 'zh-Hans', 'zh-Hans-CN': 'zh-Hans', 'zh-SG': 'zh-Hans', 'zh-TW': null, 'zh-Hant-HK': null, 'in-ID': 'id', 'id-ID': 'id', 'en-GB': 'en', 'de-AT': 'de', 'uk-UA': 'uk', 'ro-RO': null, '': null };
  for (const [tag, want] of Object.entries(cases)) assert.equal(core.languageForTag(tag), want, tag);
  assert.equal(core.pickLanguage({ device: ['ro-RO', 'pt-BR'] }), 'pt-BR');
  assert.equal(core.pickLanguage({ device: ['zh-TW'] }), 'en');
  assert.equal(core.pickLanguage({ saved: 'fr', device: ['de-DE'] }), 'fr');
  assert.equal(core.pickLanguage({ app: 'ja-JP', saved: 'fr', device: ['de-DE'] }), 'ja');
  assert.equal(core.pickLanguage({ saved: 'fr', device: ['de-DE'], enabled: ['en', 'de'] }), 'de');
  assert.equal(core.pickLanguage({ saved: 'bogus' }), 'en');
});

test('language files: every language has every key, the same placeholders, no HTML', () => {
  for (const { code } of core.LANGUAGES.slice(1)) {
    const raw = packOf(code), clean = core.cleanLanguagePack(raw, english);
    assert.equal(raw.language, code);
    assert.deepEqual(Object.keys(clean.strings).sort(), Object.keys(english.strings).sort(), `${code}: missing or refused keys`);
    assert.deepEqual(Object.keys(raw.strings).filter(key => !(key in english.strings)), [], `${code}: unknown keys`);
    assert.equal(clean.tutorial.filter(Boolean).length, english.tutorial.length, `${code}: tutorial`);
    assert.ok(clean.praise.length >= 3, `${code}: praise`);
    // A text with plural forms has one for every form the language uses, for counts 0 to 120.
    const rules = new Intl.PluralRules(core.LANGUAGES.find(l => l.code === code).locale);
    for (const [key, value] of Object.entries(clean.strings)) if (typeof value === 'object') for (let n = 0; n <= 120; n++) assert.ok(value[rules.select(n)] || value.other, `${code}.${key} for ${n}`);
  }
});

test('language files: a bad file is cleaned, never trusted', () => {
  const clean = core.cleanLanguagePack({ strings: { score: '<b>Score</b>', level: 'Nível', hintMove: 'Move {card}', newGame: 'Novo', bogus: 'x', rivalsDays: { one: '{number} dia', other: 'dias' } }, tutorial: [['a']], praise: ['<i>', 'Boa!'] }, english);
  assert.equal(clean.strings.score, undefined, 'HTML refused');
  assert.equal(clean.strings.level, undefined, 'a placeholder missing');
  assert.equal(clean.strings.hintMove, 'Move {card}');
  assert.equal(clean.strings.newGame, 'Novo');
  assert.equal(clean.strings.bogus, undefined, 'unknown key dropped');
  assert.equal(clean.strings.rivalsDays, undefined, 'plural "other" lacks {number}');
  assert.equal(clean.tutorial[0], null);
  assert.deepEqual(clean.praise, ['Boa!']);
  assert.deepEqual(core.cleanLanguagePack(null, english).strings, {});
});

test('calendar: the empty cells before day 1 follow the first day of the week', () => {
  // 1 November 2026 is a Sunday.
  assert.equal(core.calendarOffset(0, 0), 0);
  assert.equal(core.calendarOffset(0, 1), 6);
  assert.equal(core.calendarOffset(3, 1), 2);
  assert.equal(core.calendarOffset(3, 0), 3);
});

// ---- v1.4 Part 5: performance ----
test('performance config: bounds and the bundled default', () => {
  assert.deepEqual(config.performance, { auto_reduce_effects: true, low_end_memory_class_mb: 128, old_android_max_sdk: 27, fps_sample_seconds: 4, low_fps_threshold: 40, low_end_total_ram_mb: 3072 });
  assert.deepEqual(core.normalizeConfig({ performance_config: { auto_reduce_effects: 'no', low_end_memory_class_mb: -5, fps_sample_seconds: 99, low_fps_threshold: 3 } }).performance,
    { auto_reduce_effects: true, low_end_memory_class_mb: 0, old_android_max_sdk: 27, fps_sample_seconds: 15, low_fps_threshold: 10, low_end_total_ram_mb: 3072 });
});

test('reduce effects: On and Off are the player choice; Auto looks at the phone, then the sampled frame rate', () => {
  const c = config.performance, strong = { lowRam: false, memoryClass: 256, sdk: 34 };
  assert.deepEqual(core.effectsDecision({ setting: 'on', config: c, device: strong }), { low: true, reason: 'setting' });
  assert.deepEqual(core.effectsDecision({ setting: 'off', config: c, device: { lowRam: true }, sampledFps: 10 }), { low: false, reason: 'setting' });
  assert.deepEqual(core.effectsDecision({ setting: 'auto', config: c, device: strong }), { low: false, reason: 'auto' });
  assert.deepEqual(core.effectsDecision({ setting: 'bogus', config: c, device: { ...strong, lowRam: true } }), { low: true, reason: 'low_ram' });
  assert.equal(core.effectsDecision({ config: c, device: { memoryClass: 96 } }).reason, 'memory');
  assert.equal(core.effectsDecision({ config: c, device: { memoryClass: 128, sdk: 34 } }).low, false, '128 MB is not below 128');
  assert.equal(core.effectsDecision({ config: c, device: { sdk: 27 } }).reason, 'old_android');
  assert.equal(core.effectsDecision({ config: c, device: { sdk: 28 } }).low, false);
  assert.equal(core.effectsDecision({ config: c, device: { ...strong, totalRamMb: 2860 } }).reason, 'total_ram', 'a 3 GB phone');
  assert.equal(core.effectsDecision({ config: c, device: { ...strong, totalRamMb: 3700 } }).low, false, 'a 4 GB phone');
  assert.equal(core.effectsDecision({ config: { ...c, low_end_total_ram_mb: 0 }, device: { ...strong, totalRamMb: 1000 } }).low, false, '0 switches the RAM check off');
  assert.equal(core.effectsDecision({ config: c, device: strong, sampledFps: 39.9 }).reason, 'low_fps');
  assert.equal(core.effectsDecision({ config: c, device: strong, sampledFps: 40 }).low, false);
  assert.deepEqual(core.effectsDecision({ config: { ...c, auto_reduce_effects: false }, device: { lowRam: true } }), { low: false, reason: 'auto_off' });
  assert.equal(core.effectsDecision({}).low, false, 'the browser preview: nothing known, full effects');
});

test('frame stats: fps, average, p95, worst, long and dropped frames', () => {
  assert.deepEqual(core.frameStats([]), { frames: 0, fps: 0, avgMs: 0, p95Ms: 0, worstMs: 0, longFrames: 0, dropped: 0 });
  const smooth = core.frameStats(Array(60).fill(1000 / 60));
  assert.equal(smooth.fps, 60);
  assert.equal(smooth.dropped, 0);
  const janky = core.frameStats([...Array(18).fill(16.7), 100, 33.4, NaN, -1]);
  assert.equal(janky.frames, 20);
  assert.equal(janky.worstMs, 100);
  assert.equal(janky.longFrames, 1);
  assert.equal(janky.dropped, 5 + 1);
  assert.equal(janky.fps, Math.round(20 * 1000 / (18 * 16.7 + 133.4) * 10) / 10);
});


test('automatic chains: each card flies a little faster than the one before, within bounds', () => {
  assert.deepEqual(core.chainTiming(0), { flightMs: 260, gapMs: 40 });
  const steps = Array.from({ length: 12 }, (_, step) => core.chainTiming(step));
  for (let i = 1; i < steps.length; i++) assert.ok(steps[i].flightMs <= steps[i - 1].flightMs && steps[i].gapMs <= steps[i - 1].gapMs);
  assert.ok(steps[1].flightMs > 235, 'gradual, not a jump');
  assert.deepEqual(core.chainTiming(100), { flightMs: 170, gapMs: 20 });
  assert.deepEqual(core.chainTiming(-3), core.chainTiming(0));
});

test('language files: an English text with plural forms is checked against its "other" form', () => {
  const english = { strings: { deadEndUndo: { one: 'Undo 1 move', other: 'Undo {number} moves' } }, tutorial: [], praise: [] };
  const clean = core.cleanLanguagePack({ strings: { deadEndUndo: { one: 'Desfaz 1 jogada', other: 'Desfaz {number} jogadas' } } }, english);
  assert.deepEqual(clean.strings.deadEndUndo, { one: 'Desfaz 1 jogada', other: 'Desfaz {number} jogadas' });
  assert.equal(core.cleanLanguagePack({ strings: { deadEndUndo: 'Desfaz jogadas' } }, english).strings.deadEndUndo, undefined, 'the number is missing');
});

test('campaign goals: each once, from what is reached; a new player starts today, an older save starts with its past', () => {
  const fresh = core.cleanGoals(undefined, { existing: false, today: '2026-11-06' });
  assert.deepEqual(fresh, { firstDay: '2026-11-06', sent: [] });
  assert.deepEqual(core.goalsReached(fresh, { today: '2026-11-06' }), [], 'nothing on day one');
  assert.deepEqual(core.goalsReached(fresh, { wins: 3, dailyWins: 1, introDone: true, today: '2026-11-06' }), ['intro_complete', 'first_daily_win', 'wins_3']);
  assert.deepEqual(core.goalsReached(fresh, { wins: 10, today: '2026-11-07' }), ['wins_3', 'wins_10', 'day2_return']);
  const sent = core.markGoals(fresh, ['wins_3', 'day2_return', 'bogus']);
  assert.deepEqual(sent.sent, ['wins_3', 'day2_return']);
  assert.deepEqual(core.goalsReached(sent, { wins: 10, today: '2026-11-09' }), ['wins_10'], 'never twice');
  const older = core.cleanGoals(undefined, { existing: true, today: '2026-11-06', progress: { wins: 4, dailyWins: 0, introDone: true } });
  assert.equal(older.firstDay, null);
  assert.deepEqual(older.sent.sort(), ['day2_return', 'intro_complete', 'wins_3'].sort(), 'past progress is not reported');
  assert.deepEqual(core.goalsReached(older, { wins: 10, dailyWins: 1, introDone: true, today: '2026-12-01' }), ['first_daily_win', 'wins_10']);
  assert.deepEqual(core.cleanGoals({ firstDay: 'x', sent: ['wins_3', 'wins_3', 7] }), { firstDay: null, sent: ['wins_3'] });
  assert.deepEqual(core.GOAL_EVENTS, ['intro_complete', 'first_daily_win', 'wins_3', 'wins_10', 'day2_return']);
});
