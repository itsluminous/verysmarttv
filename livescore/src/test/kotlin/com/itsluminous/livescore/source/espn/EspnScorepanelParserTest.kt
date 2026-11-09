package com.itsluminous.livescore.source.espn

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cricket scorepanel of 2026-11-09 (truncated to 3 events per league) and the hand-made edge fixtures. */
@RunWith(RobolectricTestRunner::class)
class EspnScorepanelParserTest {
    private val events by lazy { CricketParser.parse(Fixtures.reader("cricket/scorepanel-${Fixtures.DAY}.json"), Fixtures.RECORDED_MS) }

    @Test
    fun everyLeagueOfTheDayIsRead() {
        assertEquals(20, events.size)
        assertEquals(11, events.map { it.competitionId }.distinct().size)
        assertTrue(events.all { it.updatedMs == Fixtures.RECORDED_MS })
    }

    @Test
    fun statesFollowTheTable() {
        assertEquals(State.LIVE, byId("1561257").state) // Abu Dhabi T10, in / Live, period 1
        assertEquals(State.BREAK, byId("1550711").state) // PAK v SL, in / Stumps
        assertEquals(State.UPCOMING, byId("1561217").state) // Quaid-e-Azam, in / Live, period 0, "Starts at 10:00"
        assertEquals(State.UPCOMING, byId("1558813").state) // WI-A v LIONS, pre
        assertEquals(State.FINAL, byId("1553296").state) // BAN v WI, post
        assertEquals(4, events.count { it.state == State.LIVE })
        assertEquals(5, events.count { it.state == State.BREAK })
    }

    @Test
    fun testInningsCarryBothInningsAndTheDeclaration() {
        val e = byId("1553296")
        assertEquals("Test", e.format)
        assertTrue(e.international)
        assertEquals("BAN", e.home.abbr)
        assertEquals("386 & 184/4 (59 ov, target 369)", e.home.score)
        assertEquals(listOf(Innings(386, 9, 111.5, isBatting = true), Innings(184, 4, 59.0, isCurrent = true, isBatting = true)), e.home.innings)
        assertEquals("356 & 398/6d", e.away.score)
        assertEquals(2, e.away.innings.size)
        assertTrue(e.away.innings[1].declared)
        assertFalse(e.away.innings[0].declared)
        assertEquals("Match drawn", e.summary)
        assertEquals("2nd Test", e.session)
    }

    @Test
    fun limitedOversSideKnowsWhoIsBatting() {
        val e = byId("1561257")
        assertTrue(e.home.isBatting)
        assertFalse(e.away.isBatting)
        assertEquals(Innings(33, 3, 4.2, isCurrent = true, isBatting = true), e.home.innings.single())
        assertTrue(e.away.innings.isEmpty())
        assertEquals("Tigers won toss & fielded", e.summary)
        assertEquals("Abu Dhabi T10", e.competition)
        assertEquals("18522", e.competitionId)
    }

    @Test
    fun formatsComeFromTheInternationalClassElseTheCard() {
        assertEquals("Youth ODI", byId("1558439").format) // internationalClassId 15
        assertEquals("ODI", byId("1558438").format) // class 0, eventType ODI, card "Other OD"
        assertEquals("First-class", byId("1555685").format)
        assertEquals("T20", byId("1558600").format) // Twenty20
        assertEquals("List A", byId("1558813").format)
        assertFalse(byId("1555685").international)
    }

    @Test
    fun timesAreParsedWithoutSeconds() {
        val e = byId("1561256")
        assertEquals(1_794_231_900_000L, e.startMs) // 2026-11-09T13:45Z
        assertEquals(1_794_441_540_000L, e.endMs) // final: the feed's 2026-11-11T23:59Z
        assertNull(byId("1561257").endMs)
    }

    @Test
    fun rainDelayIsABreakWithTheScoreKept() {
        val e = CricketParser.parse(Fixtures.reader("cricket/edge-rain-delay.json"), 1L).single()
        assertEquals(State.BREAK, e.state)
        assertEquals("Rain", e.statusDetail)
        assertEquals("T20I", e.format)
        assertEquals("19/4 (6/20 ov)", e.home.score)
        assertTrue(e.home.isBatting)
    }

    @Test
    fun superOverIsLiveInItsThirdPeriod() {
        val e = CricketParser.parse(Fixtures.reader("cricket/edge-super-over.json"), 1L).single()
        assertEquals(State.LIVE, e.state)
        assertEquals(3, e.period)
        assertEquals(2, e.home.innings.size)
        assertEquals(Innings(8, 0, 0.4, isCurrent = true, isBatting = true), e.home.innings[1])
        assertTrue(e.home.isBatting)
    }

    @Test
    fun abandonedWhileStillInIsFinal() {
        val e = CricketParser.parse(Fixtures.reader("cricket/edge-abandoned.json"), 1L).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Match abandoned without a ball bowled", e.summary)
        assertTrue(e.home.innings.isEmpty())
    }

    @Test
    fun tieIsFinalWithBothScores() {
        val e = CricketParser.parse(Fixtures.reader("cricket/edge-tie.json"), 1L).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Match tied", e.summary)
        assertEquals(240, e.home.innings.single().runs)
        assertEquals(240, e.away.innings.single().runs)
    }

    private fun byId(id: String) = events.first { it.id == id }
}
