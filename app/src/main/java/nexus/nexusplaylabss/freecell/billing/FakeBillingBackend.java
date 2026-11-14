package com.nexusplaylabss.freecell.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Debug builds only: a pretend store with a sample price, so the Remove Ads screens can be tested
 * without Google Play. The developer tools choose how the next purchase ends.
 */
final class FakeBillingBackend implements BillingBackend {
    static final List<String> OUTCOMES = Arrays.asList("success", "pending", "cancel", "failure", "offline");
    private static final String PREFS = "freecell_fake_billing";
    private static final String OWNED = "owned";

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private String outcome = "success";

    FakeBillingBackend(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void setOutcome(String value) {
        if (OUTCOMES.contains(value)) outcome = value;
    }

    void resetPurchase() {
        preferences.edit().putBoolean(OWNED, false).apply();
        queryPurchases();
    }

    @Override public void start(Listener value) {
        listener = value;
        handler.postDelayed(() -> {
            listener.onAvailability(true, "ok");
            listener.onPrice("$5.99");
            queryPurchases();
        }, 300);
    }

    @Override public void launchPurchase(Activity activity) {
        handler.postDelayed(() -> {
            switch (outcome) {
                case "success":
                    preferences.edit().putBoolean(OWNED, true).apply();
                    listener.onPurchases(owned(false), true);
                    break;
                case "pending":
                    listener.onPurchases(Collections.singletonList(purchase(RemoveAdsEntitlement.STATE_PENDING, false)), true);
                    break;
                case "cancel":
                    listener.onResult("cancelled");
                    break;
                case "offline":
                    listener.onResult("offline");
                    break;
                default:
                    listener.onResult("failed");
            }
        }, 900);
    }

    @Override public void queryPurchases() {
        handler.postDelayed(() -> listener.onPurchases(owned(true), false), 400);
    }

    @Override public void acknowledge(String purchaseToken, Runnable done) {
        handler.post(done);
    }

    private List<RemoveAdsEntitlement.PurchaseInfo> owned(boolean acknowledged) {
        if (!preferences.getBoolean(OWNED, false)) return new ArrayList<>();
        return Collections.singletonList(purchase(RemoveAdsEntitlement.STATE_PURCHASED, acknowledged));
    }

    private static RemoveAdsEntitlement.PurchaseInfo purchase(int state, boolean acknowledged) {
        return new RemoveAdsEntitlement.PurchaseInfo(
                Collections.singletonList(RemoveAdsEntitlement.PRODUCT_ID), state, acknowledged, "fake-token");
    }
}
