package com.itsluminous.livescore.source

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import java.io.IOException

/**
 * The sources behind one [ScoreSource] (§13.3, ADR-044): each sport is answered by the first registered
 * source whose [ScoreSource.sports] names it and which [enabled] lets through (the badminton source the user
 * chose, ADR-049; read on every call, so a Settings change needs no rebuild), so the repo, the picker and the
 * provider see one source and never ask which feed a sport comes from. A sport no enabled source claims has no
 * competitions and no events. Pure; `SourceRegistryTest` checks the routing.
 */
class SourceRegistry(private val sources: List<ScoreSource>, private val enabled: (ScoreSource) -> Boolean = { true }) : ScoreSource {
    override val id: String = sources.joinToString("+") { it.id }

    /** Every sport some enabled source claims, in the order of [Sport]. */
    override fun sports(): List<Sport> = Sport.entries.filter { forSport(it) != null }

    /** The enabled source that answers [sport], or null when none does. */
    fun forSport(sport: Sport): ScoreSource? = sources.firstOrNull { enabled(it) && sport in it.sports() }

    override fun competitions(sport: Sport): List<Competition> = forSport(sport)?.competitions(sport).orEmpty()

    override fun teams(competition: Competition): List<Team> = forSport(competition.sport)?.teams(competition).orEmpty()

    @Throws(IOException::class)
    override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> =
        forSport(sport)?.scoreboard(sport, competition, day).orEmpty()

    @Throws(IOException::class)
    override fun event(event: Event): Event? = forSport(event.sport)?.event(event)
}
