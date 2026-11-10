package com.itsluminous.livescore

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.ScoreSource
import java.io.File

/**
 * Installs a [ScoreRepo] with a scripted source into the app under test, so the activities and the
 * provider read known events without the network. The files go under a test-only directory and the
 * real repo is put back in `@After`.
 */
object TestRepo {
    const val IPL = "8048"
    const val INTERNATIONAL = Competition.INTERNATIONAL
    val now: Long get() = System.currentTimeMillis()

    /** A live IPL T20 at 145/3 after 16.2 overs, CSK batting against MI. */
    fun iplLive(): Event =
        Event(
            "ipl-live", Sport.CRICKET, IPL, "IPL", "T20", State.LIVE, "Live", now - 3_600_000L, null,
            Side("335974", "Chennai Super Kings", "CSK", "Chennai", "145/3 (16.2/20 ov)", listOf(Innings(145, 3, 16.2, isCurrent = true, isBatting = true)), isBatting = true),
            Side("335978", "Mumbai Indians", "MI", "Mumbai", "", listOf(Innings(0, 0, 16.2, isCurrent = true))),
            "", now,
        )

    /** A Test match at stumps with two innings a side, for the innings table. */
    fun testMatch(): Event =
        Event(
            "test-stumps", Sport.CRICKET, "24756", "West Indies in Bangladesh", "Test", State.BREAK, "Stumps", now - 2 * 86_400_000L, null,
            Side("25", "Bangladesh", "BAN", "Bangladesh", "386 & 49/2 (14 ov, target 369)", listOf(Innings(386, 10, 111.5), Innings(49, 2, 14.0, isCurrent = true, isBatting = true)), isBatting = true),
            Side("4", "West Indies", "WI", "West Indies", "356 & 398/6d", listOf(Innings(356, 10, 106.4), Innings(398, 6, 93.0, declared = true))),
            "Bangladesh require 320 runs", now, session = "2nd Test", period = 4, international = true,
        )

    /** A live badminton match in the second game, Sindhu a game up on An, from a tournament the catalogue does not list (ADR-044). */
    fun badmintonLive(): Event =
        Event(
            "bwf-live", Sport.BADMINTON, "7001", "Japan Masters", "", State.LIVE, "Round of 16 · Game 2", now - 1_800_000L, null,
            Side("5001", "Sindhu P. V.", "SIN", "Sindhu", "21 11"),
            Side("5002", "An S. Y.", "AN", "An", "18 9"),
            "", now, period = 2,
        )

    class Source(private val events: List<Event>) : ScoreSource {
        override val id = "test"
        override fun sports() = Sport.entries
        override fun competitions(sport: Sport): List<Competition> =
            when (sport) {
                Sport.CRICKET -> listOf(
                    Competition(Sport.CRICKET, Competition.INTERNATIONAL, "International matches", "Internationals"),
                    Competition(Sport.CRICKET, IPL, "Indian Premier League", "IPL"),
                )
                Sport.FOOTBALL -> listOf(Competition(Sport.FOOTBALL, "eng.1", "Premier League"))
                Sport.F1 -> listOf(Competition(Sport.F1, "f1", "Formula 1", "F1"))
                Sport.TENNIS -> listOf(Competition(Sport.TENNIS, "atp", "ATP Tour", "ATP"))
                Sport.BADMINTON -> emptyList()
            }
        override fun teams(competition: Competition): List<Team> =
            if (competition.id == Competition.INTERNATIONAL) listOf(Team(Sport.CRICKET, "6", "India", "IND"), Team(Sport.CRICKET, "7", "Pakistan", "PAK")) else emptyList()
        override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> =
            events.filter { it.sport == sport && (competition == null || it.competitionId == competition.id) }
        override fun event(event: Event): Event? = null
    }

    /** Installs a repo over [events] with the given follows JSON (null: the defaults) and an empty cache. */
    fun install(events: List<Event>, followsJson: String? = null): ScoreRepo {
        val app: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dir = File(app.filesDir, "test-repo").apply { deleteRecursively(); mkdirs() }
        val follows = FollowStore(File(dir, "follows.json"))
        if (followsJson != null) follows.import(followsJson)
        val repo = ScoreRepo(app, Source(events), follows, EventCache(File(dir, "events.json")), Prefs(app), online = { true })
        ScoreRepo.set(repo)
        return repo
    }

    fun reset() {
        ScoreRepo.set(null)
    }
}
