package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The seven top-bar focus rules of DESIGN.md §9.3 as a table (ADR-017), each with the score pill shown and hidden and
 * the carousel showing and not: Up from the grid, Up from the score pill and from a round pill, Down, Left/Right on the
 * score pill and along the bar, the carousel as a stop, Back, and the RTL mirror of the bar's ends.
 */
public class TopBarFocusTest {
    private static final int INPUTS = 1, APP = 2, NETWORK = 3, VPN = 4, REORDER = 5, LIBRARY = 6, SETTINGS = 7;
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final int BACK = KeyEvent.KEYCODE_BACK;

    /** The layout's bar: Inputs, an app button, network, VPN, reorder, library, Settings; shown as given; the score pill as given. */
    private static TopBarFocus.Bar bar(boolean inputs, boolean app, boolean network, boolean vpn, boolean reorder, boolean library, boolean scorePill) {
        List<TopBarFocus.Pill> pills = Arrays.asList(
                new TopBarFocus.Pill(INPUTS, inputs), new TopBarFocus.Pill(APP, app), new TopBarFocus.Pill(NETWORK, network),
                new TopBarFocus.Pill(VPN, vpn), new TopBarFocus.Pill(REORDER, reorder), new TopBarFocus.Pill(LIBRARY, library),
                new TopBarFocus.Pill(SETTINGS, true));
        return new TopBarFocus.Bar(pills, INPUTS, SETTINGS, scorePill);
    }

    private static final TopBarFocus.Bar PILL_SHOWN = bar(true, false, true, false, false, false, true);
    private static final TopBarFocus.Bar PILL_HIDDEN = bar(true, false, true, false, false, false, false);
    private static final TopBarFocus.Bar NO_INPUTS_PILL_SHOWN = bar(false, false, true, false, false, false, true);
    private static final TopBarFocus.Bar NO_INPUTS_PILL_HIDDEN = bar(false, false, true, false, false, false, false);

    private static String pill(int id) {
        return "PILL(" + id + ")";
    }

    private static String on(int key, int focused, TopBarFocus.Bar bar, boolean rtl) {
        return TopBarFocus.onPillKey(key, focused, bar, rtl).toString();
    }

    private static String onScore(int key, TopBarFocus.Bar bar, boolean carousel) {
        return TopBarFocus.onScorePillKey(key, bar, carousel).toString();
    }

    private static String fromGrid(TopBarFocus.Bar bar, boolean carousel, boolean dockTile) {
        return TopBarFocus.fromGridUp(bar, carousel, dockTile).toString();
    }

    // ---- rule 1: Up from the first row ---------------------------------------------------------------

    @Test
    public void rule1_upFromTheGridLandsOnTheScorePillWhenShown() {
        assertEquals("SCORE_PILL", fromGrid(PILL_SHOWN, false, true));
        assertEquals("a uniform_rows row 0 or a non-dock first row: the same", "SCORE_PILL", fromGrid(PILL_SHOWN, false, false));
    }

    @Test
    public void rule1_upFromTheGridLandsOnInputsWhenThePillIsHidden() {
        assertEquals(pill(INPUTS), fromGrid(PILL_HIDDEN, false, true));
        assertEquals(pill(INPUTS), fromGrid(PILL_HIDDEN, false, false));
    }

    @Test
    public void rule1_withoutInputsTheFirstShownPillStandsIn() {
        assertEquals(pill(NETWORK), fromGrid(NO_INPUTS_PILL_HIDDEN, false, true));
        assertEquals("a top_bar_apps button before the network pill", pill(APP), fromGrid(bar(false, true, true, false, false, false, false), false, true));
        assertEquals("Settings is always there", pill(SETTINGS), fromGrid(bar(false, false, false, false, false, false, false), false, true));
        assertEquals("the score pill still comes first", "SCORE_PILL", fromGrid(NO_INPUTS_PILL_SHOWN, false, true));
    }

    @Test
    public void rule1_neverTheFrameworksFocusSearch() {
        for (TopBarFocus.Bar bar : new TopBarFocus.Bar[]{PILL_SHOWN, PILL_HIDDEN, NO_INPUTS_PILL_SHOWN, NO_INPUTS_PILL_HIDDEN}) {
            for (boolean carousel : new boolean[]{false, true}) {
                for (boolean dock : new boolean[]{false, true}) {
                    TopBarFocus.Stop stop = TopBarFocus.fromGridUp(bar, carousel, dock).stop;
                    assertEquals("Up from the grid is never left to the framework (" + bar.scorePillShown + "," + carousel + "," + dock + ")",
                            false, stop == TopBarFocus.Stop.FRAMEWORK || stop == TopBarFocus.Stop.STAY);
                }
            }
        }
    }

