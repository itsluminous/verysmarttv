package com.itsluminous.livescore

import android.util.JsonReader
import java.io.InputStreamReader

/** The recorded ESPN payloads under `src/test/resources/fixtures/<sport>/<name>.json`. */
object Fixtures {
    const val DAY = "20261109"

    /** When the 2026-11-09 fixtures were recorded, as the `updatedMs` stamped on the parsed events. */
    const val RECORDED_MS = 1_794_000_000_000L

    fun reader(path: String): JsonReader {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$path")
            ?: throw IllegalArgumentException("no fixture fixtures/$path")
        return JsonReader(InputStreamReader(stream, Charsets.UTF_8))
    }

    /** A reader over a JSON literal, for the small shapes a test writes inline. */
    fun readerOf(json: String): JsonReader = JsonReader(java.io.StringReader(json))

    fun text(path: String): String =
        Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$path")!!.bufferedReader().use { it.readText() }
}
