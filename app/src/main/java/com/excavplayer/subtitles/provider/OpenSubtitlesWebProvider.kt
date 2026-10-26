package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenSubtitles website endpoint — HTML page scraped with the download-link
 * regex. Kept alongside the REST API because it serves different releases.
 */
@Singleton
class OpenSubtitlesWebProvider @Inject constructor() : SubtitleProvider {

    override val source = SubtitleSource.OPENSUBTITLES

    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val langSub = if (languageCode == "all") "all" else languageCode
            val encodedQuery = encode(query).replace("+", "%20")
            val html = httpGet(
                "https://www.opensubtitles.org/en/search2/sublanguageid-$langSub/moviename-$encodedQuery",
                userAgent = "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0"
            ) ?: return@withContext emptyList()

            parseOpenSubtitlesHtml(html, languageCode)
        }
}

internal fun parseOpenSubtitlesHtml(html: String, languageCode: String): List<SubtitleSearchResult> {
    val regex = Regex("""href=["'](/en/subtitleserve/sub/(\d+))["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    val results = mutableListOf<SubtitleSearchResult>()

    for (match in regex.findAll(html).take(15)) {
        val subPath = match.groupValues[1]
        val subId = match.groupValues[2]
        val rawTitle = stripTags(match.groupValues[3])
        if (rawTitle.isNotBlank()) {
            results.add(
                SubtitleSearchResult(
                    id = "os_web_$subId",
                    source = "OpenSubtitles",
                    title = rawTitle,
                    fileName = "$rawTitle.srt",
                    language = if (languageCode != "all") languageCode.uppercase() else "English",
                    languageCode = languageCode,
                    downloadUrl = "https://www.opensubtitles.org$subPath",
                    format = "srt"
                )
            )
        }
    }
    return results
}
