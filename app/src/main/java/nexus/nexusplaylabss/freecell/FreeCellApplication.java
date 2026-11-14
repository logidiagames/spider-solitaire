package com.nexusplaylabss.freecell;

import android.app.Application;
import android.content.SharedPreferences;

public final class FreeCellApplication extends Application {
    public static final String AD_PREFS = "freecell_ad_state";
    public static final String SESSION_COUNT = "session_count";

    private int sessionCount;

    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = getSharedPreferences(AD_PREFS, MODE_PRIVATE);
        sessionCount = Math.max(0, preferences.getInt(SESSION_COUNT, 0)) + 1;
        preferences.edit().putInt(SESSION_COUNT, sessionCount).apply();
    }

    public int getSessionCount() {
        return sessionCount;
    }
}
