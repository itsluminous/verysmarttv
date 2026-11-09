package com.itsluminous.verysmarttv.grid;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The order file's header names the product (DESIGN.md §7.4): this launcher writes {@code verysmarttv-order 1} and
 * reads any {@code <name>-order 1}, so a file or a backup from the fork's launcher decodes unchanged (ADR-036).
 */
public class OrderMagicTest {
    @Test
    public void writesTheNewHeader() {
        assertTrue(Order.encode(new Order("x", Arrays.asList("a/.A"))).startsWith("verysmarttv-order 1\n"));
    }

    @Test
    public void readsAnotherNameForTheSameFormat() {
        Order legacy = Order.decode("previous-order 1\ndock com.netflix.ninja\ndocked 1\na/.A\nb/.B\n");
        assertNotNull(legacy);
        assertEquals("com.netflix.ninja", legacy.dock);
        assertEquals(1, legacy.docked);
        assertEquals(Arrays.asList("a/.A", "b/.B"), legacy.keys);
        // and the one it writes itself, of course
        assertNotNull(Order.decode("verysmarttv-order 1\ndock x\na/.A\n"));
    }

    @Test
    public void onlyVersionOneOfTheFormatCounts() {
        assertNull(Order.decode("previous-order 2\ndock x\na/.A\n"));
        assertNull(Order.decode("Previous-order 1\ndock x\na/.A\n"));     // the name is lower-case letters
        assertNull(Order.decode("previous-order 1 \ndock x\na/.A\n"));    // nothing after the version
        assertNull(Order.decode("previous_order 1\ndock x\na/.A\n"));
        assertNull(Order.decode("-order 1\ndock x\na/.A\n"));
        assertNull(Order.decode("order 1\ndock x\na/.A\n"));
    }
}
