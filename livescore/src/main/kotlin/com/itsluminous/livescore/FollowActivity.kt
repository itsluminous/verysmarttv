package com.itsluminous.livescore

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Discipline
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
 * found in the cached events instead, fetching the sport once when the cache holds none of it. Under badminton two
 * more sections follow the tournaments (ADR-051): **Countries** (the nations of [BadmintonNations] and of the cached
 * matches, A–Z under letter headers; BWF source only, since SportMicro names no nationality) and **Players** (every
 * player on the cached matches, grouped by country). OK on a country or a player opens [DisciplineDialog] and the row
 * then shows the filter chosen. Enabling any badminton follow with no key stored opens [BadmintonKeyDialog] first
 * ([BadmintonGate]): OK saves the key and lets the follow through, Cancel leaves badminton unfollowed.
 */
class FollowActivity : Activity() {
    private lateinit var repo: ScoreRepo
    private lateinit var sports: LinearLayout
    private lateinit var competitions: LinearLayout
    private lateinit var teams: LinearLayout
    private var sport: Sport? = null
    private var pane: Pane? = null
    private var fetching: Sport? = null
    private val rows = HashMap<LinearLayout, MutableList<Row>>()

    /** A visible row: its box and label, and how both are read from the follow list. */
    private class Row(val box: TriStateCheckView, val label: TextView, val state: (PickerSelection) -> SelectionState, val text: (PickerSelection) -> String)

    /** What the third column shows: a competition's teams and matches, the badminton countries, or the badminton players. */
    private sealed class Pane {
        data class Teams(val competition: Competition) : Pane()

        object Countries : Pane()

