package com.itsluminous.livescore

import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** Every example line of §12.2, the kicker per state, and the 48-character limit. */
class LineFormatterTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val f = LineFormatter(zone, Locale.UK)

    // 2026-11-09 19:00 IST (13:30 UTC)
    private val now = 1_794_231_000_000L

    @Test
    fun cricketLimitedOversLive() {
        val e = cricket(
            "T20I", State.LIVE,
            Side("40", "Afghanistan", "AFG", score = "19/4 (6/20 ov)", innings = listOf(Innings(19, 4, 6.0, isCurrent = true, isBatting = true)), isBatting = true),
            Side("9", "Zimbabwe", "ZIM"),
        )
        assertEquals("T20I · AFG 19/4 (6) v ZIM", f.line(e))
        assertEquals("LIVE" to LineFormatter.COLOR_LIVE, f.kicker(e, now))
    }

    @Test
    fun cricketAwaySideBattingComesFirst() {
        val e = cricket(
            "ODI", State.LIVE,
            Side("6", "India", "IND", score = "287/6"),
            Side("2", "Australia", "AUS", score = "120/3 (22.4/50 ov, target 288)", innings = listOf(Innings(120, 3, 22.4, isCurrent = true, isBatting = true)), isBatting = true),
        )
        assertEquals("ODI · AUS 120/3 (22.4) v IND", f.line(e))
    }

    @Test
    fun cricketTestShowsBothInningsNoOvers() {
        val e = cricket(
            "Test", State.BREAK,
            Side("25", "Bangladesh", "BAN", score = "386 & 49/2 (14 ov, target 369)", innings = listOf(Innings(386, 10, 111.5), Innings(49, 2, 14.0, isCurrent = true, isBatting = true)), isBatting = true),
            Side("4", "West Indies", "WI", score = "356 & 398/6d"),
            statusDetail = "Stumps",
        )
        assertEquals("Test · BAN 386 & 49/2 v WI", f.line(e))
        assertEquals("STUMPS" to LineFormatter.COLOR_BREAK, f.kicker(e, now))
    }

    @Test
    fun cricketFinalUsesTheResultSentence() {
        val won = cricket("T20I", State.FINAL, Side("40", "Afghanistan", "AFG"), Side("9", "Zimbabwe", "ZIM"), summary = "AFG won by 23 runs")
        assertEquals("T20I · AFG won by 23 runs", f.line(won))
        assertEquals("RESULT" to LineFormatter.COLOR_GREY, f.kicker(won, now))
        val drawn = cricket("Test", State.FINAL, Side("25", "Bangladesh", "BAN"), Side("4", "West Indies", "WI"), summary = "Match drawn")
        assertEquals("Test · BAN v WI · Match drawn", f.line(drawn))
        val wkts = cricket("ODI", State.FINAL, Side("1", "Arabian Aces", "ARA"), Side("2", "Emirates Eagles", "EME"), summary = "Aces won by 10 wkts (19b rem)")
        assertEquals("ODI · Aces won by 10 wkts", f.line(wkts))
    }

    @Test
    fun cricketUpcomingAndRainBreakWords() {
        val up = cricket("ODI", State.UPCOMING, Side("6", "India", "IND"), Side("3", "South Africa", "SA"), startMs = now + 30 * 60_000L)
        assertEquals("ODI · IND v SA", f.line(up))
        assertEquals("19:30" to LineFormatter.COLOR_GREY, f.kicker(up, now))
        val tomorrow = up.copy(startMs = now + 24 * 3_600_000L)
        assertEquals("TOMORROW 19:00", f.kicker(tomorrow, now).first)
        val later = up.copy(startMs = now + 3 * 24 * 3_600_000L) // Thursday 12 Nov
        assertEquals("THU 19:00", f.kicker(later, now).first)
        val rain = cricket("T20I", State.BREAK, Side("40", "Afghanistan", "AFG"), Side("9", "Zimbabwe", "ZIM"), statusDetail = "Rain delay")
        assertEquals("RAIN DELAY", f.kicker(rain, now).first)
        assertEquals("INNINGS", f.kicker(rain.copy(statusDetail = "Innings Break"), now).first)
    }

    @Test
    fun footballLines() {
        val live = football(State.LIVE, "2", "1", clock = "67'")
        assertEquals("Premier League · ARS 2–1 CHE 67'", f.line(live))
        val ht = football(State.BREAK, "0", "0", statusDetail = "Halftime")
        assertEquals("Premier League · ARS 0–0 CHE", f.line(ht))
        assertEquals("HT" to LineFormatter.COLOR_BREAK, f.kicker(ht, now))
        val ft = football(State.FINAL, "2", "1", statusDetail = "FT")
        assertEquals("Premier League · ARS 2–1 CHE", f.line(ft))
        assertEquals("FT" to LineFormatter.COLOR_GREY, f.kicker(ft, now))
        val up = football(State.UPCOMING, "0", "0").copy(competition = "ISL", home = Side("18851", "Bengaluru FC", "BFC", shortName = "Bengaluru"), away = Side("18002", "Mumbai City FC", "MCFC", shortName = "Mumbai City"))
        assertEquals("ISL · Bengaluru v Mumbai City", f.line(up))
    }

    @Test
    fun f1Lines() {
        val gp = Side("4665", "São Paulo GP", "VER", shortName = "São Paulo GP", score = "Max Verstappen")
        val live = Event("1", Sport.F1, "f1", "Formula 1", "", State.LIVE, "Lap 32", now, null, gp, Side("", "", ""), "", now, session = "Race", period = 32)
        assertEquals("F1 · São Paulo GP · Lap 32 · VER leads", f.line(live))
        val qual = live.copy(state = State.UPCOMING, session = "Qual", period = 0, home = gp.copy(abbr = "", score = ""))
        assertEquals("F1 · São Paulo GP · Qual", f.line(qual))
        val final = live.copy(state = State.FINAL, home = gp.copy(score = "Lando Norris", abbr = "NOR"))
        assertEquals("F1 · São Paulo GP · Race · Norris wins", f.line(final))
        assertEquals("FINAL" to LineFormatter.COLOR_GREY, f.kicker(final, now))
        val red = live.copy(state = State.BREAK, statusDetail = "Red Flag")
        assertEquals("RED FLAG" to LineFormatter.COLOR_BREAK, f.kicker(red, now))
        assertEquals("F1 · São Paulo GP · Race", f.line(red))
    }

    @Test
    fun tennisLinesShortenTheTournamentFirst() {
        val a = Side("1", "Mika Brunold", "BRU", shortName = "Brunold", score = "5 6 6(7)")
        val b = Side("2", "Borna Gojo", "GOJ", shortName = "Gojo", score = "7 4 7(9)")
        val e = Event("1", Sport.TENNIS, "atp", "Bybit Stockholm Open", "", State.FINAL, "Final", now, now, a, b, "", now)
        assertEquals("Bybit Stockholm Open · Brunold 5-7 6-4 6-7 Gojo", f.line(e))
        val long = e.copy(competition = "Rolex Paris Masters Presented By Somebody", home = a.copy(shortName = "Alcaraz"), away = b.copy(shortName = "Sinner"))
        val line = f.line(long)
        assertTrue(line, line.length <= ScoreContract.LINE_MAX_CHARS)
        assertTrue(line, line.endsWith("· Alcaraz 5-7 6-4 6-7 Sinner"))
        assertTrue(line, line.contains("…"))
        val up = e.copy(state = State.UPCOMING, home = a.copy(score = ""), away = b.copy(score = ""))
        assertEquals("Bybit Stockholm Open · Brunold v Gojo", f.line(up))
    }

    @Test
    fun badmintonLinesMirrorTennisWithGames() {
        val a = Side("5001", "Sindhu P. V.", "SIN", shortName = "Sindhu", score = "21 11")
        val b = Side("5002", "Axelsen V.", "AXE", shortName = "Axelsen", score = "18 9")
        val live = Event("1", Sport.BADMINTON, "7001", "Japan Masters", "", State.LIVE, "Round of 16 · Game 2", now, null, a, b, "", now, period = 2)
        assertEquals("Japan Masters · Sindhu 21-18 11-9 Axelsen", f.line(live))
        assertEquals("LIVE" to LineFormatter.COLOR_LIVE, f.kicker(live, now))
        val up = live.copy(state = State.UPCOMING, home = a.copy(score = ""), away = b.copy(score = ""), startMs = now + 30 * 60_000L)
        assertEquals("Japan Masters · Sindhu v Axelsen", f.line(up))
        assertEquals("19:30", f.kicker(up, now).first)
        val interval = live.copy(state = State.BREAK, statusDetail = "Round of 16 · Interval", home = a.copy(score = "21"), away = b.copy(score = "15"))
        assertEquals("Japan Masters · Sindhu 21-15 Axelsen", f.line(interval))
        assertEquals("INTERVAL" to LineFormatter.COLOR_BREAK, f.kicker(interval, now))
        val done = live.copy(state = State.FINAL, statusDetail = "Round of 16 · Ended", home = a.copy(score = "21 21", winner = true), away = b.copy(score = "18 19"))
        assertEquals("Japan Masters · Sindhu 21-18 21-19 Axelsen", f.line(done))
        assertEquals("FINAL" to LineFormatter.COLOR_GREY, f.kicker(done, now))
    }

    @Test
    fun badmintonWalkoverRetirementAndLongPairs() {
        val a = Side("5009", "Christie J.", "CHR", shortName = "Christie", score = "")
        val b = Side("5010", "Antonsen A.", "ANT", shortName = "Antonsen", score = "", winner = true)
        val wo = Event("1", Sport.BADMINTON, "7003", "Japan Masters", "", State.FINAL, "Round of 16 · Walkover", now, now, a, b, "", now)
        assertEquals("Japan Masters · Antonsen w/o Christie", f.line(wo))
        val ret = wo.copy(statusDetail = "Round of 16 · Retired", home = a.copy(score = "21 5", winner = true), away = b.copy(score = "18 3", winner = false))
        assertEquals("Japan Masters · Christie 21-18 5-3 Antonsen ret.", f.line(ret))
        val pairA = Side("5003", "Chang C. H./Yang C. T.", "CHA/YAN", shortName = "Chang / Yang", score = "17")
        val pairB = Side("5004", "Kusuma F. D./Puspitasari M. T.", "KUS/PUS", shortName = "Kusuma / Puspitasari", score = "15")
        val doubles = wo.copy(state = State.LIVE, competition = "BWF World Tour Finals", statusDetail = "Game 1", home = pairA, away = pairB)
        assertEquals("BWF W… · Chang / Yang 17-15 Kusuma / Puspitasari", f.line(doubles)) // the tournament is cut first
        val longer = doubles.copy(home = pairA.copy(shortName = "Rankireddy / Shetty", score = "21 17 19"), away = pairB.copy(shortName = "Alfian / Ardianto", score = "19 21 19"))
        assertEquals("BWF World T… · RAN/SHE 21-19 17-21 19-19 ALF/ARD", f.line(longer.copy(home = longer.home.copy(abbr = "RAN/SHE"), away = longer.away.copy(abbr = "ALF/ARD"))))
    }

    @Test
    fun linesNeverExceedFortyEightCharacters() {
        val e = cricket(
            "First-class", State.FINAL,
            Side("1", "Federally Administered Tribal Areas", "FATA"), Side("2", "Peshawar Region Blues", "PRB"),
            summary = "Federally Administered Tribal Areas won by an innings and 143 runs with a day to spare",
        )
        val line = f.line(e)
        assertTrue(line, line.length <= ScoreContract.LINE_MAX_CHARS)
        assertTrue(line, line.endsWith("…"))
        assertEquals("6", LineFormatter.overs(6.0))
        assertEquals("4.2", LineFormatter.overs(4.2))
    }

    private fun cricket(format: String, state: State, home: Side, away: Side, summary: String = "", statusDetail: String = "", startMs: Long = now) =
        Event("1", Sport.CRICKET, "x", "Series", format, state, statusDetail, startMs, null, home, away, summary, now)

    private fun football(state: State, h: String, a: String, clock: String = "", statusDetail: String = "") =
        Event(
            "2", Sport.FOOTBALL, "eng.1", "Premier League", "", state, statusDetail, now, null,
            Side("359", "Arsenal", "ARS", score = h), Side("363", "Chelsea", "CHE", score = a), "", now, clock = clock,
        )
}
