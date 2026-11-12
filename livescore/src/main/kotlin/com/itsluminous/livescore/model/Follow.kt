package com.itsluminous.livescore.model

import com.itsluminous.verysmarttv.core.contract.ScoreContract

/** What a follow names (§13.2, ADR-051): a whole sport, one competition, one team, one event, a country's players, or one player. */
enum class FollowKind(val token: String) {
    SPORT("sport"),
    COMPETITION("competition"),
    TEAM("team"),
    EVENT("event"),
    COUNTRY("country"),
    PLAYER("player");

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
 * One follow (§13.2, ADR-051). [competitionId], [teamId] and [eventId] are set according to [kind]; [label] is
 * what the picker and the backup show. An event follow carries [expiresMs] (`end_ms + 6 h`). A country follow names
 * its nationality code in [country]; a player follow keeps the player's id in [teamId] and the player's own country in
 * [country] for display; both carry [disciplines], the events they cover (empty reads as all of them). What a follow
 * covers is [com.itsluminous.livescore.FollowMatcher]'s rule.
 */
data class Follow(
    val kind: FollowKind,
    val sport: Sport,
    val competitionId: String = "",
    val teamId: String = "",
    val eventId: String = "",
    val label: String,
    val expiresMs: Long = 0L,
    val country: String = "",
    val disciplines: Set<Discipline> = emptySet(),
) {
    /** The follow's identity: two follows with the same key are the same follow (a new discipline set replaces the old). */
    val key: String get() = "${kind.token}:${sport.token}:$competitionId:$teamId:$eventId:$country"

    companion object {
        fun sport(sport: Sport, label: String) = Follow(FollowKind.SPORT, sport, label = label)

        fun competition(c: Competition) = Follow(FollowKind.COMPETITION, c.sport, competitionId = c.id, label = c.name)

        fun team(t: Team) = Follow(FollowKind.TEAM, t.sport, teamId = t.id, label = t.name)

        fun event(e: Event, expiresMs: Long) =
            Follow(FollowKind.EVENT, e.sport, competitionId = e.competitionId, eventId = e.id, label = "${e.home.abbr} v ${e.away.abbr}", expiresMs = expiresMs)

        /** Every badminton match with a player of [code] on court, in [disciplines]; [label] is the country's name. */
        fun country(code: String, label: String, disciplines: Set<Discipline>) =
            Follow(FollowKind.COUNTRY, Sport.BADMINTON, label = label, country = code, disciplines = disciplines)

        /** Every badminton match [player] is in, in [disciplines]. */
        fun player(player: Player, disciplines: Set<Discipline>) =
            Follow(FollowKind.PLAYER, Sport.BADMINTON, teamId = player.id, label = player.name, country = player.country, disciplines = disciplines)
    }
}
