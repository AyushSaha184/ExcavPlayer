package com.excavplayer.subtitles

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleSearchService @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SubtitleSearchService"
        private const val OPENSUBTITLES_API_BASE = "https://api.opensubtitles.com/api/v1"
        // Default API key for OpenSubtitles REST v1
        private const val OPENSUBTITLES_DEFAULT_KEY = "e6e3zM1Xq5KzG8f7E4B2j9L3p6V1m9Nx"
        private const val SUBDL_API_BASE = "https://api.subdl.com/api/v1/subtitles"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun searchSubtitles(
        query: String,
        languageCode: String = "en",
        source: SubtitleSource = SubtitleSource.ALL
    ): List<SubtitleSearchResult> = withContext(dispatchers.io) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        logger.i(TAG, "Searching subtitles for '$cleanQuery', lang='$languageCode', source='${source.displayName}'")

        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<SubtitleSearchResult>>>()

            if (source == SubtitleSource.ALL || source == SubtitleSource.OPENSUBTITLES) {
                jobs.add(async { searchOpenSubtitles(cleanQuery, languageCode) })
                jobs.add(async { searchOpenSubtitlesWeb(cleanQuery, languageCode) })
            }

            if (source == SubtitleSource.ALL || source == SubtitleSource.SUBDL) {
                jobs.add(async { searchSubDL(cleanQuery, languageCode) })
            }

            if (source == SubtitleSource.ALL || source == SubtitleSource.YIFY) {
                jobs.add(async { searchYifySubs(cleanQuery, languageCode) })
            }

            val results = jobs.flatMap { deferred ->
                try {
                    deferred.await()
                } catch (e: Exception) {
                    logger.w(TAG, "Search provider error: ${e.message}")
                    emptyList()
                }
            }

            // Deduplicate by title & language and sort
            results.distinctBy { "${it.source}_${it.title}_${it.language}_${it.downloadUrl}" }
                .sortedWith(
                    compareByDescending<SubtitleSearchResult> {
                        if (languageCode != "all" && it.languageCode.equals(languageCode, ignoreCase = true)) 2 else 1
                    }.thenByDescending { it.downloads ?: 0 }
                )
        }
    }

    /**
     * Search via OpenSubtitles.com REST API
     */
    private fun searchOpenSubtitles(query: String, languageCode: String): List<SubtitleSearchResult> {
        val results = mutableListOf<SubtitleSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val langParam = if (languageCode == "all") "" else "&languages=$languageCode"
            val urlString = "$OPENSUBTITLES_API_BASE/subtitles?query=$encodedQuery$langParam"

            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "ExcavPlayer v1.0.0")
                setRequestProperty("Api-Key", OPENSUBTITLES_DEFAULT_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.use { it.reader().readText() }
                val root = json.parseToJsonElement(responseText).jsonObject
                val data = root["data"]?.jsonArray ?: JsonArray(emptyList())

                for (item in data) {
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val attributes = obj["attributes"]?.jsonObject ?: continue

                    val language = attributes["language"]?.jsonPrimitive?.contentOrNull ?: "English"
                    val release = attributes["release"]?.jsonPrimitive?.contentOrNull
                        ?: attributes["feature_details"]?.jsonObject?.get("movie_name")?.jsonPrimitive?.contentOrNull
                        ?: query
                    val files = attributes["files"]?.jsonArray
                    val firstFile = files?.firstOrNull()?.jsonObject
                    val fileId = firstFile?.get("file_id")?.jsonPrimitive?.intOrNull
                        ?: firstFile?.get("file_id")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val fileName = firstFile?.get("file_name")?.jsonPrimitive?.contentOrNull
                        ?: "$release.srt"
                    val format = attributes["format"]?.jsonPrimitive?.contentOrNull ?: "srt"
                    val downloads = attributes["download_count"]?.jsonPrimitive?.intOrNull
                    val hearingImpaired = attributes["hearing_impaired"]?.jsonPrimitive?.contentOrNull == "true"
                    val ratings = attributes["ratings"]?.jsonPrimitive?.contentOrNull

                    if (fileId != null) {
                        results.add(
                            SubtitleSearchResult(
                                id = "os_$fileId",
                                source = "OpenSubtitles",
                                title = release,
                                fileName = fileName,
                                language = language,
                                languageCode = languageCode,
                                downloadUrl = "https://api.opensubtitles.com/api/v1/download?file_id=$fileId",
                                format = format,
                                hearingImpaired = hearingImpaired,
                                rating = ratings,
                                downloads = downloads
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.d(TAG, "OpenSubtitles REST API search skipped/failed: ${e.message}")
        }
        return results
    }

    /**
     * Search via OpenSubtitles Web endpoint
     */
    private fun searchOpenSubtitlesWeb(query: String, languageCode: String): List<SubtitleSearchResult> {
        val results = mutableListOf<SubtitleSearchResult>()
        try {
            val langSub = if (languageCode == "all") "all" else languageCode
            val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val urlString = "https://www.opensubtitles.org/en/search2/sublanguageid-$langSub/moviename-$encodedQuery"

            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0")
            }

            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.use { it.reader().readText() }
                // Parse download links and title rows from HTML
                val regex = Regex("""href=["'](/en/subtitleserve/sub/(\d+))["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
                val matches = regex.findAll(html)

                for (match in matches.take(15)) {
                    val subPath = match.groupValues[1]
                    val subId = match.groupValues[2]
                    val rawTitle = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
                    if (rawTitle.isNotBlank()) {
                        results.add(
                            SubtitleSearchResult(
                                id = "os_web_$subId",
                                source = "OpenSubtitles",
                                title = rawTitle,
                                fileName = "$rawTitle.srt",
                                language = if (languageCode != "all") languageCode.uppercase() else "English",
                                languageCode = languageCode,
                                downloadUrl = "https://www.opensubtitles.org$subPath",
                                format = "srt"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.d(TAG, "OpenSubtitles Web search failed: ${e.message}")
        }
        return results
    }

    /**
     * Search via SubDL API
     */
    private fun searchSubDL(query: String, languageCode: String): List<SubtitleSearchResult> {
        val results = mutableListOf<SubtitleSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val langParam = if (languageCode == "all") "" else "&languages=${languageCode.uppercase()}"
            val urlString = "$SUBDL_API_BASE?film_name=$encodedQuery&type=movie$langParam"

            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "ExcavPlayer/1.0")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.use { it.reader().readText() }
                val root = json.parseToJsonElement(responseText).jsonObject
                val subtitles = root["subtitles"]?.jsonArray ?: JsonArray(emptyList())

                for (item in subtitles) {
                    val obj = item.jsonObject
                    val releaseName = obj["release_name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull
                        ?: query
                    val lang = obj["lang"]?.jsonPrimitive?.contentOrNull
                        ?: obj["language"]?.jsonPrimitive?.contentOrNull
                        ?: "English"
                    val subUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                    val fullUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"

                    results.add(
                        SubtitleSearchResult(
                            id = "subdl_${subUrl.hashCode()}",
                            source = "SubDL",
                            title = releaseName,
                            fileName = "$releaseName.srt",
                            language = lang,
                            languageCode = languageCode,
                            downloadUrl = fullUrl,
                            format = "srt"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.d(TAG, "SubDL search failed: ${e.message}")
        }
        return results
    }

    /**
     * Search via YIFY Subtitles
     */
    private fun searchYifySubs(query: String, languageCode: String): List<SubtitleSearchResult> {
        val results = mutableListOf<SubtitleSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://yts-subs.com/search/$encodedQuery"

            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }

            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.use { it.reader().readText() }
                val regex = Regex("""href=["'](/subtitles/[^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
                val matches = regex.findAll(html)

                for (match in matches.take(10)) {
                    val subPath = match.groupValues[1]
                    val title = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
                    if (title.isNotBlank()) {
                        results.add(
                            SubtitleSearchResult(
                                id = "yify_${subPath.hashCode()}",
                                source = "YIFY",
                                title = title,
                                fileName = "$title.srt",
                                language = if (languageCode != "all") languageCode.uppercase() else "English",
                                languageCode = languageCode,
                                downloadUrl = "https://yts-subs.com$subPath",
                                format = "srt"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.d(TAG, "YIFY search failed: ${e.message}")
        }
        return results
    }

    /**
     * Resolves OpenSubtitles direct download link if needed
     */
    suspend fun resolveDownloadUrl(result: SubtitleSearchResult): String = withContext(dispatchers.io) {
        if (result.downloadUrl.contains("api.opensubtitles.com/api/v1/download?file_id=")) {
            try {
                val fileId = result.downloadUrl.substringAfter("file_id=").toIntOrNull()
                if (fileId != null) {
                    val url = URL("$OPENSUBTITLES_API_BASE/download")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12_000
                        readTimeout = 15_000
                        doOutput = true
                        setRequestProperty("User-Agent", "ExcavPlayer v1.0.0")
                        setRequestProperty("Api-Key", OPENSUBTITLES_DEFAULT_KEY)
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                    }

                    OutputStreamWriter(conn.outputStream).use {
                        it.write("""{"file_id":$fileId}""")
                        it.flush()
                    }

                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.use { it.reader().readText() }
                        val root = json.parseToJsonElement(body).jsonObject
                        val directLink = root["link"]?.jsonPrimitive?.contentOrNull
                        if (!directLink.isNullOrBlank()) {
                            return@withContext directLink
                        }
                    }
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to resolve OpenSubtitles direct download link: ${e.message}")
            }
        }
        return@withContext result.downloadUrl
    }
}
