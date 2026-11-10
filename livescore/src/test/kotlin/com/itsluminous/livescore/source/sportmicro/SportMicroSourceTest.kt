package com.itsluminous.livescore.source.sportmicro

import com.itsluminous.livescore.Fixtures
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.source.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.InputStream

/** The keyed source (ADR-044): no key, no request; with one, RapidAPI's two headers for SportMicro's host and the day as `yyyy-MM-dd`. */
@RunWith(RobolectricTestRunner::class)
class SportMicroSourceTest {
    private class RecordingHttp : HttpClient {
        val calls = ArrayList<Pair<String, Map<String, String>>>()
        override fun stream(url: String, headers: Map<String, String>): InputStream {
            calls.add(url to headers)
            return ByteArrayInputStream(Fixtures.text("badminton/matches-20261110.json").toByteArray())
        }
    }

    @Test
    fun withoutAKeyNothingIsFetched() {
        val http = RecordingHttp()
        val source = SportMicroSource({ "  " }, http) { 1L }
        assertTrue(source.scoreboard(Sport.BADMINTON, null, "20261110").isEmpty())
        assertTrue(http.calls.isEmpty())
        assertEquals(listOf(Sport.BADMINTON), source.sports())
        assertTrue(source.competitions(Sport.BADMINTON).isEmpty())
        assertTrue(source.teams(Competition(Sport.BADMINTON, "7001", "Japan Masters")).isEmpty())
    }

    @Test
    fun withAKeyTheDayListIsFetchedWithRapidApiHeaders() {
        val http = RecordingHttp()
        val source = SportMicroSource({ "k3y" }, http) { 5L }
        val events = source.scoreboard(Sport.BADMINTON, null, "20261110")
        assertEquals(5, events.size)
        assertEquals(5L, events[0].updatedMs)
        val (url, headers) = http.calls.single()
        assertEquals("https://badminton-micro.p.rapidapi.com/matches-by-date?date=eq.2026-11-10", url)
        assertEquals(mapOf("x-rapidapi-key" to "k3y", "x-rapidapi-host" to "badminton-micro.p.rapidapi.com"), headers)
        assertNull(source.event(events[0]))
        assertTrue(source.scoreboard(Sport.TENNIS, null, "20261110").isEmpty())
        assertEquals(1, http.calls.size)
    }

    @Test
    fun urlKeepsAnAlreadyIsoDay() {
        assertEquals("https://badminton-micro.p.rapidapi.com/matches-by-date?date=eq.2026-11-10", SportMicroSource.url("2026-11-10"))
    }
}
