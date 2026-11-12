package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowReason
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The union over every follow kind (ADR-051): the four kinds of §13.2 with their reasons, and the badminton country and
 * player follows with their discipline filter, on the owner's example (India plus two Chinese players).
 */
class FollowMatcherTest {
    private val now = 1_794_236_400_000L
    private val sindhu = Player("5001", "Pusarla V. SINDHU", "IND")
    private val an = Player("5002", "AN Se Young", "KOR")
    private val chen = Player("57945", "CHEN Yu Fei", "CHN")
    private val wang = Player("57946", "WANG Zhi Yi", "CHN")
    private val liang = Player("81561", "LIANG Wei Keng", "CHN")
    private val wangC = Player("95254", "WANG Chang", "CHN")
    private val hoki = Player("64226", "Takuro HOKI", "JPN")
    private val kobayashi = Player("78106", "Yugo KOBAYASHI", "JPN")
    private val christie = Player("61409", "Jonatan CHRISTIE", "INA")
    private val naraoka = Player("89785", "Kodai NARAOKA", "JPN")
    private val tanisha = Player("97103", "Tanisha CRASTO", "IND")
    private val dhruv = Player("94689", "Dhruv KAPILA", "IND")

    private val wsIndia = badminton("ws", Discipline.WS, listOf(sindhu), listOf(an))
    private val wsChina = badminton("ws2", Discipline.WS, listOf(chen), listOf(wang))
    private val mdChina = badminton("md", Discipline.MD, listOf(liang, wangC), listOf(hoki, kobayashi))
    private val msIndonesia = badminton("ms", Discipline.MS, listOf(christie), listOf(naraoka))
    private val xdIndiaMixed = badminton("xd", Discipline.XD, listOf(tanisha, dhruv), listOf(hoki, wang))
    private val all = listOf(wsIndia, wsChina, mdChina, msIndonesia, xdIndiaMixed)

    @Test
    fun indiaPlusTwoChinesePlayersIsTheUnionOfTheirMatches() {
        val follows = listOf(Follow.country("IND", "India", Discipline.ALL), Follow.player(chen, Discipline.ALL), Follow.player(liang, Discipline.ALL))
        assertEquals(listOf("ws", "ws2", "md", "xd"), FollowMatcher.match(all, follows).map { it.event.id })
        assertFalse(FollowMatcher.matches(msIndonesia, follows))
        assertTrue(FollowMatcher.match(all, follows).all { it.reason == FollowReason.TEAM })
    }

    @Test
    fun aSinglesOnlyCountryFollowLeavesItsDoublesAndMixedOut() {
        val singles = listOf(Follow.country("IND", "India", setOf(Discipline.MS, Discipline.WS)))
        assertEquals(listOf("ws"), FollowMatcher.match(all, singles).map { it.event.id })
        assertFalse("a mixed pair with two Indians is not singles", FollowMatcher.matches(xdIndiaMixed, singles))
        val doubles = listOf(Follow.country("CHN", "China", setOf(Discipline.MD, Discipline.WD)))
        assertEquals(listOf("md"), FollowMatcher.match(all, doubles).map { it.event.id })
    }

    @Test
    fun aMixedPairWithOneIndianCountsForIndiaUnderAllOrMixed() {
        val xd = badminton("xd1", Discipline.XD, listOf(dhruv, wang), listOf(hoki, an))
        assertTrue(FollowMatcher.matches(xd, listOf(Follow.country("IND", "India", Discipline.ALL))))
        assertTrue(FollowMatcher.matches(xd, listOf(Follow.country("IND", "India", setOf(Discipline.XD)))))
        assertTrue("the Chinese half counts for China too", FollowMatcher.matches(xd, listOf(Follow.country("CHN", "China", setOf(Discipline.XD)))))
        assertFalse(FollowMatcher.matches(xd, listOf(Follow.country("IND", "India", setOf(Discipline.MS, Discipline.WS)))))
        assertTrue("codes match whatever their case", FollowMatcher.matches(xd, listOf(Follow.country("ind", "India", Discipline.ALL))))
    }

    @Test
    fun aPlayerFollowMatchesEitherSideInItsDisciplinesOnly() {
        val wangEverything = listOf(Follow.player(wang, Discipline.ALL))
        assertEquals(listOf("ws2", "xd"), FollowMatcher.match(all, wangEverything).map { it.event.id })
        val wangSingles = listOf(Follow.player(wang, setOf(Discipline.WS)))
        assertEquals(listOf("ws2"), FollowMatcher.match(all, wangSingles).map { it.event.id })
        assertNull(FollowMatcher.reason(wsChina, listOf(Follow.player(Player("nobody", "Nobody", "JPN"), Discipline.ALL))))
        // A follow stored with an empty set reads as every discipline.
        assertTrue(FollowMatcher.matches(xdIndiaMixed, listOf(Follow.player(wang, emptySet()))))
    }

