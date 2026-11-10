package com.itsluminous.livescore

import com.itsluminous.livescore.SelectionState.ALL
import com.itsluminous.livescore.SelectionState.NONE
import com.itsluminous.livescore.SelectionState.PARTIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The three states of a picker box (ADR-045): the count rule, the section rule, the OK rule, and the drawn view's description. */
@RunWith(RobolectricTestRunner::class)
class SelectionStateTest {
    @Test
    fun ofCountsSelectedAgainstTotal() {
        assertEquals(NONE, SelectionState.of(0, 0))
        assertEquals(NONE, SelectionState.of(0, 3))
        assertEquals(NONE, SelectionState.of(2, 0))
        assertEquals(PARTIAL, SelectionState.of(1, 3))
        assertEquals(PARTIAL, SelectionState.of(2, 3))
        assertEquals(ALL, SelectionState.of(3, 3))
        assertEquals(ALL, SelectionState.of(4, 3))
        assertEquals(NONE, SelectionState.of(-1, 3))
    }

    @Test
    fun ofChildrenTicksAFollowedSectionAndBarsAPartlyFollowedOne() {
        assertEquals(ALL, SelectionState.ofChildren(true, emptyList()))
        assertEquals(ALL, SelectionState.ofChildren(true, listOf(NONE, NONE)))
        assertEquals(NONE, SelectionState.ofChildren(false, emptyList()))
        assertEquals(NONE, SelectionState.ofChildren(false, listOf(NONE, NONE)))
        assertEquals(PARTIAL, SelectionState.ofChildren(false, listOf(ALL, NONE)))
        assertEquals(PARTIAL, SelectionState.ofChildren(false, listOf(PARTIAL, NONE)))
        assertEquals(PARTIAL, SelectionState.ofChildren(false, listOf(ALL, PARTIAL)))
        assertEquals(ALL, SelectionState.ofChildren(false, listOf(ALL, ALL)))
    }

    @Test
    fun okSelectsAllUnlessTheBoxIsFullyTicked() {
        assertTrue(SelectionState.selectsAllOnOk(NONE))
        assertTrue(SelectionState.selectsAllOnOk(PARTIAL))
        assertFalse(SelectionState.selectsAllOnOk(ALL))
    }

    @Test
    fun theViewDescribesItsState() {
        val view = TriStateCheckView(RuntimeEnvironment.getApplication())
        assertEquals(NONE, view.state)
        assertEquals("Not followed", view.contentDescription.toString())
        view.state = PARTIAL
        assertEquals("Partly followed", view.contentDescription.toString())
        view.state = ALL
        assertEquals("Followed", view.contentDescription.toString())
        assertEquals(R.string.check_partial, TriStateCheckView.description(PARTIAL))
    }
}
