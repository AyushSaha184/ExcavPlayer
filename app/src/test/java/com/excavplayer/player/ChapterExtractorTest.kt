package com.excavplayer.player

import com.excavplayer.domain.model.ChapterType
import com.excavplayer.domain.model.MediaChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterExtractorTest {

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
}
