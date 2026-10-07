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
            RawChapter(id = "c2", title = "Episode Recap", startTimeMs = 5000L, endTimeMs = 15000L)
        )

        val result = extractor.sanitizeAndNormalize(raw)

        assertEquals("Chapter 1", result[0].title)
        assertEquals(ChapterType.REGULAR, result[0].type)

        assertEquals("Episode Recap", result[1].title)
        assertEquals(ChapterType.RECAP, result[1].type)
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
}
