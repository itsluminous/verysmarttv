package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The sampler's one reading from injected readers (DESIGN.md §14.2 ResourceSamplerTest): /proc/meminfo lines and the
 * data partition's sizes become free and total bytes per row, and an unreadable source hides its row.
 */
public class ResourceSamplerTest {
    private static final long MB = 1024L * 1024L;

    private static ResourceSampler.Readers readers(@Nullable List<String> lines, @Nullable long[] storage) {
        return new ResourceSampler.Readers() {
            @Override
            @Nullable
            public List<String> memInfoLines() {
                return lines;
            }

            @Override
            @Nullable
            public long[] storageBytes() {
                return storage;
            }
        };
    }

    /** A Mi TV 4A: 1.5 GB total, 1.1 GB available. */
    private static final List<String> MI_TV = Arrays.asList(
            "MemTotal:        1510840 kB", "MemFree:          328848 kB", "MemAvailable:    1126400 kB",
            "Buffers:           12345 kB", "Cached:           400000 kB");

    @Test
    public void ramIsFreeOfTotalFromMemAvailable() {
        ResourceSampler.Sample s = ResourceSampler.sample(readers(MI_TV, new long[]{21_600L * MB, 32_000L * MB}));
        assertTrue(s.ramKnown);
        assertEquals("total: 1510840 kB in whole MB", 1475L * MB, s.ramTotal);
        assertEquals("free = total - used, in whole MB", 1475L * MB - (1475L - 1100L) * MB, s.ramFree);
        assertEquals("1.1", Gb.amount(s.ramFree, java.util.Locale.US));
    }

    @Test
    public void storageIsAvailableOfTotal() {
        ResourceSampler.Sample s = ResourceSampler.sample(readers(MI_TV, new long[]{21_600L * MB, 32_000L * MB}));
        assertTrue(s.storageKnown);
        assertEquals(21_600L * MB, s.storageFree);
        assertEquals(32_000L * MB, s.storageTotal);
        assertEquals("21.1", Gb.amount(s.storageFree, java.util.Locale.US));
        assertEquals(0.325f, Gb.usedShare(s.storageFree, s.storageTotal), 0.001f);
    }

    @Test
    public void anOlderKernelWithoutMemAvailableAddsFreeBuffersAndCached() {
        List<String> old = Arrays.asList("MemTotal:  1024000 kB", "MemFree:  100000 kB", "Buffers:  20000 kB", "Cached:  280000 kB");
        ResourceSampler.Sample s = ResourceSampler.sample(readers(old, null));
        assertTrue(s.ramKnown);
        long totalMb = 1024000 / 1024, availMb = 400000 / 1024;
        assertEquals(totalMb * MB, s.ramTotal);
        assertEquals((totalMb - (totalMb - availMb)) * MB, s.ramFree);
    }

    @Test
    public void anUnreadableSourceHidesItsRowOnly() {
        ResourceSampler.Sample noMem = ResourceSampler.sample(readers(null, new long[]{5L * MB, 10L * MB}));
        assertFalse(noMem.ramKnown);
        assertTrue(noMem.storageKnown);
        ResourceSampler.Sample noStorage = ResourceSampler.sample(readers(MI_TV, null));
        assertTrue(noStorage.ramKnown);
        assertFalse(noStorage.storageKnown);
        ResourceSampler.Sample garbage = ResourceSampler.sample(readers(Arrays.asList("nothing", "here"), new long[]{1, 0}));
        assertFalse("no MemTotal: no RAM row", garbage.ramKnown);
        assertFalse("a zero total: no storage row", garbage.storageKnown);
    }

    @Test
    public void negativeFreeSpaceClampsToZero() {
        ResourceSampler.Sample s = ResourceSampler.sample(readers(null, new long[]{-5, 10L * MB}));
        assertTrue(s.storageKnown);
        assertEquals(0, s.storageFree);
        assertEquals(1f, Gb.usedShare(s.storageFree, s.storageTotal), 0f);
    }
}
