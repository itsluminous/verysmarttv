package com.itsluminous.verysmarttv.grid;

import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeActivity;
import com.itsluminous.verysmarttv.theme.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reorder mode (DESIGN.md §8, moved from the activity verbatim): the top-bar button or the Settings menu turns it on,
 * OK picks a tile up, the arrows carry it, Back puts it down and then ends the mode, which saves the order when it
 * changed. Edit-dock mode (DockEditSession) is a reorder session that swaps one app into one dock place.
 */
public final class ReorderSession {
    private final HomeActivity host;
    /** The grid's RecyclerView: the tiles dimmed while one is carried, and where OK's focus is read. */
    private final RecyclerView gridView;
    /** Reorder mode is on (top-bar button). */
    private boolean reordering;

    /** Reorder mode is on: the card prompt and the tile menu stay away, and every key comes to dispatch first. */
    public boolean active() {
        return reordering;
    }

    /** Key of the tile being carried in reorder mode, or null. */
    private String movingKey;

    /** Tile order when reorder mode started, to save only when something moved. */
    private List<String> orderAtStart;

    /**
     * fill_dock false: tiles in the dock when reorder mode started, plus those carried or swapped into it since
     * (#499). A grid app that only slid into a free slot, which reorder mode does not show, is not one of them.
     */
    private Set<String> dockInSession;

    /** fill_dock false: how many dock slots held an app when reorder mode started. */
    private int dockedAtStart;

    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private List<String> dockApps;

    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private boolean fillDockLeavesSlots;

    /** Blue the glass turns while reorder mode is on. */
    private static final int REORDER_ACCENT = 0xFF6EC1FF;

    /** The reorder button: turns the mode on (focus goes back to the last tile), or off again. */
    public void toggleReorder() {
        if (reordering) {
            endReorder();
            return;
        }
        if (!beginReorder()) return;
        host.grid().focusTile(Math.max(0, Math.min(host.grid().lastFocused(), host.grid().adapter().movableCount() - 1)));
        Toast.makeText(host, R.string.reorder_hint, Toast.LENGTH_LONG).show();
    }

    /** The start shared by reorder and edit-dock mode. False when there is nothing to move. */
    public boolean beginReorder() {
        if (host.grid().adapter().movableCount() < 2) return false;
        // Before the reload below: once it has dropped the empty slots, the dock row also shows grid apps.
        dockInSession = new HashSet<>();
        dockedAtStart = host.grid().adapter().dockFilled();
        for (int i = 0; i < Math.min(dockedAtStart, host.grid().adapter().movableCount()); i++) dockInSession.add(host.grid().adapter().keyAt(i));
        host.grid().adapter().setReordering(true);
        if (host.grid().adapter().hasEmptySlots()) host.grid().reloadTiles();   // every position must hold a tile that can move
        reordering = true;
        orderAtStart = host.grid().adapter().movableKeys();
        host.grid().setGlassAccent(REORDER_ACCENT);
        host.topBar().setReorderActive(true);
        return true;
    }

    public void pickUp(int pos) {
        if (pos >= host.grid().adapter().movableCount()) return;   // the "Default home" tile stays put
        movingKey = host.grid().adapter().keyAt(pos);
        dimOthers();
    }

    /** Puts the carried tile down where it is; the mode stays on. */
    public void drop() {
        movingKey = null;
        dimOthers();
    }

    /** While a tile is carried, every other tile is dimmed so the carried one stands out; otherwise all are full. */
    public void dimOthers() {
        for (int i = 0; i < host.grid().view().getChildCount(); i++) {
            View item = host.grid().view().getChildAt(i);
            item.setAlpha(tileAlpha(host.grid().view().getChildAdapterPosition(item)));
        }
    }

    /** A tile's alpha: dim unless it is the carried one, or, in edit-dock mode, a dock tile other than the chosen place. */
    public float tileAlpha(int pos) {
        if (movingKey != null) {
            return pos != RecyclerView.NO_POSITION && movingKey.equals(host.grid().adapter().keyAt(pos)) ? 1f : 0.45f;
        }
        int dockEditSlot = host.dockEdit().slot();
        if (dockEditSlot >= 0 && pos >= 0 && pos < host.state().columns && pos != dockEditSlot) return 0.45f;
        return 1f;
    }

