package com.nexusplaylabss.freecell.analytics;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class AnalyticsTracker {
    private static final String TAG = "FreeCellAnalytics";
    private static final int MAX_STRING_LENGTH = 100;

    private static final Set<String> EVENTS = unmodifiableSet(
            "app_ready",
            "tutorial_open",
            "tutorial_start",
            "tutorial_step",
            "tutorial_skip",
            "tutorial_complete",
            "game_start",
            "game_win",
            "game_abandon",
            "hint_used",
            "undo_used",
            "menu_open",
            "setting_changed",
            "language_changed",
            "theme_selected",
            "daily_challenge_open",
            "daily_challenge_start",
            "daily_challenge_complete",
            "level_up",
            "coins_earned",
            "theme_unlocked",
            "no_moves_shown",
            "daily_bonus_claimed",
            "mission_claimed",
            "missions_all_claimed",
            "remove_ads_shown",
            "remove_ads_purchase_started",
            "remove_ads_purchase_completed",
            "remove_ads_purchase_failed",
            "rating_prompt_shown",
            "rating_choice",
            "event_completed",
            "rewarded_offered",
            "rewarded_started",
            "rewarded_completed",
            "rewarded_not_available",
            "share_challenge",
            "share_daily",
            "reminder_prompt_shown",
            "reminder_choice",
            "reminder_opened",
            "achievement_unlocked",
            "achievement_claimed",
            "rivals_opened",
            "effects_reduced",
            "autocomplete_used",
            "stats_reset",
            "intro_complete",
            "first_daily_win",
            "wins_3",
            "wins_10",
            "day2_return",
            "ad_request",
            "ad_loaded",
            "ad_shown",
            "ad_dismissed",
            "ad_failed"
    );

    private static final Set<String> PARAMETERS = unmodifiableSet(
            "source",
            "step",
            "total_steps",
            "game_mode",
            "level",
            "replay",
            "moves",
            "duration_seconds",
            "score",
            "menu",
            "setting",
            "enabled",
            "value",
            "language",
            "theme",
            "theme_type",
            "previous_level",
            "new_level",
            "first_run",
            "load_ms",
            "ad_format",
            "placement",
            "session_count",
            "games_played",
            "error_code",
            "error_domain",
            "difficulty_tier",
            "deal_pool",
            "rank"
    );

    /**
     * Google Ads app campaign goals (2026-11-06): the page sends each once per player. Test builds
     * (debug and qa, BuildConfig.TEST_TOOLS) never send them, so testing cannot count as conversions.
     */
    static final Set<String> GOAL_EVENTS = unmodifiableSet(
            "intro_complete",
            "first_daily_win",
            "wins_3",
            "wins_10",
            "day2_return"
    );

    /** User properties for audiences: game state only, never personal data. */
    static final Set<String> USER_PROPERTIES = unmodifiableSet(
            "player_level",
            "intro_complete",
            "ads_removed",
            "app_language"
    );
    private static final int MAX_USER_PROPERTY_LENGTH = 36;

    private final FirebaseAnalytics analytics;
    private final boolean testBuild;

    public AnalyticsTracker(Context context, boolean testBuild) {
        this.testBuild = testBuild;
        FirebaseAnalytics instance = null;
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) {
                instance = FirebaseAnalytics.getInstance(context);
            } else {
                Log.i(TAG, "Firebase is not configured; analytics events are disabled.");
            }
        } catch (IllegalStateException error) {
            Log.w(TAG, "Firebase Analytics is unavailable.", error);
        }
        analytics = instance;
    }

    /** Whether an event may be sent: it is on the list, and a campaign goal only outside test builds. */
    static boolean sends(String eventName, boolean testBuild) {
        return EVENTS.contains(eventName) && !(testBuild && GOAL_EVENTS.contains(eventName));
    }

    /** The value to store for a user property, or null when the property is not on the list. */
    static String userPropertyValue(String name, String value) {
        if (!USER_PROPERTIES.contains(name) || value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        return text.substring(0, Math.min(text.length(), MAX_USER_PROPERTY_LENGTH));
    }

    public void setUserProperty(String name, String value) {
        String clean = userPropertyValue(name, value);
        if (analytics == null || clean == null) return;
        analytics.setUserProperty(name, clean);
    }

    public void logEvent(String eventName, String parametersJson) {
        if (analytics == null || !sends(eventName, testBuild)) return;
        Bundle parameters = new Bundle();
        if (parametersJson != null && !parametersJson.isEmpty()) {
            try {
                JSONObject object = new JSONObject(parametersJson);
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!PARAMETERS.contains(key) || object.isNull(key)) continue;
                    put(parameters, key, object.get(key));
                }
            } catch (JSONException error) {
                Log.w(TAG, "Ignored malformed analytics parameters for " + eventName);
            }
        }
        analytics.logEvent(eventName, parameters);
    }

    public void logEvent(String eventName, Bundle parameters) {
        if (analytics == null || !sends(eventName, testBuild)) return;
        Bundle clean = new Bundle();
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                if (!PARAMETERS.contains(key)) continue;
                Object value = parameters.get(key);
                if (value != null) put(clean, key, value);
            }
        }
        analytics.logEvent(eventName, clean);
    }

    private static void put(Bundle bundle, String key, Object value) {
        if (value instanceof Boolean) {
            bundle.putLong(key, (Boolean) value ? 1L : 0L);
        } else if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            bundle.putLong(key, ((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            bundle.putDouble(key, ((Number) value).doubleValue());
        } else {
            String text = String.valueOf(value);
            bundle.putString(key, text.substring(0, Math.min(text.length(), MAX_STRING_LENGTH)));
        }
    }

    private static Set<String> unmodifiableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
