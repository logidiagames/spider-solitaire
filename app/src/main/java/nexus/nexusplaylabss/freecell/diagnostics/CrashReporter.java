package com.nexusplaylabss.freecell.diagnostics;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Firebase Crashlytics. Native crashes are reported by the SDK itself; this class adds the
 * page's JavaScript errors as non-fatal events and the custom keys. Collection is switched on
 * or off by the manifest (off in debug builds), so nothing here needs to check the build type.
 */
public final class CrashReporter {
    private static final String TAG = "FreeCellCrash";
    public static final int MAX_MESSAGE_LENGTH = 300;
    public static final int MAX_REPORTS_PER_SESSION = 5;
    // Anything bracketed (a JSON object or array), and words that only appear in the saved state.
    private static final Pattern BRACKETED = Pattern.compile("\\{[^{}]*\\}|\\[[^\\[\\]]*\\]");
    private static final Pattern SAVED_STATE = Pattern.compile(
            "tableau|foundations|dailyHistory|paidKeys|starBest|coins\\W*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODE = Pattern.compile("[a-z_]{1,20}");

    private final FirebaseCrashlytics crashlytics;
    private final Set<String> reported = new HashSet<>();

    public CrashReporter(Context context, String appVersion) {
        FirebaseCrashlytics instance = null;
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) instance = FirebaseCrashlytics.getInstance();
        } catch (IllegalStateException error) {
            Log.w(TAG, "Crashlytics is unavailable.", error);
        }
        crashlytics = instance;
        if (crashlytics != null) crashlytics.setCustomKey("app_version", appVersion);
    }

    /** Game mode, difficulty tier and saved-state schema of the current game, as custom keys. */
    public void setGameContext(String mode, String tier, int schema) {
        if (crashlytics == null) return;
        crashlytics.setCustomKey("game_mode", cleanWord(mode));
        crashlytics.setCustomKey("difficulty_tier", cleanWord(tier));
        crashlytics.setCustomKey("state_schema", Math.max(0, Math.min(schema, 1000)));
    }

    /** A JavaScript error from the page, sent as a non-fatal event; at most a few per session. */
    public synchronized void reportJavaScriptError(String message, int line) {
        String clean = sanitize(message);
        if (crashlytics == null || reported.size() >= MAX_REPORTS_PER_SESSION || !reported.add(clean + "@" + line)) return;
        JavaScriptError error = new JavaScriptError(clean);
        error.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("freecell.html", "script", "freecell.html", Math.max(0, line))});
        crashlytics.recordException(error);
    }

    /**
     * Error text that is safe to send: one line, no bracketed data, nothing that looks like the
     * saved game, at most {@link #MAX_MESSAGE_LENGTH} characters.
     */
    public static String sanitize(String message) {
        if (message == null) return "(no message)";
        String text = message.replaceAll("\\s+", " ").trim();
        if (text.length() > 2000) text = text.substring(0, 2000);
        for (String previous = null; !text.equals(previous); ) {
            previous = text;
            text = BRACKETED.matcher(text).replaceAll("…");
        }
        if (SAVED_STATE.matcher(text).find()) return "(message withheld: it contained game data)";
        if (text.isEmpty()) return "(no message)";
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    private static String cleanWord(String value) {
        return value != null && MODE.matcher(value).matches() ? value : "";
    }

    /** The type that groups JavaScript errors in the Crashlytics console. */
    static final class JavaScriptError extends Exception {
        JavaScriptError(String message) {
            super(message);
        }
    }
}
