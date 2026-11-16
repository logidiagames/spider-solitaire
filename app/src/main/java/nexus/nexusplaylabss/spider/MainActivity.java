package com.nexusplaylabss.spider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.app.LocaleManager;
import android.app.ActivityManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Base64;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.util.Log;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import com.nexusplaylabss.spider.feel.Haptics;
import com.nexusplaylabss.spider.i18n.Languages;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.nexusplaylabss.spider.ads.AdConfig;
import com.nexusplaylabss.spider.ads.AdmobMediatorConfig;
import com.nexusplaylabss.spider.ads.AdsConsent;
import com.nexusplaylabss.spider.ads.AdsController;
import com.nexusplaylabss.spider.billing.RemoveAdsManager;
import com.nexusplaylabss.spider.config.GameConfig;
import com.nexusplaylabss.spider.config.RemoteJsonConfig;
import com.nexusplaylabss.spider.diagnostics.CrashReporter;
import com.nexusplaylabss.spider.reminders.ReminderPlan;
import com.nexusplaylabss.spider.reminders.Reminders;
import com.nexusplaylabss.spider.share.SharedImage;
import com.nexusplaylabss.spider.analytics.AnalyticsTracker;

public final class MainActivity extends Activity {
    private static final String PREFS = "freecell_web_state";
    private static final String SAVED_STATE = "saved_state";
    /** The numbered deals collection, kept apart from the saved state with its own size limit. */
    private static final String NUMBERED_COLLECTION = "numbered_collection";
    private static final String FIRST_TUTORIAL_COMPLETED = "first_tutorial_completed";
    /** Last launch's rewarded_config said a rewarded video can be offered (decides consent with Remove Ads). */
    private static final String REWARDED_OFFERED = "rewarded_offered";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 41;
    private static final Set<String> REWARDED_PLACEMENTS =
            new HashSet<>(Arrays.asList("victory_double", "daily_bonus_double"));
    // The page may only open these two targets; they must match freecell.html.
    private static final String PRIVACY_POLICY_URL =
            "https://sites.google.com/view/spidersolitairenexus";
    private static final String SUPPORT_EMAIL = "nexusplaylabss@gmail.com";
    private static final String SUPPORT_MAILTO =
            "mailto:" + SUPPORT_EMAIL + "?subject=Spider%20Solitaire%20Support";

