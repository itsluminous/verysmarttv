package com.itsluminous.livescore.source.sportmicro

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SportMicro's day list as recorded on 2026-11-10 (ADR-044): the flat `matches-by-date` shape the source reads, the
 * nested `/matches` shape, and the flat edge fixtures. One event per match, games as the score string.
 */
@RunWith(RobolectricTestRunner::class)
class SportMicroParserTest {
    private val now = 1_794_300_000_000L

    @Test
    fun recordedDayListGivesOneEventPerMatchWithGamesAndNames() {
        val events = SportMicroParser.parse(Fixtures.reader("badminton/matches-20261110.json"), now)
        assertEquals(listOf("132958", "132962", "132893", "132896", "132912"), events.map { it.id })
        val done = events[0]
        assertEquals(Sport.BADMINTON, done.sport)
        assertEquals(State.FINAL, done.state)
        assertEquals("128995", done.competitionId)
        assertEquals("Kumamoto Masters Japan, WS, Qualifying", done.competition)
        assertEquals("", done.session) // the league `Kumamoto Masters Japan - WS` says nothing the tournament does not
        assertEquals("Ended", done.statusDetail) // the feed has no round
        assertEquals(3, done.period)
        assertEquals("Matsuta N.", done.home.name)
        assertEquals("Matsuta", done.home.shortName)
        assertEquals("MAT", done.home.abbr)
        assertEquals("21 10 11", done.home.score)
        assertEquals("15 21 21", done.away.score)
        assertEquals("Kantemyr", done.away.shortName)
        assertEquals(listOf(DetailLine("Game 1", "21-15"), DetailLine("Game 2", "10-21"), DetailLine("Game 3", "11-21")), done.details)
        assertEquals(1_794_271_724_000L, done.startMs) // specific_start_time 00:48:44Z, not the session's start_time 00:00Z
        assertEquals(done.startMs + 90L * 60_000L, done.endMs)
        assertEquals(now, done.updatedMs)
        assertFalse(done.home.winner)
        assertTrue(done.away.winner) // away_team_score 2 to home_team_score 1
        // The discipline is a word of the league (`… - WS`); the feed has no nationality, so the side is one player with none (ADR-051).
        assertEquals(Discipline.WS, done.discipline)
        assertEquals(listOf(Player("68902", "Matsuta N.", "")), done.home.players)
        assertEquals(Discipline.XD, events.first { it.competition.contains("XD") }.discipline)
    }

    @Test
    fun theDisciplineIsReadFromTheLeagueOrTheTournamentWordsOnly() {
        assertEquals(Discipline.MD, SportMicroParser.discipline("Kumamoto Masters Japan - MD"))
        assertEquals(Discipline.XD, SportMicroParser.discipline("Kumamoto Masters Japan, XD, Qualifying"))
        assertEquals(Discipline.WS, SportMicroParser.discipline("Japan Masters – WS"))
        assertNull("Masters is not MS", SportMicroParser.discipline("Kumamoto Masters Japan"))
        assertNull(SportMicroParser.discipline(""))
    }

    @Test
    fun doublesPairsReadAsSurnamesWithOrWithoutDots() {
        val events = SportMicroParser.parse(Fixtures.reader("badminton/matches-20261110.json"), now)
        val doubles = events[1]
        assertEquals("Panev / Stoeva", doubles.home.shortName)
        assertEquals("PAN/STO", doubles.home.abbr)
        assertEquals("Yu / Tse", doubles.away.shortName)
        assertEquals("7 13", doubles.home.score)
        assertTrue(doubles.away.winner)
        val upcomingPair = events[3]
        assertEquals("Kawabe / Ishikawa", upcomingPair.home.shortName)
        assertEquals("Yang / HU", upcomingPair.away.shortName) // `Yang P-H / HU L F`: hyphenated initials, an upper-case surname
    }

    @Test
    fun upcomingAndWalkoverRows() {
        val events = SportMicroParser.parse(Fixtures.reader("badminton/matches-20261110.json"), now)
        val up = events[2]
        assertEquals(State.UPCOMING, up.state)
        assertEquals("", up.home.score)
        assertEquals("Not started", up.statusDetail)
        assertEquals("Lai", up.home.shortName) // `Victor Lai`: a full name, the surname last
        assertEquals("Kumamoto Masters Japan, MS", up.competition)
        assertEquals(1_794_272_400_000L, up.startMs) // no specific_start_time yet: start_time 01:00Z
        assertNull(up.endMs)
        assertTrue(up.details.isEmpty())
        val wo = events[4]
        assertEquals(State.FINAL, wo.state)
        assertEquals("Walkover", wo.statusDetail)
        assertEquals("", wo.home.score)
        assertFalse(wo.home.winner) // the feed names no winner for a walkover: no games, no points
        assertFalse(wo.away.winner)
        assertTrue(wo.details.isEmpty())
    }

