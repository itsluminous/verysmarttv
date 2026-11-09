package com.itsluminous.livescore.source

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import java.io.IOException

/**
 * The adapter boundary (§13.3, ADR-013): everything above it reads source-neutral [Event]s. The one
 * implementation is [com.itsluminous.livescore.source.espn.EspnSource]; the catalogue methods answer
 * without network so the follow picker and the tests need no connection.
 */
interface ScoreSource {
    /** `espn`. */
    val id: String

    /** What this source can follow. */
    fun sports(): List<Sport>

    /** The competitions of [sport], from the static catalogue. */
    fun competitions(sport: Sport): List<Competition>

    /** The teams or players of [competition] the picker offers, from the static catalogue; may be empty. */
    fun teams(competition: Competition): List<Team>

    /**
     * The events of [sport] on [day] (`yyyyMMdd`, the feed's own day). For a sport whose feed is per
     * competition ([competition] is not null) one fetch covers one competition; the cricket scorepanel
     * covers every competition in one fetch and ignores [competition].
     */
    @Throws(IOException::class)
    fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event>

    /** The fuller event (innings, batters, scorers, positions, sets), or null when the feed has none. */
    @Throws(IOException::class)
    fun event(event: Event): Event?
}
