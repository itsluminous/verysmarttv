package com.itsluminous.livescore

import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowKind
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.ScoreSource

/** One fetch: a sport and, for the per-competition feeds, the competition; a day-wide feed ([Sport.oneFetchPerDay]) has none. */
data class FetchKey(val sport: Sport, val competitionId: String?) {
    val token: String get() = "${sport.token}:${competitionId ?: "*"}"

    companion object {
        fun of(event: Event): FetchKey = if (event.sport.oneFetchPerDay) FetchKey(event.sport, null) else FetchKey(event.sport, event.competitionId)
    }
}

/**
 * When to fetch (§13.5, ADR-014), as pure decisions over a clock the caller supplies; `RefreshPolicyTest`.
 *
 * - A provider read while a followed event is live or at a break fetches that competition when its
 *   last fetch is older than [LIVE_READ_MS]; an upcoming followed event about to start (or started, with
 *   the cache still saying `pre`) fetches at [MATCH_DAY_MS].
 * - Nothing on screen: a one-off job 2 min before the next followed start (and 3 min after it, for the
 *   first live reading), and a periodic job every 6 h for the fixture list.
 * - A failed fetch backs off 1, 2, 4, 8 min; offline, nothing is fetched.
 * - Badminton's feed is the user's RapidAPI key on a 100-requests-a-day free plan (ADR-044), so a live badminton
 *   match is fetched at most every [BADMINTON_LIVE_MS], whatever screen reads it; the other sports keep [LIVE_MS].
 */
class RefreshPolicy {
    /** How often an event in [state] is worth re-fetching; `stale` is twice this. */
    fun intervalMs(state: State, startMs: Long, nowMs: Long, sport: Sport? = null): Long =
        when (state) {
            State.LIVE, State.BREAK -> liveIntervalMs(sport)
            State.UPCOMING -> if (startMs - nowMs <= DAY_MS) MATCH_DAY_MS else QUIET_MS
            State.FINAL -> QUIET_MS
        }

    /** The live cadence of a sport's feed: 5 min for badminton's quota, 60 s elsewhere. */
    fun liveIntervalMs(sport: Sport?): Long = if (sport == Sport.BADMINTON) BADMINTON_LIVE_MS else LIVE_MS

    /**
     * The least time between two fetches of one key that a screen's own timer may not shorten: badminton's
     * [BADMINTON_LIVE_MS] (the open scoreboard's 30 s refresh reads the cache until then), 0 for every other sport.
     */
    fun minFetchGapMs(sport: Sport): Long = if (sport == Sport.BADMINTON) BADMINTON_LIVE_MS else 0L

    /** Whether the row's data is older than the refresh interval for its state allows (the launcher dims the pill). */
    fun isStale(event: Event, nowMs: Long): Boolean = nowMs - event.updatedMs > 2 * intervalMs(event.state, event.startMs, nowMs, event.sport)

    /**
     * The fetches a provider read should start now: none while offline, while one runs, or inside a
     * failure's backoff; else one per competition with a live or break followed event whose fetch is older
     * than [LIVE_READ_MS], plus one per competition whose followed event starts within [SOON_MS] or started
     * less than [LATE_MS] ago and whose fetch is older than [MATCH_DAY_MS].
     */
    fun onRead(
        followed: List<Followed>,
        fetchedMs: Map<FetchKey, Long>,
        online: Boolean,
        fetchRunning: Boolean,
        failures: Int,
        lastFailureMs: Long,
        nowMs: Long,
    ): List<FetchKey> {
        if (!online || fetchRunning) return emptyList()
        if (failures > 0 && nowMs - lastFailureMs < backoffMs(failures)) return emptyList()
        val keys = LinkedHashSet<FetchKey>()
        for (f in followed) {
            val key = FetchKey.of(f.event)
            val age = nowMs - (fetchedMs[key] ?: 0L)
            when (f.event.state) {
                State.LIVE, State.BREAK -> if (age >= liveReadMs(key.sport)) keys.add(key)
                State.UPCOMING -> {
                    val untilStart = f.event.startMs - nowMs
                    if (untilStart <= SOON_MS && untilStart >= -LATE_MS && age >= MATCH_DAY_MS) keys.add(key)
                }
                State.FINAL -> Unit
            }
        }
        return keys.toList()
    }

    /** The age at which a provider read re-fetches a live key: 5 s short of the sport's live interval, so a reader at that cadence never misses. */
    fun liveReadMs(sport: Sport): Long = liveIntervalMs(sport) - READ_SLACK_MS

    /** 1, 2, 4, 8 minutes, then 8. */
    fun backoffMs(failures: Int): Long = (1L shl (failures - 1).coerceIn(0, 3)) * 60_000L

    /**
     * When the next one-off job should run: 2 min before the earliest followed start still ahead, or 3 min
     * after a start that just passed (so the first live reading lands without a screen); null when nothing
     * followed is upcoming.
     */
    fun nextOneOffMs(followed: List<Followed>, nowMs: Long): Long? =
        followed.filter { it.event.state == State.UPCOMING }
            .mapNotNull { f ->
                val before = f.event.startMs - START_LEAD_MS
                val after = f.event.startMs + START_LAG_MS
                if (before > nowMs) before else if (after > nowMs) after else null
            }
            .minOrNull()

    companion object {
        const val LIVE_MS = 60_000L
        const val LIVE_READ_MS = 55_000L
        const val BADMINTON_LIVE_MS = 5L * 60_000L
        private const val READ_SLACK_MS = LIVE_MS - LIVE_READ_MS
        const val MATCH_DAY_MS = 15L * 60_000L
        const val QUIET_MS = 6L * 3600_000L
        const val PERIODIC_MS = QUIET_MS
        const val SOON_MS = 15L * 60_000L
        const val LATE_MS = 3L * 3600_000L
        const val START_LEAD_MS = 2L * 60_000L
        const val START_LAG_MS = 3L * 60_000L
        private const val DAY_MS = 24L * 3600_000L

        /** Every fetch a full refresh needs for [follows]: one per followed competition, a day-wide feed (cricket, badminton) once, tennis only when followed. */
        fun fullPlan(follows: List<Follow>, source: ScoreSource): List<FetchKey> {
            val keys = LinkedHashSet<FetchKey>()
            for (f in follows) {
                when (f.sport) {
                    Sport.CRICKET, Sport.BADMINTON -> keys.add(FetchKey(f.sport, null))
                    Sport.F1 -> keys.add(FetchKey(Sport.F1, source.competitions(Sport.F1).firstOrNull()?.id ?: "f1"))
                    Sport.FOOTBALL, Sport.TENNIS -> when (f.kind) {
                        FollowKind.SPORT -> source.competitions(f.sport).forEach { keys.add(FetchKey(f.sport, it.id)) }
                        FollowKind.COMPETITION, FollowKind.EVENT -> if (f.competitionId.isNotEmpty()) keys.add(FetchKey(f.sport, f.competitionId))
                        FollowKind.TEAM -> source.competitions(f.sport)
                            .filter { c -> source.teams(c).any { it.id == f.teamId } }
                            .forEach { keys.add(FetchKey(f.sport, it.id)) }
                        // Badminton-only kinds (ADR-051): the day-wide key above already covers them.
                        FollowKind.COUNTRY, FollowKind.PLAYER -> Unit
                    }
                }
            }
            return keys.toList()
        }
    }
}
