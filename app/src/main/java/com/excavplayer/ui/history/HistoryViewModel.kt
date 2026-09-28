package com.excavplayer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.excavplayer.domain.model.Video
import com.excavplayer.domain.model.WatchHistoryEntry
import com.excavplayer.library.HistoryManager
import com.excavplayer.library.VideoLibrary
import com.excavplayer.player.core.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HistorySection(
    val title: String,
    val entries: List<WatchHistoryEntry>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyManager: HistoryManager,
    private val videoLibrary: VideoLibrary,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val allVideos = videoLibrary.observeVideos()
    private val historyFlow = historyManager.observeHistory()

    val videosMap: StateFlow<Map<String, Video>> = allVideos
        .combine(historyFlow) { videos, _ -> videos.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val groupedHistory: StateFlow<List<HistorySection>> = historyFlow
        .combine(allVideos) { entries, _ -> groupEntriesByDate(entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun groupEntriesByDate(entries: List<WatchHistoryEntry>): List<HistorySection> {
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = today - 24 * 60 * 60 * 1000L
        val lastWeek = today - 7 * 24 * 60 * 60 * 1000L

        val todayList = mutableListOf<WatchHistoryEntry>()
        val yesterdayList = mutableListOf<WatchHistoryEntry>()
        val lastWeekList = mutableListOf<WatchHistoryEntry>()
        val olderList = mutableListOf<WatchHistoryEntry>()

        entries.forEach { entry ->
            val timestamp = entry.lastPlayedTimestamp
            when {
                timestamp >= today -> todayList.add(entry)
                timestamp >= yesterday -> yesterdayList.add(entry)
                timestamp >= lastWeek -> lastWeekList.add(entry)
                else -> olderList.add(entry)
            }
        }

        val sections = mutableListOf<HistorySection>()
        if (todayList.isNotEmpty()) sections.add(HistorySection("Today", todayList))
        if (yesterdayList.isNotEmpty()) sections.add(HistorySection("Yesterday", yesterdayList))
        if (lastWeekList.isNotEmpty()) sections.add(HistorySection("Last Week", lastWeekList))
        if (olderList.isNotEmpty()) sections.add(HistorySection("Older", olderList))
        return sections
    }

    fun resume(entry: WatchHistoryEntry) {
        val video = videosMap.value[entry.videoId] ?: return
        viewModelScope.launch {
            playerManager.queue.setQueue(listOf(video), 0)
            playerManager.play(video, entry.lastPositionMs)
        }
    }

    fun playFromBeginning(entry: WatchHistoryEntry) {
        val video = videosMap.value[entry.videoId] ?: return
        viewModelScope.launch {
            playerManager.queue.setQueue(listOf(video), 0)
            playerManager.play(video, 0L)
        }
    }

    fun removeFromHistory(videoId: String) {
        viewModelScope.launch {
            historyManager.removeFromHistory(videoId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyManager.clearHistory()
        }
    }
}
