package com.itsluminous.livescore.source.bwf

import java.io.IOException

/**
 * What one visit to BWF's Match Centre yields (ADR-049): the raw JSON of `vue-current-live` and, per tournament id it
 * named, the raw JSON of `vue-live-matches`. Raw strings, never parsed here: the parser streams them with `JsonReader`,
 * and the recorder writes them to the fixtures as they came.
 */
data class BwfCapture(val currentLive: String, val liveMatches: Map<String, String>)

/**
 * How [BwfWebViewSource] reaches the Match Centre: one call, one page visit, the capture or an [IOException] whose
 * message is the short reason the Scores app shows (`challenge not passed`, `timed out after 30 s`, `HTTP 403`).
 * [WebViewPageLoader] is the real one; tests hand the source a fake.
 */
fun interface BwfPageLoader {
    @Throws(IOException::class)
    fun load(): BwfCapture
}

/**
 * The capture as it is assembled from the bridge's calls, in the order the page's script makes them: the tournaments
 * first, then one `matches` per tournament, then `done`; or `fail` at any point. Pure, so the state machine behind
 * the WebView is tested on the JVM (`PendingCaptureTest`) where Robolectric's WebView runs no JavaScript. Every method
 * may be called from any thread; the result is read once through [result] after [isSettled].
 */
class PendingCapture {
    private val lock = Any()
    private var tournaments: String? = null
    private val matches = LinkedHashMap<String, String>()
    private var failure: String? = null
    private var done = false
    private var challenged = false

    /** `vue-current-live` as the page received it. */
    fun tournaments(json: String) = synchronized(lock) { if (!settled()) tournaments = json }

    /** `vue-live-matches` for the tournament [id]. */
    fun matches(id: String, json: String) = synchronized(lock) { if (!settled()) matches[id] = json }

    /** The script has made every call it will make. */
    fun done() = synchronized(lock) { if (!settled()) done = true }

    /** The script, the page or the loader gave up: [reason] is what the user reads after `BWF site unavailable:`. */
    fun fail(reason: String) = synchronized(lock) { if (!settled()) failure = reason.trim().ifEmpty { "unknown error" }.take(REASON_MAX) }

    /** The page showed Cloudflare's interstitial (or answered 403/503 for the main frame): a timeout then reads as the challenge not passed. */
    fun challenged() = synchronized(lock) { challenged = true }

    /** Whether a result is ready, either way. */
    fun isSettled(): Boolean = synchronized(lock) { settled() }

    private fun settled() = done || failure != null

    /**
     * The capture, or the [IOException] the source throws. Called once the loader has finished waiting: not settled by
     * then is the timeout after [timeoutMs], or the challenge that never cleared.
     */
    @Throws(IOException::class)
    fun result(timeoutMs: Long): BwfCapture =
        synchronized(lock) {
            failure?.let { throw IOException(it) }
            if (!done) throw IOException(if (challenged) "challenge not passed" else "timed out after ${timeoutMs / 1000} s")
            val current = tournaments ?: throw IOException("no tournament list from the page")
            BwfCapture(current, LinkedHashMap(matches))
        }

    private companion object {
        const val REASON_MAX = 80
    }
}
