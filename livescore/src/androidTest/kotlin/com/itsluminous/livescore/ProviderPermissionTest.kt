package com.itsluminous.livescore

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itsluminous.verysmarttv.core.contract.ScoreContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A caller without the signature permission is refused (§12.3). The shell user (`content query` over
 * UiAutomation) is not signed with the app's key, so it stands in for a foreign app; this test APK, which
 * shares the debug key, reads the same URI without trouble.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class ProviderPermissionTest {
    @Test
    fun shellIsRefusedWhileTheSignedTestAppReads() {
        val out = shell("content query --uri ${ScoreContract.PILL}")
        assertTrue(out, out.contains("SecurityException") || out.contains("Permission Denial"))
        val restore = shell("content call --uri ${ScoreContract.PILL} --method ${ScoreContract.METHOD_RESTORE} --extra ${ScoreContract.EXTRA_JSON}:s:x")
        assertTrue(restore, restore.contains("SecurityException") || restore.contains("Permission Denial"))
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        // One row per event the pill can show, or the one none row (ADR-041): never empty, never refused.
        resolver.query(ScoreContract.PILL, null, null, null, null)!!.use { assertTrue("rows: ${it.count}", it.count >= 1) }
        assertEquals(ScoreContract.TYPE_PILL, resolver.getType(ScoreContract.PILL))
    }

    /** Runs [command] as the shell user; `content` prints its refusal on stderr, so both streams are read. */
    private fun shell(command: String): String {
        val fds = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRwe(command)
        fds[1].close()
        val out = ParcelFileDescriptor.AutoCloseInputStream(fds[0]).bufferedReader().use { it.readText() }
        val err = ParcelFileDescriptor.AutoCloseInputStream(fds[2]).bufferedReader().use { it.readText() }
        return out + err
    }
}
