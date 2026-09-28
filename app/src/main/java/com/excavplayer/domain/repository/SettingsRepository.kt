package com.excavplayer.domain.repository

import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userSettings: Flow<UserSettings>
    suspend fun updatePlaybackSpeed(speed: Float)
    suspend fun updateAutoResume(enabled: Boolean)
    suspend fun updateResumeThreshold(threshold: Float)
    suspend fun updateDefaultRepeatMode(mode: RepeatMode)
    suspend fun updateAutoplayNext(enabled: Boolean)
    suspend fun updateSubtitlesEnabled(enabled: Boolean)
    suspend fun updatePreferredSubtitleLanguage(lang: String?)
    suspend fun updatePreferredAudioLanguage(lang: String?)
    suspend fun updateGestureControls(enabled: Boolean)
    suspend fun updateRememberVolume(enabled: Boolean)
    suspend fun updateContinueWatching(enabled: Boolean)
    suspend fun updateHistoryEnabled(enabled: Boolean)
}
