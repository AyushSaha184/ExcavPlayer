package com.excavplayer.domain.model

data class UserSettings(
    val defaultPlaybackSpeed: Float = 1.0f,
    val autoResume: Boolean = true,
    val resumeThresholdPercent: Float = 0.95f,
    val defaultRepeatMode: RepeatMode = RepeatMode.OFF,
    val autoplayNextVideo: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val preferredSubtitleLanguage: String? = "English",
    val preferredAudioLanguage: String? = "English",
    val gestureControlsEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val subtitleTextSize: String = "Normal",
    val subtitleTextColor: String = "White",
    val subtitleBackgroundStyle: String = "Outline",
    val continueWatchingEnabled: Boolean = true,
    val defaultScreenOrientation: String = "Auto",
    val defaultMediaFit: String = "Fit to Screen",
    val lastOpenedFolder: String? = null,
    val subtitleVerticalPositionPercent: Float = 0.90f,
    val autoRescanOnLaunch: Boolean = true,
    val headsetDetectionEnabled: Boolean = true,
    val stopOnScreenOff: Boolean = true,
    val hardwareAccelerationMode: String = "Automatic",
    val hardwareAccelerationEnabled: Boolean = true,
    val dialogueBoostEnabled: Boolean = false,
    val matchDisplayRefreshRate: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Custom",
    val equalizerBandLevels: String = "0,0,0,0,0",
    val bassBoostStrength: Int = 0
) {
    val seekDurationSeconds: Int
        get() = when {
            resumeThresholdPercent >= 0.99f -> 5
            resumeThresholdPercent >= 0.95f -> 10
            resumeThresholdPercent >= 0.92f -> 20
            resumeThresholdPercent >= 0.90f -> 30
            else -> 60
        }

    val seekDurationMs: Long
        get() = seekDurationSeconds * 1000L
}
