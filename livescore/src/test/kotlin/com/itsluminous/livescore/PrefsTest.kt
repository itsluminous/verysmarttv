package com.itsluminous.livescore

import com.itsluminous.livescore.model.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The badminton data key lives in the app's preferences, trimmed, empty until entered (ADR-044); every sport is shown until one is unticked. */
@RunWith(RobolectricTestRunner::class)
class PrefsTest {
    private val prefs = Prefs(RuntimeEnvironment.getApplication())

    @Test
    fun badmintonKeyRoundTripsTrimmedAndClears() {
        assertEquals("", prefs.badmintonKey)
        prefs.badmintonKey = "  abc123  "
        assertEquals("abc123", prefs.badmintonKey)
        assertEquals("abc123", Prefs(RuntimeEnvironment.getApplication()).badmintonKey)
        prefs.badmintonKey = ""
        assertEquals("", prefs.badmintonKey)
    }

    @Test
    fun everySportIsShownByDefaultBadmintonIncluded() {
        assertEquals(Sport.entries.toSet(), prefs.sportsShown)
        prefs.sportsShown = setOf(Sport.CRICKET)
        assertEquals(setOf(Sport.CRICKET), prefs.sportsShown)
        assertTrue(prefs.liveRefresh)
    }
}
