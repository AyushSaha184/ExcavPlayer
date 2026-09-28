package com.excavplayer.subtitles

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subtitlePreferenceDao: SubtitlePreferenceDao,
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

    suspend fun savePreference(
        videoId: String,
        trackId: String?,
        delayMs: Long = 0L,
        externalUri: String? = null
    ) = withContext(dispatchers.io) {
        val entity = SubtitlePreferenceEntity(
            videoId = videoId,
            preferredSubtitleTrackId = trackId,
            subtitleDelayMs = delayMs,
            externalSubtitleUri = externalUri
        )
        subtitlePreferenceDao.upsertPreferences(entity)
        logger.d(TAG, "Saved subtitle preferences for video: $videoId")
    }

    suspend fun getPreference(videoId: String): SubtitlePreferenceEntity? = withContext(dispatchers.io) {
        subtitlePreferenceDao.getPreferences(videoId)
    }
}
