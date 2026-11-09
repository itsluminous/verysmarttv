package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowReason
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.ScoreSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §13.5 as a table over an injected clock: refresh-on-read only when stale and live, one fetch per competition, backoff, offline, job times. */
class RefreshPolicyTest {
    private val policy = RefreshPolicy()
    private val now = 1_794_236_400_000L
    private val cricket = FetchKey(Sport.CRICKET, null)
    private val pl = FetchKey(Sport.FOOTBALL, "eng.1")

    @Test
    fun readFetchesOnlyLiveCompetitionsWhoseFetchIsOlderThan55s() {
        val followed = listOf(live("1", Sport.CRICKET, "8048"), live("2", Sport.CRICKET, "24707"), live("3", Sport.FOOTBALL, "eng.1"), upcoming("4", Sport.FOOTBALL, "ind.1", now + 5 * 3_600_000L))
        val fresh = mapOf(cricket to now - 10_000L, pl to now - 60_000L)
        assertEquals(listOf(pl), onRead(followed, fresh))
        val stale = mapOf(cricket to now - 56_000L, pl to now - 60_000L)
        assertEquals(listOf(cricket, pl), onRead(followed, stale)) // one fetch for both cricket matches
        assertEquals(listOf(cricket, pl), onRead(followed, emptyMap()))
    }

    @Test
    fun aBreakCountsAsLiveAndAFinalNever() {
        assertEquals(listOf(cricket), onRead(listOf(live("1", Sport.CRICKET, "x", State.BREAK)), emptyMap()))
        assertEquals(emptyList<FetchKey>(), onRead(listOf(live("1", Sport.CRICKET, "x", State.FINAL)), emptyMap()))
    }

    @Test
    fun upcomingFetchesAtMatchDayCadenceAroundTheStart() {
        val soon = listOf(upcoming("1", Sport.FOOTBALL, "eng.1", now + 10 * 60_000L))
        assertEquals(listOf(pl), onRead(soon, mapOf(pl to now - 16 * 60_000L)))
        assertEquals(emptyList<FetchKey>(), onRead(soon, mapOf(pl to now - 14 * 60_000L)))
        val started = listOf(upcoming("1", Sport.FOOTBALL, "eng.1", now - 2 * 3_600_000L)) // cache still says pre
        assertEquals(listOf(pl), onRead(started, emptyMap()))
        val later = listOf(upcoming("1", Sport.FOOTBALL, "eng.1", now + 3_600_000L))
        assertEquals(emptyList<FetchKey>(), onRead(later, emptyMap()))
        val longAgo = listOf(upcoming("1", Sport.FOOTBALL, "eng.1", now - 4 * 3_600_000L))
        assertEquals(emptyList<FetchKey>(), onRead(longAgo, emptyMap()))
    }

    @Test
    fun offlineRunningAndBackoffStopAFetch() {
        val followed = listOf(live("1", Sport.CRICKET, "x"))
        assertEquals(emptyList<FetchKey>(), policy.onRead(followed, emptyMap(), online = false, fetchRunning = false, failures = 0, lastFailureMs = 0, nowMs = now))
        assertEquals(emptyList<FetchKey>(), policy.onRead(followed, emptyMap(), online = true, fetchRunning = true, failures = 0, lastFailureMs = 0, nowMs = now))
        assertEquals(emptyList<FetchKey>(), policy.onRead(followed, emptyMap(), online = true, fetchRunning = false, failures = 1, lastFailureMs = now - 59_000L, nowMs = now))
        assertEquals(listOf(cricket), policy.onRead(followed, emptyMap(), online = true, fetchRunning = false, failures = 1, lastFailureMs = now - 61_000L, nowMs = now))
    }

