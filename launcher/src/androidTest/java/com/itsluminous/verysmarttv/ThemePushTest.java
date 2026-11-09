package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.TestTheme;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * A theme pushed through the {@code .theme} provider is what Home renders next (DESIGN.md §14.3 ThemePushTest). The
 * push is the setup tool's: config.json and a new theme_revision written to
 * {@code content://com.itsluminous.verysmarttv.theme/<name>}, then Home started again (a push restarts Home; here
 * the scenario does). Pinned: {@code columns: 7} lays out the seven tiles in one row;
 * a second push replaces the first; and a push drops the column choice made on the TV, as Config.syncThemeChoices
 * does for a new revision.
 */
@RunWith(AndroidJUnit4.class)
public class ThemePushTest {
    @Rule public final HomeTestRule home = new HomeTestRule();

    @Test
    public void columnsSevenShowsSevenTilesInTheFirstRow() throws Exception {
        DummyTiles.enable(7);
        home.pushTheme(new TestTheme().columns(7));
        home.launchHome();
        assertEquals(7, home.gridColumns());
        assertEquals(7, LauncherState.columns(home.target()));
        Map<Integer, Integer> tops = home.laidOutTops();
        Set<Integer> firstRowTops = new TreeSet<>();
        for (int position = 0; position < 7; position++) {
            assertTrue("tile " + position + " is laid out: " + tops, tops.containsKey(position));
            firstRowTops.add(tops.get(position));
        }
        assertEquals("the seven tiles share one row: " + tops, 1, firstRowTops.size());
        home.focusTile(0);
        home.expectShelfName(DummyTiles.label(1));
    }

    @Test
    public void aSecondPushReplacesTheFirst() throws Exception {
        DummyTiles.enable(7);
        home.pushTheme(new TestTheme().columns(4));
        home.launchHome();
        assertEquals(4, home.gridColumns());
        home.pushTheme(new TestTheme().columns(6));
        home.launchHome();
        assertEquals(6, home.gridColumns());
        Map<Integer, Integer> tops = home.laidOutTops();
        assertTrue("six tiles in the first row: " + tops, tops.containsKey(5) && tops.get(5).equals(tops.get(0)));
        assertFalse("the seventh tile is not in the first row: " + tops, tops.containsKey(6) && tops.get(6).equals(tops.get(0)));
    }

    @Test
    public void aPushDropsTheColumnsChosenOnTheTv() throws Exception {
        DummyTiles.enable(7);
        home.pushTheme(new TestTheme().columns(4));
        home.launchHome();
        // What Launcher settings' Columns row saves: the choice wins over the theme until the next push.
        LauncherState.choices(home.target()).edit().putInt("columns", 5).commit();
        home.launchHome();
        assertEquals("the on-TV choice wins over the pushed theme", 5, home.gridColumns());
        home.pushTheme(new TestTheme().columns(7));
        home.launchHome();
        assertEquals("a new push hands the columns back to the theme", 7, home.gridColumns());
        assertFalse("the choice is gone", LauncherState.choices(home.target()).contains("columns"));
    }
}
