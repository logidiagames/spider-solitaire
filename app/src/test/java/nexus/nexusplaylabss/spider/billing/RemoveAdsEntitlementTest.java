package com.nexusplaylabss.spider.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.billingclient.api.BillingClient;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class RemoveAdsEntitlementTest {
    private static RemoveAdsEntitlement.PurchaseInfo purchase(String product, int state, boolean acknowledged, String token) {
        return new RemoveAdsEntitlement.PurchaseInfo(Collections.singletonList(product), state, acknowledged, token);
    }

    @Test public void purchasedProductRemovesAdsAndNeedsAcknowledgementOnce() {
        RemoveAdsEntitlement entitlement = RemoveAdsEntitlement.from(Collections.singletonList(
                purchase("remove_ads", RemoveAdsEntitlement.STATE_PURCHASED, false, "t1")));
        assertTrue(entitlement.owned);
        assertFalse(entitlement.pending);
        assertEquals(Collections.singletonList("t1"), entitlement.tokensToAcknowledge);
        assertTrue(RemoveAdsEntitlement.from(Collections.singletonList(
                purchase("remove_ads", RemoveAdsEntitlement.STATE_PURCHASED, true, "t1"))).tokensToAcknowledge.isEmpty());
    }

    @Test public void pendingPaymentDoesNotRemoveAds() {
        RemoveAdsEntitlement entitlement = RemoveAdsEntitlement.from(Collections.singletonList(
                purchase("remove_ads", RemoveAdsEntitlement.STATE_PENDING, false, "t2")));
        assertFalse(entitlement.owned);
        assertTrue(entitlement.pending);
        assertTrue(entitlement.tokensToAcknowledge.isEmpty());
    }

    @Test public void otherProductsAndEmptyListsChangeNothing() {
        assertFalse(RemoveAdsEntitlement.from(Collections.singletonList(
                purchase("coins_pack", RemoveAdsEntitlement.STATE_PURCHASED, false, "t3"))).owned);
        assertFalse(RemoveAdsEntitlement.from(null).owned);
        assertTrue(RemoveAdsEntitlement.from(Arrays.asList(
                purchase("remove_ads", RemoveAdsEntitlement.STATE_PENDING, false, "a"),
                purchase("remove_ads", RemoveAdsEntitlement.STATE_PURCHASED, true, "b"))).owned);
    }

    @Test public void responseCodesBecomeFriendlyStatuses() {
        assertEquals("cancelled", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.USER_CANCELED));
        assertEquals("offline", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.NETWORK_ERROR));
        assertEquals("already_owned", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED));
        assertEquals("unavailable", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE));
        assertEquals("failed", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.DEVELOPER_ERROR));
        assertEquals("ok", RemoveAdsEntitlement.statusFor(BillingClient.BillingResponseCode.OK));
    }
}
