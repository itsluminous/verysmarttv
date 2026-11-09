package com.itsluminous.livescore.source.espn

import android.util.JsonReader
import android.util.JsonToken

/**
 * The few moves every streaming parser makes over `android.util.JsonReader`: walk an object's names,
 * walk an array, skip whatever is not read (`odds`, `broadcasts`, `venue`, headshots), and read scalars
 * that the feed sometimes types differently (`"1"` or `1`, a number or `null`).
 */
internal object Json {
    /** Reads an object, calling [onName] for each name with the reader positioned on its value; the value must be consumed. */
    inline fun obj(reader: JsonReader, onName: (String) -> Unit) {
        reader.beginObject()
        while (reader.hasNext()) onName(reader.nextName())
        reader.endObject()
    }

    /** Reads an array, calling [onItem] with the reader positioned on each element; the element must be consumed. */
    inline fun array(reader: JsonReader, onItem: () -> Unit) {
        reader.beginArray()
        while (reader.hasNext()) onItem()
        reader.endArray()
    }

    /** Reads an array of objects, calling [onName] for each name of each element. */
    inline fun objects(reader: JsonReader, crossinline onName: (String) -> Unit) {
        array(reader) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) obj(reader, onName) else reader.skipValue()
        }
    }

    /** A string, or "" for `null`; numbers and booleans are read as their text. */
    fun string(reader: JsonReader): String =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                ""
            }
        }

    /** An int from a number or a numeric string, else [default]. */
    fun int(reader: JsonReader, default: Int = 0): Int =
        when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextDouble().toInt()
            JsonToken.STRING -> reader.nextString().trim().toDoubleOrNull()?.toInt() ?: default
            else -> {
                reader.skipValue()
                default
            }
        }

    /** A double from a number or a numeric string, else [default]. */
    fun double(reader: JsonReader, default: Double = 0.0): Double =
        when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextDouble()
            JsonToken.STRING -> reader.nextString().trim().toDoubleOrNull() ?: default
            else -> {
                reader.skipValue()
                default
            }
        }

    /** A boolean from `true`/`false`, `1`/`0` or their strings, else false. */
    fun bool(reader: JsonReader): Boolean =
        when (reader.peek()) {
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> reader.nextDouble() != 0.0
            JsonToken.STRING -> reader.nextString().let { it == "true" || it == "1" }
            else -> {
                reader.skipValue()
                false
            }
        }
}
