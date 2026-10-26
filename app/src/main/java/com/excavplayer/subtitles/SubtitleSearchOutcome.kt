package com.excavplayer.subtitles

/**
 * What a subtitle search actually produced. Distinguishes "the sites answered
 * and have nothing" from "we could not reach the sites" from "the sites
 * answered with junk", which the UI surfaces differently.
 */
sealed interface SubtitleSearchOutcome {
    /** At least one provider returned results (other providers may still have failed). */
    data class Results(val items: List<SubtitleSearchResult>) : SubtitleSearchOutcome

    /** Every selected provider answered successfully with zero results. */
    data object NoResults : SubtitleSearchOutcome

    /** Every selected provider failed at the network level — the sites are unreachable. */
    data class NetworkError(val cause: Throwable) : SubtitleSearchOutcome

    /** Every selected provider failed for a non-network reason (malformed bodies, etc.). */
    data class ParseError(val messages: List<String>) : SubtitleSearchOutcome
}
