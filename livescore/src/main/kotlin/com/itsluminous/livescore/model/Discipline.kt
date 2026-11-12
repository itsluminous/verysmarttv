package com.itsluminous.livescore.model

/**
 * A badminton event (ADR-051): what is played on court, as both feeds code it (`MS`, `WS`, `MD`, `WD`, `XD`). A country or
 * player follow carries a set of these as its filter; [token] is what `follows.json` and the cache store.
 */
enum class Discipline(val token: String) {
    MS("MS"),
    WS("WS"),
    MD("MD"),
    WD("WD"),
    XD("XD");

    companion object {
        /** The discipline a feed's code names, case-insensitive; null for anything else. */
        fun fromToken(token: String?): Discipline? = token?.trim()?.uppercase()?.let { t -> entries.firstOrNull { it.token == t } }

        /** Every discipline: what a follow filtered on "All" holds. */
        val ALL: Set<Discipline> = entries.toSet()
    }
}

/**
 * How the user picks disciplines: All, or any of the three groups (ADR-051). A group is its [members]; a follow's set is the
 * union of the groups ticked, and reads back as the groups it completes ([groupsOf]).
 */
enum class DisciplineGroup(val members: Set<Discipline>) {
    SINGLES(setOf(Discipline.MS, Discipline.WS)),
    DOUBLES(setOf(Discipline.MD, Discipline.WD)),
    MIXED(setOf(Discipline.XD));

    companion object {
        /** The groups [disciplines] holds in full, in order; a set that is empty or [Discipline.ALL] holds every group. */
        fun groupsOf(disciplines: Set<Discipline>): List<DisciplineGroup> {
            val set = disciplines.ifEmpty { Discipline.ALL }
            return entries.filter { set.containsAll(it.members) }
        }

        /** The set the ticked [groups] stand for; empty when none is ticked, which the picker reads as "remove the follow". */
        fun union(groups: Collection<DisciplineGroup>): Set<Discipline> = groups.flatMapTo(LinkedHashSet()) { it.members }

        /** Whether [disciplines] means every discipline: the set that reads as "All". */
        fun isAll(disciplines: Set<Discipline>): Boolean = disciplines.isEmpty() || disciplines.containsAll(Discipline.ALL)
    }
}

/** One player of a badminton side (ADR-051): the feed's id, the display name, and the nationality code (`IND`; empty when the feed has none). */
data class Player(val id: String, val name: String, val country: String = "")
