package com.nexusplaylabss.freecell.reminders;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * What the page last told us about Daily reminders, and the rules built on it: at most one
 * notification a day, none once today's Daily is won, a streak-at-risk message instead of the
 * plain reminder when a long enough streak would end today.
 */
public final class ReminderPlan {
    public static final String NONE = "none";
    public static final String DAILY = "daily";
    public static final String RISK = "risk";

    public final boolean enabled;
    public final int hour;
    public final int riskHour;
    public final int minStreak;
    public final int streak;
    /** YYYY-MM-DD of the last Daily won on its own date, or "". */
    public final String lastDailyWin;
    /** YYYY-MM-DD of the last notification shown, or "". */
    public final String lastNotified;

    public ReminderPlan(boolean enabled, int hour, int riskHour, int minStreak, int streak,
                        String lastDailyWin, String lastNotified) {
        this.enabled = enabled;
        this.hour = clamp(hour, 0, 23);
        this.riskHour = clamp(riskHour, 0, 23);
        this.minStreak = Math.max(1, minStreak);
        this.streak = Math.max(0, streak);
        this.lastDailyWin = lastDailyWin == null ? "" : lastDailyWin;
        this.lastNotified = lastNotified == null ? "" : lastNotified;
    }

    public static ReminderPlan fromJson(JSONObject value, String lastNotified) {
        if (value == null) return new ReminderPlan(false, 19, 20, 3, 0, "", lastNotified);
        String win = value.optString("lastDailyWin", "");
        return new ReminderPlan(value.optBoolean("enabled", false), value.optInt("hour", 19),
                value.optInt("riskHour", 20), value.optInt("minStreak", 3), value.optInt("streak", 0),
                win.matches("\\d{4}-\\d{2}-\\d{2}") ? win : "", lastNotified);
    }

    /** The notification due on {@code day} (yesterday being {@code previousDay}). */
    public String typeFor(String day, String previousDay) {
        if (!enabled || day.equals(lastDailyWin) || day.equals(lastNotified)) return NONE;
        return streak >= minStreak && previousDay.equals(lastDailyWin) ? RISK : DAILY;
    }

    /** The streak warning comes in the evening, never before the player's own reminder hour. */
    public int hourFor(String type) {
        return RISK.equals(type) ? Math.max(hour, riskHour) : hour;
    }

    /** Epoch millis of the next slot after {@code now}, or -1 when reminders are off. */
    public long nextRun(Calendar now) {
        if (!enabled) return -1;
        for (int offset = 0; offset < 3; offset++) {
            Calendar day = (Calendar) now.clone();
            day.add(Calendar.DAY_OF_MONTH, offset);
            Calendar before = (Calendar) day.clone();
            before.add(Calendar.DAY_OF_MONTH, -1);
            String type = typeFor(dateKey(day), dateKey(before));
            if (NONE.equals(type)) continue;
            day.set(Calendar.HOUR_OF_DAY, hourFor(type));
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            if (day.getTimeInMillis() > now.getTimeInMillis()) return day.getTimeInMillis();
        }
        return -1;
    }

    public static String dateKey(Calendar day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", day.get(Calendar.YEAR),
                day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
