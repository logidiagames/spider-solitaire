package com.nexusplaylabss.spider.ads;

/**
 * Delay before retrying a banner that failed to load: starts at the configured floor (never
 * under 15 s), doubles on each consecutive failure, and stops growing at 120 s. A successful
 * load resets it, so a single failure never slows a healthy banner down for long.
 */
public final class BannerRetryBackoff {
    private int consecutiveFailures;

    /** Records a failure and returns how long to wait before the next attempt, in seconds. */
    public int nextDelaySeconds(int floorSeconds) {
        consecutiveFailures++;
        long delay = AdmobMediatorConfig.clampRetrySeconds(floorSeconds);
        for (int i = 1; i < consecutiveFailures && delay < AdmobMediatorConfig.MAX_RETRY_SECONDS;
                i++) {
            delay *= 2;
        }
        return (int) Math.min(delay, AdmobMediatorConfig.MAX_RETRY_SECONDS);
    }

    public void reset() {
        consecutiveFailures = 0;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
