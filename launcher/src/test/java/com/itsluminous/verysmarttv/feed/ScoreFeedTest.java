package com.itsluminous.verysmarttv.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;
import com.itsluminous.verysmarttv.testkit.FakeScoreProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * The pill's reader against the companion stand-in (DESIGN.md §14.2 ScoreFeedTest): the row's columns arrive on the
 * main thread; a none row and an absent provider both arrive as an empty list (read here as null); several rows
 * arrive in the companion's order, cut at MAX_ROWS; the observer's change is coalesced; the 60 s re-read is armed
 * only after a live or break row and only while started.
 */
@RunWith(RobolectricTestRunner.class)
public class ScoreFeedTest {
    private Context ctx;
    private Handler main;
    /** The first row of each delivery, or null for an empty one. */
    private final List<ScoreFeed.Row> rows = new ArrayList<>();
    /** Each delivery whole. */
    private final List<List<ScoreFeed.Row>> deliveries = new ArrayList<>();
    private int calls;
    private ScoreFeed feed;

    @Before
    public void aProviderAndAFeed() {
        ctx = RuntimeEnvironment.getApplication();
        main = new Handler(Looper.getMainLooper());
        FakeScoreProvider.reset();
        Robolectric.setupContentProvider(FakeScoreProvider.class, ScoreContract.AUTHORITY);
        feed = new ScoreFeed(ctx, main, list -> {
            rows.add(list.isEmpty() ? null : list.get(0));
            deliveries.add(list);
            calls++;
        });
    }

    @After
    public void stop() {
        feed.stop();
        FakeScoreProvider.reset();
    }

    /** Lets the query thread finish and the main looper deliver. */
    private void settle() {
        long end = System.currentTimeMillis() + 5_000;
        while (feed.loading && System.currentTimeMillis() < end) {
            Thread.yield();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aLiveRowArrivesWithItsColumnsAndArmsTheLiveRead() {
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "T20I \u00b7 AFG 19/4 (6) v ZIM", "ev1", ScoreContract.SPORT_CRICKET, 1_000L));
        feed.start();
        settle();
        assertEquals(1, calls);
        ScoreFeed.Row row = rows.get(0);
        assertNotNull(row);
        assertEquals(ScoreContract.STATE_LIVE, row.state);
        assertEquals("LIVE", row.kicker);
        assertEquals("T20I \u00b7 AFG 19/4 (6) v ZIM", row.line);
        assertEquals("ev1", row.eventId);
        assertEquals(ScoreContract.SPORT_CRICKET, row.sport);
        assertEquals("#E53935", row.color);
        assertFalse(row.stale);
        assertTrue(row.inPlay());
        assertTrue("a live row arms the 60 s re-read", feed.liveReadArmed());
        assertEquals(1, FakeScoreProvider.pillQueryCount());
    }

    @Test
    public void severalRowsArriveInOrderCutAtMaxRowsAndANoneRowAmongThemIsDropped() {
        List<Object[]> seeded = new ArrayList<>();
        for (int i = 1; i <= ScoreContract.MAX_ROWS + 2; i++) {
            seeded.add(FakeScoreProvider.pill(i == 1 ? ScoreContract.STATE_FINAL : ScoreContract.STATE_UPCOMING, "FT", "line " + i, "ev" + i, ScoreContract.SPORT_CRICKET, 1_000L));
        }
        seeded.add(2, new Object[] {ScoreContract.STATE_NONE, "", "", null, "", "", 0L, 0, "#9E9E9E"});
        FakeScoreProvider.seedPills(seeded);
        feed.start();
        settle();
        assertEquals(1, calls);
        List<ScoreFeed.Row> got = deliveries.get(0);
        assertEquals(ScoreContract.MAX_ROWS, got.size());
        assertEquals("ev1", got.get(0).eventId);
        assertEquals("the none row is skipped, the order kept", "ev3", got.get(2).eventId);
        assertEquals("ev5", got.get(got.size() - 1).eventId);
        assertFalse("no row in play: nothing re-read", feed.liveReadArmed());
    }

