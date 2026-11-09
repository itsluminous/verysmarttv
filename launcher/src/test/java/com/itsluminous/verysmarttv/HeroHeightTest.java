package com.itsluminous.verysmarttv;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * hero_height: the Launcher settings choice over the theme's key, the theme's 0 (auto) following the carousel, and the
 * tile fit's title room scaling with it; and the Trakt recommendations toggle (hero_feed) over the theme's key.
 */
public class HeroHeightTest {

    private static final float D = 2f;
    private static final int INNER = 1696, GRID_H = 846;

    @Test
    public void aSavedChoiceInRangeOverridesTheTheme() {
        assertEquals(60, Config.chosenHeroHeight(60, 100, true));
        assertEquals(120, Config.chosenHeroHeight(120, 80, false));
        assertEquals(50, Config.chosenHeroHeight(Config.HERO_HEIGHT_MIN, 100, true));
        assertEquals(130, Config.chosenHeroHeight(Config.HERO_HEIGHT_MAX, 100, true));
        // A saved choice wins over the auto rule too, whatever the carousel does.
        assertEquals(60, Config.chosenHeroHeight(60, Config.HERO_HEIGHT_AUTO, true));
        assertEquals(120, Config.chosenHeroHeight(120, Config.HERO_HEIGHT_AUTO, false));
    }

    @Test
    public void noChoiceOrAnOutOfRangeOneLeavesTheTheme() {
        assertEquals(80, Config.chosenHeroHeight(-1, 80, true));
        assertEquals(100, Config.chosenHeroHeight(0, 100, false));
        assertEquals(100, Config.chosenHeroHeight(49, 100, false));
        assertEquals(100, Config.chosenHeroHeight(131, 100, false));
        assertEquals(70, Config.chosenHeroHeight(-1, 70, false));   // a named theme height ignores the carousel
    }

    @Test
    public void theThemesAutoFollowsTheCarousel() {
        assertEquals(0, Config.HERO_HEIGHT_AUTO);
        assertEquals(100, Config.HERO_HEIGHT_WITH_FEED);
        assertEquals(80, Config.HERO_HEIGHT_WITHOUT_FEED);
        assertEquals(100, Config.autoHeroHeight(true));
        assertEquals(80, Config.autoHeroHeight(false));
        assertEquals(100, Config.chosenHeroHeight(-1, Config.HERO_HEIGHT_AUTO, true));
        assertEquals(80, Config.chosenHeroHeight(-1, Config.HERO_HEIGHT_AUTO, false));
        // An out-of-range saved choice is no choice: auto still decides.
        assertEquals(80, Config.chosenHeroHeight(49, Config.HERO_HEIGHT_AUTO, false));
        assertEquals(100, Config.chosenHeroHeight(131, Config.HERO_HEIGHT_AUTO, true));
        // Normal in the panel is the feed's auto height, Short the shelf's without it.
        assertEquals(SettingsPanel.HERO_HEIGHTS[2], Config.HERO_HEIGHT_WITH_FEED);
        assertEquals(SettingsPanel.HERO_HEIGHTS[1], Config.HERO_HEIGHT_WITHOUT_FEED);
    }

    @Test
    public void aThemeKeyIsAutoOrInRange() {
        assertEquals(0, Config.heroHeightKey(0, 100));
        assertEquals(60, Config.heroHeightKey(60, 0));
        assertEquals(50, Config.heroHeightKey(50, 0));
        assertEquals(130, Config.heroHeightKey(130, 0));
        assertEquals(0, Config.heroHeightKey(49, 0));      // out of range: the default stays
        assertEquals(0, Config.heroHeightKey(131, 0));
        assertEquals(0, Config.heroHeightKey(-1, 0));
        assertEquals(100, Config.heroHeightKey(1, 100));
    }

    @Test
    public void theTraktToggleOverridesTheThemesHeroFeed() {
        // 1 on, 0 off, unset (-1) leaves the theme's key.
        assertTrue(Config.chosenHeroFeed(1, false));
        assertTrue(Config.chosenHeroFeed(1, true));
        assertFalse(Config.chosenHeroFeed(0, true));
        assertFalse(Config.chosenHeroFeed(0, false));
        assertTrue(Config.chosenHeroFeed(-1, true));
        assertFalse(Config.chosenHeroFeed(-1, false));
        // Anything else saved is no choice.
        assertTrue(Config.chosenHeroFeed(7, true));
        assertFalse(Config.chosenHeroFeed(7, false));
    }

