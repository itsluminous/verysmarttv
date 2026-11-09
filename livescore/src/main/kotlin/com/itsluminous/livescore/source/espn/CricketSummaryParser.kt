package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.model.DetailLine

/**
 * The cricket `summary` endpoint (§13.6), read for one open scoreboard only: the players `rosters[]`
 * marks `active` are the current batters and bowlers, with their figures in the last innings they
 * appear in. 264 KB measured for a Test; every other part (`commentaries`, `news`, `videos`, `odds`,
 * `matchcards`) is skipped unread.
 */
internal object CricketSummaryParser {
    /** `activeName` values the feed uses, in the order the lines are shown. */
    private val ROLES = listOf("striker", "non-striker", "current bowler", "previous bowler")

    fun parse(reader: JsonReader): List<DetailLine> {
        val lines = ArrayList<Pair<Int, DetailLine>>()
        Json.obj(reader) { name ->
            if (name == "rosters") Json.array(reader) { roster(reader, lines) } else reader.skipValue()
        }
        return lines.sortedBy { it.first }.map { it.second }
    }

    private fun roster(reader: JsonReader, out: MutableList<Pair<Int, DetailLine>>) {
        var teamAbbr = ""
        val players = ArrayList<Pair<Int, DetailLine>>()
        Json.obj(reader) { n ->
            when (n) {
                "team" -> Json.obj(reader) { t -> if (t == "abbreviation") teamAbbr = Json.string(reader) else reader.skipValue() }
                "roster" -> Json.array(reader) { player(reader)?.let(players::add) }
                else -> reader.skipValue()
            }
        }
        players.mapTo(out) { (rank, line) -> rank to line.copy(primary = if (teamAbbr.isEmpty()) line.primary else "${line.primary} ($teamAbbr)") }
    }

    private class Figures {
        var runs = ""
        var balls = ""
        var fours = ""
        var sixes = ""
        var overs = ""
        var conceded = ""
        var wickets = ""
    }

    private fun player(reader: JsonReader): Pair<Int, DetailLine>? {
        var active = false
        var role = ""
        var name = ""
        var figures = Figures()
        Json.obj(reader) { n ->
            when (n) {
                "active" -> active = Json.bool(reader)
                "activeName" -> role = Json.string(reader)
                "athlete" -> Json.obj(reader) { a -> if (a == "displayName") name = Json.string(reader) else reader.skipValue() }
                // One entry per innings; the last one holds the current figures.
                "linescores" -> Json.array(reader) { figures = inningsFigures(reader) }
                else -> reader.skipValue()
            }
        }
        val rank = ROLES.indexOf(role)
        if (!active || rank < 0 || name.isEmpty()) return null
        val bowling = role.endsWith("bowler")
        val text = if (bowling) {
            "${figures.wickets}/${figures.conceded} (${figures.overs})"
        } else {
            "${figures.runs} (${figures.balls}b ${figures.fours}x4 ${figures.sixes}x6)"
        }
        return rank to DetailLine(name, text)
    }

    private fun inningsFigures(reader: JsonReader): Figures {
        val f = Figures()
        Json.obj(reader) { n ->
            if (n == "linescores") Json.array(reader) { stats(reader, f) } else reader.skipValue()
        }
        return f
    }

    private fun stats(reader: JsonReader, f: Figures) {
        Json.obj(reader) { n ->
            if (n != "statistics") {
                reader.skipValue()
            } else {
                Json.obj(reader) { s ->
                    if (s != "categories") {
                        reader.skipValue()
                    } else {
                        Json.array(reader) {
                            Json.obj(reader) { c -> if (c == "stats") Json.array(reader) { stat(reader, f) } else reader.skipValue() }
                        }
                    }
                }
            }
        }
    }

    private fun stat(reader: JsonReader, f: Figures) {
        var name = ""
        var value = ""
        Json.obj(reader) { n ->
            when (n) {
                "name" -> name = Json.string(reader)
                "displayValue" -> value = Json.string(reader)
                else -> reader.skipValue()
            }
        }
        when (name) {
            "runs" -> f.runs = value
            "ballsFaced" -> f.balls = value
            "fours" -> f.fours = value
            "sixes" -> f.sixes = value
            "overs" -> f.overs = value
            "conceded" -> f.conceded = value
            "wickets" -> f.wickets = value
        }
    }
}