    // ---- rule 2: Up from the score pill, Up from a round pill -------------------------------------------

    @Test
    public void rule2_upFromTheScorePillGoesToInputsAlways() {
        assertEquals(pill(INPUTS), onScore(UP, PILL_SHOWN, false));
        assertEquals("the carousel showing changes nothing above the pill", pill(INPUTS), onScore(UP, PILL_SHOWN, true));
        assertEquals("without Inputs: the first shown pill", pill(NETWORK), onScore(UP, NO_INPUTS_PILL_SHOWN, false));
    }

    @Test
    public void rule2_upFromARoundPillStays() {
        for (int pill : new int[]{INPUTS, NETWORK, SETTINGS}) {
            assertEquals("STAY", on(UP, pill, PILL_SHOWN, false));
            assertEquals("STAY", on(UP, pill, PILL_HIDDEN, false));
        }
    }

    // ---- rule 3: Down ------------------------------------------------------------------------------

    @Test
    public void rule3_downFromTheScorePillOrAnyPillReturnsToTheGrid() {
        assertEquals("GRID", onScore(DOWN, PILL_SHOWN, false));
        for (int pill : new int[]{INPUTS, NETWORK, SETTINGS}) {
            assertEquals("GRID", on(DOWN, pill, PILL_SHOWN, false));
            assertEquals("GRID", on(DOWN, pill, PILL_HIDDEN, false));
            assertEquals("the carousel is below the pill, not below the bar's pills", "GRID", on(DOWN, pill, PILL_SHOWN, true));
        }
    }

    // ---- rule 4: Left/Right ---------------------------------------------------------------------------

    @Test
    public void rule4_leftAndRightOnTheScorePillAreSwallowed() {
        assertEquals("STAY", onScore(LEFT, PILL_SHOWN, false));
        assertEquals("STAY", onScore(RIGHT, PILL_SHOWN, false));
        assertEquals("STAY", onScore(LEFT, PILL_SHOWN, true));
    }

    @Test
    public void rule4_leftAndRightWalkTheBarAndStopAtItsEnds() {
        assertEquals("the first pill: Left stays", "STAY", on(LEFT, INPUTS, PILL_HIDDEN, false));
        assertEquals("FRAMEWORK", on(RIGHT, INPUTS, PILL_HIDDEN, false));
        assertEquals("FRAMEWORK", on(LEFT, NETWORK, PILL_HIDDEN, false));
        assertEquals("FRAMEWORK", on(RIGHT, NETWORK, PILL_HIDDEN, false));
        assertEquals("Settings is the last pill: Right stays", "STAY", on(RIGHT, SETTINGS, PILL_HIDDEN, false));
        assertEquals("FRAMEWORK", on(LEFT, SETTINGS, PILL_HIDDEN, false));
        assertEquals("the score pill, under the bar, is no stop of the walk", "STAY", on(RIGHT, SETTINGS, PILL_SHOWN, false));
    }

    @Test
    public void rule4_withoutInputsTheFirstShownPillIsTheEnd() {
        assertEquals(NETWORK, TopBarFocus.firstButton(NO_INPUTS_PILL_HIDDEN));
        assertEquals("STAY", on(LEFT, NETWORK, NO_INPUTS_PILL_HIDDEN, false));
        assertEquals("the hidden Inputs pill is no end any more", "FRAMEWORK", on(LEFT, INPUTS, NO_INPUTS_PILL_HIDDEN, false));
        TopBarFocus.Bar onlySettings = bar(false, false, false, false, false, false, false);
        assertEquals(SETTINGS, TopBarFocus.firstButton(onlySettings));
        assertEquals("STAY", on(LEFT, SETTINGS, onlySettings, false));
        assertEquals("STAY", on(RIGHT, SETTINGS, onlySettings, false));
    }

    // ---- rule 5: the carousel as a stop ------------------------------------------------------------------

