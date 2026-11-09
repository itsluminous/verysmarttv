package com.itsluminous.verysmarttv.shelf;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.core.contract.HeroContract;
import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.theme.Motion;
import com.itsluminous.verysmarttv.topbar.TopBarFocus;

import java.util.Collections;
import java.util.List;

/**
 * hero_feed (DESIGN.md §8, moved from the activity verbatim): the Trakt companion's items on the shelf while the dock
 * is at rest, one every hero_feed_interval seconds, with backdrop, synopsis, chips and dots; its keys; and the reader
 * that brings the items. It takes the shelf's views from {@link ShelfController} (one package, one hero group).
 */
public final class CarouselController {
    final HomeHost host;
    final FrameLayout root;
    final ShelfController shelf;
    // ---- hero_feed (HeroFeed): the Trakt companion's items on the shelf while the dock is at rest ----
    private HeroFeed heroFeed;

    private boolean heroFeedOn;

    private int heroFeedIntervalMs;

    private List<HeroFeed.Entry> feedItems = Collections.emptyList();

    private int feedIndex;

    /** The shelf shows a carousel item instead of the focused app's name. */
    private boolean carouselShowing;

    /**
     * The carousel shelf at its full height, in dp: 10dp of padding above and below, the 36sp title (46dp), the meta
     * line (6 + 18dp), three synopsis lines (8 + 56dp), the chips (10 + 22dp) and the dots (10 + 6dp). The dock line
     * is placed for that whenever the companion has items, not for the lines the carousel happens to show: measuring
     * the live shelf made the line depend on which item was up and which layout pass ran last, and a short shelf
     * (hero_height) that trimmed the synopsis would have pulled the dock up a little further each time.
     */
    public static final float CAROUSEL_FULL_DP = 201f;

    /** heroFeedArt's design size (activity_main.xml, dp): 620 by 349, the bottom edge on the dock line at hero_height 100. */
    private static final float FEED_ART_W_DP = 620f, FEED_ART_H_DP = 349f;

    private ImageView heroFeedArt;

    private TextView heroSynopsis;

    private LinearLayout heroChips;

    private long feedArtId = -1;

    private final Runnable carouselAdvance = () -> {
        if (!carouselShowing || feedItems.isEmpty()) return;
        feedIndex = (feedIndex + 1) % feedItems.size();
        showCarouselItem(true);
    };

    /** hero_feed with items: the carousel owns the shelf . */
    public boolean hasItems() {
        return heroFeedOn && !feedItems.isEmpty();
    }

    /** The shelf shows a carousel item instead of the focused app's name . */
    public boolean showing() {
        return carouselShowing;
    }

