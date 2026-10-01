package com.excavplayer.player.core

import androidx.media3.common.PlaybackException
import com.excavplayer.domain.model.PlaybackError

object PlayerErrorMapper {

    fun map(error: PlaybackException): PlaybackError {
        val message = error.message ?: "Unknown playback error"
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                PlaybackError.FileNotFoundError(
                    uri = "",
                    message = "File not found: ${error.errorCodeName}"
                )
            }
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                PlaybackError.PermissionDeniedError(
                    uri = "",
                    message = "Storage permission denied for video playback"
                )
            }
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                PlaybackError.NetworkSourceError(
                    url = "",
                    message = "Network connection failed",
                    cause = error
                )
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                PlaybackError.CorruptedMediaError(
                    message = "Corrupted or unsupported media container format",
                    cause = error
                )
            }
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                PlaybackError.DecoderInitializationError(
                    decoderName = "Hardware/Software Decoder",
                    message = "Failed to initialize video/audio decoder",
                    cause = error
                )
            }
            PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                PlaybackError.CodecError(
                    message = "Decoding failed for stream",
                    recoverable = false
                )
            }
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                PlaybackError.MalformedSubtitleError(
                    message = "Malformed stream manifest or subtitle data",
                    cause = error
                )
            }
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> {
                PlaybackError.SourceError(
                    message = "Error reading media source: ${error.message ?: error.errorCodeName}",
                    cause = error,
                    recoverable = true
                )
            }
            else -> {
                PlaybackError.UnknownError(
                    message = "Playback error: ${error.errorCodeName} ($message)",
                    cause = error
                )
            }
        }
    }
}
