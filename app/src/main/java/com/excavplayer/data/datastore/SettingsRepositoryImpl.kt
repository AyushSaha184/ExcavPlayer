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
        val KEY_REMEMBER_VOLUME = booleanPreferencesKey("remember_volume")
        val KEY_CONTINUE_WATCHING = booleanPreferencesKey("continue_watching")
        val KEY_HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
    }

    override val userSettings: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                logger.e(TAG, "Error reading settings preferences", exception)
                emit(emptyPreferences())
            } else {
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
                preferredSubtitleLanguage = preferences[KEY_PREFERRED_SUBTITLE_LANG],
                preferredAudioLanguage = preferences[KEY_PREFERRED_AUDIO_LANG],
                gestureControlsEnabled = preferences[KEY_GESTURE_CONTROLS] ?: true,
                rememberVolume = preferences[KEY_REMEMBER_VOLUME] ?: true,
                continueWatchingEnabled = preferences[KEY_CONTINUE_WATCHING] ?: true,
                historyEnabled = preferences[KEY_HISTORY_ENABLED] ?: true
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun updatePlaybackSpeed(speed: Float) = edit { it[KEY_DEFAULT_PLAYBACK_SPEED] = speed }
    override suspend fun updateAutoResume(enabled: Boolean) = edit { it[KEY_AUTO_RESUME] = enabled }
    override suspend fun updateResumeThreshold(threshold: Float) = edit { it[KEY_RESUME_THRESHOLD] = threshold }
    override suspend fun updateDefaultRepeatMode(mode: RepeatMode) = edit { it[KEY_DEFAULT_REPEAT_MODE] = mode.name }
    override suspend fun updateAutoplayNext(enabled: Boolean) = edit { it[KEY_AUTOPLAY_NEXT] = enabled }
    override suspend fun updateSubtitlesEnabled(enabled: Boolean) = edit { it[KEY_SUBTITLES_ENABLED] = enabled }
    override suspend fun updatePreferredSubtitleLanguage(lang: String?) = edit {
        if (lang != null) it[KEY_PREFERRED_SUBTITLE_LANG] = lang else it.remove(KEY_PREFERRED_SUBTITLE_LANG)
    }
    override suspend fun updatePreferredAudioLanguage(lang: String?) = edit {
        if (lang != null) it[KEY_PREFERRED_AUDIO_LANG] = lang else it.remove(KEY_PREFERRED_AUDIO_LANG)
    }
    override suspend fun updateGestureControls(enabled: Boolean) = edit { it[KEY_GESTURE_CONTROLS] = enabled }
    override suspend fun updateRememberVolume(enabled: Boolean) = edit { it[KEY_REMEMBER_VOLUME] = enabled }
    override suspend fun updateContinueWatching(enabled: Boolean) = edit { it[KEY_CONTINUE_WATCHING] = enabled }
    override suspend fun updateHistoryEnabled(enabled: Boolean) = edit { it[KEY_HISTORY_ENABLED] = enabled }

    private suspend fun edit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(dispatchers.io) {
            runCatching {
                context.dataStore.edit(transform)
            }.onFailure { e ->
                logger.e(TAG, "Failed to update user settings", e)
            }
        }
    }
}
