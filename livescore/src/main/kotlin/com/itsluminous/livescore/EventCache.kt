package com.itsluminous.livescore

import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

/**
 * The parsed events the companion holds (§13.5 memory budget: tens of objects), in memory and in
 * `events.json`, so `/pill` answers from disk right after a process start without a fetch. A fetch
 * replaces the events of its [FetchKey]; an event seen `final` keeps the `endMs` of the fetch that first
 * saw it final, which is what "shown for 6 h after the end" counts from. Events older than the window
 * (yesterday 00:00 local) are dropped on every merge.
 */
class EventCache(private val file: File) {
    private val lock = Any()
    private var events: MutableList<Event>? = null
    private val fetched = HashMap<FetchKey, Long>()

    fun events(): List<Event> = synchronized(lock) { load().toList() }

    fun fetchedMs(): Map<FetchKey, Long> = synchronized(lock) { load(); HashMap(fetched) }

    fun event(id: String): Event? = synchronized(lock) { load().firstOrNull { it.id == id } }

    /** Replaces the events of [key] with [fresh], stamps the fetch time, drops what left the window, and saves. */
    fun merge(key: FetchKey, fresh: List<Event>, nowMs: Long, windowStartMs: Long) {
        synchronized(lock) {
            val list = load()
            val old = list.associateBy { it.id }
            list.removeAll { FetchKey.of(it) == key }
            for (e in fresh) {
                val previous = old[e.id]
                val merged = if (e.state == State.FINAL) {
                    val end = if (previous?.state == State.FINAL) previous.endMs else minOf(e.endMs ?: nowMs, nowMs)
                    e.copy(endMs = end)
                } else {
                    e
                }
                // A detail fetch (batters, scorers) is kept until the next one replaces it.
                list.add(if (merged.details.isEmpty() && previous != null && previous.details.isNotEmpty() && previous.state == merged.state) merged.copy(details = previous.details) else merged)
            }
            list.removeAll { (it.endMs ?: it.startMs) < windowStartMs && it.state == State.FINAL || it.startMs < windowStartMs - DAY_MS && it.state == State.UPCOMING }
            fetched[key] = nowMs
            save(list)
        }
    }

    /** Replaces one event (a detail fetch) in place and saves. */
    fun replace(event: Event) {
        synchronized(lock) {
            val list = load()
            val index = list.indexOfFirst { it.id == event.id }
            if (index >= 0) list[index] = event else list.add(event)
            save(list)
        }
    }

    private fun load(): MutableList<Event> {
        events?.let { return it }
        val list = ArrayList<Event>()
        try {
            if (file.isFile) decode(file.readText(), list, fetched)
        } catch (e: IOException) {
            Log.w(TAG, "cache unreadable: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cache corrupt: ${e.message}")
            list.clear()
            fetched.clear()
        }
        events = list
        return list
    }