    /** hero_feed: the views the carousel adds to the shelf, and the provider reader. Nothing shows until items arrive. */
    public void setUp(@NonNull Config cfg) {
        heroFeedOn = true;
        heroFeedIntervalMs = cfg.heroFeedInterval * 1000;
        heroFeedArt = host.findViewById(R.id.heroFeedArt);
        heroSynopsis = host.findViewById(R.id.heroSynopsis);
        heroChips = host.findViewById(R.id.heroChips);
        heroFeedArt.forceHasOverlappingRendering(false);
        // A frosted outline while the carousel has focus; nothing otherwise.
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x14FFFFFF);
        focus.setStroke((int) (2 * host.density()), 0xB3FFFFFF);
        focus.setCornerRadius(22 * host.density());
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focus);
        shelf.hero.setBackground(bg);
        // Focus can also reach the shelf from a tile's Up: the glass has no tile to sit on then.
        shelf.hero.setOnFocusChangeListener((v, has) -> {
            if (has) host.grid().hideGlass();
        });
        heroFeed = new HeroFeed(host.activity(), host.handler(), items -> {
            // The dock line is placed for the carousel while there are items (CAROUSEL_FULL_DP in the grid's layout
            // listener): lay the grid out again when that changes, so the line does not depend on which pass ran last.
            boolean hadItems = !feedItems.isEmpty();
            feedItems = items;
            if (hadItems != !items.isEmpty()) host.grid().view().requestLayout();
            if (items.isEmpty()) {
                // Nothing to show: the shelf goes back to naming the focused app, as without hero_feed.
                exitCarousel();
                return;
            }
            feedIndex = Math.min(feedIndex, items.size() - 1);
            host.grid().applyTileNames(true);
            if (carouselShowing) showCarouselItem(false);
            else if (shelf.heroShown && shelf.shelfTileFocused()) showCarousel(true);
        });
    }

    /** Puts the carousel on the shelf, in place of the app's name; animate swaps the name out, false fills in place. */
    public void showCarousel(boolean animate) {
        if (!host.state().resumed || carouselShowing || feedItems.isEmpty() || !shelf.heroShown || !shelf.shelfTileFocused() || Float.isNaN(shelf.lastDockTop)) return;
        carouselShowing = true;
        host.grid().applyTileNames(true);
        shelf.hero.setFocusable(true);
        int pad = (int) (10 * host.density());
        shelf.hero.setPadding(pad, pad, pad, pad);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) shelf.hero.getLayoutParams();
        lp.setMarginStart((int) (ShelfController.SHELF_START_DP * host.density()) - pad);
        shelf.hero.setLayoutParams(lp);
        shelf.fitCarouselTitle();
        shelf.shelfMargin(shelf.heroMeta, 6);
        shelf.shelfMargin(shelf.heroDots, 10);
        shelf.heroMeta.setVisibility(View.VISIBLE);
        heroSynopsis.setVisibility(View.VISIBLE);
        heroChips.setVisibility(View.VISIBLE);
        shelf.heroDots.setVisibility(View.VISIBLE);
        heroFeedArt.setVisibility(View.VISIBLE);
        showCarouselItem(animate);
    }

    /** Fills the shelf with the current item and fades its backdrop in; (re)starts the advance timer. */
    public void showCarouselItem(boolean animate) {
        if (feedItems.isEmpty()) return;
        feedIndex = Math.max(0, Math.min(feedIndex, feedItems.size() - 1));
        final HeroFeed.Entry e = feedItems.get(feedIndex);
        host.handler().removeCallbacks(carouselAdvance);
        host.handler().postDelayed(carouselAdvance, heroFeedIntervalMs);
        Runnable fill = () -> {
            shelf.heroName.setText(e.title);
            String meta = e.meta != null ? e.meta : (e.year > 0 ? String.valueOf(e.year) : "");
            shelf.heroMeta.setText(meta.isEmpty() ? e.kicker : e.kicker + "  ·  " + meta);
            shelf.heroMeta.setVisibility(View.VISIBLE);
            heroSynopsis.setText(e.synopsis);
            heroChips.removeAllViews();
            addFeedChip("IMDb", e.imdb, 0x99F5C518);
            addFeedChip("RT", e.rt, 0x99FA320A);
            addFeedChip("Trakt", e.trakt, 0xA6ED1C24);
            shelf.drawDots(feedItems.size(), feedIndex);
            placeCarouselShelf();
        };
        if (animate && host.state().animations && !host.state().reducedMotion) {
            shelf.hero.animate().cancel();
            shelf.hero.animate().alpha(0f).translationX(-12 * host.density()).setDuration(120).withEndAction(() -> {
                if (!carouselShowing) return;
                fill.run();
                shelf.hero.setTranslationX(12 * host.density());
                shelf.hero.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
        } else {
            shelf.hero.animate().cancel();
            fill.run();
            shelf.hero.setTranslationX(0f);
            shelf.hero.setAlpha(1f);
        }
        feedArtId = e.id;
        heroFeed.backdrop(e, 640, 360, bmp -> {
            if (!carouselShowing || feedArtId != e.id) return;
            heroFeedArt.animate().cancel();
            host.topBar().setPillsOverArt(bmp != null);
            if (bmp == null) {
                heroFeedArt.animate().alpha(0f).setDuration(200).start();
                return;
            }
            if (!host.state().animations || host.state().reducedMotion || heroFeedArt.getAlpha() == 0f) {
                heroFeedArt.setImageBitmap(bmp);
                if (host.state().animations) heroFeedArt.animate().alpha(1f).setDuration(host.state().reducedMotion ? Motion.REDUCED_MS : 500).start();
                else heroFeedArt.setAlpha(1f);
                return;
            }
            heroFeedArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                heroFeedArt.setImageBitmap(bmp);
                heroFeedArt.animate().alpha(1f).setDuration(420).start();
            }).start();
        });
    }

    private void addFeedChip(String label, String value, int border) {
        if (value == null) return;
        TextView t = new TextView(host.activity());
        t.setTypeface(shelf.heroMeta.getTypeface());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setTextColor(0xFFFFFFFF);
        t.setText(host.activity().getString(R.string.rating_chip, label, value));
        int ph = (int) (10 * host.density()), pv = (int) (4 * host.density());
        t.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setStroke((int) host.density(), border);
        bg.setCornerRadius(999 * host.density());
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) (8 * host.density());
        heroChips.addView(t, lp);
    }

    /**
     * The carousel shelf is taller than the name alone: measure it now and keep its text's bottom a little above the
     * dock. When it would reach up into the clock and date (the header ends about 108dp down), the synopsis gives up
     * lines, three to one, and the shelf never goes above the header.
     */
    public void placeCarouselShelf() {
        if (Float.isNaN(shelf.lastDockTop)) return;
        float minTop = ShelfController.SHELF_MIN_TOP_DP * host.density();   // under the clock (26dp down, 52sp tall)
        // Everything on first (an earlier item may have given some of it up), then hero_height: a short shelf starts
        // with fewer lines (two at 80 and below, one at 60 and below), and gives up the chips before the last line.
        heroSynopsis.setVisibility(View.VISIBLE);
        heroChips.setVisibility(ShelfScale.chipsShown(host.state().heroHeightPct) ? View.VISIBLE : View.GONE);
        int lines = ShelfScale.synopsisLines(host.state().heroHeightPct);
        heroSynopsis.setMaxLines(lines);
        float y;
        int gap = shelf.shelfGapPx(true);
        while (true) {
            shelf.hero.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            y = shelf.lastDockTop - gap - shelf.hero.getMeasuredHeight();
            if (y >= minTop) break;
            if (lines > 1) heroSynopsis.setMaxLines(--lines);
            else if (heroChips.getVisibility() == View.VISIBLE) heroChips.setVisibility(View.GONE);
            else if (heroSynopsis.getVisibility() == View.VISIBLE) heroSynopsis.setVisibility(View.GONE);
            else break;
        }
        shelf.hero.setY(Math.max(y, minTop));
    }

    /**
     * hero_feed: the backdrop's bottom edge sits on the dock line, so it shrinks and grows with the shelf (hero_height),
     * both ways to keep its baked-in fades where they were; {@code want} is the grid's top padding this layout settles on.
     * Nothing to do at 100, where activity_main.xml's size already fits.
     */
    public void fitFeedArt(int want) {
        if (heroFeedArt == null || host.state().heroHeightPct == 100) return;
        int artH = host.grid().gridTopInRoot() + want + Math.round(6 * host.density());
        if (artH <= 0) return;
        ViewGroup.LayoutParams lp = heroFeedArt.getLayoutParams();
        int artW = Math.round(artH * FEED_ART_W_DP / FEED_ART_H_DP);
        if (lp.height == artH && lp.width == artW) return;
        lp.height = artH;
        lp.width = artW;
        heroFeedArt.setLayoutParams(lp);
    }

    /**
     * The shelf back to the focused app's name (the companion has no items, or Home is going away); the focus back
     * on its tile if the carousel had it, and the names under the tiles off again.
     */
    public void exitCarousel() {
        host.handler().removeCallbacks(carouselAdvance);
        host.grid().applyTileNames(false);
        if (!carouselShowing) return;
        carouselShowing = false;
        host.topBar().setPillsOverArt(false);
        boolean hadFocus = shelf.hero.hasFocus();
        feedArtId = -1;
        shelf.hero.animate().cancel();
        shelf.hero.setTranslationX(0f);
        shelf.hero.setAlpha(1f);
        shelf.hero.setFocusable(false);
        shelf.hero.setPadding(0, 0, 0, 0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) shelf.hero.getLayoutParams();
        lp.setMarginStart((int) (ShelfController.SHELF_START_DP * host.density()));
        shelf.hero.setLayoutParams(lp);
        shelf.shelfTitleStyle();
        heroSynopsis.setVisibility(View.GONE);
        heroChips.setVisibility(View.GONE);
        heroChips.removeAllViews();
        shelf.heroDots.setVisibility(host.state().cinematic && shelf.heroDotsShown ? View.VISIBLE : View.GONE);
        shelf.heroMeta.setVisibility(host.state().cinematic ? View.INVISIBLE : View.GONE);
        heroFeedArt.animate().cancel();
        if (host.state().animations && !host.state().reducedMotion) {
            heroFeedArt.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                if (!carouselShowing) heroFeedArt.setVisibility(View.GONE);
            }).start();
        } else {
            heroFeedArt.setAlpha(0f);
            heroFeedArt.setVisibility(View.GONE);
        }
        int pos = host.grid().focusedTilePosition();
        if (pos < 0) pos = host.grid().lastFocused();
        if (pos >= 0 && pos < host.grid().adapter().appCount()) {
            shelf.fillHero(pos, host.grid().adapter().labelAt(pos));
            if (!Float.isNaN(shelf.lastDockTop)) {
                shelf.hero.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
                shelf.hero.setY(Math.max(ShelfController.SHELF_MIN_TOP_DP * host.density(), shelf.lastDockTop - shelf.shelfGapPx(false) - shelf.hero.getMeasuredHeight()));
            }
        }
        if (hadFocus && host.state().resumed) host.grid().focusTile(host.grid().lastFocused());
    }

    /**
     * Keys for the carousel. With the shelf focused: Left/Right move items, OK opens the title in the companion app,
     * Down and Back return to the tile, Up goes on to the top bar; the carousel stays on the shelf throughout. From a
     * dock tile, Up focuses the carousel.
     */
    public boolean handleKey(@NonNull KeyEvent event) {
        if (!carouselShowing || host.reorder().active() || host.popups().isOpen() || host.cards().promptShowing()) return false;
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (shelf.hero.hasFocus()) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && feedItems.size() > 1) {
                        int n = feedItems.size();
                        feedIndex = (feedIndex + (code == KeyEvent.KEYCODE_DPAD_LEFT ? n - 1 : 1)) % n;
                        showCarouselItem(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (down) host.grid().focusTile(host.grid().lastFocused());
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    // Rule 5: the score pill when shown, else Inputs.
                    if (down) host.topBar().focusAbove(TopBarFocus.fromCarouselUp(host.topBar().bar()));
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (!down && !event.isCanceled() && !feedItems.isEmpty()) openFeedItem(feedItems.get(feedIndex));
                    return true;
                default:
                    return false;
            }
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP && down
                && TopBarFocus.fromGridUp(host.topBar().bar(), carouselShowing, shelf.dockTileFocused()).stop == TopBarFocus.Stop.CAROUSEL) {
            focusCarousel();
            return true;
        }
        return false;
    }

    /** Focus the carousel on the shelf (Up from a dock tile, Down from the score pill: TopBarFocus's CAROUSEL verdict). */
    public void focusCarousel() {
        if (!carouselShowing) return;
        host.grid().hideGlass();
        shelf.hero.requestFocus();
    }

    /** OK on a carousel item: the companion's details page for it. */
    private void openFeedItem(HeroFeed.Entry e) {
        Intent i = new Intent().setComponent(HeroContract.DETAILS_ACTIVITY)
                .putExtra(HeroContract.EXTRA_TRAKT_ID, e.traktId).putExtra(HeroContract.EXTRA_KIND, e.kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            host.activity().startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Log.w("VerySmartTV.HeroFeed", "companion details not opened: " + ex.getMessage());
        }
    }

    public CarouselController(@NonNull HomeHost host, @NonNull FrameLayout root, @NonNull ShelfController shelf) {
        this.host = host;
        this.root = root;
        this.shelf = shelf;
    }

    /** showOwnedShelf: the backdrop back after a rebuild or hideHero took it to 0 while the carousel was up. */
    public void fadeArtBackIn() {
        if (heroFeedArt.getAlpha() == 0f && heroFeedArt.getDrawable() != null) heroFeedArt.animate().alpha(1f).setDuration(260).start();
    }

    /** hero_feed is on: the reader exists and keys may go to the carousel. */
    public boolean on() {
        return heroFeedOn;
    }

    /**
     * onResume, before the tiles are refreshed: the reader starts; the carousel owns the shelf while the companion has
     * items, so re-arm its timer, or put it up now.
     */
    public void onResume() {
        if (heroFeed != null) {
            heroFeed.start();
            if (carouselShowing) showCarouselItem(false);
            else if (shelf.heroOwned() && shelf.heroShown && shelf.shelfTileFocused()) showCarousel(false);
        }
    }

    /** onPause: the carousel stays on the shelf for the return to Home; only its advance timer and the reader stop. */
    public void onPause() {
        if (heroFeed != null) {
            host.handler().removeCallbacks(carouselAdvance);
            heroFeed.stop();
        }
    }

    /** onTrimMemory and onDestroy: the reader lets go of its bitmaps; onResume starts it again. */
    public void release() {
        if (heroFeed != null) heroFeed.release();
    }
}
