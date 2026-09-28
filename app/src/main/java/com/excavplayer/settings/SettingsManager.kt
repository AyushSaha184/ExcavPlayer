package com.excavplayer.settings

import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    val settings: Flow<UserSettings> = settingsRepository.userSettings

    suspend fun setPlaybackSpeed(speed: Float) = settingsRepository.updatePlaybackSpeed(speed)
    suspend fun setAutoResume(enabled: Boolean) = settingsRepository.updateAutoResume(enabled)
    suspend fun setResumeThreshold(threshold: Float) = settingsRepository.updateResumeThreshold(threshold)
    suspend fun setDefaultRepeatMode(mode: RepeatMode) = settingsRepository.updateDefaultRepeatMode(mode)
    suspend fun setAutoplayNext(enabled: Boolean) = settingsRepository.updateAutoplayNext(enabled)
    suspend fun setSubtitlesEnabled(enabled: Boolean) = settingsRepository.updateSubtitlesEnabled(enabled)
    suspend fun setPreferredSubtitleLanguage(lang: String?) = settingsRepository.updatePreferredSubtitleLanguage(lang)
    suspend fun setPreferredAudioLanguage(lang: String?) = settingsRepository.updatePreferredAudioLanguage(lang)
    suspend fun setGestureControls(enabled: Boolean) = settingsRepository.updateGestureControls(enabled)
    suspend fun setRememberVolume(enabled: Boolean) = settingsRepository.updateRememberVolume(enabled)
    suspend fun setContinueWatching(enabled: Boolean) = settingsRepository.updateContinueWatching(enabled)
    suspend fun setHistoryEnabled(enabled: Boolean) = settingsRepository.updateHistoryEnabled(enabled)
}
