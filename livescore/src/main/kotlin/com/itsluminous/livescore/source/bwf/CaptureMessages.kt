package com.itsluminous.livescore.source.bwf

import android.os.Bundle
import android.os.Message

/**
 * What crosses the binder between the main process and the `:bwf` process (ADR-050), as `Messenger` messages: the
 * client's one request, and the service's reply, a capture or a failure. Pure over `Bundle` and `Message`;
 * `CaptureMessagesTest` is the round trip. A day's capture is tens of kilobytes, far under the binder's 1 MB limit.
 */
object CaptureMessages {
    /** Client → service: capture the page named by [KEY_PAGE] (the Match Centre unless a test names another); `replyTo` is the client. */
    const val MSG_CAPTURE = 1

    /** Service → client: [KEY_CURRENT] and [KEY_MATCHES] hold the raw JSON strings. */
    const val MSG_RESULT = 2

    /** Service → client: [KEY_REASON] is the short reason the Scores app shows after "BWF site unavailable". */
    const val MSG_FAILED = 3

    const val KEY_PAGE = "page"
    const val KEY_CURRENT = "current_live"
    const val KEY_MATCHES = "live_matches"
    const val KEY_REASON = "reason"

    /** The request for [page]. */
    fun request(page: String): Message = Message.obtain(null, MSG_CAPTURE).also { it.data = Bundle().apply { putString(KEY_PAGE, page) } }

    /** The page a request asks for, or null when the message is not a request. */
    fun pageOf(request: Message): String? = if (request.what == MSG_CAPTURE) request.data?.getString(KEY_PAGE) else null

    fun result(capture: BwfCapture): Message =
        Message.obtain(null, MSG_RESULT).also {
            it.data = Bundle().apply {
                putString(KEY_CURRENT, capture.currentLive)
                putBundle(KEY_MATCHES, Bundle().apply { for ((id, json) in capture.liveMatches) putString(id, json) })
            }
        }

    fun failed(reason: String): Message = Message.obtain(null, MSG_FAILED).also { it.data = Bundle().apply { putString(KEY_REASON, reason) } }

    /** The reply as the client reads it: a capture, or the failure's reason; null for a message that is neither. */
    fun decode(reply: Message): Outcome? =
        when (reply.what) {
            MSG_RESULT -> {
                val data = reply.data ?: return Outcome.Failure(BAD_REPLY)
                val current = data.getString(KEY_CURRENT) ?: return Outcome.Failure(BAD_REPLY)
                val matches = LinkedHashMap<String, String>()
                data.getBundle(KEY_MATCHES)?.let { b -> for (id in b.keySet()) b.getString(id)?.let { matches[id] = it } }
                Outcome.Success(BwfCapture(current, matches))
            }
            MSG_FAILED -> Outcome.Failure(reply.data?.getString(KEY_REASON)?.ifEmpty { null } ?: "unknown error")
            else -> null
        }

    sealed class Outcome {
        data class Success(val capture: BwfCapture) : Outcome()

        data class Failure(val reason: String) : Outcome()
    }

    private const val BAD_REPLY = "browser process answered without a capture"
}
