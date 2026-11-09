package com.itsluminous.verysmarttv.grid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ChangedPackages;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Build;
import android.view.SoundEffectConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.library.AppLibraryDialog;

import com.itsluminous.verysmarttv.shelf.CarouselController;
import com.itsluminous.verysmarttv.shelf.ShelfController;
import com.itsluminous.verysmarttv.shelf.ShelfScale;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.theme.Motion;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The app grid (DESIGN.md §8, moved from the activity verbatim): the RecyclerView and its layout manager, the dock
 * line and the rows under it, the tiles' rebuild and the focus that comes back after it, the glass highlight that
 * springs between tiles, D-pad moves between rows, the App library footer and dialog, and the package receiver
 * that marks the tiles stale. The shelf and the reorder sessions act on what it reports.
 */
public final class GridController {
    // Not private: the anonymous listeners and receivers below read these, and an accessor per read is what lint's
    // SyntheticAccessor flags (the fork baselined them in the activity; this file has none).
    final HomeHost host;
    final FrameLayout root;
    private SpringAnimation springX, springY;
    float glassTargetX, glassTargetY;
    AppAdapter adapter;

    RecyclerView grid;

    GlassView glass;

    /** config.xml app_glow: the highlight takes the focused app's colour on every tile. */
    private boolean appGlow;

    /** The focus glow's colour while the cinematic shelf tints it, 0 for plain white glass. */
    private int glassAccent;

    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    float glassScrollShift;

    /** The same for springX, when single_row scrolls the grid sideways. */
    float glassScrollShiftX;

    private int glassCardW;   // width of the tile the glass was last fitted to

    /**
     * The focused tile's rect in root coordinates as {@link #onTileFocused} last fitted the glass to it. The size
     * alone cannot say whether the glass is on its tile: a cold start can run onTileFocused once the tile has its
     * final width but before the grid has its top padding, which leaves the right size at the old Y.
     */
    private final Rect glassCardRect = new Rect();

    boolean glassShown;

    private final Rect tileRect = new Rect();

    private final Rect revealRect = new Rect();

    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    static int lastFocused = 0;

    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;

    /** The highlight springs past its target (config.xml, focus_overshoot); false goes straight there. */
    private boolean focusOvershoot;

    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();

    /** library_excluded_apps: packages the App library catalogue never offers. */
    private Set<String> libraryExcludedApps = Collections.emptySet();

    /** Rebuild the tiles on the way back to Home only when apps changed meanwhile (TileRefresh). */
    final TileRefresh tileRefresh = new TileRefresh();

    /** PackageManager's change counter at the last rebuild, to catch a package broadcast that never arrived. */
    private int packageSequence;

    /** The tile adapter: the model the sessions and menus read. */
    @NonNull
    public AppAdapter adapter() {
        return adapter;
    }

    /** The last focused tile's position (static: it survives the activity, see the field). */
    public int lastFocused() {
        return lastFocused;
    }

    /** The library pill: open the library, and give focus back to the pill when it closes . */
    public void openLibraryFromTopBar() {
        onLibraryRequested();
        libraryFromTopBar = true;   // closing it goes back to this button, not to the pill after the last row
    }

    /** vpn_pill with no VPN app: the library to pick one, focus back on the pill when it closes . */
    public void openLibraryFromVpnPill() {
        onLibraryRequested();
        libraryFromVpnPill = true;
    }

    AppLibraryDialog appLibrary;

    private boolean appLibrarySelection;

    private boolean libraryOkOpens;

    private int libraryColumns = 0;

    boolean libraryFocusPending;

    RecyclerView.OnScrollListener pendingFocusScroll;

