package com.itsluminous.livescore

import com.itsluminous.livescore.model.Sport
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every sport the companion publishes is a value the contract enumerates (§12.2), so the launcher has an icon for it.
 * Robolectric: the contract's URIs are built on load.
 */
@RunWith(RobolectricTestRunner::class)
class SportTest {
    @Test
    fun everyTokenIsInTheContractsValueSet() {
        val contract = ScoreContract.SPORTS.toSet()
        for (sport in Sport.entries) assertTrue(sport.token, sport.token in contract)
    }

    @Test
    fun fromTokenRoundTripsAndRefusesTheRest() {
        for (sport in Sport.entries) assertEquals(sport, Sport.fromToken(sport.token))
        assertNull(Sport.fromToken(ScoreContract.SPORT_HOCKEY))
        assertNull(Sport.fromToken(null))
    }
}
