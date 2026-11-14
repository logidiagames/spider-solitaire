package com.nexusplaylabss.spider.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.HashSet;
import java.util.Set;

import com.nexusplaylabss.spider.BuildConfig;
import com.nexusplaylabss.spider.SpiderApplication;
import com.nexusplaylabss.spider.analytics.AnalyticsTracker;

public final class AdsController {
    private static final String TAG = "FreeCellAds";
    private static final String GAMES_PLAYED = "games_played";
    private static final String LAST_INTERSTITIAL_GAME = "last_interstitial_game";
    private static final String BACKGROUND_AT = "background_at";
    /** Offers whose reward the SDK reported but the page has not acknowledged yet. */
    private static final String EARNED_REWARDS = "rewarded_earned";

    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    /** Outcome of a rewarded offer: {@code earned}, {@code closed} (no reward) or {@code unavailable}. */
    public interface RewardedListener {
        void onRewardedResult(String offerId, String outcome);
    }

    private final Activity activity;
    private final FrameLayout bannerContainer;
    private final SharedPreferences preferences;
    private final AnalyticsTracker analyticsTracker;
    private final int sessionCount;
    private final boolean debugBuild;
    private final long launchElapsedRealtime = SystemClock.elapsedRealtime();
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable bannerTick = this::onBannerTick;
    private final BannerRetryBackoff bannerBackoff = new BannerRetryBackoff();

    private AdConfig config;
    private AdmobMediatorConfig mediator;
    private AdView bannerView;
    private AdSize bannerSize;
    private boolean bannerLoading;
    private boolean bannerEverLoaded;
    /** When the banner should next reload (refresh or retry); 0 when nothing is scheduled. */
    private long nextBannerLoadAt;
    private boolean resumed;
    private InterstitialAd interstitialAd;
    private AppOpenAd appOpenAd;
    private boolean mobileAdsInitialized;
    private boolean interstitialLoading;
    private boolean appOpenLoading;
    private boolean showingFullScreen;
    private boolean coldStartAttempted;
    private boolean onboardingBlocking;
    /** Set by the UMP consent flow; nothing is initialised or requested until it is true. */
    private boolean consentAllowsAds;
    /**
     * The player bought Remove Ads: banner, interstitial and app-open are never requested or shown
     * again. The voluntary rewarded video stays, so the SDK still starts when it is offered.
     */
    private boolean adsRemoved;
    /** rewarded_config offers at least one placement in this launch. */
    private boolean rewardedEnabled;
    private volatile RewardedAd rewardedAd;
    private boolean rewardedLoading;
    private RewardedListener rewardedListener;

    public AdsController(Activity activity, FrameLayout bannerContainer,
                         AnalyticsTracker analyticsTracker) {
        this.activity = activity;
        this.bannerContainer = bannerContainer;
        this.analyticsTracker = analyticsTracker;
        this.preferences = activity.getSharedPreferences(
                SpiderApplication.AD_PREFS, Context.MODE_PRIVATE);
        this.sessionCount = ((SpiderApplication) activity.getApplication()).getSessionCount();
        // Test builds (debug and qa) always use Google's test ad units.
        this.debugBuild = BuildConfig.TEST_TOOLS;
    }

    public void updateConfig(AdConfig value, AdmobMediatorConfig mediatorValue) {
        config = value;
        mediator = mediatorValue;
        if (!config.enabled) {
            hideBanner();
            rewardedAd = null;
            return;
        }
        if (!consentAllowsAds || !sdkNeeded()) return;
        initializeMobileAds();
        updateBanner();
        loadInterstitial();
        loadAppOpen();
        loadRewarded();
    }

    /** Set before {@link #updateConfig} from this launch's rewarded_config. */
    public void setRewardedEnabled(boolean enabled) {
        rewardedEnabled = enabled;
        if (!enabled) rewardedAd = null;
    }

    public void setRewardedListener(RewardedListener listener) {
        rewardedListener = listener;
    }

    public void onConsentResolved(boolean canRequestAds) {
        consentAllowsAds = canRequestAds;
        if (!canRequestAds) {
            hideBanner();
            interstitialAd = null;
            appOpenAd = null;
            rewardedAd = null;
            return;
        }
        if (config == null || !config.enabled || !sdkNeeded()) return;
        initializeMobileAds();
        updateBanner();
        loadInterstitial();
        loadAppOpen();
        loadRewarded();
    }

