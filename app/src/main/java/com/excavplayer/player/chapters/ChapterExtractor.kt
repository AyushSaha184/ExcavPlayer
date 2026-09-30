package com.excavplayer.player.chapters

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.ChapterType
import com.excavplayer.domain.model.MediaChapter
import com.excavplayer.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class ChapterExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "ChapterExtractor"
    }

    fun extractFromMetadata(metadata: Metadata): List<MediaChapter> {
        val chapters = mutableListOf<MediaChapter>()

        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is ChapterFrame) {
                var title = entry.chapterId
                // Check if sub-frame has text description
                for (j in 0 until entry.subFrameCount) {
                    val sub = entry.getSubFrame(j)
                    if (sub is TextInformationFrame) {
                        title = sub.values.firstOrNull() ?: entry.chapterId
                        break
                    }
                }
                val startMs = entry.startTimeMs.toLong()
                val endMs = entry.endTimeMs.toLong()
                val chType = MediaChapter.determineChapterType(title)

                chapters.add(
                    MediaChapter(
                        id = "chap_${entry.chapterId}_$i",
                        title = title.ifBlank { "Chapter ${chapters.size + 1}" },
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        type = chType
                    )
                )
            }
        }

        if (chapters.isNotEmpty()) {
            logger.i(TAG, "Extracted ${chapters.size} chapters from ID3 metadata")
        }
        return chapters.sortedBy { it.startTimeMs }
    }
}
