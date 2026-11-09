package com.itsluminous.verysmarttv.shelf;

import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.settings.SettingsChoices;

/**
 * hero_height: the shelf's typography and spacing follow its height, so a short shelf (Compact, 60) holds its name
 * or the carousel's title above the first row instead of running into it. The grid's resting line (its top padding)
 * is that height's share of the shelf's room, but the shelf cannot move up past the header (HomeActivity's
 * SHELF_MIN_TOP_DP), so at 60 the room under the clock was 60% of what the full-size text needs and the dots landed on
 * the tiles. Two things fix that: the sizes below scale with the height, and {@link #floorPx} keeps the line under the
 * shelf whatever the text measures. The pure arithmetic lives here so it can be tested without a TV.
 */
public final class ShelfScale {
    private ShelfScale() { }

    /** The layout's own height, where nothing scales, and the Compact level the factor is pinned at two thirds. */
    static final int FULL = 100, COMPACT = 60;

    /**
     * The size factor for a height: 1 at 100 and above, two thirds at 60 (a 36sp carousel title reads 24sp, the
     * 68sp cinematic name 45sp), on the line through both (five sixths at 80, Short), down to the key's floor of 50.
     */
    static float factor(int heroHeightPct) {
        int pct = Math.max(SettingsChoices.HERO_HEIGHT_MIN, Math.min(FULL, heroHeightPct));
        return 1f - (FULL - pct) / 120f;
    }

    /** A text size at that height, whole sp: 36 reads 36, 30, 24 at 100, 80, 60. */
    public static float textSp(float baseSp, int heroHeightPct) {
        return Math.round(baseSp * factor(heroHeightPct));
    }

    /** A margin or gap at that height, px: {@code baseDp} scaled, never under {@code minDp}. */
    public static int spacingPx(float baseDp, float minDp, int heroHeightPct, float density) {
        return Math.round(Math.max(minDp, baseDp * factor(heroHeightPct)) * density);
    }

    /** Synopsis lines the carousel starts with: three, two at 80 and below, one at 60 and below. */
    public static int synopsisLines(int heroHeightPct) {
        return heroHeightPct <= COMPACT ? 1 : heroHeightPct <= 80 ? 2 : 3;
    }

    /** The carousel's rating chips: left out at 60 and below, where the one synopsis line needs the room. */
    public static boolean chipsShown(int heroHeightPct) {
        return heroHeightPct > COMPACT;
    }

    /**
     * The least top padding the grid needs so the shelf's bottom (its dots) stays {@code gapPx} above the first row:
     * the shelf's top at its minimum ({@code shelfMinTopPx}, under the header) plus its measured height and the
     * gap, less where the grid starts ({@code gridTopPx}), all in the root's px. 0 when the shelf fits anyway, so the
     * caller takes the larger of this and the line hero_height asks for.
     */
    public static int floorPx(int shelfMinTopPx, int shelfHeightPx, int gapPx, int gridTopPx) {
        if (shelfHeightPx <= 0) return 0;   // not measured yet: nothing to keep the rows under
        return Math.max(0, shelfMinTopPx + shelfHeightPx + gapPx - gridTopPx);
    }
}
