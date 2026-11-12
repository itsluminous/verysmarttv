package com.itsluminous.livescore

import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.DisciplineGroup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five disciplines, the three groups the user ticks (ADR-051), and the chooser's All-and-groups box logic. */
class DisciplineTest {
    @Test
    fun codesReadCaseInsensitivelyAndGroupsCoverTheFive() {
        assertEquals(Discipline.XD, Discipline.fromToken("xd"))
        assertEquals(Discipline.MS, Discipline.fromToken(" MS "))
        assertNull(Discipline.fromToken("Masters"))
        assertNull(Discipline.fromToken(null))
        assertEquals(Discipline.ALL, DisciplineGroup.union(DisciplineGroup.entries))
        assertEquals(setOf(Discipline.MS, Discipline.WS, Discipline.XD), DisciplineGroup.union(listOf(DisciplineGroup.SINGLES, DisciplineGroup.MIXED)))
        assertTrue(DisciplineGroup.union(emptyList()).isEmpty())
    }

    @Test
    fun groupsOfReadsBackWholeGroupsOnlyAndEmptyMeansAll() {
        assertEquals(DisciplineGroup.entries, DisciplineGroup.groupsOf(Discipline.ALL))
        assertEquals(DisciplineGroup.entries, DisciplineGroup.groupsOf(emptySet()))
        assertEquals(listOf(DisciplineGroup.SINGLES), DisciplineGroup.groupsOf(setOf(Discipline.MS, Discipline.WS)))
        assertEquals(emptyList<DisciplineGroup>(), DisciplineGroup.groupsOf(setOf(Discipline.MS)))
        assertTrue(DisciplineGroup.isAll(Discipline.ALL))
        assertTrue(DisciplineGroup.isAll(emptySet()))
        assertFalse(DisciplineGroup.isAll(setOf(Discipline.XD)))
    }

    @Test
    fun theChooserTicksAllWithTheThreeAndTheThreeWithAll() {
        assertArrayEquals(booleanArrayOf(true, true, true, true), DisciplineDialog.initial(Discipline.ALL))
        assertArrayEquals(booleanArrayOf(false, true, false, false), DisciplineDialog.initial(setOf(Discipline.MS, Discipline.WS)))
        val none = booleanArrayOf(false, false, false, false)
        assertArrayEquals(booleanArrayOf(true, true, true, true), DisciplineDialog.toggle(none, 0, true))
        assertArrayEquals(booleanArrayOf(false, false, false, false), DisciplineDialog.toggle(booleanArrayOf(true, true, true, true), 0, false))
        assertArrayEquals(booleanArrayOf(false, true, true, false), DisciplineDialog.toggle(booleanArrayOf(true, true, true, true), 3, false))
        assertArrayEquals(booleanArrayOf(true, true, true, true), DisciplineDialog.toggle(booleanArrayOf(false, true, true, false), 3, true))
        assertEquals(setOf(Discipline.MD, Discipline.WD, Discipline.XD), DisciplineDialog.chosen(booleanArrayOf(false, false, true, true)))
        assertTrue(DisciplineDialog.chosen(none).isEmpty())
    }
}
