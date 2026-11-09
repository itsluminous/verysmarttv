package com.itsluminous.livescore.source

/**
 * What a feed says about an event's progress, before normalisation (§13.4): ESPN's `status.type.state`
 * (`pre`, `in`, `post`), its `type.description` (`Live`, `Stumps`, `Full Time`), `type.detail`, the
 * `period` (innings, half, lap, set), the display clock and the summary sentence.
 */
data class FeedStatus(
    val state: String = "",
    val description: String = "",
    val detail: String = "",
    val period: Int = 0,
    val clock: String = "",
    val summary: String = "",
)
