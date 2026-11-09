package com.itsluminous.traktshelf;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The carousel order the provider serves (Feed.heroItems, DESIGN.md §14.2): the Up next episode of the show watched
 * most recently leads, the rest is a shuffle of the ticked rows seeded by the sync time, capped per row, each title
 * once, titles without a backdrop or not out yet left out; watched titles leave Trending and the recommendations.
 */
@RunWith(RobolectricTestRunner.class)
public class FeedTest {
    private Context ctx;
    private File img;

    @Before
    public void context() {
        ctx = RuntimeEnvironment.getApplication();
        img = Images.dir(ctx);
        Prefs.get(ctx).edit().clear().commit();
    }

    /** An item with a backdrop on disk, so it is eligible unless it says otherwise. */
    private Item item(String kind, long traktId, String title, String row) throws IOException {
        Item it = new Item();
        it.kind = kind;
        it.traktId = traktId;
        it.title = title;
        it.sourceRow = row;
        it.backdropUrl = "https://image.example/" + traktId;
        assertTrue(new File(img, it.backdropFile()).createNewFile() || new File(img, it.backdropFile()).isFile());
        return it;
    }

    private static Feed.Row row(Feed feed, String key, Item... items) {
        Feed.Row r = new Feed.Row(key);
        r.items.addAll(Arrays.asList(items));
        feed.rows.put(key, r);
        return r;
    }

    private static List<String> titles(List<Item> items) {
        List<String> out = new ArrayList<>();
        for (Item it : items) out.add(it.title);
        return out;
    }

    @Test
    public void upNextLeadsAndTheRestIsASeededShuffle() throws IOException {
        Feed feed = new Feed();
        feed.syncedAt = 1_700_000_000_000L;
        row(feed, Feed.UP_NEXT, item(Item.SHOW, 1, "Severance", Feed.UP_NEXT), item(Item.SHOW, 2, "Dark", Feed.UP_NEXT));
        row(feed, Feed.TRENDING, item(Item.MOVIE, 10, "Heat", Feed.TRENDING), item(Item.MOVIE, 11, "Dune", Feed.TRENDING),
                item(Item.MOVIE, 12, "Arrival", Feed.TRENDING));

        List<Item> first = feed.heroItems(ctx, 12);
        assertEquals("Severance", first.get(0).title);
        assertEquals(5, first.size());
        assertEquals(new HashSet<>(Arrays.asList("Severance", "Dark", "Heat", "Dune", "Arrival")), new HashSet<>(titles(first)));

        assertEquals("the same seed gives the same order", titles(first), titles(feed.heroItems(ctx, 12)));
        feed.syncedAt += 1;
        List<String> next = titles(feed.heroItems(ctx, 12));
        assertEquals("Severance", next.get(0));
        List<String> firstTail = titles(first).subList(1, 5), nextTail = next.subList(1, 5);
        assertEquals(new HashSet<>(firstTail), new HashSet<>(nextTail));
        // Four items: 24 orders, so a new seed almost always moves something; assert the shuffle is really seeded
        // by checking a third seed against both rather than demanding a difference from one in particular.
        feed.syncedAt += 1;
        List<String> third = titles(feed.heroItems(ctx, 12)).subList(1, 5);
        assertFalse(firstTail.equals(nextTail) && nextTail.equals(third));
    }

    @Test
    public void onlyTickedRowsFeedThePoolAndTheLeadIgnoresTheTicks() throws IOException {
        Feed feed = new Feed();
        row(feed, Feed.UP_NEXT, item(Item.SHOW, 1, "Severance", Feed.UP_NEXT));
        row(feed, Feed.TRENDING, item(Item.MOVIE, 10, "Heat", Feed.TRENDING));
        row(feed, Feed.HISTORY, item(Item.MOVIE, 20, "Seen", Feed.HISTORY));
        Prefs.setHeroRows(ctx, new HashSet<>(Arrays.asList(Feed.TRENDING)));   // Up next not ticked

        List<String> out = titles(feed.heroItems(ctx, 12));
        assertEquals(Arrays.asList("Severance", "Heat"), out);
    }

