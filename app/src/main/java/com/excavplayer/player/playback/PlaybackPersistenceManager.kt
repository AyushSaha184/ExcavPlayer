package com.excavplayer.player.playback

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPersistenceManager @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "PlaybackPersistence"
        private const val PERIODIC_SAVE_INTERVAL_MS = 3000L
    }

    private var periodicJob: Job? = null

    fun startPeriodicSave(
        scope: CoroutineScope,
        getCurrentState: () -> PlaybackState,
        getCurrentVideo: () -> Video?
    ) {
        periodicJob?.cancel()
        periodicJob = scope.launch(dispatchers.io) {
            while (isActive) {
                delay(PERIODIC_SAVE_INTERVAL_MS)
                saveProgress(getCurrentState(), getCurrentVideo(), isImmediate = false)
            }
        }
    }

    fun stopPeriodicSave() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun saveImmediate(state: PlaybackState, video: Video?) {
        withContext(dispatchers.io) {
            saveProgress(state, video, isImmediate = true)
        }
    }

    private suspend fun saveProgress(state: PlaybackState, video: Video?, isImmediate: Boolean) {
        val videoId = video?.id ?: state.videoId ?: return
        if (state.durationMs <= 0) return

        val settings = settingsRepository.userSettings.first()
        val currentPos = state.currentPositionMs
        val duration = state.durationMs
        val progressPercent = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val isCompleted = progressPercent >= settings.resumeThresholdPercent

        // Persist Playback State (for AutoResume / Continue Watching)
        if (settings.autoResume && settings.continueWatchingEnabled) {
            if (isCompleted) {
                // If finished, reset position to 0 so next play starts from start
                playbackRepository.savePlaybackState(state.copy(currentPositionMs = 0L, videoId = videoId))
            } else {
                playbackRepository.savePlaybackState(state.copy(videoId = videoId))
            }
        }

        logger.d(TAG, "Saved playback state: pos=$currentPos/${duration}ms ($progressPercent%) immediate=$isImmediate")
    }

    fun onSessionStarted() {
        // Ready for new playback session
    }
}

