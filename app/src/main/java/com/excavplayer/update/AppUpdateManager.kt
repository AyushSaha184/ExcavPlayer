package com.excavplayer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.excavplayer.BuildConfig
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(
        val release: GitHubRelease,
        val currentVersion: String,
        val newVersion: String,
        val apkAsset: GitHubAsset?
    ) : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/AyushSaha184/ExcavPlayer/releases/latest"
        const val GITHUB_REPO_URL = "https://github.com/AyushSaha184/ExcavPlayer"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(isManualCheck: Boolean = false): UpdateState = withContext(dispatchers.io) {
        logger.i(TAG, "Checking for updates at $GITHUB_RELEASES_API")
        _updateState.value = UpdateState.Checking

        try {
            val url = URL(GITHUB_RELEASES_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ExcavPlayer-${BuildConfig.VERSION_NAME}")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(responseBody)

                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()
                val remoteVersion = release.tagName.removePrefix("v").removePrefix("V").trim()

                logger.i(TAG, "Current version: $currentVersion, Latest remote version: $remoteVersion")

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    val state = UpdateState.UpdateAvailable(
                        release = release,
                        currentVersion = currentVersion,
                        newVersion = remoteVersion,
                        apkAsset = apkAsset
                    )
                    _updateState.value = state
                    return@withContext state
                } else {
                    val state = if (isManualCheck) UpdateState.UpToDate else UpdateState.Idle
                    _updateState.value = state
                    return@withContext state
                }
            } else {
                logger.w(TAG, "Failed to fetch updates, HTTP response: ${connection.responseCode}")
                val state = if (isManualCheck) UpdateState.Error("Failed to check for updates (HTTP ${connection.responseCode})") else UpdateState.Idle
                _updateState.value = state
                return@withContext state
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error checking for updates", e)
            val state = if (isManualCheck) UpdateState.Error("Network error checking for updates") else UpdateState.Idle
            _updateState.value = state
            return@withContext state
        }
    }

    init {
        cleanUpdateCache()
    }

    fun cleanUpdateCache() {
        try {
            val downloadDir = File(context.cacheDir, "updates")
            if (downloadDir.exists()) {
                downloadDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to clean update cache", e)
        }
    }

    suspend fun downloadAndInstallUpdate(asset: GitHubAsset, onProgress: ((Float, Long, Long) -> Unit)? = null) = withContext(dispatchers.io) {
        try {
            logger.i(TAG, "Starting download of APK: ${asset.browserDownloadUrl}")
            _updateState.value = UpdateState.Downloading(0L, asset.size, 0f)

            val downloadDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(downloadDir, "ExcavPlayer_${asset.name}")
            if (apkFile.exists()) apkFile.delete()

            var currentUrl = asset.browserDownloadUrl
            var connection: HttpURLConnection? = null
            var redirectCount = 0

            while (redirectCount < 5) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ExcavPlayer-${BuildConfig.VERSION_NAME}")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrEmpty()) {
                        currentUrl = location
                        redirectCount++
                        conn.disconnect()
                        continue
                    }
                }
                connection = conn
                break
            }

            val finalConnection = connection ?: throw IllegalStateException("Could not establish connection")
            val totalLength = if (finalConnection.contentLengthLong > 0) finalConnection.contentLengthLong else asset.size

            finalConnection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        val progress = if (totalLength > 0) totalDownloaded.toFloat() / totalLength.toFloat() else 0f
                        _updateState.value = UpdateState.Downloading(totalDownloaded, totalLength, progress)
                        onProgress?.invoke(progress, totalDownloaded, totalLength)
                    }
                    output.flush()
                }
            }

            logger.i(TAG, "APK download completed: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            // Dismiss dialog immediately so no redundant "Install Now" box appears
            _updateState.value = UpdateState.Idle
            installApk(apkFile)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to download update APK", e)
            _updateState.value = UpdateState.Error("Failed to download update: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun installApk(apkFile: File) {
        try {
            logger.i(TAG, "Launching APK installer for: ${apkFile.absolutePath}")
            apkFile.deleteOnExit()
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to launch package installer", e)
            _updateState.value = UpdateState.Error("Failed to launch package installer: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote == current) return false
        val remoteParts = remote.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
