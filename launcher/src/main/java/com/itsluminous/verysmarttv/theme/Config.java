package com.itsluminous.verysmarttv.theme;

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
import com.itsluminous.verysmarttv.grid.AppAdapter;
import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.clock.ClockFormat;
import com.itsluminous.verysmarttv.keys.HomeKeyService;
import com.itsluminous.verysmarttv.keys.InputKeyService;
import com.itsluminous.verysmarttv.inputs.InputsRequestActivity;
import com.itsluminous.verysmarttv.keys.ServiceRepair;
import com.itsluminous.verysmarttv.grid.TopFade;
import com.itsluminous.verysmarttv.grid.UniformRows;
import com.itsluminous.verysmarttv.R;
import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.settings.SettingsChoices;
import androidx.annotation.Nullable;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.itsluminous.verysmarttv/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild: the setup
 * tool pushes a theme folder there and restarts Home. A key of a feature this launcher dropped ({@link #ThemeKeys.IGNORED_KEYS},
 * ADR-008) is ignored with one log line each, so an older theme still loads.
 *
 * Read once in {@link HomeActivity#onCreate}; nothing here touches the network.
 */
public final class Config {
    public static final String FILE_NAME = "config.json";
    public static final String WALLPAPER_NAME = "wallpaper.jpg";
    private static final String THEME_REVISION_NAME = "theme_revision";
    /** Shape of the glow behind the focused app (config.xml, glow_style): {@link ThemeKeys#ThemeKeys.GLOW_RADIAL} or {@link ThemeKeys#ThemeKeys.GLOW_HORIZON}. */
    public final String glowStyle;
    /** The focus highlight's look (config.xml, glass_style): {@link ThemeKeys#ThemeKeys.GLASS_DEFAULT}, {@link ThemeKeys#ThemeKeys.GLASS_RIM} or {@link ThemeKeys#ThemeKeys.GLASS_HALO}. */
    public final String glassStyle;
    public final List<String> dockApps;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    public final boolean fillDock;
    public final List<String> hiddenApps;
    /** Packages pre-seeded into the App library's "added" set (config.xml, library_apps); empty by default. */
    public final List<String> libraryApps;
    /** Packages the App library catalogue never offers, even with app_library on (config.xml, library_excluded_apps). */
    public final List<String> libraryExcludedApps;
    public final String liveTvPackage;
    public final String liveTvAction;
    public final String liveTvInputId;
    /** Package to the action its tile sends instead of opening the start screen (config.xml, launch_actions). */
    public final Map<String, String> launchActions;
    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    public final Map<String, String> appLabels;
    /** Package to a bundled banner_* drawable its tile shows instead of the app's art (config.xml, app_banners). */
    public final Map<String, String> appBanners;
    public final String sourcePanelService;
    public final String sourcePanelAction;
    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    public final String sourcePanelExtra;
    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    public final boolean coocaaSources;
    public final String inputAction;
    /** Vendor source numbers for passthrough inputs (config.xml or theme input_sources). */
    public final List<String> inputSources;
    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml or theme input_uri_scheme). */
    public final String inputUriScheme;
    public final int columns;
    public final boolean animations;
    /** The theme's advanced_effects; only in force while animations is on (every reader checks both). */
    public final boolean advancedEffects;
    public final int rowsBelowDock;
    /** The date on its own line under the clock instead of beside it (config.xml, date_below_clock). */
    public final boolean dateBelowClock;
    /** The VPN state as its own pill after the network pill, instead of a badge inside it (config.xml, vpn_pill). */
    public final boolean vpnPill;
    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    public final String locale;
    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    public final String greetingName;
    public final boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    public final boolean reducedMotion;
    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    public final boolean bootToLiveTv;
    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    public final boolean openOnStartChoice;
    /** Seconds the boot's app waits for a validated network; 0 opens it at once (open_on_start_network_wait). */
    public final int openOnStartNetworkWait;
    /** The Open on start app also opens when the screen comes back on (config.xml, open_on_start_on_wake). */
    public final boolean openOnStartOnWake;
    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    public final boolean dockTitle;
    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    public final boolean dockRow;
    /** Inputs, Wi-Fi and Settings folded into one gear button with a dropdown, like Google TV (config.xml top_menu). */
    public final boolean topMenu;
    /** Direct launcher-settings header action; off by default (launcher_settings_button). */
    public final boolean launcherSettingsButton;
    /** Names under every tile, dimmed until focused (config.xml always_show_names); the dock band grows for them. */
    public final boolean alwaysShowNames;
    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    public final boolean reorderButton;
    /** Show the Wi-Fi/Ethernet pill in the top bar (config.xml, show_network_pill). */
    public final boolean showNetworkPill;
    /** Show the Inputs pill when the TV has inputs to list (config.xml, show_inputs_pill). */
    public final boolean showInputsPill;
    /** Wi-Fi and Settings pills as round icon buttons, Inputs keeps its label (config.xml icon_only_buttons). */
    public final boolean iconOnlyButtons;
    /** The Inputs pill as a round icon button too (config.xml icon_only_inputs). */
    public final boolean iconOnlyInputs;
    /** With vpn_pill, the VPN pill as a round lock button (config.xml / theme icon_only_vpn). */
    public final boolean iconOnlyVpn;
    /** Back on the Home screen opens the clock screensaver instead of doing nothing (config.xml / theme back_opens_clock). */
    public final boolean backOpensClock;
    /** The ambient glow takes a new app's colour in one frame instead of dipping and fading (config.xml / theme glow_snap). */
    public final boolean glowSnap;
    /** App library as a round button in the top bar (config.xml library_button); needs app_library. */
    public final boolean libraryButton;
    /** The App library pill after the last Home row; false hides it while libraryButton is on (show_library_pill). */
    public final boolean showLibraryPill;
    /** With icon_only_buttons, the Wi-Fi pill keeps the network name up to this width in dp (0: icon only). */
    public final int wifiLabelWidth;
    /** Decode wallpapers in 32-bit colour instead of RGB_565 (config.xml wallpaper_true_color). */
    final boolean wallpaperTrueColor;
    /** The focused tile and the glass highlight swing past their target before settling (config.xml, focus_overshoot). */
    public final boolean focusOvershoot;
    public final String vpnApp;
    public final boolean showHomeSettingsShortcut;
    /** App library pill after the last Home row (config.xml app_library). */
    public final boolean appLibrary;
    /** OK directly toggles Home membership in the library (app_library_selection). */
    public final boolean appLibrarySelection;
    /** OK opens every app in the library; Add to Home stays in the hold menu (library_ok_opens). */
    public final boolean libraryOkOpens;
    /** Columns of the All apps grid, 2 to 8 (library_columns); 0 keeps the one horizontal row. */
    public final int libraryColumns;
    /** Icon-only top-bar pills grow to show their name while focused (pill_labels_on_focus). */
    public final boolean pillLabelsOnFocus;
    /** Launcher settings opens with the TV's own settings as its first, focused row (tv_settings_first). */
    public final boolean tvSettingsFirst;
    /** Launcher settings as a side panel in the TV settings style, the gear pill kept (settings_panel). */
    public final boolean settingsPanel;
    /** Every row behaves like the dock: the focused row scrolls up under the shelf, which names its app (shelf_every_row). */
    public final boolean shelfEveryRow;
    /**
     * No dock (uniform_rows): one flat order laid out columns per row, the first row too, with no band, empty slots or
     * gap of its own; fill_dock reads true, rows_below_dock 0 and shelf_every_row on while it is set (UniformRows).
     * Needs dock_row and not single_row.
     */
    public final boolean uniformRows;
    /**
     * The top shelf cycles the Trakt companion's items while the dock is at rest (hero_feed), every heroFeedInterval
     * seconds; the Trakt recommendations toggle in Launcher settings (SettingsChoices.HERO_FEED_KEY) overrides the theme's key.
     */
    public final boolean heroFeed;
    /** The theme's hero_feed before the Launcher settings toggle, what its row shows while no choice is saved. */
    public final boolean themeHeroFeed;
    public final int heroFeedInterval;
    /** Tile bottom to the next row's tile top, in dp (row_gap); AppAdapter.ITEM_EXTRA_DP is the layout's own 45. */
    public final int rowGap;
    /**
     * Height of the top shelf (the space above the dock line) as a percent of the layout's own, 50 to 130 (hero_height);
     * the dock and the rows under it move up with a shorter shelf, so more rows show. The carousel gives up synopsis
     * lines, chips and title size to fit.
     */
    public final int heroHeight;
    /**
     * The theme's hero_height before the Launcher settings choice: 0 (auto) or 50 to 130. What Follow theme in
     * Launcher settings hands the choice back to; SettingsChoices.chosenHeroHeight(-1, themeHeroHeight, feed) is the value it gives.
     */
    public final int themeHeroHeight;
    /**
     * The date on two lines, the day of the week over the day and month ("WEDNESDAY" over "4 NOVEMBER", each in the
     * TV's language), beside the clock (date_two_lines). Not with date_below_clock, whose extra line would push the
     * header down; the key reads false alongside it.
     */
    public final boolean dateTwoLines;
    /**
     * The RAM and Storage bars beside the date (widgets, a boolean), after the System bars toggle in Launcher settings
     * (SettingsChoices.WIDGETS_KEY) over the theme's key. A theme from before the change may still carry the old array of chip names;
     * it reads as on when non-empty ({@link #widgetsKey}).
     */
    public final boolean widgets;
    /** The theme's widgets before the Launcher settings choice, what its row shows while no choice is saved. */
    public final boolean themeWidgets;
    /** Icon on a card tinted from that icon, instead of the app's banner (config.xml uniform_tiles). */
    public final boolean uniformTiles;
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    public final int backgroundColor;
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    public final int[] settingsKeycodes;
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    public final int[] inputKeycodes;
    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    public final List<String> appKeys;
    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    public final boolean keyFilter;
    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    public final boolean gamepadPassthrough;
    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    public final boolean consumeVolumeKeys;
    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    public final String stockHomeRedirect;
    /** A Live TV window the firmware opens by itself at power-on is sent back to Home (power_on_home). */
    public final boolean powerOnHome;
    /** Every Home press opens this launcher, a held one too, instead of Android's long-press action (home_long_press_home). */
    public final boolean homeLongPressHome;
    /** Home at every power-on, whatever the firmware put in front: an input, Live TV or an app (power_on_home_always). */
    public final boolean powerOnHomeAlways;
    /** The Inputs menu lists only inputs reporting connected; the rest fold behind a More inputs row (config.xml / theme inputs_connected_only). */
    public final boolean inputsConnectedOnly;
    /** Only CEC device rows in the Inputs menu show a connection state, never a bare HDMI port (config.xml / theme inputs_cec_state_only). */
    public final boolean inputsCecStateOnly;
    /** "12", "24" or "system" (theme clock_format, or Launcher settings' Clock row); see ClockFormat. */
    public final String clockFormat;
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    public final boolean settingsKeyPanel;
    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    public final boolean restoreKeyServices;
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    public final File wallpaper;
    final int wallpaperDim;
    /** The dock band's opacity in percent (config.json dock_opacity, 30-100): below 100 the wallpaper shows through. */
    public final int dockOpacity;
    /** Whether the overlay file was present and parsed. */
    final boolean overlayApplied;
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    public final boolean cinematicHome;
    /** The glow in the focused app's colour behind the dock (config.xml, ambient_glow); false keeps the background plain. */
    public final boolean ambientGlow;
    /** The focus highlight in the focused app's colour on every tile (config.xml, app_glow), as cinematic_home does for the dock. */
    public final boolean appGlow;
    /** The rows fade out under the clock instead of being cut off (config.xml, fade_rows); TopFade. */
    public final boolean fadeRows;
    /** Minutes Home may sit untouched before the clock screensaver (config.xml, home_idle_clock_minutes); 0 is off. */
    public final int homeIdleClockMinutes;
    /** The dock band as light frosted glass like the pills (config.xml, dock_glass); false keeps the solid band. */
    public final boolean dockGlass;
    /** The dock band's padding equals the gap between its tiles (config.xml, dock_even_padding); false keeps the fixed padding. */
    public final boolean dockEvenPadding;
    /** The dock band and its edge draw (dock_band); false leaves the tiles floating on the wallpaper. */
    public final boolean dockBand;
    /** The dock position dots under the cinematic shelf's name (config.xml, cinematic_dots); false leaves them out. */
    public final boolean cinematicDots;
    /** The cinematic shelf keeps the white focus highlight instead of the app's colour (config.xml, cinematic_white_focus). */
    public final boolean cinematicWhiteFocus;
    /** The Wi-Fi pill's icon green while the TV is online (config.xml, wifi_status_color); false keeps it white. */
    public final boolean wifiStatusColor;
    /** Opacity of the app's artwork behind the cinematic shelf, 0 to 100 (config.xml, cinematic_art); 50 as before. */
    public final int cinematicArt;
    /** Strength of the cinematic shelf's dark scrim, 0 to 100 percent (config.xml, cinematic_scrim); 100 as before. */
    public final int cinematicScrim;
    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    public final boolean handleViewInputs;
    /** Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info (config.xml / theme tile_menu). */
    public final boolean tileMenu;
    /** With wifi_status_color, the icon orange while connected without internet (a captive portal) and red while offline (config.xml, wifi_status_warnings). */
    public final boolean wifiStatusWarnings;
    /** Apps shown as round logo buttons in the top bar, left of the network pill, and no longer as tiles (config.xml, top_bar_apps). */
    public final List<String> topBarApps;
    /** The greeting above the clock (Good evening); false hides it (config.xml, show_greeting). */
    public final boolean showGreeting;
    /** The date and the line between it and the clock in the clock's full white instead of dimmed (config.xml, header_white). */
    public final boolean headerWhite;
    /** The clock screensaver over Home's wallpaper instead of the black glow (config.xml, clock_wallpaper). */
    public final boolean clockWallpaper;
    /** How dark the clock screensaver's wallpaper is, 0 to 90 % (config.xml, clock_wallpaper_dim). */
    public final int clockWallpaperDim;
    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (config.xml, clock_glow). */
    public final boolean clockGlow;
    /** Every app in the dock's row, which scrolls sideways, and no grid below it (config.xml, single_row); needs dock_row. */
    public final boolean singleRow;
    /** With single_row, the band runs behind every app in the row, not only the dock apps (config.xml, dock_band_all). */
    public final boolean dockBandAll;

    private Config(List<String> dockApps, boolean fillDock, List<String> hiddenApps, List<String> libraryApps, List<String> libraryExcludedApps, String liveTvPackage,
                   String liveTvAction, String liveTvInputId, Map<String, String> launchActions, Map<String, String> appLabels,
                   Map<String, String> appBanners, String sourcePanelService, String sourcePanelAction,
                   String sourcePanelExtra, boolean coocaaSources, String inputAction, List<String> inputSources,
                   String inputUriScheme, int columns, int rowsBelowDock, boolean animations, boolean advancedEffects,
                   String greetingName, boolean moduleCards, boolean reducedMotion, boolean bootToLiveTv, boolean openOnStartChoice, boolean dockTitle, boolean dockRow, boolean topMenu, boolean launcherSettingsButton, boolean reorderButton, boolean showNetworkPill, boolean showInputsPill, boolean iconOnlyButtons, boolean iconOnlyInputs, boolean libraryButton, boolean showLibraryPill, int wifiLabelWidth, boolean alwaysShowNames, boolean wallpaperTrueColor, boolean focusOvershoot, String vpnApp,
                   boolean showHomeSettingsShortcut, boolean appLibrary, boolean appLibrarySelection, boolean libraryOkOpens, int libraryColumns, boolean pillLabelsOnFocus, boolean tvSettingsFirst, boolean settingsPanel, boolean shelfEveryRow, boolean uniformRows, boolean heroFeed, boolean themeHeroFeed, int heroFeedInterval, int rowGap, int heroHeight, int themeHeroHeight, boolean dateTwoLines, boolean widgets, boolean themeWidgets, boolean uniformTiles, int backgroundColor,
                   int[] settingsKeycodes, boolean settingsKeyPanel, boolean restoreKeyServices, File wallpaper,
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
        this.glowStyle = ThemeKeys.glowStyle(glowStyle);
        this.glassStyle = ThemeKeys.glassStyle(glassStyle);
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    public static @NonNull File dir(@NonNull Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    public static @NonNull Config load(@NonNull Context ctx) {
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
        JSONObject json = ThemeKeys.readOverlay(ctx, file, mounted);
        if (json != null) {
            applied = true;
            ThemeKeys.logIgnoredKeys(json);
            dock = ThemeKeys.stringList(json, "dock_apps", dock);
            fillDock = ThemeKeys.bool(json, "fill_dock", fillDock);
            hidden = ThemeKeys.stringList(json, "hidden_apps", hidden);
            libraryApps = ThemeKeys.stringList(json, "library_apps", libraryApps);
            libraryExcludedApps = ThemeKeys.stringList(json, "library_excluded_apps", libraryExcludedApps);
            liveTv = ThemeKeys.string(json, "live_tv_package", liveTv);
            liveTvAction = ThemeKeys.string(json, "live_tv_action", liveTvAction);
            liveTvInputId = ThemeKeys.string(json, "live_tv_input_id", liveTvInputId);
            launchActions = ThemeKeys.stringList(json, "launch_actions", launchActions);
            appLabels = ThemeKeys.stringList(json, "app_labels", appLabels);
            appBanners = ThemeKeys.stringList(json, "app_banners", appBanners);
            panelService = ThemeKeys.string(json, "source_panel_service", panelService);
            panelAction = ThemeKeys.string(json, "source_panel_action", panelAction);
            panelExtra = ThemeKeys.string(json, "source_panel_extra", panelExtra).trim();
            coocaaSources = ThemeKeys.bool(json, "coocaa_sources", coocaaSources);
            inputAction = ThemeKeys.string(json, "input_action", inputAction);
            inputSources = ThemeKeys.stringList(json, "input_sources", inputSources);
            inputUriScheme = ThemeKeys.string(json, "input_uri_scheme", inputUriScheme).trim();
            columns = ThemeKeys.integer(json, "columns", columns, 1, 8);
            rowsBelow = ThemeKeys.integer(json, "rows_below_dock", rowsBelow, 0, 3);
            animations = ThemeKeys.bool(json, "animations", animations);
            advancedEffects = ThemeKeys.bool(json, "advanced_effects", advancedEffects);
            greetingName = ThemeKeys.string(json, "greeting_name", greetingName).trim();
            moduleCards = ThemeKeys.bool(json, "module_cards", moduleCards);
            reducedMotion = ThemeKeys.bool(json, "reduced_motion", reducedMotion);
            bootToLiveTv = ThemeKeys.bool(json, "boot_to_live_tv", bootToLiveTv);
            openOnStartChoice = ThemeKeys.bool(json, "open_on_start_choice", openOnStartChoice);
            openOnStartNetworkWait = ThemeKeys.integer(json, "open_on_start_network_wait", openOnStartNetworkWait, 0, 600);
            openOnStartOnWake = ThemeKeys.bool(json, "open_on_start_on_wake", openOnStartOnWake);
            dockTitle = ThemeKeys.bool(json, "dock_title", dockTitle);
            dockRow = ThemeKeys.bool(json, "dock_row", dockRow);
            topMenu = ThemeKeys.bool(json, "top_menu", topMenu);
            launcherSettingsButton = ThemeKeys.bool(json, "launcher_settings_button", launcherSettingsButton);
            reorderButton = ThemeKeys.bool(json, "reorder_button", reorderButton);
            showNetworkPill = ThemeKeys.bool(json, "show_network_pill", showNetworkPill);
            showInputsPill = ThemeKeys.bool(json, "show_inputs_pill", showInputsPill);
            iconOnlyButtons = ThemeKeys.bool(json, "icon_only_buttons", iconOnlyButtons);
            iconOnlyInputs = ThemeKeys.bool(json, "icon_only_inputs", iconOnlyInputs);
            iconOnlyVpn = ThemeKeys.bool(json, "icon_only_vpn", iconOnlyVpn);
            backOpensClock = ThemeKeys.bool(json, "back_opens_clock", backOpensClock);
            glowSnap = ThemeKeys.bool(json, "glow_snap", glowSnap);
            libraryButton = ThemeKeys.bool(json, "library_button", libraryButton);
            showLibraryPill = ThemeKeys.bool(json, "show_library_pill", showLibraryPill);
            wifiLabelWidth = ThemeKeys.integer(json, "wifi_label_width", wifiLabelWidth, 0, 300);
            alwaysShowNames = ThemeKeys.bool(json, "always_show_names", alwaysShowNames);
            wallpaperTrueColor = ThemeKeys.bool(json, "wallpaper_true_color", wallpaperTrueColor);
            focusOvershoot = ThemeKeys.bool(json, "focus_overshoot", focusOvershoot);
            tileMenu = ThemeKeys.bool(json, "tile_menu", tileMenu);
            vpn = ThemeKeys.string(json, "vpn_app", vpn);
            homeSettings = ThemeKeys.bool(json, "show_home_settings_shortcut", homeSettings);
            library = ThemeKeys.bool(json, "app_library", library);
            librarySelection = ThemeKeys.bool(json, "app_library_selection", librarySelection);
            libraryOkOpens = ThemeKeys.bool(json, "library_ok_opens", libraryOkOpens);
            libraryColumns = ThemeKeys.integer(json, "library_columns", libraryColumns, 0, 8);
            pillLabelsOnFocus = ThemeKeys.bool(json, "pill_labels_on_focus", pillLabelsOnFocus);
            tvSettingsFirst = ThemeKeys.bool(json, "tv_settings_first", tvSettingsFirst);
            settingsPanel = ThemeKeys.bool(json, "settings_panel", settingsPanel);
            shelfEveryRow = ThemeKeys.bool(json, "shelf_every_row", shelfEveryRow);
            uniformRows = ThemeKeys.bool(json, "uniform_rows", uniformRows);
            heroFeed = ThemeKeys.bool(json, "hero_feed", heroFeed);
            heroFeedInterval = ThemeKeys.integer(json, "hero_feed_interval", heroFeedInterval, 3, 120);
            rowGap = ThemeKeys.integer(json, "row_gap", rowGap, 29, 90);  // 29 is AppAdapter.MIN_ROW_GAP_DP
            heroHeight = ThemeKeys.heroHeightKey(ThemeKeys.integer(json, "hero_height", heroHeight, Integer.MIN_VALUE, Integer.MAX_VALUE), heroHeight);
            dateTwoLines = ThemeKeys.bool(json, "date_two_lines", dateTwoLines);
            widgets = ThemeKeys.widgetsKey(json, widgets);
            uniformTiles = ThemeKeys.bool(json, "uniform_tiles", uniformTiles);
            background = ThemeKeys.color(json, "background_color", background);
            settingsKeycodes = ThemeKeys.intArray(json, "settings_key_keycodes", settingsKeycodes);
            settingsKeyPanel = ThemeKeys.bool(json, "settings_key_panel", settingsKeyPanel);
            restoreKeyServices = ThemeKeys.bool(json, "restore_key_services", restoreKeyServices);
            inputKeycodes = ThemeKeys.intArray(json, "input_key_keycodes", inputKeycodes);
            handleViewInputs = ThemeKeys.bool(json, "handle_view_inputs", handleViewInputs);
            appKeys = ThemeKeys.stringList(json, "app_keys", appKeys);
            stockHomeRedirect = ThemeKeys.string(json, "stock_home_redirect", stockHomeRedirect);
            cinematic = ThemeKeys.bool(json, "cinematic_home", cinematic);
            ambientGlow = ThemeKeys.bool(json, "ambient_glow", ambientGlow);
            appGlow = ThemeKeys.bool(json, "app_glow", appGlow);
            fadeRows = ThemeKeys.bool(json, "fade_rows", fadeRows);
            idleClockMinutes = ThemeKeys.integer(json, "home_idle_clock_minutes", idleClockMinutes, 0, 1440);
            keyFilter = ThemeKeys.bool(json, "key_filter", keyFilter);
            gamepadPassthrough = ThemeKeys.bool(json, "gamepad_passthrough", gamepadPassthrough);
            consumeVolumeKeys = ThemeKeys.bool(json, "consume_volume_keys", consumeVolumeKeys);
            powerOnHome = ThemeKeys.bool(json, "power_on_home", powerOnHome);
            homeLongPressHome = ThemeKeys.bool(json, "home_long_press_home", homeLongPressHome);
            powerOnHomeAlways = ThemeKeys.bool(json, "power_on_home_always", powerOnHomeAlways);
            inputsConnectedOnly = ThemeKeys.bool(json, "inputs_connected_only", inputsConnectedOnly);
            inputsCecStateOnly = ThemeKeys.bool(json, "inputs_cec_state_only", inputsCecStateOnly);
            clockFormat = ThemeKeys.string(json, SettingsChoices.CLOCK_FORMAT_KEY, clockFormat);
            dockGlass = ThemeKeys.bool(json, "dock_glass", dockGlass);
            dockEvenPadding = ThemeKeys.bool(json, "dock_even_padding", dockEvenPadding);
            dockBand = ThemeKeys.bool(json, "dock_band", dockBand);
            cinematicDots = ThemeKeys.bool(json, "cinematic_dots", cinematicDots);
            cinematicWhiteFocus = ThemeKeys.bool(json, "cinematic_white_focus", cinematicWhiteFocus);
            wifiStatusColor = ThemeKeys.bool(json, "wifi_status_color", wifiStatusColor);
            cinematicArt = ThemeKeys.integer(json, "cinematic_art", cinematicArt, 0, 100);
            cinematicScrim = ThemeKeys.integer(json, "cinematic_scrim", cinematicScrim, 0, 100);
            wifiStatusWarnings = ThemeKeys.bool(json, "wifi_status_warnings", wifiStatusWarnings);
            dateBelowClock = ThemeKeys.bool(json, "date_below_clock", dateBelowClock);
            vpnPill = ThemeKeys.bool(json, "vpn_pill", vpnPill);
            locale = ThemeKeys.string(json, "locale", locale).trim();
            topBarApps = ThemeKeys.stringList(json, "top_bar_apps", topBarApps);
            showGreeting = ThemeKeys.bool(json, "show_greeting", showGreeting);
            headerWhite = ThemeKeys.bool(json, "header_white", headerWhite);
            clockWallpaper = ThemeKeys.bool(json, "clock_wallpaper", clockWallpaper);
            clockWallpaperDim = ThemeKeys.integer(json, "clock_wallpaper_dim", clockWallpaperDim, 0, 90);
            clockGlow = ThemeKeys.bool(json, "clock_glow", clockGlow);
            singleRow = ThemeKeys.bool(json, "single_row", singleRow);
            dockBandAll = ThemeKeys.bool(json, "dock_band_all", dockBandAll);
            glowStyle = ThemeKeys.string(json, "glow_style", glowStyle);
            glassStyle = ThemeKeys.string(json, "glass_style", glassStyle);
        }
        // A top-bar app is moved there, not copied: it leaves the grid.
        for (String pkg : topBarApps) if (!hidden.contains(pkg)) hidden.add(pkg);
        // A theme push or reset invalidates choices made on the TV. On first use, remember the
        // current theme without clearing choices from an older launcher version.
        SharedPreferences choices = ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE);
        SettingsChoices.dropLegacyChoices(choices);
        // Unmounted storage looks like a theme reset; compare only once the file is readable.
        if (mounted) SettingsChoices.syncThemeChoices(choices, file, dir == null ? null : new File(dir, THEME_REVISION_NAME));
        int chosenColumns = choices.getInt("columns", columns);
        if (chosenColumns >= 4 && chosenColumns <= 7) columns = chosenColumns;
        int chosenRows = choices.getInt(SettingsChoices.ROWS_BELOW_DOCK_KEY, rowsBelow);
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
        heroFeed = SettingsChoices.chosenHeroFeed(choices.getInt(SettingsChoices.HERO_FEED_KEY, -1), heroFeed);
        int themeHeroHeight = heroHeight;
        heroHeight = SettingsChoices.chosenHeroHeight(choices.getInt(SettingsChoices.HERO_HEIGHT_KEY, -1), heroHeight, heroFeed);
        // The System bars toggle over the theme's key. An older launcher saved the Widgets tick list as a string set
        // under the same key; that is read once more and migrated (chosenWidgets).
        boolean themeWidgets = widgets;
        widgets = SettingsChoices.chosenWidgets(choices, themeWidgets);
        animations = choices.getBoolean("animations", animations);
        // advanced_effects is the theme's alone: the Animations setting gates it (readers test animations && advancedEffects).
        File wallpaper = dir != null ? new File(dir, WALLPAPER_NAME) : null;
        if (wallpaper != null && !(wallpaper.isFile() && wallpaper.length() > 0)) wallpaper = null;
        // A theme can also switch the wallpaper off while the file is still there.
        // config.json wallpaper: false keeps the plain colour; Wallpaper and ClockScene read only the file.
        if (json != null && !ThemeKeys.bool(json, "wallpaper", true)) wallpaper = null;
        int wallpaperDim = json == null ? 0 : ThemeKeys.integer(json, "wallpaper_dim", 0, 0, 90);
        int dockOpacity = json == null ? 100 : ThemeKeys.integer(json, "dock_opacity", 100, 30, 100);
        dockOpacity = SettingsChoices.chosenDockOpacity(choices.getInt(SettingsChoices.DOCK_OPACITY_KEY, -1), dockOpacity);
        clockFormat = choices.getString(SettingsChoices.CLOCK_FORMAT_KEY, clockFormat);
        return new Config(dock, fillDock, hidden, libraryApps, libraryExcludedApps, liveTv, liveTvAction, liveTvInputId, ThemeKeys.packageMap(launchActions), ThemeKeys.packageMap(appLabels),
                ThemeKeys.packageMap(appBanners), panelService, panelAction, panelExtra, coocaaSources, inputAction, inputSources, inputUriScheme, Math.max(1, columns),
                Math.max(0, rowsBelow), animations, advancedEffects, greetingName, moduleCards, reducedMotion, bootToLiveTv,
                openOnStartChoice, dockTitle, dockRow, topMenu, launcherSettingsButton, reorderButton, showNetworkPill, showInputsPill,
                iconOnlyButtons, iconOnlyInputs, libraryButton, showLibraryPill, wifiLabelWidth, alwaysShowNames, wallpaperTrueColor, focusOvershoot,
                vpn, homeSettings, library, librarySelection, libraryOkOpens, libraryColumns, pillLabelsOnFocus, tvSettingsFirst, settingsPanel, shelfEveryRow, uniformRows, heroFeed, themeHeroFeed, heroFeedInterval, rowGap, heroHeight, themeHeroHeight, dateTwoLines, widgets, themeWidgets, uniformTiles, background,
                settingsKeycodes, settingsKeyPanel, restoreKeyServices, wallpaper,
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
     * backup_restore: Backup.restore has just written the theme files, with today's stamp. Record their signature
     * as seen, so the next load does not take the write for a theme push and clear the layout choices that were
     * restored with them.
     */
    public static void adoptThemeFiles(@NonNull Context ctx) {
        File dir = dir(ctx);
        File config = dir == null ? null : new File(dir, FILE_NAME);
        File revision = dir == null ? null : new File(dir, THEME_REVISION_NAME);
        ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(SettingsChoices.THEME_SIGNATURE_KEY, SettingsChoices.themeSignature(config, revision)).commit();
    }
}
