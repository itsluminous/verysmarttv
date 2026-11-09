package com.itsluminous.livescore.model

import com.itsluminous.verysmarttv.core.contract.ScoreContract

/** Our four states (§13.4); [token] is the provider's `state` column. */
enum class State(val token: String) {
    UPCOMING(ScoreContract.STATE_UPCOMING),
    LIVE(ScoreContract.STATE_LIVE),
    BREAK(ScoreContract.STATE_BREAK),
    FINAL(ScoreContract.STATE_FINAL);

    companion object {
        fun fromToken(token: String?): State? = entries.firstOrNull { it.token == token }
    }
}

/** One innings of a cricket side, from the feed's `linescores`. */
data class Innings(
    val runs: Int,
    val wickets: Int,
    val overs: Double,
    val declared: Boolean = false,
    val isCurrent: Boolean = false,
    val isBatting: Boolean = false,
)

/**
 * One side of an event: a team, a player, or for F1 the Grand Prix with its leader. [shortName] is the
 * feed's short display name (`Brighton`, `Gojo`), what an upcoming line shows. [score] is the feed's own score
 * string (`386 & 49/2 (14 ov, target 369)`, `2`, `6-4 3-6`), kept as text because the pill shortens it
 * per sport in [com.itsluminous.livescore.LineFormatter]. [innings] is cricket only.
 */
data class Side(
    val id: String,
    val name: String,
    val abbr: String,
    val shortName: String = name,
    val score: String = "",
    val innings: List<Innings> = emptyList(),
    val isBatting: Boolean = false,
    val winner: Boolean = false,
)

/** One line of an event's detail view: a batter, a bowler, a scorer, a driver's position, a set. */
data class DetailLine(val primary: String, val secondary: String = "")

/**
 * The source-neutral event the provider, the pill and the UI read (§13.3).
 *
 * [format] is the cricket format (`T20I`, `ODI`, `Test`, `T20`, `First-class`); empty for other sports.
 * [statusDetail] keeps the feed's own words (`Stumps`, `FT`, `Lap 32`); [summary] its sentence
 * (`Bangladesh require 320 runs`). [competitionId] is the key [Competition.id] uses; [competition] the
 * name as shown. [teamIds] lists every side's id (two for a match, every driver for a race) so a team
 * follow can match without reading the sides. [international] marks a cricket match between nations
 * (the feed's `internationalClassId` is not 0), which the catalogue's [Competition.INTERNATIONAL] follows.
 */
data class Event(
    val id: String,
    val sport: Sport,
    val competitionId: String,
    val competition: String,
    val format: String,
    val state: State,
    val statusDetail: String,
    val startMs: Long,
    val endMs: Long?,
    val home: Side,
    val away: Side,
    val summary: String,
    val updatedMs: Long,
    val session: String = "",
    val period: Int = 0,
    val clock: String = "",
    val details: List<DetailLine> = emptyList(),
    val international: Boolean = false,
) {
    val teamIds: List<String> get() = listOf(home.id, away.id).filter { it.isNotEmpty() }

    val isLiveOrBreak: Boolean get() = state == State.LIVE || state == State.BREAK
}
