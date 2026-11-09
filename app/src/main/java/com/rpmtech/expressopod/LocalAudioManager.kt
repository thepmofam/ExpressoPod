package com.rpmtech.expressopod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.Keep
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Keep
data class LocalAudioFile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("title") val title: String = "",
    @SerializedName("originalFileName") val originalFileName: String = "",
    @SerializedName("localPath") val localPath: String = "",
    @SerializedName("audioUrl") val audioUrl: String = "",
    @SerializedName("durationText") val durationText: String = "",
    @SerializedName("sizeBytes") val sizeBytes: Long = 0L,
    @SerializedName("addedAt") val addedAt: Long = System.currentTimeMillis(),
    @SerializedName("pubDate") val pubDate: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("artist") val artist: String = ""
) {
    fun toEpisode(): Episode {
        return Episode(
            title = title.ifBlank { originalFileName.ifBlank { "Audio File" } },
            pubDate = pubDate.ifBlank { "Local File" },
            audioUrl = audioUrl,
            durationText = durationText,
            description = description
        )
    }
}

class LocalAudioManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val keyLocalFiles = stringPreferencesKey("local_audio_files_json")
    private val keySubscriptionTitle = stringPreferencesKey("local_audio_subscription_title")

    private val _localFiles = MutableStateFlow<List<LocalAudioFile>>(emptyList())
    val localFiles: StateFlow<List<LocalAudioFile>> = _localFiles.asStateFlow()

    private val _subscriptionTitle = MutableStateFlow(DEFAULT_SUBSCRIPTION_TITLE)
    val subscriptionTitle: StateFlow<String> = _subscriptionTitle.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    @Volatile
    private var cachedCoverUri: String? = null

    init {
        loadFromPrefsSync()
        scope.launch {
            ensureCoverArtworkFile()
            reconcileWithDataStoreAndDisk()
        }
    }

    private fun getPrefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadFromPrefsSync() {
        try {
            val prefs = getPrefs()
            val savedTitle = prefs.getString(KEY_SUBSCRIPTION_TITLE, null)?.trim()
            if (!savedTitle.isNullOrBlank()) {
                _subscriptionTitle.value = savedTitle
            }
            val json = prefs.getString(KEY_FILES_JSON, null)
            if (!json.isNullOrBlank()) {
                val parsed = parseFilesJson(json)
                if (parsed.isNotEmpty()) {
                    _localFiles.value = parsed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseFilesJson(json: String?): List<LocalAudioFile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<LocalAudioFile?>>() {}.type
            val raw: List<LocalAudioFile?> = gson.fromJson(json, type) ?: emptyList()
            raw.filterNotNull()
                .filter { it.localPath.isNotBlank() }
                .map { item ->
                    val file = File(item.localPath)
                    val resolvedUrl = Uri.fromFile(file).toString()
                    item.copy(
                        id = item.id.ifBlank { UUID.randomUUID().toString() },
                        title = item.title.ifBlank { item.originalFileName.substringBeforeLast('.').ifBlank { "Audio File" } },
                        audioUrl = resolvedUrl
                    )
                }
                .distinctBy { it.audioUrl }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun reconcileWithDataStoreAndDisk() {
        try {
            val dsPrefs = context.dataStore.data.first()
            val dsTitle = dsPrefs[keySubscriptionTitle]?.trim()
            if (_subscriptionTitle.value == DEFAULT_SUBSCRIPTION_TITLE && !dsTitle.isNullOrBlank()) {
                _subscriptionTitle.value = dsTitle
                getPrefs().edit().putString(KEY_SUBSCRIPTION_TITLE, dsTitle).apply()
            }

            if (_localFiles.value.isEmpty()) {
                val dsJson = dsPrefs[keyLocalFiles]
                val dsList = parseFilesJson(dsJson)
                if (dsList.isNotEmpty()) {
                    _localFiles.value = dsList
                    getPrefs().edit().putString(KEY_FILES_JSON, gson.toJson(dsList)).apply()
                }
            }

            val storageDir = getStorageDir()
            val current = _localFiles.value
            val valid = current.filter { item ->
                val file = File(item.localPath)
                SecurityUtils.isPathInsideDir(file, storageDir) && file.exists() && file.length() > 0L
            }
            if (valid.size != current.size) {
                saveLocalFiles(valid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLocalFiles(list: List<LocalAudioFile>) {
        _localFiles.value = list
        val json = gson.toJson(list)
        try {
            getPrefs().edit().putString(KEY_FILES_JSON, json).apply()
        } catch (_: Exception) {}
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[keyLocalFiles] = json
                }
            } catch (_: Exception) {}
        }
    }

    fun updateSubscriptionTitle(newTitle: String) {
        val cleaned = newTitle.trim().ifBlank { DEFAULT_SUBSCRIPTION_TITLE }
        _subscriptionTitle.value = cleaned
        try {
            getPrefs().edit().putString(KEY_SUBSCRIPTION_TITLE, cleaned).apply()
        } catch (_: Exception) {}
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[keySubscriptionTitle] = cleaned
                }
            } catch (_: Exception) {}
        }
    }

    fun getStorageDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: context.filesDir
        val dir = File(base, "local_audio_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCoverArtworkFile(): File {
        return File(context.filesDir, "local_audio_cover_v1.png")
    }

    fun getCoverArtworkUri(): String {
        cachedCoverUri?.let { return it }
        val file = ensureCoverArtworkFile()
        val uriStr = Uri.fromFile(file).toString()
        cachedCoverUri = uriStr
        return uriStr
    }

    private fun ensureCoverArtworkFile(): File {
        val file = getCoverArtworkFile()
        if (file.exists() && file.length() > 0L) return file
        try {
            val size = 512
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Rich Material 3 Expressive gradient background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(
                        Color.parseColor("#1E1B4B"),
                        Color.parseColor("#312E81"),
                        Color.parseColor("#0F766E")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

            // Decorative subtle radial rings
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(32, 255, 255, 255)
            }
            val cx = size / 2f
            val cy = size / 2f
            canvas.drawCircle(cx, cy, 195f, ringPaint)
            canvas.drawCircle(cx, cy, 150f, ringPaint)

            // Center rounded squircle badge
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(46, 255, 255, 255)
            }
            val cardRect = RectF(cx - 115f, cy - 115f, cx + 115f, cy + 115f)
            canvas.drawRoundRect(cardRect, 56f, 56f, cardPaint)

            // Expressive Equalizer / Audio Waveform Bars in center
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
            }
            val barWidth = 20f
            val barSpacing = 34f
            val barHeights = floatArrayOf(58f, 108f, 146f, 94f, 66f)
            val startX = cx - (barHeights.size - 1) * barSpacing / 2f

            for (i in barHeights.indices) {
                val bx = startX + i * barSpacing
                val halfH = barHeights[i] / 2f
                val rect = RectF(bx - barWidth / 2f, cy - halfH, bx + barWidth / 2f, cy + halfH)
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint)
            }

            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bmp.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file
    }

    fun buildLocalPodcast(customTitle: String = _subscriptionTitle.value): Podcast {
        val count = _localFiles.value.size
        val authorLabel = if (count == 1) "1 Local Audio File" else "$count Local Audio Files"
        return Podcast(
            title = customTitle.trim().ifBlank { DEFAULT_SUBSCRIPTION_TITLE },
            author = authorLabel,
            artworkUrl = getCoverArtworkUri(),
            feedUrl = LOCAL_FEED_URL,
            description = "Audio files downloaded elsewhere on your device and imported into ExpressoPod.",
            isCustomFeed = false
        )
    }

    fun getEpisodes(): List<Episode> {
        return _localFiles.value.map { it.toEpisode() }
    }

    fun getLocalUri(audioUrl: String?): Uri? {
        if (audioUrl.isNullOrBlank()) return null
        val matched = _localFiles.value.find { it.audioUrl == audioUrl }
        if (matched != null) {
            val f = File(matched.localPath)
            if (SecurityUtils.isPathInsideDir(f, getStorageDir()) && f.exists() && f.length() > 0L) {
                return Uri.fromFile(f)
            }
        }
        if (audioUrl.startsWith("file://")) {
            val uri = Uri.parse(audioUrl)
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (SecurityUtils.isPathInsideDir(f, getStorageDir()) && f.exists() && f.length() > 0L) {
                    return Uri.fromFile(f)
                }
            }
        }
        return null
    }

    fun getFormattedSize(audioUrl: String?): String? {
        if (audioUrl.isNullOrBlank()) return null
        val item = _localFiles.value.find { it.audioUrl == audioUrl } ?: return null
        return formatBytes(item.sizeBytes)
    }

    suspend fun importAudioUris(uris: List<Uri>): List<LocalAudioFile> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyList()
        _isImporting.value = true
        val newlyAdded = mutableListOf<LocalAudioFile>()
        try {
            val storageDir = getStorageDir()
            val resolver = context.contentResolver
            val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)

            for (uri in uris) {
                try {
                    try {
                        resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}

                    var displayName = ""
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                displayName = cursor.getString(nameIdx) ?: ""
                            }
                        }
                    }

                    if (displayName.isBlank()) {
                        displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_file.mp3"
                    }

                    val rawExt = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val mimeType = resolver.getType(uri)
                    val mimeExt = if (!mimeType.isNullOrBlank()) {
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.lowercase(Locale.ROOT) ?: ""
                    } else ""
                    val safeExt = when {
                        rawExt in ALLOWED_EXTENSIONS -> rawExt
                        mimeExt in ALLOWED_EXTENSIONS -> mimeExt
                        else -> "mp3"
                    }

                    val fileId = UUID.randomUUID().toString()
                    val targetFile = File(storageDir, "local_${fileId}.${safeExt}")

                    val copied = resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output, bufferSize = 16 * 1024)
                        }
                        true
                    } ?: false

                    if (!copied || !targetFile.exists() || targetFile.length() == 0L) {
                        targetFile.delete()
                        continue
                    }

                    var durationMs = 0L
                    var metaArtist = ""
                    var metaAlbum = ""
                    var metaTitle = ""

                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(targetFile.absolutePath)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim() ?: ""
                        metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim() ?: ""
                        metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim() ?: ""
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }

                    val baseFileName = displayName.substringBeforeLast('.').trim()
                    val defaultTitle = baseFileName.ifBlank { metaTitle.ifBlank { "Audio File" } }
                    val durationSec = (durationMs / 1000L).coerceAtLeast(0L)
                    val formattedDuration = if (durationSec > 0L) formatDurationText(durationSec.toString()) else ""
                    val sizeBytes = targetFile.length()
                    val pubDateStr = dateFormatter.format(Date())

                    val descParts = mutableListOf<String>()
                    if (metaArtist.isNotBlank()) descParts.add("Artist: $metaArtist")
                    if (metaAlbum.isNotBlank()) descParts.add("Album: $metaAlbum")
                    descParts.add("Original file: $displayName")
                    descParts.add("Size: ${formatBytes(sizeBytes)}")

                    val audioUrl = Uri.fromFile(targetFile).toString()
                    val localAudio = LocalAudioFile(
                        id = fileId,
                        title = defaultTitle,
                        originalFileName = displayName,
                        localPath = targetFile.absolutePath,
                        audioUrl = audioUrl,
                        durationText = formattedDuration,
                        sizeBytes = sizeBytes,
                        addedAt = System.currentTimeMillis(),
                        pubDate = pubDateStr,
                        description = descParts.joinToString("\n"),
                        artist = metaArtist
                    )
                    newlyAdded.add(localAudio)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (newlyAdded.isNotEmpty()) {
                val updated = newlyAdded + _localFiles.value
                saveLocalFiles(updated)
            }
        } finally {
            _isImporting.value = false
        }
        newlyAdded
    }

    fun renameAudioFile(fileId: String, newTitle: String): LocalAudioFile? {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return null
        var updatedItem: LocalAudioFile? = null
        val updatedList = _localFiles.value.map { item ->
            if (item.id == fileId) {
                val copy = item.copy(title = trimmed)
                updatedItem = copy
                copy
            } else {
                item
            }
        }
        if (updatedItem != null) {
            saveLocalFiles(updatedList)
        }
        return updatedItem
    }

    fun renameAudioFileByUrl(audioUrl: String, newTitle: String): LocalAudioFile? {
        val target = _localFiles.value.find { it.audioUrl == audioUrl } ?: return null
        return renameAudioFile(target.id, newTitle)
    }

    fun deleteAudioFile(fileId: String): LocalAudioFile? {
        val target = _localFiles.value.find { it.id == fileId } ?: return null
        try {
            val file = File(target.localPath)
            if (SecurityUtils.isPathInsideDir(file, getStorageDir()) && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        val updated = _localFiles.value.filterNot { it.id == fileId }
        saveLocalFiles(updated)
        return target
    }

    fun deleteAllAudioFiles(): List<LocalAudioFile> {
        val current = _localFiles.value
        val storageDir = getStorageDir()
        current.forEach { item ->
            try {
                val file = File(item.localPath)
                if (SecurityUtils.isPathInsideDir(file, storageDir) && file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        saveLocalFiles(emptyList())
        return current
    }

    companion object {
        const val LOCAL_FEED_URL = "local://audio_files"
        const val DEFAULT_SUBSCRIPTION_TITLE = "Local Audio Files"
        private const val PREFS_NAME = "expressopod_local_audio"
        private const val KEY_FILES_JSON = "local_audio_files_json"
        private const val KEY_SUBSCRIPTION_TITLE = "local_audio_subscription_title"

        private val ALLOWED_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "flac", "webm", "mp4", "3gp", "amr"
        )

        @Volatile
        private var instance: LocalAudioManager? = null

        fun getInstance(context: Context): LocalAudioManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAudioManager(context.applicationContext).also { instance = it }
            }
        }

        fun isLocalFeedUrl(feedUrl: String?): Boolean {
            return feedUrl?.trim().equals(LOCAL_FEED_URL, ignoreCase = true)
        }

        fun isLocalPodcast(podcast: Podcast?): Boolean {
            return isLocalFeedUrl(podcast?.feedUrl)
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return when {
                mb < 1.0 -> "%.0f KB".format((bytes.toDouble() / 1024.0).coerceAtLeast(1.0))
                mb < 1000.0 -> "%.1f MB".format(mb)
                else -> "%.2f GB".format(mb / 1024.0)
            }
        }
    }
}