    private fun save(list: List<Event>) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(list, fetched))
            if (!tmp.renameTo(file)) {
                file.writeText(encode(list, fetched))
                tmp.delete()
            }
        } catch (e: IOException) {
            Log.w(TAG, "cache not saved: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveScore.Cache"
        const val FILE_NAME = "events.json"
        private const val DAY_MS = 24L * 3600_000L

        fun encode(list: List<Event>, fetched: Map<FetchKey, Long>): String {
            val out = StringWriter()
            JsonWriter(out).use { w ->
                w.beginObject()
                w.name("fetched").beginObject()
                for ((k, v) in fetched) w.name(k.token).value(v)
                w.endObject()
                w.name("events").beginArray()
                for (e in list) event(w, e)
                w.endArray()
                w.endObject()
            }
            return out.toString()
        }

        private fun event(w: JsonWriter, e: Event) {
            w.beginObject()
            w.name("id").value(e.id)
            w.name("sport").value(e.sport.token)
            w.name("competitionId").value(e.competitionId)
            w.name("competition").value(e.competition)
            w.name("format").value(e.format)
            w.name("state").value(e.state.token)
            w.name("statusDetail").value(e.statusDetail)
            w.name("startMs").value(e.startMs)
            if (e.endMs != null) w.name("endMs").value(e.endMs)
            w.name("home")
            side(w, e.home)
            w.name("away")
            side(w, e.away)
            w.name("summary").value(e.summary)
            w.name("updatedMs").value(e.updatedMs)
            w.name("session").value(e.session)
            w.name("period").value(e.period)
            w.name("clock").value(e.clock)
            w.name("international").value(e.international)
            if (e.discipline != null) w.name("discipline").value(e.discipline.token)
            w.name("details").beginArray()
            for (d in e.details) {
                w.beginObject()
                w.name("p").value(d.primary)
                w.name("s").value(d.secondary)
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }

        private fun side(w: JsonWriter, s: Side) {
            w.beginObject()
            w.name("id").value(s.id)
            w.name("name").value(s.name)
            w.name("abbr").value(s.abbr)
            w.name("shortName").value(s.shortName)
            w.name("score").value(s.score)
            w.name("isBatting").value(s.isBatting)
            w.name("winner").value(s.winner)
            w.name("innings").beginArray()
            for (i in s.innings) {
                w.beginObject()
                w.name("runs").value(i.runs)
                w.name("wickets").value(i.wickets)
                w.name("overs").value(i.overs)
                w.name("declared").value(i.declared)
                w.name("isCurrent").value(i.isCurrent)
                w.name("isBatting").value(i.isBatting)
                w.endObject()
            }
            w.endArray()
            w.name("players").beginArray()
            for (p in s.players) {
                w.beginObject()
                w.name("id").value(p.id)
                w.name("name").value(p.name)
                w.name("country").value(p.country)
                w.endObject()
            }
            w.endArray()
            w.endObject()
        }

        fun decode(json: String, out: MutableList<Event>, fetched: MutableMap<FetchKey, Long>) {
            JsonReader(StringReader(json)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "fetched" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val token = r.nextName()
                                val ms = r.nextLong()
                                val sport = Sport.fromToken(token.substringBefore(':'))
                                if (sport != null) fetched[FetchKey(sport, token.substringAfter(':').takeIf { it != "*" })] = ms
                            }
                            r.endObject()
                        }
                        "events" -> {
                            r.beginArray()
                            while (r.hasNext()) readEvent(r)?.let(out::add)
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
        }

        private fun readEvent(r: JsonReader): Event? {
            var id = ""
            var sport: Sport? = null
            var competitionId = ""
            var competition = ""
            var format = ""
            var state: State? = null
            var statusDetail = ""
            var startMs = 0L
            var endMs: Long? = null
            var home = Side("", "", "")
            var away = Side("", "", "")
            var summary = ""
            var updatedMs = 0L
            var session = ""
            var period = 0
            var clock = ""
            var international = false
            var discipline: Discipline? = null
            val details = ArrayList<DetailLine>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = r.nextString()
                    "sport" -> sport = Sport.fromToken(r.nextString())
                    "competitionId" -> competitionId = r.nextString()
                    "competition" -> competition = r.nextString()
                    "format" -> format = r.nextString()
                    "state" -> state = State.fromToken(r.nextString())
                    "statusDetail" -> statusDetail = r.nextString()
                    "startMs" -> startMs = r.nextLong()
                    "endMs" -> endMs = r.nextLong()
                    "home" -> home = readSide(r)
                    "away" -> away = readSide(r)
                    "summary" -> summary = r.nextString()
                    "updatedMs" -> updatedMs = r.nextLong()
                    "session" -> session = r.nextString()
                    "period" -> period = r.nextInt()
                    "clock" -> clock = r.nextString()
                    "international" -> international = r.nextBoolean()
                    "discipline" -> discipline = Discipline.fromToken(r.nextString())
                    "details" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var p = ""
                            var s = ""
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "p" -> p = r.nextString()
                                    "s" -> s = r.nextString()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            details.add(DetailLine(p, s))
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isEmpty() || sport == null || state == null) return null
            return Event(id, sport, competitionId, competition, format, state, statusDetail, startMs, endMs, home, away, summary, updatedMs, session, period, clock, details, international, discipline)
        }

        private fun readSide(r: JsonReader): Side {
            var id = ""
            var name = ""
            var abbr = ""
            var shortName = ""
            var score = ""
            var isBatting = false
            var winner = false
            val innings = ArrayList<Innings>()
            val players = ArrayList<Player>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = r.nextString()
                    "name" -> name = r.nextString()
                    "abbr" -> abbr = r.nextString()
                    "shortName" -> shortName = r.nextString()
                    "score" -> score = r.nextString()
                    "isBatting" -> isBatting = r.nextBoolean()
                    "winner" -> winner = r.nextBoolean()
                    "innings" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var runs = 0
                            var wickets = 0
                            var overs = 0.0
                            var declared = false
                            var current = false
                            var batting = false
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "runs" -> runs = r.nextInt()
                                    "wickets" -> wickets = r.nextInt()
                                    "overs" -> overs = r.nextDouble()
                                    "declared" -> declared = r.nextBoolean()
                                    "isCurrent" -> current = r.nextBoolean()
                                    "isBatting" -> batting = r.nextBoolean()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            innings.add(Innings(runs, wickets, overs, declared, current, batting))
                        }
                        r.endArray()
                    }
                    "players" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var pid = ""
                            var pname = ""
                            var country = ""
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "id" -> pid = r.nextString()
                                    "name" -> pname = r.nextString()
                                    "country" -> country = r.nextString()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            players.add(Player(pid, pname, country))
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            return Side(id, name, abbr, shortName.ifEmpty { name }, score, innings, isBatting, winner, players)
        }
    }
}
