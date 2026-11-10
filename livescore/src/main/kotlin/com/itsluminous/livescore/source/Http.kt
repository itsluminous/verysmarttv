package com.itsluminous.livescore.source

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** How a source reaches the network: one function, so tests hand the parsers a fixture instead. */
fun interface HttpClient {
    /**
     * Opens [url] with the extra request [headers] (a keyed source's key, §13.1) and returns its body stream;
     * the caller closes it. Fails with [IOException] on anything but 200.
     */
    @Throws(IOException::class)
    fun stream(url: String, headers: Map<String, String>): InputStream
}

/**
 * `HttpURLConnection` with GZIP and the timeouts a TV on Wi-Fi needs (ADR-005). The body is returned as a
 * stream and never read into a `String`: the tennis scoreboard is 365 KB (§13.1).
 */
object Http : HttpClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    @Throws(IOException::class)
    override fun stream(url: String, headers: Map<String, String>): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VerySmartTV-livescore")
        for ((name, value) in headers) connection.setRequestProperty(name, value)
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return connection.inputStream
    }
}
