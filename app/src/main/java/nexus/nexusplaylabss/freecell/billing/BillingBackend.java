package com.nexusplaylabss.freecell.billing;

import android.app.Activity;

import java.util.List;

/** Google Play Billing in release builds; a fake store in debug builds. */
interface BillingBackend {
    interface Listener {
        /** Store reachability; {@code status} explains why it is not available. */
        void onAvailability(boolean available, String status);

        /** The localized price of {@link RemoveAdsEntitlement#PRODUCT_ID}, or null. */
        void onPrice(String formattedPrice);

        /** Purchases from a query ({@code fromFlow} false) or from the purchase flow (true). */
        void onPurchases(List<RemoveAdsEntitlement.PurchaseInfo> purchases, boolean fromFlow);

        /** A purchase flow or query that ended without purchases. */
        void onResult(String status);
    }

    void start(Listener listener);

    void launchPurchase(Activity activity);

    void queryPurchases();

    void acknowledge(String purchaseToken, Runnable done);
}
