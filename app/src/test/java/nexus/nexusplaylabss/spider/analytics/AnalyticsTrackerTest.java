package com.nexusplaylabss.spider.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Campaign goals never leave a test build; user properties keep to their list and Firebase's length. */
public class AnalyticsTrackerTest {
    @Test public void goalsAreSentOnlyOutsideTestBuilds() {
        for (String goal : new String[] {"intro_complete", "first_daily_win", "wins_3", "wins_10", "day2_return"}) {
            assertTrue(goal, AnalyticsTracker.sends(goal, false));
            assertFalse(goal, AnalyticsTracker.sends(goal, true));
        }
    }

    @Test public void otherEventsAreSentInEveryBuild() {
        assertTrue(AnalyticsTracker.sends("game_win", true));
        assertTrue(AnalyticsTracker.sends("autocomplete_used", false));
        assertTrue(AnalyticsTracker.sends("stats_reset", false));
        assertFalse(AnalyticsTracker.sends("made_up_event", false));
    }

    @Test public void userPropertiesKeepToTheirList() {
        assertEquals("7", AnalyticsTracker.userPropertyValue("player_level", "7"));
        assertEquals("yes", AnalyticsTracker.userPropertyValue("intro_complete", "yes"));
        assertEquals("no", AnalyticsTracker.userPropertyValue("ads_removed", "no"));
        assertEquals("pt-BR", AnalyticsTracker.userPropertyValue("app_language", "pt-BR"));
        assertNull(AnalyticsTracker.userPropertyValue("email", "someone@example.com"));
        assertNull(AnalyticsTracker.userPropertyValue("player_level", " "));
        assertEquals(36, AnalyticsTracker.userPropertyValue("app_language", "x".repeat(50)).length());
    }
}
