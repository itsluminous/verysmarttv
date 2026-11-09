package com.itsluminous.verysmarttv.support;

import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** The adb shell from inside a test, through {@link android.app.UiAutomation#executeShellCommand}. */
public final class Shell {
    private Shell() {}

    /** Runs {@code command} as the shell user and returns everything it printed. */
    public static String run(String command) throws IOException {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            pfd.close();
        }
    }

    /** The lines of {@code text} that contain {@code needle}, trimmed, one per line. */
    public static String grep(String text, String needle) {
        StringBuilder hits = new StringBuilder();
        for (String line : text.split("\n")) if (line.contains(needle)) hits.append(line.trim()).append('\n');
        return hits.toString();
    }
}
