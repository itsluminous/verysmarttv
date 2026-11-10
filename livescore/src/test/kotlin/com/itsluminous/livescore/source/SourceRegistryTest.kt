package com.itsluminous.livescore.source

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each sport goes to the first source that claims it; a sport nobody claims answers empty (§13.3, ADR-044). */
class SourceRegistryTest {
    private class Scripted(override val id: String, private val claimed: List<Sport>) : ScoreSource {
        val fetched = ArrayList<Sport>()
        override fun sports() = claimed
        override fun competitions(sport: Sport) = listOf(Competition(sport, "$id-${sport.token}", "$id ${sport.token}"))
        override fun teams(competition: Competition) = listOf(Team(competition.sport, "$id-t", "Team of $id", "T"))
        override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> {
            fetched.add(sport)
            return listOf(event(sport, "$id-$day"))
        }
        override fun event(event: Event): Event? = event.copy(summary = id)
    }

    private val espn = Scripted("espn", listOf(Sport.CRICKET, Sport.FOOTBALL, Sport.F1, Sport.TENNIS))
    private val sportmicro = Scripted("sportmicro", listOf(Sport.BADMINTON))
    private val registry = SourceRegistry(listOf(espn, sportmicro))

    @Test
    fun badmintonGoesToTheSecondSourceAndTheRestToEspn() {
        assertEquals("espn+sportmicro", registry.id)
        assertEquals(Sport.entries.toList(), registry.sports())
        assertSame(sportmicro, registry.forSport(Sport.BADMINTON))
        assertSame(espn, registry.forSport(Sport.CRICKET))
        assertEquals("sportmicro-20261110", registry.scoreboard(Sport.BADMINTON, null, "20261110").single().id)
        assertEquals("espn-20261110", registry.scoreboard(Sport.TENNIS, null, "20261110").single().id)
        assertEquals(listOf(Sport.BADMINTON), sportmicro.fetched)
        assertEquals(listOf(Sport.TENNIS), espn.fetched)
        assertEquals("sportmicro-badminton", registry.competitions(Sport.BADMINTON).single().id)
        assertEquals("Team of espn", registry.teams(Competition(Sport.FOOTBALL, "eng.1", "PL")).single().name)
        assertEquals("sportmicro", registry.event(event(Sport.BADMINTON, "x"))?.summary)
    }

    @Test
    fun theFirstClaimantWinsAndAnUnclaimedSportIsEmpty() {
        val both = SourceRegistry(listOf(Scripted("a", listOf(Sport.TENNIS)), Scripted("b", listOf(Sport.TENNIS))))
        assertEquals("a", both.forSport(Sport.TENNIS)?.id)
        assertEquals(listOf(Sport.TENNIS), both.sports())
        assertNull(both.forSport(Sport.BADMINTON))
        assertTrue(both.competitions(Sport.BADMINTON).isEmpty())
        assertTrue(both.scoreboard(Sport.BADMINTON, null, "20261110").isEmpty())
        assertTrue(both.teams(Competition(Sport.BADMINTON, "7001", "Japan Masters")).isEmpty())
        assertNull(both.event(event(Sport.BADMINTON, "x")))
    }

    private companion object {
        fun event(sport: Sport, id: String) = Event(id, sport, "c", "C", "", State.UPCOMING, "", 1L, null, Side("a", "A", "A"), Side("b", "B", "B"), "", 1L)
    }
}
