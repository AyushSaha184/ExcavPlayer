package com.excavplayer.data.repository

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.data.database.dao.PlaylistDao
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.mapper.toDomain
import com.excavplayer.domain.model.Playlist
import com.excavplayer.domain.model.PlaylistItem
import com.excavplayer.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val dispatchers: DispatcherProvider
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> {
        return playlistDao.observePlaylistsWithCount()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> {
        return playlistDao.observePlaylist(playlistId)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistDao.observePlaylistItemsWithVideo(playlistId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun createPlaylist(title: String): Long = withContext(dispatchers.io) {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                title = title.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, newTitle: String) = withContext(dispatchers.io) {
        val existing = playlistDao.observePlaylist(playlistId)
        playlistDao.updatePlaylist(
            PlaylistEntity(
                id = playlistId,
                title = newTitle.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(dispatchers.io) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addVideoToPlaylist(playlistId: Long, videoId: String): Boolean = withContext(dispatchers.io) {
        if (playlistDao.hasVideo(playlistId, videoId)) {
            return@withContext false
        }
        val nextOrder = playlistDao.getNextOrderIndex(playlistId)
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                videoId = videoId,
                orderIndex = nextOrder,
                addedAt = System.currentTimeMillis()
            )
        )
        true
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String) = withContext(dispatchers.io) {
        playlistDao.deletePlaylistItem(playlistId, videoId)
    }

    override suspend fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(dispatchers.io) {
        val items = playlistDao.getPlaylistItemsDirect(playlistId).toMutableList()
        if (fromIndex in items.indices && toIndex in items.indices && fromIndex != toIndex) {
            val moved = items.removeAt(fromIndex)
            items.add(toIndex, moved)
            val updated = items.mapIndexed { index, item -> item.copy(orderIndex = index) }
            playlistDao.updatePlaylistItems(updated)
        }
    }
}
