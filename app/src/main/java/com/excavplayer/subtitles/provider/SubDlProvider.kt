package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** SubDL API — JSON, returns release names plus direct download paths. */
@Singleton
class SubDlProvider @Inject constructor() : SubtitleProvider {

    override val source = SubtitleSource.SUBDL

    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> =
        withContext(Dispatchers.IO) {
            val langParam = if (languageCode == "all") "" else "&languages=${languageCode.uppercase()}"
            val body = httpGet(
                "https://api.subdl.com/api/v1/subtitles?film_name=${encode(query)}&type=movie$langParam",
                userAgent = "ExcavPlayer/1.0",
                headers = mapOf("Accept" to "application/json")
            ) ?: return@withContext emptyList()

            parseSubDl(body, query, languageCode)
        }
}

internal fun parseSubDl(body: String, query: String, languageCode: String): List<SubtitleSearchResult> {
    val root = ProviderJson.parseToJsonElement(body).jsonObject
    val subtitles = root["subtitles"]?.jsonArray ?: JsonArray(emptyList())
    val results = mutableListOf<SubtitleSearchResult>()

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
    return results
}
