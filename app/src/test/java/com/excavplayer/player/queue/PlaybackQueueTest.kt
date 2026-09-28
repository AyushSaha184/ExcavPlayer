package com.excavplayer.player.queue

import com.google.common.truth.Truth.assertThat
import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import org.junit.Before
import org.junit.Test

class PlaybackQueueTest {

    private lateinit var queue: PlaybackQueue

    private val testVideos = (1..5).map { id ->
        Video(
            id = "video_$id",
            uri = "content://media/external/video/media/$id",
            displayName = "Video $id",
            mimeType = "video/mp4",
            durationMs = 60000L,
            sizeBytes = 1024L,
            dateAddedSeconds = 1000L,
            dateModifiedSeconds = 1000L,
            width = 1920,
            height = 1080
        )
    }

    @Before
    fun setup() {
        queue = PlaybackQueue()
    }

    @Test
    fun `setQueue initializes items and current item`() {
        queue.setQueue(testVideos, startWithIndex = 2)

        val state = queue.state.value
        assertThat(state.items).hasSize(5)
        assertThat(state.currentIndex).isEqualTo(2)
        assertThat(state.currentItem?.id).isEqualTo("video_3")
        assertThat(state.hasNext).isTrue()
        assertThat(state.hasPrevious).isTrue()
    }

    @Test
    fun `next and previous in RepeatMode OFF stop at boundaries`() {
        queue.setQueue(testVideos, startWithIndex = 0)
        queue.setRepeatMode(RepeatMode.OFF)

        assertThat(queue.previous()).isNull()
        assertThat(queue.state.value.currentIndex).isEqualTo(0)

        // Advance to end
        assertThat(queue.next()?.id).isEqualTo("video_2")
        assertThat(queue.next()?.id).isEqualTo("video_3")
        assertThat(queue.next()?.id).isEqualTo("video_4")
        assertThat(queue.next()?.id).isEqualTo("video_5")

        // Next at end in RepeatMode OFF should return null and not advance
        assertThat(queue.next()).isNull()
        assertThat(queue.state.value.currentIndex).isEqualTo(4)
    }

    @Test
    fun `next in RepeatMode REPEAT_ALL wraps to start`() {
        queue.setQueue(testVideos, startWithIndex = 4)
        queue.setRepeatMode(RepeatMode.REPEAT_ALL)

        val next = queue.next()
        assertThat(next?.id).isEqualTo("video_1")
        assertThat(queue.state.value.currentIndex).isEqualTo(0)
    }

    @Test
    fun `previous in RepeatMode REPEAT_ALL wraps to end`() {
        queue.setQueue(testVideos, startWithIndex = 0)
        queue.setRepeatMode(RepeatMode.REPEAT_ALL)

        val prev = queue.previous()
        assertThat(prev?.id).isEqualTo("video_5")
        assertThat(queue.state.value.currentIndex).isEqualTo(4)
    }

    @Test
    fun `RepeatMode REPEAT_ONE repeats current item`() {
        queue.setQueue(testVideos, startWithIndex = 2)
        queue.setRepeatMode(RepeatMode.REPEAT_ONE)

        val next = queue.next()
        assertThat(next?.id).isEqualTo("video_3")
        assertThat(queue.state.value.currentIndex).isEqualTo(2)
    }

    @Test
    fun `shuffle preserves active video and does not lose items`() {
        queue.setQueue(testVideos, startWithIndex = 2)
        val activeBefore = queue.state.value.currentItem

        queue.setShuffle(true)
        val stateAfterShuffle = queue.state.value

        assertThat(stateAfterShuffle.isShuffleEnabled).isTrue()
        assertThat(stateAfterShuffle.items).hasSize(5)
        assertThat(stateAfterShuffle.currentItem?.id).isEqualTo(activeBefore?.id)

        // Turn shuffle off -> order should revert to original items
        queue.setShuffle(false)
        val stateAfterUnshuffle = queue.state.value
        assertThat(stateAfterUnshuffle.isShuffleEnabled).isFalse()
        assertThat(stateAfterUnshuffle.items.map { it.id }).containsExactly(
            "video_1", "video_2", "video_3", "video_4", "video_5"
        ).inOrder()
    }

    @Test
    fun `addVideo and removeAt update queue state correctly`() {
        queue.setQueue(testVideos.take(3), startWithIndex = 1)

        val newVideo = testVideos[3]
        queue.addVideo(newVideo)
        assertThat(queue.state.value.items).hasSize(4)

        // Remove item at index 0 (before current index 1)
        queue.removeAt(0)
        assertThat(queue.state.value.items).hasSize(3)
        // Current index should adjust down to 0, still pointing to video_2
        assertThat(queue.state.value.currentIndex).isEqualTo(0)
        assertThat(queue.state.value.currentItem?.id).isEqualTo("video_2")
    }

    @Test
    fun `moveItem reorders queue elements`() {
        queue.setQueue(testVideos.take(3), startWithIndex = 0)
        // Move index 0 to index 2
        queue.moveItem(0, 2)

        val items = queue.state.value.items
        assertThat(items.map { it.id }).containsExactly("video_2", "video_3", "video_1").inOrder()
        assertThat(queue.state.value.currentIndex).isEqualTo(2)
    }
}