    @Test
    fun nestedMatchesShapeParsesTheSameMatch() {
        val events = SportMicroParser.parse(Fixtures.reader("badminton/matches-nested-20261110.json"), now)
        assertEquals(5, events.size)
        val done = events[0]
        assertEquals("132958", done.id)
        assertEquals(State.FINAL, done.state)
        assertEquals("Ended", done.statusDetail)
        assertEquals("21 10 11", done.home.score)
        assertEquals("15 21 21", done.away.score)
        assertTrue(done.away.winner) // `current` 2 to 1
        assertEquals(1_794_271_724_000L, done.startMs) // the top-level specific_start_time wins over times.specific_start_time
        val up = events[3]
        assertEquals(State.UPCOMING, up.state)
        assertEquals("Not started", up.statusDetail)
        val extraPoints = events[2] // 22-20, 21-x: a game past 21
        assertTrue(extraPoints.home.winner)
    }

    @Test
    fun canceledIsFinalWithTheWord() {
        val e = SportMicroParser.parse(Fixtures.reader("badminton/edge-canceled.json"), now).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Canceled", e.statusDetail)
        assertEquals("", e.home.score)
        assertFalse(e.home.winner)
        assertFalse(e.away.winner)
    }

    @Test
    fun aRetirementTheFeedCallsEndedTakesItsWinnerFromTheGamesWon() {
        val e = SportMicroParser.parse(Fixtures.reader("badminton/edge-ended-unfinished-game.json"), now).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Ended", e.statusDetail)
        assertEquals("21 19", e.home.score)
        assertEquals("19 8", e.away.score)
        assertTrue(e.home.winner) // home_team_score 1, away 0, though the second game never reached 21
        assertFalse(e.away.winner)
    }

    @Test
    fun retirementKeepsThePointsPlayed() {
        val e = SportMicroParser.parse(Fixtures.reader("badminton/edge-retired.json"), now).single()
        assertEquals(State.FINAL, e.state)
        assertEquals("Retired", e.statusDetail)
        assertEquals("21 5", e.home.score)
        assertEquals("18 3", e.away.score)
        assertTrue(e.home.winner)
    }

    @Test
    fun liveRowsReadTheGameInProgress() {
        val events = SportMicroParser.parse(Fixtures.reader("badminton/edge-live.json"), now)
        val live = events[0]
        assertEquals(State.LIVE, live.state)
        assertEquals("Game 2", live.statusDetail)
        assertEquals(2, live.period)
        assertEquals("Sindhu", live.home.shortName)
        assertEquals("21 11", live.home.score)
        assertEquals("18 9", live.away.score)
        assertEquals("Kumamoto Masters Japan, WS", live.competition)
        assertEquals(1_794_280_360_000L, live.startMs)
        assertNull(live.endMs)
        assertFalse(live.home.winner)
        val pair = events[1]
        assertEquals("Chang / Yang", pair.home.shortName)
        assertEquals("CHA/YAN", pair.home.abbr)
        assertEquals("Kusuma / Puspitasari", pair.away.shortName)
        assertEquals("17", pair.home.score)
        assertEquals("Game 1", pair.statusDetail)
    }

    @Test
    fun decidingGameIsLiveInGameThree() {
        val e = SportMicroParser.parse(Fixtures.reader("badminton/edge-deciding-game.json"), now).single()
        assertEquals(State.LIVE, e.state)
        assertEquals(3, e.period)
        assertEquals("Game 3", e.statusDetail)
        assertEquals("21 17 19", e.home.score)
        assertEquals("19 21 19", e.away.score)
    }

    @Test
    fun aFinishedGameWithNoNextOneIsTheInterval() {
        val e = SportMicroParser.parse(Fixtures.reader("badminton/edge-between-games.json"), now).single()
        assertEquals(State.BREAK, e.state)
        assertEquals("Interval", e.statusDetail)
        assertEquals("21", e.home.score)
        assertEquals("15", e.away.score)
    }

    @Test
    fun gameOverRule() {
        assertTrue(SportMicroParser.gameOver(21, 15))
        assertTrue(SportMicroParser.gameOver(19, 21))
        assertTrue(SportMicroParser.gameOver(30, 29))
        assertFalse(SportMicroParser.gameOver(21, 20))
        assertFalse(SportMicroParser.gameOver(19, 19))
        assertFalse(SportMicroParser.gameOver(0, 0))
    }

