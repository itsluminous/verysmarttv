package com.itsluminous.livescore

import com.itsluminous.livescore.model.Competition
import com.itsluminous.livescore.model.Follow
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When enabling or holding a badminton follow needs the user's key (ADR-044; only under the SportMicro source, ADR-049), and how much of the key is ever shown. */
class BadmintonGateTest {
    private val cricket = Follow.sport(Sport.CRICKET, "Cricket")
    private val badminton = Follow.sport(Sport.BADMINTON, "Badminton")
    private val tournament = Follow.competition(Competition(Sport.BADMINTON, "7001", "Japan Masters"))
    private val player = Follow.team(Team(Sport.BADMINTON, "5001", "Sindhu P. V.", "SIN"))

    @Test
    fun aBadmintonFollowOfAnyKindNeedsAKeyWhileNoneIsStored() {
        assertTrue(BadmintonGate.needsKey(listOf(badminton), ""))
        assertTrue(BadmintonGate.needsKey(listOf(tournament), "   "))
        assertTrue(BadmintonGate.needsKey(listOf(cricket, player), ""))
    }

    @Test
    fun otherSportsAndAStoredKeyNeedNothing() {
        assertFalse(BadmintonGate.needsKey(listOf(cricket), ""))
        assertFalse(BadmintonGate.needsKey(emptyList(), ""))
        assertFalse(BadmintonGate.needsKey(listOf(badminton, player), "abcd1234"))
    }

    @Test
    fun theBwfWebsiteAndOffNeedNoKey() {
        assertFalse(BadmintonGate.needsKey(listOf(badminton), "", BadmintonSource.BWF_WEBVIEW))
        assertFalse(BadmintonGate.needsKey(listOf(tournament, player), "", BadmintonSource.NONE))
        assertTrue(BadmintonGate.needsKey(listOf(badminton), "", BadmintonSource.SPORTMICRO))
    }

    @Test
    fun onlyTheKeysLastFourCharactersAreEverShown() {
        assertEquals("1234", BadmintonGate.tail("  abcdef1234 "))
        assertEquals("ab", BadmintonGate.tail("ab"))
        assertEquals("", BadmintonGate.tail(""))
    }
}
