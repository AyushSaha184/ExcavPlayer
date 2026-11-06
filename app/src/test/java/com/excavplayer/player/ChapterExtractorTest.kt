package com.excavplayer.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.ChapterType
import com.excavplayer.domain.model.MediaChapter
import com.excavplayer.player.chapters.ChapterExtractor
import com.excavplayer.player.chapters.RawChapter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

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
            RawChapter(id = "c1", title = "Chapter 1", startTimeMs = -5000L, endTimeMs = 10000L),
            RawChapter(id = "c2", title = "Chapter 2", startTimeMs = 0L, endTimeMs = 300000L),
            RawChapter(id = "c3", title = "Chapter 3", startTimeMs = 300000L, endTimeMs = -1L),
            RawChapter(id = "c4", title = "Chapter 4", startTimeMs = 600000L, endTimeMs = 500000L)
        )

        val result = extractor.sanitizeAndNormalize(raw, videoDurationMs = 900000L)

        assertEquals(3, result.size)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(300000L, result[0].endTimeMs)
        assertEquals(300000L, result[1].startTimeMs)
        assertEquals(600000L, result[1].endTimeMs)
        assertEquals(600000L, result[2].startTimeMs)
        assertEquals(900000L, result[2].endTimeMs)
    }

    @Test
    fun testDeduplicationAndOverlapCorrection() {
        val raw = listOf(
            RawChapter(id = "c1", title = "", startTimeMs = 1000L, endTimeMs = 20000L),
            RawChapter(id = "c2", title = "Intro", startTimeMs = 1200L, endTimeMs = 30000L),
            RawChapter(id = "c3", title = "Main Content", startTimeMs = 30000L, endTimeMs = 100000L),
            RawChapter(id = "c4", title = "Credits", startTimeMs = 90000L, endTimeMs = 120000L)
        )

        val result = extractor.sanitizeAndNormalize(raw, videoDurationMs = 120000L)

        assertEquals(3, result.size)
        assertEquals("Intro", result[0].title)
        assertEquals(ChapterType.INTRO, result[0].type)
        assertEquals(1000L, result[0].startTimeMs)
        assertEquals(30000L, result[0].endTimeMs)

        assertEquals(30000L, result[1].startTimeMs)
        assertEquals(90000L, result[1].endTimeMs)

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
        assertEquals(100000L, result[1].endTimeMs)
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

        assertEquals("Chapter 3", result[2].title)
        assertEquals("Chapter 4", result[3].title)
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
    fun testExtractId3ChaptersFromMetadata() {
        val title = TextInformationFrame("TIT2", null, listOf("Introduction"))
        val chapter = ChapterFrame("intro", 0, 10_000, C.INDEX_UNSET.toLong(), C.INDEX_UNSET.toLong(), arrayOf(title))
        val metadata = Metadata(chapter)

        val result = extractor.extractFromMetadata(metadata, videoDurationMs = 60_000L)
        assertEquals(1, result.size)
        assertEquals("Introduction", result[0].title)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(10_000L, result[0].endTimeMs)
    }

    @Test
    fun testExtractFromTracks() {
        val title = TextInformationFrame("TIT2", null, listOf("Track Intro"))
        val chapter = ChapterFrame("chap1", 0, 15_000, C.INDEX_UNSET.toLong(), C.INDEX_UNSET.toLong(), arrayOf(title))
        val group = Tracks.Group(
            TrackGroup("0", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setMetadata(Metadata(chapter)).build()),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(true)
        )
        val tracks = Tracks(listOf(group))

        val result = extractor.extractFromTracks(tracks, videoDurationMs = 60_000L)
        assertEquals(1, result.size)
        assertEquals("Track Intro", result[0].title)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(15_000L, result[0].endTimeMs)
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
    fun testExtractFromUriWithLocalFileAndSidecar() = runTest {
        val tempVideo = File.createTempFile("sample_video", ".mp4")
        tempVideo.deleteOnExit()
        val sidecarFile = File(tempVideo.parentFile, "${tempVideo.nameWithoutExtension}.chapters.txt")
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
