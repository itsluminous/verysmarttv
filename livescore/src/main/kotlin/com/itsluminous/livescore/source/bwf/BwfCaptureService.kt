package com.itsluminous.livescore.source.bwf

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import java.io.IOException

/**
 * The one component of the `:bwf` process (ADR-050): a bound, private service that runs [WebViewPageLoader] for one
 * capture and answers over a [Messenger]. The main process ([BwfProcessLoader]) binds it, sends one
 * [CaptureMessages.MSG_CAPTURE], reads the reply and unbinds; the service then stops itself and, once destroyed, kills
 * its own process, so the Chromium pages, the WebView provider's mapped code and everything else the visit cost are
 * returned to the TV. Nothing else starts it: no intent filter, no job, no receiver.
 *
 * The loader must not run on a main thread, so each capture runs on a thread of its own; a capture still running when
 * the client unbinds (its 45 s timeout) dies with the process. The WebView data directory suffix this process needs is
 * set by `LiveScoreApplication` before the service exists.
 */
class BwfCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper()) { msg -> onMessage(msg) }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    /** The client is gone: stop, which destroys the service, which ends the process. */
    override fun onUnbind(intent: Intent?): Boolean {
        stopSelf()
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "capture process exiting")
        handler.post { Process.killProcess(Process.myPid()) }
    }

    private fun onMessage(msg: Message): Boolean {
        val page = CaptureMessages.pageOf(msg) ?: return false
        val reply = msg.replyTo ?: return false
        Thread({ capture(page, reply) }, "LiveScore.bwf-capture").start()
        return true
    }

    private fun capture(page: String, reply: Messenger) {
        val answer = try {
            CaptureMessages.result(WebViewPageLoader(applicationContext, page = page).load())
        } catch (e: IOException) {
            CaptureMessages.failed(e.message ?: e.javaClass.simpleName)
        }
        try {
            reply.send(answer)
        } catch (e: RemoteException) {
            Log.w(TAG, "the main process is gone before the reply: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveScore.BwfProc"

        /** The process this service is declared in (the manifest's `android:process`), as the `Application` sees it: `<package>:bwf`. */
        const val PROCESS_SUFFIX = ":bwf"

        /** The WebView data directory suffix the `:bwf` process uses, so it never opens the main process's. */
        const val DATA_DIRECTORY_SUFFIX = "bwf"
    }
}
