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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import com.excavplayer.domain.model.UserSettings
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.domain.model.PlaybackError
import com.excavplayer.domain.model.PlaybackPosition
import com.excavplayer.domain.model.PlaybackState
import com.excavplayer.domain.model.PlaybackStatus
import com.excavplayer.domain.model.PlayerState
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.SettingsRepository
import com.excavplayer.media.thumbnail.SeekPreviewLoader
import androidx.media3.exoplayer.DefaultLoadControl
import com.excavplayer.player.chapters.ChapterExtractor
import com.excavplayer.player.chapters.RawChapter
import com.excavplayer.player.playback.PlaybackPersistenceManager
import com.excavplayer.player.policy.AutoAdvancePolicy
import com.excavplayer.player.policy.PlaybackFallbackSelection
import com.excavplayer.player.policy.TrackSelectionPolicy
import com.excavplayer.player.queue.PlaybackQueue
import com.excavplayer.player.tracks.TrackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
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
import com.excavplayer.data.database.dao.EqualizerPreferenceDao
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.entity.EqualizerPreferenceEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import com.excavplayer.domain.model.EqualizerState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val settingsRepository: SettingsRepository,
    private val persistenceManager: PlaybackPersistenceManager,
    private val subtitlePreferenceDao: SubtitlePreferenceDao,
    private val equalizerPreferenceDao: EqualizerPreferenceDao,
    val queue: PlaybackQueue,
    private val chapterExtractor: ChapterExtractor,
    private val seekPreviewLoader: SeekPreviewLoader,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) : PlayerController {

    companion object {
        private const val TAG = "PlayerManager"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(TAG, "Unhandled exception in playerScope: ${throwable.message}", throwable)
        _state.update {
            it.copy(
                error = PlaybackError.SourceError(
                    message = "Playback error: ${throwable.localizedMessage ?: throwable.message ?: "Unexpected error"}",
                    cause = throwable,
                    recoverable = false
                )
            )
        }
    }

    private val playerScope = CoroutineScope(SupervisorJob() + dispatchers.main + coroutineExceptionHandler)

    private val trackSelector = DefaultTrackSelector(context)
    private val trackManager = TrackManager()

    private var currentSettings = UserSettings()
    private var userDisabledSubtitlesForSession = false
    private var audioTrackInitializedForSession = false
    private var userSelectedAudioTrackForSession: String? = null
    private var savedAudioPreferenceForVideo: String? = null
    private val activeExternalSubtitles = mutableListOf<MediaItem.SubtitleConfiguration>()

    /** The live player instance. Created on first access (e.g. when playback or the media session needs it). */
    override fun sessionPlayer(): ExoPlayer = exoPlayer

    private val exoPlayer: ExoPlayer by lazy {
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

        val offsetSubtitleParserFactory = OffsetSubtitleParserFactory {
            _state.value.subtitleDelayMs * 1000L
        }
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setSubtitleParserFactory(offsetSubtitleParserFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setSubtitleParserFactory(offsetSubtitleParserFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 8_000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 750
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
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

    private val _playbackPosition = MutableStateFlow(PlaybackPosition())
    override val playbackPosition: StateFlow<PlaybackPosition> = _playbackPosition.asStateFlow()

    private var pausedByHeadset = false

    private val becomingNoisyReceiver = AudioBecomingNoisyReceiver(
        context = context,
        logger = logger,
        onHeadsetDisconnected = {
            if (currentSettings.headsetDetectionEnabled && exoPlayer.isPlaying) {
                logger.i(TAG, "Headset disconnected while playing -> auto pausing")
                pausedByHeadset = true
                exoPlayer.pause()
            }
        },
        onHeadsetConnected = {
            if (currentSettings.headsetDetectionEnabled && pausedByHeadset) {
                logger.i(TAG, "Headset connected after disconnect -> auto resuming")
                pausedByHeadset = false
                if (exoPlayer.playbackState != Player.STATE_ENDED && exoPlayer.playbackState != Player.STATE_IDLE) {
                    exoPlayer.play()
                }
            }
        }
    )

    init {
        startPositionTicker()
        playerScope.launch {
            settingsRepository.userSettings.collect { settings ->
                val prevDialogue = currentSettings.dialogueBoostEnabled
                val prevMatchRate = currentSettings.matchDisplayRefreshRate
                val prevAudioLang = currentSettings.preferredAudioLanguage
                val prevSubLang = currentSettings.preferredSubtitleLanguage
                val prevSubsEnabled = currentSettings.subtitlesEnabled
                currentSettings = settings
                if (prevDialogue != settings.dialogueBoostEnabled) {
                    applyAudioEffects(_state.value.equalizerState)
                }
                if (prevMatchRate != settings.matchDisplayRefreshRate) {
                    applyDisplayRefreshRateOptimization(settings.matchDisplayRefreshRate)
                }
                if (prevAudioLang != settings.preferredAudioLanguage || 
                    prevSubLang != settings.preferredSubtitleLanguage || 
                    prevSubsEnabled != settings.subtitlesEnabled) {
                    applyTrackSelectionPreferences(settings)
                }
            }
        }
    }

    private fun applyTrackSelectionPreferences(settings: UserSettings) {
        try {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            val audioLangs = TrackSelectionPolicy.getLanguageCodes(settings.preferredAudioLanguage)
            builder.setPreferredAudioLanguages(*audioLangs.toTypedArray())
            if (settings.subtitlesEnabled && !settings.preferredSubtitleLanguage.isNullOrBlank()) {
                val textLangs = TrackSelectionPolicy.getLanguageCodes(settings.preferredSubtitleLanguage)
                builder.setPreferredTextLanguages(*textLangs.toTypedArray())
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            exoPlayer.trackSelectionParameters = builder.build()
        } catch (e: Exception) {
            logger.w(TAG, "Failed to apply track selection preferences: ${e.message}")
        }
    }

    private fun startPositionTicker() {
        playerScope.launch {
            var lastSecond = -1L
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    val currentSec = pos / 1000L

                    if (currentSec != lastSecond || dur != _playbackPosition.value.durationMs) {
                        lastSecond = currentSec
                        _playbackPosition.value = PlaybackPosition(
                            currentPositionMs = pos,
                            durationMs = dur,
                            bufferedPositionMs = buffered
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    override suspend fun play(video: Video, startPositionMs: Long?) = withContext(dispatchers.main) {
        try {
            logger.i(TAG, "play() called for video: ${video.id}")
            seekPreviewLoader.releaseRetriever()
            val settings = settingsRepository.userSettings.first()
            currentSettings = settings
            userDisabledSubtitlesForSession = false
            activeExternalSubtitles.clear()
            pausedByHeadset = false
            audioTrackInitializedForSession = false
            userSelectedAudioTrackForSession = null
            val savedPlayback = playbackRepository.getPlaybackState(video.id)
            savedAudioPreferenceForVideo = savedPlayback?.selectedAudioTrackId
            val rawPos = if (!settings.autoResume) {
                0L
            } else {
                startPositionMs ?: savedPlayback?.currentPositionMs ?: 0L
            }

            val duration = video.durationMs
            val thresholdMs = com.excavplayer.domain.policy.PlaybackThresholdPolicy.seekStepMs(settings.resumeThresholdPercent)
            val isCompleted = duration > 0 && (
                rawPos < 3_000L ||
                (duration - rawPos) <= thresholdMs ||
                (rawPos.toFloat() / duration.toFloat()) >= settings.resumeThresholdPercent
            )
            val resumePos = if (isCompleted) 0L else rawPos

            val savedSubPref = runCatching { subtitlePreferenceDao.getPreferences(video.id) }.getOrNull()
            val initialDelay = savedSubPref?.subtitleDelayMs ?: 0L

            if (savedSubPref?.externalSubtitleUri != null) {
                val uriStr = savedSubPref.externalSubtitleUri
                val subUri = runCatching { Uri.parse(uriStr) }.getOrNull()
                var exists = false
                if (subUri != null) {
                    try {
                        if (subUri.scheme == "file" || subUri.scheme == null) {
                            val f = File(subUri.path ?: uriStr)
                            exists = f.exists() && f.length() > 0
                        } else {
                            context.contentResolver.openInputStream(subUri)?.use { exists = true }
                        }
                    } catch (_: Exception) {
                        exists = false
                    }
                }

                if (exists && subUri != null) {
                    val cleanName = subUri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
                    val ext = cleanName.substringAfterLast('.', "").lowercase()
                    val mimeType = when (ext) {
                        "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
                        "ssa", "ass" -> androidx.media3.common.MimeTypes.TEXT_SSA
                        "ttml", "xml" -> androidx.media3.common.MimeTypes.APPLICATION_TTML
                        else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                    }
                    val label = if (cleanName.isNotBlank()) cleanName.substringBeforeLast('.') else "External Subtitle"
                    val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(mimeType)
                        .setLabel(label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    activeExternalSubtitles.add(subConfig)
                } else {
                    // If deleted from storage, silently update database without throwing any warnings
                    playerScope.launch {
                        runCatching {
                            subtitlePreferenceDao.upsertPreferences(
                                savedSubPref.copy(externalSubtitleUri = null)
                            )
                        }
                    }
                }
            }

            val mediaItem = buildMediaItem(video, activeExternalSubtitles.toList())
            persistenceManager.onSessionStarted()

            val savedEqPref = runCatching { equalizerPreferenceDao.getPreferences(video.id) }.getOrNull()
            val initialEqState = if (savedEqPref != null) {
                EqualizerState(
                    enabled = savedEqPref.enabled,
                    preset = savedEqPref.preset,
                    bandLevels = savedEqPref.bandLevels,
                    bassBoostStrength = savedEqPref.bassBoostStrength
                )
            } else {
                EqualizerState(
                    enabled = false,
                    preset = "Custom",
                    bandLevels = "0,0,0,0,0",
                    bassBoostStrength = 0
                )
            }

            _state.update {
                it.copy(
                    currentVideo = video,
                    error = null,
                    isBackgroundAudio = false,
                    isMuted = false,
                    chapters = emptyList(),
                    subtitleDelayMs = initialDelay,
                    equalizerState = initialEqState,
                    playback = it.playback.copy(
                        videoId = video.id,
                        playbackSpeed = settings.defaultPlaybackSpeed,
                        repeatMode = settings.defaultRepeatMode
                    )
                )
            }

            val currentVol = _state.value.playback.volume.coerceIn(0f, 2.0f)
            if (currentVol <= 1.0f) {
                exoPlayer.volume = currentVol
            } else {
                exoPlayer.volume = 1.0f
            }
            applyLoudnessEnhancer(currentVol)

            exoPlayer.setPlaybackParameters(PlaybackParameters(settings.defaultPlaybackSpeed))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            applyTrackSelectionPreferences(settings)
            applyAudioEffects(initialEqState)
            if (resumePos > 0) {
                exoPlayer.seekTo(resumePos)
            }
            exoPlayer.play()

            // Asynchronously extract container-level chapters (MKV EBML, MP4 atoms, sidecars)
            playerScope.launch(dispatchers.io) {
                try {
                    val initialDur = video.durationMs.takeIf { it > 0 } ?: exoPlayer.duration.takeIf { it > 0 }
                    val containerChapters = chapterExtractor.extractFromUri(video.uri, initialDur)
                    if (containerChapters.isNotEmpty() && _state.value.currentVideo?.id == video.id) {
                        _state.update { current ->
                            if (current.currentVideo?.id == video.id) {
                                val activeDur = exoPlayer.duration.takeIf { it > 0 } ?: initialDur
                                val combined = chapterExtractor.sanitizeAndNormalize(
                                    rawChapters = (current.chapters + containerChapters).map {
                                        RawChapter(it.id, it.title, it.startTimeMs, it.endTimeMs)
                                    },
                                    videoDurationMs = activeDur
                                )
                                current.copy(chapters = combined)
                            } else current
                        }
                    }
                } catch (e: Throwable) {
                    logger.w(TAG, "Failed extracting container chapters: ${e.message}")
                }
            }

            becomingNoisyReceiver.register()
            persistenceManager.startPeriodicSave(playerScope, { getLivePlaybackState() }, { _state.value.currentVideo })
        } catch (e: Throwable) {
            logger.e(TAG, "Error starting playback for video: ${video.id} (${video.uri})", e)
            _state.update {
                it.copy(
                    currentVideo = video,
                    error = PlaybackError.SourceError(
                        message = "Cannot open video: ${e.localizedMessage ?: e.message ?: "Playback failed"}",
                        cause = e,
                        recoverable = false
                    )
                )
            }
        }
    }

    private fun getLivePlaybackState(): PlaybackState {
        val pos = runCatching { exoPlayer.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        val dur = runCatching { exoPlayer.duration.coerceAtLeast(0L) }.getOrDefault(0L)
        val buf = runCatching { exoPlayer.bufferedPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        val ticked = _playbackPosition.value

        return _state.value.playback.copy(
            currentPositionMs = PlaybackFallbackSelection.pick(pos, ticked.currentPositionMs),
            durationMs = PlaybackFallbackSelection.pick(dur, ticked.durationMs),
            bufferedPositionMs = PlaybackFallbackSelection.pick(buf, ticked.bufferedPositionMs)
        )
    }

    private fun buildMediaItem(video: Video, externalSubtitles: List<MediaItem.SubtitleConfiguration> = emptyList()): MediaItem {
        var uri = if (video.uri.startsWith("/")) {
            Uri.fromFile(java.io.File(video.uri))
        } else {
            runCatching { Uri.parse(video.uri) }.getOrDefault(Uri.parse(video.uri))
        }

        if (uri.scheme == "file" || (uri.scheme == null && uri.path?.startsWith("/") == true)) {
            val path = uri.path ?: video.uri.removePrefix("file://")
            val decodedPath = Uri.decode(path)
            val file = java.io.File(decodedPath)
            if (file.exists() && file.canRead()) {
                // Directly accessible local/private file
                uri = Uri.fromFile(file)
            } else if (file.exists()) {
                // File exists but direct reading blocked (scoped storage on Android 10/11)
                // Safely search MediaStore strictly by matching absolute path
                try {
                    val projection = arrayOf(android.provider.MediaStore.Video.Media._ID)
                    val selection = "${android.provider.MediaStore.Video.Media.DATA} = ?"
                    val selectionArgs = arrayOf(file.absolutePath)
                    context.contentResolver.query(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                            val resolvedId = cursor.getLong(idCol)
                            uri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                resolvedId
                            )
                        }
                    }
                } catch (_: Throwable) {}
            }
        } else if (uri.scheme == "content") {
            // Content URI (SAF document or tree URI, or MediaStore URI):
            // Renew persistable permission if available without rewriting the URI
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
        }

        val builder = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(uri)

        // Only specify MIME type when necessary; allow ExoPlayer container sniffing
        // to avoid playback failures from false MIME type mismatches
        if (video.mimeType.isNotBlank() &&
            video.mimeType != "video/*" &&
            video.mimeType != "*/*" &&
            video.mimeType != "application/octet-stream"
        ) {
            val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
            if (ext.isEmpty() || ext in setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "flv", "ts")) {
                // Let ExoPlayer DefaultExtractorsFactory sniff the container magic bytes
            } else {
                builder.setMimeType(video.mimeType)
            }
        }

        if (externalSubtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(externalSubtitles)
        }

        return builder.build()
    }

    override fun pause() {
        logger.d(TAG, "pause()")
        pausedByHeadset = false
        exoPlayer.pause()
        persistenceManager.stopPeriodicSave()
        val livePlayback = getLivePlaybackState()
        playerScope.launch {
            persistenceManager.saveImmediate(livePlayback, _state.value.currentVideo)
        }
    }

    override fun resume() {
        logger.d(TAG, "resume()")
        pausedByHeadset = false
        val curVid = _state.value.currentVideo
        if (exoPlayer.playbackState == Player.STATE_ENDED ||
            exoPlayer.playbackState == Player.STATE_IDLE ||
            _state.value.playback.playbackStatus == PlaybackStatus.ENDED
        ) {
            if (exoPlayer.currentMediaItem == null && curVid != null) {
                exoPlayer.setMediaItem(buildMediaItem(curVid, activeExternalSubtitles.toList()))
            }
            exoPlayer.seekTo(0)
            exoPlayer.prepare()
        }
        exoPlayer.play()
        becomingNoisyReceiver.register()
        persistenceManager.startPeriodicSave(playerScope, { getLivePlaybackState() }, { _state.value.currentVideo })
    }

    override fun seekTo(positionMs: Long) {
        val dur = exoPlayer.duration.takeIf { it > 0 } ?: _playbackPosition.value.durationMs.coerceAtLeast(0L)
        val target = positionMs.coerceIn(0L, dur.coerceAtLeast(0L))
        logger.d(TAG, "seekTo: $target ms")
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        exoPlayer.seekTo(target)
        _playbackPosition.value = _playbackPosition.value.copy(currentPositionMs = target, durationMs = dur)
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
    private var bassBoost: android.media.audiofx.BassBoost? = null
    private var bassBoostSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    fun applyAudioEffects(eqState: EqualizerState) {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return

        try {
            if (eqState.enabled) {
                if (equalizer == null || equalizerSessionId != sessionId) {
                    releaseEqualizer()
                    equalizer = android.media.audiofx.Equalizer(0, sessionId)
                    equalizerSessionId = sessionId
                }
                equalizer?.let { eq ->
                    eq.enabled = true
                    val numBands = eq.numberOfBands
                    val minLevel = eq.bandLevelRange.getOrNull(0) ?: -1000
                    val maxLevel = eq.bandLevelRange.getOrNull(1) ?: 1000

                    when (eqState.preset) {
                        "Balanced" -> {
                            for (i in 0 until numBands) {
                                val band = i.toShort()
                                val centerFreq = eq.getCenterFreq(band)
                                val level = if (centerFreq >= 3_000_000) {
                                    100.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                                } else {
                                    0.toShort()
                                }
                                eq.setBandLevel(band, level)
                            }
                        }
                        "Bass Boost" -> {
                            for (i in 0 until numBands) {
                                val band = i.toShort()
                                val centerFreq = eq.getCenterFreq(band)
                                val level = when {
                                    centerFreq < 250_000 -> 700.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                                    centerFreq < 600_000 -> 400.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                                    else -> 0.toShort()
                                }
                                eq.setBandLevel(band, level)
                            }
                        }
                        else -> { // "Custom"
                            val levels = eqState.bandLevels.split(",")
                                .mapNotNull { it.trim().toIntOrNull() }
                            for (i in 0 until numBands) {
                                val band = i.toShort()
                                val dbVal = levels.getOrNull(i) ?: 0
                                val mbVal = (dbVal * 100).coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                                eq.setBandLevel(band, mbVal)
                            }
                        }
                    }
                }

                // BassBoost hardware effect
                if (eqState.preset == "Bass Boost" || eqState.bassBoostStrength > 0) {
                    if (bassBoost == null || bassBoostSessionId != sessionId) {
                        releaseBassBoost()
                        bassBoost = android.media.audiofx.BassBoost(0, sessionId)
                        bassBoostSessionId = sessionId
                    }
                    bassBoost?.let { bb ->
                        bb.enabled = true
                        val strength = if (eqState.preset == "Bass Boost" && eqState.bassBoostStrength == 0) {
                            700.toShort()
                        } else {
                            eqState.bassBoostStrength.coerceIn(0, 1000).toShort()
                        }
                        bb.setStrength(strength)
                    }
                } else {
                    releaseBassBoost()
                }
            } else if (currentSettings.dialogueBoostEnabled) {
                applyDialogueBooster(true)
                releaseBassBoost()
            } else {
                releaseEqualizer()
                releaseBassBoost()
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed to apply equalizer settings: ${e.message}")
        }
    }

    override fun setVideoEqualizerEnabled(enabled: Boolean) {
        val currentVideo = _state.value.currentVideo ?: return
        val newEqState = _state.value.equalizerState.copy(
            enabled = enabled,
            preset = if (enabled && _state.value.equalizerState.preset.isEmpty()) "Custom" else _state.value.equalizerState.preset
        )
        _state.update { it.copy(equalizerState = newEqState) }
        applyAudioEffects(newEqState)
        saveEqualizerPreferences(currentVideo.id, newEqState)
    }

    override fun setVideoEqualizerPreset(preset: String) {
        val currentVideo = _state.value.currentVideo ?: return
        val newEqState = _state.value.equalizerState.copy(preset = preset)
        _state.update { it.copy(equalizerState = newEqState) }
        applyAudioEffects(newEqState)
        saveEqualizerPreferences(currentVideo.id, newEqState)
    }

    override fun setVideoEqualizerBandLevels(levels: String) {
        val currentVideo = _state.value.currentVideo ?: return
        val newEqState = _state.value.equalizerState.copy(bandLevels = levels)
        _state.update { it.copy(equalizerState = newEqState) }
        applyAudioEffects(newEqState)
        saveEqualizerPreferences(currentVideo.id, newEqState)
    }

    override fun setVideoEqualizerBassStrength(strength: Int) {
        val currentVideo = _state.value.currentVideo ?: return
        val newEqState = _state.value.equalizerState.copy(bassBoostStrength = strength)
        _state.update { it.copy(equalizerState = newEqState) }
        applyAudioEffects(newEqState)
        saveEqualizerPreferences(currentVideo.id, newEqState)
    }

    private fun saveEqualizerPreferences(videoId: String, eqState: EqualizerState) {
        playerScope.launch(dispatchers.io) {
            runCatching {
                equalizerPreferenceDao.upsertPreferences(
                    EqualizerPreferenceEntity(
                        videoId = videoId,
                        enabled = eqState.enabled,
                        preset = eqState.preset,
                        bandLevels = eqState.bandLevels,
                        bassBoostStrength = eqState.bassBoostStrength
                    )
                )
            }
        }
    }

    fun applyDialogueBooster(enabled: Boolean) {
        if (_state.value.equalizerState.enabled) return
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
                            val centerFreq = eq.getCenterFreq(band)
                            when {
                                centerFreq in 800_000..4_500_000 -> {
                                    val maxLevel = eq.bandLevelRange.getOrNull(1) ?: 1000
                                    val boost = (maxLevel * 0.55f).toInt().coerceAtMost(maxLevel.toInt()).toShort()
                                    eq.setBandLevel(band, boost)
                                }
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
            equalizer?.let { eq ->
                val numBands = eq.numberOfBands
                for (i in 0 until numBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
                eq.enabled = false
                eq.release()
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed to release Equalizer: ${e.message}")
        } finally {
            equalizer = null
            equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun releaseBassBoost() {
        try {
            bassBoost?.let { bb ->
                bb.setStrength(0.toShort())
                bb.enabled = false
                bb.release()
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed to release BassBoost: ${e.message}")
        } finally {
            bassBoost = null
            bassBoostSessionId = C.AUDIO_SESSION_ID_UNSET
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
        if (_state.value.isMuted) {
            _state.update { it.copy(isMuted = false) }
        }
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

    override fun setMuted(muted: Boolean) {
        logger.d(TAG, "setMuted: $muted")
        if (muted) {
            exoPlayer.volume = 0f
            applyLoudnessEnhancer(0f)
            _state.update { it.copy(isMuted = true) }
        } else {
            val vol = _state.value.playback.volume.coerceIn(0f, 2.0f)
            if (vol <= 1.0f) {
                exoPlayer.volume = vol
            } else {
                exoPlayer.volume = 1.0f
            }
            applyLoudnessEnhancer(vol)
            _state.update { it.copy(isMuted = false) }
        }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        logger.d(TAG, "setRepeatMode: $mode")
        queue.setRepeatMode(mode)
        // The queue is the real repeat engine for OFF/REPEAT_ALL (it advances
        // to the next media item itself). The one mirror that must stay is
        // REPEAT_ONE: ExoPlayer's internal loop is the actual mechanism for a
        // single media item — STATE_ENDED never fires, so the queue path can't
        // advance anyway. OFF is mirrored back to clear a lingering REPEAT_ONE.
        exoPlayer.repeatMode = if (mode == RepeatMode.REPEAT_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        _state.update {
            it.copy(playback = it.playback.copy(repeatMode = mode))
        }
    }

    override fun setShuffle(enabled: Boolean) {
        logger.d(TAG, "setShuffle: $enabled")
        queue.setShuffle(enabled)
        // No ExoPlayer mirror: ordering is the queue engine's job.
        _state.update {
            it.copy(playback = it.playback.copy(isShuffleEnabled = enabled))
        }
    }

    override fun selectAudioTrack(trackId: String?) {
        logger.d(TAG, "selectAudioTrack: $trackId")
        audioTrackInitializedForSession = true
        userSelectedAudioTrackForSession = trackId
        trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_AUDIO, trackId)

        val matchingTrack = _state.value.availableAudioTracks.find { it.id == trackId }
        val compositePref = if (matchingTrack != null) {
            "${matchingTrack.id}|${matchingTrack.language.orEmpty()}|${matchingTrack.label}"
        } else {
            trackId
        }
        savedAudioPreferenceForVideo = compositePref

        _state.update { current ->
            val updatedAudio = current.availableAudioTracks.map {
                it.copy(isSelected = it.id == trackId)
            }
            current.copy(
                availableAudioTracks = updatedAudio,
                playback = current.playback.copy(selectedAudioTrackId = compositePref)
            )
        }

        val currentVid = _state.value.currentVideo
        if (currentVid != null) {
            playerScope.launch {
                persistenceManager.saveTrackSelection(
                    videoId = currentVid.id,
                    audioTrackId = compositePref,
                    videoDurationMs = currentVid.durationMs
                )
            }
        }
    }

    override fun selectSubtitleTrack(trackId: String?) {
        logger.d(TAG, "selectSubtitleTrack: $trackId")
        userDisabledSubtitlesForSession = (trackId == null)
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
        val currentVid = _state.value.currentVideo ?: return
        playerScope.launch {
            try {
                val existing = subtitlePreferenceDao.getPreferences(currentVid.id)
                val entity = existing?.copy(subtitleDelayMs = delayMs)
                    ?: SubtitlePreferenceEntity(
                        videoId = currentVid.id,
                        preferredSubtitleTrackId = _state.value.playback.selectedSubtitleTrackId,
                        subtitleDelayMs = delayMs,
                        externalSubtitleUri = null
                    )
                subtitlePreferenceDao.upsertPreferences(entity)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to persist subtitle delay: ${e.message}")
            }
        }
        if (exoPlayer.playbackState == Player.STATE_READY) {
            val pos = exoPlayer.currentPosition
            exoPlayer.seekTo(pos)
        }
    }

    override fun addExternalSubtitle(uri: String, label: String, mimeType: String) {
        val currentVideo = _state.value.currentVideo ?: return
        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        activeExternalSubtitles.removeAll { it.uri.toString() == uri }
        activeExternalSubtitles.add(subConfig)

        val currentPos = exoPlayer.currentPosition
        val newItem = buildMediaItem(currentVideo, activeExternalSubtitles.toList())
        exoPlayer.setMediaItem(newItem)
        exoPlayer.seekTo(currentPos)
        exoPlayer.prepare()

        playerScope.launch {
            try {
                val existing = subtitlePreferenceDao.getPreferences(currentVideo.id)
                val entity = existing?.copy(externalSubtitleUri = uri)
                    ?: SubtitlePreferenceEntity(
                        videoId = currentVideo.id,
                        preferredSubtitleTrackId = null,
                        subtitleDelayMs = _state.value.subtitleDelayMs,
                        externalSubtitleUri = uri
                    )
                subtitlePreferenceDao.upsertPreferences(entity)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to persist external subtitle preference: ${e.message}")
            }
        }
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
        releaseBassBoost()
        val livePlayback = getLivePlaybackState()
        val currentVid = _state.value.currentVideo
        playerScope.launch {
            persistenceManager.saveImmediate(livePlayback, currentVid)
        }
        seekPreviewLoader.releaseRetriever()
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (e: Exception) {
            logger.w(TAG, "Error stopping exoPlayer: ${e.message}")
        }
        _state.update { it.copy(currentVideo = null, isBackgroundAudio = false, isMuted = false) }
    }

    /**
     * Fire-and-forget playback start. Same playerScope + error capture the old
     * command channel used for PlayerCommand.Play — promoted to a named entry
     * point so UI callbacks don't need a coroutine or a command envelope.
     */
    override fun playNow(video: Video, startPositionMs: Long?) {
        playerScope.launch {
            try {
                play(video, startPositionMs)
            } catch (e: Throwable) {
                logger.e(TAG, "Unhandled error in playNow: ${e.message}", e)
                _state.update {
                    it.copy(
                        currentVideo = video,
                        error = PlaybackError.SourceError(
                            message = "Cannot play video: ${e.localizedMessage ?: e.message ?: "Failed"}",
                            cause = e,
                            recoverable = false
                        )
                    )
                }
            }
        }
    }

    override fun markInPictureInPicture(inPip: Boolean) {
        _state.update { it.copy(isInPictureInPicture = inPip) }
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
                        playbackStatus = status
                    )
                )
            }

            if (playbackState == Player.STATE_READY) {
                applyDialogueBooster(currentSettings.dialogueBoostEnabled)
                applyLoudnessEnhancer(_state.value.playback.volume)
                applyDisplayRefreshRateOptimization(currentSettings.matchDisplayRefreshRate)

                val readyDur = exoPlayer.duration.takeIf { it > 0 }
                if (readyDur != null) {
                    _state.update { current ->
                        val updatedVideo = current.currentVideo?.let { vid ->
                            if (vid.durationMs <= 0L) vid.copy(durationMs = readyDur) else vid
                        }
                        current.copy(currentVideo = updatedVideo ?: current.currentVideo)
                    }
                }
                if (readyDur != null && _state.value.chapters.isNotEmpty()) {
                    _state.update { current ->
                        val reNormalized = chapterExtractor.sanitizeAndNormalize(
                            rawChapters = current.chapters.map { RawChapter(it.id, it.title, it.startTimeMs, it.endTimeMs) },
                            videoDurationMs = readyDur
                        )
                        current.copy(chapters = reNormalized)
                    }
                }
            }


            if (playbackState == Player.STATE_ENDED) {
                becomingNoisyReceiver.unregister()
                persistenceManager.stopPeriodicSave()
                val livePlayback = getLivePlaybackState()
                playerScope.launch {
                    persistenceManager.saveImmediate(livePlayback, _state.value.currentVideo)

                    // Autoplay next in queue if available. Decide from hasNext
                    // first: advancing the queue without playing would hide the
                    // "next episode" affordance and skip an item.
                    val settings = settingsRepository.userSettings.first()
                    if (AutoAdvancePolicy.shouldAdvance(settings.autoplayNextVideo, queue.state.value.hasNext)) {
                        queue.next()?.let { nextVideo -> play(nextVideo) }
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _state.update { current ->
                    val updatedVideo = current.currentVideo?.let { vid ->
                        if (vid.width <= 0 || vid.height <= 0) {
                            vid.copy(width = videoSize.width, height = videoSize.height)
                        } else vid
                    }
                    current.copy(currentVideo = updatedVideo ?: current.currentVideo)
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyAudioEffects(_state.value.equalizerState)
            applyLoudnessEnhancer(_state.value.playback.volume)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update {
                it.copy(playback = it.playback.copy(isPlaying = isPlaying))
            }
            if (isPlaying) {
                persistenceManager.startPeriodicSave(
                    playerScope,
                    { getLivePlaybackState() },
                    { _state.value.currentVideo }
                )
            } else {
                persistenceManager.stopPeriodicSave()
                val livePlayback = getLivePlaybackState()
                playerScope.launch {
                    persistenceManager.saveImmediate(
                        livePlayback,
                        _state.value.currentVideo
                    )
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

            // Match remembered or preferred audio language if not yet initialized for this video session
            if (!audioTrackInitializedForSession && audio.isNotEmpty()) {
                when (val choice = TrackSelectionPolicy.chooseAudioTrack(
                    available = audio,
                    savedPreference = savedAudioPreferenceForVideo,
                    preferredLanguage = currentSettings.preferredAudioLanguage
                )) {
                    is TrackSelectionPolicy.AudioChoice.Restored -> {
                        logger.i(
                            TAG,
                            "Restoring remembered audio for video: ${choice.track.label} (${choice.track.language})"
                        )
                        if (choice.track.id != selectedAudio) {
                            trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_AUDIO, choice.track.id)
                            selectedAudio = choice.track.id
                        }
                        userSelectedAudioTrackForSession = choice.track.id
                    }
                    is TrackSelectionPolicy.AudioChoice.Preferred -> {
                        if (choice.track.id != selectedAudio) {
                            logger.i(
                                TAG,
                                "Selecting default preferred audio: ${currentSettings.preferredAudioLanguage} -> ${choice.track.label} (${choice.track.language})"
                            )
                            trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_AUDIO, choice.track.id)
                            selectedAudio = choice.track.id
                        }
                    }
                    TrackSelectionPolicy.AudioChoice.Keep -> {}
                }
                audioTrackInitializedForSession = true
            }

            // Auto-enable subtitles if enabled in user settings and not explicitly turned off for this video
            val desiredSubs = TrackSelectionPolicy.chooseSubtitleTrack(
                available = subs,
                currentlySelectedId = selectedSubs,
                subtitlesEnabled = currentSettings.subtitlesEnabled,
                userDisabledForSession = userDisabledSubtitlesForSession,
                preferredLanguage = currentSettings.preferredSubtitleLanguage
            )
            if (desiredSubs != selectedSubs) {
                trackManager.selectTrack(exoPlayer, C.TRACK_TYPE_TEXT, desiredSubs)
                selectedSubs = desiredSubs
            }

            val finalAudio = audio.map { it.copy(isSelected = it.id == selectedAudio) }
            val finalSubs = subs.map { it.copy(isSelected = it.id == selectedSubs) }

            _state.update {
                it.copy(
                    availableAudioTracks = finalAudio,
                    availableVideoTracks = video,
                    availableSubtitleTracks = finalSubs,
                    playback = it.playback.copy(
                        selectedAudioTrackId = savedAudioPreferenceForVideo ?: selectedAudio,
                        selectedVideoTrackId = selectedVideo,
                        selectedSubtitleTrackId = selectedSubs
                    )
                )
            }
        }



        override fun onMetadata(metadata: Metadata) {
            val dur = exoPlayer.duration.takeIf { it > 0 } ?: _state.value.currentVideo?.durationMs
            val extracted = chapterExtractor.extractFromMetadata(metadata, dur)
            if (extracted.isNotEmpty()) {
                _state.update { current ->
                    val combined = chapterExtractor.sanitizeAndNormalize(
                        rawChapters = (current.chapters + extracted).map {
                            RawChapter(it.id, it.title, it.startTimeMs, it.endTimeMs)
                        },
                        videoDurationMs = dur
                    )
                    current.copy(chapters = combined)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val duration = exoPlayer.duration.takeIf { it > 0 }
                ?: _playbackPosition.value.durationMs.takeIf { it > 0 }
                ?: _state.value.currentVideo?.durationMs?.takeIf { it > 0 }
                ?: 0L
            val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L).takeIf { it > 0 }
                ?: _playbackPosition.value.currentPositionMs
            val bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L).takeIf { it > 0 }
                ?: _playbackPosition.value.bufferedPositionMs

            val remainingMs = if (duration > 0) (duration - currentPos).coerceAtLeast(0L) else Long.MAX_VALUE
            val progressPercent = if (duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            // Check if error occurred when the video is about to end (e.g. premature EOF / truncated credits / audio-video duration mismatch)
            val isNearEnd = duration > 10_000L && (
                remainingMs <= 60_000L ||
                progressPercent >= 0.90f ||
                (bufferedPos > 0 && bufferedPos >= duration - 3_000L)
            )

            val isEofOrNearEndSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.cause is java.io.EOFException ||
                error.cause?.message?.contains("EOF", ignoreCase = true) == true ||
                error.message?.contains("Source error", ignoreCase = true) == true ||
                error.errorCodeName.contains("IO", ignoreCase = true)

            if (isNearEnd && isEofOrNearEndSourceError) {
                logger.w(
                    TAG,
                    "Playback encountered EOF / source error near completion ($currentPos/$duration ms, remaining: $remainingMs ms). " +
                        "Treating as natural playback completion."
                )

                becomingNoisyReceiver.unregister()
                persistenceManager.stopPeriodicSave()

                // The completed position (duration) is a persistence fact only;
                // the UI state keeps status/flags. Position lives in the one flow.
                val completedPlayback = _state.value.playback.copy(
                    playbackStatus = PlaybackStatus.ENDED,
                    isPlaying = false,
                    currentPositionMs = duration,
                    durationMs = duration,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )

                _state.update {
                    it.copy(
                        error = null,
                        playback = it.playback.copy(
                            playbackStatus = PlaybackStatus.ENDED,
                            isPlaying = false,
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                    )
                }

                playerScope.launch {
                    persistenceManager.saveImmediate(completedPlayback, _state.value.currentVideo)

                    // Autoplay next in queue if available. Decide from hasNext
                    // first: advancing the queue without playing would hide the
                    // "next episode" affordance and skip an item.
                    val settings = settingsRepository.userSettings.first()
                    if (AutoAdvancePolicy.shouldAdvance(settings.autoplayNextVideo, queue.state.value.hasNext)) {
                        queue.next()?.let { nextVideo ->
                            logger.i(TAG, "Autoplaying next video after near-end EOF: ${nextVideo.displayName}")
                            play(nextVideo)
                        }
                    }
                }
                return
            }

            val mappedError = PlayerErrorMapper.map(error)
            logger.e(TAG, "Playback error: ${mappedError.message}", error)
            _state.update { it.copy(error = mappedError) }
            becomingNoisyReceiver.unregister()
            persistenceManager.stopPeriodicSave()
            val livePlayback = getLivePlaybackState()
            playerScope.launch {
                persistenceManager.saveImmediate(livePlayback, _state.value.currentVideo)
            }
        }
    }
}
