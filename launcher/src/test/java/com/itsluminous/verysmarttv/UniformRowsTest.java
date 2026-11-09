package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * uniform_rows: no dock. Every row holds `columns` tiles of one order, the first row too; the hero dots count the
 * focused row; the resting line is the shelf's room at hero_height. The numbers are the Mi TV 4A's (1080p, density 2,
 * row_gap 32, 6 columns), where the line must stay where the dock layout put it.
 */
public class UniformRowsTest {

    private static final float D = 2f;
    /** The carousel's full height on the shelf, MainActivity.CAROUSEL_FULL_DP, and the grid's height under the header. */
    private static final int CAROUSEL_PX = Math.round(201f * D), GRID_H = 877;

    @Test
    public void theKeyNeedsTheDockRowLayoutAndNotASingleRow() {
        assertTrue(UniformRows.on(true, true, false));
        assertFalse(UniformRows.on(true, false, false));   // dock_row false is already one plain grid
        assertFalse(UniformRows.on(true, true, true));     // single_row has one row
        assertFalse(UniformRows.on(false, true, false));
    }

    @Test
    public void theFirstRowHoldsColumnsTilesLikeEveryOther() {
        // The theme's six dock_apps and fill_dock false left a seventh slot empty at 7 columns; with fill_dock
        // resolved true (Config, uniform_rows) there is no empty slot, so AppAdapter.dockFilled() is the dock size,
        // i.e. the columns: 7 apps per row puts 7 in the top row.
        assertEquals(0, DockSlots.empty(6, 7, 20, true));
        assertEquals(1, DockSlots.empty(6, 7, 20, false));   // the dock layout's gap, for comparison
        assertEquals(0, DockSlots.empty(6, 6, 20, true));
        // Positions map to rows by the columns alone: row 0 is the first `columns` tiles of the order.
        assertEquals(0, UniformRows.rowOf(0, 7));
        assertEquals(0, UniformRows.rowOf(6, 7));
        assertEquals(1, UniformRows.rowOf(7, 7));
        assertEquals(2, UniformRows.rowOf(13, 6));
        assertEquals(12, UniformRows.rowStart(13, 6));
        assertEquals(0, UniformRows.rowStart(5, 6));
    }

    @Test
    public void oneDotPerTileOfTheFocusedRowAndAShortLastRow() {
        // 20 tiles at 6 columns: three full rows and two tiles in the last.
        assertEquals(6, UniformRows.rowCount(0, 6, 20));
        assertEquals(6, UniformRows.rowCount(5, 6, 20));
        assertEquals(6, UniformRows.rowCount(13, 6, 20));
        assertEquals(2, UniformRows.rowCount(18, 6, 20));
        assertEquals(2, UniformRows.rowCount(19, 6, 20));
        // 20 tiles at 7 columns: 7, 7, then 6.
        assertEquals(7, UniformRows.rowCount(0, 7, 20));
        assertEquals(7, UniformRows.rowCount(8, 7, 20));
        assertEquals(6, UniformRows.rowCount(14, 7, 20));
    }

    @Test
    public void theLastRowCountsItsAppsNotTheAllAppsRowUnderIt() {
        // 15 apps at 6 columns: 6, 6, then 3. The count passed in is the apps (AppAdapter.appCount); the library's
        // All apps row is one more adapter item after the last app (getItemCount 16), which once gave this row a
        // fourth dot.
        int apps = 15, adapterItems = apps + 1;
        assertEquals(3, UniformRows.rowCount(12, 6, apps));
        assertEquals(3, UniformRows.rowCount(14, 6, apps));
        assertEquals(4, UniformRows.rowCount(12, 6, adapterItems));   // the old arithmetic, for the record
        // A last row that is exactly full stays full, and a lone app in it gets one dot (and so none drawn).
        assertEquals(6, UniformRows.rowCount(12, 6, 18));
        assertEquals(1, UniformRows.rowCount(18, 6, 19));
    }

    @Test
    public void theRestingLineIsTheShelfsRoomWhereTheDockLayoutPutIt() {
        // mooq1-frosted today: the carousel's 201dp plus the 40dp gap, 482px, is above the dock layout's own
        // h - dock row - 1 row - 30dp (403px), so that is where the first row rested. Uniform rows keep it.
        int shelf = UniformRows.shelfRoomPx(CAROUSEL_PX, CAROUSEL_PX, D);
        assertEquals(482, shelf);
        assertEquals(482, UniformRows.restingLinePx(shelf, 100));
        // hero_height scales the line: Short lifts the first row, Tall lowers it.
        assertEquals(386, UniformRows.restingLinePx(shelf, 80));
        assertEquals(578, UniformRows.restingLinePx(shelf, 120));
        // The line never leaves the grid: the first row (a 192px item) and most of a second still fit at 100.
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(229), true, D, 32);
        assertEquals(192, itemH);
        assertTrue(shelf + itemH < GRID_H);
    }

    @Test
    public void withoutTheCarouselTheMeasuredShelfCountsAndNeverLessThanTheTitlesRoom() {
        // Trakt recommendations off: the shelf is the name (and the cinematic lines), say 120dp, plus the gap.
        assertEquals(Math.round(160 * D), UniformRows.shelfRoomPx(Math.round(120 * D), 0, D));
        // The first layout pass can see a shelf measured at 0: the title's room (104dp) is the floor, so the rows
        // never start in the header.
        assertEquals(Math.round(AppAdapter.TITLE_ROOM_DP * D), UniformRows.shelfRoomPx(0, 0, D));
        assertEquals(Math.round(AppAdapter.TITLE_ROOM_DP * D), UniformRows.shelfRoomPx(Math.round(50 * D), 0, D));
        assertEquals(0, UniformRows.restingLinePx(0, 100));
    }

    @Test
    public void theTileFitReadsTheWidthAloneWithNoRowsCountedUnderTheFirst() {
        // Config resolves rows_below_dock to 0 under uniform_rows, so the height never shrinks the tiles: 6 columns
        // on the TV's 1696px inner width (the header's span, EdgeAlignDecoration) give the same 234px tile the dock
        // layout gave with one row below the dock.
        int byWidth = AppAdapter.tileWidthPx(1696, 6, D);
        assertEquals(byWidth, AppAdapter.tileWidthPx(1696, GRID_H, 6, 0, true, true, D, 32, 100));
        assertEquals(byWidth, AppAdapter.tileWidthPx(1696, GRID_H, 6, 1, true, true, D, 32, 100));
        assertEquals(234, byWidth);
    }
}
