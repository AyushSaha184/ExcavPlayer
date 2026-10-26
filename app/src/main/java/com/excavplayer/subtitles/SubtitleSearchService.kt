package com.excavplayer.subtitles

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.subtitles.provider.OpenSubtitlesRestProvider
import com.excavplayer.subtitles.provider.SubtitleProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the subtitle providers: selects the adapters for the requested
 * [SubtitleSource], fans out concurrently, classifies failures, and applies
 * the shared dedup/sort. Providers throw on failure; this is where a thrown
 * exception becomes a typed [SubtitleSearchOutcome].
 */
@Singleton
class SubtitleSearchService @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
    private val providers: Set<@JvmSuppressWildcards SubtitleProvider>,
    private val restProvider: OpenSubtitlesRestProvider
) {
    companion object {
        private const val TAG = "SubtitleSearchService"
    }

    suspend fun searchSubtitles(
        query: String,
        languageCode: String = "en",
        source: SubtitleSource = SubtitleSource.ALL
    ): SubtitleSearchOutcome = withContext(dispatchers.io) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SubtitleSearchOutcome.NoResults

        logger.i(TAG, "Searching subtitles for '$cleanQuery', lang='$languageCode', source='${source.displayName}'")

        coroutineScope {
            val selected = providers.filter { source == SubtitleSource.ALL || it.source == source }
            val attempts = selected.map { provider ->
                async { provider.source to runCatching { provider.search(cleanQuery, languageCode) } }
            }.awaitAll()

            val failures = mutableListOf<Pair<SubtitleSource, Throwable>>()
            val results = attempts.flatMap { (providerSource, attempt) ->
                attempt.getOrElse { e ->
                    logger.w(TAG, "${providerSource.displayName} failed: ${e.message}")
                    failures += providerSource to e
                    emptyList()
                }
            }

            when {
                results.isNotEmpty() -> SubtitleSearchOutcome.Results(deduplicateAndSort(results, languageCode))
                failures.any { it.second.isNetworkFailure() } ->
                    SubtitleSearchOutcome.NetworkError(failures.first { it.second.isNetworkFailure() }.second)
                failures.isNotEmpty() -> SubtitleSearchOutcome.ParseError(
                    failures.map { (failedSource, e) -> "${failedSource.displayName}: ${e.message ?: e.javaClass.simpleName}" }
                )
                else -> SubtitleSearchOutcome.NoResults
            }
        }
    }

    /** Shared result hygiene: dedup by identity, preferred language first, then downloads. */
    internal fun deduplicateAndSort(
        results: List<SubtitleSearchResult>,
        languageCode: String
    ): List<SubtitleSearchResult> =
        results.distinctBy { "${it.source}_${it.title}_${it.language}_${it.downloadUrl}" }
            .sortedWith(
                compareByDescending<SubtitleSearchResult> {
                    if (languageCode != "all" && it.languageCode.equals(languageCode, ignoreCase = true)) 2 else 1
                }.thenByDescending { it.downloads ?: 0 }
            )

    /** Resolves OpenSubtitles direct download links when needed (delegates to the REST provider). */
    suspend fun resolveDownloadUrl(result: SubtitleSearchResult): String =
        restProvider.resolveDownloadUrl(result)

    private fun Throwable.isNetworkFailure(): Boolean = this is IOException
}
