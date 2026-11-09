package com.itsluminous.verysmarttv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.itsluminous.verysmarttv/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild: the setup
 * tool pushes a theme folder there and restarts Home. A key of a feature this launcher dropped ({@link #IGNORED_KEYS},
 * ADR-008) is ignored with one log line each, so an older theme still loads.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "VerySmartTV.Config";
    static final String FILE_NAME = "config.json";
    static final String WALLPAPER_NAME = "wallpaper.jpg";
    static final String SETTINGS_NAME = "launcher_settings";
    static final String ROWS_BELOW_DOCK_KEY = "rows_below_dock";
    static final String DOCK_OPACITY_KEY = "dock_opacity";
    static final String CLOCK_FORMAT_KEY = "clock_format";
    /** The shelf height chosen in Launcher settings, percent (hero_height); unset means the theme's key decides. */
    static final String HERO_HEIGHT_KEY = "hero_height";
    static final int HERO_HEIGHT_MIN = 50, HERO_HEIGHT_MAX = 130;
    /**
     * hero_height 0, the default: the shelf is the layout's own 100 while the carousel is on (hero_feed, after the
     * Launcher settings toggle) and 80 without it, where the app's name alone does not need the room.
     */
    static final int HERO_HEIGHT_AUTO = 0, HERO_HEIGHT_WITH_FEED = 100, HERO_HEIGHT_WITHOUT_FEED = 80;
    /**
     * The Trakt recommendations toggle in Launcher settings: 1 on, 0 off, unset means the theme's hero_feed decides.
     * Off leaves the launcher exactly as with "hero_feed": false (the shelf names the app, no provider is read).
     */
    static final String HERO_FEED_KEY = "hero_feed";
    /**
     * The System bars toggle in Launcher settings: 1 on, 0 off, unset means the theme's widgets key decides. The
     * RAM and Storage bars themselves land in Phase 3 (DESIGN.md §9.1); until then the value is read and nothing is drawn.
     */
    static final String WIDGETS_KEY = "widgets";
    /**
     * Keys of features this launcher dropped (ADR-008). A theme that still carries one loads, and the key is
     * ignored with one {@code Log.i} line, so an older config.json never fails and nobody wonders where the feature went.
     */
    static final List<String> IGNORED_KEYS = Collections.unmodifiableList(Arrays.asList(
            "festival", "festival_until", "seasonal_skin", "greeting", "accent_color", "game_day", "race_calendar",
            "sky_intensity", "latitude", "longitude", "wallpaper_rotation_minutes", "wallpaper_order",
            "wallpaper_moods", "wallpaper_captions", "shamsi_date", "lunar_date", "lunar_utc_offset", "show_stats"));
    /**
     * A choice older launchers saved from their Advanced effects row. Advanced effects now follow the Animations
     * setting and the theme's advanced_effects key, and this key is dropped wherever it turns up (load, restore):
     * both old Animations rows wrote it false on every toggle, which then outlived turning animations back on.
     */
    static final String ADVANCED_EFFECTS_LEGACY_KEY = "advanced_effects";
    /**
     * Counts the changes made in Launcher settings. Home remembers the count it was built with and rebuilds on
     * resume when it differs, however the settings were left (see MainActivity#onResume).
     */
    static final String SETTINGS_REVISION_KEY = "settings_revision";

    /** True when Launcher settings changed something since Home was built at revision {@code seen}. */
    static boolean settingsChanged(long seen, long current) {
        return seen != current;
    }
    private static final String THEME_REVISION_NAME = "theme_revision";
    private static final String THEME_SIGNATURE_KEY = "theme_signature";

    final List<String> dockApps;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    final boolean fillDock;
    final List<String> hiddenApps;
    /** Packages pre-seeded into the App library's "added" set (config.xml, library_apps); empty by default. */
    final List<String> libraryApps;
    /** Packages the App library catalogue never offers, even with app_library on (config.xml, library_excluded_apps). */
    final List<String> libraryExcludedApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String liveTvInputId;
    /** Package to the action its tile sends instead of opening the start screen (config.xml, launch_actions). */
    final Map<String, String> launchActions;
    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    final Map<String, String> appLabels;
    /** Package to a bundled banner_* drawable its tile shows instead of the app's art (config.xml, app_banners). */
    final Map<String, String> appBanners;
    final String sourcePanelService;
    final String sourcePanelAction;
    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    final String sourcePanelExtra;
    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    final boolean coocaaSources;
    final String inputAction;
    /** Vendor source numbers for passthrough inputs (config.xml or theme input_sources). */
    final List<String> inputSources;
    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml or theme input_uri_scheme). */
    final String inputUriScheme;
    final int columns;
    final boolean animations;
    /** The theme's advanced_effects; only in force while animations is on (every reader checks both). */
    final boolean advancedEffects;
    final int rowsBelowDock;
    /** The date on its own line under the clock instead of beside it (config.xml, date_below_clock). */
    final boolean dateBelowClock;
    /** The VPN state as its own pill after the network pill, instead of a badge inside it (config.xml, vpn_pill). */
    final boolean vpnPill;
    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    final String locale;
    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    final String greetingName;
    final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    final boolean reducedMotion;
    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    final boolean bootToLiveTv;
    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    final boolean openOnStartChoice;
    /** Seconds the boot's app waits for a validated network; 0 opens it at once (open_on_start_network_wait). */
    final int openOnStartNetworkWait;
    /** The Open on start app also opens when the screen comes back on (config.xml, open_on_start_on_wake). */
    final boolean openOnStartOnWake;
    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    final boolean dockTitle;
    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    final boolean dockRow;
    /** Inputs, Wi-Fi and Settings folded into one gear button with a dropdown, like Google TV (config.xml top_menu). */
    final boolean topMenu;
    /** Direct launcher-settings header action; off by default (launcher_settings_button). */
    final boolean launcherSettingsButton;
    /** Names under every tile, dimmed until focused (config.xml always_show_names); the dock band grows for them. */
    final boolean alwaysShowNames;
    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    final boolean reorderButton;
    /** Show the Wi-Fi/Ethernet pill in the top bar (config.xml, show_network_pill). */
    final boolean showNetworkPill;
    /** Show the Inputs pill when the TV has inputs to list (config.xml, show_inputs_pill). */
    final boolean showInputsPill;
    /** Wi-Fi and Settings pills as round icon buttons, Inputs keeps its label (config.xml icon_only_buttons). */
    final boolean iconOnlyButtons;
    /** The Inputs pill as a round icon button too (config.xml icon_only_inputs). */
    final boolean iconOnlyInputs;
    /** With vpn_pill, the VPN pill as a round lock button (config.xml / theme icon_only_vpn). */
    final boolean iconOnlyVpn;
    /** Back on the Home screen opens the clock screensaver instead of doing nothing (config.xml / theme back_opens_clock). */
    final boolean backOpensClock;
    /** The ambient glow takes a new app's colour in one frame instead of dipping and fading (config.xml / theme glow_snap). */
    final boolean glowSnap;
    /** App library as a round button in the top bar (config.xml library_button); needs app_library. */
    final boolean libraryButton;
    /** The App library pill after the last Home row; false hides it while libraryButton is on (show_library_pill). */
    final boolean showLibraryPill;
    /** With icon_only_buttons, the Wi-Fi pill keeps the network name up to this width in dp (0: icon only). */
    final int wifiLabelWidth;
    /** Decode wallpapers in 32-bit colour instead of RGB_565 (config.xml wallpaper_true_color). */
    final boolean wallpaperTrueColor;
    /** The focused tile and the glass highlight swing past their target before settling (config.xml, focus_overshoot). */
    final boolean focusOvershoot;
    final String vpnApp;
    final boolean showHomeSettingsShortcut;
    /** App library pill after the last Home row (config.xml app_library). */
    final boolean appLibrary;
    /** OK directly toggles Home membership in the library (app_library_selection). */
    final boolean appLibrarySelection;
    /** OK opens every app in the library; Add to Home stays in the hold menu (library_ok_opens). */
    final boolean libraryOkOpens;
    /** Columns of the All apps grid, 2 to 8 (library_columns); 0 keeps the one horizontal row. */
    final int libraryColumns;
    /** Icon-only top-bar pills grow to show their name while focused (pill_labels_on_focus). */
    final boolean pillLabelsOnFocus;
    /** Launcher settings opens with the TV's own settings as its first, focused row (tv_settings_first). */
    final boolean tvSettingsFirst;
    /** Launcher settings as a side panel in the TV settings style, the gear pill kept (settings_panel). */
    final boolean settingsPanel;
    /** Every row behaves like the dock: the focused row scrolls up under the shelf, which names its app (shelf_every_row). */
    final boolean shelfEveryRow;
    /**
     * No dock (uniform_rows): one flat order laid out columns per row, the first row too, with no band, empty slots or
     * gap of its own; fill_dock reads true, rows_below_dock 0 and shelf_every_row on while it is set (UniformRows).
     * Needs dock_row and not single_row.
     */
    final boolean uniformRows;
    /**
     * The top shelf cycles the Trakt companion's items while the dock is at rest (hero_feed), every heroFeedInterval
     * seconds; the Trakt recommendations toggle in Launcher settings (HERO_FEED_KEY) overrides the theme's key.
     */
    final boolean heroFeed;
    /** The theme's hero_feed before the Launcher settings toggle, what its row shows while no choice is saved. */
    final boolean themeHeroFeed;
    final int heroFeedInterval;
    /** Tile bottom to the next row's tile top, in dp (row_gap); AppAdapter.ITEM_EXTRA_DP is the layout's own 45. */
    final int rowGap;
    /**
     * Height of the top shelf (the space above the dock line) as a percent of the layout's own, 50 to 130 (hero_height);
     * the dock and the rows under it move up with a shorter shelf, so more rows show. The carousel gives up synopsis
     * lines, chips and title size to fit.
     */
    final int heroHeight;
    /**
     * The theme's hero_height before the Launcher settings choice: 0 (auto) or 50 to 130. What Follow theme in
     * Launcher settings hands the choice back to; chosenHeroHeight(-1, themeHeroHeight, feed) is the value it gives.
     */
    final int themeHeroHeight;
    /**
     * The date on two lines, the day of the week over the day and month ("WEDNESDAY" over "4 NOVEMBER", each in the
     * TV's language), beside the clock (date_two_lines). Not with date_below_clock, whose extra line would push the
     * header down; the key reads false alongside it.
     */
    final boolean dateTwoLines;
    /**
     * The RAM and Storage bars beside the date (widgets, a boolean), after the System bars toggle in Launcher settings
     * (WIDGETS_KEY) over the theme's key. A theme from before the change may still carry the old array of chip names;
     * it reads as on when non-empty ({@link #widgetsKey}).
     */
    final boolean widgets;
    /** The theme's widgets before the Launcher settings choice, what its row shows while no choice is saved. */
    final boolean themeWidgets;
    /** Icon on a card tinted from that icon, instead of the app's banner (config.xml uniform_tiles). */
    final boolean uniformTiles;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    final int backgroundColor;
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    final int[] settingsKeycodes;
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    final int[] inputKeycodes;
    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    final List<String> appKeys;
    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    final boolean keyFilter;
    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    final boolean gamepadPassthrough;
    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    final boolean consumeVolumeKeys;
    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    final String stockHomeRedirect;
    /** A Live TV window the firmware opens by itself at power-on is sent back to Home (power_on_home). */
    final boolean powerOnHome;
    /** Every Home press opens this launcher, a held one too, instead of Android's long-press action (home_long_press_home). */
    final boolean homeLongPressHome;
    /** Home at every power-on, whatever the firmware put in front: an input, Live TV or an app (power_on_home_always). */
    final boolean powerOnHomeAlways;
    /** The Inputs menu lists only inputs reporting connected; the rest fold behind a More inputs row (config.xml / theme inputs_connected_only). */
    final boolean inputsConnectedOnly;
    /** Only CEC device rows in the Inputs menu show a connection state, never a bare HDMI port (config.xml / theme inputs_cec_state_only). */
    final boolean inputsCecStateOnly;
    /** "12", "24" or "system" (theme clock_format, or Launcher settings' Clock row); see ClockFormat. */
    final String clockFormat;
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    final boolean settingsKeyPanel;
    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    final boolean restoreKeyServices;
    /** Whether themes may show a wallpaper (config.json, wallpaper). */
    final boolean wallpaperEnabled;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    final File wallpaper;
    final int wallpaperDim;
    /** The dock band's opacity in percent (config.json dock_opacity, 30-100): below 100 the wallpaper shows through. */
    final int dockOpacity;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    final boolean cinematicHome;
    /** The glow in the focused app's colour behind the dock (config.xml, ambient_glow); false keeps the background plain. */
    final boolean ambientGlow;
    /** The focus highlight in the focused app's colour on every tile (config.xml, app_glow), as cinematic_home does for the dock. */
    final boolean appGlow;
    /** The rows fade out under the clock instead of being cut off (config.xml, fade_rows); TopFade. */
    final boolean fadeRows;
    /** Minutes Home may sit untouched before the clock screensaver (config.xml, home_idle_clock_minutes); 0 is off. */
    final int homeIdleClockMinutes;
    /** The dock band as light frosted glass like the pills (config.xml, dock_glass); false keeps the solid band. */
    final boolean dockGlass;
    /** The dock band's padding equals the gap between its tiles (config.xml, dock_even_padding); false keeps the fixed padding. */
    final boolean dockEvenPadding;
    /** The dock band and its edge draw (dock_band); false leaves the tiles floating on the wallpaper. */
    final boolean dockBand;
    /** The dock position dots under the cinematic shelf's name (config.xml, cinematic_dots); false leaves them out. */
    final boolean cinematicDots;
    /** The cinematic shelf keeps the white focus highlight instead of the app's colour (config.xml, cinematic_white_focus). */
    final boolean cinematicWhiteFocus;
    /** The Wi-Fi pill's icon green while the TV is online (config.xml, wifi_status_color); false keeps it white. */
    final boolean wifiStatusColor;
    /** Opacity of the app's artwork behind the cinematic shelf, 0 to 100 (config.xml, cinematic_art); 50 as before. */
    final int cinematicArt;
    /** Strength of the cinematic shelf's dark scrim, 0 to 100 percent (config.xml, cinematic_scrim); 100 as before. */
    final int cinematicScrim;
    /** Shape of the glow behind the focused app (config.xml, glow_style): {@link #GLOW_RADIAL} or {@link #GLOW_HORIZON}. */
    final String glowStyle;
    /** The focus highlight's look (config.xml, glass_style): {@link #GLASS_DEFAULT}, {@link #GLASS_RIM} or {@link #GLASS_HALO}. */
    final String glassStyle;

    static final String GLOW_RADIAL = "radial", GLOW_HORIZON = "horizon";
    static final String GLASS_DEFAULT = "glass", GLASS_RIM = "rim", GLASS_HALO = "halo";
    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    final boolean handleViewInputs;
    /** Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info (config.xml / theme tile_menu). */
    final boolean tileMenu;
    /** With wifi_status_color, the icon orange while connected without internet (a captive portal) and red while offline (config.xml, wifi_status_warnings). */
    final boolean wifiStatusWarnings;
    /** Apps shown as round logo buttons in the top bar, left of the network pill, and no longer as tiles (config.xml, top_bar_apps). */
    final List<String> topBarApps;
    /** The greeting above the clock (Good evening); false hides it (config.xml, show_greeting). */
    final boolean showGreeting;
    /** The date and the line between it and the clock in the clock's full white instead of dimmed (config.xml, header_white). */
    final boolean headerWhite;
    /** The clock screensaver over Home's wallpaper instead of the black glow (config.xml, clock_wallpaper). */
    final boolean clockWallpaper;
    /** How dark the clock screensaver's wallpaper is, 0 to 90 % (config.xml, clock_wallpaper_dim). */
    final int clockWallpaperDim;
    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (config.xml, clock_glow). */
    final boolean clockGlow;
    /** Every app in the dock's row, which scrolls sideways, and no grid below it (config.xml, single_row); needs dock_row. */
    final boolean singleRow;
    /** With single_row, the band runs behind every app in the row, not only the dock apps (config.xml, dock_band_all). */
    final boolean dockBandAll;

    private Config(List<String> dockApps, boolean fillDock, List<String> hiddenApps, List<String> libraryApps, List<String> libraryExcludedApps, String liveTvPackage,
                   String liveTvAction, String liveTvInputId, Map<String, String> launchActions, Map<String, String> appLabels,
                   Map<String, String> appBanners, String sourcePanelService, String sourcePanelAction,
                   String sourcePanelExtra, boolean coocaaSources, String inputAction, List<String> inputSources,
                   String inputUriScheme, int columns, int rowsBelowDock, boolean animations, boolean advancedEffects,
                   String greetingName, boolean moduleCards, boolean reducedMotion, boolean bootToLiveTv, boolean openOnStartChoice, boolean dockTitle, boolean dockRow, boolean topMenu, boolean launcherSettingsButton, boolean reorderButton, boolean showNetworkPill, boolean showInputsPill, boolean iconOnlyButtons, boolean iconOnlyInputs, boolean libraryButton, boolean showLibraryPill, int wifiLabelWidth, boolean alwaysShowNames, boolean wallpaperTrueColor, boolean focusOvershoot, String vpnApp,
                   boolean showHomeSettingsShortcut, boolean appLibrary, boolean appLibrarySelection, boolean libraryOkOpens, int libraryColumns, boolean pillLabelsOnFocus, boolean tvSettingsFirst, boolean settingsPanel, boolean shelfEveryRow, boolean uniformRows, boolean heroFeed, boolean themeHeroFeed, int heroFeedInterval, int rowGap, int heroHeight, int themeHeroHeight, boolean dateTwoLines, boolean widgets, boolean themeWidgets, boolean uniformTiles, int backgroundColor,
                   int[] settingsKeycodes, boolean settingsKeyPanel, boolean restoreKeyServices, boolean wallpaperEnabled, File wallpaper,
                   int wallpaperDim, int dockOpacity, boolean overlayApplied,
                   int[] inputKeycodes, List<String> appKeys, String stockHomeRedirect,
                   boolean cinematicHome, boolean ambientGlow, boolean appGlow, boolean fadeRows, int homeIdleClockMinutes,
                   boolean keyFilter, boolean dockGlass, String glowStyle,
                   boolean handleViewInputs, boolean tileMenu, boolean dockEvenPadding, boolean dockBand, boolean cinematicDots, boolean cinematicWhiteFocus, boolean wifiStatusColor, int cinematicScrim, int cinematicArt,
                   boolean gamepadPassthrough, boolean consumeVolumeKeys, boolean powerOnHome, boolean homeLongPressHome, boolean wifiStatusWarnings, List<String> topBarApps, boolean showGreeting, boolean headerWhite, boolean clockWallpaper, int clockWallpaperDim, boolean clockGlow, String glassStyle, boolean singleRow, boolean dockBandAll, boolean dateBelowClock, boolean vpnPill, String locale, boolean powerOnHomeAlways, boolean inputsConnectedOnly, boolean inputsCecStateOnly,
                   int openOnStartNetworkWait, boolean openOnStartOnWake, boolean iconOnlyVpn, String clockFormat, boolean backOpensClock, boolean glowSnap) {
        this.handleViewInputs = handleViewInputs;
        this.tileMenu = tileMenu;
        this.inputKeycodes = inputKeycodes;
        this.appKeys = Collections.unmodifiableList(appKeys);
        this.keyFilter = keyFilter;
        this.stockHomeRedirect = stockHomeRedirect;
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.fillDock = fillDock;
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.libraryApps = Collections.unmodifiableList(libraryApps);
        this.libraryExcludedApps = Collections.unmodifiableList(libraryExcludedApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.liveTvInputId = liveTvInputId;
        this.launchActions = Collections.unmodifiableMap(launchActions);
        this.appLabels = Collections.unmodifiableMap(appLabels);
        this.appBanners = Collections.unmodifiableMap(appBanners);
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.sourcePanelExtra = sourcePanelExtra;
        this.coocaaSources = coocaaSources;
        this.inputAction = inputAction;
        this.inputSources = Collections.unmodifiableList(inputSources);
        this.inputUriScheme = inputUriScheme;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.animations = animations;
        this.advancedEffects = advancedEffects;
        this.dateBelowClock = dateBelowClock;
        this.vpnPill = vpnPill;
        this.locale = locale;
        this.greetingName = greetingName;
        this.moduleCards = moduleCards;
        this.reducedMotion = reducedMotion;
        this.bootToLiveTv = bootToLiveTv;
        this.openOnStartChoice = openOnStartChoice;
        this.dockTitle = dockTitle;
        this.dockRow = dockRow;
        this.topMenu = topMenu;
        this.launcherSettingsButton = launcherSettingsButton;
        this.reorderButton = reorderButton;
        this.showNetworkPill = showNetworkPill;
        this.showInputsPill = showInputsPill;
        this.iconOnlyButtons = iconOnlyButtons;
        this.iconOnlyInputs = iconOnlyInputs;
        this.iconOnlyVpn = iconOnlyVpn;
        this.backOpensClock = backOpensClock;
        this.glowSnap = glowSnap;
        this.libraryButton = libraryButton;
        this.showLibraryPill = showLibraryPill;
        this.wifiLabelWidth = wifiLabelWidth;
        this.alwaysShowNames = alwaysShowNames;
        this.wallpaperTrueColor = wallpaperTrueColor;
        this.focusOvershoot = focusOvershoot;
        this.vpnApp = vpnApp;
        this.showHomeSettingsShortcut = showHomeSettingsShortcut;
        this.appLibrary = appLibrary;
        this.appLibrarySelection = appLibrarySelection;
        this.libraryOkOpens = libraryOkOpens;
        this.libraryColumns = libraryColumns;
        this.pillLabelsOnFocus = pillLabelsOnFocus;
        this.tvSettingsFirst = tvSettingsFirst;
        this.settingsPanel = settingsPanel;
        this.shelfEveryRow = shelfEveryRow;
        this.uniformRows = uniformRows;
        this.heroFeed = heroFeed;
        this.themeHeroFeed = themeHeroFeed;
        this.heroFeedInterval = heroFeedInterval;
        this.rowGap = rowGap;
        this.heroHeight = heroHeight;
        this.themeHeroHeight = themeHeroHeight;
        this.dateTwoLines = dateTwoLines;
        this.widgets = widgets;
        this.themeWidgets = themeWidgets;
        this.uniformTiles = uniformTiles;
        this.backgroundColor = backgroundColor;
        this.settingsKeycodes = settingsKeycodes;
        this.settingsKeyPanel = settingsKeyPanel;
        this.restoreKeyServices = restoreKeyServices;
        this.wallpaperEnabled = wallpaperEnabled;
        this.wallpaper = wallpaper;
        this.wallpaperDim = wallpaperDim;
        this.dockOpacity = dockOpacity;
        this.overlayApplied = overlayApplied;
        this.cinematicHome = cinematicHome;
        this.ambientGlow = ambientGlow;
        this.appGlow = appGlow;
        this.fadeRows = fadeRows;
        this.homeIdleClockMinutes = homeIdleClockMinutes;
        this.dockGlass = dockGlass;
        this.dockEvenPadding = dockEvenPadding;
        this.dockBand = dockBand;
        this.cinematicDots = cinematicDots;
        this.cinematicWhiteFocus = cinematicWhiteFocus;
        this.wifiStatusColor = wifiStatusColor;
        this.cinematicArt = cinematicArt;
        this.cinematicScrim = cinematicScrim;
        this.gamepadPassthrough = gamepadPassthrough;
        this.consumeVolumeKeys = consumeVolumeKeys;
        this.powerOnHome = powerOnHome;
        this.homeLongPressHome = homeLongPressHome;
        this.powerOnHomeAlways = powerOnHomeAlways;
        this.inputsConnectedOnly = inputsConnectedOnly;
        this.inputsCecStateOnly = inputsCecStateOnly;
        this.openOnStartNetworkWait = openOnStartNetworkWait;
        this.openOnStartOnWake = openOnStartOnWake;
        this.clockFormat = clockFormat;
        this.wifiStatusWarnings = wifiStatusWarnings;
        this.topBarApps = Collections.unmodifiableList(topBarApps);
        this.showGreeting = showGreeting;
        this.headerWhite = headerWhite;
        this.clockWallpaper = clockWallpaper;
        this.clockWallpaperDim = clockWallpaperDim;
        this.clockGlow = clockGlow;
        this.singleRow = singleRow;
        this.dockBandAll = dockBandAll;
        this.glowStyle = glowStyle(glowStyle);
        this.glassStyle = glassStyle(glassStyle);
    }

    /** A known glow_style, or radial (the default) for anything else. */
    static String glowStyle(String value) {
        return value != null && GLOW_HORIZON.equalsIgnoreCase(value.trim()) ? GLOW_HORIZON : GLOW_RADIAL;
    }

    /** A known glass_style, or glass (the default) for anything else. */
    static String glassStyle(String value) {
        if (value == null) return GLASS_DEFAULT;
        String v = value.trim();
        if (GLASS_RIM.equalsIgnoreCase(v)) return GLASS_RIM;
        return GLASS_HALO.equalsIgnoreCase(v) ? GLASS_HALO : GLASS_DEFAULT;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        boolean fillDock = res.getBoolean(R.bool.fill_dock);
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        List<String> libraryApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.library_apps)));
        List<String> libraryExcludedApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.library_excluded_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String liveTvInputId = res.getString(R.string.live_tv_input_id).trim();
        List<String> launchActions = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.launch_actions)));
        List<String> appLabels = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_labels)));
        List<String> appBanners = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_banners)));
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String panelExtra = res.getString(R.string.source_panel_extra).trim();
        boolean coocaaSources = res.getBoolean(R.bool.coocaa_sources);
        String inputAction = res.getString(R.string.input_action).trim();
        List<String> inputSources = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.input_sources)));
        String inputUriScheme = res.getString(R.string.input_uri_scheme).trim();
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        boolean animations = res.getBoolean(R.bool.animations);
        boolean advancedEffects = res.getBoolean(R.bool.advanced_effects);
        String greetingName = res.getString(R.string.greeting_name).trim();
        boolean moduleCards = res.getBoolean(R.bool.module_cards);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        boolean bootToLiveTv = res.getBoolean(R.bool.boot_to_live_tv);
        boolean openOnStartChoice = res.getBoolean(R.bool.open_on_start_choice);
        int openOnStartNetworkWait = res.getInteger(R.integer.open_on_start_network_wait);
        boolean openOnStartOnWake = res.getBoolean(R.bool.open_on_start_on_wake);
        boolean dockTitle = res.getBoolean(R.bool.dock_title);
        boolean dockRow = res.getBoolean(R.bool.dock_row);
        boolean topMenu = res.getBoolean(R.bool.top_menu);
        boolean launcherSettingsButton = res.getBoolean(R.bool.launcher_settings_button);
        boolean reorderButton = res.getBoolean(R.bool.reorder_button);
        boolean showNetworkPill = res.getBoolean(R.bool.show_network_pill);
        boolean showInputsPill = res.getBoolean(R.bool.show_inputs_pill);
        boolean iconOnlyButtons = res.getBoolean(R.bool.icon_only_buttons);
        boolean iconOnlyInputs = res.getBoolean(R.bool.icon_only_inputs);
        boolean iconOnlyVpn = res.getBoolean(R.bool.icon_only_vpn);
        boolean backOpensClock = res.getBoolean(R.bool.back_opens_clock);
        boolean glowSnap = res.getBoolean(R.bool.glow_snap);
        boolean libraryButton = res.getBoolean(R.bool.library_button);
        boolean showLibraryPill = res.getBoolean(R.bool.show_library_pill);
        int wifiLabelWidth = res.getInteger(R.integer.wifi_label_width);
        boolean alwaysShowNames = res.getBoolean(R.bool.always_show_names);
        boolean wallpaperTrueColor = res.getBoolean(R.bool.wallpaper_true_color);
        boolean focusOvershoot = res.getBoolean(R.bool.focus_overshoot);
        boolean tileMenu = res.getBoolean(R.bool.tile_menu);
        String vpn = res.getString(R.string.vpn_app).trim();
        boolean homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut);
        boolean library = res.getBoolean(R.bool.app_library);
        boolean librarySelection = res.getBoolean(R.bool.app_library_selection);
        boolean libraryOkOpens = res.getBoolean(R.bool.library_ok_opens);
        int libraryColumns = res.getInteger(R.integer.library_columns);
        boolean pillLabelsOnFocus = res.getBoolean(R.bool.pill_labels_on_focus);
        boolean tvSettingsFirst = res.getBoolean(R.bool.tv_settings_first);
        boolean settingsPanel = res.getBoolean(R.bool.settings_panel);
        boolean shelfEveryRow = res.getBoolean(R.bool.shelf_every_row);
        boolean uniformRows = res.getBoolean(R.bool.uniform_rows);
        boolean heroFeed = res.getBoolean(R.bool.hero_feed);
        int heroFeedInterval = res.getInteger(R.integer.hero_feed_interval);
        int rowGap = res.getInteger(R.integer.row_gap);
        int heroHeight = res.getInteger(R.integer.hero_height);
        boolean dateTwoLines = res.getBoolean(R.bool.date_two_lines);
        boolean widgets = res.getBoolean(R.bool.widgets);
        boolean uniformTiles = res.getBoolean(R.bool.uniform_tiles);
        int background = 0xFF000000;
        int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);
        boolean settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel);
        boolean restoreKeyServices = res.getBoolean(R.bool.restore_key_services);
        int[] inputKeycodes = res.getIntArray(R.array.input_key_keycodes);
        boolean handleViewInputs = res.getBoolean(R.bool.handle_view_inputs);
        List<String> appKeys = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_keys)));
        String stockHomeRedirect = res.getString(R.string.stock_home_redirect).trim();
        boolean cinematic = res.getBoolean(R.bool.cinematic_home);
        boolean ambientGlow = res.getBoolean(R.bool.ambient_glow);
        boolean appGlow = res.getBoolean(R.bool.app_glow);
        boolean fadeRows = res.getBoolean(R.bool.fade_rows);
        int idleClockMinutes = res.getInteger(R.integer.home_idle_clock_minutes);
        boolean keyFilter = res.getBoolean(R.bool.key_filter);
        boolean gamepadPassthrough = res.getBoolean(R.bool.gamepad_passthrough);
        boolean consumeVolumeKeys = res.getBoolean(R.bool.consume_volume_keys);
        boolean powerOnHome = res.getBoolean(R.bool.power_on_home);
        boolean homeLongPressHome = res.getBoolean(R.bool.home_long_press_home);
        boolean powerOnHomeAlways = res.getBoolean(R.bool.power_on_home_always);
        boolean inputsConnectedOnly = res.getBoolean(R.bool.inputs_connected_only);
        boolean inputsCecStateOnly = res.getBoolean(R.bool.inputs_cec_state_only);
        String clockFormat = res.getString(R.string.clock_format);
        boolean dockGlass = res.getBoolean(R.bool.dock_glass);
        boolean dockEvenPadding = res.getBoolean(R.bool.dock_even_padding);
        boolean dockBand = res.getBoolean(R.bool.dock_band);
        boolean cinematicDots = res.getBoolean(R.bool.cinematic_dots);
        boolean cinematicWhiteFocus = res.getBoolean(R.bool.cinematic_white_focus);
        boolean wifiStatusColor = res.getBoolean(R.bool.wifi_status_color);
        int cinematicArt = res.getInteger(R.integer.cinematic_art);
        int cinematicScrim = res.getInteger(R.integer.cinematic_scrim);
        String glowStyle = res.getString(R.string.glow_style);
        boolean wifiStatusWarnings = res.getBoolean(R.bool.wifi_status_warnings);
        boolean dateBelowClock = res.getBoolean(R.bool.date_below_clock);
        boolean vpnPill = res.getBoolean(R.bool.vpn_pill);
        String locale = res.getString(R.string.locale).trim();
        List<String> topBarApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.top_bar_apps)));
        boolean showGreeting = res.getBoolean(R.bool.show_greeting);
        boolean headerWhite = res.getBoolean(R.bool.header_white);
        boolean clockWallpaper = res.getBoolean(R.bool.clock_wallpaper);
        int clockWallpaperDim = res.getInteger(R.integer.clock_wallpaper_dim);
        boolean clockGlow = res.getBoolean(R.bool.clock_glow);
        String glassStyle = res.getString(R.string.glass_style);
        boolean singleRow = res.getBoolean(R.bool.single_row);
        boolean dockBandAll = res.getBoolean(R.bool.dock_band_all);
        boolean applied = false;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        JSONObject json = readOverlay(ctx, file, mounted);
        if (json != null) {
            applied = true;
            logIgnoredKeys(json);
            dock = stringList(json, "dock_apps", dock);
            fillDock = bool(json, "fill_dock", fillDock);
            hidden = stringList(json, "hidden_apps", hidden);
            libraryApps = stringList(json, "library_apps", libraryApps);
            libraryExcludedApps = stringList(json, "library_excluded_apps", libraryExcludedApps);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            liveTvInputId = string(json, "live_tv_input_id", liveTvInputId);
            launchActions = stringList(json, "launch_actions", launchActions);
            appLabels = stringList(json, "app_labels", appLabels);
            appBanners = stringList(json, "app_banners", appBanners);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            panelExtra = string(json, "source_panel_extra", panelExtra).trim();
            coocaaSources = bool(json, "coocaa_sources", coocaaSources);
            inputAction = string(json, "input_action", inputAction);
            inputSources = stringList(json, "input_sources", inputSources);
            inputUriScheme = string(json, "input_uri_scheme", inputUriScheme).trim();
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            animations = bool(json, "animations", animations);
            advancedEffects = bool(json, "advanced_effects", advancedEffects);
            greetingName = string(json, "greeting_name", greetingName).trim();
            moduleCards = bool(json, "module_cards", moduleCards);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            bootToLiveTv = bool(json, "boot_to_live_tv", bootToLiveTv);
            openOnStartChoice = bool(json, "open_on_start_choice", openOnStartChoice);
            openOnStartNetworkWait = integer(json, "open_on_start_network_wait", openOnStartNetworkWait, 0, 600);
            openOnStartOnWake = bool(json, "open_on_start_on_wake", openOnStartOnWake);
            dockTitle = bool(json, "dock_title", dockTitle);
            dockRow = bool(json, "dock_row", dockRow);
            topMenu = bool(json, "top_menu", topMenu);
            launcherSettingsButton = bool(json, "launcher_settings_button", launcherSettingsButton);
            reorderButton = bool(json, "reorder_button", reorderButton);
            showNetworkPill = bool(json, "show_network_pill", showNetworkPill);
            showInputsPill = bool(json, "show_inputs_pill", showInputsPill);
            iconOnlyButtons = bool(json, "icon_only_buttons", iconOnlyButtons);
            iconOnlyInputs = bool(json, "icon_only_inputs", iconOnlyInputs);
            iconOnlyVpn = bool(json, "icon_only_vpn", iconOnlyVpn);
            backOpensClock = bool(json, "back_opens_clock", backOpensClock);
            glowSnap = bool(json, "glow_snap", glowSnap);
            libraryButton = bool(json, "library_button", libraryButton);
            showLibraryPill = bool(json, "show_library_pill", showLibraryPill);
            wifiLabelWidth = integer(json, "wifi_label_width", wifiLabelWidth, 0, 300);
            alwaysShowNames = bool(json, "always_show_names", alwaysShowNames);
            wallpaperTrueColor = bool(json, "wallpaper_true_color", wallpaperTrueColor);
            focusOvershoot = bool(json, "focus_overshoot", focusOvershoot);
            tileMenu = bool(json, "tile_menu", tileMenu);
            vpn = string(json, "vpn_app", vpn);
            homeSettings = bool(json, "show_home_settings_shortcut", homeSettings);
            library = bool(json, "app_library", library);
            librarySelection = bool(json, "app_library_selection", librarySelection);
            libraryOkOpens = bool(json, "library_ok_opens", libraryOkOpens);
            libraryColumns = integer(json, "library_columns", libraryColumns, 0, 8);
            pillLabelsOnFocus = bool(json, "pill_labels_on_focus", pillLabelsOnFocus);
            tvSettingsFirst = bool(json, "tv_settings_first", tvSettingsFirst);
            settingsPanel = bool(json, "settings_panel", settingsPanel);
            shelfEveryRow = bool(json, "shelf_every_row", shelfEveryRow);
            uniformRows = bool(json, "uniform_rows", uniformRows);
            heroFeed = bool(json, "hero_feed", heroFeed);
            heroFeedInterval = integer(json, "hero_feed_interval", heroFeedInterval, 3, 120);
            rowGap = integer(json, "row_gap", rowGap, 29, 90);  // 29 is AppAdapter.MIN_ROW_GAP_DP
            heroHeight = heroHeightKey(integer(json, "hero_height", heroHeight, Integer.MIN_VALUE, Integer.MAX_VALUE), heroHeight);
            dateTwoLines = bool(json, "date_two_lines", dateTwoLines);
            widgets = widgetsKey(json, widgets);
            uniformTiles = bool(json, "uniform_tiles", uniformTiles);
            background = color(json, "background_color", background);
            settingsKeycodes = intArray(json, "settings_key_keycodes", settingsKeycodes);
            settingsKeyPanel = bool(json, "settings_key_panel", settingsKeyPanel);
            restoreKeyServices = bool(json, "restore_key_services", restoreKeyServices);
            inputKeycodes = intArray(json, "input_key_keycodes", inputKeycodes);
            handleViewInputs = bool(json, "handle_view_inputs", handleViewInputs);
            appKeys = stringList(json, "app_keys", appKeys);
            stockHomeRedirect = string(json, "stock_home_redirect", stockHomeRedirect);
            cinematic = bool(json, "cinematic_home", cinematic);
            ambientGlow = bool(json, "ambient_glow", ambientGlow);
            appGlow = bool(json, "app_glow", appGlow);
            fadeRows = bool(json, "fade_rows", fadeRows);
            idleClockMinutes = integer(json, "home_idle_clock_minutes", idleClockMinutes, 0, 1440);
            keyFilter = bool(json, "key_filter", keyFilter);
            gamepadPassthrough = bool(json, "gamepad_passthrough", gamepadPassthrough);
            consumeVolumeKeys = bool(json, "consume_volume_keys", consumeVolumeKeys);
            powerOnHome = bool(json, "power_on_home", powerOnHome);
            homeLongPressHome = bool(json, "home_long_press_home", homeLongPressHome);
            powerOnHomeAlways = bool(json, "power_on_home_always", powerOnHomeAlways);
            inputsConnectedOnly = bool(json, "inputs_connected_only", inputsConnectedOnly);
            inputsCecStateOnly = bool(json, "inputs_cec_state_only", inputsCecStateOnly);
            clockFormat = string(json, CLOCK_FORMAT_KEY, clockFormat);
            dockGlass = bool(json, "dock_glass", dockGlass);
            dockEvenPadding = bool(json, "dock_even_padding", dockEvenPadding);
            dockBand = bool(json, "dock_band", dockBand);
            cinematicDots = bool(json, "cinematic_dots", cinematicDots);
            cinematicWhiteFocus = bool(json, "cinematic_white_focus", cinematicWhiteFocus);
            wifiStatusColor = bool(json, "wifi_status_color", wifiStatusColor);
            cinematicArt = integer(json, "cinematic_art", cinematicArt, 0, 100);
            cinematicScrim = integer(json, "cinematic_scrim", cinematicScrim, 0, 100);
            wifiStatusWarnings = bool(json, "wifi_status_warnings", wifiStatusWarnings);
            dateBelowClock = bool(json, "date_below_clock", dateBelowClock);
            vpnPill = bool(json, "vpn_pill", vpnPill);
            locale = string(json, "locale", locale).trim();
            topBarApps = stringList(json, "top_bar_apps", topBarApps);
            showGreeting = bool(json, "show_greeting", showGreeting);
            headerWhite = bool(json, "header_white", headerWhite);
            clockWallpaper = bool(json, "clock_wallpaper", clockWallpaper);
            clockWallpaperDim = integer(json, "clock_wallpaper_dim", clockWallpaperDim, 0, 90);
            clockGlow = bool(json, "clock_glow", clockGlow);
            singleRow = bool(json, "single_row", singleRow);
            dockBandAll = bool(json, "dock_band_all", dockBandAll);
            glowStyle = string(json, "glow_style", glowStyle);
            glassStyle = string(json, "glass_style", glassStyle);
        }
        // A top-bar app is moved there, not copied: it leaves the grid.
        for (String pkg : topBarApps) if (!hidden.contains(pkg)) hidden.add(pkg);
        // A theme push or reset invalidates choices made on the TV. On first use, remember the
        // current theme without clearing choices from an older launcher version.
        SharedPreferences choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        dropLegacyChoices(choices);
        // Unmounted storage looks like a theme reset; compare only once the file is readable.
        if (mounted) syncThemeChoices(choices, dir, file);
        int chosenColumns = choices.getInt("columns", columns);
        if (chosenColumns >= 4 && chosenColumns <= 7) columns = chosenColumns;
        int chosenRows = choices.getInt(ROWS_BELOW_DOCK_KEY, rowsBelow);
        if (chosenRows >= 0 && chosenRows <= 3) rowsBelow = chosenRows;
        // uniform_rows: no dock, so the first row has no empty slots (fill_dock) and no rows are counted under it
        // (rows_below_dock: the rows fill downward from the shelf's bottom); every row takes the shelf (shelf_every_row).
        uniformRows = UniformRows.on(uniformRows, dockRow, singleRow);
        if (uniformRows) {
            fillDock = true;
            rowsBelow = 0;
            shelfEveryRow = true;
        }
        // The Trakt recommendations toggle, then the shelf height: its auto default (0) follows the feed as toggled.
        boolean themeHeroFeed = heroFeed;
        heroFeed = chosenHeroFeed(choices.getInt(HERO_FEED_KEY, -1), heroFeed);
        int themeHeroHeight = heroHeight;
        heroHeight = chosenHeroHeight(choices.getInt(HERO_HEIGHT_KEY, -1), heroHeight, heroFeed);
        // The System bars toggle over the theme's key. An older launcher saved the Widgets tick list as a string set
        // under the same key; that is read once more and migrated (chosenWidgets).
        boolean themeWidgets = widgets;
        widgets = chosenWidgets(choices, themeWidgets);
        animations = choices.getBoolean("animations", animations);
        // advanced_effects is the theme's alone: the Animations setting gates it (readers test animations && advancedEffects).
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        boolean wallpaperEnabled = json == null || bool(json, "wallpaper", true);
        if (!wallpaperEnabled) wallpaper = null;
        int wallpaperDim = json == null ? 0 : integer(json, "wallpaper_dim", 0, 0, 90);
        int dockOpacity = json == null ? 100 : integer(json, "dock_opacity", 100, 30, 100);
        dockOpacity = chosenDockOpacity(choices.getInt(DOCK_OPACITY_KEY, -1), dockOpacity);
        clockFormat = choices.getString(CLOCK_FORMAT_KEY, clockFormat);
        return new Config(dock, fillDock, hidden, libraryApps, libraryExcludedApps, liveTv, liveTvAction, liveTvInputId, packageMap(launchActions), packageMap(appLabels),
                packageMap(appBanners), panelService, panelAction, panelExtra, coocaaSources, inputAction, inputSources, inputUriScheme, Math.max(1, columns),
                Math.max(0, rowsBelow), animations, advancedEffects, greetingName, moduleCards, reducedMotion, bootToLiveTv,
                openOnStartChoice, dockTitle, dockRow, topMenu, launcherSettingsButton, reorderButton, showNetworkPill, showInputsPill,
                iconOnlyButtons, iconOnlyInputs, libraryButton, showLibraryPill, wifiLabelWidth, alwaysShowNames, wallpaperTrueColor, focusOvershoot,
                vpn, homeSettings, library, librarySelection, libraryOkOpens, libraryColumns, pillLabelsOnFocus, tvSettingsFirst, settingsPanel, shelfEveryRow, uniformRows, heroFeed, themeHeroFeed, heroFeedInterval, rowGap, heroHeight, themeHeroHeight, dateTwoLines, widgets, themeWidgets, uniformTiles, background,
                settingsKeycodes, settingsKeyPanel, restoreKeyServices, wallpaperEnabled, wallpaper,
                wallpaperDim, dockOpacity, applied, inputKeycodes, appKeys, stockHomeRedirect,
                cinematic, ambientGlow, appGlow, fadeRows,
                Math.max(0, idleClockMinutes), keyFilter, dockGlass, glowStyle, handleViewInputs, tileMenu, dockEvenPadding,
                dockBand, cinematicDots, cinematicWhiteFocus, wifiStatusColor, cinematicScrim, cinematicArt, gamepadPassthrough,
                consumeVolumeKeys, powerOnHome, homeLongPressHome, wifiStatusWarnings, topBarApps, showGreeting, headerWhite, clockWallpaper,
                Math.max(0, Math.min(90, clockWallpaperDim)), clockGlow, glassStyle, singleRow && dockRow, dockBandAll,
                dateBelowClock, vpnPill, locale, powerOnHomeAlways, inputsConnectedOnly, inputsCecStateOnly,
                Math.max(0, Math.min(600, openOnStartNetworkWait)), openOnStartOnWake, iconOnlyVpn, clockFormat, backOpensClock, glowSnap);
    }

    /**
     * Drops the choices no setting writes any more (ADVANCED_EFFECTS_LEGACY_KEY), so a value an older launcher
     * saved cannot turn the theme's effects off behind the Animations row. Backup.restore calls it too, so a
     * backup taken by that launcher restores clean.
     */
    static void dropLegacyChoices(SharedPreferences choices) {
        if (choices.contains(ADVANCED_EFFECTS_LEGACY_KEY)) choices.edit().remove(ADVANCED_EFFECTS_LEGACY_KEY).apply();
    }

    private static void syncThemeChoices(SharedPreferences choices, File dir, File config) {
        File revision = dir == null ? null : new File(dir, THEME_REVISION_NAME);
        String token = revisionToken(revision);
        String signature = fileStamp(config) + ":" + token;
        String seen = choices.getString(THEME_SIGNATURE_KEY, null);
        if (seen == null) {
            // Existing preferences survive an app upgrade, unless the first launch follows an
            // explicit theme push or reset by the updated tools.
            SharedPreferences.Editor editor = choices.edit();
            if (!"absent".equals(token)) editor.remove("columns").remove("animations")
                    .remove(ROWS_BELOW_DOCK_KEY)
                    .remove(DOCK_OPACITY_KEY).remove(CLOCK_FORMAT_KEY).remove(HERO_HEIGHT_KEY).remove(HERO_FEED_KEY).remove(WIDGETS_KEY);
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
        } else if (!seen.equals(signature)) {
            choices.edit().remove("columns").remove("animations")
                    .remove(ROWS_BELOW_DOCK_KEY).remove(DOCK_OPACITY_KEY)
                    .remove(CLOCK_FORMAT_KEY).remove(HERO_HEIGHT_KEY).remove(HERO_FEED_KEY).remove(WIDGETS_KEY)
                    .putString(THEME_SIGNATURE_KEY, signature).commit();
        }
    }

    /**
     * backup_restore: Backup.restore has just written the theme files, with today's stamp. Record their signature
     * as seen, so the next load does not take the write for a theme push and clear the layout choices that were
     * restored with them.
     */
    static void adoptThemeFiles(Context ctx) {
        File dir = dir(ctx);
        File config = dir == null ? null : new File(dir, FILE_NAME);
        File revision = dir == null ? null : new File(dir, THEME_REVISION_NAME);
        ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(THEME_SIGNATURE_KEY, fileStamp(config) + ":" + revisionToken(revision)).commit();
    }

    /** The dock band opacity chosen in Launcher settings, or the theme's when none (or an out-of-range one) is saved. */
    static int chosenDockOpacity(int chosen, int theme) {
        return chosen >= 30 && chosen <= 100 ? chosen : theme;
    }

    /**
     * The shelf height: the Launcher settings choice (hero_height) when one in range is saved, else the theme's key
     * when it names a height, else the auto rule for the theme's 0: HERO_HEIGHT_WITH_FEED with the carousel on
     * ({@code feed}, the effective hero_feed after the toggle), HERO_HEIGHT_WITHOUT_FEED without it.
     */
    static int chosenHeroHeight(int chosen, int theme, boolean feed) {
        if (chosen >= HERO_HEIGHT_MIN && chosen <= HERO_HEIGHT_MAX) return chosen;
        if (theme >= HERO_HEIGHT_MIN && theme <= HERO_HEIGHT_MAX) return theme;
        return autoHeroHeight(feed);
    }

    /** hero_height 0: 100 while the carousel is on, 80 without it. */
    static int autoHeroHeight(boolean feed) {
        return feed ? HERO_HEIGHT_WITH_FEED : HERO_HEIGHT_WITHOUT_FEED;
    }

    /** The Trakt recommendations toggle (HERO_FEED_KEY): 1 is on, 0 off, anything else leaves the theme's hero_feed. */
    static boolean chosenHeroFeed(int chosen, boolean theme) {
        return chosen == 1 || (chosen != 0 && theme);
    }

    /**
     * The bars in force: the System bars toggle (WIDGETS_KEY, 1 on, 0 off) when one is saved, else the theme's key.
     * A tick list an older launcher saved under the same key (a string set of chip names) is migrated on the way:
     * non-empty reads as on, empty as off, and the choice is written back as the int so the set is read only once.
     */
    static boolean chosenWidgets(SharedPreferences choices, boolean theme) {
        if (!choices.contains(WIDGETS_KEY)) return theme;
        try {
            return chosenWidgets(choices.getInt(WIDGETS_KEY, -1), theme);
        } catch (ClassCastException legacy) {
            Set<String> ticks = choices.getStringSet(WIDGETS_KEY, null);
            boolean on = ticks != null && !ticks.isEmpty();
            choices.edit().putInt(WIDGETS_KEY, on ? 1 : 0).apply();
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "migrated the widgets tick list to System bars " + (on ? "on" : "off"));
            return on;
        }
    }

    /** The System bars toggle: 1 is on, 0 off, anything else leaves the theme's widgets. */
    static boolean chosenWidgets(int chosen, boolean theme) {
        return chosen == 1 || (chosen != 0 && theme);
    }

    /**
     * A theme's {@code widgets}: a boolean as the Keys table has it, or the array of chip names older themes wrote
     * ({@code ["memory", "cpu", "storage"]}), which reads as on when it names anything and off when empty; anything
     * else keeps {@code fallback}.
     */
    static boolean widgetsKey(JSONObject o, boolean fallback) {
        Object v = o.opt("widgets");
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof JSONArray) {
            boolean on = ((JSONArray) v).length() > 0;
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "widgets is a boolean now; the chip list reads as " + (on ? "true" : "false"));
            return on;
        }
        return fallback;
    }

    /** One {@code Log.i} per {@link #IGNORED_KEYS} entry the theme still carries (ADR-008). */
    static List<String> ignoredKeys(JSONObject o) {
        List<String> found = new ArrayList<>();
        for (String key : IGNORED_KEYS) if (o.has(key)) found.add(key);
        return found;
    }

    private static void logIgnoredKeys(JSONObject o) {
        for (String key : ignoredKeys(o)) {
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "ignored key " + key + ": that feature is not in this launcher");
        }
    }

    /** A theme's hero_height value: 0 (auto) or 50 to 130; anything else keeps {@code fallback}. */
    static int heroHeightKey(int value, int fallback) {
        return value == HERO_HEIGHT_AUTO || (value >= HERO_HEIGHT_MIN && value <= HERO_HEIGHT_MAX) ? value : fallback;
    }

    private static String fileStamp(File file) {
        return file != null && file.isFile() ? file.length() + "@" + file.lastModified() : "absent";
    }

    private static String revisionToken(File file) {
        if (file == null || !file.isFile() || file.length() > 64) return "absent";
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : "absent";
        } catch (Exception ignored) {
            return "absent";
        }
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay.
     */
    private static JSONObject readOverlay(Context ctx, File file, boolean mounted) {
        File copy = new File(ctx.getFilesDir(), FILE_NAME);
        if (!mounted) return read(copy);
        JSONObject json = read(file);
        if (json == null) {
            copy.delete();
        } else {
            try (FileOutputStream out = new FileOutputStream(copy)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not keep a copy of " + file + ": " + e.getMessage());
            }
        }
        return json;
    }

    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    private static int[] intArray(JSONObject o, String key, int[] fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return fallback;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return fallback;
        }
        return out;
    }

    /** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
    static Map<String, String> packageMap(List<String> entries) {
        Map<String, String> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).trim();
            if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);
        }
        return out;
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