    private final IntentFilter packageFilter = new IntentFilter();

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            // In the background this only marks the tiles stale; onResume rebuilds them once.
            if (!tileRefresh.onPackagesChanged()) return;
            reloadApps(false);
            if (appLibrary != null) appLibrary.refresh();
        }
    };

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    public void reloadApps(boolean animate) {
        reloadApps(animate, true);
    }

    /** @param rebuild false keeps the tiles as they are and only puts focus back (TileRefresh.Action.REFOCUS). */
    public void reloadApps(boolean animate, boolean rebuild) {
        reloadApps(animate, rebuild, true);
    }

    /**
     * @param entrance false puts focus back as an animated reload would, without replaying the tiles' entrance:
     *                 on a return to the same tiles it would only mark a change that didn't happen.
     */
    public void reloadApps(boolean animate, boolean rebuild, boolean entrance) {
        cancelPendingFocusScroll();
        RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
        final boolean restoreLibrary = adapter.isLibraryAt(adapter.appCount()) && footer != null
                && footer.itemView.findViewById(R.id.btnLibrary) != null
                && footer.itemView.findViewById(R.id.btnLibrary).hasFocus();
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = appLibrary == null && (animate || grid.hasFocus());
        if (restoreFocus) {
            glassShown = false;
            glass.setAlpha(0f);
            host.shelf().resetForReload();
        }
        if (rebuild) reloadTiles();
        if (animate && entrance && host.state().animations && !host.state().reducedMotion) {
            grid.scheduleLayoutAnimation();
            // A rebuild redraws the grid anyway; without one the entrance needs a draw to start.
            if (!rebuild) grid.invalidate();
        }

        if (restoreFocus) {
            // Keep a focused footer at the end even when the app count changes.
            if (restoreLibrary) grid.post(this::focusLibrary);
            else restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.appCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) host.shelf().hideHero();
            // A newly installed app may have brought a card with it.
            host.state().tilesReady = true;
            host.startup().startPendingSettingsReorder();
            host.cards().showCardPrompt();
        }
    }

    /** True when PackageManager reports apps changed since the last rebuild (one binder call). */
    private boolean packagesChangedSinceRebuild() {
        ChangedPackages changed = host.activity().getPackageManager().getChangedPackages(packageSequence);
        return changed != null && !changed.getPackageNames().isEmpty();
    }

    private int currentPackageSequence(int since) {
        ChangedPackages changed = host.activity().getPackageManager().getChangedPackages(since);
        return changed != null ? changed.getSequenceNumber() : since;
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    public void reloadTiles() {
        if (host.reorder().active()) host.reorder().endReorder();   // an app installed or removed while reordering: keep the order so far
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        // An updated app may bring new artwork; the backdrop is cheap to make again.
        host.shelf().dropArtCache();
        // One LEANBACK query for both the tiles and the card checks.
        final List<ResolveInfo> leanback = host.activity().getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        adapter.reload(host.cards().discover(hiddenApps, leanback), leanback);
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        packageSequence = currentPackageSequence(packageSequence);
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    public void restoreFocus() {
        final boolean returnToLibrary = libraryFocusPending;
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            if (returnToLibrary || libraryFocusPending) focusLibrary();
            else focusTile(pos >= 0 ? pos : returnSlot);
            host.state().tilesReady = true;
            host.startup().startPendingSettingsReorder();
            host.cards().showCardPrompt();
        });
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    public int focusedTilePosition() {
        View f = host.activity().getCurrentFocus();
        if (f == null || f.getId() != R.id.card || !(f.getParent() instanceof View)) return -1;
        return grid.getChildAdapterPosition((View) f.getParent());
    }

    /**
     * Applies an adapter change without the grid jumping. After a move the layout manager anchors on the focused
     * tile, keeping it at its old place on screen and shifting every other row; pinning the first visible row at its
     * current offset as the next layout's anchor keeps the scroll where it was. onTileFocused still scrolls
     * afterwards if the moved tile itself ends up off screen.
     */
    public void keepScroll(@NonNull Runnable change) {
        GridLayoutManager lm = (GridLayoutManager) grid.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        View firstView = first != RecyclerView.NO_POSITION ? lm.findViewByPosition(first) : null;
        // single_row scrolls sideways, so its first visible tile is pinned by its left edge instead; mirrored by
        // In a right-to-left layout the row is laid out from the right, and the offset counts from the right edge.
        int offset = firstView == null ? 0
                : host.state().singleRow && host.state().rtl ? grid.getWidth() - grid.getPaddingRight() - lm.getDecoratedRight(firstView)
                : host.state().singleRow ? lm.getDecoratedLeft(firstView) - grid.getPaddingLeft()
                : lm.getDecoratedTop(firstView) - grid.getPaddingTop();
        change.run();
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset);
    }

    /**
     * Once the grid has applied the change, put the glass (and focus, if the move dropped it) on the tile at pos.
     * The grid has a fixed size, so it applies adapter changes on the next animation frame rather than in a layout
     * pass; until then it cannot find the tile by position. Wait frame by frame (at most 10) for that. No focus
     * change fires for a moved tile, so focusTile's already-focused path calls onTileFocused itself.
     */
    public void refocusAfterLayout(int pos) {
        grid.postOnAnimation(new Runnable() {
            int frames;

            @Override
            public void run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested()) && ++frames < 10) {
                    grid.postOnAnimation(this);
                    return;
                }
                focusTile(pos);
                host.reorder().dimOthers();   // views that came into view or were rebound during the move
            }
        });
    }

    /** AppAdapter.TileListener.onLibraryRequested: the App library over Home. */
    public void onLibraryRequested() {
        if (appLibrary != null) appLibrary.close();
        host.popups().dismiss();
        libraryFocusPending = false;
        libraryFromTopBar = false;
        libraryFromVpnPill = false;
        appLibrary = new AppLibraryDialog(host.activity(), host.state().animations, host.state().reducedMotion, appLibrarySelection, libraryOkOpens, libraryColumns, adapter::containsPackage, () -> reloadApps(false), () -> {
            appLibrary = null;
            host.popups().track(null);
            if (libraryFromTopBar) {
                libraryFromTopBar = false;
                host.topBar().focusLibraryPill();
                return;
            }
            boolean fromVpnPill = libraryFromVpnPill;
            libraryFromVpnPill = false;
            // The VPN pill can hide while the library is open (its VPN dropped): then focus goes as from the grid.
            if (fromVpnPill && host.topBar().focusVpnPillIfShown()) return;
            libraryFocusPending = true;
            grid.post(() -> {
                if (libraryFocusPending && appLibrary == null && host.activity().hasWindowFocus() && !host.activity().isFinishing()) focusLibrary();
            });
        }, libraryExcludedApps);
        host.popups().track(appLibrary.popup);
        appLibrary.show();
    }

    /** AppAdapter.TileListener.onTileFocused: the glass onto the tile, the shelf or the glow for it. */
    public void onTileFocused(int position, @NonNull View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glass
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.getWidth() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * host.density();        // matches GlassView's glow inset
        float scale = adapter.focusScale();
        float w = card.getWidth() * scale + 2 * pad;
        float h = card.getHeight() * scale + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        // advanced_effects lifts a focused grid tile by 4dp and the glass follows it; dock tiles stay put (#2135).
        // With uniform_rows no tile is a dock tile, so every one lifts.
        boolean lifted = host.state().advancedEffects && (host.state().uniformRows || !DockSlots.inDockRow(host.state().dockRow, host.state().singleRow, position, host.state().columns));
        float y = r.exactCenterY() - h / 2f - (lifted ? 4f * host.density() : 0f);

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h || card.getWidth() != glassCardW) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            // GlassView draws its slab inset by pad, i.e. exactly over the zoomed tile: same corners as the tile.
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), host.density()) * scale);
        }
        glassCardW = card.getWidth();
        glassCardRect.set(r);
        glassTargetX = x;
        glassTargetY = y;
        // Cinematic shelf: the highlight glows in the dock app's colour, plain white in the grid (and on the dock with
        // cinematic_white_focus). app_glow: every tile. In a single row every tile is the dock's kind.
        boolean coloured = appGlow || host.state().cinematic && !host.shelf().cinematicWhiteFocus() && (host.state().singleRow || position < host.state().columns || host.state().shelfEveryRow);
        int accent = coloured ? adapter.ambientColorAt(position) : 0;
        if ((host.state().cinematic || appGlow) && accent != glassAccent) {
            glassAccent = accent;
            glass.setAccent(accent);
        }
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (host.state().animations && !host.state().reducedMotion) glass.animate().alpha(1f).setDuration(180).start();
            else glass.setAlpha(1f);
            glassShown = true;
        } else if (!host.state().animations) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
        } else {
            springX.animateToFinalPosition(x - glassScrollShiftX);
            springY.animateToFinalPosition(y - glassScrollShift);
        }
        View item = (View) card.getParent();
        if (host.state().dockRow && (host.state().singleRow || position < host.state().columns || host.state().shelfEveryRow)) {
            // Dock (or, with shelf_every_row, any row): park it at the bottom of the screen and show the top shelf for
            // this app. In a single row
            // every tile is in the dock's row, which never scrolls vertically.
            // DockDecoration's 6dp above the row; with uniform_rows there is none, and every row lands on the line itself.
            int decoratedTop = item.getTop() - (host.state().uniformRows ? 0 : (int) (6 * host.density()));
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = !host.state().singleRow && Math.abs(dy) > (int) (8 * host.density());
            if (scroll) {
                if (!host.state().animations || host.state().reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            host.shelf().showHero(position, scroll ? r.top - dy : r.top);
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            host.shelf().showGlowOnly(position);
            // Scroll just enough so the whole glass (not only the tile) is visible.
            revealRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, revealRect, host.state().reducedMotion);
        }
    }

    /** Refits the glass to the focused holder once the grid layout and tile dimensions settle. */
    public void refitGlass() {
        if (host.activity().isDestroyed() || adapter.appCount() == 0 || libraryFocusPending) return;
        int pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() <= 0 || !card.isFocused()) return;
        float pad = 6f * host.density();
        float scale = adapter.focusScale();
        int w = (int) (card.getWidth() * scale + 2 * pad);
        int h = (int) (card.getHeight() * scale + 2 * pad);
        // The tile's rect, not only its width: the grid's top padding lands after the tile has its width, so a
        // cold start can leave the glass at the right size and the wrong place. Comparing the rect catches that.
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        if (!glassShown || card.getWidth() != glassCardW || glass.getWidth() != w || glass.getHeight() != h
                || !glassCardRect.equals(r)) {
            onTileFocused(pos, card);
        }
    }

    /**
     * The names under the tiles while the carousel owns the shelf (there is no shelf name to tell the focused app).
     * Changes the bound holders in place: a rebind would move the focus.
     */
    public void applyTileNames(boolean on) {
        if (!adapter.setNamesUnderTiles(on)) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = grid.getChildViewHolder(grid.getChildAt(i));
            if (vh instanceof AppAdapter.Holder) adapter.refreshName((AppAdapter.Holder) vh, vh.getBindingAdapterPosition());
        }
    }

    public void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().cancel();
        if (host.state().animations && !host.state().reducedMotion) glass.animate().alpha(0f).setDuration(160).start();
        else glass.setAlpha(0f);
    }

    /** AppAdapter.TileListener.onTileVerticalKey: where a D-pad key on a tile sends focus (FocusRules in step 2). */
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.appCount();
        int target;
        if (host.state().singleRow) {
            // One row: Up leaves for the top bar, Down has nowhere to go, and Left/Right walk the whole row,
            // stopping at its two ends (the default focus search scrolls the row to the next tile).
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            boolean forward = host.state().rtl ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            int last = adapter.focusablePosition(count - 1);
            if (forward && position >= last) {
                if (adapter.isLibraryAt(count)) focusLibrary();
                return true;
            }
            return !forward && position == 0;
        }
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            boolean forward = host.state().rtl ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (forward && position == adapter.focusablePosition(count - 1) && adapter.isLibraryAt(count)) {
                focusLibrary();
                return true;
            }
            // In the dock, the last app is the row's end: the empty slots after it take no focus.
            int rowCount = position < host.state().columns ? Math.min(count, adapter.dockFilled()) : count;
            return GridNavigation.atHorizontalEdge(position, rowCount, host.state().columns,
                    host.state().rtl, keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < host.state().columns) return false;          // dock row: let focus move to the top bar
            target = position - host.state().columns;
        } else {
            int lastRowStart = ((count - 1) / host.state().columns) * host.state().columns;
            if (position >= lastRowStart) {
                if (adapter.isLibraryAt(count)) focusLibrary();
                return true;
            }
            target = Math.min(position + host.state().columns, count - 1);
        }
        focusTile(target);
        // The default focus search clicks for Left/Right; Up/Down are handled here, so click for them too.
        grid.playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP
                ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
        return true;
    }

    /** AppAdapter.TileListener.onLibraryFocused. */
    public void onLibraryFocused() {
        libraryFocusPending = false;
        hideGlass();
        host.shelf().hideHero();
    }

    /** AppAdapter.TileListener.onLibraryUp. */
    public void onLibraryUp() {
        if (adapter.appCount() == 0) { host.topBar().firstTopButton().requestFocus(); return; }
        int lastRow = ((adapter.appCount() - 1) / host.state().columns) * host.state().columns;
        focusTile(Math.min(lastRow + lastFocused % host.state().columns, adapter.appCount() - 1));
    }

    public void focusLibrary() {
        cancelPendingFocusScroll();
        int position = adapter.appCount();
        if (!adapter.isLibraryAt(position)) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View button = holder.itemView.findViewById(R.id.btnLibrary);
            Rect visible = new Rect();
            if (button.getGlobalVisibleRect(visible) && visible.height() >= button.getHeight()) {
                if (button.requestFocus()) libraryFocusPending = false;
                return;
            }
        }
        if (!host.state().animations) {
            grid.scrollToPosition(position);
            grid.post(() -> {
                if (appLibrary != null || !host.activity().hasWindowFocus()) return;
                RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
                if (footer != null && adapter.isLibraryAt(adapter.appCount())
                        && footer.itemView.findViewById(R.id.btnLibrary).requestFocus()) libraryFocusPending = false;
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder footer = rv.findViewHolderForAdapterPosition(position);
                if (footer != null && footer.itemView.findViewById(R.id.btnLibrary).requestFocus())
                    libraryFocusPending = false;
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(position);
    }

    public void cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll);
            pendingFocusScroll = null;
            grid.stopScroll();
        }
    }

    public void focusTile(int position) {
        libraryFocusPending = false;
        cancelPendingFocusScroll();
        if (adapter.appCount() == 0) {
            if (adapter.isLibraryAt(0)) focusLibrary();
            else host.topBar().firstTopButton().requestFocus();
            return;
        }
        final int pos = adapter.focusablePosition(Math.max(0, Math.min(position, adapter.appCount() - 1)));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (!host.state().animations) {
            grid.scrollToPosition(pos);
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(pos);
    }

    /** The open library came from that button: closing it returns there instead of scrolling to the grid's end. */
    private boolean libraryFromTopBar;

    /** The open library came from the VPN pill (vpn_pill, no VPN app): closing it returns there while the pill still shows. */
    private boolean libraryFromVpnPill;

    /** The grid's top in the root's px: where its padding, the shelf's resting line, is measured from. */
    public int gridTopInRoot() {
        Rect r = tileRect;
        r.set(0, 0, 0, 0);
        root.offsetDescendantRectToMyCoords(grid, r);
        return r.top;
    }

    public GridController(@NonNull HomeHost host, @NonNull FrameLayout root, @NonNull Config cfg) {
        this.host = host;
        this.root = root;
        appLibrarySelection = cfg.appLibrarySelection;
        libraryOkOpens = cfg.libraryOkOpens;
        libraryColumns = cfg.libraryColumns;
        focusOvershoot = cfg.focusOvershoot;
        appGlow = cfg.appGlow;
        hiddenApps = new HashSet<>(cfg.hiddenApps);
        libraryExcludedApps = new HashSet<>(cfg.libraryExcludedApps);
    }

    /** The RecyclerView, for the reorder sessions in this package (they move its children and read its layout). */
    @NonNull
    public RecyclerView view() {
        return grid;
    }

    /** The highlight's accent while reorder mode is on, 0 for its own. */
    public void setGlassAccent(int color) {
        glass.setAccent(color);
    }

    /** onCreate: the grid, its layout manager and decorations, the adapter, the dock line and the glass springs. */
    public void setUp(@NonNull Config cfg) {
        glass = host.findViewById(R.id.glass);
        glass.setStyle(cfg.glassStyle);
        grid = host.findViewById(R.id.grid);
        // supportsRtl: the framework mirrors the layout; the grid's key and position arithmetic follows it.
        host.state().rtl = host.isRtl();
        // single_row: one span laid out sideways, so the same adapter, glass and dock band serve a scrolling row.
        grid.setLayoutManager(new GridLayoutManager(host.activity(), host.state().singleRow ? 1 : host.state().columns,
                host.state().singleRow ? RecyclerView.HORIZONTAL : RecyclerView.VERTICAL, false) {
            @Override
            protected boolean isLayoutRTL() {
                return host.state().rtl;
            }

            /**
             * Every "bring this tile on screen" scroll lands here, both RecyclerView's own on focus and
             * onTileFocused's. The stock version treats the grid's top padding as the visible top, but that
             * padding only pushes the dock down the screen: grid rows scroll up into it. With the stock rule,
             * Up into a row above the padding dragged that row down to the padding line, cut it off at the
             * bottom of the screen and could scroll the focused tile out entirely, dropping focus into the
             * top bar. Grid tiles now scroll just enough to show the rect between the grid's top edge and its
             * bottom; dock tiles are left to onTileFocused, which parks the dock row on the padding line.
             */
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                if (host.state().singleRow) {
                    // Sideways: scroll just enough to bring the tile's cell inside the padding. Cells are a
                    // column wide, so the row moves a whole tile at a time and the tiles stay on the columns.
                    // The whole cell, not the card rect passed in: the card is narrower and centred in it.
                    int left = child.getLeft();
                    int right = child.getRight();
                    int dx;
                    if (left < getPaddingLeft()) dx = left - getPaddingLeft();
                    else if (right > getWidth() - getPaddingRight()) dx = right - (getWidth() - getPaddingRight());
                    else dx = 0;
                    if (dx == 0) return false;
                    if (immediate || host.state().reducedMotion || !host.state().animations) parent.scrollBy(dx, 0);
                    else parent.smoothScrollBy(dx, 0);
                    return true;
                }
                if (host.state().dockRow && getPosition(child) < host.state().columns) return false;
                int top = child.getTop() + rect.top;
                int bottom = top + rect.height();
                // A row already fully on screen stays put, even inside the bottom padding (the last row
                // rows_below_dock leaves visible ends there; nudging it up was the bug). A row that has to scroll
                // in still stops at the padding line, clear of the screen edge, as before.
                int limitBottom = getHeight() - getPaddingBottom();
                int dy;
                if (top < 0) dy = top;
                else if (bottom <= getHeight()) dy = 0;
                else dy = Math.min(top, bottom - limitBottom);
                if (dy == 0) return false;
                if (immediate || host.state().reducedMotion || !host.state().animations) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        });
        ((GridLayoutManager) grid.getLayoutManager()).setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return !host.state().singleRow && adapter != null && adapter.isLibraryAt(position) ? host.state().columns : 1; }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        // Tiles that scroll out while one is carried come back from the view cache without a rebind; set their
        // dimming again whenever one attaches, so none stays dim after the drop.
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View v) {
                v.setAlpha(host.reorder().tileAlpha(grid.getChildAdapterPosition(v)));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View v) {
            }
        });
        // uniform_rows: no band and no offsets of its own for the first row; it is a row like the others.
        if (host.state().dockRow && !host.state().uniformRows) grid.addItemDecoration(new DockDecoration(host.state().columns, host.density(), host.state().cinematic, cfg.alwaysShowNames, cfg.dockOpacity, cfg.dockGlass, cfg.dockEvenPadding, cfg.dockBand,
                host.state().singleRow, cfg.dockBandAll).bottomDp(AppAdapter.dockBottomDp(cfg.rowGap)));
        // The tiles share the header's edges: the first starts under the clock, the last ends under the pills.
        if (!host.state().singleRow) grid.addItemDecoration(new EdgeAlignDecoration(host.state().rtl));
        if (cfg.fadeRows) TopFade.attach(grid, 48 * host.density());
        // Reduced motion: the tiles are simply there, with no entrance (reloadApps skips it there too).
        if (host.state().animations && !host.state().reducedMotion) {
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(host.activity(), R.anim.tile_enter), 0.06f));
        }
        adapter = new AppAdapter(host.activity(), host.tileListener(), host.state().columns, cfg);
        Order savedOrder = Order.load(Order.file(host.activity()));
        adapter.setSavedOrder(Order.ranks(savedOrder, cfg.dockApps), Order.docked(savedOrder, cfg.dockApps));
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        int rowsBelowDock = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            // Item height follows the tile size, which follows the columns and the rows that must fit (AppAdapter.tileWidthPx).
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int tileW = AppAdapter.tileWidthPx(inner, h, host.state().columns, rowsBelowDock, cfg.dockTitle, cfg.dockRow, host.density(), cfg.rowGap, host.state().heroHeightPct);
            int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), true, host.density(), cfg.rowGap);
            // The first row's height: item height plus DockDecoration's offsets, or with uniform_rows the item alone.
            int dockRow = itemH + (host.state().uniformRows ? 0 : (int) (AppAdapter.dockExtraDp(cfg.rowGap) * host.density()));
            int gridRows = itemH * rowsBelowDock;
            // Keep the hero below the header when the library is enabled on a short screen,
            // even when the theme requests an extra visible row below the dock.
            int carouselRoom = host.carousel().hasItems() ? Math.round(CarouselController.CAROUSEL_FULL_DP * host.density()) : 0;
            int shelfClearance = cfg.appLibrary ? Math.max(host.shelf().measuredHeight(), carouselRoom) + (int) (40 * host.density()) : 0;
            int want = Math.max(shelfClearance, h - dockRow - gridRows - (int) (AppAdapter.BOTTOM_ROOM_DP * host.density()));
            // hero_height: the shelf takes that share of its room, the dock and the rows under it move up (or down) with
            // it. The dock itself stays on screen, whatever the percent; the floors below still apply.
            if (host.state().dockRow && host.state().heroHeightPct != 100) {
                want = Math.min(Math.round(want * host.state().heroHeightPct / 100f), h - dockRow - (int) (AppAdapter.BOTTOM_ROOM_DP * host.density()));
            }
            // Where the rows cannot fit even at the smallest tile (3 rows with the title on), the title keeps its
            // room above the dock and the last rows run off the bottom instead of the title drawing over the clock.
            if (host.state().dockRow && cfg.dockTitle && rowsBelowDock > 0) want = Math.max(want, (int) (AppAdapter.TITLE_ROOM_DP * host.density()));
            // No dock to push down: the grid starts at the top, with room for the first row's zoom and glass.
            if (!host.state().dockRow) want = (int) (AppAdapter.TOP_ROOM_DP * host.density());
            // uniform_rows: no dock to park and no rows counted under it. The first row rests on the shelf's bottom
            // (the carousel's full height or the measured shelf, plus the 40dp gap) at hero_height, and the rows fill
            // downward from there; those that do not fit scroll up on Down.
            if (host.state().uniformRows) want = UniformRows.restingLinePx(UniformRows.shelfRoomPx(host.shelf().measuredHeight(), carouselRoom, host.density()), host.state().heroHeightPct);
            // hero_height under 100: the line is that share of the room, but the shelf's text only scales to two
            // thirds (ShelfScale) and the shelf never rises past the header (ShelfController.SHELF_MIN_TOP_DP), so the line stays at
            // least the shelf's measured height plus its gap under that top, whatever the percent asks: the dots
            // never land on the first row. At 100 the shelf fits in its room and this changes nothing.
            int shelfGap = host.shelf().shelfGapPx(host.carousel().showing());
            if (host.state().dockRow && !host.state().singleRow) {
                want = Math.max(want, ShelfScale.floorPx(Math.round(ShelfController.SHELF_MIN_TOP_DP * host.density()), host.shelf().measuredHeight(), shelfGap, gridTopInRoot()));
            }
            // A single row never shows the names under its tiles (the title above the row names the app), so the
            // 7dp gap and 22dp label item_app.xml keeps for them would only lift the row: sit it that much lower.
            if (host.state().singleRow && !cfg.alwaysShowNames) want += (int) (29 * host.density());
            // shelf_every_row: room under the last row, so every row can scroll up to the padding line.
            int wantBottom = host.state().shelfEveryRow ? Math.max(grid.getPaddingBottom(), h - want - dockRow) : grid.getPaddingBottom();
            if (host.state().shelfEveryRow) {
                // The rows that scroll up past the padding line would run on under the shelf's name (clipToPadding is
                // off so a focused tile can grow past its row): cut them 16dp above the line, or at the shelf's gap
                // when that is less (a short shelf), which keeps the focused row's own growth and loses the row above.
                int clipTop = Math.max(0, want - Math.min((int) (16 * host.density()), shelfGap));
                Rect clip = grid.getClipBounds();
                if (clip == null || clip.top != clipTop || clip.right != r - l || clip.bottom != h) {
                    grid.setClipBounds(new Rect(0, clipTop, r - l, h));
                }
            }
            // The backdrop's size follows the dock line; set after this pass, not from inside it.
            final int shelfBottom = want;
            grid.post(() -> host.carousel().fitFeedArt(shelfBottom));
            if (grid.getPaddingTop() != want || grid.getPaddingBottom() != wantBottom) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), wantBottom);
                // A vertical grid parks the dock on the new padding line by scrolling (onTileFocused); a sideways
                // row cannot scroll vertically, and the layout request made from inside this layout pass is
                // dropped, so lay the row out again on the next frame. The glass and the title were placed for the
                // old position, so fit them again once it has moved.
                if (host.state().singleRow) grid.post(() -> {
                    grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            int pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1));
                            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
                            View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
                            if (card != null && card.isFocused()) onTileFocused(pos, card);
                        }
                    });
                    grid.requestLayout();
                });
            }
            // Cold start or column count > 4: the first focus can run before the grid has its final width or
            // padding, leaving the glass at the XML design size until a key is pressed.
            grid.post(this::refitGlass);
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
                // single_row scrolls sideways: the same bookkeeping on the other axis.
                if (dx != 0) {
                    glassTargetX -= dx;
                    glassScrollShiftX -= dx;
                    glass.setTranslationX(glass.getTranslationX() - dx);
                }
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentX") {
            @Override
            public float getValue(View v) {
                return v.getTranslationX() - glassScrollShiftX;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationX(value + glassScrollShiftX);
            }
        });
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (host.state().reducedMotion) {
                Motion.reduce(s);
            } else if (!focusOvershoot) {
                // Critically damped: glides straight onto the tile without swinging past it.
                s.setSpring(new SpringForce().setStiffness(host.state().advancedEffects ? 520f : 440f).setDampingRatio(1f));
            } else {
                s.setSpring(new SpringForce().setStiffness(host.state().advancedEffects ? 320f : 260f)
                        .setDampingRatio(host.state().advancedEffects ? 0.76f : 0.58f));
            }
        }
        // Hide the glass when focus leaves the tiles.
        host.activity().getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        // Registered for the activity's whole life, not only while it is started: an app installed or updated while
        // another app is in front must still mark the tiles stale, or Home would have to rebuild on every return.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.activity().registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            host.activity().registerReceiver(packageReceiver, packageFilter);
        }
    }

    /** onNewIntent with HOME: back to the top with the first dock tile focused, like the stock launcher. */
    public void onHomeAgain(boolean stopped) {
        grid.scrollToPosition(0);
        lastFocused = 0;
        lastFocusedKey = adapter.getItemCount() > 0 ? adapter.keyAt(0) : null;
        if (stopped) {
            tileRefresh.onHomeAgain();
        } else {
            // Still on screen (the quick panel was over it, or Home was already in front): the tiles are current,
            // so only go back to the first one; rebuilding would replay the entrance animation for nothing.
            grid.post(() -> focusTile(0));
        }
    }

    public void onStart() {
        tileRefresh.onStart();
    }

    /** onResume: the adapter back after onTrimMemory, then the rebuild or refocus TileRefresh asks for. */
    public void onResume() {
        if (grid.getAdapter() == null) grid.setAdapter(adapter);   // dropped by onTrimMemory
    }

    /** onResume, after the wallpaper is back: rebuild or refocus the tiles as TileRefresh decides. */
    public void refreshOnResume() {
        switch (tileRefresh.onResume(packagesChangedSinceRebuild())) {
            case REBUILD:
                host.state().tilesReady = false;
                reloadApps(true);
                break;
            case REFOCUS:
                // The same tiles: only the focus goes back, as after a rebuild, without replaying the entrance.
                host.state().tilesReady = false;
                reloadApps(true, false, false);
                break;
            default:
                break;
        }
    }

    public void onPause() {
        cancelPendingFocusScroll();
        if (appLibrary != null) appLibrary.close();
    }

    public void onStop() {
        tileRefresh.onStop();
    }

    /**
     * onTrimMemory past UI_HIDDEN: let go of every tile's artwork, and rebuild it when Home comes back. The adapter is
     * detached too: with Home stopped no layout pass runs, so the tile views (and the bitmaps they hold) would stay
     * attached until Home comes back. setAdapter(null) recycles them now.
     */
    public void onTrimMemory() {
        adapter.clear();
        grid.setAdapter(null);
        grid.getRecycledViewPool().clear();
        tileRefresh.invalidate();
    }

    /** onWindowFocusChanged(true): a library focus that waited for the window. */
    public void onWindowFocus() {
        if (libraryFocusPending && appLibrary == null) focusLibrary();
    }

    public void onDestroy() {
        host.activity().unregisterReceiver(packageReceiver);
    }
}
