package com.excavplayer.folders

import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.excavplayer.ui.folders.logic.FolderTreeManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderTreeManagerTest {

    private fun createTestVideo(
        id: String,
        displayName: String,
        folderPath: String,
        sizeBytes: Long = 1000L
    ): Video {
        return Video(
            id = id,
            uri = "content://media/external/video/media/$id",
            displayName = displayName,
            mimeType = "video/mp4",
            durationMs = 60000L,
            sizeBytes = sizeBytes,
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
    fun testNormalizePath() {
        assertThat(FolderTreeManager.normalizePath(null))
            .isEqualTo("/storage/emulated/0")
        assertThat(FolderTreeManager.normalizePath(""))
            .isEqualTo("/storage/emulated/0")
        assertThat(FolderTreeManager.normalizePath("/storage/emulated/0/"))
            .isEqualTo("/storage/emulated/0")
        assertThat(FolderTreeManager.normalizePath("Internal Storage"))
            .isEqualTo("/storage/emulated/0")
        assertThat(FolderTreeManager.normalizePath("Movies/Action"))
            .isEqualTo("/storage/emulated/0/Movies/Action")
        assertThat(FolderTreeManager.normalizePath("/storage/emulated/0/Movies/Action/"))
            .isEqualTo("/storage/emulated/0/Movies/Action")
        assertThat(FolderTreeManager.normalizePath("/storage/1234-5678/Videos/"))
            .isEqualTo("/storage/1234-5678/Videos")
    }

    @Test
    fun testGetParentPath() {
        assertThat(FolderTreeManager.getParentPath("/storage/emulated/0"))
            .isNull()
        assertThat(FolderTreeManager.getParentPath("/storage/emulated/0/Movies"))
            .isEqualTo("/storage/emulated/0")
        assertThat(FolderTreeManager.getParentPath("/storage/emulated/0/Movies/Action"))
            .isEqualTo("/storage/emulated/0/Movies")
        assertThat(FolderTreeManager.getParentPath("/storage/1234-5678/Videos"))
            .isEqualTo("/storage/1234-5678")
    }

    @Test
    fun testBuildTree_hierarchicalAggregations() {
        val v1 = createTestVideo("1", "vid1.mp4", "/storage/emulated/0/Movies/Action", sizeBytes = 100L)
        val v2 = createTestVideo("2", "vid2.mp4", "/storage/emulated/0/Movies/Comedy", sizeBytes = 200L)
        val v3 = createTestVideo("3", "root_vid.mp4", "/storage/emulated/0", sizeBytes = 50L)
        val v4 = createTestVideo("4", "dcim.mp4", "/storage/emulated/0/DCIM/Camera", sizeBytes = 300L)

        val tree = FolderTreeManager.buildTree(listOf(v1, v2, v3, v4))

        // Check root subfolders: should be DCIM and Movies
        val rootSubfolders = FolderTreeManager.getSubfolders(tree, null)
        assertThat(rootSubfolders.map { it.name }).containsExactly("DCIM", "Movies").inOrder()

        // Movies should aggregate Action + Comedy = 2 videos, 300 bytes
        val moviesFolder = rootSubfolders.first { it.name == "Movies" }
        assertThat(moviesFolder.videoCount).isEqualTo(2)
        assertThat(moviesFolder.totalSizeBytes).isEqualTo(300L)

        // Subfolders of Movies should be Action and Comedy
        val moviesChildren = FolderTreeManager.getSubfolders(tree, "/storage/emulated/0/Movies")
        assertThat(moviesChildren.map { it.name }).containsExactly("Action", "Comedy").inOrder()
        val actionFolder = moviesChildren.first { it.name == "Action" }
        assertThat(actionFolder.videoCount).isEqualTo(1)
        assertThat(actionFolder.totalSizeBytes).isEqualTo(100L)

        // Direct videos at /storage/emulated/0/Movies/Action should be vid1
        val actionVideos = FolderTreeManager.getDirectVideos(tree, "/storage/emulated/0/Movies/Action")
        assertThat(actionVideos.map { it.displayName }).containsExactly("vid1.mp4")

        // Direct videos at /storage/emulated/0 should be root_vid
        val rootVideos = FolderTreeManager.getDirectVideos(tree, null)
        assertThat(rootVideos.map { it.displayName }).containsExactly("root_vid.mp4")
    }

    @Test
    fun testBreadcrumbs() {
        val crumbs = FolderTreeManager.getBreadcrumbs("/storage/emulated/0/Movies/Action")
        assertThat(crumbs.map { it.title }).containsExactly("Internal Storage", "Movies", "Action").inOrder()
        assertThat(crumbs.last().path).isEqualTo("/storage/emulated/0/Movies/Action")
    }

    @Test
    fun testGetAllDescendantVideos() {
        val v1 = createTestVideo("1", "v1.mp4", "/storage/emulated/0/Movies/Action")
        val v2 = createTestVideo("2", "v2.mp4", "/storage/emulated/0/Movies/Drama")
        val v3 = createTestVideo("3", "v3.mp4", "/storage/emulated/0/DCIM")

        val tree = FolderTreeManager.buildTree(listOf(v1, v2, v3))

        val movieVideos = FolderTreeManager.getAllDescendantVideos(tree, "/storage/emulated/0/Movies")
        assertThat(movieVideos.map { it.id }).containsExactly("1", "2")
    }
}
