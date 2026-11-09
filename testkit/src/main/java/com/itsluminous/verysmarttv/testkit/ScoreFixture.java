package com.itsluminous.verysmarttv.testkit;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

/**
 * The {@code /pill} rows the launcher's tests script into {@link FakeScoreProvider} (DESIGN.md §14.3), one per state
 * the pill renders, named so a test in another process can ask the stand-in for one by name through
 * {@code call(}{@link #METHOD_FIXTURE}{@code , name)}. {@link #BACKUP_JSON} is what {@code /backup} answers until a
 * restore replaces it.
 */
public final class ScoreFixture {
    private ScoreFixture() {}

    /** {@code call()} method of the stand-in that switches the pill row: the arg is one of the names below. */
    public static final String METHOD_FIXTURE = "fixture";
    /** {@code call()} method of the stand-in that answers the last JSON a restore delivered, under {@link ScoreContract#EXTRA_JSON}. */
    public static final String METHOD_RESTORED = "restored";

    public static final String LIVE = "live", BREAK = "break", UPCOMING = "upcoming", FINAL = "final", EMPTY = "empty";

    /** The event id every non-empty fixture carries: what OK on the pill must hand the scoreboard. */
    public static final String EVENT_ID = "fixture-event-1";

    public static final String LIVE_KICKER = "LIVE", LIVE_LINE = "T20I \u00b7 AFG 19/4 (6) v ZIM";
    public static final String BREAK_KICKER = "STUMPS", BREAK_LINE = "Test \u00b7 BAN 386 & 49/2 v WI";
    public static final String UPCOMING_KICKER = "19:30", UPCOMING_LINE = "ISL \u00b7 Bengaluru v Mumbai City";
    public static final String FINAL_KICKER = "FT", FINAL_LINE = "T20I \u00b7 AFG won by 23 runs";

    /** A follow list as the companion would store it; opaque to the launcher, carried verbatim by its backup. */
    public static final String BACKUP_JSON = "{\"version\":1,\"follows\":[{\"kind\":\"competition\",\"id\":\"ipl\"},{\"kind\":\"team\",\"id\":\"afg\"}]}";

    /** The row for {@code name}; {@link #EMPTY} (or an unknown name) is the companion's none row. */
    @NonNull
    public static Object[] row(@NonNull String name) {
        long now = System.currentTimeMillis();
        switch (name) {
            case LIVE:
                return new Object[] {ScoreContract.STATE_LIVE, LIVE_KICKER, LIVE_LINE, EVENT_ID, ScoreContract.SPORT_CRICKET, "T20I", now, 0, "#E53935"};
            case BREAK:
                return new Object[] {ScoreContract.STATE_BREAK, BREAK_KICKER, BREAK_LINE, EVENT_ID, ScoreContract.SPORT_CRICKET, "Test", now, 0, "#FFB300"};
            case UPCOMING:
                return new Object[] {ScoreContract.STATE_UPCOMING, UPCOMING_KICKER, UPCOMING_LINE, EVENT_ID, ScoreContract.SPORT_FOOTBALL, "ISL", now, 0, "#9E9E9E"};
            case FINAL:
                return new Object[] {ScoreContract.STATE_FINAL, FINAL_KICKER, FINAL_LINE, EVENT_ID, ScoreContract.SPORT_CRICKET, "T20I", now, 0, "#9E9E9E"};
            default:
                return new Object[] {ScoreContract.STATE_NONE, "", "", null, "", "", 0L, 0, "#9E9E9E"};
        }
    }

    /** Seeds the fake's pill with the named row; the backup row is left as it is, so a switch keeps a restored list. */
    public static void seed(@NonNull String name) {
        FakeScoreProvider.seedPill(row(name));
    }

    /** The fresh stand-in: the live row and {@link #BACKUP_JSON}. */
    public static void seedFresh() {
        seed(LIVE);
        FakeScoreProvider.seedBackup(BACKUP_JSON);
    }
}
