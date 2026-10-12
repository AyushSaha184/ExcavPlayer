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
        searchService = mockk(relaxed = true),
        downloader = mockk(relaxed = true),
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

    @Test
    fun `cleanVideoTitle removes resolutions, codecs, junk and preserves show, season, episode`() {
        val test1 = cleanVideoTitle("Inception.2010.1080p.BluRay.x264.AAC5.1-[YTS.MX].mp4")
        assertThat(test1.formatted).isEqualTo("Inception")
        assertThat(test1.season).isNull()
        assertThat(test1.episode).isNull()

        val test2 = cleanVideoTitle("Breaking.Bad.S01E03.720p.HDTV.x264-AVS.mkv")
        assertThat(test2.formatted).isEqualTo("Breaking Bad S01E03")
        assertThat(test2.season).isEqualTo(1)
        assertThat(test2.episode).isEqualTo(3)

        val test3 = cleanVideoTitle("Stranger Things 4x01 Chapter One 1080p.mkv")
        assertThat(test3.formatted).isEqualTo("Stranger Things S04E01")
        assertThat(test3.season).isEqualTo(4)
        assertThat(test3.episode).isEqualTo(1)

        val test4 = cleanVideoTitle("The.Batman.2022.2160p.UHD.HDR.x265.mkv")
        assertThat(test4.formatted).isEqualTo("The Batman")

        val test5 = cleanVideoTitle("Naruto.Shippuden.Episode.42.720p.mp4")
        assertThat(test5.formatted).isEqualTo("Naruto Shippuden E42")
        assertThat(test5.episode).isEqualTo(42)
    }
}
