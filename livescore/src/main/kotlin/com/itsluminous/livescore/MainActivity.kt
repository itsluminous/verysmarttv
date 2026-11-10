package com.itsluminous.livescore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.itsluminous.livescore.model.Event
import com.itsluminous.livescore.model.Sport
import com.itsluminous.livescore.source.bwf.BwfWebViewSource
import com.itsluminous.verysmarttv.core.contract.ScoreContract

/**
 * Home (§13.6): today's followed events as rows, live first, with Follow and Settings pills. An empty
 * list leads to Follow. Badminton follows with no key stored ([BadmintonGate], ADR-044) put a "needs a data key"
 * row above them that opens [BadmintonKeyDialog]; the experimental BWF website source failing (ADR-049) puts a
 * "BWF site unavailable" row with the reason, which opens Settings. Also the module card's activity and the target of
 * [ScoreContract.ACTION_OPEN] without an event id; with one, it forwards to [ScoreboardActivity].
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: ScoreRepo
    private lateinit var rows: LinearLayout
    private lateinit var empty: View
    private lateinit var notice: View
    private lateinit var status: TextView
    private val onChange: () -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() {
            repo.onRead()
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (forwardOpen(intent)) return
        setContentView(R.layout.activity_main)
        repo = ScoreRepo.get(this)
        rows = findViewById(R.id.rows)
        empty = findViewById(R.id.empty)
        notice = findViewById(R.id.notice)
        status = findViewById(R.id.status)
        findViewById<View>(R.id.followPill).setOnClickListener { startActivity(Intent(this, FollowActivity::class.java)) }
        findViewById<View>(R.id.settingsPill).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        if (System.currentTimeMillis() - repo.lastFetchMs() > RefreshPolicy.MATCH_DAY_MS) repo.refreshAll(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardOpen(intent)
    }

    /** `ACTION_OPEN` with an event id goes to the scoreboard; returns whether this activity is finishing. */
    private fun forwardOpen(intent: Intent?): Boolean {
        val id = intent?.getStringExtra(ScoreContract.EXTRA_EVENT_ID)
        if (intent?.action != ScoreContract.ACTION_OPEN || id.isNullOrEmpty()) return false
        startActivity(Intent(this, ScoreboardActivity::class.java).putExtra(ScoreContract.EXTRA_EVENT_ID, id))
        if (!::repo.isInitialized) {
            finish()
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (!::repo.isInitialized) return
        repo.addListener(onChange)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        if (!::repo.isInitialized) return
        repo.removeListener(onChange)
        handler.removeCallbacks(tick)
    }

    internal fun render() {
        val now = System.currentTimeMillis()
        val followed = repo.followed(now)
        val focusedId = rows.focusedChild?.tag as? String
        rows.removeAllViews()
        for (f in followed) {
            val row = Ui.inflate(rows, R.layout.event_row)
            val e = f.event
            row.tag = e.id
            Ui.kicker(row.findViewById(R.id.kicker), repo.formatter.kicker(e, now))
            row.findViewById<TextView>(R.id.meta).text = meta(e)
            row.findViewById<TextView>(R.id.line).text = Ui.line(this, e)
            row.findViewById<TextView>(R.id.summary).text = e.summary.ifEmpty { e.statusDetail }
            row.setOnClickListener { startActivity(Intent(this, ScoreboardActivity::class.java).putExtra(ScoreContract.EXTRA_EVENT_ID, e.id)) }
            rows.addView(row)
            if (e.id == focusedId) row.requestFocus()
        }
        val hasRows = followed.isNotEmpty()
        renderNotice()
        empty.visibility = if (hasRows) View.GONE else View.VISIBLE
        findViewById<View>(R.id.scroll).visibility = if (hasRows) View.VISIBLE else View.GONE
        status.text = Ui.updated(this, repo, null, now)
        if (!hasRows) findViewById<View>(R.id.followPill).requestFocus() else if (rows.focusedChild == null && currentFocus == null) rows.getChildAt(0).requestFocus()
    }

    /** The one notice row: the key (SportMicro, no key stored) or the BWF website's last failure; GONE otherwise. */
    private fun renderNotice() {
        val title = findViewById<TextView>(R.id.noticeTitle)
        val hint = findViewById<TextView>(R.id.noticeHint)
        val bwfError = if (repo.prefs.badmintonSource == BadmintonSource.BWF_WEBVIEW) repo.lastError(FetchKey(Sport.BADMINTON, null)) else null
        when {
            BadmintonGate.needsKey(repo.follows.all(), repo.prefs.badmintonKey, repo.prefs.badmintonSource) -> {
                title.setText(R.string.badminton_needs_key_row)
                hint.setText(R.string.badminton_needs_key_row_hint)
                notice.setOnClickListener {
                    BadmintonKeyDialog.show(this, repo.prefs, allowRemove = false, onSaved = {
                        render()
                        repo.refreshAll(null)
                    })
                }
                notice.visibility = View.VISIBLE
            }
            bwfError != null && repo.follows.all().any { it.sport == Sport.BADMINTON } -> {
                title.setText(R.string.bwf_unavailable_row)
                hint.text = getString(R.string.bwf_unavailable_row_hint, bwfError.removePrefix(BwfWebViewSource.UNAVAILABLE).trimStart(':', ' '))
                notice.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
                notice.visibility = View.VISIBLE
            }
            else -> notice.visibility = View.GONE
        }
    }

    private fun meta(e: Event): String {
        val sport = Ui.sportName(this, e.sport)
        val comp = listOf(e.competition, e.format, e.session).filter { it.isNotEmpty() && it != sport }.distinct().joinToString(" · ")
        return if (comp.isEmpty()) sport else getString(R.string.row_meta, sport, comp)
    }

    companion object {
        private const val TICK_MS = 60_000L
    }
}
