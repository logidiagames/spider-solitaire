package com.nexusplaylabss.freecell.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class LanguagesTest {
    @Test public void onlyOurLanguagesMapToAnAsset() {
        assertEquals("i18n/pt-BR.json", Languages.asset("pt-BR"));
        assertEquals("i18n/zh-Hans.json", Languages.asset("zh-Hans"));
        assertNull(Languages.asset("en"));
        assertNull(Languages.asset("../strings"));
        assertNull(Languages.asset("xx"));
        assertNull(Languages.asset(null));
    }

    @Test public void everyLanguageHasItsFileAndItsLocale() throws Exception {
        String locales = new String(Files.readAllBytes(new File("src/main/res/xml/locales_config.xml").toPath()), "UTF-8");
        for (String code : Languages.CODES) {
            if (!"en".equals(code)) assertTrue(code, new File("src/main/assets/" + Languages.asset(code)).isFile());
        }
        for (String tag : new String[] {"en", "pt-PT", "pt-BR", "es", "fr", "it", "de", "nl", "pl", "tr", "ru", "uk", "ja", "ko", "zh-Hans", "id", "sv", "cs"}) {
            assertTrue(tag, locales.contains("android:name=\"" + tag + "\""));
        }
    }
}
