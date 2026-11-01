package com.rpmtech.expressopod

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Xml
import android.view.WindowManager
import android.widget.Toast
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.annotation.Keep
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.security.MessageDigest

val Context.dataStore by preferencesDataStore(
    name = "podcast_settings",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    }
)

// ============================================================================
// 1. DATA MODELS & NETWORKING (iTunes + RSS Parser)
// ============================================================================

@Keep
data class Podcast(
    @SerializedName("title") val title: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("artworkUrl") val artworkUrl: String = "",
    @SerializedName("feedUrl") val feedUrl: String = "",
    @SerializedName("description") val description: String = ""
)

@Keep
data class Episode(
    @SerializedName("title") val title: String = "",
    @SerializedName("pubDate") val pubDate: String = "",
    @SerializedName("audioUrl") val audioUrl: String = "",
    @SerializedName("durationText") val durationText: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("chaptersUrl") val chaptersUrl: String = ""
)

val appGson: Gson = Gson()
val podcastListType: java.lang.reflect.Type = object : TypeToken<List<Podcast?>>() {}.type
val queueItemListType: java.lang.reflect.Type = object : TypeToken<List<QueueItem?>>() {}.type
val mapStringLongType: java.lang.reflect.Type = object : TypeToken<Map<String, Long>>() {}.type
private val HTML_TAG_REGEX = Regex("<[^>]*>")

object SubscriptionStorageManager {
    private const val PREFS_BACKUP = "expressopod_subscriptions_backup"
    private const val KEY_BACKUP = "subscriptions_backup"
    private const val KEY_EXPLICIT_EMPTY = "has_explicitly_cleared_subscriptions"

    private val BACKUP_LOCATIONS = listOf(
        Pair(PREFS_BACKUP, KEY_BACKUP),
        Pair("expressopod_state", "cached_subscriptions"),
        Pair("expressopod_state", "subscriptions"),
        Pair("expressopod_settings", "subscriptions"),
        Pair("expressopod_settings", "cached_subscriptions"),
        Pair("podcast_settings", "subscriptions"),
        Pair("expressive_podcasts_settings", "subscriptions")
    )