    private WebView webView;
    private AdsController adsController;
    private AdsConsent adsConsent;
    /** True until this launch's consent check has finished; read by the page from its thread. */
    private volatile boolean launchConsentPending = true;
    private AnalyticsTracker analyticsTracker;
    private CrashReporter crashReporter;
    private OnBackInvokedCallback backCallback;
    /** This launch's validated game settings for the page; null until Remote Config delivers. */
    private volatile String gameConfigJson;
    private String bundledGameConfigJson;
    private RemoveAdsManager removeAds;
    /** Which reminder opened the app ("daily" or "risk"), read once by the page. */
    private volatile String openedFromReminder = "";
    /** When this launch's onCreate began, for the startup time the page reports (v1.4 Part 5). */
    private long launchStartedAt;
    private boolean fullyDrawnReported;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchStartedAt = SystemClock.uptimeMillis();
        if (BuildConfig.DEBUG) enableDebugChecks();
        // The page (about 1 MB of assets) is read on its own thread while the WebView, consent, ads
        // and Remote Config start here (v1.4 Part 5); it is loaded once both are ready.
        new Thread(this::readPage, "freecell-page").start();
        readReminderExtra(getIntent());
        getWindow().setStatusBarColor(Color.rgb(5, 70, 43));
        getWindow().setNavigationBarColor(Color.rgb(4, 51, 31));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(12, 124, 61));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setTextZoom(100);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        LinearLayout appLayout = new LinearLayout(this);
        appLayout.setOrientation(LinearLayout.VERTICAL);
        appLayout.setBackgroundColor(Color.rgb(12, 124, 61));
        appLayout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout bannerContainer = new FrameLayout(this);
        bannerContainer.setVisibility(View.GONE);
        appLayout.addView(bannerContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(appLayout);

        boolean tutorialRequired = !getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(FIRST_TUTORIAL_COMPLETED, false);
        crashReporter = new CrashReporter(this, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        analyticsTracker = new AnalyticsTracker(this, BuildConfig.TEST_TOOLS);
        adsController = new AdsController(this, bannerContainer, analyticsTracker);
        adsController.setOnboardingBlocking(tutorialRequired);
        adsController.setRewardedListener(this::onRewardedResult);
        // Remove Ads: the purchase cached from the last check stops every ad the player did not ask
        // for. The voluntary rewarded video stays, so the ads SDK and the consent form still run
        // unless the last rewarded_config offered no rewarded video at all.
        boolean rewardedOffered = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(REWARDED_OFFERED, true);
        adsController.setRewardedEnabled(rewardedOffered);
        boolean adsRemoved = RemoveAdsManager.cachedAdsRemoved(this);
        if (adsRemoved) adsController.setAdsRemoved();
        analyticsTracker.setUserProperty("ads_removed", adsRemoved ? "yes" : "no");
        removeAds = new RemoveAdsManager(this, BuildConfig.TEST_TOOLS, this::onRemoveAdsChanged);
        removeAds.start();
        // Google's required order: consent first, and only then the ads SDK. The loading screen
        // holds its last step until this resolves, so the form is always shown over it.
        adsConsent = new AdsConsent(this);
        if (adsRemoved && !rewardedOffered) onLaunchConsentResolved(false);
        else adsConsent.gather(this, this::onLaunchConsentResolved);
        try {
            RemoteJsonConfig remoteConfig = new RemoteJsonConfig(this, remoteConfigParameters());
            bundledGameConfigJson =
                    GameConfig.sanitize(remoteConfig.bundledValues()::object).toString();
            remoteConfig.start(values -> {
                JSONObject game = GameConfig.sanitize(values::object);
                gameConfigJson = game.toString();
                boolean rewarded = GameConfig.rewardedOffered(game);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(REWARDED_OFFERED, rewarded).apply();
                AdConfig ads = AdConfig.parseOrFallback(
                        values.remote(AdConfig.REMOTE_CONFIG_KEY),
                        values.bundled(AdConfig.REMOTE_CONFIG_KEY));
                AdmobMediatorConfig mediator = AdmobMediatorConfig.parseOrFallback(
                        values.remote(AdmobMediatorConfig.REMOTE_CONFIG_KEY),
                        values.bundled(AdmobMediatorConfig.REMOTE_CONFIG_KEY));
                Log.i("RemoteJsonConfig",
                        AdmobMediatorConfig.REMOTE_CONFIG_KEY + ": " + mediator.summary());
                runOnUiThread(() -> {
                    if (adsController == null) return;
                    adsController.setRewardedEnabled(rewarded);
                    adsController.updateConfig(ads, mediator);
                });
            });
        } catch (IOException error) {
            throw new IllegalStateException("Missing bundled Remote Config defaults", error);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::handleBackRequest;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }

    }

    /** Builds the page off the main thread, then loads it in the WebView on the main thread. */
    private void readPage() {
        String document;
        try {
            document = buildDocument(readAsset("freecell.html"));
        } catch (IOException error) {
            document = null;
        }
        String page = document;
        runOnUiThread(() -> {
            if (isDestroyed() || webView == null) return;
            if (page == null) webView.loadData("<h1>Unable to load Spider Solitaire</h1>", "text/html", "UTF-8");
            else webView.loadDataWithBaseURL("https://spider.local/", page, "text/html", "UTF-8", null);
        });
    }

    /**
     * Debug builds only (v1.4 Part 5): the page can be inspected from chrome://inspect, and StrictMode
     * logs disk or network work on the main thread and leaked resources (tag "StrictMode").
     */
    private static void enableDebugChecks() {
        WebView.setWebContentsDebuggingEnabled(true);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }

    /** Every Remote Config parameter of the app and its bundled default in assets. */
    private static Map<String, String> remoteConfigParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(AdConfig.REMOTE_CONFIG_KEY, "ad_config_defaults.json");
        parameters.put(AdmobMediatorConfig.REMOTE_CONFIG_KEY, "config_admbob_mediator_defaults.json");
        for (String key : GameConfig.KEYS) parameters.put(key, GameConfig.bundledAsset(key));
        return parameters;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readReminderExtra(intent);
        if (webView != null && !openedFromReminder.isEmpty()) {
            webView.evaluateJavascript("window.freecellReminderOpened&&window.freecellReminderOpened()", null);
        }
    }

    private void readReminderExtra(Intent intent) {
        String type = intent == null ? null : intent.getStringExtra(Reminders.EXTRA_TYPE);
        if (ReminderPlan.DAILY.equals(type) || ReminderPlan.RISK.equals(type)) openedFromReminder = type;
    }

    /** Asks for the Android 13+ notification permission; the page hears back in freecellNotificationPermission. */
    private void requestNotificationPermission() {
        if (Reminders.allowed(this)) {
            onNotificationPermission(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        } else {
            // Notifications are off for the app in Android settings: only the player can turn them on there.
            onNotificationPermission(false);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) onNotificationPermission(Reminders.allowed(this));
    }

    private void onNotificationPermission(boolean granted) {
        if (webView == null) return;
        webView.evaluateJavascript("window.freecellNotificationPermission&&window.freecellNotificationPermission("
                + granted + ")", null);
    }

    private void openNotificationSettings() {
        Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Nothing to open; the page already explained where the setting is.
        }
    }

    @Override protected void onPause() {
        if (adsController != null) adsController.onPause();
        if (webView != null) {
            webView.evaluateJavascript("window.freecellSave&&window.freecellSave()", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (adsController != null) adsController.onResume();
    }

    @Override protected void onStart() {
        super.onStart();
        if (adsController != null) adsController.onForeground();
    }

    @Override protected void onStop() {
        if (adsController != null) adsController.onBackground();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        if (adsController != null) {
            adsController.destroy();
            adsController = null;
        }
        super.onDestroy();
    }

    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        handleBackRequest();
    }

    private void onLaunchConsentResolved(boolean canRequestAds) {
        launchConsentPending = false;
        onAdsConsentResolved(canRequestAds);
        // If the page read the flag while it was still pending, it is waiting for this call.
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.freecellConsentResolved&&window.freecellConsentResolved()", null);
        }
    }

    /** Billing changed: stop ads at once after a purchase, and tell the page. */
    private void onRemoveAdsChanged(RemoveAdsManager manager, String event) {
        if (manager.adsRemoved() && adsController != null) adsController.setAdsRemoved();
        if (analyticsTracker != null) analyticsTracker.setUserProperty("ads_removed", manager.adsRemoved() ? "yes" : "no");
        if (webView == null) return;
        webView.evaluateJavascript("window.freecellBillingUpdate&&window.freecellBillingUpdate("
                + manager.toJson() + "," + JSONObject.quote(event) + ")", null);
    }

    /** The rewarded video ended: the page credits {@code earned} once per offer id. */
    private void onRewardedResult(String offerId, String outcome) {
        if (webView == null) return;
        webView.evaluateJavascript("window.freecellRewardedResult&&window.freecellRewardedResult("
                + JSONObject.quote(offerId) + "," + JSONObject.quote(outcome) + ")", null);
    }

    /** The Android share sheet with plain text (challenge a friend, or a Daily result without image). */
    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, null));
        } catch (ActivityNotFoundException ignored) {
            // Nothing can share; the page has nothing else to do.
        }
    }

    /**
     * Writes the page's Daily result PNG (checked: PNG only, 2 MB at most) to the cache and shares
     * it through the FileProvider, with the text. No storage permission is needed.
     */
    private boolean shareImage(String image, String text) {
        String payload = SharedImage.payload(image);
        if (payload == null) return false;
        byte[] bytes;
        try {
            bytes = Base64.decode(payload, Base64.DEFAULT);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (!SharedImage.isPng(bytes)) return false;
        File folder = new File(getCacheDir(), "shared");
        if (!folder.isDirectory() && !folder.mkdirs()) return false;
        File file = new File(folder, "spider-daily.png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        } catch (IOException error) {
            return false;
        }
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException error) {
            return false;
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("", uri));
        runOnUiThread(() -> {
            try {
                startActivity(Intent.createChooser(send, null));
            } catch (ActivityNotFoundException ignored) {
                shareText(text);
            }
        });
        return true;
    }

    /** "Rate us": Spider Solitaire's page in the Play Store app, or on the web when it is missing. */
    private void openPlayStore() {
        String id = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + id)));
        } catch (ActivityNotFoundException noStore) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + id)));
            } catch (ActivityNotFoundException ignored) {
                // Nothing can open it; the card has already closed.
            }
        }
    }

    /**
     * The feedback form: the player's own mail app opens with the message to the support
     * address, so they send it (and can get a reply) from their account. Nothing goes elsewhere.
     */
    private void sendFeedback(String message) {
        String text = message.trim();
        if (text.isEmpty()) return;
        if (text.length() > 2000) text = text.substring(0, 2000);
        String body = text + "\n\n— Spider Solitaire " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE
                + "), Android " + Build.VERSION.RELEASE;
        Uri uri = Uri.parse("mailto:" + SUPPORT_EMAIL + "?subject=" + Uri.encode("Spider Solitaire feedback")
                + "&body=" + Uri.encode(body));
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, uri));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, SUPPORT_EMAIL, Toast.LENGTH_LONG).show();
        }
    }

    private void onAdsConsentResolved(boolean canRequestAds) {
        if (adsController != null) adsController.onConsentResolved(canRequestAds);
    }

    private void handleBackRequest() {
        if (webView == null) {
            finishAfterTransition();
            return;
        }
        webView.evaluateJavascript(
                "window.freecellBack?window.freecellBack():false",
                value -> {
                    if (!"true".equals(value)) finishAfterTransition();
                });
    }

    /**
     * v1.3 data for the page, before its own script runs: the deal tiers (assets/deal_catalog.json,
     * numbers only) and the invented Daily Rivals (assets/rivals_roster.json). Without them the page
     * still deals from its fallback catalog and hides Daily Rivals.
     */
    private String dealCatalogScript() {
        return assetScript("deal_catalog.json", "__freecellDealCatalog")
                + assetScript("rivals_roster.json", "__freecellRivalsRoster");
    }

    private String assetScript(String asset, String global) {
        try {
            String json = new JSONObject(readAsset(asset)).toString();
            return "window." + global + "=" + json + ";";
        } catch (IOException | org.json.JSONException error) {
            Log.w("FreeCell", asset + " unavailable", error);
            return "";
        }
    }

    private String buildDocument(String fragment) {
        String prelude = "<script>"
                + "try{window.__freecellInitialState=JSON.parse(AndroidBridge.loadState()||'{}')}"
                + "catch(e){window.__freecellInitialState={}};"
                + "try{window.__freecellTutorialRequired=AndroidBridge.isFirstRunTutorialRequired()}"
                + "catch(e){window.__freecellTutorialRequired=false};"
                + dealCatalogScript()
                + "window.lucide={createIcons:function(){}};"
                + "</script>";
        String appCss = "<style>"
                + "html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#0c7c3d;}"
                + "#freecell-live-preview{width:100%;height:100%;padding:0!important;background:#0c7c3d;}"
                + "#freecell-live-preview .game-phone{width:100%!important;height:100%!important;border-radius:0!important;box-shadow:none!important;}"
                + "#freecell-live-preview .scorebar{height:calc(43px + env(safe-area-inset-top));padding-top:env(safe-area-inset-top);}"
                + "#freecell-live-preview .playfield{padding-bottom:calc(98px + env(safe-area-inset-bottom));}"
                + "#freecell-live-preview .column{height:calc(100vh - 228px - env(safe-area-inset-top) - env(safe-area-inset-bottom))!important;}"
                + "#freecell-live-preview .winning{bottom:calc(88px + env(safe-area-inset-bottom));}"
                + "#freecell-live-preview .toolbar{height:calc(89px + env(safe-area-inset-bottom));padding-bottom:calc(2px + env(safe-area-inset-bottom));}"
                + "#freecell-live-preview .modal-shade{padding-top:calc(17px + env(safe-area-inset-top));padding-bottom:calc(17px + env(safe-area-inset-bottom));}"
                + "#freecell-live-preview i[data-lucide]{display:inline-grid;place-items:center;font-style:normal;}"
                + "#freecell-live-preview i[data-lucide]::before{font-family:Arial,sans-serif;font-weight:700;line-height:1;}"
                + "#freecell-live-preview i[data-lucide=\"settings\"]::before{content:'⚙';}"
                + "#freecell-live-preview i[data-lucide=\"panels-top-left\"]::before{content:'▣';}"
                + "#freecell-live-preview i[data-lucide=\"plus\"]::before{content:'+';}"
                + "#freecell-live-preview i[data-lucide=\"lightbulb\"]::before{content:'♧';}"
                + "#freecell-live-preview i[data-lucide=\"undo-2\"]::before{content:'↶';}"
                + "#freecell-live-preview i[data-lucide=\"trophy\"]::before{content:'★';}"
                + "#freecell-live-preview i[data-lucide=\"check\"]::before{content:'✓';}"
                + "#freecell-live-preview i[data-lucide=\"crown\"]::before{content:'♛';}"
                + "#freecell-live-preview i[data-lucide=\"dices\"]::before{content:'◆';}"
                + "#freecell-live-preview i[data-lucide=\"list\"]::before{content:'≡';}"
                + "#freecell-live-preview i[data-lucide=\"lock\"]::before{content:'●';font-size:11px;}"
                + "#freecell-live-preview i[data-lucide=\"rotate-ccw\"]::before{content:'↻';}"
                + "#freecell-live-preview i[data-lucide=\"spade\"]::before{content:'♠';}"
                + "</style>";
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover\">"
                + "</head><body>" + prelude + fragment + appCss + "</body></html>";
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private final class AndroidBridge {
        private final SharedPreferences preferences;
        private final Vibrator vibrator;
        private long lastHaptic;

        AndroidBridge(Context context) {
            preferences = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        @JavascriptInterface public String loadState() {
            return preferences.getString(SAVED_STATE, "{}");
        }

        @JavascriptInterface public void saveState(String state) {
            if (state == null || state.length() > 500_000) return;
            preferences.edit().putString(SAVED_STATE, state).apply();
        }

        @JavascriptInterface public String loadCollection() {
            return preferences.getString(NUMBERED_COLLECTION, "");
        }

        @JavascriptInterface public void saveCollection(String collection) {
            if (collection == null || collection.length() > 200_000) return;
            preferences.edit().putString(NUMBERED_COLLECTION, collection).apply();
        }

        @JavascriptInterface public boolean isFirstRunTutorialRequired() {
            return !preferences.getBoolean(FIRST_TUTORIAL_COMPLETED, false);
        }

        @JavascriptInterface public void onFirstRunTutorialCompleted() {
            preferences.edit().putBoolean(FIRST_TUTORIAL_COMPLETED, true).apply();
            runOnUiThread(() -> {
                if (adsController != null) adsController.completeOnboarding();
            });
        }

        /**
         * {@code restart} is true when the player restarts the same deal; {@code afterPromo} when a
         * rating card or Remove Ads screen was shown in this game transition (no interstitial then);
         * {@code afterLoss} when the previous game was not won (moves or not) (see AdEligibility).
         */
        @JavascriptInterface public void onGameStarted(String mode, boolean restart, boolean afterPromo, boolean afterLoss) {
            if (adsController == null) return;
            runOnUiThread(() -> adsController.onGameStarted(mode == null ? "" : mode, restart, afterPromo, afterLoss));
        }

        @JavascriptInterface public int getSessionCount() {
            return ((SpiderApplication) getApplication()).getSessionCount();
        }

        /** Availability, Play's localized price and whether ads are removed (decided natively). */
        @JavascriptInterface public String getBillingState() {
            return removeAds == null ? "{}" : removeAds.toJson();
        }

        @JavascriptInterface public void startRemoveAdsPurchase() {
            runOnUiThread(() -> {
                if (removeAds != null) removeAds.purchase(MainActivity.this);
            });
        }

        /** A "double your coins" video is loaded and may be shown now. */
        @JavascriptInterface public boolean isRewardedReady() {
            AdsController controller = adsController;
            return controller != null && controller.rewardedReady();
        }

        /** Preload the rewarded video at a quiet moment (between games). */
        @JavascriptInterface public void prepareRewarded() {
            runOnUiThread(() -> {
                if (adsController != null) adsController.loadRewarded();
            });
        }

        /** The answer arrives in window.freecellRewardedResult(offerId, outcome). */
        @JavascriptInterface public void showRewarded(String offerId, String placement) {
            if (offerId == null || !offerId.matches("[a-z0-9_-]{1,40}")
                    || !REWARDED_PLACEMENTS.contains(placement)) return;
            runOnUiThread(() -> {
                if (adsController != null) adsController.showRewarded(offerId, placement);
                else onRewardedResult(offerId, "unavailable");
            });
        }

        /** Offers earned but not yet acknowledged by the page, as a JSON array of ids. */
        @JavascriptInterface public String getEarnedRewards() {
            AdsController controller = adsController;
            return controller == null ? "[]" : new JSONArray(controller.earnedRewards()).toString();
        }

        @JavascriptInterface public void acknowledgeReward(String offerId) {
            if (offerId == null) return;
            runOnUiThread(() -> {
                if (adsController != null) adsController.acknowledgeReward(offerId);
            });
        }

        @JavascriptInterface public void shareText(String text) {
            if (text == null || text.isEmpty() || text.length() > 1000) return;
            runOnUiThread(() -> MainActivity.this.shareText(text));
        }

        /** True when the image was accepted and the share sheet is opening; false means share text. */
        @JavascriptInterface public boolean shareImage(String image, String text) {
            if (text == null || text.length() > 1000) return false;
            return MainActivity.this.shareImage(image, text);
        }

        /** Reminder state and texts (in the app's language) from the page; plans the next reminder. */
        @JavascriptInterface public void setReminderState(String json) {
            if (json == null || json.length() > 4000) return;
            Reminders.update(getApplicationContext(), json);
        }

        @JavascriptInterface public boolean areNotificationsAllowed() {
            return Reminders.allowed(getApplicationContext());
        }

        @JavascriptInterface public void requestNotificationPermission() {
            runOnUiThread(MainActivity.this::requestNotificationPermission);
        }

        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(MainActivity.this::openNotificationSettings);
        }

        /** "daily" or "risk" when a reminder opened the app, once; otherwise "". */
        @JavascriptInterface public String consumeReminderOpen() {
            String type = openedFromReminder;
            openedFromReminder = "";
            return type;
        }

        /** Test builds only: show a reminder now. */
        @JavascriptInterface public boolean debugNotify(String type) {
            if (!BuildConfig.TEST_TOOLS) return false;
            return Reminders.post(getApplicationContext(), ReminderPlan.RISK.equals(type) ? ReminderPlan.RISK : ReminderPlan.DAILY);
        }

        @JavascriptInterface public void openPlayStore() {
            runOnUiThread(MainActivity.this::openPlayStore);
        }

        @JavascriptInterface public void sendFeedback(String message) {
            if (message == null) return;
            runOnUiThread(() -> sendFeedback(message));
        }

        /** Test builds only: how the fake store ends the next purchase, or forget the purchase. */
        @JavascriptInterface public void setFakeBillingOutcome(String outcome) {
            if (!BuildConfig.TEST_TOOLS || outcome == null) return;
            runOnUiThread(() -> {
                if (removeAds == null) return;
                if ("reset".equals(outcome)) removeAds.resetDebugPurchase();
                else removeAds.setDebugOutcome(outcome);
            });
        }

        /**
         * Validated game settings (economy, theme prices, Daily Challenge, unlock levels). The
         * page reads them once per launch, so a new Remote Config value never applies mid-game.
         */
        @JavascriptInterface public String getGameConfig() {
            String json = gameConfigJson;
            return json != null ? json : bundledGameConfigJson;
        }

        /** Developer tools in the page (DEV panel) run only when this is true: debug and qa builds. */
        @JavascriptInterface public boolean isDebugBuild() {
            return BuildConfig.TEST_TOOLS;
        }

        @JavascriptInterface public boolean isConsentPending() {
            return launchConsentPending;
        }

        @JavascriptInterface public boolean isPrivacyOptionsRequired() {
            return adsConsent != null && adsConsent.isPrivacyOptionsRequired();
        }

        @JavascriptInterface public void showPrivacyOptions() {
            runOnUiThread(() -> {
                if (adsConsent != null) {
                    adsConsent.showPrivacyOptions(MainActivity.this,
                            MainActivity.this::onAdsConsentResolved);
                }
            });
        }

        @JavascriptInterface public void trackEvent(String name, String parametersJson) {
            if (analyticsTracker != null) analyticsTracker.logEvent(name, parametersJson);
        }

        /** Analytics user properties from the page (AnalyticsTracker keeps only its list). */
        @JavascriptInterface public void setUserProperty(String name, String value) {
            if (analyticsTracker != null) analyticsTracker.setUserProperty(name, value);
        }

        /** A JavaScript error in the page: a non-fatal Crashlytics event (cleaned and capped). */
        @JavascriptInterface public void reportJsError(String message, int line) {
            if (crashReporter != null) crashReporter.reportJavaScriptError(message, line);
        }

        /** The current game's mode, difficulty tier and saved-state schema, as crash custom keys. */
        @JavascriptInterface public void setCrashContext(String mode, String tier, int schema) {
            if (crashReporter != null) crashReporter.setGameContext(mode, tier, schema);
        }

        /** Test builds only: crash the app on purpose, to check that Crashlytics receives it. */
        @JavascriptInterface public void debugCrash() {
            if (!BuildConfig.TEST_TOOLS) return;
            runOnUiThread(() -> {
                throw new RuntimeException("Test crash from the DEV tools (not a real error)");
            });
        }

        @JavascriptInterface public void setOrientation(boolean locked) {
            runOnUiThread(() -> setRequestedOrientation(locked
                    ? ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }

        @JavascriptInterface public void openExternal(String target) {
            if (target == null) return;
            boolean isPrivacy = target.equals(PRIVACY_POLICY_URL);
            boolean isContact = target.equals(SUPPORT_MAILTO);
            if (!isPrivacy && !isContact) return;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(isContact
                            ? Intent.ACTION_SENDTO : Intent.ACTION_VIEW, Uri.parse(target));
                    startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                    // No mail app (or browser): show the address so the player can still write.
                    Toast.makeText(MainActivity.this,
                            isContact ? SUPPORT_EMAIL : PRIVACY_POLICY_URL,
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        /**
         * Phone facts for the page's Auto "Reduce effects" (v1.4 Part 5): Android's low-RAM flag, the
         * app's memory class in MB and the Android version.
         */
        /** The phone's total RAM in MB (0 when unknown), for the page's "reduce effects" Auto choice. */
        private long totalRamMb(ActivityManager manager) {
            if (manager == null) return 0L;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return Math.max(0L, info.totalMem / (1024L * 1024L));
        }

        @JavascriptInterface public String getDeviceInfo() {
            ActivityManager manager = getSystemService(ActivityManager.class);
            try {
                return new JSONObject()
                        .put("lowRam", manager != null && manager.isLowRamDevice())
                        .put("memoryClass", manager == null ? 0 : manager.getMemoryClass())
                        .put("sdk", Build.VERSION.SDK_INT)
                        .put("totalRamMb", totalRamMb(manager))
                        .toString();
            } catch (org.json.JSONException impossible) {
                return "{}";
            }
        }

        /**
         * The page is playable (v1.4 Part 5): logs the time since onCreate with the page's own
         * timings (tag "FreeCellPerf") and tells Android the app is fully drawn, once per launch.
         */
        @JavascriptInterface public void reportStartup(String pageTimings) {
            long sinceCreate = SystemClock.uptimeMillis() - launchStartedAt;
            String timings = pageTimings == null ? "" : pageTimings.replaceAll("[^A-Za-z0-9 =.,_-]", "");
            Log.i("FreeCellPerf", "playable " + sinceCreate + " ms after onCreate; page " + timings.substring(0, Math.min(200, timings.length())));
            runOnUiThread(() -> {
                if (fullyDrawnReported || isDestroyed()) return;
                fullyDrawnReported = true;
                reportFullyDrawn();
            });
        }

        /** A language file (v1.4 Part 4), only one of ours (Languages.asset); "" otherwise. */
        @JavascriptInterface public String getTranslations(String code) {
            String asset = Languages.asset(code);
            if (asset == null) return "";
            try {
                return readAsset(asset);
            } catch (IOException error) {
                Log.w("FreeCell", asset + " unavailable", error);
                return "";
            }
        }

        /**
         * The app's own language from Android 13+ (Settings → Apps → Spider Solitaire → Language; "" when
         * none) and the device languages, as BCP 47 tags: the page picks its language from them.
         */
        @JavascriptInterface public String getLanguageInfo() {
            JSONArray device = new JSONArray();
            LocaleList locales = LocaleList.getDefault();
            for (int i = 0; i < locales.size(); i++) device.put(locales.get(i).toLanguageTag());
            String app = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LocaleList own = getSystemService(LocaleManager.class).getApplicationLocales();
                if (!own.isEmpty()) app = own.get(0).toLanguageTag();
            }
            try {
                return new JSONObject().put("app", app).put("device", device).toString();
            } catch (org.json.JSONException impossible) {
                return "{}";
            }
        }

        /** The language chosen in the game becomes the app's language in Android 13+ settings too. */
        @JavascriptInterface public void setAppLanguage(String tag) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || tag == null || !tag.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,2}")) return;
            runOnUiThread(() -> getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(tag)));
        }

        /**
         * Haptic feedback (v1.4 Part 3, see Haptics): Android's predefined tick, click and double
         * click from Android 10, short one-shots before; never while the system's touch feedback is
         * off, at most one every 40 ms (a win always plays).
         */
        @SuppressWarnings("deprecation")
        @JavascriptInterface public void haptic(String kind) {
            long[] timings = Haptics.pattern(kind);
            if (timings == null || vibrator == null || !vibrator.hasVibrator() || !systemHapticsOn()) return;
            long now = SystemClock.uptimeMillis();
            if (!Haptics.allowed(kind, now, lastHaptic)) return;
            lastHaptic = now;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? predefined(kind) : null;
                if (effect == null) effect = VibrationEffect.createWaveform(timings, -1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
                } else {
                    vibrator.vibrate(effect);
                }
            } else {
                vibrator.vibrate(timings, -1);
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private VibrationEffect predefined(String kind) {
            switch (kind) {
                case Haptics.TICK: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
                case Haptics.CLICK: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
                case Haptics.DOUBLE: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);
                case Haptics.REJECT: return VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);
                default: return null;
            }
        }

        private boolean systemHapticsOn() {
            return Settings.System.getInt(getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
        }

        @SuppressWarnings("deprecation")
        @JavascriptInterface public void vibrate(int milliseconds) {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            int duration = Math.max(1, Math.min(milliseconds, 200));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }
}
