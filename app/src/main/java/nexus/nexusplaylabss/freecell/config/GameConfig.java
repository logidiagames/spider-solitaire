package com.nexusplaylabss.freecell.config;

import static com.nexusplaylabss.freecell.config.RemoteJsonConfig.bool;
import static com.nexusplaylabss.freecell.config.RemoteJsonConfig.intIn;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Game settings from Remote Config (unlock levels, Daily Challenge, economy, theme prices),
 * validated here and handed to the page as one JSON object. The page validates again with the
 * same bounds (FreeCellCore.normalizeConfig in freecell.html).
 */
public final class GameConfig {
    public static final String PROGRESSION = "progression_config";
    public static final String DAILY_CHALLENGE = "daily_challenge_config";
    public static final String ECONOMY = "economy_config";
    public static final String THEMES = "themes_config";
    public static final String DAILY_BONUS = "daily_bonus_config";
    public static final String MISSIONS = "missions_config";
    public static final String REMOVE_ADS = "remove_ads_config";
    public static final String RATING = "rating_config";
    public static final String STORE = "store_config";
    public static final String EVENTS = "events_config";
    public static final String REWARDED = "rewarded_config";
    public static final String NOTIFICATIONS = "notifications_config";
    public static final String STARS = "stars_config";
    public static final String ACHIEVEMENTS = "achievements_config";
    public static final String DIFFICULTY = "difficulty_config";
    public static final String RIVALS = "rivals_config";
    public static final String HINT = "hint_config";
    public static final String TIPS = "tips_config";
    public static final String FEEL = "feel_config";
    public static final String LANGUAGES = "languages_config";
    public static final String PERFORMANCE = "performance_config";
    public static final List<String> KEYS = Arrays.asList(
            PROGRESSION, DAILY_CHALLENGE, ECONOMY, THEMES, DAILY_BONUS, MISSIONS, REMOVE_ADS, RATING, STORE, EVENTS,
            REWARDED, NOTIFICATIONS, STARS, ACHIEVEMENTS, DIFFICULTY, RIVALS, HINT, TIPS, FEEL, LANGUAGES, PERFORMANCE);
    private static final List<String> ACHIEVEMENT_COUNTERS = Arrays.asList("wins", "no_undo_wins",
            "three_star_wins", "daily_streak", "daily_wins", "numbered_deals", "items_bought", "fast_win", "rivals_top3");
    private static final List<String> STORE_CATEGORIES = Arrays.asList("card_faces", "card_backs", "backgrounds", "victory_effects");

    static final List<String> THEME_IDS =
            Arrays.asList("classic", "aurora", "sunlight", "neon", "moonstone", "emberwood");
    // v1.3: the base win reward follows the deal tier; the Daily keeps its own.
    private static final String[] REWARD_KEYS = {"easy", "medium", "hard", "daily"};
    private static final int[] DEFAULT_REWARDS = {10, 20, 30, 50};
    static final List<String> TIERS = Arrays.asList("easy", "medium", "hard");
    private static final String[] DEFAULT_DAILY_TIERS = {"hard", "medium", "medium", "medium", "medium", "medium", "hard"};
    static final List<String> POOLS = Arrays.asList("very_easy", "easy", "medium", "hard");
    private static final int[] DEFAULT_FIRST_GAMES = {25904, 5152, 11987, 13464, 9998};
    /** Default pool weights per tier (easy, medium, hard), in POOLS order. */
    private static final int[][] DEFAULT_MIXES = {{60, 40, 0, 0}, {0, 30, 70, 0}, {0, 0, 60, 40}};
    private static final int[] DEFAULT_PRICES = {0, 0, 0, 300, 500, 800};
    private static final List<String> DEFAULT_FREE = Arrays.asList("classic", "aurora", "sunlight");
    private static final int[] DEFAULT_BONUS_CYCLE = {20, 20, 30, 30, 40, 40, 100};
    private static final List<String> MISSION_TYPES = Arrays.asList("win_games", "win_no_undo",
            "win_no_hint", "win_fast", "win_under_moves", "win_daily", "win_random", "win_winnable",
            "win_numbered", "foundation_cards", "complete_suits", "empty_columns", "play_games");