    /** Banner, interstitial and app-open. */
    private boolean adsAllowed() {
        return consentAllowsAds && !adsRemoved;
    }

    /** With Remove Ads bought, the SDK only runs for the rewarded video. */
    private boolean sdkNeeded() {
        return !adsRemoved || rewardedEnabled;
    }

    private boolean rewardedAllowed() {
        return consentAllowsAds && rewardedEnabled && !onboardingBlocking
                && config != null && config.enabled;
    }

    /**
     * After a Remove Ads purchase (or at launch when it is already owned): stop every ad the
     * player did not ask for. The rewarded video is voluntary and stays.
     */
    public void setAdsRemoved() {
        if (adsRemoved) return;
        adsRemoved = true;
        hideBanner();
        interstitialAd = null;
        appOpenAd = null;
    }

    public void setOnboardingBlocking(boolean blocking) {
        onboardingBlocking = blocking;
        if (blocking) hideBanner();
    }

    public void completeOnboarding() {
        if (!onboardingBlocking) return;
        onboardingBlocking = false;
        coldStartAttempted = true;
        preferences.edit().remove(BACKGROUND_AT).apply();
        updateBanner();
        loadInterstitial();
        loadAppOpen();
        loadRewarded();
    }

    /**
     * A restart of the same deal still counts as a played game for the cadence, but never
     * shows an interstitial. Nor does a game started right after a promotional popup (rating card
     * or Remove Ads screen): one interruption per game transition at most.
     */
    public void onGameStarted(String mode, boolean restart, boolean afterPromo, boolean afterLoss) {
        if (onboardingBlocking || "developer".equals(mode)) return;
        int gamesPlayed = gamesPlayed() + 1;
        preferences.edit().putInt(GAMES_PLAYED, gamesPlayed).apply();
        if (adsRemoved) return;
        updateBanner();
        boolean skipAfterLoss = config == null || config.interstitials.skipAfterLoss;
        if (!afterPromo && AdEligibility.interstitialOnGameStart(restart, afterLoss, skipAfterLoss)) maybeShowInterstitial(gamesPlayed);
    }

    public void onBackground() {
        if (!showingFullScreen) {
            preferences.edit().putLong(BACKGROUND_AT, System.currentTimeMillis()).apply();
        }
    }

    public void onForeground() {
        if (config == null || showingFullScreen || onboardingBlocking) return;
        long backgroundAt = preferences.getLong(BACKGROUND_AT, 0L);
        preferences.edit().remove(BACKGROUND_AT).apply();
        if (backgroundAt > 0L && AdEligibility.backgroundAppOpen(
                config, sessionCount, Math.max(0L, System.currentTimeMillis() - backgroundAt))) {
            showAppOpenIfAvailable(false);
        } else {
            maybeShowColdStart();
        }
    }

    public void onResume() {
        resumed = true;
        if (bannerView != null) bannerView.resume();
        scheduleBannerTick();
    }

    public void onPause() {
        resumed = false;
        bannerHandler.removeCallbacks(bannerTick);
        if (bannerView != null) bannerView.pause();
    }

    public void destroy() {
        bannerHandler.removeCallbacks(bannerTick);
        if (bannerView != null) {
            bannerView.destroy();
            bannerView = null;
        }
        bannerContainer.removeAllViews();
        interstitialAd = null;
        appOpenAd = null;
        rewardedAd = null;
        rewardedListener = null;
    }

    private void initializeMobileAds() {
        if (mobileAdsInitialized || !sdkNeeded()) return;
        mobileAdsInitialized = true;
        MobileAds.initialize(activity, status -> {
            Log.i(TAG, "Mobile Ads initialized. Amazon enabled=" + config.enableAmazonSdk
                    + ", PubMatic enabled=" + config.enablePubmaticSdk);
            updateBanner();
            loadInterstitial();
            loadAppOpen();
            loadRewarded();
        });
    }

