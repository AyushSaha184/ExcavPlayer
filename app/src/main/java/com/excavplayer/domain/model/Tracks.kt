package com.excavplayer.domain.model

data class AudioTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val channelCount: Int = 2,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val isSelected: Boolean = false
)

data class VideoTrack(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val mimeType: String? = null,
    val isSelected: Boolean = false
) {
    val resolutionLabel: String
        get() = when {
            height >= 2160 -> "4K ($width×$height)"
            height >= 1440 -> "2K ($width×$height)"
            height >= 1080 -> "1080p ($width×$height)"
            height >= 720 -> "720p ($width×$height)"
            height >= 480 -> "480p ($width×$height)"
            height > 0 -> "${height}p ($width×$height)"
            else -> "Auto"
        }
}

data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val isEmbedded: Boolean = true,
    val uri: String? = null,
    val isSelected: Boolean = false
)
