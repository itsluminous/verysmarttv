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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One hidden WebView per fetch (ADR-049): built on the main thread over the application context, never attached to a
 * window, loads BWF's Match Centre, and once the page is up (not Cloudflare's "Just a moment…" interstitial) runs
 * [SCRIPT], which uses the page's own `fetch` for `vue-current-live` and `vue-live-matches` and hands the raw JSON to
 * the [Bridge]. The view reaches only BWF's hosts and Cloudflare's challenge ([allowed]): every other request is answered
 * empty and every other navigation refused; file and content URLs are off. The calling thread (the repo's fetch
 * executor, never the main thread) waits up to [LOAD_TIMEOUT_MS]; whatever the outcome the WebView is then stopped,
 * stripped of the bridge, destroyed, its cache, cookies and web storage deleted, and Chromium is told the app is under
 * memory pressure so it drops what it cached. Nothing of the visit outlives the call but the strings in the [BwfCapture].
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

    /** Main thread: the WebView, configured and loading, or the exception its construction threw. */
    private fun start(pending: PendingCapture, onSettled: () -> Unit): WebView {
        val web = WebView(app)
        try {
            configure(web, pending, onSettled)
        } catch (e: RuntimeException) {
            web.destroy()
            throw e
        }
        return web
    }

    /** Main thread: the settings, the bridge and the client, then the page load; a throw here leaves the caller a view to destroy. */
    @SuppressLint("SetJavaScriptEnabled") // The page is a JavaScript app: without it there is nothing to read (ADR-049).
    private fun configure(web: WebView, pending: PendingCapture, onSettled: () -> Unit) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = USER_AGENT
        web.settings.blockNetworkImage = true
        web.settings.loadsImagesAutomatically = false
        // The page is the only thing this view may reach: no file:// or content:// (API 29 allows both by default), no
        // location, no new windows, and nothing off BWF's hosts (the client below refuses every other origin).
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.setGeolocationEnabled(false)
        web.settings.setSupportMultipleWindows(false)
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        web.addJavascriptInterface(Bridge(pending, onSettled), BRIDGE)
        web.webViewClient = Client(pending, onSettled)
        web.loadUrl(PAGE)
    }

    /** Main thread: everything the visit left, gone. */
    private fun tearDown(web: WebView) {
        try {
            web.stopLoading()
            web.removeJavascriptInterface(BRIDGE)
            web.webViewClient = WebViewClient()
            web.loadUrl("about:blank")
            web.clearCache(true)
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

        /** One tournament's matches could not be fetched: the capture goes on without it. */
        @JavascriptInterface fun skipped(id: String, reason: String) {
            Log.w(TAG, "tournament $id skipped: ${reason.take(REASON_LOG_MAX)}")
        }

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

        /** The main frame may only move within BWF's own hosts (and Cloudflare's challenge): anything else is refused, not loaded. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host
            if (allowed(host)) return false
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "refused navigation to $host")
            return true
        }

        /** Every request off BWF's hosts (fonts, analytics, ads, whatever the page pulls in) is answered empty on the device. */
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (allowed(request.url.host)) return null
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "blocked ${request.url.host}")
            return WebResourceResponse("text/plain", "utf-8", BLOCKED_STATUS, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageFinished(view: WebView, url: String) {
            // Chromium's own error page finishes too, after onReceivedError has already failed the capture: nothing to ask it.
            if (injected || pending.isSettled() || !url.startsWith(PAGE)) return
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
        const val RETRY_DELAY_MS = 600
        const val PAGE = "https://match-centre.bwfbadminton.com/"
        const val API = "https://extranet-lv.bwfbadminton.com/api/match-center/"
        private const val BRIDGE = "LiveScoreBridge"
        private const val BLOCKED_STATUS = 403
        private const val REASON_LOG_MAX = 120

        /** The hosts the hidden view may talk to: BWF's own, and the one Cloudflare serves its challenge script from. */
        private val HOSTS = listOf("bwfbadminton.com", "challenges.cloudflare.com")

        /** Whether a request to [host] is let through: [HOSTS] and their subdomains, nothing else (no host at all included). Pure. */
        fun allowed(host: String?): Boolean {
            val h = host?.lowercase()?.trimEnd('.') ?: return false
            return HOSTS.any { h == it || h.endsWith(".$it") }
        }

        /** An Android Chrome string without the WebView's `; wv` marker: the one the 2026-11-10 probe passed the challenge with. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * The page's own `fetch`, from its origin and with its cookies, for the tournament list and then, one tournament at a
         * time (at most [MAX_TOURNAMENTS]), its live matches. A tournament list that fails or is not JSON fails the whole
         * capture. A tournament's matches may fail the page's `fetch` although the response arrived (`Failed to fetch`, no
         * HTTP error: on 2026-11-11 the API's cached answer for some tournaments carried no CORS header, so the page's origin
         * could not read it); after [RETRY_DELAY_MS] the same call is made once more with a cache-busting `_=` parameter,
         * which fetched every tournament in every run tried. A tournament that still fails is skipped and named to the
         * bridge, and only every tournament failing fails the capture.
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
                var failed = [];
                function matches(id, fresh) { return get('vue-live-matches?tmtId=' + encodeURIComponent(id) + '&tmtType=0' + (fresh ? '&_=' + Date.now() : '')); }
                function later(ms) { return new Promise(function (resolve) { setTimeout(resolve, ms); }); }
                return ids.reduce(function (chain, id) {
                  return chain.then(function () {
                    return matches(id, false)
                      .catch(function () { return later($RETRY_DELAY_MS).then(function () { return matches(id, true); }); })
                      .then(function (m) { bridge.matches(id, m); },
                            function (e) { failed.push(id); bridge.skipped(id, String(e && e.message || e)); });
                  });
                }, Promise.resolve()).then(function () {
                  if (ids.length > 0 && failed.length === ids.length) { throw new Error('no tournament answered (' + failed.length + ' asked)'); }
                });
              }).then(function () { bridge.done(); })
                .catch(function (e) {
                  var msg = String(e && e.message || e);
                  bridge.fail(/failed to fetch/i.test(msg) ? 'the page could not reach the live-score API' : msg);
                });
            })();
        """.trimIndent()
    }
}
