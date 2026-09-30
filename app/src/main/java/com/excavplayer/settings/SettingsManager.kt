package com.excavplayer.settings

import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SettingsManager"
    }

    val settings: Flow<UserSettings> = settingsRepository.userSettings

    suspend fun setPlaybackSpeed(speed: Float) {
        logger.i(TAG, "Setting default playback speed to: $speed")
        settingsRepository.updatePlaybackSpeed(speed)
    }

    suspend fun setAutoResume(enabled: Boolean) {
        logger.i(TAG, "Setting auto resume to: $enabled")
        settingsRepository.updateAutoResume(enabled)
    }

    suspend fun setResumeThreshold(threshold: Float) {
        logger.i(TAG, "Setting resume threshold to: $threshold")
        settingsRepository.updateResumeThreshold(threshold)
    }

    suspend fun setDefaultRepeatMode(mode: RepeatMode) {
        logger.i(TAG, "Setting default repeat mode to: $mode")
        settingsRepository.updateDefaultRepeatMode(mode)
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        logger.i(TAG, "Setting autoplay next to: $enabled")
        settingsRepository.updateAutoplayNext(enabled)
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        logger.i(TAG, "Setting subtitles enabled to: $enabled")
        settingsRepository.updateSubtitlesEnabled(enabled)
    }

    suspend fun setPreferredSubtitleLanguage(lang: String?) {
        logger.i(TAG, "Setting preferred subtitle language to: $lang")
        settingsRepository.updatePreferredSubtitleLanguage(lang)
    }

    suspend fun setPreferredAudioLanguage(lang: String?) {
        logger.i(TAG, "Setting preferred audio language to: $lang")
        settingsRepository.updatePreferredAudioLanguage(lang)
    }

    suspend fun setGestureControls(enabled: Boolean) {
        logger.i(TAG, "Setting gesture controls to: $enabled")
        settingsRepository.updateGestureControls(enabled)
    }

    suspend fun setBrightnessGesture(enabled: Boolean) {
        logger.i(TAG, "Setting brightness gesture to: $enabled")
        settingsRepository.updateBrightnessGesture(enabled)
    }

    suspend fun setVolumeGesture(enabled: Boolean) {
        logger.i(TAG, "Setting volume gesture to: $enabled")
        settingsRepository.updateVolumeGesture(enabled)
    }

    suspend fun setSubtitleTextSize(size: String) {
        logger.i(TAG, "Setting subtitle text size to: $size")
        settingsRepository.updateSubtitleTextSize(size)
    }

    suspend fun setSubtitleTextColor(color: String) {
        logger.i(TAG, "Setting subtitle text color to: $color")
        settingsRepository.updateSubtitleTextColor(color)
    }

    suspend fun setSubtitleBackgroundStyle(style: String) {
        logger.i(TAG, "Setting subtitle background style to: $style")
        settingsRepository.updateSubtitleBackgroundStyle(style)
    }

    suspend fun setRememberVolume(enabled: Boolean) {
        logger.i(TAG, "Setting remember volume to: $enabled")
        settingsRepository.updateRememberVolume(enabled)
    }

    suspend fun setContinueWatching(enabled: Boolean) {
        logger.i(TAG, "Setting continue watching to: $enabled")
        settingsRepository.updateContinueWatching(enabled)
    }

    suspend fun setDefaultScreenOrientation(orientation: String) {
        logger.i(TAG, "Setting default screen orientation to: $orientation")
        settingsRepository.updateDefaultScreenOrientation(orientation)
    }

    suspend fun setDefaultMediaFit(fit: String) {
        logger.i(TAG, "Setting default media fit to: $fit")
        settingsRepository.updateDefaultMediaFit(fit)
    }

    suspend fun setLastOpenedFolder(folderPath: String?) {
        logger.i(TAG, "Setting last opened folder to: $folderPath")
        settingsRepository.updateLastOpenedFolder(folderPath)
    }

    suspend fun setSubtitlePosition(percentY: Float) {
        logger.i(TAG, "Setting subtitle position Y percent to: $percentY")
        settingsRepository.updateSubtitlePosition(percentY)
    }

    suspend fun setAutoRescanOnLaunch(enabled: Boolean) {
        logger.i(TAG, "Setting auto rescan on launch to: $enabled")
        settingsRepository.updateAutoRescanOnLaunch(enabled)
    }

    suspend fun setHeadsetDetection(enabled: Boolean) {
        logger.i(TAG, "Setting headset detection to: $enabled")
        settingsRepository.updateHeadsetDetection(enabled)
    }

    suspend fun setStopOnScreenOff(enabled: Boolean) {
        logger.i(TAG, "Setting stop on screen off to: $enabled")
        settingsRepository.updateStopOnScreenOff(enabled)
    }

    suspend fun setHardwareAcceleration(enabled: Boolean) {
        logger.i(TAG, "Setting hardware acceleration to: $enabled")
        settingsRepository.updateHardwareAcceleration(enabled)
    }
}
