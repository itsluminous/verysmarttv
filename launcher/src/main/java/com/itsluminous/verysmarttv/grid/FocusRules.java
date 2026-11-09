package com.itsluminous.verysmarttv.grid;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * Where a D-pad key on a tile sends focus (DESIGN.md §8 rule 2, ADR-007): the arithmetic of
 * {@code GridController.onTileVerticalKey}, pure. The controller hands it the grid's shape and executes the verdict;
 * every branch is a row of {@code FocusRulesTest}.
 */
public final class FocusRules {
    private FocusRules() {
    }

    /** What the controller does with the key. */
    public enum Kind {
        /** Return false: the framework's focus search handles the key (Up from the first row, mid-row Left/Right). */
        PASS,
        /** Return true and do nothing: the key is swallowed at an edge (Down from a single row, the row's ends). */
        SWALLOW,
        /** Focus the App library footer after the last row. */
        LIBRARY,
        /** Focus the tile at {@link Verdict#target} and click. */
        MOVE
    }

    public static final class Verdict {
        public final Kind kind;
        /** The tile to focus for {@link Kind#MOVE}; -1 otherwise. */
        public final int target;

        private Verdict(Kind kind, int target) {
            this.kind = kind;
            this.target = target;
        }

        static final Verdict PASS = new Verdict(Kind.PASS, -1);
        static final Verdict SWALLOW = new Verdict(Kind.SWALLOW, -1);
        static final Verdict LIBRARY = new Verdict(Kind.LIBRARY, -1);

        static Verdict move(int target) {
            return new Verdict(Kind.MOVE, target);
        }

        @Override
        @NonNull
        public String toString() {
            return kind + (kind == Kind.MOVE ? "(" + target + ")" : "");
        }
    }

    /** The grid as the rule sees it. */
    public static final class Grid {
        /** Tiles, the library footer not counted. */
        public final int count;
        public final int columns;
        /** Apps in the dock row: with fill_dock off the empty slots after them take no focus. */
        public final int dockFilled;
        /** The last position that takes focus (an empty dock slot does not). */
        public final int lastFocusable;
        /** The App library footer follows the last tile. */
        public final boolean libraryAtEnd;
        public final boolean rtl;
        /** single_row: every app in one sideways row, no grid below. */
        public final boolean singleRow;

        public Grid(int count, int columns, int dockFilled, int lastFocusable, boolean libraryAtEnd, boolean rtl, boolean singleRow) {
            this.count = count;
            this.columns = columns;
            this.dockFilled = dockFilled;
            this.lastFocusable = lastFocusable;
            this.libraryAtEnd = libraryAtEnd;
            this.rtl = rtl;
            this.singleRow = singleRow;
        }
    }

    /** The verdict for keyCode (a D-pad direction) on the tile at position. */
    @NonNull
    public static Verdict onTileKey(int position, int keyCode, @NonNull Grid g) {
        if (g.singleRow) {
            // One row: Up leaves for the top bar, Down has nowhere to go, and Left/Right walk the whole row,
            // stopping at its two ends (the default focus search scrolls the row to the next tile).
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return Verdict.PASS;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return Verdict.SWALLOW;
            boolean forward = g.rtl ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (forward && position >= g.lastFocusable) {
                return g.libraryAtEnd ? Verdict.LIBRARY : Verdict.SWALLOW;
            }
            return !forward && position == 0 ? Verdict.SWALLOW : Verdict.PASS;
        }
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            boolean forward = g.rtl ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (forward && position == g.lastFocusable && g.libraryAtEnd) return Verdict.LIBRARY;
            // In the dock, the last app is the row's end: the empty slots after it take no focus.
            int rowCount = position < g.columns ? Math.min(g.count, g.dockFilled) : g.count;
            return GridNavigation.atHorizontalEdge(position, rowCount, g.columns, g.rtl, keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                    ? Verdict.SWALLOW : Verdict.PASS;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < g.columns) return Verdict.PASS;          // dock row: let focus move to the top bar
            return Verdict.move(position - g.columns);
        }
        int lastRowStart = ((g.count - 1) / g.columns) * g.columns;
        if (position >= lastRowStart) return g.libraryAtEnd ? Verdict.LIBRARY : Verdict.SWALLOW;
        return Verdict.move(Math.min(position + g.columns, g.count - 1));
    }

    /** Up from the App library footer: the last row, in the column focus left (the first-row tile Up came from). */
    public static int fromLibraryUp(int lastFocused, int count, int columns) {
        int lastRow = ((count - 1) / columns) * columns;
        return Math.min(lastRow + lastFocused % columns, count - 1);
    }

    /** Down from a top-bar pill: back to the first-row tile Up came from, not wherever the focus finder lands. */
    public static int fromBarDown(int lastFocused, int columns, boolean singleRow) {
        return singleRow ? lastFocused : lastFocused % columns;
    }
}
