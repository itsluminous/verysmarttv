package com.itsluminous.verysmarttv.testkit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class FakeScoreProviderTest {
    private ContentResolver resolver;

    @Before
    public void setUp() {
        Robolectric.setupContentProvider(FakeScoreProvider.class, ScoreContract.AUTHORITY);
        resolver = RuntimeEnvironment.getApplication().getContentResolver();
    }

    @After
    public void tearDown() {
        FakeScoreProvider.reset();
    }

    @Test
    public void freshCompanionAnswersNoneEmptyAndEmptyBackup() {
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            assertEquals(1, c.getCount());
            assertArrayEquals(ScoreContract.PILL_COLUMNS, c.getColumnNames());
            c.moveToFirst();
            assertEquals(ScoreContract.STATE_NONE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)));
            assertTrue(c.isNull(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)));
        }
        try (Cursor c = resolver.query(ScoreContract.EVENTS, null, null, null, null)) {
            assertEquals(0, c.getCount());
            assertArrayEquals(ScoreContract.EVENT_COLUMNS, c.getColumnNames());
        }
        try (Cursor c = resolver.query(ScoreContract.BACKUP, null, null, null, null)) {
            c.moveToFirst();
            assertEquals("{}", c.getString(c.getColumnIndexOrThrow(ScoreContract.JSON)));
        }
        assertEquals(1, FakeScoreProvider.pillQueryCount());
    }

    @Test
    public void seededPillComesBackVerbatim() {
        FakeScoreProvider.seedPill(FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "T20I · AFG 19/4 (6) v ZIM",
                "401", ScoreContract.SPORT_CRICKET, 1_700_000_000_000L));
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            c.moveToFirst();
            assertEquals(ScoreContract.STATE_LIVE, c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE)));
            assertEquals("LIVE", c.getString(c.getColumnIndexOrThrow(ScoreContract.KICKER)));
            assertEquals("T20I · AFG 19/4 (6) v ZIM", c.getString(c.getColumnIndexOrThrow(ScoreContract.LINE)));
            assertEquals("401", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)));
            assertEquals(1_700_000_000_000L, c.getLong(c.getColumnIndexOrThrow(ScoreContract.UPDATED_MS)));
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow(ScoreContract.STALE)));
        }
    }

    @Test
    public void seededPillsComeBackAsOneRowEachInOrder() {
        FakeScoreProvider.seedPills(Arrays.asList(
                FakeScoreProvider.pill(ScoreContract.STATE_LIVE, "LIVE", "a", "401", ScoreContract.SPORT_CRICKET, 1L),
                FakeScoreProvider.pill(ScoreContract.STATE_UPCOMING, "19:30", "b", "402", ScoreContract.SPORT_FOOTBALL, 1L)));
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            assertEquals(2, c.getCount());
            c.moveToLast();
            assertEquals("402", c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)));
        }
        FakeScoreProvider.reset();
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            assertEquals("back to the one none row", 1, c.getCount());
        }
    }

    @Test
    public void theStackFixtureSeedsFiveRowsWithDistinctEventIdsLiveFirst() {
        ScoreFixture.seed(ScoreFixture.STACK);
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            assertEquals(5, c.getCount());
            java.util.Set<String> ids = new java.util.HashSet<>();
            String[] states = new String[5];
            for (int i = 0; c.moveToNext(); i++) {
                ids.add(c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)));
                states[i] = c.getString(c.getColumnIndexOrThrow(ScoreContract.STATE));
                assertEquals(ScoreFixture.STACK_EVENT_IDS[i], c.getString(c.getColumnIndexOrThrow(ScoreContract.EVENT_ID)));
            }
            assertEquals("five distinct events", 5, ids.size());
            assertArrayEquals(new String[] {"live", "live", "break", "upcoming", "final"}, states);
        }
        ScoreFixture.seed(ScoreFixture.LIVE);
        try (Cursor c = resolver.query(ScoreContract.PILL, null, null, null, null)) {
            assertEquals("a one-row fixture replaces the stack", 1, c.getCount());
        }
    }

    @Test
    public void eventsAndSingleEventLookup() {
        Object[] a = event("401", "IPL", "CSK", "MI");
        Object[] b = event("402", "ISL", "BFC", "MCFC");
        FakeScoreProvider.seedEvents(Arrays.asList(a, b));
        try (Cursor c = resolver.query(ScoreContract.EVENTS, null, null, null, null)) {
            assertEquals(2, c.getCount());
        }
        try (Cursor c = resolver.query(ScoreContract.event("402"), null, null, null, null)) {
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals("ISL", c.getString(c.getColumnIndexOrThrow(ScoreContract.COMPETITION)));
        }
        try (Cursor c = resolver.query(ScoreContract.event("nope"), null, null, null, null)) {
            assertEquals(0, c.getCount());
        }
    }

    @Test
    public void mimeTypesFollowTheContract() {
        assertEquals(ScoreContract.TYPE_PILL, resolver.getType(ScoreContract.PILL));
        assertEquals(ScoreContract.TYPE_EVENTS, resolver.getType(ScoreContract.EVENTS));
        assertEquals(ScoreContract.TYPE_EVENT, resolver.getType(ScoreContract.event("1")));
        assertEquals(ScoreContract.TYPE_BACKUP, resolver.getType(ScoreContract.BACKUP));
        assertNull(resolver.getType(Uri.parse("content://" + ScoreContract.AUTHORITY + "/other")));
        assertNull(resolver.query(Uri.parse("content://" + ScoreContract.AUTHORITY + "/other"), null, null, null, null));
    }

    @Test
    public void restoreStoresTheJsonAndNotifiesThePill() {
        AtomicInteger changes = new AtomicInteger();
        resolver.registerContentObserver(ScoreContract.PILL, false, new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                changes.incrementAndGet();
            }
        });
        Bundle extras = new Bundle();
        extras.putString(ScoreContract.EXTRA_JSON, "{\"follows\":[1]}");
        Bundle result = resolver.call(ScoreContract.BACKUP, ScoreContract.METHOD_RESTORE, null, extras);
        ShadowLooper.idleMainLooper();

        assertTrue(result.getBoolean(ScoreContract.RESULT_OK));
        assertEquals(Collections.singletonList("{\"follows\":[1]}"), FakeScoreProvider.restored());
        assertEquals(1, changes.get());
        try (Cursor c = resolver.query(ScoreContract.BACKUP, null, null, null, null)) {
            c.moveToFirst();
            assertEquals("{\"follows\":[1]}", c.getString(0));
        }
    }

    @Test
    public void restoreWithoutJsonOrAnUnknownMethodFails() {
        Bundle noJson = resolver.call(ScoreContract.BACKUP, ScoreContract.METHOD_RESTORE, null, new Bundle());
        assertFalse(noJson.getBoolean(ScoreContract.RESULT_OK));
        assertTrue(noJson.getString(ScoreContract.RESULT_ERROR).contains(ScoreContract.EXTRA_JSON));

        Bundle unknown = resolver.call(ScoreContract.BACKUP, "wipe", null, null);
        assertFalse(unknown.getBoolean(ScoreContract.RESULT_OK));
        assertTrue(FakeScoreProvider.restored().isEmpty());
    }

    private static Object[] event(String id, String competition, String home, String away) {
        return new Object[] {
            Long.parseLong(id), id, ScoreContract.SPORT_CRICKET, competition, "T20", ScoreContract.STATE_LIVE, "", 0L, null,
            home, home, "0/0", away, away, "", "", ScoreContract.REASON_COMPETITION, 0L,
        };
    }
}
