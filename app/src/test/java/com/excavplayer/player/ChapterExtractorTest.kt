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

    @Test
    fun testMatroskaSeekHeadAtLargeOffsetExtractsChapters() {
        val headerBaos = ByteArrayOutputStream()

        // 1. EBML Header (0x1A45DFA3), size 0
        headerBaos.write(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x80.toByte()))

        // 2. Segment (0x18538067), unknown size (0xFF)
        headerBaos.write(byteArrayOf(0x18.toByte(), 0x53.toByte(), 0x80.toByte(), 0x67.toByte(), 0xFF.toByte()))

        // Segment data starts right here.
        // SeekHead (0x114D9B74)
        val seekHeadPayload = ByteArrayOutputStream()
        // Seek entry (0x4DBB)
        val seekEntryPayload = ByteArrayOutputStream()
        // SeekID (0x53AB) = Chapters ID (0x1043A770)
        seekEntryPayload.write(byteArrayOf(0x53.toByte(), 0xAB.toByte(), 0x84.toByte(), 0x10.toByte(), 0x43.toByte(), 0xA7.toByte(), 0x70.toByte()))
        // SeekPosition (0x53AC) = 25,000,000L (25 MB offset relative to start of Segment data, well past 16MB!)
        val targetSeekPos = 25_000_000L
        val posBytes = ByteArray(4)
        for (b in 0 until 4) {
            posBytes[b] = ((targetSeekPos shr ((3 - b) * 8)) and 0xFF).toByte()
        }
        seekEntryPayload.write(byteArrayOf(0x53.toByte(), 0xAC.toByte(), 0x84.toByte()))
        seekEntryPayload.write(posBytes)

        val seekEntryBytes = seekEntryPayload.toByteArray()
        seekHeadPayload.write(byteArrayOf(0x4D.toByte(), 0xBB.toByte(), (0x80 or seekEntryBytes.size).toByte()))
        seekHeadPayload.write(seekEntryBytes)

        val seekHeadBytes = seekHeadPayload.toByteArray()
        headerBaos.write(byteArrayOf(0x11.toByte(), 0x4D.toByte(), 0x9B.toByte(), 0x74.toByte(), (0x80 or seekHeadBytes.size).toByte()))
        headerBaos.write(seekHeadBytes)

        val headerBytes = headerBaos.toByteArray()
        val segmentDataStartOffset = 10L // 5 bytes EBML header + 5 bytes Segment ID & len

        // Prepare Chapters Master (0x1043A770) at target offset
        val chapBaos = ByteArrayOutputStream()
        val editionPayload = ByteArrayOutputStream()
        val atomPayload = ByteArrayOutputStream()
        // ChapterTimeStart (0x91) = 0
        atomPayload.write(byteArrayOf(0x91.toByte(), 0x81.toByte(), 0x00.toByte()))
        // ChapterDisplay (0x80)
        val dispPayload = ByteArrayOutputStream()
        val titleBytes = "Deep Chapter".toByteArray(Charsets.UTF_8)
        dispPayload.write(0x85)
        dispPayload.write(0x80 or titleBytes.size)
        dispPayload.write(titleBytes)
        val dispBytes = dispPayload.toByteArray()
        atomPayload.write(byteArrayOf(0x80.toByte(), (0x80 or dispBytes.size).toByte()))
        atomPayload.write(dispBytes)

        val atomBytes = atomPayload.toByteArray()
        editionPayload.write(byteArrayOf(0x73.toByte(), 0xC4.toByte(), (0x80 or atomBytes.size).toByte()))
        editionPayload.write(atomBytes)

        val editionBytes = editionPayload.toByteArray()
        val chapPayload = ByteArrayOutputStream()
        chapPayload.write(byteArrayOf(0x45.toByte(), 0xB9.toByte(), (0x80 or editionBytes.size).toByte()))
        chapPayload.write(editionBytes)

        val chapPayloadBytes = chapPayload.toByteArray()
        chapBaos.write(byteArrayOf(0x10.toByte(), 0x43.toByte(), 0xA7.toByte(), 0x70.toByte(), (0x80 or chapPayloadBytes.size).toByte()))
        chapBaos.write(chapPayloadBytes)
        val chapBytes = chapBaos.toByteArray()

        val chaptersAbsolutePos = segmentDataStartOffset + targetSeekPos
        val sparseSource = object : com.excavplayer.player.chapters.SeekableSource {
            override val size: Long = chaptersAbsolutePos + chapBytes.size + 1000L
            override var position: Long = 0L

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= size) return -1
                if (position in 0 until headerBytes.size) {
                    val toCopy = (headerBytes.size - position.toInt()).coerceAtMost(length)
                    System.arraycopy(headerBytes, position.toInt(), buffer, offset, toCopy)
                    position += toCopy
                    return toCopy
                }
                if (position in chaptersAbsolutePos until (chaptersAbsolutePos + chapBytes.size)) {
                    val chunkOffset = (position - chaptersAbsolutePos).toInt()
                    val toCopy = (chapBytes.size - chunkOffset).coerceAtMost(length)
                    System.arraycopy(chapBytes, chunkOffset, buffer, offset, toCopy)
                    position += toCopy
                    return toCopy
                }
                // Simulate skipped data
                val nextMark = if (position < chaptersAbsolutePos) chaptersAbsolutePos else size
                val bytesToRead = (nextMark - position).coerceAtMost(length.toLong()).toInt()
                buffer.fill(0, offset, offset + bytesToRead)
                position += bytesToRead
                return bytesToRead
            }

            override fun close() {}
        }

        val result = extractor.extractFromSeekable(sparseSource, videoDurationMs = 120_000L)
        assertEquals(1, result.size)
        assertEquals("Deep Chapter", result[0].title)
        assertEquals(0L, result[0].startTimeMs)
    }

    @Test
    fun testMp4WithMoovAtEndOfFileAfterLargeMdatExtractsChapters() {
        val ftypBaos = ByteArrayOutputStream()
        // ftyp box (size 20)
        ftypBaos.write(byteArrayOf(0x00, 0x00, 0x00, 0x14))
        ftypBaos.write("ftypisom".toByteArray(Charsets.US_ASCII))
        ftypBaos.write(byteArrayOf(0x00, 0x00, 0x02, 0x00))
        ftypBaos.write("mp41".toByteArray(Charsets.US_ASCII))
        val ftypBytes = ftypBaos.toByteArray()

        // Large mdat box: header is 8 bytes, size is 40 MB (40_000_008 bytes)
        val mdatSize = 40_000_008L
        val mdatHeaderBaos = ByteArrayOutputStream()
        mdatHeaderBaos.write(byteArrayOf(
            ((mdatSize shr 24) and 0xFF).toByte(),
            ((mdatSize shr 16) and 0xFF).toByte(),
            ((mdatSize shr 8) and 0xFF).toByte(),
            (mdatSize and 0xFF).toByte()
        ))
        mdatHeaderBaos.write("mdat".toByteArray(Charsets.US_ASCII))
        val mdatHeaderBytes = mdatHeaderBaos.toByteArray()

        // moov box at offset = ftypBytes.size + mdatSize
        val moovPayload = ByteArrayOutputStream()
        val udtaPayload = ByteArrayOutputStream()
        val chplPayload = ByteArrayOutputStream()

        chplPayload.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01)) // 1 chapter
        chplPayload.write(ByteArray(8)) // 0L timestamp
        val title = "Late Moov Chapter".toByteArray(Charsets.UTF_8)
        chplPayload.write(title.size)
        chplPayload.write(title)
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
        val moovHeaderBaos = ByteArrayOutputStream()
        moovHeaderBaos.write(byteArrayOf(
            ((moovBoxSize shr 24) and 0xFF).toByte(),
            ((moovBoxSize shr 16) and 0xFF).toByte(),
            ((moovBoxSize shr 8) and 0xFF).toByte(),
            (moovBoxSize and 0xFF).toByte(),
            'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()
        ))
        moovHeaderBaos.write(moovData)
        val moovBytes = moovHeaderBaos.toByteArray()

        val moovOffset = ftypBytes.size + mdatSize
        val totalFileSize = moovOffset + moovBytes.size

        val sparseMp4 = object : com.excavplayer.player.chapters.SeekableSource {
            override val size: Long = totalFileSize
            override var position: Long = 0L

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= size) return -1
                if (position in 0 until ftypBytes.size) {
                    val toCopy = (ftypBytes.size - position.toInt()).coerceAtMost(length)
                    System.arraycopy(ftypBytes, position.toInt(), buffer, offset, toCopy)
                    position += toCopy
                    return toCopy
                }
                if (position in ftypBytes.size until (ftypBytes.size + mdatHeaderBytes.size)) {
                    val rel = (position - ftypBytes.size).toInt()
                    val toCopy = (mdatHeaderBytes.size - rel).coerceAtMost(length)
                    System.arraycopy(mdatHeaderBytes, rel, buffer, offset, toCopy)
                    position += toCopy
                    return toCopy
                }
                if (position in moovOffset until (moovOffset + moovBytes.size)) {
                    val rel = (position - moovOffset).toInt()
                    val toCopy = (moovBytes.size - rel).coerceAtMost(length)
                    System.arraycopy(moovBytes, rel, buffer, offset, toCopy)
                    position += toCopy
                    return toCopy
                }
                // Simulate mdat body bytes
                val nextMark = if (position < moovOffset) moovOffset else size
                val bytesToRead = (nextMark - position).coerceAtMost(length.toLong()).toInt()
                buffer.fill(0, offset, offset + bytesToRead)
                position += bytesToRead
                return bytesToRead
            }

            override fun close() {}
        }

        val chapters = extractor.extractFromSeekable(sparseMp4, videoDurationMs = 60_000L)
        assertEquals(1, chapters.size)
        assertEquals("Late Moov Chapter", chapters[0].title)
        assertEquals(0L, chapters[0].startTimeMs)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(
            ((size shr 24) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        ))
        baos.write(type.toByteArray(Charsets.US_ASCII))
        baos.write(payload)
        return baos.toByteArray()
    }

    @Test
    fun testMp4AppleQuickTimeChapterTrackExtractsChapters() {
        val textSample1 = byteArrayOf(0, 5) + "Intro".toByteArray(Charsets.UTF_8)
        val textSample2 = byteArrayOf(0, 9) + "Main Part".toByteArray(Charsets.UTF_8)
        val samplesData = textSample1 + textSample2

        // Assemble stbl
        val mdhdPayload = ByteArrayOutputStream()
        mdhdPayload.write(byteArrayOf(0, 0, 0, 0)) // version/flags
        mdhdPayload.write(ByteArray(8)) // creation & mod time
        mdhdPayload.write(byteArrayOf(0x00, 0x00, 0x03, 0xE8.toByte())) // timescale = 1000
        mdhdPayload.write(byteArrayOf(0x00, 0x00, 0xEA.toByte(), 0x60)) // duration = 60000
        mdhdPayload.write(ByteArray(4)) // lang & quality
        val mdhdBox = box("mdhd", mdhdPayload.toByteArray())

        val hdlrPayload = ByteArrayOutputStream()
        hdlrPayload.write(byteArrayOf(0, 0, 0, 0))
        hdlrPayload.write(ByteArray(4))
        hdlrPayload.write("text".toByteArray(Charsets.US_ASCII))
        hdlrPayload.write(ByteArray(12))
        hdlrPayload.write("Chapters\u0000".toByteArray(Charsets.UTF_8))
        val hdlrBox = box("hdlr", hdlrPayload.toByteArray())

        // stts: 2 samples, 20s and 40s
        val sttsPayload = ByteArrayOutputStream()
        sttsPayload.write(byteArrayOf(0, 0, 0, 0)) // version/flags
        sttsPayload.write(byteArrayOf(0, 0, 0, 2)) // 2 entries
        // entry 1: 1 sample, delta 20,000 ms
        sttsPayload.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0x4E, 0x20))
        // entry 2: 1 sample, delta 40,000 ms
        sttsPayload.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0x9C.toByte(), 0x40))
        val sttsBox = box("stts", sttsPayload.toByteArray())

        // stsc: 1 chunk containing 2 samples
        val stscPayload = ByteArrayOutputStream()
        stscPayload.write(byteArrayOf(0, 0, 0, 0))
        stscPayload.write(byteArrayOf(0, 0, 0, 1)) // 1 entry
        stscPayload.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1))
        val stscBox = box("stsc", stscPayload.toByteArray())

        // stsz: sample sizes (7 and 11)
        val stszPayload = ByteArrayOutputStream()
        stszPayload.write(byteArrayOf(0, 0, 0, 0))
        stszPayload.write(byteArrayOf(0, 0, 0, 0)) // sample_size = 0 (variable)
        stszPayload.write(byteArrayOf(0, 0, 0, 2)) // count = 2
        stszPayload.write(byteArrayOf(0, 0, 0, textSample1.size.toByte()))
        stszPayload.write(byteArrayOf(0, 0, 0, textSample2.size.toByte()))
        val stszBox = box("stsz", stszPayload.toByteArray())

        // We will place samplesData inside an mdat box at offset 40
        val sampleChunkOffset = 40L
        val stcoPayload = ByteArrayOutputStream()
        stcoPayload.write(byteArrayOf(0, 0, 0, 0))
        stcoPayload.write(byteArrayOf(0, 0, 0, 1)) // 1 chunk
        stcoPayload.write(byteArrayOf(
            ((sampleChunkOffset shr 24) and 0xFF).toByte(),
            ((sampleChunkOffset shr 16) and 0xFF).toByte(),
            ((sampleChunkOffset shr 8) and 0xFF).toByte(),
            (sampleChunkOffset and 0xFF).toByte()
        ))
        val stcoBox = box("stco", stcoPayload.toByteArray())

        val stblBox = box("stbl", sttsBox + stscBox + stszBox + stcoBox)
        val minfBox = box("minf", stblBox)
        val mdiaBox = box("mdia", mdhdBox + hdlrBox + minfBox)
        val trakBox = box("trak", mdiaBox)
        val moovBox = box("moov", trakBox)

        val fullFile = ByteArrayOutputStream()
        // Offset 0: ftyp (32 bytes)
        val ftypPayload = "ftypisom".toByteArray(Charsets.US_ASCII) + ByteArray(16)
        fullFile.write(box("ftyp", ftypPayload))
        // Offset 32: mdat (8 bytes header + samplesData) -> payload is at offset 40
        fullFile.write(box("mdat", samplesData))
        // Then moovBox
        fullFile.write(moovBox)

        val source = com.excavplayer.player.chapters.ByteArraySeekableSource(fullFile.toByteArray())
        val chapters = extractor.extractFromSeekable(source, videoDurationMs = 60_000L)

        assertEquals(2, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].startTimeMs)
        assertEquals(20_000L, chapters[0].endTimeMs)

        assertEquals("Main Part", chapters[1].title)
        assertEquals(20_000L, chapters[1].startTimeMs)
        assertEquals(60_000L, chapters[1].endTimeMs)
    }

    @Test
    fun testParseSidecarYouTubeFormat() {
        val text = """
            00:00 Intro
            01:45 Opening Song (OP)
            14:20 Midpoint Discussion
            22:00 Ending (ED)
        """.trimIndent()

        val chapters = extractor.parseSidecarChapters(text, videoDurationMs = 1500_000L)
        assertEquals(4, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].startTimeMs)
        assertEquals(105_000L, chapters[0].endTimeMs)
        assertEquals(ChapterType.INTRO, chapters[0].type)

        assertEquals("Opening Song (OP)", chapters[1].title)
        assertEquals(105_000L, chapters[1].startTimeMs)
        assertEquals(ChapterType.INTRO, chapters[1].type)

        assertEquals("Midpoint Discussion", chapters[2].title)
        assertEquals(860_000L, chapters[2].startTimeMs)

        assertEquals("Ending (ED)", chapters[3].title)
        assertEquals(1320_000L, chapters[3].startTimeMs)
        assertEquals(1500_000L, chapters[3].endTimeMs)
        assertEquals(ChapterType.OUTRO, chapters[3].type)
    }

    @Test
    fun testParseSidecarVorbisFormat() {
        val text = """
            CHAPTER01=00:00:00.000
            CHAPTER01NAME=Prologue
            CHAPTER02=00:01:30.000
            CHAPTER02NAME=Opening
        """.trimIndent()

        val chapters = extractor.parseSidecarChapters(text, videoDurationMs = 300_000L)
        assertEquals(2, chapters.size)
        assertEquals("Prologue", chapters[0].title)
        assertEquals(0L, chapters[0].startTimeMs)
        assertEquals(90_000L, chapters[0].endTimeMs)

        assertEquals("Opening", chapters[1].title)
        assertEquals(90_000L, chapters[1].startTimeMs)
        assertEquals(300_000L, chapters[1].endTimeMs)
    }

    @Test
    fun testExtractFromUriWithLocalFileAndSidecar() = kotlinx.coroutines.test.runTest {
        val tempVideo = java.io.File.createTempFile("sample_video", ".mp4")
        tempVideo.deleteOnExit()
        val sidecarFile = java.io.File(tempVideo.parentFile, "${tempVideo.nameWithoutExtension}.chapters.txt")
        sidecarFile.writeText("00:00 Intro\n01:30 Main Content")
        sidecarFile.deleteOnExit()

        val chapters = extractor.extractFromUri(tempVideo.absolutePath, videoDurationMs = 180_000L)
        assertEquals(2, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].startTimeMs)
        assertEquals(90_000L, chapters[0].endTimeMs)
        assertEquals("Main Content", chapters[1].title)
        assertEquals(90_000L, chapters[1].startTimeMs)
        assertEquals(180_000L, chapters[1].endTimeMs)
    }
}





