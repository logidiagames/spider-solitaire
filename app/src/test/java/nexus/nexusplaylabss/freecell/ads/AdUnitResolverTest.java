package com.nexusplaylabss.freecell.ads;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AdUnitResolverTest {
    private static final String TEST_UNIT = "ca-app-pub-3940256099942544/9214589741";
    private static final String MEDIATOR_UNIT = "ca-app-pub-1626295360729995/6507689900";
    private static final String ADS_CONFIG_UNIT = "ca-app-pub-1626295360729995/1111111111";

    private static AdmobMediatorConfig mediator(boolean disabled) throws Exception {
        return AdmobMediatorConfig.parse("{\"_IsDisabled\":" + disabled + "}");
    }

    @Test public void debugAlwaysUsesTheTestUnit() throws Exception {
        assertEquals(TEST_UNIT, AdUnitResolver.resolve(
                true, TEST_UNIT, mediator(false), MEDIATOR_UNIT, ADS_CONFIG_UNIT));
        assertEquals(TEST_UNIT, AdUnitResolver.resolve(true, TEST_UNIT, null, "", ""));
    }

    @Test public void activeMediatorWithACodeWinsInRelease() throws Exception {
        assertEquals(MEDIATOR_UNIT, AdUnitResolver.resolve(
                false, TEST_UNIT, mediator(false), MEDIATOR_UNIT, ADS_CONFIG_UNIT));
    }

    @Test public void emptyMediatorCodeFallsBackToAdsConfig() throws Exception {
        assertEquals(ADS_CONFIG_UNIT, AdUnitResolver.resolve(
                false, TEST_UNIT, mediator(false), "", ADS_CONFIG_UNIT));
        assertEquals(ADS_CONFIG_UNIT, AdUnitResolver.resolve(
                false, TEST_UNIT, null, null, ADS_CONFIG_UNIT));
    }

    @Test public void disabledMediatorNeverOverridesIds() throws Exception {
        assertEquals(ADS_CONFIG_UNIT, AdUnitResolver.resolve(
                false, TEST_UNIT, mediator(true), MEDIATOR_UNIT, ADS_CONFIG_UNIT));
    }

    @Test public void bothEmptyMeansTheFormatIsNotRequested() throws Exception {
        assertEquals("", AdUnitResolver.resolve(false, TEST_UNIT, mediator(false), "", ""));
        assertEquals("", AdUnitResolver.resolve(false, TEST_UNIT, mediator(true), MEDIATOR_UNIT,
                null));
    }
}
