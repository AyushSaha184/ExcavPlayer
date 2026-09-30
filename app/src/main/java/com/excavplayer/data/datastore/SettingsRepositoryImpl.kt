package com.excavplayer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
        val KEY_REMEMBER_VOLUME = booleanPreferencesKey("remember_volume")
        val KEY_CONTINUE_WATCHING = booleanPreferencesKey("continue_watching")
        val KEY_DEFAULT_SCREEN_ORIENTATION = stringPreferencesKey("default_screen_orientation")
        val KEY_DEFAULT_MEDIA_FIT = stringPreferencesKey("default_media_fit")
        val KEY_LAST_OPENED_FOLDER = stringPreferencesKey("last_opened_folder")
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
                preferredAudioLanguage = preferences[KEY_PREFERRED_AUDIO_LANG],
                gestureControlsEnabled = preferences[KEY_GESTURE_CONTROLS] ?: true,
                brightnessGestureEnabled = preferences[KEY_BRIGHTNESS_GESTURE] ?: true,
                volumeGestureEnabled = preferences[KEY_VOLUME_GESTURE] ?: true,
                subtitleTextSize = preferences[KEY_SUBTITLE_TEXT_SIZE] ?: "Normal",
                subtitleTextColor = preferences[KEY_SUBTITLE_TEXT_COLOR] ?: "White",
                subtitleBackgroundStyle = preferences[KEY_SUBTITLE_BACKGROUND_STYLE] ?: "Outline",
                rememberVolume = preferences[KEY_REMEMBER_VOLUME] ?: true,
                continueWatchingEnabled = preferences[KEY_CONTINUE_WATCHING] ?: true,
                defaultScreenOrientation = preferences[KEY_DEFAULT_SCREEN_ORIENTATION] ?: "Auto",
                defaultMediaFit = preferences[KEY_DEFAULT_MEDIA_FIT] ?: "Fit to Screen",
                lastOpenedFolder = preferences[KEY_LAST_OPENED_FOLDER]
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun updatePlaybackSpeed(speed: Float) {
        logger.i(TAG, "updatePlaybackSpeed: $speed")
        edit { it[KEY_DEFAULT_PLAYBACK_SPEED] = speed }
    }

    override suspend fun updateAutoResume(enabled: Boolean) {
        logger.i(TAG, "updateAutoResume: $enabled")
        edit { it[KEY_AUTO_RESUME] = enabled }
    }

    override suspend fun updateResumeThreshold(threshold: Float) {
        logger.i(TAG, "updateResumeThreshold: $threshold")
        edit { it[KEY_RESUME_THRESHOLD] = threshold }
    }

    override suspend fun updateDefaultRepeatMode(mode: RepeatMode) {
        logger.i(TAG, "updateDefaultRepeatMode: $mode")
        edit { it[KEY_DEFAULT_REPEAT_MODE] = mode.name }
    }

    override suspend fun updateAutoplayNext(enabled: Boolean) {
        logger.i(TAG, "updateAutoplayNext: $enabled")
        edit { it[KEY_AUTOPLAY_NEXT] = enabled }
    }

    override suspend fun updateSubtitlesEnabled(enabled: Boolean) {
        logger.i(TAG, "updateSubtitlesEnabled: $enabled")
        edit { it[KEY_SUBTITLES_ENABLED] = enabled }
    }

    override suspend fun updatePreferredSubtitleLanguage(lang: String?) {
        logger.i(TAG, "updatePreferredSubtitleLanguage: $lang")
        edit {
            if (lang != null) it[KEY_PREFERRED_SUBTITLE_LANG] = lang else it.remove(KEY_PREFERRED_SUBTITLE_LANG)
        }
    }

    override suspend fun updatePreferredAudioLanguage(lang: String?) {
        logger.i(TAG, "updatePreferredAudioLanguage: $lang")
        edit {
            if (lang != null) it[KEY_PREFERRED_AUDIO_LANG] = lang else it.remove(KEY_PREFERRED_AUDIO_LANG)
        }
    }

    override suspend fun updateGestureControls(enabled: Boolean) {
        logger.i(TAG, "updateGestureControls: $enabled")
        edit { it[KEY_GESTURE_CONTROLS] = enabled }
    }

    override suspend fun updateBrightnessGesture(enabled: Boolean) {
        logger.i(TAG, "updateBrightnessGesture: $enabled")
        edit { it[KEY_BRIGHTNESS_GESTURE] = enabled }
    }

    override suspend fun updateVolumeGesture(enabled: Boolean) {
        logger.i(TAG, "updateVolumeGesture: $enabled")
        edit { it[KEY_VOLUME_GESTURE] = enabled }
    }

    override suspend fun updateSubtitleTextSize(size: String) {
        logger.i(TAG, "updateSubtitleTextSize: $size")
        edit { it[KEY_SUBTITLE_TEXT_SIZE] = size }
    }

    override suspend fun updateSubtitleTextColor(color: String) {
        logger.i(TAG, "updateSubtitleTextColor: $color")
        edit { it[KEY_SUBTITLE_TEXT_COLOR] = color }
    }

    override suspend fun updateSubtitleBackgroundStyle(style: String) {
        logger.i(TAG, "updateSubtitleBackgroundStyle: $style")
        edit { it[KEY_SUBTITLE_BACKGROUND_STYLE] = style }
    }

    override suspend fun updateRememberVolume(enabled: Boolean) {
        logger.i(TAG, "updateRememberVolume: $enabled")
        edit { it[KEY_REMEMBER_VOLUME] = enabled }
    }

    override suspend fun updateContinueWatching(enabled: Boolean) {
        logger.i(TAG, "updateContinueWatching: $enabled")
        edit { it[KEY_CONTINUE_WATCHING] = enabled }
    }

    override suspend fun updateDefaultScreenOrientation(orientation: String) {
        logger.i(TAG, "updateDefaultScreenOrientation: $orientation")
        edit { it[KEY_DEFAULT_SCREEN_ORIENTATION] = orientation }
    }

    override suspend fun updateDefaultMediaFit(fit: String) {
        logger.i(TAG, "updateDefaultMediaFit: $fit")
        edit { it[KEY_DEFAULT_MEDIA_FIT] = fit }
    }

    override suspend fun updateLastOpenedFolder(folderPath: String?) {
        logger.i(TAG, "updateLastOpenedFolder: $folderPath")
        edit {
            if (folderPath != null) it[KEY_LAST_OPENED_FOLDER] = folderPath else it.remove(KEY_LAST_OPENED_FOLDER)
        }
    }

    private suspend fun edit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(dispatchers.io) {
            runCatching {
                context.dataStore.edit(transform)
            }.onFailure { e ->
                logger.e(TAG, "Failed to update user settings in DataStore", e)
            }
        }
    }
}
