package com.itsluminous.livescore.source.bwf

import android.util.JsonReader
import android.util.JsonToken
import com.itsluminous.livescore.StateNormaliser
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus
import com.itsluminous.livescore.source.espn.Json
import com.itsluminous.livescore.source.sportmicro.SportMicroParser
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** A tournament the Match Centre lists as live this week (`vue-current-live`). */
data class BwfTournament(val id: String, val name: String, val startMs: Long, val endMs: Long)

/**
 * BWF's Match Centre JSON (ADR-049), as the page's own Vue template reads it (`tournament.*.js`, read 2026-11-10):
 *
 * - `vue-current-live`: `{results: [{id, name, start_date "2026-11-10 00:00:00", end_date, tmtLink, …}]}`.
 * - `vue-live-matches?tmtId=…`: `{results: [{match_detail: {code, t1p1_player_model: {name_short1, …}, t1p2_player_model?,
 *   t2p1_player_model, t2p2_player_model?, t1p1country_model, …}, live_detail: {court_name, match_state ("P" while
 *   playing), match_state_name, event ("MS"), round ("R32"), duration, team1_g1_score … team2_g3_score (null until
 *   played), service_player}, status_name ("Retired", "Walkover" when there is one), winner (1 or 2)}]}`.
 *
 * One [Event] per match in the shape the SportMicro parser gives, so `LineFormatter` and the picker read both alike:
 * pairs as `A / B` (surnames and abbreviations through [SportMicroParser]), games as the score string. The match
 * centre lists the matches on court, not the day's order of play, so nothing here is `upcoming` by schedule. Everything
 * not named above is skipped unread.
 */
internal object BwfParser {
    /** `vue-current-live` → the tournaments; an empty list when the shape is not the one above. */
    fun parseTournaments(reader: JsonReader): List<BwfTournament> {
        val out = ArrayList<BwfTournament>()
        results(reader) {
            var id = ""
            var name = ""
            var start = ""
            var end = ""
            Json.obj(reader) { n ->
                when (n) {
                    "id" -> id = Json.string(reader)
                    "name" -> name = Json.string(reader)
                    "start_date" -> start = Json.string(reader)
                    "end_date" -> end = Json.string(reader)
                    else -> reader.skipValue()
                }
            }
            if (id.isNotEmpty()) out.add(BwfTournament(id, name.ifEmpty { "BWF $id" }, dayMs(start), dayMs(end)))
        }
        return out
    }

    /** `vue-live-matches` of [tournament] → one event per match. */
    fun parseLiveMatches(reader: JsonReader, tournament: BwfTournament, updatedMs: Long): List<Event> {
        val out = ArrayList<Event>()
        results(reader) {
            val m = Match()
            Json.obj(reader) { n ->
                when (n) {
                    "match_detail" -> Json.obj(reader) { f -> m.detail(reader, f) }
                    "live_detail" -> Json.obj(reader) { f -> m.live(reader, f) }
                    "status_name" -> m.statusName = Json.string(reader)
                    "winner" -> m.winner = Json.int(reader)
                    else -> reader.skipValue()
                }
            }
            m.toEvent(tournament, updatedMs)?.let(out::add)
        }
        return out
    }

    private class Match {
        var code = ""
        var event = ""
        var round = ""
        var court = ""
        var state = ""
        var stateName = ""
        var statusName = ""
        var winner = 0
        val team1 = ArrayList<String>()
        val team2 = ArrayList<String>()
        val team1Ids = ArrayList<String>()
        val team2Ids = ArrayList<String>()
        val points1 = IntArray(GAMES) { -1 }
        val points2 = IntArray(GAMES) { -1 }

        fun detail(reader: JsonReader, n: String) {
            when {
                n == "code" -> code = Json.string(reader)
                n == "event" && event.isEmpty() -> event = Json.string(reader)
                n.matches(PLAYER) -> player(n, reader)
                else -> reader.skipValue()
            }
        }

