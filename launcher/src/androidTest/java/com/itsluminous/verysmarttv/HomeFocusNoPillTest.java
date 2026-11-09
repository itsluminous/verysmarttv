package com.itsluminous.verysmarttv;

import static com.itsluminous.verysmarttv.support.HomeTestRule.LIBRARY;
import static com.itsluminous.verysmarttv.support.HomeTestRule.PILL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.TILE;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.TestTheme;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The D-pad on Home without the score pill (DESIGN.md §14.3 HomeFocusNoPillTest; the pill is Phase 3), written on
 * the undecomposed HomeActivity so the Phase 2 moves are checked behaviour for behaviour. Each test is one path:
 * a start tile, then keys with the focus expected after each. The rules it pins, as today's
 * {@code onTileVerticalKey}, the pills' key listener, {@code onLibraryUp} and {@code focusLibrary} have them:
 *
 * <ul>
 *   <li>Left/Right stop at a row's ends; Right at the last tile goes to the All apps pill when there is one.
 *   <li>Up from the first row leaves for the top bar; Down from a pill returns to the first-row tile in the column
 *       focus left from, not to the last pill's own geometry; Up on a pill stays; Left/Right stop at the bar's ends.
 *   <li>Up from a lower row goes a row up; Down goes a row down, to the last tile when the row is short, and from the
 *       last row to the All apps pill; Up from that pill returns to the last row in the column focus left from.
 *   <li>uniform_rows: Down scrolls the focused row up to the shelf's line, and the shelf names the focused app.
 * </ul>
 *
 * The grid holds only the dummy tiles (Tile 1 to Tile N, in that order), so a position's label is known.
 */
@RunWith(AndroidJUnit4.class)
public class HomeFocusNoPillTest {
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;

    @Rule public final HomeTestRule home = new HomeTestRule();

    private static String tile(int position) {
        return TILE + position;
    }

    /** Six apps, four columns: a full dock and two tiles under it. */
    private void sixApps() throws Exception {
        start(new TestTheme().columns(4), 6);
    }

    /** Seven apps, four columns, the second row kept on screen under the dock. */
    private void sevenApps() throws Exception {
        start(new TestTheme().columns(4).rowsBelowDock(1), 7);
    }

    private void start(TestTheme theme, int tiles) throws Exception {
        DummyTiles.enable(tiles);
        home.pushTheme(theme);
        home.launchHome();
        home.focusTile(0);
    }

    /** Presses each key and checks the focus that follows; a repeat of the previous expectation means "stays". */
    private void path(Object... keyThenFocus) {
        String at = home.focus();
        for (int i = 0; i < keyThenFocus.length; i += 2) {
            int key = (Integer) keyThenFocus[i];
            String expected = (String) keyThenFocus[i + 1];
            String after = KeyEvent.keyCodeToString(key) + " from " + at;
            home.press(key);
            if (expected.equals(at)) home.expectFocusStays(expected, after);
            else home.expectFocus(expected, after);
            at = PILL.equals(expected) ? home.focus() : expected;
        }
    }

    @Test
    public void sixApps_topLeftCorner() throws Exception {
        sixApps();
        home.expectShelfName(DummyTiles.label(1));
        path(LEFT, tile(0), UP, PILL, DOWN, tile(0), DOWN, tile(4), UP, tile(0));
    }

    @Test
    public void sixApps_topRightCorner() throws Exception {
        sixApps();
        path(RIGHT, tile(1), RIGHT, tile(2), RIGHT, tile(3), RIGHT, tile(3), UP, PILL, DOWN, tile(3), DOWN, tile(5));
        home.expectShelfHidden();   // under the dock only the glow follows the tile
    }

    @Test
    public void sixApps_bottomLeftCorner() throws Exception {
        sixApps();
        path(DOWN, tile(4), LEFT, tile(4), UP, tile(0), DOWN, tile(4), DOWN, LIBRARY, UP, tile(4));
    }

    @Test
    public void sixApps_bottomRightCorner_rightAtTheRowEndOpensAllApps() throws Exception {
        sixApps();
        path(DOWN, tile(4), RIGHT, tile(5), RIGHT, LIBRARY, UP, tile(5), DOWN, LIBRARY,
                LEFT, LIBRARY, RIGHT, LIBRARY, DOWN, LIBRARY, UP, tile(5), UP, tile(1));
    }

