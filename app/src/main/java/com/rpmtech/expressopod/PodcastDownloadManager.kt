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
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
    @SerializedName("description") val description: String = ""
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

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        scope.launch {
            loadDownloadedEpisodes()
            verifyDiskFiles()
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

    private suspend fun saveDownloadedEpisodes(list: List<DownloadedEpisode>) {
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

    private suspend fun verifyDiskFiles() {
        val current = _downloadedEpisodes.value
        val valid = current.filter {
            val file = File(it.localPath)
            file.exists() && file.length() > 0
        }
        if (valid.size != current.size) {
            saveDownloadedEpisodes(valid)
        }
    }

    fun isDownloaded(audioUrl: String): Boolean {
        return _downloadedEpisodes.value.any { it.audioUrl == audioUrl && File(it.localPath).exists() }
    }

    fun isDownloading(audioUrl: String): Boolean {
        return _activeDownloads.value.containsKey(audioUrl)
    }

    fun getLocalFile(audioUrl: String): File? {
        val item = _downloadedEpisodes.value.find { it.audioUrl == audioUrl } ?: return null
        val file = File(item.localPath)
        val storageDir = getStorageDir()
        if (!SecurityUtils.isPathInsideDir(file, storageDir)) return null
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getDownloadedEpisode(audioUrl: String): DownloadedEpisode? {
        return _downloadedEpisodes.value.find { it.audioUrl == audioUrl }
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

    fun downloadEpisode(podcast: Podcast, episode: Episode) {
        val audioUrl = episode.audioUrl
        if (audioUrl.isBlank() || !SecurityUtils.isSafeWebUrl(audioUrl) || isDownloaded(audioUrl) || isDownloading(audioUrl)) return

        val job = scope.launch {
            val hash = md5(audioUrl)
            val storageDir = getStorageDir()
            val targetFile = File(storageDir, "ep_${hash}.mp3")
            val partFile = File(storageDir, "ep_${hash}.part")

            // Initialize progress
            _activeDownloads.value = _activeDownloads.value + (audioUrl to 0f)

            try {
                val request = Request.Builder()
                    .url(audioUrl)
                    .header("User-Agent", "ExpressiveCasts/1.0")
                    .build()

                NetworkModule.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()

                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
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
                                _activeDownloads.value = _activeDownloads.value + (audioUrl to progress)
                            }
                        }
                    }

                    if (!isActive) {
                        partFile.delete()
                        return@launch
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
                        description = episode.description
                    )

                    val updated = _downloadedEpisodes.value.filter { it.audioUrl != audioUrl } + downloaded
                    saveDownloadedEpisodes(updated)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
                if (partFile.exists()) partFile.delete()
            } finally {
                _activeDownloads.value = _activeDownloads.value - audioUrl
                activeJobs.remove(audioUrl)
            }
        }
        activeJobs[audioUrl] = job
    }

    fun cancelDownload(audioUrl: String) {
        activeJobs[audioUrl]?.cancel()
        activeJobs.remove(audioUrl)
        _activeDownloads.value = _activeDownloads.value - audioUrl
        scope.launch {
            val hash = md5(audioUrl)
            val partFile = File(getStorageDir(), "ep_${hash}.part")
            if (partFile.exists()) partFile.delete()
        }
    }

    fun deleteDownload(audioUrl: String) {
        cancelDownload(audioUrl)
        scope.launch {
            val item = _downloadedEpisodes.value.find { it.audioUrl == audioUrl }
            if (item != null) {
                val file = File(item.localPath)
                if (SecurityUtils.isPathInsideDir(file, getStorageDir()) && file.exists()) {
                    file.delete()
                }
                val updated = _downloadedEpisodes.value.filter { it.audioUrl != audioUrl }
                saveDownloadedEpisodes(updated)
            }
        }
    }

    fun deleteAllDownloads() {
        scope.launch {
            _downloadedEpisodes.value.forEach { item ->
                val file = File(item.localPath)
                if (file.exists()) file.delete()
            }
            saveDownloadedEpisodes(emptyList())
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