    @Test
    public void aLiveRowAnywhereInTheListArmsTheLiveRead() {
        List<Object[]> seeded = new ArrayList<>();
        seeded.add(FakeScoreProvider.pill(ScoreContract.STATE_UPCOMING, "19:30", "a", "ev1", ScoreContract.SPORT_FOOTBALL, 1_000L));
        seeded.add(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "b", "ev2", ScoreContract.SPORT_CRICKET, 1_000L));
        FakeScoreProvider.seedPills(seeded);
        feed.start();
        settle();
        assertTrue(feed.liveReadArmed());
    }

    @Test
    public void aNoneRowArrivesAsNullAndArmsNothing() {
        feed.start();
        settle();
        assertEquals(1, calls);
        assertNull("state none: nothing to show", rows.get(0));
        assertFalse(feed.liveReadArmed());
    }

    @Test
    public void anUpcomingRowDoesNotArmTheLiveRead() {
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_UPCOMING, "19:30", "ISL \u00b7 Bengaluru v Mumbai City", "ev2", ScoreContract.SPORT_FOOTBALL, 1_000L));
        feed.start();
        settle();
        assertNotNull(rows.get(0));
        assertFalse(rows.get(0).inPlay());
        assertFalse(feed.liveReadArmed());
    }

    @Test
    public void aStaleFlagComesThrough() {
        Object[] row = FakeScoreProvider.pill(ScoreContract.STATE_BREAK, "STUMPS", "Test \u00b7 BAN 386 & 49/2 v WI", "ev3", ScoreContract.SPORT_CRICKET, 1_000L);
        row[7] = 1;
        FakeScoreProvider.seedPill(row);
        feed.start();
        settle();
        assertTrue(rows.get(0).stale);
        assertTrue("a break counts as in play", rows.get(0).inPlay());
        assertTrue(feed.liveReadArmed());
    }

    @Test
    public void theLiveReadRepeatsAfterSixtySeconds() {
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "line", "ev1", ScoreContract.SPORT_CRICKET, 1_000L));
        feed.start();
        settle();
        assertEquals(1, FakeScoreProvider.pillQueryCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScoreFeed.LIVE_PERIOD_MS - 1));
        settle();
        assertEquals("not before the minute", 1, FakeScoreProvider.pillQueryCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2));
        settle();
        assertEquals("the minute's re-read", 2, FakeScoreProvider.pillQueryCount());
        assertEquals(2, calls);
    }

    @Test
    public void stopDisarmsTheLiveRead() {
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "line", "ev1", ScoreContract.SPORT_CRICKET, 1_000L));
        feed.start();
        settle();
        assertTrue(feed.liveReadArmed());
        feed.stop();
        assertFalse(feed.liveReadArmed());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScoreFeed.LIVE_PERIOD_MS + 1));
        settle();
        assertEquals("nothing is read while stopped", 1, FakeScoreProvider.pillQueryCount());
    }

    @Test
    public void aChangeNotificationIsCoalescedIntoOneRead() {
        feed.start();
        settle();
        assertEquals(1, FakeScoreProvider.pillQueryCount());
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "line", "ev1", ScoreContract.SPORT_CRICKET, 1_000L));
        ctx.getContentResolver().notifyChange(ScoreContract.PILL, null);
        ctx.getContentResolver().notifyChange(ScoreContract.PILL, null);
        ctx.getContentResolver().notifyChange(ScoreContract.PILL, null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("not yet: the change waits COALESCE_MS", 1, FakeScoreProvider.pillQueryCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScoreFeed.COALESCE_MS + 1));
        settle();
        assertEquals("three notifications, one read", 2, FakeScoreProvider.pillQueryCount());
        assertEquals(ScoreContract.STATE_LIVE, rows.get(1).state);
    }

    /** A companion signed with another key: every query throws SecurityException. */
    public static class DenyingProvider extends FakeScoreProvider {
        @Override
        public android.database.Cursor query(@androidx.annotation.NonNull android.net.Uri uri, String[] p, String s, String[] a, String o) {
            throw new SecurityException("Permission Denial: opening provider");
        }
    }

    @Test
    public void aRefusalArrivesAsNullOnceAndEndsTheReads() {
        ShadowContentResolver.reset();
        Robolectric.setupContentProvider(DenyingProvider.class, ScoreContract.AUTHORITY);
        feed.start();
        settle();
        assertEquals(1, calls);
        assertNull(rows.get(0));
        assertFalse(feed.liveReadArmed());
        assertTrue("the refusal is remembered", feed.denied);
        // A change notification and the live timer read nothing more this session: no retry loop.
        ctx.getContentResolver().notifyChange(ScoreContract.PILL, null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ScoreFeed.LIVE_PERIOD_MS + ScoreFeed.COALESCE_MS));
        settle();
        assertEquals("no second delivery", 1, calls);
        // The next start (onResume) tries once more.
        feed.stop();
        feed.start();
        settle();
        assertEquals(2, calls);
    }

    @Test
    public void anAbsentProviderArrivesAsNull() {
        // No provider answers the authority: the companion is not installed.
        ShadowContentResolver.reset();
        feed.start();
        settle();
        assertEquals(1, calls);
        assertNull(rows.get(0));
        assertFalse(feed.liveReadArmed());
    }
}
