package com.nexusplaylabss.spider.billing;

import android.app.Activity;
import android.content.Context;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Google Play Billing for the one non-consumable product. */
final class PlayBillingBackend implements BillingBackend, PurchasesUpdatedListener {
    private final BillingClient client;
    private Listener listener;
    private ProductDetails product;

    PlayBillingBackend(Context context) {
        client = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    @Override public void start(Listener value) {
        listener = value;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                boolean ok = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                listener.onAvailability(ok, RemoveAdsEntitlement.statusFor(result.getResponseCode()));
                if (!ok) return;
                queryProduct();
                queryPurchases();
            }

            @Override public void onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
            }
        });
    }

    private void queryProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(RemoveAdsEntitlement.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        client.queryProductDetailsAsync(params, (result, details) -> {
            List<ProductDetails> list = details == null ? null : details.getProductDetailsList();
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null || list.isEmpty()) {
                product = null;
                listener.onPrice(null);
                return;
            }
            product = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
            listener.onPrice(offer == null ? null : offer.getFormattedPrice());
        });
    }

    @Override public void queryPurchases() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        listener.onPurchases(convert(purchases), false);
                    } else {
                        listener.onResult(RemoveAdsEntitlement.statusFor(result.getResponseCode()));
                    }
                });
    }

    @Override public void launchPurchase(Activity activity) {
        if (product == null) {
            listener.onResult("unavailable");
            return;
        }
        BillingFlowParams.ProductDetailsParams.Builder item =
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product);
        ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
        if (offer != null && offer.getOfferToken() != null) item.setOfferToken(offer.getOfferToken());
        BillingResult result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(item.build())).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            listener.onResult(RemoveAdsEntitlement.statusFor(result.getResponseCode()));
        }
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            listener.onPurchases(convert(purchases), true);
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryPurchases();
        } else {
            listener.onResult(RemoveAdsEntitlement.statusFor(code));
        }
    }

    @Override public void acknowledge(String purchaseToken, Runnable done) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken).build(), result -> done.run());
    }

    private static List<RemoveAdsEntitlement.PurchaseInfo> convert(List<Purchase> purchases) {
        List<RemoveAdsEntitlement.PurchaseInfo> list = new ArrayList<>();
        if (purchases == null) return list;
        for (Purchase purchase : purchases) {
            int state = purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                    ? RemoveAdsEntitlement.STATE_PURCHASED
                    : purchase.getPurchaseState() == Purchase.PurchaseState.PENDING
                            ? RemoveAdsEntitlement.STATE_PENDING : 0;
            list.add(new RemoveAdsEntitlement.PurchaseInfo(purchase.getProducts(), state,
                    purchase.isAcknowledged(), purchase.getPurchaseToken()));
        }
        return list;
    }
}
