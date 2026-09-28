package com.excavplayer.player.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlaybackError
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.PlaybackStatus
import com.excavplayer.domain.model.PlayerCommand
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.SettingsRepository
import com.excavplayer.player.playback.PlaybackPersistenceManager
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.player.tracks.TrackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val settingsRepository: SettingsRepository,
    private val persistenceManager: PlaybackPersistenceManager,
    val queue: PlaybackQueue,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : PlayerController {

    companion object {
        private const val TAG = "PlayerManager"
    }

    private val playerScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val trackSelector = DefaultTrackSelector(context)
    private val trackManager = TrackManager()

    override val exoPlayer: ExoPlayer by lazy {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(false) // We manage audio becoming noisy with explicit receiver
            .build().apply {
                addListener(PlayerEventListener())
            }
    }

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val becomingNoisyReceiver = AudioBecomingNoisyReceiver(context, logger) {
        pause()
    }

    init {
        startPositionTicker()
    }

    private fun startPositionTicker() {
        playerScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)

                    _state.update { current ->
                        current.copy(
                            playback = current.playback.copy(
                                currentPositionMs = pos,
                                durationMs = dur,
                                bufferedPositionMs = buffered,
                                isPlaying = true,
                                lastUpdatedTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
                delay(300L)
            }
        }
    }

    override suspend fun play(video: Video, startPositionMs: Long?) = withContext(dispatchers.main) {
        logger.i(TAG, "play() called for video: ${video.id}")
        val settings = settingsRepository.userSettings.first()

        val resumePos = startPositionMs ?: if (settings.autoResume) {
            val savedState = playbackRepository.getPlaybackState(video.id)
            savedState?.currentPositionMs ?: 0L
        } else {
            0L
        }

        val mediaItem = buildMediaItem(video)
        persistenceManager.onSessionStarted()

        _state.update {
            it.copy(
                currentVideo = video,
                error = null,
                playback = it.playback.copy(
                    videoId = video.id,
                    playbackSpeed = settings.defaultPlaybackSpeed,
                    repeatMode = settings.defaultRepeatMode
                )
            )
        }

        exoPlayer.setPlaybackParameters(PlaybackParameters(settings.defaultPlaybackSpeed))
        exoPlayer.setMediaItem(mediaItem)
        if (resumePos > 0) {
            exoPlayer.seekTo(resumePos)
        }
        exoPlayer.prepare()
        exoPlayer.play()

        becomingNoisyReceiver.register()
        persistenceManager.startPeriodicSave(playerScope, { _state.value.playback }, { _state.value.currentVideo })
    }

    private fun buildMediaItem(video: Video, externalSubtitles: List<MediaItem.SubtitleConfiguration> = emptyList()): MediaItem {
        val uri = Uri.parse(video.uri)
        val builder = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(uri)

        if (externalSubtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(externalSubtitles)
        }

        return builder.build()
    }

    override fun pause() {
        logger.d(TAG, "pause()")
        exoPlayer.pause()
        becomingNoisyReceiver.unregister()
        persistenceManager.stopPeriodicSave()
        playerScope.launch {
            persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
        }
    }

    override fun resume() {
        logger.d(TAG, "resume()")
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
        }
        exoPlayer.play()
        becomingNoisyReceiver.register()
        persistenceManager.startPeriodicSave(playerScope, { _state.value.playback }, { _state.value.currentVideo })
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        logger.d(TAG, "seekTo: $target ms")
        exoPlayer.seekTo(target)
        _state.update {
            it.copy(playback = it.playback.copy(currentPositionMs = target))
        }
    }

    override fun seekForward(offsetMs: Long) {
        val current = exoPlayer.currentPosition
        seekTo(current + offsetMs)
    }

    override fun seekBackward(offsetMs: Long) {
        val current = exoPlayer.currentPosition
        seekTo(current - offsetMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        logger.d(TAG, "setPlaybackSpeed: $clampedSpeed")
        exoPlayer.setPlaybackSpeed(clampedSpeed)
        _state.update {
            it.copy(playback = it.playback.copy(playbackSpeed = clampedSpeed))
        }
    }

    override fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clampedVolume
        _state.update {
            it.copy(playback = it.playback.copy(volume = clampedVolume))
        }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        logger.d(TAG, "setRepeatMode: $mode")
        queue.setRepeatMode(mode)
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
        }
        _state.update {
            it.copy(playback = it.playback.copy(repeatMode = mode))
        }
    }

    override fun setShuffle(enabled: Boolean) {
        logger.d(TAG, "setShuffle: $enabled")
        queue.setShuffle(enabled)
        exoPlayer.shuffleModeEnabled = enabled
        _state.update {
            it.copy(playback = it.playback.copy(isShuffleEnabled = enabled))
        }
    }

    override fun selectAudioTrack(trackId: String?) {
        logger.d(TAG, "selectAudioTrack: $trackId")
        trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_AUDIO, trackId)
        _state.update {
            it.copy(playback = it.playback.copy(selectedAudioTrackId = trackId))
        }
    }

    override fun selectSubtitleTrack(trackId: String?) {
        logger.d(TAG, "selectSubtitleTrack: $trackId")
        trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_TEXT, trackId)
        _state.update {
            it.copy(playback = it.playback.copy(selectedSubtitleTrackId = trackId))
        }
    }

    override fun selectVideoTrack(trackId: String?) {
        logger.d(TAG, "selectVideoTrack: $trackId")
        trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_VIDEO, trackId)
        _state.update {
            it.copy(playback = it.playback.copy(selectedVideoTrackId = trackId))
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        _state.update { it.copy(subtitleDelayMs = delayMs) }
    }

    override fun addExternalSubtitle(uri: String, label: String, mimeType: String) {
        val currentVideo = _state.value.currentVideo ?: return
        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val currentPos = exoPlayer.currentPosition
        val newItem = buildMediaItem(currentVideo, listOf(subConfig))
        exoPlayer.setMediaItem(newItem)
        exoPlayer.seekTo(currentPos)
        exoPlayer.prepare()
    }

    override fun stop() {
        logger.d(TAG, "stop()")
        becomingNoisyReceiver.unregister()
        persistenceManager.stopPeriodicSave()
        playerScope.launch {
            persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
        }
        exoPlayer.stop()
    }

    override fun release() {
        logger.d(TAG, "release()")
        becomingNoisyReceiver.unregister()
        persistenceManager.stopPeriodicSave()
        playerScope.launch {
            persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
            exoPlayer.release()
            playerScope.cancel()
        }
    }

    override fun dispatch(command: PlayerCommand) {
        when (command) {
            is PlayerCommand.Play -> playerScope.launch { play(command.video, command.startPositionMs) }
            PlayerCommand.Pause -> pause()
            PlayerCommand.Resume -> resume()
            is PlayerCommand.SeekTo -> seekTo(command.positionMs)
            is PlayerCommand.SeekRelative -> seekForward(command.offsetMs)
            is PlayerCommand.SetSpeed -> setPlaybackSpeed(command.speed)
            is PlayerCommand.SetVolume -> setVolume(command.volume)
            is PlayerCommand.SetRepeatMode -> setRepeatMode(command.mode)
            is PlayerCommand.SetShuffle -> setShuffle(command.enabled)
            is PlayerCommand.SelectAudioTrack -> selectAudioTrack(command.trackId)
            is PlayerCommand.SelectSubtitleTrack -> selectSubtitleTrack(command.trackId)
            is PlayerCommand.SelectVideoTrack -> selectVideoTrack(command.trackId)
            is PlayerCommand.SetSubtitleDelay -> setSubtitleDelay(command.delayMs)
            is PlayerCommand.AddExternalSubtitle -> addExternalSubtitle(command.uri, command.label, command.mimeType)
            PlayerCommand.Stop -> stop()
            PlayerCommand.Release -> release()
            is PlayerCommand.SetFullscreen -> _state.update { it.copy(isFullscreenRequested = command.enabled) }
            is PlayerCommand.SetControlsVisible -> _state.update { it.copy(areControlsVisible = command.visible) }
            is PlayerCommand.SetScreenLocked -> _state.update { it.copy(isScreenLocked = command.locked) }
            is PlayerCommand.SetInPictureInPicture -> _state.update { it.copy(isInPictureInPicture = command.inPip) }
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val status = when (playbackState) {
                Player.STATE_IDLE -> PlaybackStatus.IDLE
                Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.IDLE
            }

            _state.update {
                it.copy(
                    playback = it.playback.copy(
                        playbackStatus = status,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    )
                )
            }

            if (playbackState == Player.STATE_ENDED) {
                becomingNoisyReceiver.unregister()
                persistenceManager.stopPeriodicSave()
                playerScope.launch {
                    persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)

                    // Autoplay next in queue if available
                    val settings = settingsRepository.userSettings.first()
                    if (settings.autoplayNextVideo) {
                        val nextVideo = queue.next()
                        if (nextVideo != null) {
                            play(nextVideo)
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update {
                it.copy(playback = it.playback.copy(isPlaying = isPlaying))
            }
            if (isPlaying) {
                becomingNoisyReceiver.register()
                persistenceManager.startPeriodicSave(playerScope, { _state.value.playback }, { _state.value.currentVideo })
            } else {
                becomingNoisyReceiver.unregister()
                persistenceManager.stopPeriodicSave()
                playerScope.launch {
                    persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audio = trackManager.extractAudioTracks(tracks)
            val video = trackManager.extractVideoTracks(tracks)
            val subs = trackManager.extractSubtitleTracks(tracks)

            val selectedAudio = audio.find { it.isSelected }?.id
            val selectedVideo = video.find { it.isSelected }?.id
            val selectedSubs = subs.find { it.isSelected }?.id

            _state.update {
                it.copy(
                    availableAudioTracks = audio,
                    availableVideoTracks = video,
                    availableSubtitleTracks = subs,
                    playback = it.playback.copy(
                        selectedAudioTrackId = selectedAudio,
                        selectedVideoTrackId = selectedVideo,
                        selectedSubtitleTrackId = selectedSubs
                    )
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val mappedError = PlayerErrorMapper.map(error)
            logger.e(TAG, "Playback error: ${mappedError.message}", error)
            _state.update { it.copy(error = mappedError) }
            becomingNoisyReceiver.unregister()
            persistenceManager.stopPeriodicSave()
            playerScope.launch {
                persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
            }
        }
    }
}