    /** Ends the mode and saves the order if it changed since the mode started. */
    public void endReorder() {
        if (!reordering) return;
        host.dockEdit().reset();
        drop();
        reordering = false;
        host.grid().adapter().setReordering(false);
        host.grid().setGlassAccent(0);
        host.topBar().setReorderActive(false);
        List<String> keys = host.grid().adapter().movableKeys();
        List<String> before = orderAtStart;
        orderAtStart = null;
        // fill_dock false: the dock is what the owner put there, saved with its size, so the empty slots that
        // come back after the mode go after it (#499). With fill_dock true every dock slot holds an app anyway.
        int docked = -1;
        if (fillDockLeavesSlots) {
            docked = DockSlots.dockCount(keys, host.state().columns, dockInSession);
            keys = DockSlots.dockFirst(keys, host.state().columns, dockInSession);
        }
        dockInSession = null;
        if (!keys.equals(before) || (fillDockLeavesSlots && docked != dockedAtStart)) {
            Order order = new Order(Order.dockKey(dockApps), keys, docked);
            if (Order.save(Order.file(host), order)) {
                host.grid().adapter().setSavedOrder(Order.ranks(order, dockApps), Order.docked(order, dockApps));
            } else {
                Toast.makeText(host, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            }
        }
        if (fillDockLeavesSlots) host.grid().reloadTiles();   // the empty dock slots left out while reordering come back
    }

    /**
     * Moves the carried tile one step. Left and Right wrap across rows, like a list; Up and Down move a whole row,
     * and Down into a short last row lands on its last tile. Nothing moves past either end or up into the top bar.
     */
    private void moveBy(int keyCode) {
        int from = host.grid().adapter().positionOf(movingKey);
        int n = host.grid().adapter().movableCount();
        if (from < 0 || from >= n) {
            drop();
            return;
        }
        // A single row has no rows to move between: Up and Down leave the carried tile where it is.
        if (host.state().singleRow && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) return;
        int to;
        // A right-to-left layout mirrors the grid: position 0 is on the right, so Left moves towards the end.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) to = host.state().rtl ? from + 1 : from - 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) to = host.state().rtl ? from - 1 : from + 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) to = from - host.state().columns;
        else if (from + host.state().columns < n) to = from + host.state().columns;
        else to = from / host.state().columns < (n - 1) / host.state().columns ? n - 1 : from;
        if (to < 0 || to >= n || to == from) return;
        host.grid().keepScroll(() -> host.grid().adapter().move(from, to));
        // The owner carries this tile: where it lands decides whether it is in the dock, not dock_apps (#499).
        if (to < host.state().columns) dockInSession.add(movingKey);
        else dockInSession.remove(movingKey);
        host.grid().refocusAfterLayout(to);
    }

    public ReorderSession(@NonNull HomeActivity host, @NonNull RecyclerView gridView, @NonNull Config cfg) {
        this.host = host;
        this.gridView = gridView;
        dockApps = cfg.dockApps;
        fillDockLeavesSlots = !cfg.fillDock;
    }

    /** fill_dock false: the apps in the dock this session (DockEditSession adds and removes as it swaps). */
    @NonNull
    public Set<String> dockInSession() {
        return dockInSession;
    }

    /**
     * Every key while the mode is on (dispatchKeyEvent, after the hold-OK and menu-key checks):
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    public boolean dispatch(@NonNull KeyEvent event) {
        if (host.dockEdit().active()) return host.dockEdit().dispatch(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (movingKey != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveBy(code);
                    return true;
                default:
                    if (ok || back) {
                        if (!down) drop();
                        return true;
                    }
                    return host.dispatchKeyEventToViews(event);
            }
        }
        if (back) {
            if (!down) endReorder();
            return true;
        }
        int pos = host.grid().focusedTilePosition();
        View focus = host.getCurrentFocus();
        if (ok && focus != null && focus.getId() == R.id.btnLibrary) {
            // The App library pill sits in the grid but is no tile: OK ends the mode (saving the order) and opens it.
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                endReorder();
                host.grid().onLibraryRequested();
            }
            return true;
        }
        if (ok && host.grid().view().hasFocus()) {
            // RecyclerView can briefly report NO_POSITION during a move or rebind. Still consume
            // OK on the grid: falling through would let the focused card open its app in reorder mode.
            if (!down && pos >= 0) pickUp(pos);
            return true;
        }
        return host.dispatchKeyEventToViews(event);   // arrows navigate; OK on a top-bar pill works as usual
    }
}
