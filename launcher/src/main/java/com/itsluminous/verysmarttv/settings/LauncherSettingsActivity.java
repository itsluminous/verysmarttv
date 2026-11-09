package com.itsluminous.verysmarttv.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.home.LauncherLocale;
import com.itsluminous.verysmarttv.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    public static final String EXTRA_REORDER = "com.itsluminous.verysmarttv.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView animationsChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;

    private SettingsPanel panel;

    @Override
    public void onBackPressed() {
        if (panel != null && panel.onBack()) return;
        super.onBackPressed();
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(SettingsChoices.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        if (config.settingsPanel) {
            // settings_panel: the side panel draws itself; the rows below belong to the full-screen page.
            panel = new SettingsPanel(this, config, choices, this::changed);
            return;
        }
        setContentView(R.layout.activity_launcher_settings);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(SettingsChoices.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(SettingsChoices.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // uniform_rows: no dock, so Rows below dock and Dock band opacity have nothing to set; both groups are left out.
        if (config.uniformRows) {
            hideChoiceGroup(rowChoices[0]);
            hideChoiceGroup(dockChoices[0]);
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(SettingsChoices.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        animationsChoice = findViewById(R.id.animationsChoice);
        // Animations: one switch for all the motion; the theme's advanced_effects ride on it (Config gates them on
        // animations), so the page has no Advanced effects row and saves no choice for it.
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            choices.edit().putBoolean("animations", animations).apply();
            changed();
            refresh();
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(SettingsChoices.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove(SettingsChoices.DOCK_OPACITY_KEY).remove(SettingsChoices.CLOCK_FORMAT_KEY).remove(SettingsChoices.HERO_HEIGHT_KEY).remove(SettingsChoices.HERO_FEED_KEY)
                    .remove(SettingsChoices.WIDGETS_KEY).remove(SettingsChoices.SCORE_PILL_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        View tvSettingsChoice = findViewById(R.id.tvSettingsChoice);
        if (config.tvSettingsFirst) {
            // tv_settings_first: the TV's own settings move from the foot of the page to just under the title and take
            // the first focus, so a click on Settings still reaches them in one more press.
            LinearLayout page = (LinearLayout) tvSettingsChoice.getParent();
            page.removeView(tvSettingsChoice);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tvSettingsChoice.getLayoutParams();
            lp.topMargin = Math.round(28 * getResources().getDisplayMetrics().density);
            page.addView(tvSettingsChoice, 1, lp);
            if (reorderChoice.getVisibility() == View.VISIBLE) {
                ((LinearLayout.LayoutParams) reorderChoice.getLayoutParams()).topMargin =
                        Math.round(8 * getResources().getDisplayMetrics().density);
            }
            tvSettingsChoice.requestFocus();
        } else if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    /** Hides a row of numbered choices and the label above it (the child before their row in the shared column). */
    private static void hideChoiceGroup(View choice) {
        View group = (View) choice.getParent();
        LinearLayout column = (LinearLayout) group.getParent();
        int at = column.indexOfChild(group);
        if (at > 0) column.getChildAt(at - 1).setVisibility(View.GONE);
        group.setVisibility(View.GONE);
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }


    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(SettingsChoices.SETTINGS_REVISION_KEY,
                choices.getLong(SettingsChoices.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(SettingsChoices.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations")
                || choices.contains(SettingsChoices.DOCK_OPACITY_KEY)
                || choices.contains(SettingsChoices.CLOCK_FORMAT_KEY) || choices.contains(SettingsChoices.HERO_HEIGHT_KEY)
                || choices.contains(SettingsChoices.HERO_FEED_KEY) || choices.contains(SettingsChoices.WIDGETS_KEY)
                || choices.contains(SettingsChoices.SCORE_PILL_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