    private void updateBanner() {
        if (onboardingBlocking || config == null || !adsAllowed() || !mobileAdsInitialized
                || !AdEligibility.banner(config, sessionCount, gamesPlayed())) {
            hideBanner();
            return;
        }
        if (bannerView != null) return;

        String adUnit = adUnit(mediator == null ? null : mediator.bannerAdUnitId,
                config.bannerAdUnitId, TEST_BANNER);
        if (adUnit.isEmpty()) return;
        AdView view = new AdView(activity);
        view.setAdUnitId(adUnit);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int widthDp = Math.max(320, (int) (metrics.widthPixels / metrics.density));
        bannerSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
        view.setAdSize(bannerSize);
        view.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                bannerLoading = false;
                bannerBackoff.reset();
                logAdEvent("ad_loaded", "banner", "game_footer");
                if (!bannerEverLoaded) {
                    bannerEverLoaded = true;
                    // Reserve the slot at the ad's own height, so later reloads (and a reload
                    // that fails, which keeps the previous ad) never move the game above it.
                    bannerContainer.setMinimumHeight(bannerSize.getHeightInPixels(activity));
                }
                bannerContainer.setVisibility(View.VISIBLE);
                if (autoRefreshActive()) {
                    planBannerLoad(mediator.autoRefreshIntervalSeconds * 1000L);
                }
            }

