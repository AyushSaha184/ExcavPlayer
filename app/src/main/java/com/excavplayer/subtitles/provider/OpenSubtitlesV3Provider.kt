package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.CleanedTitle
import com.excavplayer.subtitles.LanguageRegistry
import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import com.excavplayer.subtitles.cleanVideoTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenSubtitles via the Stremio gateway: resolves the title to an IMDb id
 * (IMDb Suggest, falling back to Cinemeta) and fetches the subtitle list.
 */
@Singleton
class OpenSubtitlesV3Provider @Inject constructor() : SubtitleProvider {

    override val source = SubtitleSource.OPENSUBTITLES

    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val cleanInfo: CleanedTitle = cleanVideoTitle(query)
            val searchTitle = cleanInfo.title.ifBlank { query }
            val firstChar = searchTitle.firstOrNull()?.lowercaseChar() ?: 'a'
            val encodedQuery = encode(searchTitle.lowercase())

            var imdbId: String? = null
            var isSeries = (cleanInfo.season != null && cleanInfo.episode != null)

            // 1. Try official IMDb Suggest API (degrades to Cinemeta on failure)
            imdbId = parseImdbSuggest(
                httpGet(
                    "https://v2.sg.media-imdb.com/suggestion/titles/$firstChar/$encodedQuery.json",
                    userAgent = "Mozilla/5.0",
                    connectTimeoutMs = 8_000,
                    readTimeoutMs = 8_000
                )
            )?.let { (id, qType) ->
                if (qType.contains("series", ignoreCase = true) || qType.contains("tv", ignoreCase = true)) {
                    isSeries = true
                }
                id
            } ?: run {
                // Fallback: Cinemeta catalog search
                val cat = if (isSeries) "series" else "movie"
                parseCinemeta(
                    httpGet(
                        "https://v3-cinemeta.strem.io/catalog/$cat/top/search=$encodedQuery.json",
                        userAgent = "Mozilla/5.0",
                        connectTimeoutMs = 8_000,
                        readTimeoutMs = 8_000
                    )
                )
            }

            if (imdbId == null) return@withContext emptyList()

            // 2. Fetch OpenSubtitles v3
            val subUrl = if (cleanInfo.season != null && cleanInfo.episode != null) {
                "https://opensubtitles-v3.strem.io/subtitles/series/$imdbId:${cleanInfo.season}:${cleanInfo.episode}.json"
            } else {
                "https://opensubtitles-v3.strem.io/subtitles/movie/$imdbId.json"
            }

            val body = httpGet(
                subUrl,
                userAgent = "Mozilla/5.0",
                connectTimeoutMs = 10_000,
                readTimeoutMs = 12_000
            ) ?: return@withContext emptyList()

            parseStremioSubtitles(body, languageCode, searchTitle, cleanInfo.formatted)
        }
}

/** Returns (imdbId, qType) for the first suggestion, or null. */
internal fun parseImdbSuggest(body: String?): Pair<String, String>? {
    if (body.isNullOrBlank()) return null
    val root = ProviderJson.parseToJsonElement(body).jsonObject
    val items = root["d"]?.jsonArray
    val best = items?.firstOrNull()?.jsonObject
    val id = best?.get("id")?.jsonPrimitive?.contentOrNull ?: return null
    val qType = best?.get("q")?.jsonPrimitive?.contentOrNull.orEmpty()
    return id to qType
}

internal fun parseCinemeta(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val root = ProviderJson.parseToJsonElement(body).jsonObject
    val metas = root["metas"]?.jsonArray
    return metas?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
}

internal fun parseStremioSubtitles(
    body: String,
    languageCode: String,
    searchTitle: String,
    fallbackRelease: String
): List<SubtitleSearchResult> {
    val root = ProviderJson.parseToJsonElement(body).jsonObject
    val subtitles = root["subtitles"]?.jsonArray ?: JsonArray(emptyList())

    val target2 = if (languageCode != "all") languageCode.lowercase() else null
    val results = mutableListOf<SubtitleSearchResult>()

    for (item in subtitles) {
        val obj = item.jsonObject
        val subId = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
        val downloadUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
        val rawLang = obj["lang"]?.jsonPrimitive?.contentOrNull ?: "eng"
        val lang2 = LanguageRegistry.normalizeLangCode(rawLang)

        if (target2 != null && !lang2.equals(target2, ignoreCase = true) && !rawLang.equals(target2, ignoreCase = true)) {
            continue
        }

        val fileName = obj["subtitleFileName"]?.jsonPrimitive?.contentOrNull
            ?: obj["movieReleaseName"]?.jsonPrimitive?.contentOrNull
            ?: "$searchTitle.srt"
        val releaseName = obj["movieReleaseName"]?.jsonPrimitive?.contentOrNull
            ?: fallbackRelease
        val langDisplayName = LanguageRegistry.displayNameForAny(rawLang)

        results.add(
            SubtitleSearchResult(
                id = "os_v3_$subId",
                source = "OpenSubtitles",
                title = releaseName,
                fileName = fileName,
                language = langDisplayName,
                languageCode = lang2,
                downloadUrl = downloadUrl,
                format = "srt",
                hearingImpaired = false,
                downloads = null
            )
        )
    }
    return results
}
