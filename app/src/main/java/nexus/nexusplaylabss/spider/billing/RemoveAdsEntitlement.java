package com.nexusplaylabss.spider.billing;

import com.android.billingclient.api.BillingClient;

import java.util.ArrayList;
import java.util.List;

/** Pure rules for the "remove_ads" non-consumable product. */
public final class RemoveAdsEntitlement {
    public static final String PRODUCT_ID = "remove_ads";
    public static final int STATE_PURCHASED = 1;
    public static final int STATE_PENDING = 2;

    /** What the app needs from a Play purchase. */
    public static final class PurchaseInfo {
        final List<String> products;
        final int state;
        final boolean acknowledged;
        final String token;

        public PurchaseInfo(List<String> products, int state, boolean acknowledged, String token) {
            this.products = products == null ? new ArrayList<>() : products;
            this.state = state;
            this.acknowledged = acknowledged;
            this.token = token == null ? "" : token;
        }
    }

    public final boolean owned;
    public final boolean pending;
    /** Purchases that must be acknowledged, or Google refunds them after three days. */
    public final List<String> tokensToAcknowledge;

    private RemoveAdsEntitlement(boolean owned, boolean pending, List<String> tokensToAcknowledge) {
        this.owned = owned;
        this.pending = pending;
        this.tokensToAcknowledge = tokensToAcknowledge;
    }

    public static RemoveAdsEntitlement from(List<PurchaseInfo> purchases) {
        boolean owned = false;
        boolean pending = false;
        List<String> toAcknowledge = new ArrayList<>();
        if (purchases != null) {
            for (PurchaseInfo purchase : purchases) {
                if (!purchase.products.contains(PRODUCT_ID)) continue;
                if (purchase.state == STATE_PURCHASED) {
                    owned = true;
                    if (!purchase.acknowledged && !purchase.token.isEmpty()) {
                        toAcknowledge.add(purchase.token);
                    }
                } else if (purchase.state == STATE_PENDING) {
                    pending = true;
                }
            }
        }
        return new RemoveAdsEntitlement(owned, pending && !owned, toAcknowledge);
    }

    /** A Billing response code as one of the statuses the page explains to the player. */
    public static String statusFor(int responseCode) {
        switch (responseCode) {
            case BillingClient.BillingResponseCode.OK:
                return "ok";
            case BillingClient.BillingResponseCode.USER_CANCELED:
                return "cancelled";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                return "already_owned";
            case BillingClient.BillingResponseCode.NETWORK_ERROR:
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED:
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT:
                return "offline";
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE:
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED:
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE:
                return "unavailable";
            default:
                return "failed";
        }
    }
}
