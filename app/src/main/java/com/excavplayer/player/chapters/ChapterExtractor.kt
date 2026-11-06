package com.excavplayer.player.chapters

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.ChapterTocFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.ChapterType
import com.excavplayer.domain.model.MediaChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class RawChapter(
    val id: String = "",
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long = -1L
)

@OptIn(UnstableApi::class)
@Singleton
class ChapterExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "ChapterExtractor"
        private const val MAX_CHAPTERS_LIMIT = 500
    }

    /**
     * Extracts chapters from media container using NextPlayer's FFmpeg native MediaInfoBuilder,
     * or external sidecar files (.chp, .chapters.txt, .chapters.xml).
     */
    suspend fun extractFromUri(uriString: String, videoDurationMs: Long? = null): List<MediaChapter> = withContext(Dispatchers.IO) {
        val rawLocalPath = when {
            uriString.startsWith("file://", ignoreCase = true) -> uriString.substring(7)
            uriString.startsWith("/") -> uriString
            else -> null
        }
        val localPath = rawLocalPath?.let { raw ->
            try {
                java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
            } catch (_: Throwable) {
                raw
            }
        }

        // 1. Check sidecar files if local path exists
        if (localPath != null) {
            try {
                val mediaFile = File(localPath)
                if (mediaFile.exists()) {
                    val parent = mediaFile.parentFile
                    val baseName = mediaFile.nameWithoutExtension
                    val candidateExtensions = listOf(".chapters.txt", ".chp", ".chapters.xml")
                    for (ext in candidateExtensions) {
                        val sidecar = File(parent, "$baseName$ext")
                        if (sidecar.exists() && sidecar.isFile && sidecar.length() > 0) {
                            val content = sidecar.readText(StandardCharsets.UTF_8)
                            val sidecarChapters = parseSidecarChapters(content, videoDurationMs)
                            if (sidecarChapters.isNotEmpty()) {
                                logger.i(TAG, "Loaded ${sidecarChapters.size} chapters from sidecar file: ${sidecar.name}")
                                return@withContext sidecarChapters
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.d(TAG, "Sidecar chapter check skipped: ${e.message}")
            }
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext emptyList()

        // 2. NextPlayer FFmpeg extraction via MediaInfoBuilder (MKV, MP4, WebM, etc.)
        val mediaInfo = runCatching {
            MediaInfoBuilder().from(context, uri).build()
        }.onFailure { e ->
            logger.d(TAG, "MediaInfoBuilder chapter extraction failed: ${e.message}")
        }.getOrNull()

        val ffmpegChapters = try {
            mediaInfo?.chapters?.mapIndexed { i, ch ->
                RawChapter(
                    id = "chap_${ch.index}_$i",
                    title = ch.title.orEmpty(),
                    startTimeMs = ch.start,
                    endTimeMs = ch.end
                )
            }.orEmpty()
        } finally {
            try {
                mediaInfo?.release()
            } catch (e: Throwable) {
                logger.d(TAG, "Failed releasing MediaInfo: ${e.message}")
            }
        }

        if (ffmpegChapters.isNotEmpty()) {
            logger.i(TAG, "Extracted ${ffmpegChapters.size} chapters via NextPlayer FFmpeg MediaInfo")
            return@withContext sanitizeAndNormalize(ffmpegChapters, videoDurationMs)
        }

        emptyList()
    }

    /**
     * Extracts dynamic chapters from Media3 player tracks (following NextPlayer's ChaptersState approach).
     */
    fun extractFromTracks(tracks: Tracks, videoDurationMs: Long? = null): List<MediaChapter> {
        val raw = mutableListOf<RawChapter>()
        for (group in tracks.groups) {
            val mediaTrackGroup = group.mediaTrackGroup
            for (i in 0 until mediaTrackGroup.length) {
                val format = mediaTrackGroup.getFormat(i)
                val metadata = format.metadata ?: continue
                raw.addAll(extractRawFromMetadata(metadata))
            }
        }
        return sanitizeAndNormalize(raw, videoDurationMs)
    }

    /**
     * Extracts chapters from Media3 Metadata (ID3 ChapterFrame, Vorbis comments, etc.).
     */
    fun extractFromMetadata(metadata: Metadata, videoDurationMs: Long? = null): List<MediaChapter> {
        val raw = extractRawFromMetadata(metadata)
        val normalized = sanitizeAndNormalize(raw, videoDurationMs)
        if (normalized.isNotEmpty()) {
            logger.i(TAG, "Extracted ${normalized.size} normalized chapters from metadata")
        }
        return normalized
    }

    private fun extractRawFromMetadata(metadata: Metadata): List<RawChapter> {
        val rawChapters = mutableListOf<RawChapter>()
        val vorbisTimeMap = mutableMapOf<String, Long>()
        val vorbisEndTimeMap = mutableMapOf<String, Long>()
        val vorbisNameMap = mutableMapOf<String, String>()

        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is ChapterFrame -> {
                    var title = entry.chapterId.orEmpty()
                    for (j in 0 until entry.subFrameCount) {
                        when (val sub = entry.getSubFrame(j)) {
                            is TextInformationFrame -> {
                                val text = sub.values.firstOrNull()
                                if (!text.isNullOrBlank()) {
                                    title = text
                                    break
                                }
                            }
                            is CommentFrame -> {
                                if (sub.text.isNotBlank()) {
                                    title = sub.text
                                    break
                                }
                            }
                        }
                    }
                    rawChapters.add(
                        RawChapter(
                            id = "chap_${entry.chapterId}_$i",
                            title = title,
                            startTimeMs = entry.startTimeMs.toLong(),
                            endTimeMs = entry.endTimeMs.toLong()
                        )
                    )
                }

                is ChapterTocFrame -> {
                    logger.d(TAG, "Found ChapterTocFrame: ${entry.elementId}")
                }

                is VorbisComment -> {
                    val key = entry.key.uppercase().trim()
                    val value = entry.value.trim()
                    parseVorbisChapterTag(key, value, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                }

                is TextInformationFrame -> {
                    val desc = entry.description?.uppercase()?.trim().orEmpty()
                    val value = entry.values.firstOrNull()?.trim().orEmpty()
                    if (desc.startsWith("CHAPTER") && value.isNotEmpty()) {
                        parseVorbisChapterTag(desc, value, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                    }
                }

                is CommentFrame -> {
                    val desc = entry.description?.uppercase()?.trim().orEmpty()
                    val text = entry.text?.trim().orEmpty()
                    if (desc.startsWith("CHAPTER") && text.isNotEmpty()) {
                        parseVorbisChapterTag(desc, text, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                    }
                }
            }
        }

        for ((chapterNum, startMs) in vorbisTimeMap) {
            val title = vorbisNameMap[chapterNum] ?: "Chapter $chapterNum"
            val endMs = vorbisEndTimeMap[chapterNum] ?: -1L
            rawChapters.add(
                RawChapter(
                    id = "vorbis_$chapterNum",
                    title = title,
                    startTimeMs = startMs,
                    endTimeMs = endMs
                )
            )
        }

        return rawChapters
    }

    /**
     * Parses external sidecar chapter text in YouTube/timestamp or OGG/Vorbis format.
     */
    fun parseSidecarChapters(content: String, videoDurationMs: Long? = null): List<MediaChapter> {
        val rawChapters = mutableListOf<RawChapter>()
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val isVorbis = lines.any { it.startsWith("CHAPTER", ignoreCase = true) && it.contains("=") }
        if (isVorbis) {
            val vorbisTimeMap = mutableMapOf<String, Long>()
            val vorbisEndTimeMap = mutableMapOf<String, Long>()
            val vorbisNameMap = mutableMapOf<String, String>()

            for (line in lines) {
                val eqIdx = line.indexOf('=')
                if (eqIdx != -1) {
                    val key = line.substring(0, eqIdx).trim()
                    val value = line.substring(eqIdx + 1).trim()
                    parseVorbisChapterTag(key, value, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                }
            }

            for ((chapterNum, startMs) in vorbisTimeMap) {
                val title = vorbisNameMap[chapterNum] ?: "Chapter $chapterNum"
                val endMs = vorbisEndTimeMap[chapterNum] ?: -1L
                rawChapters.add(
                    RawChapter(
                        id = "sidecar_vorbis_$chapterNum",
                        title = title,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                )
            }
        } else {
            val timestampRegex = Regex("""^[\[\(]?(\d{1,2}:\d{2}(?::\d{2})?(?:\.\d{1,3})?)[\]\)]?[\s\-:]+(.*)$""")
            var index = 1
            for (line in lines) {
                val match = timestampRegex.find(line)
                if (match != null) {
                    val timeStr = match.groupValues[1]
                    val title = match.groupValues[2].trim(' ', '-', ':')
                    val timeMs = parseTimestampMs(timeStr)
                    if (timeMs != null) {
                        rawChapters.add(
                            RawChapter(
                                id = "sidecar_ts_${index++}",
                                title = title,
                                startTimeMs = timeMs
                            )
                        )
                    }
                }
            }
        }

        return sanitizeAndNormalize(rawChapters, videoDurationMs)
    }

    /**
     * Comprehensive validation, deduplication, duration bounds checking, and title sanitization.
     */
    fun sanitizeAndNormalize(
        rawChapters: List<RawChapter>,
        videoDurationMs: Long? = null
    ): List<MediaChapter> {
        if (rawChapters.isEmpty()) return emptyList()

        val validStart = rawChapters
            .filter { it.startTimeMs >= 0L }
            .take(MAX_CHAPTERS_LIMIT)
        if (validStart.isEmpty()) return emptyList()

        val sorted = validStart.sortedBy { it.startTimeMs }

        val deduplicated = mutableListOf<RawChapter>()
        for (chapter in sorted) {
            val last = deduplicated.lastOrNull()
            if (last == null || (chapter.startTimeMs - last.startTimeMs) >= 500L) {
                deduplicated.add(chapter)
            } else {
                val lastTitle = last.title.trim()
                val newTitle = chapter.title.trim()
                val preferredTitle = when {
                    lastTitle.isBlank() -> newTitle
                    newTitle.isBlank() -> lastTitle
                    lastTitle.startsWith("Chapter ", ignoreCase = true) && !newTitle.startsWith("Chapter ", ignoreCase = true) -> newTitle
                    else -> lastTitle
                }
                val preferredId = if (last.id.isBlank() && chapter.id.isNotBlank()) chapter.id else last.id
                deduplicated[deduplicated.lastIndex] = RawChapter(
                    id = preferredId,
                    title = preferredTitle,
                    startTimeMs = minOf(last.startTimeMs, chapter.startTimeMs),
                    endTimeMs = maxOf(last.endTimeMs, chapter.endTimeMs)
                )
            }
        }

        val totalDuration = videoDurationMs?.takeIf { it > 0 }
        val result = mutableListOf<MediaChapter>()

        for (i in 0 until deduplicated.size) {
            val current = deduplicated[i]
            val next = deduplicated.getOrNull(i + 1)

            val start = current.startTimeMs
            if (totalDuration != null && start >= totalDuration) {
                continue
            }

            val nextStart = next?.startTimeMs
            var end = current.endTimeMs

            if (end <= start || end <= 0L || (nextStart != null && end > nextStart)) {
                end = nextStart ?: (totalDuration ?: (start + 60_000L))
            }

            if (totalDuration != null && end > totalDuration) {
                end = totalDuration
            }

            if (end <= start) {
                end = if (nextStart != null && nextStart > start) nextStart else start + 1_000L
            }

            val fallbackIndex = result.size + 1
            val sanitizedTitle = sanitizeTitle(current.title, fallbackIndex)
            val type = MediaChapter.determineChapterType(sanitizedTitle)

            result.add(
                MediaChapter(
                    id = current.id.ifBlank { "chap_${i}_$start" },
                    title = sanitizedTitle,
                    startTimeMs = start,
                    endTimeMs = end,
                    type = type
                )
            )
        }

        return result
    }

    fun sanitizeTitle(rawTitle: String, fallbackIndex: Int): String {
        var cleaned = rawTitle
            .replace("\uFFFD", "")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

        val meaningfulChars = cleaned.count { it.isLetterOrDigit() }
        if (cleaned.isBlank() || meaningfulChars == 0) {
            return "Chapter $fallbackIndex"
        }

        cleaned = cleaned.trim('?', ' ', '-', '_', ':', '.')
        if (cleaned.isBlank() || cleaned.count { it.isLetterOrDigit() } == 0) {
            return "Chapter $fallbackIndex"
        }

        return cleaned
    }

    private fun parseVorbisChapterTag(
        key: String,
        value: String,
        timeMap: MutableMap<String, Long>,
        endTimeMap: MutableMap<String, Long>,
        nameMap: MutableMap<String, String>
    ) {
        val upperKey = key.uppercase().trim()
        if (upperKey.startsWith("CHAPTER") && !upperKey.endsWith("NAME")) {
            if (upperKey.endsWith("END")) {
                val num = upperKey.removePrefix("CHAPTER").removeSuffix("END")
                parseTimestampMs(value)?.let { endTimeMap[num] = it }
            } else {
                val num = upperKey.removePrefix("CHAPTER").removeSuffix("TIME")
                parseTimestampMs(value)?.let { timeMap[num] = it }
            }
        } else if (upperKey.startsWith("CHAPTER") && upperKey.endsWith("NAME")) {
            val num = upperKey.removePrefix("CHAPTER").removeSuffix("NAME")
            if (value.isNotBlank()) {
                nameMap[num] = value
            }
        }
    }

    fun parseTimestampMs(timeStr: String): Long? {
        val clean = timeStr.trim()
        if (clean.isEmpty()) return null

        val parts = clean.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val mins = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val secs = secParts[0].toLong()
                    val millis = if (secParts.size > 1) {
                        secParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (hours * 3600 + mins * 60 + secs) * 1000L + millis
                }
                2 -> {
                    val mins = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val secs = secParts[0].toLong()
                    val millis = if (secParts.size > 1) {
                        secParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (mins * 60 + secs) * 1000L + millis
                }
                1 -> {
                    if (clean.contains(".")) {
                        val secParts = clean.split(".")
                        val secs = secParts[0].toLong()
                        val millis = if (secParts.size > 1) {
                            secParts[1].padEnd(3, '0').take(3).toLong()
                        } else 0L
                        secs * 1000L + millis
                    } else {
                        clean.toLongOrNull()
                    }
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
