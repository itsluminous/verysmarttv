package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * row_gap and the focused tile's name: AppAdapter lays the 22dp label out so that, slid down by its 4dp focus motion,
 * it is centred between the tile's bottom and the next row's tile top, at every gap Config allows (29 to 90 dp), on
 * any TV density, while the item stays exactly tile + row_gap tall so the rows keep their pitch.
 */
public class RowGapNameTest {

    private static final float[] DENSITIES = {1f, 1.33f, 1.5f, 2f, 2.625f};
    private static final int[] TILE_HEIGHTS_DP = {64, 79, 101};   // 6 columns on 1080p, 5 columns, the design size

    @Test
    public void focusedNameIsCentredInTheGapAtEveryRowGap() {
        for (float d : DENSITIES) {
            for (int tileDp : TILE_HEIGHTS_DP) {
                int h = Math.round(tileDp * d);
                for (int gap = 29; gap <= 90; gap++) {
                    float centre = AppAdapter.focusedNameCentrePx(h, d, gap);
                    float want = gap * d / 2f;
                    assertTrue("row_gap " + gap + " density " + d + ": name centre " + centre + " px, gap centre " + want,
                            Math.abs(centre - want) <= 1f);
                }
            }
        }
    }

    @Test
    public void itemStaysTilePlusRowGapTall() {
        for (float d : DENSITIES) {
            for (int tileDp : TILE_HEIGHTS_DP) {
                int h = Math.round(tileDp * d);
                for (int gap = 29; gap <= 90; gap++) {
                    int[] pad = AppAdapter.itemPaddingPx(h, d, gap);
                    int nameBottom = AppAdapter.nameTopMarginPx(h, d, gap) + Math.round(AppAdapter.NAME_HEIGHT_DP * d);
                    assertTrue("row_gap " + gap + ": the label's box ends under the tile", nameBottom >= h);
                    int item = pad[0] + Math.max(h, nameBottom) + pad[1];   // what a wrap_content FrameLayout measures
                    assertEquals("row_gap " + gap + " density " + d, AppAdapter.itemHeightPx(h, true, d, gap), item);
                    assertTrue("row_gap " + gap + ": paddings stay positive", pad[0] >= 0 && pad[1] >= 0);
                    assertTrue("row_gap " + gap + ": the slid label ends inside the item",
                            nameBottom + Math.round(AppAdapter.NAME_SLIDE_DP * d) <= item);
                }
            }
        }
    }

    @Test
    public void theLayoutsOwnGapKeepsItemAppXmlsNumbers() {
        // item_app.xml: 8dp padding, the label 7dp under the tile, which the formula reproduces within half a dp (so
        // AppAdapter can keep the XML untouched at row_gap 45 and compute only for other gaps).
        assertEquals(7.5f, AppAdapter.nameTopDp(45), 0.001f);
        assertEquals(8, AppAdapter.itemPadTopDp(45));
        assertEquals(14f, AppAdapter.nameTopMarginPx(0, 2f, 45), 1f);   // 15 px against the XML's 14
        assertEquals(16, AppAdapter.itemPaddingPx(0, 2f, 45)[0]);
    }

    @Test
    public void theMooq1GapPutsTheLabelOneDpUnderTheTile() {
        // row_gap 32 on a 1080p TV (2 px/dp) with 64dp tiles: the fixed 7dp margin left the label's centre 5dp (10 px) low.
        int h = 128;
        assertEquals(h + 2, AppAdapter.nameTopMarginPx(h, 2f, 32));
        assertEquals(32f, AppAdapter.focusedNameCentrePx(h, 2f, 32), 0.5f);
        int[] pad = AppAdapter.itemPaddingPx(h, 2f, 32);
        assertEquals(2, pad[0]);                                   // 1dp, as before: the tiles do not move
        assertEquals(h + 64, pad[0] + h + 2 + 44 + pad[1]);
        assertTrue("the slid label (8 px) ends inside the item", pad[0] + h + 2 + 44 + 8 <= h + 64);
    }

    @Test
    public void theSmallestGapStillClearsTheTile() {
        // At 29dp the label's box starts half a dp above the tile's bottom; its 17dp of text does not reach the tile.
        assertEquals(-0.5f, AppAdapter.nameTopDp(29), 0.001f);
        assertTrue(AppAdapter.nameTopDp(29) + AppAdapter.NAME_SLIDE_DP + (AppAdapter.NAME_HEIGHT_DP - 17) / 2f > 0);
    }
}