        /** `t1p1_player_model` → team 1's first player: its `name_short1` (and `id`). */
        private fun player(n: String, reader: JsonReader) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                return
            }
            var name = ""
            var id = ""
            Json.obj(reader) { f ->
                when (f) {
                    "name_short1" -> name = Json.string(reader)
                    "name_display" -> if (name.isEmpty()) name = Json.string(reader) else reader.skipValue()
                    "id" -> id = Json.string(reader)
                    else -> reader.skipValue()
                }
            }
            if (name.isEmpty()) return
            val (names, ids) = if (n.startsWith("t1")) team1 to team1Ids else team2 to team2Ids
            names.add(name.trim())
            ids.add(id)
        }

        fun live(reader: JsonReader, n: String) {
            when {
                n == "court_name" -> court = Json.string(reader)
                n == "match_state" -> state = Json.string(reader)
                n == "match_state_name" -> stateName = Json.string(reader)
                n == "event" -> event = Json.string(reader)
                n == "round" -> round = Json.string(reader)
                n.matches(GAME) -> game(n, reader)
                else -> reader.skipValue()
            }
        }

        /** `team1_g2_score` → team 1's points in game 2; `null` is a game not played. */
        private fun game(n: String, reader: JsonReader) {
            val team = if (n.startsWith("team1")) points1 else points2
            val index = n.substringAfter("_g").substringBefore('_').toIntOrNull() ?: 0
            val value = Json.int(reader, -1)
            if (index in 1..GAMES && value >= 0) team[index - 1] = value
        }

        fun toEvent(t: BwfTournament, updatedMs: Long): Event? {
            if (code.isEmpty() || team1.isEmpty() || team2.isEmpty()) return null
            val played = (0 until GAMES).count { points1[it] >= 0 || points2[it] >= 0 }
            val p1 = (0 until played).map { points1[it].coerceAtLeast(0) }
            val p2 = (0 until played).map { points2[it].coerceAtLeast(0) }
            val lastOver = played > 0 && SportMicroParser.gameOver(p1[played - 1], p2[played - 1])
            val feedState = feedState()
            val reason = statusName.ifEmpty { if (feedState == "live") "" else stateName }
            var state = StateNormaliser.normalise(FeedStatus(feedState, reason, period = played))
            var why = reason
            if (state == State.LIVE && lastOver && winner == 0) {
                // A game over with no next one begun: the interval between games, as the SportMicro parser reads it (ADR-044).
                state = State.BREAK
                why = SportMicroParser.BETWEEN_GAMES
            }
            val games = (0 until played).map { "${p1[it]}-${p2[it]}" }
            val detail = if (state == State.LIVE && played > 0) "Game $played" else why.ifEmpty { stateName }
            val roundText = listOf(event, round).filter { it.isNotEmpty() }.joinToString(" - ")
            val startMs = startMs(t.startMs, updatedMs)
            val home = side(team1Ids, team1, p1, state == State.FINAL && winner == 1)
            val away = side(team2Ids, team2, p2, state == State.FINAL && winner == 2)
            return Event(
                id = "bwf-$code",
                sport = Sport.BADMINTON,
                competitionId = "bwf-${t.id}",
                competition = t.name,
                format = "",
                state = state,
                statusDetail = listOf(roundText, detail).filter { it.isNotEmpty() }.joinToString(" · "),
                startMs = startMs,
                endMs = if (state == State.FINAL) updatedMs else null,
                home = home,
                away = away,
                summary = "",
                updatedMs = updatedMs,
                session = court,
                period = played,
                details = games.mapIndexed { i, g -> DetailLine("Game ${i + 1}", g) },
            )
        }

        /**
         * The feed's own word for where the match is: `P` is playing (the one code the Match Centre's template tests);
         * otherwise `match_state_name` as written (`Playing`, `Finished`, `Walkover`, `Warm up`…), and when that is
         * empty too, the winner or the points decide.
         */
        private fun feedState(): String =
            when {
                state.equals("P", true) -> "live"
                stateName.isNotEmpty() -> stateName
                winner != 0 -> "finished"
                (0 until GAMES).any { points1[it] >= 0 || points2[it] >= 0 } -> "live"
                else -> "upcoming"
            }

        private fun side(ids: List<String>, names: List<String>, points: List<Int>, winner: Boolean): Side {
            val name = names.joinToString(" / ")
            return Side(
                id = ids.filter { it.isNotEmpty() }.joinToString("/"),
                name = name,
                abbr = abbreviate(name),
                shortName = surnames(name),
                score = points.joinToString(" "),
                winner = winner,
            )
        }
    }

    /**
     * BWF writes the surname in capitals wherever it falls (`LEE Zii Jia`, `Kodai NARAOKA`, `AN Se Young`), so the
     * capitalised word is the surname, title-cased for the line (`Lee`, `Naraoka`, `An`); a name with no such word reads
     * by SportMicro's rule (initials after the surname, else the last word). A pair is `A / B`.
     */
    fun surnames(name: String): String =
        name.split("/").map { player ->
            val words = player.trim().split(' ').filter { it.isNotEmpty() }
            val caps = words.filter { w -> w.length >= 2 && w.all { it.isLetter() && it.isUpperCase() } } // `Z-W` is initials, not a surname
            if (caps.isNotEmpty() && caps.size < words.size) {
                caps.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
            } else {
                SportMicroParser.surnames(player)
            }
        }.filter { it.isNotEmpty() }.joinToString(" / ")

    /** `LEE`, and for a pair the first three letters of each surname: `CHI/SOH`. */
    fun abbreviate(name: String): String =
        surnames(name).split(" / ").map { it.filter(Char::isLetterOrDigit).take(3).uppercase() }.filter { it.isNotEmpty() }.joinToString("/")

    private const val GAMES = 3
    private val PLAYER = Regex("t[12]p[12]_player_model")
    private val GAME = Regex("team[12]_g[1-3]_score")

    /** The `results` array of a `{results: […]}` envelope, or a bare array; [onItem] is called on each object element. */
    private inline fun results(reader: JsonReader, onItem: () -> Unit) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> Json.obj(reader) { n -> if (n == "results" && reader.peek() == JsonToken.BEGIN_ARRAY) items(reader, onItem) else reader.skipValue() }
            JsonToken.BEGIN_ARRAY -> items(reader, onItem)
            else -> reader.skipValue()
        }
    }

    private inline fun items(reader: JsonReader, onItem: () -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) if (reader.peek() == JsonToken.BEGIN_OBJECT) onItem() else reader.skipValue()
        reader.endArray()
    }

    /** `2026-11-10 00:00:00` (the feed's dates carry no zone; read as UTC) to epoch milliseconds, 0 when unreadable. */
    fun dayMs(text: String): Long {
        val day = text.trim().take(10)
        if (day.length < 10) return 0L
        return try {
            LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            0L
        }
    }

    /** The match centre gives no start time per match: a match on court today started today, so the day's start (UTC) or the tournament's, whichever is later. */
    fun startMs(tournamentStartMs: Long, updatedMs: Long): Long = maxOf(tournamentStartMs, updatedMs - updatedMs % DAY_MS)

    private const val DAY_MS = 24L * 3600_000L
}
