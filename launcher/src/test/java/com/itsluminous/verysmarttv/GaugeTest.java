package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** widgets as ring gauges: the three percents a reading gives, and the arc each one draws. */
public class GaugeTest {

    @Test
    public void ramIsUsedOfTotalAndStorageUsedOfData() {
        MainActivity.Readings r = new MainActivity.Readings();
        r.mem = new long[]{620, 1000};
        r.totalBytes = 8_000_000_000L;
        r.freeBytes = 4_900_000_000L;
        assertEquals(62, r.memoryPercent());
        assertEquals(39, r.storagePercent());        // 3.1 GB used of 8
    }

    @Test
    public void theCpuRingIsBusyWhenReadableAndTheClockLoadOtherwise() {
        MainActivity.Readings r = new MainActivity.Readings();
        r.cpu = 8;
        r.cpuClockLoad = 55;
        assertEquals(8, r.cpuGaugePercent());
        r.cpu = -1;
        assertEquals(55, r.cpuGaugePercent());       // Android 10 hides /proc/stat: how far the clocks are above idle
        r.cpuClockLoad = -1;
        assertEquals(-1, r.cpuGaugePercent());       // neither: the gauge hides
    }

    @Test
    public void noReadingIsMinusOneNotZero() {
        MainActivity.Readings r = new MainActivity.Readings();
        assertEquals(-1, r.memoryPercent());
        assertEquals(-1, r.storagePercent());
        assertEquals(-1, MainActivity.Readings.percentOf(5, 0));
        assertEquals(100, MainActivity.Readings.percentOf(7, 5));   // never past the ring
    }

    @Test
    public void theArcSweepsThreePointSixDegreesAPoint() {
        assertEquals(0f, GaugeView.sweepDegrees(0), 0f);
        assertEquals(223.2f, GaugeView.sweepDegrees(62), 0.01f);
        assertEquals(360f, GaugeView.sweepDegrees(100), 0f);
        assertEquals(360f, GaugeView.sweepDegrees(140), 0f);
    }

    @Test
    public void threeGaugesWithTheirGapsFitBeforeThePills() {
        float d = 2f;
        int width = 3 * GaugeView.widthPx(d) + 2 * Math.round(MainActivity.WIDGET_GAP_DP * d);
        assertEquals(336, width);                                    // 168dp at 1080p
        assertEquals(96, GaugeView.widthPx(d));                      // 48dp: the ring's 44 and 2 each side, whatever the label
        assertEquals(128, GaugeView.heightPx(d));                    // 64dp: inside the clock row's 65dp
    }

    @Test
    public void aLabelWiderThanTheGaugeShrinksInsteadOfWideningIt() {
        int gauge = GaugeView.widthPx(2f);                           // 96px; the label has 94
        assertEquals(10f, GaugeView.fitLabelSp(60f, gauge), 0f);     // RAM, CPU: full size
        assertEquals(10f, GaugeView.fitLabelSp(94f, gauge), 0f);     // just fits
        assertEquals(9f, GaugeView.fitLabelSp(99f, gauge), 0f);      // STORAGE at 1080p: 10 * 94 / 99 = 9.49 -> 9
        assertEquals(8.5f, GaugeView.fitLabelSp(108f, gauge), 0f);   // 8.70 -> 8.5
        assertEquals(7f, GaugeView.fitLabelSp(200f, gauge), 0f);     // never below 7sp
        assertEquals(10f, GaugeView.fitLabelSp(0f, gauge), 0f);      // no label: full size
    }

    @Test
    public void theGlassIsThePillsFrost() {
        assertEquals(0x2EFFFFFF, GaugeView.FROST_TOP);               // pill_bg's gradient start
        assertEquals(0x14FFFFFF, GaugeView.FROST_BOTTOM);            // and end
        assertEquals(0x38FFFFFF, GaugeView.RIM);                     // and its 1dp stroke
        assertEquals(0x66000000, GaugeView.OVER_ART_BASE);           // pill_bg_over_art's base
    }
}
