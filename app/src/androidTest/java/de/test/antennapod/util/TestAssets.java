package de.test.antennapod.util;

import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestAssets {
    private TestAssets() {
    }

    public static byte[] readBytes(String name) throws IOException {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            return IOUtils.toByteArray(in);
        }
    }

    public static String readText(String name) throws IOException {
        return new String(readBytes(name), StandardCharsets.UTF_8);
    }
}
