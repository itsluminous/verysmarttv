package com.itsluminous.livescore.model

import com.itsluminous.verysmarttv.core.contract.ScoreContract

/** What a follow names (§13.2): a whole sport, one competition, one team, or one event. */
enum class FollowKind(val token: String) {
    SPORT("sport"),
    COMPETITION("competition"),
    TEAM("team"),
    EVENT("event");

    companion object {
        fun fromToken(token: String?): FollowKind? = entries.firstOrNull { it.token == token }
    }
}

/** Why an event is in the window; the provider's `followed_reason` column. Ordered by priority (§12.2). */
enum class FollowReason(val token: String) {
    EVENT(ScoreContract.REASON_EVENT),
    TEAM(ScoreContract.REASON_TEAM),
    COMPETITION(ScoreContract.REASON_COMPETITION),
}

/**
 * One follow (§13.2). [competitionId], [teamId] and [eventId] are set according to [kind]; [label] is
 * what the picker and the backup show. An event follow carries [expiresMs] (`end_ms + 6 h`).
 */
data class Follow(
    val kind: FollowKind,
    val sport: Sport,
    val competitionId: String = "",
    val teamId: String = "",
    val eventId: String = "",
    val label: String,
    val expiresMs: Long = 0L,
) {
    /** The follow's identity: two follows with the same key are the same follow. */
    val key: String get() = "${kind.token}:${sport.token}:$competitionId:$teamId:$eventId"

    /** The reason this follow gives [event], or null when it does not cover it. */
    fun reasonFor(event: Event): FollowReason? {
        if (event.sport != sport) return null
        return when (kind) {
            FollowKind.SPORT -> FollowReason.COMPETITION
            FollowKind.COMPETITION ->
                if (event.competitionId == competitionId || (competitionId == Competition.INTERNATIONAL && event.international)) {
                    FollowReason.COMPETITION
                } else {
                    null
                }
            FollowKind.TEAM -> if (teamId in event.teamIds) FollowReason.TEAM else null
            FollowKind.EVENT -> if (event.id == eventId) FollowReason.EVENT else null
        }
    }

    companion object {
        fun sport(sport: Sport, label: String) = Follow(FollowKind.SPORT, sport, label = label)

        fun competition(c: Competition) = Follow(FollowKind.COMPETITION, c.sport, competitionId = c.id, label = c.name)

        fun team(t: Team) = Follow(FollowKind.TEAM, t.sport, teamId = t.id, label = t.name)

        fun event(e: Event, expiresMs: Long) =
            Follow(FollowKind.EVENT, e.sport, competitionId = e.competitionId, eventId = e.id, label = "${e.home.abbr} v ${e.away.abbr}", expiresMs = expiresMs)
    }
}
