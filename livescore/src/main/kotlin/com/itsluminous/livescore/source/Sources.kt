package com.itsluminous.livescore.source

import android.content.Context
import com.itsluminous.livescore.Prefs
import com.itsluminous.livescore.R
import com.itsluminous.livescore.source.espn.EspnCatalogue
import com.itsluminous.livescore.source.espn.EspnSource
import com.itsluminous.livescore.source.sportdevs.SportDevsSource

/**
 * Where the sources are built and registered (§13.3, ADR-044): ESPN for cricket, football, F1 and tennis;
 * SportDevs for badminton, with the key the user entered in Settings. Adding a source means adding it here.
 */
object Sources {
    fun create(context: Context, prefs: Prefs): ScoreSource {
        val catalogue = context.resources.openRawResource(R.raw.espn_catalogue).use { EspnCatalogue.load(it) }
        return SourceRegistry(listOf(EspnSource(catalogue, Http), SportDevsSource({ prefs.badmintonKey }, Http)))
    }
}
