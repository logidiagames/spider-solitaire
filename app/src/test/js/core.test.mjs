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
const difficulty = config.difficulty, WARMUP = config.difficulty.first_games.seeds;

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
  const list = ['win_no_hint', 'win_under_moves', 'empty_columns', 'complete_suits', 'win_random', 'foundation_cards']
    .map(type => config.missions.definitions.find(d => d.type === type)).map(d => ({ ...d, progress: 0, claimed: false }));
  let state = { list };
  state = core.applyMissionEvent(state, { type: 'win', mode: 'numbered', usedUndo: true, usedHint: false, seconds: 900, moves: 170 });
  state = core.applyMissionEvent(state, { type: 'win', mode: 'random', usedUndo: false, usedHint: true, seconds: 900, moves: 300 });
  state = core.applyMissionEvent(state, { type: 'column_emptied' });
  state = core.applyMissionEvent(state, { type: 'suit_complete', count: 2 });
  state = core.applyMissionEvent(state, { type: 'foundation', count: 52 });
  state = core.applyMissionEvent(state, { type: 'foundation', count: 52 });
  assert.deepEqual(state.list.map(m => m.progress), [1, 1, 1, 2, 1, 52]);
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
  assert.equal(core.starsFor(config, { undos: 0, seconds: 720, moves: 180 }), 3);
  assert.equal(core.starsFor(config, { undos: 0, seconds: 721, moves: 100 }), 2);
  assert.equal(core.starsFor(config, { undos: 0, seconds: 300, moves: 181 }), 2);
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
  assert.equal(core.advanceIntro(start, 0, { event: 'start', seed: 5965327 }), 1);
  assert.equal(core.advanceIntro(start, 1, { event: 'win', seed: 5152 }), 1);
  const choice = core.normalizeConfig({ difficulty_config: { first_games: { ignore_selected_mode: false } } }).difficulty;
  assert.equal(core.firstGameSeed(choice, 0, { selectedTier: 'hard' }), null);
  assert.equal(core.firstGameSeed(choice, 0, { selectedTier: 'easy' }), 5965327);
});