    fun parseSubscriptionsJson(json: String?): List<Podcast> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list: List<Podcast?> = appGson.fromJson(json, podcastListType) ?: emptyList()
            list.filterNotNull()
                .filter { !it.feedUrl.isNullOrBlank() }
                .map {
                    it.copy(
                        title = it.title.ifBlank { "Podcast" },
                        author = it.author,
                        artworkUrl = it.artworkUrl,
                        feedUrl = it.feedUrl.trim()
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadSubscriptionsFromBackup(context: Context): List<Podcast> {
        try {
            val backupPrefs = context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
            if (backupPrefs.getBoolean(KEY_EXPLICIT_EMPTY, false)) {
                return emptyList()
            }
        } catch (_: Exception) {}

        for ((prefName, key) in BACKUP_LOCATIONS) {
            try {
                val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val json = sp.getString(key, null)
                val list = parseSubscriptionsJson(json)
                if (list.isNotEmpty()) {
                    return list
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun saveSubscriptionsToBackup(context: Context, json: String) {
        if (json.isBlank()) return
        val list = parseSubscriptionsJson(json)
        if (list.isEmpty()) return // Do not overwrite backup with empty via this method

        try {
            context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP, json)
                .putBoolean(KEY_EXPLICIT_EMPTY, false)
                .apply()
            context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                .edit().putString("cached_subscriptions", json).apply()
        } catch (_: Exception) {}
    }

    fun recordExplicitEmpty(context: Context) {
        try {
            context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP, "[]")
                .putBoolean(KEY_EXPLICIT_EMPTY, true)
                .apply()
            context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                .edit().putString("cached_subscriptions", "[]").apply()
        } catch (_: Exception) {}
    }
}

@Keep
data class QueueItem(
    @SerializedName("podcast") val podcast: Podcast,
    @SerializedName("episode") val episode: Episode
)

object QueueStorageManager {
    private const val PREFS_BACKUP = "expressopod_queue_backup"
    private const val KEY_BACKUP = "queue_backup"
    private const val KEY_EXPLICIT_EMPTY = "has_explicitly_cleared_queue"

    private val BACKUP_LOCATIONS = listOf(
        Pair(PREFS_BACKUP, KEY_BACKUP),
        Pair("expressopod_state", "cached_queue"),
        Pair("expressopod_state", "up_next_queue"),
        Pair("expressopod_settings", "up_next_queue")
    )

    fun parseQueueJson(json: String?): List<QueueItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val list: List<QueueItem?> = appGson.fromJson(json, queueItemListType) ?: emptyList()
            list.filterNotNull().filter { !it.episode.audioUrl.isNullOrBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadQueueFromBackup(context: Context): List<QueueItem> {
        try {
            val backupPrefs = context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
            if (backupPrefs.getBoolean(KEY_EXPLICIT_EMPTY, false)) {
                return emptyList()
            }
        } catch (_: Exception) {}

        for ((prefName, key) in BACKUP_LOCATIONS) {
            try {
                val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val json = sp.getString(key, null)
                val list = parseQueueJson(json)
                if (list.isNotEmpty()) {
                    return list
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun saveQueueToBackup(context: Context, json: String) {
        if (json.isBlank()) return
        val list = parseQueueJson(json)
        try {
            val isExplicitEmpty = list.isEmpty()
            context.getSharedPreferences(PREFS_BACKUP, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKUP, json)
                .putBoolean(KEY_EXPLICIT_EMPTY, isExplicitEmpty)
                .apply()
            context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                .edit().putString("cached_queue", json).apply()
        } catch (_: Exception) {}
    }
}


fun formatHtmlDescription(rawHtml: String): String {
    if (rawHtml.isBlank()) return ""
    return try {
        android.text.Html.fromHtml(rawHtml, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
    } catch (_: Exception) {
        rawHtml.replace(HTML_TAG_REGEX, "").trim()
    }
}

fun formatDurationText(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""

    // Case 1: Pure number of seconds (e.g. "2249", "2249.0", "479")
    if (!trimmed.contains(":")) {
        val sec = trimmed.toDoubleOrNull()?.toLong() ?: return trimmed
        if (sec <= 0L) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) {
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    // Case 2: Colon-separated components (e.g. "37:29" or "01:01:31" or "00:2249")
    val parts = trimmed.split(":")
    return when (parts.size) {
        2 -> {
            val p0 = parts[0].trim().toLongOrNull() ?: 0L
            val p1 = parts[1].trim().toDoubleOrNull()?.toLong() ?: 0L
            val totalSec = p0 * 60 + p1
            val h = totalSec / 3600
            val remM = (totalSec % 3600) / 60
            val remS = totalSec % 60
            if (h > 0) {
                "%02d:%02d:%02d".format(h, remM, remS)
            } else {
                "%02d:%02d".format(remM, remS)
            }
        }
        3 -> {
            val h = parts[0].trim().toLongOrNull() ?: 0L
            val m = parts[1].trim().toLongOrNull() ?: 0L
            val s = parts[2].trim().toDoubleOrNull()?.toLong() ?: 0L
            "%02d:%02d:%02d".format(h, m, s)
        }
        else -> trimmed
    }
}

fun parseDurationMs(durationStr: String): Long {
    val trimmed = durationStr.trim()
    if (trimmed.isEmpty()) return 0L
    if (!trimmed.contains(":")) {
        val sec = trimmed.toDoubleOrNull() ?: return 0L
        return (sec * 1000).toLong()
    }
    val parts = trimmed.split(":")
    return when (parts.size) {
        1 -> (parts[0].trim().toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
        2 -> {
            val m = parts[0].trim().toLongOrNull() ?: 0L
            val s = parts[1].trim().toDoubleOrNull()?.toLong() ?: 0L
            (m * 60 + s) * 1000L
        }
        3 -> {
            val h = parts[0].trim().toLongOrNull() ?: 0L
            val m = parts[1].trim().toLongOrNull() ?: 0L
            val s = parts[2].trim().toDoubleOrNull()?.toLong() ?: 0L
            (h * 3600 + m * 60 + s) * 1000L
        }
        else -> 0L
    }
}

fun formatTimeRemaining(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "${seconds}s left"
    }
}

// iTunes DTOs
data class ItunesResponse(val results: List<ItunesItem> = emptyList())
data class ItunesItem(
    val collectionName: String?,
    val artistName: String?,
    val artworkUrl600: String?,
    val feedUrl: String?
)

interface ItunesApi {
    @GET("search?media=podcast&entity=podcast")
    suspend fun search(@Query("term") term: String): ItunesResponse
}

// Removed Podcast Index

object NetworkModule {
    @Volatile
    private var _okHttp: OkHttpClient? = null

    val okHttp: OkHttpClient
        get() = _okHttp ?: synchronized(this) {
            _okHttp ?: OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build().also { _okHttp = it }
        }

    fun initCache(cacheDir: java.io.File) {
        try {
            synchronized(this) {
                if (_okHttp?.cache == null) {
                    val httpCache = okhttp3.Cache(java.io.File(cacheDir, "http_cache"), 25L * 1024 * 1024)
                    _okHttp = OkHttpClient.Builder()
                        .cache(httpCache)
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                }
            }
        } catch (_: Exception) {}
    }

    val itunesApi: ItunesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(appGson))
            .build()
            .create(ItunesApi::class.java)
    }

    data class ParsedFeed(
        val channelTitle: String,
        val channelAuthor: String,
        val channelImage: String,
        val episodes: List<Episode>,
        val channelDescription: String = "",
        val channelLink: String = ""
    )

    private val feedCache = java.util.concurrent.ConcurrentHashMap<String, ParsedFeed>()

    fun getCachedFeed(feedUrl: String): ParsedFeed? = feedCache[feedUrl]

    suspend fun fetchPodcastFeed(feedUrl: String): ParsedFeed? = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        var channelTitle = "Unknown"
        var channelAuthor = "Unknown"
        var channelImage = ""
        var channelDescription = ""
        var channelLink = ""
        
        try {
            val req = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "ExpressoPod/1.0")
                .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                .build()
            okHttp.newCall(req).execute().use { response ->
                val stream = response.body?.byteStream() ?: return@withContext feedCache[feedUrl]
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    try {
                        setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
                    } catch (_: Exception) {}
                    setInput(stream, null)
                }

                fun readTextSafe(): String {
                    val sb = StringBuilder()
                    var depth = 1
                    while (depth > 0) {
                        when (parser.next()) {
                            XmlPullParser.START_TAG -> depth++
                            XmlPullParser.END_TAG -> depth--
                            XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                                if (depth == 1) sb.append(parser.text ?: "")
                            }
                            XmlPullParser.END_DOCUMENT -> break
                        }
                    }
                    return sb.toString().trim()
                }

                var insideItem = false
                var insideImage = false
                var title = ""; var audioUrl = ""; var pubDate = ""; var duration = ""; var description = ""; var chaptersUrl = ""

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tagName = parser.name ?: ""
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName.equals("item", true)) {
                                insideItem = true
                                title = ""; audioUrl = ""; pubDate = ""; duration = ""; description = ""; chaptersUrl = ""
                            } else if (tagName.equals("image", true)) {
                                insideImage = true
                            } else if (insideItem) {
                                when {
                                    tagName.equals("title", true) -> title = readTextSafe()
                                    tagName.equals("pubDate", true) -> pubDate = readTextSafe().take(16)
                                    (tagName.equals("itunes:duration", true) || tagName.equals("duration", true)) -> duration = formatDurationText(readTextSafe())
                                    tagName.equals("enclosure", true) -> audioUrl = parser.getAttributeValue(null, "url") ?: ""
                                    (tagName.equals("podcast:chapters", true) || tagName.equals("chapters", true)) -> {
                                        chaptersUrl = parser.getAttributeValue(null, "url") ?: ""
                                    }
                                    tagName.equals("content:encoded", true) -> {
                                        val content = readTextSafe()
                                        if (content.isNotBlank()) description = content
                                    }
                                    tagName.equals("description", true) -> {
                                        val desc = readTextSafe()
                                        if (description.isBlank() && desc.isNotBlank()) description = desc
                                    }
                                    tagName.equals("itunes:summary", true) -> {
                                        val sum = readTextSafe()
                                        if (description.isBlank() && sum.isNotBlank()) description = sum
                                    }
                                }
                            } else {
                                // Channel level tags (Podcasting 1.0 & 2.0)
                                when {
                                    tagName.equals("title", true) && !insideImage -> channelTitle = readTextSafe()
                                    tagName.equals("itunes:author", true) -> channelAuthor = readTextSafe()
                                    tagName.equals("podcast:person", true) -> channelAuthor = readTextSafe() // Podcasting 2.0
                                    tagName.equals("itunes:image", true) -> channelImage = parser.getAttributeValue(null, "href") ?: channelImage
                                    tagName.equals("url", true) && insideImage -> channelImage = readTextSafe()
                                    (tagName.equals("description", true) || tagName.equals("itunes:summary", true)) && !insideImage -> {
                                        val d = readTextSafe()
                                        if (channelDescription.isBlank() && d.isNotBlank()) channelDescription = d
                                    }
                                    tagName.equals("link", true) && !insideImage -> {
                                        val l = readTextSafe()
                                        if (channelLink.isBlank() && l.isNotBlank()) channelLink = l
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tagName.equals("item", true) && insideItem) {
                                if (audioUrl.isNotBlank()) episodes.add(Episode(title, pubDate, audioUrl, duration, description, chaptersUrl))
                                insideItem = false
                            } else if (tagName.equals("image", true)) {
                                insideImage = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext feedCache[feedUrl]
        }
        val result = ParsedFeed(channelTitle, channelAuthor, channelImage, episodes, channelDescription, channelLink)
        feedCache[feedUrl] = result
        result
    }
}

// ============================================================================
// 2. VIEWMODEL (State, Presets, & MediaController)
// ============================================================================

class PodcastViewModel(private val app: Application) : AndroidViewModel(app) {
    var searchQuery by mutableStateOf("")
    var podcasts by mutableStateOf<List<Podcast>>(emptyList())

    // Subscriptions screen selected podcast & episodes
    var subscriptionSelectedPodcast by mutableStateOf<Podcast?>(null)
    var subscriptionEpisodes by mutableStateOf<List<Episode>>(emptyList())

    // Discover screen selected podcast & episodes
    var discoverSelectedPodcast by mutableStateOf<Podcast?>(null)
    var discoverEpisodes by mutableStateOf<List<Episode>>(emptyList())

    // Backwards-compatible delegating properties
    var selectedPodcast: Podcast?
        get() = subscriptionSelectedPodcast ?: discoverSelectedPodcast
        set(value) {
            subscriptionSelectedPodcast = value
        }

    val episodes: List<Episode>
        get() = if (subscriptionSelectedPodcast != null) subscriptionEpisodes else if (discoverSelectedPodcast != null) discoverEpisodes else emptyList()

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Playback State
    private var controller: MediaController? = null
    var currentEpisode by mutableStateOf<Episode?>(null)
    var currentPodcast by mutableStateOf<Podcast?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(1L)
    var currentChapters by mutableStateOf<List<Chapter>>(emptyList())
    var isLoadingChapters by mutableStateOf(false)

    val currentChapterIndex: Int
        get() {
            val pos = currentPosition
            val list = currentChapters
            if (list.isEmpty()) return -1
            for (i in list.indices.reversed()) {
                if (pos >= list[i].startMs) {
                    return i
                }
            }
            return 0
        }

    val currentChapter: Chapter?
        get() {
            val idx = currentChapterIndex
            return if (idx >= 0 && idx < currentChapters.size) currentChapters[idx] else null
        }

    // Preset Flows
    val rewindSec = app.dataStore.data
        .map { it[intPreferencesKey("rewind_sec")] ?: 15 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    val forwardSec = app.dataStore.data
        .map { it[intPreferencesKey("forward_sec")] ?: 30 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    private fun loadInitialSubscriptions(): List<Podcast> {
        return SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
    }

    // Subscriptions
    val subscriptions = app.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val json = prefs[stringPreferencesKey("subscriptions")]
            val parsed = SubscriptionStorageManager.parseSubscriptionsJson(json)
            if (parsed.isNotEmpty()) {
                SubscriptionStorageManager.saveSubscriptionsToBackup(app, json!!)
                parsed
            } else {
                // If DataStore is empty, missing key, or failed to parse, check persistent backup
                val backupList = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                if (backupList.isNotEmpty()) {
                    // Restore to DataStore in background so it's repaired
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val backupJson = Gson().toJson(backupList)
                            app.dataStore.edit { editPrefs ->
                                val curData = editPrefs[stringPreferencesKey("subscriptions")]
                                if (SubscriptionStorageManager.parseSubscriptionsJson(curData).isEmpty()) {
                                    editPrefs[stringPreferencesKey("subscriptions")] = backupJson
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    backupList
                } else {
                    emptyList()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, loadInitialSubscriptions())

    private val _themeConfig = MutableStateFlow(
        app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE).getInt("theme_config", 0)
    )
    val themeConfig = _themeConfig.asStateFlow()

    val playbackPositions = app.dataStore.data
        .map { prefs ->
            val json = prefs[stringPreferencesKey("playback_positions")]
            if (json != null) {
                try { Gson().fromJson<Map<String, Long>>(json, object : TypeToken<Map<String, Long>>() {}.type) }
                catch (e: Exception) { emptyMap<String, Long>() }
            } else emptyMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val isGridView = app.dataStore.data
        .map { it[androidx.datastore.preferences.core.booleanPreferencesKey("is_grid_view")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val archivedEpisodes = app.dataStore.data
        .map { it[androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val playedEpisodes = app.dataStore.data
        .map { it[androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val autoArchiveEnabled = app.dataStore.data
        .map { it[androidx.datastore.preferences.core.booleanPreferencesKey("auto_archive_enabled")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val keepUnarchivedEpisodes = app.dataStore.data
        .map { it[androidx.datastore.preferences.core.stringSetPreferencesKey("keep_unarchived_episodes")] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val keepScreenOn = app.dataStore.data
        .map { it[booleanPreferencesKey("keep_screen_on")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showRemainingTime = app.dataStore.data
        .map { it[booleanPreferencesKey("show_remaining_time")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val volumeBoostEnabled = app.dataStore.data
        .map { it[booleanPreferencesKey("volume_boost_enabled")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val cachePlayingEpisode = app.dataStore.data
        .map { it[booleanPreferencesKey("cache_playing_episode")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Downloads for Offline Listening
    private val downloadManager = PodcastDownloadManager.getInstance(app)
    val downloadedEpisodes = downloadManager.downloadedEpisodes
    val activeDownloads = downloadManager.activeDownloads

    // Notifications (Opt-in via toggle)
    val notificationsEnabled = app.dataStore.data
        .map { it[EpisodeSyncWorker.KEY_NOTIFICATIONS_ENABLED] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mutedNotificationPodcasts = app.dataStore.data
        .map { it[EpisodeSyncWorker.KEY_MUTED_PODCASTS] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val podcastsToNotifyExpanded = app.dataStore.data
        .map { it[booleanPreferencesKey("podcasts_to_notify_expanded")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    var upNextQueue by mutableStateOf<List<QueueItem>>(emptyList())

    fun saveUpNextQueue() {
        val current = upNextQueue
        val json = Gson().toJson(current)
        QueueStorageManager.saveQueueToBackup(app, json)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    prefs[stringPreferencesKey("up_next_queue")] = json
                }
            } catch (_: Exception) {}
        }
    }

    enum class PlaybackNavTarget {
        EpisodeList,
        SubscriptionsHome,
        NowPlaying
    }
    var isPlayingQueue by mutableStateOf(false)
    var playbackNavTarget by mutableStateOf<PlaybackNavTarget?>(null)

    init {
        // 1. Synchronously load last played podcast & episode from SharedPreferences
        val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        val podJson = statePrefs.getString("last_podcast", null)
        val epJson = statePrefs.getString("last_episode", null)
        val lastPos = statePrefs.getLong("last_position", 0L)
        if (podJson != null) {
            try { currentPodcast = Gson().fromJson(podJson, Podcast::class.java) } catch (_: Exception) {}
        }
        if (epJson != null) {
            try { currentEpisode = Gson().fromJson(epJson, Episode::class.java) } catch (_: Exception) {}
        }
        currentEpisode?.let { ep ->
            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
            val epDurationMs = parseDurationMs(ep.durationText)
            val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos
            if (savedPos > 0L) {
                currentPosition = savedPos
            }
            if (epDurationMs > 0L) {
                duration = epDurationMs
            }
            loadChaptersForEpisode(ep)
        }

        // 2. Also keep DataStore in sync
        viewModelScope.launch {
            app.dataStore.data.collect { prefs ->
                if (currentPodcast == null) {
                    val pJson = prefs[stringPreferencesKey("last_podcast")]
                    if (pJson != null) {
                        try { currentPodcast = Gson().fromJson(pJson, Podcast::class.java) } catch (_: Exception) {}
                    }
                }
                if (currentEpisode == null) {
                    val eJson = prefs[stringPreferencesKey("last_episode")]
                    if (eJson != null) {
                        try {
                            currentEpisode = Gson().fromJson(eJson, Episode::class.java)
                            prepareControllerWithCurrentEpisode()
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        viewModelScope.launch {
            val enabled = app.dataStore.data.firstOrNull()?.get(EpisodeSyncWorker.KEY_NOTIFICATIONS_ENABLED) ?: false
            if (enabled) {
                EpisodeSyncWorker.schedulePeriodicSync(app)
            }
        }

        // Synchronously restore Up Next queue from persistent backup
        val initialQueue = QueueStorageManager.loadQueueFromBackup(app)
        if (initialQueue.isNotEmpty()) {
            upNextQueue = initialQueue
        }
        viewModelScope.launch {
            app.dataStore.data.collect { prefs ->
                val qJson = prefs[stringPreferencesKey("up_next_queue")]
                if (!qJson.isNullOrBlank()) {
                    val parsed = QueueStorageManager.parseQueueJson(qJson)
                    if (upNextQueue.isEmpty() && parsed.isNotEmpty()) {
                        upNextQueue = parsed
                    }
                }
            }
        }

        // Synchronously load saved theme preference from SharedPreferences
        val spSettings = app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
        if (spSettings.contains("theme_config")) {
            val savedThemePref = spSettings.getInt("theme_config", 0)
            if (_themeConfig.value != savedThemePref) {
                _themeConfig.value = savedThemePref
            }
        } else {
            // One-time migration from DataStore if present
            viewModelScope.launch {
                try {
                    val dsTheme = app.dataStore.data.firstOrNull()?.get(intPreferencesKey("theme_config"))
                    if (dsTheme != null && !spSettings.contains("theme_config")) {
                        _themeConfig.value = dsTheme
                        spSettings.edit().putInt("theme_config", dsTheme).apply()
                    }
                } catch (_: Exception) {}
            }
        }

        initMediaController()
        search()
    }

    fun prepareControllerWithCurrentEpisode() {
        val ctrl = controller ?: return
        val pod = currentPodcast ?: return
        val ep = currentEpisode ?: return

        val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        val lastPos = statePrefs.getLong("last_position", 0L)
        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
        val epDurationMs = parseDurationMs(ep.durationText)
        val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos

        if (ctrl.currentMediaItem?.mediaId == ep.audioUrl) {
            if (ctrl.currentPosition > 0L) {
                currentPosition = ctrl.currentPosition
            } else if (savedPos > 0L) {
                ctrl.seekTo(savedPos)
                currentPosition = savedPos
            }
            if (ctrl.duration > 0L) {
                duration = ctrl.duration
            } else if (epDurationMs > 0L) {
                duration = epDurationMs
            }
            return
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(ep.title)
            .setArtist(pod.title)
            .setArtworkUri(Uri.parse(pod.artworkUrl))
            .build()
        val localUri = downloadManager.getLocalUriIfDownloaded(ep.audioUrl)
        val playbackUri = localUri ?: Uri.parse(ep.audioUrl)
        val item = MediaItem.Builder()
            .setMediaId(ep.audioUrl)
            .setUri(playbackUri)
            .setMediaMetadata(metadata)
            .build()

        if (savedPos > 0L) {
            ctrl.setMediaItem(item, savedPos)
            currentPosition = savedPos
        } else {
            ctrl.setMediaItem(item, 0L)
        }
        ctrl.prepare()
        if (ctrl.duration > 0L) {
            duration = ctrl.duration
        } else if (epDurationMs > 0L) {
            duration = epDurationMs
        }
    }

    fun syncPlaybackState() {
        val ctrl = controller
        if (ctrl != null) {
            if (ctrl.currentPosition > 0L) {
                currentPosition = ctrl.currentPosition
            }
            if (ctrl.duration > 0L) {
                duration = ctrl.duration
            }
            isPlaying = ctrl.isPlaying
        } else {
            val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val lastPos = statePrefs.getLong("last_position", 0L)
            currentEpisode?.let { ep ->
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
                val epDurationMs = parseDurationMs(ep.durationText)
                val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos
                if (savedPos > 0L) {
                    currentPosition = savedPos
                }
                if (epDurationMs > 0L) {
                    duration = epDurationMs
                }
            }
        }
        syncWidget()
    }

    fun saveCurrentPlaybackPosition() {
        val ep = currentEpisode ?: return
        val pod = currentPodcast
        val ctrl = controller
        val isEnded = ctrl != null && (ctrl.playbackState == Player.STATE_ENDED || (ctrl.duration > 0L && ctrl.currentPosition >= ctrl.duration - 2000L))
        if (isEnded) {
            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            posPrefs.edit().remove(ep.audioUrl).apply()
            val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            statePrefs.edit().putLong("last_position", 0L).apply()
            currentPosition = 0L
            return
        }
        val pos = if (ctrl != null && ctrl.currentPosition > 0L) ctrl.currentPosition else currentPosition
        if (pos > 0L) {
            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            posPrefs.edit().putLong(ep.audioUrl, pos).apply()
        }
        if (pod != null) {
            val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val editor = statePrefs.edit()
                .putString("last_podcast", appGson.toJson(pod))
                .putString("last_episode", appGson.toJson(ep))
            if (pos > 0L) {
                editor.putLong("last_position", pos)
            }
            editor.apply()
        }
    }

    override fun onCleared() {
        saveCurrentPlaybackPosition()
        super.onCleared()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            prepareControllerWithCurrentEpisode()
            controller?.let {
                isPlaying = it.isPlaying
            }

            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (!playing) saveCurrentPlaybackPosition()
                    syncWidget()
                }
                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    controller?.let { isPlaying = it.isPlaying }
                    syncWidget()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    controller?.let { isPlaying = it.isPlaying }
                    syncWidget()
                    if (playbackState == Player.STATE_ENDED) {
                        val endedEp = currentEpisode
                        if (endedEp != null) {
                            markEpisodePlayed(endedEp, true)
                            val shouldKeepUnarchived = keepUnarchivedEpisodes.value.contains(endedEp.audioUrl)
                            if (autoArchiveEnabled.value && !shouldKeepUnarchived) {
                                toggleArchive(endedEp, true)
                            }
                            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                            posPrefs.edit().remove(endedEp.audioUrl).apply()
                        }
                        val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                        statePrefs.edit().putLong("last_position", 0L).apply()
                        currentPosition = 0L

                        if (upNextQueue.isNotEmpty()) {
                            isPlayingQueue = true
                            val nextItem = upNextQueue.first()
                            upNextQueue = upNextQueue.drop(1)
                            saveUpNextQueue()
                            playEpisode(nextItem.podcast, nextItem.episode, autoPlay = true, fromQueue = true)
                        } else {
                            if (isPlayingQueue) {
                                isPlayingQueue = false
                                playbackNavTarget = PlaybackNavTarget.SubscriptionsHome
                            } else {
                                playbackNavTarget = PlaybackNavTarget.EpisodeList
                            }
                        }
                    }
                }
            })
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            var loopCount = 0
            var lastWidgetMinute = -1L
            while (true) {
                controller?.let {
                    if (isPlaying != it.isPlaying) {
                        isPlaying = it.isPlaying
                    }
                    if (it.playbackState != Player.STATE_ENDED && (it.isPlaying || it.currentPosition > 0L)) {
                        currentPosition = it.currentPosition
                    }
                    if (it.duration > 0L) {
                        duration = it.duration
                    }
                    loopCount++
                    // Persist playback position every 2 seconds while playing
                    if (it.isPlaying && loopCount % 4 == 0 && it.currentPosition > 0L) {
                        saveCurrentPlaybackPosition()
                    }
                    if (it.isPlaying) {
                        val dur = if (duration > 0L) duration else (currentEpisode?.let { ep -> parseDurationMs(ep.durationText) } ?: 0L)
                        val remMins = if (dur > currentPosition) (dur - currentPosition) / 60000L else 0L
                        if (remMins != lastWidgetMinute && remMins >= 0L) {
                            lastWidgetMinute = remMins
                            syncWidget()
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun search() {
        if (searchQuery.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            discoverSelectedPodcast = null
            discoverEpisodes = emptyList()
            try {
                podcasts = NetworkModule.itunesApi.search(searchQuery).results.mapNotNull {
                    if (it.feedUrl != null && it.collectionName != null) {
                        Podcast(it.collectionName, it.artistName ?: "Unknown", it.artworkUrl600 ?: "", it.feedUrl)
                    } else null
                }
            } catch (e: Exception) {
                errorMessage = "Search error: ${e.localizedMessage}"
            }
            isLoading = false
        }
    }

    fun clearSearch() {
        searchQuery = ""
        podcasts = emptyList()
        discoverSelectedPodcast = null
        discoverEpisodes = emptyList()
        errorMessage = null
    }

    fun getSavedPosition(audioUrl: String): Long {
        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        return posPrefs.getLong(audioUrl, 0L)
    }

    fun loadSubscriptionPodcastEpisodes(podcast: Podcast) {
        subscriptionSelectedPodcast = podcast
        val cached = NetworkModule.getCachedFeed(podcast.feedUrl)
        if (cached != null && cached.episodes.isNotEmpty()) {
            subscriptionEpisodes = cached.episodes
            isLoading = false
        } else {
            isLoading = true
        }

        viewModelScope.launch {
            val parsed = NetworkModule.fetchPodcastFeed(podcast.feedUrl)
            if (parsed != null) {
                subscriptionEpisodes = parsed.episodes
                val updatedPodcast = podcast.copy(
                    title = if (parsed.channelTitle.isNotBlank()) parsed.channelTitle else podcast.title,
                    author = if (parsed.channelAuthor.isNotBlank()) parsed.channelAuthor else podcast.author,
                    artworkUrl = if (parsed.channelImage.isNotBlank()) parsed.channelImage else podcast.artworkUrl,
                    description = if (parsed.channelDescription.isNotBlank()) parsed.channelDescription else podcast.description
                )
                subscriptionSelectedPodcast = updatedPodcast

                // If imported from OPML or opened via notification, update missing info
                if (podcast.title == "Podcast" || podcast.title.isBlank() || podcast.author == "Unknown" || podcast.artworkUrl.isBlank() || podcast.description.isBlank()) {
                    // Update in subscriptions
                    app.dataStore.edit { prefs ->
                        val json = prefs[stringPreferencesKey("subscriptions")]
                        var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                        if (current.isEmpty()) {
                            val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                            if (backup.isNotEmpty()) current = backup.toMutableList()
                        }
                        
                        val idx = current.indexOfFirst { it.feedUrl == podcast.feedUrl }
                        if (idx != -1) {
                            current[idx] = updatedPodcast
                            val updatedJson = Gson().toJson(current)
                            prefs[stringPreferencesKey("subscriptions")] = updatedJson
                            SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                        }
                    }
                }
            } else if (cached == null) {
                subscriptionEpisodes = emptyList()
            }
            isLoading = false
        }
    }

    fun navigateToPodcastEpisodes(feedUrl: String, title: String = "", author: String = "", artworkUrl: String = "") {
        if (feedUrl.isBlank()) return
        val existing = subscriptions.value.find { it.feedUrl == feedUrl }
        val targetPodcast = existing ?: Podcast(
            title = title.ifBlank { "Podcast" },
            author = author,
            artworkUrl = artworkUrl,
            feedUrl = feedUrl
        )
        discoverSelectedPodcast = null
        subscriptionSelectedPodcast = targetPodcast
        loadSubscriptionPodcastEpisodes(targetPodcast)
        playbackNavTarget = PlaybackNavTarget.EpisodeList
    }

    fun loadDiscoverPodcastEpisodes(podcast: Podcast) {
        discoverSelectedPodcast = podcast
        val cached = NetworkModule.getCachedFeed(podcast.feedUrl)
        if (cached != null && cached.episodes.isNotEmpty()) {
            discoverEpisodes = cached.episodes
            isLoading = false
        } else {
            isLoading = true
        }

        viewModelScope.launch {
            val parsed = NetworkModule.fetchPodcastFeed(podcast.feedUrl)
            if (parsed != null) {
                discoverEpisodes = parsed.episodes
                discoverSelectedPodcast = podcast.copy(
                    title = if (parsed.channelTitle.isNotBlank()) parsed.channelTitle else podcast.title,
                    author = if (parsed.channelAuthor.isNotBlank()) parsed.channelAuthor else podcast.author,
                    artworkUrl = if (parsed.channelImage.isNotBlank()) parsed.channelImage else podcast.artworkUrl,
                    description = if (parsed.channelDescription.isNotBlank()) parsed.channelDescription else podcast.description
                )
            } else if (cached == null) {
                discoverEpisodes = emptyList()
            }
            isLoading = false
        }
    }

    fun loadPodcastEpisodes(podcast: Podcast) {
        loadSubscriptionPodcastEpisodes(podcast)
    }

    fun playEpisode(podcast: Podcast, episode: Episode, autoPlay: Boolean = false, fromQueue: Boolean = false) {
        isPlayingQueue = fromQueue
        saveCurrentPlaybackPosition()

        currentPodcast = podcast
        currentEpisode = episode

        val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        statePrefs.edit()
            .putString("last_podcast", appGson.toJson(podcast))
            .putString("last_episode", appGson.toJson(episode))
            .apply()
        
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("last_podcast")] = appGson.toJson(podcast)
                prefs[stringPreferencesKey("last_episode")] = appGson.toJson(episode)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast.title)
            .setArtworkUri(Uri.parse(podcast.artworkUrl))
            .build()

        val localUri = downloadManager.getLocalUriIfDownloaded(episode.audioUrl)
        val playbackUri = localUri ?: Uri.parse(episode.audioUrl)

        val item = MediaItem.Builder()
            .setMediaId(episode.audioUrl)
            .setUri(playbackUri)
            .setMediaMetadata(metadata)
            .build()

        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        val rawSavedPos = posPrefs.getLong(episode.audioUrl, 0L)
        val epDurationMs = parseDurationMs(episode.durationText)
        val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos
        this.currentPosition = savedPos
        if (epDurationMs > 0L) {
            this.duration = epDurationMs
        }
        loadChaptersForEpisode(episode)

        statePrefs.edit()
            .putString("last_podcast", appGson.toJson(podcast))
            .putString("last_episode", appGson.toJson(episode))
            .putLong("last_position", savedPos)
            .apply()

        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("last_podcast")] = appGson.toJson(podcast)
                prefs[stringPreferencesKey("last_episode")] = appGson.toJson(episode)
            }
        }

        controller?.apply {
            if (savedPos > 0L) {
                setMediaItem(item, savedPos)
            } else {
                setMediaItem(item, 0L)
            }
            prepare()
            if (autoPlay) {
                play()
            } else {
                pause()
            }
        }
        syncWidget()
    }

    fun playDownloadedEpisode(downloaded: DownloadedEpisode, autoPlay: Boolean = false) {
        val podcast = Podcast(
            title = downloaded.podcastTitle,
            author = "Downloaded",
            artworkUrl = downloaded.podcastArtworkUrl,
            feedUrl = ""
        )
        val episode = Episode(
            title = downloaded.episodeTitle,
            pubDate = downloaded.pubDate,
            audioUrl = downloaded.audioUrl,
            durationText = downloaded.durationText,
            description = downloaded.description
        )
        playEpisode(podcast, episode, autoPlay = autoPlay)
    }

    fun downloadEpisode(podcast: Podcast, episode: Episode) {
        downloadManager.downloadEpisode(podcast, episode)
    }

    fun downloadAllEpisodes(podcast: Podcast, episodes: List<Episode>) {
        episodes.forEach { episode ->
            downloadEpisode(podcast, episode)
        }
    }

    fun cancelDownload(audioUrl: String) {
        downloadManager.cancelDownload(audioUrl)
    }

    fun deleteDownload(audioUrl: String) {
        downloadManager.deleteDownload(audioUrl)
    }

    fun deleteAllDownloads() {
        downloadManager.deleteAllDownloads()
    }

    fun getFormattedDownloadSize(audioUrl: String): String? {
        return downloadManager.getFormattedSize(audioUrl)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit {
                it[EpisodeSyncWorker.KEY_NOTIFICATIONS_ENABLED] = enabled
            }
            if (enabled) {
                EpisodeSyncWorker.schedulePeriodicSync(app)
            } else {
                EpisodeSyncWorker.cancelSync(app)
            }
        }
    }

    fun triggerEpisodeSync() {
        EpisodeSyncWorker.syncNow(app)
    }

    fun togglePodcastNotification(feedUrl: String, enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val currentMuted = (prefs[EpisodeSyncWorker.KEY_MUTED_PODCASTS] ?: emptySet()).toMutableSet()
                if (enabled) {
                    currentMuted.remove(feedUrl)
                } else {
                    currentMuted.add(feedUrl)
                }
                prefs[EpisodeSyncWorker.KEY_MUTED_PODCASTS] = currentMuted
            }
        }
    }

    fun setAllPodcastNotifications(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                if (enabled) {
                    prefs[EpisodeSyncWorker.KEY_MUTED_PODCASTS] = emptySet()
                } else {
                    val allUrls = subscriptions.value.map { it.feedUrl }.toSet()
                    prefs[EpisodeSyncWorker.KEY_MUTED_PODCASTS] = allUrls
                }
            }
        }
    }

    fun addToUpNext(podcast: Podcast, episode: Episode) {
        val filtered = upNextQueue.filterNot { it.episode.audioUrl == episode.audioUrl }
        // Place next in line after currently playing episode (at the top of the queue)
        upNextQueue = listOf(QueueItem(podcast, episode)) + filtered
        saveUpNextQueue()
        syncWidget()
    }

    fun removeFromUpNext(item: QueueItem) {
        upNextQueue = upNextQueue - item
        saveUpNextQueue()
        syncWidget()
    }

    fun removeFromUpNextByUrl(audioUrl: String) {
        upNextQueue = upNextQueue.filterNot { it.episode.audioUrl == audioUrl }
        saveUpNextQueue()
        syncWidget()
    }

    fun reorderUpNextItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in upNextQueue.indices || toIndex !in upNextQueue.indices) return
        val list = upNextQueue.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        upNextQueue = list
    }

    fun moveUpNextItem(fromIndex: Int, toIndex: Int) {
        reorderUpNextItem(fromIndex, toIndex)
        saveUpNextQueue()
        syncWidget()
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
                saveCurrentPlaybackPosition()
            } else {
                if (it.currentMediaItem == null && currentEpisode != null && currentPodcast != null) {
                    prepareControllerWithCurrentEpisode()
                }
                it.play()
            }
        }
        syncWidget()
    }

    fun skipRewind() {
        controller?.let {
            val target = (it.currentPosition - rewindSec.value * 1000L).coerceAtLeast(0L)
            it.seekTo(target)
            currentPosition = target
            currentEpisode?.let { ep ->
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                posPrefs.edit().putLong(ep.audioUrl, target).apply()
                val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                statePrefs.edit().putLong("last_position", target).apply()
            }
            syncWidget()
        }
    }

    fun skipForward() {
        controller?.let {
            val target = (it.currentPosition + forwardSec.value * 1000L).coerceAtMost(duration)
            it.seekTo(target)
            currentPosition = target
            currentEpisode?.let { ep ->
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                posPrefs.edit().putLong(ep.audioUrl, target).apply()
                val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                statePrefs.edit().putLong("last_position", target).apply()
            }
            syncWidget()
        }
    }

    fun seekTo(fraction: Float) {
        controller?.let {
            val target = (duration * fraction).toLong()
            it.seekTo(target)
            currentPosition = target
            currentEpisode?.let { ep ->
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                posPrefs.edit().putLong(ep.audioUrl, target).apply()
                val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                statePrefs.edit().putLong("last_position", target).apply()
            }
            syncWidget()
        }
    }

    fun seekToPosition(positionMs: Long) {
        val target = positionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        controller?.let {
            it.seekTo(target)
            currentPosition = target
            currentEpisode?.let { ep ->
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                posPrefs.edit().putLong(ep.audioUrl, target).apply()
                val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                statePrefs.edit().putLong("last_position", target).apply()
            }
            syncWidget()
        }
    }

    fun seekToChapter(chapter: Chapter) {
        seekToPosition(chapter.startMs)
        if (!isPlaying) {
            controller?.play()
            isPlaying = true
        }
    }

    fun skipToNextChapter() {
        val list = currentChapters
        if (list.isEmpty()) return
        val nextIdx = currentChapterIndex + 1
        if (nextIdx < list.size) {
            seekToChapter(list[nextIdx])
        }
    }

    fun skipToPreviousChapter() {
        val list = currentChapters
        if (list.isEmpty()) return
        val curIdx = currentChapterIndex
        if (curIdx > 0) {
            val curStart = list[curIdx].startMs
            if (currentPosition - curStart > 3000L) {
                seekToPosition(curStart)
            } else {
                seekToChapter(list[curIdx - 1])
            }
        } else if (curIdx == 0) {
            seekToPosition(list[0].startMs)
        }
    }

    fun onChaptersReceived(chaptersJson: String) {
        val parsed = ChapterParser.parseChaptersJson(chaptersJson)
        if (parsed.isNotEmpty()) {
            val totalDurMs = currentEpisode?.let { parseDurationMs(it.durationText) }?.takeIf { it > 0 } ?: duration
            currentChapters = ChapterParser.finalizeChapters(parsed, totalDurMs)
        }
    }

    fun loadChaptersForEpisode(episode: Episode) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingChapters = true
            val chapters = mutableListOf<Chapter>()
            val totalDurMs = parseDurationMs(episode.durationText).let { if (it > 0) it else duration }

            // 1. Podcasting 2.0 Chapters JSON
            if (episode.chaptersUrl.isNotBlank() && SecurityUtils.isSafeWebUrl(episode.chaptersUrl)) {
                try {
                    val req = Request.Builder()
                        .url(episode.chaptersUrl)
                        .header("User-Agent", "ExpressoPod/1.0")
                        .build()
                    NetworkModule.okHttp.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val parsed = ChapterParser.parseChaptersJson(body)
                                if (parsed.isNotEmpty()) {
                                    chapters.addAll(parsed)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Check cached ID3 chapters from PlaybackService
            if (chapters.isEmpty()) {
                val cached = app.getSharedPreferences("expressopod_chapters", Context.MODE_PRIVATE)
                    .getString(episode.audioUrl, null)
                if (!cached.isNullOrBlank()) {
                    chapters.addAll(ChapterParser.parseChaptersJson(cached))
                }
            }

            // 3. Fallback: Parse timestamps from episode description / show notes
            if (chapters.isEmpty()) {
                val rawDesc = episode.description.ifBlank {
                    episodes.find { it.audioUrl == episode.audioUrl }?.description ?: ""
                }
                if (rawDesc.isNotBlank()) {
                    val parsedDesc = ChapterParser.parseChaptersFromText(rawDesc, totalDurMs)
                    if (parsedDesc.isNotEmpty()) {
                        chapters.addAll(parsedDesc)
                    }
                }
            }

            val finalized = ChapterParser.finalizeChapters(chapters, totalDurMs)
            withContext(Dispatchers.Main) {
                currentChapters = finalized
                isLoadingChapters = false
            }
        }
    }

    fun syncWidget() {
        val ep = currentEpisode
        val pod = currentPodcast
        val title = ep?.title ?: "No Episode Playing"
        val podcastTitle = pod?.title ?: "Expressive Casts"
        val artUrl = pod?.artworkUrl ?: ""
        val pos = currentPosition
        val dur = if (duration > 0L) duration else (ep?.let { parseDurationMs(it.durationText) } ?: 0L)
        val remMs = if (dur > pos) (dur - pos) else 0L
        val remText = if (remMs > 0L) formatWidgetRemainingTime(remMs) else ""
        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        val queueItems = upNextQueue.take(3).map { queueItem ->
            val epDur = parseDurationMs(queueItem.episode.durationText)
            val savedPos = posPrefs.getLong(queueItem.episode.audioUrl, 0L)
            val queueRemMs = if (epDur > savedPos) (epDur - savedPos) else epDur
            val queueRemText = if (queueRemMs > 0L) formatWidgetRemainingTime(queueRemMs) else ""

            WidgetQueueItem(
                title = queueItem.episode.title,
                podcastTitle = queueItem.podcast.title,
                durationText = queueItem.episode.durationText,
                audioUrl = queueItem.episode.audioUrl,
                artworkUrl = queueItem.podcast.artworkUrl,
                remainingTimeText = queueRemText
            )
        }
        val queueJson = Gson().toJson(queueItems)

        viewModelScope.launch {
            app.updateWidgetData(
                title = title,
                podcast = podcastTitle,
                isPlaying = isPlaying,
                remainingText = remText,
                artUrl = artUrl,
                queueJson = queueJson
            )
            PodcastWidget().updateAll(app)
        }
    }

    fun updateTheme(theme: Int) {
        _themeConfig.value = theme
        app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("theme_config", theme)
            .apply()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit {
                    it[intPreferencesKey("theme_config")] = theme
                }
            } catch (_: Exception) {}
        }
    }

    fun updateGridView(isGrid: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit {
                it[androidx.datastore.preferences.core.booleanPreferencesKey("is_grid_view")] = isGrid
            }
        }
    }

    fun toggleArchive(episode: Episode, archive: Boolean) {
        viewModelScope.launch {
            if (archive) {
                deleteDownload(episode.audioUrl)
            }
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                if (archive) current.add(episode.audioUrl) else current.remove(episode.audioUrl)
                prefs[key] = current
            }
        }
    }

    fun toggleKeepUnarchived(episode: Episode) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("keep_unarchived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                if (current.contains(episode.audioUrl)) {
                    current.remove(episode.audioUrl)
                } else {
                    current.add(episode.audioUrl)
                    // If episode was previously archived, unarchive it
                    val archiveKey = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                    val archivedSet = prefs[archiveKey]?.toMutableSet()
                    if (archivedSet != null && archivedSet.remove(episode.audioUrl)) {
                        prefs[archiveKey] = archivedSet
                    }
                }
                prefs[key] = current
            }
        }
    }

    fun archiveAllEpisodes(episodes: List<Episode>) {
        viewModelScope.launch {
            val pinned = keepUnarchivedEpisodes.value
            val unpinnedEpisodes = episodes.filter { !pinned.contains(it.audioUrl) }
            unpinnedEpisodes.forEach { ep ->
                deleteDownload(ep.audioUrl)
            }
            app.dataStore.edit { prefs ->
                val pinnedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("keep_unarchived_episodes")
                val activePinned = prefs[pinnedKey] ?: pinned
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                unpinnedEpisodes.forEach { ep ->
                    if (ep.audioUrl.isNotBlank() && !activePinned.contains(ep.audioUrl)) {
                        current.add(ep.audioUrl)
                    }
                }
                prefs[key] = current
            }
        }
    }

    fun unarchiveAllEpisodes(episodes: List<Episode>) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                episodes.forEach { ep ->
                    current.remove(ep.audioUrl)
                }
                prefs[key] = current
            }
        }
    }

    fun markEpisodePlayed(episode: Episode, played: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                if (played) {
                    current.add(episode.audioUrl)
                    val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                    posPrefs.edit().remove(episode.audioUrl).apply()
                    val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                    if (currentEpisode?.audioUrl == episode.audioUrl) {
                        statePrefs.edit().putLong("last_position", 0L).apply()
                        currentPosition = 0L
                    }
                } else {
                    current.remove(episode.audioUrl)
                }
                prefs[key] = current
            }
        }
    }

    fun setAutoArchiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                prefs[androidx.datastore.preferences.core.booleanPreferencesKey("auto_archive_enabled")] = enabled
            }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("keep_screen_on")] = enabled }
        }
    }

    fun setShowRemainingTime(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("show_remaining_time")] = enabled }
        }
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("volume_boost_enabled", enabled).apply()
            app.dataStore.edit { it[booleanPreferencesKey("volume_boost_enabled")] = enabled }
        }
    }

    fun setCachePlayingEpisode(enabled: Boolean) {
        viewModelScope.launch {
            app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("cache_playing_episode", enabled).apply()
            app.dataStore.edit { it[booleanPreferencesKey("cache_playing_episode")] = enabled }
        }
    }

    fun setPodcastsToNotifyExpanded(expanded: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("podcasts_to_notify_expanded")] = expanded }
        }
    }

    private val _episodeListScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveEpisodeListPosition(feedUrl: String, index: Int, offset: Int) {
        if (feedUrl.isNotBlank()) {
            _episodeListScrollPositions[feedUrl] = Pair(index, offset)
        }
    }

    fun getEpisodeListPosition(feedUrl: String): Pair<Int, Int> {
        return _episodeListScrollPositions[feedUrl] ?: Pair(0, 0)
    }

    fun updatePresets(newRewind: Int, newForward: Int) {
        viewModelScope.launch {
            app.dataStore.edit {
                it[intPreferencesKey("rewind_sec")] = newRewind
                it[intPreferencesKey("forward_sec")] = newForward
            }
            PodcastWidget().updateAll(app)
        }
    }

    fun toggleSubscription(podcast: Podcast) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val json = prefs[stringPreferencesKey("subscriptions")]
                var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                if (current.isEmpty()) {
                    val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                    if (backup.isNotEmpty()) current = backup.toMutableList()
                }

                if (current.any { it.feedUrl == podcast.feedUrl }) {
                    current.removeAll { it.feedUrl == podcast.feedUrl }
                } else {
                    current.add(podcast)
                }
                val updatedJson = Gson().toJson(current)
                prefs[stringPreferencesKey("subscriptions")] = updatedJson
                if (current.isEmpty()) {
                    SubscriptionStorageManager.recordExplicitEmpty(app)
                } else {
                    SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                }
            }
        }
    }

    fun updateSubscriptionOrder(newOrder: List<Podcast>) {
        if (newOrder.isEmpty() && subscriptions.value.isNotEmpty()) return // Safeguard
        viewModelScope.launch {
            val json = Gson().toJson(newOrder)
            app.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("subscriptions")] = json
            }
            if (newOrder.isNotEmpty()) {
                SubscriptionStorageManager.saveSubscriptionsToBackup(app, json)
            }
        }
    }

    fun exportOpml(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(uri)?.use { output ->
                    val xmlSerializer = Xml.newSerializer()
                    xmlSerializer.setOutput(output, "UTF-8")
                    xmlSerializer.startDocument("UTF-8", true)
                    xmlSerializer.startTag(null, "opml")
                    xmlSerializer.attribute(null, "version", "2.0")
                    xmlSerializer.startTag(null, "head")
                    xmlSerializer.startTag(null, "title")
                    xmlSerializer.text("Podcast Subscriptions")
                    xmlSerializer.endTag(null, "title")
                    xmlSerializer.endTag(null, "head")
                    xmlSerializer.startTag(null, "body")

                    val currentSubs = subscriptions.value
                    for (pod in currentSubs) {
                        xmlSerializer.startTag(null, "outline")
                        xmlSerializer.attribute(null, "text", pod.title)
                        xmlSerializer.attribute(null, "title", pod.title)
                        xmlSerializer.attribute(null, "type", "rss")
                        xmlSerializer.attribute(null, "xmlUrl", pod.feedUrl)
                        xmlSerializer.endTag(null, "outline")
                    }
                    xmlSerializer.endTag(null, "body")
                    xmlSerializer.endTag(null, "opml")
                    xmlSerializer.endDocument()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importOpml(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newSubs = mutableListOf<Podcast>()
                app.contentResolver.openInputStream(uri)?.use { input ->
                    val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance().apply {
                        try {
                            setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
                        } catch (_: Exception) {}
                    }
                    val parser = factory.newPullParser()
                    parser.setInput(input, null)
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                            var xmlUrl: String? = null
                            var title: String? = null
                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i)
                                if (attrName.equals("xmlUrl", ignoreCase = true) || attrName.equals("url", ignoreCase = true)) {
                                    if (xmlUrl == null) xmlUrl = parser.getAttributeValue(i)
                                } else if (attrName.equals("title", ignoreCase = true) || attrName.equals("text", ignoreCase = true)) {
                                    if (title == null) title = parser.getAttributeValue(i)
                                }
                            }
                            if (xmlUrl != null) {
                                newSubs.add(Podcast(title ?: "Unknown", "Unknown", "", xmlUrl))
                            }
                        }
                        eventType = parser.next()
                    }
                }

                val fullSubs = newSubs.map { sub ->
                    async {
                        val parsed = NetworkModule.fetchPodcastFeed(sub.feedUrl)
                        if (parsed != null) {
                            sub.copy(title = parsed.channelTitle, author = parsed.channelAuthor, artworkUrl = parsed.channelImage)
                        } else sub
                    }
                }.awaitAll()

                app.dataStore.edit { prefs ->
                    val json = prefs[stringPreferencesKey("subscriptions")]
                    var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                    if (current.isEmpty()) {
                        val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                        if (backup.isNotEmpty()) current = backup.toMutableList()
                    }

                    for (sub in fullSubs) {
                        if (!current.any { it.feedUrl == sub.feedUrl }) {
                            current.add(sub)
                        }
                    }
                    val updatedJson = Gson().toJson(current)
                    prefs[stringPreferencesKey("subscriptions")] = updatedJson
                    SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// ============================================================================
// 3. UI & MATERIAL 3 EXPRESSIVE THEME
// ============================================================================

class MainActivity : ComponentActivity() {
    private var viewModelRef: PodcastViewModel? = null

    private val playbackAndQueueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.rpmtech.expressopod.ACTION_QUEUE_CHANGED" -> {
                    val playedUrl = intent.getStringExtra("played_url")
                    if (playedUrl != null) {
                        viewModelRef?.let { vm ->
                            val statePrefs = context?.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                            val podJson = statePrefs?.getString("last_podcast", null)
                            val epJson = statePrefs?.getString("last_episode", null)
                            if (podJson != null) {
                                try { vm.currentPodcast = Gson().fromJson(podJson, Podcast::class.java) } catch (_: Exception) {}
                            }
                            if (epJson != null) {
                                try { vm.currentEpisode = Gson().fromJson(epJson, Episode::class.java) } catch (_: Exception) {}
                            }
                            vm.removeFromUpNextByUrl(playedUrl)
                            vm.syncPlaybackState()
                        }
                    }
                }
                "com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED" -> {
                    val isPlaying = intent.getBooleanExtra("is_playing", false)
                    val pos = intent.getLongExtra("position", -1L)
                    viewModelRef?.let { vm ->
                        vm.isPlaying = isPlaying
                        if (pos >= 0L) {
                            vm.currentPosition = pos
                        }
                        vm.syncPlaybackState()
                    }
                }
                "com.rpmtech.expressopod.ACTION_CHAPTERS_UPDATED" -> {
                    val audioUrl = intent.getStringExtra("audio_url")
                    val json = intent.getStringExtra("chapters_json")
                    if (audioUrl != null && json != null && viewModelRef?.currentEpisode?.audioUrl == audioUrl) {
                        viewModelRef?.onChaptersReceived(json)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EpisodeNotificationManager.createNotificationChannel(this)
        val filter = IntentFilter().apply {
            addAction("com.rpmtech.expressopod.ACTION_QUEUE_CHANGED")
            addAction("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED")
            addAction("com.rpmtech.expressopod.ACTION_CHAPTERS_UPDATED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackAndQueueReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackAndQueueReceiver, filter)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        enableEdgeToEdge()
        setContent {
            val vm: PodcastViewModel = viewModel()
            viewModelRef = vm
            val themeConfig by vm.themeConfig.collectAsState()

            LaunchedEffect(intent) {
                handleNotificationIntent(intent, vm)
            }

            ExpressivePodcastTheme(themeConfig = themeConfig) {
                PodcastAppScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModelRef?.syncPlaybackState()
    }

    override fun onPause() {
        super.onPause()
        viewModelRef?.saveCurrentPlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        viewModelRef?.saveCurrentPlaybackPosition()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(playbackAndQueueReceiver)
        } catch (e: Exception) {}
        viewModelRef?.saveCurrentPlaybackPosition()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelRef?.let { vm ->
            handleNotificationIntent(intent, vm)
        }
    }

    private fun handleNotificationIntent(intent: Intent?, vm: PodcastViewModel) {
        if (intent == null) return
        val action = intent.getStringExtra("action") ?: intent.action
        val feedUrl = intent.getStringExtra("feed_url")
        if (action == "OPEN_PODCAST_EPISODES" || action == "OPEN_EPISODE" || !feedUrl.isNullOrBlank()) {
            val podTitle = intent.getStringExtra("podcast_title") ?: ""
            val podAuthor = intent.getStringExtra("podcast_author") ?: ""
            val podArtwork = intent.getStringExtra("podcast_artwork") ?: ""
            vm.navigateToPodcastEpisodes(
                feedUrl = feedUrl ?: "",
                title = podTitle,
                author = podAuthor,
                artworkUrl = podArtwork
            )
            intent.action = null
            intent.removeExtra("action")
            intent.removeExtra("feed_url")
            intent.removeExtra("audio_url")
            intent.removeExtra("podcast_title")
            intent.removeExtra("podcast_author")
            intent.removeExtra("podcast_artwork")
            intent.removeExtra("episode_title")
        } else if (action == "com.rpmtech.expressopod.ACTION_NOW_PLAYING" || intent.getBooleanExtra("from_media_notification", false)) {
            vm.playbackNavTarget = PodcastViewModel.PlaybackNavTarget.NowPlaying
            intent.action = null
            intent.removeExtra("action")
            intent.removeExtra("from_media_notification")
        }
    }
}

@Composable
fun ExpressivePodcastTheme(themeConfig: Int = 0, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = when (themeConfig) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val dynamicContext = remember(context, darkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetUiMode = if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val overrideConfig = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or targetUiMode
            }
            context.createConfigurationContext(overrideConfig)
        } else {
            context
        }
    }

    val colorScheme = remember(darkTheme, dynamicContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (darkTheme) dynamicDarkColorScheme(dynamicContext) else dynamicLightColorScheme(dynamicContext)
            } catch (_: Exception) {
                if (darkTheme) darkColorScheme() else lightColorScheme()
            }
        } else {
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.isForceDarkAllowed = false
        }
        val activity = context as? ComponentActivity
        SideEffect {
            if (activity != null) {
                activity.enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            } else if (context is Activity) {
                val window = context.window
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}

@Composable
fun rememberIs3ButtonNavigation(): Boolean {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val tappableBottom = WindowInsets.tappableElement.getBottom(density)
    val gestureLeft = WindowInsets.systemGestures.getLeft(density, layoutDirection)
    val gestureRight = WindowInsets.systemGestures.getRight(density, layoutDirection)
    val navBarsBottom = WindowInsets.navigationBars.getBottom(density)

    return remember(tappableBottom, gestureLeft, gestureRight, navBarsBottom) {
        val navModeSetting = try {
            android.provider.Settings.Secure.getInt(
                context.contentResolver,
                "navigation_mode",
                -1
            )
        } catch (_: Exception) {
            -1
        }
        if (navModeSetting == 0 || navModeSetting == 1) {
            true
        } else if (navModeSetting == 2) {
            false
        } else {
            tappableBottom > 0 || (gestureLeft == 0 && gestureRight == 0 && navBarsBottom > 0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastAppScreen(vm: PodcastViewModel = viewModel()) {
    val context = LocalContext.current
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Subscriptions, 1 = Discover, 2 = Now Playing, 3 = Settings, 4 = Up Next
    var showPresetsSheet by remember { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    androidx.activity.compose.BackHandler(
        enabled = activeTab != 0 || vm.subscriptionSelectedPodcast != null || vm.discoverSelectedPodcast != null
    ) {
        when {
            activeTab == 0 && vm.subscriptionSelectedPodcast != null -> {
                vm.subscriptionSelectedPodcast = null
            }
            activeTab == 1 && vm.discoverSelectedPodcast != null -> {
                vm.discoverSelectedPodcast = null
            }
            else -> {
                activeTab = 0
            }
        }
    }

    LaunchedEffect(vm.playbackNavTarget) {
        when (vm.playbackNavTarget) {
            PodcastViewModel.PlaybackNavTarget.SubscriptionsHome -> {
                vm.subscriptionSelectedPodcast = null
                vm.discoverSelectedPodcast = null
                activeTab = 0
                vm.playbackNavTarget = null
            }
            PodcastViewModel.PlaybackNavTarget.EpisodeList -> {
                if (vm.discoverSelectedPodcast != null) {
                    activeTab = 1
                } else {
                    if (vm.subscriptionSelectedPodcast == null && vm.currentPodcast != null) {
                        vm.subscriptionSelectedPodcast = vm.currentPodcast
                        vm.loadSubscriptionPodcastEpisodes(vm.currentPodcast!!)
                    }
                    activeTab = 0
                }
                vm.playbackNavTarget = null
            }
            PodcastViewModel.PlaybackNavTarget.NowPlaying -> {
                activeTab = 2
                vm.playbackNavTarget = null
            }
            null -> {}
        }
    }

    val rewindPreset by vm.rewindSec.collectAsState()
    val forwardPreset by vm.forwardSec.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorResource(R.color.ic_launcher_background),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            "ExpressoPod",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { activeTab = 4 }) {
                        BadgedBox(
                            badge = {
                                if (vm.upNextQueue.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = CircleShape
                                        )
                                    ) {
                                        AnimatedContent(
                                            targetState = vm.upNextQueue.size,
                                            transitionSpec = {
                                                (slideInVertically { height -> height } + fadeIn())
                                                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                            },
                                            label = "QueueBadgeCount"
                                        ) { count ->
                                            Text(
                                                text = if (count > 99) "99+" else "$count",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    lineHeight = 12.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Podcasts,
                                contentDescription = "Up Next",
                                tint = if (activeTab == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val is3ButtonNav = rememberIs3ButtonNavigation()
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val screenWidthDp = configuration.screenWidthDp
            val screenHeightDp = configuration.screenHeightDp
            val fontScale = density.fontScale

            val isSmallScreen = screenWidthDp < 360 || screenHeightDp < 700

            // Responsive outer horizontal padding: on compact screens, conserve horizontal space
            val barHorizontalPadding = when {
                screenWidthDp < 340 -> 4.dp
                screenWidthDp < 370 -> 8.dp
                screenWidthDp < 400 -> 12.dp
                else -> if (is3ButtonNav) 12.dp else 16.dp
            }

            // In 3-button nav, sit snugly right above the system navigation bar without colliding
            val barBottomPadding = if (is3ButtonNav) {
                if (isSmallScreen) 2.dp else 4.dp
            } else {
                if (isSmallScreen) 6.dp else 12.dp
            }
            val barTopPadding = if (is3ButtonNav) 2.dp else 4.dp

            val barHeight = when {
                isSmallScreen || is3ButtonNav -> 68.dp
                else -> 74.dp
            }

            // Responsive text sizing for tabs to fit perfectly on different screens and font scales
            val tabFontSize = when {
                screenWidthDp < 340 || fontScale > 1.25f -> 10.sp
                screenWidthDp < 370 || fontScale > 1.1f -> 10.5.sp
                screenWidthDp < 410 -> 11.5.sp
                else -> 12.sp
            }

            val iconSize = if (isSmallScreen) 22.dp else 24.dp

            val tabTextStyle = MaterialTheme.typography.labelSmall.copy(
                fontSize = tabFontSize,
                lineBreak = LineBreak.Heading,
                hyphens = Hyphens.None
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentEpisode = vm.currentEpisode
                val currentPodcast = vm.currentPodcast
                val isMiniPlayerVisible = activeTab != 2 && currentEpisode != null && currentPodcast != null

                AnimatedVisibility(
                    visible = isMiniPlayerVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    if (currentEpisode != null && currentPodcast != null) {
                        MiniPlayer(
                            podcast = currentPodcast,
                            episode = currentEpisode,
                            isPlaying = vm.isPlaying,
                            currentPos = vm.currentPosition,
                            duration = vm.duration,
                            onTogglePlayPause = { vm.togglePlayPause() },
                            onSkipForward = { vm.skipForward() },
                            onClick = { activeTab = 2 }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding() // Ensures bottom bar is always placed above 3-button nav and gesture insets
                        .padding(
                            start = barHorizontalPadding,
                            end = barHorizontalPadding,
                            top = barTopPadding,
                            bottom = barBottomPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(if (is3ButtonNav) 22.dp else 30.dp)),
                        tonalElevation = if (is3ButtonNav) 6.dp else 10.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        val tabs = listOf(
                            Triple(0, Icons.Default.Subscriptions, "Subscriptions"),
                            Triple(1, Icons.Default.Explore, "Discover"),
                            Triple(2, Icons.Default.GraphicEq, "Now Playing"),
                            Triple(3, Icons.Default.Settings, "Settings")
                        )

                        tabs.forEach { (index, icon, label) ->
                            val selected = activeTab == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = index },
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(iconSize)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = tabTextStyle.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            saveableStateHolder.SaveableStateProvider(activeTab) {
                when (activeTab) {
                    0 -> {
                        SubscriptionsScreen(
                            vm = vm,
                            onEpisodeSelected = { podcast, episode ->
                                vm.playEpisode(podcast, episode, autoPlay = false)
                                activeTab = 2
                            }
                        )
                    }
                    1 -> {
                        DiscoverScreen(
                            vm = vm,
                            onEpisodeSelected = { podcast, episode ->
                                vm.playEpisode(podcast, episode, autoPlay = false)
                                activeTab = 2
                            }
                        )
                    }
                    2 -> {
                        ExpressivePlayerScreen(
                            vm = vm,
                            rewindSec = rewindPreset,
                            forwardSec = forwardPreset,
                            onOpenPresets = { showPresetsSheet = true }
                        )
                    }
                    3 -> {
                        SettingsScreen(vm = vm)
                    }
                    4 -> {
                        UpNextScreen(
                            vm = vm,
                            onClose = { activeTab = 0 }
                        )
                    }
                }
            }
        }

        if (showPresetsSheet) {
            SkipPresetBottomSheet(
                currentRewind = rewindPreset,
                currentForward = forwardPreset,
                onDismiss = { showPresetsSheet = false },
                onSave = { r, f -> vm.updatePresets(r, f) }
            )
        }
    }
}

@Composable
fun MiniPlayer(
    podcast: Podcast,
    episode: Episode,
    isPlaying: Boolean,
    currentPos: Long,
    duration: Long,
    onTogglePlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Column {
            if (duration > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = podcast.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onSkipForward,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Fast Forward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: PodcastViewModel) {
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }

    if (showAboutScreen) {
        AboutScreen(onBack = { showAboutScreen = false })
        return
    }

    val context = LocalContext.current
    val themeConfig by vm.themeConfig.collectAsState()
    val rewindPreset by vm.rewindSec.collectAsState()
    val forwardPreset by vm.forwardSec.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val showRemainingTime by vm.showRemainingTime.collectAsState()
    val cachePlayingEpisode by vm.cachePlayingEpisode.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()
    val mutedPodcastUrls by vm.mutedNotificationPodcasts.collectAsState()
    val podcastsToNotifyExpanded by vm.podcastsToNotifyExpanded.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            vm.setNotificationsEnabled(true)
            Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            vm.setNotificationsEnabled(false)
            Toast.makeText(context, "Notification permission is required to receive episode updates.", Toast.LENGTH_LONG).show()
        }
    }

    val launcherExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        uri?.let { vm.exportOpml(it) }
    }

    val launcherImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.importOpml(it) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 1. Appearance
        SettingsCategoryHeader(
            title = "Appearance",
            icon = Icons.Default.Palette
        )
        ThemeSettingsCard(
            currentTheme = themeConfig,
            onSelectTheme = { vm.updateTheme(it) }
        )

        // 2. Display Options
        SettingsCategoryHeader(
            title = "Display Options",
            icon = Icons.Default.StayCurrentPortrait
        )
        KeepScreenOnCard(
            keepScreenOn = keepScreenOn,
            onToggleKeepScreenOn = { vm.setKeepScreenOn(it) }
        )

        // 3. Playback Options
        SettingsCategoryHeader(
            title = "Playback Options",
            icon = Icons.Default.GraphicEq
        )
        ShowRemainingTimeCard(
            showRemainingTime = showRemainingTime,
            onToggleShowRemainingTime = { vm.setShowRemainingTime(it) }
        )
        SkipPresetsCard(
            rewindPreset = rewindPreset,
            forwardPreset = forwardPreset,
            onUpdateRewind = { vm.updatePresets(it, forwardPreset) },
            onUpdateForward = { vm.updatePresets(rewindPreset, it) }
        )

        // 4. Podcast Management
        SettingsCategoryHeader(
            title = "Podcast Management",
            icon = Icons.Default.Podcasts
        )
        StreamingCacheCard(
            cachePlayingEpisode = cachePlayingEpisode,
            onToggleCachePlayingEpisode = { vm.setCachePlayingEpisode(it) }
        )
        AutoArchiveCard(
            autoArchiveEnabled = autoArchiveEnabled,
            onToggle = { vm.setAutoArchiveEnabled(it) }
        )
        DownloadsSettingsCard(
            downloadCount = downloadedEpisodes.size,
            onRemoveAllDownloads = {
                vm.deleteAllDownloads()
            }
        )

        // 5. Background Activity & Notifications
        SettingsCategoryHeader(
            title = "Background Activity & Notifications",
            icon = Icons.Default.NotificationsActive
        )
        NotificationSettingsCard(
            notificationsEnabled = notificationsEnabled,
            onToggleNotifications = { checked ->
                if (checked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            vm.setNotificationsEnabled(true)
                            Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        vm.setNotificationsEnabled(true)
                        Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    vm.setNotificationsEnabled(false)
                    Toast.makeText(context, "Notifications turned off", Toast.LENGTH_SHORT).show()
                }
            },
            onSyncNow = {
                vm.triggerEpisodeSync()
                Toast.makeText(context, "Checking subscriptions for new episodes...", Toast.LENGTH_SHORT).show()
            },
            subscriptions = subscriptions,
            mutedPodcastUrls = mutedPodcastUrls,
            onTogglePodcastNotification = { url, enabled -> vm.togglePodcastNotification(url, enabled) },
            onSetAllPodcastNotifications = { enabled -> vm.setAllPodcastNotifications(enabled) },
            isListExpanded = podcastsToNotifyExpanded,
            onToggleListExpanded = { vm.setPodcastsToNotifyExpanded(it) }
        )
        StreamingCacheCard(
            cachePlayingEpisode = cachePlayingEpisode,
            onToggleCachePlayingEpisode = { vm.setCachePlayingEpisode(it) }
        )

        // 6. Backup
        SettingsCategoryHeader(
            title = "Backup",
            icon = Icons.Default.Backup
        )
        OpmlSubscriptionsCard(
            onImport = { launcherImport.launch(arrayOf("text/xml", "application/xml", "*/*")) },
            onExport = { launcherExport.launch("subscriptions.opml") }
        )

        Spacer(Modifier.height(8.dp))

        AboutExpressoPodCard(
            onClick = { showAboutScreen = true }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val cardPadding = if (screenWidthDp < 360) 12.dp else 18.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(cardPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingOptionChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    showCheckIcon: Boolean = true,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp
    val fontScale = density.fontScale

    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    val horizPadding = when {
        !showCheckIcon -> if (screenWidthDp < 360) 2.dp else 4.dp
        screenWidthDp < 360 -> 6.dp
        else -> 8.dp
    }
    val vertPadding = if (showCheckIcon) 10.dp else 8.dp

    val chipFontSize = when {
        !showCheckIcon -> {
            when {
                screenWidthDp < 340 || fontScale > 1.25f -> 10.5.sp
                screenWidthDp < 380 || fontScale > 1.05f -> 11.5.sp
                else -> 13.sp
            }
        }
        else -> {
            when {
                screenWidthDp < 340 || fontScale > 1.25f -> 11.5.sp
                screenWidthDp < 380 || fontScale > 1.05f -> 12.5.sp
                else -> 14.sp
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = vertPadding, horizontal = horizPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected && showCheckIcon) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = chipFontSize,
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None
                ),
                color = textColor,
                fontWeight = fontWeight
            )
        }
    }
}

@Composable
private fun ThemeSettingsCard(
    currentTheme: Int,
    onSelectTheme: (Int) -> Unit
) {
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Appearance & Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose light, dark, or follow system theme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingOptionChip(
                selected = currentTheme == 0,
                label = "System",
                modifier = Modifier.weight(1f),
                onClick = { onSelectTheme(0) }
            )
            SettingOptionChip(
                selected = currentTheme == 1,
                label = "Light",
                modifier = Modifier.weight(1f),
                onClick = { onSelectTheme(1) }
            )
            SettingOptionChip(
                selected = currentTheme == 2,
                label = "Dark",
                modifier = Modifier.weight(1f),
                onClick = { onSelectTheme(2) }
            )
        }
    }
}

@Composable
private fun KeepScreenOnCard(
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit
) {
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.StayCurrentPortrait,
                    contentDescription = null,
                    tint = if (keepScreenOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Keep Screen On",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prevent device screen from sleeping while using ExpressoPod",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = keepScreenOn,
                onCheckedChange = onToggleKeepScreenOn
            )
        }
    }
}

@Composable
private fun ShowRemainingTimeCard(
    showRemainingTime: Boolean,
    onToggleShowRemainingTime: (Boolean) -> Unit
) {
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = if (showRemainingTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Show Remaining Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Display remaining time instead of total duration in player",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = showRemainingTime,
                onCheckedChange = onToggleShowRemainingTime
            )
        }
    }
}

@Composable
private fun StreamingCacheCard(
    cachePlayingEpisode: Boolean,
    onToggleCachePlayingEpisode: (Boolean) -> Unit
) {
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Cached,
                    contentDescription = null,
                    tint = if (cachePlayingEpisode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Cache Playing Episode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cache audio to local storage during streaming for smooth scrubbing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = cachePlayingEpisode,
                onCheckedChange = onToggleCachePlayingEpisode
            )
        }
    }
}

@Composable
private fun SkipPresetsCard(
    rewindPreset: Int,
    forwardPreset: Int,
    onUpdateRewind: (Int) -> Unit,
    onUpdateForward: (Int) -> Unit
) {
    val rewindOptions = remember { listOf(5, 10, 15, 30, 45) }
    val forwardOptions = remember { listOf(10, 15, 30, 45, 60) }

    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Skip Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Set jump intervals for player controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Text(
            text = "Rewind interval (${rewindPreset}s)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val chipSpacing = if (screenWidthDp < 360) 4.dp else 6.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(chipSpacing)
        ) {
            rewindOptions.forEach { sec ->
                SettingOptionChip(
                    selected = rewindPreset == sec,
                    label = "${sec}s",
                    showCheckIcon = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateRewind(sec) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Fast Forward interval (${forwardPreset}s)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(chipSpacing)
        ) {
            forwardOptions.forEach { sec ->
                SettingOptionChip(
                    selected = forwardPreset == sec,
                    label = "${sec}s",
                    showCheckIcon = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onUpdateForward(sec) }
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    subscriptions: List<Podcast>,
    mutedPodcastUrls: Set<String>,
    onTogglePodcastNotification: (String, Boolean) -> Unit,
    onSetAllPodcastNotifications: (Boolean) -> Unit,
    isListExpanded: Boolean,
    onToggleListExpanded: (Boolean) -> Unit
) {
    val activeCount = subscriptions.count { !mutedPodcastUrls.contains(it.feedUrl) }
    val totalCount = subscriptions.size

    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "New Episode Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Notify when subscriptions release new episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = onToggleNotifications
            )
        }

        if (notificationsEnabled) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Background Sync Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ExpressoPod checks your subscriptions periodically in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check Subscriptions Now")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            // Dynamic Per-Podcast Notification Selection Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleListExpanded(!isListExpanded) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Podcasts to Notify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalCount == 0) "No subscriptions yet" else "$activeCount of $totalCount podcasts active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onToggleListExpanded(!isListExpanded) }) {
                    Icon(
                        if (isListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isListExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (isListExpanded) {
                Spacer(Modifier.height(8.dp))

                if (totalCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No subscriptions yet. Subscribed podcasts will appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onSetAllPodcastNotifications(true) },
                            modifier = Modifier.weight(1f),
                            enabled = activeCount < totalCount
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onSetAllPodcastNotifications(false) },
                            modifier = Modifier.weight(1f),
                            enabled = activeCount > 0
                        ) {
                            Text("Mute All", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subscriptions.forEach { podcast ->
                            val isNotified = !mutedPodcastUrls.contains(podcast.feedUrl)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onTogglePodcastNotification(podcast.feedUrl, !isNotified) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = podcast.artworkUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isNotified) "Notifying" else "Muted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isNotified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = isNotified,
                                        onCheckedChange = { checked ->
                                            onTogglePodcastNotification(podcast.feedUrl, checked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpmlSubscriptionsCard(
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ImportExport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "OPML Subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Transfer feeds between podcast apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onImport,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import OPML")
            }
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export OPML")
            }
        }
    }
}

@Composable
private fun AutoArchiveCard(
    autoArchiveEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Auto-Archive Finished Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically archive episodes after they finish playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = autoArchiveEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun DownloadsSettingsCard(
    downloadCount: Int,
    onRemoveAllDownloads: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Remove All Downloads?")
            },
            text = {
                Text("This will permanently delete all $downloadCount downloaded episode(s) from your device. You can stream or re-download them at any time.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onRemoveAllDownloads()
                        Toast.makeText(context, "All downloads removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Downloaded Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (downloadCount > 0) "$downloadCount episode(s) downloaded" else "No downloaded episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { showConfirmDialog = true },
                enabled = downloadCount > 0,
                colors = ButtonDefaults.filledTonalButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Remove All")
            }
        }
    }
}

@Composable
private fun AboutExpressoPodCard(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "About ExpressoPod",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open About Screen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    androidx.activity.compose.BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Icon Graphic
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF3E2010),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(120.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "ExpressoPod App Icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // App Name
                Text(
                    text = "ExpressoPod",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Space in between
                Spacer(Modifier.height(16.dp))

                // App Version Number
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Copyright
                Text(
                    text = "Copyright 2026 by rpmtech",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowCard(
    podcast: Podcast,
    episode: Episode,
    isArchived: Boolean,
    isPlayed: Boolean = false,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadSize: String?,
    remainingTimeText: String? = null,
    playbackProgress: Float? = null,
    isCurrentlyPlaying: Boolean = false,
    isKeepUnarchived: Boolean = false,
    isAutoArchiveEnabled: Boolean = true,
    isSelected: Boolean = false,
    isInUpNext: Boolean = false,
    onPlay: () -> Unit,
    onLongClick: () -> Unit = {},
    onAddToUpNext: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleKeepUnarchived: () -> Unit = {},
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    val baseColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val cardColor = if (isSelected) {
        Color(
            red = baseColor.red * 0.65f,
            green = baseColor.green * 0.65f,
            blue = baseColor.blue * 0.65f,
            alpha = 1f
        )
    } else {
        baseColor
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isArchived || isPlayed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${episode.pubDate} • ${formatDurationText(episode.durationText).ifBlank { "Audio" }}" +
                                (if (isArchived) " • Archived" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isKeepUnarchived) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "Episode Pinned",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (isPlayed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "Played",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else if (remainingTimeText != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        if (isCurrentlyPlaying) Icons.Default.GraphicEq else Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = remainingTimeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (isDownloading) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    val pct = ((downloadProgress ?: 0f) * 100).toInt()
                                    Text(
                                        text = if (pct > 0) "Downloading $pct%" else "Downloading...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    if (!isPlayed && playbackProgress != null && playbackProgress > 0f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { playbackProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Row: PocketCasts style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Absorb clicks so they do not trigger card onPlay */ },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onCancelDownload() },
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = downloadProgress ?: 0f
                        if (progress > 0f) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel download",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isDownloaded) {
                    IconButton(
                        onClick = onDeleteDownload,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Downloaded - tap to delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download for offline listening",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onAddToUpNext,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        if (isInUpNext) Icons.AutoMirrored.Filled.QueueMusic else Icons.Default.Queue,
                        contentDescription = if (isInUpNext) "In Up Next" else "Add to Up Next",
                        tint = if (isInUpNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayed,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        if (isPlayed) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = if (isPlayed) "Mark as unplayed" else "Mark as played",
                        tint = if (isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!isKeepUnarchived) {
                    IconButton(
                        onClick = onToggleArchive,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (isArchived) "Unarchive" else "Archive",
                            tint = if (isArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isAutoArchiveEnabled || isKeepUnarchived) {
                    if (isKeepUnarchived) {
                        FilledTonalIconButton(
                            onClick = onToggleKeepUnarchived,
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Episode Pinned (tap to unpin)",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onToggleKeepUnarchived,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = "Episode Unpinned (tap to pin)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastOptionsMenu(
    podcast: Podcast,
    episodes: List<Episode>,
    downloadedEpisodes: List<DownloadedEpisode>,
    activeDownloads: Map<String, Float>,
    archivedEpisodes: Set<String>,
    keepUnarchivedEpisodes: Set<String> = emptySet(),
    selectedEpisodeUrls: Set<String> = emptySet(),
    showArchived: Boolean = false,
    filterMode: Int = 0,
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDownloadAll: (List<Episode>) -> Unit,
    onArchiveAll: (List<Episode>) -> Unit,
    onUnarchiveAll: (List<Episode>) -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val hasSelected = selectedEpisodeUrls.isNotEmpty()
    val candidateEpisodes = if (hasSelected) {
        episodes.filter { selectedEpisodeUrls.contains(it.audioUrl) }
    } else {
        episodes
    }

    val selectedArchivedCount = candidateEpisodes.count { archivedEpisodes.contains(it.audioUrl) }
    val selectedUnarchivedCount = candidateEpisodes.size - selectedArchivedCount

    val isUnarchive = if (hasSelected) {
        selectedArchivedCount > 0 && (selectedUnarchivedCount == 0 || showArchived || filterMode == 3)
    } else {
        showArchived || filterMode == 3
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Podcast options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (hasSelected) {
                DropdownMenuItem(
                    text = { Text("Select All") },
                    leadingIcon = {
                        Icon(Icons.Default.SelectAll, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSelectAll()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(if (hasSelected) "Download Selected" else "Download All") },
                leadingIcon = {
                    Icon(Icons.Default.Download, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    val toDownload = candidateEpisodes.filter { ep ->
                        !downloadedEpisodes.any { it.audioUrl == ep.audioUrl } && !activeDownloads.containsKey(ep.audioUrl)
                    }
                    if (toDownload.isNotEmpty()) {
                        onDownloadAll(toDownload)
                        Toast.makeText(
                            context,
                            if (hasSelected) "Downloading ${toDownload.size} selected episode(s)..." else "Downloading ${toDownload.size} episode(s)...",
                            Toast.LENGTH_SHORT
                        ).show()
                        onClearSelection()
                    } else {
                        Toast.makeText(
                            context,
                            if (hasSelected) "Selected episode(s) are already downloaded" else "All episodes are already downloaded",
                            Toast.LENGTH_SHORT
                        ).show()
                        onClearSelection()
                    }
                }
            )
            if (isUnarchive) {
                DropdownMenuItem(
                    text = { Text(if (hasSelected) "Unarchive Selected" else "Unarchive All") },
                    leadingIcon = {
                        Icon(Icons.Default.Unarchive, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        val toUnarchive = candidateEpisodes.filter { archivedEpisodes.contains(it.audioUrl) }
                        if (toUnarchive.isNotEmpty()) {
                            onUnarchiveAll(toUnarchive)
                            Toast.makeText(
                                context,
                                if (hasSelected) "Unarchived ${toUnarchive.size} selected episode(s)" else "Unarchived ${toUnarchive.size} episode(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            onClearSelection()
                        } else {
                            Toast.makeText(
                                context,
                                if (hasSelected) "Selected episode(s) are not archived" else "No archived episodes to unarchive",
                                Toast.LENGTH_SHORT
                            ).show()
                            onClearSelection()
                        }
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(if (hasSelected) "Archive Selected" else "Archive All") },
                    leadingIcon = {
                        Icon(Icons.Default.Archive, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        val toArchive = candidateEpisodes.filter { 
                            !archivedEpisodes.contains(it.audioUrl) && !keepUnarchivedEpisodes.contains(it.audioUrl) 
                        }
                        if (toArchive.isNotEmpty()) {
                            onArchiveAll(toArchive)
                            Toast.makeText(
                                context,
                                if (hasSelected) "Archived ${toArchive.size} selected episode(s)" else "Archived ${toArchive.size} episode(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            onClearSelection()
                        } else {
                            val hasPinned = candidateEpisodes.any { keepUnarchivedEpisodes.contains(it.audioUrl) && !archivedEpisodes.contains(it.audioUrl) }
                            if (hasPinned) {
                                Toast.makeText(
                                    context,
                                    if (hasSelected) "Selected episode(s) are pinned" else "All unpinned episodes are already archived",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    if (hasSelected) "Selected episode(s) are already archived" else "All episodes are already archived",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onClearSelection()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PodcastInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun PodcastDetailView(
    podcast: Podcast,
    episodes: List<Episode>,
    vm: PodcastViewModel,
    onBack: () -> Unit,
    onEpisodeSelected: (Podcast, Episode) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
    val playedEpisodes by vm.playedEpisodes.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val activeDownloads by vm.activeDownloads.collectAsState()
    val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()

    var selectedTab by rememberSaveable(podcast.feedUrl) { mutableIntStateOf(0) } // 0: Episodes, 1: Podcast Info
    var showArchived by rememberSaveable(podcast.feedUrl) { mutableStateOf(false) }
    var filterMode by rememberSaveable(podcast.feedUrl) { mutableIntStateOf(0) } // 0: All, 1: Unplayed, 2: Downloaded, 3: Archived
    var selectedEpisodeUrls by rememberSaveable(podcast.feedUrl) { mutableStateOf(emptySet<String>()) }

    BackHandler(enabled = true) {
        if (selectedEpisodeUrls.isNotEmpty()) {
            selectedEpisodeUrls = emptySet()
        } else if (selectedTab != 0) {
            selectedTab = 0
        } else {
            onBack()
        }
    }

    val displayedEpisodes = when (filterMode) {
        1 -> episodes.filter { ep -> !playedEpisodes.contains(ep.audioUrl) && !archivedEpisodes.contains(ep.audioUrl) }
        2 -> episodes.filter { ep -> downloadedEpisodes.any { it.audioUrl == ep.audioUrl } }
        3 -> episodes.filter { ep -> archivedEpisodes.contains(ep.audioUrl) }
        else -> if (showArchived) episodes else episodes.filter { ep -> !archivedEpisodes.contains(ep.audioUrl) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (selectedEpisodeUrls.isNotEmpty()) {
                IconButton(onClick = { selectedEpisodeUrls = emptySet() }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${selectedEpisodeUrls.size} selected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                val isSubscribed = subscriptions.any { it.feedUrl == podcast.feedUrl }
                IconButton(onClick = { vm.toggleSubscription(podcast) }) {
                    Icon(
                        if (isSubscribed) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            PodcastOptionsMenu(
                podcast = podcast,
                episodes = displayedEpisodes,
                downloadedEpisodes = downloadedEpisodes,
                activeDownloads = activeDownloads,
                archivedEpisodes = archivedEpisodes,
                keepUnarchivedEpisodes = keepUnarchivedEpisodes,
                selectedEpisodeUrls = selectedEpisodeUrls,
                showArchived = showArchived,
                filterMode = filterMode,
                onSelectAll = { selectedEpisodeUrls = displayedEpisodes.map { it.audioUrl }.toSet() },
                onClearSelection = { selectedEpisodeUrls = emptySet() },
                onDownloadAll = { vm.downloadAllEpisodes(podcast, it) },
                onArchiveAll = { vm.archiveAllEpisodes(it) },
                onUnarchiveAll = { vm.unarchiveAllEpisodes(it) }
            )
        }

        // Centered Podcast Artwork & Title Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.size(104.dp)
            ) {
                AsyncImage(
                    model = podcast.artworkUrl,
                    contentDescription = podcast.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (podcast.author.isNotBlank() && podcast.author != "Unknown") {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Navigation Tabs: Episodes & Podcast Info
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        if (episodes.isNotEmpty()) "Episodes (${episodes.size})" else "Episodes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Podcast Info",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (vm.isLoading && episodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (selectedTab) {
                0 -> {
                    // Episodes Tab
                    val (savedIndex, savedOffset) = remember(podcast.feedUrl) {
                        vm.getEpisodeListPosition(podcast.feedUrl)
                    }
                    val episodeListState = rememberSaveable(podcast.feedUrl, saver = LazyListState.Saver) {
                        LazyListState(savedIndex, savedOffset)
                    }

                    LaunchedEffect(episodeListState) {
                        snapshotFlow { episodeListState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { idx ->
                                vm.saveEpisodeListPosition(podcast.feedUrl, idx, episodeListState.firstVisibleItemScrollOffset)
                            }
                    }
                    DisposableEffect(episodeListState) {
                        onDispose {
                            vm.saveEpisodeListPosition(podcast.feedUrl, episodeListState.firstVisibleItemIndex, episodeListState.firstVisibleItemScrollOffset)
                        }
                    }
                    val downloadedSet = remember(downloadedEpisodes) { downloadedEpisodes.map { it.audioUrl }.toSet() }
                    val unarchivedCount = episodes.count { ep -> !archivedEpisodes.contains(ep.audioUrl) }
                    val unplayedCount = episodes.count { ep -> !playedEpisodes.contains(ep.audioUrl) && !archivedEpisodes.contains(ep.audioUrl) }
                    val downloadedCount = episodes.count { ep -> downloadedSet.contains(ep.audioUrl) }
                    val archivedCount = episodes.count { ep -> archivedEpisodes.contains(ep.audioUrl) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterMode == 0,
                            onClick = { filterMode = 0 },
                            label = { Text(if (showArchived) "All (${episodes.size})" else "All ($unarchivedCount)") },
                            leadingIcon = if (filterMode == 0) {
                                { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = filterMode == 1,
                            onClick = { filterMode = 1 },
                            label = { Text("Unplayed ($unplayedCount)") },
                            leadingIcon = if (filterMode == 1) {
                                { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = filterMode == 2,
                            onClick = { filterMode = 2 },
                            label = { Text("Downloaded ($downloadedCount)") },
                            leadingIcon = {
                                Icon(
                                    if (filterMode == 2) Icons.Default.CheckCircle else Icons.Default.DownloadDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = filterMode == 3,
                            onClick = { filterMode = 3 },
                            label = { Text("Archived ($archivedCount)") },
                            leadingIcon = if (filterMode == 3) {
                                { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = showArchived,
                            onClick = {
                                showArchived = !showArchived
                                if (filterMode == 3 && !showArchived) {
                                    filterMode = 0
                                }
                            },
                            label = { Text(if (showArchived) "Hide Archived" else "Show Archived") },
                            leadingIcon = {
                                Icon(
                                    if (showArchived) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }

                    if (displayedEpisodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    when (filterMode) {
                                        1 -> Icons.Default.CheckCircle
                                        2 -> Icons.Default.DownloadDone
                                        3 -> Icons.Default.Archive
                                        else -> Icons.AutoMirrored.Filled.List
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (filterMode == 0 && !showArchived && archivedCount > 0) {
                                        "All episodes in this podcast are archived"
                                    } else when (filterMode) {
                                        1 -> "All caught up! No unplayed episodes."
                                        2 -> "No downloaded episodes for this podcast"
                                        3 -> "No archived episodes for this podcast"
                                        else -> "No episodes found"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (filterMode == 0 && !showArchived && archivedCount > 0) {
                                        "Tap 'Show Archived' above to view archived episodes."
                                    } else when (filterMode) {
                                        1 -> "New episodes will appear here when released."
                                        2 -> "Tap the download icon on any episode to save it for offline listening."
                                        3 -> "Archived episodes will appear here."
                                        else -> "Check back later for new episodes."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = episodeListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = displayedEpisodes,
                                key = { it.audioUrl }
                            ) { ep ->
                                val isArchived = archivedEpisodes.contains(ep.audioUrl)
                                val isPlayed = playedEpisodes.contains(ep.audioUrl)
                                val isDownloaded = downloadedSet.contains(ep.audioUrl)
                                val downloadProgress = activeDownloads[ep.audioUrl]
                                val isDownloading = downloadProgress != null
                                val downloadSize = vm.getFormattedDownloadSize(ep.audioUrl)

                                val isCurrent = vm.currentEpisode?.audioUrl == ep.audioUrl
                                val isPlaying = isCurrent && vm.isPlaying
                                val totalDurationMs = if (isCurrent && vm.duration > 0L) vm.duration else parseDurationMs(ep.durationText)
                                val currentPosMs = if (isCurrent) vm.currentPosition else vm.getSavedPosition(ep.audioUrl)

                                val remainingTimeText = if (currentPosMs > 0L && totalDurationMs > 0L) {
                                    val remainingMs = (totalDurationMs - currentPosMs).coerceAtLeast(0L)
                                    formatTimeRemaining(remainingMs)
                                } else if (isCurrent && currentPosMs > 0L) {
                                    "${currentPosMs / 60000}m listened"
                                } else null

                                val playbackProgress = if (currentPosMs > 0L && totalDurationMs > 0L) {
                                    (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                                } else null

                                val isKeepUnarchived = keepUnarchivedEpisodes.contains(ep.audioUrl)
                                val isSelected = selectedEpisodeUrls.contains(ep.audioUrl)
                                val isInUpNext = vm.upNextQueue.any { it.episode.audioUrl == ep.audioUrl }

                                EpisodeRowCard(
                                    podcast = podcast,
                                    episode = ep,
                                    isArchived = isArchived,
                                    isPlayed = isPlayed,
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    downloadProgress = downloadProgress,
                                    downloadSize = downloadSize,
                                    remainingTimeText = remainingTimeText,
                                    playbackProgress = playbackProgress,
                                    isCurrentlyPlaying = isPlaying,
                                    isKeepUnarchived = isKeepUnarchived,
                                    isAutoArchiveEnabled = autoArchiveEnabled,
                                    isSelected = isSelected,
                                    isInUpNext = isInUpNext,
                                    onPlay = {
                                        if (selectedEpisodeUrls.isNotEmpty()) {
                                            val sel = selectedEpisodeUrls.contains(ep.audioUrl)
                                            selectedEpisodeUrls = if (sel) selectedEpisodeUrls - ep.audioUrl else selectedEpisodeUrls + ep.audioUrl
                                        } else {
                                            val epIndex = displayedEpisodes.indexOfFirst { it.audioUrl == ep.audioUrl }
                                            if (epIndex >= 0) {
                                                vm.saveEpisodeListPosition(podcast.feedUrl, epIndex, 0)
                                            }
                                            onEpisodeSelected(podcast, ep)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val sel = selectedEpisodeUrls.contains(ep.audioUrl)
                                        selectedEpisodeUrls = if (sel) selectedEpisodeUrls - ep.audioUrl else selectedEpisodeUrls + ep.audioUrl
                                    },
                                    onAddToUpNext = {
                                        if (vm.currentEpisode?.audioUrl == ep.audioUrl) {
                                            Toast.makeText(context, "Currently playing", Toast.LENGTH_SHORT).show()
                                        } else {
                                            if (isInUpNext) {
                                                vm.removeFromUpNextByUrl(ep.audioUrl)
                                                Toast.makeText(context, "Removed from Up Next", Toast.LENGTH_SHORT).show()
                                            } else {
                                                vm.addToUpNext(podcast, ep)
                                                Toast.makeText(context, "Added to Up Next (playing next)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onTogglePlayed = { vm.markEpisodePlayed(ep, !isPlayed) },
                                    onToggleArchive = { vm.toggleArchive(ep, !isArchived) },
                                    onToggleKeepUnarchived = {
                                        val willKeep = !isKeepUnarchived
                                        vm.toggleKeepUnarchived(ep)
                                        Toast.makeText(
                                            context,
                                            if (willKeep) "Episode Pinned (will stay unarchived)" else "Episode Unpinned (auto-archive restored)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDownload = { vm.downloadEpisode(podcast, ep) },
                                    onCancelDownload = { vm.cancelDownload(ep.audioUrl) },
                                    onDeleteDownload = { vm.deleteDownload(ep.audioUrl) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Podcast Info Tab
                    val cachedFeed = remember(podcast.feedUrl) { NetworkModule.getCachedFeed(podcast.feedUrl) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        // About Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "About",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val rawDesc = podcast.description.ifBlank { cachedFeed?.channelDescription ?: "" }
                                if (rawDesc.isNotBlank()) {
                                    FormattedEpisodeNotes(
                                        notesText = rawDesc,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "No description available for this podcast.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Details Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                val authorText = podcast.author.ifBlank { cachedFeed?.channelAuthor ?: "" }
                                if (authorText.isNotBlank() && authorText != "Unknown") {
                                    PodcastInfoRow(
                                        icon = Icons.Default.Person,
                                        label = "Author / Host",
                                        value = authorText
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                }

                                PodcastInfoRow(
                                    icon = Icons.AutoMirrored.Filled.List,
                                    label = "Total Episodes",
                                    value = "${episodes.size} episodes"
                                )

                                val latestPubDate = episodes.firstOrNull()?.pubDate ?: ""
                                if (latestPubDate.isNotBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                    PodcastInfoRow(
                                        icon = Icons.Default.Schedule,
                                        label = "Latest Release",
                                        value = latestPubDate
                                    )
                                }

                                val websiteUrl = (cachedFeed?.channelLink ?: "").trim()
                                if (websiteUrl.isNotBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                SecurityUtils.openSafeUrl(context, websiteUrl)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Website", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = websiteUrl,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Feed & Subscription Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Feed & Subscription",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.RssFeed,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("RSS Feed URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = podcast.feedUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("RSS Feed URL", podcast.feedUrl))
                                            Toast.makeText(context, "RSS feed URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy RSS Feed URL", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val isSubscribed = subscriptions.any { it.feedUrl == podcast.feedUrl }
                                FilledTonalButton(
                                    onClick = { vm.toggleSubscription(podcast) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        if (isSubscribed) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isSubscribed) "Subscribed (Tap to unsubscribe)" else "Subscribe to Podcast")
                                }

                                if (isSubscribed) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val mutedPodcasts by vm.mutedNotificationPodcasts.collectAsState()
                                    val isNotified = !mutedPodcasts.contains(podcast.feedUrl)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                                            .clickable { vm.togglePodcastNotification(podcast.feedUrl, !isNotified) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                if (isNotified) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isNotified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    "New Episode Notifications",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    if (isNotified) "Notifying on new releases" else "Notifications muted",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = isNotified,
                                            onCheckedChange = { checked -> vm.togglePodcastNotification(podcast.feedUrl, checked) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverScreen(vm: PodcastViewModel, onEpisodeSelected: (Podcast, Episode) -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (vm.discoverSelectedPodcast != null) {
            PodcastDetailView(
                podcast = vm.discoverSelectedPodcast!!,
                episodes = vm.discoverEpisodes,
                vm = vm,
                onBack = { vm.discoverSelectedPodcast = null },
                onEpisodeSelected = onEpisodeSelected
            )
        } else {
            // Search Input
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { query ->
                    vm.searchQuery = query
                    if (query.isBlank()) {
                        vm.clearSearch()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search podcasts...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        vm.discoverSelectedPodcast = null
                        vm.search()
                    },
                    onSearch = {
                        keyboardController?.hide()
                        vm.discoverSelectedPodcast = null
                        vm.search()
                    }
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.searchQuery.isNotBlank()) {
                            IconButton(onClick = { vm.clearSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                keyboardController?.hide()
                                vm.discoverSelectedPodcast = null
                                vm.search()
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Search")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            vm.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (vm.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (vm.podcasts.isNotEmpty()) {
                // Podcast Search Results List
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.podcasts, key = { it.feedUrl }) { podcast ->
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.loadDiscoverPodcastEpisodes(podcast) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = podcast.artworkUrl,
                                    contentDescription = podcast.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = podcast.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = podcast.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty Discover State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Discover Podcasts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Search millions of shows across Apple Podcasts & RSS feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressivePlayerScreen(
    vm: PodcastViewModel,
    rewindSec: Int,
    forwardSec: Int,
    onOpenPresets: () -> Unit
) {
    val episode = vm.currentEpisode
    val podcast = vm.currentPodcast
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Player, 1 = Chapters, 2 = Notes

    // Expressive Spring Animation for Play/Pause Pill
    val playCorner by animateDpAsState(
        targetValue = if (vm.isPlaying) 24.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "corner"
    )
    val playWidth by animateDpAsState(
        targetValue = if (vm.isPlaying) 128.dp else 96.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "width"
    )
    val playColor by animateColorAsState(
        targetValue = if (vm.isPlaying) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
        label = "color"
    )
    val playContentColor by animateColorAsState(
        targetValue = if (vm.isPlaying) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
        label = "contentColor"
    )

    val playerConfig = LocalConfiguration.current
    val playerScreenWidth = playerConfig.screenWidthDp
    val playerScreenHeight = playerConfig.screenHeightDp

    val artworkSize = when {
        playerScreenHeight < 680 || playerScreenWidth < 340 -> 190.dp
        playerScreenHeight < 740 || playerScreenWidth < 370 -> 220.dp
        else -> 260.dp
    }

    val buttonSpacing = when {
        playerScreenWidth < 340 -> 8.dp
        playerScreenWidth < 380 -> 12.dp
        else -> 20.dp
    }

    val skipBtnSize = when {
        playerScreenWidth < 340 -> 58.dp
        playerScreenWidth < 380 -> 64.dp
        else -> 72.dp
    }

    val dynamicPlayWidth = when {
        playerScreenWidth < 340 -> if (vm.isPlaying) 100.dp else 80.dp
        playerScreenWidth < 380 -> if (vm.isPlaying) 112.dp else 88.dp
        else -> playWidth
    }

    val playerHorizPadding = if (playerScreenWidth < 360) 12.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = playerHorizPadding, end = playerHorizPadding, top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expressive Top Tab Navigation: Player vs Chapters vs Notes (Slimmed)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Player",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val count = vm.currentChapters.size
                    Text(
                        text = if (count > 0) "Chapters ($count)" else "Chapters",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (selectedTab == 0) {
            // ================= PLAYER TAB =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expressive Squircle Artwork Container
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    tonalElevation = 10.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(artworkSize)
                ) {
                    if (podcast != null) {
                        AsyncImage(
                            model = podcast.artworkUrl,
                            contentDescription = podcast.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Podcasts,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Episode Title & Podcast Subtitle (no truncation!)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = episode?.title ?: "Select an Episode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = podcast?.title ?: "Use Discover to search podcasts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
                    val activeDownloads by vm.activeDownloads.collectAsState()
                    val isDownloaded = episode?.let { ep -> downloadedEpisodes.any { it.audioUrl == ep.audioUrl } } ?: false
                    val isDownloading = episode?.let { ep -> activeDownloads.containsKey(ep.audioUrl) } ?: false
                    val downloadProgress = episode?.let { ep -> activeDownloads[ep.audioUrl] }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    episode?.let { vm.deleteDownload(it.audioUrl) }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.OfflinePin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    val size = episode?.let { vm.getFormattedDownloadSize(it.audioUrl) }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (size != null) "Downloaded • $size" else "Downloaded",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else if (isDownloading) {
                            AssistChip(
                                onClick = { episode?.let { vm.cancelDownload(it.audioUrl) } },
                                label = {
                                    val pct = ((downloadProgress ?: 0f) * 100).toInt()
                                    Text("Downloading $pct%")
                                },
                                leadingIcon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else if (episode != null && podcast != null) {
                            AssistChip(
                                onClick = { vm.downloadEpisode(podcast, episode) },
                                label = { Text("Download") },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
                        if (episode != null) {
                            val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
                            val isPinned = keepUnarchivedEpisodes.contains(episode.audioUrl)
                            if (autoArchiveEnabled || isPinned) {
                                AssistChip(
                                    onClick = {
                                        val willPin = !isPinned
                                        vm.toggleKeepUnarchived(episode)
                                        Toast.makeText(
                                            context,
                                            if (willPin) "Episode Pinned (will stay unarchived)" else "Episode Unpinned (auto-archive restored)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    label = { Text(if (isPinned) "Episode Pinned" else "Episode Unpinned") },
                                    leadingIcon = {
                                        Icon(
                                            if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = if (isPinned) "Episode Pinned" else "Episode Unpinned",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            } else {
                                val archivedEpisodes by vm.archivedEpisodes.collectAsState()
                                val isArchived = archivedEpisodes.contains(episode.audioUrl)
                                AssistChip(
                                    onClick = { vm.toggleArchive(episode, !isArchived) },
                                    label = { Text(if (isArchived) "Archived" else "Archive") },
                                    leadingIcon = {
                                        Icon(
                                            if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }

                // Current Chapter Pill (Clicking jumps directly to Chapters section)
                if (vm.currentChapters.isNotEmpty()) {
                    val activeCh = vm.currentChapter
                    if (activeCh != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable { selectedTab = 1 }
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = "Chapter",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Ch. ${vm.currentChapterIndex + 1}: ${activeCh.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Progress Scrubber
                val showRemainingTime by vm.showRemainingTime.collectAsState()
                Column(modifier = Modifier.fillMaxWidth()) {
                    val progress = (vm.currentPosition.toFloat() / vm.duration.toFloat()).coerceIn(0f, 1f)
                    Slider(
                        value = progress,
                        onValueChange = { vm.seekTo(it) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(vm.currentPosition), style = MaterialTheme.typography.labelMedium)
                        val remainingMs = (vm.duration - vm.currentPosition).coerceAtLeast(0L)
                        val rightTimeText = if (showRemainingTime) "-${formatTime(remainingMs)}" else formatTime(vm.duration)
                        Text(
                            text = rightTimeText,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable {
                                vm.setShowRemainingTime(!showRemainingTime)
                            }
                        )
                    }
                }


                // Transport Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    FilledTonalButton(
                        onClick = { vm.skipRewind() },
                        shape = CircleShape,
                        modifier = Modifier.size(skipBtnSize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Replay, contentDescription = "Rewind")
                            Text(
                                "-${rewindSec}s",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (playerScreenWidth < 360) 10.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineBreak = LineBreak.Heading,
                                    hyphens = Hyphens.None
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { vm.togglePlayPause() },
                        shape = RoundedCornerShape(playCorner),
                        modifier = Modifier.size(width = dynamicPlayWidth, height = if (playerScreenWidth < 360) 68.dp else 78.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = playColor,
                            contentColor = playContentColor
                        )
                    ) {
                        Icon(
                            imageVector = if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(if (playerScreenWidth < 360) 30.dp else 38.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { vm.skipForward() },
                        shape = CircleShape,
                        modifier = Modifier.size(skipBtnSize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Forward30, contentDescription = "Fast Forward")
                            Text(
                                "+${forwardSec}s",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (playerScreenWidth < 360) 10.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineBreak = LineBreak.Heading,
                                    hyphens = Hyphens.None
                                )
                            )
                        }
                    }
                }

                // Quick Action Chip: Volume Boost
                val volumeBoost by vm.volumeBoostEnabled.collectAsState()
                FilterChip(
                    selected = volumeBoost,
                    onClick = { vm.setVolumeBoostEnabled(!volumeBoost) },
                    label = { Text("Volume Boost") },
                    leadingIcon = {
                        Icon(
                            if (volumeBoost) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        } else if (selectedTab == 1) {
            // ================= CHAPTERS TAB =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Chapters Header Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chapters",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val count = vm.currentChapters.size
                                val totalDurText = formatDurationText(episode?.durationText ?: "")
                                Text(
                                    text = if (count > 0) "$count Chapters${if (totalDurText.isNotBlank()) " • $totalDurText" else ""}" else "No chapters loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick chapter skip controls
                            if (vm.currentChapters.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { vm.skipToPreviousChapter() },
                                        enabled = vm.currentChapterIndex > 0
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Chapter")
                                    }
                                    IconButton(
                                        onClick = { vm.skipToNextChapter() },
                                        enabled = vm.currentChapterIndex < vm.currentChapters.size - 1
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter")
                                    }
                                }
                            }
                        }

                        // Active Chapter Progress Hero Bar
                        val activeChapter = vm.currentChapter
                        if (activeChapter != null) {
                            val chStart = activeChapter.startMs
                            val chEnd = if (activeChapter.endMs > chStart) activeChapter.endMs else vm.duration
                            val chDur = (chEnd - chStart).coerceAtLeast(1L)
                            val chElapsed = (vm.currentPosition - chStart).coerceAtLeast(0L)
                            val chProgress = (chElapsed.toFloat() / chDur.toFloat()).coerceIn(0f, 1f)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Now: ${activeChapter.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = "${formatTime(chElapsed)} / ${formatTime(chDur)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { chProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }

                if (vm.isLoadingChapters) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (vm.currentChapters.isEmpty()) {
                    // Empty state
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No Chapters Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This episode does not contain chapter markers or timestamps in its notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Chapters List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 64.dp)
                    ) {
                        itemsIndexed(vm.currentChapters) { index, chapter ->
                            val isActive = index == vm.currentChapterIndex
                            val isPast = index < vm.currentChapterIndex
                            val chDur = chapter.durationMs

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (isPast) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                tonalElevation = if (isActive) 4.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.seekToChapter(chapter)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Index / Icon badge
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = if (vm.isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                                contentDescription = "Active Chapter",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Chapter Image (if available)
                                    if (!chapter.imgUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = chapter.imgUrl,
                                            contentDescription = chapter.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }

                                    // Title & Time Info
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                            else if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatTime(chapter.startMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (chDur > 0L) {
                                                Text(
                                                    text = "• ${formatDurationText(formatTime(chDur))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }

                                    // Link Button (if chapter has external web link)
                                    if (!chapter.url.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                SecurityUtils.openSafeUrl(context, chapter.url)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Chapter Link",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ================= NOTES TAB =================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header in Notes
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = episode?.title ?: "No Episode Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = podcast?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    if (episode != null) {
                        Text(
                            text = "${episode.pubDate} • ${formatDurationText(episode.durationText).ifBlank { "Audio" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Show Notes Body
                val rawDescription = episode?.description?.ifBlank {
                    vm.episodes.find { it.audioUrl == episode.audioUrl }?.description
                } ?: ""

                if (rawDescription.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FormattedEpisodeNotes(
                            notesText = rawDescription,
                            modifier = Modifier.padding(20.dp),
                            onSeekToMs = { ms -> vm.seekToPosition(ms) }
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No Show Notes Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This episode does not include show notes or description.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Mini Transport Bar at bottom of Notes
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val progress = (vm.currentPosition.toFloat() / vm.duration.toFloat()).coerceIn(0f, 1f)
                        Slider(
                            value = progress,
                            onValueChange = { vm.seekTo(it) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(vm.currentPosition), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(vm.duration), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { vm.skipRewind() }) {
                                Icon(Icons.Default.Replay, contentDescription = "Rewind")
                            }
                            Spacer(Modifier.width(16.dp))
                            FilledIconButton(
                                onClick = { vm.togglePlayPause() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause"
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            IconButton(onClick = { vm.skipForward() }) {
                                Icon(Icons.Default.Forward30, contentDescription = "Forward")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkipPresetBottomSheet(
    currentRewind: Int,
    currentForward: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    val presetOptions = listOf(5, 10, 15, 30, 45, 60)
    var selectedRewind by remember { mutableIntStateOf(currentRewind) }
    var selectedForward by remember { mutableIntStateOf(currentForward) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Customize Skip Presets",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Text("Rewind Time (Seconds)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetOptions.forEach { sec ->
                    FilterChip(
                        selected = selectedRewind == sec,
                        onClick = {
                            selectedRewind = sec
                            onSave(selectedRewind, selectedForward)
                        },
                        label = { Text("${sec}s") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Fast Forward Time (Seconds)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetOptions.forEach { sec ->
                    FilterChip(
                        selected = selectedForward == sec,
                        onClick = {
                            selectedForward = sec
                            onSave(selectedRewind, selectedForward)
                        },
                        label = { Text("${sec}s") }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsBottomSheet(
    vm: PodcastViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            vm.setNotificationsEnabled(true)
            Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            vm.setNotificationsEnabled(false)
            Toast.makeText(context, "Notification permission is required to receive episode updates.", Toast.LENGTH_LONG).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Episode Notifications",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "New Episode Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Notify when subscribed podcasts release new episodes (opt-in)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        vm.setNotificationsEnabled(true)
                                        Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    vm.setNotificationsEnabled(true)
                                    Toast.makeText(context, "New episode notifications enabled!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                vm.setNotificationsEnabled(false)
                                Toast.makeText(context, "Notifications turned off", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (notificationsEnabled) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Background Sync Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "WorkManager periodically checks feeds for updates in the background without draining battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                vm.triggerEpisodeSync()
                                Toast.makeText(context, "Checking subscriptions for new episodes now...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check Subscriptions Now")
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Notifications are currently disabled. Turn on the toggle above to receive background episode alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SubscriptionsScreen(
    vm: PodcastViewModel,
    onEpisodeSelected: (Podcast, Episode) -> Unit,
    onOpenNotificationSettings: () -> Unit = {}
) {
    val subscriptions by vm.subscriptions.collectAsState()
    var orderedSubscriptions by remember(subscriptions) { mutableStateOf(subscriptions) }
    var isDraggingGrid by remember { mutableStateOf(false) }
    var isDraggingList by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(subscriptions) {
        if (!isDraggingGrid && !isDraggingList) {
            orderedSubscriptions = subscriptions
        }
    }

    val displaySubscriptions = if (orderedSubscriptions.isEmpty() && subscriptions.isNotEmpty()) subscriptions else orderedSubscriptions

    val isGridView by vm.isGridView.collectAsState()
    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
    val playedEpisodes by vm.playedEpisodes.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val activeDownloads by vm.activeDownloads.collectAsState()
    val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (vm.subscriptionSelectedPodcast == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Subscriptions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { vm.updateGridView(!isGridView) }) {
                    Icon(if (isGridView) Icons.Default.Menu else Icons.Default.GridView, contentDescription = "Toggle View")
                }
            }
        }
        
        if (vm.subscriptionSelectedPodcast != null) {
            PodcastDetailView(
                podcast = vm.subscriptionSelectedPodcast!!,
                episodes = vm.subscriptionEpisodes,
                vm = vm,
                onBack = { vm.subscriptionSelectedPodcast = null },
                onEpisodeSelected = onEpisodeSelected
            )
        } else if (displaySubscriptions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No subscriptions yet. Go to Discover to add some!", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            if (isGridView) {
                val gridState = rememberLazyGridState()
                val reorderableLazyGridState = rememberReorderableLazyGridState(gridState) { from, to ->
                    orderedSubscriptions = orderedSubscriptions.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                }

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    state = gridState,
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = orderedSubscriptions,
                        key = { it.feedUrl }
                    ) { podcast ->
                        ReorderableItem(reorderableLazyGridState, key = podcast.feedUrl) { isDragging ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp)
                            val scale by animateFloatAsState(if (isDragging) 1.08f else 1f)

                            Surface(
                                onClick = { vm.loadSubscriptionPodcastEpisodes(podcast) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDragging) 0.95f else 0.6f),
                                shadowElevation = elevation,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .longPressDraggableHandle(
                                        interactionSource = interactionSource,
                                        onDragStarted = {
                                            isDraggingGrid = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            isDraggingGrid = false
                                            vm.updateSubscriptionOrder(orderedSubscriptions)
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = podcast.artworkUrl,
                                    contentDescription = podcast.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                    orderedSubscriptions = orderedSubscriptions.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = orderedSubscriptions,
                        key = { it.feedUrl }
                    ) { podcast ->
                        ReorderableItem(reorderableLazyListState, key = podcast.feedUrl) { isDragging ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp)
                            val scale by animateFloatAsState(if (isDragging) 1.04f else 1f)

                            Surface(
                                onClick = { vm.loadSubscriptionPodcastEpisodes(podcast) },
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDragging) 0.95f else 0.6f),
                                shadowElevation = elevation,
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .longPressDraggableHandle(
                                        interactionSource = interactionSource,
                                        onDragStarted = {
                                            isDraggingList = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            isDraggingList = false
                                            vm.updateSubscriptionOrder(orderedSubscriptions)
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = podcast.artworkUrl,
                                        contentDescription = podcast.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = podcast.author,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { vm.toggleSubscription(podcast) }) {
                                        Icon(Icons.Default.Star, contentDescription = "Unsubscribe", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Press and hold to rearrange",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
