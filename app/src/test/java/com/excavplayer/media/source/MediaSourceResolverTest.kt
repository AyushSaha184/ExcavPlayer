package com.excavplayer.media.source

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.excavplayer.core.logging.AndroidAppLogger
import com.excavplayer.domain.model.MediaSourceType
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSourceResolverTest {

    private val resolver = MediaSourceResolver(
        context = mockk(relaxed = true),
        logger = AndroidAppLogger()
    )

    @Test
    fun `resolveSourceType resolves MediaStore, SAF tree, SAF document, and network URIs`() {
        val mediaStoreUri = Uri.parse("content://media/external/video/media/1234")
        assertThat(resolver.resolveSourceType(mediaStoreUri))
            .isEqualTo(MediaSourceType.LOCAL_MEDIASTORE)

        val safDocUri = Uri.parse("content://com.android.providers.media.documents/document/video%3A1234")
        assertThat(resolver.resolveSourceType(safDocUri))
            .isEqualTo(MediaSourceType.LOCAL_DOCUMENT)

        val safTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2Fvideo.mp4")
        assertThat(resolver.resolveSourceType(safTreeUri))
            .isEqualTo(MediaSourceType.LOCAL_TREE)

        val httpUri = Uri.parse("https://example.com/video.mp4")
        assertThat(resolver.resolveSourceType(httpUri))
            .isEqualTo(MediaSourceType.NETWORK_FUTURE)

        val smbUri = Uri.parse("smb://192.168.1.100/share/video.mkv")
        assertThat(resolver.resolveSourceType(smbUri))
            .isEqualTo(MediaSourceType.NETWORK_FUTURE)
    }
}