test('difficulty config: bad warm-up settings fall back to the defaults', () => {
  const d = core.normalizeConfig({ difficulty_config: { first_games: { seeds: [3, 3], advance_on: 'never' }, default_tier: 'impossible' } }).difficulty;
  assert.deepEqual(d.first_games.seeds, WARMUP);
  assert.equal(d.first_games.advance_on, 'win');
  assert.equal(d.default_tier, 'easy');
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
  assert.ok(at(medium, 0.5) > 480 && at(medium, 0.5) < 740, `median ${at(medium, 0.5)}`);
  assert.ok(at(medium, 0.9) / at(medium, 0.1) > 1.8 && at(medium, 0.9) / at(medium, 0.1) < 4, 'spread');
  const ratio = at(hard, 0.5) / at(medium, 0.5);
  assert.ok(ratio > 1.5 && ratio < 1.7, `hard/medium ${ratio}`);
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
  assert.deepEqual(core.normalizeConfig({}).rivals.tier_multipliers, { easy: 0.8, medium: 1, hard: 1.6 });
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

// ---- Spider rules ----
const S = (r, s = 3, up = true, id) => (id === undefined ? { r, s, up } : { r, s, up, id });
const emptyBoard = () => ({ tableau: Array.from({ length: 10 }, () => [S(13, 0)]), stock: [], foundations: Array(8).fill(0), done: [], suits: 1 });
const tmove = (fromPile, index, toPile) => ({ from: { type: 'tableau', pile: fromPile, index }, to: { type: 'tableau', pile: toPile } });

test('deals: 104 different cards, 6-6-6-6-5-5-5-5-5-5 with the last face up, 50 in the stock', () => {
  for (const [tier, suits] of [['easy', [3]], ['medium', [2, 3]], ['hard', [0, 1, 2, 3]]]) {
    const board = core.newBoard(12345, core.suitsOf(tier));
    assert.deepEqual(board.tableau.map(column => column.length), [6, 6, 6, 6, 5, 5, 5, 5, 5, 5]);
    for (const column of board.tableau) column.forEach((card, index) => assert.equal(card.up, index === column.length - 1));
    assert.equal(board.stock.length, 50);
    const all = [...board.tableau.flat(), ...board.stock];
    assert.equal(new Set(all.map(card => card.id)).size, 104);
    assert.deepEqual([...new Set(all.map(card => card.s))].sort(), suits, tier);
    for (const suit of suits) for (let r = 1; r <= 13; r++) assert.equal(all.filter(card => card.s === suit && card.r === r).length, 8 / suits.length);
  }
  assert.deepEqual(core.dealGame(77, 2), core.dealGame(77, 2));
  assert.notDeepEqual(core.dealGame(77, 2), core.dealGame(78, 2));
});

test('move rule: any card on one rank higher; only runs of one suit move together; empty columns take anything', () => {
  const state = emptyBoard();
  state.tableau[0] = [S(9, 2)];
  state.tableau[1] = [S(13, 0), S(8, 3), S(7, 3)];
  state.tableau[2] = [S(13, 0), S(8, 1), S(7, 3)];
  state.tableau[3] = [];
  assert.ok(core.canMove(state, tmove(1, 1, 0)), '8♠7♠ onto 9♥');
  assert.ok(!core.canMove(state, tmove(2, 1, 0)), '8♦7♠ is not one suit');
  assert.ok(!core.canMove(state, tmove(2, 2, 0)), '7♠ does not go on 9♥');
  assert.ok(core.canMove(state, tmove(2, 2, 3)), 'an empty column takes any card');
  const hidden = { ...state, tableau: state.tableau.map((column, i) => i === 1 ? [S(13, 0), S(8, 3, false), S(7, 3)] : column) };
  assert.ok(!core.canMove(hidden, tmove(1, 1, 0)), 'never a face-down card');
  for (const listed of core.listMoves(state, { all: true })) assert.ok(core.canMove(state, listed), JSON.stringify(listed));
});

test('deal: one face-up card on every column, only when no column is empty', () => {
  const board = core.newBoard(5, 1);
  assert.ok(core.canMove(board, core.DEAL));
  const next = core.applyMove(board, core.DEAL);
  assert.equal(next.stock.length, 40);
  next.tableau.forEach((column, pile) => { assert.ok(column.length >= board.tableau[pile].length); assert.equal(column.at(-1).up, true) });
  const withEmpty = { ...board, tableau: board.tableau.map((column, i) => i === 4 ? [] : column) };
  assert.ok(!core.canMove(withEmpty, core.DEAL));
  assert.ok(!core.listMoves(withEmpty).some(listed => listed.from.type === 'stock'));
  assert.ok(!core.canMove({ ...board, stock: [] }, core.DEAL));
});

test('a complete run leaves by itself, turns the card under it and scores +100 − 1', () => {
  const state = emptyBoard();
  state.tableau[0] = [S(5, 1, false, 300), ...Array.from({ length: 12 }, (_, i) => S(13 - i, 3, true, 200 + i))];
  state.tableau[1] = [S(13, 0), S(1, 3, true, 212)];
  const next = core.applyMove(state, tmove(1, 1, 0));
  assert.deepEqual(next.tableau[0], [S(5, 1, true, 300)]);
  assert.deepEqual(next.foundations, [13, 0, 0, 0, 0, 0, 0, 0]);
  assert.deepEqual(next.done, [3]);
  assert.equal(core.moveScore(state, next), 99);
  assert.equal(core.moveScore(next, core.applyMove(next, tmove(2, 0, 3))), -1);
  assert.equal(core.won({ ...next, foundations: Array(8).fill(13) }), true);
});

test('solver: a found solution is a sequence of legal moves that wins the deal (1 suit)', () => {
  for (const seed of [1, 2, 3]) {
    const board = core.newBoard(seed, 1);
    const result = core.runToEnd(core.analyse(board, { maxNodes: 100000 }));
    assert.equal(result.status, 'solved', `deal ${seed}`);
    let state = board;
    for (const step of result.line) { assert.ok(core.canMove(state, step)); state = core.applyMove(state, step) }
    assert.ok(core.won(state));
  }
});

test('smart hint: the line is followed without a new search, and leaving it asks for one', () => {
  const board = core.newBoard(3, 1);
  const result = core.runToEnd(core.analyse(board, { maxNodes: 100000 }));
  const line = core.makeLine(board, result.line);
  let state = board;
  for (let i = 0; i < 10; i++) { const next = core.nextOnLine(line, state); assert.ok(next, `step ${i}`); state = core.applyMove(state, next.move) }
  const firstKey = core.positionKey(core.applyMove(board, result.line[0]));
  const off = core.listMoves(board).find(listed => core.positionKey(core.applyMove(board, listed)) !== firstKey && !line.some(step => step.key === core.positionKey(core.applyMove(board, listed))));
  if (off) assert.equal(core.nextOnLine(line, core.applyMove(board, off)), null);
});

test('stuck: no legal move at all, a won game is never stuck, and the hint has a move while one exists', () => {
  const stuck = emptyBoard();
  assert.equal(core.stuckReason(stuck, { maxNodes: 1000 }), 'none');
  assert.equal(core.stuckReason({ ...stuck, foundations: Array(8).fill(13) }), null);
  const board = core.newBoard(9, 4);
  const hint = core.suggestMove(board, { maxNodes: 300 });
  assert.ok(hint.move && core.canMove(board, hint.move));
});

const spiderCatalog = () => core.cleanCatalog(JSON.parse(fs.readFileSync(path.join(assets, 'deal_catalog.json'), 'utf8')));
test('deals per tier: never one of the last 50; listed deals first; the Daily is the same for everyone and never a warm-up deal', () => {
  const bundled = spiderCatalog();
  let history = null; const seen = [];
  for (let i = 0; i < 60; i++) { const pick = core.pickDeal(bundled, difficulty, 'hard', { history }); assert.ok(!seen.slice(-50).includes(pick.seed)); seen.push(pick.seed); history = core.rememberDeal(history, 'hard', pick.seed) }
  if (bundled.easy.length) assert.ok(bundled.easy.includes(core.pickDeal(bundled, difficulty, 'easy').seed));
  const a = core.dailyDeal(bundled, config, '2026-11-16'), b = core.dailyDeal(bundled, config, '2026-11-16');
  assert.deepEqual(a, b);
  assert.equal(a.tier, config.difficulty.daily_tiers_by_weekday[1]);
  for (let day = 1; day <= 28; day++) assert.ok(!WARMUP.includes(core.dailyDeal(bundled, config, `2026-02-${String(day).padStart(2, '0')}`).seed));
});

test('catalog: listed deals are won by the solver, easiest first; the warm-up deals are won with 1 suit', () => {
  const bundled = spiderCatalog();
  const wins = (seed, suits) => core.runToEnd(core.analyse(core.newBoard(seed, suits), { maxNodes: 150000 })).status === 'solved';
  for (const seed of bundled.easy.slice(0, 2)) assert.ok(wins(seed, 1), `easy ${seed}`);
  for (const seed of bundled.medium.slice(0, 1)) assert.ok(wins(seed, 2), `medium ${seed}`);
  for (const seed of WARMUP) assert.ok(wins(seed, 1), `warm-up ${seed}`);
});

test('adaptive difficulty: starts at the easiest band, half a band up per win, a band down per loss', () => {
  let level = core.cleanAdapt(null);
  assert.deepEqual(level, { easy: 0, medium: 0, hard: 0 });
  level = core.adaptAfter(level, 'easy', true); assert.equal(core.adaptBand(level, 'easy'), 0);
  level = core.adaptAfter(level, 'easy', true); assert.equal(core.adaptBand(level, 'easy'), 1);
  for (let i = 0; i < 20; i++) level = core.adaptAfter(level, 'easy', true);
  assert.equal(core.adaptBand(level, 'easy'), core.ADAPT_BANDS - 1);
  level = core.adaptAfter(level, 'easy', false); assert.equal(core.adaptBand(level, 'easy'), core.ADAPT_BANDS - 2);
  for (let i = 0; i < 20; i++) level = core.adaptAfter(level, 'easy', false);
  assert.equal(level.easy, 0); assert.equal(level.medium, 0);
  const list = Array.from({ length: 100 }, (_, i) => i + 1);
  assert.deepEqual(core.bandSlice(list, 0), list.slice(0, 20));
  assert.deepEqual(core.bandSlice(list, 4), list.slice(80));
  const catalog = core.cleanCatalog({ easy: list });
  for (let i = 0; i < 30; i++) assert.ok(core.pickDeal(catalog, difficulty, 'easy', { band: 1 }).seed > 20 && core.pickDeal(catalog, difficulty, 'easy', { band: 1 }).seed <= 40);
  assert.equal(core.migrateState({ version: 2, adaptLevel: { easy: 3.5, medium: 99 } }).adaptLevel.medium, core.ADAPT_BANDS - 1);
});
