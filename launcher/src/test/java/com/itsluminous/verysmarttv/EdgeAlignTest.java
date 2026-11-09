package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** EdgeAlignDecoration: the tiles fill the header's span, first at its left edge, last at its right, equal gaps between. */
public class EdgeAlignTest {

    private static final float D = 2f;
    /** activity_main.xml at 1920 px: the header runs from 112 to 1808, and so does the grid's inner width. */
    private static final int INNER = 1696;

    /** The tiles' left edges in grid-inner px for this many columns, as GridLayoutManager cells plus the insets give them. */
    private static int[] lefts(int columns, int tile) {
        int[] out = new int[columns];
        int[] insets = new int[2];
        for (int i = 0; i < columns; i++) {
            EdgeAlignDecoration.insetsPx(i, columns, INNER, tile, insets);
            out[i] = EdgeAlignDecoration.cellStartPx(i, columns, INNER) + insets[0];
        }
        return out;
    }

    @Test
    public void firstTileStartsAtTheLeftEdgeAndTheLastEndsAtTheRight() {
        for (int columns = 4; columns <= 7; columns++) {
            int tile = AppAdapter.tileWidthPx(INNER, columns, D);
            int[] lefts = lefts(columns, tile);
            assertEquals("columns " + columns, 0, lefts[0]);
            assertEquals("columns " + columns, INNER, lefts[columns - 1] + tile, 1);
        }
    }

    @Test
    public void everyGapIsTheSameWithinAPixel() {
        for (int columns = 4; columns <= 7; columns++) {
            int tile = AppAdapter.tileWidthPx(INNER, columns, D);
            int[] lefts = lefts(columns, tile);
            float gap = EdgeAlignDecoration.gapPx(columns, INNER, tile);
            for (int i = 1; i < columns; i++) {
                assertEquals("columns " + columns + " gap " + i, gap, lefts[i] - lefts[i - 1] - tile, 1.01f);
            }
        }
    }

    @Test
    public void sixColumnsOnTheTv() {
        // 234px tiles, 58.4px apart, where the centred cells left the first tile 43px in from the header's edge.
        int tile = AppAdapter.tileWidthPx(INNER, 6, D);
        assertEquals(234, tile);
        int[] lefts = lefts(6, tile);
        assertEquals(0, lefts[0]);
        assertEquals(INNER, lefts[5] + tile, 1);
        assertEquals(58.4f, EdgeAlignDecoration.gapPx(6, INNER, tile), 0.01f);
    }

    @Test
    public void aFocusedEdgeTileStillFitsInTheGridsOverflowRoom() {
        // The grid reaches 16dp past the header on each side (activity_main.xml): a focused tile grows about
        // 6 percent a side (AppAdapter.FOCUS_SCALE, a little more with advanced effects), so the room must cover it.
        int room = Math.round(16 * D);
        for (int columns = 4; columns <= 7; columns++) {
            int tile = AppAdapter.tileWidthPx(INNER, columns, D);
            float grow = tile * (1.13f - 1f) / 2f;
            assertTrue("columns " + columns, grow < room);
        }
    }

    @Test
    public void oneColumnCentresAndAFullSpanRowIsLeftAlone() {
        int[] insets = new int[2];
        EdgeAlignDecoration.insetsPx(0, 1, 1000, 400, insets);
        assertEquals(300, insets[0]);
        assertEquals(300, insets[1]);
        EdgeAlignDecoration.insetsPx(0, 0, 1000, 400, insets);
        assertEquals(0, insets[0] + insets[1]);
        EdgeAlignDecoration.insetsPx(2, 4, 1000, 300, insets);   // wider than its cell: nothing to share
        assertEquals(0, insets[0] + insets[1]);
        assertEquals(1696, EdgeAlignDecoration.cellStartPx(6, 6, 1696));   // the borders end at the inner's edge
    }
}
