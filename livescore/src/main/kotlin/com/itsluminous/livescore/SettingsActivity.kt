package com.itsluminous.livescore

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.itsluminous.livescore.model.Sport

/** Refresh now, Live refresh, Sports shown, Badminton data key, Restore default follows, Clear follows (§13.6, ADR-044). */
class SettingsActivity : Activity() {
    private lateinit var repo: ScoreRepo
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        repo = ScoreRepo.get(this)
        list = findViewById(R.id.list)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        list.removeAllViews()
        val refresh = row(getString(R.string.refresh_now), Ui.updated(this, repo, null, System.currentTimeMillis())) { value ->
            value.text = getString(R.string.refreshing)
            repo.refreshAll { value.text = Ui.updated(this, repo, null, System.currentTimeMillis()) }
        }
        refresh.requestFocus()
        row(getString(R.string.live_refresh), liveLabel()) { value ->
            repo.prefs.liveRefresh = !repo.prefs.liveRefresh
            value.text = liveLabel()
        }
        row(getString(R.string.sports_shown), sportsLabel()) { value -> pickSports(value) }
        row(getString(R.string.badminton_key), keyLabel()) { value -> editKey(value) }
        row(getString(R.string.restore_defaults), getString(R.string.restore_defaults_value)) { _ ->
            repo.follows.import(FollowStore.encode(FollowStore.defaults()))
            build()
        }
        row(getString(R.string.clear_follows), resources.getQuantityString(R.plurals.clear_follows_value, repo.follows.all().size, repo.follows.all().size)) { _ ->
            AlertDialog.Builder(this)
                .setMessage(R.string.clear_follows_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    repo.follows.clear()
                    build()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun liveLabel(): String = getString(if (repo.prefs.liveRefresh) R.string.live_refresh_on else R.string.live_refresh_off)

    private fun sportsLabel(): String = Sport.entries.filter { it in repo.prefs.sportsShown }.joinToString(", ") { Ui.sportName(this, it) }

    private fun pickSports(value: TextView) {
        val all = Sport.entries
        val checked = BooleanArray(all.size) { all[it] in repo.prefs.sportsShown }
        AlertDialog.Builder(this)
            .setTitle(R.string.sports_shown)
            .setMultiChoiceItems(all.map { Ui.sportName(this, it) }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.ok) { _, _ ->
                val chosen = all.filterIndexed { i, _ -> checked[i] }.toSet()
                if (chosen.isNotEmpty()) repo.prefs.sportsShown = chosen
                value.text = sportsLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Never the key itself: "Not set", or its last four characters so the user can tell which key is in. */
    private fun keyLabel(): String {
        val key = repo.prefs.badmintonKey
        return if (key.isEmpty()) getString(R.string.badminton_key_unset) else getString(R.string.badminton_key_set, BadmintonGate.tail(key))
    }

    /** The key dialog (BadmintonKeyDialog): OK saves it and fetches, Remove clears it (the follows stay), Cancel leaves it. */
    private fun editKey(value: TextView) {
        BadmintonKeyDialog.show(
            this, repo.prefs, allowRemove = true,
            onSaved = {
                value.text = keyLabel()
                repo.refreshAll(null)
            },
            onRemoved = { value.text = keyLabel() },
        )
    }

    private fun row(name: String, value: String, onClick: (TextView) -> Unit): View {
        val row = Ui.inflate(list, R.layout.settings_row)
        row.findViewById<TextView>(R.id.rowName).text = name
        val valueView = row.findViewById<TextView>(R.id.rowValue)
        valueView.text = value
        row.setOnClickListener { onClick(valueView) }
        list.addView(row)
        return row
    }
}
