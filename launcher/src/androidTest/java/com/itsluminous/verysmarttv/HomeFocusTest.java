package com.itsluminous.verysmarttv;

import static com.itsluminous.verysmarttv.support.HomeTestRule.CAROUSEL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.PILL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.SCORE_PILL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.TILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.core.contract.HeroContract;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.IntentCapture;
import com.itsluminous.verysmarttv.support.ScoreStandIn;
import com.itsluminous.verysmarttv.support.TestTheme;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * The D-pad on Home with the score pill (DESIGN.md §14.3 HomeFocusTest, §9.3 rules 1 to 7): the live-score stand-in
 * answers {@link ScoreContract#PILL} with {@link ScoreFixture}'s rows through the signature permission, as the real
 * companion would. Up from a dock tile focuses the score pill, Up again Inputs (the first shown pill on an emulator
 * without inputs), Up once more stays; Down returns to the dock tile in the column Up left; Left and Right on the pill
 * stay; Back on the pill or a round pill returns to the grid; the pill reads the row's kicker and line; an empty row
 * hides the pill and Up skips to Inputs; OK sends the companion's scoreboard intent with the event id; and with the
 * carousel showing the order is tile, carousel, pill, Inputs and back.
 */
@RunWith(AndroidJUnit4.class)
public class HomeFocusTest {
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final int BACK = KeyEvent.KEYCODE_BACK;

    @Rule public final HomeTestRule home = new HomeTestRule();

    private IntentCapture intents;

    @Before
    public void standInInstalled() {
        ScoreStandIn.assertInstalled(home.target());
        ScoreStandIn.fixture(home.target(), ScoreFixture.LIVE);
    }

    @After
    public void cleanUp() {
        if (intents != null) intents.stop();
        ScoreStandIn.fixture(home.target(), ScoreFixture.LIVE);
    }

    private void start(TestTheme theme) throws Exception {
        DummyTiles.enable(7);
        home.pushTheme(theme.scorePill().widgets());
        home.launchHome();
        home.focusTile(0);
        home.waitFor("the score pill to show the live fixture", a -> a.findViewById(R.id.scorePill).getVisibility() == View.VISIBLE
                && String.valueOf(((TextView) a.findViewById(R.id.scorePill)).getText()).endsWith(ScoreFixture.LIVE_LINE));
    }

    /** Inputs when it shows (a TV with inputs), else the first shown pill: TopBarFocus.firstButton on this emulator. */
    private String inputsOrFirst() {
        return PILL + " " + home.onHome(a -> {
            ViewGroup bar = a.findViewById(R.id.topButtons);
            if (a.findViewById(R.id.btnInputs).getVisibility() == View.VISIBLE) return "btnInputs";
            for (int i = 0; i < bar.getChildCount(); i++) {
                View pill = bar.getChildAt(i);
                if (pill.getVisibility() == View.VISIBLE) return a.getResources().getResourceEntryName(pill.getId());
            }
            return "btnSettings";
        });
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
            at = expected;
        }
    }

    @Test
    public void rules1to3_upFromTheDockToThePillToInputs_downReturnsToTheColumn() throws Exception {
        start(new TestTheme().columns(4).rowsBelowDock(1));
        String inputs = inputsOrFirst();
        path(UP, SCORE_PILL, UP, inputs, UP, inputs, DOWN, tile(0));
        home.focusTile(3);
        path(UP, SCORE_PILL, DOWN, tile(3));
        assertTrue("the pill is a real focus stop", home.onHome(a -> a.findViewById(R.id.scorePill).isFocusable()));
    }

    @Test
    public void rule1_theLastFocusedPillIsNeverRestoredByUp() throws Exception {
        start(new TestTheme().columns(4));
        String inputs = inputsOrFirst();
        path(UP, SCORE_PILL, UP, inputs);
        // Walk to the bar's last pill, go down, and come back up: the pill, then Inputs, not Settings.
        for (int i = 0; i < 8 && !home.focus().equals(PILL + " btnSettings"); i++) home.press(RIGHT);
        home.expectFocus(PILL + " btnSettings", "RIGHT to the bar's end");
        path(DOWN, tile(0), UP, SCORE_PILL, UP, inputs);
    }

    @Test
    public void rules4and6_leftRightStayOnThePill_backReturnsToTheGridFromThePillAndFromInputs() throws Exception {
        start(new TestTheme().columns(4));
        path(RIGHT, tile(1), UP, SCORE_PILL, LEFT, SCORE_PILL, RIGHT, SCORE_PILL, BACK, tile(1));
        String inputs = inputsOrFirst();
        path(UP, SCORE_PILL, UP, inputs, BACK, tile(1));
    }

    @Test
    public void thePillRendersEachFixtureAndHidesForAnEmptyRow() throws Exception {
        start(new TestTheme().columns(4));
        assertEquals(ScoreFixture.LIVE_KICKER + "  " + ScoreFixture.LIVE_LINE, home.text(R.id.scorePill));
        ScoreStandIn.fixture(home.target(), ScoreFixture.UPCOMING);
        home.waitFor("the upcoming row", a -> String.valueOf(((TextView) a.findViewById(R.id.scorePill)).getText()).endsWith(ScoreFixture.UPCOMING_LINE));
        assertEquals(ScoreFixture.UPCOMING_KICKER + "  " + ScoreFixture.UPCOMING_LINE, home.text(R.id.scorePill));
        ScoreStandIn.fixture(home.target(), ScoreFixture.FINAL);
        home.waitFor("the final row", a -> String.valueOf(((TextView) a.findViewById(R.id.scorePill)).getText()).endsWith(ScoreFixture.FINAL_LINE));
        assertEquals(ScoreFixture.FINAL_KICKER + "  " + ScoreFixture.FINAL_LINE, home.text(R.id.scorePill));
        ScoreStandIn.fixture(home.target(), ScoreFixture.BREAK);
        home.waitFor("the break row", a -> String.valueOf(((TextView) a.findViewById(R.id.scorePill)).getText()).endsWith(ScoreFixture.BREAK_LINE));
        // Empty: GONE, unfocusable, and Up from the dock skips straight to Inputs.
        ScoreStandIn.fixture(home.target(), ScoreFixture.EMPTY);
        home.waitFor("the pill to hide", a -> a.findViewById(R.id.scorePill).getVisibility() == View.GONE);
        path(UP, inputsOrFirst(), DOWN, tile(0));
        // And back: the pill returns, and takes Up again.
        ScoreStandIn.fixture(home.target(), ScoreFixture.LIVE);
        home.waitFor("the pill to return", a -> a.findViewById(R.id.scorePill).getVisibility() == View.VISIBLE);
        path(UP, SCORE_PILL, DOWN, tile(0));
    }

    @Test
    public void aRowThatVanishesUnderFocusHandsFocusBackToTheGrid() throws Exception {
        start(new TestTheme().columns(4));
        path(RIGHT, tile(1), RIGHT, tile(2), UP, SCORE_PILL);
        ScoreStandIn.fixture(home.target(), ScoreFixture.EMPTY);
        home.expectFocus(tile(2), "the pill hid while focused");
    }

    @Test
    public void okOnThePillOpensTheScoreboardWithTheEventId() throws Exception {
        start(new TestTheme().columns(4));
        path(UP, SCORE_PILL);
        intents = IntentCapture.start();
        home.press(KeyEvent.KEYCODE_DPAD_CENTER);
        long end = System.currentTimeMillis() + HomeTestRule.WAIT_MS;
        while (intents.intents().isEmpty() && System.currentTimeMillis() < end) Thread.sleep(100);
        List<Intent> sent = intents.intents();
        assertEquals("OK on the pill starts exactly one activity: " + sent, 1, sent.size());
        assertEquals(ScoreContract.SCOREBOARD_ACTIVITY, sent.get(0).getComponent());
        assertEquals(ScoreFixture.EVENT_ID, sent.get(0).getStringExtra(ScoreContract.EXTRA_EVENT_ID));
        assertTrue("opened in its own task", (sent.get(0).getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void thePillSitsUnderTheEndOfThePillRow() throws Exception {
        start(new TestTheme().columns(4));
        int[] edges = home.onHome(a -> {
            View root = a.findViewById(R.id.root), bar = a.findViewById(R.id.topButtons), pill = a.findViewById(R.id.scorePill);
            Rect b = new Rect(0, 0, bar.getWidth(), bar.getHeight()), p = new Rect(0, 0, pill.getWidth(), pill.getHeight());
            ((ViewGroup) root).offsetDescendantRectToMyCoords(bar, b);
            ((ViewGroup) root).offsetDescendantRectToMyCoords(pill, p);
            return new int[]{b.right, p.right, b.bottom, p.top, Math.round(a.getResources().getDisplayMetrics().density)};
        });
        assertEquals("end-aligned with the last pill", edges[0], edges[1]);
        assertEquals("8dp under the pill row", 8 * edges[4], edges[3] - edges[2], edges[4]);
    }

    @Test
    public void rule5_withTheCarouselTheOrderIsTileCarouselPillInputsAndBack() throws Exception {
        ProviderInfo hero = home.target().getPackageManager().resolveContentProvider(HeroContract.AUTHORITY, 0);
        org.junit.Assume.assumeTrue("the Trakt stand-in is installed (./gradlew :testkit:hero:installDebug)",
                hero != null && hero.name.endsWith("FixtureHeroProvider"));
        start(new TestTheme().columns(4).rowsBelowDock(1).heroFeed());
        home.waitFor("the carousel to own the shelf", a -> a.findViewById(R.id.heroSynopsis).getVisibility() == View.VISIBLE);
        String inputs = inputsOrFirst();
        path(UP, CAROUSEL, UP, SCORE_PILL, UP, inputs, DOWN, tile(0), UP, CAROUSEL, UP, SCORE_PILL, DOWN, CAROUSEL, DOWN, tile(0));
        path(UP, CAROUSEL, UP, SCORE_PILL, BACK, tile(0));
    }

    private static String tile(int position) {
        return TILE + position;
    }
}
