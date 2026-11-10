package com.itsluminous.livescore.source.sportmicro

import android.util.JsonReader
import android.util.JsonToken
import com.itsluminous.livescore.StateNormaliser
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus
import com.itsluminous.livescore.source.espn.EspnCommon
import com.itsluminous.livescore.source.espn.Json

/**
 * SportMicro's badminton match list (§13.1, ADR-044), recorded 2026-11-10. Two shapes carry the same match:
 *
 * - `matches-by-date` (the one the source reads): an array of `{date, matches[]}` day groups whose matches are
 *   **flat**: `status` is a word (`upcoming`, `finished`, `canceled`…), `status_reason` another (`Not started`,
 *   `Ended`, `Walkover`, `Canceled`), `home_team_score` the games won (a number, absent before play) and
 *   `home_team_period_N_score` the points of game N.
 * - `/matches` (by id or tournament): a bare array whose matches are **nested**: `status: {type, reason}`,
 *   `status_type`, `home_team_score: {current, display, period_N}`, `times: {specific_start_time}`.
 *
 * One [Event] per match; a side's points per game are kept as the score string (`21 18 11`) and as detail lines,
 * the way the tennis parser keeps sets. `specific_start_time` (the real first serve) is preferred to `start_time`,
 * which the feed rounds to the session's start, often midnight. Only the names below are read; everything else in
 * a match (images, arena, season, importance) is skipped. The feed has no `round`, so the detail is the status word.
 */
internal object SportMicroParser {
    private const val GAME_POINTS = 21
    private const val GAME_CAP = 30
    private const val MATCH_MS = 90L * 60_000L
    private val LEAGUE_SEPARATORS = listOf(", ", " - ", " – ", ": ")
    private val INITIAL = Regex("^[A-Z]{1,3}\\.?$")

