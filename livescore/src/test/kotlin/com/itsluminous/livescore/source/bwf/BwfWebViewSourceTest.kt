package com.itsluminous.livescore.source.bwf

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * The experimental source over a fake loader (ADR-049): one page visit per fetch, shared by the two days of a full
 * refresh; every loader failure and every shape change is an IOException that starts with "BWF site unavailable".
 */
@RunWith(RobolectricTestRunner::class)
class BwfWebViewSourceTest {
    private class FakeLoader(private val answer: () -> BwfCapture) : BwfPageLoader {
        var loads = 0
        override fun load(): BwfCapture {
            loads++
            return answer()
        }
    }

    private val recorded = BwfCapture(Fixtures.text("bwf/current-live-20261110.json"), mapOf("5216" to Fixtures.text("bwf/live-matches-handmade.json"), "5652" to Fixtures.text("bwf/live-matches-empty-20261110.json")))

    @Test
    fun oneVisitGivesTheMatchesOfEveryTournamentAndTheSecondDayReusesIt() {
        val loader = FakeLoader { recorded }
        var now = 1_000L
        val source = BwfWebViewSource(loader) { now }
        assertEquals(listOf(Sport.BADMINTON), source.sports())
        assertTrue(source.competitions(Sport.BADMINTON).isEmpty())
        assertTrue(source.teams(Competition(Sport.BADMINTON, "bwf-5216", "Kumamoto")).isEmpty())
        val today = source.scoreboard(Sport.BADMINTON, null, "20261110")
        assertEquals(5, today.size)
        assertEquals("Kumamoto Masters Japan 2026", today[0].competition)
        assertEquals(1_000L, today[0].updatedMs)
        now += 30_000L
        val tomorrow = source.scoreboard(Sport.BADMINTON, null, "20261111")
        assertSame(today, tomorrow)
        assertEquals(1, loader.loads)
        now += BwfWebViewSource.REUSE_MS
        source.scoreboard(Sport.BADMINTON, null, "20261111")
        assertEquals(2, loader.loads)
        assertTrue(source.scoreboard(Sport.TENNIS, null, "20261110").isEmpty())
        assertNull(source.event(today[0]))
        assertEquals(2, loader.loads)
    }

    @Test
    fun aLoaderFailureIsTheSiteUnavailableWithItsReason() {
        val source = BwfWebViewSource(FakeLoader { throw IOException("challenge not passed") }) { 1L }
        try {
            source.scoreboard(Sport.BADMINTON, null, "20261110")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertEquals("BWF site unavailable: challenge not passed", e.message)
        }
    }

    @Test
    fun aChangedShapeIsTheSiteUnavailableNotACrash() {
        val source = BwfWebViewSource(FakeLoader { BwfCapture("not json at all", emptyMap()) }) { 1L }
        try {
            source.scoreboard(Sport.BADMINTON, null, "20261110")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.startsWith("BWF site unavailable: the page's data changed shape"))
        }
    }

    @Test
    fun aTournamentThePageNamesButTheListDoesNotStillYieldsItsMatches() {
        val capture = BwfCapture("""{"results":[]}""", mapOf("9" to Fixtures.text("bwf/live-matches-handmade.json")))
        val events = BwfWebViewSource.parse(capture, 5L)
        assertEquals(5, events.size)
        assertEquals("BWF 9", events[0].competition)
        assertEquals("bwf-9", events[0].competitionId)
        assertEquals(State.LIVE, events[0].state)
    }
}
