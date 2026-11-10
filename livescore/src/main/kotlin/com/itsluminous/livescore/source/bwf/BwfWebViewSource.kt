package com.itsluminous.livescore.source.bwf

import android.util.JsonReader
import android.util.Log
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import com.itsluminous.livescore.source.ScoreSource
import java.io.IOException
import java.io.StringReader

/**
 * BWF's own Match Centre through a hidden WebView (ADR-049): the **experimental** badminton [ScoreSource], chosen in
 * Settings → Badminton source and off by default. No key: the page is loaded by [BwfPageLoader] (a WebView on the
 * device, a fake in tests), which hands back the raw JSON the page itself requests, and [BwfParser] streams it into the
 * same badminton [Event]s SportMicro's parser fills. The page lists the matches on court, not a day's schedule, so the
 * `day` argument only decides reuse: the two days of a full refresh share one page visit ([REUSE_MS]).
 *
 * Any failure of the visit is an [IOException] whose message starts with [UNAVAILABLE] and the loader's short reason;
 * the repo logs it, backs off, marks the rows stale and the Scores app shows the reason as a row. Nothing here can
 * reach the other sources or the launcher.
 */
class BwfWebViewSource(
    private val loader: BwfPageLoader,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScoreSource {
    override val id: String = ID

    private val lock = Any()
    private var lastEvents: List<Event> = emptyList()
    private var lastCaptureMs = 0L

    override fun sports(): List<Sport> = listOf(Sport.BADMINTON)

    /** No static catalogue: the picker lists the tournaments found in the cached events (ADR-044's rule). */
    override fun competitions(sport: Sport): List<Competition> = emptyList()

    override fun teams(competition: Competition): List<Team> = emptyList()

    @Throws(IOException::class)
    override fun scoreboard(sport: Sport, competition: Competition?, day: String): List<Event> {
        if (sport != Sport.BADMINTON) return emptyList()
        val now = clock()
        synchronized(lock) {
            if (lastCaptureMs > 0 && now - lastCaptureMs < REUSE_MS) return lastEvents
        }
        val capture = try {
            loader.load()
        } catch (e: IOException) {
            throw IOException("$UNAVAILABLE: ${e.message ?: "no reason given"}", e)
        }
        val events = try {
            parse(capture, now)
        } catch (e: IOException) {
            // JsonReader's MalformedJsonException: the page handed back something that is not JSON (an HTML error page, say).
            throw IOException("$UNAVAILABLE: the page's data changed shape (${e.message})", e)
        } catch (e: IllegalStateException) {
            throw IOException("$UNAVAILABLE: the page's data changed shape (${e.message})", e)
        } catch (e: NumberFormatException) {
            throw IOException("$UNAVAILABLE: the page's data changed shape (${e.message})", e)
        }
        if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "match centre: ${capture.liveMatches.size} tournament(s), ${events.size} match(es) on court")
        synchronized(lock) {
            lastEvents = events
            lastCaptureMs = now
        }
        return events
    }

    override fun event(event: Event): Event? = null

    companion object {
        const val ID = "bwf"
        const val UNAVAILABLE = "BWF site unavailable"
        const val REUSE_MS = 60_000L
        private const val TAG = "LiveScore.Bwf"

        /** The capture into events: the tournaments, then each tournament's matches. Pure; `BwfWebViewSourceTest`. */
        @Throws(IOException::class, IllegalStateException::class)
        fun parse(capture: BwfCapture, nowMs: Long): List<Event> {
            val tournaments = JsonReader(StringReader(capture.currentLive)).use { BwfParser.parseTournaments(it) }
            val events = ArrayList<Event>()
            for ((id, json) in capture.liveMatches) {
                val tournament = tournaments.firstOrNull { it.id == id } ?: BwfTournament(id, "BWF $id", 0L, 0L)
                JsonReader(StringReader(json)).use { events.addAll(BwfParser.parseLiveMatches(it, tournament, nowMs)) }
            }
            return events
        }
    }
}
