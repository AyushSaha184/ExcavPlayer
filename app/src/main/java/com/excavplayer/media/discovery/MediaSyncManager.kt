package com.excavplayer.media.discovery

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.media.MediaStoreDataSource
import com.excavplayer.domain.model.MediaAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Syncing(val itemsScanned: Int) : SyncStatus
    data class Success(val totalScanned: Int, val updatedCount: Int) : SyncStatus
    data class Error(val throwable: Throwable) : SyncStatus
}

@Singleton
class MediaSyncManager @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val videoDao: VideoDao,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "MediaSyncManager"
    }

    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun syncMediaStore(): ExcavResult<Int> = withContext(dispatchers.io) {
        if (!syncMutex.tryLock()) {
            logger.d(TAG, "Sync already in progress, skipping concurrent request")
            return@withContext ExcavResult.Success(0)
        }

        try {
            _syncStatus.value = SyncStatus.Syncing(0)
            logger.i(TAG, "Starting MediaStore synchronization")

            val existingIds = videoDao.getAllVideoIds().toHashSet()
            val discoveredIds = HashSet<String>()
            var totalCount = 0

            mediaStoreDataSource.queryAllVideosPaged(batchSize = 250) { batch ->
                videoDao.insertVideos(batch)
                batch.forEach { discoveredIds.add(it.id) }
                totalCount += batch.size
                _syncStatus.value = SyncStatus.Syncing(totalCount)
            }

            // Detect deleted / missing videos from MediaStore
            val removedIds = existingIds.filter { id ->
                id.startsWith("ms_") && !discoveredIds.contains(id)
            }

            if (removedIds.isNotEmpty()) {
                logger.i(TAG, "Marking ${removedIds.size} videos as UNAVAILABLE")
                videoDao.markUnavailable(removedIds)
            }

            _syncStatus.value = SyncStatus.Success(totalScanned = totalCount, updatedCount = discoveredIds.size)
            logger.i(TAG, "Sync complete: $totalCount items discovered, ${removedIds.size} marked unavailable")
            ExcavResult.Success(totalCount)
        } catch (e: Exception) {
            logger.e(TAG, "Error synchronizing media library", e)
            _syncStatus.value = SyncStatus.Error(e)
            ExcavResult.Error(e)
        } finally {
            syncMutex.unlock()
        }
    }
}
