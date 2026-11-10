package com.itsluminous.livescore

import com.itsluminous.livescore.model.State

/**
 * Which followed events the pill shows, in order (§12.2): live > break > upcoming within 12 h > final within
 * 6 h; ties by follow reason (`event` > `team` > `competition`), then earliest start. The first row is
 * [choose]: several live with the same reason alternate every 20 s there, so a launcher showing one line
 * gets both matches in turn; [ranked] follows it with every other eligible event in that order, for a
 * launcher that stacks several lines (it cuts the list to its own row count). Pure; `PillChooserTest`.
 */
object PillChooser {
    const val UPCOMING_WINDOW_MS = 12L * 3600_000L
    const val FINAL_WINDOW_MS = 6L * 3600_000L
    const val ALTERNATE_MS = 20_000L

    private val ORDER = compareBy<Followed> { rank(it.event.state) }.thenBy { it.reason.ordinal }.thenBy { it.event.startMs }

    fun choose(candidates: List<Followed>, nowMs: Long): Followed? {
        val eligible = candidates.filter { eligible(it, nowMs) }
        if (eligible.isEmpty()) return null
        val best = eligible.minWith(ORDER)
        if (best.event.state != State.LIVE) return best
        // Every live event followed for the same reason as the best one takes a 20 s turn, earliest start first.
        val turns = eligible.filter { it.event.state == State.LIVE && it.reason == best.reason }.sortedBy { it.event.startMs }
        if (turns.size < 2) return best
        return turns[((nowMs / ALTERNATE_MS) % turns.size).toInt()]
    }

    /**
     * Every eligible event, the pill's own ([choose]) first and the rest in rank order (live, break, upcoming
     * soonest, recent finals; reason, then start within a state). Empty when nothing is eligible.
     */
    fun ranked(candidates: List<Followed>, nowMs: Long): List<Followed> {
        val first = choose(candidates, nowMs) ?: return emptyList()
        return listOf(first) + candidates.filter { it !== first && eligible(it, nowMs) }.sortedWith(ORDER)
    }

    /** Whether the companion should notify observers on a 20 s cadence: two or more live events share the pill. */
    fun alternates(candidates: List<Followed>, nowMs: Long): Boolean {
        val live = candidates.filter { it.event.state == State.LIVE && eligible(it, nowMs) }
        if (live.size < 2) return false
        val bestReason = live.minOf { it.reason.ordinal }
        return live.count { it.reason.ordinal == bestReason } >= 2
    }

    private fun eligible(f: Followed, nowMs: Long): Boolean =
        when (f.event.state) {
            State.LIVE, State.BREAK -> true
            State.UPCOMING -> f.event.startMs - nowMs <= UPCOMING_WINDOW_MS
            // endMs is when the cache first saw the event final (EventCache), not the feed's scheduled last day.
            State.FINAL -> nowMs - (f.event.endMs ?: f.event.startMs) <= FINAL_WINDOW_MS
        }

    private fun rank(state: State): Int =
        when (state) {
            State.LIVE -> 0
            State.BREAK -> 1
            State.UPCOMING -> 2
            State.FINAL -> 3
        }
}
