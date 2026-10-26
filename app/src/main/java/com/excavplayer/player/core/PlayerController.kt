package com.excavplayer.player.core

import androidx.media3.common.Player
import com.excavplayer.domain.model.PlaybackPosition
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.StateFlow

/**
 * The player's consumer contract: everything the UI, the activity, and the
 * media session need from the playback engine — and nothing else.
 *
 * Screen-lock / fullscreen / controls visibility are UI concerns and live in
 * the UI layer's own state, not here.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>
    val playbackPosition: StateFlow<PlaybackPosition>

    /** The live player instance. First access creates it. */
    fun sessionPlayer(): Player

    /** Fire-and-forget playback start; safe to call from UI callbacks. */
    fun playNow(video: Video, startPositionMs: Long? = null)

    suspend fun play(video: Video, startPositionMs: Long? = null)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun seekForward(offsetMs: Long = 10000L)
    fun seekBackward(offsetMs: Long = 10000L)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun selectAudioTrack(trackId: String?)
    fun selectSubtitleTrack(trackId: String?)
    fun selectVideoTrack(trackId: String?)
    fun setSubtitleDelay(delayMs: Long)
    fun addExternalSubtitle(uri: String, label: String, mimeType: String)
    fun stop()
    fun startBackgroundPlay()
    fun stopBackgroundPlay()
    fun markInPictureInPicture(inPip: Boolean)
    fun setVideoEqualizerEnabled(enabled: Boolean)
    fun setVideoEqualizerPreset(preset: String)
    fun setVideoEqualizerBandLevels(levels: String)
    fun setVideoEqualizerBassStrength(strength: Int)
}