    private GameConfig() {}

    public static String bundledAsset(String key) {
        return key + "_defaults.json";
    }

    /** {@code source} returns the JSON object of each parameter (remote, or bundled default). */
    public static JSONObject sanitize(Function<String, JSONObject> source) {
        try {
            JSONObject progression = source.apply(PROGRESSION);
            JSONObject unlock = object(progression, "unlock_levels");
            JSONObject daily = source.apply(DAILY_CHALLENGE);
            JSONObject economy = source.apply(ECONOMY);
            JSONObject rewards = object(economy, "win_rewards");
            JSONObject fast = object(economy, "bonus_fast");
            JSONObject themes = source.apply(THEMES);
            JSONObject prices = object(themes, "prices");

            JSONObject cleanRewards = new JSONObject();
            for (int i = 0; i < REWARD_KEYS.length; i++) {
                cleanRewards.put(REWARD_KEYS[i], intIn(rewards, REWARD_KEYS[i], 0, 1000, DEFAULT_REWARDS[i]));
            }
            JSONObject cleanPrices = new JSONObject();
            for (int i = 0; i < THEME_IDS.size(); i++) {
                String id = THEME_IDS.get(i);
                cleanPrices.put(id, intIn(prices, id, 0, 100000, DEFAULT_PRICES[i]));
            }

            return new JSONObject()
                    .put(PROGRESSION, new JSONObject().put("unlock_levels", new JSONObject()
                            .put("daily_challenge", intIn(unlock, "daily_challenge", 1, 100, 2))
                            .put("missions", intIn(unlock, "missions", 1, 100, 3))))
                    .put(DAILY_CHALLENGE, new JSONObject()
                            .put("history_days", intIn(daily, "history_days", 31, 1000, 400)))
                    .put(ECONOMY, new JSONObject()
                            .put("win_rewards", cleanRewards)
                            .put("bonus_no_undo", intIn(economy, "bonus_no_undo", 0, 1000, 10))
                            .put("bonus_fast", new JSONObject()
                                    .put("seconds", intIn(fast, "seconds", 30, 7200, 600))
                                    .put("coins", intIn(fast, "coins", 0, 1000, 10)))
                            .put("daily_streak_bonus_per_day",
                                    intIn(economy, "daily_streak_bonus_per_day", 0, 100, 2))
                            .put("daily_streak_bonus_cap",
                                    intIn(economy, "daily_streak_bonus_cap", 0, 1000, 20))
                            .put("pay_once_per_deal_seed",
                                    bool(economy, "pay_once_per_deal_seed", true)))
                    .put(THEMES, new JSONObject()
                            .put("prices", cleanPrices)
                            .put("free", new JSONArray(freeThemes(themes))))
                    .put(DAILY_BONUS, dailyBonus(source.apply(DAILY_BONUS)))
                    .put(MISSIONS, missions(source.apply(MISSIONS)))
                    .put(REMOVE_ADS, removeAds(source.apply(REMOVE_ADS)))
                    .put(RATING, rating(source.apply(RATING)))
                    .put(STORE, store(source.apply(STORE)))
                    .put(EVENTS, events(source.apply(EVENTS)))
                    .put(REWARDED, rewarded(source.apply(REWARDED)))
                    .put(NOTIFICATIONS, notifications(source.apply(NOTIFICATIONS)))
                    .put(STARS, stars(source.apply(STARS)))
                    .put(ACHIEVEMENTS, achievements(source.apply(ACHIEVEMENTS)))
                    .put(DIFFICULTY, difficulty(source.apply(DIFFICULTY)))
                    .put(RIVALS, rivals(source.apply(RIVALS)))
                    .put(HINT, hint(source.apply(HINT)))
                    .put(TIPS, new JSONObject()
                            .put("enabled", bool(source.apply(TIPS), "enabled", true))
                            .put("free_cells_tip_min_occupied", intIn(source.apply(TIPS), "free_cells_tip_min_occupied", 1, 4, 3)))
                    // v1.4 Part 3: sounds and haptics switches, master volume 0–1, the suit effect.
                    .put(FEEL, new JSONObject()
                            .put("sounds", bool(source.apply(FEEL), "sounds", true))
                            .put("haptics", bool(source.apply(FEEL), "haptics", true))
                            .put("sound_volume", number(numberIn(source.apply(FEEL), "sound_volume", 0, 1, 0.6)))
                            .put("suit_complete_effect", bool(source.apply(FEEL), "suit_complete_effect", true)))
                    .put(LANGUAGES, languages(source.apply(LANGUAGES)))
                    // v1.4 Part 5: when Auto turns the reduced effects on.
                    .put(PERFORMANCE, new JSONObject()
                            .put("auto_reduce_effects", bool(source.apply(PERFORMANCE), "auto_reduce_effects", true))
                            .put("low_end_memory_class_mb", intIn(source.apply(PERFORMANCE), "low_end_memory_class_mb", 0, 4096, 128))
                            .put("old_android_max_sdk", intIn(source.apply(PERFORMANCE), "old_android_max_sdk", 0, 40, 27))
                            .put("fps_sample_seconds", intIn(source.apply(PERFORMANCE), "fps_sample_seconds", 1, 15, 4))
                            .put("low_fps_threshold", intIn(source.apply(PERFORMANCE), "low_fps_threshold", 10, 60, 40))
                            .put("low_end_total_ram_mb", intIn(source.apply(PERFORMANCE), "low_end_total_ram_mb", 0, 65536, 3072)));
        } catch (JSONException impossible) {
            throw new IllegalStateException("Could not build the game configuration", impossible);
        }
    }

