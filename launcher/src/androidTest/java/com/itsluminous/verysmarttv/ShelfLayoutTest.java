package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.itsluminous.verysmarttv.core.contract.HeroContract;
import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.ScoreStandIn;
import com.itsluminous.verysmarttv.support.TestTheme;
import com.itsluminous.verysmarttv.testkit.HeroFixture;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;
import com.itsluminous.verysmarttv.topbar.ScoreLineView;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The score stack and the carousel shelf as independent layers (DESIGN.md §9.2, ADR-046), on the emulator with both
 * stand-ins: with no stack and with the five-row fixture at score_rows 1, 3 and 5, the shelf's top is the same to the
 * pixel, so the stack never moves the shelf; the title's and the synopsis's boxes are as wide with a stack as without
 * one, so the stack never narrows the shelf's text; and nothing in the top-right column has a background except the
 * lines themselves, each on its own shade (no veil behind the stack as a whole, nothing drawn over the backdrop).
 */
@RunWith(AndroidJUnit4.class)
public class ShelfLayoutTest {
    @Rule public final HomeTestRule home = new HomeTestRule();

    @Before
    public void bothStandInsInstalled() {
        ScoreStandIn.assertInstalled(home.target());
        ProviderInfo hero = home.target().getPackageManager().resolveContentProvider(HeroContract.AUTHORITY, 0);
        Assume.assumeTrue("the Trakt stand-in is installed (./gradlew :testkit:hero:installDebug)",
                hero != null && hero.name.endsWith("FixtureHeroProvider"));
    }

    @After
    public void liveAgain() {
        ScoreStandIn.fixture(home.target(), ScoreFixture.LIVE);
    }

    /** What Home showed once it settled with the carousel up and {@code rows} lines (0 hides the pill). */
    private static final class Seen {
        float shelfTop;
        int titleWidth, synopsisWidth;
        Rect stack;
        int lines, linesWithShade;
        /** Views with a background that reach into the stack's column and are not a line or part of one. */
        List<String> shadedBehind = new ArrayList<>();
        boolean artUp;
    }

    private Seen homeWith(int rows) throws Exception {
        DummyTiles.enable(7);
        ScoreStandIn.fixture(home.target(), rows == 0 ? ScoreFixture.EMPTY : ScoreFixture.STACK);
        home.pushTheme(new TestTheme().columns(4).rowsBelowDock(1).heroFeed().scorePill().scoreRows(Math.max(1, rows)));
        home.launchHome();
        home.focusTile(0);
        home.waitFor("the carousel to show the first fixture title", a -> a.findViewById(R.id.heroSynopsis).getVisibility() == View.VISIBLE
                && HeroFixture.TITLES[0].contentEquals(((android.widget.TextView) a.findViewById(R.id.heroName)).getText()));
        home.waitFor("the backdrop to decode and fade in", a -> {
            ImageView art = a.findViewById(R.id.heroFeedArt);
            return art.getVisibility() == View.VISIBLE && art.getDrawable() != null && art.getAlpha() > 0.99f;
        });
        if (rows == 0) home.waitFor("no score pill", a -> a.findViewById(R.id.scorePill).getVisibility() != View.VISIBLE);
        else home.waitFor(rows + " lines", a -> home.scoreLines().size() == rows);
        // A layout pass after the last change settles the shelf's boxes.
        Thread.sleep(600);
        screenshot(rows);
        Seen s = home.onHome(this::read);
        Log.i("VerySmartTV.ShelfLayoutTest", String.format(Locale.ROOT, "%d rows: shelf top %.1f, title %dpx, synopsis %dpx, stack %s, shaded behind %s",
                rows, s.shelfTop, s.titleWidth, s.synopsisWidth, s.stack.toShortString(), s.shadedBehind));
        return s;
    }

