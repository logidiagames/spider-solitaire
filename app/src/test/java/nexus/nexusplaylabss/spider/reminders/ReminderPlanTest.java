package com.nexusplaylabss.spider.reminders;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public final class ReminderPlanTest {
    private static Calendar at(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance();
        value.clear();
        value.set(year, month - 1, day, hour, minute);
        return value;
    }

    @Test public void oneReminderADayAndNoneOnceTodaysDailyIsWon() {
        ReminderPlan plan = new ReminderPlan(true, 19, 20, 3, 1, "2026-12-01", "");
        assertEquals(ReminderPlan.DAILY, plan.typeFor("2026-12-03", "2026-12-02"));
        assertEquals(ReminderPlan.NONE, plan.typeFor("2026-12-01", "2026-11-30"));
        ReminderPlan notified = new ReminderPlan(true, 19, 20, 3, 1, "2026-12-01", "2026-12-03");
        assertEquals(ReminderPlan.NONE, notified.typeFor("2026-12-03", "2026-12-02"));
        assertEquals(ReminderPlan.NONE, new ReminderPlan(false, 19, 20, 3, 9, "", "").typeFor("2026-12-03", "2026-12-02"));
    }

    @Test public void streakAtRiskOnlyWithALongEnoughStreakWonYesterday() {
        ReminderPlan plan = new ReminderPlan(true, 19, 20, 3, 3, "2026-12-02", "");
        assertEquals(ReminderPlan.RISK, plan.typeFor("2026-12-03", "2026-12-02"));
        assertEquals(20, plan.hourFor(ReminderPlan.RISK));
        assertEquals(ReminderPlan.DAILY, new ReminderPlan(true, 19, 20, 3, 2, "2026-12-02", "").typeFor("2026-12-03", "2026-12-02"));
        assertEquals(21, new ReminderPlan(true, 21, 20, 3, 3, "2026-12-02", "").hourFor(ReminderPlan.RISK));
    }

    @Test public void nextRunIsTodaysSlotOrTomorrowsAfterAWin() {
        ReminderPlan plan = new ReminderPlan(true, 19, 20, 3, 0, "", "");
        assertEquals(at(2026, 12, 3, 19, 0).getTimeInMillis(), plan.nextRun(at(2026, 12, 3, 10, 0)));
        assertEquals(at(2026, 12, 4, 19, 0).getTimeInMillis(), plan.nextRun(at(2026, 12, 3, 19, 30)));
        ReminderPlan won = new ReminderPlan(true, 19, 20, 3, 4, "2026-12-03", "");
        assertEquals(at(2026, 12, 4, 20, 0).getTimeInMillis(), won.nextRun(at(2026, 12, 3, 9, 0)));
        assertEquals(-1, new ReminderPlan(false, 19, 20, 3, 0, "", "").nextRun(at(2026, 12, 3, 9, 0)));
    }
}
