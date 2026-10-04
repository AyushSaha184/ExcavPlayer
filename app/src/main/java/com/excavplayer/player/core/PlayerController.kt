package com.excavplayer.player.core

import androidx.media3.common.Player
import com.excavplayer.domain.model.PlaybackPosition
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val state: StateFlow<PlayerState>
    val playbackPosition: StateFlow<PlaybackPosition>
    val exoPlayer: Player

    suspend fun play(video: Video, startPositionMs: Long? = null)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun selectAudioTrack(trackId: String?)
    fun selectSubtitleTrack(trackId: String?)
    fun selectVideoTrack(trackId: String?)
    fun setSubtitleDelay(delayMs: Long)
    fun addExternalSubtitle(uri: String, label: String, mimeType: String)
    fun stop()
    fun release()
    fun startBackgroundPlay()
    fun stopBackgroundPlay()
    fun dispatch(command: PlayerCommand)
}
