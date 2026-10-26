package com.excavplayer.subtitles

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CleanedTitle(
    val formatted: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null
)

fun cleanVideoTitle(rawName: String): CleanedTitle {
    val baseName = rawName.substringBeforeLast('.')

    var season: Int? = null
    var episode: Int? = null
    var titlePart: String

    val seMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:s(\d{1,2})[\.\s_]*e(\d{1,3}))(?:[\.\s_)\]-]|$)""").find(baseName)
        ?: Regex("""(?i)(?:[\.\s_(\[-])(\d{1,2})x(\d{1,3})(?:[\.\s_)\]-]|$)""").find(baseName)
        ?: Regex("""(?i)(?:[\.\s_(\[-])(?:season|s)[\.\s_]*(\d{1,2})[\.\s_]+(?:episode|ep|e)[\.\s_]*(\d{1,3})""").find(baseName)

    if (seMatch != null) {
        season = seMatch.groupValues[1].toIntOrNull()
        episode = seMatch.groupValues[2].toIntOrNull()
        titlePart = baseName.substring(0, seMatch.range.first)
    } else {
        val epMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:ep|episode)[\.\s_]*(\d{1,3})""").find(baseName)
        if (epMatch != null) {
            episode = epMatch.groupValues[1].toIntOrNull()
            titlePart = baseName.substring(0, epMatch.range.first)
        } else {
            val sMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:season|s)[\.\s_]*(\d{1,2})""").find(baseName)
            if (sMatch != null) {
                season = sMatch.groupValues[1].toIntOrNull()
                titlePart = baseName.substring(0, sMatch.range.first)
            } else {
                // Movie title extraction:
                // Look for release year e.g. (1999) or .2022. or 2022 followed by tags
                val yearMatches = Regex("""(?i)(?:[\.\s_(\[-])((?:19|20)\d{2})(?:[\.\s_)\]-]|$)""").findAll(baseName).toList()
                val movieYearMatch = yearMatches.lastOrNull { m ->
                    val afterYear = baseName.substring(m.range.last).trim()
                    afterYear.isEmpty() || Regex("""(?i)(?:[\.\s_\)\]-]|^)(?:2160p|4k|uhd|1080p|1080i|720p|576p|480p|360p|bluray|blu-ray|bdrip|brrip|webrip|web-dl|web|hdrip|hdtv|dvdrip|dvd|remux|cam|ts|proper|repack|x264|x265|h264|h265|hevc|aac|dts|ddp|truehd|atmos)""").containsMatchIn(afterYear)
                } ?: yearMatches.firstOrNull()

                if (movieYearMatch != null && movieYearMatch.range.first > 0) {
                    titlePart = baseName.substring(0, movieYearMatch.range.first)
                } else {
                    // Fall back to cutting before resolution / release quality tags
                    val qualityMatch = Regex("""(?i)(?:[\.\s_(\[-])(2160p|4k|uhd|1080p|1080i|720p|576p|480p|360p|bluray|blu-ray|bdrip|brrip|webrip|web-dl|web|hdrip|hdtv|dvdrip|dvd|remux|cam|ts|proper|repack|x264|x265|h264|h265|hevc|xvid|divx)(?:[\.\s_)\]-]|$)""").find(baseName)
                    titlePart = if (qualityMatch != null && qualityMatch.range.first > 0) {
                        baseName.substring(0, qualityMatch.range.first)
                    } else {
                        baseName
                    }
                }
            }
        }
    }

    var cleaned = titlePart.replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), " ")

    val junkPatterns = listOf(
        Regex("""(?i)\b(?:aac|ddp?|dts|ac3)?\s*\d\s*[\.\s]\s*\d\b"""),
        Regex("""(?i)\b(?:2160p|4k|uhd|1080p|1080i|720p|480p|360p|576p)\b"""),
        Regex("""(?i)\b(?:x264|x265|h264|h265|hevc|avc|xvid|divx|10bit|8bit)\b"""),
        Regex("""(?i)\b(?:bluray|blu-ray|bdrip|brrip|webrip|web-dl|web|hdrip|hdtv|dvdrip|dvd|remux|cam|ts|proper|repack|extended|unrated|directors[\.\s]cut)\b"""),
        Regex("""(?i)\b(?:hdr(?:10(?:\+)?)?|sdr|dv|dolby[\.\s]vision|atmos|truehd)\b"""),
        Regex("""(?i)\b(?:yts(?:\.mx|\.lt|\.ag)?|rarbg|eztv|tgx|galaxyrg|qxr|ettv|sparks|geckos|vxt)\b"""),
        Regex("""\b(?:19|20)\d{2}\b""")
    )

    for (pattern in junkPatterns) {
        cleaned = cleaned.replace(pattern, " ")
    }

    cleaned = cleaned.replace(Regex("""[\._]+"""), " ")
    cleaned = cleaned.replace(Regex("""\s+-\s+"""), " ")
    cleaned = cleaned.replace(Regex("""^[\s\-]+|[\s\-]+$"""), "")
    cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

    if (cleaned.isBlank()) {
        cleaned = rawName.substringBeforeLast('.')
    }

    val formatted = when {
        season != null && episode != null -> String.format("%s S%02dE%02d", cleaned, season, episode)
        season != null -> String.format("%s S%02d", cleaned, season)
        episode != null -> String.format("%s E%02d", cleaned, episode)
        else -> cleaned
    }

    return CleanedTitle(formatted, cleaned, season, episode)
}

@Singleton
class SubtitleHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    val searchService: SubtitleSearchService,
    val downloader: SubtitleDownloader,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SubtitleHelper"
    }

    fun inferMimeTypeFromUri(uri: Uri): String {
        val path = uri.path ?: uri.toString()
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            "ttml", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    suspend fun searchOnlineSubtitles(
        query: String,
        languageCode: String = "en",
        source: SubtitleSource = SubtitleSource.ALL
    ): SubtitleSearchOutcome = withContext(dispatchers.io) {
        searchService.searchSubtitles(query, languageCode, source)
    }

    suspend fun downloadAndSaveSubtitle(
        result: SubtitleSearchResult,
        videoTitle: String
    ): Result<File> = withContext(dispatchers.io) {
        val resolvedUrl = searchService.resolveDownloadUrl(result)
        val updatedResult = if (resolvedUrl != result.downloadUrl) result.copy(downloadUrl = resolvedUrl) else result
        downloader.downloadSubtitle(updatedResult, videoTitle)
    }

    fun getDownloadedSubtitles(): List<File> {
        val dir = downloader.getSubtitlesDirectory()
        return if (dir.exists()) {
            dir.listFiles { file ->
                file.isFile && (file.name.endsWith(".srt", ignoreCase = true) ||
                        file.name.endsWith(".vtt", ignoreCase = true) ||
                        file.name.endsWith(".ass", ignoreCase = true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }
}
