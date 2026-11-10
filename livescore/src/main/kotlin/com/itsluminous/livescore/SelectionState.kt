package com.itsluminous.livescore

/**
 * What a picker row's box shows (ADR-045): nothing under it followed, some of it, or all of it. A leaf (a team,
 * a match, "All cricket") is [NONE] or [ALL]; a section (a sport, a competition with teams or matches) combines
 * its children with [ofChildren]. Pure; `SelectionStateTest` is the table.
 */
enum class SelectionState {
    NONE,
    PARTIAL,
    ALL;

    companion object {
        /** [selected] of [total] children: an empty box, a bar, or a tick. No children at all is an empty box. */
        fun of(selected: Int, total: Int): SelectionState =
            when {
                total <= 0 || selected <= 0 -> NONE
                selected >= total -> ALL
                else -> PARTIAL
            }

        /**
         * A section's box: a tick when the section itself is followed (everything under it is covered), else from
         * its children: a tick when every child is ticked, a bar when any child is ticked or barred, else empty.
         */
        fun ofChildren(selfFollowed: Boolean, children: List<SelectionState>): SelectionState {
            if (selfFollowed) return ALL
            val state = of(children.count { it == ALL }, children.size)
            return if (state == NONE && children.any { it == PARTIAL }) PARTIAL else state
        }

        /** The OK rule on a section: an empty or barred box selects everything under it; a ticked one clears everything. */
        fun selectsAllOnOk(state: SelectionState): Boolean = state != ALL
    }
}
