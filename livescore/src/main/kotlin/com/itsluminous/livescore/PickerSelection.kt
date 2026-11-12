package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Sport

/**
 * The picker's boxes over one follow list (ADR-045): a leaf is ticked when its follow is in the list; a
 * competition is ticked when its own follow is, barred when some of its teams or matches are; a sport the same
 * over its competitions, and barred when anything of that sport is followed that no listed competition holds (a
 * team of a league the catalogue does not list). [selectAll] and [clearAll] are the OK rule's two moves as the
 * follows to add and to remove. Pure; `PickerSelectionTest`.
 */
class PickerSelection(private val follows: List<Follow>) {
    fun has(follow: Follow): Boolean = follows.any { it.key == follow.key }

    fun leaf(follow: Follow): SelectionState = if (has(follow)) SelectionState.ALL else SelectionState.NONE

    /** A competition's box from its own follow and its [children] (team and match follows). */
    fun competition(c: Competition, children: List<Follow>): SelectionState = section(Follow.competition(c), children)

    /** A section's box from the follow that covers it ([own]: a competition's, or the sport's for the badminton Countries and Players sections, ADR-051) and its [children]. */
    fun section(own: Follow, children: List<Follow>): SelectionState = SelectionState.ofChildren(has(own), children.map(::leaf))

    /** The follow in the list with [follow]'s key (the same follow, perhaps with another discipline set), or null. */
    fun find(follow: Follow): Follow? = follows.firstOrNull { it.key == follow.key }

    /** A sport's box from its own follow and its competitions' boxes; barred when something of the sport is followed outside them. */
    fun sport(s: Sport, competitions: List<SelectionState>): SelectionState {
        val state = SelectionState.ofChildren(has(Follow.sport(s, "")), competitions)
        return if (state == SelectionState.NONE && follows.any { it.sport == s }) SelectionState.PARTIAL else state
    }

    /** OK on a section that is not fully ticked: the section's own follow is added; the children stay as they are. */
    fun selectAll(section: Follow): Change = Change(remove = emptyList(), add = listOf(section))

    /** OK on a fully ticked competition: its follow and every child follow go. */
    fun clearAll(section: Follow, children: List<Follow>): Change =
        Change(remove = (listOf(section) + children).filter(::has), add = emptyList())

    /** OK on a fully ticked sport: every follow of that sport goes, whatever its kind. */
    fun clearSport(s: Sport): Change = Change(remove = follows.filter { it.sport == s }, add = emptyList())

    /** The follows a move removes and adds, applied together by `FollowStore.replaceAll`. */
    data class Change(val remove: List<Follow>, val add: List<Follow>)
}
