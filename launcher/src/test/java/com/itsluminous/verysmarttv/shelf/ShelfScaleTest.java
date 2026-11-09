package com.itsluminous.verysmarttv.shelf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.itsluminous.verysmarttv.grid.UniformRows;

/**
 * hero_height: the shelf's text and spacing scale with its height, and the grid's resting line never comes up under
 * the shelf. The numbers are the Mi TV 4A's (1080p, density 2): the header ends 90dp down (SHELF_MIN_TOP_DP, 180px),
 * the grid starts 203px down, and the cinematic shelf (a 68sp name, the meta line, the dots) measured 248px at 100.
 */
public class ShelfScaleTest {

    private static final float D = 2f;
    private static final int SHELF_MIN_TOP = 180, GRID_TOP = 203;

    @Test
    public void theFactorIsOneAtFullHeightAndTwoThirdsAtCompact() {
        assertEquals(1f, ShelfScale.factor(100), 0.0001f);
        assertEquals(1f, ShelfScale.factor(120), 0.0001f);   // Tall keeps the layout's sizes
        assertEquals(5 / 6f, ShelfScale.factor(80), 0.0001f);
        assertEquals(2 / 3f, ShelfScale.factor(60), 0.0001f);
        assertEquals(1f - 50 / 120f, ShelfScale.factor(50), 0.0001f);   // the key's floor, on the same line
        assertEquals(ShelfScale.factor(50), ShelfScale.factor(10), 0.0001f);
    }

    @Test
    public void theTitleReads36And30And24AtNormalShortAndCompact() {
        assertEquals(36f, ShelfScale.textSp(36f, 100), 0f);
        assertEquals(30f, ShelfScale.textSp(36f, 80), 0f);
        assertEquals(24f, ShelfScale.textSp(36f, 60), 0f);
        // The cinematic name and the plain one.
        assertEquals(68f, ShelfScale.textSp(68f, 100), 0f);
        assertEquals(45f, ShelfScale.textSp(68f, 60), 0f);
        assertEquals(32f, ShelfScale.textSp(48f, 60), 0f);
    }

    @Test
    public void spacingScalesButKeepsItsFloor() {
        // The 40dp under the app's name: 80px at 100, 54px at 60; the 12dp under the carousel's dots: 24px, then 16px.
        assertEquals(80, ShelfScale.spacingPx(40f, 8f, 100, D));
        assertEquals(53, ShelfScale.spacingPx(40f, 8f, 60, D));
        assertEquals(24, ShelfScale.spacingPx(12f, 8f, 100, D));
        assertEquals(16, ShelfScale.spacingPx(12f, 8f, 60, D));
        // Never under the floor: 8dp at the key's 50.
        assertEquals(16, ShelfScale.spacingPx(12f, 8f, 50, D));
        assertEquals(8, ShelfScale.spacingPx(6f, 4f, 50, D));
    }

    @Test
    public void theCarouselKeepsFewerLinesAndNoChipsOnAShortShelf() {
        assertEquals(3, ShelfScale.synopsisLines(100));
        assertEquals(2, ShelfScale.synopsisLines(80));
        assertEquals(1, ShelfScale.synopsisLines(60));
        assertEquals(1, ShelfScale.synopsisLines(50));
        assertTrue(ShelfScale.chipsShown(80));
        assertFalse(ShelfScale.chipsShown(60));
    }

    @Test
    public void theRestingLineStaysUnderTheShelfAtCompact() {
        // Trakt recommendations off at Compact: the shelf's room (248px measured plus the 40dp gap, 328px) at 60% put
        // the line 197px under the grid's top, 400px down the screen, while the shelf, pinned under the header at
        // 180px, ended at 428px: its dots on the first row. The floor keeps the line under the shelf plus the gap.
        int shelf = 248, line60 = UniformRows.restingLinePx(UniformRows.shelfRoomPx(shelf, 0, D), 60);
        assertEquals(197, line60);
        int gap = ShelfScale.spacingPx(40f, 8f, 60, D);
        int floor = ShelfScale.floorPx(SHELF_MIN_TOP, shelf, gap, GRID_TOP);
        assertEquals(180 + 248 + 53 - 203, floor);
        assertTrue(floor > line60);
        assertTrue(GRID_TOP + Math.max(line60, floor) >= SHELF_MIN_TOP + shelf + gap);
        // With the name scaled to 45sp the shelf is shorter (about 190px), and the floor with it.
        assertTrue(ShelfScale.floorPx(SHELF_MIN_TOP, 190, gap, GRID_TOP) < floor);
    }

    @Test
    public void theFloorChangesNothingWhereTheShelfFits() {
        // At 100 the carousel (403px measured, a pixel over its 201dp room) sits above a line 483px under the grid's
        // top: the floor is lower.
        int line = UniformRows.restingLinePx(UniformRows.shelfRoomPx(403, Math.round(201 * D), D), 100);
        assertEquals(483, line);
        assertTrue(ShelfScale.floorPx(SHELF_MIN_TOP, 403, ShelfScale.spacingPx(12f, 8f, 100, D), GRID_TOP) < line);
        // The same for the name at 100 under the dock layout's 40dp.
        assertTrue(ShelfScale.floorPx(SHELF_MIN_TOP, 248, 80, GRID_TOP) < UniformRows.restingLinePx(UniformRows.shelfRoomPx(248, 0, D), 100));
        // A shelf not measured yet asks for nothing, so the first pass keeps its own floor (UniformRows.shelfRoomPx).
        assertEquals(0, ShelfScale.floorPx(SHELF_MIN_TOP, 0, 80, GRID_TOP));
    }
}
