package com.nexusplaylabss.spider.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Owns the "ads removed" entitlement. Only this class decides it, from Play purchases; the page
 * can ask to buy but never grants anything. Purchases are queried at every launch, so a
 * reinstall gets the purchase back by itself; the last answer is cached for offline launches.
 */
public final class RemoveAdsManager {
    private static final String PREFS = "freecell_billing";
    private static final String ADS_REMOVED = "ads_removed";

    public interface Listener {
        /** {@code event}: "state", "purchase_completed" or "purchase_failed". */
        void onChange(RemoveAdsManager manager, String event);
    }

    private final SharedPreferences preferences;
    private final BillingBackend backend;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean available;
    private String price;
    private boolean adsRemoved;
    private boolean pending;
    private boolean busy;
    private String status = "";

    public RemoveAdsManager(Context context, boolean debugStore, Listener listener) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        backend = debugStore ? new FakeBillingBackend(context) : new PlayBillingBackend(context);
        this.listener = listener;
        adsRemoved = preferences.getBoolean(ADS_REMOVED, false);
    }

    public static boolean cachedAdsRemoved(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ADS_REMOVED, false);
    }

    public boolean adsRemoved() {
        return adsRemoved;
    }

    public void start() {
        backend.start(new BillingBackend.Listener() {
            @Override public void onAvailability(boolean value, String reason) {
                main.post(() -> {
                    available = value;
                    if (!value) status = reason;
                    listener.onChange(RemoveAdsManager.this, "state");
                });
            }

            @Override public void onPrice(String formattedPrice) {
                main.post(() -> {
                    price = formattedPrice;
                    listener.onChange(RemoveAdsManager.this, "state");
                });
            }

            @Override public void onPurchases(List<RemoveAdsEntitlement.PurchaseInfo> purchases, boolean fromFlow) {
                main.post(() -> apply(RemoveAdsEntitlement.from(purchases), fromFlow));
            }

            @Override public void onResult(String result) {
                main.post(() -> {
                    boolean wasBusy = busy;
                    busy = false;
                    status = result;
                    listener.onChange(RemoveAdsManager.this, wasBusy ? "purchase_failed" : "state");
                });
            }
        });
    }

    public void purchase(Activity activity) {
        if (adsRemoved || busy) return;
        busy = true;
        status = "";
        listener.onChange(this, "state");
        backend.launchPurchase(activity);
    }

    /** Debug builds only: how the fake store ends the next purchase, and forgetting it. */
    public void setDebugOutcome(String outcome) {
        if (backend instanceof FakeBillingBackend) ((FakeBillingBackend) backend).setOutcome(outcome);
    }

    public void resetDebugPurchase() {
        if (backend instanceof FakeBillingBackend) ((FakeBillingBackend) backend).resetPurchase();
    }

    private void apply(RemoveAdsEntitlement entitlement, boolean fromFlow) {
        boolean wasRemoved = adsRemoved;
        boolean wasBusy = busy;
        busy = false;
        adsRemoved = entitlement.owned;
        pending = entitlement.pending;
        preferences.edit().putBoolean(ADS_REMOVED, adsRemoved).apply();
        for (String token : entitlement.tokensToAcknowledge) backend.acknowledge(token, () -> {});
        String event = "state";
        if (adsRemoved && !wasRemoved && (fromFlow || wasBusy)) {
            status = "ok";
            event = "purchase_completed";
        } else if (pending) {
            status = "pending";
        }
        listener.onChange(this, event);
    }

    /** The state the page shows: availability, Play's localized price, and the entitlement. */
    public String toJson() {
        try {
            return new JSONObject()
                    .put("available", available)
                    .put("price", price == null ? JSONObject.NULL : price)
                    .put("adsRemoved", adsRemoved)
                    .put("pending", pending)
                    .put("busy", busy)
                    .put("status", status)
                    .toString();
        } catch (JSONException impossible) {
            return "{}";
        }
    }
}
