package com.itsluminous.verysmarttv.keys;

import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.settings.QuickSettingsActivity;
import com.itsluminous.verysmarttv.settings.SystemSettings;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * Every key Home receives (DESIGN.md §8, moved from the activity verbatim): vendor app keys and their holds, the
 * Settings button sent as a keycode, the carousel's keys, the hold-OK timer that opens the tile menu where View's
 * long click misses, the Menu key, and then reorder mode or the focused view.
 */
public final class KeyDispatcher {
    private final HomeActivity host;
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private final int[] settingsKeycodes;

    private final boolean settingsKeyPanel;

    private final java.util.Map<Integer, String> homeAppKeys;

    private final java.util.Map<Integer, String> homeHoldAppKeys;

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
                host.open(settingsKeyPanel ? new Intent(host, QuickSettingsActivity.class)
                        : SystemSettings.intent(host, Settings.ACTION_SETTINGS));
            }
            return true;
        }
        return false;
    }

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
        if (!host.state().tileMenuEnabled || !isOkKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                holdOkFired = false;
                host.handler().removeCallbacks(holdOkTimer);
                holdOkPosition = host.reorder().active() || host.popups().isOpen() || host.cards().promptShowing() ? -1 : host.grid().focusedTilePosition();
                holdOkKey = holdOkPosition < 0 || holdOkPosition >= host.grid().adapter().appCount() ? null : host.grid().adapter().keyAt(holdOkPosition);
                if (holdOkKey == null) {
                    holdOkDown = -1;
                    return false;
                }
                holdOkDown = event.getDownTime();
                host.handler().postDelayed(holdOkTimer, HOLD_OK_MS);
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
        host.handler().removeCallbacks(holdOkTimer);
        holdOkDown = -1;
        holdOkKey = null;
        if (holdOkFired) {
            holdOkFired = false;
            View f = host.getCurrentFocus();
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
    public void cancelHoldOk() {
        host.handler().removeCallbacks(holdOkTimer);
        if (holdOkDown >= 0) holdOkFired = true;
        holdOkDown = -1;
        holdOkPosition = -1;
        holdOkKey = null;
    }

    private void fireHoldOk() {
        host.handler().removeCallbacks(holdOkTimer);
        if (holdOkDown < 0 || holdOkFired) return;
        holdOkFired = true;
        if (host.popups().isOpen() || host.reorder().active()) return;   // View's own long click got there first
        int position = holdOkPosition;
        if (position < 0 || position >= host.grid().adapter().appCount() || position != host.grid().focusedTilePosition()) return;
        // Same slot is not enough: tiles may have been rebuilt during the hold, so the app must match too.
        if (!holdOkKey.equals(host.grid().adapter().keyAt(position))) return;
        View card = host.getCurrentFocus();
        if (card != null) card.setPressed(false);
        host.popups().showTileMenu(position, card);
    }

    public KeyDispatcher(@NonNull HomeActivity host, @NonNull Config cfg) {
        this.host = host;
        homeAppKeys = RemoteKeys.parseAppKeys(cfg.appKeys);
        homeHoldAppKeys = RemoteKeys.parseHoldAppKeys(cfg.appKeys);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
    }

    /**
     * Reorder mode (top-bar button). While it is on, every key comes here first:
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    public boolean dispatch(@NonNull KeyEvent event) {
        // Vendor keys (TCL Netflix button, 4062) never reach the key services, only the focused window, so their
        // "hold:" entries run here too or nowhere. Only while resumed: onPause releases Home's holds, and a DOWN
        // taken after that would arm one nothing releases.
        if (host.state().resumed && event.getKeyCode() >= 1000 && RemoteKeys.handle(host, event, homeAppKeys.get(event.getKeyCode()),
                homeHoldAppKeys.get(event.getKeyCode()))) return true;
        if (handleSettingsKey(event)) return true;
        if (host.carousel().on() && host.carousel().handleKey(event)) return true;
        if (holdOk(event)) return true;
        if (host.state().tileMenuEnabled && !host.reorder().active() && !host.popups().isOpen() && !host.cards().promptShowing()
                && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            int position = host.grid().focusedTilePosition();
            if (position >= 0 && position < host.grid().adapter().appCount()
                    && !host.getPackageName().equals(host.grid().adapter().packageAt(position))) {
                if (RemoteKeys.menuReleased(event.getAction(), event.getFlags())) {
                    host.popups().showTileMenu(position, host.getCurrentFocus());
                }
                return true;
            }
        }
        if (!host.reorder().active()) return host.dispatchKeyEventToViews(event);
        return host.reorder().dispatch(event);
    }
}
