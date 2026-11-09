package com.itsluminous.verysmarttv.support;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.itsluminous.verysmarttv.LauncherState;
import com.itsluminous.verysmarttv.MainActivity;
import com.itsluminous.verysmarttv.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.theme.ThemeProvider;

/**
 * What every launcher instrumentation test needs around it (DESIGN.md §14.3):
 *
 * <ul>
 *   <li><b>Leave the emulator as found.</b> Before the test the launcher's whole state is exported through
 *       {@code Backup} (preferences, order, card decisions, theme and wallpaper); after it the export is restored,
 *       the dummy tiles are all enabled again and any backup file the test made is deleted. So the owner's theme
 *       survives a run with {@code leaveApksInstalledAfterRun}.
 *   <li><b>Theme push</b> through the {@code .theme} provider, the way the setup tool pushes one: config.json, a fresh
 *       {@code theme_revision} (so the choices made on the TV are dropped, as after a real push) and no wallpaper. The
 *       test shares the launcher's uid, which the provider accepts without WRITE_SECURE_SETTINGS.
 *   <li><b>Home</b> launched with {@link ActivityScenario}, read on its main thread, driven with Espresso key presses
 *       (through the activity's own {@code dispatchKeyEvent}), and described by {@link #focus()}.
 * </ul>
 *
 * <p>No module-card prompt can appear: {@link TestTheme} hides every app but the dummy tiles, and a hidden app's card
 * is never discovered, so there is nothing to pre-approve (the prompt was what blocked Espresso in Phase 1).
 */
public final class HomeTestRule extends ExternalResource {
    /** The launcher's package. */
    public static final String PKG = "com.itsluminous.verysmarttv";
    /** The theme provider (ThemeProvider, authority {@code <package>.theme}). */
    public static final Uri THEME = Uri.parse("content://" + PKG + ".theme");
    /** How a focused tile is described: this prefix and its adapter position. */
    public static final String TILE = "tile ";
    /** How a focused top-bar pill is described: this prefix and the pill's id name. {@link #PILL} alone matches any. */
    public static final String PILL = "pill";
    /** The All apps pill after the last row. */
    public static final String LIBRARY = "library";
    /** The top shelf while the carousel owns it. */
    public static final String CAROUSEL = "carousel";
    /** Longest wait for focus or a layout to settle, including a smooth scroll. */
    public static final long WAIT_MS = 8_000;
    /** How long a key that must be swallowed is given to move focus wrongly before the check. */
    private static final long SETTLE_MS = 600;

    private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private JSONObject snapshot;
    private Set<String> backupsBefore;
    private ActivityScenario<?> scenario;

    @Override
    protected void before() throws JSONException, IOException {
        snapshot = LauncherState.export(target);
        backupsBefore = backupNames();
        DummyTiles.enableAll();
    }

    @Override
    protected void after() {
        closeActivity();
        try {
            DummyTiles.enableAll();
        } catch (IOException e) {
            throw new AssertionError("could not enable the dummy tiles again: " + e.getMessage(), e);
        }
        for (String name : backupNames()) {
            if (!backupsBefore.contains(name)) new File(LauncherState.backupDir(target), name).delete();
        }
        try {
            LauncherState.restore(target, snapshot);
        } catch (JSONException e) {
            throw new AssertionError("could not put the launcher's settings back: " + e.getMessage(), e);
        }
    }

    /** The launcher's context: its files, preferences and providers. */
    public Context target() {
        return target;
    }

    /** Writes the theme through the provider, with a fresh revision and no wallpaper; Home reads it when next created. */
    public void pushTheme(TestTheme theme) throws IOException, JSONException {
        pushTheme(theme.json(target));
    }

    /** Writes {@code configJson} as the theme's config.json through the provider, with a fresh revision and no wallpaper. */
    public void pushTheme(String configJson) throws IOException {
        write(LauncherState.CONFIG_FILE, configJson);
        write("theme_revision", String.valueOf(System.nanoTime()));
        target.getContentResolver().delete(THEME.buildUpon().appendPath(LauncherState.WALLPAPER_FILE).build(), null, null);
    }

    private void write(String name, String text) throws IOException {
        Uri uri = THEME.buildUpon().appendPath(name).build();
        try (OutputStream out = target.getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("the theme provider did not open " + uri);
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Starts Home and waits for its first tile to be laid out and focus to land. */
    public ActivityScenario<MainActivity> launchHome() {
        closeActivity();
        ActivityScenario<MainActivity> home = ActivityScenario.launch(MainActivity.class);
        scenario = home;
        waitFor("the grid to lay out a focused tile", a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            return grid != null && grid.getChildCount() > 0 && a.getCurrentFocus() != null;
        });
        return home;
    }

    /** Starts another of the launcher's activities (Launcher settings) and keeps it to close after the test. */
    public <A extends Activity> ActivityScenario<A> launch(Class<A> activity) {
        closeActivity();
        ActivityScenario<A> s = ActivityScenario.launch(activity);
        scenario = s;
        return s;
    }

    private void closeActivity() {
        if (scenario != null) scenario.close();
        scenario = null;
    }

    // ---- focus ------------------------------------------------------------------------------------

    /** Focuses the tile at {@code position} directly (it must be laid out, as every first-row tile is) and waits for it. */
    public void focusTile(int position) {
        onHome(a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
            if (holder == null) throw new AssertionError("tile " + position + " is not laid out; reach it with the D-pad");
            holder.itemView.findViewById(R.id.card).requestFocus();
            return null;
        });
        expectFocus(TILE + position, "requestFocus on tile " + position);
    }

