package com.nexusplaylabss.freecell.ads;

import org.json.JSONException;
import org.json.JSONObject;

public final class AdConfig {
    public static final String REMOTE_CONFIG_KEY = "ads_config";

    public final boolean enabled;
    public final Interstitials interstitials;
    public final Banners banners;
    public final AppOpen appOpen;
    public final String bannerAdUnitId;
    public final String interstitialAdUnitId;
    public final String appOpenAdUnitId;
    /** The "double your coins" video (bundled since 2026-11-10; empty means no rewarded request). */
    public final String rewardedAdUnitId;
    public final boolean enableAmazonSdk;
    public final boolean enablePubmaticSdk;

    private AdConfig(
            boolean enabled,
            Interstitials interstitials,
            Banners banners,
            AppOpen appOpen,
            String bannerAdUnitId,
            String interstitialAdUnitId,
            String appOpenAdUnitId,
            String rewardedAdUnitId,
            boolean enableAmazonSdk,
            boolean enablePubmaticSdk) {
        this.enabled = enabled;
        this.interstitials = interstitials;
        this.banners = banners;
        this.appOpen = appOpen;
        this.bannerAdUnitId = bannerAdUnitId;
        this.interstitialAdUnitId = interstitialAdUnitId;
        this.appOpenAdUnitId = appOpenAdUnitId;
        this.rewardedAdUnitId = rewardedAdUnitId;
        this.enableAmazonSdk = enableAmazonSdk;
        this.enablePubmaticSdk = enablePubmaticSdk;
    }

    /** A missing or invalid remote value falls back to the bundled default. */
    public static AdConfig parseOrFallback(String value, String bundledDefault) {
        try {
            return parse(value);
        } catch (JSONException | NullPointerException invalidRemoteValue) {
            try {
                return parse(bundledDefault);
            } catch (JSONException impossible) {
                throw new IllegalStateException("Bundled ad configuration is invalid", impossible);
            }
        }
    }

    public static AdConfig parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject availability = root.optJSONObject("availability");
        if (availability == null) availability = new JSONObject();

        JSONObject interstitialJson = availability.optJSONObject("interstitials");
        if (interstitialJson == null) interstitialJson = new JSONObject();
        JSONObject interstitialConditions = conditions(interstitialJson);
        Interstitials interstitials = new Interstitials(
                bounded(interstitialJson.optInt("interval", 1), 1, 100),
                bounded(interstitialConditions.optInt("session", 1), 1, 10_000),
                bounded(interstitialConditions.optInt("games_played", 3), 0, 1_000_000),
                interstitialJson.optBoolean("skip_after_loss", true));

        JSONObject bannerJson = availability.optJSONObject("banners");
        if (bannerJson == null) bannerJson = new JSONObject();
        JSONObject bannerConditions = conditions(bannerJson);
        Banners banners = new Banners(
                bounded(bannerConditions.optInt("session", 1), 1, 10_000),
                bounded(bannerConditions.optInt("games_played", 0), 0, 1_000_000));

        JSONObject appOpenJson = availability.optJSONObject("app_open");
        if (appOpenJson == null) appOpenJson = new JSONObject();
        AppOpen appOpen = new AppOpen(
                bounded(appOpenJson.optInt("cold_start_session", 1), 1, 10_000),
                bounded(appOpenJson.optInt("background_min_session", 1), 1, 10_000),
                bounded(appOpenJson.optInt("splash_timeout", 6), 0, 30),
                bounded(appOpenJson.optInt("background_time_to_show_min", 1), 1, 24 * 60));

        JSONObject units = root.optJSONObject("ad_unit_ids");
        if (units == null) units = new JSONObject();
        return new AdConfig(
                root.optBoolean("enabled", true),
                interstitials,
                banners,
                appOpen,
                units.optString("banner", "").trim(),
                units.optString("interstitial", "").trim(),
                units.optString("app_open", "").trim(),
                units.optString("rewarded", "").trim(),
                root.optBoolean("enable_amazon_sdk", false),
                root.optBoolean("enable_pubmatic_sdk", false));
    }

    private static JSONObject conditions(JSONObject parent) {
        JSONObject value = parent.optJSONObject("availability_conditions");
        return value == null ? new JSONObject() : value;
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static final class Interstitials {
        public final int interval;
        public final int minimumSession;
        public final int minimumGamesPlayed;
        /** No interstitial when the new game replaces one the player did not win (owner requests 2026-11-10). */
        public final boolean skipAfterLoss;

        Interstitials(int interval, int minimumSession, int minimumGamesPlayed, boolean skipAfterLoss) {
            this.interval = interval;
            this.minimumSession = minimumSession;
            this.minimumGamesPlayed = minimumGamesPlayed;
            this.skipAfterLoss = skipAfterLoss;
        }
    }

    public static final class Banners {
        public final int minimumSession;
        public final int minimumGamesPlayed;

        Banners(int minimumSession, int minimumGamesPlayed) {
            this.minimumSession = minimumSession;
            this.minimumGamesPlayed = minimumGamesPlayed;
        }
    }

    public static final class AppOpen {
        public final int coldStartSession;
        public final int backgroundMinimumSession;
        public final int splashTimeoutSeconds;
        public final int backgroundMinutesToShow;

        AppOpen(
                int coldStartSession,
                int backgroundMinimumSession,
                int splashTimeoutSeconds,
                int backgroundMinutesToShow) {
            this.coldStartSession = coldStartSession;
            this.backgroundMinimumSession = backgroundMinimumSession;
            this.splashTimeoutSeconds = splashTimeoutSeconds;
            this.backgroundMinutesToShow = backgroundMinutesToShow;
        }
    }
}
