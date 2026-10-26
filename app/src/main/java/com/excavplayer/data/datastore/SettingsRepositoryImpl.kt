package com.excavplayer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : SettingsRepository {

    companion object {
        private const val TAG = "SettingsRepository"
        val KEY_DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_RESUME_THRESHOLD = floatPreferencesKey("resume_threshold")
        val KEY_DEFAULT_REPEAT_MODE = stringPreferencesKey("default_repeat_mode")
        val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val KEY_SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val KEY_PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val KEY_PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val KEY_GESTURE_CONTROLS = booleanPreferencesKey("gesture_controls")
        val KEY_BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
        val KEY_VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
        val KEY_SUBTITLE_TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        val KEY_SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val KEY_SUBTITLE_BACKGROUND_STYLE = stringPreferencesKey("subtitle_background_style")
        val KEY_CONTINUE_WATCHING = booleanPreferencesKey("continue_watching")
        val KEY_DEFAULT_SCREEN_ORIENTATION = stringPreferencesKey("default_screen_orientation")
        val KEY_DEFAULT_MEDIA_FIT = stringPreferencesKey("default_media_fit")
        val KEY_LAST_OPENED_FOLDER = stringPreferencesKey("last_opened_folder")
        val KEY_SUBTITLE_VERTICAL_POS = floatPreferencesKey("subtitle_vertical_pos")
        val KEY_AUTO_RESCAN = booleanPreferencesKey("auto_rescan_on_launch")
        val KEY_HEADSET_DETECTION = booleanPreferencesKey("headset_detection")
        val KEY_STOP_ON_SCREEN_OFF = booleanPreferencesKey("stop_on_screen_off")
        val KEY_HARDWARE_ACCELERATION = booleanPreferencesKey("hardware_acceleration")
        val KEY_HARDWARE_ACCELERATION_MODE = stringPreferencesKey("hardware_acceleration_mode")
        val KEY_DIALOGUE_BOOST = booleanPreferencesKey("dialogue_boost")
        val KEY_MATCH_DISPLAY_REFRESH_RATE = booleanPreferencesKey("match_display_refresh_rate")
    }

    override val userSettings: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                logger.e(TAG, "Error reading settings DataStore preferences", exception)
                emit(emptyPreferences())
            } else {
                logger.e(TAG, "Unexpected error reading DataStore", exception)
                throw exception
            }
        }
        .map { preferences ->
            val hwMode = preferences[KEY_HARDWARE_ACCELERATION_MODE]
                ?: if (preferences[KEY_HARDWARE_ACCELERATION] == false) "Disabled" else "Automatic"

            UserSettings(
                defaultPlaybackSpeed = preferences[KEY_DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
                autoResume = preferences[KEY_AUTO_RESUME] ?: true,
                resumeThresholdPercent = preferences[KEY_RESUME_THRESHOLD] ?: 0.95f,
                defaultRepeatMode = runCatching {
                    RepeatMode.valueOf(preferences[KEY_DEFAULT_REPEAT_MODE] ?: RepeatMode.OFF.name)
                }.getOrDefault(RepeatMode.OFF),
                autoplayNextVideo = preferences[KEY_AUTOPLAY_NEXT] ?: true,
                subtitlesEnabled = preferences[KEY_SUBTITLES_ENABLED] ?: true,
                preferredSubtitleLanguage = preferences[KEY_PREFERRED_SUBTITLE_LANG] ?: "English",
                preferredAudioLanguage = preferences[KEY_PREFERRED_AUDIO_LANG] ?: "English",
                gestureControlsEnabled = preferences[KEY_GESTURE_CONTROLS] ?: true,
                brightnessGestureEnabled = preferences[KEY_BRIGHTNESS_GESTURE] ?: true,
                volumeGestureEnabled = preferences[KEY_VOLUME_GESTURE] ?: true,
                subtitleTextSize = preferences[KEY_SUBTITLE_TEXT_SIZE] ?: "Normal",
                subtitleTextColor = preferences[KEY_SUBTITLE_TEXT_COLOR] ?: "White",
                subtitleBackgroundStyle = preferences[KEY_SUBTITLE_BACKGROUND_STYLE] ?: "Outline",
                continueWatchingEnabled = preferences[KEY_CONTINUE_WATCHING] ?: true,
                defaultScreenOrientation = preferences[KEY_DEFAULT_SCREEN_ORIENTATION] ?: "Auto",
                defaultMediaFit = preferences[KEY_DEFAULT_MEDIA_FIT] ?: "Fit to Screen",
                lastOpenedFolder = preferences[KEY_LAST_OPENED_FOLDER],
                subtitleVerticalPositionPercent = preferences[KEY_SUBTITLE_VERTICAL_POS] ?: 0.90f,
                autoRescanOnLaunch = preferences[KEY_AUTO_RESCAN] ?: true,
                headsetDetectionEnabled = preferences[KEY_HEADSET_DETECTION] ?: true,
                stopOnScreenOff = preferences[KEY_STOP_ON_SCREEN_OFF] ?: true,
                hardwareAccelerationMode = hwMode,
                hardwareAccelerationEnabled = hwMode != "Disabled",
                dialogueBoostEnabled = preferences[KEY_DIALOGUE_BOOST] ?: false,
                matchDisplayRefreshRate = preferences[KEY_MATCH_DISPLAY_REFRESH_RATE] ?: false
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun updatePlaybackSpeed(speed: Float) {
        logger.i(TAG, "updatePlaybackSpeed: $speed")
        edit(KEY_DEFAULT_PLAYBACK_SPEED) { it[KEY_DEFAULT_PLAYBACK_SPEED] = speed }
    }

    override suspend fun updateAutoResume(enabled: Boolean) {
        logger.i(TAG, "updateAutoResume: $enabled")
        edit(KEY_AUTO_RESUME) { it[KEY_AUTO_RESUME] = enabled }
    }

    override suspend fun updateResumeThreshold(threshold: Float) {
        logger.i(TAG, "updateResumeThreshold: $threshold")
        edit(KEY_RESUME_THRESHOLD) { it[KEY_RESUME_THRESHOLD] = threshold }
    }

    override suspend fun updateDefaultRepeatMode(mode: RepeatMode) {
        logger.i(TAG, "updateDefaultRepeatMode: $mode")
        edit(KEY_DEFAULT_REPEAT_MODE) { it[KEY_DEFAULT_REPEAT_MODE] = mode.name }
    }

    override suspend fun updateAutoplayNext(enabled: Boolean) {
        logger.i(TAG, "updateAutoplayNext: $enabled")
        edit(KEY_AUTOPLAY_NEXT) { it[KEY_AUTOPLAY_NEXT] = enabled }
    }

    override suspend fun updateSubtitlesEnabled(enabled: Boolean) {
        logger.i(TAG, "updateSubtitlesEnabled: $enabled")
        edit(KEY_SUBTITLES_ENABLED) { it[KEY_SUBTITLES_ENABLED] = enabled }
    }

    override suspend fun updatePreferredSubtitleLanguage(lang: String?) {
        logger.i(TAG, "updatePreferredSubtitleLanguage: $lang")
        edit(KEY_PREFERRED_SUBTITLE_LANG) {
            if (lang != null) it[KEY_PREFERRED_SUBTITLE_LANG] = lang else it.remove(KEY_PREFERRED_SUBTITLE_LANG)
        }
    }

    override suspend fun updatePreferredAudioLanguage(lang: String?) {
        logger.i(TAG, "updatePreferredAudioLanguage: $lang")
        edit(KEY_PREFERRED_AUDIO_LANG) {
            if (lang != null) it[KEY_PREFERRED_AUDIO_LANG] = lang else it.remove(KEY_PREFERRED_AUDIO_LANG)
        }
    }

    override suspend fun updateGestureControls(enabled: Boolean) {
        logger.i(TAG, "updateGestureControls: $enabled")
        edit(KEY_GESTURE_CONTROLS) { it[KEY_GESTURE_CONTROLS] = enabled }
    }

    override suspend fun updateBrightnessGesture(enabled: Boolean) {
        logger.i(TAG, "updateBrightnessGesture: $enabled")
        edit(KEY_BRIGHTNESS_GESTURE) { it[KEY_BRIGHTNESS_GESTURE] = enabled }
    }

    override suspend fun updateVolumeGesture(enabled: Boolean) {
        logger.i(TAG, "updateVolumeGesture: $enabled")
        edit(KEY_VOLUME_GESTURE) { it[KEY_VOLUME_GESTURE] = enabled }
    }

    override suspend fun updateSubtitleTextSize(size: String) {
        logger.i(TAG, "updateSubtitleTextSize: $size")
        edit(KEY_SUBTITLE_TEXT_SIZE) { it[KEY_SUBTITLE_TEXT_SIZE] = size }
    }

    override suspend fun updateSubtitleTextColor(color: String) {
        logger.i(TAG, "updateSubtitleTextColor: $color")
        edit(KEY_SUBTITLE_TEXT_COLOR) { it[KEY_SUBTITLE_TEXT_COLOR] = color }
    }

    override suspend fun updateSubtitleBackgroundStyle(style: String) {
        logger.i(TAG, "updateSubtitleBackgroundStyle: $style")
        edit(KEY_SUBTITLE_BACKGROUND_STYLE) { it[KEY_SUBTITLE_BACKGROUND_STYLE] = style }
    }

    override suspend fun updateContinueWatching(enabled: Boolean) {
        logger.i(TAG, "updateContinueWatching: $enabled")
        edit(KEY_CONTINUE_WATCHING) { it[KEY_CONTINUE_WATCHING] = enabled }
    }

    override suspend fun updateDefaultScreenOrientation(orientation: String) {
        logger.i(TAG, "updateDefaultScreenOrientation: $orientation")
        edit(KEY_DEFAULT_SCREEN_ORIENTATION) { it[KEY_DEFAULT_SCREEN_ORIENTATION] = orientation }
    }

    override suspend fun updateDefaultMediaFit(fit: String) {
        logger.i(TAG, "updateDefaultMediaFit: $fit")
        edit(KEY_DEFAULT_MEDIA_FIT) { it[KEY_DEFAULT_MEDIA_FIT] = fit }
    }

    override suspend fun updateLastOpenedFolder(folderPath: String?) {
        logger.i(TAG, "updateLastOpenedFolder: $folderPath")
        edit(KEY_LAST_OPENED_FOLDER) {
            if (folderPath != null) it[KEY_LAST_OPENED_FOLDER] = folderPath else it.remove(KEY_LAST_OPENED_FOLDER)
        }
    }

    override suspend fun updateSubtitlePosition(percentY: Float) {
        logger.i(TAG, "updateSubtitlePosition: $percentY")
        edit(KEY_SUBTITLE_VERTICAL_POS) { it[KEY_SUBTITLE_VERTICAL_POS] = percentY }
    }

    override suspend fun updateAutoRescanOnLaunch(enabled: Boolean) {
        logger.i(TAG, "updateAutoRescanOnLaunch: $enabled")
        edit(KEY_AUTO_RESCAN) { it[KEY_AUTO_RESCAN] = enabled }
    }

    override suspend fun updateHeadsetDetection(enabled: Boolean) {
        logger.i(TAG, "updateHeadsetDetection: $enabled")
        edit(KEY_HEADSET_DETECTION) { it[KEY_HEADSET_DETECTION] = enabled }
    }

    override suspend fun updateStopOnScreenOff(enabled: Boolean) {
        logger.i(TAG, "updateStopOnScreenOff: $enabled")
        edit(KEY_STOP_ON_SCREEN_OFF) { it[KEY_STOP_ON_SCREEN_OFF] = enabled }
    }

    override suspend fun updateHardwareAcceleration(enabled: Boolean) {
        logger.i(TAG, "updateHardwareAcceleration: $enabled")
        updateHardwareAccelerationMode(if (enabled) "Automatic" else "Disabled")
    }

    override suspend fun updateHardwareAccelerationMode(mode: String) {
        logger.i(TAG, "updateHardwareAccelerationMode: $mode")
        edit(KEY_HARDWARE_ACCELERATION_MODE) {
            it[KEY_HARDWARE_ACCELERATION_MODE] = mode
            it[KEY_HARDWARE_ACCELERATION] = (mode != "Disabled")
        }
    }

    override suspend fun updateDialogueBoost(enabled: Boolean) {
        logger.i(TAG, "updateDialogueBoost: $enabled")
        edit(KEY_DIALOGUE_BOOST) { it[KEY_DIALOGUE_BOOST] = enabled }
    }

    override suspend fun updateMatchDisplayRefreshRate(enabled: Boolean) {
        logger.i(TAG, "updateMatchDisplayRefreshRate: $enabled")
        edit(KEY_MATCH_DISPLAY_REFRESH_RATE) { it[KEY_MATCH_DISPLAY_REFRESH_RATE] = enabled }
    }

    /**
     * Single DataStore write path. [key] names the primary key being written so
     * that a failed write can be attributed in the log.
     */
    private suspend fun edit(key: Preferences.Key<*>, transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(dispatchers.io) {
            runCatching {
                context.dataStore.edit(transform)
            }.onFailure { e ->
                logger.e(TAG, "Failed to write setting '${key.name}' to DataStore", e)
            }
        }
    }
}
