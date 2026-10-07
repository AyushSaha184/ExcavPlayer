package com.excavplayer.player

import android.content.Context
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.ChapterType
import com.excavplayer.domain.model.MediaChapter
import com.excavplayer.player.chapters.ChapterExtractor
import com.excavplayer.player.chapters.RawChapter
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class ChapterExtractorTest {

    private lateinit var extractor: ChapterExtractor
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockLogger = mockk<AppLogger>(relaxed = true)

    @Before
    fun setUp() {
        extractor = ChapterExtractor(mockContext, mockLogger)
    }

    @Test
    fun testChapterTypeDetection() {
        assertEquals(ChapterType.INTRO, MediaChapter.determineChapterType("Opening Theme (OP)"))
        assertEquals(ChapterType.INTRO, MediaChapter.determineChapterType("Anime Intro"))
        assertEquals(ChapterType.INTRO, MediaChapter.determineChapterType("Prologue"))
        assertEquals(ChapterType.OUTRO, MediaChapter.determineChapterType("Ending Song (ED)"))
        assertEquals(ChapterType.OUTRO, MediaChapter.determineChapterType("Credits / Outro"))
        assertEquals(ChapterType.RECAP, MediaChapter.determineChapterType("Episode Recap"))
        assertEquals(ChapterType.PREVIEW, MediaChapter.determineChapterType("Next Episode Preview"))
        assertEquals(ChapterType.REGULAR, MediaChapter.determineChapterType("Chapter 1: The Encounter"))
    }

    @Test
    fun testTimestampParser() {
        assertEquals(3661500L, extractor.parseTimestampMs("01:01:01.500"))
        assertEquals(65000L, extractor.parseTimestampMs("01:05.000"))
        assertEquals(30000L, extractor.parseTimestampMs("30.000"))
        assertEquals(12345L, extractor.parseTimestampMs("12345"))
    }

    @Test
    fun testSanitizeAndNormalizeFiltersNegativeAndFixesEndTimes() {
        val raw = listOf(
            RawChapter(id = "c1", title = "Chapter 1", startTimeMs = -5000L, endTimeMs = 10000L), // Corrupt negative start
            RawChapter(id = "c2", title = "Chapter 2", startTimeMs = 0L, endTimeMs = 300000L),
            RawChapter(id = "c3", title = "Chapter 3", startTimeMs = 300000L, endTimeMs = -1L), // Invalid negative end time
            RawChapter(id = "c4", title = "Chapter 4", startTimeMs = 600000L, endTimeMs = 500000L) // End before start
        )

        val result = extractor.sanitizeAndNormalize(raw, videoDurationMs = 900000L)

        assertEquals(3, result.size)
        // c2
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(300000L, result[0].endTimeMs)
        // c3 (fixed using c4 start)
        assertEquals(300000L, result[1].startTimeMs)
        assertEquals(600000L, result[1].endTimeMs)
        // c4 (fixed using video duration)
        assertEquals(600000L, result[2].startTimeMs)
        assertEquals(900000L, result[2].endTimeMs)
    }

    @Test
    fun testDeduplicationAndOverlapCorrection() {
        val raw = listOf(
            RawChapter(id = "c1", title = "", startTimeMs = 1000L, endTimeMs = 20000L),
            RawChapter(id = "c2", title = "Intro", startTimeMs = 1200L, endTimeMs = 30000L), // Duplicate start (<500ms)
            RawChapter(id = "c3", title = "Main Content", startTimeMs = 30000L, endTimeMs = 100000L),
            RawChapter(id = "c4", title = "Credits", startTimeMs = 90000L, endTimeMs = 120000L) // Overlaps c3
        )

        val result = extractor.sanitizeAndNormalize(raw, videoDurationMs = 120000L)

        assertEquals(3, result.size)
        // Duplicate merged with title "Intro"
        assertEquals("Intro", result[0].title)
        assertEquals(ChapterType.INTRO, result[0].type)
        assertEquals(1000L, result[0].startTimeMs)
        assertEquals(30000L, result[0].endTimeMs)

        // c3 adjusted so end <= c4 start
        assertEquals(30000L, result[1].startTimeMs)
        assertEquals(90000L, result[1].endTimeMs)

        // c4
        assertEquals(90000L, result[2].startTimeMs)
        assertEquals(120000L, result[2].endTimeMs)
    }

    @Test
    fun testChaptersExtendingBeyondVideoDuration() {
        val raw = listOf(
            RawChapter(id = "c1", title = "Valid", startTimeMs = 0L, endTimeMs = 50000L),
            RawChapter(id = "c2", title = "Extending", startTimeMs = 50000L, endTimeMs = 150000L),
            RawChapter(id = "c3", title = "Beyond", startTimeMs = 120000L, endTimeMs = 180000L)
        )

        val result = extractor.sanitizeAndNormalize(raw, videoDurationMs = 100000L)

        assertEquals(2, result.size)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(50000L, result[0].endTimeMs)

        assertEquals(50000L, result[1].startTimeMs)
        assertEquals(100000L, result[1].endTimeMs) // Capped to duration
    }

    @Test
    fun testTitleSanitizationAndFallback() {
        val raw = listOf(
            RawChapter(id = "c1", title = "   \u0000\u001F  ", startTimeMs = 0L, endTimeMs = 5000L),
            RawChapter(id = "c2", title = "Episode Recap", startTimeMs = 5000L, endTimeMs = 15000L),
            RawChapter(id = "c3", title = "\uFFFD\uFFFD\uFFFD\uFFFD", startTimeMs = 15000L, endTimeMs = 25000L),
            RawChapter(id = "c4", title = "??? ??? - ??", startTimeMs = 25000L, endTimeMs = 35000L),
            RawChapter(id = "c5", title = "? Opening Sequence ?", startTimeMs = 35000L, endTimeMs = 45000L)
        )

        val result = extractor.sanitizeAndNormalize(raw)

        assertEquals(5, result.size)
        assertEquals("Chapter 1", result[0].title)
        assertEquals(ChapterType.REGULAR, result[0].type)

        assertEquals("Episode Recap", result[1].title)
        assertEquals(ChapterType.RECAP, result[1].type)

        // Unicode replacement chars replaced with fallback Chapter 3
        assertEquals("Chapter 3", result[2].title)

        // Only question marks and dashes replaced with fallback Chapter 4
        assertEquals("Chapter 4", result[3].title)

        // Leading/trailing question marks trimmed around valid text
        assertEquals("Opening Sequence", result[4].title)
        assertEquals(ChapterType.INTRO, result[4].type)
    }

    @Test
    fun testExtractVorbisChaptersFromMetadata() {
        val metadata = Metadata(
            VorbisComment("CHAPTER01", "00:00:00.000"),
            VorbisComment("CHAPTER01NAME", "Prologue"),
            VorbisComment("CHAPTER02", "00:01:30.000"),
            VorbisComment("CHAPTER02NAME", "Opening"),
            VorbisComment("CHAPTER03", "00:03:00.000"),
            VorbisComment("CHAPTER03NAME", "Part A")
        )

        val result = extractor.extractFromMetadata(metadata, videoDurationMs = 600000L)

        assertEquals(3, result.size)
        assertEquals("Prologue", result[0].title)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(90000L, result[0].endTimeMs)

        assertEquals("Opening", result[1].title)
        assertEquals(ChapterType.INTRO, result[1].type)
        assertEquals(90000L, result[1].startTimeMs)
        assertEquals(180000L, result[1].endTimeMs)

        assertEquals("Part A", result[2].title)
        assertEquals(180000L, result[2].startTimeMs)
        assertEquals(600000L, result[2].endTimeMs)
    }

    @Test
    fun testEbmlMatroskaChapterParsing() {
        // Construct valid Matroska EBML binary buffer
        val baos = ByteArrayOutputStream()

        // 1. EBML Header (0x1A45DFA3), size 0
        baos.write(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x80.toByte()))

        // 2. Segment (0x18538067), size VINT (unknown or length)
        baos.write(byteArrayOf(0x18.toByte(), 0x53.toByte(), 0x80.toByte(), 0x67.toByte(), 0xFF.toByte()))

        // 3. Chapters Master (0x1043A770)
        val chaptersPayload = ByteArrayOutputStream()

        // EditionEntry (0x45B9)
        val editionPayload = ByteArrayOutputStream()

        // ChapterAtom 1: start 0, title "Intro Theme"
        val atom1Payload = ByteArrayOutputStream()
        // ChapterTimeStart (0x91), len 1, val 0
        atom1Payload.write(byteArrayOf(0x91.toByte(), 0x81.toByte(), 0x00.toByte()))
        // ChapterDisplay (0x80)
        val disp1Payload = ByteArrayOutputStream()
        val title1Bytes = "Intro Theme".toByteArray(Charsets.UTF_8)
        disp1Payload.write(0x85) // ChapString
        disp1Payload.write(0x80 or title1Bytes.size)
        disp1Payload.write(title1Bytes)
        val disp1Data = disp1Payload.toByteArray()
        atom1Payload.write(byteArrayOf(0x80.toByte(), (0x80 or disp1Data.size).toByte()))
        atom1Payload.write(disp1Data)

        val atom1Data = atom1Payload.toByteArray()
        editionPayload.write(byteArrayOf(0x73.toByte(), 0xC4.toByte(), (0x80 or atom1Data.size).toByte()))
        editionPayload.write(atom1Data)

        // ChapterAtom 2: start 60,000,000,000 ns (60s), title "Main Part"
        val atom2Payload = ByteArrayOutputStream()
        // ChapterTimeStart (0x91), len 8, val 60,000,000,000L = 0x0000000DF8475800L
        val startNs = 60_000_000_000L
        val nsBytes = ByteArray(8)
        for (b in 0 until 8) {
            nsBytes[b] = ((startNs shr ((7 - b) * 8)) and 0xFF).toByte()
        }
        atom2Payload.write(0x91)
        atom2Payload.write(0x88)
        atom2Payload.write(nsBytes)

        val disp2Payload = ByteArrayOutputStream()
        val title2Bytes = "Main Part".toByteArray(Charsets.UTF_8)
        disp2Payload.write(0x85)
        disp2Payload.write(0x80 or title2Bytes.size)
        disp2Payload.write(title2Bytes)
        val disp2Data = disp2Payload.toByteArray()
        atom2Payload.write(byteArrayOf(0x80.toByte(), (0x80 or disp2Data.size).toByte()))
        atom2Payload.write(disp2Data)

        val atom2Data = atom2Payload.toByteArray()
        editionPayload.write(byteArrayOf(0x73.toByte(), 0xC4.toByte(), (0x80 or atom2Data.size).toByte()))
        editionPayload.write(atom2Data)

        val editionData = editionPayload.toByteArray()
        chaptersPayload.write(byteArrayOf(0x45.toByte(), 0xB9.toByte(), (0x80 or editionData.size).toByte()))
        chaptersPayload.write(editionData)

        val chaptersData = chaptersPayload.toByteArray()
        baos.write(byteArrayOf(0x10.toByte(), 0x43.toByte(), 0xA7.toByte(), 0x70.toByte(), (0x80 or chaptersData.size).toByte()))
        baos.write(chaptersData)

        val rawChapters = extractor.parseEbmlContainer(baos.toByteArray())
        val normalized = extractor.sanitizeAndNormalize(rawChapters, videoDurationMs = 120000L)

        assertEquals(2, normalized.size)
        assertEquals("Intro Theme", normalized[0].title)
        assertEquals(ChapterType.INTRO, normalized[0].type)
        assertEquals(0L, normalized[0].startTimeMs)
        assertEquals(60000L, normalized[0].endTimeMs)

        assertEquals("Main Part", normalized[1].title)
        assertEquals(60000L, normalized[1].startTimeMs)
        assertEquals(120000L, normalized[1].endTimeMs)
    }

    @Test
    fun testEbmlRejectsCorruptRandomBinaryStreamWith0x91Bytes() {
        // Simulate a video stream with random bytes that contain 0x91 and 0x85 repeatedly
        val randomStream = ByteArray(1024 * 64)
        for (i in randomStream.indices) {
            randomStream[i] = when (i % 7) {
                0 -> 0x91.toByte()
                2 -> 0x85.toByte()
                else -> (i and 0xFF).toByte()
            }
        }

        val chapters = extractor.parseEbmlContainer(randomStream)
        // Since randomStream does not have valid EBML Segment/Chapters structure, it MUST NOT extract fake chapters
        assertTrue("Should extract 0 chapters from random binary data", chapters.isEmpty())
    }

    @Test
    fun testMp4ChplBoxParsing() {
        val baos = ByteArrayOutputStream()

        // moov box
        val moovPayload = ByteArrayOutputStream()
        // udta box
        val udtaPayload = ByteArrayOutputStream()
        // chpl box
        val chplPayload = ByteArrayOutputStream()

        // version 0 (1 byte), flags (3 bytes), reserved (1 byte), chapter count (1 byte) = 2
        chplPayload.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x02))

        // Chapter 1: timestamp 0 (8 bytes), title len 8, "Prologue"
        chplPayload.write(ByteArray(8)) // 0L
        val title1 = "Prologue".toByteArray(Charsets.UTF_8)
        chplPayload.write(title1.size)
        chplPayload.write(title1)

        // Chapter 2: timestamp 300_000_000L (30s in 10MHz timescale), title len 6, "Part 1"
        val time2 = 300_000_000L
        val time2Bytes = ByteArray(8)
        for (b in 0 until 8) {
            time2Bytes[b] = ((time2 shr ((7 - b) * 8)) and 0xFF).toByte()
        }
        chplPayload.write(time2Bytes)
        val title2 = "Part 1".toByteArray(Charsets.UTF_8)
        chplPayload.write(title2.size)
        chplPayload.write(title2)

        val chplData = chplPayload.toByteArray()
        val chplBoxSize = chplData.size + 8
        udtaPayload.write(byteArrayOf(
            ((chplBoxSize shr 24) and 0xFF).toByte(),
            ((chplBoxSize shr 16) and 0xFF).toByte(),
            ((chplBoxSize shr 8) and 0xFF).toByte(),
            (chplBoxSize and 0xFF).toByte(),
            'c'.code.toByte(), 'h'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte()
        ))
        udtaPayload.write(chplData)

        val udtaData = udtaPayload.toByteArray()
        val udtaBoxSize = udtaData.size + 8
        moovPayload.write(byteArrayOf(
            ((udtaBoxSize shr 24) and 0xFF).toByte(),
            ((udtaBoxSize shr 16) and 0xFF).toByte(),
            ((udtaBoxSize shr 8) and 0xFF).toByte(),
            (udtaBoxSize and 0xFF).toByte(),
            'u'.code.toByte(), 'd'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()
        ))
        moovPayload.write(udtaData)

        val moovData = moovPayload.toByteArray()
        val moovBoxSize = moovData.size + 8
        baos.write(byteArrayOf(
            ((moovBoxSize shr 24) and 0xFF).toByte(),
            ((moovBoxSize shr 16) and 0xFF).toByte(),
            ((moovBoxSize shr 8) and 0xFF).toByte(),
            (moovBoxSize and 0xFF).toByte(),
            'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()
        ))
        baos.write(moovData)

        val chapters = extractor.parseMp4Boxes(baos.toByteArray())
        val normalized = extractor.sanitizeAndNormalize(chapters, videoDurationMs = 60000L)

        assertEquals(2, normalized.size)
        assertEquals("Prologue", normalized[0].title)
        assertEquals(0L, normalized[0].startTimeMs)
        assertEquals(30000L, normalized[0].endTimeMs)

        assertEquals("Part 1", normalized[1].title)
        assertEquals(30000L, normalized[1].startTimeMs)
        assertEquals(60000L, normalized[1].endTimeMs)
    }
}
