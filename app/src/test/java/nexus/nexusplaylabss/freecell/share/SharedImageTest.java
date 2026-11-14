package com.nexusplaylabss.freecell.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SharedImageTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Test public void acceptsOnlyPngDataUrlsOrPlainBase64() {
        assertEquals("iVBORw0K", SharedImage.payload("data:image/png;base64,iVBORw0K"));
        assertEquals("iVBORw0K", SharedImage.payload("iVBORw0K"));
        assertNull(SharedImage.payload("data:image/jpeg;base64,/9j/4AAQ"));
        assertNull(SharedImage.payload(""));
        assertNull(SharedImage.payload(null));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= SharedImage.MAX_BASE64_CHARS; i++) tooLong.append('A');
        assertNull(SharedImage.payload(tooLong.toString()));
    }

    @Test public void checksThePngSignatureAndSize() {
        assertTrue(SharedImage.isPng(PNG));
        assertFalse(SharedImage.isPng(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0}));
        assertFalse(SharedImage.isPng(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
        assertFalse(SharedImage.isPng(null));
        byte[] huge = new byte[SharedImage.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);
        assertFalse(SharedImage.isPng(huge));
    }
}
