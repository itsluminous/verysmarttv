package com.itsluminous.livescore

import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.FollowReason
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The priority of §12.2: live > break > upcoming within 12 h > final within 6 h; reason, start, alternation. */
class PillChooserTest {
    private val now = 1_794_236_400_000L
    private val hour = 3_600_000L

    @Test
    fun emptyIsNone() {
        assertNull(PillChooser.choose(emptyList(), now))
    }

    @Test
    fun liveBeatsBreakBeatsUpcomingBeatsFinal() {
        val live = f("live", State.LIVE)
        val brk = f("break", State.BREAK)
        val up = f("up", State.UPCOMING, start = now + hour)
        val fin = f("fin", State.FINAL, end = now - hour)
        assertEquals(live, PillChooser.choose(listOf(fin, up, brk, live), now))
        assertEquals(brk, PillChooser.choose(listOf(fin, up, brk), now))
        assertEquals(up, PillChooser.choose(listOf(fin, up), now))
        assertEquals(fin, PillChooser.choose(listOf(fin), now))
    }

    @Test
    fun windowsForUpcomingAndFinal() {
        assertNull(PillChooser.choose(listOf(f("late", State.UPCOMING, start = now + 13 * hour)), now))
        assertNull(PillChooser.choose(listOf(f("old", State.FINAL, end = now - 7 * hour)), now))
        val inWindow = f("ok", State.UPCOMING, start = now + 12 * hour)
        assertEquals(inWindow, PillChooser.choose(listOf(inWindow, f("late", State.UPCOMING, start = now + 13 * hour)), now))
        val recent = f("recent", State.FINAL, end = now - 6 * hour)
        assertEquals(recent, PillChooser.choose(listOf(recent), now))
    }

    @Test
    fun tiesGoToTheFollowReasonThenTheEarliestStart() {
        val team = f("team", State.UPCOMING, FollowReason.TEAM, start = now + 2 * hour)
        val comp = f("comp", State.UPCOMING, FollowReason.COMPETITION, start = now + hour)
        val event = f("event", State.UPCOMING, FollowReason.EVENT, start = now + 3 * hour)
        assertEquals(event, PillChooser.choose(listOf(comp, team, event), now))
        assertEquals(team, PillChooser.choose(listOf(comp, team), now))
        val earlier = f("earlier", State.UPCOMING, FollowReason.COMPETITION, start = now + hour / 2)
        assertEquals(earlier, PillChooser.choose(listOf(comp, earlier), now))
    }

    @Test
    fun twoLiveMatchesOfTheSameReasonAlternateEveryTwentySeconds() {
        val a = f("a", State.LIVE, FollowReason.TEAM, start = now - 2 * hour)
        val b = f("b", State.LIVE, FollowReason.TEAM, start = now - hour)
        val c = f("c", State.LIVE, FollowReason.COMPETITION, start = now - 3 * hour)
        val t0 = now - now % PillChooser.ALTERNATE_MS
        assertEquals(a, PillChooser.choose(listOf(c, b, a), t0))
        assertEquals(b, PillChooser.choose(listOf(c, b, a), t0 + PillChooser.ALTERNATE_MS))
        assertEquals(a, PillChooser.choose(listOf(c, b, a), t0 + 2 * PillChooser.ALTERNATE_MS))
        assertTrue(PillChooser.alternates(listOf(c, b, a), t0))
        assertFalse(PillChooser.alternates(listOf(c, a), t0))
        assertEquals(a, PillChooser.choose(listOf(c, a), t0 + PillChooser.ALTERNATE_MS))
    }

    @Test
    fun rankedPutsThePillsOwnFirstThenEveryOtherEligibleEventInRankOrder() {
        val live = f("live", State.LIVE, FollowReason.TEAM, start = now - hour)
        val liveLater = f("live2", State.LIVE, FollowReason.COMPETITION, start = now - hour / 2)
        val brk = f("break", State.BREAK)
        val soon = f("soon", State.UPCOMING, start = now + hour)
        val later = f("later", State.UPCOMING, start = now + 3 * hour)
        val tooLate = f("tooLate", State.UPCOMING, start = now + 13 * hour)
        val fin = f("fin", State.FINAL, end = now - hour)
        val old = f("old", State.FINAL, end = now - 7 * hour)
        val ranked = PillChooser.ranked(listOf(old, later, fin, tooLate, soon, brk, liveLater, live), now)
        assertEquals(listOf(live, liveLater, brk, soon, later, fin), ranked)
        assertEquals("the first row is what choose() picks", PillChooser.choose(listOf(liveLater, live), now), ranked[0])
        assertEquals(emptyList<Followed>(), PillChooser.ranked(listOf(old, tooLate), now))
    }

    @Test
    fun rankedKeepsTheAlternatingFirstRowAndTheRestStill() {
        val a = f("a", State.LIVE, FollowReason.TEAM, start = now - 2 * hour)
        val b = f("b", State.LIVE, FollowReason.TEAM, start = now - hour)
        val c = f("c", State.UPCOMING, start = now + hour)
        val t0 = now - now % PillChooser.ALTERNATE_MS
        assertEquals(listOf(a, b, c), PillChooser.ranked(listOf(c, b, a), t0))
        assertEquals("b's turn: it leads, a follows, c stays last", listOf(b, a, c), PillChooser.ranked(listOf(c, b, a), t0 + PillChooser.ALTERNATE_MS))
    }

    private fun f(id: String, state: State, reason: FollowReason = FollowReason.COMPETITION, start: Long = now - 3_600_000L, end: Long? = null) =
        Followed(Event(id, Sport.CRICKET, "c", "C", "T20I", state, "", start, end, Side("1", "A", "A"), Side("2", "B", "B"), "", now), reason)
}
