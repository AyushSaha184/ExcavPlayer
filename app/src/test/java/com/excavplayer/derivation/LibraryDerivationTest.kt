package com.excavplayer.derivation

import com.excavplayer.domain.derivation.LibraryDerivation
import com.excavplayer.domain.derivation.VideoGroup
import com.excavplayer.domain.model.MediaAvailability
import com.excavplayer.domain.model.MediaSourceType
import com.excavplayer.domain.model.Video
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryDerivationTest {

    private fun createTestVideo(
        id: String,
        displayName: String,
        folderPath: String,
        folderName: String = folderPath.substringAfterLast('/'),
        uri: String = "content://media/external/video/media/$id",
        sizeBytes: Long = 1000L
    ): Video {
        return Video(
            id = id,
            uri = uri,
            displayName = displayName,
            mimeType = "video/mp4",
            durationMs = 60000L,
            sizeBytes = sizeBytes,
            dateAddedSeconds = 1700000000L,
            dateModifiedSeconds = 1700000000L,
            width = 1920,
            height = 1080,
            folderPath = folderPath,
            folderName = folderName,
            sourceType = MediaSourceType.LOCAL_MEDIASTORE,
            availability = MediaAvailability.AVAILABLE
        )
    }

    // ── Normalization ────────────────────────────────────────────────

    @Test
    fun `normalizeFolderPath canonicalizes null, empty, and root variants`() {
        assertThat(LibraryDerivation.normalizeFolderPath(null)).isEqualTo("/storage/emulated/0")
        assertThat(LibraryDerivation.normalizeFolderPath("")).isEqualTo("/storage/emulated/0")
        assertThat(LibraryDerivation.normalizeFolderPath("/storage/emulated/0/")).isEqualTo("/storage/emulated/0")
        assertThat(LibraryDerivation.normalizeFolderPath("Internal Storage")).isEqualTo("/storage/emulated/0")
        assertThat(LibraryDerivation.normalizeFolderPath("/storage/emulated")).isEqualTo("/storage/emulated/0")
    }

    @Test
    fun `normalizeFolderPath maps sdcard aliases`() {
        assertThat(LibraryDerivation.normalizeFolderPath("/sdcard/Movies")).isEqualTo("/storage/emulated/0/Movies")
        assertThat(LibraryDerivation.normalizeFolderPath("/mnt/sdcard/DCIM")).isEqualTo("/storage/emulated/0/DCIM")
    }

    @Test
    fun `normalizeFolderPath keeps relative and storage-root paths`() {
        assertThat(LibraryDerivation.normalizeFolderPath("Movies/Action")).isEqualTo("/storage/emulated/0/Movies/Action")
        assertThat(LibraryDerivation.normalizeFolderPath("/storage/1234-5678/Videos/")).isEqualTo("/storage/1234-5678/Videos")
        assertThat(LibraryDerivation.normalizeFolderPath("/storage/emulated/0/Movies/Action/")).isEqualTo("/storage/emulated/0/Movies/Action")
    }

    // ── Exclusion: video level ────────────────────────────────────────

    @Test
    fun `isExcludedFromLibrary keeps normal videos`() {
        val video = createTestVideo("1", "movie.mp4", "/storage/emulated/0/Movies")
        assertThat(LibraryDerivation.isExcludedFromLibrary(video)).isFalse()
    }

    @Test
    fun `isExcludedFromLibrary excludes saf and transient ids`() {
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("saf_doc_1", "a.mp4", "/x"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("transient_9", "b.mp4", "/x"))).isTrue()
    }

    @Test
    fun `isExcludedFromLibrary excludes special folder names case-insensitively`() {
        for (name in listOf("External Videos", "SAF Files", "Selected Folder", "private")) {
            assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/somewhere", folderName = name.lowercase())))
                .isTrue()
        }
    }

    @Test
    fun `isExcludedFromLibrary excludes special folder paths`() {
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/emulated/0/SAF Files/sub"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "External Videos"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/emulated/0/Selected Folder"))).isTrue()
    }

    @Test
    fun `isExcludedFromLibrary excludes private storage paths and uris`() {
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/private"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/private/sub"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/x", uri = "/storage/private/1.mp4"))).isTrue()
        assertThat(
            LibraryDerivation.isExcludedFromLibrary(
                createTestVideo("1", "a.mp4", "/x", uri = "content://com.android.providers.media.documents/document/1")
            )
        ).isTrue()
        assertThat(
            LibraryDerivation.isExcludedFromLibrary(
                createTestVideo("1", "a.mp4", "/x", uri = "content://com.android.externalstorage.documents/tree/1")
            )
        ).isTrue()
    }

    @Test
    fun `isExcludedFromLibrary excludes trash and recycle folders`() {
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/emulated/0/.Trash/x"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/emulated/0/Recycle/file.mp4"))).isTrue()
        assertThat(LibraryDerivation.isExcludedFromLibrary(createTestVideo("1", "a.mp4", "/storage/emulated/0/Recently_Deleted"))).isTrue()
    }

    // ── Exclusion: path and name level ───────────────────────────────

    @Test
    fun `isExcludedFolderPath guards restore paths`() {
        assertThat(LibraryDerivation.isExcludedFolderPath("SAF Files")).isTrue()
        assertThat(LibraryDerivation.isExcludedFolderPath("/storage/emulated/0/SAF Files/sub")).isTrue()
        assertThat(LibraryDerivation.isExcludedFolderPath("/storage/private")).isTrue()
        assertThat(LibraryDerivation.isExcludedFolderPath("some/path/storage/private/x.mp4")).isTrue()
        assertThat(LibraryDerivation.isExcludedFolderPath("/storage/emulated/0/Movies")).isFalse()
        assertThat(LibraryDerivation.isExcludedFolderPath("Internal Storage")).isFalse()
        assertThat(LibraryDerivation.isExcludedFolderPath(null)).isFalse()
        assertThat(LibraryDerivation.isExcludedFolderPath("")).isFalse()
    }

    @Test
    fun `isExcludedFolderName matches exactly`() {
        assertThat(LibraryDerivation.isExcludedFolderName("saf files")).isTrue()
        assertThat(LibraryDerivation.isExcludedFolderName("My Movies")).isFalse()
        assertThat(LibraryDerivation.isExcludedFolderName("")).isFalse()
    }

    // ── Series extraction: one test per strategy ─────────────────────

    @Test
    fun `pattern 1 extracts SxxEyy season-episode titles`() {
        val result = LibraryDerivation.extractSeriesSeason("Show.Name.S01E05.720p.x264.mp4", "downloads")
        assertThat(result).isNotNull()
        assertThat(result!!.first).isEqualTo("show name_s1")
        assertThat(result.second).isEqualTo("Show Name S1")
    }

    @Test
    fun `pattern 1 handles Season-Episode phrasing`() {
        val result = LibraryDerivation.extractSeriesSeason("Gravity Falls - Season 2 Episode 3.mp4", "shows")
        assertThat(result!!.first).isEqualTo("gravity falls_s2")
    }

    @Test
    fun `pattern 2 extracts NxNN titles`() {
        val result = LibraryDerivation.extractSeriesSeason("Supernatural.11x01.mp4", "tv")
        assertThat(result!!.first).isEqualTo("supernatural_s11")
        assertThat(result.second).isEqualTo("Supernatural S11")
    }

    @Test
    fun `pattern 3 extracts season-only titles`() {
        val result = LibraryDerivation.extractSeriesSeason("Friends.S05.Remastered.mp4", "tv")
        assertThat(result!!.first).isEqualTo("friends_s5")
    }

    @Test
    fun `pattern 4 extracts anime bracket titles and falls back to folder season`() {
        val noFolder = LibraryDerivation.extractSeriesSeason("[SubsPlease] Show - 01 (1080p).mkv", "downloads")
        assertThat(noFolder!!.first).isEqualTo("show")
        assertThat(noFolder.second).isEqualTo("Show")

        val withFolder = LibraryDerivation.extractSeriesSeason("[SubsPlease] Show - 01 (1080p).mkv", "Show Season 2")
        assertThat(withFolder!!.first).isEqualTo("show_s2")
    }

    @Test
    fun `pattern 5 extracts series from folder name when file name has no marker`() {
        val result = LibraryDerivation.extractSeriesSeason("Episode 01.mp4", "Breaking Bad S03")
        assertThat(result!!.first).isEqualTo("breaking bad_s3")
    }

    @Test
    fun `extractSeriesSeason returns null when nothing matches`() {
        assertThat(LibraryDerivation.extractSeriesSeason("randomvideo.mp4", "Movies")).isNull()
        assertThat(LibraryDerivation.extractSeriesSeason("birthday.mp4", "")).isNull()
    }

    // ── cleanSeriesTitle ─────────────────────────────────────────────

    @Test
    fun `cleanSeriesTitle strips noise tags and release junk`() {
        assertThat(LibraryDerivation.cleanSeriesTitle("Attack On Titan [1080p] x265 BluRay.mp4"))
            .isEqualTo("Attack On Titan")
    }

    @Test
    fun `cleanSeriesTitle stops at year tokens`() {
        assertThat(LibraryDerivation.cleanSeriesTitle("Movie 2019 Extra Footage")).isEqualTo("Movie")
    }

    @Test
    fun `cleanSeriesTitle preserves short uppercase words`() {
        assertThat(LibraryDerivation.cleanSeriesTitle("NASA Missions")).contains("NASA")
    }

    @Test
    fun `cleanSeriesTitle falls back to raw when nothing survives`() {
        assertThat(LibraryDerivation.cleanSeriesTitle("a")).isEqualTo("a")
    }

    // ── Grouping ─────────────────────────────────────────────────────

    @Test
    fun `computeVideoGroups returns empty for empty input`() {
        assertThat(LibraryDerivation.computeVideoGroups(emptyList())).isEmpty()
    }

    @Test
    fun `computeVideoGroups does not group single episodes`() {
        val videos = listOf(createTestVideo("1", "Show.S01E01.mp4", "/x"))
        assertThat(LibraryDerivation.computeVideoGroups(videos)).isEmpty()
    }

    @Test
    fun `computeVideoGroups groups two episodes of a series sorted naturally`() {
        val v1 = createTestVideo("1", "Show.S01E02.mp4", "/x")
        val v2 = createTestVideo("2", "Show.S01E01.mp4", "/x")
        val groups = LibraryDerivation.computeVideoGroups(listOf(v1, v2))
        assertThat(groups).hasSize(1)
        assertThat(groups.first().videos.map { it.displayName })
            .containsExactly("Show.S01E01.mp4", "Show.S01E02.mp4").inOrder()
        assertThat(groups.first().name).isEqualTo("Show S1")
    }

    @Test
    fun `computeVideoGroups sorts groups case-insensitively by name`() {
        val a = listOf(
            createTestVideo("1", "Zeta.S01E01.mp4", "/x"),
            createTestVideo("2", "Zeta.S01E02.mp4", "/x"),
            createTestVideo("3", "alpha.S01E01.mp4", "/y"),
            createTestVideo("4", "alpha.S01E02.mp4", "/y")
        )
        val groups = LibraryDerivation.computeVideoGroups(a)
        assertThat(groups.map { it.name }).containsExactly("Alpha S1", "Zeta S1").inOrder()
    }

    // ── Folder map ───────────────────────────────────────────────────

    @Test
    fun `computeFolderVideosMap indexes by normalized path and lowercase folder name`() {
        val v = createTestVideo("1", "a.mp4", "/storage/emulated/0/Movies/Foo")
        val map = LibraryDerivation.computeFolderVideosMap(listOf(v))
        assertThat(map).containsKey("/storage/emulated/0/Movies/Foo")
        assertThat(map).containsKey("foo")
        assertThat(map["/storage/emulated/0/Movies/Foo"]).hasSize(1)
    }

    @Test
    fun `computeFolderVideosMap returns empty for empty input`() {
        assertThat(LibraryDerivation.computeFolderVideosMap(emptyList())).isEmpty()
    }

    // ── Memoized derive ──────────────────────────────────────────────

    @Test
    fun `derive reuses result for the same list instance`() {
        val videos = listOf(createTestVideo("1", "Show.S01E01.mp4", "/x"), createTestVideo("2", "Show.S01E02.mp4", "/x"))
        val first = LibraryDerivation.derive(videos, emptyList())
        val second = LibraryDerivation.derive(videos, emptyList())
        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `derive recomputes for a fresh list instance`() {
        val videos = listOf(createTestVideo("1", "Show.S01E01.mp4", "/x"), createTestVideo("2", "Show.S01E02.mp4", "/x"))
        val first = LibraryDerivation.derive(videos, emptyList())
        val sameContentDifferentInstance = listOf(videos[0], videos[1])
        val second = LibraryDerivation.derive(sameContentDifferentInstance, emptyList())
        assertThat(first).isNotSameInstanceAs(second)
        assertThat(second.groups.first().name).isEqualTo(first.groups.first().name)
    }

    @Test
    fun `derive yields groups and folder map together`() {
        val videos = listOf(
            createTestVideo("1", "Show.S01E01.mp4", "/storage/emulated/0/Shows"),
            createTestVideo("2", "Show.S01E02.mp4", "/storage/emulated/0/Shows")
        )
        val state = LibraryDerivation.derive(videos, emptyList())
        assertThat(state.groups).hasSize(1)
        assertThat(state.groups.first() is VideoGroup).isTrue()
        assertThat(state.folderVideosMap).containsKey("/storage/emulated/0/Shows")
    }
}
