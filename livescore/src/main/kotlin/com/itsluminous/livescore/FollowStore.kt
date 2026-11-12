package com.itsluminous.livescore

import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowKind
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

/**
 * The follow list (§13.2): `follows.json` in the app's files dir, at most [MAX] entries, read once and
 * kept in memory. [export] is the exact JSON on disk, what `/backup` hands the launcher; [import] replaces
 * the list from such a JSON (`call("restore")`, ADR-032). A missing or corrupt file means the defaults
 * (ADR-033). Event follows expire at their `expiresMs`; [prune] drops them.
 */
class FollowStore(private val file: File) {
    private val lock = Any()
    private var follows: MutableList<Follow>? = null
    private val listeners = ArrayList<() -> Unit>()

    fun all(): List<Follow> = synchronized(lock) { load().toList() }

    fun isFollowed(follow: Follow): Boolean = synchronized(lock) { load().any { it.key == follow.key } }

    /** Adds or removes [follow]; returns whether it is followed afterwards. */
    fun toggle(follow: Follow): Boolean {
        val now: Boolean
        synchronized(lock) {
            val list = load()
            val index = list.indexOfFirst { it.key == follow.key }
            if (index >= 0) {
                list.removeAt(index)
                now = false
            } else {
                if (list.size >= MAX) list.removeAt(0)
                list.add(follow)
                now = true
            }
            save(list)
        }
        notifyListeners()
        return now
    }

    /**
     * One change of several follows (the picker's select-all and clear-all, ADR-045): [remove] go, then [add] join
     * (those not already in), the list is saved once and listeners hear once. The oldest entries make room at [MAX].
     */
    fun replaceAll(remove: Collection<Follow>, add: Collection<Follow>) {
        synchronized(lock) {
            val list = load()
            val gone = remove.map { it.key }.toSet()
            list.removeAll { it.key in gone }
            for (f in add) {
                if (list.any { it.key == f.key }) continue
                if (list.size >= MAX) list.removeAt(0)
                list.add(f)
            }
            save(list)
        }
        notifyListeners()
    }

    fun clear() {
        synchronized(lock) {
            follows = ArrayList()
            save(follows!!)
        }
        notifyListeners()
    }

    /** Drops event follows past their expiry; returns whether anything changed. */
    fun prune(nowMs: Long): Boolean {
        val changed: Boolean
        synchronized(lock) {
            val list = load()
            changed = list.removeAll { it.kind == FollowKind.EVENT && it.expiresMs in 1..nowMs }
            if (changed) save(list)
        }
        if (changed) notifyListeners()
        return changed
    }

    /** The list as the JSON stored on disk. */
    fun export(): String = synchronized(lock) { encode(load()) }

    /** Replaces the list from [json]; false (and no change) when it does not parse. */
    fun import(json: String): Boolean {
        val parsed = decode(json) ?: return false
        synchronized(lock) {
            follows = parsed.take(MAX).toMutableList()
            save(follows!!)
        }
        notifyListeners()
        return true
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyListeners() {
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it() }
    }

    private fun load(): MutableList<Follow> {
        follows?.let { return it }
        val loaded = try {
            if (file.isFile) decode(file.readText()) else null
        } catch (e: IOException) {
            Log.w(TAG, "follows unreadable: ${e.message}")
            null
        }
        val list = (loaded ?: defaults()).toMutableList()
        if (loaded == null) {
            Log.i(TAG, "follows: defaults")
            save(list)
        }
        follows = list
        return list
    }

    private fun save(list: List<Follow>) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(list))
            if (!tmp.renameTo(file)) {
                file.writeText(encode(list))
                tmp.delete()
            }
        } catch (e: IOException) {
            Log.w(TAG, "follows not saved: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveScore.Follows"
        const val FILE_NAME = "follows.json"
        const val MAX = 100
        private const val VERSION = 1

        /** The first-run list (ADR-033): India and the IPL, ISL + Premier League + Champions League, F1. */
        fun defaults(): List<Follow> =
            listOf(
                Follow.team(Team(Sport.CRICKET, "6", "India", "IND")),
                Follow.competition(Competition(Sport.CRICKET, "8048", "Indian Premier League", "IPL")),
                Follow.competition(Competition(Sport.FOOTBALL, "ind.1", "Indian Super League", "ISL")),
                Follow.competition(Competition(Sport.FOOTBALL, "eng.1", "Premier League")),
                Follow.competition(Competition(Sport.FOOTBALL, "uefa.champions", "Champions League", "UCL")),
                Follow.sport(Sport.F1, "Formula 1"),
            )

        fun encode(list: List<Follow>): String {
            val out = StringWriter()
            JsonWriter(out).use { w ->
                w.beginObject()
                w.name("version").value(VERSION)
                w.name("follows").beginArray()
                for (f in list) {
                    w.beginObject()
                    w.name("kind").value(f.kind.token)
                    w.name("sport").value(f.sport.token)
                    w.name("competition").value(f.competitionId)
                    w.name("team").value(f.teamId)
                    w.name("event").value(f.eventId)
                    w.name("label").value(f.label)
                    w.name("expires").value(f.expiresMs)
                    w.name("country").value(f.country)
                    w.name("disciplines").beginArray()
                    for (d in f.disciplines) w.value(d.token)
                    w.endArray()
                    w.endObject()
                }
                w.endArray()
                w.endObject()
            }
            return out.toString()
        }

        /** The list in [json], or null when it is not a follow list; unknown kinds, sports and disciplines are dropped (a row from before ADR-051 has no country or disciplines). */
        fun decode(json: String): List<Follow>? {
            val list = ArrayList<Follow>()
            var version = -1
            try {
                JsonReader(StringReader(json)).use { r ->
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "version" -> version = r.nextInt()
                            "follows" -> {
                                r.beginArray()
                                while (r.hasNext()) follow(r)?.let(list::add)
                                r.endArray()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
            } catch (e: IOException) {
                return null
            } catch (e: IllegalStateException) {
                return null
            } catch (e: NumberFormatException) {
                return null
            }
            return if (version == VERSION) list else null
        }

        private fun follow(r: JsonReader): Follow? {
            var kind = ""
            var sport = ""
            var competition = ""
            var team = ""
            var event = ""
            var label = ""
            var expires = 0L
            var country = ""
            val disciplines = LinkedHashSet<Discipline>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "kind" -> kind = r.nextString()
                    "sport" -> sport = r.nextString()
                    "competition" -> competition = r.nextString()
                    "team" -> team = r.nextString()
                    "event" -> event = r.nextString()
                    "label" -> label = r.nextString()
                    "expires" -> expires = r.nextLong()
                    "country" -> country = r.nextString()
                    "disciplines" -> {
                        r.beginArray()
                        while (r.hasNext()) Discipline.fromToken(r.nextString())?.let(disciplines::add)
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            val k = FollowKind.fromToken(kind) ?: return null
            val s = Sport.fromToken(sport) ?: return null
            return Follow(k, s, competition, team, event, label, expires, country, disciplines)
        }
    }
}
