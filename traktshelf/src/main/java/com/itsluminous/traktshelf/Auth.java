package com.itsluminous.traktshelf;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The Trakt account: device-code sign-in, the stored tokens and their refresh.
 *
 * Trakt's development apps created since October 2026 get no client secret ("this app signs users in with
 * PKCE"). The device flow (POST /oauth/device/code, then /oauth/device/token polled at the given interval) was
 * checked against api.trakt.tv with this app's client_id alone: the code call answers 200, the poll answers
 * 400 "pending" rather than 401/403, which is the documented "waiting for the user" answer. Harbor's maintained
 * fork does the same ("device sign-in and no client secret", docs/SUNSET.md). So the TV shows the code and
 * the user approves on auth.trakt.tv/activate; no browser, no laptop, no secret anywhere.
 */
final class Auth {
    private static final String TAG = "TraktShelf.Auth";
    private static final String PREFS = "auth";
    static final String API = "https://api.trakt.tv";

    /** The device code Trakt handed out: what to show the user and how to poll. */
    static final class DeviceCode {
        final String deviceCode, userCode, verificationUrl;
        final int expiresIn, interval;

        DeviceCode(String deviceCode, String userCode, String verificationUrl, int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUrl = verificationUrl;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    /** Poll outcome. */
    enum Poll {PENDING, AUTHORIZED, DENIED, EXPIRED, ALREADY_USED, SLOW_DOWN, ERROR}

    private final SharedPreferences prefs;

    Auth(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean configured() {
        return !BuildConfig.TRAKT_CLIENT_ID.isEmpty();
    }

    boolean signedIn() {
        return prefs.getString("access_token", null) != null;
    }

    String accessToken() {
        return prefs.getString("access_token", null);
    }

    String username() {
        return prefs.getString("username", null);
    }

    void setUsername(String name) {
        prefs.edit().putString("username", name).apply();
    }

    long expiresAt() {
        return prefs.getLong("expires_at", 0);
    }

    void signOut() {
        String token = accessToken();
        prefs.edit().clear().apply();
        if (token == null) return;
        // Best effort: tell Trakt the token is gone. Needs no secret either.
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject().put("token", token).put("client_id", BuildConfig.TRAKT_CLIENT_ID);
                Http.postJson(API + "/oauth/revoke", null, body);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "revoke failed: " + e.getMessage());
            }
        }, "trakt-revoke").start();
    }

    DeviceCode requestDeviceCode() throws IOException, JSONException {
        JSONObject body = new JSONObject().put("client_id", BuildConfig.TRAKT_CLIENT_ID);
        Http.Response r = Http.postJson(API + "/oauth/device/code", null, body);
        if (!r.ok()) throw new IOException("device/code answered " + r.code);
        JSONObject j = r.json();
        return new DeviceCode(j.getString("device_code"), j.getString("user_code"),
                j.optString("verification_url", "https://trakt.tv/activate"), j.optInt("expires_in", 600), Math.max(5, j.optInt("interval", 5)));
    }

    Poll pollDeviceToken(DeviceCode code) {
        try {
            JSONObject body = new JSONObject().put("code", code.deviceCode).put("client_id", BuildConfig.TRAKT_CLIENT_ID);
            Http.Response r = Http.request("POST", API + "/oauth/device/token", null, body.toString());
            switch (r.code) {
                case 200:
                    store(r.json());
                    return Poll.AUTHORIZED;
                case 400:
                    return Poll.PENDING;
                case 404:
                    return Poll.ERROR;      // invalid device_code
                case 409:
                    return Poll.ALREADY_USED;
                case 410:
                    return Poll.EXPIRED;
                case 418:
                    return Poll.DENIED;
                case 429:
                    return Poll.SLOW_DOWN;
                default:
                    Log.w(TAG, "device/token answered " + r.code + ": " + r.body);
                    return Poll.ERROR;
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "device/token: " + e.getMessage());
            return Poll.ERROR;
        }
    }

    private void store(JSONObject token) throws JSONException {
        long expiresIn = token.optLong("expires_in", 7 * 86400);
        prefs.edit()
                .putString("access_token", token.getString("access_token"))
                .putString("refresh_token", token.optString("refresh_token", null))
                .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000L)
                .apply();
    }

    /**
     * Refreshes the access token when it is within a day of expiry (Trakt tokens last 24 h or 7 days). With
     * client_id only, like the device flow. A refresh Trakt refuses (401) signs the account out, so the UI asks again
     * instead of failing every sync.
     */
    boolean refreshIfNeeded() {
        if (!signedIn()) return false;
        if (System.currentTimeMillis() < expiresAt() - 86_400_000L) return true;
        String refresh = prefs.getString("refresh_token", null);
        if (refresh == null) return true;
        try {
            JSONObject body = new JSONObject()
                    .put("refresh_token", refresh)
                    .put("client_id", BuildConfig.TRAKT_CLIENT_ID)
                    .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
                    .put("grant_type", "refresh_token");
            Http.Response r = Http.postJson(API + "/oauth/token", null, body);
            if (r.ok()) {
                store(r.json());
                Log.i(TAG, "token refreshed");
                return true;
            }
            Log.w(TAG, "refresh answered " + r.code + ": " + r.body);
            if (r.code == 401 || r.code == 403) {
                prefs.edit().clear().apply();
                return false;
            }
            return true;   // transient: keep the old token, it may still work
        } catch (IOException | JSONException e) {
            Log.w(TAG, "refresh: " + e.getMessage());
            return true;
        }
    }
}
