package com.nexusplaylabss.spider.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class AdmobMediatorConfigTest {
    private static final String BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String APP_OPEN = "ca-app-pub-3940256099942544/9257395921";

    private static String bundled() throws Exception {
        return new String(Files.readAllBytes(
                Paths.get("src/main/assets/config_admbob_mediator_defaults.json")),
                StandardCharsets.UTF_8);
    }

    private static String withRefresh(int refreshSeconds, int floorSeconds) {
        return "{\"_BannerConfig\":{\"AutoRefreshIntervalSec\":" + refreshSeconds
                + ",\"FloorReloadDelaySec\":" + floorSeconds + "}}";
    }

    @Test public void parsesBundledDefault() throws Exception {
        AdmobMediatorConfig config = AdmobMediatorConfig.parse(bundled());
        assertEquals(0, config.version);
        assertFalse(config.disabled);
        assertTrue(config.isActive());
        assertEquals(2, config.priority);
        assertEquals(0, config.bannerType);
        assertEquals(1, config.bannerPosition);
        assertEquals(15, config.autoRefreshIntervalSeconds);
        assertEquals(15, config.floorReloadDelaySeconds);
        assertEquals(BANNER, config.bannerAdUnitId);
        assertEquals(INTERSTITIAL, config.interstitialAdUnitId);
        assertEquals("", config.rewardedAdUnitId);
        assertEquals(APP_OPEN, config.appOpenAdUnitId);
        assertEquals(10000, config.reConsentIntervalMinutes);
    }

    @Test(expected = JSONException.class)
    public void invalidJsonIsRejected() throws Exception {
        AdmobMediatorConfig.parse("{not json");
    }

    @Test public void invalidOrMissingRemoteValueFallsBackToBundled() throws Exception {
        String bundled = bundled();
        assertEquals(BANNER, AdmobMediatorConfig.parseOrFallback("{not json", bundled)
                .bannerAdUnitId);
        assertEquals(BANNER, AdmobMediatorConfig.parseOrFallback(null, bundled).bannerAdUnitId);
    }

    @Test public void missingFieldsUseSafeDefaults() throws Exception {
        AdmobMediatorConfig config = AdmobMediatorConfig.parse("{}");
        assertTrue(config.isActive());
        assertEquals(15, config.autoRefreshIntervalSeconds);
        assertEquals(15, config.floorReloadDelaySeconds);
        assertEquals("", config.bannerAdUnitId);
        assertEquals("", config.interstitialAdUnitId);
        assertEquals("", config.appOpenAdUnitId);
        assertEquals(0, config.reConsentIntervalMinutes);
    }

    @Test public void otherPlatformKeysAreIgnored() throws Exception {
        AdmobMediatorConfig config = AdmobMediatorConfig.parse("{"
                + "\"_BannerData\":{\"AndroidCode\":\" " + BANNER + " \","
                + "\"IOSCode\":\"other-platform-placeholder\"},"
                + "\"_IOSOnlyKey\":true}");
        assertEquals(BANNER, config.bannerAdUnitId);
    }

    @Test public void refreshIntervalIsAlwaysClampedTo15Through120() throws Exception {
        assertEquals(15, AdmobMediatorConfig.parse(withRefresh(5, 15)).autoRefreshIntervalSeconds);
        assertEquals(120,
                AdmobMediatorConfig.parse(withRefresh(500, 15)).autoRefreshIntervalSeconds);
        assertEquals(15, AdmobMediatorConfig.parse(withRefresh(15, 15)).autoRefreshIntervalSeconds);
        assertEquals(45, AdmobMediatorConfig.parse(withRefresh(45, 15)).autoRefreshIntervalSeconds);
        assertEquals(15, AdmobMediatorConfig.parse(withRefresh(-3, 15)).autoRefreshIntervalSeconds);
    }

    @Test public void floorReloadDelayNeverGoesUnder15() throws Exception {
        assertEquals(15, AdmobMediatorConfig.parse(withRefresh(15, 2)).floorReloadDelaySeconds);
        assertEquals(40, AdmobMediatorConfig.parse(withRefresh(15, 40)).floorReloadDelaySeconds);
        assertEquals(120, AdmobMediatorConfig.parse(withRefresh(15, 900)).floorReloadDelaySeconds);
    }

    @Test public void isDisabledSwitchesTheConfigOff() throws Exception {
        AdmobMediatorConfig config = AdmobMediatorConfig.parse(
                "{\"_IsDisabled\":true,\"_BannerData\":{\"AndroidCode\":\"" + BANNER + "\"}}");
        assertTrue(config.disabled);
        assertFalse(config.isActive());
    }
}
