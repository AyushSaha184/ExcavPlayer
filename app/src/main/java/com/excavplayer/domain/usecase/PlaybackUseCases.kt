package com.excavplayer.domain.usecase

import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetPlaybackStateUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(videoId: String): Flow<PlaybackState?> =
        playbackRepository.observePlaybackState(videoId)

    suspend fun getDirect(videoId: String): PlaybackState? =
        playbackRepository.getPlaybackState(videoId)
}

class SavePlaybackStateUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(state: PlaybackState) {
        val settings = settingsRepository.userSettings.first()
        if (settings.autoResume) {
            playbackRepository.savePlaybackState(state)
        }
    }

    suspend fun clear(videoId: String) {
        playbackRepository.clearPlaybackState(videoId)
    }
}

class GetContinueWatchingUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): Flow<List<Video>> {
        val settings = settingsRepository.userSettings.first()
        val threshold = if (settings.continueWatchingEnabled) settings.resumeThresholdPercent else 0f
        return playbackRepository.observeContinueWatching(threshold)
    }
}
