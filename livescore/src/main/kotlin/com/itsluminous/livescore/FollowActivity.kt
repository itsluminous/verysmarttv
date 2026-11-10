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
 * on a sport fills the competitions, focus on a competition fills its teams and today's matches. Opened by
 * the launcher on a long press of the pill (ScoreContract.FOLLOW_ACTIVITY).
 *
 * Every row carries a [TriStateCheckView] (ADR-045): a leaf (a team, a match, "All cricket") is ticked or empty
 * and OK toggles it; a section (a sport, a competition with teams or matches) is ticked when it is followed
 * itself or every child is, barred when some of what is under it is, and OK on a section selects everything
 * under it unless it is fully ticked, when it clears everything ([SelectionState.selectsAllOnOk]).
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
    private val rows = HashMap<LinearLayout, MutableList<Row>>()

    /** A visible row: its box and how its state is read from the follow list. */
    private class Row(val box: TriStateCheckView, val state: (PickerSelection) -> SelectionState)

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
            val row = section(
                sports, Ui.sportName(this, s),
                state = { sel -> sel.sport(s, competitionsOf(s).map { c -> sel.competition(c, childrenOf(c)) }) },
                select = { sel -> sel.selectAll(follow) },
                clear = { sel -> sel.clearSport(s) },
                onFocus = { showCompetitions(s) },
            )
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
        clearColumn(competitions)
        leaf(competitions, getString(R.string.whole_sport, Ui.sportName(this, s)), Follow.sport(s, Ui.sportName(this, s)), onFocus = { showTeams(null) })
        val list = competitionsOf(s)
        for (c in list) {
            val follow = Follow.competition(c)
            section(
                competitions, c.name,
                state = { sel -> sel.competition(c, childrenOf(c)) },
                select = { sel -> sel.selectAll(follow) },
                clear = { sel -> sel.clearAll(follow, childrenOf(c)) },
                onFocus = { showTeams(c) },
            )
        }
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
                        clearColumn(competitions)
                        showCompetitions(s)
                    }
                }
            }
        }
    }

    private fun showTeams(c: Competition?) {
        if (c != null && competition == c && teams.childCount > 0) return
        competition = c
        clearColumn(teams)
        if (c == null) {
            hint(teams, R.string.no_teams)
            return
        }
        val events = eventsOf(c)
        for (t in teamsOf(c, events)) leaf(teams, t.name, Follow.team(t), onFocus = null)
        for (e in events) leaf(teams, getString(R.string.follow_match, Ui.line(this, e)), eventFollow(e), onFocus = null)
    }

    /** The team and match follows under [c]: what its box combines and what clearing it removes. */
    private fun childrenOf(c: Competition): List<Follow> {
        val events = eventsOf(c)
        return teamsOf(c, events).map { Follow.team(it) } + events.map { eventFollow(it) }
    }

    private fun eventFollow(e: Event): Follow = Follow.event(e, (e.endMs ?: e.startMs + EVENT_LENGTH_MS) + PillChooser.FINAL_WINDOW_MS)

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

    private fun clearColumn(column: LinearLayout) {
        column.removeAllViews()
        rows.remove(column)
    }

    /** A leaf row: ticked when its follow is in the list; OK toggles the follow. */
    private fun leaf(column: LinearLayout, label: String, follow: Follow, onFocus: (() -> Unit)?): View =
        row(column, label, state = { sel -> sel.leaf(follow) }, onFocus = onFocus) { repo.follows.toggle(follow) }

    /** A section row: its box from [state]; OK applies [select] unless the box is fully ticked, then [clear]. */
    private fun section(
        column: LinearLayout,
        label: String,
        state: (PickerSelection) -> SelectionState,
        select: (PickerSelection) -> PickerSelection.Change,
        clear: (PickerSelection) -> PickerSelection.Change,
        onFocus: (() -> Unit)?,
    ): View =
        row(column, label, state, onFocus) {
            val sel = selection()
            val change = if (SelectionState.selectsAllOnOk(state(sel))) select(sel) else clear(sel)
            repo.follows.replaceAll(change.remove, change.add)
        }

    private fun row(column: LinearLayout, label: String, state: (PickerSelection) -> SelectionState, onFocus: (() -> Unit)?, onOk: () -> Unit): View {
        val view = Ui.inflate(column, R.layout.pick_row)
        view.findViewById<TextView>(R.id.label).text = label
        val box = view.findViewById<TriStateCheckView>(R.id.tick)
        box.state = state(selection())
        view.setOnClickListener {
            onOk()
            refreshBoxes()
        }
        if (onFocus != null) view.setOnFocusChangeListener { _, has -> if (has) onFocus() }
        column.addView(view)
        rows.getOrPut(column) { ArrayList() }.add(Row(box, state))
        return view
    }

    private fun selection() = PickerSelection(repo.follows.all())

    /** Every visible box reads the follow list again: a child's OK changes its parents, a section's OK its children. */
    private fun refreshBoxes() {
        val sel = selection()
        for (list in rows.values) for (r in list) r.box.state = r.state(sel)
    }

    companion object {
        private const val HINT = 0x80FFFFFF.toInt()
        private const val HINT_SP = 13f
        private const val EVENT_LENGTH_MS = 4L * 3600_000L
    }
}
