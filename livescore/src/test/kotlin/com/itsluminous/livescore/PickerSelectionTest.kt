package com.itsluminous.livescore

import com.itsluminous.livescore.SelectionState.ALL
import com.itsluminous.livescore.SelectionState.NONE
import com.itsluminous.livescore.SelectionState.PARTIAL
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker's boxes over a follow list, and the two OK moves (ADR-045). */
class PickerSelectionTest {
    private val internationals = Competition(Sport.CRICKET, Competition.INTERNATIONAL, "International matches")
    private val ipl = Competition(Sport.CRICKET, "8048", "Indian Premier League", "IPL")
    private val india = Team(Sport.CRICKET, "6", "India", "IND")
    private val pakistan = Team(Sport.CRICKET, "7", "Pakistan", "PAK")
    private val match = Event("m1", Sport.CRICKET, Competition.INTERNATIONAL, "Internationals", "ODI", State.UPCOMING, "", 1L, null, Side("6", "India", "IND"), Side("7", "Pakistan", "PAK"), "", 1L, international = true)
    private val children = listOf(Follow.team(india), Follow.team(pakistan), Follow.event(match, 2L))

    @Test
    fun leavesAndCompetitions() {
        val none = PickerSelection(emptyList())
        assertEquals(NONE, none.leaf(Follow.team(india)))
        assertEquals(NONE, none.competition(internationals, children))
        val one = PickerSelection(listOf(Follow.team(india)))
        assertEquals(ALL, one.leaf(Follow.team(india)))
        assertEquals(PARTIAL, one.competition(internationals, children))
        assertEquals(NONE, one.competition(ipl, emptyList()))
        val everyChild = PickerSelection(children)
        assertEquals(ALL, everyChild.competition(internationals, children))
        val whole = PickerSelection(listOf(Follow.competition(internationals)))
        assertEquals(ALL, whole.competition(internationals, children))
        assertEquals(NONE, whole.leaf(Follow.team(india))) // a child's own box shows its own follow
        assertEquals(ALL, PickerSelection(listOf(Follow.competition(ipl))).competition(ipl, emptyList()))
    }

    @Test
    fun sportsCombineTheirCompetitionsAndSeeStrayFollows() {
        val none = PickerSelection(emptyList())
        assertEquals(NONE, none.sport(Sport.CRICKET, listOf(NONE, NONE)))
        assertEquals(PARTIAL, none.sport(Sport.CRICKET, listOf(PARTIAL, NONE)))
        assertEquals(PARTIAL, none.sport(Sport.CRICKET, listOf(ALL, NONE)))
        assertEquals(ALL, none.sport(Sport.CRICKET, listOf(ALL, ALL)))
        assertEquals(ALL, PickerSelection(listOf(Follow.sport(Sport.CRICKET, "Cricket"))).sport(Sport.CRICKET, listOf(NONE, NONE)))
        // A team of a league the catalogue does not list: nothing the columns show is ticked, but the sport is barred.
        val stray = PickerSelection(listOf(Follow.team(Team(Sport.CRICKET, "999", "Lahore Qalandars", "LQ"))))
        assertEquals(PARTIAL, stray.sport(Sport.CRICKET, listOf(NONE, NONE)))
        assertEquals(NONE, stray.sport(Sport.FOOTBALL, listOf(NONE)))
    }

    @Test
    fun selectAllAddsTheSectionAndClearAllRemovesItWithItsChildren() {
        val sel = PickerSelection(listOf(Follow.team(india), Follow.competition(internationals), Follow.competition(ipl), Follow.sport(Sport.FOOTBALL, "Football")))
        val select = sel.selectAll(Follow.competition(internationals))
        assertTrue(select.remove.isEmpty())
        assertEquals(listOf(Follow.competition(internationals)), select.add)
        val clear = sel.clearAll(Follow.competition(internationals), children)
        assertEquals(listOf(Follow.competition(internationals).key, Follow.team(india).key), clear.remove.map { it.key })
        assertTrue(clear.add.isEmpty())
        val sport = sel.clearSport(Sport.CRICKET)
        assertEquals(listOf(Follow.team(india).key, Follow.competition(internationals).key, Follow.competition(ipl).key), sport.remove.map { it.key })
        assertTrue(sport.add.isEmpty())
    }
}
