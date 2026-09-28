package com.excavplayer.data.repository

import android.content.Context
import android.net.Uri
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.data.database.mapper.toEntity
import com.excavplayer.domain.model.MediaSource
import com.excavplayer.domain.repository.MediaSourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSourceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaSourceDao: MediaSourceDao,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : MediaSourceRepository {

    companion object {
        private const val TAG = "MediaSourceRepository"
    }

    override fun observeMediaSources(): Flow<List<MediaSource>> {
        return mediaSourceDao.observeSources()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun registerMediaSource(source: MediaSource) = withContext(dispatchers.io) {
        mediaSourceDao.insertSource(source.toEntity())
    }

    override suspend fun unregisterMediaSource(sourceId: String) = withContext(dispatchers.io) {
        mediaSourceDao.deleteSource(sourceId)
    }

    override suspend fun validateSources(): List<MediaSource> = withContext(dispatchers.io) {
        val sources = mediaSourceDao.getAllSources()
        val validated = sources.map { entity ->
            val uri = Uri.parse(entity.uri)
            val isStillAccessible = try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.close()
                true
            } catch (e: Exception) {
                logger.w(TAG, "Source ${entity.id} is no longer accessible: ${e.message}")
                false
            }
            val now = System.currentTimeMillis()
            mediaSourceDao.updateAccessibility(entity.id, isStillAccessible, now)
            entity.toDomain().copy(isAccessible = isStillAccessible, lastValidatedTimestamp = now)
        }
        validated
    }
}
