package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.StateNormaliser
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus

/**
 * The cricket `scorepanel` (§13.1): `scores[]`, each a league (`leagues[0]`) with its `events[]`. Reads
 * one event at a time and keeps only what [Event] carries; `venue`, `broadcasts`, `highlights`,
 * `leaders`, `statistics`, `records` and the headshots are skipped unread.
 */
internal object CricketParser {
    /** `class.internationalClassId` → format; 9 and 10 are the women's ODI and T20I, 14 to 16 youth. */
    private val INTERNATIONAL_FORMATS = mapOf(
        "1" to "Test", "2" to "ODI", "3" to "T20I", "9" to "ODI", "10" to "T20I",
        "14" to "Youth Test", "15" to "Youth ODI", "16" to "Youth T20",
    )

    fun parse(reader: JsonReader, updatedMs: Long): List<Event> {
        val events = ArrayList<Event>()
        Json.obj(reader) { name ->
            if (name == "scores") Json.array(reader) { leagueBlock(reader, events, updatedMs) } else reader.skipValue()
        }
        return events
    }

    private class League(var id: String = "", var name: String = "", var abbr: String = "")

    /** One `scores[]` element: its `leagues[0]` and `events[]`; `leagues` precedes `events` in the feed. */
    private fun leagueBlock(reader: JsonReader, out: MutableList<Event>, updatedMs: Long) {
        val league = League()
        val pending = ArrayList<Event>()
        Json.obj(reader) { name ->
            when (name) {
                "leagues" -> Json.array(reader) {
                    val l = League()
                    Json.obj(reader) { n ->
                        when (n) {
                            "id" -> l.id = Json.string(reader)
                            "name" -> l.name = Json.string(reader)
                            "abbreviation" -> l.abbr = Json.string(reader)
                            else -> reader.skipValue()
                        }
                    }
                    if (league.id.isEmpty()) {
                        league.id = l.id
                        league.name = l.name
                        league.abbr = l.abbr
                    }
                }
                "events" -> Json.array(reader) { event(reader, updatedMs)?.let(pending::add) }
                else -> reader.skipValue()
            }
        }
        val competition = league.abbr.ifEmpty { league.name }
        pending.mapTo(out) { it.copy(competitionId = league.id, competition = competition) }
    }

    private class EventBuilder {
        var id = ""
        var date = ""
        var endDate = ""
        var description = ""
        var status = FeedStatus()
        var competitionStatus = FeedStatus()
        var internationalClassId = "0"
        var eventType = ""
        var classCard = ""
        val sides = ArrayList<Pair<Side, Boolean>>()
    }

    private fun event(reader: JsonReader, updatedMs: Long): Event? {
        val b = EventBuilder()
        Json.obj(reader) { name ->
            when (name) {
                "id" -> b.id = Json.string(reader)
                "date" -> b.date = Json.string(reader)
                "endDate" -> b.endDate = Json.string(reader)
                "status" -> b.status = EspnCommon.status(reader)
                "competitions" -> Json.array(reader) { competition(reader, b) }
                else -> reader.skipValue()
            }
        }
        if (b.id.isEmpty() || b.sides.size < 2) return null
        val homeIndex = b.sides.indexOfFirst { it.second }.coerceAtLeast(0)
        val home = b.sides[homeIndex].first
        val away = b.sides[if (homeIndex == 0) 1 else 0].first
        val status = if (b.status.state.isEmpty()) b.competitionStatus else b.status
        val state = StateNormaliser.normalise(status)
        return Event(
            id = b.id,
            sport = Sport.CRICKET,
            competitionId = "",
            competition = "",
            format = format(b),
            state = state,
            statusDetail = status.description,
            startMs = EspnCommon.epochMs(b.date),
            endMs = if (state == State.FINAL) EspnCommon.epochMs(b.endDate).takeIf { it > 0 } else null,
            home = home,
            away = away,
            summary = status.summary,
            updatedMs = updatedMs,
            session = b.description,
            period = status.period,
            international = b.internationalClassId.isNotEmpty() && b.internationalClassId != "0",
        )
    }

    private fun format(b: EventBuilder): String {
        INTERNATIONAL_FORMATS[b.internationalClassId]?.let { return it }
        return when (b.classCard) {
            "First-class", "List A" -> b.classCard
            "Twenty20" -> "T20"
            else -> b.eventType.ifEmpty { b.classCard }
        }
    }

    private fun competition(reader: JsonReader, b: EventBuilder) {
        Json.obj(reader) { name ->
            when (name) {
                "description" -> b.description = Json.string(reader)
                "status" -> b.competitionStatus = EspnCommon.status(reader)
                "class" -> Json.obj(reader) { c ->
                    when (c) {
                        "internationalClassId" -> b.internationalClassId = Json.string(reader)
                        "eventType" -> b.eventType = Json.string(reader)
                        "generalClassCard" -> b.classCard = Json.string(reader)
                        else -> reader.skipValue()
                    }
                }
                "competitors" -> Json.array(reader) { b.sides.add(competitor(reader)) }
                else -> reader.skipValue()
            }
        }
    }

    /** One `competitors[]` element: the side and whether the feed calls it `home`. */
    private fun competitor(reader: JsonReader): Pair<Side, Boolean> {
        var id = ""
        var name = ""
        var short = ""
        var abbr = ""
        var score = ""
        var home = false
        var winner = false
        val innings = ArrayList<Innings>()
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
                "linescores" -> Json.array(reader) { innings.add(innings(reader)) }
                else -> reader.skipValue()
            }
        }
        // The feed lists one linescore per period for both sides; keep the ones this side batted in, then
        // mark the declared ones from the score string (`356 & 398/6d`: the second innings this side batted).
        val declared = declaredInnings(score)
        val batted = innings.filter { it.isBatting || it.runs > 0 || it.wickets > 0 }
            .mapIndexed { i, inn -> if (i in declared) inn.copy(declared = true) else inn }
        val side = Side(
            id = id,
            name = name,
            abbr = abbr.ifEmpty { EspnCommon.abbreviate(name) },
            shortName = short.ifEmpty { name },
            score = score,
            innings = batted,
            isBatting = innings.any { it.isCurrent && it.isBatting },
            winner = winner,
        )
        return side to home
    }

    private fun innings(reader: JsonReader): Innings {
        var runs = 0
        var wickets = 0
        var overs = 0.0
        var batting = false
        var current = false
        Json.obj(reader) { name ->
            when (name) {
                "runs" -> runs = Json.int(reader)
                "wickets" -> wickets = Json.int(reader)
                "overs" -> overs = Json.double(reader)
                "isBatting" -> batting = Json.bool(reader)
                "isCurrent" -> current = Json.bool(reader)
                else -> reader.skipValue()
            }
        }
        return Innings(runs, wickets, overs, isCurrent = current, isBatting = batting)
    }

    /** Which of this side's innings the feed's score string marks declared: `356 & 398/6d` → {1}. */
    private fun declaredInnings(score: String): Set<Int> {
        if (score.isEmpty()) return emptySet()
        val parts = score.substringBefore('(').split('&')
        return parts.indices.filter { parts[it].trim().endsWith("d") }.toSet()
    }
}