    @Test
    public void theToggleDecidesTheAutoHeight() {
        // mooq1-frosted: hero_feed true, no hero_height. The theme gives 100; Off in the panel makes it 80.
        boolean feed = Config.chosenHeroFeed(-1, true);
        assertEquals(100, Config.chosenHeroHeight(-1, Config.HERO_HEIGHT_AUTO, feed));
        feed = Config.chosenHeroFeed(0, true);
        assertEquals(80, Config.chosenHeroHeight(-1, Config.HERO_HEIGHT_AUTO, feed));
        // A theme without the carousel, turned on from the panel: 100.
        feed = Config.chosenHeroFeed(1, false);
        assertEquals(100, Config.chosenHeroHeight(-1, Config.HERO_HEIGHT_AUTO, feed));
    }

    @Test
    public void thePanelOffersTheFourLevelsInRange() {
        int[] levels = SettingsPanel.HERO_HEIGHTS;
        assertEquals(4, levels.length);
        for (int pct : levels) {
            assertTrue(pct >= Config.HERO_HEIGHT_MIN && pct <= Config.HERO_HEIGHT_MAX);
            assertEquals(pct, Config.chosenHeroHeight(pct, 100, true));
        }
        assertEquals(100, levels[2]);   // Normal is the layout's own
    }

    @Test
    public void thePickerMarksTheHeightInForceAndAddsAThemesOddOne() {
        // The height in force is one of the four: the picker is the four, and that one is marked. No "Theme default" row.
        assertArrayEquals(new int[]{60, 80, 100, 120}, SettingsPanel.heroHeightChoices(100));
        assertEquals(2, SettingsPanel.indexOf(SettingsPanel.heroHeightChoices(100), 100));
        assertEquals(1, SettingsPanel.indexOf(SettingsPanel.heroHeightChoices(80), 80));
        // mooq1-frosted, no saved choice: the feed on gives 100 (Normal), off 80 (Short).
        assertEquals(2, SettingsPanel.indexOf(SettingsPanel.heroHeightChoices(Config.chosenHeroHeight(-1, 0, true)), 100));
        assertEquals(1, SettingsPanel.indexOf(SettingsPanel.heroHeightChoices(Config.chosenHeroHeight(-1, 0, false)), 80));
        // A theme's hero_height outside the four joins the list in order of size, so the marked row is always shown.
        assertArrayEquals(new int[]{60, 70, 80, 100, 120}, SettingsPanel.heroHeightChoices(70));
        assertArrayEquals(new int[]{50, 60, 80, 100, 120}, SettingsPanel.heroHeightChoices(50));
        assertArrayEquals(new int[]{60, 80, 100, 120, 130}, SettingsPanel.heroHeightChoices(130));
        assertEquals(4, SettingsPanel.indexOf(SettingsPanel.heroHeightChoices(130), 130));
        // The list handed out is a copy: a caller cannot reorder the panel's levels.
        int[] copy = SettingsPanel.heroHeightChoices(100);
        copy[0] = 1;
        assertEquals(60, SettingsPanel.HERO_HEIGHTS[0]);
        assertEquals(-1, SettingsPanel.indexOf(SettingsPanel.HERO_HEIGHTS, 70));
    }

    @Test
    public void aShorterShelfReservesLessTitleRoomSoTilesStayLarger() {
        // Two rows under a titled dock at 4 columns: at 100 the tiles sit on the 85% floor; a 60% shelf frees 42dp
        // of title room, so the fit can give them more width, and never less.
        int at100 = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D, (int) AppAdapter.ITEM_EXTRA_DP, 100);
        int at60 = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D, (int) AppAdapter.ITEM_EXTRA_DP, 60);
        int at120 = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D, (int) AppAdapter.ITEM_EXTRA_DP, 120);
        assertTrue(at60 >= at100);
        assertTrue(at120 <= at100);
        // The two-argument-shorter overload is the 100 case.
        assertEquals(at100, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, true, true, D, (int) AppAdapter.ITEM_EXTRA_DP));
    }
}
