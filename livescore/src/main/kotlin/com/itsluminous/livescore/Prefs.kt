package com.itsluminous.livescore

import android.content.Context
import android.content.SharedPreferences
import com.itsluminous.livescore.model.Sport

/** The companion's own settings (§13.6): live refresh on or off, and which sports are shown. */
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

    companion object {
        const val NAME = "settings"
        private const val KEY_LIVE = "live_refresh"
        private const val KEY_SPORTS = "sports_shown"
    }
}
