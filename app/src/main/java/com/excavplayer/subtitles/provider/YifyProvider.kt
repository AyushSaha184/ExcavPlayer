package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** YIFY subtitles — HTML page scraped with the download-link regex. */
@Singleton
class YifyProvider @Inject constructor() : SubtitleProvider {

    override val source = SubtitleSource.YIFY

    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val html = httpGet(
                "https://yts-subs.com/search/${encode(query)}",
                userAgent = "Mozilla/5.0 (Android; Mobile)"
            ) ?: return@withContext emptyList()

            parseYifyHtml(html, languageCode)
        }
}

internal fun parseYifyHtml(html: String, languageCode: String): List<SubtitleSearchResult> {
    val regex = Regex("""href=["'](/subtitles/[^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    val results = mutableListOf<SubtitleSearchResult>()

    for (match in regex.findAll(html).take(10)) {
        val subPath = match.groupValues[1]
        val title = stripTags(match.groupValues[2])
        if (title.isNotBlank()) {
            results.add(
                SubtitleSearchResult(
                    id = "yify_${subPath.hashCode()}",
                    source = "YIFY",
                    title = title,
                    fileName = "$title.srt",
                    language = if (languageCode != "all") languageCode.uppercase() else "English",
                    languageCode = languageCode,
                    downloadUrl = "https://yts-subs.com$subPath",
                    format = "srt"
                )
            )
        }
    }
    return results
}
