package com.itsluminous.verysmarttv.shelf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.R;
import com.itsluminous.verysmarttv.grid.LastOpened;
import com.itsluminous.verysmarttv.grid.UniformRows;
import com.itsluminous.verysmarttv.home.HomeHost;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.theme.Motion;

import java.util.HashMap;
import java.util.Map;

/**
 * The top shelf (DESIGN.md §8, moved from the activity verbatim): the focused app's name over the dock, the cinematic
 * shelf's artwork, "Last opened" line, dots and scrim, the shelf's typography and spacing for hero_height, and the
 * glow-only state the grid rows take. {@link CarouselController} is the shelf's other face: it shares these views
 * (the one hero group of activity_main.xml), which is why the two live in one package and read each other's fields.
 */
public final class ShelfController {
    final HomeHost host;
    final FrameLayout root;
    final AmbientGlow glow;
    /** The carousel that owns the shelf while the companion has items (one view group, so it is set, not passed). */
    CarouselController carousel;
    TextView heroName;

    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    View hero;

    TextView heroMeta;

    LinearLayout heroDots;

    /** config.xml cinematic_white_focus: the cinematic shelf keeps the white highlight. */
    private boolean cinematicWhiteFocus;

    private ImageView heroArt;

    /** config.xml cinematic_dots: false leaves the dock position dots out of the cinematic shelf. */
    boolean heroDotsShown = true;

    /** The artwork's opacity behind the cinematic shelf (config.xml cinematic_art); 0 leaves it out. */
    private float heroArtAlpha = 0.5f;

    /** Softened artwork per app (HeroArt, about 5 KB each), made the first time its dock tile is focused. */
    private final Map<String, Bitmap> heroArtCache = new HashMap<>();

    /** The app whose artwork heroArt shows, so focusing it again only fades it back in. */
    private String heroArtKey;

    /** Where the dock's top was when the shelf was last placed, so the taller carousel shelf can be re-placed. */
    float lastDockTop = Float.NaN;

    boolean heroShown;

    /** The shelf's measured height, for the dock line . */
    public int measuredHeight() {
        return hero.getMeasuredHeight();
    }

    /** A rebuild that puts focus back: the shelf, the glow and the artwork start from nothing . */
    public void resetForReload() {
        heroShown = false;
        hero.setAlpha(0f);
        glow.setAlpha(0f);
        heroArt.animate().cancel();
        heroArt.setAlpha(0f);
    }

    /** An updated app may bring new artwork; the backdrop is cheap to make again . */
    public void dropArtCache() {
        heroArtCache.clear();
        heroArtKey = null;
    }

    /** cinematic_white_focus: the cinematic shelf keeps the white highlight . */
    public boolean cinematicWhiteFocus() {
        return cinematicWhiteFocus;
    }

