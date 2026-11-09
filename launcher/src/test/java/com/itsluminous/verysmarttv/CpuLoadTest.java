package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * widgets: the CPU gauge where /proc/stat is hidden. The load is how far each core is clocked above its floor, not
 * its share of the top clock, which on the Mi TV 4A (1150, 1350, 1500 MHz) could never read below 77%.
 */
public class CpuLoadTest {

    private static final long FLOOR = 1_150_000, TOP = 1_500_000;

    @Test
    public void idleCoresAtTheFloorReadZero() {
        long[] cur = {FLOOR, FLOOR, FLOOR, FLOOR};
        assertEquals(0, CpuLoad.percent(cur, floors(4), tops(4)));
    }

    @Test
    public void coresAtTheTopReadAHundred() {
        long[] cur = {TOP, TOP, TOP, TOP};
        assertEquals(100, CpuLoad.percent(cur, floors(4), tops(4)));
    }

    @Test
    public void mixedCoresAverage() {
        long[] cur = {TOP, FLOOR, 1_350_000, FLOOR};                 // 100, 0, 57.1, 0
        assertEquals(39, CpuLoad.percent(cur, floors(4), tops(4)));  // 157.1 / 4 = 39.3
        assertEquals(50, CpuLoad.percent(new long[]{TOP, FLOOR}, floors(2), tops(2)));
    }

    @Test
    public void aFixedClockCountsAsZeroAndReadingsAreClamped() {
        assertEquals(0, CpuLoad.percent(new long[]{TOP}, new long[]{TOP}, new long[]{TOP}));   // floor == top
        assertEquals(50, CpuLoad.percent(new long[]{TOP, TOP}, new long[]{TOP, FLOOR}, new long[]{TOP, TOP}));  // one fixed (0), one full
        // sysfs can report a clock outside the table for a moment: never past the ring, never below zero
        assertEquals(100, CpuLoad.percent(new long[]{1_700_000}, new long[]{FLOOR}, new long[]{TOP}));
        assertEquals(0, CpuLoad.percent(new long[]{216_000}, new long[]{FLOOR}, new long[]{TOP}));
    }

    @Test
    public void nothingReadIsMinusOne() {
        assertEquals(-1, CpuLoad.percent(null, null, null));
        assertEquals(-1, CpuLoad.percent(new long[0], new long[0], new long[0]));
    }

    @Test
    public void theFloorIsTheTablesSmallestClockElseCpuinfoMin() {
        // The Mi TV 4A: a table of three clocks and a cpuinfo_min_freq (216 MHz) the governor never picks.
        assertEquals(FLOOR, CpuLoad.floor("1150000 1350000 1500000 ", 216_000, TOP));
        assertEquals(FLOOR, CpuLoad.floor("1500000 1350000 1150000", 216_000, TOP));   // any order
        assertEquals(216_000, CpuLoad.floor(null, 216_000, TOP));                       // no table: cpuinfo_min_freq
        assertEquals(216_000, CpuLoad.floor("", 216_000, TOP));
        assertEquals(216_000, CpuLoad.floor("1500000", 216_000, TOP));                 // a table with only the top
        assertEquals(-1, CpuLoad.floor(null, TOP, TOP));                               // nothing below the top
        assertEquals(-1, CpuLoad.floor(null, -1, TOP));
        assertEquals(FLOOR, CpuLoad.floor("x 1150000 1500000", -1, TOP));              // a word in the table is skipped
    }

    @Test
    public void theSmootherGlidesOverTheCoarseSteps() {
        CpuLoad.Smoother s = new CpuLoad.Smoother();
        assertEquals(0, s.next(0));                                  // the first reading seeds it
        assertEquals(50, s.next(100));                               // 0 -> 100 reads 50, not 100
        assertEquals(75, s.next(100));
        assertEquals(88, s.next(100));
        assertEquals(44, s.next(0));
        assertEquals(-1, s.next(-1));                                // nothing read: hides, and the average restarts
        assertEquals(100, s.next(100));
        s.reset();
        assertEquals(57, s.next(57));
    }

    private static long[] floors(int n) {
        long[] f = new long[n];
        java.util.Arrays.fill(f, FLOOR);
        return f;
    }

    private static long[] tops(int n) {
        long[] t = new long[n];
        java.util.Arrays.fill(t, TOP);
        return t;
    }
}
