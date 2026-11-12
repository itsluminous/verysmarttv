package com.itsluminous.livescore.source.bwf

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BWF's Match Centre JSON (ADR-049): the recorded tournament list, the recorded empty match list, and the hand-made
 * match list written from the page's own template. One event per match on court, in the SportMicro shape.
 */
@RunWith(RobolectricTestRunner::class)
class BwfParserTest {
    private val now = 1_794_324_000_000L // 2026-11-10 23:20 UTC, when the list was recorded
    private val kumamoto = BwfTournament("5216", "Kumamoto Masters Japan 2026", BwfParser.dayMs("2026-11-10 00:00:00"), BwfParser.dayMs("2026-11-15 00:00:00"))

    @Test
    fun recordedTournamentListGivesTheTwoTournamentsOfTheWeek() {
        val list = BwfParser.parseTournaments(Fixtures.reader("bwf/current-live-20261110.json"))
        assertEquals(listOf("5216", "5652"), list.map { it.id })
        assertEquals("Kumamoto Masters Japan 2026", list[0].name)
        assertEquals("VICTOR Kaohsiung Masters 2026", list[1].name)
        assertEquals(1_794_268_800_000L, list[0].startMs) // 2026-11-10T00:00Z
        assertEquals(1_794_700_800_000L, list[0].endMs) // 2026-11-15T00:00Z
    }

    @Test
    fun recordedEmptyMatchListGivesNoEvents() {
        assertTrue(BwfParser.parseLiveMatches(Fixtures.reader("bwf/live-matches-empty-20261110.json"), kumamoto, now).isEmpty())
    }

    @Test
    fun aPlayingSinglesIsLiveInItsSecondGameWithTheSurnamesTheWayBwfWritesThem() {
        val e = events()[0]
        assertEquals("bwf-B7C1A2D3-0001", e.id)
        assertEquals(Sport.BADMINTON, e.sport)
        assertEquals(State.LIVE, e.state)
        assertEquals("bwf-5216", e.competitionId)
        assertEquals("Kumamoto Masters Japan 2026", e.competition)
        assertEquals("MS - R32 · Game 2", e.statusDetail)
        assertEquals("Court 1", e.session)
        assertEquals(2, e.period)
        assertEquals("LEE Zii Jia", e.home.name)
        assertEquals("Lee", e.home.shortName)
        assertEquals("LEE", e.home.abbr)
        assertEquals("Naraoka", e.away.shortName)
        assertEquals("NAR", e.away.abbr)
        assertEquals("21 11", e.home.score)
        assertEquals("18 9", e.away.score)
        assertEquals(listOf(DetailLine("Game 1", "21-18"), DetailLine("Game 2", "11-9")), e.details)
        assertEquals("57945", e.home.id)
        assertEquals(now - now % 86_400_000L, e.startMs) // started today: the day's start, the tournament began earlier
        assertEquals(null, e.endMs)
        assertEquals(now, e.updatedMs)
        assertFalse(e.home.winner || e.away.winner)
    }

    @Test
    fun aGameOverWithNoNextGameBegunIsTheInterval() {
        val e = events()[1]
        assertEquals(State.BREAK, e.state)
        assertEquals("WD - R32 · Interval", e.statusDetail)
        assertEquals("Matsuyama / Shida", e.home.shortName)
        assertEquals("MAT/SHI", e.home.abbr)
        assertEquals("Jolly / Gopichand", e.away.shortName)
        assertEquals("Nami MATSUYAMA / Chiharu SHIDA", e.home.name)
        assertEquals("60000/60001", e.home.id)
        assertEquals("21", e.home.score)
        assertEquals("17", e.away.score)
    }

    @Test
    fun aFinishedMatchNamesTheWinnerAndEndsAtTheFetch() {
        val e = events()[2]
        assertEquals(State.FINAL, e.state)
        assertEquals("WS - R32 · Finished", e.statusDetail)
        assertTrue(e.home.winner)
        assertFalse(e.away.winner)
        assertEquals("An", e.home.shortName)
        assertEquals("Katethong", e.away.shortName)
        assertEquals("21 21", e.home.score)
        assertEquals(now, e.endMs)
    }

