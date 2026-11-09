package com.itsluminous.livescore.source

import android.content.Context
import com.itsluminous.livescore.R
import com.itsluminous.livescore.source.espn.EspnCatalogue
import com.itsluminous.livescore.source.espn.EspnSource

/** Where the one source of v1 is built (§13.3): adding a source means registering it here. */
object Sources {
    fun create(context: Context): ScoreSource {
        val catalogue = context.resources.openRawResource(R.raw.espn_catalogue).use { EspnCatalogue.load(it) }
        return EspnSource(catalogue, Http)
    }
}
