package com.itsluminous.livescore.source.espn

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.R
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The soccer, F1 and tennis scoreboards of 2026-11-09 and their hand-made edge fixtures. */
@RunWith(RobolectricTestRunner::class)
class EspnScoreboardParserTest {
    private val pl = Competition(Sport.FOOTBALL, "eng.1", "Premier League")
    private val f1 = Competition(Sport.F1, "f1", "Formula 1", "F1")
    private val atp = Competition(Sport.TENNIS, "atp", "ATP Tour", "ATP")

    @Test
    fun soccerFullTimeRowsCarryScoresAndGoals() {
        val events = SoccerParser.parse(Fixtures.reader("football/eng1-${Fixtures.DAY}.json"), pl, 1L)
        assertEquals(3, events.size)
        val e = events.first { it.id == "401879244" }
        assertEquals(State.FINAL, e.state)
        assertEquals("FT", e.statusDetail)
        assertEquals("Premier League", e.competition)
        assertEquals("eng.1", e.competitionId)
        assertEquals("BHA", e.home.abbr)
        assertEquals("Brighton", e.home.shortName)
        assertEquals("2", e.home.score)
        assertEquals("0", e.away.score)
        assertTrue(e.home.winner)
        assertEquals(DetailLine("15' Diego Gómez", "BHA · Goal"), e.details[0])
        assertEquals(DetailLine("23' Igor Thiago", "BRE · Red Card"), e.details[1])
        assertEquals(1_794_146_400_000L, e.startMs) // 2026-11-08T14:00Z
        assertEquals(e.startMs + 2 * 3_600_000L, e.endMs)
    }

    @Test
    fun soccerLiveHalftimeAndScheduled() {
        val events = SoccerParser.parse(Fixtures.reader("football/edge-live.json"), pl, 1L)
        val live = events.first { it.id == "9200001" }
        assertEquals(State.LIVE, live.state)
        assertEquals("67'", live.clock)
        assertEquals("67'", live.statusDetail)
        assertEquals(listOf("12' Bukayo Saka", "44' Cole Palmer", "61' Gabriel"), live.details.map { it.primary })
        val ht = events.first { it.id == "9200002" }
        assertEquals(State.BREAK, ht.state)
        assertEquals("HT", ht.statusDetail)
        val pre = events.first { it.id == "9200003" }
        assertEquals(State.UPCOMING, pre.state)
        assertNull(pre.endMs)
        assertEquals("Man United", pre.home.shortName)
    }

    @Test
    fun f1WeekendIsOneEventOnTheLastSessionOnceOver() {
        val e = F1Parser.parse(Fixtures.reader("f1/f1-${Fixtures.DAY}.json"), f1, 1L, 1_794_300_000_000L).single()
        assertEquals("600057448", e.id)
        assertEquals(State.FINAL, e.state)
        assertEquals("Race", e.session)
        assertEquals("MSC Cruises São Paulo GP", e.home.shortName)
        assertEquals("Lando Norris", e.home.score)
        assertEquals("NOR", e.home.abbr)
        assertTrue(e.home.winner)
        assertEquals(DetailLine("FP1 · P1", "Lando Norris"), e.details.first())
        assertEquals(50, e.details.size) // 5 sessions x top 10
        assertEquals(1_794_157_200_000L, e.startMs) // the race, 2026-11-08T17:00Z
    }

    @Test
    fun f1RedFlagIsABreakOnTheCurrentLap() {
        val e = F1Parser.parse(Fixtures.reader("f1/edge-red-flag.json"), f1, 1L, 1L).single()
        assertEquals(State.BREAK, e.state)
        assertEquals("Race", e.session)
        assertEquals(32, e.period)
        assertEquals("Red Flag", e.statusDetail)
        assertEquals("VER", e.home.abbr)
        assertEquals("Max Verstappen", e.home.score)
    }

