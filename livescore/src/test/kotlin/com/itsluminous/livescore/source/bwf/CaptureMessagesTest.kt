package com.itsluminous.livescore.source.bwf

import android.os.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The binder messages between the main process and the `:bwf` process (ADR-050): a capture, a failure and a request round-trip. */
@RunWith(RobolectricTestRunner::class)
class CaptureMessagesTest {
    @Test
    fun aCaptureCrossesTheBundleWithEveryTournamentInOrder() {
        val capture = BwfCapture("""{"results":[{"id":5216}]}""", linkedMapOf("5216" to """{"results":[]}""", "5844" to """{"results":[{"x":1}]}"""))
        val decoded = CaptureMessages.decode(CaptureMessages.result(capture))
        assertEquals(CaptureMessages.Outcome.Success(capture), decoded)
        assertEquals(listOf("5216", "5844"), (decoded as CaptureMessages.Outcome.Success).capture.liveMatches.keys.toList())
    }

    @Test
    fun aFailureCarriesItsReasonAndAnEmptyOneReadsUnknown() {
        assertEquals(CaptureMessages.Outcome.Failure("challenge not passed"), CaptureMessages.decode(CaptureMessages.failed("challenge not passed")))
        assertEquals(CaptureMessages.Outcome.Failure("unknown error"), CaptureMessages.decode(CaptureMessages.failed("")))
    }

    @Test
    fun aRequestNamesItsPageAndOtherMessagesAreNotRequestsOrReplies() {
        assertEquals("https://example.invalid/", CaptureMessages.pageOf(CaptureMessages.request("https://example.invalid/")))
        assertNull(CaptureMessages.pageOf(CaptureMessages.failed("x")))
        assertNull(CaptureMessages.decode(Message.obtain(null, 99)))
        assertEquals(CaptureMessages.Outcome.Failure("browser process answered without a capture"), CaptureMessages.decode(Message.obtain(null, CaptureMessages.MSG_RESULT)))
    }
}
