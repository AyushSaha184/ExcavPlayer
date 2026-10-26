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
    var name = rawName.substringBeforeLast('.')
    name = name.replace(Regex("""\[.*?\]|\(.*?\)|<.*?>"""), " ")

    var season: Int? = null
    var episode: Int? = null
    var titlePart = name

    val seMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:s(\d{1,2})[\.\s_]*e(\d{1,3}))(?:[\.\s_)\]-]|$)""").find(name)
        ?: Regex("""(?i)(?:[\.\s_(\[-])(\d{1,2})x(\d{1,3})(?:[\.\s_)\]-]|$)""").find(name)
        ?: Regex("""(?i)(?:[\.\s_(\[-])(?:season|s)[\.\s_]*(\d{1,2})[\.\s_]+(?:episode|ep|e)[\.\s_]*(\d{1,3})""").find(name)

    if (seMatch != null) {
        season = seMatch.groupValues[1].toIntOrNull()
        episode = seMatch.groupValues[2].toIntOrNull()
        titlePart = name.substring(0, seMatch.range.first)
    } else {
        val epMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:ep|episode)[\.\s_]*(\d{1,3})""").find(name)
        if (epMatch != null) {
            episode = epMatch.groupValues[1].toIntOrNull()
            titlePart = name.substring(0, epMatch.range.first)
        } else {
            val sMatch = Regex("""(?i)(?:[\.\s_(\[-])(?:season|s)[\.\s_]*(\d{1,2})""").find(name)
            if (sMatch != null) {
                season = sMatch.groupValues[1].toIntOrNull()
                titlePart = name.substring(0, sMatch.range.first)
            }
        }
    }

    val junkPatterns = listOf(
        Regex("""(?i)\b(?:aac|ddp?|dts|ac3)?\s*\d\s*[\.\s]\s*\d\b"""),
        Regex("""(?i)\b(?:2160p|4k|uhd|1080p|1080i|720p|480p|360p|576p)\b"""),
        Regex("""(?i)\b(?:x264|x265|h264|h265|hevc|avc|xvid|divx|10bit|8bit)\b"""),
        Regex("""(?i)\b(?:bluray|blu-ray|bdrip|brrip|webrip|web-dl|web|hdrip|hdtv|dvdrip|dvd|remux|cam|ts|proper|repack|extended|unrated|directors[\.\s]cut)\b"""),
        Regex("""(?i)\b(?:hdr(?:10(?:\+)?)?|sdr|dv|dolby[\.\s]vision|atmos|truehd)\b"""),
        Regex("""(?i)\b(?:yts(?:\.mx|\.lt|\.ag)?|rarbg|eztv|tgx|galaxyrg|qxr|ettv|sparks|geckos|vxt)\b"""),
        Regex("""\b(?:19|20)\d{2}\b""")
    )

    var cleaned = titlePart
    for (pattern in junkPatterns) {
        cleaned = cleaned.replace(pattern, " ")
    }

    cleaned = cleaned.replace(Regex("""[\._\-]+"""), " ")
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
    ): List<SubtitleSearchResult> = withContext(dispatchers.io) {
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
