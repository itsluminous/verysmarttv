package com.itsluminous.livescore.source.sportdevs

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The hand-made SportDevs day list and its edge fixtures (ADR-044): one event per match, games as the score string. */
@RunWith(RobolectricTestRunner::class)
class SportDevsParserTest {
    private val now = 1_794_300_000_000L

    @Test
    fun dayListGivesOneEventPerMatchWithGamesAndNames() {
        val events = SportDevsParser.parse(Fixtures.reader("badminton/sample-20261110.json"), now)
        assertEquals(listOf("900001", "900002", "900003", "900004"), events.map { it.id })
        val live = events[0]
        assertEquals(Sport.BADMINTON, live.sport)
        assertEquals(State.LIVE, live.state)
        assertEquals("7001", live.competitionId)
        assertEquals("Japan Masters, Women Singles", live.competition)
        assertEquals("", live.session) // the league name is inside the tournament name, not repeated
        assertEquals("Round of 16 · Game 2", live.statusDetail)
        assertEquals(2, live.period)
        assertEquals("Sindhu P. V.", live.home.name)
        assertEquals("Sindhu", live.home.shortName)
        assertEquals("SIN", live.home.abbr)
        assertEquals("21 11", live.home.score)
        assertEquals("18 9", live.away.score)
        assertEquals("An", live.away.shortName)
        assertEquals(listOf(DetailLine("Game 1", "21-18"), DetailLine("Game 2", "11-9")), live.details)
        assertEquals(1_794_282_000_000L, live.startMs) // 2026-11-10T03:40Z
        assertNull(live.endMs)
        assertEquals(now, live.updatedMs)
        assertFalse(live.home.winner)
    }

    @Test
    fun doublesPairsReadAsSurnames() {
        val events = SportDevsParser.parse(Fixtures.reader("badminton/sample-20261110.json"), now)
        val doubles = events[1]
        assertEquals("Chang / Yang", doubles.home.shortName)
        assertEquals("CHA/YAN", doubles.home.abbr)
        assertEquals("Kusuma / Puspitasari", doubles.away.shortName)
        assertEquals("17", doubles.home.score)
        assertEquals("Round of 16 · Game 1", doubles.statusDetail)
    }

    @Test
    fun upcomingAndFinishedRows() {
        val events = SportDevsParser.parse(Fixtures.reader("badminton/sample-20261110.json"), now)
        val up = events[2]
        assertEquals(State.UPCOMING, up.state)
        assertEquals("", up.home.score)
        assertEquals("Round of 16 · Not started", up.statusDetail)
        assertNull(up.endMs)
        val done = events[3]
        assertEquals(State.FINAL, done.state)
        assertEquals("21 21", done.home.score)
        assertEquals("15 19", done.away.score)
        assertTrue(done.home.winner)
        assertFalse(done.away.winner)
        assertEquals("Round of 32 · Ended", done.statusDetail)
        assertEquals(done.startMs + 90L * 60_000L, done.endMs)
    }

    @Test
    fun walkoverIsFinalWithTheWinnerAndNoPoints() {
        val e = SportDevsParser.parse(Fixtures.reader("badminton/edge-walkover.json"), now).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Round of 16 · Walkover", e.statusDetail)
        assertEquals("", e.home.score)
        assertTrue(e.away.winner)
        assertFalse(e.home.winner)
        assertTrue(e.details.isEmpty())
    }

    @Test
    fun retirementKeepsThePointsPlayed() {
        val e = SportDevsParser.parse(Fixtures.reader("badminton/edge-retired.json"), now).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Round of 16 · Retired", e.statusDetail)
        assertEquals("21 5", e.home.score)
        assertEquals("18 3", e.away.score)
        assertTrue(e.home.winner)
    }

    @Test
    fun decidingGameIsLiveInGameThree() {
        val e = SportDevsParser.parse(Fixtures.reader("badminton/edge-deciding-game.json"), now).single()
        assertEquals(State.LIVE, e.state)
        assertEquals(3, e.period)
        assertEquals("Round of 16 · Game 3", e.statusDetail)
        assertEquals("21 17 19", e.home.score)
        assertEquals("19 21 19", e.away.score)
    }

    @Test
    fun aFinishedGameWithNoNextOneIsTheInterval() {
        val e = SportDevsParser.parse(Fixtures.reader("badminton/edge-between-games.json"), now).single()
        assertEquals(State.BREAK, e.state)
        assertEquals("Round of 16 · Interval", e.statusDetail)
        assertEquals("21", e.home.score)
        assertEquals("15", e.away.score)
    }

    @Test
    fun gameOverRule() {
        assertTrue(SportDevsParser.gameOver(21, 15))
        assertTrue(SportDevsParser.gameOver(19, 21))
        assertTrue(SportDevsParser.gameOver(30, 29))
        assertFalse(SportDevsParser.gameOver(21, 20))
        assertFalse(SportDevsParser.gameOver(19, 19))
        assertFalse(SportDevsParser.gameOver(0, 0))
    }

    @Test
    fun surnamesAndAbbreviations() {
        assertEquals("Sindhu", SportDevsParser.surnames("Sindhu P. V."))
        assertEquals("Sindhu", SportDevsParser.surnames("Pusarla Venkata Sindhu"))
        assertEquals("Axelsen", SportDevsParser.surnames("Axelsen V."))
        assertEquals("Chang / Yang", SportDevsParser.surnames("Chang C. H./Yang C. T."))
        assertEquals("Chang / Yang", SportDevsParser.surnames("Chang C. H. / Yang C. T."))
        assertEquals("AXE", SportDevsParser.abbreviate("Axelsen V."))
        assertEquals("CHA/YAN", SportDevsParser.abbreviate("Chang C. H./Yang C. T."))
        assertEquals("", SportDevsParser.surnames(""))
    }

    @Test
    fun aBareArrayOfMatchesAndAnEmptyBodyParseToo() {
        val one = SportDevsParser.parse(Fixtures.readerOf("""[{"id":1,"name":"Sen L. vs Axelsen V.","status":{"type":"upcoming"},"start_time":"2026-11-10T06:00:00+00:00"}]"""), now).single()
        assertEquals("Sen L.", one.home.name)
        assertEquals("Axelsen V.", one.away.name)
        assertEquals("Sen L. vs Axelsen V.", one.competition) // no tournament name: the match name stands in
        assertEquals(State.UPCOMING, one.state)
        assertTrue(SportDevsParser.parse(Fixtures.readerOf("[]"), now).isEmpty())
        assertTrue(SportDevsParser.parse(Fixtures.readerOf("""{"message":"You are not subscribed to this API."}"""), now).isEmpty())
        assertTrue(SportDevsParser.parse(Fixtures.readerOf("""[{"id":2,"status":{"type":"live"}}]"""), now).isEmpty()) // no names
    }
}
