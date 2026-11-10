package com.itsluminous.livescore

import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Sport

/**
 * When badminton needs the user's RapidAPI key (ADR-044): the moment a badminton follow is enabled (the sport, a
 * tournament, a player or a match) with no key stored, the picker asks for the key before the follow lands; and
 * while badminton follows are in place with no key (the user removed it in Settings), Home and the picker say the
 * rows need a key and the source fetches nothing. Pure; `BadmintonGateTest` is the table.
 */
object BadmintonGate {
    /** Whether [follows] (those being added, or those in place) include badminton while [key] is blank. */
    fun needsKey(follows: Collection<Follow>, key: String): Boolean = key.isBlank() && follows.any { it.sport == Sport.BADMINTON }

    /** The key as the Settings row and the dialog show it: never the key, only its last [KEY_TAIL] characters. */
    fun tail(key: String): String = key.trim().takeLast(KEY_TAIL)

    const val KEY_TAIL = 4
}