    @Test
    fun surnamesAndAbbreviations() {
        assertEquals("Sindhu", SportMicroParser.surnames("Sindhu P. V."))
        assertEquals("Sindhu", SportMicroParser.surnames("Pusarla Venkata Sindhu"))
        assertEquals("Axelsen", SportMicroParser.surnames("Axelsen V."))
        assertEquals("Pong", SportMicroParser.surnames("Pong L K K"))
        assertEquals("He", SportMicroParser.surnames("He Z-W"))
        assertEquals("Xu", SportMicroParser.surnames("Wen Jing Xu."))
        assertEquals("Chang / Yang", SportMicroParser.surnames("Chang C. H./Yang C. T."))
        assertEquals("Chang / Yang", SportMicroParser.surnames("Chang C. H. / Yang C. T."))
        assertEquals("Lai / Lai", SportMicroParser.surnames("Lai J B T / Lai C"))
        assertEquals("AXE", SportMicroParser.abbreviate("Axelsen V."))
        assertEquals("CHA/YAN", SportMicroParser.abbreviate("Chang C. H./Yang C. T."))
        assertEquals("", SportMicroParser.surnames(""))
    }

    @Test
    fun aBareArrayOfMatchesAndAnEmptyBodyParseToo() {
        val one = SportMicroParser.parse(Fixtures.readerOf("""[{"id":1,"name":"Sen L. vs Axelsen V.","status":"upcoming","start_time":"2026-11-10T06:00:00+00:00"}]"""), now).single()
        assertEquals("Sen L.", one.home.name)
        assertEquals("Axelsen V.", one.away.name)
        assertEquals("Sen L. vs Axelsen V.", one.competition) // no tournament name: the match name stands in
        assertEquals(State.UPCOMING, one.state)
        assertTrue(SportMicroParser.parse(Fixtures.readerOf("[]"), now).isEmpty())
        assertTrue(SportMicroParser.parse(Fixtures.readerOf("""{"message":"You are not subscribed to this API."}"""), now).isEmpty())
        assertTrue(SportMicroParser.parse(Fixtures.readerOf("""[{"id":2,"status":"live"}]"""), now).isEmpty()) // no names
    }

    @Test
    fun anEndingGivenOnlyAsTheTypeIsTheDetailAndTheWinnerComesFromThePoints() {
        // The normaliser accepts `walkover` / `retired` as the type itself (ADR-044); the formatter needs the word in the detail
        // and a winner, so both are derived when the feed gives neither a reason nor the games won. Both shapes.
        val wo = SportMicroParser.parse(
            Fixtures.readerOf("""[{"id":3,"home_team_name":"Christie J.","away_team_name":"Antonsen A.","status":{"type":"walkover"},"round":{"name":"Round of 16"},
                "home_team_score":{"period_1":0},"away_team_score":{"period_1":21}}]"""),
            now,
        ).single()
        assertEquals(State.FINAL, wo.state)
        assertEquals("Round of 16 · Walkover", wo.statusDetail)
        assertTrue(wo.away.winner)
        assertFalse(wo.home.winner)
        val ret = SportMicroParser.parse(
            Fixtures.readerOf("""[{"id":4,"home_team_name":"Christie J.","away_team_name":"Antonsen A.","status":"retired",
                "home_team_period_1_score":21,"home_team_period_2_score":5,"away_team_period_1_score":18,"away_team_period_2_score":3}]"""),
            now,
        ).single()
        assertEquals(State.FINAL, ret.state)
        assertEquals("Retired", ret.statusDetail)
        assertTrue(ret.home.winner) // one game to none from the points: no games-won number
        assertFalse(ret.away.winner)
        val paused = SportMicroParser.parse(Fixtures.readerOf("""[{"id":5,"name":"A B vs C D","status":"suspended","home_team_period_1_score":11,"away_team_period_1_score":9}]"""), now).single()
        assertEquals(State.BREAK, paused.state)
        assertEquals("Suspended", paused.statusDetail)
        assertEquals(1, SportMicroParser.gamesWon(listOf(21, 19, 11), listOf(18, 21, 9)))
        assertEquals(0, SportMicroParser.gamesWon(listOf(20), listOf(19))) // not over yet
    }

    @Test
    fun aLeagueThatPrefixesTheTournamentIsCutAndKeptAsTheSession() {
        val e = SportMicroParser.parse(
            Fixtures.readerOf("""[{"id":6,"name":"A B vs C D","status":"upcoming","tournament_name":"BWF World Tour, Japan Masters","league_name":"BWF World Tour"}]"""),
            now,
        ).single()
        assertEquals("Japan Masters", e.competition)
        assertEquals("", e.session) // the tournament already names the league
        val other = SportMicroParser.parse(
            Fixtures.readerOf("""[{"id":7,"name":"A B vs C D","status":"upcoming","tournament_name":"Japan Masters, MS","league_name":"BWF World Tour - MS"}]"""),
            now,
        ).single()
        assertEquals("Japan Masters, MS", other.competition)
        assertEquals("BWF World Tour - MS", other.session)
    }
}
