package com.nexusplaylabss.freecell.reminders;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import com.nexusplaylabss.freecell.MainActivity;
import com.nexusplaylabss.freecell.R;

/**
 * Daily Challenge reminders: local only, through WorkManager (no exact alarms). The page sends
 * its state and texts (in the app's language) with {@link #update}; one unique job always waits
 * for the next slot.
 */
public final class Reminders {
    public static final String EXTRA_TYPE = "freecell_reminder";
    private static final String PREFS = "freecell_reminders";
    private static final String STATE = "state";
    private static final String LAST_NOTIFIED = "last_notified";
    private static final String CHANNEL = "daily_reminders";
    private static final String WORK = "daily_reminder";
    private static final int NOTIFICATION_ID = 1001;

    private Reminders() {
    }

    /** New state from the page: store it, rename the channel to its language, plan again. */
    public static void update(Context context, String json) {
        JSONObject state;
        try {
            state = new JSONObject(json);
        } catch (JSONException invalid) {
            return;
        }
        prefs(context).edit().putString(STATE, state.toString()).apply();
        ensureChannel(context, texts(state).optString("channel", "Daily reminders"));
        Calendar now = Calendar.getInstance();
        // Today's Daily is won, or reminders are off: a reminder still on screen is out of date.
        if (!state.optBoolean("enabled") || ReminderPlan.dateKey(now).equals(state.optString("lastDailyWin"))) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        }
        schedule(context);
    }

    public static void schedule(Context context) {
        WorkManager work = WorkManager.getInstance(context);
        Calendar now = Calendar.getInstance();
        long next = plan(context).nextRun(now);
        if (next < 0) {
            work.cancelUniqueWork(WORK);
            return;
        }
        work.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ReminderWorker.class)
                        .setInitialDelay(Math.max(0L, next - now.getTimeInMillis()), TimeUnit.MILLISECONDS)
                        .build());
    }

    static void fire(Context context) {
        ReminderPlan plan = plan(context);
        Calendar now = Calendar.getInstance();
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        String type = plan.typeFor(ReminderPlan.dateKey(now), ReminderPlan.dateKey(yesterday));
        if (!ReminderPlan.NONE.equals(type) && now.get(Calendar.HOUR_OF_DAY) >= plan.hourFor(type)
                && post(context, type)) {
            prefs(context).edit().putString(LAST_NOTIFIED, ReminderPlan.dateKey(now)).apply();
        }
        schedule(context);
    }

    /** Shows a reminder now (also used by the debug tools). False when notifications are not allowed. */
    public static boolean post(Context context, String type) {
        if (!allowed(context)) return false;
        JSONObject state = state(context);
        JSONObject texts = texts(state);
        ensureChannel(context, texts.optString("channel", "Daily reminders"));
        boolean risk = ReminderPlan.RISK.equals(type);
        String title = texts.optString(risk ? "riskTitle" : "dailyTitle", "FreeCell");
        String body = texts.optString(risk ? "riskBody" : "dailyBody", "")
                .replace("{number}", String.valueOf(Math.max(0, state.optInt("streak", 0))));
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TYPE, risk ? ReminderPlan.RISK : ReminderPlan.DAILY);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(11, 100, 48))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
            return true;
        } catch (SecurityException notAllowed) {
            return false;
        }
    }

    /** The app may post: notifications are on for it and, on Android 13+, the permission is granted. */
    public static boolean allowed(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void ensureChannel(Context context, String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                name == null || name.isEmpty() ? "Daily reminders" : name, NotificationManager.IMPORTANCE_DEFAULT));
    }

    private static ReminderPlan plan(Context context) {
        return ReminderPlan.fromJson(state(context), prefs(context).getString(LAST_NOTIFIED, ""));
    }

    private static JSONObject state(Context context) {
        try {
            return new JSONObject(prefs(context).getString(STATE, "{}"));
        } catch (JSONException invalid) {
            return new JSONObject();
        }
    }

    private static JSONObject texts(JSONObject state) {
        JSONObject texts = state.optJSONObject("texts");
        return texts == null ? new JSONObject() : texts;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
