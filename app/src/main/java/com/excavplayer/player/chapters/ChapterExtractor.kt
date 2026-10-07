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
import java.nio.charset.StandardCharsets
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
        private const val MAX_CONTAINER_SCAN_BYTES = 16 * 1024 * 1024 // 16 MB max header scan limit
        private const val MAX_CHAPTERS_LIMIT = 300
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
     * Sanitizes titles and eliminates unicode replacement characters, excessive question marks,
     * or corrupted binary string remnants.
     */
    fun sanitizeTitle(rawTitle: String, fallbackIndex: Int): String {
        var cleaned = rawTitle
            .replace("\uFFFD", "") // Unicode replacement character ()
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Control characters
            .trim()
            .replace(Regex("\\s+"), " ")

        val meaningfulChars = cleaned.count { it.isLetterOrDigit() }
        if (cleaned.isBlank() || meaningfulChars == 0) {
            return "Chapter $fallbackIndex"
        }

        // Clean stray leading/trailing question marks, hyphens, colons
        cleaned = cleaned.trim('?', ' ', '-', '_', ':', '.')
        if (cleaned.isBlank() || cleaned.count { it.isLetterOrDigit() } == 0) {
            return "Chapter $fallbackIndex"
        }

        return cleaned
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
        val validStart = rawChapters
            .filter { it.startTimeMs >= 0L }
            .take(MAX_CHAPTERS_LIMIT)
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
                // Merge duplicate: prefer explicit named title over generic "Chapter X" or empty
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
        return magic.size >= 4 &&
                (magic[0] == 0x1A.toByte() && magic[1] == 0x45.toByte() &&
                 magic[2] == 0xDF.toByte() && magic[3] == 0xA3.toByte())
    }

    private fun isMp4(magic: ByteArray): Boolean {
        if (magic.size < 8) return false
        val type = String(magic, 4, 4, StandardCharsets.US_ASCII)
        return type == "ftyp" || type == "moov" || type == "free" || type == "mdat"
    }

    // -----------------------------------------------------------------------------------------
    // Proper Hierarchical EBML Matroska Chapters Parser
    // -----------------------------------------------------------------------------------------

    private fun extractMatroskaChapters(stream: InputStream): List<RawChapter> {
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
        if (data.size < 8) return emptyList()

        return parseEbmlContainer(data)
    }

    fun parseEbmlContainer(data: ByteArray): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        val reader = EbmlBufferReader(data)

        var seekChaptersOffset: Long? = null
        var segmentDataStartOffset: Int = 0

        while (reader.hasRemaining()) {
            val elementId = reader.readElementId() ?: break
            val elementSize = reader.readElementSize() ?: break
            val currentPos = reader.position

            when (elementId) {
                0x1A45DFA3L -> { // EBML Header - skip payload
                    if (elementSize > 0) reader.skip(elementSize)
                }
                0x18538067L -> { // Segment - Master element, descend into it!
                    segmentDataStartOffset = currentPos
                    val segmentEnd = if (elementSize >= 0 && currentPos + elementSize <= data.size) {
                        (currentPos + elementSize).toInt()
                    } else data.size

                    parseSegmentChildren(reader, segmentEnd, chapters, data, segmentDataStartOffset) { foundOffset ->
                        seekChaptersOffset = foundOffset
                    }
                    break
                }
                else -> {
                    if (elementSize > 0) reader.skip(elementSize) else break
                }
            }
        }

        // If chapters weren't inline before clusters, but SeekHead found its offset
        if (chapters.isEmpty() && seekChaptersOffset != null) {
            val absoluteChaptersPos = segmentDataStartOffset + seekChaptersOffset!!.toInt()
            if (absoluteChaptersPos in 0 until (data.size - 4)) {
                reader.position = absoluteChaptersPos
                val id = reader.readElementId()
                val size = reader.readElementSize()
                if (id == 0x1043A770L && size != null) {
                    val end = if (size >= 0 && reader.position + size <= data.size) {
                        (reader.position + size).toInt()
                    } else data.size
                    parseChaptersMaster(reader, end, chapters, data)
                }
            }
        }

        return chapters
    }

    private fun parseSegmentChildren(
        reader: EbmlBufferReader,
        segmentEnd: Int,
        chapters: MutableList<RawChapter>,
        data: ByteArray,
        segmentDataStart: Int,
        onSeekChaptersFound: (Long) -> Unit
    ) {
        while (reader.position < segmentEnd) {
            val elementId = reader.readElementId() ?: break
            val elementSize = reader.readElementSize() ?: break
            val elementStart = reader.position

            val end = if (elementSize >= 0 && elementStart + elementSize <= segmentEnd) {
                (elementStart + elementSize).toInt()
            } else segmentEnd

            when (elementId) {
                0x114D9B74L -> { // SeekHead - parse Seek entries to find Chapters
                    parseSeekHead(reader, end, onSeekChaptersFound)
                }
                0x1043A770L -> { // Chapters - Master Element!
                    parseChaptersMaster(reader, end, chapters, data)
                    reader.position = end
                }
                0x1F43B675L -> { // Cluster - Media packets! STOP or skip; NEVER search raw cluster bytes!
                    if (elementSize >= 0 && elementStart + elementSize <= segmentEnd) {
                        reader.position = end
                    } else {
                        // Cluster with unknown size / reaches end: stop segment scanning
                        break
                    }
                }
                else -> {
                    // Skip other segment children (Info, Tracks, Cues, Tags, etc.)
                    if (elementSize >= 0) {
                        reader.position = end
                    } else break
                }
            }
        }
    }

    private fun parseSeekHead(
        reader: EbmlBufferReader,
        seekHeadEnd: Int,
        onSeekChaptersFound: (Long) -> Unit
    ) {
        while (reader.position < seekHeadEnd) {
            val id = reader.readElementId() ?: break
            val size = reader.readElementSize() ?: break
            val end = (reader.position + size).toInt().coerceAtMost(seekHeadEnd)

            if (id == 0x4DBBL) { // Seek Master
                var seekId: Long? = null
                var seekPos: Long? = null
                while (reader.position < end) {
                    val subId = reader.readElementId() ?: break
                    val subSize = reader.readElementSize() ?: break
                    when (subId) {
                        0x53ABL -> seekId = reader.readUint(subSize) // SeekID
                        0x53ACL -> seekPos = reader.readUint(subSize) // SeekPosition
                        else -> reader.skip(subSize)
                    }
                }
                if (seekId == 0x1043A770L && seekPos != null) {
                    onSeekChaptersFound(seekPos)
                }
            } else {
                reader.position = end
            }
        }
    }

    private fun parseChaptersMaster(
        reader: EbmlBufferReader,
        chaptersEnd: Int,
        chapters: MutableList<RawChapter>,
        data: ByteArray
    ) {
        while (reader.position < chaptersEnd) {
            val id = reader.readElementId() ?: break
            val size = reader.readElementSize() ?: break
            val end = (reader.position + size).toInt().coerceAtMost(chaptersEnd)

            if (id == 0x45B9L) { // EditionEntry Master
                parseEditionEntry(reader, end, chapters, data)
            } else {
                reader.position = end
            }
        }
    }

    private fun parseEditionEntry(
        reader: EbmlBufferReader,
        editionEnd: Int,
        chapters: MutableList<RawChapter>,
        data: ByteArray
    ) {
        while (reader.position < editionEnd) {
            val id = reader.readElementId() ?: break
            val size = reader.readElementSize() ?: break
            val end = (reader.position + size).toInt().coerceAtMost(editionEnd)

            if (id == 0x73C4L) { // ChapterAtom Master
                parseChapterAtom(reader, end, chapters, data)
            } else {
                reader.position = end
            }
        }
    }

    private fun parseChapterAtom(
        reader: EbmlBufferReader,
        atomEnd: Int,
        chapters: MutableList<RawChapter>,
        data: ByteArray
    ) {
        var startNs: Long? = null
        var endNs: Long? = null
        var title = ""
        var uid = ""

        while (reader.position < atomEnd) {
            val id = reader.readElementId() ?: break
            val size = reader.readElementSize() ?: break
            val end = (reader.position + size).toInt().coerceAtMost(atomEnd)

            when (id) {
                0x7373L -> { // ChapterUID
                    uid = reader.readUint(size)?.toString() ?: ""
                }
                0x91L -> { // ChapterTimeStart (uint ns)
                    startNs = reader.readUint(size)
                }
                0x92L -> { // ChapterTimeEnd (uint ns)
                    endNs = reader.readUint(size)
                }
                0x80L -> { // ChapterDisplay Master
                    while (reader.position < end) {
                        val dispId = reader.readElementId() ?: break
                        val dispSize = reader.readElementSize() ?: break
                        when (dispId) {
                            0x85L -> { // ChapString (UTF-8 String)
                                title = reader.readUtf8String(dispSize)
                            }
                            else -> reader.skip(dispSize)
                        }
                    }
                }
                0x73C4L -> { // Nested ChapterAtom
                    parseChapterAtom(reader, end, chapters, data)
                }
                else -> {
                    reader.position = end
                }
            }
        }

        if (startNs != null) {
            val startMs = startNs / 1_000_000L
            val endMs = endNs?.let { it / 1_000_000L } ?: -1L
            chapters.add(
                RawChapter(
                    id = if (uid.isNotBlank()) "mkv_ch_$uid" else "mkv_ch_${chapters.size}",
                    title = title,
                    startTimeMs = startMs,
                    endTimeMs = endMs
                )
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // MP4 / MOV Box Chapters Parser
    // -----------------------------------------------------------------------------------------

    private fun extractMp4Chapters(stream: InputStream): List<RawChapter> {
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
        if (data.size < 8) return emptyList()

        return parseMp4Boxes(data)
    }

    fun parseMp4Boxes(data: ByteArray): List<RawChapter> {
        val chapters = mutableListOf<RawChapter>()
        var offset = 0

        while (offset + 8 <= data.size) {
            val boxSizeLong = ((data[offset].toLong() and 0xFF) shl 24) or
                              ((data[offset + 1].toLong() and 0xFF) shl 16) or
                              ((data[offset + 2].toLong() and 0xFF) shl 8) or
                              (data[offset + 3].toLong() and 0xFF)
            val boxType = String(data, offset + 4, 4, StandardCharsets.US_ASCII)

            var headerSize = 8
            val boxSize = when (boxSizeLong) {
                1L -> { // 64-bit large box size
                    if (offset + 16 > data.size) break
                    headerSize = 16
                    var size64 = 0L
                    for (b in 0 until 8) {
                        size64 = (size64 shl 8) or (data[offset + 8 + b].toLong() and 0xFF)
                    }
                    size64.coerceAtMost(data.size.toLong() - offset).toInt()
                }
                0L -> data.size - offset
                else -> boxSizeLong.coerceAtMost(data.size.toLong() - offset).toInt()
            }

            if (boxSize < headerSize) break

            val boxEnd = offset + boxSize
            if (boxType == "moov") {
                parseMoovBox(data, offset + headerSize, boxEnd, chapters)
                break // Chapters are inside moov
            }

            offset = boxEnd
        }

        return chapters
    }

    private fun parseMoovBox(
        data: ByteArray,
        startOffset: Int,
        endOffset: Int,
        chapters: MutableList<RawChapter>
    ) {
        var offset = startOffset
        while (offset + 8 <= endOffset) {
            val boxSize = readBoxSize(data, offset, endOffset) ?: break
            val boxType = String(data, offset + 4, 4, StandardCharsets.US_ASCII)
            val boxEnd = offset + boxSize

            if (boxType == "udta") {
                parseUdtaBox(data, offset + 8, boxEnd, chapters)
                if (chapters.isNotEmpty()) return
            }

            offset = boxEnd
        }
    }

    private fun parseUdtaBox(
        data: ByteArray,
        startOffset: Int,
        endOffset: Int,
        chapters: MutableList<RawChapter>
    ) {
        var offset = startOffset
        while (offset + 8 <= endOffset) {
            val boxSize = readBoxSize(data, offset, endOffset) ?: break
            val boxType = String(data, offset + 4, 4, StandardCharsets.US_ASCII)
            val boxEnd = offset + boxSize

            if (boxType == "chpl") {
                parseChplAtom(data, offset + 8, boxEnd, chapters)
                return
            }

            offset = boxEnd
        }
    }

    private fun parseChplAtom(
        data: ByteArray,
        payloadStart: Int,
        payloadEnd: Int,
        chapters: MutableList<RawChapter>
    ) {
        if (payloadEnd - payloadStart < 5) return
        var offset = payloadStart
        val version = data[offset].toInt() and 0xFF
        offset += 4 // 1 byte version + 3 bytes flags

        val chapterCount: Int
        if (version == 0) {
            if (offset + 2 > payloadEnd) return
            offset += 1 // 1 byte reserved
            chapterCount = data[offset].toInt() and 0xFF
            offset += 1
        } else {
            if (offset + 8 > payloadEnd) return
            offset += 4 // 4 bytes reserved
            chapterCount = (((data[offset].toInt() and 0xFF) shl 24) or
                           ((data[offset + 1].toInt() and 0xFF) shl 16) or
                           ((data[offset + 2].toInt() and 0xFF) shl 8) or
                           (data[offset + 3].toInt() and 0xFF)).coerceAtLeast(0)
            offset += 4
        }

        if (chapterCount in 1..MAX_CHAPTERS_LIMIT) {
            for (c in 0 until chapterCount) {
                if (offset + 9 > payloadEnd) break
                // 8 bytes QuickTime timescale timestamp (10,000,000 units/sec)
                var timeValue = 0L
                for (b in 0 until 8) {
                    timeValue = (timeValue shl 8) or (data[offset + b].toLong() and 0xFF)
                }
                val startMs = timeValue / 10_000L
                offset += 8

                val titleLen = data[offset].toInt() and 0xFF
                offset += 1
                val title = if (titleLen > 0 && offset + titleLen <= payloadEnd) {
                    String(data, offset, titleLen, StandardCharsets.UTF_8)
                } else ""
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
    }

    private fun readBoxSize(data: ByteArray, offset: Int, maxEnd: Int): Int? {
        if (offset + 8 > maxEnd) return null
        val size = ((data[offset].toLong() and 0xFF) shl 24) or
                   ((data[offset + 1].toLong() and 0xFF) shl 16) or
                   ((data[offset + 2].toLong() and 0xFF) shl 8) or
                   (data[offset + 3].toLong() and 0xFF)
        if (size < 8) return null
        return size.coerceAtMost((maxEnd - offset).toLong()).toInt()
    }
}

/**
 * Lightweight memory reader for EBML variable-length integers and elements.
 */
class EbmlBufferReader(private val data: ByteArray) {
    var position: Int = 0

    fun hasRemaining(): Boolean = position < data.size

    fun skip(bytes: Long) {
        position = (position + bytes.toInt()).coerceAtMost(data.size)
    }

    /**
     * Reads EBML Variable-Length Integer (VINT) for Element ID. Preserves length indicator bit.
     */
    fun readElementId(): Long? {
        if (!hasRemaining()) return null
        val firstByte = data[position].toInt() and 0xFF
        val numBytes = vintLength(firstByte) ?: return null
        if (position + numBytes > data.size) return null

        var id = 0L
        for (i in 0 until numBytes) {
            id = (id shl 8) or (data[position + i].toLong() and 0xFF)
        }
        position += numBytes
        return id
    }

    /**
     * Reads EBML VINT for Data Size. Strips length indicator bit. Returns -1 for unknown size.
     */
    fun readElementSize(): Long? {
        if (!hasRemaining()) return null
        val firstByte = data[position].toInt() and 0xFF
        val numBytes = vintLength(firstByte) ?: return null
        if (position + numBytes > data.size) return null

        val mask = (0xFF shr numBytes)
        var size = (firstByte and mask).toLong()
        for (i in 1 until numBytes) {
            size = (size shl 8) or (data[position + i].toLong() and 0xFF)
        }
        position += numBytes

        val isUnknown = when (numBytes) {
            1 -> size == 0x7FL
            2 -> size == 0x3FFFL
            3 -> size == 0x1FFFFFL
            4 -> size == 0x0FFFFFFFL
            8 -> size == 0x00FFFFFFFFFFFFFFL
            else -> false
        }
        return if (isUnknown) -1L else size
    }

    fun readUint(size: Long): Long? {
        if (size <= 0 || position + size > data.size) return null
        var value = 0L
        for (i in 0 until size.toInt()) {
            value = (value shl 8) or (data[position + i].toLong() and 0xFF)
        }
        position += size.toInt()
        return value
    }

    fun readUtf8String(size: Long): String {
        if (size <= 0 || position + size > data.size) return ""
        val str = String(data, position, size.toInt(), StandardCharsets.UTF_8)
        position += size.toInt()
        return str
    }

    private fun vintLength(firstByte: Int): Int? {
        if (firstByte == 0) return null
        return when {
            (firstByte and 0x80) != 0 -> 1
            (firstByte and 0x40) != 0 -> 2
            (firstByte and 0x20) != 0 -> 3
            (firstByte and 0x10) != 0 -> 4
            (firstByte and 0x08) != 0 -> 5
            (firstByte and 0x04) != 0 -> 6
            (firstByte and 0x02) != 0 -> 7
            (firstByte and 0x01) != 0 -> 8
            else -> null
        }
    }
}
