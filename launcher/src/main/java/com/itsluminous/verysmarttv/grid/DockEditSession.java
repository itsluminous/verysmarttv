package com.itsluminous.verysmarttv.grid;

import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeActivity;

/**
 * Edit-dock mode (DESIGN.md §8, moved from the activity verbatim): a reorder session that swaps one chosen app into
 * one dock place, from the Settings menu (choose the place, then the app) or from the tile menu (one of the two
 * already chosen). ReorderSession's begin and end bracket it.
 */
public final class DockEditSession {
    private final HomeActivity host;

    public DockEditSession(@NonNull HomeActivity host) {
        this.host = host;
    }

    /** Edit-dock mode is on: every key goes to dispatch. */
    public boolean active() {
        return dockEditing;
    }

    /** Dock place being changed, or -1 while the place is still being chosen (ReorderSession dims the others). */
    public int slot() {
        return dockEditSlot;
    }

    /** endReorder: edit-dock mode ends with the session. */
    public void reset() {
        dockEditing = false;
        dockEditSlot = -1;
        dockEditAppKey = null;
    }

    /** Edit-dock mode (Settings menu): a reorder session that swaps one chosen app into one dock place. */
    private boolean dockEditing;

    /** Dock place being changed in edit-dock mode, or -1 while the place is still being chosen. */
    private int dockEditSlot = -1;

    /** Tile menu's "Add to dock": the app waiting for a dock place in edit-dock mode, or null. */
    private String dockEditAppKey;

    /** Edit dock (Settings menu): focus goes to the first dock place, which OK then chooses. */
    public void startDockEdit() {
        if (host.reorder().active()) host.reorder().endReorder();
        if (!host.reorder().beginReorder()) return;
        dockEditing = true;
        dockEditSlot = -1;
        host.grid().focusTile(0);
        Toast.makeText(host, R.string.dock_edit_hint, Toast.LENGTH_LONG).show();
    }

    /**
     * Keys in edit-dock mode. First OK on a dock tile chooses that place (the other dock tiles dim) and focus moves
     * to the grid; then OK on any tile swaps that app into the place, saves the order and ends the mode. Back goes
     * one step back: from choosing the app to choosing the place, and from there out of the mode.
     */
    public boolean dispatch(@NonNull KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) {
                if (dockEditAppKey != null) {
                    host.reorder().endReorder();   // "Add to dock" cancelled before a place was chosen
                } else if (dockEditSlot >= 0) {
                    int slot = dockEditSlot;
                    dockEditSlot = -1;
                    host.reorder().dimOthers();
                    host.grid().focusTile(slot);
                } else {
                    host.reorder().endReorder();
                }
            }
            return true;
        }
        int pos = host.grid().focusedTilePosition();
        if (ok && pos >= 0) {
            if (!down) {
                int n = host.grid().adapter().movableCount();
                if (dockEditAppKey != null) {
                    if (pos < Math.min(host.state().columns, n)) {
                        int from = host.grid().adapter().positionOf(dockEditAppKey);
                        if (from >= 0) swapIntoDock(pos, from);
                        else host.reorder().endReorder();
                    }
                } else if (dockEditSlot < 0) {
                    if (pos < Math.min(host.state().columns, n)) {
                        dockEditSlot = pos;
                        host.reorder().dimOthers();
                        host.grid().focusTile(Math.min(host.state().columns, n - 1));
                        Toast.makeText(host, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show();
                    }
                } else if (pos < n) {
                    swapIntoDock(dockEditSlot, pos);
                }
            }
            return true;   // never opens the app while editing
        }
        return host.dispatchKeyEventToViews(event);   // arrows navigate as usual
    }

    /**
     * Edit-dock mode started from the tile menu. A dock app: its place is already chosen, so focus goes to the grid
     * for the app to put there. A grid app: it is already chosen, so focus goes to the dock for the place.
     */
    public void startDockEditFor(@NonNull String key, boolean inDock) {
        if (host.reorder().active()) host.reorder().endReorder();
        if (!host.reorder().beginReorder()) return;
        dockEditing = true;
        int pos = host.grid().adapter().positionOf(key);   // beginReorder can rebuild the tiles
        int n = host.grid().adapter().movableCount();
        if (pos < 0) {
            host.reorder().endReorder();
        } else if (inDock) {
            dockEditSlot = pos;
            host.reorder().dimOthers();
            host.grid().focusTile(Math.min(host.state().columns, n - 1));
            Toast.makeText(host, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show();
        } else {
            dockEditAppKey = key;
            host.grid().focusTile(0);
            Toast.makeText(host, R.string.dock_edit_pick_place, Toast.LENGTH_LONG).show();
        }
    }

    /** Puts the app at pos into dock place slot, and the app that was there where the chosen one was; then saves. */
    private void swapIntoDock(int slot, int pos) {
        String slotKey = host.grid().adapter().keyAt(slot);
        if (pos != slot) {
            host.grid().keepScroll(() -> {
                host.grid().adapter().move(pos, slot);
                host.grid().adapter().move(host.grid().adapter().positionOf(slotKey), pos);
            });
            if (pos >= host.state().columns) host.reorder().dockInSession().remove(slotKey);
        }
        // Outside the check: with no empty slots shown, the chosen app may already sit in the free place.
        host.reorder().dockInSession().add(host.grid().adapter().keyAt(slot));
        host.reorder().endReorder();
        host.grid().refocusAfterLayout(slot);
    }
}