    fun parse(reader: JsonReader, updatedMs: Long): List<Event> {
        val events = ArrayList<Event>()
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> Json.array(reader) { element(reader, updatedMs, events) }
            JsonToken.BEGIN_OBJECT -> element(reader, updatedMs, events)
            else -> reader.skipValue()
        }
        return events
    }

    /** A match, or a day group whose `matches[]` are matches. */
    private fun element(reader: JsonReader, updatedMs: Long, out: MutableList<Event>) {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return
        }
        val m = Match()
        var group = false
        Json.obj(reader) { name ->
            if (name == "matches" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                group = true
                Json.array(reader) { element(reader, updatedMs, out) }
            } else {
                m.read(name, reader)
            }
        }
        if (!group) m.toEvent(updatedMs)?.let(out::add)
    }

    private class Score {
        var games = -1
        private val byGame = HashMap<Int, Int>()
        val points: List<Int> get() = (1..(byGame.keys.maxOrNull() ?: 0)).map { byGame[it] ?: 0 }

        /** The nested shape: `{current, display, period_1 …}`; `display` is `current` again and is skipped. */
        fun read(reader: JsonReader) {
            Json.obj(reader) { n ->
                when {
                    n == "current" -> games = Json.int(reader, -1)
                    n.startsWith("period_") -> game(n.removePrefix("period_"), reader)
                    else -> reader.skipValue()
                }
            }
        }

        /** The flat shape: `home_team_score: 2` and `home_team_period_1_score: 21`. */
        fun readFlat(rest: String, reader: JsonReader) {
            when {
                rest == "score" -> games = Json.int(reader, -1)
                rest.startsWith("period_") && rest.endsWith("_score") -> game(rest.removePrefix("period_").removeSuffix("_score"), reader)
                else -> reader.skipValue()
            }
        }

        private fun game(index: String, reader: JsonReader) {
            val i = index.toIntOrNull()
            val value = Json.int(reader, -1)
            if (i != null && value >= 0) byGame[i] = value
        }
    }

    private class Match {
        var id = ""
        var name = ""
        var tournamentId = ""
        var tournament = ""
        var leagueId = ""
        var league = ""
        var type = ""
        var reason = ""
        var start = ""
        var specificStart = ""
        var homeId = ""
        var homeName = ""
        var awayId = ""
        var awayName = ""
        val home = Score()
        val away = Score()
        var round = ""

        fun read(n: String, reader: JsonReader) {
            when {
                n == "id" -> id = Json.string(reader)
                n == "name" -> name = Json.string(reader)
                n == "tournament_id" -> tournamentId = Json.string(reader)
                n == "tournament_name" -> tournament = Json.string(reader)
                n == "league_id" -> leagueId = Json.string(reader)
                n == "league_name" -> league = Json.string(reader)
                n == "status_type" -> if (type.isEmpty()) type = Json.string(reader) else reader.skipValue()
                n == "status_reason" -> reason = Json.string(reader)
                n == "status" -> status(reader)
                n == "start_time" -> start = Json.string(reader)
                n == "specific_start_time" -> specificStart = Json.string(reader)
                n == "times" -> times(reader)
                n == "home_team_id" -> homeId = Json.string(reader)
                n == "home_team_name" -> homeName = Json.string(reader)
                n == "away_team_id" -> awayId = Json.string(reader)
                n == "away_team_name" -> awayName = Json.string(reader)
                n == "home_team_score" && reader.peek() == JsonToken.BEGIN_OBJECT -> home.read(reader)
                n == "away_team_score" && reader.peek() == JsonToken.BEGIN_OBJECT -> away.read(reader)
                n.startsWith("home_team_") -> home.readFlat(n.removePrefix("home_team_"), reader)
                n.startsWith("away_team_") -> away.readFlat(n.removePrefix("away_team_"), reader)
                n == "round" -> round(reader)
                else -> reader.skipValue()
            }
        }

        /** `status: "finished"` (flat) or `status: {type, reason}` (nested). */
        private fun status(reader: JsonReader) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                val text = Json.string(reader)
                if (text.isNotEmpty()) type = text
                return
            }
            Json.obj(reader) { s ->
                when (s) {
                    "type" -> type = Json.string(reader)
                    "reason" -> reason = Json.string(reader)
                    else -> reader.skipValue()
                }
            }
        }

        private fun times(reader: JsonReader) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                return
            }
            Json.obj(reader) { t -> if (t == "specific_start_time" && specificStart.isEmpty()) specificStart = Json.string(reader) else reader.skipValue() }
        }

        private fun round(reader: JsonReader) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                round = Json.string(reader)
                return
            }
            Json.obj(reader) { r -> if (r == "name") round = Json.string(reader) else reader.skipValue() }
        }

        fun toEvent(updatedMs: Long): Event? {
            if (id.isEmpty()) return null
            if (homeName.isEmpty() && awayName.isEmpty()) {
                val parts = name.split(" vs ", " v ")
                if (parts.size == 2) {
                    homeName = parts[0].trim()
                    awayName = parts[1].trim()
                }
            }
            if (homeName.isEmpty() || awayName.isEmpty()) return null
            val homePoints = home.points
            val awayPoints = away.points
            val played = maxOf(homePoints.size, awayPoints.size)
            val lastOver = played > 0 && gameOver(homePoints.getOrElse(played - 1) { 0 }, awayPoints.getOrElse(played - 1) { 0 })
            // The ending or pause: the feed's reason, else the type itself when it is one (`walkover`, `retired`, `suspended`).
            // A live match whose last game is over with no next game begun is at the interval, whatever game ordinal the
            // reason names (`1st game`), unless the reason is itself a pause or ending the normaliser knows.
            val given = reason.ifEmpty { typeAsReason() }
            val interval = lastOver && type.equals("live", true) && StateNormaliser.normalise(FeedStatus(type, given, period = played)) == State.LIVE
            val why = if (interval) BETWEEN_GAMES else given
            val status = FeedStatus(type, why, period = played)
            val state = StateNormaliser.normalise(status)
            val games = (0 until played).map { i -> "${homePoints.getOrElse(i) { 0 }}-${awayPoints.getOrElse(i) { 0 }}" }
            val detail = if (state == State.LIVE && played > 0) "Game $played" else why
            val startMs = EspnCommon.epochMs(specificStart.ifEmpty { start }).takeIf { it > 0 } ?: EspnCommon.epochMs(start)
            val homeGames = if (home.games >= 0) home.games else gamesWon(homePoints, awayPoints)
            val awayGames = if (away.games >= 0) away.games else gamesWon(awayPoints, homePoints)
            val homeWon = state == State.FINAL && homeGames > awayGames
            val awayWon = state == State.FINAL && awayGames > homeGames
            return Event(
                id = id,
                sport = Sport.BADMINTON,
                competitionId = tournamentId.ifEmpty { leagueId.ifEmpty { tournament } },
                competition = tournamentName(),
                format = "",
                state = state,
                statusDetail = listOf(round, detail).filter { it.isNotEmpty() }.joinToString(" · "),
                startMs = startMs,
                endMs = if (state == State.FINAL && startMs > 0) startMs + MATCH_MS else null,
                home = side(homeId, homeName, homePoints, homeWon),
                away = side(awayId, awayName, awayPoints, awayWon),
                summary = "",
                updatedMs = updatedMs,
                session = session(),
                period = played,
                details = games.mapIndexed { i, g -> DetailLine("Game ${i + 1}", g) },
            )
        }

        /** `BWF World Tour, Japan Masters` → `Japan Masters`: a league that prefixes the tournament is the row's meta, not the line's prefix. */
        private fun tournamentName(): String {
            val t = tournament.ifEmpty { league.ifEmpty { name } }
            if (league.isEmpty() || !t.startsWith(league) || t == league) return t
            val rest = t.removePrefix(league)
            val separator = LEAGUE_SEPARATORS.firstOrNull { rest.startsWith(it) } ?: return t
            return rest.removePrefix(separator).trim().ifEmpty { t }
        }

        /**
         * The league as the row's meta, only when the tournament does not already say it: the feed's league
         * `Kumamoto Masters Japan - WS` against its tournament `Kumamoto Masters Japan, WS, Qualifying` adds nothing.
         */
        private fun session(): String {
            if (league.isEmpty()) return ""
            val base = league.substringBefore(" - ").trim()
            return if (tournament.contains(league) || base.isNotEmpty() && tournament.startsWith(base)) "" else league
        }

        /** A type that is itself the ending or pause (`walkover`, `retired`, `suspended`…), capitalised as a reason; empty for the plain states. */
        private fun typeAsReason(): String {
            val word = type.trim()
            if (word.isEmpty() || word.lowercase() in PLAIN_TYPES) return ""
            return word.replaceFirstChar { it.uppercase() }
        }

        private fun side(id: String, name: String, points: List<Int>, winner: Boolean): Side =
            Side(
                id = id,
                name = name,
                abbr = abbreviate(name),
                shortName = surnames(name),
                score = points.joinToString(" "),
                winner = winner,
            )
    }

    const val BETWEEN_GAMES = "Interval"

    /** The types that only say where the match is, not why: anything else (`walkover`, `retired`, `suspended`) is also the reason. */
    private val PLAIN_TYPES = setOf(
        "upcoming", "notstarted", "not started", "scheduled", "pre", "live", "inprogress", "in progress", "in",
        "finished", "ended", "final", "post",
    )

    /** A game is over at 21 with a two-point lead, or at 30. */
    fun gameOver(a: Int, b: Int): Boolean {
        val high = maxOf(a, b)
        val low = minOf(a, b)
        return high >= GAME_CAP || high >= GAME_POINTS && high - low >= 2
    }

    /** The games [mine] won against [theirs], counted from the points when the feed gives no games-won number. */
    fun gamesWon(mine: List<Int>, theirs: List<Int>): Int =
        mine.indices.count { i -> mine[i] > theirs.getOrElse(i) { 0 } && gameOver(mine[i], theirs.getOrElse(i) { 0 }) }

    /**
     * The surname of each player. The feed writes the surname first and the initials after it, with or without
     * dots: `Matsuta N.` → `Matsuta`, `Pong L K K` → `Pong`, `He Z-W` → `He`; a full name has the surname last
     * (`Victor Lai` → `Lai`); a pair `Chang C. H./Yang C. T.` or `Yu Z J / Tse Y S` → `Chang / Yang`, `Yu / Tse`.
     */
    fun surnames(name: String): String =
        name.split("/").map { player ->
            val words = player.trim().split(' ').filter { it.isNotEmpty() }
            when {
                words.isEmpty() -> ""
                words.size > 1 && words.drop(1).all { INITIAL.matches(it.replace("-", "")) } -> words.first()
                else -> words.last()
            }.trimEnd('.')
        }.filter { it.isNotEmpty() }.joinToString(" / ")

    /** `MAT`, and for a pair the first three letters of each surname: `CHA/YAN`. */
    fun abbreviate(name: String): String =
        surnames(name).split(" / ").map { it.filter(Char::isLetterOrDigit).take(3).uppercase() }.filter { it.isNotEmpty() }.joinToString("/")
}
