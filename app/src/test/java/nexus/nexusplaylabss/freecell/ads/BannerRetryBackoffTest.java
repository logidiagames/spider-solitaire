package com.nexusplaylabss.freecell.ads;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BannerRetryBackoffTest {
    @Test public void doublesFromTheFloorAndStopsAt120() {
        BannerRetryBackoff backoff = new BannerRetryBackoff();
        assertEquals(15, backoff.nextDelaySeconds(15));
        assertEquals(30, backoff.nextDelaySeconds(15));
        assertEquals(60, backoff.nextDelaySeconds(15));
        assertEquals(120, backoff.nextDelaySeconds(15));
        assertEquals(120, backoff.nextDelaySeconds(15));
        assertEquals(5, backoff.consecutiveFailures());
    }

    @Test public void neverWaitsLessThan15Seconds() {
        BannerRetryBackoff backoff = new BannerRetryBackoff();
        assertEquals(15, backoff.nextDelaySeconds(0));
        assertEquals(30, backoff.nextDelaySeconds(3));
    }

    @Test public void usesALargerFloorAndCapsIt() {
        BannerRetryBackoff backoff = new BannerRetryBackoff();
        assertEquals(40, backoff.nextDelaySeconds(40));
        assertEquals(80, backoff.nextDelaySeconds(40));
        assertEquals(120, backoff.nextDelaySeconds(40));
    }

    @Test public void aSuccessfulLoadResetsTheBackoff() {
        BannerRetryBackoff backoff = new BannerRetryBackoff();
        backoff.nextDelaySeconds(15);
        backoff.nextDelaySeconds(15);
        backoff.reset();
        assertEquals(0, backoff.consecutiveFailures());
        assertEquals(15, backoff.nextDelaySeconds(15));
    }
}
