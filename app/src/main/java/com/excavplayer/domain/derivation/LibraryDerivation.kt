package com.excavplayer.domain.derivation

import com.excavplayer.domain.model.Folder
import com.excavplayer.domain.model.NaturalVideoComparator
import com.excavplayer.domain.model.Video

/**
 * Pure library derivation: series grouping, folder mapping, path normalization,
 * and the exclusion rule. No Android imports — fully unit-testable.
 *
 * ## Exclusion condition table
 *
 * The rule "which videos must never appear in the library" existed as ~6 copies.
 * This predicate is now the single owner. Divergences found during unification:
 *
 * | Copy | Scope | Notes |
 * |---|---|---|
 * | FolderTreeManager.buildTree filter | richest (this union) | source of the union below |
 * | VideoDao SQL (4 sites) | "SAF Files" name/path only | Room SQL cannot call Kotlin; kept as a guarded backstop, see mirror comments in VideoDao |
 * | MediaStoreDataSource row filter | `/storage/private` on the raw `data` column | also checks `contains("/storage/private/")` — the stricter containment is folded into [isExcludedFolderPath] |
 * | StorageFileScanner | `/storage/private` + traversal pruning | traversal sets (data/obb/cache/...) are scan-time concerns and stay local to the scanner |
 * | ExcavApp restore guard | SAF Files / External Videos / Selected Folder / /storage/private on a saved path | folded into [isExcludedFolderPath] |
 * | UiState (former home of this file) | normalizeFolderPath only | normalization moved here |
 */
object LibraryDerivation {

    const val INTERNAL_STORAGE_ROOT = "/storage/emulated/0"
    const val INTERNAL_STORAGE_LABEL = "Internal Storage"

    /** OEM trash/recycle folder segments (Xiaomi, Samsung, Huawei, Oppo, Vivo, etc.). */
    private val TRASH_SEGMENTS = setOf(
        ".trash", ".trashed", "trash", ".trashbin", "trashbin",
        ".recycle", "recycle", ".recycler", "recycler",
        ".securetrash", "recently_deleted", ".recently_deleted",
        ".gallery_trash", "gallery_trash"
    )

    /** Special folders that never contain library videos. */
    private val EXCLUDED_FOLDER_NAMES = setOf(
        "External Videos", "SAF Files", "Selected Folder", "private"
    )

    /**
     * Sentinel folder of transient shared-in videos (played without library
     * registration). Excluded only on the exact name+path pair so real user
     * folders that merely contain the word "External" are untouched.
     */
    const val TRANSIENT_EXTERNAL_FOLDER = "External"

    private val NOISE_TAGS = setOf(
        "1080p", "720p", "480p", "360p", "2160p", "4k", "uhd", "fhd", "hd",
        "x264", "x265", "h264", "h265", "hevc", "avc", "10bit", "8bit",
        "web-dl", "webrip", "bluray", "bdrip", "dvdrip", "hdtv", "hdrip",
        "aac", "aac2", "dts", "ac3", "ddp5", "ddp", "eac3", "flac", "mp3",
        "repack", "proper", "remux", "dual", "multi", "eng", "ita", "sub",
        "dub", "uncensored", "directors", "cut", "extended", "complete"
    )

    private val GENERIC_FOLDER_NAMES = setOf(
        "0", "emulated", "storage", "internal storage", "videos", "video",
        "dcim", "camera", "download", "downloads", "telegram", "whatsapp video",
        "movies", "movie", "series", "tv shows", "tv", "media"
    )

    /**
     * Normalizes directory and file paths to canonical absolute Unix paths.
     */
    fun normalizeFolderPath(raw: String?): String {
        var trimmed = raw.orEmpty().trim().trimEnd('/')
        if (trimmed.startsWith("/sdcard")) {
            trimmed = INTERNAL_STORAGE_ROOT + trimmed.removePrefix("/sdcard")
        } else if (trimmed.startsWith("/mnt/sdcard")) {
            trimmed = INTERNAL_STORAGE_ROOT + trimmed.removePrefix("/mnt/sdcard")
        }
        return when {
            trimmed.isEmpty() ||
            trimmed == INTERNAL_STORAGE_ROOT ||
            trimmed == "/storage/emulated" ||
            trimmed.equals(INTERNAL_STORAGE_LABEL, ignoreCase = true) -> INTERNAL_STORAGE_ROOT
            trimmed.startsWith(INTERNAL_STORAGE_ROOT) -> trimmed
            trimmed.startsWith("/storage/") -> trimmed
            trimmed.startsWith("/") -> trimmed
            else -> "$INTERNAL_STORAGE_ROOT/$trimmed"
        }
    }

