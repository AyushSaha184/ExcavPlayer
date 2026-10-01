package com.excavplayer.player.core

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.PlaybackError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerErrorMapperTest {

    @Test
    fun `maps file not found error to FileNotFoundError`() {
        val ex = PlaybackException(
            "File missing",
            null,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.FileNotFoundError::class.java)
        assertThat(domainError.isRecoverable).isFalse()
    }

    @Test
    fun `maps permission denied to PermissionDeniedError`() {
        val ex = PlaybackException(
            "Access denied",
            null,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.PermissionDeniedError::class.java)
        assertThat(domainError.isRecoverable).isFalse()
    }

    @Test
    fun `maps decoder init failure to DecoderInitializationError with recoverable true`() {
        val ex = PlaybackException(
            "Decoder init fail",
            null,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.DecoderInitializationError::class.java)
        assertThat(domainError.isRecoverable).isTrue()
    }

    @Test
    fun `maps container malformed to CorruptedMediaError`() {
        val ex = PlaybackException(
            "Corrupt stream",
            null,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.CorruptedMediaError::class.java)
        assertThat(domainError.isRecoverable).isFalse()
    }

    @Test
    fun `maps network timeout to NetworkSourceError`() {
        val ex = PlaybackException(
            "Timeout",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.NetworkSourceError::class.java)
        assertThat(domainError.isRecoverable).isTrue()
    }

    @Test
    fun `maps unspecified io error to SourceError with recoverable true`() {
        val ex = PlaybackException(
            "Source error",
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.SourceError::class.java)
        assertThat(domainError.isRecoverable).isTrue()
        assertThat(domainError.message).contains("Source error")
    }

    @Test
    fun `maps read position out of range to SourceError with recoverable true`() {
        val ex = PlaybackException(
            "EOF reached",
            null,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        )

        val domainError = PlayerErrorMapper.map(ex)
        assertThat(domainError).isInstanceOf(PlaybackError.SourceError::class.java)
        assertThat(domainError.isRecoverable).isTrue()
    }
}
