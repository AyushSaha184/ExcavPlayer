package com.excavplayer.subtitles.provider

import com.excavplayer.subtitles.SubtitleSearchResult
import com.excavplayer.subtitles.SubtitleSource
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One subtitle site, one adapter. A provider either returns its results or
 * throws — it never swallows a failure into an empty list, so the orchestrator
 * can distinguish "nothing found" from "couldn't reach the site".
 */
interface SubtitleProvider {
    val source: SubtitleSource

    suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult>
}

/** Shared lenient JSON config for API-backed providers. */
internal val ProviderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * The shared HTTP shape of every provider: GET, a 2xx-or-null contract, and
 * provider-specific headers. Non-2xx answers mean "this site has nothing for
 * us" (empty result), not an exception — network-level failures throw.
 */
internal fun httpGet(
    urlString: String,
    userAgent: String,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = 12_000,
    readTimeoutMs: Int = 15_000
): String? {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        this.connectTimeout = connectTimeoutMs
        this.readTimeout = readTimeoutMs
        setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    if (conn.responseCode !in 200..299) return null
    return conn.inputStream.use { it.reader().readText() }
}

internal fun httpPost(
    urlString: String,
    body: String,
    userAgent: String,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = 12_000,
    readTimeoutMs: Int = 15_000
): String? {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        this.connectTimeout = connectTimeoutMs
        this.readTimeout = readTimeoutMs
        doOutput = true
        setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    OutputStreamWriter(conn.outputStream).use {
        it.write(body)
        it.flush()
    }
    if (conn.responseCode !in 200..299) return null
    return conn.inputStream.use { it.reader().readText() }
}

internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

/** Strips HTML tags from a capture group. */
internal fun stripTags(html: String): String = html.replace(Regex("<[^>]*>"), "").trim()
