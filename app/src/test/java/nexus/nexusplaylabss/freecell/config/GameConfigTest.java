package com.nexusplaylabss.freecell.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class GameConfigTest {
    private static String bundled(String key) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/assets/" + GameConfig.bundledAsset(key))),
                StandardCharsets.UTF_8);
    }

    private static JSONObject sanitize(Map<String, String> remote) throws Exception {
        Map<String, String> defaults = new HashMap<>();
        for (String key : GameConfig.KEYS) defaults.put(key, bundled(key));
        return GameConfig.sanitize(key -> RemoteJsonConfig.parseObject(remote.get(key), defaults.get(key)));
    }

    @Test public void bundledDefaultsComeThroughUnchanged() throws Exception {
        JSONObject config = sanitize(new HashMap<>());
        for (String key : GameConfig.KEYS) {
            JSONObject expected = new JSONObject(bundled(key));
            JSONObject actual = config.getJSONObject(key);
            if (key.equals(GameConfig.EVENTS)) {
                assertEquals(key, expected.toString().length(), actual.toString().length());
                continue;
            }
            if (key.equals(GameConfig.STORE)) {
                JSONArray want = expected.getJSONArray("items"), got = actual.getJSONArray("items");
                assertEquals(want.length(), got.length());
                for (int i = 0; i < want.length(); i++) {
                    for (String field : new String[] {"id", "cat", "price"}) {
                        assertEquals(field, String.valueOf(want.getJSONObject(i).get(field)),
                                String.valueOf(got.getJSONObject(i).get(field)));
                    }
                    assertEquals("event", want.getJSONObject(i).optBoolean("event"), got.getJSONObject(i).getBoolean("event"));
                }
                continue;
            }
            if (key.equals(GameConfig.DAILY_BONUS)) {
                assertEquals(key, expected.toString().length(), actual.toString().length());
                continue;
            }
            if (key.equals(GameConfig.MISSIONS)) {
                JSONArray want = expected.getJSONArray("definitions"), got = actual.getJSONArray("definitions");
                assertEquals(want.length(), got.length());
                for (int i = 0; i < want.length(); i++) {
                    for (String field : new String[] {"id", "type", "target", "reward"}) {
                        assertEquals(field, String.valueOf(want.getJSONObject(i).get(field)),
                                String.valueOf(got.getJSONObject(i).get(field)));
                    }
                }
                assertEquals(expected.getInt("refresh_hours"), actual.getInt("refresh_hours"));
                continue;
            }
            if (key.equals(GameConfig.THEMES)) {
                assertEquals(expected.getJSONArray("free").toString(), actual.getJSONArray("free").toString());
                JSONObject prices = expected.getJSONObject("prices");
                for (String id : new String[] {"neon", "moonstone", "emberwood"}) {
                    assertEquals(prices.getInt(id), actual.getJSONObject("prices").getInt(id));
                }
            } else {
                assertSame(key, expected, actual);
            }
        }
    }

    /** Same keys and values, ignoring key order (Android's org.json has no similar()). */
    private static void assertSame(String path, Object expected, Object actual) throws Exception {
        if (expected instanceof JSONObject) {
            JSONObject e = (JSONObject) expected, a = (JSONObject) actual;
            assertEquals(path, e.length(), a.length());
            for (java.util.Iterator<String> keys = e.keys(); keys.hasNext(); ) {
                String k = keys.next();
                assertSame(path + "." + k, e.get(k), a.get(k));
            }
        } else {
            assertEquals(path, String.valueOf(expected), String.valueOf(actual));
        }
    }

    @Test public void invalidJsonFallsBackToTheBundledDefault() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.ECONOMY, "not json");
        JSONObject economy = sanitize(remote).getJSONObject(GameConfig.ECONOMY);
        assertEquals(20, economy.getJSONObject("win_rewards").getInt("medium"));
    }

    @Test public void valuesAreClampedAndWrongTypesFallBack() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.ECONOMY, "{\"win_rewards\":{\"medium\":-3,\"daily\":\"lots\",\"random\":99},"
                + "\"bonus_fast\":{\"seconds\":5},\"pay_once_per_deal_seed\":\"no\"}");
        remote.put(GameConfig.DAILY_CHALLENGE, "{\"forgiveness_days_per_month\":99,\"history_days\":2}");
        JSONObject config = sanitize(remote);
        JSONObject economy = config.getJSONObject(GameConfig.ECONOMY);
        assertEquals(0, economy.getJSONObject("win_rewards").getInt("medium"));
        assertEquals(10, economy.getJSONObject("win_rewards").getInt("easy"));
        assertFalse(economy.getJSONObject("win_rewards").has("random"));
        assertEquals(50, economy.getJSONObject("win_rewards").getInt("daily"));
        assertEquals(30, economy.getJSONObject("bonus_fast").getInt("seconds"));
        assertTrue(economy.getBoolean("pay_once_per_deal_seed"));
        assertFalse(config.getJSONObject(GameConfig.DAILY_CHALLENGE).has("forgiveness_days_per_month"));
        assertEquals(31, config.getJSONObject(GameConfig.DAILY_CHALLENGE).getInt("history_days"));
    }

    @Test public void bonusCycleAndMissionsAreValidated() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.DAILY_BONUS, "{\"cycle_rewards\":[5,\"x\"],\"reset_on_miss\":false}");
        remote.put(GameConfig.MISSIONS, "{\"all_complete_bonus\":99999,\"refresh_hours\":-3,\"definitions\":"
                + "[{\"id\":\"w\",\"type\":\"win_games\",\"target\":4,\"reward\":7},"
                + "{\"id\":\"w\",\"type\":\"play_games\"},{\"id\":\"x\",\"type\":\"fly\"},"
                + "{\"id\":\"Bad Id\",\"type\":\"win_games\"}]}");
        JSONObject config = sanitize(remote);
        JSONObject bonus = config.getJSONObject(GameConfig.DAILY_BONUS);
        assertEquals(7, bonus.getJSONArray("cycle_rewards").length());
        assertFalse(bonus.getBoolean("reset_on_miss"));
        JSONObject missions = config.getJSONObject(GameConfig.MISSIONS);
        assertEquals(10000, missions.getInt("all_complete_bonus"));
        assertEquals(0, missions.getInt("refresh_hours"));
        JSONArray definitions = missions.getJSONArray("definitions");
        assertEquals(1, definitions.length());
        assertEquals(4, definitions.getJSONObject(0).getInt("target"));
        assertEquals(600, definitions.getJSONObject(0).getInt("seconds"));
    }

    @Test public void promoSettingsNeverAllowTheFirstSession() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.RATING, "{\"min_session\":1,\"cooldown_days\":0,\"max_prompts\":500}");
        remote.put(GameConfig.REMOVE_ADS, "{\"enabled\":false,\"min_session\":0,\"interval_games\":0}");
        JSONObject config = sanitize(remote);
        JSONObject rating = config.getJSONObject(GameConfig.RATING);
        assertEquals(2, rating.getInt("min_session"));
        assertEquals(1, rating.getInt("cooldown_days"));
        assertEquals(100, rating.getInt("max_prompts"));
        JSONObject removeAds = config.getJSONObject(GameConfig.REMOVE_ADS);
        assertFalse(removeAds.getBoolean("enabled"));
        assertEquals(2, removeAds.getInt("min_session"));
        assertEquals(1, removeAds.getInt("interval_games"));
    }

    @Test public void storeItemsAreFilteredAndBounded() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.STORE, "{\"items\":[{\"id\":\"neon\",\"cat\":\"card_faces\",\"price\":-5},"
                + "{\"id\":\"neon\",\"cat\":\"card_faces\",\"price\":9},{\"id\":\"x\",\"cat\":\"hats\"},"
                + "{\"id\":\"winter_back\",\"cat\":\"card_backs\",\"price\":0,\"event\":true}],"
                + "\"shield\":{\"price\":999999,\"max_per_month\":-1}}");
        JSONObject store = sanitize(remote).getJSONObject(GameConfig.STORE);
        JSONArray items = store.getJSONArray("items");
        assertEquals(2, items.length());
        assertEquals(0, items.getJSONObject(0).getInt("price"));
        assertTrue(items.getJSONObject(1).getBoolean("event"));
        assertFalse(store.has("shield"));
    }

    @Test public void eventsKeepOnlyValidEntries() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.EVENTS, "{\"events\":[{\"id\":\"a\",\"start\":\"2026-12-10\",\"end\":\"2026-12-01\",\"goal\":{\"type\":\"play_days\"}},"
                + "{\"id\":\"b\",\"enabled\":true,\"start\":\"2026-12-01\",\"end\":\"2026-12-31\",\"goal\":{\"type\":\"play_days\",\"target\":0},\"reward_items\":[\"winter_back\",\"Bad\"]}]}");
        JSONArray events = sanitize(remote).getJSONObject(GameConfig.EVENTS).getJSONArray("events");
        assertEquals(1, events.length());
        JSONObject event = events.getJSONObject(0);
        assertTrue(event.getBoolean("enabled"));
        assertEquals(1, event.getJSONObject("goal").getInt("target"));
        assertEquals(1, event.getJSONArray("reward_items").length());
    }

    @Test public void rewardedIsBoundedAndOfferedOnlyWithAPlacement() throws Exception {
        assertTrue(GameConfig.rewardedOffered(sanitize(new HashMap<>())));
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.REWARDED, "{\"enabled\":true,\"placements\":{\"victory_double\":false,\"daily_bonus_double\":\"yes\"},"
                + "\"max_per_day\":500,\"min_games_played\":-3}");
        JSONObject config = sanitize(remote);
        JSONObject rewarded = config.getJSONObject(GameConfig.REWARDED);
        assertEquals(100, rewarded.getInt("max_per_day"));
        assertEquals(0, rewarded.getInt("min_games_played"));
        assertFalse(rewarded.getJSONObject("placements").getBoolean("victory_double"));
        assertTrue(rewarded.getJSONObject("placements").getBoolean("daily_bonus_double"));
        assertTrue(GameConfig.rewardedOffered(config));
        remote.put(GameConfig.REWARDED, "{\"placements\":{\"victory_double\":false,\"daily_bonus_double\":false}}");
        assertFalse(GameConfig.rewardedOffered(sanitize(remote)));
        remote.put(GameConfig.REWARDED, "{\"enabled\":false}");
        assertFalse(GameConfig.rewardedOffered(sanitize(remote)));
    }

    @Test public void notificationHoursAreBounded() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.NOTIFICATIONS, "{\"enabled\":false,\"ask_after_daily_wins\":0,\"default_hour\":25,\"streak_risk_hour\":-1,\"min_streak_for_risk\":\"x\"}");
        JSONObject value = sanitize(remote).getJSONObject(GameConfig.NOTIFICATIONS);
        assertFalse(value.getBoolean("enabled"));
        assertEquals(1, value.getInt("ask_after_daily_wins"));
        assertEquals(23, value.getInt("default_hour"));
        assertEquals(0, value.getInt("streak_risk_hour"));
        assertEquals(3, value.getInt("min_streak_for_risk"));
    }

    @Test public void starRulesAreBoundedAndThreeStarsNeverAllowMoreUndosThanTwo() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.STARS, "{\"two_star_max_undos\":1,\"three_star_max_undos\":5,\"three_star_seconds\":5,\"three_star_moves\":10,\"star_bonus_coins\":{\"two\":-1}}");
        JSONObject stars = sanitize(remote).getJSONObject(GameConfig.STARS);
        assertEquals(1, stars.getInt("three_star_max_undos"));
        assertEquals(30, stars.getInt("three_star_seconds"));
        assertEquals(52, stars.getInt("three_star_moves"));
        assertEquals(0, stars.getJSONObject("star_bonus_coins").getInt("two"));
        assertEquals(10, stars.getJSONObject("star_bonus_coins").getInt("three"));
    }

    @Test public void achievementsKeepOnlyKnownCountersAndUniqueIds() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.ACHIEVEMENTS, "{\"items\":[{\"id\":\"wins_10\",\"counter\":\"wins\",\"target\":10,\"reward\":30},"
                + "{\"id\":\"wins_10\",\"counter\":\"wins\",\"target\":20},{\"id\":\"x\",\"counter\":\"flying\",\"target\":1},"
                + "{\"id\":\"fast\",\"counter\":\"fast_win\",\"target\":0,\"reward\":99999}]}");
        JSONObject value = sanitize(remote).getJSONObject(GameConfig.ACHIEVEMENTS);
        JSONArray items = value.getJSONArray("items");
        assertTrue(value.getBoolean("enabled"));
        assertEquals(2, items.length());
        assertEquals(1, items.getJSONObject(1).getInt("target"));
        assertEquals(10000, items.getJSONObject(1).getInt("reward"));
    }

    @Test public void unknownThemesAreDroppedAndClassicStaysFree() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.THEMES, "{\"prices\":{\"neon\":120},\"free\":[\"neon\",\"rainbow\"]}");
        JSONObject themes = sanitize(remote).getJSONObject(GameConfig.THEMES);
        JSONArray free = themes.getJSONArray("free");
        assertEquals("[\"classic\",\"neon\"]", free.toString());
        assertEquals(120, themes.getJSONObject("prices").getInt("neon"));
        assertEquals(500, themes.getJSONObject("prices").getInt("moonstone"));
        assertFalse(themes.getJSONObject("prices").has("rainbow"));
    }

    @Test public void difficultyKeepsKnownTiersAndFallsBackPerWeekday() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.DIFFICULTY, "{\"enabled_tiers\":[\"hard\",\"bogus\",\"easy\"],\"default_tier\":\"medium\","
                + "\"daily_tiers_by_weekday\":[\"easy\",\"x\"],\"unlock_levels\":{\"hard\":500}}");
        JSONObject difficulty = sanitize(remote).getJSONObject(GameConfig.DIFFICULTY);
        assertEquals("[\"easy\",\"hard\"]", difficulty.getJSONArray("enabled_tiers").toString());
        assertEquals("easy", difficulty.getString("default_tier"));
        assertEquals("[\"easy\",\"medium\",\"medium\",\"medium\",\"medium\",\"medium\",\"hard\"]",
                difficulty.getJSONArray("daily_tiers_by_weekday").toString());
        assertEquals(100, difficulty.getJSONObject("unlock_levels").getInt("hard"));
        assertEquals(1, difficulty.getJSONObject("unlock_levels").getInt("easy"));
        remote.put(GameConfig.DIFFICULTY, "{\"enabled_tiers\":[]}");
        assertEquals(3, sanitize(remote).getJSONObject(GameConfig.DIFFICULTY).getJSONArray("enabled_tiers").length());
    }

    @Test public void difficultyMixesAddUpTo100AndWarmUpDealsAreChecked() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.DIFFICULTY, "{\"mixes\":{\"easy\":{\"very_easy\":50,\"easy\":50},\"medium\":{\"medium\":80,\"easy\":30},"
                + "\"hard\":{\"hard\":100,\"bogus\":0}},\"easy_split_index\":5000,"
                + "\"first_games\":{\"enabled\":false,\"advance_on\":\"never\",\"seeds\":[3,3]}}");
        JSONObject difficulty = sanitize(remote).getJSONObject(GameConfig.DIFFICULTY);
        JSONObject mixes = difficulty.getJSONObject("mixes");
        assertEquals(50, mixes.getJSONObject("easy").getInt("very_easy"));
        // 80 + 30 is not 100, and "bogus" is not a pool: both fall back to the default mix.
        assertEquals(70, mixes.getJSONObject("medium").getInt("medium"));
        assertEquals(30, mixes.getJSONObject("medium").getInt("easy"));
        assertEquals(60, mixes.getJSONObject("hard").getInt("medium"));
        assertEquals(40, mixes.getJSONObject("hard").getInt("hard"));
        assertEquals(1000, difficulty.getInt("easy_split_index"));
        JSONObject first = difficulty.getJSONObject("first_games");
        assertFalse(first.getBoolean("enabled"));
        assertEquals("win", first.getString("advance_on"));
        assertEquals("[25904,5152,11987,13464,9998]", first.getJSONArray("seeds").toString());
        remote.put(GameConfig.DIFFICULTY, "{\"first_games\":{\"advance_on\":\"start\",\"seeds\":[7,8]}}");
        first = sanitize(remote).getJSONObject(GameConfig.DIFFICULTY).getJSONObject("first_games");
        assertEquals("start", first.getString("advance_on"));
        assertEquals("[7,8]", first.getJSONArray("seeds").toString());
        assertTrue(first.getBoolean("ignore_selected_mode"));
    }

    @Test public void hintBudgetsAreBoundedAndTheCapIsOptional() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.HINT, "{\"smart_hint\":\"yes\",\"time_budget_ms\":9000,\"node_limit\":5,\"background_budget_ms\":-4,\"max_hints_per_game\":3}");
        remote.put(GameConfig.TIPS, "{\"enabled\":false,\"free_cells_tip_min_occupied\":9}");
        JSONObject config = sanitize(remote);
        JSONObject hint = config.getJSONObject(GameConfig.HINT);
        assertTrue(hint.getBoolean("smart_hint"));
        assertEquals(3000, hint.getInt("time_budget_ms"));
        assertEquals(1000, hint.getInt("node_limit"));
        assertEquals(0, hint.getInt("background_budget_ms"));
        assertEquals(3, hint.getInt("max_hints_per_game"));
        assertFalse(config.getJSONObject(GameConfig.TIPS).getBoolean("enabled"));
        assertEquals(4, config.getJSONObject(GameConfig.TIPS).getInt("free_cells_tip_min_occupied"));
        remote.put(GameConfig.HINT, "{\"max_hints_per_game\":\"none\"}");
        assertTrue(sanitize(remote).getJSONObject(GameConfig.HINT).isNull("max_hints_per_game"));
    }

    @Test public void feelVolumeIsBetweenZeroAndOne() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.FEEL, "{\"sounds\":false,\"haptics\":\"no\",\"sound_volume\":4}");
        JSONObject feel = sanitize(remote).getJSONObject(GameConfig.FEEL);
        assertFalse(feel.getBoolean("sounds"));
        assertTrue(feel.getBoolean("haptics"));
        assertEquals(1, feel.getDouble("sound_volume"), 0);
        assertTrue(feel.getBoolean("suit_complete_effect"));
    }

    @Test public void performanceIsBounded() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.PERFORMANCE, "{\"auto_reduce_effects\":\"yes\",\"low_end_memory_class_mb\":99999,\"fps_sample_seconds\":0,\"low_fps_threshold\":90,\"low_end_total_ram_mb\":-1}");
        JSONObject performance = sanitize(remote).getJSONObject(GameConfig.PERFORMANCE);
        assertTrue(performance.getBoolean("auto_reduce_effects"));
        assertEquals(4096, performance.getInt("low_end_memory_class_mb"));
        assertEquals(27, performance.getInt("old_android_max_sdk"));
        assertEquals(1, performance.getInt("fps_sample_seconds"));
        assertEquals(60, performance.getInt("low_fps_threshold"));
        assertEquals(0, performance.getInt("low_end_total_ram_mb"));
    }

    @Test public void languagesKeepTheFixedOrderAndEnglish() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.LANGUAGES, "{\"enabled\":[\"ja\",\"xx\",\"pt-BR\"],\"beta\":[\"ja\",\"en\",\"ko\"]}");
        JSONObject languages = sanitize(remote).getJSONObject(GameConfig.LANGUAGES);
        assertEquals("[\"en\",\"pt-BR\",\"ja\"]", languages.getJSONArray("enabled").toString());
        assertEquals("[\"ja\"]", languages.getJSONArray("beta").toString());
    }

    @Test public void rivalsAreBoundedAndRewardsStayASwitch() throws Exception {
        Map<String, String> remote = new HashMap<>();
        remote.put(GameConfig.RIVALS, "{\"rivals_shown\":500,\"sigma\":\"x\",\"median_seconds\":5,"
                + "\"tier_multipliers\":{\"hard\":9,\"easy\":\"0.456\"},\"rewards\":{\"enabled\":true}}");
        JSONObject rivals = sanitize(remote).getJSONObject(GameConfig.RIVALS);
        assertEquals(60, rivals.getInt("rivals_shown"));
        assertEquals(60, rivals.getInt("median_seconds"));
        assertEquals("0.35", String.valueOf(rivals.get("sigma")));
        assertEquals("3", String.valueOf(rivals.getJSONObject("tier_multipliers").get("hard")));
        assertEquals("0.46", String.valueOf(rivals.getJSONObject("tier_multipliers").get("easy")));
        assertEquals("1", String.valueOf(rivals.getJSONObject("tier_multipliers").get("medium")));
        assertTrue(rivals.getJSONObject("rewards").getBoolean("enabled"));
    }
}