    /**
     * The screen as the test saw it, for the PR description: {@code shelf-<rows>rows.png} in the launcher's external
     * files directory ({@code adb pull /sdcard/Android/data/com.itsluminous.verysmarttv/files/}). Best effort: a
     * missing or read-only directory fails nothing.
     */
    private static void screenshot(int rows) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        if (dir == null) return;
        Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if (shot == null) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, "shelf-" + rows + "rows.png"))) {
            shot.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException ignored) {
            // The screenshot is a convenience, not what the test checks.
        } finally {
            shot.recycle();
        }
    }

    private Seen read(HomeActivity a) {
        ViewGroup root = a.findViewById(R.id.root);
        Seen s = new Seen();
        View hero = a.findViewById(R.id.hero);
        s.shelfTop = hero.getY();
        s.titleWidth = a.findViewById(R.id.heroName).getWidth();
        s.synopsisWidth = a.findViewById(R.id.heroSynopsis).getWidth();
        View pill = a.findViewById(R.id.scorePill);
        s.stack = pill.getVisibility() == View.VISIBLE ? onScreen(pill) : new Rect();
        s.artUp = a.findViewById(R.id.heroFeedArt).getAlpha() > 0.99f;
        if (s.stack.isEmpty()) return s;
        // The stack's column: from the lines' left edge to the screen's right edge, the stack's own height.
        Rect column = new Rect(s.stack.left, s.stack.top, onScreen(root).right, s.stack.bottom);
        walk(a, root, onScreen(root), column, s, false);
        return s;
    }

    /**
     * Every visible view with a background that draws now ({@link #shaded}) and reaches into {@code column}, other than
     * a line and what a line holds, and other than a layer that covers the whole {@code screen} (the root, the
     * wallpaper, the ambient glow: those are behind everything, not behind the column): none may exist (ADR-046 as
     * amended). Views are placed on screen, so the shelf's setY counts.
     */
    private static void walk(HomeActivity a, View v, Rect screen, Rect column, Seen s, boolean inLine) {
        if (v.getVisibility() != View.VISIBLE) return;
        boolean line = v instanceof ScoreLineView;
        if (line) {
            s.lines++;
            if (shaded(v)) s.linesWithShade++;
        } else if (!inLine && shaded(v) && !onScreen(v).contains(screen) && Rect.intersects(onScreen(v), column)) {
            int id = v.getId();
            s.shadedBehind.add(v.getClass().getSimpleName() + (id == View.NO_ID ? "" : " " + a.getResources().getResourceEntryName(id)) + " " + onScreen(v));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(a, g.getChildAt(i), screen, column, s, inLine || line);
        }
    }

    /** A background that draws in the view's current state: a focus ring at rest (the shelf's) draws nothing. */
    private static boolean shaded(View v) {
        return v.getBackground() != null && v.getBackground().getCurrent() != null;
    }

    private static Rect onScreen(View v) {
        int[] xy = new int[2];
        v.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + v.getWidth(), xy[1] + v.getHeight());
    }

    private static final int[] ROWS = {0, 1, 3, 5};

    @Test
    public void theShelfKeepsItsTopAndItsTextsWidth_andNothingButTheLinesIsShadedInTheStacksColumn() throws Exception {
        List<Seen> seen = new ArrayList<>();
        for (int rows : ROWS) seen.add(homeWith(rows));
        Seen none = seen.get(0);
        assertTrue("no stack at 0 rows", none.stack.isEmpty());
        assertTrue("the carousel's title box is laid out", none.titleWidth > 0);
        assertTrue("the carousel's synopsis box is laid out", none.synopsisWidth > 0);
        for (int i = 0; i < ROWS.length; i++) {
            Seen s = seen.get(i);
            assertTrue("the backdrop is up at " + ROWS[i] + " rows", s.artUp);
            if (ROWS[i] == 0) continue;
            assertFalse("the stack shows at " + ROWS[i] + " rows", s.stack.isEmpty());
            assertEquals("the lines at " + ROWS[i] + " rows", ROWS[i], s.lines);
            assertEquals("every line keeps its own shade at " + ROWS[i] + " rows", ROWS[i], s.linesWithShade);
            assertEquals(String.format(Locale.ROOT, "nothing but the lines is shaded in the %d-row stack's column %s", ROWS[i], s.stack),
                    "", String.join("; ", s.shadedBehind));
            assertEquals("the shelf's top with " + ROWS[i] + " rows is where it was with none", none.shelfTop, s.shelfTop, 0.5f);
            assertEquals("the title's box with " + ROWS[i] + " rows is as wide as with none", none.titleWidth, s.titleWidth);
            assertEquals("the synopsis's box with " + ROWS[i] + " rows is as wide as with none", none.synopsisWidth, s.synopsisWidth);
        }
    }
}
