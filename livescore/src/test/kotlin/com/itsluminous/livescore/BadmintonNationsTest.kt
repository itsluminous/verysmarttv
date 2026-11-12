package com.itsluminous.livescore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The static nations and how a BWF code becomes a name (ADR-051). */
class BadmintonNationsTest {
    @Test
    fun theIocCodesTheFeedWritesAreNamedThroughTheirIsoCountry() {
        assertEquals("Indonesia", BadmintonNations.name("INA", Locale.ENGLISH))
        assertEquals("Malaysia", BadmintonNations.name("MAS", Locale.ENGLISH))
        assertEquals("Denmark", BadmintonNations.name("DEN", Locale.ENGLISH))
        assertEquals("Germany", BadmintonNations.name("GER", Locale.ENGLISH))
        assertEquals("Allemagne", BadmintonNations.name("GER", Locale.FRENCH))
        assertEquals("India", BadmintonNations.name("ind", Locale.ENGLISH))
    }

    @Test
    fun aCodeOutsideTheListIsNamedWhenThePlatformKnowsItAndNullOtherwise() {
        assertEquals("Japan", BadmintonNations.name("JPN", Locale.ENGLISH))
        assertEquals("South Korea", BadmintonNations.name("KOR", Locale.ENGLISH))
        assertNull("not a country code", BadmintonNations.name("ZZZ", Locale.ENGLISH))
        assertNull(BadmintonNations.name("", Locale.ENGLISH))
        assertNull("the picker names the non-ISO members itself", BadmintonNations.name("TPE", Locale.ENGLISH))
        assertNull(BadmintonNations.name("ENG", Locale.ENGLISH))
    }

    @Test
    fun theListHoldsTheWorldTourNationsOnceEach() {
        assertTrue(BadmintonNations.CODES.size >= 40)
        assertEquals(BadmintonNations.CODES.size, BadmintonNations.CODES.toSet().size)
        assertTrue(BadmintonNations.CODES.containsAll(listOf("CHN", "INA", "JPN", "DEN", "MAS", "KOR", "THA", "IND", "TPE", "ENG")))
        for (code in BadmintonNations.ISO.keys) assertTrue(code, BadmintonNations.name(code, Locale.ENGLISH) != null)
    }
}
