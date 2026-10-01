package com.excavplayer.domain.model

sealed class PlaybackError(
    open val message: String,
    open val cause: Throwable? = null,
    val isRecoverable: Boolean = false
) {
    data class CodecError(
        override val message: String,
        val codecName: String? = null,
        val recoverable: Boolean = false
    ) : PlaybackError(message, isRecoverable = recoverable)

    data class FileNotFoundError(
        val uri: String,
        override val message: String = "Media file not found: $uri"
    ) : PlaybackError(message, isRecoverable = false)

    data class PermissionDeniedError(
        val uri: String,
        override val message: String = "Storage permission revoked or denied for: $uri"
    ) : PlaybackError(message, isRecoverable = false)

    data class InvalidUriError(
        val uri: String,
        override val message: String = "Invalid or unparseable content URI: $uri"
    ) : PlaybackError(message, isRecoverable = false)

    data class CorruptedMediaError(
        override val message: String = "Media container or stream is corrupted",
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = false)

    data class DecoderInitializationError(
        val decoderName: String,
        override val message: String = "Failed to initialize decoder: $decoderName",
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = true)

    data class InsufficientResourcesError(
        override val message: String = "Insufficient system resources for decoding or rendering",
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = true)

    data class MalformedSubtitleError(
        override val message: String = "Subtitle file could not be parsed",
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = true)

    data class NetworkSourceError(
        val url: String,
        override val message: String = "Network error connecting to media source: $url",
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = true)

    data class SourceError(
        override val message: String = "Media source read error",
        override val cause: Throwable? = null,
        val recoverable: Boolean = true
    ) : PlaybackError(message, cause, isRecoverable = recoverable)

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : PlaybackError(message, cause, isRecoverable = false)
}
