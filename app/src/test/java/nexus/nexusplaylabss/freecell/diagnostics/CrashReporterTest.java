package com.nexusplaylabss.freecell.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashReporterTest {
    @Test public void keepsAPlainErrorMessage() {
        assertEquals("TypeError: Cannot read properties of null (reading 'click')",
                CrashReporter.sanitize("TypeError: Cannot read properties of null (reading 'click')"));
    }

    @Test public void foldsWhitespaceAndCapsTheLength() {
        assertEquals("a b", CrashReporter.sanitize("  a \n\t b "));
        String longText = "x".repeat(1000);
        assertEquals(CrashReporter.MAX_MESSAGE_LENGTH, CrashReporter.sanitize(longText).length());
    }

    @Test public void dropsBracketedData() {
        String clean = CrashReporter.sanitize("Bad value {\"a\":{\"b\":1}} in [1,[2,3]] here");
        assertFalse(clean.contains("{"));
        assertFalse(clean.contains("["));
        assertTrue(clean.startsWith("Bad value"));
    }

    @Test public void withholdsAnythingThatLooksLikeTheSavedGame() {
        assertEquals("(message withheld: it contained game data)",
                CrashReporter.sanitize("SyntaxError near version:2,coins: 450,tableau"));
        assertEquals("(message withheld: it contained game data)",
                CrashReporter.sanitize("Unexpected token in dailyHistory"));
    }

    @Test public void neverReturnsAnEmptyMessage() {
        assertEquals("(no message)", CrashReporter.sanitize(null));
        assertEquals("(no message)", CrashReporter.sanitize("   "));
    }
}