    @Test
    fun severalFollowsOnOneMatchListItOnceWithTheStrongestReason() {
        val follows = listOf(
            Follow.sport(Sport.BADMINTON, "Badminton"),
            Follow.country("CHN", "China", Discipline.ALL),
            Follow.player(liang, Discipline.ALL),
            Follow.player(wangC, Discipline.ALL),
            Follow.event(mdChina, 0L),
        )
        val matched = FollowMatcher.match(listOf(mdChina), follows)
        assertEquals(1, matched.size)
        assertEquals(FollowReason.EVENT, matched.single().reason)
        assertEquals(FollowReason.TEAM, FollowMatcher.reason(mdChina, follows.dropLast(1)))
        assertEquals(FollowReason.COMPETITION, FollowMatcher.reason(mdChina, follows.take(1)))
    }

    @Test
    fun aMatchWithoutADisciplineOrNationalitiesOnlyMeetsAnAllFollow() {
        val plain = badminton("plain", null, listOf(Player("77", "Someone", "")), listOf(Player("78", "Other", "")))
        assertTrue(FollowMatcher.matches(plain, listOf(Follow.player(Player("77", "Someone", ""), Discipline.ALL))))
        assertFalse("no discipline to compare against a filter", FollowMatcher.matches(plain, listOf(Follow.player(Player("77", "Someone", ""), setOf(Discipline.MS)))))
        assertFalse("no nationality to match a country", FollowMatcher.matches(plain, listOf(Follow.country("IND", "India", Discipline.ALL))))
        assertFalse("a country follow is badminton's", FollowMatcher.matches(cricket("c"), listOf(Follow.country("IND", "India", Discipline.ALL))))
    }

    @Test
    fun theFourOlderKindsKeepTheirReasons() {
        val e = cricket("1", competitionId = "8048", home = Side("6", "India", "IND"), away = Side("2", "Australia", "AUS"))
        assertEquals(FollowReason.COMPETITION, FollowMatcher.reasonFor(Follow.sport(Sport.CRICKET, "Cricket"), e))
        assertEquals(FollowReason.COMPETITION, FollowMatcher.reasonFor(Follow.competition(Competition(Sport.CRICKET, "8048", "IPL")), e))
        assertNull(FollowMatcher.reasonFor(Follow.competition(Competition(Sport.CRICKET, "8679", "PSL")), e))
        assertEquals(FollowReason.TEAM, FollowMatcher.reasonFor(Follow.team(Team(Sport.CRICKET, "6", "India", "IND")), e))
        assertNull(FollowMatcher.reasonFor(Follow.team(Team(Sport.FOOTBALL, "6", "x", "X")), e))
        assertEquals(FollowReason.EVENT, FollowMatcher.reasonFor(Follow.event(e, 0L), e))
        val intl = Follow.competition(Competition(Sport.CRICKET, Competition.INTERNATIONAL, "Internationals"))
        assertNull(FollowMatcher.reasonFor(intl, e))
        assertEquals(FollowReason.COMPETITION, FollowMatcher.reasonFor(intl, e.copy(international = true)))
        val matched = FollowMatcher.match(listOf(e), listOf(Follow.sport(Sport.CRICKET, "Cricket"), Follow.team(Team(Sport.CRICKET, "6", "India", "IND"))))
        assertEquals(listOf(Followed(e, FollowReason.TEAM)), matched)
    }

    private fun badminton(id: String, discipline: Discipline?, team1: List<Player>, team2: List<Player>): Event =
        Event(
            id, Sport.BADMINTON, "bwf-5216", "Kumamoto Masters", "", State.LIVE, "Game 1", now, null,
            side(team1), side(team2), "", now, discipline = discipline,
        )

    private fun side(players: List<Player>) = Side(players.joinToString("/") { it.id }, players.joinToString(" / ") { it.name }, "X", players = players)

    private fun cricket(id: String, competitionId: String = "c", home: Side = Side("a", "A", "A"), away: Side = Side("b", "B", "B")) =
        Event(id, Sport.CRICKET, competitionId, "C", "T20I", State.UPCOMING, "", now, null, home, away, "", now)
}
