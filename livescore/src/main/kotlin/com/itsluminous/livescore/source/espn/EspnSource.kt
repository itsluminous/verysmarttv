package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.HttpClient
import com.itsluminous.livescore.source.ScoreSource
import java.io.IOException
import java.io.InputStreamReader

/**
 * ESPN's public site feeds (§13.1, ADR-012), the [ScoreSource] of the four sports it carries. Four URL shapes:
 *
 * - cricket: `site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=yyyyMMdd` (every league of the day)
 * - football: `site.api.espn.com/apis/site/v2/sports/soccer/<league>/scoreboard?dates=yyyyMMdd` (one day only; a range is refused)
 * - F1: `site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard?dates=yyyyMMdd-yyyyMMdd`
 * - tennis: `site.api.espn.com/apis/site/v2/sports/tennis/<atp|wta>/scoreboard?dates=yyyyMMdd-yyyyMMdd`
 *
 * The `day` argument is passed through as the `dates` value: a day for cricket and football, a range for
 * F1 and tennis (checked 2026-11-09: soccer answers 400 to a range, the other two accept it).
 *
 * Every body is parsed as a stream and never held as text (ADR-005). [clock] gives the fetch time
 * stamped on each event, and the "now" the F1 parser picks its session with.
 */
class EspnSource(
    private val catalogue: EspnCatalogue,
    private val http: HttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    override val id: String = ID

    override fun sports(): List<Sport> = SPORTS

    override fun competitions(sport: Sport): List<Competition> = catalogue.competitions(sport)

    override fun teams(competition: Competition): List<Team> = catalogue.teams(competition)

    @Throws(IOException::class)
    override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> {
        val now = clock()
        val url = url(sport, competition, day) ?: return emptyList()
        return http.stream(url, emptyMap()).use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                when (sport) {
                    Sport.CRICKET -> CricketParser.parse(reader, now)
                    Sport.FOOTBALL -> SoccerParser.parse(reader, competition ?: return emptyList(), now)
                    Sport.F1 -> F1Parser.parse(reader, competition ?: catalogue.competitions(sport).first(), now, now)
                    Sport.TENNIS -> TennisParser.parse(reader, competition ?: return emptyList(), now)
                    Sport.BADMINTON -> emptyList()
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun event(event: Event): Event? {
        if (event.sport != Sport.CRICKET || event.competitionId.isEmpty()) return null
        val url = summaryUrl(event.competitionId, event.id)
        val lines = http.stream(url, emptyMap()).use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { CricketSummaryParser.parse(it) }
        }
        return event.copy(details = lines, updatedMs = clock())
    }

    companion object {
        const val ID = "espn"

        /** The four sports ESPN's feeds carry; badminton has no feed there (404, checked 2026-11-10), see `SportMicroSource`. */
        val SPORTS: List<Sport> = listOf(Sport.CRICKET, Sport.FOOTBALL, Sport.F1, Sport.TENNIS)
        private const val SITE = "https://site.api.espn.com/apis/site/v2/sports"
        private const val WEB = "https://site.web.api.espn.com/apis/site/v2/sports"

        /** The URL a fetch reads; null when the sport needs a competition and none is given. Pure, for `EspnSourceUrlTest`. */
        fun url(sport: Sport, competition: Competition?, day: String): String? =
            when (sport) {
                Sport.CRICKET -> "$WEB/cricket/scorepanel?dates=$day"
                Sport.FOOTBALL -> competition?.let { "$SITE/soccer/${it.id}/scoreboard?dates=$day" }
                Sport.F1 -> "$SITE/racing/f1/scoreboard?dates=$day"
                Sport.TENNIS -> competition?.let { "$SITE/tennis/${it.id}/scoreboard?dates=$day" }
                Sport.BADMINTON -> null
            }

        /** The cricket `summary` endpoint for one match: the league id and the event id. */
        fun summaryUrl(leagueId: String, eventId: String): String = "$WEB/cricket/$leagueId/summary?event=$eventId"
    }
}
