package com.itsluminous.verysmarttv.topbar;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Focus on the top bar (DESIGN.md §9.3, ADR-017, §8 rule 2): where Up from the grid lands, where the keys on the score
 * pill and the round pills go, where Up from the carousel lands, and which pill the grid falls back to. Pure;
 * {@code TopBarController}, {@code ScorePillController}, {@code GridController} and {@code CarouselController} build a
 * {@link Bar} from the views and execute the verdict. Expressed in bar order, so RTL needs no branch of its own: the
 * framework mirrors the children, and the ends are named by {@code rtl} (rule 7).
 *
 * <p>The rules, each a row of {@code TopBarFocusTest}:
 * <ol>
 *   <li>Up from a first-row tile: the score pill when shown, else Inputs (the first shown pill when Inputs is hidden).
 *       Never the pill focused last.
 *   <li>Up from the score pill: Inputs (same fallback). Up from a round pill: stays.
 *   <li>Down from the score pill or a round pill: the grid (the first-row tile Up came from).
 *   <li>Left/Right on the score pill: swallowed. Left/Right on the round pills: along the bar, ends swallowed.
 *   <li>The carousel is a stop between the grid and the pill: Up from a dock tile reaches it while it shows; Up from it
 *       lands on the score pill when shown, else Inputs; Down from the score pill lands on it while it shows.
 *   <li>Back on the score pill or a round pill: the grid.
 *   <li>RTL: bar order, not screen side; the score pill is alone on its row and needs no mirror.
 *   <li>The score pill stacks {@link Bar#scoreRows} lines and is one stop (ADR-041): Up from the grid or the carousel
 *       lands on its bottom line (row {@code scoreRows - 1}, nearest the grid); inside it Up and Down move a line at a
 *       time; Up from the top line (row 0) is rule 2, Down from the bottom line is rule 3.
 * </ol>
 */
public final class TopBarFocus {
    private TopBarFocus() {
    }

    /** Where focus goes. */
    public enum Stop {
        /** Stay where it is (Up on a round pill, Left or Right at the bar's end or on the score pill). */
        STAY,
        /** The framework's focus search takes the key (Left or Right along the bar). */
        FRAMEWORK,
        /** The grid: the first-row tile Up came from (GridController remembers it). */
        GRID,
        /** The carousel on the shelf. */
        CAROUSEL,
        /** A round pill, named by {@link Verdict#pillId}. */
        PILL,
        /** The score pill under the bar. */
        SCORE_PILL
    }

    public static final class Verdict {
        public final Stop stop;
        /** The pill's view id for {@link Stop#PILL}; 0 otherwise. */
        public final int pillId;
        /** The line for {@link Stop#SCORE_PILL}, 0 the top one; 0 otherwise. */
        public final int row;

        private Verdict(Stop stop, int pillId, int row) {
            this.stop = stop;
            this.pillId = pillId;
            this.row = row;
        }

        static final Verdict STAY = new Verdict(Stop.STAY, 0, 0);
        static final Verdict FRAMEWORK = new Verdict(Stop.FRAMEWORK, 0, 0);
        static final Verdict GRID = new Verdict(Stop.GRID, 0, 0);
        static final Verdict CAROUSEL = new Verdict(Stop.CAROUSEL, 0, 0);

        static Verdict pill(int id) {
            return new Verdict(Stop.PILL, id, 0);
        }

        static Verdict scorePill(int row) {
            return new Verdict(Stop.SCORE_PILL, 0, row);
        }

        @Override
        @NonNull
        public String toString() {
            if (stop == Stop.PILL) return stop + "(" + pillId + ")";
            if (stop == Stop.SCORE_PILL) return stop + "(" + row + ")";
            return stop.toString();
        }
    }

    /** One pill as the rule sees it. */
    public static final class Pill {
        public final int id;
        public final boolean visible;

        public Pill(int id, boolean visible) {
            this.id = id;
            this.visible = visible;
        }
    }

    /** The bar: its pills in child (bar) order, which of them are Inputs and Settings, and how many score lines show. */
    public static final class Bar {
        public final List<Pill> pills;
        public final int inputsId;
        public final int settingsId;
        /** The lines the score pill shows (score_pill on, the companion answering rows worth showing); 0 while it is hidden. */
        public final int scoreRows;
        /** The score pill is on screen: {@link #scoreRows} is above 0. */
        public final boolean scorePillShown;

        public Bar(@NonNull List<Pill> pills, int inputsId, int settingsId, int scoreRows) {
            this.pills = pills;
            this.inputsId = inputsId;
            this.settingsId = settingsId;
            this.scoreRows = Math.max(0, scoreRows);
            this.scorePillShown = this.scoreRows > 0;
        }

        boolean inputsShown() {
            for (Pill p : pills) if (p.id == inputsId) return p.visible;
            return false;
        }
    }

    /** The first shown pill after Inputs, in bar order (top_bar_apps, the network pill, ...); Settings is always there. */
    public static int firstTopButton(@NonNull Bar bar) {
        for (Pill p : bar.pills) {
            if (p.id != bar.inputsId && p.visible) return p.id;
        }
        return bar.settingsId;
    }

    /** The bar's first pill: Inputs while it shows, else {@link #firstTopButton}. */
    public static int firstButton(@NonNull Bar bar) {
        return bar.inputsShown() ? bar.inputsId : firstTopButton(bar);
    }

    /** The stop above the grid and the carousel: the score pill's bottom line when shown, else Inputs (rules 1, 2, 5 and 8). */
    @NonNull
    static Verdict aboveGrid(@NonNull Bar bar) {
        return bar.scorePillShown ? Verdict.scorePill(bar.scoreRows - 1) : Verdict.pill(firstButton(bar));
    }

    /**
     * Up from a first-row tile (rules 1 and 5): the carousel while it shows and the tile is in the dock row; else the
     * score pill when shown; else Inputs. Never the framework's focus search, which would restore the pill focused last.
     */
    @NonNull
    public static Verdict fromGridUp(@NonNull Bar bar, boolean carouselShowing, boolean dockTileFocused) {
        if (carouselShowing && dockTileFocused) return Verdict.CAROUSEL;
        return aboveGrid(bar);
    }

    /** Up from the carousel (rule 5): the score pill when shown, else Inputs. */
    @NonNull
    public static Verdict fromCarouselUp(@NonNull Bar bar) {
        return aboveGrid(bar);
    }

    /**
     * A D-pad or Back key down on line {@code row} of the score pill (rules 2, 3, 4, 6, 8): Up goes a line up, or to
     * Inputs from the top line; Down a line down, or from the bottom line to the carousel while it shows else the grid;
     * Left and Right stay (the pill is alone on its rows); Back goes to the grid. A {@code row} outside the lines shown
     * is read as the nearer end.
     */
    @NonNull
    public static Verdict onScorePillKey(int keyCode, int row, @NonNull Bar bar, boolean carouselShowing) {
        int at = Math.max(0, Math.min(row, bar.scoreRows - 1));
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return at > 0 ? Verdict.scorePill(at - 1) : Verdict.pill(firstButton(bar));
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (at < bar.scoreRows - 1) return Verdict.scorePill(at + 1);
                return carouselShowing ? Verdict.CAROUSEL : Verdict.GRID;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Verdict.STAY;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                return Verdict.GRID;
            default:
                return Verdict.FRAMEWORK;
        }
    }

    /**
     * A D-pad or Back key down on the round pill {@code focusedId} (rules 2, 3, 4, 6): Up stays; Down and Back go back
     * to the grid; Left and Right stop at the bar's visual ends and otherwise go to the framework's focus search,
     * which walks the bar.
     */
    @NonNull
    public static Verdict onPillKey(int keyCode, int focusedId, @NonNull Bar bar, boolean rtl) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return Verdict.STAY;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) return Verdict.GRID;
        int firstButton = firstButton(bar);
        // The bar's visual ends: the framework mirrors child order for a right-to-left locale.
        int leftButton = rtl ? bar.settingsId : firstButton;
        int rightButton = rtl ? firstButton : bar.settingsId;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return focusedId == leftButton ? Verdict.STAY : Verdict.FRAMEWORK;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return focusedId == rightButton ? Verdict.STAY : Verdict.FRAMEWORK;
        return Verdict.FRAMEWORK;
    }
}
