package com.excavplayer.subtitles

import android.content.Context
import android.os.Environment
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "SubtitleDownloader"
        private const val SUBTITLES_DIR_NAME = "EscavPlayer/subtitles"
        private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }

    /**
     * Resolves and ensures the destination directory:
     * Downloads/EscavPlayer/subtitles/
     */
    fun getSubtitlesDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, SUBTITLES_DIR_NAME)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created && !dir.exists()) {
                val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SUBTITLES_DIR_NAME)
                fallback.mkdirs()
                return fallback
            }
        }
        return dir
    }

    /**
     * Downloads subtitle from [result], unzips if needed, saves to Downloads/EscavPlayer/subtitles/
     * and returns the downloaded file.
     */
    suspend fun downloadSubtitle(
        result: SubtitleSearchResult,
        videoTitle: String
    ): Result<File> = withContext(dispatchers.io) {
        runCatching {
            val targetDir = getSubtitlesDirectory()

            // Generate clean target filename
            val baseName = sanitizeFileName(
                if (result.fileName.isNotBlank()) result.fileName
                else "${videoTitle}_${result.language}_${result.source}"
            )
            val extension = if (baseName.endsWith(".srt", ignoreCase = true) ||
                baseName.endsWith(".vtt", ignoreCase = true) ||
                baseName.endsWith(".ass", ignoreCase = true)
            ) "" else ".srt"

            val targetFile = File(targetDir, "$baseName$extension")

            logger.i(TAG, "Downloading subtitle from: ${result.downloadUrl} -> ${targetFile.absolutePath}")

            // Download bytes with redirect handling
            val bytes = fetchUrlBytes(result.downloadUrl)

            if (bytes.isEmpty()) {
                throw IllegalStateException("Downloaded subtitle file is empty")
            }

            // Check if downloaded data is a ZIP archive
            if (isZipArchive(bytes)) {
                logger.i(TAG, "Detected ZIP archive, extracting subtitle entry...")
                val extracted = extractSubtitleFromZip(bytes, targetFile)
                if (extracted == null) {
                    throw IllegalStateException("No valid subtitle file (.srt, .vtt, .ass) found in ZIP archive")
                }
                extracted
            } else {
                FileOutputStream(targetFile).use { it.write(bytes) }
                targetFile
            }
        }.onFailure { e ->
            logger.e(TAG, "Failed to download subtitle: ${e.message}", e)
        }
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == ZIP_HEADER[0] &&
                bytes[1] == ZIP_HEADER[1] &&
                bytes[2] == ZIP_HEADER[2] &&
                bytes[3] == ZIP_HEADER[3]
    }

    private fun extractSubtitleFromZip(zipBytes: ByteArray, targetFile: File): File? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (!entry.isDirectory && (
                            entryName.endsWith(".srt") ||
                            entryName.endsWith(".vtt") ||
                            entryName.endsWith(".ass")
                        )
                ) {
                    val finalFile = if (entryName.endsWith(".vtt") && !targetFile.name.endsWith(".vtt", ignoreCase = true)) {
                        File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.vtt")
                    } else if (entryName.endsWith(".ass") && !targetFile.name.endsWith(".ass", ignoreCase = true)) {
                        File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.ass")
                    } else {
                        targetFile
                    }

                    FileOutputStream(finalFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    return finalFile
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun fetchUrlBytes(urlString: String, maxRedirects: Int = 5): ByteArray {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "ExcavPlayer/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "*/*")
            }

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val redirectUrl = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location header")
                currentUrl = if (redirectUrl.startsWith("http")) redirectUrl else URL(url, redirectUrl).toString()
                conn.disconnect()
                redirects++
                continue
            }

            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP error $responseCode while downloading subtitle")
            }

            return conn.inputStream.use { it.readBytes() }
        }

        throw IllegalStateException("Too many redirects ($redirects)")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(120)
    }
}