    /** Presses one D-pad or OK/Back key through the activity window, down and up. */
    public void press(int keyCode) {
        onView(isRoot()).perform(pressKey(keyCode));
    }

    /**
     * Where focus is, read on the main thread: {@code "tile N"}, {@code "pill <id>"} for a top-bar button,
     * {@code "library"}, {@code "carousel"}, another view's id name, or {@code "none"}.
     */
    public String focus() {
        return onHome(a -> {
            View f = a.getCurrentFocus();
            if (f == null) return "none";
            RecyclerView grid = a.findViewById(R.id.grid);
            if (f.getId() == R.id.card && f.getParent() instanceof View) return TILE + grid.getChildAdapterPosition((View) f.getParent());
            if (f.getId() == R.id.btnLibrary) return LIBRARY;
            if (f.getId() == R.id.hero) return CAROUSEL;
            ViewGroup bar = a.findViewById(R.id.topButtons);
            if (f.getParent() == bar) return PILL + " " + idName(a, f);
            return idName(a, f);
        });
    }

    private static String idName(Activity a, View v) {
        return v.getId() == View.NO_ID ? v.getClass().getSimpleName() : a.getResources().getResourceEntryName(v.getId());
    }

    /** Waits until {@link #focus()} is {@code expected} ({@link #PILL} alone accepts any pill); fails naming where it is. */
    public void expectFocus(String expected, String after) {
        long end = System.currentTimeMillis() + WAIT_MS;
        String seen = focus();
        while (!matches(expected, seen) && System.currentTimeMillis() < end) {
            sleep(100);
            seen = focus();
        }
        if (!matches(expected, seen)) fail(after + ": expected focus on " + expected + " but it is on " + seen);
    }

    /** Gives a swallowed key time to move focus wrongly, then checks it did not. */
    public void expectFocusStays(String expected, String after) {
        sleep(SETTLE_MS);
        String seen = focus();
        if (!matches(expected, seen)) fail(after + ": focus should have stayed on " + expected + " but it is on " + seen);
    }

    private static boolean matches(String expected, String seen) {
        return expected.equals(seen) || (PILL.equals(expected) && seen.startsWith(PILL + " "));
    }

    // ---- reading Home -----------------------------------------------------------------------------

    /** Reads a value from the activity on its main thread. */
    public <T> T onHome(Function<MainActivity, T> read) {
        @SuppressWarnings("unchecked")
        ActivityScenario<MainActivity> home = (ActivityScenario<MainActivity>) scenario;
        if (home == null) throw new IllegalStateException("launchHome() first");
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        home.onActivity(a -> {
            try {
                out.set(read.apply(a));
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) throw failure.get();
        return out.get();
    }

    /** Polls {@code check} on the main thread every 100 ms until it holds; fails after {@link #WAIT_MS} naming {@code what}. */
    public void waitFor(String what, Predicate<MainActivity> check) {
        long end = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < end) {
            if (onHome(check::test)) return;
            sleep(100);
        }
        fail("waited " + WAIT_MS + " ms for " + what);
    }

    /** The text of a TextView of Home. */
    public String text(int id) {
        return onHome(a -> String.valueOf(((TextView) a.findViewById(id)).getText()));
    }

    /** The span count of the grid's layout manager: the columns Home laid out. */
    public int gridColumns() {
        return onHome(a -> ((GridLayoutManager) ((RecyclerView) a.findViewById(R.id.grid)).getLayoutManager()).getSpanCount());
    }

    /** The adapter positions laid out in the grid right now, with the top of each item, as "pos:top" pairs. */
    public Map<Integer, Integer> laidOutTops() {
        return onHome(a -> {
            RecyclerView grid = a.findViewById(R.id.grid);
            Map<Integer, Integer> tops = new TreeMap<>();
            for (int i = 0; i < grid.getChildCount(); i++) {
                View child = grid.getChildAt(i);
                tops.put(grid.getChildAdapterPosition(child), child.getTop());
            }
            return tops;
        });
    }

    /** Asserts the shelf is shown and names {@code label} (the focused app's name in large type). */
    public void expectShelfName(String label) {
        waitFor("the shelf to show and name " + label, a -> a.findViewById(R.id.hero).getAlpha() > 0.99f
                && label.contentEquals(((TextView) a.findViewById(R.id.heroName)).getText()));
        assertEquals(label, text(R.id.heroName));
    }

    /** Asserts the shelf is faded out (a grid tile or the All apps pill has focus: only the glow follows). */
    public void expectShelfHidden() {
        waitFor("the shelf to fade out", a -> a.findViewById(R.id.hero).getAlpha() < 0.01f);
    }

    private Set<String> backupNames() {
        Set<String> names = new HashSet<>();
        File dir = LauncherState.backupDir(target);
        String[] listed = dir == null ? null : dir.list();
        if (listed != null) names.addAll(Arrays.asList(listed));
        return names;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
