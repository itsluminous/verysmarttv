package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.StateNormaliser
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus

/**
 * The `tennis/atp` and `tennis/wta` scoreboards (§13.1): one `events[]` entry per tournament whose
 * `groupings[].competitions[]` are the matches. One [Event] per match; a player's `linescores[]` are the
 * sets, kept as the score string (`7-5 4-6 7-6`) and as detail lines. The biggest payload of the four
 * (365 KB measured), which is why nothing here holds more than one match at a time.
 */
internal object TennisParser {
    fun parse(reader: JsonReader, competition: Competition, updatedMs: Long): List<Event> {
        val events = ArrayList<Event>()
        Json.obj(reader) { name ->
            if (name == "events") Json.array(reader) { tournament(reader, competition, updatedMs, events) } else reader.skipValue()
        }
        return events
    }

    private fun tournament(reader: JsonReader, competition: Competition, updatedMs: Long, out: MutableList<Event>) {
        var name = ""
        val pending = ArrayList<Event>()
        Json.obj(reader) { n ->
            when (n) {
                "shortName" -> name = Json.string(reader)
                "name" -> if (name.isEmpty()) name = Json.string(reader) else reader.skipValue()
                "groupings" -> Json.array(reader) { grouping(reader, competition, updatedMs, pending) }
                else -> reader.skipValue()
            }
        }
        pending.mapTo(out) { it.copy(competition = name.ifEmpty { competition.name }) }
    }

    private fun grouping(reader: JsonReader, competition: Competition, updatedMs: Long, out: MutableList<Event>) {
        var groupName = ""
        val pending = ArrayList<Event>()
        Json.obj(reader) { n ->
            when (n) {
                "grouping" -> Json.obj(reader) { g -> if (g == "displayName") groupName = Json.string(reader) else reader.skipValue() }
                "competitions" -> Json.array(reader) { match(reader, competition, updatedMs)?.let(pending::add) }
                else -> reader.skipValue()
            }
        }
        pending.mapTo(out) { it.copy(session = groupName) }
    }

    private class Player(val id: String, val name: String, val home: Boolean, val winner: Boolean, val sets: List<String>)

    private fun match(reader: JsonReader, competition: Competition, updatedMs: Long): Event? {
        var id = ""
        var date = ""
        var round = ""
        var status = FeedStatus()
        var note = ""
        val players = ArrayList<Player>()
        Json.obj(reader) { n ->
            when (n) {
                "id" -> id = Json.string(reader)
                "date" -> date = Json.string(reader)
                "status" -> status = EspnCommon.status(reader)
                "round" -> Json.obj(reader) { r -> if (r == "displayName") round = Json.string(reader) else reader.skipValue() }
                "notes" -> Json.array(reader) { Json.obj(reader) { t -> if (t == "text" && note.isEmpty()) note = Json.string(reader) else reader.skipValue() } }
                "competitors" -> Json.array(reader) { player(reader)?.let(players::add) }
                else -> reader.skipValue()
            }
        }
        if (id.isEmpty() || players.size < 2) return null
        val homeIndex = players.indexOfFirst { it.home }.coerceAtLeast(0)
        val a = players[homeIndex]
        val b = players[if (homeIndex == 0) 1 else 0]
        val state = StateNormaliser.normalise(status)
        val sets = a.sets.indices.map { i -> "${a.sets[i]}-${b.sets.getOrElse(i) { "" }}".trimEnd('-') }
        val lines = sets.mapIndexed { i, s -> DetailLine("Set ${i + 1}", s) }
        return Event(
            id = id,
            sport = Sport.TENNIS,
            competitionId = competition.id,
            competition = competition.name,
            format = "",
            state = state,
            statusDetail = listOf(round, if (state == State.LIVE) "Set ${status.period}" else status.detail).filter { it.isNotEmpty() }.joinToString(" · "),
            startMs = EspnCommon.epochMs(date),
            endMs = if (state == State.FINAL) EspnCommon.epochMs(date) + MATCH_MS else null,
            home = Side(a.id, a.name, EspnCommon.abbreviate(a.name), shortName = lastName(a.name), score = a.sets.joinToString(" "), winner = a.winner),
            away = Side(b.id, b.name, EspnCommon.abbreviate(b.name), shortName = lastName(b.name), score = b.sets.joinToString(" "), winner = b.winner),
            summary = note,
            updatedMs = updatedMs,
            period = status.period,
            details = lines,
        )
    }

    /** `Borna Gojo` → `Gojo`; a doubles pair `A B / C D` → `B / D`. */
    private fun lastName(name: String): String =
        name.split(" / ").map { it.trim().substringAfterLast(' ') }.joinToString(" / ")

    /** A match is over about three hours after it starts; the feed gives no end time for tennis. */
    private const val MATCH_MS = 3L * 3600_000L

    private fun player(reader: JsonReader): Player? {
        var id = ""
        var name = ""
        var home = false
        var winner = false
        val sets = ArrayList<String>()
        var roster = ""
        Json.obj(reader) { n ->
            when (n) {
                "id" -> id = Json.string(reader)
                "homeAway" -> home = Json.string(reader) == "home"
                "winner" -> winner = Json.bool(reader)
                "athlete" -> Json.obj(reader) { a -> if (a == "displayName") name = Json.string(reader) else reader.skipValue() }
                // Doubles: `roster{displayName: "A / B", athletes[...]}` instead of `athlete`.
                "roster" -> Json.obj(reader) { r -> if (r == "displayName") roster = Json.string(reader) else reader.skipValue() }
                "linescores" -> Json.array(reader) { sets.add(set(reader)) }
                else -> reader.skipValue()
            }
        }
        val shown = name.ifEmpty { roster }
        if (shown.isEmpty()) return null
        return Player(id, shown, home, winner, sets)
    }

    /** One set: games, with the tiebreak in parentheses when the feed has one (`7(9)`). */
    private fun set(reader: JsonReader): String {
        var games = 0
        var tiebreak = -1
        Json.obj(reader) { n ->
            when (n) {
                "value" -> games = Json.int(reader)
                "tiebreak" -> tiebreak = Json.int(reader, -1)
                else -> reader.skipValue()
            }
        }
        return if (tiebreak >= 0) "$games($tiebreak)" else games.toString()
    }
}
