package com.itsluminous.livescore

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team

/**
 * The follow picker (§13.6, ADR-034): sport → competition → team columns, each a vertical list; focus
 * on a sport fills the competitions, focus on a competition fills its teams and today's matches; OK
 * toggles the tick. Opened by the launcher on a long press of the pill (ScoreContract.FOLLOW_ACTIVITY).
 *
 * A sport whose source has no static catalogue (badminton, ADR-044) lists the tournaments and players
 * found in the cached events instead, fetching the sport once when the cache holds none of it.
 */
class FollowActivity : Activity() {
    private lateinit var repo: ScoreRepo
    private lateinit var sports: LinearLayout
    private lateinit var competitions: LinearLayout
    private lateinit var teams: LinearLayout
    private var sport: Sport? = null
    private var competition: Competition? = null
    private var fetching: Sport? = null

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
        val list = competitionsOf(s)
        for (c in list) pick(competitions, c.name, Follow.competition(c), onFocus = { showTeams(c) })
        if (list.isEmpty() && repo.source.competitions(s).isEmpty()) dynamicHint(s)
        showTeams(null)
    }

    /** The static catalogue, or for a sport without one the tournaments in the cache, soonest first. */
    private fun competitionsOf(s: Sport): List<Competition> {
        val static = repo.source.competitions(s)
        if (static.isNotEmpty()) return static
        return repo.cache.events().filter { it.sport == s }.sortedBy { it.startMs }.distinctBy { it.competitionId }
            .map { Competition(s, it.competitionId, it.competition) }
    }

    /** Why the column is empty for a catalogue-less sport: no key, a fetch under way, or a feed with nothing this week. */
    private fun dynamicHint(s: Sport) {
        val needsKey = s == Sport.BADMINTON && repo.prefs.badmintonKey.isEmpty()
        when {
            needsKey -> hint(competitions, R.string.badminton_needs_key)
            fetching == s -> hint(competitions, R.string.badminton_none)
            else -> {
                hint(competitions, R.string.badminton_loading)
                fetching = s
                repo.refreshSport(s) {
                    if (sport == s && !isFinishing) {
                        competitions.removeAllViews()
                        showCompetitions(s)
                    }
                }
            }
        }
    }

    private fun showTeams(c: Competition?) {
        if (c != null && competition == c && teams.childCount > 0) return
        competition = c
        teams.removeAllViews()
        if (c == null) {
            hint(teams, R.string.no_teams)
            return
        }
        val events = eventsOf(c)
        for (t in teamsOf(c, events)) pick(teams, t.name, Follow.team(t), onFocus = null)
        for (e in events) {
            val label = getString(R.string.follow_match, Ui.line(this, e))
            pick(teams, label, Follow.event(e, (e.endMs ?: e.startMs + EVENT_LENGTH_MS) + PillChooser.FINAL_WINDOW_MS), onFocus = null)
        }
    }

    /** The cached events of [c] in the window, as the "Match · …" rows. */
    private fun eventsOf(c: Competition): List<Event> {
        val window = repo.window(System.currentTimeMillis())
        return repo.cache.events()
            .filter { it.competitionId == c.id || c.id == Competition.INTERNATIONAL && it.international && it.sport == c.sport }
            .filter { it.startMs in window || (it.endMs ?: it.startMs) in window }
    }

    /** The catalogue's teams, or for a sport without a catalogue the players of the competition's cached events. */
    private fun teamsOf(c: Competition, events: List<Event>): List<Team> {
        val static = repo.source.teams(c)
        if (static.isNotEmpty() || repo.source.competitions(c.sport).isNotEmpty()) return static
        return events.flatMap { listOf(it.home, it.away) }.filter { it.id.isNotEmpty() }.distinctBy { it.id }.sortedBy { it.name }
            .map { Team(c.sport, it.id, it.name, it.abbr) }
    }

    private fun hint(column: LinearLayout, text: Int) {
        val view = TextView(this)
        view.text = getString(text)
        view.setTextColor(HINT)
        view.textSize = HINT_SP
        column.addView(view)
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
