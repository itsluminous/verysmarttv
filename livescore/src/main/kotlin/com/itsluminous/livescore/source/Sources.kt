package com.itsluminous.livescore.source

import android.content.Context
import com.itsluminous.livescore.BadmintonSource
import com.itsluminous.livescore.Prefs
import com.itsluminous.livescore.R
import com.itsluminous.livescore.source.bwf.BwfWebViewSource
import com.itsluminous.livescore.source.bwf.BwfProcessLoader
import com.itsluminous.livescore.source.espn.EspnCatalogue
import com.itsluminous.livescore.source.espn.EspnSource
import com.itsluminous.livescore.source.sportmicro.SportMicroSource

/**
 * Where the sources are built and registered (§13.3, ADR-044, ADR-049): ESPN for cricket, football, F1 and tennis;
 * for badminton, SportMicro's Badminton Micro API with the key the user entered in Settings, or BWF's own Match Centre
 * through a hidden WebView in the `:bwf` process (ADR-050), whichever **Settings → Badminton source** names (neither under
 * Off). Adding a source means adding it here.
 */
object Sources {
    fun create(context: Context, prefs: Prefs): ScoreSource {
        val app = context.applicationContext
        val catalogue = app.resources.openRawResource(R.raw.espn_catalogue).use { EspnCatalogue.load(it) }
        val sources = listOf(EspnSource(catalogue, Http), SportMicroSource({ prefs.badmintonKey }, Http), BwfWebViewSource(BwfProcessLoader(app)))
        return SourceRegistry(sources) { source -> enabled(source.id, prefs.badmintonSource) }
    }

    /** Whether the source [id] answers under the badminton [choice]: ESPN always, the two badminton sources only when chosen. Pure. */
    fun enabled(id: String, choice: BadmintonSource): Boolean =
        when (id) {
            SportMicroSource.ID -> choice == BadmintonSource.SPORTMICRO
            BwfWebViewSource.ID -> choice == BadmintonSource.BWF_WEBVIEW
            else -> true
        }
}
