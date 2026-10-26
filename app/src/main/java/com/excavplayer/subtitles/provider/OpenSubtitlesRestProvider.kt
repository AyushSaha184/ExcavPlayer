package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenSubtitles.com REST API — JSON, needs the shared API key, and returns
 * the richest metadata (download counts, hearing-impaired flag, rating).
 */
@Singleton
class OpenSubtitlesRestProvider @Inject constructor() : SubtitleProvider {

    override val source = SubtitleSource.OPENSUBTITLES

    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val langParam = if (languageCode == "all") "" else "&languages=$languageCode"
            val body = httpGet(
                "${OPENSUBTITLES_API_BASE}/subtitles?query=${encode(query)}$langParam",
                userAgent = "ExcavPlayer v1.0.0",
                headers = mapOf(
                    "Api-Key" to OPENSUBTITLES_API_KEY,
                    "Content-Type" to "application/json",
                    "Accept" to "application/json"
                )
            ) ?: return@withContext emptyList()

            parseOpenSubtitlesRest(body, query, languageCode)
        }

    /**
     * Resolves OpenSubtitles file ids to a direct download link via the
     * REST download endpoint. Non-OpenSubtitles links pass through.
     */
    suspend fun resolveDownloadUrl(result: SubtitleSearchResult): String =
        withContext(Dispatchers.IO) {
            if (result.downloadUrl.contains("$OPENSUBTITLES_API_BASE/download?file_id=")) {
                val fileId = result.downloadUrl.substringAfter("file_id=").toIntOrNull()
                if (fileId != null) {
                    val link = parseDirectLink(
                        httpPost(
                            "$OPENSUBTITLES_API_BASE/download",
                            body = """{"file_id":$fileId}""",
                            userAgent = "ExcavPlayer v1.0.0",
                            headers = mapOf(
                                "Api-Key" to OPENSUBTITLES_API_KEY,
                                "Content-Type" to "application/json",
                                "Accept" to "application/json"
                            )
                        )
                    )
                    if (!link.isNullOrBlank()) return@withContext link
                }
            }
            result.downloadUrl
        }

    private fun parseDirectLink(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val root = ProviderJson.parseToJsonElement(body).jsonObject
        return root["link"]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        internal const val OPENSUBTITLES_API_BASE = "https://api.opensubtitles.com/api/v1"
        // Default API key for OpenSubtitles REST v1
        internal const val OPENSUBTITLES_API_KEY = "e6e3zM1Xq5KzG8f7E4B2j9L3p6V1m9Nx"
    }
}

internal fun parseOpenSubtitlesRest(
    body: String,
    query: String,
    languageCode: String
): List<SubtitleSearchResult> {
    val root = ProviderJson.parseToJsonElement(body).jsonObject
    val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
    val results = mutableListOf<SubtitleSearchResult>()

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

        if (fileId != null && id.isNotBlank()) {
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
    return results
}
