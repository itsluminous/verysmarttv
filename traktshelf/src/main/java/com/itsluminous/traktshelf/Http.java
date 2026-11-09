package com.itsluminous.traktshelf;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Small HTTP client on HttpURLConnection: one JSON request, with headers, a timeout and one retry on 429 that
 * waits what the server asks. No library, no connection pool: the sync runs a few hundred requests in a
 * background thread and a TV with 1 GB has better uses for its memory.
 */
final class Http {
    private static final String TAG = "TraktShelf.Http";
    static final int CONNECT_MS = 10_000;
    static final int READ_MS = 20_000;

    /** A response: HTTP status and the body as text (empty for 204). */
    static final class Response {
        final int code;
        final String body;
        final Map<String, java.util.List<String>> headers;

        Response(int code, String body, Map<String, java.util.List<String>> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }

        boolean ok() {
            return code >= 200 && code < 300;
        }

        JSONObject json() throws JSONException {
            return new JSONObject(body);
        }

        String header(String name) {
            if (headers == null) return null;
            java.util.List<String> v = headers.get(name);
            if (v == null) v = headers.get(name.toLowerCase(java.util.Locale.ROOT));
            return v == null || v.isEmpty() ? null : v.get(0);
        }
    }

    private Http() {
    }

    static Response get(String url, Map<String, String> headers) throws IOException {
        return request("GET", url, headers, null);
    }

    static Response postJson(String url, Map<String, String> headers, JSONObject body) throws IOException {
        return request("POST", url, headers, body == null ? "{}" : body.toString());
    }

    static Response request(String method, String url, Map<String, String> headers, String body) throws IOException {
        Response r = once(method, url, headers, body);
        if (r.code == 429) {
            // Trakt: 1000 GET / 5 min; OMDb and TMDB return 429 too. Wait what Retry-After says, at most 12 s, once.
            long wait = 2000;
            String ra = r.header("Retry-After");
            if (ra != null) {
                try {
                    wait = Math.min(12_000, Long.parseLong(ra.trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                }
            }
            Log.w(TAG, "429 from " + host(url) + ", waiting " + wait + " ms");
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return r;
            }
            r = once(method, url, headers, body);
        }
        return r;
    }

    private static Response once(String method, String url, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "TraktShelf/0.1 (Android TV)");
            c.setRequestProperty("Accept", "application/json");
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in, 4 << 20);
            return new Response(code, text, c.getHeaderFields());
        } finally {
            c.disconnect();
        }
    }

    /** Downloads a file (an image) to bytes, at most max bytes; null on any failure. */
    static byte[] getBytes(String url, int max) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setRequestProperty("User-Agent", "TraktShelf/0.1 (Android TV)");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(64 << 10);
                byte[] buf = new byte[16 << 10];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) return null;
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            Log.w(TAG, "download failed " + host(url) + ": " + e.getMessage());
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 << 10);
        byte[] buf = new byte[8 << 10];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("response over " + max + " bytes");
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (IOException e) {
            return url;
        }
    }
}
