package com.itsluminous.livescore

import android.app.Activity
import android.app.AlertDialog
import com.itsluminous.livescore.model.Discipline
import com.itsluminous.livescore.model.DisciplineGroup

/**
 * The discipline chooser a country or player follow opens (ADR-051): a multi-choice list *All*, *Singles*, *Doubles*,
 * *Mixed doubles*. *All* ticks or clears the three groups, and the three tick *All* when they are complete ([toggle],
 * pure). OK hands the set the ticked groups stand for to [onChosen] (empty when nothing is ticked: the caller removes the
 * follow); Cancel does nothing.
 */
object DisciplineDialog {
    private const val ALL = 0
    private val GROUPS = DisciplineGroup.entries

    fun show(activity: Activity, title: String, current: Set<Discipline>, onChosen: (Set<Discipline>) -> Unit) {
        val checked = initial(current)
        val items = (listOf(activity.getString(R.string.discipline_all)) + GROUPS.map { Ui.groupName(activity, it) }).toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMultiChoiceItems(items, checked) { dialog, which, isChecked ->
                val next = toggle(checked, which, isChecked)
                val list = (dialog as AlertDialog).listView
                for (i in next.indices) {
                    if (checked[i] != next[i]) list.setItemChecked(i, next[i])
                    checked[i] = next[i]
                }
            }
            .setPositiveButton(R.string.ok) { _, _ -> onChosen(chosen(checked)) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** The boxes for [current]: each group ticked when the set holds it in full, All when every group is. */
    fun initial(current: Set<Discipline>): BooleanArray {
        val groups = DisciplineGroup.groupsOf(current)
        return BooleanArray(GROUPS.size + 1) { i -> if (i == ALL) groups.size == GROUPS.size else GROUPS[i - 1] in groups }
    }

    /** The boxes after [which] is set to [isChecked]: All moves the three, the three move All. */
    fun toggle(checked: BooleanArray, which: Int, isChecked: Boolean): BooleanArray {
        val next = checked.copyOf()
        next[which] = isChecked
        if (which == ALL) {
            for (i in 1 until next.size) next[i] = isChecked
        } else {
            next[ALL] = (1 until next.size).all { next[it] }
        }
        return next
    }

    /** The disciplines the ticked groups stand for; empty when none is. */
    fun chosen(checked: BooleanArray): Set<Discipline> = DisciplineGroup.union(GROUPS.filterIndexed { i, _ -> checked[i + 1] })
}
