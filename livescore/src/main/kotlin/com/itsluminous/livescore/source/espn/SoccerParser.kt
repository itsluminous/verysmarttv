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
 * One league's soccer `scoreboard` (§13.1): `leagues[0]` names the competition, `events[]` are the
 * matches; a match's `competitions[0].details[]` are its goals and cards, kept as [DetailLine]s for the
 * scoreboard view. `odds`, `venue`, `broadcasts`, `headlines`, `form` and `records` are skipped unread.
 */
internal object SoccerParser {
    fun parse(reader: JsonReader, competition: Competition, updatedMs: Long): List<Event> {
        val events = ArrayList<Event>()
        var leagueName = competition.name
        val pending = ArrayList<Event>()
        Json.obj(reader) { name ->
            when (name) {
                "leagues" -> Json.array(reader) {
                    Json.obj(reader) { l ->
                        when (l) {
                            "abbreviation" -> Json.string(reader).takeIf { it.isNotEmpty() }?.let { leagueName = it }
                            else -> reader.skipValue()
                        }
                    }
                }
                "events" -> Json.array(reader) { event(reader, competition, updatedMs)?.let(pending::add) }
                else -> reader.skipValue()
            }
        }
        pending.mapTo(events) { it.copy(competition = leagueName) }
        return events
    }

    private class EventBuilder {
        var id = ""
        var date = ""
        var status = FeedStatus()
        val sides = ArrayList<Pair<Side, Boolean>>()
        val details = ArrayList<Detail>()
    }

    private class Detail(val clock: String, val teamId: String, val text: String, val player: String, val goal: Boolean, val red: Boolean)

    private fun event(reader: JsonReader, competition: Competition, updatedMs: Long): Event? {
        val b = EventBuilder()
        Json.obj(reader) { name ->
            when (name) {
                "id" -> b.id = Json.string(reader)
                "date" -> b.date = Json.string(reader)
                "status" -> b.status = EspnCommon.status(reader)
                "competitions" -> Json.array(reader) { competition(reader, b) }
                else -> reader.skipValue()
            }
        }
        if (b.id.isEmpty() || b.sides.size < 2) return null
        val homeIndex = b.sides.indexOfFirst { it.second }.coerceAtLeast(0)
        val home = b.sides[homeIndex].first
        val away = b.sides[if (homeIndex == 0) 1 else 0].first
        val state = StateNormaliser.normalise(b.status)
        val detailLines = b.details
            .filter { it.goal || it.red }
            .map { d ->
                val abbr = if (d.teamId == home.id) home.abbr else if (d.teamId == away.id) away.abbr else ""
                DetailLine(primary = "${d.clock} ${d.player.ifEmpty { d.text }}".trim(), secondary = listOf(abbr, if (d.goal) d.text else d.text).filter { it.isNotEmpty() }.joinToString(" · "))
            }
        return Event(
            id = b.id,
            sport = Sport.FOOTBALL,
            competitionId = competition.id,
            competition = competition.name,
            format = "",
            state = state,
            statusDetail = if (state == State.LIVE) b.status.clock else b.status.detail.ifEmpty { b.status.description },
            startMs = EspnCommon.epochMs(b.date),
            endMs = if (state == State.FINAL) EspnCommon.epochMs(b.date) + FULL_TIME_MS else null,
            home = home,
            away = away,
            summary = b.status.summary,
            updatedMs = updatedMs,
            period = b.status.period,
            clock = b.status.clock,
            details = detailLines,
        )
    }

    /** A match ends about two hours after its kick-off; the feed gives no end time for soccer. */
    private const val FULL_TIME_MS = 2L * 3600_000L

    private fun competition(reader: JsonReader, b: EventBuilder) {
        Json.obj(reader) { name ->
            when (name) {
                "competitors" -> Json.array(reader) { b.sides.add(competitor(reader)) }
                "details" -> Json.array(reader) { detail(reader)?.let(b.details::add) }
                else -> reader.skipValue()
            }
        }
    }

    private fun competitor(reader: JsonReader): Pair<Side, Boolean> {
        var id = ""
        var name = ""
        var short = ""
        var abbr = ""
        var score = ""
        var home = false
        var winner = false
        Json.obj(reader) { n ->
            when (n) {
                "homeAway" -> home = Json.string(reader) == "home"
                "winner" -> winner = Json.bool(reader)
                "score" -> score = Json.string(reader).trim()
                "team" -> Json.obj(reader) { t ->
                    when (t) {
                        "id" -> id = Json.string(reader)
                        "displayName" -> name = Json.string(reader)
                        "shortDisplayName" -> short = Json.string(reader)
                        "abbreviation" -> abbr = Json.string(reader)
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        return Side(id, name, abbr.ifEmpty { EspnCommon.abbreviate(name) }, shortName = short.ifEmpty { name }, score = score, winner = winner) to home
    }

    private fun detail(reader: JsonReader): Detail? {
        var clock = ""
        var teamId = ""
        var text = ""
        var player = ""
        var goal = false
        var red = false
        Json.obj(reader) { n ->
            when (n) {
                "type" -> Json.obj(reader) { t -> if (t == "text") text = Json.string(reader) else reader.skipValue() }
                "clock" -> Json.obj(reader) { c -> if (c == "displayValue") clock = Json.string(reader) else reader.skipValue() }
                "team" -> Json.obj(reader) { t -> if (t == "id") teamId = Json.string(reader) else reader.skipValue() }
                "scoringPlay" -> goal = Json.bool(reader)
                "redCard" -> red = Json.bool(reader)
                "athletesInvolved" -> Json.array(reader) {
                    Json.obj(reader) { a ->
                        if (a == "displayName" && player.isEmpty()) player = Json.string(reader) else reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        return if (text.isEmpty() && player.isEmpty()) null else Detail(clock, teamId, text, player, goal, red)
    }
}