    @Test
    public void eachRowIsCappedAtHalfTheCountAndEachTitleAppearsOnce() throws IOException {
        Feed feed = new Feed();
        Item[] many = new Item[8];
        for (int i = 0; i < many.length; i++) many[i] = item(Item.MOVIE, 100 + i, "T" + i, Feed.TRENDING);
        row(feed, Feed.TRENDING, many);
        row(feed, Feed.REC_MOVIES, item(Item.MOVIE, 100, "T0 again", Feed.REC_MOVIES), item(Item.MOVIE, 200, "Rec", Feed.REC_MOVIES));

        List<Item> out = feed.heroItems(ctx, 6);
        // Rows are walked in Feed.ROW_ORDER: Recommended movies first (both items), then Trending, capped at
        // ceil(6 / 2) = 3, where T0 is skipped because its key (movie:100) is already in as "T0 again".
        assertEquals(5, out.size());
        int trending = 0;
        for (Item it : out) {
            assertFalse("T0".equals(it.title));
            if (Feed.TRENDING.equals(it.sourceRow)) trending++;
        }
        assertEquals(3, trending);
        assertEquals(0, feed.heroItems(ctx, 0).size());
    }

    @Test
    public void upcomingTitlesAndTitlesWithoutABackdropAreLeftOut() throws IOException {
        Feed feed = new Feed();
        Item aired = item(Item.SHOW, 1, "Aired", Feed.UP_NEXT);
        Item notYet = item(Item.SHOW, 2, "Next week", Feed.UP_NEXT);
        notYet.nextAired = System.currentTimeMillis() + 7L * 86_400_000L;
        Item noArt = new Item();
        noArt.kind = Item.MOVIE; noArt.traktId = 3; noArt.title = "No art"; noArt.sourceRow = Feed.TRENDING;
        Item future = item(Item.MOVIE, 4, "Future film", Feed.TRENDING);
        future.released = System.currentTimeMillis() + 30L * 86_400_000L;
        row(feed, Feed.UP_NEXT, notYet, aired);
        row(feed, Feed.TRENDING, noArt, future);

        assertEquals(Arrays.asList("Aired"), titles(feed.heroItems(ctx, 12)));
    }

    @Test
    public void watchedTitlesLeaveTrendingAndRecommendationsButNotTheWatchlist() throws IOException {
        Feed feed = new Feed();
        Item heat = item(Item.MOVIE, 10, "Heat", Feed.TRENDING);
        Item dune = item(Item.MOVIE, 11, "Dune", Feed.TRENDING);
        Item recShow = item(Item.SHOW, 30, "Seen show", Feed.REC_SHOWS);
        Item listed = item(Item.MOVIE, 10, "Heat (watchlist)", Feed.WATCHLIST_MOVIES);
        row(feed, Feed.TRENDING, heat, dune);
        row(feed, Feed.REC_SHOWS, recShow);
        row(feed, Feed.WATCHLIST_MOVIES, listed);
        feed.watched.addAll(Arrays.asList(heat.key(), recShow.key()));

        List<String> gone = feed.hideWatched();
        assertEquals(new HashSet<>(Arrays.asList("Heat", "Seen show")), new HashSet<>(gone));
        assertEquals(Arrays.asList("Dune"), titles(feed.rows.get(Feed.TRENDING).items));
        assertTrue(feed.rows.get(Feed.REC_SHOWS).items.isEmpty());
        assertEquals(1, feed.rows.get(Feed.WATCHLIST_MOVIES).items.size());

        List<String> carousel = titles(feed.heroItems(ctx, 12));
        assertFalse(carousel.contains("Heat"));
        assertTrue(carousel.contains("Dune"));
        assertTrue(carousel.contains("Heat (watchlist)"));
    }
}
