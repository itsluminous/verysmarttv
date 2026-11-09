package com.itsluminous.verysmarttv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ChangedPackages;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.SoundEffectConstants;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.itsluminous.verysmarttv.core.contract.HeroContract;
import com.itsluminous.verysmarttv.R;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.itsluminous.verysmarttv.grid.AppAdapter;
import com.itsluminous.verysmarttv.library.AppLibraryDialog;
import com.itsluminous.verysmarttv.cards.CardPrompts;
import com.itsluminous.verysmarttv.clock.ClockFormat;
import com.itsluminous.verysmarttv.clock.IdleClock;
import com.itsluminous.verysmarttv.clock.ClockRefresh;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.inputs.CoocaaSources;
import com.itsluminous.verysmarttv.grid.DockDecoration;
import com.itsluminous.verysmarttv.grid.DockEditSession;
import com.itsluminous.verysmarttv.grid.EdgeAlignDecoration;
import com.itsluminous.verysmarttv.grid.GlassView;
import com.itsluminous.verysmarttv.grid.GridController;
import com.itsluminous.verysmarttv.shelf.AmbientGlow;
import com.itsluminous.verysmarttv.shelf.CarouselController;
import com.itsluminous.verysmarttv.shelf.ShelfController;
import com.itsluminous.verysmarttv.shelf.HeroFeed;
import com.itsluminous.verysmarttv.keys.HomeKeyService;
import com.itsluminous.verysmarttv.inputs.InputFold;
import com.itsluminous.verysmarttv.inputs.InputNames;
import com.itsluminous.verysmarttv.inputs.InputsMenu;
import com.itsluminous.verysmarttv.inputs.InputsRequestActivity;
import com.itsluminous.verysmarttv.grid.LastOpened;
import com.itsluminous.verysmarttv.grid.LaunchActions;
import com.itsluminous.verysmarttv.settings.LauncherSettingsActivity;
import com.itsluminous.verysmarttv.library.LibraryApps;
import com.itsluminous.verysmarttv.menus.GlassPopups;
import com.itsluminous.verysmarttv.theme.Motion;
import com.itsluminous.verysmarttv.settings.OpenOnStart;
import com.itsluminous.verysmarttv.grid.ReorderSession;
import com.itsluminous.verysmarttv.settings.QuickSettingsActivity;
import com.itsluminous.verysmarttv.keys.RemoteKeys;
import com.itsluminous.verysmarttv.keys.ServiceRepair;
import com.itsluminous.verysmarttv.shelf.ShelfScale;
import com.itsluminous.verysmarttv.settings.SystemSettings;
import com.itsluminous.verysmarttv.grid.TileRefresh;
import com.itsluminous.verysmarttv.grid.TopFade;
import com.itsluminous.verysmarttv.grid.UniformRows;
import com.itsluminous.verysmarttv.topbar.TopBarController;
import com.itsluminous.verysmarttv.theme.Wallpaper;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class HomeActivity extends Activity implements AppAdapter.TileListener, HomeHost {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    // ---- HomeHost: what the controllers get from the activity ------------------------------------

    @Override
    @NonNull
    public Activity activity() {
        return this;
    }

    @Override
    @NonNull
    public HomeState state() {
        return state;
    }

    @Override
    @NonNull
    public Handler handler() {
        return handler;
    }

    @Override
    public float density() {
        return density;
    }

    @Override
    public boolean dispatchKeyEventToViews(@NonNull KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }


    /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
    public static final String ACTION_LAUNCHER_SETTINGS = "com.itsluminous.verysmarttv.action.LAUNCHER_SETTINGS";
    /** Sent by the "library" action word of app_keys: open the App library over Home (needs app_library). */
    public static final String ACTION_APP_LIBRARY = "com.itsluminous.verysmarttv.action.APP_LIBRARY";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    public static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** The booleans and counts every controller reads (DESIGN.md §8, HomeState); filled in onCreate. */
    final HomeState state = new HomeState();
    private FrameLayout root;
    private Wallpaper wallpaper;
    private float density;
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private int[] settingsKeycodes = new int[0];
    private boolean settingsKeyPanel;
    @NonNull
    public StartupRouter startup() {
        return startup;
    }

    @NonNull
    public CardPrompts cards() {
        return cardPrompts;
    }

    @NonNull
    public GlassPopups popups() {
        return popups;
    }

    @NonNull
    public InputsMenu inputs() {
        return inputs;
    }

    @NonNull
    public TopBarController topBar() {
        return topBar;
    }

    @NonNull
    public GridController grid() {
        return grid;
    }

    @NonNull
    public ShelfController shelf() {
        return shelf;
    }

    @NonNull
    public CarouselController carousel() {
        return carousel;
    }

    @NonNull
    public AmbientGlow glow() {
        return glow;
    }

    @NonNull
    public ReorderSession reorder() {
        return reorder;
    }

    @NonNull
    public DockEditSession dockEdit() {
        return dockEdit;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The module-card prompt and the Module cards list (CardPrompts). */
    private CardPrompts cardPrompts;
    /** Reorder mode (ReorderSession) and edit-dock mode (DockEditSession). */
    private ReorderSession reorder;
    private DockEditSession dockEdit;
    /** The app grid: tiles, focus, the glass highlight, the library (GridController). */
    private GridController grid;
    /** The glass dropdowns: popup window, top_menu, the Settings menu, the tile menu (GlassPopups). */
    private GlassPopups popups;
    /** The Inputs menu, how an input opens, the Live TV intent (InputsMenu). */
    private InputsMenu inputs;
    /** The header and the pills (TopBarController). */
    private TopBarController topBar;
    /** Boot and wake opens, the intents Home answers, the Launcher settings result (StartupRouter). */
    StartupRouter startup;
    /** The glow behind the grid (AmbientGlow): the shelf asks it for colours and fades. */
    private AmbientGlow glow;
    /** The top shelf (ShelfController) and the carousel on it (CarouselController). */
    private ShelfController shelf;
    private CarouselController carousel;
    /** home_idle_clock_minutes and back_opens_clock (IdleClock). */
    private IdleClock idleClock;
    private java.util.Map<Integer, String> homeAppKeys = java.util.Collections.emptyMap();
    /** clock_format, for the race panel's session times (the TextClocks take it in onCreate). */
    private String clockFormat = "system";
    private java.util.Map<Integer, String> homeHoldAppKeys = java.util.Collections.emptyMap();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        homeAppKeys = RemoteKeys.parseAppKeys(cfg.appKeys);
        clockFormat = cfg.clockFormat;
        ClockFormat.apply(getWindow().getDecorView(), clockFormat);
        homeHoldAppKeys = RemoteKeys.parseHoldAppKeys(cfg.appKeys);
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this);
        startup = new StartupRouter(this, cfg);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
        state.columns = cfg.columns;
        state.animations = cfg.animations;
        state.advancedEffects = cfg.animations && cfg.advancedEffects;
        state.reducedMotion = cfg.reducedMotion;
        state.dockRow = cfg.dockRow;
        state.singleRow = cfg.singleRow;
        state.shelfEveryRow = cfg.shelfEveryRow && state.dockRow && !state.singleRow;
        state.uniformRows = cfg.uniformRows;
        state.heroHeightPct = cfg.heroHeight;
        state.appLibraryEnabled = cfg.appLibrary;
        state.tileMenuEnabled = cfg.tileMenu;

        root = findViewById(R.id.root);
        cardPrompts = new CardPrompts(this, root);
        popups = new GlassPopups(this, root);
        inputs = new InputsMenu(this, root, cfg);
        topBar = new TopBarController(this, root);
        grid = new GridController(this, root, cfg);
        idleClock = new IdleClock(this, root, cfg);
        root.setBackgroundColor(cfg.backgroundColor);
        // root fills the window with an opaque colour (Config keeps background_color opaque), so the window's own
        // black under it is a second full-screen fill on every frame for nothing. The starting window still uses
        // the theme's background, so launch looks the same.
        getWindow().setBackgroundDrawable(null);
        wallpaper = new Wallpaper(this, root, cfg);
        glow = new AmbientGlow(this, findViewById(R.id.ambient), cfg);
        shelf = new ShelfController(this, root, glow);
        carousel = shelf.carousel();
        state.cinematic = cfg.cinematicHome;
        shelf.setUp(cfg);
        if (cfg.heroFeed && state.dockRow && cfg.dockTitle) carousel.setUp(cfg);
        shelf.applyDockTitle(cfg);
        topBar.setUpHeader(cfg);
        state.moduleCards = cfg.moduleCards;

        grid.setUp(cfg);
        reorder = new ReorderSession(this, grid.view(), cfg);
        dockEdit = new DockEditSession(this);
        topBar.setUpPills(cfg);


        startup.onCreate();
        topBar.registerCallbacks();

        // Not after recreate(): the launch intent survives it and would open the settings again.
        if (savedInstanceState == null && !startup.openIfAsked(getIntent())) {
            inputs.openInputsIfAsked(getIntent());
        }
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (startup.openIfAsked(intent)) return;
        if (inputs.openInputsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        grid.onHomeAgain(state.stopped);
    }

    @Override
    protected void onStart() {
        super.onStart();
        state.stopped = false;
        state.started = true;
        topBar.onStart();
        grid.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        state.resumed = true;
        // Something changed in Launcher settings: rebuild with it, however the settings were left. Only Back
        // delivered their RESULT_OK; Home (the remote's button) clears them off this task and the result comes
        // back cancelled, so a new wallpaper or layout waited for the next process start. Before the carousel's
        // reader starts: with Trakt recommendations just turned off, the rebuilt Home has no reader at all.
        if (startup.settingsChanged()) {
            recreate();
            return;
        }
        carousel.onResume();
        idleClock.restart();
        cardPrompts.onResume();
        grid.onResume();
        wallpaper.restore();                                        // released by onTrimMemory
        grid.refreshOnResume();
        if (startup.openLiveTvAfterBoot()) return;
        topBar.onResume();
        shelf.onResume();
    }

    /** Whether the framework lays this screen out right-to-left (supportsRtl): the drawn gradients and the grid's key arithmetic follow it. */
    @Override
    public boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Only "Reorder tiles" in Launcher settings still answers with a result: Home rebuilds (the saved choices are
     * reloaded) and then enters reorder mode. Every other change is picked up in onResume through the change count.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        startup.onActivityResult(requestCode, resultCode, data);
    }

    /** Any key on Home restarts the countdown to the idle clock. */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        idleClock.restart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        state.resumed = false;
        carousel.onPause();
        // No UP reaches a paused Home, so a vendor-key hold it saw go down would still open its app later.
        // Only Home's own share is dropped: a key service that saw the same DOWN keeps its hold.
        RemoteKeys.release(this);
        cancelHoldOk();
        idleClock.cancel();
        reorder.endReorder();   // Home or another app while reorder.active() keeps the order so far
        glow.stopAmbientAnimation();
        shelf.onPause();
        glow.cancelFade();
        topBar.onPause();
        startup.onPause();
        grid.onPause();
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        cardPrompts.onPause();
        popups.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        state.started = false;
        topBar.onStop();
        state.stopped = true;
        grid.onStop();
    }

    /**
     * Another app needs the memory while Home is in the background: let go of every tile's artwork, and
     * rebuild it when Home comes back. Not at UI_HIDDEN, the ordinary trip to an app, where keeping the
     * tiles is what makes the return instant.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // RUNNING_LOW and CRITICAL also arrive while Home is visible when an accessibility service keeps the
        // process in the foreground; never blank the screen the owner is looking at.
        if (!MemoryTrimPolicy.shouldRelease(level, state.started)) return;
        grid.onTrimMemory();
        shelf.dropArtCache();
        carousel.release();   // onResume starts it again
        wallpaper.releaseForMemoryPressure();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            startup.startPendingSettingsReorder();
            grid.onWindowFocus();
            cardPrompts.showCardPrompt();
        } else {
            cancelHoldOk();   // a dialog or another window took the remote while OK was held
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RemoteKeys.release(this);
        carousel.release();
        cancelHoldOk();
        grid.onDestroy();
        startup.onDestroy();
        wallpaper.close();
        topBar.unregisterCallbacks();
    }

    /**
     * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) opens the same
     * thing HomeKeyService would. While that service runs it consumes the key first; without it (not enabled,
     * or switched off by a force-stop) the stock launcher that used to handle the key is gone, and the button did
     * nothing even on Home.
     */
    private boolean handleSettingsKey(KeyEvent event) {
        for (int code : settingsKeycodes) {
            if (event.getKeyCode() != code) continue;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                open(settingsKeyPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS));
            }
            return true;
        }
        return false;
    }

    // ---- reorder ---------------------------------------------------------------------------------

    /**
     * Reorder mode (top-bar button). While it is on, every key comes here first:
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Vendor keys (TCL Netflix button, 4062) never reach the key services, only the focused window, so their
        // "hold:" entries run here too or nowhere. Only while resumed: onPause releases Home's holds, and a DOWN
        // taken after that would arm one nothing releases.
        if (state.resumed && event.getKeyCode() >= 1000 && RemoteKeys.handle(this, event, homeAppKeys.get(event.getKeyCode()),
                homeHoldAppKeys.get(event.getKeyCode()))) return true;
        if (handleSettingsKey(event)) return true;
        if (carousel.on() && carousel.handleKey(event)) return true;
        if (holdOk(event)) return true;
        if (state.tileMenuEnabled && !reorder.active() && !popups.isOpen() && !cardPrompts.promptShowing()
                && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            int position = grid.focusedTilePosition();
            if (position >= 0 && position < grid.adapter().appCount()
                    && !getPackageName().equals(grid.adapter().packageAt(position))) {
                if (RemoteKeys.menuReleased(event.getAction(), event.getFlags())) {
                    onTileLongPress(position, getCurrentFocus());
                }
                return true;
            }
        }
        if (!reorder.active()) return super.dispatchKeyEvent(event);
        return reorder.dispatch(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        idleClock.onBackPressed();
    }

    // ---- glass highlight -------------------------------------------------------------------------

    // ---- top shelf -------------------------------------------------------------------------------

    // ---- hero_feed carousel ----------------------------------------------------------------------

    // ---- cinematic shelf -------------------------------------------------------------------------

    // ---- date_two_lines --------------------------------------------------------------------------

    // ---- navigation ------------------------------------------------------------------------------

    // ---- top bar ---------------------------------------------------------------------------------

    @Override
    public void open(@NonNull Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    // ---- AppAdapter.TileListener: the grid controller answers ------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        grid.onTileFocused(position, card);
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        return grid.onTileVerticalKey(position, keyCode);
    }

    @Override
    public void onLibraryRequested() {
        grid.onLibraryRequested();
    }

    @Override
    public void onLibraryFocused() {
        grid.onLibraryFocused();
    }

    @Override
    public void onLibraryUp() {
        grid.onLibraryUp();
    }

    @Override
    public void onTileLongPress(int position, View card) {
        popups.showTileMenu(position, card);
    }

    /** A tile configured with launch_actions "input:<id>" (config.xml): switch to that TV input directly,
     *  the same path the Inputs menu's own rows use, instead of opening the input's owning app normally. */
    @Override
    public void onOpenInput(String inputId) {
        inputs.openInput(inputId);
    }

    /**
     * Hold OK on a tile: a glass menu next to it. Move starts reorder mode with this tile already picked up;
     * Remove from Home takes the shortcut off (only with the app library on, where one press adds it back);
     * App info opens the TV's page for the app, which is what holding OK used to do.
     */
    // ---- hold OK ---------------------------------------------------------------------------------
    // View's own long click misses on some remotes: an IR remote that sends its repeat frames as separate
    // presses, or a Bluetooth one whose repeats land after a tile rebind, never holds one DOWN long enough.
    // So the launcher times the hold itself: OK down on a tile, still down (or still repeating) after
    // HOLD_OK_MS opens the tile menu, and the UP that follows is eaten so it doesn't also open the app.

    private static final long HOLD_OK_MS = 550;
    private long holdOkDown = -1;
    private int holdOkPosition = -1;
    private String holdOkKey;    // the app under OK when it went down; a tile rebuild can move another app to that slot
    private boolean holdOkFired;
    private final Runnable holdOkTimer = this::fireHoldOk;

    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    /** True when the event belongs to a hold that opened the tile menu and must not reach the tile. */
    private boolean holdOk(KeyEvent event) {
        if (!state.tileMenuEnabled || !isOkKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                holdOkFired = false;
                handler.removeCallbacks(holdOkTimer);
                holdOkPosition = reorder.active() || popups.isOpen() || cardPrompts.promptShowing() ? -1 : grid.focusedTilePosition();
                holdOkKey = holdOkPosition < 0 || holdOkPosition >= grid.adapter().appCount() ? null : grid.adapter().keyAt(holdOkPosition);
                if (holdOkKey == null) {
                    holdOkDown = -1;
                    return false;
                }
                holdOkDown = event.getDownTime();
                handler.postDelayed(holdOkTimer, HOLD_OK_MS);
                return false;
            }
            if (holdOkFired) return true;
            if (holdOkDown >= 0 && event.getEventTime() - holdOkDown >= HOLD_OK_MS) {
                fireHoldOk();
                return true;
            }
            return false;
        }
        // ACTION_UP
        handler.removeCallbacks(holdOkTimer);
        holdOkDown = -1;
        holdOkKey = null;
        if (holdOkFired) {
            holdOkFired = false;
            View f = getCurrentFocus();
            if (f != null) f.setPressed(false);
            return true;
        }
        return false;
    }

    /**
     * Stops timing a held OK when the launcher is paused, loses its window or goes away, so the menu can't open
     * behind another screen. An interrupted press still counts as handled: its UP, if it comes back here, is eaten
     * rather than opening the tile.
     */
    private void cancelHoldOk() {
        handler.removeCallbacks(holdOkTimer);
        if (holdOkDown >= 0) holdOkFired = true;
        holdOkDown = -1;
        holdOkPosition = -1;
        holdOkKey = null;
    }

    private void fireHoldOk() {
        handler.removeCallbacks(holdOkTimer);
        if (holdOkDown < 0 || holdOkFired) return;
        holdOkFired = true;
        if (popups.isOpen() || reorder.active()) return;   // View's own long click got there first
        int position = holdOkPosition;
        if (position < 0 || position >= grid.adapter().appCount() || position != grid.focusedTilePosition()) return;
        // Same slot is not enough: tiles may have been rebuilt during the hold, so the app must match too.
        if (!holdOkKey.equals(grid.adapter().keyAt(position))) return;
        View card = getCurrentFocus();
        if (card != null) card.setPressed(false);
        onTileLongPress(position, card);
    }

}
