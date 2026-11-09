package com.itsluminous.verysmarttv.grid;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Every branch of the grid's D-pad rule as a table (DESIGN.md §14.2): single row, dock row Up, mid-grid Up/Down,
 * the last row's Down to the library, horizontal edges with a partly filled dock, 7 columns under uniform_rows
 * (one flat order, no empty slots), and the RTL mirror. The Espresso suite pins the same paths on the emulator
 * (HomeFocusNoPillTest); this table is the one that runs on every build.
 */
public class FocusRulesTest {
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;

    /** A grid with a full dock, every position focusable, the library after the last tile unless said otherwise. */
    private static FocusRules.Grid grid(int count, int columns, boolean library) {
        return new FocusRules.Grid(count, columns, Math.min(count, columns), count - 1, library, false, false);
    }

    private static String verdict(int position, int key, FocusRules.Grid g) {
        return FocusRules.onTileKey(position, key, g).toString();
    }

    @Test
    public void sixAppsFourColumns() {
        FocusRules.Grid g = grid(6, 4, true);
        // dock row (positions 0-3): Up leaves for the top bar, Down lands on the row below (short row: its last tile)
        assertEquals("PASS", verdict(0, UP, g));
        assertEquals("PASS", verdict(3, UP, g));
        assertEquals("MOVE(4)", verdict(0, DOWN, g));
        assertEquals("MOVE(5)", verdict(1, DOWN, g));
        assertEquals("the short second row: Down from column 3 lands on its last tile", "MOVE(5)", verdict(3, DOWN, g));
        // horizontal edges of the dock
        assertEquals("SWALLOW", verdict(0, LEFT, g));
        assertEquals("PASS", verdict(0, RIGHT, g));
        assertEquals("PASS", verdict(1, LEFT, g));
        assertEquals("the dock's last column: Right stays", "SWALLOW", verdict(3, RIGHT, g));
        // the last row: Down goes to the library, Right at its end too
        assertEquals("LIBRARY", verdict(4, DOWN, g));
        assertEquals("LIBRARY", verdict(5, DOWN, g));
        assertEquals("LIBRARY", verdict(5, RIGHT, g));
        assertEquals("SWALLOW", verdict(4, LEFT, g));
        assertEquals("MOVE(0)", verdict(4, UP, g));
        assertEquals("MOVE(1)", verdict(5, UP, g));
    }

    @Test
    public void withoutTheLibraryTheEndsSwallow() {
        FocusRules.Grid g = grid(6, 4, false);
        assertEquals("SWALLOW", verdict(5, DOWN, g));
        assertEquals("SWALLOW", verdict(5, RIGHT, g));
        assertEquals("SWALLOW", verdict(4, DOWN, g));
    }

    @Test
    public void sevenAppsFourColumnsMiddleRow() {
        FocusRules.Grid g = grid(11, 4, true);
        // row 1 (4-7) sits between the dock and the last row (8-10)
        assertEquals("MOVE(1)", verdict(5, UP, g));
        assertEquals("MOVE(9)", verdict(5, DOWN, g));
        assertEquals("Down from column 3 to a 3-tile last row: its last tile", "MOVE(10)", verdict(7, DOWN, g));
        assertEquals("SWALLOW", verdict(4, LEFT, g));
        assertEquals("SWALLOW", verdict(7, RIGHT, g));
        assertEquals("PASS", verdict(5, RIGHT, g));
        assertEquals("the last tile is the row's end even mid-column: the library follows it", "LIBRARY", verdict(10, RIGHT, g));
        assertEquals("SWALLOW", verdict(10, RIGHT, grid(11, 4, false)));
        assertEquals("LIBRARY", verdict(8, DOWN, g));
    }

    @Test
    public void partlyFilledDockEndsAtItsLastApp() {
        // fill_dock false: two apps in a four-slot dock; the empty slots take no focus
        FocusRules.Grid g = new FocusRules.Grid(6, 4, 2, 5, true, false, false);
        assertEquals("the second app is the dock's end", "SWALLOW", verdict(1, RIGHT, g));
        assertEquals("PASS", verdict(0, RIGHT, g));
        assertEquals("grid rows still use the column count", "PASS", verdict(4, RIGHT, g));
    }

