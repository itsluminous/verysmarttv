package com.itsluminous.verysmarttv.topbar;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Focus on the top bar (DESIGN.md §9.3, §8 rule 2): where Up, Down, Left and Right go from a pill, where Up from the
 * carousel lands, and which pill the grid falls back to. Pure; {@code TopBarController} builds a {@link Bar} from
 * its views and executes the verdict. Expressed in bar order, so RTL needs no branch of its own: the framework
 * mirrors the children, and the ends are named by {@code rtl}.
 *
 * <p>Phase 3 adds the score pill as one more {@link Stop} and one more flag on the bar (DESIGN.md §9.3 rule 1);
 * the rules here are the carried ones, without it.
 */
public final class TopBarFocus {
    private TopBarFocus() {
    }

    /** Where focus goes. */
    public enum Stop {
        /** Stay where it is (Up on a pill, Left or Right at the bar's end). */
        STAY,
        /** The framework's focus search takes the key (Left or Right along the bar). */
        FRAMEWORK,
        /** The grid: the first-row tile Up came from (GridController remembers it). */
        GRID,
        /** The carousel on the shelf. */
        CAROUSEL,
        /** A pill, named by {@link Verdict#pillId}. */
        PILL
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

    /** The bar: its pills in child (bar) order, and which of them are Inputs and Settings. */
    public static final class Bar {
        public final List<Pill> pills;
        public final int inputsId;
        public final int settingsId;

        public Bar(@NonNull List<Pill> pills, int inputsId, int settingsId) {
            this.pills = pills;
            this.inputsId = inputsId;
            this.settingsId = settingsId;
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

    /**
     * A D-pad key down on the pill {@code focusedId}: Up stays; Down goes back to the grid; Left and Right stop at the
     * bar's visual ends and otherwise go to the framework's focus search, which walks the bar.
     */
    @NonNull
    public static Verdict onPillKey(int keyCode, int focusedId, @NonNull Bar bar, boolean rtl) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return Verdict.STAY;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return Verdict.GRID;
        int firstButton = firstButton(bar);
        // The bar's visual ends: the framework mirrors child order for a right-to-left locale.
        int leftButton = rtl ? bar.settingsId : firstButton;
        int rightButton = rtl ? firstButton : bar.settingsId;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return focusedId == leftButton ? Verdict.STAY : Verdict.FRAMEWORK;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return focusedId == rightButton ? Verdict.STAY : Verdict.FRAMEWORK;
        return Verdict.FRAMEWORK;
    }

    /** Up from the carousel: the first shown pill after Inputs (the carried rule; Phase 3 puts the score pill first). */
    @NonNull
    public static Verdict fromCarouselUp(@NonNull Bar bar) {
        return Verdict.pill(firstTopButton(bar));
    }

    /**
     * Up from a tile: the carousel when it is showing and the tile is in the dock row; otherwise the framework's focus
     * search (the carried behaviour; Phase 3's rule 1 names the score pill, then Inputs, instead).
     */
    @NonNull
    public static Verdict fromGridUp(boolean carouselShowing, boolean dockTileFocused) {
        return carouselShowing && dockTileFocused ? Verdict.CAROUSEL : Verdict.FRAMEWORK;
    }
}
