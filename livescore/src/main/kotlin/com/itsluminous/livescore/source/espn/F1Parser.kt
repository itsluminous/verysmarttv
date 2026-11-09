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
 * The `racing/f1` scoreboard (§13.1): one `events[]` entry per Grand Prix weekend whose `competitions[]`
 * are the sessions (FP1, FP2, FP3, Sprint, Qual, Race), each with its drivers in finishing order. One
 * [Event] per weekend: its state and leader come from the session in progress, else the next one to run,
 * else the last one finished; the other sessions' results are the detail lines.
 */
internal object F1Parser {
    fun parse(reader: JsonReader, competition: Competition, updatedMs: Long, nowMs: Long): List<Event> {
        val events = ArrayList<Event>()
        Json.obj(reader) { name ->
            if (name == "events") Json.array(reader) { event(reader, competition, updatedMs, nowMs)?.let(events::add) } else reader.skipValue()
        }
        return events
    }

    private class Session(val name: String, val date: String, val status: FeedStatus, val drivers: List<Side>)

    private fun event(reader: JsonReader, competition: Competition, updatedMs: Long, nowMs: Long): Event? {
        var id = ""
        var date = ""
        var endDate = ""
        var shortName = ""
        var name = ""
        var status = FeedStatus()
        val sessions = ArrayList<Session>()
        Json.obj(reader) { n ->
            when (n) {
                "id" -> id = Json.string(reader)
                "date" -> date = Json.string(reader)
                "endDate" -> endDate = Json.string(reader)
                "name" -> name = Json.string(reader)
                "shortName" -> shortName = Json.string(reader)
                "status" -> status = EspnCommon.status(reader)
                "competitions" -> Json.array(reader) { session(reader)?.let(sessions::add) }
                else -> reader.skipValue()
            }
        }
        if (id.isEmpty()) return null
        val current = pick(sessions, nowMs)
        val sessionStatus = current?.status ?: status
        val state = StateNormaliser.normalise(sessionStatus)
        val leader = current?.drivers?.firstOrNull() ?: Side("", "", "")
        val gp = shortName.ifEmpty { name }
        val lines = ArrayList<DetailLine>()
        for (s in sessions) {
            s.drivers.take(TOP_POSITIONS).forEachIndexed { i, d -> lines.add(DetailLine("${s.name} · P${i + 1}", d.name)) }
        }
        return Event(
            id = id,
            sport = Sport.F1,
            competitionId = competition.id,
            competition = competition.name,
            format = "",
            state = state,
            statusDetail = if (state == State.LIVE && sessionStatus.period > 0) "Lap ${sessionStatus.period}" else sessionStatus.detail,
            startMs = EspnCommon.epochMs(current?.date?.ifEmpty { date } ?: date),
            endMs = if (state == State.FINAL) EspnCommon.epochMs(endDate).takeIf { it > 0 } else null,
            home = Side(leader.id, gp, leader.abbr, shortName = gp, score = leader.name, winner = leader.winner),
            away = Side("", "", ""),
            summary = sessionStatus.summary,
            updatedMs = updatedMs,
            session = current?.name ?: "",
            period = sessionStatus.period,
            details = lines,
        )
    }

    private const val TOP_POSITIONS = 10

    /**
     * The session in progress, else the first the feed still calls `pre`, else the last one (the race, once
     * the weekend is over). [nowMs] orders sessions whose status the feed left empty.
     */
    private fun pick(sessions: List<Session>, nowMs: Long): Session? {
        if (sessions.isEmpty()) return null
        sessions.firstOrNull { it.status.state == "in" }?.let { return it }
        sessions.filter { it.status.state == "pre" || it.status.state.isEmpty() && EspnCommon.epochMs(it.date) > nowMs }
            .minByOrNull { EspnCommon.epochMs(it.date) }?.let { return it }
        return sessions.last()
    }

    private fun session(reader: JsonReader): Session? {
        var name = ""
        var date = ""
        var status = FeedStatus()
        val drivers = ArrayList<Pair<Int, Side>>()
        Json.obj(reader) { n ->
            when (n) {
                "date" -> date = Json.string(reader)
                "status" -> status = EspnCommon.status(reader)
                "type" -> Json.obj(reader) { t -> if (t == "abbreviation") name = Json.string(reader) else reader.skipValue() }
                "competitors" -> Json.array(reader) { driver(reader)?.let(drivers::add) }
                else -> reader.skipValue()
            }
        }
        if (name.isEmpty() && drivers.isEmpty()) return null
        return Session(name, date, status, drivers.sortedBy { it.first }.map { it.second })
    }

    private fun driver(reader: JsonReader): Pair<Int, Side>? {
        var id = ""
        var order = Int.MAX_VALUE
        var winner = false
        var name = ""
        var short = ""
        Json.obj(reader) { n ->
            when (n) {
                "id" -> id = Json.string(reader)
                "order" -> order = Json.int(reader, Int.MAX_VALUE)
                "winner" -> winner = Json.bool(reader)
                "athlete" -> Json.obj(reader) { a ->
                    when (a) {
                        "displayName" -> name = Json.string(reader)
                        "shortName" -> short = Json.string(reader)
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        if (name.isEmpty()) return null
        return order to Side(id, name, EspnCommon.abbreviate(name), shortName = short.ifEmpty { name }, winner = winner)
    }
}
