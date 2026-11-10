package com.itsluminous.livescore.source.bwf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The state machine behind the WebView's bridge (ADR-049), the part of the loader a JVM can test: calls in order, first failure wins, timeouts named. */
class PendingCaptureTest {
    @Test
    fun tournamentsThenMatchesThenDoneIsACapture() {
        val p = PendingCapture()
        assertFalse(p.isSettled())
        p.tournaments("""{"results":[]}""")
        p.matches("5216", "a")
        p.matches("5652", "b")
        assertFalse(p.isSettled())
        p.done()
        assertTrue(p.isSettled())
        val capture = p.result(30_000L)
        assertEquals("""{"results":[]}""", capture.currentLive)
        assertEquals(mapOf("5216" to "a", "5652" to "b"), capture.liveMatches)
    }

    @Test
    fun failWinsAndNothingAfterItCounts() {
        val p = PendingCapture()
        p.tournaments("x")
        p.fail("  HTTP 403 for vue-live-matches  ")
        p.done()
        p.matches("1", "late")
        assertTrue(p.isSettled())
        try {
            p.result(30_000L)
            fail()
        } catch (e: IOException) {
            assertEquals("HTTP 403 for vue-live-matches", e.message)
        }
    }

    @Test
    fun notSettledIsTheTimeoutOrTheChallenge() {
        try {
            PendingCapture().result(30_000L)
            fail()
        } catch (e: IOException) {
            assertEquals("timed out after 30 s", e.message)
        }
        val challenged = PendingCapture().apply { challenged() }
        try {
            challenged.result(30_000L)
            fail()
        } catch (e: IOException) {
            assertEquals("challenge not passed", e.message)
        }
    }

    @Test
    fun doneWithoutATournamentListAndAnEmptyReasonAreNamed() {
        val p = PendingCapture().apply { done() }
        try {
            p.result(1L)
            fail()
        } catch (e: IOException) {
            assertEquals("no tournament list from the page", e.message)
        }
        val q = PendingCapture().apply { fail("   ") }
        try {
            q.result(1L)
            fail()
        } catch (e: IOException) {
            assertEquals("unknown error", e.message)
        }
        val long = PendingCapture().apply { fail("x".repeat(200)) }
        try {
            long.result(1L)
            fail()
        } catch (e: IOException) {
            assertEquals(80, e.message!!.length)
        }
    }
}
