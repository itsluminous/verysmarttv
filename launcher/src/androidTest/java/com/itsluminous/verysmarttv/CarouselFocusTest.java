package com.itsluminous.verysmarttv;

import static com.itsluminous.verysmarttv.support.HomeTestRule.CAROUSEL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.PILL;
import static com.itsluminous.verysmarttv.support.HomeTestRule.TILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.core.contract.HeroContract;
import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.IntentCapture;
import com.itsluminous.verysmarttv.support.Shell;
import com.itsluminous.verysmarttv.support.TestTheme;
import com.itsluminous.verysmarttv.testkit.HeroFixture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * The carousel's keys with the companion stand-in installed (DESIGN.md §14.3 CarouselFocusTest, without the score pill). {@code :testkit:hero} installs as the companion's package and answers
 * {@link HeroContract#ITEMS} with {@link HeroFixture}'s three titles and backdrops through the signature permission,
 * exactly as the real one would. Pinned, as today's {@code handleCarouselKey} and {@code showCarouselItem} have it:
 * a dock tile puts the first title on the shelf with its backdrop; Up from a dock tile focuses the carousel, Up from a
 * lower row does not; Left/Right change the title and wrap; OK sends the companion's details intent with the title's
 * Trakt id and kind; Down and Back return to the tile Up left; Up goes on to Inputs (DESIGN.md §9.3 rule 5; the score
 * pill, when shown, comes first: HomeFocusTest).
 */
@RunWith(AndroidJUnit4.class)
public class CarouselFocusTest {
    private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
    private static final String STAND_IN_PROVIDER = "com.itsluminous.verysmarttv.testkit.hero.FixtureHeroProvider";

    @Rule public final HomeTestRule home = new HomeTestRule();

    private IntentCapture intents;

    @Before
    public void standInInstalled() throws Exception {
        ProviderInfo provider = home.target().getPackageManager().resolveContentProvider(HeroContract.AUTHORITY, 0);
        assertNotNull("nothing answers " + HeroContract.AUTHORITY + "; install the stand-in: ./gradlew :testkit:hero:installDebug", provider);
        assertEquals("the real companion answers " + HeroContract.AUTHORITY + ", not the stand-in; run ./gradlew :testkit:hero:installDebug",
                STAND_IN_PROVIDER, provider.name);
        DummyTiles.enable(7);
        // The stand-in starts cold every time: its first rows then land while the first tile takes focus, the moment
        // the shelf's title swap must not be cancelled by the tile's own shelf update (seen on a 1 GB AVD).
        Shell.run("am force-stop " + HeroContract.PACKAGE);
        home.pushTheme(new TestTheme().columns(4).rowsBelowDock(1).heroFeed());
        home.launchHome();
        home.focusTile(0);
        home.waitFor("the carousel to show the first fixture title", a -> a.findViewById(R.id.heroSynopsis).getVisibility() == View.VISIBLE
                && HeroFixture.TITLES[0].contentEquals(((android.widget.TextView) a.findViewById(R.id.heroName)).getText()));
    }

    @After
    public void stopCapture() {
        if (intents != null) intents.stop();
    }

    @Test
    public void aDockTileShowsTheFirstTitleWithSynopsisChipsDotsAndBackdrop() {
        assertEquals(HeroFixture.TITLES[0], home.text(R.id.heroName));
        assertEquals("Synopsis of " + HeroFixture.TITLES[0] + ".", home.text(R.id.heroSynopsis));
        int chips = home.onHome(a -> ((ViewGroup) a.findViewById(R.id.heroChips)).getChildCount());
        assertEquals("IMDb, RT and Trakt chips", 3, chips);
        assertEquals(View.VISIBLE, (int) home.onHome(a -> a.findViewById(R.id.heroDots).getVisibility()));
        home.waitFor("the backdrop to decode and fade in", a -> {
            ImageView art = a.findViewById(R.id.heroFeedArt);
            return art.getVisibility() == View.VISIBLE && art.getDrawable() != null && art.getAlpha() > 0.99f;
        });
        // The focused app's name moves under its tile while the carousel owns the shelf.
        assertEquals(TILE + 0, home.focus());
    }

    @Test
    public void upFromADockTileFocusesTheCarousel_downAndBackReturnToThatTile() {
        home.press(RIGHT);
        home.expectFocus(TILE + 1, "RIGHT from tile 0");
        home.press(UP);
        home.expectFocus(CAROUSEL, "UP from dock tile 1");
        home.press(DOWN);
        home.expectFocus(TILE + 1, "DOWN from the carousel");
        home.press(UP);
        home.expectFocus(CAROUSEL, "UP from dock tile 1 again");
        home.press(KeyEvent.KEYCODE_BACK);
        home.expectFocus(TILE + 1, "BACK from the carousel");
        assertEquals("the carousel keeps the shelf", HeroFixture.TITLES[0], home.text(R.id.heroName));
    }

    @Test
    public void upFromALowerRowGoesARowUp_notIntoTheCarousel() {
        home.press(DOWN);
        home.expectFocus(TILE + 4, "DOWN from tile 0");
        home.press(UP);
        home.expectFocus(TILE + 0, "UP from tile 4");
    }

    @Test
    public void leftAndRightChangeTheTitleAndWrap() {
        home.press(UP);
        home.expectFocus(CAROUSEL, "UP from tile 0");
        home.press(RIGHT);
        expectTitle(1, "RIGHT");
        home.press(RIGHT);
        expectTitle(2, "RIGHT");
        home.press(RIGHT);
        expectTitle(0, "RIGHT past the last item");
        home.press(LEFT);
        expectTitle(2, "LEFT past the first item");
        assertEquals(CAROUSEL, home.focus());
    }

    @Test
    public void okOpensTheTitleInTheCompanionWithItsTraktIdAndKind() {
        home.press(UP);
        home.expectFocus(CAROUSEL, "UP from tile 0");
        home.press(RIGHT);
        expectTitle(1, "RIGHT");
        intents = IntentCapture.start();
        home.press(KeyEvent.KEYCODE_DPAD_CENTER);
        long end = System.currentTimeMillis() + HomeTestRule.WAIT_MS;
        while (intents.intents().isEmpty() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        List<Intent> sent = intents.intents();
        assertEquals("OK on a carousel item starts exactly one activity: " + sent, 1, sent.size());
        Intent details = sent.get(0);
        assertEquals(HeroContract.DETAILS_ACTIVITY, details.getComponent());
        assertEquals(HeroFixture.TRAKT_IDS[1], details.getLongExtra(HeroContract.EXTRA_TRAKT_ID, -1));
        assertEquals(HeroFixture.KINDS[1], details.getStringExtra(HeroContract.EXTRA_KIND));
        assertTrue("opened in its own task", (details.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals("focus stays on the carousel", CAROUSEL, home.focus());
    }

    @Test
    public void upFromTheCarouselGoesToInputs_downReturnsToTheDockColumn() {
        home.press(RIGHT);
        home.expectFocus(TILE + 1, "RIGHT from tile 0");
        home.press(UP);
        home.expectFocus(CAROUSEL, "UP from tile 1");
        // Rule 5 (DESIGN.md §9.3): without the score pill, Up from the carousel lands on Inputs; on an emulator without
        // inputs the Inputs pill is hidden and the first shown pill stands in.
        String inputsOrFirst = home.onHome(a -> {
            ViewGroup bar = a.findViewById(R.id.topButtons);
            View inputs = a.findViewById(R.id.btnInputs);
            if (inputs.getVisibility() == View.VISIBLE) return "btnInputs";
            for (int i = 0; i < bar.getChildCount(); i++) {
                View pill = bar.getChildAt(i);
                if (pill.getVisibility() == View.VISIBLE) return a.getResources().getResourceEntryName(pill.getId());
            }
            return "btnSettings";
        });
        home.press(UP);
        home.expectFocus(PILL + " " + inputsOrFirst, "UP from the carousel");
        home.press(DOWN);
        home.expectFocus(TILE + 1, "DOWN from the bar");
    }

    private void expectTitle(int index, String after) {
        home.waitFor(after + ": the shelf to name " + HeroFixture.TITLES[index],
                a -> HeroFixture.TITLES[index].contentEquals(((android.widget.TextView) a.findViewById(R.id.heroName)).getText()));
    }
}
