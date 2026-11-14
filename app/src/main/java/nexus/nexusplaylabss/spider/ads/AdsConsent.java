package com.nexusplaylabss.spider.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;
import com.nexusplaylabss.spider.BuildConfig;

/**
 * Google's User Messaging Platform consent flow.
 *
 * Ads may only be requested once this has said so: the consent information is refreshed on every
 * launch, and the form is shown only where the user's region requires an answer. Where it is
 * required, the privacy options form lets the user change that answer later from Settings.
 */
public final class AdsConsent {
    private static final String TAG = "FreeCellConsent";

    public interface Listener {
        void onConsentResolved(boolean canRequestAds);
    }

    private final ConsentInformation information;

    public AdsConsent(Context context) {
        information = UserMessagingPlatform.getConsentInformation(context);
    }

    /** True when an answer given in an earlier session already allows ads. */
    public boolean canRequestAds() {
        return information.canRequestAds();
    }

    public boolean isPrivacyOptionsRequired() {
        return information.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void gather(Activity activity, Listener listener) {
        ConsentRequestParameters.Builder builder = new ConsentRequestParameters.Builder();
        ConsentDebugSettings debugSettings = debugSettings(activity);
        if (debugSettings != null) builder.setConsentDebugSettings(debugSettings);
        ConsentRequestParameters parameters = builder.build();
        information.requestConsentInfoUpdate(
                activity,
                parameters,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    logFormError("Consent form", formError);
                    listener.onConsentResolved(information.canRequestAds());
                }),
                requestError -> {
                    // Offline or unreachable: an answer stored by a previous session still holds.
                    logFormError("Consent info update", requestError);
                    listener.onConsentResolved(information.canRequestAds());
                });
    }

    public void showPrivacyOptions(Activity activity, Listener listener) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            logFormError("Privacy options form", formError);
            listener.onConsentResolved(information.canRequestAds());
        });
    }

    /**
     * Debug builds only: the test devices listed in BuildConfig.UMP_TEST_DEVICE_IDS are treated
     * as being in the EEA, so the form and the privacy options can be tested from anywhere.
     */
    private static ConsentDebugSettings debugSettings(Context context) {
        if (!BuildConfig.DEBUG || BuildConfig.UMP_TEST_DEVICE_IDS.isEmpty()) return null;
        ConsentDebugSettings.Builder builder = new ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA);
        for (String id : BuildConfig.UMP_TEST_DEVICE_IDS.split(",")) {
            builder.addTestDeviceHashedId(id);
        }
        Log.i(TAG, "UMP debug geography EEA for test devices: " + BuildConfig.UMP_TEST_DEVICE_IDS);
        return builder.build();
    }

    private static void logFormError(String step, FormError error) {
        if (error == null) return;
        Log.w(TAG, step + " failed (" + error.getErrorCode() + "): " + error.getMessage());
    }
}