    @Test
    fun aRetirementKeepsTheWordForTheLine() {
        val e = events()[3]
        assertEquals(State.FINAL, e.state)
        assertEquals("MD - R32 · Retired", e.statusDetail)
        assertTrue(e.home.winner)
        assertEquals("Chia / Soh", e.home.shortName)
        assertEquals("Liang / Wang", e.away.shortName)
        assertEquals("21 8", e.home.score)
    }

    @Test
    fun aWarmUpWithNoPointsIsUpcoming() {
        val e = events()[4]
        assertEquals(State.UPCOMING, e.state)
        assertEquals("XD - R32 · Warm up", e.statusDetail)
        assertEquals("", e.home.score)
        assertEquals("Puavaranukroh / Taerattanachai", e.home.shortName)
        assertEquals(0, e.period)
    }

    @Test
    fun aMatchWithoutACodeOrASideIsDroppedAndUnknownNamesAreSkipped() {
        val json = """{"results":[{"match_detail":{"code":"","t1p1_player_model":{"name_short1":"A"},"t2p1_player_model":{"name_short1":"B"}},"live_detail":{}},
            {"match_detail":{"code":"x","t1p1_player_model":{"name_short1":"Only ONE"},"t2p1_player_model":null,"extra":{"deep":[1,2,{"a":null}]}},"live_detail":{"match_state":"P","odd":true}},
            {"match_detail":{"code":"ok","t1p1_player_model":{"name_short1":"Victor Lai"},"t2p1_player_model":{"name_short1":"He Z-W"}},"live_detail":{"match_state":"P","team1_g1_score":"5","team2_g1_score":3},"winner":null}]}"""
        val events = BwfParser.parseLiveMatches(Fixtures.readerOf(json), kumamoto, now)
        assertEquals(listOf("bwf-ok"), events.map { it.id })
        assertEquals("Lai", events[0].home.shortName) // no capitalised word: SportMicro's rule
        assertEquals("He", events[0].away.shortName)
        assertEquals(State.LIVE, events[0].state)
        assertEquals("Game 1", events[0].statusDetail)
    }

    @Test
    fun withNoStateWordsTheWinnerOrThePointsDecide() {
        val json = """{"results":[{"match_detail":{"code":"w","t1p1_player_model":{"name_short1":"A B"},"t2p1_player_model":{"name_short1":"C D"}},"live_detail":{"team1_g1_score":21,"team2_g1_score":9,"team1_g2_score":21,"team2_g2_score":7},"winner":1},
            {"match_detail":{"code":"n","t1p1_player_model":{"name_short1":"A B"},"t2p1_player_model":{"name_short1":"C D"}},"live_detail":{}},
            {"match_detail":{"code":"s","t1p1_player_model":{"name_short1":"A B"},"t2p1_player_model":{"name_short1":"C D"}},"live_detail":{"match_state":"S","match_state_name":"Suspended","team1_g1_score":4,"team2_g1_score":2}}]}"""
        val events = BwfParser.parseLiveMatches(Fixtures.readerOf(json), kumamoto, now)
        assertEquals(listOf(State.FINAL, State.UPCOMING, State.BREAK), events.map { it.state })
        assertTrue(events[0].home.winner)
        assertEquals("Suspended", events[2].statusDetail)
    }

    @Test
    fun aTournamentListInAnotherShapeIsEmptyNotAnError() {
        assertTrue(BwfParser.parseTournaments(Fixtures.readerOf("""{"results":{"id":1}}""")).isEmpty())
        assertTrue(BwfParser.parseTournaments(Fixtures.readerOf("""[]""")).isEmpty())
        assertTrue(BwfParser.parseTournaments(Fixtures.readerOf("""{"results":[{"name":"no id"}]}""")).isEmpty())
        assertEquals(0L, BwfParser.dayMs("soon"))
        assertEquals(0L, BwfParser.dayMs(""))
    }

    @Test
    fun recordedTournamentListOfTheSecondDayHasFourTournaments() {
        val list = BwfParser.parseTournaments(Fixtures.reader("bwf/current-live-20261112.json"))
        assertEquals(listOf("5216", "5652", "5773", "5844"), list.map { it.id })
        assertEquals("Kumamoto Masters Japan 2026", list[0].name)
    }

