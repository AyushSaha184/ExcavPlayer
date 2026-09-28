package com.excavplayer.player.queue

import com.excavplayer.domain.model.RepeatMode
import com.excavplayer.domain.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class QueueState(
    val items: List<Video> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentItem: Video?
        get() = if (currentIndex in items.indices) items[currentIndex] else null

    val hasNext: Boolean
        get() = when (repeatMode) {
            RepeatMode.REPEAT_ALL, RepeatMode.REPEAT_ONE -> items.isNotEmpty()
            RepeatMode.OFF -> currentIndex < items.size - 1
        }

    val hasPrevious: Boolean
        get() = when (repeatMode) {
            RepeatMode.REPEAT_ALL, RepeatMode.REPEAT_ONE -> items.isNotEmpty()
            RepeatMode.OFF -> currentIndex > 0
        }
}

@Singleton
class PlaybackQueue @Inject constructor() {

    private val originalItems = mutableListOf<Video>()
    private val displayOrderIndices = mutableListOf<Int>()
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    fun setQueue(items: List<Video>, startWithIndex: Int = 0, keepShuffle: Boolean = false) {
        synchronized(this) {
            originalItems.clear()
            originalItems.addAll(items)
            rebuildDisplayOrder(keepShuffle, startWithIndex)
        }
    }

    fun addVideo(video: Video) {
        synchronized(this) {
            originalItems.add(video)
            val newOriginalIndex = originalItems.size - 1
            if (_state.value.isShuffleEnabled) {
                // Insert randomly after current position
                val currentPos = _state.value.currentIndex
                val insertAt = if (currentPos >= 0 && currentPos < displayOrderIndices.size) {
                    Random.nextInt(currentPos + 1, displayOrderIndices.size + 1)
                } else {
                    displayOrderIndices.size
                }
                displayOrderIndices.add(insertAt, newOriginalIndex)
            } else {
                displayOrderIndices.add(newOriginalIndex)
            }
            publishState(_state.value.currentIndex)
        }
    }

    fun removeAt(index: Int) {
        synchronized(this) {
            if (index !in displayOrderIndices.indices) return
            val originalIndex = displayOrderIndices.removeAt(index)
            originalItems.removeAt(originalIndex)

            // Adjust remaining indices in displayOrderIndices
            for (i in displayOrderIndices.indices) {
                if (displayOrderIndices[i] > originalIndex) {
                    displayOrderIndices[i] -= 1
                }
            }

            val current = _state.value.currentIndex
            val newCurrent = when {
                displayOrderIndices.isEmpty() -> -1
                index < current -> current - 1
                index == current -> current.coerceAtMost(displayOrderIndices.size - 1)
                else -> current
            }
            publishState(newCurrent)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        synchronized(this) {
            if (fromIndex !in displayOrderIndices.indices || toIndex !in displayOrderIndices.indices) return
            val item = displayOrderIndices.removeAt(fromIndex)
            displayOrderIndices.add(toIndex, item)

            var current = _state.value.currentIndex
            if (current == fromIndex) {
                current = toIndex
            } else if (fromIndex < current && toIndex >= current) {
                current -= 1
            } else if (fromIndex > current && toIndex <= current) {
                current += 1
            }
            publishState(current)
        }
    }

    fun setShuffle(enabled: Boolean) {
        synchronized(this) {
            if (_state.value.isShuffleEnabled == enabled) return
            val currentVideo = state.value.currentItem
            rebuildDisplayOrder(shuffle = enabled, preserveVideo = currentVideo)
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    fun next(): Video? {
        synchronized(this) {
            if (displayOrderIndices.isEmpty()) return null
            val current = _state.value.currentIndex
            val nextIndex = when (_state.value.repeatMode) {
                RepeatMode.REPEAT_ONE -> current
                RepeatMode.REPEAT_ALL -> if (current >= displayOrderIndices.size - 1) 0 else current + 1
                RepeatMode.OFF -> if (current < displayOrderIndices.size - 1) current + 1 else -1
            }
            if (nextIndex in displayOrderIndices.indices) {
                publishState(nextIndex)
                return getActiveItems()[nextIndex]
            }
            return null
        }
    }

    fun previous(): Video? {
        synchronized(this) {
            if (displayOrderIndices.isEmpty()) return null
            val current = _state.value.currentIndex
            val prevIndex = when (_state.value.repeatMode) {
                RepeatMode.REPEAT_ONE -> current
                RepeatMode.REPEAT_ALL -> if (current <= 0) displayOrderIndices.size - 1 else current - 1
                RepeatMode.OFF -> if (current > 0) current - 1 else -1
            }
            if (prevIndex in displayOrderIndices.indices) {
                publishState(prevIndex)
                return getActiveItems()[prevIndex]
            }
            return null
        }
    }

    fun moveTo(index: Int): Video? {
        synchronized(this) {
            if (index in displayOrderIndices.indices) {
                publishState(index)
                return getActiveItems()[index]
            }
            return null
        }
    }

    private fun rebuildDisplayOrder(
        shuffle: Boolean,
        startIndex: Int = 0,
        preserveVideo: Video? = null
    ) {
        displayOrderIndices.clear()
        val originalCount = originalItems.size
        for (i in 0 until originalCount) {
            displayOrderIndices.add(i)
        }

        var newCurrentIndex = startIndex.coerceIn(0, (originalCount - 1).coerceAtLeast(0))

        if (shuffle && originalCount > 1) {
            val preservedOriginalIdx = preserveVideo?.let { target ->
                originalItems.indexOfFirst { it.id == target.id }.takeIf { it != -1 }
            } ?: startIndex

            val others = displayOrderIndices.filter { it != preservedOriginalIdx }.shuffled()
            displayOrderIndices.clear()
            displayOrderIndices.add(preservedOriginalIdx)
            displayOrderIndices.addAll(others)
            newCurrentIndex = 0
        } else if (preserveVideo != null) {
            val idx = originalItems.indexOfFirst { it.id == preserveVideo.id }
            if (idx != -1) newCurrentIndex = idx
        }

        _state.update {
            it.copy(
                isShuffleEnabled = shuffle
            )
        }
        publishState(if (originalItems.isEmpty()) -1 else newCurrentIndex)
    }

    private fun getActiveItems(): List<Video> {
        return displayOrderIndices.mapNotNull { index ->
            if (index in originalItems.indices) originalItems[index] else null
        }
    }

    private fun publishState(currentIndex: Int) {
        val activeItems = getActiveItems()
        _state.update {
            it.copy(
                items = activeItems,
                currentIndex = currentIndex
            )
        }
    }
}
