package com.excavplayer.folders

import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.folders.logic.FolderStateManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderStateManagerTest {

    private fun createTestVideo(
        id: String,
        displayName: String,
        folderPath: String
    ): Video {
        return Video(
            id = id,
            uri = "content://media/external/video/media/$id",
            displayName = displayName,
            mimeType = "video/mp4",
            durationMs = 60000L,
            sizeBytes = 1024L,
            dateAddedSeconds = 1700000000L,
            dateModifiedSeconds = 1700000000L,
            width = 1920,
            height = 1080,
            folderPath = folderPath,
            sourceType = MediaSourceType.LOCAL_MEDIASTORE,
            availability = MediaAvailability.AVAILABLE
        )
    }

    @Test
    fun testNavigationStack() {
        val manager = FolderStateManager()
        val v1 = createTestVideo("1", "vid.mp4", "/storage/emulated/0/Movies/Action")
        val state1 = manager.computeUiState(listOf(v1))

        // At root:
        assertThat(state1.currentPath).isNull()
        assertThat(state1.canGoBack).isFalse()
        assertThat(state1.subfolders.map { it.name }).containsExactly("Movies")

        // Navigate into Movies
        val moviesFolder = state1.subfolders.first()
        manager.navigateInto(moviesFolder)

        val state2 = manager.computeUiState(listOf(v1))
        assertThat(state2.currentPath).isEqualTo("/storage/emulated/0/Movies")
        assertThat(state2.canGoBack).isTrue()
        assertThat(state2.subfolders.map { it.name }).containsExactly("Action")
        assertThat(state2.directVideos).isEmpty()

        // Navigate into Action
        val actionFolder = state2.subfolders.first()
        manager.navigateInto(actionFolder)

        val state3 = manager.computeUiState(listOf(v1))
        assertThat(state3.currentPath).isEqualTo("/storage/emulated/0/Movies/Action")
        assertThat(state3.canGoBack).isTrue()
        assertThat(state3.subfolders).isEmpty()
        assertThat(state3.directVideos.map { it.displayName }).containsExactly("vid.mp4")

        // Navigate Up: should go back to Movies
        val wentUp = manager.navigateUp()
        assertThat(wentUp).isTrue()
        val state4 = manager.computeUiState(listOf(v1))
        assertThat(state4.currentPath).isEqualTo("/storage/emulated/0/Movies")

        // Navigate Up again: should go back to root
        val wentUp2 = manager.navigateUp()
        assertThat(wentUp2).isTrue()
        val state5 = manager.computeUiState(listOf(v1))
        assertThat(state5.currentPath).isNull()
        assertThat(state5.canGoBack).isFalse()
    }

    @Test
    fun testSelectionMode() {
        val manager = FolderStateManager()
        val v1 = createTestVideo("1", "vid.mp4", "/storage/emulated/0/Movies")
        val state = manager.computeUiState(listOf(v1))

        assertThat(state.isSelectionMode).isFalse()

        // Toggle folder selection
        manager.toggleFolderSelection("/storage/emulated/0/Movies")
        val stateSel = manager.computeUiState(listOf(v1))
        assertThat(stateSel.isSelectionMode).isTrue()
        assertThat(stateSel.selectedFolderPaths).containsExactly("/storage/emulated/0/Movies")

        // Clear selection
        manager.clearSelection()
        val stateCleared = manager.computeUiState(listOf(v1))
        assertThat(stateCleared.isSelectionMode).isFalse()
        assertThat(stateCleared.selectedFolderPaths).isEmpty()
    }

    @Test
    fun testResolveDescendantVideos() {
        val manager = FolderStateManager()
        val v1 = createTestVideo("1", "a.mp4", "/storage/emulated/0/Movies/Action")
        val v2 = createTestVideo("2", "b.mp4", "/storage/emulated/0/Movies/Drama")
        val v3 = createTestVideo("3", "c.mp4", "/storage/emulated/0/Downloads")
        manager.computeUiState(listOf(v1, v2, v3))

        val resolved = manager.resolveDescendantVideos(listOf("/storage/emulated/0/Movies"))
        assertThat(resolved.map { it.id }).containsExactly("1", "2")
    }
}
