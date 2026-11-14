package com.nexusplaylabss.freecell.i18n;

import java.util.Arrays;
import java.util.List;

/**
 * The game's languages (v1.4 Part 4), the same list and order as FreeCellCore.LANGUAGES in the page.
 * English is built into the page; every other language is the asset {@code i18n/<code>.json}.
 * The locale tags are those of {@code res/xml/locales_config.xml} (Android 13+ per-app language).
 */
public final class Languages {
    public static final List<String> CODES = Arrays.asList("en", "pt", "pt-BR", "es", "fr", "it", "de",
            "nl", "pl", "tr", "ru", "uk", "ja", "ko", "zh-Hans", "id", "sv", "cs");

    private Languages() {}

    /** The asset of a language file, or null for English or anything that is not one of ours. */
    public static String asset(String code) {
        if (code == null || "en".equals(code) || !CODES.contains(code)) return null;
        return "i18n/" + code + ".json";
    }
}
