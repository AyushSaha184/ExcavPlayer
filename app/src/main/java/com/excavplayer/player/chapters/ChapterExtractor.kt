package com.excavplayer.player.chapters

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.ChapterTocFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.MediaChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

data class RawChapter(
    val id: String = "",
    val title: String = "",
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
        private const val MAX_CONTAINER_SCAN_BYTES = 10 * 1024 * 1024 // 10 MB scan limit
    }

    /**
     * Extracts and validates chapters from Media3 player metadata (ID3, Vorbis, Text).
     */
    fun extractFromMetadata(metadata: Metadata, videoDurationMs: Long? = null): List<MediaChapter> {
        val rawChapters = mutableListOf<RawChapter>()
        val vorbisTimeMap = mutableMapOf<String, Long>()
        val vorbisEndTimeMap = mutableMapOf<String, Long>()
        val vorbisNameMap = mutableMapOf<String, String>()

        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is ChapterFrame -> {
                    var title = entry.chapterId
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
                    // Table of contents frame can supply sub-chapter ids if needed
                    logger.d(TAG, "Found ChapterTocFrame: ${entry.elementId}")
                }

                is VorbisComment -> {
                    val key = entry.key.uppercase().trim()
                    val value = entry.value.trim()
                    parseVorbisChapterTag(key, value, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                }

                is TextInformationFrame -> {
                    val desc = entry.description?.uppercase()?.trim() ?: ""
                    val value = entry.values.firstOrNull()?.trim() ?: ""
                    if (desc.startsWith("CHAPTER") && value.isNotEmpty()) {
                        parseVorbisChapterTag(desc, value, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                    }
                }

                is CommentFrame -> {
                    val desc = entry.description?.uppercase()?.trim() ?: ""
                    val text = entry.text?.trim() ?: ""
                    if (desc.startsWith("CHAPTER") && text.isNotEmpty()) {
                        parseVorbisChapterTag(desc, text, vorbisTimeMap, vorbisEndTimeMap, vorbisNameMap)
                    }
                }
            }
        }

        // Assemble Vorbis comment chapters (common in MKV, WebM, Ogg)
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

        val normalized = sanitizeAndNormalize(rawChapters, videoDurationMs)
        if (normalized.isNotEmpty()) {
            logger.i(TAG, "Extracted ${normalized.size} normalized chapters from metadata")
        }
        return normalized
    }

    /**
     * Extracts chapters directly from the media container (MKV Matroska EBML or MP4/MOV atoms).
     */
    suspend fun extractFromUri(uriString: String, videoDurationMs: Long? = null): List<MediaChapter> = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext emptyList()
        val rawChapters = mutableListOf<RawChapter>()

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedInputStream(stream, 64 * 1024).use { bis ->
                    // Read header magic to distinguish MKV (EBML) vs MP4
                    bis.mark(16)
                    val magic = ByteArray(12)
                    val read = bis.read(magic)
                    bis.reset()

                    if (read >= 4 && isMatroska(magic)) {
                        rawChapters.addAll(extractMatroskaChapters(bis))
                    } else if (read >= 8 && isMp4(magic)) {
                        rawChapters.addAll(extractMp4Chapters(bis))
                    }
                }
            }
        } catch (e: Exception) {
            logger.d(TAG, "Container chapter extraction skipped or failed: ${e.message}")
        }

        sanitizeAndNormalize(rawChapters, videoDurationMs)
    }

    /**
     * Comprehensive validation, deduplication, duration bounds checking, and title sanitization.
     */
    fun sanitizeAndNormalize(
        rawChapters: List<RawChapter>,
        videoDurationMs: Long? = null
    ): List<MediaChapter> {
        if (rawChapters.isEmpty()) return emptyList()

        // 1. Filter negative or corrupt start timestamps
        val validStart = rawChapters.filter { it.startTimeMs >= 0L }
        if (validStart.isEmpty()) return emptyList()

        // 2. Sort by start timestamp ascending
        val sorted = validStart.sortedBy { it.startTimeMs }

        // 3. Deduplicate chapters that start at the exact same or very near timestamp (< 500ms apart)
        val deduplicated = mutableListOf<RawChapter>()
        for (chapter in sorted) {
            val last = deduplicated.lastOrNull()
            if (last == null || (chapter.startTimeMs - last.startTimeMs) >= 500L) {
                deduplicated.add(chapter)
            } else {
                // Merge duplicate: keep earliest start, max end time, and preferred title
                val preferredTitle = if (last.title.isBlank() && chapter.title.isNotBlank()) chapter.title else last.title
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

            // Skip if start position is past the known video duration
            if (totalDuration != null && start >= totalDuration) {
                continue
            }

            val nextStart = next?.startTimeMs
            var end = current.endTimeMs

            // Fix end timestamp: if negative, <= startTime, or extends past the next chapter's start
            if (end <= start || end <= 0L || (nextStart != null && end > nextStart)) {
                end = nextStart ?: (totalDuration ?: (start + 60_000L))
            }

            // Cap to video duration if duration is known
            if (totalDuration != null && end > totalDuration) {
                end = totalDuration
            }

            // Ensure strictly end > start
            if (end <= start) {
                end = if (nextStart != null && nextStart > start) nextStart else start + 1_000L
            }

            // Sanitize title: remove unprintable ASCII control characters
            val sanitizedTitle = current.title
                .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
                .trim()
                .ifBlank { "Chapter ${result.size + 1}" }

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

    // -----------------------------------------------------------------------------------------
    // Helper Parsers
    // -----------------------------------------------------------------------------------------

    private fun parseVorbisChapterTag(
        key: String,
        value: String,
        timeMap: MutableMap<String, Long>,
        endTimeMap: MutableMap<String, Long>,
        nameMap: MutableMap<String, String>
    ) {
        val chapterRegex = Regex("^CHAPTER(\\d{1,4})(NAME|END)?$", RegexOption.IGNORE_CASE)
        val match = chapterRegex.find(key) ?: return
        val num = match.groupValues[1]
        val suffix = match.groupValues[2].uppercase()

        when (suffix) {
            "NAME" -> nameMap[num] = value
            "END" -> parseTimestampMs(value)?.let { endTimeMap[num] = it }
            "" -> parseTimestampMs(value)?.let { timeMap[num] = it }
        }
    }

    fun parseTimestampMs(timeStr: String): Long? {
        val clean = timeStr.trim()
        if (clean.isEmpty()) return null

        // Try HH:MM:SS.mmm or MM:SS.mmm or SS.mmm or raw millis
        val parts = clean.split(":")
        try {
            return when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secondsParts = parts[2].split(".")
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secondsParts = parts[1].split(".")
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else 0L
                    (minutes * 60 + seconds) * 1000 + millis
                }
                1 -> {
                    if (clean.contains(".")) {
                        val secondsParts = clean.split(".")
                        val seconds = secondsParts[0].toLong()
                        val millis = if (secondsParts.size > 1) {
                            secondsParts[1].padEnd(3, '0').take(3).toLong()
                        } else 0L
                        seconds * 1000 + millis
                    } else {
                        clean.toLongOrNull()
                    }
                }
                else -> clean.toLongOrNull()
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun isMatroska(magic: ByteArray): Boolean {
        // EBML ID: 0x1A 0x45 0xDF 0xA3
        return magic.size >= 4 &&
                (magic[0] == 0x1A.toByte() && magic[1] == 0x45.toByte() &&
                 magic[2] == 0xDF.toByte() && magic[3] == 0xA3.toByte())
    }

    private fun isMp4(magic: ByteArray): Boolean {
        // MP4 / MOV contains 'ftyp' or 'moov' at offset 4
        if (magic.size < 8) return false
        val type = String(magic, 4, 4, Charsets.US_ASCII)
        return type == "ftyp" || type == "moov" || type == "free" || type == "mdat"
    }

    /**
     * Fast EBML scan for Matroska Chapters atom (0x1043A770)
     */
     private fun extractMatroskaChapters(stream: InputStream): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        val buffer = ByteArray(64 * 1024)
        var totalRead = 0
        val bos = java.io.ByteArrayOutputStream(256 * 1024)

        while (totalRead < MAX_CONTAINER_SCAN_BYTES) {
            val count = stream.read(buffer)
            if (count <= 0) break
            bos.write(buffer, 0, count)
            totalRead += count
        }

        val data = bos.toByteArray()
        var i = 0
        while (i < data.size - 8) {
            // Check for ChapterTimeStart ID: 0x91
            if (data[i] == 0x91.toByte()) {
                val len = (data[i + 1].toInt() and 0xFF)
                if (len in 1..8 && i + 2 + len <= data.size) {
                    var ns = 0L
                    for (b in 0 until len) {
                        ns = (ns shl 8) or (data[i + 2 + b].toLong() and 0xFF)
                    }
                    val startMs = ns / 1_000_000L

                    // Look ahead within 128 bytes for ChapterDisplay (0x80) -> ChapString (0x85)
                    var title = ""
                    var searchIndex = i + 2 + len
                    val maxSearch = (searchIndex + 128).coerceAtMost(data.size - 2)
                    while (searchIndex < maxSearch) {
                        if (data[searchIndex] == 0x85.toByte()) {
                            val strLen = data[searchIndex + 1].toInt() and 0xFF
                            if (strLen in 1..120 && searchIndex + 2 + strLen <= data.size) {
                                title = String(data, searchIndex + 2, strLen, Charsets.UTF_8)
                            }
                            break
                        }
                        searchIndex++
                    }

                    chapters.add(
                        RawChapter(
                            id = "mkv_ch_${chapters.size}",
                            title = title,
                            startTimeMs = startMs
                        )
                    )
                    i = searchIndex
                    continue
                }
            }
            i++
        }

        return chapters
    }

    /**
     * Fast scan for MP4 chpl (Chapter List) atom in QuickTime/MP4 containers
     */
    private fun extractMp4Chapters(stream: InputStream): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        val buffer = ByteArray(64 * 1024)
        var totalRead = 0
        val bos = java.io.ByteArrayOutputStream(256 * 1024)

        while (totalRead < MAX_CONTAINER_SCAN_BYTES) {
            val count = stream.read(buffer)
            if (count <= 0) break
            bos.write(buffer, 0, count)
            totalRead += count
        }

        val data = bos.toByteArray()
        var i = 0
        while (i < data.size - 12) {
            // Look for 'chpl' fourcc
            if (data[i] == 'c'.code.toByte() && data[i + 1] == 'h'.code.toByte() &&
                data[i + 2] == 'p'.code.toByte() && data[i + 3] == 'l'.code.toByte()
            ) {
                // 'chpl' atom format: 4 bytes version+flags, 4 bytes chapter count (or 1 byte reserved + 4 bytes count)
                val chapterCount = data[i + 8].toInt() and 0xFF
                if (chapterCount in 1..200) {
                    var offset = i + 9
                    for (c in 0 until chapterCount) {
                        if (offset + 9 > data.size) break
                        // 8-byte timestamp in 10,000,000 timescale (QuickTime timescale)
                        var timeValue = 0L
                        for (b in 0 until 8) {
                            timeValue = (timeValue shl 8) or (data[offset + b].toLong() and 0xFF)
                        }
                        val startMs = timeValue / 10_000L
                        offset += 8

                        val titleLen = data[offset].toInt() and 0xFF
                        offset += 1
                        val title = if (titleLen > 0 && offset + titleLen <= data.size) {
                            String(data, offset, titleLen, Charsets.UTF_8)
                        } else "Chapter ${c + 1}"
                        offset += titleLen

                        chapters.add(
                            RawChapter(
                                id = "mp4_ch_$c",
                                title = title,
                                startTimeMs = startMs
                            )
                        )
                    }
                }
                break
            }
            i++
        }

        return chapters
    }
}
