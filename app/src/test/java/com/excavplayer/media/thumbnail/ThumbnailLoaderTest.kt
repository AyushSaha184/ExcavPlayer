package com.excavplayer.media.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThumbnailLoaderTest {

    private lateinit var thumbnailLoader: ThumbnailLoader

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val testLogger = object : AppLogger {
        override fun d(tag: String, message: String) {}
        override fun i(tag: String, message: String) {}
        override fun w(tag: String, message: String, throwable: Throwable?) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        thumbnailLoader = ThumbnailLoader(context, testDispatchers, testLogger)
    }

    @Test
    fun `isSolidColor returns true for solid black bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        assertThat(thumbnailLoader.isSolidColor(bitmap)).isTrue()
    }

    @Test
    fun `isSolidColor returns true for solid white bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        assertThat(thumbnailLoader.isSolidColor(bitmap)).isTrue()
    }

    @Test
    fun `isSolidColor returns false for diverse multicolored bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val color = if ((x / 10 + y / 10) % 2 == 0) Color.RED else Color.BLUE
                bitmap.setPixel(x, y, color)
            }
        }

        assertThat(thumbnailLoader.isSolidColor(bitmap)).isFalse()
    }

    @Test
    fun `isSolidColor returns true for solid content even with black letterbox borders`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        // Draw black border around outer 5 pixels (letterbox)
        for (x in 0 until 100) {
            for (y in 0..5) bitmap.setPixel(x, y, Color.BLACK)
            for (y in 94..99) bitmap.setPixel(x, y, Color.BLACK)
        }

        // Inner 80% is still solid white, so isSolidColor should ignore the outer borders and detect solid color
        assertThat(thumbnailLoader.isSolidColor(bitmap)).isTrue()
    }
}

