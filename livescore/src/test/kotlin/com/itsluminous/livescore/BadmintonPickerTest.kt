package com.itsluminous.livescore

import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Countries and Players lists the picker builds over the cache (ADR-051). */
class BadmintonPickerTest {
    private val sindhu = Player("5001", "Pusarla V. SINDHU", "IND")
    private val an = Player("5002", "AN Se Young", "KOR")
    private val chen = Player("57945", "CHEN Yu Fei", "CHN")
    private val wang = Player("57946", "WANG Zhi Yi", "CHN")
    private val events = listOf(
        badminton("ws", Discipline.WS, listOf(sindhu), listOf(an)),
        badminton("xd", Discipline.XD, listOf(wang, Player("1", "Someone", "ZZZ")), listOf(chen, Player("2", "Other", ""))),
        badminton("ws2", Discipline.WS, listOf(chen), listOf(wang)),
        Event("cricket", Sport.CRICKET, "c", "C", "T20", State.LIVE, "", 0L, null, Side("a", "A", "A", players = listOf(Player("9", "Not badminton", "IND"))), Side("b", "B", "B"), "", 0L),
    )
    private val name = { code: String -> if (code == "ZZZ") "ZZZ" else java.util.Locale("", BadmintonNations.ISO[code] ?: code).getDisplayCountry(java.util.Locale.ENGLISH) }

    @Test
    fun countriesAreTheStaticListPlusTheCachesCodesByLetter() {
        val letters = BadmintonPicker.countries(events, name)
        val all = letters.flatMap { it.countries }
        assertEquals(all.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER), all.map { it.name })
        assertEquals(BadmintonNations.CODES.size + 1, all.size) // KOR, IND and CHN are static; ZZZ is new; the empty code is not a country
        assertTrue(all.any { it.code == "ZZZ" && it.name == "ZZZ" })
        assertEquals(1, all.count { it.code == "IND" })
        assertEquals(letters.map { it.letter }, letters.map { it.letter }.distinct())
        val i = letters.first { it.letter == "I" }
        assertEquals(listOf("IND", "INA", "IRL", "ISR", "ITA"), i.countries.map { it.code })
    }

    @Test
    fun playersAreListedOnceByCountryWithTheDisciplinesTheyPlayed() {
        val groups = BadmintonPicker.players(events, name)
        assertEquals(listOf("CHN", "IND", "KOR", "ZZZ", ""), groups.map { it.code })
        val china = groups.first { it.code == "CHN" }
        assertEquals(listOf("CHEN Yu Fei", "WANG Zhi Yi"), china.players.map { it.player.name })
        assertEquals(listOf(Discipline.WS, Discipline.XD), china.players[0].disciplines)
        assertEquals(sindhu, groups.first { it.code == "IND" }.players.single().player)
        assertEquals("Other", groups.last().players.single().player.name)
        assertTrue(BadmintonPicker.players(events.takeLast(1), name).isEmpty())
    }

    private fun badminton(id: String, discipline: Discipline?, team1: List<Player>, team2: List<Player>): Event =
        Event(id, Sport.BADMINTON, "bwf-1", "T", "", State.LIVE, "", 0L, null, side(team1), side(team2), "", 0L, discipline = discipline)

    private fun side(players: List<Player>) = Side(players.joinToString("/") { it.id }, players.joinToString(" / ") { it.name }, "X", players = players)
}
