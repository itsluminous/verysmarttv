package com.itsluminous.livescore

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.DisciplineGroup
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.State

/** The bits every screen shares: sport names, the kicker colour, the "updated" line, row inflation. */
internal object Ui {
    fun sportName(c: Context, sport: Sport): String =
        c.getString(
            when (sport) {
                Sport.CRICKET -> R.string.sport_cricket
                Sport.FOOTBALL -> R.string.sport_football
                Sport.F1 -> R.string.sport_f1
                Sport.TENNIS -> R.string.sport_tennis
                Sport.BADMINTON -> R.string.sport_badminton
            },
        )

    /** Sets the kicker's text and colour from the formatter's pair. */
    fun kicker(view: TextView, kicker: Pair<String, String>) {
        view.text = kicker.first
        view.setTextColor(Color.parseColor(kicker.second))
    }

    fun updated(c: Context, repo: ScoreRepo, event: Event?, nowMs: Long): String {
        val ms = event?.updatedMs ?: repo.lastFetchMs()
        if (ms <= 0L) return c.getString(R.string.updated_never)
        val text = if (nowMs - ms < DateUtils.MINUTE_IN_MILLIS) {
            c.getString(R.string.updated_now)
        } else {
            c.getString(R.string.updated_ago, DateUtils.getRelativeTimeSpanString(ms, nowMs, DateUtils.MINUTE_IN_MILLIS))
        }
        return if (event != null && repo.isStale(event, nowMs)) c.getString(R.string.row_meta, text, c.getString(R.string.stale)) else text
    }

    fun inflate(parent: ViewGroup, layout: Int): View = LayoutInflater.from(parent.context).inflate(layout, parent, false)

    /**
     * A badminton nationality code as a name (ADR-051): the four non-ISO BWF members from strings, the rest from the platform
     * in the TV's language, the code itself when the platform does not know it, and the "no nationality" line for an empty code.
     */
    fun countryName(c: Context, code: String): String =
        when (code.uppercase()) {
            "" -> c.getString(R.string.country_none)
            "TPE" -> c.getString(R.string.country_tpe)
            "ENG" -> c.getString(R.string.country_eng)
            "SCO" -> c.getString(R.string.country_sco)
            "WAL" -> c.getString(R.string.country_wal)
            else -> BadmintonNations.name(code, c.resources.configuration.locales[0]) ?: code.uppercase()
        }

    /** A follow's discipline set as the user reads it: "All", or the ticked groups ("Singles, Mixed doubles"). */
    fun disciplineFilter(c: Context, disciplines: Set<Discipline>): String {
        if (DisciplineGroup.isAll(disciplines)) return c.getString(R.string.discipline_all)
        return DisciplineGroup.groupsOf(disciplines).joinToString(c.getString(R.string.discipline_list_separator)) { groupName(c, it) }
    }

    fun groupName(c: Context, group: DisciplineGroup): String =
        c.getString(
            when (group) {
                DisciplineGroup.SINGLES -> R.string.discipline_singles
                DisciplineGroup.DOUBLES -> R.string.discipline_doubles
                DisciplineGroup.MIXED -> R.string.discipline_mixed
            },
        )

    /** The teams line of an event row, per sport. */
    fun line(c: Context, e: Event): String =
        when (e.sport) {
            Sport.F1 -> if (e.home.score.isEmpty() || e.state == State.FINAL) e.home.shortName else c.getString(R.string.row_leader, e.home.shortName, e.home.score)
            else -> if (e.home.score.isEmpty() && e.away.score.isEmpty()) {
                c.getString(R.string.row_versus, e.home.name, e.away.name)
            } else {
                c.getString(R.string.row_score, e.home.abbr, e.home.score.ifEmpty { "–" }, e.away.abbr, e.away.score.ifEmpty { "–" })
            }
        }
}