    @Test
    public void rule5_upFromADockTileReachesTheCarouselWhileItShows() {
        assertEquals("CAROUSEL", fromGrid(PILL_SHOWN, true, true));
        assertEquals("CAROUSEL", fromGrid(PILL_HIDDEN, true, true));
        assertEquals("a grid row, not the dock: past the carousel to the pill", "SCORE_PILL", fromGrid(PILL_SHOWN, true, false));
        assertEquals(pill(INPUTS), fromGrid(PILL_HIDDEN, true, false));
    }

    @Test
    public void rule5_upFromTheCarouselLandsOnTheScorePillWhenShownElseInputs() {
        assertEquals("SCORE_PILL", TopBarFocus.fromCarouselUp(PILL_SHOWN).toString());
        assertEquals(pill(INPUTS), TopBarFocus.fromCarouselUp(PILL_HIDDEN).toString());
        assertEquals(pill(NETWORK), TopBarFocus.fromCarouselUp(NO_INPUTS_PILL_HIDDEN).toString());
    }

    @Test
    public void rule5_downFromTheScorePillLandsOnTheCarouselWhileItShows() {
        assertEquals("CAROUSEL", onScore(DOWN, PILL_SHOWN, true));
        assertEquals("GRID", onScore(DOWN, PILL_SHOWN, false));
    }

    // ---- rule 6: Back -------------------------------------------------------------------------------

    @Test
    public void rule6_backAnywhereInTheBarReturnsToTheGrid() {
        assertEquals("GRID", onScore(BACK, PILL_SHOWN, false));
        assertEquals("even while the carousel shows: Back is the grid, Down the carousel", "GRID", onScore(BACK, PILL_SHOWN, true));
        for (int pill : new int[]{INPUTS, NETWORK, SETTINGS}) {
            assertEquals("GRID", on(BACK, pill, PILL_SHOWN, false));
            assertEquals("GRID", on(KeyEvent.KEYCODE_ESCAPE, pill, PILL_HIDDEN, false));
        }
    }

    // ---- rule 7: RTL ---------------------------------------------------------------------------------

    @Test
    public void rule7_rtlMirrorsTheBarsEndsOnly() {
        assertEquals("STAY", on(RIGHT, INPUTS, PILL_HIDDEN, true));
        assertEquals("FRAMEWORK", on(LEFT, INPUTS, PILL_HIDDEN, true));
        assertEquals("STAY", on(LEFT, SETTINGS, PILL_HIDDEN, true));
        assertEquals("FRAMEWORK", on(RIGHT, SETTINGS, PILL_HIDDEN, true));
        assertEquals("Up and Down are not mirrored", "GRID", on(DOWN, NETWORK, PILL_HIDDEN, true));
        assertEquals("STAY", on(UP, NETWORK, PILL_SHOWN, true));
    }

    @Test
    public void rule7_theScorePillAndTheUpRulesNeedNoRtlBranch() {
        // Expressed in bar order: the verdicts carry no screen side, so the same bar gives the same answer either way.
        assertEquals("SCORE_PILL", fromGrid(PILL_SHOWN, false, true));
        assertEquals("STAY", onScore(LEFT, PILL_SHOWN, false));
        assertEquals("STAY", onScore(RIGHT, PILL_SHOWN, false));
        assertEquals(pill(INPUTS), onScore(UP, PILL_SHOWN, false));
    }

    // ---- the fallbacks the rules share -----------------------------------------------------------------

    @Test
    public void firstTopButtonSkipsInputsAndTakesTheFirstShownPill() {
        assertEquals(NETWORK, TopBarFocus.firstTopButton(PILL_HIDDEN));
        assertEquals("a top_bar_apps button comes before the network pill", APP, TopBarFocus.firstTopButton(bar(true, true, true, false, false, false, false)));
        assertEquals("with the network pill hidden: the next shown one", REORDER, TopBarFocus.firstTopButton(bar(true, false, false, false, true, true, false)));
        assertEquals("Settings is always there", SETTINGS, TopBarFocus.firstTopButton(bar(true, false, false, false, false, false, false)));
    }

    @Test
    public void otherKeysGoToTheFramework() {
        assertEquals("FRAMEWORK", on(KeyEvent.KEYCODE_DPAD_CENTER, NETWORK, PILL_HIDDEN, false));
        assertEquals("FRAMEWORK", onScore(KeyEvent.KEYCODE_DPAD_CENTER, PILL_SHOWN, false));
    }
}
