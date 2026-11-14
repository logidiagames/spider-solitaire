package com.nexusplaylabss.freecell.config;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every JSON Remote Config parameter of the app: each one has a bundled default in assets, the
 * values are delivered once per launch (the ones downloaded during the previous launch), and a
 * missing or invalid value falls back to the bundled default. Callers validate fields with
 * {@link #intIn} and {@link #bool}.
 */
public final class RemoteJsonConfig {
    private static final String TAG = "RemoteJsonConfig";
    private static final long FETCH_INTERVAL_SECONDS = 12L * 60L * 60L;

    /** The raw values for one launch. */
    public static final class Values {
        private final Map<String, String> remote;
        private final Map<String, String> bundled;

        Values(Map<String, String> remote, Map<String, String> bundled) {
            this.remote = remote;
            this.bundled = bundled;
        }

        public String remote(String key) {
            String value = remote.get(key);
            return value == null ? bundled(key) : value;
        }

        public String bundled(String key) {
            return bundled.get(key);
        }

        /** The remote JSON object, or the bundled one when the remote value is not a JSON object. */
        public JSONObject object(String key) {
            return parseObject(remote(key), bundled(key));
        }
    }

    public interface Listener {
        void onValues(Values values);
    }

    private final Context context;
    private final Map<String, String> bundled = new LinkedHashMap<>();

    /** {@code parameters} maps each Remote Config key to its bundled default file in assets. */
    public RemoteJsonConfig(Context context, Map<String, String> parameters) throws IOException {
        this.context = context.getApplicationContext();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            bundled.put(parameter.getKey(), readAsset(parameter.getValue()));
        }
    }

    /** Bundled defaults only, for anything asked before the first delivery. */
    public Values bundledValues() {
        return new Values(Collections.emptyMap(), Collections.unmodifiableMap(bundled));
    }

    public void start(Listener listener) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "Firebase is not configured; using bundled configuration.");
            listener.onValues(bundledValues());
            return;
        }

        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                .build());
        remoteConfig.setDefaultsAsync(new HashMap<>(bundled));

        // Activate the values downloaded during the previous launch (a local step), then fetch in
        // the background for the next launch, so nothing changes in the middle of a game.
        remoteConfig.activate().addOnCompleteListener(task -> {
            Map<String, String> remote = new HashMap<>();
            for (String key : bundled.keySet()) remote.put(key, remoteConfig.getString(key));
            listener.onValues(new Values(remote, Collections.unmodifiableMap(bundled)));
            remoteConfig.fetch().addOnFailureListener(error ->
                    Log.w(TAG, "Remote Config fetch failed", error));
        });
    }

    public static JSONObject parseObject(String value, String bundledDefault) {
        try {
            return new JSONObject(value);
        } catch (JSONException | NullPointerException invalidRemoteValue) {
            try {
                return new JSONObject(bundledDefault);
            } catch (JSONException | NullPointerException impossible) {
                throw new IllegalStateException("Bundled configuration is invalid", impossible);
            }
        }
    }

    /** A number clamped to [min, max]; anything that is not a number gives {@code fallback}. */
    public static int intIn(JSONObject object, String key, int min, int max, int fallback) {
        Object value = object == null ? null : object.opt(key);
        if (!(value instanceof Number)) return fallback;
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) return fallback;
        return (int) Math.max(min, Math.min(max, Math.round(number)));
    }

    public static boolean bool(JSONObject object, String key, boolean fallback) {
        Object value = object == null ? null : object.opt(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
