package com.itsluminous.verysmarttv;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewParent;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.settings.LauncherSettingsActivity;
import com.itsluminous.verysmarttv.settings.SettingsChoices;
import com.itsluminous.verysmarttv.support.DummyTiles;
import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.ScoreStandIn;
import com.itsluminous.verysmarttv.support.TestTheme;
import com.itsluminous.verysmarttv.testkit.ScoreFixture;
import com.itsluminous.verysmarttv.theme.Config;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Live scores and System bars through the settings panel (DESIGN.md §14.3 SettingsPersistTest, §10): a pick saves the
 * choice over the theme's key, Home built next shows or hides the pill and the bars accordingly, the row reads the
 * new state, and the choice is on disk in the launcher's preferences, where a process restart reads it (Config.load
 * from a fresh Context sees it: the same read a relaunch after am force-stop does).
 */
@RunWith(AndroidJUnit4.class)
public class SettingsPersistTest {
    @Rule public final HomeTestRule home = new HomeTestRule();

    private Context ctx;

    @Before
    public void aThemeWithBoth() throws Exception {
        ctx = home.target();
        ScoreStandIn.assertInstalled(ctx);
        ScoreStandIn.fixture(ctx, ScoreFixture.LIVE);
        DummyTiles.enable(6);
        home.pushTheme(new TestTheme().columns(4).settingsPanel().scorePill().widgets());
    }

    @Test
    public void liveScoresOffHidesThePill_onBringsItBack() throws Exception {
        home.launchHome();
        home.waitFor("the pill with the theme's key", a -> a.findViewById(R.id.scorePill).getVisibility() == View.VISIBLE);

        home.launch(LauncherSettingsActivity.class);
        tap(ctx.getString(R.string.score_pill_label));
        tap(ctx.getString(R.string.off));
        assertEquals("the row reads the new state", ctx.getString(R.string.off), subtitleOf(ctx.getString(R.string.score_pill_label)));
        assertEquals(0, prefs().getInt(SettingsChoices.SCORE_PILL_KEY, -1));
        assertFalse("Config applies the choice over the theme", Config.load(ctx).scorePill);
        assertTrue("the theme's own value is kept", Config.load(ctx).themeScorePill);

        home.launchHome();
        assertEquals("Home built after the pick has no pill", View.GONE, (int) home.onHome(a -> a.findViewById(R.id.scorePill).getVisibility()));

        home.launch(LauncherSettingsActivity.class);
        tap(ctx.getString(R.string.score_pill_label));
        tap(ctx.getString(R.string.on));
        assertEquals(1, prefs().getInt(SettingsChoices.SCORE_PILL_KEY, -1));
        home.launchHome();
        home.waitFor("the pill is back", a -> a.findViewById(R.id.scorePill).getVisibility() == View.VISIBLE);
    }

    @Test
    public void systemBarsOffHidesTheBlock_onBringsItBack() throws Exception {
        home.launchHome();
        assertEquals(View.VISIBLE, (int) home.onHome(a -> a.findViewById(R.id.resourceBars).getVisibility()));
        home.launch(LauncherSettingsActivity.class);
        tap(ctx.getString(R.string.widgets_label));
        tap(ctx.getString(R.string.off));
        assertEquals(0, prefs().getInt(SettingsChoices.WIDGETS_KEY, -1));
        home.launchHome();
        assertEquals(View.GONE, (int) home.onHome(a -> a.findViewById(R.id.resourceBars).getVisibility()));
        home.launch(LauncherSettingsActivity.class);
        tap(ctx.getString(R.string.widgets_label));
        tap(ctx.getString(R.string.on));
        home.launchHome();
        assertEquals(View.VISIBLE, (int) home.onHome(a -> a.findViewById(R.id.resourceBars).getVisibility()));
    }

    @Test
    public void theChoiceIsOnDiskForTheNextProcess() throws Exception {
        home.launch(LauncherSettingsActivity.class);
        tap(ctx.getString(R.string.score_pill_label));
        tap(ctx.getString(R.string.off));
        // What a relaunch after am force-stop reads: the preferences file, through a Context that has not seen the pick.
        Context fresh = ctx.createPackageContext(ctx.getPackageName(), 0);
        assertEquals(0, fresh.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE).getInt(SettingsChoices.SCORE_PILL_KEY, -1));
        assertFalse(Config.load(fresh).scorePill);
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    private void tap(String text) {
        onView(withText(text)).perform(performClick());
    }

    /** The line under a row's title: the second TextView of the row's text column. */
    private String subtitleOf(String rowLabel) {
        final String[] out = new String[1];
        onView(withText(rowLabel)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "read the subtitle under " + rowLabel; }
            @Override public void perform(UiController ui, View title) {
                android.view.ViewGroup column = (android.view.ViewGroup) title.getParent();
                out[0] = column.getChildCount() > 1 ? String.valueOf(((android.widget.TextView) column.getChildAt(1)).getText()) : "";
            }
        });
        return out[0];
    }

    /** performClick on the first clickable view at or above the matched one (the panel's rows are clickable layouts around texts). */
    private static ViewAction performClick() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "performClick on the clickable ancestor"; }
            @Override public void perform(UiController ui, View view) {
                View target = view;
                while (target != null && !target.isClickable()) {
                    ViewParent parent = target.getParent();
                    target = parent instanceof View ? (View) parent : null;
                }
                if (target == null) throw new AssertionError("nothing clickable at or above " + view);
                target.performClick();
                ui.loopMainThreadUntilIdle();
            }
        };
    }
}
