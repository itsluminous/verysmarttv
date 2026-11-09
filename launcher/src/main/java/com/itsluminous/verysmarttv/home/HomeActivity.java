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
import com.itsluminous.verysmarttv.shelf.HeroArt;
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
    private TextView heroName;
    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    private View hero;
    private TextView heroMeta;
    private LinearLayout heroDots;
    /** config.xml cinematic_white_focus: the cinematic shelf keeps the white highlight. */
    private boolean cinematicWhiteFocus;
    private ImageView heroArt;
    /** config.xml cinematic_dots: false leaves the dock position dots out of the cinematic shelf. */
    private boolean heroDotsShown = true;
    /** The artwork's opacity behind the cinematic shelf (config.xml cinematic_art); 0 leaves it out. */
    private float heroArtAlpha = 0.5f;
    /** Softened artwork per app (HeroArt, about 5 KB each), made the first time its dock tile is focused. */
    private final Map<String, Bitmap> heroArtCache = new HashMap<>();
    /** The app whose artwork heroArt shows, so focusing it again only fades it back in. */
    private String heroArtKey;
    // ---- hero_feed (HeroFeed): the Trakt companion's items on the shelf while the dock is at rest ----
    private HeroFeed heroFeed;
    private boolean heroFeedOn;
    private int heroFeedIntervalMs;
    private List<HeroFeed.Entry> feedItems = Collections.emptyList();
    private int feedIndex;
    /** The shelf shows a carousel item instead of the focused app's name. */
    private boolean carouselShowing;
    /** Where the dock's top was when the shelf was last placed, so the taller carousel shelf can be re-placed. */
    private float lastDockTop = Float.NaN;
    /**
     * The carousel shelf at its full height, in dp: 10dp of padding above and below, the 36sp title (46dp), the meta
     * line (6 + 18dp), three synopsis lines (8 + 56dp), the chips (10 + 22dp) and the dots (10 + 6dp). The dock line
     * is placed for that whenever the companion has items, not for the lines the carousel happens to show: measuring
     * the live shelf made the line depend on which item was up and which layout pass ran last, and a short shelf
     * (hero_height) that trimmed the synopsis would have pulled the dock up a little further each time.
     */
    public static final float CAROUSEL_FULL_DP = 201f;
    /** heroFeedArt's design size (activity_main.xml, dp): 620 by 349, the bottom edge on the dock line at hero_height 100. */
    private static final float FEED_ART_W_DP = 620f, FEED_ART_H_DP = 349f;
    private ImageView heroFeedArt;
    private TextView heroSynopsis;
    private LinearLayout heroChips;
    private long feedArtId = -1;
    private final Runnable carouselAdvance = () -> {
        if (!carouselShowing || feedItems.isEmpty()) return;
        feedIndex = (feedIndex + 1) % feedItems.size();
        showCarouselItem(true);
    };
    private boolean heroShown;
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

    /** hero_feed with items: the carousel owns the shelf (CarouselController in Phase 2). */
    public boolean carouselHasItems() {
        return heroFeedOn && !feedItems.isEmpty();
    }

    /** The shelf shows a carousel item instead of the focused app's name (CarouselController in Phase 2). */
    public boolean carouselShowing() {
        return carouselShowing;
    }

    /** The shelf's measured height, for the dock line (ShelfController in Phase 2). */
    public int shelfMeasuredHeight() {
        return hero.getMeasuredHeight();
    }

    /** A rebuild that puts focus back: the shelf, the glow and the artwork start from nothing (ShelfController in Phase 2). */
    public void resetShelfForReload() {
        heroShown = false;
        hero.setAlpha(0f);
        glow.setAlpha(0f);
        heroArt.animate().cancel();
        heroArt.setAlpha(0f);
    }

    /** An updated app may bring new artwork; the backdrop is cheap to make again (ShelfController in Phase 2). */
    public void dropShelfArtCache() {
        heroArtCache.clear();
        heroArtKey = null;
    }

    @NonNull
    public ReorderSession reorder() {
        return reorder;
    }

    @NonNull
    public DockEditSession dockEdit() {
        return dockEdit;
    }

    /** cinematic_white_focus: the cinematic shelf keeps the white highlight (ShelfController in Phase 2). */
    public boolean cinematicWhiteFocus() {
        return cinematicWhiteFocus;
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
        heroName = findViewById(R.id.heroName);
        hero = findViewById(R.id.hero);
        // The hero is now a group (name, and the cinematic lines): without this every fade of the default
        // shelf would render it through an offscreen buffer first, as the glow above notes.
        hero.forceHasOverlappingRendering(false);
        heroMeta = findViewById(R.id.heroMeta);
        heroDots = findViewById(R.id.heroDots);
        heroArt = findViewById(R.id.heroArt);
        state.cinematic = cfg.cinematicHome;
        heroDotsShown = cfg.cinematicDots;
        cinematicWhiteFocus = cfg.cinematicWhiteFocus;
        heroArtAlpha = cfg.cinematicArt / 100f;
        if (state.cinematic) setUpCinematicShelf(cfg.cinematicScrim);
        else heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, shelfTitleSp(48f));   // the layout's 48sp, scaled with hero_height
        // A theme with the carousel (hero_feed): the app's name takes the carousel title's size and box too, so the
        // shelf reads the same whether the carousel is on, off in Launcher settings, or without items.
        carouselTypography = (cfg.themeHeroFeed || cfg.heroFeed) && state.dockRow && cfg.dockTitle;
        // The meta line and the dots keep their distance from the name in proportion too (ShelfScale).
        shelfTitleStyle();
        if (cfg.heroFeed && state.dockRow && cfg.dockTitle) setUpHeroFeed(cfg);
        // dock_title false: the name above the dock (and the cinematic lines under it) never shows; the glow
        // still follows the dock.
        if (!cfg.dockTitle) hero.setVisibility(View.GONE);
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
        if (heroFeed != null) {
            heroFeed.start();
            // The carousel owns the shelf while the companion has items: re-arm its timer, or put it up now.
            if (carouselShowing) showCarouselItem(false);
            else if (heroOwned() && heroShown && shelfTileFocused()) showCarousel(false);
        }
        idleClock.restart();
        cardPrompts.onResume();
        grid.onResume();
        wallpaper.restore();                                        // released by onTrimMemory
        grid.refreshOnResume();
        if (startup.openLiveTvAfterBoot()) return;
        topBar.onResume();
        // Back from an app opened from its tile, focus is still on that tile, so the cinematic "Last opened" line kept
        // the time from before this launch ("2 days ago" right after opening it). Refresh it.
        if (state.cinematic && heroMeta != null && grid.lastFocused() >= 0 && grid.lastFocused() < grid.adapter().appCount()) updateHeroMeta(grid.lastFocused());
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
        if (heroFeed != null) {
            // The carousel stays on the shelf for the return to Home; only its advance timer and the reader stop.
            handler.removeCallbacks(carouselAdvance);
            heroFeed.stop();
        }
        // No UP reaches a paused Home, so a vendor-key hold it saw go down would still open its app later.
        // Only Home's own share is dropped: a key service that saw the same DOWN keeps its hold.
        RemoteKeys.release(this);
        cancelHoldOk();
        idleClock.cancel();
        reorder.endReorder();   // Home or another app while reorder.active() keeps the order so far
        glow.stopAmbientAnimation();
        hero.animate().cancel();
        glow.cancelFade();
        heroArt.animate().cancel();
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
        heroArtCache.clear();
        heroArtKey = null;
        if (heroFeed != null) heroFeed.release();   // onResume starts it again
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
        if (heroFeed != null) heroFeed.release();
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
        if (heroFeedOn && handleCarouselKey(event)) return true;
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

    public void showHero(int position, float dockTopInRoot) {
        lastDockTop = dockTopInRoot;
        if (heroOwned()) {
            // hero_feed with items: the carousel owns the shelf; the focused app's name is under its tile instead.
            showOwnedShelf(position);
            return;
        }
        if (carouselShowing) return;   // the companion's items just went away: exitCarousel is on its way
        String label = grid.adapter().labelAt(position);
        int color = grid.adapter().ambientColorAt(position);
        // A short shelf (hero_height) can leave less room than the name needs: it then stays under the header rather
        // than over the clock, and the 40dp gap above the dock absorbs the difference.
        float y = Math.max(SHELF_MIN_TOP_DP * density, dockTopInRoot - shelfGapPx(false) - hero.getHeight());
        boolean changed = !label.contentEquals(heroName.getText());
        if (state.cinematic && heroArtAlpha > 0f) showHeroArt(position);

        if (!state.animations || state.reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            hero.animate().cancel();
            glow.cancelFade();
            fillHero(position, label);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.setAlpha(1f);
            if (glow.on()) {
                glow.setAmbient(color);
                glow.setAlpha(1f);
            }
            heroShown = true;
            return;
        }

        if (!heroShown) {
            glow.stopAmbientAnimation();
            hero.animate().cancel();
            glow.cancelFade();
            fillHero(position, label);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.animate().alpha(1f).setDuration(260).start();
            if (glow.on()) {
                glow.setAmbient(color);
                glow.fadeTo(1f, 600);
            }
            heroShown = true;
        } else if (changed) {
            if (state.advancedEffects) hero.animate().cancel();
            hero.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                fillHero(position, label);
                hero.setY(y);
                hero.setTranslationX(12 * density);
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (glow.on() && state.advancedEffects) {
                glow.transitionAmbient(color);
            } else if (glow.on() && color != glow.color() && glow.snaps()) {
                glow.cancelFade();
                glow.setAmbient(color);
                glow.setAlpha(1f);
            } else if (glow.on() && color != glow.color()) {
                glow.dipAndSwap(color, 200);
            }
        } else {
            hero.setY(y);
            // onPause cancels the fade-ins, and a second Home during one lands on the same tile, which starts none:
            // finish them, or the shelf and the glow stay dim until focus moves to another app.
            if (hero.getAlpha() < 1f) hero.animate().alpha(1f).setDuration(260).start();
            if (glow.on() && color == glow.color() && glow.getAlpha() < 1f) {
                glow.fadeTo(1f, 600);
            }
        }
    }

    /** The shelf's text for this dock app: its name and, on the cinematic shelf, "Last opened" and the dots. */
    private void fillHero(int position, String label) {
        heroName.setText(label);
        if (!state.cinematic) return;
        updateHeroMeta(position);
        updateHeroDots(position);
    }

    // ---- hero_feed carousel ----------------------------------------------------------------------

    /** hero_feed: the views the carousel adds to the shelf, and the provider reader. Nothing shows until items arrive. */
    private void setUpHeroFeed(Config cfg) {
        heroFeedOn = true;
        heroFeedIntervalMs = cfg.heroFeedInterval * 1000;
        heroFeedArt = findViewById(R.id.heroFeedArt);
        heroSynopsis = findViewById(R.id.heroSynopsis);
        heroChips = findViewById(R.id.heroChips);
        heroFeedArt.forceHasOverlappingRendering(false);
        // A frosted outline while the carousel has focus; nothing otherwise.
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x14FFFFFF);
        focus.setStroke((int) (2 * density), 0xB3FFFFFF);
        focus.setCornerRadius(22 * density);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focus);
        hero.setBackground(bg);
        // Focus can also reach the shelf from a tile's Up: the glass has no tile to sit on then.
        hero.setOnFocusChangeListener((v, has) -> {
            if (has) grid.hideGlass();
        });
        heroFeed = new HeroFeed(this, handler, items -> {
            // The dock line is placed for the carousel while there are items (CAROUSEL_FULL_DP in the grid's layout
            // listener): lay the grid out again when that changes, so the line does not depend on which pass ran last.
            boolean hadItems = !feedItems.isEmpty();
            feedItems = items;
            if (hadItems != !items.isEmpty()) grid.view().requestLayout();
            if (items.isEmpty()) {
                // Nothing to show: the shelf goes back to naming the focused app, as without hero_feed.
                exitCarousel();
                return;
            }
            feedIndex = Math.min(feedIndex, items.size() - 1);
            grid.applyTileNames(true);
            if (carouselShowing) showCarouselItem(false);
            else if (heroShown && shelfTileFocused()) showCarousel(true);
        });
    }

    /** hero_feed with items: the carousel owns the top shelf, whatever tile is focused. */
    private boolean heroOwned() {
        return heroFeedOn && !feedItems.isEmpty();
    }

    private boolean dockTileFocused() {
        int pos = grid.focusedTilePosition();
        return pos >= 0 && state.dockRow && (state.singleRow || pos < state.columns);
    }

    /** A tile whose row the shelf belongs to: the dock, or with shelf_every_row any row. */
    private boolean shelfTileFocused() {
        int pos = grid.focusedTilePosition();
        return pos >= 0 && state.dockRow && (state.singleRow || pos < state.columns || state.shelfEveryRow);
    }

    /**
     * The shelf for a focused tile while the carousel owns it: the carousel goes up if it is not up yet (and stays
     * where it is otherwise), the cinematic artwork of the app stays out, and only the glow takes the app's colour.
     */
    private void showOwnedShelf(int position) {
        int color = grid.adapter().ambientColorAt(position);
        boolean first = !heroShown;
        heroShown = true;
        if (!carouselShowing) {
            showCarousel(false);
        } else {
            placeCarouselShelf();
            // A rebuild (refreshTiles) or hideHero took the shelf to 0 while the carousel was up: bring it back.
            if (hero.getAlpha() < 1f) {
                hero.animate().cancel();
                if (state.animations && !state.reducedMotion) hero.animate().alpha(1f).translationX(0f).setDuration(260).start();
                else {
                    hero.setTranslationX(0f);
                    hero.setAlpha(1f);
                }
            }
            if (heroFeedArt.getAlpha() == 0f && heroFeedArt.getDrawable() != null) heroFeedArt.animate().alpha(1f).setDuration(260).start();
        }
        if (state.cinematic && heroArt.getAlpha() > 0f) {
            heroArt.animate().cancel();
            if (state.animations && !state.reducedMotion) heroArt.animate().alpha(0f).setDuration(300).start();
            else heroArt.setAlpha(0f);
            heroArtKey = null;   // so the app's artwork comes back when the shelf returns to its name
        }
        if (!glow.on()) return;
        if (!state.animations || state.reducedMotion) {
            glow.cancelFade();
            glow.setAmbient(color);
            glow.setAlpha(1f);
        } else if (first) {
            glow.stopAmbientAnimation();
            glow.cancelFade();
            glow.setAmbient(color);
            glow.fadeTo(1f, 600);
        } else if (color != glow.color()) {
            if (state.advancedEffects) {
                glow.transitionAmbient(color);
            } else if (glow.snaps()) {
                glow.cancelFade();
                glow.setAmbient(color);
                glow.setAlpha(1f);
            } else {
                glow.dipAndSwap(color, 200);
            }
        } else if (glow.getAlpha() < 1f) {
            glow.fadeTo(1f, 600);
        }
    }

    /** Puts the carousel on the shelf, in place of the app's name; animate swaps the name out, false fills in place. */
    private void showCarousel(boolean animate) {
        if (!state.resumed || carouselShowing || feedItems.isEmpty() || !heroShown || !shelfTileFocused() || Float.isNaN(lastDockTop)) return;
        carouselShowing = true;
        grid.applyTileNames(true);
        hero.setFocusable(true);
        int pad = (int) (10 * density);
        hero.setPadding(pad, pad, pad, pad);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) hero.getLayoutParams();
        lp.setMarginStart((int) (SHELF_START_DP * density) - pad);
        hero.setLayoutParams(lp);
        fitCarouselTitle();
        shelfMargin(heroMeta, 6);
        shelfMargin(heroDots, 10);
        heroMeta.setVisibility(View.VISIBLE);
        heroSynopsis.setVisibility(View.VISIBLE);
        heroChips.setVisibility(View.VISIBLE);
        heroDots.setVisibility(View.VISIBLE);
        heroFeedArt.setVisibility(View.VISIBLE);
        showCarouselItem(animate);
    }

    /** Fills the shelf with the current item and fades its backdrop in; (re)starts the advance timer. */
    private void showCarouselItem(boolean animate) {
        if (feedItems.isEmpty()) return;
        feedIndex = Math.max(0, Math.min(feedIndex, feedItems.size() - 1));
        final HeroFeed.Entry e = feedItems.get(feedIndex);
        handler.removeCallbacks(carouselAdvance);
        handler.postDelayed(carouselAdvance, heroFeedIntervalMs);
        Runnable fill = () -> {
            heroName.setText(e.title);
            String meta = e.meta != null ? e.meta : (e.year > 0 ? String.valueOf(e.year) : "");
            heroMeta.setText(meta.isEmpty() ? e.kicker : e.kicker + "  ·  " + meta);
            heroMeta.setVisibility(View.VISIBLE);
            heroSynopsis.setText(e.synopsis);
            heroChips.removeAllViews();
            addFeedChip("IMDb", e.imdb, 0x99F5C518);
            addFeedChip("RT", e.rt, 0x99FA320A);
            addFeedChip("Trakt", e.trakt, 0xA6ED1C24);
            drawDots(feedItems.size(), feedIndex);
            placeCarouselShelf();
        };
        if (animate && state.animations && !state.reducedMotion) {
            hero.animate().cancel();
            hero.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                if (!carouselShowing) return;
                fill.run();
                hero.setTranslationX(12 * density);
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
        } else {
            hero.animate().cancel();
            fill.run();
            hero.setTranslationX(0f);
            hero.setAlpha(1f);
        }
        feedArtId = e.id;
        heroFeed.backdrop(e, 640, 360, bmp -> {
            if (!carouselShowing || feedArtId != e.id) return;
            heroFeedArt.animate().cancel();
            topBar.setPillsOverArt(bmp != null);
            if (bmp == null) {
                heroFeedArt.animate().alpha(0f).setDuration(200).start();
                return;
            }
            if (!state.animations || state.reducedMotion || heroFeedArt.getAlpha() == 0f) {
                heroFeedArt.setImageBitmap(bmp);
                if (state.animations) heroFeedArt.animate().alpha(1f).setDuration(state.reducedMotion ? Motion.REDUCED_MS : 500).start();
                else heroFeedArt.setAlpha(1f);
                return;
            }
            heroFeedArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                heroFeedArt.setImageBitmap(bmp);
                heroFeedArt.animate().alpha(1f).setDuration(420).start();
            }).start();
        });
    }

    private void addFeedChip(String label, String value, int border) {
        if (value == null) return;
        TextView t = new TextView(this);
        t.setTypeface(heroMeta.getTypeface());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setTextColor(0xFFFFFFFF);
        t.setText(getString(R.string.rating_chip, label, value));
        int ph = (int) (10 * density), pv = (int) (4 * density);
        t.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setStroke((int) density, border);
        bg.setCornerRadius(999 * density);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) (8 * density);
        heroChips.addView(t, lp);
    }

    /**
     * The carousel shelf is taller than the name alone: measure it now and keep its text's bottom a little above the
     * dock. When it would reach up into the clock and date (the header ends about 108dp down), the synopsis gives up
     * lines, three to one, and the shelf never goes above the header.
     */
    private void placeCarouselShelf() {
        if (Float.isNaN(lastDockTop)) return;
        float minTop = SHELF_MIN_TOP_DP * density;   // under the clock (26dp down, 52sp tall)
        // Everything on first (an earlier item may have given some of it up), then hero_height: a short shelf starts
        // with fewer lines (two at 80 and below, one at 60 and below), and gives up the chips before the last line.
        heroSynopsis.setVisibility(View.VISIBLE);
        heroChips.setVisibility(ShelfScale.chipsShown(state.heroHeightPct) ? View.VISIBLE : View.GONE);
        int lines = ShelfScale.synopsisLines(state.heroHeightPct);
        heroSynopsis.setMaxLines(lines);
        float y;
        int gap = shelfGapPx(true);
        while (true) {
            hero.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            y = lastDockTop - gap - hero.getMeasuredHeight();
            if (y >= minTop) break;
            if (lines > 1) heroSynopsis.setMaxLines(--lines);
            else if (heroChips.getVisibility() == View.VISIBLE) heroChips.setVisibility(View.GONE);
            else if (heroSynopsis.getVisibility() == View.VISIBLE) heroSynopsis.setVisibility(View.GONE);
            else break;
        }
        hero.setY(Math.max(y, minTop));
    }

    /** The carousel title's widest and narrowest sizes: 36sp, and 24sp before an ellipsis (five sixths of each at hero_height 60). */
    private static final float CAROUSEL_TITLE_SP = 36f, CAROUSEL_TITLE_MIN_SP = 24f, CAROUSEL_TITLE_WIDTH_DP = 560f;
    /**
     * The app's name on the shelf in the carousel title's type (fitCarouselTitle: 36sp in its one-line box, the meta
     * line 6dp under it) rather than the layout's 48sp or the cinematic 68sp: a theme that has the carousel
     * (hero_feed), whether or not it is showing. Without it the name was the clock's size next to a 36sp carousel.
     */
    private boolean carouselTypography;

    /**
     * The shelf's title and spacing when it names the focused app: the carousel's own with carouselTypography, the
     * layout's (48sp, or the cinematic 68sp, the meta line 12dp under the name and the dots 16dp) otherwise.
     */
    private void shelfTitleStyle() {
        if (carouselTypography) {
            fitCarouselTitle();
            shelfMargin(heroMeta, 6);
            shelfMargin(heroDots, 10);
        } else {
            unfitCarouselTitle(shelfTitleSp(state.cinematic ? 68f : 48f));
            shelfMargin(heroMeta, 12);
            shelfMargin(heroDots, 16);
        }
    }

    /**
     * The carousel's title on one line: from 36sp down to 24sp, whichever largest fits 560dp, then an ellipsis. The
     * view is given that width and one 36sp line of height, so a shrunk title leaves the synopsis, chips and dots
     * where a short title has them (TextView's auto-size needs the bounded box; with wrap_content it never grows
     * back after shrinking). The text sits at the bottom of the box, next to the kicker line under it.
     */
    private void fitCarouselTitle() {
        float maxSp = shelfTitleSp(CAROUSEL_TITLE_SP), minSp = shelfTitleSp(CAROUSEL_TITLE_MIN_SP);
        heroName.setHorizontallyScrolling(false);   // singleLine's scrolling would give auto-size an unbounded width
        heroName.setMaxLines(1);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroName.setGravity(Gravity.BOTTOM | (heroName.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));   // keeps the layout's horizontal gravity
        ViewGroup.LayoutParams lp = heroName.getLayoutParams();
        lp.width = (int) (CAROUSEL_TITLE_WIDTH_DP * density);
        lp.height = oneLinePx(heroName, maxSp);
        heroName.setLayoutParams(lp);
        heroName.setAutoSizeTextTypeUniformWithConfiguration(Math.round(minSp), Math.round(maxSp), 1, TypedValue.COMPLEX_UNIT_SP);
    }

    /** The app's name again: one line of sp that overflows rather than wraps, the view as wide and tall as the text. */
    private void unfitCarouselTitle(float sp) {
        heroName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);   // setTextSize is ignored while on
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        heroName.setHorizontallyScrolling(true);
        heroName.setEllipsize(null);
        heroName.setGravity(Gravity.TOP | (heroName.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));
        ViewGroup.LayoutParams lp = heroName.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        heroName.setLayoutParams(lp);
    }

    /** One line of the view's typeface at sp, no font padding (as heroName is laid out), plus a pixel so that size still fits auto-size's test. */
    private static int oneLinePx(TextView t, float sp) {
        android.graphics.Paint p = new android.graphics.Paint(t.getPaint());
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, t.getResources().getDisplayMetrics()));
        android.graphics.Paint.FontMetricsInt fm = p.getFontMetricsInt();
        return fm.descent - fm.ascent + t.getPaddingTop() + t.getPaddingBottom() + 1;
    }

    /** The shelf never reaches up into the header: 26dp of padding and the 52sp clock end about 90dp down. */
    public static final float SHELF_MIN_TOP_DP = 90f;
    /** The shelf's left margin, dp: the header's own (activity_main.xml), so its texts start where the clock does. */
    static final float SHELF_START_DP = 56f;

    /** The shelf's title size for this hero_height: the layout's own at 100, two thirds of it at 60 (ShelfScale). */
    private float shelfTitleSp(float base) {
        return ShelfScale.textSp(base, state.heroHeightPct);
    }

    /**
     * The room the shelf keeps above the focused row's tiles, px: 40dp under the app's name (14 plus the 26 of the
     * dock layout), 12dp under the carousel's dots, both scaled with hero_height and never under 8dp.
     */
    public int shelfGapPx(boolean carousel) {
        return ShelfScale.spacingPx(carousel ? 12f : 40f, 8f, state.heroHeightPct, density);
    }

    /**
     * hero_feed: the backdrop's bottom edge sits on the dock line, so it shrinks and grows with the shelf (hero_height),
     * both ways to keep its baked-in fades where they were; {@code want} is the grid's top padding this layout settles on.
     * Nothing to do at 100, where activity_main.xml's size already fits.
     */
    public void fitFeedArt(int want) {
        if (heroFeedArt == null || state.heroHeightPct == 100) return;
        int artH = grid.gridTopInRoot() + want + Math.round(6 * density);
        if (artH <= 0) return;
        ViewGroup.LayoutParams lp = heroFeedArt.getLayoutParams();
        int artW = Math.round(artH * FEED_ART_W_DP / FEED_ART_H_DP);
        if (lp.height == artH && lp.width == artW) return;
        lp.height = artH;
        lp.width = artW;
        heroFeedArt.setLayoutParams(lp);
    }

    /**
     * The shelf back to the focused app's name (the companion has no items, or Home is going away); the focus back
     * on its tile if the carousel had it, and the names under the tiles off again.
     */
    private void exitCarousel() {
        handler.removeCallbacks(carouselAdvance);
        grid.applyTileNames(false);
        if (!carouselShowing) return;
        carouselShowing = false;
        topBar.setPillsOverArt(false);
        boolean hadFocus = hero.hasFocus();
        feedArtId = -1;
        hero.animate().cancel();
        hero.setTranslationX(0f);
        hero.setAlpha(1f);
        hero.setFocusable(false);
        hero.setPadding(0, 0, 0, 0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) hero.getLayoutParams();
        lp.setMarginStart((int) (SHELF_START_DP * density));
        hero.setLayoutParams(lp);
        shelfTitleStyle();
        heroSynopsis.setVisibility(View.GONE);
        heroChips.setVisibility(View.GONE);
        heroChips.removeAllViews();
        heroDots.setVisibility(state.cinematic && heroDotsShown ? View.VISIBLE : View.GONE);
        heroMeta.setVisibility(state.cinematic ? View.INVISIBLE : View.GONE);
        heroFeedArt.animate().cancel();
        if (state.animations && !state.reducedMotion) {
            heroFeedArt.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                if (!carouselShowing) heroFeedArt.setVisibility(View.GONE);
            }).start();
        } else {
            heroFeedArt.setAlpha(0f);
            heroFeedArt.setVisibility(View.GONE);
        }
        int pos = grid.focusedTilePosition();
        if (pos < 0) pos = grid.lastFocused();
        if (pos >= 0 && pos < grid.adapter().appCount()) {
            fillHero(pos, grid.adapter().labelAt(pos));
            if (!Float.isNaN(lastDockTop)) {
                hero.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
                hero.setY(Math.max(SHELF_MIN_TOP_DP * density, lastDockTop - shelfGapPx(false) - hero.getMeasuredHeight()));
            }
        }
        if (hadFocus && state.resumed) grid.focusTile(grid.lastFocused());
    }

    /**
     * Keys for the carousel. With the shelf focused: Left/Right move items, OK opens the title in the companion app,
     * Down and Back return to the tile, Up goes on to the top bar; the carousel stays on the shelf throughout. From a
     * dock tile, Up focuses the carousel.
     */
    private boolean handleCarouselKey(KeyEvent event) {
        if (!carouselShowing || reorder.active() || popups.isOpen() || cardPrompts.promptShowing()) return false;
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (hero.hasFocus()) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && feedItems.size() > 1) {
                        int n = feedItems.size();
                        feedIndex = (feedIndex + (code == KeyEvent.KEYCODE_DPAD_LEFT ? n - 1 : 1)) % n;
                        showCarouselItem(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (down) grid.focusTile(grid.lastFocused());
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) topBar.firstTopButton().requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (!down && !event.isCanceled() && !feedItems.isEmpty()) openFeedItem(feedItems.get(feedIndex));
                    return true;
                default:
                    return false;
            }
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP && down && dockTileFocused()) {
            grid.hideGlass();
            hero.requestFocus();
            return true;
        }
        return false;
    }

    /** A shelf line's margin above it, the layout's dp scaled with hero_height (ShelfScale), never under 4dp. */
    private void shelfMargin(View v, int dp) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.topMargin = ShelfScale.spacingPx(dp, 4f, state.heroHeightPct, density);
        v.setLayoutParams(lp);
    }

    /** OK on a carousel item: the companion's details page for it. */
    private void openFeedItem(HeroFeed.Entry e) {
        Intent i = new Intent().setComponent(HeroContract.DETAILS_ACTIVITY)
                .putExtra(HeroContract.EXTRA_TRAKT_ID, e.traktId).putExtra(HeroContract.EXTRA_KIND, e.kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Log.w("VerySmartTV.HeroFeed", "companion details not opened: " + ex.getMessage());
        }
    }

    // ---- cinematic shelf -------------------------------------------------------------------------

    /**
     * The parts that never change: a larger name, the meta line and the dots, the artwork and the scrim, at
     * scrimPercent of its strength (cinematic_scrim). cinematic_art 0 leaves the artwork out, 0 percent the scrim.
     */
    private void setUpCinematicShelf(int scrimPercent) {
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, shelfTitleSp(68f));
        heroName.setLetterSpacing(-0.025f);
        heroMeta.setVisibility(View.INVISIBLE);   // keeps its height, so the name does not jump when it fills in
        heroDots.setVisibility(heroDotsShown ? View.VISIBLE : View.GONE);
        heroArt.setVisibility(heroArtAlpha > 0f ? View.VISIBLE : View.GONE);
        // One stretched bitmap: apply its alpha while drawing instead of through an offscreen buffer.
        heroArt.forceHasOverlappingRendering(false);
        if (scrimPercent <= 0) return;   // cinematic_scrim 0: no scrim at all
        View scrim = findViewById(R.id.heroScrim);
        Drawable shade = scrimDrawable();
        // On the bitmap's paint, not the view: a view's alpha would draw the full screen through an offscreen buffer.
        shade.setAlpha(scrimPercent * 255 / 100);
        scrim.setBackground(shade);
        scrim.setVisibility(View.VISIBLE);
    }

    /**
     * Darkens the side the shelf's text is on and the lower part of the screen, so the name, the dock and
     * the grid stay readable over the artwork. One small bitmap stretched over the screen, like the glow.
     */
    private Drawable scrimDrawable() {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        boolean rtl = isRtl();   // the scrim is drawn, not laid out, so it mirrors itself
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(rtl ? w : 0, 0, rtl ? 0 : w, 0,
                new int[]{0xD1060406, 0x59060406, 0x00060406}, new float[]{0f, 0.42f, 0.7f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x00060406, 0x00060406, 0xDB060406, 0xFF060406}, new float[]{0f, 0.28f, 0.58f, 0.76f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable scrim = new BitmapDrawable(getResources(), bmp);
        scrim.setGravity(Gravity.FILL);
        return scrim;
    }

    /** Swaps the backdrop to this app's artwork: fade out, swap, fade in, the way the shelf's name changes. */
    private void showHeroArt(int position) {
        String key = grid.adapter().keyAt(position);
        if (key == null) return;
        heroArt.animate().cancel();
        if (key.equals(heroArtKey)) {
            fadeHeroArt(420);
            return;
        }
        Bitmap art = heroArtCache.get(key);
        if (art == null) {
            art = HeroArt.from(grid.adapter().artworkAt(position));
            if (art != null) heroArtCache.put(key, art);
        }
        heroArtKey = key;
        final Bitmap next = art;
        if (!state.animations || state.reducedMotion || heroArt.getAlpha() == 0f) {
            heroArt.setImageBitmap(next);
            fadeHeroArt(600);
            return;
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            heroArt.setImageBitmap(next);
            heroArt.animate().alpha(heroArtAlpha).setDuration(420).start();
        }).start();
    }

    private void fadeHeroArt(int ms) {
        if (!state.animations) heroArt.setAlpha(heroArtAlpha);
        else heroArt.animate().alpha(heroArtAlpha).setDuration(state.reducedMotion ? Math.min(ms, Motion.REDUCED_MS) : ms).start();
    }

    /** "Last opened yesterday, 9:40 PM", the time in white; nothing (but the same height) when it was never seen. */
    private void updateHeroMeta(int position) {
        long when = LastOpened.get(this, grid.adapter().packageAt(position));
        if (when <= 0) {
            heroMeta.setVisibility(View.INVISIBLE);
            return;
        }
        String rel = LastOpened.describe(this, when);
        String text = getString(R.string.last_opened, rel);
        SpannableString line = new SpannableString(text);
        int at = text.indexOf(rel);
        if (at >= 0) line.setSpan(new ForegroundColorSpan(0xFFFFFFFF), at, at + rel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroMeta.setText(line);
        heroMeta.setVisibility(View.VISIBLE);
    }

    /** One dot per dock app, the focused one drawn long and white. Hebrew docks start on the right, so do the dots. */
    private void updateHeroDots(int position) {
        if (!heroDotsShown) return;
        int count = Math.min(grid.adapter().dockFilled(), state.columns);
        // One dot per app in the focused row: the last row may be short (shelf_every_row, and so uniform_rows). The
        // apps alone: getItemCount also counts the All apps row the library adds after the last Home row (its own
        // full-width item, AppAdapter.isLibraryAt), which gave the last row one dot more than its tiles.
        if (state.shelfEveryRow) count = UniformRows.rowCount(position, state.columns, grid.adapter().appCount());
        drawDots(count, state.shelfEveryRow ? position % state.columns : position);
    }

    /** count dots, the on-th drawn long and white. Hebrew docks start on the right, so do the dots. */
    private void drawDots(int count, int on) {
        heroDots.setVisibility(count > 1 ? View.VISIBLE : View.INVISIBLE);
        if (heroDots.getChildCount() != count) {
            heroDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(this);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(3 * density);
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
                if (i > 0) lp.leftMargin = (int) (6 * density);
                heroDots.addView(dot, lp);
            }
        }
        for (int i = 0; i < count; i++) {
            View dot = heroDots.getChildAt(i);
            boolean lit = i == on;
            ((GradientDrawable) dot.getBackground()).setColor(lit ? 0xFFFFFFFF : 0x4DFFFFFF);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            int w = (int) ((lit ? 20 : 6) * density);
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
    }

    /**
     * Grid tiles (every tile with dock_row false): the glow behind the grid takes the focused tile's colour; the big
     * name above the dock does not show (every grid tile has its name underneath).
     */
    public void showGlowOnly(int position) {
        int color = grid.adapter().ambientColorAt(position);
        glow.stopAmbientAnimation();
        hero.animate().cancel();
        glow.cancelFade();
        hero.setAlpha(0f);
        heroArt.animate().cancel();
        heroArt.setAlpha(0f);
        heroShown = false;
        if (!glow.on()) return;
        if (!state.animations || state.reducedMotion) {
            glow.setAmbient(color);
            glow.setAlpha(1f);
            return;
        }
        if (color == glow.color() && glow.hasMask()) {
            glow.fadeTo(1f, 300);
            return;
        }
        if (glow.snaps()) {
            glow.setAmbient(color);
            glow.setAlpha(1f);
            return;
        }
        // The same dip-and-swap as the dock's top shelf.
        glow.dipAndSwap(color, glow.getAlpha() > 0.35f ? 200 : 0);
    }

    public void hideHero() {
        if (!heroShown) return;
        glow.stopAmbientAnimation();
        hero.animate().cancel();
        glow.cancelFade();
        heroArt.animate().cancel();
        heroShown = false;
        if (state.animations && !state.reducedMotion) {
            hero.animate().alpha(0f).setDuration(160).start();
            if (glow.on()) glow.fadeTo(0f, 300);
            heroArt.animate().alpha(0f).setDuration(300).start();
        } else {
            hero.setAlpha(0f);
            glow.setAlpha(0f);
            heroArt.setAlpha(0f);
        }
    }

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
