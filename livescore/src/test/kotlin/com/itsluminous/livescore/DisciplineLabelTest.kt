package com.itsluminous.livescore

import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.DisciplineGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The filter as a picker row reads it (ADR-051), through the string resources: "All", one group's name, or the groups
 * separated by a comma and a space (the separator is a quoted resource, since Android trims an unquoted one to the comma).
 */
@RunWith(RobolectricTestRunner::class)
class DisciplineLabelTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun theFilterReadsAllOneGroupOrTheGroupsCommaSeparated() {
        assertEquals("All", Ui.disciplineFilter(app, Discipline.ALL))
        assertEquals("All", Ui.disciplineFilter(app, emptySet()))
        assertEquals("Singles", Ui.disciplineFilter(app, DisciplineGroup.SINGLES.members))
        assertEquals("Doubles, Mixed doubles", Ui.disciplineFilter(app, setOf(Discipline.MD, Discipline.WD, Discipline.XD)))
        assertEquals("Singles, Mixed doubles", Ui.disciplineFilter(app, setOf(Discipline.MS, Discipline.WS, Discipline.XD)))
    }
}