    /**
     * The library exclusion rule, video level. Mirrored in SQL in VideoDao —
     * update both together (see mirror comments there).
     */
    fun isExcludedFromLibrary(video: Video): Boolean {
        return video.id.startsWith("saf_") ||
            video.id.startsWith("transient_") ||
            (video.folderName == TRANSIENT_EXTERNAL_FOLDER && video.folderPath == TRANSIENT_EXTERNAL_FOLDER) ||
            isExcludedFolderPath(video.folderPath) ||
            isExcludedFolderName(video.folderName) ||
            video.uri.startsWith("/storage/private") ||
            video.uri.startsWith("content://com.android.providers.media.documents/") ||
            video.uri.startsWith("content://com.android.externalstorage.documents/")
    }

    /** Path-level exclusion (used for raw file paths and saved/restore paths). */
    fun isExcludedFolderPath(path: String?): Boolean {
        val p = path.orEmpty()
        if (p.isEmpty()) return false
        if (EXCLUDED_FOLDER_NAMES.any { p.equals(it, ignoreCase = true) || p.contains(it, ignoreCase = true) }) return true
        if (p.equals("private", ignoreCase = true) || p.endsWith("/private")) return true
        if (p.startsWith("/storage/private") || p.contains("/storage/private/")) return true
        return p.lowercase().split('/').any { it in TRASH_SEGMENTS }
    }

    /** Name-level exclusion (a folder's immediate name). */
    fun isExcludedFolderName(name: String?): Boolean {
        val n = name.orEmpty()
        return EXCLUDED_FOLDER_NAMES.any { n.equals(it, ignoreCase = true) }
    }

