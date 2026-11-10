package com.itsluminous.livescore.model

import com.itsluminous.verysmarttv.core.contract.ScoreContract

/**
 * The sports the companion follows; [token] is the value the provider's `sport` column carries (§12.2).
 * [oneFetchPerDay] marks the feeds that answer every competition of a day in one request (cricket's
 * scorepanel, the badminton day list), so a [com.itsluminous.livescore.FetchKey] for them names no competition.
 */
enum class Sport(val token: String, val oneFetchPerDay: Boolean = false) {
    CRICKET(ScoreContract.SPORT_CRICKET, oneFetchPerDay = true),
    FOOTBALL(ScoreContract.SPORT_FOOTBALL),
    F1(ScoreContract.SPORT_F1),
    TENNIS(ScoreContract.SPORT_TENNIS),
    BADMINTON(ScoreContract.SPORT_BADMINTON, oneFetchPerDay = true);

    companion object {
        fun fromToken(token: String?): Sport? = entries.firstOrNull { it.token == token }
    }
}

/**
 * A competition a source knows (§13.3): [id] is the source's own key (an ESPN league id for cricket, a
 * league slug such as `eng.1` for football, `f1`, `atp`/`wta`), unique within the sport.
 */
data class Competition(val sport: Sport, val id: String, val name: String, val shortName: String = name) {
    companion object {
        /** The cricket pseudo-competition that stands for every match between nations (§13.2 "internationals"). */
        const val INTERNATIONAL = "international"
    }
}

/** A team or player a follow can name; [id] is the source's id, unique within the sport. */
data class Team(val sport: Sport, val id: String, val name: String, val abbr: String)
