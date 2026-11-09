package com.itsluminous.verysmarttv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.support.Shell;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The install-and-Home smoke of DESIGN.md §14.3: the launcher answers the HOME key, Home shows a grid with
 * tiles, and the pills take focus. It runs against whatever the emulator has installed (no theme is pushed,
 * so it holds for config.xml's defaults and for a pushed theme alike) and leaves the device as it found it,
 * except that the launcher is Home afterwards, which is the state every other instrumentation test wants.
 *
 * <p>The HOME-key test needs the shell: {@code cmd package set-home-activity} and {@code dumpsys activity}
 * through {@link android.app.UiAutomation#executeShellCommand}, which is why this one uses UiAutomator
 * rather than Espresso alone (§14.1: UiAutomator only for the smoke that spans apps). On a Google TV image
 * {@code set-home-activity} alone does not move Home away from the stock launcher (seen on the API 34
 * {@code google_atv} images; the AOSP {@code android-tv} image CI uses has no stock launcher), so when HOME still
 * resolves to one of the four stock launchers the setup tool knows, the test disables it the way the install flow
 * does, after this launcher is installed, says so in logcat, and enables it again when the test ends: Gradle
 * uninstalls the launcher after a connected run, and a TV with its stock launcher disabled and no other Home
 * would be left on FallbackHome (the state the Home guard exists to prevent). Pass
 * {@code -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true} to keep the debug launcher installed.
 *
 * <p>The first Home after traktshelf is installed shows the module-card prompt over the grid. Espresso then waits
 * for a root with window focus that never comes, and UiAutomator sees only the prompt's window, so the view checks
 * read the activity's views on the main thread through {@link ActivityScenario#onActivity}, polling until the
 * first layout has happened; the prompt is left unanswered.
 */
@RunWith(AndroidJUnit4.class)
public class HomeSmokeTest {
    private static final String PKG = "com.itsluminous.verysmarttv";
    private static final String HOME = PKG + "/.home.HomeActivity";
    private static final long WAIT_MS = 15_000;
    private static final String TAG = "VerySmartTV.HomeSmokeTest";
    /** The stock launchers the install flow may disable once this launcher is installed (DESIGN.md §16, carried KNOWN_STOCK_LAUNCHERS). */
    private static final List<String> STOCK_LAUNCHERS = Arrays.asList("com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome");

    private UiDevice device;
    private ActivityScenario<HomeActivity> scenario;
    /** The stock launcher homeKeyLandsOnTheLauncher disabled, to enable again in tearDown; null when it disabled none. */
    private String disabledStockLauncher;

    @Before
    public void setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void tearDown() throws IOException {
        if (scenario != null) scenario.close();
        if (disabledStockLauncher != null) {
            Log.i(TAG, "enabling " + disabledStockLauncher + " again; this launcher stays Home while it is installed");
            shell("pm enable --user 0 " + disabledStockLauncher);
            disabledStockLauncher = null;
        }
    }

    /** `set-home-activity`, HOME keyevent, `mFocusedApp` names HomeActivity (§14.3 HomeSmokeTest). */
    @Test
    public void homeKeyLandsOnTheLauncher() throws IOException {
        String set = shell("cmd package set-home-activity " + HOME);
        assertTrue("set-home-activity: " + set, set.contains("Success") || set.trim().isEmpty());
        String owner = homeOwner();
        if (!owner.startsWith(PKG + "/") && STOCK_LAUNCHERS.contains(owner.split("/")[0])) {
            Log.i(TAG, "HOME still resolves to " + owner + " after set-home-activity; disabling it as the install flow does");
            disabledStockLauncher = owner.split("/")[0];
            shell("pm disable-user --user 0 " + disabledStockLauncher);
            shell("cmd package set-home-activity " + HOME);
            owner = homeOwner();
        }
        assertTrue("HOME resolves to " + owner, owner.startsWith(PKG + "/"));
        device.pressHome();
        assertTrue("the launcher did not come to the front within " + WAIT_MS + " ms",
                device.wait(Until.hasObject(By.pkg(PKG).depth(0)), WAIT_MS));
        assertEquals(PKG, device.getCurrentPackageName());
        String focused = shell("dumpsys activity activities");
        assertTrue("mFocusedApp is not the launcher:\n" + grep(focused, "mFocusedApp"),
                grep(focused, "mFocusedApp").contains(HOME));
    }

    /** Home creates, the grid is on screen with an adapter, and the adapter has at least one tile. */
    @Test
    public void gridHasTiles() {
        scenario = ActivityScenario.launch(HomeActivity.class);
        assertTrue("the grid never laid out a tile", waitForActivity(a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            return grid != null && grid.getChildCount() > 0;
        }));
        AtomicInteger items = new AtomicInteger(-1);
        AtomicInteger children = new AtomicInteger(-1);
        scenario.onActivity(a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            assertEquals("the grid is not shown", View.VISIBLE, grid.getVisibility());
            assertNotNull("the grid has no adapter", grid.getAdapter());
            items.set(grid.getAdapter().getItemCount());
            children.set(grid.getChildCount());
        });
        assertTrue("the adapter holds no tiles", items.get() > 0);
        assertTrue("no tile is laid out", children.get() > 0);
    }

    /** Every pill the configuration shows is focusable, and the Settings pill actually takes focus. */
    @Test
    public void pillsAreFocusable() {
        scenario = ActivityScenario.launch(HomeActivity.class);
        assertTrue("the Settings pill never laid out", waitForActivity(a -> {
            View settings = a.findViewById(R.id.btnSettings);
            return settings != null && settings.getWidth() > 0;
        }));
        int[] pills = {R.id.btnInputs, R.id.btnWifi, R.id.btnVpn, R.id.btnLibraryTop, R.id.btnReorder};
        scenario.onActivity(a -> {
            View settings = a.findViewById(R.id.btnSettings);
            assertEquals("the Settings pill is not shown", View.VISIBLE, settings.getVisibility());
            assertTrue("the Settings pill is not focusable", settings.isFocusable());
            for (int id : pills) {
                View pill = a.findViewById(id);
                if (pill.getVisibility() == View.VISIBLE) {
                    assertTrue(a.getResources().getResourceEntryName(id) + " is shown but not focusable", pill.isFocusable());
                }
            }
            assertTrue("requestFocus on the Settings pill was refused", settings.requestFocus());
            assertTrue("the Settings pill did not take focus", settings.hasFocus());
        });
    }

    /** `package/.Activity` that answers HOME now, or "" when the shell could not say. */
    private String homeOwner() throws IOException {
        String out = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
        String[] lines = out.trim().split("\n");
        String last = lines[lines.length - 1].trim();
        return last.contains("/") ? last : "";
    }

    /** Polls `test` on the main thread every 200 ms until it holds or WAIT_MS passed. */
    private boolean waitForActivity(ActivityCheck test) {
        long end = System.currentTimeMillis() + WAIT_MS;
        AtomicBoolean ok = new AtomicBoolean();
        do {
            scenario.onActivity(a -> ok.set(test.holds(a)));
            if (ok.get()) return true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < end);
        return false;
    }

    private interface ActivityCheck {
        boolean holds(HomeActivity activity);
    }

    private static String shell(String command) throws IOException {
        return Shell.run(command);
    }

    private static String grep(String text, String needle) {
        return Shell.grep(text, needle);
    }
}