            @Override public void onAdImpression() {
                logAdEvent("ad_shown", "banner", "game_footer");
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                bannerLoading = false;
                logAdFailure("banner", "game_footer", error.getCode(), error.getDomain());
                Log.w(TAG, "Banner failed to load: " + error.getMessage());
                if (!autoRefreshActive()) {
                    hideBanner();
                    return;
                }
                int delay = bannerBackoff.nextDelaySeconds(mediator.floorReloadDelaySeconds);
                Log.i(TAG, "Banner retry in " + delay + "s (failure "
                        + bannerBackoff.consecutiveFailures() + ")");
                planBannerLoad(delay * 1000L);
            }
        });
        bannerView = view;
        bannerContainer.removeAllViews();
        bannerContainer.addView(view);
        requestBannerLoad();
    }

    private void requestBannerLoad() {
        if (bannerView == null || bannerLoading) return;
        bannerLoading = true;
        nextBannerLoadAt = 0L;
        Log.d(TAG, "Banner request");
        logAdEvent("ad_request", "banner", "game_footer");
        bannerView.loadAd(new AdRequest.Builder().build());
    }

    /** Auto-refresh (and retry with backoff) only run while the mediator config is active. */
    private boolean autoRefreshActive() {
        return mediator != null && mediator.isActive();
    }

    private void planBannerLoad(long delayMs) {
        nextBannerLoadAt = SystemClock.elapsedRealtime() + delayMs;
        scheduleBannerTick();
    }

    /** (Re)posts the timer for the planned load; it only runs while the activity is resumed. */
    private void scheduleBannerTick() {
        bannerHandler.removeCallbacks(bannerTick);
        if (!resumed || nextBannerLoadAt == 0L || bannerView == null) return;
        long delay = Math.max(1000L, nextBannerLoadAt - SystemClock.elapsedRealtime());
        bannerHandler.postDelayed(bannerTick, delay);
    }

    private void onBannerTick() {
        // Not loading now leaves the load planned; onResume, the end of a full-screen ad and
        // the end of onboarding call scheduleBannerTick again.
        if (!canReloadBanner()) return;
        requestBannerLoad();
    }

    private boolean canReloadBanner() {
        return resumed && autoRefreshActive() && bannerView != null && !bannerLoading
                && !showingFullScreen && !onboardingBlocking && adsAllowed()
                && config != null && config.enabled
                // A refresh needs the banner on screen; a retry of the very first load does not.
                && (!bannerEverLoaded || bannerContainer.getVisibility() == View.VISIBLE);
    }

    private void hideBanner() {
        bannerHandler.removeCallbacks(bannerTick);
        nextBannerLoadAt = 0L;
        bannerLoading = false;
        bannerEverLoaded = false;
        bannerBackoff.reset();
        bannerContainer.setVisibility(View.GONE);
        bannerContainer.setMinimumHeight(0);
        if (bannerView != null) {
            bannerView.destroy();
            bannerView = null;
        }
        bannerContainer.removeAllViews();
    }

    private void loadInterstitial() {
        if (config == null || !config.enabled || !adsAllowed() || !mobileAdsInitialized
                || interstitialLoading || interstitialAd != null) return;
        String adUnit = adUnit(mediator == null ? null : mediator.interstitialAdUnitId,
                config.interstitialAdUnitId, TEST_INTERSTITIAL);
        if (adUnit.isEmpty()) return;
        interstitialLoading = true;
        logAdEvent("ad_request", "interstitial", "between_games");
        InterstitialAd.load(activity, adUnit, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialLoading = false;
                        interstitialAd = ad;
                        logAdEvent("ad_loaded", "interstitial", "between_games");
                    }

                    @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        interstitialLoading = false;
                        interstitialAd = null;
                        logAdFailure("interstitial", "between_games",
                                error.getCode(), error.getDomain());
                        Log.w(TAG, "Interstitial failed to load: " + error.getMessage());
                    }
                });
    }

    private void maybeShowInterstitial(int gamesPlayed) {
        if (onboardingBlocking || config == null || showingFullScreen || !AdEligibility.interstitial(
                config,
                sessionCount,
                gamesPlayed,
                preferences.getInt(LAST_INTERSTITIAL_GAME, 0))) {
            return;
        }
        if (interstitialAd == null) {
            loadInterstitial();
            return;
        }
        InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        showingFullScreen = true;
        preferences.edit().putInt(LAST_INTERSTITIAL_GAME, gamesPlayed).apply();
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                logAdEvent("ad_shown", "interstitial", "between_games");
            }

            @Override public void onAdDismissedFullScreenContent() {
                logAdEvent("ad_dismissed", "interstitial", "between_games");
                showingFullScreen = false;
                scheduleBannerTick();
                loadInterstitial();
            }

            @Override public void onAdFailedToShowFullScreenContent(
                    @NonNull com.google.android.gms.ads.AdError error) {
                logAdFailure("interstitial", "between_games",
                        error.getCode(), error.getDomain());
                showingFullScreen = false;
                scheduleBannerTick();
                loadInterstitial();
            }
        });
        ad.show(activity);
    }

    private void loadAppOpen() {
        if (config == null || !config.enabled || !adsAllowed() || !mobileAdsInitialized
                || appOpenLoading || appOpenAd != null) return;
        String adUnit = adUnit(mediator == null ? null : mediator.appOpenAdUnitId,
                config.appOpenAdUnitId, TEST_APP_OPEN);
        if (adUnit.isEmpty()) return;
        appOpenLoading = true;
        logAdEvent("ad_request", "app_open", "app_foreground");
        AppOpenAd.load(activity, adUnit, new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenLoading = false;
                        appOpenAd = ad;
                        logAdEvent("ad_loaded", "app_open", "app_foreground");
                        maybeShowColdStart();
                    }

                    @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        appOpenLoading = false;
                        appOpenAd = null;
                        logAdFailure("app_open", "app_foreground",
                                error.getCode(), error.getDomain());
                        Log.w(TAG, "App open failed to load: " + error.getMessage());
                    }
                });
    }

    private void maybeShowColdStart() {
        if (onboardingBlocking || config == null || coldStartAttempted || showingFullScreen
                || !AdEligibility.coldStartAppOpen(config, sessionCount)) return;
        long elapsed = SystemClock.elapsedRealtime() - launchElapsedRealtime;
        if (elapsed > config.appOpen.splashTimeoutSeconds * 1000L) {
            coldStartAttempted = true;
            return;
        }
        if (appOpenAd != null) {
            coldStartAttempted = true;
            showAppOpenIfAvailable(true);
        }
    }

    private void showAppOpenIfAvailable(boolean coldStart) {
        if (onboardingBlocking || adsRemoved) return;
        if (appOpenAd == null || showingFullScreen) {
            loadAppOpen();
            return;
        }
        AppOpenAd ad = appOpenAd;
        appOpenAd = null;
        showingFullScreen = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                logAdEvent("ad_shown", "app_open", "app_foreground");
            }

            @Override public void onAdDismissedFullScreenContent() {
                logAdEvent("ad_dismissed", "app_open", "app_foreground");
                showingFullScreen = false;
                scheduleBannerTick();
                loadAppOpen();
            }

            @Override public void onAdFailedToShowFullScreenContent(
                    @NonNull com.google.android.gms.ads.AdError error) {
                logAdFailure("app_open", "app_foreground",
                        error.getCode(), error.getDomain());
                showingFullScreen = false;
                scheduleBannerTick();
                loadAppOpen();
            }
        });
        Log.i(TAG, coldStart ? "Showing cold-start app open" : "Showing resume app open");
        ad.show(activity);
    }

    // ---- Rewarded video ("double your coins") ----

    public boolean rewardedReady() {
        return rewardedAd != null && !showingFullScreen && rewardedAllowed();
    }

    /** Preload at a quiet moment; does nothing when one is loaded or loading. */
    public void loadRewarded() {
        if (!rewardedAllowed() || !mobileAdsInitialized || rewardedLoading || rewardedAd != null) return;
        String adUnit = adUnit(null, config.rewardedAdUnitId, TEST_REWARDED);
        if (adUnit.isEmpty()) return;
        rewardedLoading = true;
        logAdEvent("ad_request", "rewarded", "double_coins");
        RewardedAd.load(activity, adUnit, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedLoading = false;
                rewardedAd = ad;
                logAdEvent("ad_loaded", "rewarded", "double_coins");
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError error) {
                rewardedLoading = false;
                rewardedAd = null;
                logAdFailure("rewarded", "double_coins", error.getCode(), error.getDomain());
                Log.w(TAG, "Rewarded failed to load: " + error.getMessage());
            }
        });
    }

    /**
     * Shows the video for one offer. The reward is saved (under the offer id) the moment the SDK
     * reports it, so a crash before the page credits it cannot lose or double it: the page
     * credits each offer once and then calls {@link #acknowledgeReward}.
     */
    public void showRewarded(String offerId, String placement) {
        RewardedAd ad = rewardedAd;
        if (ad == null || showingFullScreen || !rewardedAllowed()) {
            notifyRewarded(offerId, "unavailable");
            loadRewarded();
            return;
        }
        rewardedAd = null;
        showingFullScreen = true;
        boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() {
                logAdEvent("ad_shown", "rewarded", placement);
            }

            @Override public void onAdDismissedFullScreenContent() {
                logAdEvent("ad_dismissed", "rewarded", placement);
                finishRewarded(offerId, earned[0] ? "earned" : "closed");
            }

            @Override public void onAdFailedToShowFullScreenContent(
                    @NonNull com.google.android.gms.ads.AdError error) {
                logAdFailure("rewarded", placement, error.getCode(), error.getDomain());
                finishRewarded(offerId, earned[0] ? "earned" : "unavailable");
            }
        });
        ad.show(activity, reward -> {
            earned[0] = true;
            Set<String> pending = new HashSet<>(preferences.getStringSet(EARNED_REWARDS, new HashSet<>()));
            pending.add(offerId);
            preferences.edit().putStringSet(EARNED_REWARDS, pending).commit();
        });
    }

    /** Earned offers the page has not acknowledged (for example after a crash). */
    public Set<String> earnedRewards() {
        return new HashSet<>(preferences.getStringSet(EARNED_REWARDS, new HashSet<>()));
    }

    public void acknowledgeReward(String offerId) {
        Set<String> pending = earnedRewards();
        if (pending.remove(offerId)) preferences.edit().putStringSet(EARNED_REWARDS, pending).apply();
    }

    private void finishRewarded(String offerId, String outcome) {
        showingFullScreen = false;
        scheduleBannerTick();
        notifyRewarded(offerId, outcome);
        loadRewarded();
    }

    private void notifyRewarded(String offerId, String outcome) {
        if (rewardedListener != null) rewardedListener.onRewardedResult(offerId, outcome);
    }

    private int gamesPlayed() {
        return Math.max(0, preferences.getInt(GAMES_PLAYED, 0));
    }

    private String adUnit(String mediatorUnit, String configured, String testUnit) {
        return AdUnitResolver.resolve(debugBuild, testUnit, mediator, mediatorUnit, configured);
    }

    private void logAdEvent(String event, String format, String placement) {
        Bundle parameters = new Bundle();
        parameters.putString("ad_format", format);
        parameters.putString("placement", placement);
        parameters.putInt("session_count", sessionCount);
        parameters.putInt("games_played", gamesPlayed());
        analyticsTracker.logEvent(event, parameters);
    }

    private void logAdFailure(String format, String placement, int code, String domain) {
        Bundle parameters = new Bundle();
        parameters.putString("ad_format", format);
        parameters.putString("placement", placement);
        parameters.putInt("session_count", sessionCount);
        parameters.putInt("games_played", gamesPlayed());
        parameters.putInt("error_code", code);
        parameters.putString("error_domain", domain);
        analyticsTracker.logEvent("ad_failed", parameters);
    }
}
