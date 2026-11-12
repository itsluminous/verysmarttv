package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowKind
import com.itsluminous.livescore.model.FollowReason

/** An event together with the best reason a follow gives for it (§12.2 `followed_reason`). */
data class Followed(val event: Event, val reason: FollowReason)

/**
 * Which events the follow list covers, and why (§13.2, ADR-051). Pure; `FollowMatcherTest` is the table.
 *
 * Union semantics: an event is followed when **any** follow covers it, and is listed once with its strongest reason
 * (`event` > `team` > `competition`). A sport follow covers every event of the sport; a competition follow its
 * competition (cricket's `international` pseudo-competition every match between nations); a team follow any event the
 * team is a side of; an event follow that one event. A **country** follow covers a badminton match when any player on
 * either side carries that nationality and the match's discipline is in the follow's set, so a mixed-doubles pair with
 * one Indian counts for India; a **player** follow when that player is on either side, in the set. An empty set reads
 * as every discipline. Country and player follows report `team`: a participant, which is what the contract's reason
 * means, and the launcher's `/events` keeps its three values.
 */
object FollowMatcher {
    /** The followed events among [events], each with its strongest reason; an event two follows cover is listed once. */
    fun match(events: List<Event>, follows: List<Follow>): List<Followed> =
        events.mapNotNull { event -> reason(event, follows)?.let { Followed(event, it) } }

    /** Whether any of [follows] covers [event]. */
    fun matches(event: Event, follows: List<Follow>): Boolean = reason(event, follows) != null

    /** The strongest reason [follows] give for [event], or null when none covers it. */
    fun reason(event: Event, follows: List<Follow>): FollowReason? = follows.mapNotNull { reasonFor(it, event) }.minByOrNull { it.ordinal }

    /** The reason [follow] gives [event], or null when it does not cover it. */
    fun reasonFor(follow: Follow, event: Event): FollowReason? {
        if (event.sport != follow.sport) return null
        return when (follow.kind) {
            FollowKind.SPORT -> FollowReason.COMPETITION
            FollowKind.COMPETITION ->
                if (event.competitionId == follow.competitionId || (follow.competitionId == Competition.INTERNATIONAL && event.international)) {
                    FollowReason.COMPETITION
                } else {
                    null
                }
            FollowKind.TEAM -> if (follow.teamId in event.teamIds) FollowReason.TEAM else null
            FollowKind.EVENT -> if (event.id == follow.eventId) FollowReason.EVENT else null
            FollowKind.COUNTRY ->
                if (disciplineIn(follow, event) && event.players.any { it.country.isNotEmpty() && it.country.equals(follow.country, ignoreCase = true) }) FollowReason.TEAM else null
            FollowKind.PLAYER -> if (disciplineIn(follow, event) && event.players.any { it.id.isNotEmpty() && it.id == follow.teamId }) FollowReason.TEAM else null
        }
    }

    /** Whether the match's discipline is one the follow asks for: an empty set asks for all; a match with no discipline passes only an "All" follow. */
    private fun disciplineIn(follow: Follow, event: Event): Boolean {
        if (follow.disciplines.isEmpty()) return true
        val d = event.discipline ?: return follow.disciplines.containsAll(Discipline.ALL)
        return d in follow.disciplines
    }
}
