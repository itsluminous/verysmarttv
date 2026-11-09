package com.itsluminous.livescore

import com.itsluminous.livescore.model.State
import com.itsluminous.livescore.source.FeedStatus

/**
 * ESPN's three states into our four (§13.4): `pre` is upcoming; `post` is final; `in` is live unless the
 * description is one of the pauses below, or the match has not begun (`period 0` with a "Starts at"
 * summary, as the Quaid-e-Azam Trophy showed on 2026-11-09). Pure; the table test is `StateNormaliserTest`.
 */
object StateNormaliser {
    /** Descriptions ESPN uses while a started match is not being played. Matched case-insensitively, as a prefix. */
    private val PAUSES = listOf(
        "stumps", "lunch", "tea", "dinner", "drinks", "innings break", "delay", "rain", "halftime", "half-time",
        "half time", "suspended", "bad light", "wet outfield", "break", "end of", "interrupted", "postponed",
        "abandoned", "cancel", "no result", "walkover", "retired", "red flag",
    )

    /** Descriptions that end a match in `in` state without a `post` (abandoned, cancelled); treated as final. */
    private val ENDINGS = listOf("abandoned", "cancel", "no result", "walkover", "retired", "postponed")

    fun normalise(status: FeedStatus): State {
        val description = status.description.trim().lowercase()
        return when (status.state.lowercase()) {
            "pre" -> State.UPCOMING
            "post" -> State.FINAL
            "in" -> normaliseIn(status, description)
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
