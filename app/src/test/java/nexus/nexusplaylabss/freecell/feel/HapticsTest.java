package com.nexusplaylabss.freecell.feel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HapticsTest {
    @Test public void everyKnownKindIsShortAndUnknownKindsPlayNothing() {
        for (String kind : new String[] {Haptics.TICK, Haptics.CLICK, Haptics.DOUBLE, Haptics.WIN, Haptics.REJECT}) {
            long[] timings = Haptics.pattern(kind);
            for (int i = 1; i < timings.length; i += 2) {
                assertTrue(kind, timings[i] >= 10 && timings[i] <= 35);
            }
        }
        assertArrayEquals(new long[] {0, 15, 70, 15}, Haptics.pattern(Haptics.DOUBLE));
        assertNull(Haptics.pattern("rumble"));
        assertNull(Haptics.pattern(null));
    }

    @Test public void effectsAreRateLimitedExceptTheWin() {
        assertFalse(Haptics.allowed(Haptics.CLICK, 1020, 1000));
        assertTrue(Haptics.allowed(Haptics.CLICK, 1040, 1000));
        assertTrue(Haptics.allowed(Haptics.WIN, 1001, 1000));
    }
}
