package com.itsluminous.livescore.source.bwf

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One hidden WebView per fetch (ADR-049): built on the main thread over the application context, never attached to a
 * window, loads BWF's Match Centre, and once the page is up (not Cloudflare's "Just a moment…" interstitial) runs
 * [SCRIPT], which uses the page's own `fetch` for `vue-current-live` and `vue-live-matches` and hands the raw JSON to
 * the [Bridge]. The calling thread (the repo's fetch executor, never the main thread) waits up to [LOAD_TIMEOUT_MS];
 * whatever the outcome the WebView is then stopped, stripped of the bridge, destroyed, its cookies and web storage
 * deleted, and Chromium is told the app is under memory pressure so it drops what it cached. Nothing of the visit
 * outlives the call but the strings in the [BwfCapture].
 *
 * Every failure (the challenge not clearing, a main-frame error, a 4xx or 5xx from an API call, a JavaScript error, a
 * renderer gone, no WebView on the device, the timeout) becomes an [IOException] whose message is the short reason the
 * Scores app shows after "BWF site unavailable".
 */
class WebViewPageLoader(context: Context, private val handler: Handler = Handler(Looper.getMainLooper())) : BwfPageLoader {
    private val app = context.applicationContext

    @Throws(IOException::class)
    override fun load(): BwfCapture {
        if (Looper.myLooper() == Looper.getMainLooper()) throw IOException("the page loader must not run on the main thread")
        val pending = PendingCapture()
        val latch = CountDownLatch(1)
        val view = AtomicReference<WebView?>()
        val onSettled = { latch.countDown() }
        handler.post {
            try {
                view.set(start(pending, onSettled))
            } catch (e: RuntimeException) {
                // No WebView provider on the device, or it failed to start: the one place a WebView can throw at construction.
                pending.fail("no browser engine: ${e.message ?: e.javaClass.simpleName}")
                onSettled()
            }
        }
        latch.await(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        handler.post { view.getAndSet(null)?.let(::tearDown) }
        return pending.result(LOAD_TIMEOUT_MS)
    }

    /** Main thread: the WebView, its client and the bridge, then the page load. */
    @SuppressLint("SetJavaScriptEnabled") // The page is a JavaScript app: without it there is nothing to read (ADR-049).
    private fun start(pending: PendingCapture, onSettled: () -> Unit): WebView {
        val web = WebView(app)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = USER_AGENT
        web.settings.blockNetworkImage = true
        web.settings.loadsImagesAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        web.addJavascriptInterface(Bridge(pending, onSettled), BRIDGE)
        web.webViewClient = Client(pending, onSettled)
        web.loadUrl(PAGE)
        return web
    }

    /** Main thread: everything the visit left, gone. */
    private fun tearDown(web: WebView) {
        try {
            web.stopLoading()
            web.removeJavascriptInterface(BRIDGE)
            web.webViewClient = WebViewClient()
            web.loadUrl("about:blank")
            web.clearCache(false)
            web.destroy()
        } catch (e: RuntimeException) {
            Log.w(TAG, "WebView teardown: ${e.message}")
        }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        // Chromium registers a ComponentCallbacks2 on the application: this is how it is told to purge its caches now.
        (app as? ComponentCallbacks2)?.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
    }

    /** What the page's script calls. Runs on the WebView's bridge thread; `PendingCapture` is safe for that. */
    private class Bridge(private val pending: PendingCapture, private val onSettled: () -> Unit) {
        @JavascriptInterface fun tournaments(json: String) = pending.tournaments(json)

        @JavascriptInterface fun matches(id: String, json: String) = pending.matches(id, json)

        @JavascriptInterface fun done() {
            pending.done()
            onSettled()
        }

        @JavascriptInterface fun fail(reason: String) {
            pending.fail(reason)
            onSettled()
        }
    }

    private class Client(private val pending: PendingCapture, private val onSettled: () -> Unit) : WebViewClient() {
        private var injected = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "page started $url")
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (injected || !url.startsWith(PAGE)) return
            view.evaluateJavascript("document.title") { title ->
                when {
                    injected -> Unit
                    title.contains("just a moment", ignoreCase = true) || title.contains("attention required", ignoreCase = true) -> {
                        pending.challenged()
                        Log.i(TAG, "Cloudflare interstitial up, waiting for it to clear")
                    }
                    else -> {
                        injected = true
                        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "page finished, title $title, asking the page for its JSON")
                        view.evaluateJavascript(SCRIPT, null)
                    }
                }
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame) return
            val code = errorResponse.statusCode
            if (code == 403 || code == 503) {
                // Cloudflare's interstitial answers 403 or 503 and then reloads the real page itself: wait for it.
                pending.challenged()
                Log.i(TAG, "main frame HTTP $code, a challenge page most likely")
            } else {
                fail("HTTP $code for the page")
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) fail(error.description?.toString().orEmpty().ifEmpty { "page failed to load" })
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            fail(if (detail.didCrash()) "browser engine crashed" else "browser engine was killed for memory")
            return true
        }

        private fun fail(reason: String) {
            pending.fail(reason)
            onSettled()
        }
    }

    companion object {
        private const val TAG = "LiveScore.BwfWeb"
        const val LOAD_TIMEOUT_MS = 30_000L
        const val MAX_TOURNAMENTS = 4
        const val PAGE = "https://match-centre.bwfbadminton.com/"
        const val API = "https://extranet-lv.bwfbadminton.com/api/match-center/"
        private const val BRIDGE = "LiveScoreBridge"

        /** An Android Chrome string without the WebView's `; wv` marker: the one the 2026-11-10 probe passed the challenge with. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * The page's own `fetch`, from its origin and with its cookies, for the tournament list and then the live matches of
         * each tournament (at most [MAX_TOURNAMENTS]); a non-2xx answer or a list that is not JSON fails the whole capture.
         */
        val SCRIPT = """
            (function () {
              var api = '$API';
              var bridge = window.$BRIDGE;
              function get(path) {
                return fetch(api + path, { credentials: 'same-origin' }).then(function (r) {
                  if (!r.ok) { throw new Error('HTTP ' + r.status + ' for ' + path.split('?')[0]); }
                  return r.text();
                });
              }
              get('vue-current-live').then(function (text) {
                bridge.tournaments(text);
                var ids = [];
                try {
                  var list = JSON.parse(text).results || [];
                  for (var i = 0; i < list.length; i++) { if (list[i] && list[i].id != null) { ids.push(String(list[i].id)); } }
                } catch (e) { throw new Error('tournament list is not JSON'); }
                ids = ids.slice(0, $MAX_TOURNAMENTS);
                return Promise.all(ids.map(function (id) {
                  return get('vue-live-matches?tmtId=' + encodeURIComponent(id) + '&tmtType=0').then(function (m) { bridge.matches(id, m); });
                }));
              }).then(function () { bridge.done(); })
                .catch(function (e) {
                  var msg = String(e && e.message || e);
                  bridge.fail(/failed to fetch/i.test(msg) ? 'the page could not reach the live-score API' : msg);
                });
            })();
        """.trimIndent()
    }
}