    /**
     * Languages (v1.4 Part 4), same rule as FreeCellCore.normalizeLanguages: the known languages that
     * are listed, in the fixed order, English always; Beta ones among them (never English).
     */
    private static JSONObject languages(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("enabled");
        JSONArray marked = value == null ? null : value.optJSONArray("beta");
        JSONArray enabled = new JSONArray(), beta = new JSONArray();
        for (String code : com.nexusplaylabss.freecell.i18n.Languages.CODES) {
            if (!"en".equals(code) && given != null && !contains(given, code)) continue;
            enabled.put(code);
            if (!"en".equals(code) && marked != null && contains(marked, code)) beta.put(code);
        }
        return new JSONObject().put("enabled", enabled).put("beta", beta);
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) if (value.equals(array.opt(i))) return true;
        return false;
    }

    /**
     * The hint's solver (v1.4), same bounds as FreeCellCore.normalizeConfig: time and position
     * budgets, background analysis after each move, and an optional cap on hints per game (null:
     * no cap).
     */
    private static JSONObject hint(JSONObject value) throws JSONException {
        Object cap = value == null ? null : value.opt("max_hints_per_game");
        return new JSONObject()
                .put("smart_hint", bool(value, "smart_hint", true))
                .put("time_budget_ms", intIn(value, "time_budget_ms", 50, 3000, 300))
                .put("node_limit", intIn(value, "node_limit", 1000, 2_000_000, 400_000))
                .put("background_analysis", bool(value, "background_analysis", true))
                .put("background_budget_ms", intIn(value, "background_budget_ms", 0, 1000, 150))
                .put("max_hints_per_game", cap instanceof Number ? (Object) intIn(value, "max_hints_per_game", 0, 1000, 0) : JSONObject.NULL);
    }

    /**
     * Daily Rivals (v1.3), same bounds as FreeCellCore.normalizeRivals. Rewards for a rank do not
     * exist yet: rewards.enabled is kept for a later version and nothing reads it.
     */
    private static JSONObject rivals(JSONObject value) throws JSONException {
        JSONObject multipliers = value == null ? null : value.optJSONObject("tier_multipliers");
        JSONObject rewards = value == null ? null : value.optJSONObject("rewards");
        double[] fallback = {0.7, 1, 1.4};
        JSONObject cleanMultipliers = new JSONObject();
        for (int i = 0; i < TIERS.size(); i++) {
            cleanMultipliers.put(TIERS.get(i), number(numberIn(multipliers, TIERS.get(i), 0.3, 3, fallback[i])));
        }
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("rivals_shown", intIn(value, "rivals_shown", 5, 60, 30))
                .put("median_seconds", intIn(value, "median_seconds", 60, 3600, 420))
                .put("sigma", number(numberIn(value, "sigma", 0.05, 1.5, 0.35)))
                .put("min_seconds", intIn(value, "min_seconds", 10, 600, 90))
                .put("tier_multipliers", cleanMultipliers)
                .put("rewards", new JSONObject().put("enabled", bool(rewards, "enabled", false)));
    }

    /** A decimal in [min, max] rounded to two places, or the fallback when missing or not a number. */
    private static double numberIn(JSONObject object, String key, double min, double max, double fallback) {
        Object raw = object == null ? null : object.opt(key);
        double value;
        if (raw instanceof Number) value = ((Number) raw).doubleValue();
        else if (raw instanceof String) {
            try {
                value = Double.parseDouble((String) raw);
            } catch (NumberFormatException notANumber) {
                return fallback;
            }
        } else return fallback;
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return Math.min(max, Math.max(min, Math.round(value * 100) / 100.0));
    }

    /** Whole numbers as integers, so 1 stays 1 in the JSON the page reads (as in the defaults). */
    private static Object number(double value) {
        return value == Math.rint(value) ? (Object) (int) value : (Object) value;
    }

    /**
     * Difficulty tiers (v1.3), same bounds as FreeCellCore.normalizeDifficulty: known tiers in a
     * fixed order (all three when none is valid), a default among them, one tier per weekday
     * (Sunday first, each falling back on its own), an unlock level per tier, the warm-up deals
     * (first_games), the Easy list's split (easy_split_index) and each tier's mix of pools.
     */
    private static JSONObject difficulty(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("enabled_tiers");
        List<String> enabled = new java.util.ArrayList<>();
        for (String tier : TIERS) {
            boolean listed = given == null;
            for (int i = 0; given != null && i < given.length(); i++) if (tier.equals(given.opt(i))) listed = true;
            if (listed) enabled.add(tier);
        }
        if (enabled.isEmpty()) enabled.addAll(TIERS);
        String wanted = value == null ? "" : value.optString("default_tier", "");
        String defaultTier = enabled.contains(wanted) ? wanted : enabled.contains("medium") ? "medium" : enabled.get(0);
        JSONArray daily = value == null ? null : value.optJSONArray("daily_tiers_by_weekday");
        JSONArray weekdays = new JSONArray();
        for (int day = 0; day < DEFAULT_DAILY_TIERS.length; day++) {
            Object tier = daily == null ? null : daily.opt(day);
            weekdays.put(tier instanceof String && TIERS.contains(tier) ? tier : DEFAULT_DAILY_TIERS[day]);
        }
        JSONObject unlock = value == null ? null : value.optJSONObject("unlock_levels");
        JSONObject unlockLevels = new JSONObject();
        for (String tier : TIERS) unlockLevels.put(tier, intIn(unlock, tier, 1, 100, 1));
        return new JSONObject()
                .put("enabled_tiers", new JSONArray(enabled))
                .put("default_tier", defaultTier)
                .put("daily_tiers_by_weekday", weekdays)
                .put("unlock_levels", unlockLevels)
                .put("first_games", firstGames(value == null ? null : value.optJSONObject("first_games")))
                .put("easy_split_index", intIn(value, "easy_split_index", 0, 1000, 64))
                .put("mixes", mixes(value == null ? null : value.optJSONObject("mixes")));
    }

    /** Warm-up deals: 1–10 different deal numbers 1–9,999,999, else the default five. */
    private static JSONObject firstGames(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("seeds");
        JSONArray seeds = new JSONArray();
        Set<Integer> seen = new LinkedHashSet<>();
        boolean valid = given != null && given.length() >= 1 && given.length() <= 10;
        for (int i = 0; valid && i < given.length(); i++) {
            Object item = given.opt(i);
            double number = item instanceof Number ? ((Number) item).doubleValue() : -1;
            valid = number == Math.rint(number) && number >= 1 && number <= 9_999_999 && seen.add((int) number);
        }
        if (valid) for (int seed : seen) seeds.put(seed);
        else for (int seed : DEFAULT_FIRST_GAMES) seeds.put(seed);
        String advance = value == null ? "" : value.optString("advance_on", "");
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("ignore_selected_mode", bool(value, "ignore_selected_mode", true))
                .put("advance_on", advance.equals("win") || advance.equals("start") ? advance : "win")
                .put("seeds", seeds);
    }

    /** Per tier, whole weights 0–100 for known pools that add up to 100, else the default mix. */
    private static JSONObject mixes(JSONObject value) throws JSONException {
        JSONObject result = new JSONObject();
        for (int tier = 0; tier < TIERS.size(); tier++) {
            JSONObject given = value == null ? null : value.optJSONObject(TIERS.get(tier));
            JSONObject mix = new JSONObject();
            boolean valid = given != null && given.length() > 0;
            int total = 0;
            for (java.util.Iterator<String> keys = valid ? given.keys() : null; valid && keys.hasNext(); ) {
                String pool = keys.next();
                Object weight = given.opt(pool);
                double number = weight instanceof Number ? ((Number) weight).doubleValue() : -1;
                valid = POOLS.contains(pool) && number == Math.rint(number) && number >= 0 && number <= 100;
                if (valid) { mix.put(pool, (int) number); total += (int) number; }
            }
            if (!valid || total != 100) {
                mix = new JSONObject();
                for (int pool = 0; pool < POOLS.size(); pool++) {
                    if (DEFAULT_MIXES[tier][pool] > 0) mix.put(POOLS.get(pool), DEFAULT_MIXES[tier][pool]);
                }
            }
            result.put(TIERS.get(tier), mix);
        }
        return result;
    }

    /**
     * Store items with a known category, a plain id and a bounded price (one per category and id).
     * Whether an id is an item the app can draw is checked by the page, which ignores the rest.
     */
    private static JSONObject store(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("items");
        JSONArray items = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; given != null && i < given.length(); i++) {
            JSONObject item = given.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String cat = item.optString("cat", "");
            if (!id.matches("[a-z0-9_]{1,40}") || !STORE_CATEGORIES.contains(cat) || !seen.add(cat + ":" + id)) continue;
            items.put(new JSONObject()
                    .put("id", id)
                    .put("cat", cat)
                    .put("price", intIn(item, "price", 0, 100000, 0))
                    .put("event", bool(item, "event", false)));
        }
        return new JSONObject()
                .put("categories", new JSONArray(STORE_CATEGORIES))
                .put("items", items);
    }

    /**
     * Seasonal events: plain ids, YYYY-MM-DD dates with start not after end, the play_days goal,
     * up to six reward item ids, at most ten events. Anything else is dropped.
     */
    private static JSONObject events(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("events");
        JSONArray events = new JSONArray();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; given != null && i < given.length() && events.length() < 10; i++) {
            JSONObject event = given.optJSONObject(i);
            if (event == null) continue;
            String id = event.optString("id", "");
            String start = event.optString("start", "");
            String end = event.optString("end", "");
            JSONObject goal = event.optJSONObject("goal");
            if (!id.matches("[a-z0-9_]{1,40}") || !start.matches("\\d{4}-\\d{2}-\\d{2}") || !end.matches("\\d{4}-\\d{2}-\\d{2}")
                    || start.compareTo(end) > 0 || goal == null || !"play_days".equals(goal.optString("type"))
                    || !ids.add(id)) continue;
            JSONArray rewards = new JSONArray();
            JSONArray items = event.optJSONArray("reward_items");
            for (int j = 0; items != null && j < items.length() && rewards.length() < 6; j++) {
                String item = items.optString(j, "");
                if (item.matches("[a-z0-9_]{1,40}")) rewards.put(item);
            }
            events.put(new JSONObject()
                    .put("id", id)
                    .put("enabled", bool(event, "enabled", false))
                    .put("start", start)
                    .put("end", end)
                    .put("goal", new JSONObject().put("type", "play_days")
                            .put("target", intIn(goal, "target", 1, 366, 10)))
                    .put("reward_items", rewards));
        }
        return new JSONObject().put("events", events);
    }

    /**
     * Achievements: a known counter, a target (seconds for fast_win) and a reward; plain unique
     * ids, at most 60. An empty or unusable list falls back to the bundled one in the page.
     */
    private static JSONObject achievements(JSONObject value) throws JSONException {
        JSONArray given = value == null ? null : value.optJSONArray("items");
        JSONArray items = new JSONArray();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; given != null && i < given.length() && items.length() < 60; i++) {
            JSONObject item = given.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String counter = item.optString("counter", "");
            if (!id.matches("[a-z0-9_]{1,40}") || !ACHIEVEMENT_COUNTERS.contains(counter) || !ids.add(id)) continue;
            items.put(new JSONObject()
                    .put("id", id)
                    .put("counter", counter)
                    .put("target", intIn(item, "target", 1, 100000, 1))
                    .put("reward", intIn(item, "reward", 0, 10000, 0)));
        }
        return new JSONObject().put("enabled", bool(value, "enabled", true)).put("items", items);
    }

    /** Stars per win (1 to 3) and the coins for reaching 2 or 3 stars on a deal. */
    private static JSONObject stars(JSONObject value) throws JSONException {
        JSONObject bonus = object(value, "star_bonus_coins");
        int twoMax = intIn(value, "two_star_max_undos", 0, 1000, 2);
        return new JSONObject()
                .put("two_star_max_undos", twoMax)
                .put("three_star_max_undos", intIn(value, "three_star_max_undos", 0, twoMax, 0))
                .put("three_star_seconds", intIn(value, "three_star_seconds", 30, 7200, 480))
                .put("three_star_moves", intIn(value, "three_star_moves", 52, 10000, 140))
                .put("star_bonus_coins", new JSONObject()
                        .put("two", intIn(bonus, "two", 0, 10000, 5))
                        .put("three", intIn(bonus, "three", 0, 10000, 10)));
    }

    /** Daily Challenge reminders: when to offer them and the default hours. */
    private static JSONObject notifications(JSONObject value) throws JSONException {
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("ask_after_daily_wins", intIn(value, "ask_after_daily_wins", 1, 100, 1))
                .put("default_hour", intIn(value, "default_hour", 0, 23, 19))
                .put("streak_risk_hour", intIn(value, "streak_risk_hour", 0, 23, 20))
                .put("min_streak_for_risk", intIn(value, "min_streak_for_risk", 1, 365, 3));
    }

    /** The voluntary "double your coins" video: switch, placements and limits. */
    private static JSONObject rewarded(JSONObject value) throws JSONException {
        JSONObject placements = object(value, "placements");
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("placements", new JSONObject()
                        .put("victory_double", bool(placements, "victory_double", true))
                        .put("daily_bonus_double", bool(placements, "daily_bonus_double", true)))
                .put("max_per_day", intIn(value, "max_per_day", 0, 100, 6))
                .put("min_games_played", intIn(value, "min_games_played", 0, 10000, 2));
    }

    /**
     * Whether any rewarded placement can show. With Remove Ads bought, this alone decides if the
     * ads SDK (and the consent form) still run.
     */
    public static boolean rewardedOffered(JSONObject sanitized) {
        JSONObject rewarded = sanitized.optJSONObject(REWARDED);
        if (rewarded == null || !rewarded.optBoolean("enabled") || rewarded.optInt("max_per_day") <= 0) return false;
        JSONObject placements = object(rewarded, "placements");
        return placements.optBoolean("victory_double") || placements.optBoolean("daily_bonus_double");
    }

    /** When the Remove Ads screen may show by itself between games. */
    private static JSONObject removeAds(JSONObject value) throws JSONException {
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("interval_games", intIn(value, "interval_games", 1, 1000, 6))
                .put("min_games_played", intIn(value, "min_games_played", 0, 10000, 4))
                .put("min_session", intIn(value, "min_session", 2, 10000, 2))
                .put("cooldown_hours", intIn(value, "cooldown_hours", 0, 24 * 365, 12));
    }

    /** When the rating card may show after a win. Never on the first session. */
    private static JSONObject rating(JSONObject value) throws JSONException {
        return new JSONObject()
                .put("enabled", bool(value, "enabled", true))
                .put("interval_games", intIn(value, "interval_games", 1, 1000, 10))
                .put("min_wins", intIn(value, "min_wins", 0, 10000, 3))
                .put("min_session", intIn(value, "min_session", 2, 10000, 2))
                .put("cooldown_days", intIn(value, "cooldown_days", 1, 3650, 45))
                .put("max_prompts", intIn(value, "max_prompts", 0, 100, 3));
    }

    /** 1 to 14 rewards (0 to 10000 coins each); any other list uses the default cycle. */
    private static JSONObject dailyBonus(JSONObject bonus) throws JSONException {
        JSONArray rewards = bonus == null ? null : bonus.optJSONArray("cycle_rewards");
        boolean valid = rewards != null && rewards.length() >= 1 && rewards.length() <= 14;
        for (int i = 0; valid && i < rewards.length(); i++) valid = rewards.opt(i) instanceof Number;
        JSONArray cycle = new JSONArray();
        if (valid) {
            for (int i = 0; i < rewards.length(); i++) {
                cycle.put((int) Math.max(0, Math.min(10000, Math.round(rewards.getDouble(i)))));
            }
        } else {
            for (int reward : DEFAULT_BONUS_CYCLE) cycle.put(reward);
        }
        return new JSONObject()
                .put("enabled", bool(bonus, "enabled", true))
                .put("cycle_rewards", cycle)
                .put("reset_on_miss", bool(bonus, "reset_on_miss", true));
    }

    /**
     * Mission definitions of known types with unique ids; anything else is dropped. When fewer than
     * three types remain, the page falls back to its bundled list (the same as the assets file).
     */
    private static JSONObject missions(JSONObject missions) throws JSONException {
        JSONArray given = missions == null ? null : missions.optJSONArray("definitions");
        JSONArray definitions = new JSONArray();
        Set<String> ids = new LinkedHashSet<>();
        for (int j = 0; given != null && j < given.length(); j++) {
            JSONObject value = given.optJSONObject(j);
            if (value == null) continue;
            String id = value.optString("id", "");
            String type = value.optString("type", "");
            if (!id.matches("[a-z0-9_]{1,40}") || !MISSION_TYPES.contains(type) || !ids.add(id)) continue;
            definitions.put(new JSONObject()
                    .put("id", id)
                    .put("type", type)
                    .put("target", intIn(value, "target", 1, 1000, 1))
                    .put("reward", intIn(value, "reward", 0, 10000, 0))
                    .put("seconds", intIn(value, "seconds", 30, 7200, 600))
                    .put("moves", intIn(value, "moves", 20, 1000, 120)));
        }
        return new JSONObject()
                .put("enabled", bool(missions, "enabled", true))
                .put("all_complete_bonus", intIn(missions, "all_complete_bonus", 0, 10000, 40))
                .put("refresh_hours", intIn(missions, "refresh_hours", 0, 168, 12))
                .put("definitions", definitions);
    }

    /** Known theme ids only; Classic is always free so a player always has a theme. */
    private static Set<String> freeThemes(JSONObject themes) {
        Set<String> free = new LinkedHashSet<>();
        free.add("classic");
        JSONArray list = themes == null ? null : themes.optJSONArray("free");
        if (list == null) {
            free.addAll(DEFAULT_FREE);
            return free;
        }
        for (int i = 0; i < list.length(); i++) {
            String id = list.optString(i, "");
            if (THEME_IDS.contains(id)) free.add(id);
        }
        return free;
    }

    private static JSONObject object(JSONObject parent, String key) {
        JSONObject child = parent == null ? null : parent.optJSONObject(key);
        return child == null ? new JSONObject() : child;
    }
}
