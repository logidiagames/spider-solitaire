package com.nexusplaylabss.freecell;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The "DEV · Tools" button exists only when AndroidBridge.isDebugBuild() (BuildConfig.TEST_TOOLS)
 * is true, so a release build, and every AAB, must have TEST_TOOLS false. Only the debug unit
 * tests run here, so the release settings are checked in the build file itself, and the AAB
 * script must build the release variant.
 */
public class DevToolsGateTest {
    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    /** The text of the block that starts at {@code start} (from its opening brace to the matching one). */
    private static String block(String text, String start) {
        int open = text.indexOf('{', text.indexOf(start));
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            if (text.charAt(i) == '}' && --depth == 0) return text.substring(open, i + 1);
        }
        throw new AssertionError("no block for " + start);
    }

    @Test
    public void releaseBuildsNeverHaveTheDeveloperTools() throws IOException {
        String gradle = read("build.gradle.kts");
        assertTrue("TEST_TOOLS defaults to false",
                block(gradle, "defaultConfig").contains("buildConfigField(\"boolean\", \"TEST_TOOLS\", \"false\")"));
        String release = block(gradle, "        release {");
        assertFalse("the release build type must not turn TEST_TOOLS on", release.contains("TEST_TOOLS"));
    }

    @Test
    public void theAabScriptBuildsTheReleaseVariant() throws IOException {
        String script = read("../tools/build_release_aab.sh");
        assertTrue(script.contains("bundleRelease"));
        assertFalse(script.contains("bundleDebug") || script.contains("bundleQa"));
    }

    @Test
    public void thisTestBuildKeepsTheDeveloperTools() {
        assertTrue("debug and qa builds keep the DEV Tools", BuildConfig.TEST_TOOLS);
    }
}
