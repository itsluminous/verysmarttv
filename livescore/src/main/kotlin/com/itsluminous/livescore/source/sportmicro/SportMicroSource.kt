package com.itsluminous.livescore.source.sportmicro

import android.util.JsonReader
import android.util.Log
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.HttpClient
import com.itsluminous.livescore.source.ScoreSource
import java.io.IOException
import java.io.InputStreamReader

/**
 * SportMicro's "Badminton Micro" API through RapidAPI (§13.1, ADR-044), the [ScoreSource] for the one sport ESPN
 * has no feed for. Keyed: the user's own RapidAPI key, entered in Settings and read through [key] from the app's
 * preferences, never built into the APK, a build variable or a CI secret. Without a key every fetch answers an
 * empty list and logs once, so a badminton follow costs no network and no failure backoff until the key is set.
 *
 * One URL shape, one request per day: `matches-by-date?date=eq.yyyy-MM-dd`, every tournament of the day
 * (so [Sport.oneFetchPerDay]). The free plan answers `x-ratelimit-requests-limit: 100` a day (measured
 * 2026-11-10), which is why `RefreshPolicy` fetches badminton at most every 5 minutes. The feed has no static
 * tournament list the picker could offer without the network, so [competitions] and [teams] are empty: the picker
 * lists the tournaments and players it finds in the cached events instead. The body is parsed as a stream (ADR-005).
 */
class SportMicroSource(
    private val key: () -> String,
    private val http: HttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    override val id: String = ID

    @Volatile private var saidNoKey = false

    override fun sports(): List<Sport> = listOf(Sport.BADMINTON)

    override fun competitions(sport: Sport): List<Competition> = emptyList()

    override fun teams(competition: Competition): List<Team> = emptyList()

    @Throws(IOException::class)
    override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> {
        if (sport != Sport.BADMINTON) return emptyList()
        val k = key().trim()
        if (k.isEmpty()) {
            if (!saidNoKey) {
                saidNoKey = true
                Log.i(TAG, "badminton not fetched: no data key (Settings > Badminton data key)")
            }
            return emptyList()
        }
        saidNoKey = false
        val now = clock()
        return http.stream(url(day), headers(k)).use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { SportMicroParser.parse(it, now) }
        }
    }

    override fun event(event: Event): Event? = null

    companion object {
        const val ID = "sportmicro"
        private const val TAG = "LiveScore.SportMicro"
        const val HOST = "badminton-micro.p.rapidapi.com"

        /** The day list for a `yyyyMMdd` day (the repo's day expression), as the feed's `yyyy-MM-dd`. Pure, for `SportMicroSourceTest`. */
        fun url(day: String): String {
            val iso = if (day.length == 8 && day.all(Char::isDigit)) "${day.substring(0, 4)}-${day.substring(4, 6)}-${day.substring(6, 8)}" else day
            return "https://$HOST/matches-by-date?date=eq.$iso"
        }

        /** RapidAPI's two headers: the user's key and the API's host. */
        fun headers(key: String): Map<String, String> = mapOf("x-rapidapi-key" to key, "x-rapidapi-host" to HOST)
    }
}
