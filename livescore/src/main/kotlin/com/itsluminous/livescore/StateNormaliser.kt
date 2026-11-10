package com.itsluminous.livescore

import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus

/**
 * The feeds' states into our four (§13.4). ESPN's `pre` is upcoming, `post` final, and `in` live unless the
 * description is one of the pauses below, or the match has not begun (`period 0` with a "Starts at" summary,
 * as the Quaid-e-Azam Trophy showed on 2026-11-09). SportDevs' badminton feed (ADR-044) names its states in
 * words: `upcoming` / `notstarted`, `live` / `inprogress`, `finished` / `ended`, and the endings and pauses
 * as their own types (`walkover`, `retired`, `canceled`, `postponed`, `interrupted`, `suspended`, `delayed`),
 * which read the same way as the ESPN descriptions. Pure; the table test is `StateNormaliserTest`.
 */
object StateNormaliser {
    /** Descriptions ESPN uses while a started match is not being played. Matched case-insensitively, as a prefix. */
    private val PAUSES = listOf(
        "stumps", "lunch", "tea", "dinner", "drinks", "innings break", "delay", "rain", "halftime", "half-time",
        "half time", "suspended", "bad light", "wet outfield", "break", "end of", "interrupted", "postponed",
        "abandoned", "cancel", "no result", "walkover", "retired", "red flag", "pause", "interval",
    )

    /** Descriptions that end a match in `in` state without a `post` (abandoned, cancelled); treated as final. */
    private val ENDINGS = listOf("abandoned", "cancel", "no result", "walkover", "retired", "postponed")

    /** The words a feed may use for the three ESPN states, SportDevs' included. */
    private val UPCOMING_WORDS = listOf("pre", "upcoming", "notstarted", "not started", "scheduled")
    private val LIVE_WORDS = listOf("in", "live", "inprogress", "in progress")
    private val FINAL_WORDS = listOf("post", "finished", "ended", "final")

    fun normalise(status: FeedStatus): State {
        val description = status.description.trim().lowercase()
        val state = status.state.trim().lowercase()
        return when {
            state in UPCOMING_WORDS -> State.UPCOMING
            state in FINAL_WORDS -> State.FINAL
            state in LIVE_WORDS -> normaliseIn(status, description)
            ENDINGS.any { state.startsWith(it) } -> State.FINAL
            PAUSES.any { state.startsWith(it) } -> State.BREAK
            else -> if (ENDINGS.any { description.startsWith(it) }) State.FINAL else State.UPCOMING
        }
    }

    private fun normaliseIn(status: FeedStatus, description: String): State {
        if (ENDINGS.any { description.startsWith(it) }) return State.FINAL
        if (status.period == 0 && status.summary.trim().lowercase().startsWith("starts at")) return State.UPCOMING
        if (PAUSES.any { description.startsWith(it) }) return State.BREAK
        return State.LIVE
    }
}
