package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.ScoreSource
import java.io.IOException

/** A scripted [ScoreSource]: events per sport, a counter of fetches, and a switch that makes every fetch fail. */
class FakeSource : ScoreSource {
    val events = HashMap<Sport, MutableList<Event>>()
    val fetched = ArrayList<Pair<Sport, String?>>()
    var failing = false

    override val id = "fake"

    override fun sports() = Sport.entries

    override fun competitions(sport: Sport): List<Competition> =
        when (sport) {
            Sport.CRICKET -> listOf(Competition(Sport.CRICKET, "8048", "Indian Premier League", "IPL"))
            Sport.FOOTBALL -> listOf(Competition(Sport.FOOTBALL, "ind.1", "ISL"), Competition(Sport.FOOTBALL, "eng.1", "Premier League"), Competition(Sport.FOOTBALL, "uefa.champions", "UCL"))
            Sport.F1 -> listOf(Competition(Sport.F1, "f1", "Formula 1", "F1"))
            Sport.TENNIS -> listOf(Competition(Sport.TENNIS, "atp", "ATP"))
            Sport.BADMINTON -> emptyList()
        }

    override fun teams(competition: Competition): List<Team> = emptyList()

    override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> {
        fetched.add(sport to competition?.id)
        if (failing) throw IOException("scripted failure")
        return events[sport].orEmpty().filter { competition == null || it.competitionId == competition.id }
    }

    override fun event(event: Event): Event? = null
}
