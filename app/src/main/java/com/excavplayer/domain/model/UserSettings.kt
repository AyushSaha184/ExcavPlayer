package com.excavplayer.domain.model

data class UserSettings(
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoResume: Boolean = true,
    val resumeThresholdPercent: Float = 0.95f,
    val defaultRepeatMode: RepeatMode = RepeatMode.OFF,
    val autoplayNextVideo: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val preferredSubtitleLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val gestureControlsEnabled: Boolean = true,
    val rememberVolume: Boolean = true,
    val continueWatchingEnabled: Boolean = true,
    val historyEnabled: Boolean = true
)
