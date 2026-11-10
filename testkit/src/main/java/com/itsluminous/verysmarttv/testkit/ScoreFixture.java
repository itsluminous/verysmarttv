package com.itsluminous.verysmarttv.testkit;

import androidx.annotation.NonNull;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The {@code /pill} rows the launcher's tests script into {@link FakeScoreProvider} (DESIGN.md §14.3), one per state
 * the pill renders, named so a test in another process can ask the stand-in for one by name through
 * {@code call(}{@link #METHOD_FIXTURE}{@code , name)}; and {@link #STACK}, five events at once in the companion's
 * order, for the stacked pill (ADR-041). {@link #BACKUP_JSON} is what {@code /backup} answers until a restore replaces
 * it.
 */
public final class ScoreFixture {
    private ScoreFixture() {}

    /** {@code call()} method of the stand-in that switches the pill row: the arg is one of the names below. */
    public static final String METHOD_FIXTURE = "fixture";
    /** {@code call()} method of the stand-in that answers the last JSON a restore delivered, under {@link ScoreContract#EXTRA_JSON}. */
    public static final String METHOD_RESTORED = "restored";

    public static final String LIVE = "live", BREAK = "break", UPCOMING = "upcoming", FINAL = "final", EMPTY = "empty";
    /**
     * Five rows at once: {@link #LIVE}, a second live match, {@link #BREAK}, {@link #UPCOMING}, {@link #FINAL}; see
     * {@link #STACK_EVENT_IDS} and {@link #STACK_SPORTS} (four sports, so every line's icon can be told apart).
     */
    public static final String STACK = "stack";

    /** The event id every one-row fixture carries: what OK on the pill must hand the scoreboard. */
    public static final String EVENT_ID = "fixture-event-1";
    /** The event ids of the {@link #STACK} rows, top first; the first is {@link #EVENT_ID}. */
    public static final String[] STACK_EVENT_IDS = {EVENT_ID, "fixture-event-2", "fixture-event-3", "fixture-event-4", "fixture-event-5"};
    /** The sport of each {@link #STACK} row, top first: cricket, tennis, cricket, badminton, cricket. */
    public static final String[] STACK_SPORTS = {ScoreContract.SPORT_CRICKET, ScoreContract.SPORT_TENNIS, ScoreContract.SPORT_CRICKET,
            ScoreContract.SPORT_BADMINTON, ScoreContract.SPORT_CRICKET};
    /** The second live match of the stack: a tennis match, the stack's third sport. */
    public static final String LIVE2_KICKER = "LIVE", LIVE2_LINE = "ATP Finals \u00b7 Alcaraz 6\u20134 3\u20132 Sinner";

    public static final String LIVE_KICKER = "LIVE", LIVE_LINE = "T20I \u00b7 AFG 19/4 (6) v ZIM";
    public static final String BREAK_KICKER = "STUMPS", BREAK_LINE = "Test \u00b7 BAN 386 & 49/2 v WI";
    /** The upcoming row is a badminton match (ADR-044), the companion's line shape for the sport. */
    public static final String UPCOMING_KICKER = "19:30", UPCOMING_LINE = "Japan Masters \u00b7 Sindhu v Axelsen";
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
                return new Object[] {ScoreContract.STATE_UPCOMING, UPCOMING_KICKER, UPCOMING_LINE, EVENT_ID, ScoreContract.SPORT_BADMINTON, "Japan Masters", now, 0, "#9E9E9E"};
            case FINAL:
                return new Object[] {ScoreContract.STATE_FINAL, FINAL_KICKER, FINAL_LINE, EVENT_ID, ScoreContract.SPORT_CRICKET, "T20I", now, 0, "#9E9E9E"};
            default:
                return new Object[] {ScoreContract.STATE_NONE, "", "", null, "", "", 0L, 0, "#9E9E9E"};
        }
    }

    /** The five {@link #STACK} rows, in the order the companion would rank them: live, live, break, upcoming, final. */
    @NonNull
    public static List<Object[]> stack() {
        long now = System.currentTimeMillis();
        List<Object[]> rows = new ArrayList<>(Arrays.asList(
                row(LIVE),
                new Object[] {ScoreContract.STATE_LIVE, LIVE2_KICKER, LIVE2_LINE, STACK_EVENT_IDS[1], STACK_SPORTS[1], "ATP Finals", now, 0, "#E53935"},
                row(BREAK), row(UPCOMING), row(FINAL)));
        for (int i = 2; i < rows.size(); i++) rows.get(i)[3] = STACK_EVENT_IDS[i];
        return rows;
    }

    /** Seeds the fake's pill with the named row, or the five {@link #STACK} rows; the backup row is left as it is, so a switch keeps a restored list. */
    public static void seed(@NonNull String name) {
        if (STACK.equals(name)) FakeScoreProvider.seedPills(stack());
        else FakeScoreProvider.seedPill(row(name));
    }

    /** The fresh stand-in: the live row and {@link #BACKUP_JSON}. */
    public static void seedFresh() {
        seed(LIVE);
        FakeScoreProvider.seedBackup(BACKUP_JSON);
    }
}
