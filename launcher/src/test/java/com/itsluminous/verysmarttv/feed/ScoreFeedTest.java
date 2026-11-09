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
 * main thread; a none row and an absent provider both arrive as null; the observer's change is coalesced; the 60 s
 * re-read is armed only after a live or break row and only while started.
 */
@RunWith(RobolectricTestRunner.class)
public class ScoreFeedTest {
    private Context ctx;
    private Handler main;
    private final List<ScoreFeed.Row> rows = new ArrayList<>();
    private int calls;
    private ScoreFeed feed;

    @Before
    public void aProviderAndAFeed() {
        ctx = RuntimeEnvironment.getApplication();
        main = new Handler(Looper.getMainLooper());
        FakeScoreProvider.reset();
        Robolectric.setupContentProvider(FakeScoreProvider.class, ScoreContract.AUTHORITY);
        feed = new ScoreFeed(ctx, main, row -> {
            rows.add(row);
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
        assertEquals("#E53935", row.color);
        assertFalse(row.stale);
        assertTrue(row.inPlay());
        assertTrue("a live row arms the 60 s re-read", feed.liveReadArmed());
        assertEquals(1, FakeScoreProvider.pillQueryCount());
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
