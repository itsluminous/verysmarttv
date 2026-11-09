package com.itsluminous.verysmarttv.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.shelf.HeroFeed;
import com.itsluminous.verysmarttv.theme.Wallpaper;
import com.itsluminous.verysmarttv.R;
import androidx.annotation.NonNull;

/**
 * settings_panel: Launcher settings as a side panel in the style of Android TV's own Settings, a dark sheet on the
 * right over Home, with a title, a section caption, and rows of icon, title and the current value. A row with
 * several values opens a second page in the same panel (a list with the current one marked); Back returns. The
 * value a row shows, and the row its picker marks, is the one in force: the saved choice, else what the theme gives,
 * so no picker has a "Theme default" row. The panel has no Follow theme or Wallpaper row (the full-screen page keeps
 * both): a TV has no file picker, and a theme push (tools/theme.sh) or a Backup &amp; restore clears every saved
 * choice, as Config.syncThemeChoices does when the theme's signature changes. With uniform_rows there is no dock,
 * so Rows below dock and Dock band opacity are left out too. Everything is drawn in code so the panel costs no
 * layout resource.
 */
public final class SettingsPanel {
    private static final int PANEL_BG = 0xFF1B1E22, ROW_FOCUS = 0xFF2F3338, CAPTION = 0xFF4C8DF5, SUBTITLE = 0xFFA7ADB4;
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    /** hero_height: the four shelf heights the panel offers, percent; a theme's own height outside them joins the list. */
    public static final int[] HERO_HEIGHTS = {60, 80, 100, 120};
    private static final int[] HERO_HEIGHT_NAMES = {R.string.hero_height_compact, R.string.hero_height_short,
            R.string.hero_height_normal, R.string.hero_height_tall};
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};

    private final Activity activity;
    private final Config config;
    private final SharedPreferences choices;
    private final Runnable changed;
    private final float density;
    private final LinearLayout list;
    private final TextView title;
    private final TextView caption;
    private final ScrollView scroller;
    private Runnable backHandler;

    SettingsPanel(Activity activity, Config config, SharedPreferences choices, Runnable changed) {
        this.activity = activity;
        this.config = config;
        this.choices = choices;
        this.changed = changed;
        density = activity.getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(PANEL_BG);
        panel.setClickable(true);
        root.addView(panel, new FrameLayout.LayoutParams(dp(360), -1, Gravity.END));

        title = text(activity.getString(R.string.launcher_settings), 26, Color.WHITE, true);
        title.setPadding(dp(40), dp(36), dp(40), dp(22));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(activity);
        divider.setBackgroundColor(0xFF2A2E33);
        panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        scroller = new ScrollView(activity);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(24));
        scroller.addView(list, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        caption = text("", 14, CAPTION, true);
        caption.setPadding(dp(40), dp(14), dp(40), dp(10));

        activity.setContentView(root);
        showMain();
    }

    /** True when the panel was on a sub-page and went back to the main one; false lets the activity finish. */
    boolean onBack() {
        if (backHandler == null) return false;
        Runnable back = backHandler;
        backHandler = null;
        back.run();
        return true;
    }

    // ---- pages -------------------------------------------------------------------------------------

    private void showMain() {
        list.removeAllViews();
        title.setText(R.string.launcher_settings);
        caption.setText(R.string.settings);
        list.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        View first = row(R.drawable.ic_settings, activity.getString(R.string.tv_settings), null,
                v -> open(SystemSettings.intent(activity, android.provider.Settings.ACTION_SETTINGS)));
        row(R.drawable.ic_swap_vert, activity.getString(R.string.reorder), null, v -> {
            activity.setResult(Activity.RESULT_OK, new Intent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true));
            activity.finish();
        });

        TextView layoutCaption = text(activity.getString(R.string.launcher_settings), 14, CAPTION, true);
        layoutCaption.setPadding(dp(40), dp(22), dp(40), dp(10));
        list.addView(layoutCaption, new LinearLayout.LayoutParams(-1, -2));

        int columns = choices.getInt("columns", config.columns);
        row(R.drawable.ic_apps, activity.getString(R.string.columns_label), String.valueOf(columns), v -> {
            String[] labels = {"4", "5", "6", "7"};
            choose(activity.getString(R.string.columns_label), labels, columns - 4, which ->
                    save(e -> e.putInt("columns", which + 4)));
        });
        // Trakt recommendations as it stands now (the saved toggle over the theme's hero_feed): the hero height's
        // auto rule and its row read it, so it comes before them.
        int chosenFeed = choices.getInt(Config.HERO_FEED_KEY, -1);
        boolean feedNow = Config.chosenHeroFeed(chosenFeed, config.themeHeroFeed);
        // uniform_rows: no dock, so nothing sits below it and no band has an opacity; the two rows are left out.
        if (config.dockRow && !config.uniformRows) {
            int rows = choices.getInt(Config.ROWS_BELOW_DOCK_KEY, config.rowsBelowDock);
            // shelf_every_row: every row scrolls to the dock's line, so there is no dock in the owner's eyes; the row
            // keeps its key and values but names the first row, and says what the number does.
            boolean everyRow = config.shelfEveryRow;
            String rowsLabel = activity.getString(everyRow ? R.string.rows_below_first_row_label : R.string.rows_below_dock_label);
            String rowsValue = everyRow ? rows + " \u00b7 " + activity.getString(R.string.rows_below_first_row_hint) : String.valueOf(rows);
            row(R.drawable.ic_apps_pill, rowsLabel, rowsValue, v -> {
                String[] labels = {"0", "1", "2", "3"};
                choose(rowsLabel, labels, rows, which -> save(e -> e.putInt(Config.ROWS_BELOW_DOCK_KEY, which)));
            });
            int opacity = choices.getInt(Config.DOCK_OPACITY_KEY, config.dockOpacity);
            row(R.drawable.ic_picture, activity.getString(R.string.dock_opacity_label), opacity + "%", v -> {
                String[] labels = new String[DOCK_OPACITIES.length];
                int checked = 0;
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = DOCK_OPACITIES[i] + "%";
                    if (DOCK_OPACITIES[i] == opacity) checked = i;
                }
                choose(activity.getString(R.string.dock_opacity_label), labels, checked, which ->
                        save(e -> e.putInt(Config.DOCK_OPACITY_KEY, DOCK_OPACITIES[which])));
            });
        }
        if (config.dockRow) {
            // Hero banner height: the shelf's share of the room above the first row. The row and its picker show the
            // height in force, Config.chosenHeroHeight: the saved choice, else the theme's hero_height, else with its
            // 0 (auto) 100 while the Trakt carousel is on and 80 without it. A pick saves that level; a theme push
            // hands the choice back to the theme.
            int heroNow = Config.chosenHeroHeight(choices.getInt(Config.HERO_HEIGHT_KEY, -1), config.themeHeroHeight, feedNow);
            row(R.drawable.ic_picture, activity.getString(R.string.hero_height_label), heroHeightLabel(heroNow), v -> {
                int[] levels = heroHeightChoices(heroNow);
                String[] labels = new String[levels.length];
                for (int i = 0; i < levels.length; i++) labels[i] = heroHeightLabel(levels[i]);
                choose(activity.getString(R.string.hero_height_label), labels, indexOf(levels, heroNow), which ->
                        save(e -> e.putInt(Config.HERO_HEIGHT_KEY, levels[which])));
            });
        }
        // Trakt recommendations (hero_feed): the carousel on the top shelf, over the theme's key. Always offered, so a
        // TV without the companion can keep it off whatever theme it runs; the value says when that app is missing.
        // The row and its picker show the state in force (the theme's unless a choice is saved); a pick saves it.
        String feedState = onOff(feedNow);
        if (!heroFeedInstalled()) feedState += " \u00b7 " + activity.getString(R.string.hero_feed_not_installed);
        row(R.drawable.ic_livetv, activity.getString(R.string.hero_feed_label), feedState, v -> {
            String[] labels = {onOff(true), onOff(false)};
            choose(activity.getString(R.string.hero_feed_label), labels, feedNow ? 0 : 1, which ->
                    save(e -> e.putInt(Config.HERO_FEED_KEY, which == 0 ? 1 : 0)));
        });
        String clock = choices.getString(Config.CLOCK_FORMAT_KEY, config.clockFormat);
        String[] clockLabels = {activity.getString(R.string.clock_format_system),
                activity.getString(R.string.clock_format_12), activity.getString(R.string.clock_format_24)};
        int clockIndex = Math.max(0, java.util.Arrays.asList(CLOCK_FORMATS).indexOf(clock));
        row(R.drawable.ic_livetv, activity.getString(R.string.dream_name), clockLabels[clockIndex], v ->
                choose(activity.getString(R.string.dream_name), clockLabels, clockIndex, which ->
                        save(e -> e.putString(Config.CLOCK_FORMAT_KEY, CLOCK_FORMATS[which]))));
        // System bars: the RAM and Storage bars beside the date (a theme's widgets key, a boolean). A picker like the
        // Trakt row (On, Off); the row and its picker show the state in force, the saved choice, else the theme's
        // (Config.chosenWidgets). The bars themselves land in Phase 3 (DESIGN.md §9.1).
        boolean barsNow = Config.chosenWidgets(choices, config.themeWidgets);
        row(R.drawable.ic_memory, activity.getString(R.string.widgets_label), onOff(barsNow), v -> {
            String[] labels = {onOff(true), onOff(false)};
            choose(activity.getString(R.string.widgets_label), labels, barsNow ? 0 : 1, which ->
                    save(e -> e.putInt(Config.WIDGETS_KEY, which == 0 ? 1 : 0)));
        });

        // Animations: one switch for all the motion. Off is still, with no zoom, lift or glow; On has the theme's
        // advanced_effects along with it (Config.advancedEffects gates on animations), so there is no Advanced
        // effects row here and no choice saved for it (Config.ADVANCED_EFFECTS_LEGACY_KEY).
        // A picker like the other rows (On, Off), so the row shows the new state when the pick returns; the old
        // toggle saved its value and left the row reading the old one until the panel was reopened.
        boolean animations = choices.getBoolean("animations", config.animations);
        row(R.drawable.ic_swap_vert, plainLabel(R.string.animations_state), onOff(animations), v -> {
            String[] labels = {onOff(true), onOff(false)};
            choose(plainLabel(R.string.animations_state), labels, animations ? 0 : 1, which ->
                    save(e -> e.putBoolean("animations", which == 0)));
        });
        if (config.openOnStartChoice) {
            row(R.drawable.ic_input, activity.getString(R.string.open_on_start_label), openOnStartLabel(), v -> showOpenOnStart());
        }
        row(R.drawable.ic_home_settings, activity.getString(R.string.home_settings),
                activity.getString(R.string.home_settings_help), v -> open(new Intent(activity, HomeSettingsActivity.class)));
        showBackupRows();
        backHandler = null;
        scroller.scrollTo(0, 0);
        first.requestFocus();
    }

    /**
     * backup_restore: the Backup &amp; restore section. Back up writes Backup.export to files/backup/ (reachable as
     * /sdcard/Android/data/&lt;package&gt;/files/backup over adb, no permission needed); Restore from file lists that
     * folder, a file adb pushed there included. A restore asks first and then closes the panel, so Home rebuilds with
     * the restored settings.
     */
    private void showBackupRows() {
        String pkg = activity.getPackageName();
        TextView backupCaption = text(activity.getString(R.string.backup_section), 14, CAPTION, true);
        backupCaption.setPadding(dp(40), dp(22), dp(40), dp(10));
        list.addView(backupCaption, new LinearLayout.LayoutParams(-1, -2));
        row(R.drawable.ic_lock, activity.getString(R.string.backup_now), activity.getString(R.string.backup_now_hint, pkg), v -> {
            try {
                File out = Backup.saveToFile(activity);
                toast(activity.getString(R.string.backup_saved, "Android/data/" + pkg + "/files/" + Backup.BACKUP_DIR + "/" + out.getName()));
            } catch (Exception e) {
                toast(activity.getString(R.string.backup_failed, String.valueOf(e.getMessage())));
            }
        });
        row(R.drawable.ic_lock_open, activity.getString(R.string.restore_file), activity.getString(R.string.restore_file_hint), v -> {
            List<File> files = Backup.backups(activity);
            if (files.isEmpty()) { toast(activity.getString(R.string.restore_file_none)); return; }
            String[] labels = new String[files.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = files.get(i).getName();
            choose(activity.getString(R.string.restore_file), labels, -1, which -> confirmRestore(labels[which], () -> Backup.read(files.get(which))));
        });
        TextView note = text(activity.getString(R.string.backup_readable_note, pkg), 13, SUBTITLE, false);
        note.setPadding(dp(40), dp(6), dp(40), dp(8));
        list.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    /** What a restore reads: a backup file. */
    private interface Source { org.json.JSONObject read() throws Exception; }

    private void confirmRestore(String from, Source source) {
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(activity.getString(R.string.restore_confirm_message, from))
                .setPositiveButton(R.string.restore_button, (d, w) -> {
                    try {
                        Backup.restore(activity, source.read());
                    } catch (Exception e) {
                        toast(activity.getString(R.string.restore_failed, String.valueOf(e.getMessage())));
                        showMain();
                        return;
                    }
                    toast(activity.getString(R.string.restore_done));
                    // Home rebuilds on resume: the settings revision moved (Backup.restore).
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> showMain())
                .show();
    }

    private void toast(String message) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
    }

    /** A second page: the title names the setting, one row per value, the current one marked; Back returns. */
    private void choose(String heading, String[] labels, int checked, Consumer<Integer> onPick) {
        list.removeAllViews();
        title.setText(heading);
        View focus = null;
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            View r = row(radio(i == checked), labels[i], null, v -> {
                if (which != checked) onPick.accept(which);
                showMain();
            });
            if (i == checked) focus = r;
        }
        backHandler = this::showMain;
        scroller.scrollTo(0, 0);
        (focus != null ? focus : list.getChildAt(0)).requestFocus();
    }

    private void showOpenOnStart() {
        PackageManager pm = activity.getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = activity.getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        choose(activity.getString(R.string.open_on_start_label), labels, checked, which -> {
            if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
            else {
                OpenOnStart.skipThisBoot(activity);
                choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
            }
        });
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private void save(Consumer<SharedPreferences.Editor> edit) {
        SharedPreferences.Editor e = choices.edit();
        edit.accept(e);
        e.apply();
        changed.run();
    }

    /** "Animations: %1$s" as the bare "Animations": the state strings double as row labels (31 languages). */
    private String plainLabel(int stateString) {
        String s = activity.getString(stateString, "");
        int colon = s.lastIndexOf(':');
        return (colon > 0 ? s.substring(0, colon) : s).trim();
    }

    private String onOff(boolean on) { return activity.getString(on ? R.string.on : R.string.off); }

    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return activity.getString(R.string.off);
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * The hero height row's value and its picker's rows: the named level with its percent ("Short (80%)") for one of
     * the four levels, the bare percent ("70%") for a height a theme named outside them.
     */
    private String heroHeightLabel(int pct) {
        int i = indexOf(HERO_HEIGHTS, pct);
        return i >= 0 ? activity.getString(HERO_HEIGHT_NAMES[i]) + " (" + pct + "%)" : pct + "%";
    }

    /**
     * The heights the picker offers, HERO_HEIGHTS in order, with {@code effective} (the height in force, as
     * Config.chosenHeroHeight gives it) slotted in by size when a theme named one outside the four, so the marked row
     * is always one of those shown.
     */
    @NonNull
    public static int[] heroHeightChoices(int effective) {
        if (indexOf(HERO_HEIGHTS, effective) >= 0) return HERO_HEIGHTS.clone();
        int[] out = new int[HERO_HEIGHTS.length + 1];
        int at = 0;
        while (at < HERO_HEIGHTS.length && HERO_HEIGHTS[at] < effective) at++;
        System.arraycopy(HERO_HEIGHTS, 0, out, 0, at);
        out[at] = effective;
        System.arraycopy(HERO_HEIGHTS, at, out, at + 1, HERO_HEIGHTS.length - at);
        return out;
    }

    public static int indexOf(@NonNull int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }

    /** True while the Trakt shelf companion's provider (HeroFeed.ITEMS) is on the TV. */
    private boolean heroFeedInstalled() {
        try {
            return activity.getPackageManager().resolveContentProvider(HeroFeed.ITEMS.getAuthority(), 0) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void open(Intent intent) {
        try { activity.startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(activity, R.string.not_available, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** One settings row: a 24dp icon (or its empty slot), a title, and an optional value or hint line under it. */
    private View row(int icon, String label, String subtitle, View.OnClickListener onClick) {
        Drawable d = icon == 0 ? null : activity.getDrawable(icon);
        if (d != null) { d = d.mutate(); d.setTint(Color.WHITE); }
        return row(d, label, subtitle, onClick);
    }

    private View row(Drawable icon, String label, String subtitle, View.OnClickListener onClick) {
        LinearLayout r = new LinearLayout(activity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(40), dp(subtitle == null ? 18 : 14), dp(40), dp(subtitle == null ? 18 : 14));
        r.setMinimumHeight(dp(64));
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(rowBackground());
        r.setOnClickListener(onClick);
        ImageView iv = new ImageView(activity);
        iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.setMarginEnd(dp(24));
        r.addView(iv, ip);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text(label, 17, Color.WHITE, false), new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null) {
            TextView sub = text(subtitle, 13, SUBTITLE, false);
            sub.setMaxLines(3);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = dp(3);
            column.addView(sub, sp);
        }
        r.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        list.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    /** A radio mark: a ring, filled with a dot when chosen, in the caption blue. */
    private Drawable radio(boolean on) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setSize(dp(20), dp(20));
        ring.setStroke(dp(2), on ? CAPTION : SUBTITLE);
        ring.setColor(Color.TRANSPARENT);
        if (!on) return ring;
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(CAPTION);
        android.graphics.drawable.LayerDrawable both = new android.graphics.drawable.LayerDrawable(new Drawable[]{ring, dot});
        both.setLayerInset(1, dp(5), dp(5), dp(5), dp(5));
        return both;
    }

    private Drawable rowBackground() {
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(ROW_FOCUS);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return bg;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.SANS_SERIF);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private int dp(int value) { return Math.round(value * density); }
}
