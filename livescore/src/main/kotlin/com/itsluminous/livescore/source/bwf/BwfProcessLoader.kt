package com.itsluminous.livescore.source.bwf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The [BwfPageLoader] the main process uses (ADR-050): binds [BwfCaptureService] in the `:bwf` process for one capture,
 * asks for [page], waits for the reply over the binder and unbinds, which ends that process. The calling thread (the
 * repo's fetch executor, never the main thread) blocks for at most [TIMEOUT_MS]: the page's own 30 s plus the process
 * start and the round trip. A timeout unbinds all the same, which kills a capture still running, and reads as
 * `timed out after 45 s`; a connection that dies reads as `browser process died`. The failure the service reports is
 * rethrown with its reason, so [BwfWebViewSource] shows the same words whichever process the page failed in.
 */
class BwfProcessLoader(context: Context, private val page: String = WebViewPageLoader.PAGE, private val handler: Handler = Handler(Looper.getMainLooper())) : BwfPageLoader {
    private val app = context.applicationContext

    @Throws(IOException::class)
    override fun load(): BwfCapture {
        if (Looper.myLooper() == Looper.getMainLooper()) throw IOException("the page loader must not run on the main thread")
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<CaptureMessages.Outcome?>()
        val settle = { o: CaptureMessages.Outcome ->
            if (outcome.compareAndSet(null, o)) latch.countDown()
        }
        val replies = Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                val decoded = CaptureMessages.decode(msg)
                if (decoded != null) settle(decoded)
                decoded != null
            },
        )
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val request: Message = CaptureMessages.request(page).also { it.replyTo = replies }
                try {
                    Messenger(service).send(request)
                } catch (e: RemoteException) {
                    settle(CaptureMessages.Outcome.Failure("browser process died: ${e.message}"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = settle(CaptureMessages.Outcome.Failure(DIED))

            override fun onBindingDied(name: ComponentName) = settle(CaptureMessages.Outcome.Failure(DIED))
        }
        val bound = AtomicReference(false)
        handler.post {
            bound.set(app.bindService(Intent(app, BwfCaptureService::class.java), connection, Context.BIND_AUTO_CREATE))
            if (!bound.get()) settle(CaptureMessages.Outcome.Failure("browser process could not be started"))
        }
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        handler.post { if (bound.get()) unbind(connection) }
        return when (val o = outcome.get()) {
            is CaptureMessages.Outcome.Success -> o.capture
            is CaptureMessages.Outcome.Failure -> throw IOException(o.reason)
            null -> throw IOException("timed out after ${TIMEOUT_MS / 1000} s")
        }
    }

    private fun unbind(connection: ServiceConnection) {
        try {
            app.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unbind: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveScore.BwfProc"
        const val TIMEOUT_MS = 45_000L
        private const val DIED = "browser process died"
    }
}