    fun cleanSeriesTitle(raw: String): String {
        var name = raw.trim()
        name = name.replace(Regex("""^\s*\[[^\]]+\]\s*"""), "")
        name = name.replace(Regex("""^\s*\([^)]+\)\s*"""), "")
        if (name.contains('.')) {
            val ext = name.substringAfterLast('.').lowercase()
            if (ext in setOf("mp4", "mkv", "avi", "webm", "mov", "flv", "ts", "m4v", "3gp")) {
                name = name.substringBeforeLast('.')
            }
        }
        name = name.replace('.', ' ').replace('_', ' ')

        val tokens = name.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val cleanTokens = mutableListOf<String>()
        for (token in tokens) {
            val lower = token.lowercase().trim('(', ')', '[', ']', '{', '}', '-', '_', '.')
            if (NOISE_TAGS.contains(lower) || lower.matches(Regex("""\d{3,4}p""")) || (lower.length == 4 && lower.startsWith("20") && lower.toIntOrNull() != null && cleanTokens.isNotEmpty())) {
                break
            }
            val cleaned = token.trim('(', ')', '[', ']', '{', '}', '-', '_', '.')
            if (cleaned.isNotBlank()) {
                cleanTokens.add(cleaned)
            }
        }

        val result = cleanTokens.joinToString(" ").trim()
        return if (result.length >= 2) {
            result.split(" ").joinToString(" ") { word ->
                if (word.all { it.isUpperCase() } && word.length <= 4) word
                else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } else {
            raw.trim()
        }
    }

    fun extractSeriesSeason(displayName: String, folderName: String): Pair<String, String>? {
        val cleanName = displayName.trim()

        // Pattern 1: S11E01 / S11.E01 / S11_E01 / S11 - E01 / Season 11 Episode 01
        val sMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})[.\s_\-]*(?:e|ep|episode)[.\s_\-]*\d{1,3}""", RegexOption.IGNORE_CASE).find(cleanName)
        if (sMatch != null) {
            val rawSeries = sMatch.groupValues[1]
            val seasonNum = sMatch.groupValues[2].toIntOrNull()
            val seriesTitle = cleanSeriesTitle(rawSeries)
            if (seriesTitle.isNotBlank()) {
                val seasonStr = if (seasonNum != null) "S$seasonNum" else ""
                val key = "${seriesTitle.lowercase()}_s${seasonNum ?: 0}"
                val display = if (seasonNum != null) "$seriesTitle $seasonStr" else seriesTitle
                return Pair(key, display)
            }
        }

        // Pattern 2: 11x01 (e.g. Supernatural.11x01.mp4)
        val xMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(\d{1,2})x\d{1,3}""", RegexOption.IGNORE_CASE).find(cleanName)
        if (xMatch != null) {
            val rawSeries = xMatch.groupValues[1]
            val seasonNum = xMatch.groupValues[2].toIntOrNull()
            val seriesTitle = cleanSeriesTitle(rawSeries)
            if (seriesTitle.isNotBlank() && seasonNum != null) {
                val key = "${seriesTitle.lowercase()}_s$seasonNum"
                val display = "$seriesTitle S$seasonNum"
                return Pair(key, display)
            }
        }

        // Pattern 3: S11 / Season 11 alone in file name
        val sOnlyMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})(?:[.\s_\-]|\b)""", RegexOption.IGNORE_CASE).find(cleanName)
        if (sOnlyMatch != null) {
            val rawSeries = sOnlyMatch.groupValues[1]
            val seasonNum = sOnlyMatch.groupValues[2].toIntOrNull()
            val seriesTitle = cleanSeriesTitle(rawSeries)
            if (seriesTitle.isNotBlank() && seasonNum != null) {
                val key = "${seriesTitle.lowercase()}_s$seasonNum"
                val display = "$seriesTitle S$seasonNum"
                return Pair(key, display)
            }
        }

        // Pattern 4: Anime episode format: [SubsPlease] Show - 01 (1080p).mkv
        val animeMatch = Regex("""^(?:\[[^\]]+\]\s*)?(.+?)\s*[-_]\s*(?:(?:ep|episode)\s*)?(\d{1,4})(?:\s*\(.*?\))?(?:\s*\[.*?\])?(?:\.[\w\d]+)?$""", RegexOption.IGNORE_CASE).find(cleanName)
        if (animeMatch != null) {
            val rawSeries = animeMatch.groupValues[1]
            val seriesTitle = cleanSeriesTitle(rawSeries)
            if (seriesTitle.length >= 2 && !seriesTitle.all { it.isDigit() }) {
                val folderSeason = Regex("""(?i)(?:s|season)[.\s_\-]*(\d{1,2})""").find(folderName)?.groupValues?.get(1)?.toIntOrNull()
                val key = if (folderSeason != null) "${seriesTitle.lowercase()}_s$folderSeason" else seriesTitle.lowercase()
                val display = if (folderSeason != null) "$seriesTitle S$folderSeason" else seriesTitle
                return Pair(key, display)
            }
        }

        // Pattern 5: Check folder name for Series/Season
        if (folderName.isNotBlank() && !GENERIC_FOLDER_NAMES.contains(folderName.lowercase().trim())) {
            val folderSeasonMatch = Regex("""(?i)(?:^|[\s._\-\(\[])(.+?)[.\s_\-]+(?:s|season)[.\s_\-]*(\d{1,2})""", RegexOption.IGNORE_CASE).find(folderName)
            if (folderSeasonMatch != null) {
                val rawSeries = folderSeasonMatch.groupValues[1]
                val seasonNum = folderSeasonMatch.groupValues[2].toIntOrNull()
                val seriesTitle = cleanSeriesTitle(rawSeries)
                if (seriesTitle.isNotBlank() && seasonNum != null) {
                    return Pair("${seriesTitle.lowercase()}_s$seasonNum", "$seriesTitle S$seasonNum")
                }
            }
        }

        return null
    }

    fun computeVideoGroups(videos: List<Video>): List<VideoGroup> {
        if (videos.isEmpty()) return emptyList()

        val groups = mutableListOf<VideoGroup>()

        // Pass 1: Smart Series & Season pattern extraction
        val seriesSeasonBuckets = mutableMapOf<String, Pair<String, MutableList<Video>>>()
        for (video in videos) {
            val extracted = extractSeriesSeason(video.displayName, video.folderName)
            if (extracted != null) {
                val (key, display) = extracted
                val bucket = seriesSeasonBuckets.getOrPut(key) { Pair(display, mutableListOf()) }
                bucket.second.add(video)
            }
        }

        for ((key, pair) in seriesSeasonBuckets) {
            val (displayName, vids) = pair
            if (vids.size >= 2) {
                val sortedVideos = vids.distinctBy { it.id }.sortedWith(NaturalVideoComparator)
                groups.add(
                    VideoGroup(
                        id = "group_$key",
                        name = displayName,
                        path = sortedVideos.firstOrNull()?.folderPath.orEmpty(),
                        videos = sortedVideos
                    )
                )
            }
        }

        return groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun computeFolderVideosMap(videos: List<Video>): Map<String, List<Video>> {
        if (videos.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, MutableList<Video>>()
        for (v in videos) {
            val norm = normalizeFolderPath(v.folderPath)
            map.getOrPut(norm) { mutableListOf() }.add(v)
            if (v.folderName.isNotEmpty()) {
                map.getOrPut(v.folderName.lowercase()) { mutableListOf() }.add(v)
            }
        }
        return map.mapValues { (_, list) -> list.sortedWith(NaturalVideoComparator) }
    }

    /**
     * Groups + folder map in one call, memoized on list identity — the combine
     * block in the viewmodel re-fires on any flow change, but grouping only
     * depends on the videos list, so identical list instances reuse the result.
     */
    fun derive(videos: List<Video>, folders: List<Folder>): LibraryDerivedState {
        val key = videos to folders
        val cachedKey = memoKey
        if (cachedKey != null && cachedKey.first === key.first && cachedKey.second === key.second) {
            return memoValue
        }
        val fresh = LibraryDerivedState(computeVideoGroups(videos), computeFolderVideosMap(videos))
        memoKey = key
        memoValue = fresh
        return fresh
    }

    private var memoKey: Pair<List<Video>, List<Folder>>? = null
    private var memoValue = LibraryDerivedState(emptyList(), emptyMap())
}

data class VideoGroup(
    val id: String,
    val name: String,
    val path: String,
    val videos: List<Video>
)

data class LibraryDerivedState(
    val groups: List<VideoGroup>,
    val folderVideosMap: Map<String, List<Video>>
)