    @Test
    public void sixApps_middleTile() throws Exception {
        sixApps();
        path(RIGHT, tile(1), DOWN, tile(5), UP, tile(1), LEFT, tile(0), RIGHT, tile(1), RIGHT, tile(2), DOWN, tile(5));
    }

    @Test
    public void sixApps_barEndsStayAndDownReturnsToTheColumnUpLeft() throws Exception {
        sixApps();
        path(RIGHT, tile(1), UP, PILL);
        List<String> pills = pillsInBarOrder();
        assertTrue("the bar shows at least two pills: " + pills, pills.size() >= 2);
        String first = PILL + " " + pills.get(0), last = PILL + " " + pills.get(pills.size() - 1);
        for (int i = 0; i < pills.size() && !home.focus().equals(first); i++) {
            home.press(LEFT);
            home.expectFocus(PILL, "LEFT along the bar");
        }
        home.expectFocus(first, "LEFT to the bar's first pill");
        path(LEFT, first, UP, first);
        for (int i = 0; i < pills.size() && !home.focus().equals(last); i++) {
            home.press(RIGHT);
            home.expectFocus(PILL, "RIGHT along the bar");
        }
        home.expectFocus(last, "RIGHT to the bar's last pill");
        path(RIGHT, last, UP, last, DOWN, tile(1));
    }

    @Test
    public void sevenApps_cornersAndMiddle() throws Exception {
        sevenApps();
        path(DOWN, tile(4), LEFT, tile(4), RIGHT, tile(5), RIGHT, tile(6), RIGHT, LIBRARY, UP, tile(6),
                UP, tile(2), RIGHT, tile(3), DOWN, tile(6), LEFT, tile(5), UP, tile(1), UP, PILL, DOWN, tile(1),
                RIGHT, tile(2), RIGHT, tile(3), RIGHT, tile(3), UP, PILL, DOWN, tile(3));
    }

    @Test
    public void sevenColumns_oneFullRow_rightAtTheEndOpensAllApps() throws Exception {
        start(new TestTheme().columns(7), 7);
        path(LEFT, tile(0), RIGHT, tile(1), RIGHT, tile(2), RIGHT, tile(3), RIGHT, tile(4), RIGHT, tile(5), RIGHT, tile(6),
                RIGHT, LIBRARY, UP, tile(6), DOWN, LIBRARY, UP, tile(6), UP, PILL, DOWN, tile(6), DOWN, LIBRARY);
        home.expectShelfHidden();   // the All apps pill has no shelf name
    }

    @Test
    public void uniformRows_downScrollsTheRowToTheShelfLineAndTheShelfNamesTheApp() throws Exception {
        start(new TestTheme().columns(4).uniformRows(), 7);
        home.expectShelfName(DummyTiles.label(1));
        path(DOWN, tile(4));
        home.expectShelfName(DummyTiles.label(5));
        home.waitFor("row 2 to park on the shelf's line", a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(4);
            float density = a.getResources().getDisplayMetrics().density;
            return h != null && Math.abs(h.itemView.getTop() - grid.getPaddingTop()) <= 8 * density;
        });
        Map<Integer, Integer> tops = home.laidOutTops();
        assertTrue("the first row slid out above the line: " + tops, !tops.containsKey(0) || tops.get(0) < tops.get(4));
        path(RIGHT, tile(5), RIGHT, tile(6), RIGHT, LIBRARY, UP, tile(6), UP, tile(2), UP, PILL, DOWN, tile(2));
        home.expectShelfName(DummyTiles.label(3));
    }

    /** The visible top-bar pills' id names in bar order, read from the activity. */
    private List<String> pillsInBarOrder() {
        return home.onHome(a -> {
            ViewGroup bar = a.findViewById(R.id.topButtons);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < bar.getChildCount(); i++) {
                View pill = bar.getChildAt(i);
                if (pill.getVisibility() == View.VISIBLE) names.add(a.getResources().getResourceEntryName(pill.getId()));
            }
            return names;
        });
    }
}
