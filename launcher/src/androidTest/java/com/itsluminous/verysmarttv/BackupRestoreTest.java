package com.itsluminous.verysmarttv;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.itsluminous.verysmarttv.support.HomeTestRule;
import com.itsluminous.verysmarttv.support.TestTheme;

import org.hamcrest.Matcher;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.settings.LauncherSettingsActivity;

/**
 * Backup and restore from the settings panel (DESIGN.md §14.3 BackupRestoreTest), driven by its rows' text: the
 * panel draws itself in code, so text is what a row has. Pinned: the Animations row's picker saves the choice; Back
 * up settings writes a file into files/backup; Restore from file lists that folder (a file pushed there too), asks,
 * puts the backed-up choice back and moves the settings revision so Home rebuilds on its next resume.
 */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreTest {
    @Rule public final HomeTestRule home = new HomeTestRule();

    private Context ctx;
    private ActivityScenario<LauncherSettingsActivity> panel;

    @Before
    public void openThePanel() throws Exception {
        ctx = home.target();
        home.pushTheme(new TestTheme().settingsPanel());
        panel = home.launch(LauncherSettingsActivity.class);
        assertTrue("the theme's Animations default is On", LauncherState.animations(ctx));
    }

    @Test
    public void backUp_changeAgain_restoreBringsTheBackedUpValueBack() throws Exception {
        tap(label(R.string.animations_state));
        tap(ctx.getString(R.string.off));
        assertFalse("the picker saved Off", LauncherState.animations(ctx));
        assertEquals(ctx.getString(R.string.off), subtitleOf(label(R.string.animations_state)));

        Set<String> before = names(LauncherState.backups(ctx));
        tap(ctx.getString(R.string.backup_now));
        String saved = waitForNewBackup(before);
        assertTrue("Back up settings writes verysmarttv-<date>.json: " + saved, saved.startsWith("verysmarttv-") && saved.endsWith(".json"));

        tap(label(R.string.animations_state));
        tap(ctx.getString(R.string.on));
        assertTrue("changed again after the backup", LauncherState.animations(ctx));

        long revision = LauncherState.settingsRevision(ctx);
        tap(ctx.getString(R.string.restore_file));
        tap(saved);
        onView(withText(R.string.restore_button)).inRoot(isDialog()).perform(performClick());
        waitForPanelToClose();
        assertFalse("the restore put Off back", LauncherState.animations(ctx));
        assertTrue("the settings revision moved, so Home rebuilds on resume", LauncherState.settingsRevision(ctx) > revision);
    }

    @Test
    public void restoreFromAFilePushedIntoTheBackupFolder() throws Exception {
        tap(label(R.string.animations_state));
        tap(ctx.getString(R.string.off));
        JSONObject withOff = LauncherState.export(ctx);
        File pushed = new File(LauncherState.backupDir(ctx), "pushed-restore-me.json");   // what adb push lands there
        try (FileOutputStream out = new FileOutputStream(pushed)) {
            out.write(withOff.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        tap(label(R.string.animations_state));
        tap(ctx.getString(R.string.on));
        assertTrue(LauncherState.animations(ctx));

        tap(ctx.getString(R.string.restore_file));
        tap(pushed.getName());
        onView(withText(R.string.restore_button)).inRoot(isDialog()).perform(performClick());
        waitForPanelToClose();
        assertFalse("the pushed file's Off is in force", LauncherState.animations(ctx));
    }

    @Test
    public void cancellingTheRestoreChangesNothing() throws Exception {
        Set<String> before = names(LauncherState.backups(ctx));
        tap(ctx.getString(R.string.backup_now));
        String saved = waitForNewBackup(before);
        tap(label(R.string.animations_state));
        tap(ctx.getString(R.string.off));
        long revision = LauncherState.settingsRevision(ctx);
        tap(ctx.getString(R.string.restore_file));
        tap(saved);
        onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(performClick());
        assertFalse("still Off", LauncherState.animations(ctx));
        assertEquals("no rebuild asked for", revision, LauncherState.settingsRevision(ctx));
        // Back on the main page: the Animations row is there again with its value.
        assertEquals(ctx.getString(R.string.off), subtitleOf(label(R.string.animations_state)));
    }

    /** The row label the panel shows for a "Name: %1$s" state string: the part before the colon. */
    private String label(int stateString) {
        String s = ctx.getString(stateString, "");
        int colon = s.lastIndexOf(':');
        return (colon > 0 ? s.substring(0, colon) : s).trim();
    }

    /** Clicks the row (or button) that shows {@code text}, through the clickable ancestor's own click handler. */
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

    /**
     * performClick on the first clickable view at or above the matched one: the panel's rows are clickable
     * LinearLayouts around plain TextViews, and a TV emulator need not take a touch tap.
     */
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

    private String waitForNewBackup(Set<String> before) throws InterruptedException {
        long end = System.currentTimeMillis() + HomeTestRule.WAIT_MS;
        while (System.currentTimeMillis() < end) {
            for (String name : names(LauncherState.backups(ctx))) if (!before.contains(name)) return name;
            Thread.sleep(100);
        }
        throw new AssertionError("Back up settings wrote no new file into " + LauncherState.backupDir(ctx));
    }

    private void waitForPanelToClose() throws InterruptedException {
        long end = System.currentTimeMillis() + HomeTestRule.WAIT_MS;
        while (panel.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED && System.currentTimeMillis() < end) Thread.sleep(100);
        assertEquals("the panel closes after a restore", androidx.lifecycle.Lifecycle.State.DESTROYED, panel.getState());
    }

    private static Set<String> names(java.util.List<File> files) {
        Set<String> names = new HashSet<>();
        for (File f : files) names.add(f.getName());
        return names;
    }
}
