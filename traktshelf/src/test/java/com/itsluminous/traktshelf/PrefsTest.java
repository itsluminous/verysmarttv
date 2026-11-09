package com.itsluminous.traktshelf;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The companion's settings as the provider reads them on every query (Prefs, DESIGN.md §14.2). */
@RunWith(RobolectricTestRunner.class)
public class PrefsTest {
    private Context ctx;

    @Before
    public void context() {
        ctx = RuntimeEnvironment.getApplication();
        Prefs.get(ctx).edit().clear().commit();
    }

    @Test
    public void everyRowButRecentlyWatchedIsTickedByDefault() {
        Set<String> rows = Prefs.heroRows(ctx);
        assertEquals(new HashSet<>(Arrays.asList(Feed.UP_NEXT, Feed.REC_SHOWS, Feed.REC_MOVIES, Feed.WATCHLIST_SHOWS,
                Feed.WATCHLIST_MOVIES, Feed.TRENDING)), rows);
        assertFalse(rows.contains(Feed.HISTORY));
    }

    @Test
    public void savedRowsComeBackInRowOrder() {
        Prefs.setHeroRows(ctx, new HashSet<>(Arrays.asList(Feed.HISTORY, Feed.UP_NEXT, "not_a_row")));
        assertEquals(Arrays.asList(Feed.UP_NEXT, Feed.HISTORY), new ArrayList<>(Prefs.heroRows(ctx)));
    }

    @Test
    public void theOldWatchlistTickStandsForBothWatchlistRows() {
        Prefs.setHeroRows(ctx, new HashSet<>(Arrays.asList(Feed.LEGACY_WATCHLIST, Feed.TRENDING)));
        assertEquals(Arrays.asList(Feed.WATCHLIST_SHOWS, Feed.WATCHLIST_MOVIES, Feed.TRENDING), new ArrayList<>(Prefs.heroRows(ctx)));
    }

    @Test
    public void itemCountIsOneOfTheChoices() {
        assertEquals(Prefs.HERO_ITEMS_DEFAULT, Prefs.heroItems(ctx));
        for (int n : Prefs.HERO_ITEM_CHOICES) {
            Prefs.setHeroItems(ctx, n);
            assertEquals(n, Prefs.heroItems(ctx));
        }
        Prefs.setHeroItems(ctx, 7);
        assertEquals("a value outside the choices falls back", Prefs.HERO_ITEMS_DEFAULT, Prefs.heroItems(ctx));
    }

    @Test
    public void watchlistOrderIsAddedUnlessReleaseIsSaved() {
        assertEquals(Prefs.ORDER_ADDED, Prefs.watchlistOrder(ctx));
        Prefs.setWatchlistOrder(ctx, Prefs.ORDER_RELEASE);
        assertEquals(Prefs.ORDER_RELEASE, Prefs.watchlistOrder(ctx));
        Prefs.setWatchlistOrder(ctx, "random");
        assertEquals(Prefs.ORDER_ADDED, Prefs.watchlistOrder(ctx));
        assertTrue(Prefs.get(ctx).contains(Prefs.KEY_WATCHLIST_ORDER));
    }
}
