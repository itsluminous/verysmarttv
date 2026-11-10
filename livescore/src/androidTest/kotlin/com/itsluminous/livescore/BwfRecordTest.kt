package com.itsluminous.livescore

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.livescore.source.bwf.BwfWebViewSource
import com.itsluminous.livescore.source.bwf.WebViewPageLoader
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * Records BWF's Match Centre JSON through the real hidden WebView (ADR-049), for `tools/dev/record_bwf_fixtures.sh`:
 * runs only with `-e bwf_record true` (it needs the internet and a site that answers), so the ordinary connected suite
 * skips it. Writes `current-live.json` and `live-matches-<tmtId>.json` under the app's `files/bwf-record/`, which the
 * script pulls and trims, and logs how the visit went under `LiveScore.BwfRecord`, challenge included.
 */
@RunWith(AndroidJUnit4::class)
class BwfRecordTest {
    @Test
    fun recordTheMatchCentre() {
        assumeTrue("pass -e bwf_record true to record", InstrumentationRegistry.getArguments().getString("bwf_record") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dir = File(app.filesDir, "bwf-record").apply { deleteRecursively(); mkdirs() }
        Thread.sleep(SETTLE_MS) // a quiet moment for a memory reading before the WebView exists
        val started = System.currentTimeMillis()
        try {
            val capture = WebViewPageLoader(app).load()
            File(dir, "current-live.json").writeText(capture.currentLive)
            for ((id, json) in capture.liveMatches) File(dir, "live-matches-$id.json").writeText(json)
            val events = BwfWebViewSource.parse(capture, System.currentTimeMillis())
            Log.i(TAG, "captured in ${System.currentTimeMillis() - started} ms: ${capture.liveMatches.size} tournament(s), ${events.size} match(es): ${events.joinToString { "${it.state} ${it.home.shortName} ${it.home.score} v ${it.away.shortName} ${it.away.score} (${it.statusDetail})" }}")
        } catch (e: IOException) {
            Log.w(TAG, "capture failed after ${System.currentTimeMillis() - started} ms: ${e.message}")
            File(dir, "failed.txt").writeText(e.message.orEmpty())
        }
        Thread.sleep(SETTLE_MS) // the WebView is torn down on the main thread meanwhile; a memory reading after
    }

    private companion object {
        const val TAG = "LiveScore.BwfRecord"
        const val SETTLE_MS = 8_000L
    }
}