    public void showHero(int position, float dockTopInRoot) {
        lastDockTop = dockTopInRoot;
        if (heroOwned()) {
            // hero_feed with items: the carousel owns the shelf; the focused app's name is under its tile instead.
            showOwnedShelf(position);
            return;
        }
        if (carousel.showing()) return;   // the companion's items just went away: exitCarousel is on its way
        String label = host.grid().adapter().labelAt(position);
        int color = host.grid().adapter().ambientColorAt(position);
        // A short shelf (hero_height) can leave less room than the name needs: it then stays under the header rather
        // than over the clock, and the 40dp gap above the dock absorbs the difference.
        float y = Math.max(SHELF_MIN_TOP_DP * host.density(), dockTopInRoot - shelfGapPx(false) - hero.getHeight());
        boolean changed = !label.contentEquals(heroName.getText());
        if (host.state().cinematic && heroArtAlpha > 0f) showHeroArt(position);

        if (!host.state().animations || host.state().reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            hero.animate().cancel();
            glow.cancelFade();
            fillHero(position, label);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.setAlpha(1f);
            if (glow.on()) {
                glow.setAmbient(color);
                glow.setAlpha(1f);
            }
            heroShown = true;
            return;
        }

        if (!heroShown) {
            glow.stopAmbientAnimation();
            hero.animate().cancel();
            glow.cancelFade();
            fillHero(position, label);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.animate().alpha(1f).setDuration(260).start();
            if (glow.on()) {
                glow.setAmbient(color);
                glow.fadeTo(1f, 600);
            }
            heroShown = true;
        } else if (changed) {
            if (host.state().advancedEffects) hero.animate().cancel();
            hero.animate().alpha(0f).translationX(-12 * host.density()).setDuration(120).withEndAction(() -> {
                fillHero(position, label);
                hero.setY(y);
                hero.setTranslationX(12 * host.density());
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (glow.on() && host.state().advancedEffects) {
                glow.transitionAmbient(color);
            } else if (glow.on() && color != glow.color() && glow.snaps()) {
                glow.cancelFade();
                glow.setAmbient(color);
                glow.setAlpha(1f);
            } else if (glow.on() && color != glow.color()) {
                glow.dipAndSwap(color, 200);
            }
        } else {
            hero.setY(y);
            // onPause cancels the fade-ins, and a second Home during one lands on the same tile, which starts none:
            // finish them, or the shelf and the glow stay dim until focus moves to another app.
            if (hero.getAlpha() < 1f) hero.animate().alpha(1f).setDuration(260).start();
            if (glow.on() && color == glow.color() && glow.getAlpha() < 1f) {
                glow.fadeTo(1f, 600);
            }
        }
    }

    /** The shelf's text for this dock app: its name and, on the cinematic shelf, "Last opened" and the dots. */
    public void fillHero(int position, @NonNull String label) {
        heroName.setText(label);
        if (!host.state().cinematic) return;
        updateHeroMeta(position);
        updateHeroDots(position);
    }

    /** hero_feed with items: the carousel owns the top shelf, whatever tile is focused. */
    public boolean heroOwned() {
        return carousel.hasItems();
    }

    public boolean dockTileFocused() {
        int pos = host.grid().focusedTilePosition();
        return pos >= 0 && host.state().dockRow && (host.state().singleRow || pos < host.state().columns);
    }

    /** A tile whose row the shelf belongs to: the dock, or with shelf_every_row any row. */
    public boolean shelfTileFocused() {
        int pos = host.grid().focusedTilePosition();
        return pos >= 0 && host.state().dockRow && (host.state().singleRow || pos < host.state().columns || host.state().shelfEveryRow);
    }

    /**
     * The shelf for a focused tile while the carousel owns it: the carousel goes up if it is not up yet (and stays
     * where it is otherwise), the cinematic artwork of the app stays out, and only the glow takes the app's colour.
     */
    public void showOwnedShelf(int position) {
        int color = host.grid().adapter().ambientColorAt(position);
        boolean first = !heroShown;
        heroShown = true;
        if (!carousel.showing()) {
            carousel.showCarousel(false);
        } else {
            carousel.placeCarouselShelf();
            // A rebuild (refreshTiles) or hideHero took the shelf to 0 while the carousel was up: bring it back. Not
            // while a title swap is fading it out: its end action fills the title and fades it back in.
            if (hero.getAlpha() < 1f && !carousel.swapping()) {
                hero.animate().cancel();
                if (host.state().animations && !host.state().reducedMotion) hero.animate().alpha(1f).translationX(0f).setDuration(260).start();
                else {
                    hero.setTranslationX(0f);
                    hero.setAlpha(1f);
                }
            }
            carousel.fadeArtBackIn();
        }
        if (host.state().cinematic && heroArt.getAlpha() > 0f) {
            heroArt.animate().cancel();
            if (host.state().animations && !host.state().reducedMotion) heroArt.animate().alpha(0f).setDuration(300).start();
            else heroArt.setAlpha(0f);
            heroArtKey = null;   // so the app's artwork comes back when the shelf returns to its name
        }
        if (!glow.on()) return;
        if (!host.state().animations || host.state().reducedMotion) {
            glow.cancelFade();
            glow.setAmbient(color);
            glow.setAlpha(1f);
        } else if (first) {
            glow.stopAmbientAnimation();
            glow.cancelFade();
            glow.setAmbient(color);
            glow.fadeTo(1f, 600);
        } else if (color != glow.color()) {
            if (host.state().advancedEffects) {
                glow.transitionAmbient(color);
            } else if (glow.snaps()) {
                glow.cancelFade();
                glow.setAmbient(color);
                glow.setAlpha(1f);
            } else {
                glow.dipAndSwap(color, 200);
            }
        } else if (glow.getAlpha() < 1f) {
            glow.fadeTo(1f, 600);
        }
    }

    /** The carousel title's widest and narrowest sizes: 36sp, and 24sp before an ellipsis (five sixths of each at hero_height 60). */
    private static final float CAROUSEL_TITLE_SP = 36f, CAROUSEL_TITLE_MIN_SP = 24f, CAROUSEL_TITLE_WIDTH_DP = 560f;

    /**
     * The app's name on the shelf in the carousel title's type (fitCarouselTitle: 36sp in its one-line box, the meta
     * line 6dp under it) rather than the layout's 48sp or the cinematic 68sp: a theme that has the carousel
     * (hero_feed), whether or not it is showing. Without it the name was the clock's size next to a 36sp carousel.
     */
    private boolean carouselTypography;

    /**
     * The shelf's title and spacing when it names the focused app: the carousel's own with carouselTypography, the
     * layout's (48sp, or the cinematic 68sp, the meta line 12dp under the name and the dots 16dp) otherwise.
     */
    public void shelfTitleStyle() {
        if (carouselTypography) {
            fitCarouselTitle();
            shelfMargin(heroMeta, 6);
            shelfMargin(heroDots, 10);
        } else {
            unfitCarouselTitle(shelfTitleSp(host.state().cinematic ? 68f : 48f));
            shelfMargin(heroMeta, 12);
            shelfMargin(heroDots, 16);
        }
    }

    /**
     * The carousel's title on one line: from 36sp down to 24sp, whichever largest fits 560dp, then an ellipsis. The
     * view is given that width and one 36sp line of height, so a shrunk title leaves the synopsis, chips and dots
     * where a short title has them (TextView's auto-size needs the bounded box; with wrap_content it never grows
     * back after shrinking). The text sits at the bottom of the box, next to the kicker line under it.
     */
    void fitCarouselTitle() {
        float maxSp = shelfTitleSp(CAROUSEL_TITLE_SP), minSp = shelfTitleSp(CAROUSEL_TITLE_MIN_SP);
        heroName.setHorizontallyScrolling(false);   // singleLine's scrolling would give auto-size an unbounded width
        heroName.setMaxLines(1);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroName.setGravity(Gravity.BOTTOM | (heroName.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));   // keeps the layout's horizontal gravity
        ViewGroup.LayoutParams lp = heroName.getLayoutParams();
        lp.width = (int) (CAROUSEL_TITLE_WIDTH_DP * host.density());
        lp.height = oneLinePx(heroName, maxSp);
        heroName.setLayoutParams(lp);
        heroName.setAutoSizeTextTypeUniformWithConfiguration(Math.round(minSp), Math.round(maxSp), 1, TypedValue.COMPLEX_UNIT_SP);
    }

    /** The app's name again: one line of sp that overflows rather than wraps, the view as wide and tall as the text. */
    private void unfitCarouselTitle(float sp) {
        heroName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);   // setTextSize is ignored while on
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        heroName.setHorizontallyScrolling(true);
        heroName.setEllipsize(null);
        heroName.setGravity(Gravity.TOP | (heroName.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));
        ViewGroup.LayoutParams lp = heroName.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        heroName.setLayoutParams(lp);
    }

    /** One line of the view's typeface at sp, no font padding (as heroName is laid out), plus a pixel so that size still fits auto-size's test. */
    private static int oneLinePx(TextView t, float sp) {
        android.graphics.Paint p = new android.graphics.Paint(t.getPaint());
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, t.getResources().getDisplayMetrics()));
        android.graphics.Paint.FontMetricsInt fm = p.getFontMetricsInt();
        return fm.descent - fm.ascent + t.getPaddingTop() + t.getPaddingBottom() + 1;
    }

    /** The shelf never reaches up into the header: 26dp of padding and the 52sp clock end about 90dp down. */
    public static final float SHELF_MIN_TOP_DP = 90f;

    /** The shelf's left margin, dp: the header's own (activity_main.xml), so its texts start where the clock does. */
    static final float SHELF_START_DP = 56f;

    /** The shelf's title size for this hero_height: the layout's own at 100, two thirds of it at 60 (ShelfScale). */
    public float shelfTitleSp(float base) {
        return ShelfScale.textSp(base, host.state().heroHeightPct);
    }

    /**
     * The room the shelf keeps above the focused row's tiles, px: 40dp under the app's name (14 plus the 26 of the
     * dock layout), 12dp under the carousel's dots, both scaled with hero_height and never under 8dp.
     */
    public int shelfGapPx(boolean carousel) {
        return ShelfScale.spacingPx(carousel ? 12f : 40f, 8f, host.state().heroHeightPct, host.density());
    }

    /** A shelf line's margin above it, the layout's dp scaled with hero_height (ShelfScale), never under 4dp. */
    public void shelfMargin(@NonNull View v, int dp) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.topMargin = ShelfScale.spacingPx(dp, 4f, host.state().heroHeightPct, host.density());
        v.setLayoutParams(lp);
    }

    /**
     * The parts that never change: a larger name, the meta line and the dots, the artwork and the scrim, at
     * scrimPercent of its strength (cinematic_scrim). cinematic_art 0 leaves the artwork out, 0 percent the scrim.
     */
    private void setUpCinematicShelf(int scrimPercent) {
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, shelfTitleSp(68f));
        heroName.setLetterSpacing(-0.025f);
        heroMeta.setVisibility(View.INVISIBLE);   // keeps its height, so the name does not jump when it fills in
        heroDots.setVisibility(heroDotsShown ? View.VISIBLE : View.GONE);
        heroArt.setVisibility(heroArtAlpha > 0f ? View.VISIBLE : View.GONE);
        // One stretched bitmap: apply its alpha while drawing instead of through an offscreen buffer.
        heroArt.forceHasOverlappingRendering(false);
        if (scrimPercent <= 0) return;   // cinematic_scrim 0: no scrim at all
        View scrim = host.findViewById(R.id.heroScrim);
        Drawable shade = scrimDrawable();
        // On the bitmap's paint, not the view: a view's alpha would draw the full screen through an offscreen buffer.
        shade.setAlpha(scrimPercent * 255 / 100);
        scrim.setBackground(shade);
        scrim.setVisibility(View.VISIBLE);
    }

    /**
     * Darkens the side the shelf's text is on and the lower part of the screen, so the name, the dock and
     * the grid stay readable over the artwork. One small bitmap stretched over the screen, like the glow.
     */
    private Drawable scrimDrawable() {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        boolean rtl = host.isRtl();   // the scrim is drawn, not laid out, so it mirrors itself
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(rtl ? w : 0, 0, rtl ? 0 : w, 0,
                new int[]{0xD1060406, 0x59060406, 0x00060406}, new float[]{0f, 0.42f, 0.7f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x00060406, 0x00060406, 0xDB060406, 0xFF060406}, new float[]{0f, 0.28f, 0.58f, 0.76f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable scrim = new BitmapDrawable(host.activity().getResources(), bmp);
        scrim.setGravity(Gravity.FILL);
        return scrim;
    }

    /** Swaps the backdrop to this app's artwork: fade out, swap, fade in, the way the shelf's name changes. */
    private void showHeroArt(int position) {
        String key = host.grid().adapter().keyAt(position);
        if (key == null) return;
        heroArt.animate().cancel();
        if (key.equals(heroArtKey)) {
            fadeHeroArt(420);
            return;
        }
        Bitmap art = heroArtCache.get(key);
        if (art == null) {
            art = HeroArt.from(host.grid().adapter().artworkAt(position));
            if (art != null) heroArtCache.put(key, art);
        }
        heroArtKey = key;
        final Bitmap next = art;
        if (!host.state().animations || host.state().reducedMotion || heroArt.getAlpha() == 0f) {
            heroArt.setImageBitmap(next);
            fadeHeroArt(600);
            return;
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            heroArt.setImageBitmap(next);
            heroArt.animate().alpha(heroArtAlpha).setDuration(420).start();
        }).start();
    }

    private void fadeHeroArt(int ms) {
        if (!host.state().animations) heroArt.setAlpha(heroArtAlpha);
        else heroArt.animate().alpha(heroArtAlpha).setDuration(host.state().reducedMotion ? Math.min(ms, Motion.REDUCED_MS) : ms).start();
    }

    /** "Last opened yesterday, 9:40 PM", the time in white; nothing (but the same height) when it was never seen. */
    private void updateHeroMeta(int position) {
        long when = LastOpened.get(host.activity(), host.grid().adapter().packageAt(position));
        if (when <= 0) {
            heroMeta.setVisibility(View.INVISIBLE);
            return;
        }
        String rel = LastOpened.describe(host.activity(), when);
        String text = host.activity().getString(R.string.last_opened, rel);
        SpannableString line = new SpannableString(text);
        int at = text.indexOf(rel);
        if (at >= 0) line.setSpan(new ForegroundColorSpan(0xFFFFFFFF), at, at + rel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroMeta.setText(line);
        heroMeta.setVisibility(View.VISIBLE);
    }

    /** One dot per dock app, the focused one drawn long and white. Hebrew docks start on the right, so do the dots. */
    private void updateHeroDots(int position) {
        if (!heroDotsShown) return;
        int count = Math.min(host.grid().adapter().dockFilled(), host.state().columns);
        // One dot per app in the focused row: the last row may be short (shelf_every_row, and so uniform_rows). The
        // apps alone: getItemCount also counts the All apps row the library adds after the last Home row (its own
        // full-width item, AppAdapter.isLibraryAt), which gave the last row one dot more than its tiles.
        if (host.state().shelfEveryRow) count = UniformRows.rowCount(position, host.state().columns, host.grid().adapter().appCount());
        drawDots(count, host.state().shelfEveryRow ? position % host.state().columns : position);
    }

    /** count dots, the on-th drawn long and white. Hebrew docks start on the right, so do the dots. */
    public void drawDots(int count, int on) {
        heroDots.setVisibility(count > 1 ? View.VISIBLE : View.INVISIBLE);
        if (heroDots.getChildCount() != count) {
            heroDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(host.activity());
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(3 * host.density());
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * host.density()), (int) (6 * host.density()));
                if (i > 0) lp.leftMargin = (int) (6 * host.density());
                heroDots.addView(dot, lp);
            }
        }
        for (int i = 0; i < count; i++) {
            View dot = heroDots.getChildAt(i);
            boolean lit = i == on;
            ((GradientDrawable) dot.getBackground()).setColor(lit ? 0xFFFFFFFF : 0x4DFFFFFF);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            int w = (int) ((lit ? 20 : 6) * host.density());
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
    }

    /**
     * Grid tiles (every tile with dock_row false): the glow behind the grid takes the focused tile's colour; the big
     * name above the dock does not show (every grid tile has its name underneath).
     */
    public void showGlowOnly(int position) {
        int color = host.grid().adapter().ambientColorAt(position);
        glow.stopAmbientAnimation();
        hero.animate().cancel();
        glow.cancelFade();
        hero.setAlpha(0f);
        heroArt.animate().cancel();
        heroArt.setAlpha(0f);
        heroShown = false;
        if (!glow.on()) return;
        if (!host.state().animations || host.state().reducedMotion) {
            glow.setAmbient(color);
            glow.setAlpha(1f);
            return;
        }
        if (color == glow.color() && glow.hasMask()) {
            glow.fadeTo(1f, 300);
            return;
        }
        if (glow.snaps()) {
            glow.setAmbient(color);
            glow.setAlpha(1f);
            return;
        }
        // The same dip-and-swap as the dock's top shelf.
        glow.dipAndSwap(color, glow.getAlpha() > 0.35f ? 200 : 0);
    }

    public void hideHero() {
        if (!heroShown) return;
        glow.stopAmbientAnimation();
        hero.animate().cancel();
        glow.cancelFade();
        heroArt.animate().cancel();
        heroShown = false;
        if (host.state().animations && !host.state().reducedMotion) {
            hero.animate().alpha(0f).setDuration(160).start();
            if (glow.on()) glow.fadeTo(0f, 300);
            heroArt.animate().alpha(0f).setDuration(300).start();
        } else {
            hero.setAlpha(0f);
            glow.setAlpha(0f);
            heroArt.setAlpha(0f);
        }
    }

    public ShelfController(@NonNull HomeHost host, @NonNull FrameLayout root, @NonNull AmbientGlow glow) {
        this.host = host;
        this.root = root;
        this.glow = glow;
        carousel = new CarouselController(host, root, this);
    }

    /** The carousel on this shelf. */
    @NonNull
    public CarouselController carousel() {
        return carousel;
    }

    /** onCreate, after the glow: the shelf's views, the cinematic set-up and the title style. */
    public void setUp(@NonNull Config cfg) {
        heroName = host.findViewById(R.id.heroName);
        hero = host.findViewById(R.id.hero);
        // The hero is now a group (name, and the cinematic lines): without this every fade of the default
        // shelf would render it through an offscreen buffer first, as the glow above notes.
        hero.forceHasOverlappingRendering(false);
        heroMeta = host.findViewById(R.id.heroMeta);
        heroDots = host.findViewById(R.id.heroDots);
        heroArt = host.findViewById(R.id.heroArt);
        heroDotsShown = cfg.cinematicDots;
        cinematicWhiteFocus = cfg.cinematicWhiteFocus;
        heroArtAlpha = cfg.cinematicArt / 100f;
        if (host.state().cinematic) setUpCinematicShelf(cfg.cinematicScrim);
        else heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, shelfTitleSp(48f));   // the layout's 48sp, scaled with hero_height
        // A theme with the carousel (hero_feed): the app's name takes the carousel title's size and box too, so the
        // shelf reads the same whether the carousel is on, off in Launcher settings, or without items.
        carouselTypography = (cfg.themeHeroFeed || cfg.heroFeed) && host.state().dockRow && cfg.dockTitle;
        // The meta line and the dots keep their distance from the name in proportion too (ShelfScale).
        shelfTitleStyle();
    }

    /** onCreate, after the carousel: dock_title false hides the shelf for good. */
    public void applyDockTitle(@NonNull Config cfg) {
        // dock_title false: the name above the dock (and the cinematic lines under it) never shows; the glow
        // still follows the dock.
        if (!cfg.dockTitle) hero.setVisibility(View.GONE);
    }

    /** onResume, back from an app opened from its tile: the cinematic "Last opened" line kept the time from before. */
    public void onResume() {
        int last = host.grid().lastFocused();
        if (host.state().cinematic && heroMeta != null && last >= 0 && last < host.grid().adapter().appCount()) updateHeroMeta(last);
    }

    /** onPause: the fades stop where they are. */
    public void onPause() {
        hero.animate().cancel();
        heroArt.animate().cancel();
    }
}
