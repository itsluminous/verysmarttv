package com.itsluminous.verysmarttv.core.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ScoreContractTest {
    @Test
    public void authorityAndPermissionsAreUnderTheCompanionPackage() {
        assertEquals("com.itsluminous.livescore", ScoreContract.PACKAGE);
        assertEquals("com.itsluminous.livescore.scores", ScoreContract.AUTHORITY);
        assertEquals("com.itsluminous.livescore.permission.READ_SCORES", ScoreContract.PERMISSION_READ);
        assertEquals("com.itsluminous.livescore.permission.WRITE_SCORES", ScoreContract.PERMISSION_WRITE);
    }

    @Test
    public void urisFollowTheDesign() {
        assertEquals("content://com.itsluminous.livescore.scores/pill", ScoreContract.PILL.toString());
        assertEquals("content://com.itsluminous.livescore.scores/events", ScoreContract.EVENTS.toString());
        assertEquals("content://com.itsluminous.livescore.scores/backup", ScoreContract.BACKUP.toString());
    }

    @Test
    public void eventUriEncodesTheFeedId() {
        Uri uri = ScoreContract.event("401584632");
        assertEquals("content://com.itsluminous.livescore.scores/events/401584632", uri.toString());
        assertEquals("401584632", uri.getLastPathSegment());
        Uri odd = ScoreContract.event("a/b c");
        assertEquals("a/b c", odd.getLastPathSegment());
        assertEquals(2, odd.getPathSegments().size());
    }

    @Test
    public void mimeTypesUseTheVndConvention() {
        assertEquals("vnd.android.cursor.item/vnd.com.itsluminous.livescore.pill", ScoreContract.TYPE_PILL);
        assertEquals("vnd.android.cursor.dir/vnd.com.itsluminous.livescore.event", ScoreContract.TYPE_EVENTS);
        assertEquals("vnd.android.cursor.item/vnd.com.itsluminous.livescore.event", ScoreContract.TYPE_EVENT);
        assertEquals("vnd.android.cursor.item/vnd.com.itsluminous.livescore.backup", ScoreContract.TYPE_BACKUP);
    }

    @Test
    public void restoreCallNames() {
        assertEquals("restore", ScoreContract.METHOD_RESTORE);
        assertEquals("json", ScoreContract.EXTRA_JSON);
        assertEquals(Arrays.asList("json"), Arrays.asList(ScoreContract.BACKUP_COLUMNS));
    }

    @Test
    public void pillColumnsAreTheNineOfTheDesign() {
        List<String> expected = Arrays.asList("state", "kicker", "line", "event_id", "sport", "competition", "updated_ms", "stale", "color");
        assertEquals(expected, Arrays.asList(ScoreContract.PILL_COLUMNS));
        assertNoDuplicates(ScoreContract.PILL_COLUMNS);
    }

    @Test
    public void eventColumnsAreTheEighteenOfTheDesign() {
        List<String> expected = Arrays.asList("_id", "event_id", "sport", "competition", "format", "state", "status_detail",
                "start_ms", "end_ms", "home_name", "home_abbr", "home_score", "away_name", "away_abbr", "away_score",
                "summary", "followed_reason", "updated_ms");
        assertEquals(expected, Arrays.asList(ScoreContract.EVENT_COLUMNS));
        assertNoDuplicates(ScoreContract.EVENT_COLUMNS);
    }

    @Test
    public void statesSportsAndReasonsAreDistinctLowercaseTokens() {
        assertNoDuplicates(new String[] {ScoreContract.STATE_LIVE, ScoreContract.STATE_BREAK, ScoreContract.STATE_UPCOMING,
                ScoreContract.STATE_FINAL, ScoreContract.STATE_NONE});
        assertNoDuplicates(ScoreContract.SPORTS);
        assertNoDuplicates(new String[] {ScoreContract.REASON_TEAM, ScoreContract.REASON_COMPETITION, ScoreContract.REASON_EVENT});
        for (String s : new String[] {ScoreContract.STATE_LIVE, ScoreContract.SPORT_F1, ScoreContract.REASON_EVENT}) {
            assertTrue(s, s.equals(s.toLowerCase(java.util.Locale.ROOT)));
        }
        for (String s : ScoreContract.SPORTS) assertTrue(s, s.equals(s.toLowerCase(java.util.Locale.ROOT)));
    }

    @Test
    public void sportsAreTheSevenOfTheDesign() {
        List<String> expected = Arrays.asList("cricket", "football", "f1", "tennis", "badminton", "basketball", "hockey");
        assertEquals(expected, Arrays.asList(ScoreContract.SPORTS));
        assertEquals(ScoreContract.SPORT_CRICKET, ScoreContract.SPORTS[0]);
        assertEquals(ScoreContract.SPORT_BADMINTON, "badminton");
        assertEquals(ScoreContract.SPORT_HOCKEY, "hockey");
    }

    @Test
    public void lineLimitIsFortyEight() {
        assertEquals(48, ScoreContract.LINE_MAX_CHARS);
    }

    @Test
    public void aLauncherShowsAtMostFivePillRows() {
        assertEquals(5, ScoreContract.MAX_ROWS);
    }

    private static void assertNoDuplicates(String[] values) {
        assertEquals(Arrays.toString(values), values.length, new HashSet<>(Arrays.asList(values)).size());
    }
}
