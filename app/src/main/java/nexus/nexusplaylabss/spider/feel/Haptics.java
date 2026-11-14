package com.nexusplaylabss.spider.feel;

/**
 * The page's haptic feedback (v1.4 Part 3): a light tick when a card is placed, a firmer click on
 * the foundations, a double pulse when a suit is complete, a short pattern for a win, and a buzz
 * for a move that is not allowed. Android's predefined effects play them from Android 10; older
 * phones get these short timings (off, on, off, on… in ms, every pulse 10–35 ms).
 */
public final class Haptics {
    public static final String TICK = "tick";
    public static final String CLICK = "click";
    public static final String DOUBLE = "double";
    public static final String WIN = "win";
    public static final String REJECT = "reject";
    /** The shortest gap between two effects; a win pattern is never dropped. */
    public static final long MIN_GAP_MS = 40;

    private Haptics() {}

    /** Timings for a kind, or null for an unknown kind (nothing plays). */
    public static long[] pattern(String kind) {
        if (kind == null) return null;
        switch (kind) {
            case TICK: return new long[] {0, 10};
            case CLICK: return new long[] {0, 20};
            case DOUBLE: return new long[] {0, 15, 70, 15};
            case WIN: return new long[] {0, 20, 90, 20, 90, 35};
            case REJECT: return new long[] {0, 30};
            default: return null;
        }
    }

    /** Whether an effect may play now, given when the last one played. */
    public static boolean allowed(String kind, long now, long last) {
        return WIN.equals(kind) || now - last >= MIN_GAP_MS;
    }
}
