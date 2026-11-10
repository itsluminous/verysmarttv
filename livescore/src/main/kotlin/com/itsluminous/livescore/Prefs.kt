package com.itsluminous.livescore

import android.content.Context
import android.content.SharedPreferences
import com.itsluminous.livescore.model.Sport

/**
 * Where badminton scores come from (ADR-044, ADR-049): SportMicro's keyed API (the default), BWF's own Match Centre read
 * through a hidden WebView (experimental), or nowhere. [token] is the stored value.
 */
enum class BadmintonSource(val token: String) {
    SPORTMICRO("sportmicro"),
    BWF_WEBVIEW("bwf_webview"),
    NONE("none");

    companion object {
        fun fromToken(token: String?): BadmintonSource = entries.firstOrNull { it.token == token } ?: SPORTMICRO
    }
}

/** The companion's own settings (§13.6): live refresh on or off, which sports are shown, the badminton source (ADR-049) and its data key (ADR-044). */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Whether a provider read or an open scoreboard may fetch at the live cadence; off, only the 6 h job fetches. */
    var liveRefresh: Boolean
        get() = prefs.getBoolean(KEY_LIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE, value).apply()

    /** The sports Home, the picker and the pill show; every sport until one is unticked. */
    var sportsShown: Set<Sport>
        get() = prefs.getStringSet(KEY_SPORTS, null)?.mapNotNull { Sport.fromToken(it) }?.toSet() ?: Sport.entries.toSet()
        set(value) = prefs.edit().putStringSet(KEY_SPORTS, value.map { it.token }.toSet()).apply()

    /** The user's own RapidAPI key for SportMicro's badminton feed; empty until entered. Kept here, never in the APK, a build variable, a CI secret or a backup. */
    var badmintonKey: String
        get() = prefs.getString(KEY_BADMINTON, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BADMINTON, value.trim()).apply()

    /** Which source answers badminton; SportMicro until the user picks the experimental BWF website or Off. */
    var badmintonSource: BadmintonSource
        get() = BadmintonSource.fromToken(prefs.getString(KEY_BADMINTON_SOURCE, null))
        set(value) = prefs.edit().putString(KEY_BADMINTON_SOURCE, value.token).apply()

    companion object {
        const val NAME = "settings"
        private const val KEY_BADMINTON_SOURCE = "badminton_source"
        private const val KEY_LIVE = "live_refresh"
        private const val KEY_SPORTS = "sports_shown"
        private const val KEY_BADMINTON = "badminton_key"
    }
}
