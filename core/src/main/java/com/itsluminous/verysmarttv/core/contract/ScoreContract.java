package com.itsluminous.verysmarttv.core.contract;

import android.content.ComponentName;
import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * What the live-score companion publishes and the launcher's score pill reads (DESIGN.md §12.2).
 *
 * <p>The provider at {@link #AUTHORITY} is guarded by two {@code signature}-level permissions the
 * companion declares and the launcher holds (§12.3): {@link #PERMISSION_READ} for every query and
 * {@link #PERMISSION_WRITE} for the one {@code call()} method, {@link #METHOD_RESTORE}. It answers:
 *
 * <ul>
 *   <li>{@link #PILL}: one row per event the pill can show, {@link #PILL_COLUMNS}, the text the launcher
 *       renders under the pills, in the order the companion ranks them (live, then a break, then upcoming
 *       soonest, then recent finals; the first row is what a one-line pill shows). The launcher cuts the
 *       list to its own row count ({@code score_rows}, at most {@link #MAX_ROWS}); the companion never
 *       needs to know that count. Always answerable from the on-disk cache, never blocks on a fetch;
 *       exactly one row with {@link #STATE_NONE} when nothing followed is in the window. A query is also
 *       the companion's signal that a screen is looking (§13.5). Observers are notified after every
 *       refresh and after the follow list changes.
 *   <li>{@link #EVENTS}: the followed events from yesterday 00:00 to tomorrow 23:59 local, live first
 *       then by start, {@link #EVENT_COLUMNS}; {@code events/<event_id>} returns one.
 *   <li>{@link #BACKUP}: exactly one row, {@link #BACKUP_COLUMNS}: the follow list as the JSON the
 *       companion stores, so the launcher's Backup file carries it under {@code companions.livescore}
 *       without parsing it (ADR-032).
 *   <li>{@code call(}{@link #METHOD_RESTORE}{@code , null, extras)} with {@link #EXTRA_JSON}: replaces the
 *       follow list and notifies {@link #PILL}; returns {@link #RESULT_OK} true, or false with
 *       {@link #RESULT_ERROR}.
 * </ul>
 *
 * <p>Team logos are not in v1; the path {@code image/team/<id>} is reserved, not implemented.
 */
public final class ScoreContract {
    private ScoreContract() {}

    /** The companion's package. */
    public static final String PACKAGE = "com.itsluminous.livescore";

    /** Content authority of the score provider. */
    public static final String AUTHORITY = PACKAGE + ".scores";

    /** Read permission on the provider; {@code protectionLevel="signature"}. */
    public static final String PERMISSION_READ = PACKAGE + ".permission.READ_SCORES";

    /** Write permission on the provider, needed for {@link #METHOD_RESTORE}; {@code protectionLevel="signature"}. */
    public static final String PERMISSION_WRITE = PACKAGE + ".permission.WRITE_SCORES";

    /** Path of the pill rows. */
    public static final String PATH_PILL = "pill";

    /** Path of the followed events in the window. */
    public static final String PATH_EVENTS = "events";

    /** Path of the one-row follow-list backup. */
    public static final String PATH_BACKUP = "backup";

    /** {@code content://com.itsluminous.livescore.scores/pill}. */
    public static final Uri PILL = Uri.parse("content://" + AUTHORITY + "/" + PATH_PILL);

    /** {@code content://com.itsluminous.livescore.scores/events}. */
    public static final Uri EVENTS = Uri.parse("content://" + AUTHORITY + "/" + PATH_EVENTS);

    /** {@code content://com.itsluminous.livescore.scores/backup}. */
    public static final Uri BACKUP = Uri.parse("content://" + AUTHORITY + "/" + PATH_BACKUP);

    /** MIME type of the {@link #PILL} cursor. */
    public static final String TYPE_PILL = "vnd.android.cursor.item/vnd." + PACKAGE + ".pill";

    /** MIME type of the {@link #EVENTS} cursor. */
    public static final String TYPE_EVENTS = "vnd.android.cursor.dir/vnd." + PACKAGE + ".event";

    /** MIME type of one {@code events/<event_id>} row. */
    public static final String TYPE_EVENT = "vnd.android.cursor.item/vnd." + PACKAGE + ".event";

    /** MIME type of the {@link #BACKUP} cursor. */
    public static final String TYPE_BACKUP = "vnd.android.cursor.item/vnd." + PACKAGE + ".backup";

    /** {@code call()} method that replaces the follow list from {@link #EXTRA_JSON}. */
    public static final String METHOD_RESTORE = "restore";

    /** {@code String} extra of {@link #METHOD_RESTORE}: the JSON a {@link #BACKUP} query returned. */
    public static final String EXTRA_JSON = "json";

    /** {@code boolean} key of the {@link #METHOD_RESTORE} result. */
    public static final String RESULT_OK = "ok";

    /** {@code String} key of the {@link #METHOD_RESTORE} result when {@link #RESULT_OK} is false. */
    public static final String RESULT_ERROR = "error";

    /** The activity the launcher opens from the pill, with {@link #EXTRA_EVENT_ID}. */
    public static final ComponentName SCOREBOARD_ACTIVITY = new ComponentName(PACKAGE, PACKAGE + ".ScoreboardActivity");

    /** The follow picker the launcher opens on a long press of the pill. */
    public static final ComponentName FOLLOW_ACTIVITY = new ComponentName(PACKAGE, PACKAGE + ".FollowActivity");

    /** {@code String} extra of {@link #SCOREBOARD_ACTIVITY}: the {@link #EVENT_ID} to show. */
    public static final String EXTRA_EVENT_ID = "event_id";

    /**
     * Intent action the companion answers for opening a score: with {@link #EXTRA_EVENT_ID} it shows that
     * event's scoreboard, without it the companion's Home. Equivalent to starting
     * {@link #SCOREBOARD_ACTIVITY} by component, for a launcher that prefers an action.
     */
    public static final String ACTION_OPEN = PACKAGE + ".action.OPEN";

    // States (column STATE), §13.4.

    /** In play. */
    public static final String STATE_LIVE = "live";

    /** Started, not in play: stumps, lunch, half time, rain. */
    public static final String STATE_BREAK = "break";

    /** Not started. */
    public static final String STATE_UPCOMING = "upcoming";

    /** Over; shown for a while after the end. */
    public static final String STATE_FINAL = "final";

    /** Pill only: nothing followed in the window; the launcher hides the pill. */
    public static final String STATE_NONE = "none";

    // Sports (column SPORT): the whole value set is SPORTS. The companion publishes the ones it has a source for
    // (cricket, football, f1 and tennis in v1); a launcher maps each to its icon and shows a generic one for any
    // value outside the set, so a companion may add a sport before the launcher learns it.

    public static final String SPORT_CRICKET = "cricket";
    public static final String SPORT_FOOTBALL = "football";
    public static final String SPORT_F1 = "f1";
    public static final String SPORT_TENNIS = "tennis";
    public static final String SPORT_BADMINTON = "badminton";
    public static final String SPORT_BASKETBALL = "basketball";
    public static final String SPORT_HOCKEY = "hockey";

    /** Every value {@link #SPORT} may carry, each with its own icon in the launcher. */
    public static final String[] SPORTS = {
        SPORT_CRICKET, SPORT_FOOTBALL, SPORT_F1, SPORT_TENNIS, SPORT_BADMINTON, SPORT_BASKETBALL, SPORT_HOCKEY,
    };

    // Follow reasons (column FOLLOWED_REASON).

    public static final String REASON_TEAM = "team";
    public static final String REASON_COMPETITION = "competition";
    public static final String REASON_EVENT = "event";

    // Columns shared by /pill and /events.

    /** One of the {@code STATE_*} values. */
    public static final String STATE = "state";

    /** The feed's event id. Nullable on the pill when {@link #STATE} is {@link #STATE_NONE}. */
    public static final String EVENT_ID = "event_id";

    /** One of the {@code SPORT_*} values ({@link #SPORTS}); a launcher shows a generic icon for anything else. */
    public static final String SPORT = "sport";

    /** Competition name as shown ({@code "IPL"}, {@code "Premier League"}). */
    public static final String COMPETITION = "competition";

    /** Epoch milliseconds of the fetch the row comes from. */
    public static final String UPDATED_MS = "updated_ms";

    // Pill-only columns.

    /** Short text in the state colour before the line ({@code "LIVE"}, {@code "STUMPS"}, {@code "19:30"}); may be empty. */
    public static final String KICKER = "kicker";

    /** The one-line text, at most {@link #LINE_MAX_CHARS} characters, already shortened by the companion. */
    public static final String LINE = "line";

    /** 1 when {@link #UPDATED_MS} is older than the refresh interval for the state; the launcher dims the pill. */
    public static final String STALE = "stale";

    /** {@code #RRGGBB} colour for the kicker. */
    public static final String COLOR = "color";

    /** Longest {@link #LINE} the pill accepts. */
    public static final int LINE_MAX_CHARS = 48;

    /** The most {@link #PILL} rows a launcher shows at once; a longer answer is cut, never an error. */
    public static final int MAX_ROWS = 5;

    // Event-only columns.

    /** Row id. */
    public static final String ID = "_id";

    /** Cricket format ({@code "T20I"}, {@code "ODI"}, {@code "Test"}); empty for other sports. */
    public static final String FORMAT = "format";

    /** The feed's own status words ({@code "Stumps"}, {@code "HT"}, {@code "Q3"}). */
    public static final String STATUS_DETAIL = "status_detail";

    /** Scheduled start, epoch milliseconds. */
    public static final String START_MS = "start_ms";

    /** End, epoch milliseconds; null while not over. */
    public static final String END_MS = "end_ms";

    public static final String HOME_NAME = "home_name";
    public static final String HOME_ABBR = "home_abbr";
    public static final String HOME_SCORE = "home_score";
    public static final String AWAY_NAME = "away_name";
    public static final String AWAY_ABBR = "away_abbr";
    public static final String AWAY_SCORE = "away_score";

    /** The feed's one-sentence summary ({@code "Bangladesh require 320 runs"}). */
    public static final String SUMMARY = "summary";

    /** One of the {@code REASON_*} values: why the event is in the window. */
    public static final String FOLLOWED_REASON = "followed_reason";

    // Backup column.

    /** The companion's follow list as the JSON it stores; opaque to the launcher. */
    public static final String JSON = "json";

    /** Every column of {@link #PILL}, in cursor order. */
    public static final String[] PILL_COLUMNS = {
        STATE, KICKER, LINE, EVENT_ID, SPORT, COMPETITION, UPDATED_MS, STALE, COLOR,
    };

    /** Every column of {@link #EVENTS}, in cursor order. */
    public static final String[] EVENT_COLUMNS = {
        ID, EVENT_ID, SPORT, COMPETITION, FORMAT, STATE, STATUS_DETAIL, START_MS, END_MS,
        HOME_NAME, HOME_ABBR, HOME_SCORE, AWAY_NAME, AWAY_ABBR, AWAY_SCORE,
        SUMMARY, FOLLOWED_REASON, UPDATED_MS,
    };

    /** Every column of {@link #BACKUP}, in cursor order. */
    public static final String[] BACKUP_COLUMNS = {JSON};

    /** {@code content://…scores/events/<eventId>}. */
    @NonNull
    public static Uri event(@NonNull String eventId) {
        return Uri.withAppendedPath(EVENTS, Uri.encode(eventId));
    }
}