        object Players : Pane()
    }

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
        pane = null
        clearColumn(competitions)
        val whole = Follow.sport(s, Ui.sportName(this, s))
        leaf(competitions, getString(R.string.whole_sport, Ui.sportName(this, s)), whole, onFocus = { showPane(null) })
        val list = competitionsOf(s)
        for (c in list) {
            val follow = Follow.competition(c)
            section(
                competitions, c.name,
                state = { sel -> sel.competition(c, childrenOf(c)) },
                select = { sel -> sel.selectAll(follow) },
                clear = { sel -> sel.clearAll(follow, childrenOf(c)) },
                onFocus = { showPane(Pane.Teams(c)) },
            )
        }
        if (list.isEmpty() && repo.source.competitions(s).isEmpty()) dynamicHint(s)
        if (s == Sport.BADMINTON) {
            // The two cross-tournament sections (ADR-051): covered by "All Badminton", barred when some country or player is followed.
            section(
                competitions, getString(R.string.section_countries),
                state = { sel -> sel.section(whole, countryFollows()) },
                select = { sel -> sel.selectAll(whole) },
                clear = { sel -> sel.clearAll(whole, countryFollows()) },
                onFocus = { showPane(Pane.Countries) },
            )
            section(
                competitions, getString(R.string.section_players),
                state = { sel -> sel.section(whole, playerFollows()) },
                select = { sel -> sel.selectAll(whole) },
                clear = { sel -> sel.clearAll(whole, playerFollows()) },
                onFocus = { showPane(Pane.Players) },
            )
        }
        showPane(null)
    }

    /** The static catalogue, or for a sport without one the tournaments in the cache, soonest first. */
    private fun competitionsOf(s: Sport): List<Competition> {
        val static = repo.source.competitions(s)
        if (static.isNotEmpty()) return static
        return repo.cache.events().filter { it.sport == s }.sortedBy { it.startMs }.distinctBy { it.competitionId }
            .map { Competition(s, it.competitionId, it.competition) }
    }

    /** Why the column is empty for a catalogue-less sport: no key, the BWF website failing, a fetch under way, or a feed with nothing this week. */
    private fun dynamicHint(s: Sport) {
        val needsKey = s == Sport.BADMINTON && BadmintonGate.needsKey(listOf(Follow.sport(s, "")), repo.prefs.badmintonKey, repo.prefs.badmintonSource)
        val bwfError = if (s == Sport.BADMINTON && repo.prefs.badmintonSource == BadmintonSource.BWF_WEBVIEW) repo.lastError(FetchKey(s, null)) else null
        when {
            needsKey -> hint(competitions, R.string.badminton_needs_key)
            fetching == s && bwfError != null -> hint(competitions, R.string.bwf_unavailable_column)
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

    private fun showPane(p: Pane?) {
        if (p != null && pane == p && teams.childCount > 0) return
        pane = p
        clearColumn(teams)
        when (p) {
            null -> hint(teams, R.string.no_teams)
            is Pane.Teams -> showTeams(p.competition)
            Pane.Countries -> showCountries()
            Pane.Players -> showPlayers()
        }
    }

    private fun showTeams(c: Competition) {
        val events = eventsOf(c)
        for (t in teamsOf(c, events)) leaf(teams, t.name, Follow.team(t), onFocus = null)
        for (e in events) leaf(teams, getString(R.string.follow_match, Ui.line(this, e)), eventFollow(e), onFocus = null)
    }

    /** The countries (ADR-051): the BWF source names nationalities; SportMicro does not, so under it the pane says so and lists nothing. */
    private fun showCountries() {
        if (repo.prefs.badmintonSource != BadmintonSource.BWF_WEBVIEW) {
            hint(teams, R.string.countries_need_bwf)
            return
        }
        for (letter in BadmintonPicker.countries(repo.cache.events()) { Ui.countryName(this, it) }) {
            header(teams, letter.letter)
            for (c in letter.countries) filterRow(teams, getString(R.string.row_meta, c.code, c.name), Follow.country(c.code, c.name, Discipline.ALL))
        }
    }

    /** The players on the cached matches, by country (ADR-051), each with the disciplines the cache shows them in. */
    private fun showPlayers() {
        val groups = BadmintonPicker.players(repo.cache.events()) { Ui.countryName(this, it) }
        if (groups.isEmpty()) {
            hint(teams, R.string.no_players_cached)
            return
        }
        for (g in groups) {
            header(teams, Ui.countryName(this, g.code))
            for (e in g.players) {
                val played = e.disciplines.joinToString("/") { it.token }
                val label = if (played.isEmpty()) e.player.name else getString(R.string.player_row, e.player.name, played)
                filterRow(teams, label, Follow.player(e.player, Discipline.ALL))
            }
        }
    }

    /** Every country the Countries pane can list, as follows (any discipline set: the key ignores it), for the section's box and its clear-all. */
    private fun countryFollows(): List<Follow> =
        BadmintonPicker.countries(repo.cache.events()) { Ui.countryName(this, it) }.flatMap { it.countries }.map { Follow.country(it.code, it.name, Discipline.ALL) }

    private fun playerFollows(): List<Follow> =
        BadmintonPicker.players(repo.cache.events()) { Ui.countryName(this, it) }.flatMap { it.players }.map { Follow.player(it.player, Discipline.ALL) }

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

    private fun hint(column: LinearLayout, text: Int) = note(column, getString(text), HINT_SP)

    /** A letter or a country over the rows under it: not focusable, the hint's size (11sp read too small on the 1080p review). */
    private fun header(column: LinearLayout, text: String) = note(column, text, HEADER_SP)

    private fun note(column: LinearLayout, text: String, sizeSp: Float) {
        val view = TextView(this)
        view.text = text
        view.setTextColor(HINT)
        view.textSize = sizeSp
        column.addView(view)
    }

    /** Empties a column and scrolls it back to its top, so a pane that replaces a long one starts at its first row. */
    private fun clearColumn(column: LinearLayout) {
        column.removeAllViews()
        rows.remove(column)
        (column.parent as? ScrollView)?.scrollTo(0, 0)
    }

    /** A leaf row: ticked when its follow is in the list; OK toggles the follow. */
    private fun leaf(column: LinearLayout, label: String, follow: Follow, onFocus: (() -> Unit)?): View =
        change(column, { label }, state = { sel -> sel.leaf(follow) }, onFocus = onFocus) { sel ->
            if (sel.leaf(follow) == SelectionState.ALL) PickerSelection.Change(remove = listOf(follow), add = emptyList())
            else PickerSelection.Change(remove = emptyList(), add = listOf(follow))
        }

    /**
     * A country or player row (ADR-051): ticked when a follow with its key is in the list, its label ending in the filter
     * chosen; OK opens the discipline chooser over the current set (All when new), and OK there replaces the follow with
     * the set, or removes it when nothing is ticked.
     */
    private fun filterRow(column: LinearLayout, base: String, follow: Follow): View =
        row(
            column,
            text = { sel -> sel.find(follow)?.let { getString(R.string.row_meta, base, Ui.disciplineFilter(this, it.disciplines)) } ?: base },
            state = { sel -> sel.leaf(follow) },
            onFocus = null,
        ) {
            val current = selection().find(follow)
            DisciplineDialog.show(this, getString(R.string.discipline_title, follow.label), current?.disciplines ?: Discipline.ALL) { chosen ->
                apply(PickerSelection.Change(remove = listOfNotNull(current), add = if (chosen.isEmpty()) emptyList() else listOf(follow.copy(disciplines = chosen))))
            }
        }

    /** A section row: its box from [state]; OK applies [select] unless the box is fully ticked, then [clear]. */
    private fun section(
        column: LinearLayout,
        label: String,
        state: (PickerSelection) -> SelectionState,
        select: (PickerSelection) -> PickerSelection.Change,
        clear: (PickerSelection) -> PickerSelection.Change,
        onFocus: (() -> Unit)?,
    ): View =
        change(column, { label }, state, onFocus) { sel -> if (SelectionState.selectsAllOnOk(state(sel))) select(sel) else clear(sel) }

    /** A row whose OK is the change [onOk] reads off the current follows; [apply] gates it on the badminton key. */
    private fun change(
        column: LinearLayout,
        text: (PickerSelection) -> String,
        state: (PickerSelection) -> SelectionState,
        onFocus: (() -> Unit)?,
        onOk: (PickerSelection) -> PickerSelection.Change,
    ): View = row(column, text, state, onFocus) { apply(onOk(selection())) }

    /** A row: its label and box read off the current follows (and again after every change, [refreshBoxes]); OK runs [onClick]. */
    private fun row(
        column: LinearLayout,
        text: (PickerSelection) -> String,
        state: (PickerSelection) -> SelectionState,
        onFocus: (() -> Unit)?,
        onClick: () -> Unit,
    ): View {
        val view = Ui.inflate(column, R.layout.pick_row)
        val label = view.findViewById<TextView>(R.id.label)
        label.text = text(selection())
        val box = view.findViewById<TriStateCheckView>(R.id.tick)
        box.state = state(selection())
        view.setOnClickListener { onClick() }
        if (onFocus != null) view.setOnFocusChangeListener { _, has -> if (has) onFocus() }
        column.addView(view)
        rows.getOrPut(column) { ArrayList() }.add(Row(box, label, state, text))
        return view
    }

    private fun selection() = PickerSelection(repo.follows.all())

    /**
     * One change to the follow list, saved once. A change that enables badminton with no key stored asks for the
     * key first ([BadmintonGate.needsKey]): OK saves it and the change lands, and a badminton column that said
     * "needs a key" is listed again; Cancel leaves the list, and the boxes, as they were.
     */
    private fun apply(change: PickerSelection.Change) {
        if (BadmintonGate.needsKey(change.add, repo.prefs.badmintonKey, repo.prefs.badmintonSource)) {
            BadmintonKeyDialog.show(
                this, repo.prefs, allowRemove = false,
                onSaved = {
                    repo.follows.replaceAll(change.remove, change.add)
                    refreshBoxes()
                    if (sport == Sport.BADMINTON) {
                        clearColumn(competitions)
                        showCompetitions(Sport.BADMINTON)
                    }
                },
            )
            return
        }
        repo.follows.replaceAll(change.remove, change.add)
        refreshBoxes()
    }

    /** Every visible box and label reads the follow list again: a child's OK changes its parents, a section's OK its children, a chooser the row's filter. */
    private fun refreshBoxes() {
        val sel = selection()
        for (list in rows.values) {
            for (r in list) {
                r.box.state = r.state(sel)
                r.label.text = r.text(sel)
            }
        }
    }

    companion object {
        private const val HINT = 0x80FFFFFF.toInt()
        private const val HINT_SP = 13f
        private const val HEADER_SP = 13f
        private const val EVENT_LENGTH_MS = 4L * 3600_000L
    }
}
