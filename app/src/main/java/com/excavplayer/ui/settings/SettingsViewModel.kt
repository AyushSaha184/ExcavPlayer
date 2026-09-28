package com.excavplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.library.VideoLibrary
import com.excavplayer.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val videoLibrary: VideoLibrary
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun toggleAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAutoResume(enabled)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsManager.setPlaybackSpeed(speed)
        }
    }

    fun setDefaultRepeatMode(mode: RepeatMode) {
        viewModelScope.launch {
            settingsManager.setDefaultRepeatMode(mode)
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSubtitlesEnabled(enabled)
        }
    }

    fun setPreferredSubtitleLanguage(lang: String?) {
        viewModelScope.launch {
            settingsManager.setPreferredSubtitleLanguage(lang)
        }
    }

    fun setPreferredAudioLanguage(lang: String?) {
        viewModelScope.launch {
            settingsManager.setPreferredAudioLanguage(lang)
        }
    }

    fun toggleGestureControls(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setGestureControls(enabled)
        }
    }

    fun rescanMedia() {
        viewModelScope.launch {
            _isScanning.value = true
            when (val result = videoLibrary.refresh()) {
                is ExcavResult.Success -> {
                    _syncMessage.value = "Synced ${result.data} media files"
                }
                is ExcavResult.Error -> {
                    _syncMessage.value = "Scan failed: ${result.message ?: result.exception.message}"
                }
                is ExcavResult.Loading -> {}
            }
            _isScanning.value = false
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
