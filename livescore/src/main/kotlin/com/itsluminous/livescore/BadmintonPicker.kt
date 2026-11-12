package com.itsluminous.livescore

import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Player
import com.itsluminous.livescore.model.Sport

/**
 * The picker's Countries and Players lists over the cached badminton matches (ADR-051). Pure; `BadmintonPickerTest`.
 *
 * Countries: every nationality on a cached match and every code of [BadmintonNations], named by [name], A–Z by name and
 * grouped under the name's first letter. Players: every player of every cached badminton match, once each, with the
 * disciplines the cache shows them in, grouped by country (the players' own order within a country is by name); a
 * player whose feed gives no nationality (SportMicro, where a side is one id) goes under the empty code, which the
 * picker names "no nationality in this feed".
 */
object BadmintonPicker {
    /** One country row: the feed's code and the name shown. */
    data class Country(val code: String, val name: String)

    /** A letter and the countries whose names start with it. */
    data class Letter(val letter: String, val countries: List<Country>)

    /** One player row: the player and the disciplines the cache shows them in. */
    data class Entry(val player: Player, val disciplines: List<Discipline>)

    /** The players of one country; [code] is empty for the feed-without-nationality group. */
    data class Group(val code: String, val players: List<Entry>)

    /** The countries to offer, by letter: the static list plus what the cache shows, named by [name], A–Z. */
    fun countries(events: List<Event>, name: (String) -> String): List<Letter> {
        val codes = LinkedHashSet(BadmintonNations.CODES)
        badminton(events).flatMap { it.players }.map { it.country }.filter { it.isNotEmpty() }.forEach { codes.add(it.uppercase()) }
        val rows = codes.map { Country(it, name(it)) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return rows.groupBy { it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "" }.map { (letter, list) -> Letter(letter, list) }
    }

    /** The players on the cached matches, each once, by country ([name] orders the groups), then by name; the no-country group last. */
    fun players(events: List<Event>, name: (String) -> String): List<Group> {
        val played = LinkedHashMap<String, Pair<Player, LinkedHashSet<Discipline>>>()
        for (e in badminton(events)) {
            for (p in e.players) {
                if (p.id.isEmpty()) continue
                val entry = played.getOrPut(p.id) { p to LinkedHashSet() }
                e.discipline?.let { entry.second.add(it) }
            }
        }
        val entries = played.values.map { (p, d) -> Entry(p, d.sortedBy { it.ordinal }) }
        return entries.groupBy { it.player.country.uppercase() }
            .map { (code, list) -> Group(code, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.player.name })) }
            .sortedWith(compareBy<Group> { if (it.code.isEmpty()) 1 else 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { name(it.code) })
    }

    private fun badminton(events: List<Event>) = events.filter { it.sport == Sport.BADMINTON }
}
