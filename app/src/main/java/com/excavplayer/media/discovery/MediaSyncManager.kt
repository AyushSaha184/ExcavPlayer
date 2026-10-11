package com.excavplayer.media.discovery

import android.net.Uri
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
import java.io.File
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
    private val storageFileScanner: StorageFileScanner,
    private val videoDao: VideoDao,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "MediaSyncManager"
        private const val SYNC_COOLDOWN_MS = 45_000L
    }

    private var lastSyncTimestamp = 0L

    private val syncMutex = Mutex()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun syncMediaStore(force: Boolean = false): ExcavResult<Int> = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTimestamp < SYNC_COOLDOWN_MS)) {
            logger.d(TAG, "Sync skipped: cooldown active (${now - lastSyncTimestamp}ms < ${SYNC_COOLDOWN_MS}ms)")
            return@withContext ExcavResult.Success(0)
        }

        if (!syncMutex.tryLock()) {
            logger.d(TAG, "Sync already in progress, skipping concurrent request")
            return@withContext ExcavResult.Success(0)
        }

        try {
            lastSyncTimestamp = now
            _syncStatus.value = SyncStatus.Syncing(0)
            logger.i(TAG, "Starting MediaStore synchronization (force=$force)")

            val existingIds = videoDao.getAllVideoIds().toHashSet()
            val discoveredIds = HashSet<String>()
            var totalCount = 0

            // 1. MediaStore query
            mediaStoreDataSource.queryAllVideosPaged(batchSize = 250) { batch ->
                videoDao.insertVideos(batch)
                batch.forEach { discoveredIds.add(it.id) }
                totalCount += batch.size
                _syncStatus.value = SyncStatus.Syncing(totalCount)
            }

            // Detect deleted / missing videos from MediaStore (exclude SAF videos)
            val removedIds = existingIds.filter { id ->
                !id.startsWith("saf_") && !discoveredIds.contains(id)
            }

            if (removedIds.isNotEmpty()) {
                logger.i(TAG, "Deleting ${removedIds.size} missing MediaStore/legacy videos from database")
                videoDao.deleteVideos(removedIds)
            }

            // Clean up any legacy filesystem videos from the database
            val existingFsVideos = videoDao.getAllFsVideos()
            if (existingFsVideos.isNotEmpty()) {
                logger.i(TAG, "Purging ${existingFsVideos.size} legacy filesystem videos from database")
                videoDao.deleteVideos(existingFsVideos.map { it.id })
            }

            // 2. Storage file scan: discover unindexed videos and request MediaScanner to index them into MediaStore
            var unindexedScanned = 0
            if (force || existingIds.isEmpty()) {
                val existingKeys = videoDao.getAllVideoKeys().toHashSet()
                val unindexedVideos = storageFileScanner.scanForUnindexedVideos(existingKeys)
                unindexedScanned = unindexedVideos.size
                // Note: scanForUnindexedVideos already calls MediaScannerConnection.scanFile.
                // We do NOT insert raw file:/// URIs into Room, allowing MediaStore to index them cleanly.
            }

            _syncStatus.value = SyncStatus.Success(totalScanned = totalCount, updatedCount = discoveredIds.size)
            logger.i(TAG, "Sync complete: $totalCount items discovered ($unindexedScanned sent to MediaScanner)")
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
