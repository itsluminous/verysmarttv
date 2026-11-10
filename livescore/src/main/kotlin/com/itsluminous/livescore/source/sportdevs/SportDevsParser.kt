package com.itsluminous.livescore.source.sportdevs

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
 * SportDevs' badminton match list (§13.1, ADR-044): an array of match objects, or of `{date, matches[]}`
 * day groups holding them (`matches-by-date`). One [Event] per match; a side's `period_1 … period_n` are the
 * points of each game, kept as the score string (`21 18 11`) and as detail lines, the way the tennis parser
 * keeps sets. Only the names below are read; everything else in a match (images, odds, venue) is skipped.
 */
internal object SportDevsParser {
    private const val GAME_POINTS = 21
    private const val GAME_CAP = 30
    private const val MATCH_MS = 90L * 60_000L
    private val LEAGUE_SEPARATORS = listOf(", ", " - ", " – ", ": ")

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
        val points = ArrayList<Int>()
        var point = ""

        fun read(reader: JsonReader) {
            val byGame = HashMap<Int, Int>()
            Json.obj(reader) { n ->
                when {
                    n == "current" -> games = Json.int(reader, -1)
                    n == "point" -> point = Json.string(reader)
                    n.startsWith("period_") -> {
                        val index = n.removePrefix("period_").toIntOrNull()
                        val value = Json.int(reader, -1)
                        if (index != null && value >= 0) byGame[index] = value
                    }
                    else -> reader.skipValue()
                }
            }
            for (i in 1..(byGame.keys.maxOrNull() ?: 0)) points.add(byGame[i] ?: 0)
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
        var homeId = ""
        var homeName = ""
        var awayId = ""
        var awayName = ""
        val home = Score()
        val away = Score()
        var round = ""

        fun read(n: String, reader: JsonReader) {
            when (n) {
                "id" -> id = Json.string(reader)
                "name" -> name = Json.string(reader)
                "tournament_id" -> tournamentId = Json.string(reader)
                "tournament_name" -> tournament = Json.string(reader)
                "league_id" -> leagueId = Json.string(reader)
                "league_name" -> league = Json.string(reader)
                "status_type" -> if (type.isEmpty()) type = Json.string(reader) else reader.skipValue()
                "status" -> status(reader)
                "start_time" -> start = Json.string(reader)
                "home_team_id" -> homeId = Json.string(reader)
                "home_team_name" -> homeName = Json.string(reader)
                "away_team_id" -> awayId = Json.string(reader)
                "away_team_name" -> awayName = Json.string(reader)
                "home_team_score" -> if (reader.peek() == JsonToken.BEGIN_OBJECT) home.read(reader) else reader.skipValue()
                "away_team_score" -> if (reader.peek() == JsonToken.BEGIN_OBJECT) away.read(reader) else reader.skipValue()
                "round" -> round(reader)
                else -> reader.skipValue()
            }
        }

        private fun status(reader: JsonReader) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                val text = Json.string(reader)
                if (type.isEmpty()) type = text
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
            val played = maxOf(home.points.size, away.points.size)
            val lastOver = played > 0 && gameOver(home.points.getOrElse(played - 1) { 0 }, away.points.getOrElse(played - 1) { 0 })
            val status = FeedStatus(type, reason.ifEmpty { if (lastOver && type.equals("live", true)) BETWEEN_GAMES else "" }, period = played)
            val state = StateNormaliser.normalise(status)
            val games = (0 until played).map { i -> "${home.points.getOrElse(i) { 0 }}-${away.points.getOrElse(i) { 0 }}" }
            val detail = when {
                state == State.LIVE && played > 0 -> "Game $played"
                state == State.BREAK && reason.isEmpty() -> BETWEEN_GAMES
                else -> reason
            }
            val startMs = EspnCommon.epochMs(start)
            val homeWon = state == State.FINAL && home.games > away.games
            val awayWon = state == State.FINAL && away.games > home.games
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
                home = side(homeId, homeName, home, homeWon),
                away = side(awayId, awayName, away, awayWon),
                summary = "",
                updatedMs = updatedMs,
                session = league.takeIf { it.isNotEmpty() && !tournament.contains(it) }.orEmpty(),
                period = played,
                details = games.mapIndexed { i, g -> DetailLine("Game ${i + 1}", g) },
            )
        }

        /** `BWF World Tour, Japan Masters` → `Japan Masters`: the league is the row's meta, not the line's prefix. */
        private fun tournamentName(): String {
            val t = tournament.ifEmpty { league.ifEmpty { name } }
            if (league.isEmpty() || !t.startsWith(league) || t == league) return t
            val rest = t.removePrefix(league)
            val separator = LEAGUE_SEPARATORS.firstOrNull { rest.startsWith(it) } ?: return t
            return rest.removePrefix(separator).trim().ifEmpty { t }
        }

        private fun side(id: String, name: String, score: Score, winner: Boolean): Side =
            Side(
                id = id,
                name = name,
                abbr = abbreviate(name),
                shortName = surnames(name),
                score = score.points.joinToString(" "),
                winner = winner,
            )
    }

    const val BETWEEN_GAMES = "Interval"

    /** A game is over at 21 with a two-point lead, or at 30. */
    fun gameOver(a: Int, b: Int): Boolean {
        val high = maxOf(a, b)
        val low = minOf(a, b)
        return high >= GAME_CAP || high >= GAME_POINTS && high - low >= 2
    }

    /**
     * The surname of each player: `Sindhu P. V.` → `Sindhu` (the feed writes the surname first, initials after),
     * `Pusarla Venkata Sindhu` → `Sindhu` (no initials: the last word); a pair `Chang C. H./Yang C. T.` → `Chang / Yang`.
     */
    fun surnames(name: String): String =
        name.split("/").map { player ->
            val words = player.trim().split(' ').filter { it.isNotEmpty() }
            when {
                words.isEmpty() -> ""
                words.size > 1 && words.drop(1).all { it.endsWith(".") } -> words.first()
                else -> words.last()
            }
        }.filter { it.isNotEmpty() }.joinToString(" / ")

    /** `SIN`, and for a pair the first three letters of each surname: `CHA/YAN`. */
    fun abbreviate(name: String): String =
        surnames(name).split(" / ").map { it.filter(Char::isLetterOrDigit).take(3).uppercase() }.filter { it.isNotEmpty() }.joinToString("/")
}
