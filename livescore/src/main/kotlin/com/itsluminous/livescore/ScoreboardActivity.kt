package com.itsluminous.livescore

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Side
import com.itsluminous.livescore.model.Sport
import com.itsluminous.verysmarttv.core.contract.ScoreContract

/**
 * One event's detail (§13.6): cricket's innings per side and the current batters and bowlers when the
 * feed has them; football's goals and cards; F1 session results; tennis sets. Refreshes every 30 s while
 * resumed. Opened by the launcher's pill with [ScoreContract.EXTRA_EVENT_ID].
 */
class ScoreboardActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: ScoreRepo
    private var eventId = ""
    private val refresh = object : Runnable {
        override fun run() {
            val e = repo.event(eventId)
            if (e != null && e.isLiveOrBreak || e != null && e.details.isEmpty()) {
                repo.refreshOne(e) { fresh -> if (fresh != null) render(fresh) }
            }
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)
        repo = ScoreRepo.get(this)
        eventId = intent.getStringExtra(ScoreContract.EXTRA_EVENT_ID).orEmpty()
    }

    override fun onResume() {
        super.onResume()
        val e = repo.event(eventId)
        if (e == null) {
            findViewById<TextView>(R.id.title).text = getString(R.string.event_missing)
            return
        }
        render(e)
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    internal fun render(e: Event) {
        val now = System.currentTimeMillis()
        Ui.kicker(findViewById(R.id.kicker), repo.formatter.kicker(e, now))
        val parts = listOf(Ui.sportName(this, e.sport), e.competition, e.format, e.session, e.statusDetail).filter { it.isNotEmpty() }.distinct()
        findViewById<TextView>(R.id.header).text = parts.joinToString(" · ")
        findViewById<TextView>(R.id.updated).text = Ui.updated(this, repo, e, now)
        findViewById<TextView>(R.id.title).text = Ui.line(this, e)
        findViewById<TextView>(R.id.summary).text = e.summary
        side(findViewById(R.id.homeBlock), e.home, e.sport)
        val away = findViewById<LinearLayout>(R.id.awayBlock)
        if (e.sport == Sport.F1) away.visibility = View.GONE else side(away, e.away, e.sport)
        val details = findViewById<LinearLayout>(R.id.details)
        details.removeAllViews()
        if (e.details.isEmpty()) {
            val row = Ui.inflate(details, R.layout.detail_row)
            row.findViewById<TextView>(R.id.primary).text = getString(if (e.isLiveOrBreak || e.sport == Sport.CRICKET) R.string.refreshing else R.string.no_details)
            details.addView(row)
        }
        for (d in e.details) {
            val row = Ui.inflate(details, R.layout.detail_row)
            row.findViewById<TextView>(R.id.primary).text = d.primary
            row.findViewById<TextView>(R.id.secondary).text = d.secondary
            details.addView(row)
        }
    }

    private fun side(block: LinearLayout, s: Side, sport: Sport) {
        block.removeAllViews()
        val view = Ui.inflate(block, R.layout.side_block)
        view.findViewById<TextView>(R.id.sideName).text = if (s.isBatting) getString(R.string.row_meta, s.name, getString(R.string.batting_now)) else s.name
        view.findViewById<TextView>(R.id.sideScore).text = if (sport == Sport.CRICKET) s.score.substringBefore(" (") else s.score
        val innings = view.findViewById<LinearLayout>(R.id.sideInnings)
        for (i in s.innings) {
            val row = Ui.inflate(innings, R.layout.detail_row)
            val overs = LineFormatter.overs(i.overs)
            row.findViewById<TextView>(R.id.primary).text = when {
                i.declared -> getString(R.string.innings_declared, i.runs, i.wickets, overs)
                i.wickets >= ALL_OUT && !i.isCurrent -> getString(R.string.innings_all_out, i.runs, overs)
                else -> getString(R.string.innings_line, i.runs, i.wickets, overs)
            }
            innings.addView(row)
        }
        block.addView(view)
    }

    companion object {
        private const val REFRESH_MS = 30_000L
        private const val ALL_OUT = 10
    }
}