    @Test
    fun recordedKumamotoMatchesReadTheDisplayNamesAndTheGameUnderWay() {
        val events = BwfParser.parseLiveMatches(Fixtures.reader("bwf/live-matches-5216-20261112.json"), kumamoto, now)
        assertEquals(listOf("bwf-177", "bwf-97", "bwf-21"), events.map { it.id })
        assertEquals(listOf(State.LIVE, State.LIVE, State.LIVE), events.map { it.state })
        val doubles = events[0]
        assertEquals("Hsiang Chieh CHIU / WANG Chi-Lin", doubles.home.name) // name_display, not the initials of name_short1
        assertEquals("Chiu / Wang", doubles.home.shortName)
        assertEquals("CHI/WAN", doubles.home.abbr)
        assertEquals("Okamura / Yamashita", doubles.away.shortName)
        assertEquals("MD - R16 · Game 1", doubles.statusDetail)
        assertEquals("16", doubles.home.score)
        assertEquals("14", doubles.away.score)
        assertEquals("Court 1", doubles.session)
        val third = events[2]
        assertEquals("Farhan", third.home.shortName)
        assertEquals("21 6 11", third.home.score)
        assertEquals("13 21 3", third.away.score)
        assertEquals("MS - R16 · Game 3", third.statusDetail)
        assertEquals(3, third.period)
    }

    @Test
    fun recordedWaitingToStartAtOneGameEachIsTheIntervalNotAThirdGameAtLoveAll() {
        val events = BwfParser.parseLiveMatches(Fixtures.reader("bwf/live-matches-5844-20261112.json"), BwfTournament("5844", "Guwahati Masters 2026", 0L, 0L), now)
        val waiting = events[0]
        assertEquals("bwf-27", waiting.id)
        assertEquals(State.BREAK, waiting.state) // match_state S, "Waiting to start", team1_g3_score 0: the next game has not begun
        assertEquals("MS - R32 · Interval", waiting.statusDetail)
        assertEquals("22 13", waiting.home.score)
        assertEquals("20 21", waiting.away.score)
        assertEquals(2, waiting.period)
        assertEquals(listOf(DetailLine("Game 1", "22-20"), DetailLine("Game 2", "13-21")), waiting.details)
        assertEquals("Buarung", waiting.home.shortName)
        assertEquals("Kachain BUARUNG", waiting.home.name)
        assertEquals(State.LIVE, events[1].state)
        assertEquals("MS - R32 · Game 1", events[1].statusDetail)
        assertEquals("Tan", events[1].away.shortName) // `TAN Y X ` with a trailing space
        assertEquals("MS - R32 · Game 2", events[2].statusDetail)
        assertEquals("13 9", events[2].home.score)
    }

    @Test
    fun aMatchJustStartedUnderPKeepsItsLoveAllGameAndAWarmUpStartsNow() {
        val json = """{"results":[{"match_detail":{"code":"p","t1p1_player_model":{"name_display":"A B"},"t2p1_player_model":{"name_display":"C D"}},"live_detail":{"match_state":"P","match_state_name":"In Progress","team1_g1_score":21,"team2_g1_score":13,"team1_g2_score":0,"team2_g2_score":0}},
            {"match_detail":{"code":"w","t1p1_player_model":{"name_display":"A B"},"t2p1_player_model":{"name_display":"C D"}},"live_detail":{"match_state":"W","match_state_name":"Warm Up","team1_g1_score":0,"team2_g1_score":0}}]}"""
        val events = BwfParser.parseLiveMatches(Fixtures.readerOf(json), kumamoto, now)
        assertEquals(State.LIVE, events[0].state)
        assertEquals("Game 2", events[0].statusDetail)
        assertEquals("21 0", events[0].home.score)
        assertEquals(State.UPCOMING, events[1].state)
        assertEquals("Warm Up", events[1].statusDetail)
        assertEquals("", events[1].home.score)
        assertEquals(now, events[1].startMs) // on court, not begun: starts about now, not at the day's start
    }

    private fun events() = BwfParser.parseLiveMatches(Fixtures.reader("bwf/live-matches-handmade.json"), kumamoto, now)
}
