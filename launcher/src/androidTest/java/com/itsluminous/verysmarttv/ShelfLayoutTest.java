package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
import com.itsluminous.verysmarttv.topbar.ScoreStackShade;

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
 * pixel, so the stack never moves the shelf; and with a stack, the title, the meta line, the synopsis and the chips
 * all end before the stack's bounds, and the backdrop is veiled under it (the shade covers the stack).
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

    /** The shelf's top and the stack's bounds after Home settled with the carousel up and {@code rows} lines (0 hides the pill). */
    private static final class Seen {
        float shelfTop;
        Rect stack;
        List<Rect> texts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Rect shade;
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
        // A layout pass after the last change settles the shelf's text boxes.
        Thread.sleep(600);
        screenshot(rows);
        return home.onHome(this::read);
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
        View pill = a.findViewById(R.id.scorePill);
        s.stack = pill.getVisibility() == View.VISIBLE ? inRoot(root, pill) : new Rect();
        for (int id : new int[]{R.id.heroName, R.id.heroMeta, R.id.heroSynopsis, R.id.heroChips}) {
            View v = a.findViewById(id);
            if (v.getVisibility() != View.VISIBLE || v.getWidth() == 0) continue;
            // The shelf is placed with setY (a translation offsetDescendantRectToMyCoords leaves out): its children's
            // rects in the shelf's frame, moved by where the shelf stands.
            Rect r = inRoot((ViewGroup) hero, v);
            r.offset(Math.round(hero.getX()), Math.round(hero.getY()));
            s.texts.add(r);
            s.names.add(a.getResources().getResourceEntryName(id));
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof ScoreStackShade && c.getVisibility() == View.VISIBLE) s.shade = inRoot(root, c);
        }
        s.artUp = a.findViewById(R.id.heroFeedArt).getAlpha() > 0.99f;
        return s;
    }

    private static Rect inRoot(ViewGroup root, View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        root.offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    private static final int[] ROWS = {0, 1, 3, 5};

    @Test
    public void theShelfTopIsTheSameWithNoStackAndWithOneThreeAndFiveRows_andItsTextEndsBeforeTheStack() throws Exception {
        List<Seen> seen = new ArrayList<>();
        for (int rows : ROWS) seen.add(homeWith(rows));
        // Crossings first, every row count at once, so a failure names each of them.
        StringBuilder crossings = new StringBuilder();
        for (int i = 0; i < ROWS.length; i++) {
            Seen s = seen.get(i);
            assertTrue("the shelf has text to check at " + ROWS[i] + " rows", s.texts.size() >= 2);
            if (ROWS[i] == 0) {
                assertTrue("no stack at 0 rows", s.stack.isEmpty());
                continue;
            }
            assertFalse("the stack shows at " + ROWS[i] + " rows", s.stack.isEmpty());
            for (int t = 0; t < s.texts.size(); t++) {
                if (Rect.intersects(s.texts.get(t), s.stack)) {
                    crossings.append(String.format(Locale.ROOT, "%s %s crosses the %d-row stack %s; ", s.names.get(t), s.texts.get(t), ROWS[i], s.stack));
                }
            }
        }
        assertEquals("the shelf's text ends before the stack", "", crossings.toString());
        for (int i = 0; i < ROWS.length; i++) {
            Seen s = seen.get(i);
            assertTrue("the backdrop is up at " + ROWS[i] + " rows", s.artUp);
            if (ROWS[i] == 0) {
                assertTrue("no stack, no shade", s.shade == null);
            } else {
                assertNotNull("the backdrop is veiled under the " + ROWS[i] + "-row stack", s.shade);
                assertTrue("the shade " + s.shade + " covers the stack " + s.stack, s.shade.contains(s.stack));
            }
        }
        for (int i = 1; i < ROWS.length; i++) {
            assertEquals("the shelf's top with " + ROWS[i] + " rows is where it was with none", seen.get(0).shelfTop, seen.get(i).shelfTop, 0.5f);
        }
    }
}
