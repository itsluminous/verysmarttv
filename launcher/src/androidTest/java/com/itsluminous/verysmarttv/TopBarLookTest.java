package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The top bar looks the same with and without the Trakt carousel (ADR-048), on the emulator with both stand-ins:
 * with the carousel up and its backdrop decoded behind the header, and with the carousel off (hero_feed false, which
 * is what the Trakt recommendations row's Off means), every round pill is on {@code pill_bg}, every score line on
 * {@code score_line_bg}, and the RAM and Storage bars are the same view with no state to switch. A background is
 * matched to its resource by the pixels it draws at the view's size, so a swapped drawable would show.
 */
@RunWith(AndroidJUnit4.class)
public class TopBarLookTest {
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

    /** The backgrounds of the bar's pills and the stack's lines, by resource, after Home settled. */
    private static final class Look {
        final List<String> pills = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        boolean barsShown, artUp;
    }

    private Look homeWith(boolean carousel) throws Exception {
        DummyTiles.enable(7);
        ScoreStandIn.fixture(home.target(), ScoreFixture.STACK);
        TestTheme theme = new TestTheme().columns(4).rowsBelowDock(1).scorePill().scoreRows(2).widgets();
        if (carousel) theme.heroFeed();
        home.pushTheme(theme);
        home.launchHome();
        home.focusTile(0);
        if (carousel) {
            home.waitFor("the carousel to show the first fixture title", a -> a.findViewById(R.id.heroSynopsis).getVisibility() == View.VISIBLE
                    && HeroFixture.TITLES[0].contentEquals(((android.widget.TextView) a.findViewById(R.id.heroName)).getText()));
            home.waitFor("the backdrop to decode and fade in", a -> {
                ImageView art = a.findViewById(R.id.heroFeedArt);
                return art.getVisibility() == View.VISIBLE && art.getDrawable() != null && art.getAlpha() > 0.99f;
            });
        } else {
            home.waitFor("the shelf to name the focused app", a -> a.findViewById(R.id.heroSynopsis).getVisibility() != View.VISIBLE);
        }
        home.waitFor("2 lines", a -> home.scoreLines().size() == 2);
        Thread.sleep(300);
        Look look = home.onHome(this::read);
        Log.i("VerySmartTV.TopBarLookTest", String.format(Locale.ROOT, "carousel %s (backdrop up %s): pills %s, lines %s, bars shown %s",
                carousel ? "on" : "off", look.artUp, look.pills, look.lines, look.barsShown));
        return look;
    }

    private Look read(HomeActivity a) {
        Look look = new Look();
        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnReorder, R.id.btnLibraryTop, R.id.btnSettings}) {
            View pill = a.findViewById(id);
            if (pill == null || pill.getVisibility() != View.VISIBLE) continue;
            look.pills.add(a.getResources().getResourceEntryName(id) + "=" + name(a, pill.getBackground()));
        }
        ViewGroup stack = a.findViewById(R.id.scorePill);
        for (int i = 0; i < stack.getChildCount(); i++) {
            View line = stack.getChildAt(i);
            if (line.getVisibility() == View.VISIBLE) look.lines.add(name(a, line.getBackground()));
        }
        assertTrue("the lines are ScoreLineViews", stack.getChildAt(0) instanceof ScoreLineView);
        look.barsShown = a.findViewById(R.id.resourceBars).getVisibility() == View.VISIBLE;
        look.artUp = a.findViewById(R.id.heroFeedArt).getAlpha() > 0.99f;
        return look;
    }

    /**
     * The resource a background came from: pill_bg or score_line_bg when, drawn at the view's size in the view's state,
     * it gives the same pixels as that resource does; else the drawable's class.
     */
    private static String name(HomeActivity a, Drawable bg) {
        assertNotNull("a background", bg);
        Bitmap drawn = render(bg, bg.getBounds().width(), bg.getBounds().height(), bg.getState());
        for (int id : new int[]{R.drawable.pill_bg, R.drawable.score_line_bg}) {
            Drawable res = ContextCompat.getDrawable(a, id);
            if (res != null && render(res, drawn.getWidth(), drawn.getHeight(), bg.getState()).sameAs(drawn)) {
                return a.getResources().getResourceEntryName(id);
            }
        }
        return bg.getClass().getSimpleName();
    }

    private static Bitmap render(Drawable d, int w, int h, int[] state) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        d.mutate();
        d.setState(state);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(new Canvas(b));
        return b;
    }

    @Test
    public void thePillsAndTheLinesKeepTheirOneBackground_withTheCarouselAndWithout() throws Exception {
        Look on = homeWith(true);
        Look off = homeWith(false);
        assertTrue("the backdrop is up with the carousel", on.artUp);
        assertTrue("the bars show in both", on.barsShown && off.barsShown);
        assertTrue("there are pills to compare", on.pills.size() >= 3);
        assertEquals("two lines in each", 2, on.lines.size());
        assertEquals("the pills' backgrounds with the carousel are the ones without it", off.pills, on.pills);
        assertEquals("the lines' backgrounds with the carousel are the ones without it", off.lines, on.lines);
        for (String pill : on.pills) assertTrue(pill + " is on pill_bg", pill.endsWith("=pill_bg"));
        for (String line : on.lines) assertEquals("score_line_bg", line);
    }
}
