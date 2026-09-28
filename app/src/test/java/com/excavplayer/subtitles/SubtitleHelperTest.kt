package com.excavplayer.subtitles

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import com.excavplayer.core.coroutine.DefaultDispatcherProvider
import com.excavplayer.core.logging.AndroidAppLogger
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleHelperTest {

    private val dao = mockk<SubtitlePreferenceDao>(relaxed = true)
    private val helper = SubtitleHelper(
        context = mockk(relaxed = true),
        subtitlePreferenceDao = dao,
        dispatchers = DefaultDispatcherProvider(),
        logger = AndroidAppLogger()
    )

    @Test
    fun `inferMimeTypeFromUri correctly resolves extensions`() {
        assertThat(helper.inferMimeTypeFromUri(Uri.parse("file:///movie/sub.srt")))
            .isEqualTo(MimeTypes.APPLICATION_SUBRIP)

        assertThat(helper.inferMimeTypeFromUri(Uri.parse("file:///movie/sub.vtt")))
            .isEqualTo(MimeTypes.TEXT_VTT)

        assertThat(helper.inferMimeTypeFromUri(Uri.parse("file:///movie/sub.ssa")))
            .isEqualTo(MimeTypes.TEXT_SSA)

        assertThat(helper.inferMimeTypeFromUri(Uri.parse("file:///movie/sub.ass")))
            .isEqualTo(MimeTypes.TEXT_SSA)

        assertThat(helper.inferMimeTypeFromUri(Uri.parse("file:///movie/sub.ttml")))
            .isEqualTo(MimeTypes.APPLICATION_TTML)
    }
}
