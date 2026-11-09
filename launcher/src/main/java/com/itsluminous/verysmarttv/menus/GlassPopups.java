package com.itsluminous.verysmarttv.menus;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.keys.HomeKeyService;
import com.itsluminous.verysmarttv.library.LibraryApps;
import com.itsluminous.verysmarttv.settings.SystemSettings;
import com.itsluminous.verysmarttv.theme.Motion;

/**
 * The glass dropdowns on Home (DESIGN.md §8, moved from the activity verbatim): the popup window every menu hangs
 * in, its drop-in, the gear's top_menu, the Settings pill's hold-OK menu, the tile menu, and the plain rows the
 * last two are built from. One popup is open at a time; onPause closes it.
 */
public final class GlassPopups {
    private final HomeActivity host;
    private final FrameLayout root;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    public GlassPopups(@NonNull HomeActivity host, @NonNull FrameLayout root) {
        this.host = host;
        this.root = root;
    }

    /** A dropdown is open: keys belong to it, and the tile menu and the card prompt stay away. */
    public boolean isOpen() {
        return openPopup != null;
    }

    /** The App library's popup counts as the open one while it shows (onLibraryRequested); null when it closed. */
    public void track(@Nullable PopupWindow popup) {
        openPopup = popup;
    }

    /** onPause: nothing of ours stays on screen over another app. */
    public void dismiss() {
        if (openPopup != null) openPopup.dismiss();
    }

