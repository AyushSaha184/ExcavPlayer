package com.excavplayer.player.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.excavplayer.service.PlaybackService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.excavplayer.domain.model.UserSettings
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
import com.excavplayer.player.chapters.ChapterExtractor
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
    private val chapterExtractor: ChapterExtractor,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : PlayerController {

    companion object {
        private const val TAG = "PlayerManager"
    }

    private val playerScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val trackSelector = DefaultTrackSelector(context)
    private val trackManager = TrackManager()

    private var currentSettings = UserSettings()

    override val exoPlayer: ExoPlayer by lazy {
        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            when (currentSettings.hardwareAccelerationMode) {
                "Disabled" -> {
                    // Pure software decoding: prioritize software decoders
                    decoders.sortedBy { it.hardwareAccelerated }
                }
                "Full Acceleration" -> {
                    // Full hardware decoding & direct GPU output
                    decoders.sortedByDescending { it.hardwareAccelerated }
                }
                "Decoding Acceleration" -> {
                    // Hardware accelerated decoding
                    decoders.sortedByDescending { it.hardwareAccelerated }
                }
                else -> { // "Automatic"
                    // Automatic: hardware first with graceful fallback
                    decoders.sortedByDescending { it.hardwareAccelerated }
                }
            }
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(mediaCodecSelector)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(false) // We manage audio becoming noisy with explicit receiver
            .build().apply {
                val strategy = if (currentSettings.matchDisplayRefreshRate) {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                } else {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                }
                setVideoChangeFrameRateStrategy(strategy)
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
        playerScope.launch {
            settingsRepository.userSettings.collect { settings ->
                val prevDialogue = currentSettings.dialogueBoostEnabled
                val prevMatchRate = currentSettings.matchDisplayRefreshRate
                currentSettings = settings
                if (prevDialogue != settings.dialogueBoostEnabled) {
                    applyDialogueBooster(settings.dialogueBoostEnabled)
                }
                if (prevMatchRate != settings.matchDisplayRefreshRate) {
                    applyDisplayRefreshRateOptimization(settings.matchDisplayRefreshRate)
                }
            }
        }
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
                isBackgroundAudio = false,
                chapters = emptyList(),
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

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private fun applyLoudnessEnhancer(volume: Float) {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            try {
                if (loudnessEnhancer == null || loudnessEnhancerSessionId != sessionId) {
                    releaseLoudnessEnhancer()
                    loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
                    loudnessEnhancerSessionId = sessionId
                }
                if (volume > 1.0f) {
                    // Boost volume from 100% to 200% mapped to +0 to +1200 mB (+12dB)
                    val gainMb = ((volume - 1.0f) * 1200f).toInt()
                    loudnessEnhancer?.setTargetGain(gainMb)
                    loudnessEnhancer?.enabled = true
                } else {
                    loudnessEnhancer?.setTargetGain(0)
                    loudnessEnhancer?.enabled = false
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to configure LoudnessEnhancer: ${e.message}")
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            logger.w(TAG, "Failed to release LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
            loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private var equalizer: android.media.audiofx.Equalizer? = null
    private var equalizerSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    fun applyDialogueBooster(enabled: Boolean) {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            try {
                if (equalizer == null || equalizerSessionId != sessionId) {
                    releaseEqualizer()
                    equalizer = android.media.audiofx.Equalizer(0, sessionId)
                    equalizerSessionId = sessionId
                }
                equalizer?.let { eq ->
                    if (enabled) {
                        eq.enabled = true
                        val numBands = eq.numberOfBands
                        for (i in 0 until numBands) {
                            val band = i.toShort()
                            val centerFreq = eq.getCenterFreq(band) // in mHz
                            when {
                                // Boost human vocal speech range (800 Hz - 4.5 kHz)
                                centerFreq in 800_000..4_500_000 -> {
                                    val maxLevel = eq.bandLevelRange.getOrNull(1) ?: 1000
                                    val boost = (maxLevel * 0.55f).toInt().coerceAtMost(maxLevel.toInt()).toShort()
                                    eq.setBandLevel(band, boost)
                                }
                                // Subtle cut on heavy sub-bass (< 250 Hz) to clear up mud/boomy rumble
                                centerFreq < 250_000 -> {
                                    val minLevel = eq.bandLevelRange.getOrNull(0) ?: -1000
                                    val cut = (minLevel * 0.25f).toInt().coerceAtLeast(minLevel.toInt()).toShort()
                                    eq.setBandLevel(band, cut)
                                }
                                else -> {
                                    eq.setBandLevel(band, 0.toShort())
                                }
                            }
                        }
                    } else {
                        val numBands = eq.numberOfBands
                        for (i in 0 until numBands) {
                            eq.setBandLevel(i.toShort(), 0.toShort())
                        }
                        eq.enabled = false
                    }
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to configure Dialogue Booster Equalizer: ${e.message}")
            }
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Exception) {
            logger.w(TAG, "Failed to release Equalizer: ${e.message}")
        } finally {
            equalizer = null
            equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    fun applyDisplayRefreshRateOptimization(enabled: Boolean) {
        try {
            val strategy = if (enabled) {
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            } else {
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
            exoPlayer.setVideoChangeFrameRateStrategy(strategy)
            logger.d(TAG, "Applied video change frame rate strategy: $strategy (enabled=$enabled)")
        } catch (e: Exception) {
            logger.w(TAG, "Failed to apply video change frame rate strategy: ${e.message}")
        }
    }

    override fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 2.0f)
        if (clampedVolume <= 1.0f) {
            exoPlayer.volume = clampedVolume
        } else {
            exoPlayer.volume = 1.0f
        }
        applyLoudnessEnhancer(clampedVolume)
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

    override fun startBackgroundPlay() {
        logger.i(TAG, "startBackgroundPlay()")
        _state.update { it.copy(isBackgroundAudio = true) }
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to start PlaybackService: ${e.message}")
        }
    }

    override fun stopBackgroundPlay() {
        logger.i(TAG, "stopBackgroundPlay()")
        _state.update { it.copy(isBackgroundAudio = false) }
        try {
            context.stopService(Intent(context, PlaybackService::class.java))
        } catch (_: Exception) {}
    }

    override fun stop() {
        logger.d(TAG, "stop()")
        stopBackgroundPlay()
        becomingNoisyReceiver.unregister()
        persistenceManager.stopPeriodicSave()
        releaseLoudnessEnhancer()
        releaseEqualizer()
        playerScope.launch {
            persistenceManager.saveImmediate(_state.value.playback, _state.value.currentVideo)
        }
        exoPlayer.stop()
        _state.update { it.copy(currentVideo = null, isBackgroundAudio = false) }
    }

    override fun release() {
        logger.d(TAG, "release()")
        becomingNoisyReceiver.unregister()
        persistenceManager.stopPeriodicSave()
        releaseLoudnessEnhancer()
        releaseEqualizer()
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
            is PlayerCommand.SetBackgroundAudio -> {
                if (command.enabled) startBackgroundPlay() else stopBackgroundPlay()
            }
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

            if (playbackState == Player.STATE_READY) {
                applyDialogueBooster(currentSettings.dialogueBoostEnabled)
                applyLoudnessEnhancer(_state.value.playback.volume)
                applyDisplayRefreshRateOptimization(currentSettings.matchDisplayRefreshRate)
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

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyDialogueBooster(currentSettings.dialogueBoostEnabled)
            applyLoudnessEnhancer(_state.value.playback.volume)
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

            var selectedAudio = audio.find { it.isSelected }?.id
            val selectedVideo = video.find { it.isSelected }?.id
            var selectedSubs = subs.find { it.isSelected }?.id

            // Match preferred audio language if configured
            currentSettings.preferredAudioLanguage?.let { preferredLang ->
                val matchingAudio = audio.find { track ->
                    matchesLanguage(track.language, track.label, preferredLang)
                }
                if (matchingAudio != null && matchingAudio.id != selectedAudio) {
                    trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_AUDIO, matchingAudio.id)
                    selectedAudio = matchingAudio.id
                }
            }

            // Auto-enable subtitles if enabled in user settings
            if (currentSettings.subtitlesEnabled) {
                if (selectedSubs == null && subs.isNotEmpty()) {
                    val preferredLang = currentSettings.preferredSubtitleLanguage
                    val matchingSub = if (!preferredLang.isNullOrBlank()) {
                        subs.find { track ->
                            matchesLanguage(track.language, track.label, preferredLang)
                        } ?: subs.first()
                    } else {
                        subs.first()
                    }
                    trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_TEXT, matchingSub.id)
                    selectedSubs = matchingSub.id
                }
            } else {
                if (selectedSubs != null) {
                    trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_TEXT, null)
                    selectedSubs = null
                }
            }

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

        private fun matchesLanguage(language: String?, label: String?, preferred: String): Boolean {
            if (preferred.isBlank()) return false
            val pref = preferred.trim().lowercase()
            val lang = language?.trim()?.lowercase()
            val lab = label?.trim()?.lowercase()

            if (lang != null && (lang == pref || lang.startsWith(pref) || pref.startsWith(lang))) return true
            if (lab != null && (lab.contains(pref) || pref.contains(lab))) return true

            val synonyms = when (pref) {
                "english", "en" -> listOf("en", "eng", "english")
                "japanese", "ja" -> listOf("ja", "jpn", "japanese", "jp")
                "spanish", "es" -> listOf("es", "spa", "spanish", "espanol", "español")
                "french", "fr" -> listOf("fr", "fra", "fre", "french", "francais", "français")
                "german", "de" -> listOf("de", "deu", "ger", "german", "deutsch")
                "chinese", "zh" -> listOf("zh", "zho", "chi", "chinese")
                "korean", "ko" -> listOf("ko", "kor", "korean")
                "hindi", "hi" -> listOf("hi", "hin", "hindi")
                "russian", "ru" -> listOf("ru", "rus", "russian")
                "portuguese", "pt" -> listOf("pt", "por", "portuguese")
                "italian", "it" -> listOf("it", "ita", "italian")
                else -> listOf(pref)
            }

            return synonyms.any { s ->
                (lang != null && (lang == s || lang.startsWith(s))) ||
                (lab != null && lab.contains(s))
            }
        }

        override fun onMetadata(metadata: Metadata) {
            val extracted = chapterExtractor.extractFromMetadata(metadata)
            if (extracted.isNotEmpty()) {
                _state.update { current ->
                    val combined = (current.chapters + extracted).distinctBy { it.startTimeMs }.sortedBy { it.startTimeMs }
                    current.copy(chapters = combined)
                }
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
