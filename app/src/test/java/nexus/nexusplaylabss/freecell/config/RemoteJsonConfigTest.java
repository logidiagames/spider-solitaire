package com.nexusplaylabss.freecell.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class RemoteJsonConfigTest {
    @Test public void intInClampsAndRejectsNonNumbers() throws Exception {
        JSONObject json = new JSONObject("{\"a\":7.6,\"b\":-9,\"c\":\"5\",\"d\":true}");
        assertEquals(8, RemoteJsonConfig.intIn(json, "a", 0, 10, 1));
        assertEquals(0, RemoteJsonConfig.intIn(json, "b", 0, 10, 1));
        assertEquals(1, RemoteJsonConfig.intIn(json, "c", 0, 10, 1));
        assertEquals(1, RemoteJsonConfig.intIn(json, "d", 0, 10, 1));
        assertEquals(1, RemoteJsonConfig.intIn(json, "missing", 0, 10, 1));
    }

    @Test public void boolOnlyAcceptsBooleans() throws Exception {
        JSONObject json = new JSONObject("{\"on\":false,\"text\":\"true\"}");
        assertFalse(RemoteJsonConfig.bool(json, "on", true));
        assertTrue(RemoteJsonConfig.bool(json, "text", true));
    }

    @Test public void parseObjectFallsBackOnInvalidOrMissingValues() {
        assertEquals(1, RemoteJsonConfig.parseObject("[1,2]", "{\"x\":1}").optInt("x"));
        assertEquals(1, RemoteJsonConfig.parseObject(null, "{\"x\":1}").optInt("x"));
        assertEquals(2, RemoteJsonConfig.parseObject("{\"x\":2}", "{\"x\":1}").optInt("x"));
    }
}