    @Test
    fun backoffDoublesToEightMinutes() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 8L, 8L), (1..6).map { policy.backoffMs(it) / 60_000L })
    }

    @Test
    fun staleIsTwiceTheInterval() {
        val l = live("1", Sport.CRICKET, "x").event
        assertFalse(policy.isStale(l.copy(updatedMs = now - 119_000L), now))
        assertTrue(policy.isStale(l.copy(updatedMs = now - 121_000L), now))
        val u = upcoming("2", Sport.CRICKET, "x", now + 3_600_000L).event
        assertFalse(policy.isStale(u.copy(updatedMs = now - 29 * 60_000L), now))
        assertTrue(policy.isStale(u.copy(updatedMs = now - 31 * 60_000L), now))
        val far = upcoming("3", Sport.CRICKET, "x", now + 3 * 24 * 3_600_000L).event
        assertFalse(policy.isStale(far.copy(updatedMs = now - 11 * 3_600_000L), now))
        assertTrue(policy.isStale(far.copy(updatedMs = now - 13 * 3_600_000L), now))
    }

    @Test
    fun oneOffJobTwoMinutesBeforeTheNextStartThenThreeAfter() {
        val start = now + 3_600_000L
        val followed = listOf(upcoming("1", Sport.CRICKET, "x", start), upcoming("2", Sport.CRICKET, "y", start + 3_600_000L))
        assertEquals(start - 2 * 60_000L, policy.nextOneOffMs(followed, now))
        assertEquals(start + 3 * 60_000L, policy.nextOneOffMs(followed, start - 60_000L))
        assertEquals(start + 3_600_000L - 2 * 60_000L, policy.nextOneOffMs(followed, start + 4 * 60_000L))
        assertNull(policy.nextOneOffMs(listOf(live("1", Sport.CRICKET, "x")), now))
        assertNull(policy.nextOneOffMs(emptyList(), now))
        assertEquals(6L * 3_600_000L, RefreshPolicy.PERIODIC_MS)
    }

    @Test
    fun fullPlanCoversEveryFollowOnceAndTennisOnlyWhenFollowed() {
        val plan = RefreshPolicy.fullPlan(FollowStore.defaults(), source)
        assertEquals(listOf(cricket, FetchKey(Sport.FOOTBALL, "ind.1"), pl, FetchKey(Sport.FOOTBALL, "uefa.champions"), FetchKey(Sport.F1, "f1")), plan)
        val withTennis = RefreshPolicy.fullPlan(listOf(Follow.competition(Competition(Sport.TENNIS, "atp", "ATP"))), source)
        assertEquals(listOf(FetchKey(Sport.TENNIS, "atp")), withTennis)
        val team = RefreshPolicy.fullPlan(listOf(Follow.team(Team(Sport.FOOTBALL, "359", "Arsenal", "ARS"))), source)
        assertEquals(listOf(pl), team)
        val sport = RefreshPolicy.fullPlan(listOf(Follow.sport(Sport.FOOTBALL, "Football")), source)
        assertEquals(listOf(FetchKey(Sport.FOOTBALL, "ind.1"), pl), sport)
    }

    private val source = object : ScoreSource {
        override val id = "fake"
        override fun sports() = Sport.entries
        override fun competitions(sport: Sport) = when (sport) {
            Sport.FOOTBALL -> listOf(Competition(Sport.FOOTBALL, "ind.1", "ISL"), Competition(Sport.FOOTBALL, "eng.1", "PL"))
            Sport.F1 -> listOf(Competition(Sport.F1, "f1", "F1"))
            Sport.TENNIS -> listOf(Competition(Sport.TENNIS, "atp", "ATP"), Competition(Sport.TENNIS, "wta", "WTA"))
            Sport.CRICKET -> emptyList()
        }
        override fun teams(competition: Competition) = if (competition.id == "eng.1") listOf(Team(Sport.FOOTBALL, "359", "Arsenal", "ARS")) else emptyList()
        override fun scoreboard(sport: Sport, competition: Competition?, day: String) = emptyList<Event>()
        override fun event(event: Event): Event? = null
    }

    private fun onRead(followed: List<Followed>, fetched: Map<FetchKey, Long>) =
        policy.onRead(followed, fetched, online = true, fetchRunning = false, failures = 0, lastFailureMs = 0, nowMs = now)

    private fun live(id: String, sport: Sport, competition: String, state: State = State.LIVE) =
        Followed(Event(id, sport, competition, competition, "", state, "", now - 3_600_000L, null, Side("a", "A", "A"), Side("b", "B", "B"), "", now), FollowReason.COMPETITION)

    private fun upcoming(id: String, sport: Sport, competition: String, startMs: Long) =
        Followed(Event(id, sport, competition, competition, "", State.UPCOMING, "", startMs, null, Side("a", "A", "A"), Side("b", "B", "B"), "", now), FollowReason.COMPETITION)
}
