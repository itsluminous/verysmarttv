package com.itsluminous.livescore

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.livescore.source.bwf.BwfCaptureService
import com.itsluminous.livescore.source.bwf.BwfProcessLoader
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The `:bwf` process end to end (ADR-050): the real client binds the real service, which starts the process, runs the
 * WebView against a page on an unreachable host under the allowlist, answers the failure over the binder, and the
 * process is gone within [EXIT_WAIT_MS] of the reply. No internet needed: the host does not resolve. The main process
 * never creates a WebView, so this test is also the proof that the companion's own process never holds one.
 */
@RunWith(AndroidJUnit4::class)
class BwfCaptureServiceTest {
    @Test
    fun aCaptureFailsOverTheBinderAndTheProcessExits() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val loader = BwfProcessLoader(app, page = UNREACHABLE_PAGE)
        val executor = Executors.newSingleThreadExecutor()
        val started = System.currentTimeMillis()
        val message = executor.submit<String> {
            try {
                loader.load()
                fail("a page on a host that does not resolve cannot be captured")
                ""
            } catch (e: IOException) {
                e.message.orEmpty()
            }
        }.get()
        executor.shutdown()
        val took = System.currentTimeMillis() - started
        assertTrue(
            "the failure must be the page's, not the client's timeout or a bind that never started the process: $message after $took ms",
            message.isNotEmpty() && !message.startsWith("timed out after 45") && !message.startsWith("browser process"),
        )
        assertTrue("the :bwf process stayed more than ${EXIT_WAIT_MS / 1000} s after the reply ($message)", awaitExit(app))
    }

    /** Polls the app's own process list until the `:bwf` process is gone, for up to [EXIT_WAIT_MS]. */
    private fun awaitExit(app: Context): Boolean {
        val deadline = System.currentTimeMillis() + EXIT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (bwfProcesses(app).isEmpty()) return true
            Thread.sleep(POLL_MS)
        }
        return false
    }

    /** The names of this app's running processes that are the `:bwf` one; `getRunningAppProcesses` lists the caller's own. */
    private fun bwfProcesses(app: Context): List<String> {
        val am = app.getSystemService(ActivityManager::class.java)
        return am.runningAppProcesses.orEmpty().map { it.processName }.filter { it.endsWith(BwfCaptureService.PROCESS_SUFFIX) }
    }

    private companion object {
        /** Under the loader's allowlist (so the request is not answered empty on the device) and a name that does not resolve. */
        const val UNREACHABLE_PAGE = "https://no-such-host-for-the-test.bwfbadminton.com/"
        const val EXIT_WAIT_MS = 10_000L
        const val POLL_MS = 200L
    }
}
