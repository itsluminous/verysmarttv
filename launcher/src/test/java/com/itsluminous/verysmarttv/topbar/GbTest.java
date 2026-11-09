package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

/**
 * The System bars' figure (DESIGN.md §14.2 GbTest): "1.1 GB", "21.6 GB", "512 MB", "0 B"; rounding at 9.95 GB; the
 * locale's decimal separator; and the used share a bar fills.
 */
public class GbTest {
    private static final long MB = 1024L * 1024L, GB = MB * 1024L;

    private static String free(long bytes, Locale locale) {
        return Gb.amount(bytes, locale) + " " + Gb.unit(bytes);
    }

    @Test
    public void gigabytesKeepOneDecimal() {
        assertEquals("1.1 GB", free(Math.round(1.1 * GB), Locale.US));
        assertEquals("21.6 GB", free(Math.round(21.6 * GB), Locale.US));
        assertEquals("1.0 GB", free(GB, Locale.US));
        assertEquals("99.9 GB", free(Math.round(99.94 * GB), Locale.US));
    }

    @Test
    public void megabytesAndBytesAreWhole() {
        assertEquals("512 MB", free(512 * MB, Locale.US));
        assertEquals("1023 MB", free(GB - 1, Locale.US));
        assertEquals("0 B", free(0, Locale.US));
        assertEquals("1023 B", free(1023, Locale.US));
        assertEquals("a negative count reads as nothing free", "0 B", free(-5, Locale.US));
        assertEquals("under a megabyte the count is bytes", "1048575 B", free(MB - 1, Locale.US));
    }

    @Test
    public void roundingAtNinePointNineFive() {
        assertEquals("9.9 GB", free(Math.round(9.94 * GB), Locale.US));
        assertEquals("10.0 GB", free(Math.round(9.95 * GB), Locale.US));
        assertEquals("a hundred gigabytes and up drop the decimal", "100 GB", free(100 * GB, Locale.US));
        assertEquals("128 GB", free(Math.round(127.6 * GB), Locale.US));
    }

    @Test
    public void theLocaleDecimalSeparator() {
        assertEquals("1,1 GB", free(Math.round(1.1 * GB), Locale.GERMANY));
        assertEquals("21,6 GB", free(Math.round(21.6 * GB), Locale.FRANCE));
        assertEquals("512 MB", free(512 * MB, Locale.GERMANY));
    }

    @Test
    public void theUsedShareFillsTheBar() {
        assertEquals(0.75f, Gb.usedShare(GB, 4 * GB), 0.0001f);
        assertEquals(0f, Gb.usedShare(4 * GB, 4 * GB), 0f);
        assertEquals(1f, Gb.usedShare(0, 4 * GB), 0f);
        assertEquals("more free than total clamps", 0f, Gb.usedShare(5 * GB, 4 * GB), 0f);
        assertEquals("no total: nothing to fill", 0f, Gb.usedShare(1, 0), 0f);
    }
}
