package com.itsluminous.verysmarttv.topbar;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The carried top-bar focus rules as a table (DESIGN.md §9.3 without the score pill, which Phase 3 adds as one more
 * Stop): Up on a pill stays, Down returns to the grid, Left and Right stop at the bar's visual ends and otherwise go
 * to the framework, Up from the carousel lands on the first pill after Inputs, Up from a dock tile reaches the
 * carousel only while it shows, and the RTL mirror of the ends.
 */
public class TopBarFocusTest {
    private static final int INPUTS = 1, APP = 2, NETWORK = 3, VPN = 4, REORDER = 5, LIBRARY = 6, SETTINGS = 7;
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;

    /** The layout's bar: Inputs, an app button, network, VPN, reorder, library, Settings; shown as given. */
    private static TopBarFocus.Bar bar(boolean inputs, boolean app, boolean network, boolean vpn, boolean reorder, boolean library) {
        List<TopBarFocus.Pill> pills = Arrays.asList(
                new TopBarFocus.Pill(INPUTS, inputs), new TopBarFocus.Pill(APP, app), new TopBarFocus.Pill(NETWORK, network),
                new TopBarFocus.Pill(VPN, vpn), new TopBarFocus.Pill(REORDER, reorder), new TopBarFocus.Pill(LIBRARY, library),
                new TopBarFocus.Pill(SETTINGS, true));
        return new TopBarFocus.Bar(pills, INPUTS, SETTINGS);
    }

    private static final TopBarFocus.Bar DEFAULT = bar(true, false, true, false, false, false);

    private static String on(int key, int focused, TopBarFocus.Bar bar, boolean rtl) {
        return TopBarFocus.onPillKey(key, focused, bar, rtl).toString();
    }

    @Test
    public void upStaysAndDownReturnsToTheGrid() {
        for (int pill : new int[]{INPUTS, NETWORK, SETTINGS}) {
            assertEquals("STAY", on(UP, pill, DEFAULT, false));
            assertEquals("GRID", on(DOWN, pill, DEFAULT, false));
        }
    }

    @Test
    public void leftAndRightStopAtTheBarsEnds() {
        assertEquals("the first pill: Left stays", "STAY", on(LEFT, INPUTS, DEFAULT, false));
        assertEquals("FRAMEWORK", on(RIGHT, INPUTS, DEFAULT, false));
        assertEquals("FRAMEWORK", on(LEFT, NETWORK, DEFAULT, false));
        assertEquals("FRAMEWORK", on(RIGHT, NETWORK, DEFAULT, false));
        assertEquals("Settings is the last pill: Right stays", "STAY", on(RIGHT, SETTINGS, DEFAULT, false));
        assertEquals("FRAMEWORK", on(LEFT, SETTINGS, DEFAULT, false));
    }

    @Test
    public void rtlMirrorsTheEnds() {
        assertEquals("STAY", on(RIGHT, INPUTS, DEFAULT, true));
        assertEquals("FRAMEWORK", on(LEFT, INPUTS, DEFAULT, true));
        assertEquals("STAY", on(LEFT, SETTINGS, DEFAULT, true));
        assertEquals("FRAMEWORK", on(RIGHT, SETTINGS, DEFAULT, true));
        assertEquals("Up and Down are not mirrored", "GRID", on(DOWN, NETWORK, DEFAULT, true));
    }

    @Test
    public void withoutInputsTheFirstShownPillIsTheEnd() {
        TopBarFocus.Bar noInputs = bar(false, false, true, false, false, false);
        assertEquals(NETWORK, TopBarFocus.firstButton(noInputs));
        assertEquals("STAY", on(LEFT, NETWORK, noInputs, false));
        assertEquals("the hidden Inputs pill is no end any more", "FRAMEWORK", on(LEFT, INPUTS, noInputs, false));
        TopBarFocus.Bar onlySettings = bar(false, false, false, false, false, false);
        assertEquals(SETTINGS, TopBarFocus.firstButton(onlySettings));
        assertEquals("STAY", on(LEFT, SETTINGS, onlySettings, false));
        assertEquals("STAY", on(RIGHT, SETTINGS, onlySettings, false));
    }

    @Test
    public void firstTopButtonSkipsInputsAndTakesTheFirstShownPill() {
        assertEquals(NETWORK, TopBarFocus.firstTopButton(DEFAULT));
        assertEquals("a top_bar_apps button comes before the network pill", APP, TopBarFocus.firstTopButton(bar(true, true, true, false, false, false)));
        assertEquals("with the network pill hidden: the next shown one", REORDER, TopBarFocus.firstTopButton(bar(true, false, false, false, true, true)));
        assertEquals("Settings is always there", SETTINGS, TopBarFocus.firstTopButton(bar(true, false, false, false, false, false)));
        assertEquals("Inputs itself never counts", SETTINGS, TopBarFocus.firstTopButton(bar(true, false, false, false, false, false)));
    }

    @Test
    public void upFromTheCarouselLandsOnTheFirstPillAfterInputs() {
        assertEquals("PILL(" + NETWORK + ")", TopBarFocus.fromCarouselUp(DEFAULT).toString());
        assertEquals("PILL(" + SETTINGS + ")", TopBarFocus.fromCarouselUp(bar(true, false, false, false, false, false)).toString());
        assertEquals(TopBarFocus.Stop.PILL, TopBarFocus.fromCarouselUp(DEFAULT).stop);
    }

    @Test
    public void upFromATileReachesTheCarouselOnlyFromTheDockWhileItShows() {
        assertEquals("CAROUSEL", TopBarFocus.fromGridUp(true, true).toString());
        assertEquals("a grid row: Up goes to the row above, not the shelf", "FRAMEWORK", TopBarFocus.fromGridUp(true, false).toString());
        assertEquals("no carousel: the framework's focus search", "FRAMEWORK", TopBarFocus.fromGridUp(false, true).toString());
        assertEquals("FRAMEWORK", TopBarFocus.fromGridUp(false, false).toString());
    }

    @Test
    public void otherKeysGoToTheFramework() {
        assertEquals("FRAMEWORK", on(KeyEvent.KEYCODE_DPAD_CENTER, NETWORK, DEFAULT, false));
    }
}