    @Test
    fun tennisMatchesCarrySetsAndTiebreaks() {
        val events = TennisParser.parse(Fixtures.reader("tennis/atp-${Fixtures.DAY}.json"), atp, 1L)
        assertEquals(6, events.size)
        val e = events.first { it.id == "187453" }
        assertEquals("Bybit Stockholm Open", e.competition)
        assertEquals("atp", e.competitionId)
        assertEquals("Men's Singles", e.session)
        assertEquals(State.FINAL, e.state)
        assertEquals("Mika Brunold", e.home.name)
        assertEquals("Brunold", e.home.shortName)
        assertEquals("5 6 6(7)", e.home.score)
        assertEquals("7 4 7(9)", e.away.score)
        assertTrue(e.away.winner)
        assertEquals(listOf(DetailLine("Set 1", "5-7"), DetailLine("Set 2", "6-4"), DetailLine("Set 3", "6(7)-7(9)")), e.details)
        assertEquals("Borna Gojo (CRO) bt Mika Brunold (SUI) 7-5 4-6 7-6 (9-7)", e.summary)
        val doubles = events.first { it.id == "186555" }
        assertEquals("N.Sriram Balaji / Evan King", doubles.home.name)
        assertEquals("Balaji / King", doubles.home.shortName)
    }

    @Test
    fun tennisWalkoverIsFinalAndALiveMatchKnowsItsSet() {
        val events = TennisParser.parse(Fixtures.reader("tennis/edge-walkover.json"), atp, 1L)
        val wo = events.first { it.id == "9100001" }
        assertEquals(State.FINAL, wo.state)
        assertEquals("Round 2 · Walkover", wo.statusDetail)
        assertTrue(wo.details.isEmpty())
        val live = events.first { it.id == "9100002" }
        assertEquals(State.LIVE, live.state)
        assertEquals("Round 2 · Set 3", live.statusDetail)
        assertEquals("6 3 2", live.home.score)
    }

    @Test
    fun cricketSummaryGivesTheFourActivePlayers() {
        val lines = CricketSummaryParser.parse(Fixtures.reader("cricket/summary-${Fixtures.DAY}.json"))
        assertEquals(
            listOf(
                DetailLine("Ghazi Ghori (PAK)", "7 (10b 0x4 0x6)"),
                DetailLine("Saud Shakeel (PAK)", "57 (112b 7x4 0x6)"),
                DetailLine("Asitha Fernando (SL)", "2/64 (15.2)"),
                DetailLine("Lahiru Kumara (SL)", "0/70 (16)"),
            ),
            lines,
        )
    }

    @Test
    fun catalogueLoadsEverySportFromTheRawResource() {
        val raw = RuntimeEnvironment.getApplication().resources.openRawResource(R.raw.espn_catalogue)
        val c = raw.use { EspnCatalogue.load(it) }
        assertEquals("international", c.competitions(Sport.CRICKET).first().id)
        assertEquals(12, c.teams(c.competitions(Sport.CRICKET).first()).size)
        assertEquals("6", c.teams(c.competitions(Sport.CRICKET).first()).first { it.abbr == "IND" }.id)
        assertEquals("8048", c.find(Sport.CRICKET, "8048")!!.id)
        assertEquals(listOf("ind.1", "eng.1", "uefa.champions"), c.competitions(Sport.FOOTBALL).map { it.id }.take(3))
        assertTrue(c.teams(c.find(Sport.FOOTBALL, "eng.1")!!).size >= 20)
        assertEquals(listOf("f1"), c.competitions(Sport.F1).map { it.id })
        assertEquals(listOf("atp", "wta"), c.competitions(Sport.TENNIS).map { it.id })
    }

    @Test
    fun urlsPerSportAndTennisOnlyWithACompetition() {
        assertEquals("https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=20261109", EspnSource.url(Sport.CRICKET, null, "20261109"))
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard?dates=20261109", EspnSource.url(Sport.FOOTBALL, pl, "20261109"))
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates=20261108-20261110", EspnSource.url(Sport.F1, f1, "20261108-20261110"))
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard?dates=20261108-20261110", EspnSource.url(Sport.TENNIS, atp, "20261108-20261110"))
        assertNull(EspnSource.url(Sport.TENNIS, null, "20261109"))
        assertNull(EspnSource.url(Sport.FOOTBALL, null, "20261109"))
        assertEquals("https://site.web.api.espn.com/apis/site/v2/sports/cricket/24707/summary?event=1550711", EspnSource.summaryUrl("24707", "1550711"))
    }
}
