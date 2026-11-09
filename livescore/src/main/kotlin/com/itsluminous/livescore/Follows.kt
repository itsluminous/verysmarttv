package com.itsluminous.livescore

import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.FollowReason

/** An event together with the best reason a follow gives for it (§12.2 `followed_reason`). */
data class Followed(val event: Event, val reason: FollowReason)

/** Which events the follow list covers, and why. Pure. */
object Follows {
    /** The followed events among [events], each with its strongest reason (`event` > `team` > `competition`). */
    fun match(events: List<Event>, follows: List<Follow>): List<Followed> =
        events.mapNotNull { event ->
            follows.mapNotNull { it.reasonFor(event) }.minByOrNull { it.ordinal }?.let { Followed(event, it) }
        }
}
