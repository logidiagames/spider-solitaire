package com.nexusplaylabss.freecell.ads;

public final class AdEligibility {
    private AdEligibility() {}

    public static boolean banner(AdConfig config, int session, int gamesPlayed) {
        return config.enabled
                && session >= config.banners.minimumSession
                && gamesPlayed >= config.banners.minimumGamesPlayed;
    }

    public static boolean interstitial(
            AdConfig config,
            int session,
            int gamesPlayed,
            int lastShownAtGame) {
        if (!config.enabled
                || session < config.interstitials.minimumSession
                || gamesPlayed < config.interstitials.minimumGamesPlayed) {
            return false;
        }
        int baseline = Math.max(
                config.interstitials.minimumGamesPlayed - config.interstitials.interval,
                lastShownAtGame);
        return gamesPlayed - baseline >= config.interstitials.interval;
    }

    /**
     * Restarting the same deal never earns an interstitial; only a new deal may. With
     * {@code skipAfterLoss} (ads_config, on by default) neither does a new deal that follows a game
     * the player did not win. The interval and the session / games-played conditions stay as they
     * are: the next game after a win can show the interstitial that was skipped.
     */
    public static boolean interstitialOnGameStart(boolean restart, boolean afterLoss, boolean skipAfterLoss) {
        return !restart && !(afterLoss && skipAfterLoss);
    }

    public static boolean coldStartAppOpen(AdConfig config, int session) {
        return config.enabled && session >= config.appOpen.coldStartSession;
    }

    public static boolean backgroundAppOpen(
            AdConfig config,
            int session,
            long backgroundDurationMillis) {
        long minimum = config.appOpen.backgroundMinutesToShow * 60_000L;
        return config.enabled
                && session >= config.appOpen.backgroundMinimumSession
                && backgroundDurationMillis >= minimum;
    }
}
