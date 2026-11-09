package com.itsluminous.verysmarttv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.cards.CardPrompts;
import com.itsluminous.verysmarttv.clock.ClockFormat;
import com.itsluminous.verysmarttv.clock.IdleClock;
import com.itsluminous.verysmarttv.grid.AppAdapter;
import com.itsluminous.verysmarttv.grid.DockEditSession;
import com.itsluminous.verysmarttv.grid.GridController;
import com.itsluminous.verysmarttv.grid.ReorderSession;
import com.itsluminous.verysmarttv.inputs.InputsMenu;
import com.itsluminous.verysmarttv.keys.KeyDispatcher;
import com.itsluminous.verysmarttv.keys.RemoteKeys;
import com.itsluminous.verysmarttv.keys.ServiceRepair;
import com.itsluminous.verysmarttv.menus.GlassPopups;
import com.itsluminous.verysmarttv.shelf.AmbientGlow;
import com.itsluminous.verysmarttv.shelf.CarouselController;
import com.itsluminous.verysmarttv.shelf.ShelfController;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.theme.Wallpaper;
import com.itsluminous.verysmarttv.topbar.TopBarController;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 *
 * <p>The activity only wires (DESIGN.md §8, ADR-007): it reads the theme once, builds the controllers in the order
 * their views depend on each other, and hands every lifecycle callback, key and tile event to the controller that
 * owns it. The behaviour lives in those controllers; the flags they share live in {@link HomeState}.
 */
public class HomeActivity extends Activity implements AppAdapter.TileListener, HomeHost {
    /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
    public static final String ACTION_LAUNCHER_SETTINGS = "com.itsluminous.verysmarttv.action.LAUNCHER_SETTINGS";
    /** Sent by the "library" action word of app_keys: open the App library over Home (needs app_library). */
    public static final String ACTION_APP_LIBRARY = "com.itsluminous.verysmarttv.action.APP_LIBRARY";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    public static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    /** The booleans and counts every controller reads (DESIGN.md §8, HomeState); filled in onCreate. */
    private final HomeState state = new HomeState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private Wallpaper wallpaper;
    private float density;

    /** Boot and wake opens, the intents Home answers, the Launcher settings result. */
    private StartupRouter startup;
    /** The module-card prompt and the Module cards list. */
    private CardPrompts cardPrompts;
    /** The glass dropdowns: popup window, top_menu, the Settings menu, the tile menu. */
    private GlassPopups popups;
    /** The Inputs menu, how an input opens, the Live TV intent. */
    private InputsMenu inputs;
    /** The header and the pills. */
    private TopBarController topBar;
    /** The app grid: tiles, focus, the glass highlight, the library. */
    private GridController grid;
    /** The glow behind the grid: the shelf asks it for colours and fades. */
    private AmbientGlow glow;
    /** The top shelf and the carousel on it. */
    private ShelfController shelf;
    private CarouselController carousel;
    /** Reorder mode and edit-dock mode. */
    private ReorderSession reorder;
    private DockEditSession dockEdit;
    /** Every key Home receives. */
    private KeyDispatcher keys;
    /** home_idle_clock_minutes and back_opens_clock. */
    private IdleClock idleClock;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        keys = new KeyDispatcher(this, cfg);
        ClockFormat.apply(getWindow().getDecorView(), cfg.clockFormat);
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this);
        startup = new StartupRouter(this, cfg);
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
        keys.cancelHoldOk();
        idleClock.cancel();
        reorder.endReorder();   // Home or another app while reordering keeps the order so far
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
            keys.cancelHoldOk();   // a dialog or another window took the remote while OK was held
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RemoteKeys.release(this);
        carousel.release();
        keys.cancelHoldOk();
        grid.onDestroy();
        startup.onDestroy();
        wallpaper.close();
        topBar.unregisterCallbacks();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return keys.dispatch(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        idleClock.onBackPressed();
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

    /** Whether the framework lays this screen out right-to-left (supportsRtl): the drawn gradients and the grid's key arithmetic follow it. */
    @Override
    public boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public void open(@NonNull Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEventToViews(@NonNull KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    @NonNull
    public AppAdapter.TileListener tileListener() {
        return this;
    }

    @Override
    @NonNull
    public StartupRouter startup() {
        return startup;
    }

    @Override
    @NonNull
    public CardPrompts cards() {
        return cardPrompts;
    }

    @Override
    @NonNull
    public GlassPopups popups() {
        return popups;
    }

    @Override
    @NonNull
    public InputsMenu inputs() {
        return inputs;
    }

    @Override
    @NonNull
    public TopBarController topBar() {
        return topBar;
    }

    @Override
    @NonNull
    public GridController grid() {
        return grid;
    }

    @Override
    @NonNull
    public ShelfController shelf() {
        return shelf;
    }

    @Override
    @NonNull
    public CarouselController carousel() {
        return carousel;
    }

    @Override
    @NonNull
    public AmbientGlow glow() {
        return glow;
    }

    @Override
    @NonNull
    public ReorderSession reorder() {
        return reorder;
    }

    @Override
    @NonNull
    public DockEditSession dockEdit() {
        return dockEdit;
    }

    @Override
    @NonNull
    public KeyDispatcher keys() {
        return keys;
    }

    @Override
    @NonNull
    public IdleClock idleClock() {
        return idleClock;
    }

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

    /** Hold OK (or the Menu key) on a tile: the tile menu next to it. */
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
}
