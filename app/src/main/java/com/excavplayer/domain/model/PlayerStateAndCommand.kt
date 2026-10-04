package com.excavplayer.domain.model

data class PlaybackPosition(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
)

data class PlayerState(
    val currentVideo: Video? = null,
    val playback: PlaybackState = PlaybackState(),
    val availableAudioTracks: List<AudioTrack> = emptyList(),
    val availableVideoTracks: List<VideoTrack> = emptyList(),
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
    val chapters: List<MediaChapter> = emptyList(),
    val subtitleDelayMs: Long = 0L,
    val error: PlaybackError? = null,
    val isFullscreenRequested: Boolean = false,
    val isLandscapeRequested: Boolean = false,
    val areControlsVisible: Boolean = true,
    val isScreenLocked: Boolean = false,
    val isInPictureInPicture: Boolean = false,
    val isBackgroundAudio: Boolean = false
)

sealed interface PlayerCommand {
    data class Play(val video: Video, val startPositionMs: Long? = null) : PlayerCommand
    data object Pause : PlayerCommand
    data object Resume : PlayerCommand
    data class SeekTo(val positionMs: Long) : PlayerCommand
    data class SeekRelative(val offsetMs: Long) : PlayerCommand
    data class SetSpeed(val speed: Float) : PlayerCommand
    data class SetVolume(val volume: Float) : PlayerCommand
    data class SetRepeatMode(val mode: RepeatMode) : PlayerCommand
    data class SetShuffle(val enabled: Boolean) : PlayerCommand
    data class SelectAudioTrack(val trackId: String?) : PlayerCommand
    data class SelectSubtitleTrack(val trackId: String?) : PlayerCommand
    data class SelectVideoTrack(val trackId: String?) : PlayerCommand
    data class SetSubtitleDelay(val delayMs: Long) : PlayerCommand
    data class AddExternalSubtitle(val uri: String, val label: String, val mimeType: String) : PlayerCommand
    data object Stop : PlayerCommand
    data object Release : PlayerCommand

    // UI independent layout states
    data class SetFullscreen(val enabled: Boolean) : PlayerCommand
    data class SetControlsVisible(val visible: Boolean) : PlayerCommand
    data class SetScreenLocked(val locked: Boolean) : PlayerCommand
    data class SetInPictureInPicture(val inPip: Boolean) : PlayerCommand
    data class SetBackgroundAudio(val enabled: Boolean) : PlayerCommand
}
