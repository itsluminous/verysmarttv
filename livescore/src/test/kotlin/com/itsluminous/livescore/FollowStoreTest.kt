package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowKind
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** JSON round trip (the country and player kinds included, ADR-051), defaults, expiry of event follows, the 100-entry cap, a corrupt file. */
@RunWith(RobolectricTestRunner::class)
class FollowStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_794_236_400_000L

    @Test
    fun missingFileGivesTheDefaultsAndWritesThem() {
        val file = File(folder.root, "follows.json")
        val store = FollowStore(file)
        assertEquals(FollowStore.defaults(), store.all())
        assertTrue(file.isFile)
        assertEquals(FollowStore.defaults(), FollowStore(file).all())
    }

    @Test
    fun exportIsTheFileAndImportsBack() {
        val store = FollowStore(File(folder.root, "f.json"))
        store.toggle(Follow.team(Team(Sport.CRICKET, "7", "Pakistan", "PAK")))
        val json = store.export()
        assertEquals(File(folder.root, "f.json").readText(), json)
        assertTrue(json.contains("\"version\":1"))
        val other = FollowStore(File(folder.root, "g.json"))
        assertTrue(other.import(json))
        assertEquals(store.all(), other.all())
        assertEquals(7, other.all().size)
    }

    @Test
    fun corruptFileAndBadImportFallBackWithoutChange() {
        val file = File(folder.root, "f.json")
        file.writeText("{not json")
        val store = FollowStore(file)
        assertEquals(FollowStore.defaults(), store.all())
        assertFalse(store.import("[]"))
        assertFalse(store.import("{\"version\":2,\"follows\":[]}"))
        assertFalse(store.import("garbage"))
        assertEquals(FollowStore.defaults(), store.all())
        assertTrue(store.import("{\"version\":1,\"follows\":[]}"))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun unknownKindsAndSportsAreDroppedOnImport() {
        val store = FollowStore(File(folder.root, "f.json"))
        val json = """{"version":1,"follows":[{"kind":"team","sport":"cricket","competition":"","team":"6","event":"","label":"India","expires":0},
            {"kind":"planet","sport":"cricket","competition":"","team":"","event":"","label":"x","expires":0},
            {"kind":"team","sport":"curling","competition":"","team":"1","event":"","label":"y","expires":0}]}"""
        assertTrue(store.import(json))
        assertEquals(listOf(Follow(FollowKind.TEAM, Sport.CRICKET, teamId = "6", label = "India")), store.all())
    }

    @Test
    fun toggleAddsThenRemovesAndNotifies() {
        val store = FollowStore(File(folder.root, "f.json"))
        var notified = 0
        store.addListener { notified++ }
        val f = Follow.competition(Competition(Sport.TENNIS, "wta", "WTA Tour", "WTA"))
        assertTrue(store.toggle(f))
        assertTrue(store.isFollowed(f))
        assertFalse(store.toggle(f))
        assertFalse(store.isFollowed(f))
        assertEquals(2, notified)
        store.clear()
        assertTrue(store.all().isEmpty())
        assertEquals(3, notified)
    }

    @Test
    fun replaceAllRemovesThenAddsOnceAndKeepsTheCap() {
        val file = File(folder.root, "f.json")
        val store = FollowStore(file)
        var notified = 0
        store.addListener { notified++ }
        val india = Follow.team(Team(Sport.CRICKET, "6", "India", "IND"))
        val ipl = Follow.competition(Competition(Sport.CRICKET, "8048", "Indian Premier League", "IPL"))
        val whole = Follow.sport(Sport.CRICKET, "Cricket")
        // The defaults hold India and the IPL: a clear-all of cricket takes both in one save and one notification.
        store.replaceAll(remove = listOf(india, ipl, whole), add = emptyList())
        assertFalse(store.isFollowed(india))
        assertFalse(store.isFollowed(ipl))
        assertEquals(1, notified)
        assertEquals(FollowStore.defaults().size - 2, store.all().size)
        // A select-all adds the section once, even when asked twice, and leaves the rest alone.
        store.replaceAll(remove = emptyList(), add = listOf(whole, whole))
        assertEquals(1, store.all().count { it.key == whole.key })
        assertEquals(2, notified)
        assertEquals(store.all(), FollowStore(file).all())
        // The cap: the oldest entries make room for what is added.
        val many = (1..FollowStore.MAX).map { Follow.team(Team(Sport.FOOTBALL, "t$it", "Team $it", "T$it")) }
        store.replaceAll(remove = emptyList(), add = many)
        assertEquals(FollowStore.MAX, store.all().size)
        assertTrue(store.isFollowed(many.last()))
        assertFalse(store.isFollowed(whole))
    }

    @Test
    fun eventFollowsExpireAndTheCapHolds() {
        val store = FollowStore(File(folder.root, "f.json"))
        store.clear()
        val e = event("e1")
        store.toggle(Follow.event(e, now + 1000))
        assertFalse(store.prune(now))
        assertTrue(store.prune(now + 1000))
        assertTrue(store.all().isEmpty())
        for (i in 0 until 105) store.toggle(Follow.event(event("x$i"), 0L))
        assertEquals(FollowStore.MAX, store.all().size)
        assertEquals("x5", store.all().first().eventId)
    }

    @Test
    fun countryAndPlayerFollowsRoundTripWithTheirDisciplinesAndLegacyRowsStillLoad() {
        val store = FollowStore(File(folder.root, "f.json"))
        store.clear()
        val india = Follow.country("IND", "India", setOf(Discipline.MS, Discipline.WS))
        val chen = Follow.player(Player("57945", "CHEN Yu Fei", "CHN"), Discipline.ALL)
        store.replaceAll(emptyList(), listOf(india, chen))
        val json = store.export()
        assertTrue(json, json.contains("\"kind\":\"country\"") && json.contains("\"country\":\"IND\"") && json.contains("\"disciplines\":[\"MS\",\"WS\"]"))
        val other = FollowStore(File(folder.root, "g.json"))
        assertTrue(other.import(json))
        assertEquals(listOf(india, chen), other.all())
        assertEquals(listOf(india, chen), FollowStore(File(folder.root, "f.json")).all())
        // A new discipline set replaces the follow: same key, one entry.
        store.replaceAll(listOf(india), listOf(india.copy(disciplines = Discipline.ALL)))
        assertEquals(1, store.all().count { it.kind == FollowKind.COUNTRY })
        assertEquals(Discipline.ALL, store.all().first { it.kind == FollowKind.COUNTRY }.disciplines)
        // A row written before ADR-051 has no country or disciplines; an unknown discipline token is dropped.
        val legacy = """{"version":1,"follows":[{"kind":"team","sport":"cricket","competition":"","team":"6","event":"","label":"India","expires":0},
            {"kind":"player","sport":"badminton","competition":"","team":"9","event":"","label":"X","expires":0,"country":"JPN","disciplines":["XD","QQ"]}]}"""
        assertTrue(store.import(legacy))
        assertEquals(Follow(FollowKind.TEAM, Sport.CRICKET, teamId = "6", label = "India"), store.all()[0])
        assertEquals(Follow.player(Player("9", "X", "JPN"), setOf(Discipline.XD)), store.all()[1])
    }

    private fun event(id: String, competitionId: String = "c", home: Side = Side("a", "A", "A"), away: Side = Side("b", "B", "B")) =
        Event(id, Sport.CRICKET, competitionId, "C", "T20I", State.UPCOMING, "", now, null, home, away, "", now)
}
