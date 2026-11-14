package com.nexusplaylabss.freecell.share;

import java.util.Arrays;

/**
 * Narrow checks for the Daily result image the page hands over to share: a PNG only, at most
 * {@link #MAX_BYTES}. Decoding itself is done by the caller (android.util.Base64).
 */
public final class SharedImage {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Base64 grows the data by 4/3; anything longer cannot be a valid image. */
    public static final int MAX_BASE64_CHARS = (MAX_BYTES / 3 + 1) * 4;
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private SharedImage() {
    }

    /** The Base64 payload of a PNG data URL (or plain Base64), or null when it is not acceptable. */
    public static String payload(String value) {
        if (value == null) return null;
        String data = value.startsWith(DATA_URL_PREFIX) ? value.substring(DATA_URL_PREFIX.length()) : value;
        if (data.isEmpty() || data.length() > MAX_BASE64_CHARS || data.startsWith("data:")) return null;
        return data;
    }

    public static boolean isPng(byte[] bytes) {
        return bytes != null && bytes.length > PNG_SIGNATURE.length && bytes.length <= MAX_BYTES
                && Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE);
    }
}
