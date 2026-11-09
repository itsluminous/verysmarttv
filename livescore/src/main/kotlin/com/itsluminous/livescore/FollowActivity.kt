package com.itsluminous.livescore

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Sport

/**
 * The follow picker (§13.6, ADR-034): sport → competition → team columns, each a vertical list; focus
 * on a sport fills the competitions, focus on a competition fills its teams and today's matches; OK
 * toggles the tick. Opened by the launcher on a long press of the pill (ScoreContract.FOLLOW_ACTIVITY).
 */
class FollowActivity : Activity() {
    private lateinit var repo: ScoreRepo
    private lateinit var sports: LinearLayout
    private lateinit var competitions: LinearLayout
    private lateinit var teams: LinearLayout
    private var sport: Sport? = null
    private var competition: Competition? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow)
        repo = ScoreRepo.get(this)
        sports = findViewById(R.id.sports)
        competitions = findViewById(R.id.competitions)
        teams = findViewById(R.id.teams)
        val shown = repo.source.sports().filter { it in repo.prefs.sportsShown }
        for (s in shown) {
            val follow = Follow.sport(s, Ui.sportName(this, s))
            val row = pick(sports, Ui.sportName(this, s), follow, onFocus = { showCompetitions(s) })
            if (sport == null) {
                sport = s
                row.requestFocus()
            }
        }
        sport?.let(::showCompetitions)
    }

    private fun showCompetitions(s: Sport) {
        if (sport == s && competitions.childCount > 0) return
        sport = s
        competition = null
        competitions.removeAllViews()
        pick(competitions, getString(R.string.whole_sport, Ui.sportName(this, s)), Follow.sport(s, Ui.sportName(this, s)), onFocus = { showTeams(null) })
        for (c in repo.source.competitions(s)) pick(competitions, c.name, Follow.competition(c), onFocus = { showTeams(c) })
        showTeams(null)
    }

    private fun showTeams(c: Competition?) {
        if (c != null && competition == c && teams.childCount > 0) return
        competition = c
        teams.removeAllViews()
        if (c == null) {
            val hint = TextView(this)
            hint.text = getString(R.string.no_teams)
            hint.setTextColor(HINT)
            hint.textSize = HINT_SP
            teams.addView(hint)
            return
        }
        for (t in repo.source.teams(c)) pick(teams, t.name, Follow.team(t), onFocus = null)
        val now = System.currentTimeMillis()
        val window = repo.window(now)
        for (e in repo.cache.events().filter { it.competitionId == c.id || c.id == Competition.INTERNATIONAL && it.international && it.sport == c.sport }) {
            if (e.startMs !in window && (e.endMs ?: e.startMs) !in window) continue
            val label = getString(R.string.follow_match, Ui.line(this, e))
            pick(teams, label, Follow.event(e, (e.endMs ?: e.startMs + EVENT_LENGTH_MS) + PillChooser.FINAL_WINDOW_MS), onFocus = null)
        }
    }

    private fun pick(column: LinearLayout, label: String, follow: Follow, onFocus: (() -> Unit)?): View {
        val row = Ui.inflate(column, R.layout.pick_row)
        row.findViewById<TextView>(R.id.label).text = label
        val tick = row.findViewById<TextView>(R.id.tick)
        tick.text = getString(if (repo.follows.isFollowed(follow)) R.string.tick_on else R.string.tick_off)
        row.setOnClickListener { tick.text = getString(if (repo.follows.toggle(follow)) R.string.tick_on else R.string.tick_off) }
        if (onFocus != null) row.setOnFocusChangeListener { _, has -> if (has) onFocus() }
        column.addView(row)
        return row
    }

    companion object {
        private const val HINT = 0x80FFFFFF.toInt()
        private const val HINT_SP = 13f
        private const val EVENT_LENGTH_MS = 4L * 3600_000L
    }
}
