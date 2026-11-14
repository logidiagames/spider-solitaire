package com.nexusplaylabss.freecell.ads;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The {@code config_admbob_mediator} Remote Config parameter (Android only).
 *
 * It drives the banner auto-refresh and may override the ad unit IDs of {@link AdConfig}. Only
 * the {@code AndroidCode} of each format is read; any other platform's keys are ignored. Fields
 * the app does not act on yet ({@code Type}, {@code Position}, {@code _Priority},
 * {@code _Version}, the rewarded unit and {@code _ReConsentIntervalMinutes}) are parsed and kept
 * for logging only. See docs/REMOTE_CONFIG_ADS.md.
 */
public final class AdmobMediatorConfig {
    public static final String REMOTE_CONFIG_KEY = "config_admbob_mediator";

    public static final int MIN_REFRESH_SECONDS = 15;
    public static final int MAX_REFRESH_SECONDS = 120;
    public static final int MIN_RETRY_SECONDS = 15;
    public static final int MAX_RETRY_SECONDS = 120;

    public final int version;
    public final boolean disabled;
    public final int priority;
    public final int bannerType;
    public final int bannerPosition;
    public final int autoRefreshIntervalSeconds;
    public final int floorReloadDelaySeconds;
    public final String bannerAdUnitId;
    public final String interstitialAdUnitId;
    public final String rewardedAdUnitId;
    public final String appOpenAdUnitId;
    public final int reConsentIntervalMinutes;

    private AdmobMediatorConfig(JSONObject root) {
        JSONObject banner = object(root, "_BannerConfig");
        version = root.optInt("_Version", 0);
        disabled = root.optBoolean("_IsDisabled", false);
        priority = root.optInt("_Priority", 0);
        bannerType = banner.optInt("Type", 0);
        bannerPosition = banner.optInt("Position", 0);
        autoRefreshIntervalSeconds = clampRefreshSeconds(
                banner.optInt("AutoRefreshIntervalSec", MIN_REFRESH_SECONDS));
        floorReloadDelaySeconds = clampRetrySeconds(
                banner.optInt("FloorReloadDelaySec", MIN_RETRY_SECONDS));
        bannerAdUnitId = androidCode(root, "_BannerData");
        interstitialAdUnitId = androidCode(root, "_InterstitialData");
        rewardedAdUnitId = androidCode(root, "_RewardedData");
        appOpenAdUnitId = androidCode(root, "_AppOpenData");
        reConsentIntervalMinutes = Math.max(0, root.optInt("_ReConsentIntervalMinutes", 0));
    }

    public static AdmobMediatorConfig parse(String json) throws JSONException {
        return new AdmobMediatorConfig(new JSONObject(json));
    }

    /** The remote value when it parses, otherwise the bundled one, which must be valid. */
    public static AdmobMediatorConfig parseOrFallback(String value, String bundledDefault) {
        try {
            return parse(value);
        } catch (JSONException | NullPointerException invalidRemoteValue) {
            try {
                return parse(bundledDefault);
            } catch (JSONException impossible) {
                throw new IllegalStateException("Bundled mediator configuration is invalid",
                        impossible);
            }
        }
    }

    /** False when {@code _IsDisabled} is set: no auto-refresh and no ad unit overrides. */
    public boolean isActive() {
        return !disabled;
    }

    public static int clampRefreshSeconds(int seconds) {
        return Math.max(MIN_REFRESH_SECONDS, Math.min(seconds, MAX_REFRESH_SECONDS));
    }

    public static int clampRetrySeconds(int seconds) {
        return Math.max(MIN_RETRY_SECONDS, Math.min(seconds, MAX_RETRY_SECONDS));
    }

    public String summary() {
        return "version=" + version + ", disabled=" + disabled + ", priority=" + priority
                + ", bannerType=" + bannerType + ", bannerPosition=" + bannerPosition
                + ", refresh=" + autoRefreshIntervalSeconds + "s"
                + ", floorReload=" + floorReloadDelaySeconds + "s"
                + ", rewardedSet=" + !rewardedAdUnitId.isEmpty()
                + ", reConsentMinutes=" + reConsentIntervalMinutes;
    }

    private static JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static String androidCode(JSONObject root, String key) {
        return object(root, key).optString("AndroidCode", "").trim();
    }
}
