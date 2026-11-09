package com.itsluminous.livescore

import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** The table of §13.4, row by row. */
class StateNormaliserTest {
    @Test
    fun preIsUpcoming() {
        assertEquals(State.UPCOMING, normalise("pre", "Scheduled"))
    }

    @Test
    fun inWithPeriodZeroAndStartsAtIsUpcoming() {
        assertEquals(State.UPCOMING, StateNormaliser.normalise(FeedStatus("in", "Live", period = 0, summary = "Starts at 10:00 local time")))
        assertEquals(State.LIVE, StateNormaliser.normalise(FeedStatus("in", "Live", period = 0, summary = "Multan won toss")))
        assertEquals(State.LIVE, StateNormaliser.normalise(FeedStatus("in", "Live", period = 1, summary = "Starts at 10:00 local time")))
    }

    @Test
    fun inPlayDescriptionsAreLive() {
        for (d in listOf("Live", "In Progress", "1st Half", "2nd Half", "Q3", "Set 2", "Lap 32", "")) {
            assertEquals(d, State.LIVE, normalise("in", d))
        }
    }

    @Test
    fun pausesAreBreaks() {
        for (d in listOf(
            "Stumps", "Lunch", "Tea", "Dinner", "Drinks", "Innings Break", "Delayed", "Rain", "Halftime", "Suspended",
            "Bad light", "Wet outfield", "Red Flag", "Half-time", "Rain delay",
        )) {
            assertEquals(d, State.BREAK, normalise("in", d))
        }
    }

    @Test
    fun endingsWhileStillInAreFinal() {
        for (d in listOf("Abandoned", "Cancelled", "No result", "Walkover", "Retired", "Postponed")) {
            assertEquals(d, State.FINAL, normalise("in", d))
        }
    }

    @Test
    fun postIsFinal() {
        assertEquals(State.FINAL, normalise("post", "Result"))
        assertEquals(State.FINAL, normalise("post", "Full Time"))
    }

    @Test
    fun unknownStateFallsBackToUpcoming() {
        assertEquals(State.UPCOMING, normalise("", ""))
        assertEquals(State.FINAL, normalise("", "Abandoned"))
    }

    private fun normalise(state: String, description: String) = StateNormaliser.normalise(FeedStatus(state, description, period = 1))
}
