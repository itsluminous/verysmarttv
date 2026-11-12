package com.itsluminous.livescore

import com.itsluminous.livescore.model.DetailLine
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Innings
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Round trip to disk, replacement per fetch key, the window, and the final `endMs` rule. */
@RunWith(RobolectricTestRunner::class)
class EventCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_794_236_400_000L
    private val windowStart = now - 24 * 3_600_000L
    private val cricket = FetchKey(Sport.CRICKET, null)

    @Test
    fun roundTripKeepsEveryField() {
        val file = File(folder.root, "events.json")
        val cache = EventCache(file)
        val e = Event(
            "1", Sport.CRICKET, "24707", "Sri Lanka in Pakistan", "Test", State.BREAK, "Stumps", now - 3_600_000L, null,
            Side("7", "Pakistan", "PAK", "Pakistan", "281/5 (67.2 ov)", listOf(Innings(281, 5, 67.2, false, true, true)), true, false),
            Side("8", "Sri Lanka", "SL", "Sri Lanka", "", emptyList(), false, false),
            "Sri Lanka won toss & fielded", now, "1st Test", 1, "0'", listOf(DetailLine("Ghazi Ghori (PAK)", "7 (10b)")), true,
        )
        val badminton = Event(
            "b", Sport.BADMINTON, "bwf-5216", "Kumamoto", "", State.LIVE, "MD - R16 · Game 1", now, null,
            Side("1/2", "A / B", "A/B", "A / B", "16", players = listOf(Player("1", "A", "TPE"), Player("2", "B", "TPE"))),
            Side("3", "C", "C", "C", "14", players = listOf(Player("3", "C", "JPN"))),
            "", now, discipline = Discipline.MD,
        )
        cache.merge(cricket, listOf(e), now, windowStart)
        cache.merge(FetchKey(Sport.BADMINTON, null), listOf(badminton), now, windowStart)
        val reloaded = EventCache(file)
        assertEquals(listOf(e, badminton), reloaded.events())
        assertEquals(mapOf(cricket to now, FetchKey(Sport.BADMINTON, null) to now), reloaded.fetchedMs())
        assertEquals(e, reloaded.event("1"))
        assertEquals(badminton, reloaded.event("b"))
        assertNull(reloaded.event("2"))
    }

    @Test
    fun aFetchReplacesItsOwnKeyOnly() {
        val cache = EventCache(File(folder.root, "e.json"))
        cache.merge(cricket, listOf(event("c1", Sport.CRICKET)), now, windowStart)
        cache.merge(FetchKey(Sport.FOOTBALL, "eng.1"), listOf(event("f1", Sport.FOOTBALL, "eng.1")), now, windowStart)
        cache.merge(cricket, listOf(event("c2", Sport.CRICKET)), now + 1, windowStart)
        assertEquals(listOf("f1", "c2"), cache.events().map { it.id })
    }

    @Test
    fun finalEndIsWhenItWasFirstSeenFinal() {
        val cache = EventCache(File(folder.root, "e.json"))
        val live = event("1", Sport.CRICKET, state = State.LIVE)
        cache.merge(cricket, listOf(live), now, windowStart)
        val scheduledEnd = now + 30 * 3_600_000L
        cache.merge(cricket, listOf(live.copy(state = State.FINAL, endMs = scheduledEnd)), now + 60_000L, windowStart)
        assertEquals(now + 60_000L, cache.event("1")!!.endMs)
        cache.merge(cricket, listOf(live.copy(state = State.FINAL, endMs = scheduledEnd)), now + 120_000L, windowStart)
        assertEquals(now + 60_000L, cache.event("1")!!.endMs)
        val alreadyFinal = event("2", Sport.CRICKET, state = State.FINAL).copy(endMs = now - 3_600_000L)
        cache.merge(cricket, listOf(alreadyFinal), now, windowStart)
        assertEquals(now - 3_600_000L, cache.event("2")!!.endMs)
    }

    @Test
    fun oldFinalsLeaveTheWindowAndDetailsSurviveARefresh() {
        val cache = EventCache(File(folder.root, "e.json"))
        val old = event("old", Sport.CRICKET, state = State.FINAL).copy(startMs = windowStart - 48 * 3_600_000L, endMs = windowStart - 3_600_000L)
        val detailed = event("d", Sport.CRICKET, state = State.LIVE).copy(details = listOf(DetailLine("a", "b")))
        cache.merge(cricket, listOf(old, detailed), now, windowStart)
        assertEquals(listOf("d"), cache.events().map { it.id })
        cache.merge(cricket, listOf(detailed.copy(details = emptyList(), summary = "fresh")), now + 1, windowStart)
        assertEquals(listOf(DetailLine("a", "b")), cache.event("d")!!.details)
        assertEquals("fresh", cache.event("d")!!.summary)
    }

    @Test
    fun corruptFileStartsEmpty() {
        val file = File(folder.root, "e.json")
        file.writeText("{\"events\": [{\"id\":")
        val cache = EventCache(file)
        assertTrue(cache.events().isEmpty())
        cache.merge(cricket, listOf(event("1", Sport.CRICKET)), now, windowStart)
        assertEquals(1, EventCache(file).events().size)
    }

    private fun event(id: String, sport: Sport, competitionId: String = "c", state: State = State.UPCOMING) =
        Event(id, sport, competitionId, "C", "", state, "", now, null, Side("a", "A", "A"), Side("b", "B", "B"), "", now)
}
