package com.itsluminous.verysmarttv.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.theme.Config;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings panel's rows (DESIGN.md §14.2 SettingsPanelTest, §10): System bars and Live scores are always offered;
 * the Live scores value names the missing companion until its provider resolves; a pick saves the choice, which the
 * row then shows and Config applies over the theme's key; and the two rows read the theme's value until a choice is saved.
 */
@RunWith(RobolectricTestRunner.class)
public class SettingsPanelTest {
    private Context ctx;
    private Activity activity;
    private SharedPreferences choices;
    private int changes;

    @Before
    public void anActivity() {
        ctx = RuntimeEnvironment.getApplication();
        activity = Robolectric.buildActivity(Activity.class).create().get();
        choices = ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().clear().commit();
    }

    private SettingsPanel panel() {
        return new SettingsPanel(activity, Config.load(activity), choices, () -> changes++);
    }

    /** Every TextView under the activity's content, in order. */
    private List<TextView> texts() {
        List<TextView> out = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), out);
        return out;
    }

    private static void collect(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    /** The value text that follows the row titled {@code title}, or null when the row is absent. */
    private String valueOf(String title) {
        List<TextView> texts = texts();
        for (int i = 0; i < texts.size(); i++) {
            if (title.contentEquals(texts.get(i).getText()) && i + 1 < texts.size()) return texts.get(i + 1).getText().toString();
        }
        return null;
    }

    /** Clicks the row titled {@code title}: its title TextView's first clickable ancestor. */
    private void click(String title) {
        for (TextView t : texts()) {
            if (title.contentEquals(t.getText())) {
                View v = t;
                while (v != null && !v.isClickable()) v = (View) v.getParent();
                assertNotNull("row " + title + " is clickable", v);
                v.performClick();
                return;
            }
        }
        throw new AssertionError("no row titled " + title);
    }

    private void installCompanion() {
        ProviderInfo info = new ProviderInfo();
        info.authority = ScoreContract.AUTHORITY;
        info.name = ScoreContract.PACKAGE + ".ScoreProvider";
        info.packageName = ScoreContract.PACKAGE;
        info.readPermission = ScoreContract.PERMISSION_READ;
        Shadows.shadowOf(ctx.getPackageManager()).addOrUpdateProvider(info);
    }

    @Test
    public void liveScoresRowSaysWhenTheCompanionIsMissing() {
        panel();
        String value = valueOf(ctx.getString(R.string.score_pill_label));
        assertNotNull("the Live scores row is offered without the companion", value);
        assertTrue(value, value.startsWith(ctx.getString(R.string.off)));
        assertTrue(value, value.endsWith(ctx.getString(R.string.score_pill_not_installed)));
    }

    @Test
    public void liveScoresRowReadsPlainOnceTheCompanionResolves() {
        installCompanion();
        panel();
        assertEquals(ctx.getString(R.string.off), valueOf(ctx.getString(R.string.score_pill_label)));
    }

    @Test
    public void systemBarsRowIsAlwaysThereAndReadsTheTheme() {
        panel();
        assertEquals("config.xml's widgets is false", ctx.getString(R.string.off), valueOf(ctx.getString(R.string.widgets_label)));
    }

    @Test
    public void pickingLiveScoresOnSavesTheChoiceAndConfigAppliesIt() {
        installCompanion();
        panel();
        assertFalse(Config.load(activity).scorePill);
        click(ctx.getString(R.string.score_pill_label));
        // The picker page: On, Off. On is the first row of the list after the caption.
        click(ctx.getString(R.string.on));
        assertEquals(1, choices.getInt(SettingsChoices.SCORE_PILL_KEY, -1));
        assertEquals(1, changes);
        assertEquals("back on the main page, the row shows the new state", ctx.getString(R.string.on), valueOf(ctx.getString(R.string.score_pill_label)));
        Config cfg = Config.load(activity);
        assertTrue("the choice overrides the theme's key", cfg.scorePill);
        assertFalse("the theme's own value is kept apart", cfg.themeScorePill);
    }

    @Test
    public void pickingSystemBarsOnSavesTheChoiceAndConfigAppliesIt() {
        panel();
        click(ctx.getString(R.string.widgets_label));
        click(ctx.getString(R.string.on));
        assertEquals(1, choices.getInt(SettingsChoices.WIDGETS_KEY, -1));
        assertTrue(Config.load(activity).widgets);
        assertEquals(ctx.getString(R.string.on), valueOf(ctx.getString(R.string.widgets_label)));
    }

    @Test
    public void pickingOffSavesZeroAndTheRowFollows() {
        choices.edit().putInt(SettingsChoices.SCORE_PILL_KEY, 1).commit();
        installCompanion();
        panel();
        assertEquals(ctx.getString(R.string.on), valueOf(ctx.getString(R.string.score_pill_label)));
        click(ctx.getString(R.string.score_pill_label));
        click(ctx.getString(R.string.off));
        assertEquals(0, choices.getInt(SettingsChoices.SCORE_PILL_KEY, -1));
        assertFalse(Config.load(activity).scorePill);
    }

    @Test
    public void aThemePushClearsTheChoice() {
        choices.edit().putInt(SettingsChoices.SCORE_PILL_KEY, 1).putString(SettingsChoices.THEME_SIGNATURE_KEY, "old").commit();
        SettingsChoices.syncThemeChoices(choices, null, null);
        assertFalse("a changed theme signature drops the Live scores choice", choices.contains(SettingsChoices.SCORE_PILL_KEY));
        panel();
        assertEquals("the row reads the theme's value again", ctx.getString(R.string.off), valueOf(ctx.getString(R.string.score_pill_label)).substring(0, 3));
    }
}
