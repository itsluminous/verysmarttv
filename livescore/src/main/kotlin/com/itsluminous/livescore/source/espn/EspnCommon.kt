package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import com.itsluminous.livescore.source.FeedStatus
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The pieces every ESPN scoreboard shares: the `status` object and the ISO dates. */
internal object EspnCommon {
    /** Reads a `status` object (`clock`, `displayClock`, `period`, `summary`, `type{state, description, detail}`). */
    fun status(reader: JsonReader): FeedStatus {
        var state = ""
        var description = ""
        var detail = ""
        var period = 0
        var clock = ""
        var summary = ""
        Json.obj(reader) { name ->
            when (name) {
                "period" -> period = Json.int(reader)
                "displayClock" -> clock = Json.string(reader)
                "summary" -> summary = Json.string(reader)
                "type" -> Json.obj(reader) { t ->
                    when (t) {
                        "state" -> state = Json.string(reader)
                        "description" -> description = Json.string(reader)
                        "detail" -> detail = Json.string(reader)
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        return FeedStatus(state, description, detail, period, clock, summary)
    }

    /** `2026-11-09T13:45Z` (no seconds) or a full offset date-time, to epoch milliseconds; 0 when unreadable. */
    fun epochMs(iso: String): Long {
        if (iso.isEmpty()) return 0L
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            0L
        }
    }

    /** A team or player abbreviation when the feed has none: the first three letters of the last word, upper case. */
    fun abbreviate(name: String): String {
        val word = name.trim().split(' ').lastOrNull { it.isNotEmpty() } ?: return ""
        return word.filter { it.isLetterOrDigit() }.take(3).uppercase()
    }
}
