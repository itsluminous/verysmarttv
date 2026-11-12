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
    fun sportMicroTypesReadLikeEspnStates() {
        // SportMicro (badminton, ADR-044) names states in words and gives the endings and pauses as their own types.
        for (t in listOf("upcoming", "notstarted", "Not started", "scheduled")) assertEquals(t, State.UPCOMING, normalise(t, "Not started"))
        for (t in listOf("live", "inprogress", "In progress")) assertEquals(t, State.LIVE, normalise(t, "2nd game"))
        assertEquals(State.LIVE, normalise("live", ""))
        for (t in listOf("finished", "ended", "final")) assertEquals(t, State.FINAL, normalise(t, "Ended"))
        assertEquals(State.FINAL, normalise("finished", "Walkover"))
        assertEquals(State.FINAL, normalise("live", "Walkover"))
        assertEquals(State.FINAL, normalise("live", "Retired"))
        for (t in listOf("walkover", "retired", "canceled", "cancelled", "postponed")) assertEquals(t, State.FINAL, normalise(t, ""))
        for (t in listOf("interrupted", "suspended", "delayed", "pause")) assertEquals(t, State.BREAK, normalise(t, ""))
        for (d in listOf("Interval", "Pause", "Break")) assertEquals(d, State.BREAK, normalise("live", d))
    }

    @Test
    fun bwfMatchCentreWordsReadTheSameWay() {
        // BWF's Match Centre (ADR-049): `Playing` while on court, `Warm up` and the like before, `Completed` after.
        assertEquals(State.LIVE, normalise("Playing", ""))
        for (t in listOf("Warm up", "warm-up", "Warming up", "On court", "Coming up", "Waiting to start")) assertEquals(t, State.UPCOMING, normalise(t, ""))
        for (t in listOf("Completed", "complete", "Finished")) assertEquals(t, State.FINAL, normalise(t, ""))
        assertEquals(State.FINAL, normalise("Playing", "Retired"))
        assertEquals(State.BREAK, normalise("Suspended", ""))
    }

    @Test
    fun unknownStateFallsBackToUpcoming() {
        assertEquals(State.UPCOMING, normalise("", ""))
        assertEquals(State.FINAL, normalise("", "Abandoned"))
    }

    private fun normalise(state: String, description: String) = StateNormaliser.normalise(FeedStatus(state, description, period = 1))
}
