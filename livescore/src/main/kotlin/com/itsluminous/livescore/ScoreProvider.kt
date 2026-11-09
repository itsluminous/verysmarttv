package com.itsluminous.livescore

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.itsluminous.verysmarttv.core.contract.ScoreContract

/**
 * What the launcher's score pill reads (§12.2), speaking [ScoreContract]:
 *
 * - `/pill`: one row, computed from the cache by [PillChooser] and [LineFormatter]; `state=none` when
 *   nothing followed is in the window. Never blocks on a fetch; the query is the signal that a screen is
 *   looking, and [ScoreRepo.onRead] decides whether that starts one (§13.5).
 * - `/events`, `/events/<id>`: the followed events in the window, live first then by start.
 * - `/backup`: the follow list as its JSON; `call("restore", json)` replaces it (ADR-032).
 *
 * Reads are guarded by the `signature` permission `READ_SCORES`, the call by `WRITE_SCORES`, both
 * declared in the manifest (§12.3). `insert`, `update` and `delete` do nothing.
 */
class ScoreProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        // The launcher queries this on every Home: when the jobs are re-registered after a reboot.
        context?.let { ScoreRepo.get(it).scheduleJobs() }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val repo = ScoreRepo.get(context ?: return null)
        val cursor = when (MATCHER.match(uri)) {
            M_PILL -> pill(repo)
            M_EVENTS -> events(repo, null)
            M_EVENT -> events(repo, uri.lastPathSegment)
            M_BACKUP -> MatrixCursor(ScoreContract.BACKUP_COLUMNS).also { it.addRow(arrayOf<Any?>(repo.follows.export())) }
            else -> return null
        }
        cursor.setNotificationUri(context!!.contentResolver, if (MATCHER.match(uri) == M_PILL) ScoreContract.PILL else ScoreContract.EVENTS)
        if (MATCHER.match(uri) != M_BACKUP) repo.onRead()
        return cursor
    }

    private fun pill(repo: ScoreRepo): MatrixCursor {
        val cursor = MatrixCursor(ScoreContract.PILL_COLUMNS)
        val now = repo.now()
        val chosen = repo.pill(now)
        if (chosen == null) {
            cursor.addRow(arrayOf<Any?>(ScoreContract.STATE_NONE, "", "", null, "", "", 0L, 0, LineFormatter.COLOR_GREY))
            return cursor
        }
        val e = chosen.event
        val (kicker, color) = repo.formatter.kicker(e, now)
        cursor.addRow(
            arrayOf<Any?>(
                e.state.token, kicker, repo.formatter.line(e), e.id, e.sport.token, e.competition, e.updatedMs,
                if (repo.isStale(e, now)) 1 else 0, color,
            ),
        )
        return cursor
    }

    private fun events(repo: ScoreRepo, onlyId: String?): MatrixCursor {
        val cursor = MatrixCursor(ScoreContract.EVENT_COLUMNS)
        var id = 0L
        for (f in repo.followed()) {
            val e = f.event
            if (onlyId != null && e.id != onlyId) continue
            cursor.addRow(
                arrayOf<Any?>(
                    id++, e.id, e.sport.token, e.competition, e.format, e.state.token, e.statusDetail, e.startMs, e.endMs,
                    e.home.name, e.home.abbr, e.home.score, e.away.name, e.away.abbr, e.away.score,
                    e.summary, f.reason.token, e.updatedMs,
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String? =
        when (MATCHER.match(uri)) {
            M_PILL -> ScoreContract.TYPE_PILL
            M_EVENTS -> ScoreContract.TYPE_EVENTS
            M_EVENT -> ScoreContract.TYPE_EVENT
            M_BACKUP -> ScoreContract.TYPE_BACKUP
            else -> null
        }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = Bundle()
        if (method != ScoreContract.METHOD_RESTORE) {
            return result.fail("unknown method $method")
        }
        val json = extras?.getString(ScoreContract.EXTRA_JSON) ?: return result.fail("missing ${ScoreContract.EXTRA_JSON}")
        val repo = ScoreRepo.get(context ?: return result.fail("no context"))
        if (!repo.follows.import(json)) return result.fail("not a follow list")
        if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "follows restored: ${repo.follows.all().size}")
        result.putBoolean(ScoreContract.RESULT_OK, true)
        return result
    }

    private fun Bundle.fail(error: String): Bundle {
        putBoolean(ScoreContract.RESULT_OK, false)
        putString(ScoreContract.RESULT_ERROR, error)
        return this
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val TAG = "LiveScore.Provider"
        private const val M_PILL = 1
        private const val M_EVENTS = 2
        private const val M_EVENT = 3
        private const val M_BACKUP = 4
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(ScoreContract.AUTHORITY, ScoreContract.PATH_PILL, M_PILL)
            addURI(ScoreContract.AUTHORITY, ScoreContract.PATH_EVENTS, M_EVENTS)
            addURI(ScoreContract.AUTHORITY, ScoreContract.PATH_EVENTS + "/*", M_EVENT)
            addURI(ScoreContract.AUTHORITY, ScoreContract.PATH_BACKUP, M_BACKUP)
        }
    }
}
