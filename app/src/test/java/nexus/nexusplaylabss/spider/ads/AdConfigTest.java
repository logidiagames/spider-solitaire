package com.nexusplaylabss.spider.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class AdConfigTest {
    private static final String PRODUCTION_PUBLISHER = "ca-app-pub-1626295360729995/";

    private static final String CONFIG = "{"
            + "\"availability\":{"
            + "\"interstitials\":{\"interval\":1,\"availability_conditions\":{\"session\":1,\"games_played\":3}},"
            + "\"banners\":{\"availability_conditions\":{\"session\":1,\"games_played\":0}},"
            + "\"app_open\":{\"cold_start_session\":1,\"background_min_session\":1,\"splash_timeout\":6,\"background_time_to_show_min\":1}},"
            + "\"enable_amazon_sdk\":true,\"enable_pubmatic_sdk\":true}";

    @Test public void parsesRequestedConfiguration() throws Exception {
        AdConfig config = AdConfig.parse(CONFIG);
        assertEquals(1, config.interstitials.interval);
        assertEquals(1, config.interstitials.minimumSession);
        assertEquals(3, config.interstitials.minimumGamesPlayed);
        assertEquals(1, config.banners.minimumSession);
        assertEquals(0, config.banners.minimumGamesPlayed);
        assertEquals(6, config.appOpen.splashTimeoutSeconds);
        assertEquals(1, config.appOpen.backgroundMinutesToShow);
        assertTrue(config.enableAmazonSdk);
        assertTrue(config.enablePubmaticSdk);
    }

    @Test public void interstitialStartsAtThirdGameAndRespectsInterval() throws Exception {
        AdConfig config = AdConfig.parse(CONFIG);
        assertFalse(AdEligibility.interstitial(config, 1, 2, 0));
        assertTrue(AdEligibility.interstitial(config, 1, 3, 0));
        assertFalse(AdEligibility.interstitial(config, 1, 3, 3));
        assertTrue(AdEligibility.interstitial(config, 1, 4, 3));
    }

    // Spider Solitaire has no ad units of its own yet: the bundled defaults are Google's test units
    // until the owner creates them in AdMob (publisher ca-app-pub-1626295360729995). Never another app's.
    private static final String GOOGLE_TEST_PUBLISHER = "ca-app-pub-3940256099942544/";

    @Test public void bundledDefaultsCarryOurPublisherOrGoogleTestUnits() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/ad_config_defaults.json")),
                StandardCharsets.UTF_8);
        AdConfig config = AdConfig.parse(json);
        for (String unit : new String[] {
                config.bannerAdUnitId, config.interstitialAdUnitId, config.appOpenAdUnitId, config.rewardedAdUnitId}) {
            assertTrue(unit, unit.startsWith(PRODUCTION_PUBLISHER) || unit.startsWith(GOOGLE_TEST_PUBLISHER));
            assertFalse("FreeCell's ad units belong to FreeCell", unit.endsWith("/6507689900") || unit.endsWith("/5548540454")
                    || unit.endsWith("/1504212022") || unit.endsWith("/3060836279"));
        }
    }

    @Test public void rewardedUnitIsReadAndEmptyByDefault() throws Exception {
        assertEquals("", AdConfig.parse(CONFIG).rewardedAdUnitId);
        assertEquals(PRODUCTION_PUBLISHER + "1",
                AdConfig.parse("{\"ad_unit_ids\":{\"rewarded\":\" " + PRODUCTION_PUBLISHER + "1 \"}}").rewardedAdUnitId);
    }

    @Test public void restartingADealNeverShowsAnInterstitial() {
        assertFalse(AdEligibility.interstitialOnGameStart(true, false, true));
        assertTrue(AdEligibility.interstitialOnGameStart(false, false, true));
        assertFalse("no interstitial after a loss", AdEligibility.interstitialOnGameStart(false, true, true));
        assertTrue("switched off in Remote Config", AdEligibility.interstitialOnGameStart(false, true, false));
    }

    @Test public void bannerAndAppOpenRespectSessionAndBackgroundThresholds() throws Exception {
        AdConfig config = AdConfig.parse(CONFIG);
        assertTrue(AdEligibility.banner(config, 1, 0));
        assertTrue(AdEligibility.coldStartAppOpen(config, 1));
        assertFalse(AdEligibility.backgroundAppOpen(config, 1, 59_999L));
        assertTrue(AdEligibility.backgroundAppOpen(config, 1, 60_000L));
    }
}