    @Test
    public void sevenColumnsOneFullRow() {
        // uniform_rows with 7 columns and 7 apps: one row that is both first and last
        FocusRules.Grid g = grid(7, 7, true);
        assertEquals("PASS", verdict(0, UP, g));
        assertEquals("PASS", verdict(6, UP, g));
        assertEquals("the only row is the last: Down goes to the library", "LIBRARY", verdict(3, DOWN, g));
        assertEquals("LIBRARY", verdict(6, RIGHT, g));
        assertEquals("SWALLOW", verdict(0, LEFT, g));
        assertEquals("PASS", verdict(3, LEFT, g));
        assertEquals("PASS", verdict(3, RIGHT, g));
    }

    @Test
    public void sevenColumnsTwoRows() {
        FocusRules.Grid g = grid(12, 7, false);
        assertEquals("MOVE(11)", verdict(5, DOWN, g));
        assertEquals("MOVE(11)", verdict(6, DOWN, g));
        assertEquals("MOVE(7)", verdict(0, DOWN, g));
        assertEquals("MOVE(4)", verdict(11, UP, g));
        assertEquals("SWALLOW", verdict(11, RIGHT, g));
        assertEquals("SWALLOW", verdict(7, LEFT, g));
    }

    @Test
    public void rtlMirrorsLeftAndRight() {
        FocusRules.Grid g = new FocusRules.Grid(6, 4, 4, 5, true, true, false);
        // position 0 is on the right: Right stays, Left moves on; the row's end (3) is on the left
        assertEquals("SWALLOW", verdict(0, RIGHT, g));
        assertEquals("PASS", verdict(0, LEFT, g));
        assertEquals("SWALLOW", verdict(3, LEFT, g));
        assertEquals("PASS", verdict(3, RIGHT, g));
        assertEquals("Left at the last tile goes to the library", "LIBRARY", verdict(5, LEFT, g));
        assertEquals("Up and Down are not mirrored", "MOVE(1)", verdict(5, UP, g));
    }

    @Test
    public void singleRow() {
        FocusRules.Grid g = new FocusRules.Grid(9, 4, 9, 8, true, false, true);
        assertEquals("Up leaves for the top bar", "PASS", verdict(0, UP, g));
        assertEquals("Down has nowhere to go", "SWALLOW", verdict(3, DOWN, g));
        assertEquals("SWALLOW", verdict(0, LEFT, g));
        assertEquals("PASS", verdict(0, RIGHT, g));
        assertEquals("PASS", verdict(4, LEFT, g));
        assertEquals("the row's end goes to the library", "LIBRARY", verdict(8, RIGHT, g));
        FocusRules.Grid noLibrary = new FocusRules.Grid(9, 4, 9, 8, false, false, true);
        assertEquals("SWALLOW", verdict(8, RIGHT, noLibrary));
        FocusRules.Grid rtl = new FocusRules.Grid(9, 4, 9, 8, true, true, true);
        assertEquals("LIBRARY", verdict(8, LEFT, rtl));
        assertEquals("SWALLOW", verdict(0, RIGHT, rtl));
    }

    @Test
    public void libraryUpReturnsToTheLastRowInTheColumnFocusLeft() {
        assertEquals(5, FocusRules.fromLibraryUp(1, 6, 4));
        assertEquals("a short last row: its last tile", 5, FocusRules.fromLibraryUp(3, 6, 4));
        assertEquals(10, FocusRules.fromLibraryUp(2, 11, 4));
        assertEquals("one row: the same column", 2, FocusRules.fromLibraryUp(2, 7, 7));
    }

    @Test
    public void barDownReturnsToTheFirstRowTileUpCameFrom() {
        assertEquals(1, FocusRules.fromBarDown(5, 4, false));
        assertEquals(3, FocusRules.fromBarDown(3, 4, false));
        assertEquals("a single row keeps the position itself", 5, FocusRules.fromBarDown(5, 4, true));
    }
}