    @NonNull
    public PopupWindow glassPopup(@NonNull View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    public void dropIn(@NonNull PopupWindow popup, @NonNull View panel, @NonNull View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * host.density());
        popup.showAsDropDown(anchor, 0, (int) (10 * host.density()), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(host.state().reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** top_menu: the gear's dropdown, standing in for the Inputs, Wi-Fi and Settings pills. */
    public void showTopMenu() {
        View anchor = host.findViewById(R.id.btnSettings);
        View panel = host.getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (220 * host.density()));
        // Inputs opens its own list once this one has closed.
        if (host.topBar().showInputsMenuRow()) host.inputs().addInputRow(rows, popup, R.drawable.ic_input, host.getString(R.string.inputs), -1, () -> root.post(host.inputs()::showInputs));
        host.inputs().addInputRow(rows, popup, R.drawable.ic_wifi, host.getString(R.string.quick_network), -1, host.topBar()::openNetworkPill);
        CharSequence network = host.topBar().networkPillLabel();
        if (!TextUtils.isEmpty(network)) {
            TextView status = rows.getChildAt(rows.getChildCount() - 1).findViewById(R.id.status);
            status.setText(network);
            status.setVisibility(View.VISIBLE);
        }
        host.inputs().addInputRow(rows, popup, R.drawable.ic_swap_vert, host.getString(R.string.reorder), -1, host.reorder()::toggleReorder);
        if (host.topBar().launcherSettingsButton()) {
            host.inputs().addInputRow(rows, popup, R.drawable.ic_home_settings_pill, host.getString(R.string.launcher_settings), -1,
                    () -> host.startup().openLauncherSettings());
        } else {
            host.inputs().addInputRow(rows, popup, R.drawable.ic_settings, host.getString(R.string.settings), -1,
                    () -> host.open(SystemSettings.intent(host, Settings.ACTION_SETTINGS)));
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (220 * host.density()), Math.min(panel.getMeasuredWidth(), (int) (360 * host.density()))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    /** The header opens launcher settings directly; hold OK keeps the extra home actions available. */
    public void showSettingsMenu() {
        View anchor = host.findViewById(R.id.btnSettings);
        View panel = host.getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (200 * host.density()));
        if (!host.topBar().launcherSettingsButton()) addSettingsMenuRow(rows, popup, R.string.launcher_settings, () -> host.startup().openLauncherSettings());
        // Firmware that ships its own settings app next to the AOSP one (Xiaomi China is one): the
        // vendor app holds pages the AOSP settings has no row for, such as Account & Security with
        // the ADB switch. Offered here only while one of them is installed.
        if (SystemSettings.vendorIntent(host) != null) {
            addSettingsMenuRow(rows, popup, R.string.native_settings, () -> {
                Intent vendor = SystemSettings.vendorIntent(host);
                if (vendor != null) host.open(vendor);
            });
        }
        if (host.state().moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, host.cards()::showModuleCards);
        if (host.state().dockRow && !host.state().shelfEveryRow) addSettingsMenuRow(rows, popup, R.string.dock_edit, host.dockEdit()::startDockEdit);
        addSettingsMenuRow(rows, popup, R.string.reorder, host.reorder()::toggleReorder);
        // A launcher may not restart the TV itself; the Home key helper can open the system power menu instead.
        if (HomeKeyService.canOpenPowerMenu()) addSettingsMenuRow(rows, popup, R.string.power_menu, HomeKeyService::openPowerMenu);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * host.density()), panel.getMeasuredWidth()));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    /** Hold OK on a tile: the tile menu next to it (AppAdapter.TileListener.onTileLongPress). */
    public void showTileMenu(int position, @NonNull View card) {
        if (host.reorder().active() || openPopup != null) return;
        String key = host.grid().adapter().keyAt(position);
        String pkg = host.grid().adapter().packageAt(position);
        // Empty dock slots and the Default-home tile carry the launcher's own package: no menu for them.
        if (key == null || pkg == null || pkg.equals(host.getPackageName())) return;
        View panel = host.getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (200 * host.density()));
        if (host.state().dockRow && !host.state().shelfEveryRow && position < host.grid().adapter().movableCount()) {
            boolean inDock = position < host.state().columns;
            addSettingsMenuRow(rows, popup, inDock ? R.string.tile_replace_in_dock : R.string.tile_add_to_dock,
                    () -> host.dockEdit().startDockEditFor(key, inDock));
        }
        if (position < host.grid().adapter().movableCount()) addSettingsMenuRow(rows, popup, R.string.tile_move, () -> {
            if (!host.reorder().beginReorder()) return;
            int at = host.grid().adapter().positionOf(key);   // beginReorder can rebuild the tiles
            if (at < 0) return;
            host.grid().focusTile(at);
            host.reorder().pickUp(at);
            Toast.makeText(host, R.string.reorder_hint, Toast.LENGTH_LONG).show();
        });
        if (host.state().appLibraryEnabled) addSettingsMenuRow(rows, popup, R.string.library_remove_shortcut, () -> {
            if (LibraryApps.save(host, pkg, false)) host.grid().reloadApps(false);
            else Toast.makeText(host, R.string.library_save_failed, Toast.LENGTH_LONG).show();
        });
        // Some firmware has no App info page at all; a row that only toasts "can't open" helps nobody.
        Intent info = SystemSettings.appInfo(host, pkg);
        if (info != null) addSettingsMenuRow(rows, popup, R.string.library_app_info, () -> host.open(info));
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * host.density()), panel.getMeasuredWidth()));
        // Dock tiles sit near the bottom of the screen: open the menu above the tile when it would not fit below.
        int[] at = new int[2];
        card.getLocationInWindow(at);
        int gap = (int) (10 * host.density()), height = panel.getMeasuredHeight();
        if (at[1] + card.getHeight() + gap + height <= root.getHeight()) {
            dropIn(popup, panel, card, Gravity.START);
        } else {
            panel.setAlpha(0f);
            panel.setPivotY(height);
            panel.setScaleY(0.92f);
            panel.setTranslationY(8 * host.density());
            popup.showAtLocation(root, Gravity.NO_GRAVITY, at[0], Math.max(0, at[1] - gap - height));
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
        rows.getChildAt(0).requestFocus();
    }

    public void addSettingsMenuRow(@NonNull LinearLayout rows, @NonNull PopupWindow popup, int title, @NonNull Runnable action) {
        View row = host.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        // The OK-up after opening the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                popup.dismiss();
                action.run();
            }
            return true;
        });
        rows.addView(row);
    }
}
