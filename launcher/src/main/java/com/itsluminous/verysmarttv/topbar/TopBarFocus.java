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

        private Verdict(Stop stop, int pillId) {
            this.stop = stop;
            this.pillId = pillId;
        }

        static final Verdict STAY = new Verdict(Stop.STAY, 0);
        static final Verdict FRAMEWORK = new Verdict(Stop.FRAMEWORK, 0);
        static final Verdict GRID = new Verdict(Stop.GRID, 0);
        static final Verdict CAROUSEL = new Verdict(Stop.CAROUSEL, 0);
        static final Verdict SCORE_PILL = new Verdict(Stop.SCORE_PILL, 0);

        static Verdict pill(int id) {
            return new Verdict(Stop.PILL, id);
        }

        @Override
        @NonNull
        public String toString() {
            return stop + (stop == Stop.PILL ? "(" + pillId + ")" : "");
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

    /** The bar: its pills in child (bar) order, which of them are Inputs and Settings, and whether the score pill shows. */
    public static final class Bar {
        public final List<Pill> pills;
        public final int inputsId;
        public final int settingsId;
        /** The score pill is on screen (score_pill on, the companion answering a row worth showing). */
        public final boolean scorePillShown;

        public Bar(@NonNull List<Pill> pills, int inputsId, int settingsId, boolean scorePillShown) {
            this.pills = pills;
            this.inputsId = inputsId;
            this.settingsId = settingsId;
            this.scorePillShown = scorePillShown;
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

    /** The stop above the grid and the carousel: the score pill when shown, else Inputs (rules 1, 2 and 5). */
    @NonNull
    static Verdict aboveGrid(@NonNull Bar bar) {
        return bar.scorePillShown ? Verdict.SCORE_PILL : Verdict.pill(firstButton(bar));
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
     * A D-pad or Back key down on the score pill (rules 2, 3, 4, 6): Up goes to Inputs, Down to the carousel while it
     * shows else the grid, Left and Right stay (it is alone on its row), Back goes to the grid.
     */
    @NonNull
    public static Verdict onScorePillKey(int keyCode, @NonNull Bar bar, boolean carouselShowing) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return Verdict.pill(firstButton(bar));
            case KeyEvent.KEYCODE_DPAD_DOWN:
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
