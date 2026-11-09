package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import java.io.InputStream
import java.io.InputStreamReader

/**
 * The static catalogue (§13.3): which competitions each sport has and which teams a competition lists,
 * from `res/raw/espn_catalogue.json`, written by `tools/dev/record_fixtures.sh --catalogue`. No network;
 * the picker and the tests read the same file.
 */
class EspnCatalogue private constructor(
    private val competitions: Map<Sport, List<Competition>>,
    private val teams: Map<String, List<Team>>,
) {
    fun competitions(sport: Sport): List<Competition> = competitions[sport].orEmpty()

    fun teams(competition: Competition): List<Team> = teams["${competition.sport.token}:${competition.id}"].orEmpty()

    /** Every competition of every sport, for looking one up by sport and id. */
    fun find(sport: Sport, id: String): Competition? = competitions(sport).firstOrNull { it.id == id }

    companion object {
        fun load(stream: InputStream): EspnCatalogue {
            val competitions = HashMap<Sport, List<Competition>>()
            val teams = HashMap<String, List<Team>>()
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                Json.obj(reader) { sportName ->
                    val sport = Sport.fromToken(sportName)
                    if (sport == null) {
                        reader.skipValue()
                    } else {
                        val list = ArrayList<Competition>()
                        Json.array(reader) { competition(reader, sport, list, teams) }
                        competitions[sport] = list
                    }
                }
            }
            return EspnCatalogue(competitions, teams)
        }

        private fun competition(reader: JsonReader, sport: Sport, out: MutableList<Competition>, teams: MutableMap<String, List<Team>>) {
            var id = ""
            var name = ""
            var short = ""
            val list = ArrayList<Team>()
            Json.obj(reader) { n ->
                when (n) {
                    "id" -> id = Json.string(reader)
                    "name" -> name = Json.string(reader)
                    "short" -> short = Json.string(reader)
                    "teams" -> Json.array(reader) { team(reader, sport)?.let(list::add) }
                    else -> reader.skipValue()
                }
            }
            if (id.isEmpty()) return
            out.add(Competition(sport, id, name, short.ifEmpty { name }))
            teams["${sport.token}:$id"] = list
        }

        private fun team(reader: JsonReader, sport: Sport): Team? {
            var id = ""
            var name = ""
            var abbr = ""
            Json.obj(reader) { n ->
                when (n) {
                    "id" -> id = Json.string(reader)
                    "name" -> name = Json.string(reader)
                    "abbr" -> abbr = Json.string(reader)
                    else -> reader.skipValue()
                }
            }
            return if (id.isEmpty()) null else Team(sport, id, name, abbr.ifEmpty { EspnCommon.abbreviate(name) })
        }
    }
}
