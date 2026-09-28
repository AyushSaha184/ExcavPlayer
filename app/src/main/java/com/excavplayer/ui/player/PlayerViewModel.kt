package com.excavplayer.ui.player

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.domain.model.Video
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import com.excavplayer.player.playback.PipHelper
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.player.queue.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlayerSheet {
    NONE,
    SPEED,
    AUDIO,
    SUBTITLES,
    QUEUE,
    OPTIONS,
    DETAILS
}

enum class ResizeMode(val label: String) {
    FIT("Fit"),
    ZOOM("Zoom"),
    STRETCH("Stretch")
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val videoLibrary: VideoLibrary,
    private val pipHelper: PipHelper
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerManager.state
    val queueState: StateFlow<QueueState> = playerManager.queue.state

    private val _currentSheet = MutableStateFlow(PlayerSheet.NONE)
    val currentSheet: StateFlow<PlayerSheet> = _currentSheet.asStateFlow()

    private val _resizeMode = MutableStateFlow(ResizeMode.FIT)
    val resizeMode: StateFlow<ResizeMode> = _resizeMode.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked: StateFlow<Boolean> = _isScreenLocked.asStateFlow()

    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _showBrightnessIndicator = MutableStateFlow(false)
    val showBrightnessIndicator: StateFlow<Boolean> = _showBrightnessIndicator.asStateFlow()

    private val _showVolumeIndicator = MutableStateFlow(false)
    val showVolumeIndicator: StateFlow<Boolean> = _showVolumeIndicator.asStateFlow()

    private var autoHideJob: kotlinx.coroutines.Job? = null

    init {
        resetControlsTimeout()
    }

    fun toggleControlsVisibility() {
        if (_isScreenLocked.value) {
            _controlsVisible.value = !_controlsVisible.value
            return
        }
        _controlsVisible.value = !_controlsVisible.value
        if (_controlsVisible.value) {
            resetControlsTimeout()
        }
    }

    fun resetControlsTimeout() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(4500L)
            if (playerState.value.playback.isPlaying && _currentSheet.value == PlayerSheet.NONE) {
                _controlsVisible.value = false
            }
        }
    }

    fun toggleLock() {
        _isScreenLocked.value = !_isScreenLocked.value
        _controlsVisible.value = true
        resetControlsTimeout()
    }

    fun openSheet(sheet: PlayerSheet) {
        _currentSheet.value = sheet
        _controlsVisible.value = false
    }

    fun closeSheet() {
        _currentSheet.value = PlayerSheet.NONE
        _controlsVisible.value = true
        resetControlsTimeout()
    }

    fun togglePlayPause() {
        if (playerManager.exoPlayer.isPlaying) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
        resetControlsTimeout()
    }

    fun seekRelative(offsetMs: Long) {
        if (offsetMs > 0) playerManager.seekForward(offsetMs)
        else playerManager.seekBackward(-offsetMs)
        resetControlsTimeout()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        resetControlsTimeout()
    }

    fun playNext() {
        val next = playerManager.queue.next()
        if (next != null) {
            viewModelScope.launch { playerManager.play(next) }
        }
        resetControlsTimeout()
    }

    fun playPrevious() {
        val prev = playerManager.queue.previous()
        if (prev != null) {
            viewModelScope.launch { playerManager.play(prev) }
        }
        resetControlsTimeout()
    }

    fun setSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(trackId: String?) {
        playerManager.selectAudioTrack(trackId)
    }

    fun selectSubtitleTrack(trackId: String?) {
        playerManager.selectSubtitleTrack(trackId)
    }

    fun setSubtitleDelay(delayMs: Long) {
        playerManager.setSubtitleDelay(delayMs)
    }

    fun addExternalSubtitle(uri: Uri) {
        playerManager.addExternalSubtitle(uri.toString(), "External", "application/x-subrip")
    }

    fun cycleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            ResizeMode.FIT -> ResizeMode.ZOOM
            ResizeMode.ZOOM -> ResizeMode.STRETCH
            ResizeMode.STRETCH -> ResizeMode.FIT
        }
    }

    fun enterPip(activity: Activity) {
        val state = playerState.value
        pipHelper.enterPictureInPicture(activity, state.currentVideo, state.playback.isPlaying)
    }

    fun adjustBrightness(delta: Float) {
        val newBrightness = (_brightness.value + delta).coerceIn(0.01f, 1f)
        _brightness.value = newBrightness
        _showBrightnessIndicator.value = true
        viewModelScope.launch {
            delay(1200L)
            _showBrightnessIndicator.value = false
        }
    }

    fun adjustVolume(delta: Float) {
        val newVolume = (_volume.value + delta).coerceIn(0f, 1f)
        _volume.value = newVolume
        playerManager.setVolume(newVolume)
        _showVolumeIndicator.value = true
        viewModelScope.launch {
            delay(1200L)
            _showVolumeIndicator.value = false
        }
    }

    fun playQueueIndex(index: Int) {
        val video = playerManager.queue.moveTo(index)
        if (video != null) {
            viewModelScope.launch { playerManager.play(video) }
        }
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            videoLibrary.toggleFavorite(videoId)
        }
    }
}
