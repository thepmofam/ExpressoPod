package com.rpmtech.expressopod

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class DownloadedEpisode(
    @SerializedName("audioUrl") val audioUrl: String = "",
    @SerializedName("podcastTitle") val podcastTitle: String = "",
    @SerializedName("podcastArtworkUrl") val podcastArtworkUrl: String = "",
    @SerializedName("episodeTitle") val episodeTitle: String = "",
    @SerializedName("pubDate") val pubDate: String = "",
    @SerializedName("durationText") val durationText: String = "",
    @SerializedName("localPath") val localPath: String = "",
    @SerializedName("sizeBytes") val sizeBytes: Long = 0L,
    @SerializedName("downloadedAt") val downloadedAt: Long = System.currentTimeMillis(),
    @SerializedName("description") val description: String = "",
    @SerializedName("feedUrl") val feedUrl: String = ""
)

class PodcastDownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val keyDownloads = stringPreferencesKey("downloaded_episodes_json")

    private val _downloadedEpisodes = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
    val downloadedEpisodes: StateFlow<List<DownloadedEpisode>> = _downloadedEpisodes.asStateFlow()

    // Map of audioUrl to download progress (0.0f .. 1.0f)
    private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Float>> = _activeDownloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val stateMutex = Mutex()
    // Limit simultaneous active network streams to prevent server rate-limiting and timeout stalls during "Download All"
    private val downloadSemaphore = Semaphore(3)

    init {
        scope.launch {
            stateMutex.withLock {
                loadDownloadedEpisodes()
                verifyDiskFilesLocked()
            }
        }
    }

    private suspend fun loadDownloadedEpisodes() {
        var json: String? = null
        try {
            val prefs = context.dataStore.data.first()
            json = prefs[keyDownloads]
        } catch (_: Exception) {}

        if (json.isNullOrBlank()) {
            val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            json = statePrefs.getString("cached_downloads", null)
            if (!json.isNullOrBlank()) {
                // Restore to DataStore
                try {
                    context.dataStore.edit { it[keyDownloads] = json }
                } catch (_: Exception) {}
            }
        }

        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<DownloadedEpisode>>() {}.type
                val list: List<DownloadedEpisode> = gson.fromJson(json, type) ?: emptyList()
                _downloadedEpisodes.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun saveDownloadedEpisodesLocked(list: List<DownloadedEpisode>) {
        _downloadedEpisodes.value = list
        val json = gson.toJson(list)
        try {
            context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                .edit().putString("cached_downloads", json).apply()
        } catch (_: Exception) {}
        try {
            context.dataStore.edit { prefs ->
                prefs[keyDownloads] = json
            }
        } catch (_: Exception) {}
    }

    private suspend fun verifyDiskFilesLocked() {
        val current = _downloadedEpisodes.value
        val valid = current.filter {
            val file = File(it.localPath)
            file.exists() && file.length() > 0
        }
        if (valid.size != current.size) {
            saveDownloadedEpisodesLocked(valid)
        }
    }

    fun isDownloaded(audioUrl: String): Boolean {
        val cleanUrl = audioUrl.trim()
        return _downloadedEpisodes.value.any { it.audioUrl == cleanUrl && File(it.localPath).exists() }
    }

    fun isDownloading(audioUrl: String): Boolean {
        val cleanUrl = audioUrl.trim()
        return _activeDownloads.value.containsKey(cleanUrl) || activeJobs.containsKey(cleanUrl)
    }

    fun getLocalFile(audioUrl: String): File? {
        val cleanUrl = audioUrl.trim()
        val item = _downloadedEpisodes.value.find { it.audioUrl == cleanUrl } ?: return null
        val file = File(item.localPath)
        val storageDir = getStorageDir()
        if (!SecurityUtils.isPathInsideDir(file, storageDir)) return null
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getDownloadedEpisode(audioUrl: String): DownloadedEpisode? {
        val cleanUrl = audioUrl.trim()
        return _downloadedEpisodes.value.find { it.audioUrl == cleanUrl }
    }

    fun getFormattedSize(audioUrl: String): String? {
        val bytes = getDownloadedEpisode(audioUrl)?.sizeBytes ?: return null
        if (bytes <= 0) return null
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb < 1000) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024)
    }

    fun getLocalUriIfDownloaded(audioUrl: String): Uri? {
        val file = getLocalFile(audioUrl) ?: return null
        return Uri.fromFile(file)
    }

    private fun getStorageDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }

    fun downloadEpisodes(podcast: Podcast, episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        if (podcast.feedUrl.isNotBlank()) {
            FeedCredentialsManager.registerEpisodesForFeed(
                context,
                podcast.feedUrl,
                episodes.mapNotNull { it.audioUrl.trim().takeIf { url -> url.isNotEmpty() } }
            )
        }
        episodes.forEach { episode ->
            downloadEpisode(podcast, episode, skipCredentialRegistration = true)
        }
    }

    fun downloadEpisode(podcast: Podcast, episode: Episode, skipCredentialRegistration: Boolean = false) {
        val audioUrl = episode.audioUrl.trim()
        if (audioUrl.isBlank() || !SecurityUtils.isSafeWebUrl(audioUrl)) return

        val job: Job
        synchronized(this) {
            if (isDownloaded(audioUrl) || isDownloading(audioUrl)) return
            // Immediately mark as queued/active so UI and subsequent checks see it right away
            _activeDownloads.update { it + (audioUrl to 0f) }
            job = scope.launch(start = CoroutineStart.LAZY) {
                val hash = md5(audioUrl)
                val storageDir = getStorageDir()
                val targetFile = File(storageDir, "ep_${hash}.mp3")
                val partFile = File(storageDir, "ep_${hash}.part")

                try {
                    if (!skipCredentialRegistration && podcast.feedUrl.isNotBlank()) {
                        FeedCredentialsManager.registerEpisodesForFeed(context, podcast.feedUrl, listOf(audioUrl))
                    }

                    downloadSemaphore.withPermit {
                        if (!isActive) return@withPermit

                        var lastException: Exception? = null
                        var downloadSucceeded = false
                        val maxAttempts = 3

                        for (attempt in 1..maxAttempts) {
                            if (!isActive) break
                            try {
                                if (partFile.exists()) partFile.delete()
                                _activeDownloads.update { current ->
                                    if (current.containsKey(audioUrl)) current + (audioUrl to 0f) else current
                                }

                                NetworkModule.executeAuthenticatedCall(
                                    url = audioUrl,
                                    feedUrl = podcast.feedUrl.ifBlank { null },
                                    client = NetworkModule.downloadHttpClient
                                ).use { response ->
                                    if (!response.isSuccessful) {
                                        throw Exception("HTTP ${response.code}: ${response.message}")
                                    }
                                    val body = response.body ?: throw Exception("Empty response body")
                                    val contentLength = body.contentLength()
                                    val inputStream = body.byteStream()

                                    FileOutputStream(partFile).use { output ->
                                        val buffer = ByteArray(32 * 1024)
                                        var bytesCopied: Long = 0
                                        var lastProgressUpdate = 0L

                                        while (isActive) {
                                            val read = inputStream.read(buffer)
                                            if (read == -1) break
                                            output.write(buffer, 0, read)
                                            bytesCopied += read

                                            val now = System.currentTimeMillis()
                                            if (contentLength > 0 && now - lastProgressUpdate > 250) {
                                                lastProgressUpdate = now
                                                val progress = (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                                _activeDownloads.update { current ->
                                                    if (current.containsKey(audioUrl)) current + (audioUrl to progress) else current
                                                }
                                            }
                                        }
                                    }

                                    if (!isActive) {
                                        if (partFile.exists()) partFile.delete()
                                        return@withPermit
                                    }

                                    if (!partFile.exists() || partFile.length() == 0L) {
                                        throw Exception("Downloaded file is empty")
                                    }
                                    if (contentLength > 0 && partFile.length() < contentLength) {
                                        throw Exception("Incomplete download: expected $contentLength bytes, got ${partFile.length()}")
                                    }

                                    downloadSucceeded = true
                                }
                                if (downloadSucceeded) break
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                lastException = e
                                if (partFile.exists()) partFile.delete()
                                if (attempt < maxAttempts && isActive) {
                                    delay(1000L * attempt)
                                }
                            }
                        }

                        if (!downloadSucceeded) {
                            throw lastException ?: Exception("Download failed after $maxAttempts attempts")
                        }

                        if (!isActive) {
                            if (partFile.exists()) partFile.delete()
                            return@withPermit
                        }

                        // Rename partFile to targetFile
                        if (targetFile.exists()) targetFile.delete()
                        if (!partFile.renameTo(targetFile)) {
                            partFile.copyTo(targetFile, overwrite = true)
                            partFile.delete()
                        }

                        val downloaded = DownloadedEpisode(
                            audioUrl = audioUrl,
                            podcastTitle = podcast.title,
                            podcastArtworkUrl = podcast.artworkUrl,
                            episodeTitle = episode.title,
                            pubDate = episode.pubDate,
                            durationText = episode.durationText,
                            localPath = targetFile.absolutePath,
                            sizeBytes = targetFile.length(),
                            downloadedAt = System.currentTimeMillis(),
                            description = episode.description,
                            feedUrl = podcast.feedUrl
                        )

                        stateMutex.withLock {
                            val updated = _downloadedEpisodes.value.filter { it.audioUrl != audioUrl } + downloaded
                            saveDownloadedEpisodesLocked(updated)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        e.printStackTrace()
                    }
                    if (partFile.exists()) partFile.delete()
                } finally {
                    _activeDownloads.update { it - audioUrl }
                    activeJobs.remove(audioUrl)
                }
            }
            activeJobs[audioUrl] = job
        }
        job.start()
    }

    fun cancelDownload(audioUrl: String) {
        val cleanUrl = audioUrl.trim()
        activeJobs.remove(cleanUrl)?.cancel()
        _activeDownloads.update { it - cleanUrl }
        scope.launch {
            val hash = md5(cleanUrl)
            val partFile = File(getStorageDir(), "ep_${hash}.part")
            if (partFile.exists()) partFile.delete()
        }
    }

    fun deleteDownload(audioUrl: String) {
        val cleanUrl = audioUrl.trim()
        cancelDownload(cleanUrl)
        scope.launch {
            stateMutex.withLock {
                val item = _downloadedEpisodes.value.find { it.audioUrl == cleanUrl }
                if (item != null) {
                    val file = File(item.localPath)
                    if (SecurityUtils.isPathInsideDir(file, getStorageDir()) && file.exists()) {
                        file.delete()
                    }
                    val updated = _downloadedEpisodes.value.filter { it.audioUrl != cleanUrl }
                    saveDownloadedEpisodesLocked(updated)
                }
            }
        }
    }

    fun deleteAllDownloads() {
        val urlsToCancel = activeJobs.keys.toList()
        urlsToCancel.forEach { cancelDownload(it) }
        scope.launch {
            stateMutex.withLock {
                _downloadedEpisodes.value.forEach { item ->
                    val file = File(item.localPath)
                    if (file.exists()) file.delete()
                }
                saveDownloadedEpisodesLocked(emptyList())
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PodcastDownloadManager? = null

        fun getInstance(context: Context): PodcastDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: PodcastDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
