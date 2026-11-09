package com.itsluminous.verysmarttv.home;

/**
 * The flags and counts that every part of Home reads (DESIGN.md §8, ADR-007): what the theme turned on, how the grid
 * is shaped, and where the activity is in its lifecycle. {@link HomeActivity#onCreate} fills the configuration half
 * from {@code Config} once; the lifecycle half follows the activity callbacks. Plain data, so a controller's
 * decisions can be tested against a {@code HomeState} built by hand.
 */
public final class HomeState {
    /** Tiles per row and dock slots (res/values/config.xml, columns). */
    public int columns = 4;
    /** The framework lays this screen out right-to-left (supportsRtl): the grid's key and position arithmetic follows it. */
    public boolean rtl;
    /** config.xml dock_row: the first row is a separate dock (band, parked low, top shelf) rather than plain grid. */
    public boolean dockRow;
    /** uniform_rows (UniformRows): no dock; the first row is the first {@link #columns} tiles of one order, with no band or gap. */
    public boolean uniformRows;
    /** config single_row: every app in the dock's row, scrolling sideways, no grid below. */
    public boolean singleRow;
    /** shelf_every_row, with dock_row and not single_row: every row scrolls to the dock's place and takes the shelf. */
    public boolean shelfEveryRow;
    public boolean animations;
    public boolean advancedEffects;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    public boolean reducedMotion;
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    public boolean cinematic;
    /**
     * hero_height: the shelf's height as a percent of the layout's own (Config.heroHeight, or the Launcher settings
     * choice). The grid's top padding, the dock line, scales by it; the carousel fits the room that leaves.
     */
    public int heroHeightPct = 100;
    /** The app library is on (config.xml, app_library): only then can the tile menu take an app off Home. */
    public boolean appLibraryEnabled;
    /** The same opt-in menu is available from either hold-OK or the remote's Menu key. */
    public boolean tileMenuEnabled;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    public boolean moduleCards;
    /** config.xml top_menu: Inputs, Wi-Fi and Settings folded into one gear. */
    public boolean topMenu;
    /** The top-bar pills on pill_bg_over_art, their frost over a dark base, while a carousel backdrop is up behind them. */
    public boolean pillsOverArt;

    /** Between onStart and onStop: Home is (at least partly) on screen. */
    public boolean started;
    /** Between onStop and onStart: Home was fully covered by an app, not just by the translucent quick panel. */
    public boolean stopped;
    /**
     * Between onResume and onPause: Home is in front. The idle clock only runs then; a key's ACTION_UP can still
     * reach Home after onPause, when that key opened another app, and must not start the clock over the app.
     */
    public boolean resumed;
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    public boolean tilesReady;
}
