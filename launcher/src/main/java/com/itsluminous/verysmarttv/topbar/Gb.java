package com.itsluminous.verysmarttv.topbar;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * The figure the System bars show for a byte count (DESIGN.md §9.1): "1.1 GB free", "21.6 GB free", "512 MB free".
 * Pure: the number and its unit come from here, the words ("GB", "free") from string resources in {@link ResourceBars},
 * so this is a JVM test and the view a Robolectric one. GB keeps one decimal up to 100 GB (the reference image reads
 * "21.6 GB free"), none from 100 GB; MB is whole; under 1 MB the count is bytes.
 */
public final class Gb {
    private Gb() {
    }

    private static final long KB = 1024L, MB = KB * 1024, GB = MB * 1024;

    /** The unit {@link #amount} chose, so the view can pick its string. */
    public enum Unit { B, MB, GB }

    @NonNull
    public static Unit unit(long bytes) {
        if (bytes >= GB) return Unit.GB;
        if (bytes >= MB) return Unit.MB;
        return Unit.B;
    }

    /**
     * The number for {@code bytes} in {@link #unit}: GB with one decimal below 100 GB ("1.1", "21.6", "10.0" for
     * 9.95 GB), whole from 100 GB; MB and B whole. The decimal separator is the locale's.
     */
    @NonNull
    public static String amount(long bytes, @NonNull Locale locale) {
        long clamped = Math.max(0, bytes);
        switch (unit(clamped)) {
            case GB:
                double gb = clamped / (double) GB;
                return gb >= 100 ? String.format(locale, "%d", Math.round(gb)) : String.format(locale, "%.1f", gb);
            case MB:
                return String.format(locale, "%d", clamped / MB);
            default:
                return String.format(locale, "%d", clamped);
        }
    }

    /** The filled share of a bar for {@code free} of {@code total}, 0 to 1: how much is used. */
    public static float usedShare(long free, long total) {
        if (total <= 0) return 0f;
        float used = (total - Math.max(0, Math.min(free, total))) / (float) total;
        return Math.max(0f, Math.min(1f, used));
    }
}
