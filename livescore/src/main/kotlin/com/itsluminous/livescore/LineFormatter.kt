package com.itsluminous.livescore

import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The pill's text (§12.2, ADR-015): the one-line [line] per sport and the [kicker] per state, computed
 * here so the launcher renders and never reasons about sports. Pure; `LineFormatterTest` holds the table.
 *
 * - cricket, limited overs: `T20I · AFG 19/4 (6) v ZIM`; Tests: `Test · BAN 386 & 49/2 v WI`
 * - football: `Premier League · ARS 2–1 CHE 67'`; upcoming `ISL · Bengaluru v Mumbai City`
 * - F1: `F1 · São Paulo GP · Race`; live `F1 · São Paulo GP · Lap 32 · NOR leads`
 * - tennis: `Stockholm Open · Gojo 7-5 4-6 7-6 Brunold`
 *
 * Every line is at most [ScoreContract.LINE_MAX_CHARS] characters; a longer one ends in an ellipsis.
 */
class LineFormatter(private val zone: ZoneId, private val locale: Locale = Locale.getDefault()) {
    /** The kicker text and its colour for [event] at [nowMs]. The texts are data the launcher renders (ADR-035, exempt). */
    fun kicker(event: Event, nowMs: Long): Pair<String, String> =
        when (event.state) {
            State.LIVE -> "LIVE" to COLOR_LIVE
            State.BREAK -> breakWord(event) to COLOR_BREAK
            State.UPCOMING -> startTime(event.startMs, nowMs) to COLOR_GREY
            State.FINAL -> finalWord(event.sport) to COLOR_GREY
        }

    fun line(event: Event): String =
        shorten(
            when (event.sport) {
                Sport.CRICKET -> cricket(event)
                Sport.FOOTBALL -> football(event)
                Sport.F1 -> f1(event)
                Sport.TENNIS -> tennis(event)
            },
        )

    private fun cricket(e: Event): String {
        val prefix = e.format.ifEmpty { e.competition }
        return when (e.state) {
            State.UPCOMING -> "$prefix · ${e.home.abbr} v ${e.away.abbr}"
            State.FINAL -> {
                val summary = e.summary.trim()
                if (summary.contains(" won ") || summary.contains(" win ")) {
                    "$prefix · ${summary.substringBefore(" (")}"
                } else {
                    "$prefix · ${e.home.abbr} v ${e.away.abbr} · ${summary.ifEmpty { "Result" }}"
                }
            }
            State.LIVE, State.BREAK -> {
                val batting = if (e.away.isBatting && !e.home.isBatting) e.away else e.home
                val other = if (batting === e.home) e.away else e.home
                val score = batting.score.substringBefore(" (").trim()
                if (score.isEmpty()) return "$prefix · ${e.home.abbr} v ${e.away.abbr}"
                val current = batting.innings.lastOrNull { it.isCurrent } ?: batting.innings.lastOrNull()
                val overs = if (score.contains('&') || current == null) "" else " (${overs(current.overs)})"
                "$prefix · ${batting.abbr} $score$overs v ${other.abbr}"
            }
        }
    }

    private fun football(e: Event): String {
        val comp = e.competition
        return when (e.state) {
            State.UPCOMING -> "$comp · ${e.home.shortName} v ${e.away.shortName}"
            State.LIVE -> "$comp · ${e.home.abbr} ${e.home.score}–${e.away.score} ${e.away.abbr} ${e.clock}".trim()
            State.BREAK, State.FINAL -> "$comp · ${e.home.abbr} ${e.home.score}–${e.away.score} ${e.away.abbr}"
        }
    }

    private fun f1(e: Event): String {
        val gp = "F1 · ${e.home.shortName}"
        val session = e.session.ifEmpty { e.statusDetail }
        return when (e.state) {
            State.LIVE -> {
                val lap = if (e.period > 0) "Lap ${e.period}" else session
                if (e.home.abbr.isEmpty()) "$gp · $lap" else "$gp · $lap · ${e.home.abbr} leads"
            }
            State.FINAL -> if (e.home.score.isEmpty()) "$gp · $session" else "$gp · $session · ${lastName(e.home.score)} wins"
            State.UPCOMING, State.BREAK -> "$gp · $session"
        }
    }

    private fun tennis(e: Event): String {
        val sets = sets(e.home, e.away)
        val tail = if (sets.isEmpty()) "${e.home.shortName} v ${e.away.shortName}" else "${e.home.shortName} $sets ${e.away.shortName}"
        val full = "${e.competition} · $tail"
        if (full.length <= ScoreContract.LINE_MAX_CHARS) return full
        val room = ScoreContract.LINE_MAX_CHARS - tail.length - 3
        return if (room >= 4) "${e.competition.take(room - 1).trimEnd()}… · $tail" else tail
    }

    /** `7 4 7(9)` and `5 6 6(7)` → `7-5 4-6 7-6`; a tiebreak's points are left to the detail view. */
    private fun sets(a: Side, b: Side): String {
        val sa = a.score.split(' ').filter { it.isNotEmpty() }
        val sb = b.score.split(' ').filter { it.isNotEmpty() }
        return sa.indices.map { i -> "${sa[i].substringBefore('(')}-${sb.getOrElse(i) { "" }.substringBefore('(')}".trimEnd('-') }.joinToString(" ")
    }

    private fun breakWord(e: Event): String {
        val word = e.statusDetail.trim()
        return when {
            word.equals("halftime", true) || word.equals("half-time", true) || word.equals("half time", true) -> "HT"
            word.isEmpty() -> "BREAK"
            word.length <= KICKER_MAX -> word.uppercase(locale)
            else -> word.uppercase(locale).substringBefore(' ').take(KICKER_MAX)
        }
    }

    private fun finalWord(sport: Sport): String =
        when (sport) {
            Sport.FOOTBALL -> "FT"
            Sport.CRICKET -> "RESULT"
            Sport.F1, Sport.TENNIS -> "FINAL"
        }

    /** `19:30` today, `TOMORROW 19:30`, else the weekday `SAT 19:30`, in the TV's zone. */
    private fun startTime(startMs: Long, nowMs: Long): String {
        if (startMs <= 0) return "SOON"
        val start = Instant.ofEpochMilli(startMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = start.format(TIME)
        return when (start.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "TOMORROW $time"
            else -> "${start.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale)} $time"
        }
    }

    companion object {
        const val COLOR_LIVE = "#E53935"
        const val COLOR_BREAK = "#FFB300"
        const val COLOR_GREY = "#9E9E9E"
        private const val KICKER_MAX = 12
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        /** `6.0` → `6`, `4.2` → `4.2`. */
        fun overs(overs: Double): String =
            if (overs == Math.floor(overs)) overs.toInt().toString() else String.format(Locale.ROOT, "%.1f", overs)

        /** At most [ScoreContract.LINE_MAX_CHARS] characters, cut on a word boundary with an ellipsis. */
        fun shorten(text: String): String {
            val max = ScoreContract.LINE_MAX_CHARS
            if (text.length <= max) return text
            val cut = text.take(max - 1)
            val space = cut.lastIndexOf(' ')
            return (if (space > max / 2) cut.substring(0, space) else cut).trimEnd(' ', '·') + "…"
        }

        fun lastName(name: String): String = name.trim().substringAfterLast(' ')
    }
}
