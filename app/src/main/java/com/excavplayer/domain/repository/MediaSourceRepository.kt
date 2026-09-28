package com.excavplayer.domain.repository

import com.excavplayer.domain.model.MediaSource
import kotlinx.coroutines.flow.Flow

interface MediaSourceRepository {
    fun observeMediaSources(): Flow<List<MediaSource>>
    suspend fun registerMediaSource(source: MediaSource)
    suspend fun unregisterMediaSource(sourceId: String)
    suspend fun validateSources(): List<MediaSource>
}
