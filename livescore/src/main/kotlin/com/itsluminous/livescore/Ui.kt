package com.itsluminous.livescore

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
