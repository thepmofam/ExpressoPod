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
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.flow.combine
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
    @SerializedName("description") val description: String = "",
    @SerializedName("isCustomFeed") val isCustomFeed: Boolean = false
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
                .distinctBy { it.feedUrl }
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

object PlaybackSpeedManager {
    private const val PREFS_NAME = "expressopod_settings"
    const val KEY_SPEEDS_MAP = "subscription_playback_speeds"
    const val KEY_GLOBAL_SPEED = "global_playback_speed"
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 5.0f
    const val DEFAULT_SPEED = 1.0f

    private val mapStringFloatType: java.lang.reflect.Type =
        object : TypeToken<Map<String, Float?>>() {}.type

    fun clampSpeed(speed: Float): Float {
        if (speed.isNaN() || speed.isInfinite()) return DEFAULT_SPEED
        val snapped = kotlin.math.round(speed * 10f) / 10f
        return snapped.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    fun formatSpeed(speed: Float): String {
        val clamped = clampSpeed(speed)
        return String.format(java.util.Locale.US, "%.1fx", clamped)
    }

    fun normalizeKey(feedUrl: String?, podcastTitle: String? = null): String {
        val rawFeed = feedUrl?.trim().orEmpty()
        if (rawFeed.isNotBlank()) {
            val norm = FeedCredentialsManager.normalizeUrlKey(rawFeed)
            if (norm.isNotBlank()) return norm
            return rawFeed
        }
        val rawTitle = podcastTitle?.trim().orEmpty()
        if (rawTitle.isNotBlank()) {
            return "title:${rawTitle.lowercase(java.util.Locale.ROOT)}"
        }
        return "__default__"
    }

    fun parseSpeedsJson(json: String?): Map<String, Float> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val raw: Map<String, Float?>? = appGson.fromJson(json, mapStringFloatType)
            raw?.mapNotNull { (k, v) ->
                if (k.isNotBlank() && v != null) k to clampSpeed(v) else null
            }?.toMap() ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun loadSpeedsMap(context: Context): Map<String, Float> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            parseSpeedsJson(prefs.getString(KEY_SPEEDS_MAP, null))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveSpeedsMap(context: Context, speedsMap: Map<String, Float>) {
        try {
            val json = appGson.toJson(speedsMap)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SPEEDS_MAP, json)
                .apply()
        } catch (_: Exception) {}
    }

    fun loadGlobalSpeed(context: Context): Float {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            clampSpeed(prefs.getFloat(KEY_GLOBAL_SPEED, DEFAULT_SPEED))
        } catch (_: Exception) {
            DEFAULT_SPEED
        }
    }

    fun saveGlobalSpeed(context: Context, speed: Float) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_GLOBAL_SPEED, clampSpeed(speed))
                .apply()
        } catch (_: Exception) {}
    }

    fun getSpeedForPodcast(
        context: Context,
        podcast: Podcast?,
        speedsMap: Map<String, Float> = loadSpeedsMap(context),
        subscriptions: List<Podcast> = emptyList(),
        globalSpeed: Float = loadGlobalSpeed(context)
    ): Float {
        val fallbackSpeed = clampSpeed(globalSpeed)
        if (podcast == null) {
            return speedsMap["__default__"]?.let { clampSpeed(it) } ?: fallbackSpeed
        }
        if (podcast.feedUrl.isNotBlank()) {
            val normKey = normalizeKey(podcast.feedUrl, podcast.title)
            speedsMap[normKey]?.let { return clampSpeed(it) }
            speedsMap[podcast.feedUrl.trim()]?.let { return clampSpeed(it) }
        }
        if (podcast.title.isNotBlank()) {
            val matchedSub = subscriptions.find {
                it.title.trim().equals(podcast.title.trim(), ignoreCase = true) && it.feedUrl.isNotBlank()
            } ?: SubscriptionStorageManager.loadSubscriptionsFromBackup(context).find {
                it.title.trim().equals(podcast.title.trim(), ignoreCase = true) && it.feedUrl.isNotBlank()
            }
            if (matchedSub != null) {
                val subNormKey = normalizeKey(matchedSub.feedUrl, matchedSub.title)
                speedsMap[subNormKey]?.let { return clampSpeed(it) }
                speedsMap[matchedSub.feedUrl.trim()]?.let { return clampSpeed(it) }
            }
            val titleKey = normalizeKey(null, podcast.title)
            speedsMap[titleKey]?.let { return clampSpeed(it) }
        }
        return fallbackSpeed
    }

    fun getSpeedForSavedPodcast(context: Context): Float {
        val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        val podJson = statePrefs.getString("last_podcast", null)
        val pod = if (podJson != null) {
            try { appGson.fromJson(podJson, Podcast::class.java) } catch (_: Exception) { null }
        } else null
        return getSpeedForPodcast(context, pod)
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
@Keep
data class ItunesResponse(
    @SerializedName("results") val results: List<ItunesItem?>? = emptyList()
)

@Keep
data class ItunesItem(
    @SerializedName("collectionId") val collectionId: Long? = null,
    @SerializedName("collectionName") val collectionName: String? = null,
    @SerializedName("artistName") val artistName: String? = null,
    @SerializedName("artworkUrl600") val artworkUrl600: String? = null,
    @SerializedName("feedUrl") val feedUrl: String? = null
)

interface ItunesApi {
    @GET("search?media=podcast&entity=podcast")
    suspend fun search(@Query("term") term: String): ItunesResponse

    @GET("search?media=podcast&entity=podcast")
    suspend fun searchWithLimit(@Query("term") term: String, @Query("limit") limit: Int = 24): ItunesResponse
}

// Removed Podcast Index

object NetworkModule {
    @Volatile
    private var _okHttp: OkHttpClient? = null

    private val authInterceptor = Interceptor { chain ->
        val originalReq = chain.request()
        val urlStr = originalReq.url.toString()
        if (originalReq.header("Authorization") != null || FeedCredentialsManager.hasConflictingQueryAuth(urlStr)) {
            return@Interceptor chain.proceed(originalReq)
        }

        val creds = FeedCredentialsManager.getCredentialsForUrl(urlStr)
        if (creds != null && FeedCredentialsManager.shouldAttachAuthDirectly(urlStr)) {
            val authedReq = originalReq.newBuilder()
                .header("Authorization", creds.toBasicAuthHeader())
                .build()
            return@Interceptor chain.proceed(authedReq)
        }

        val response = chain.proceed(originalReq)
        if ((response.code == 401 || response.code == 403) && creds != null && !FeedCredentialsManager.hasConflictingQueryAuth(urlStr)) {
            response.close()
            val retryReq = originalReq.newBuilder()
                .header("Authorization", creds.toBasicAuthHeader())
                .build()
            return@Interceptor chain.proceed(retryReq)
        }
        response
    }

    val okHttp: OkHttpClient
        get() = _okHttp ?: synchronized(this) {
            _okHttp ?: OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build().also { _okHttp = it }
        }

    val downloadHttpClient: OkHttpClient by lazy {
        okHttp.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun initCache(cacheDir: java.io.File) {
        try {
            synchronized(this) {
                if (_okHttp?.cache == null) {
                    val httpCache = okhttp3.Cache(java.io.File(cacheDir, "http_cache"), 25L * 1024 * 1024)
                    _okHttp = OkHttpClient.Builder()
                        .cache(httpCache)
                        .addInterceptor(authInterceptor)
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                }
            }
        } catch (_: Exception) {}
    }

    fun executeAuthenticatedCall(
        url: String,
        feedUrl: String? = null,
        explicitCreds: FeedCredentials? = null,
        acceptHeader: String? = null,
        client: OkHttpClient = okHttp
    ): okhttp3.Response {
        val (cleanUrl, embeddedCreds) = FeedCredentialsManager.extractEmbeddedCredentials(url)
        val creds = explicitCreds?.takeIf { it.isValid }
            ?: embeddedCreds?.takeIf { it.isValid }
            ?: FeedCredentialsManager.getCredentialsForUrl(cleanUrl, feedUrl)

        val attachInitialAuth = creds != null && (
            explicitCreds != null ||
                embeddedCreds != null ||
                FeedCredentialsManager.shouldAttachAuthDirectly(cleanUrl, feedUrl)
            ) && !FeedCredentialsManager.hasConflictingQueryAuth(cleanUrl)

        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", "ExpressoPod/1.0")
        if (!acceptHeader.isNullOrBlank()) {
            reqBuilder.header("Accept", acceptHeader)
        }
        if (attachInitialAuth && creds != null) {
            reqBuilder.header("Authorization", creds.toBasicAuthHeader())
        }

        val response = client.newCall(reqBuilder.build()).execute()
        if ((response.code == 401 || response.code == 403) && creds != null && !attachInitialAuth && !FeedCredentialsManager.hasConflictingQueryAuth(cleanUrl)) {
            response.close()
            val retryReq = reqBuilder.header("Authorization", creds.toBasicAuthHeader()).build()
            return client.newCall(retryReq).execute()
        }
        return response
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

    sealed class FeedFetchResult {
        data class Success(val feedUrl: String, val parsedFeed: ParsedFeed) : FeedFetchResult()
        data class AuthRequired(val httpCode: Int, val message: String) : FeedFetchResult()
        data class Error(val message: String) : FeedFetchResult()
    }

    private val feedCache = java.util.concurrent.ConcurrentHashMap<String, ParsedFeed>()

    fun getCachedFeed(feedUrl: String): ParsedFeed? = feedCache[FeedCredentialsManager.normalizeUrlKey(feedUrl)] ?: feedCache[feedUrl]

    suspend fun fetchPodcastFeedResult(
        rawFeedUrl: String,
        username: String? = null,
        password: String? = null
    ): FeedFetchResult = withContext(Dispatchers.IO) {
        val normalizedInput = SecurityUtils.normalizeFeedUrl(rawFeedUrl)
            ?: return@withContext FeedFetchResult.Error("Please enter a valid RSS feed URL (http:// or https://).")
        val (cleanFeedUrl, embeddedCreds) = FeedCredentialsManager.extractEmbeddedCredentials(normalizedInput)
        val explicitCreds = if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
            FeedCredentials(username?.trim() ?: "", password ?: "")
        } else {
            embeddedCreds
        }

        val episodes = mutableListOf<Episode>()
        val seenAudioUrls = HashSet<String>()
        var channelTitle = ""
        var channelAuthor = ""
        var channelImage = ""
        var channelDescription = ""
        var channelLink = ""
        var sawRssOrFeedRoot = false

        try {
            executeAuthenticatedCall(
                url = cleanFeedUrl,
                feedUrl = cleanFeedUrl,
                explicitCreds = explicitCreds,
                acceptHeader = "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8"
            ).use { response ->
                if (response.code == 401 || response.code == 403) {
                    val hasProvidedCreds = (explicitCreds != null && explicitCreds.isValid) || FeedCredentialsManager.hasCredentials(cleanFeedUrl)
                    val msg = if (hasProvidedCreds) {
                        "Authentication failed (HTTP ${response.code}). Please verify your username and password."
                    } else {
                        "This private feed requires a username and password (HTTP ${response.code})."
                    }
                    return@withContext FeedFetchResult.AuthRequired(response.code, msg)
                }

                if (!response.isSuccessful) {
                    return@withContext FeedFetchResult.Error("Server returned HTTP ${response.code} (${response.message.ifBlank { "Error" }}).")
                }

                val stream = response.body?.byteStream()
                    ?: return@withContext FeedFetchResult.Error("Received an empty response from the server.")

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
                            if (tagName.equals("rss", true) || tagName.equals("feed", true) || tagName.equals("channel", true)) {
                                sawRssOrFeedRoot = true
                            }
                            if (tagName.equals("item", true) || tagName.equals("entry", true)) {
                                sawRssOrFeedRoot = true
                                insideItem = true
                                title = ""; audioUrl = ""; pubDate = ""; duration = ""; description = ""; chaptersUrl = ""
                            } else if (tagName.equals("image", true)) {
                                insideImage = true
                            } else if (insideItem) {
                                when {
                                    tagName.equals("title", true) -> title = readTextSafe()
                                    tagName.equals("pubDate", true) || tagName.equals("published", true) || tagName.equals("updated", true) -> {
                                        if (pubDate.isBlank()) pubDate = readTextSafe().take(16)
                                    }
                                    (tagName.equals("itunes:duration", true) || tagName.equals("duration", true)) -> duration = formatDurationText(readTextSafe())
                                    tagName.equals("enclosure", true) -> audioUrl = parser.getAttributeValue(null, "url") ?: audioUrl
                                    tagName.equals("media:content", true) -> {
                                        if (audioUrl.isBlank()) {
                                            val medium = parser.getAttributeValue(null, "medium") ?: ""
                                            val type = parser.getAttributeValue(null, "type") ?: ""
                                            val urlAttr = parser.getAttributeValue(null, "url") ?: ""
                                            if (urlAttr.isNotBlank() && (medium.equals("audio", true) || type.startsWith("audio/", true) || urlAttr.contains(".mp3", true) || urlAttr.contains(".m4a", true))) {
                                                audioUrl = urlAttr
                                            }
                                        }
                                    }
                                    tagName.equals("link", true) -> {
                                        val rel = parser.getAttributeValue(null, "rel") ?: ""
                                        val type = parser.getAttributeValue(null, "type") ?: ""
                                        val href = parser.getAttributeValue(null, "href") ?: ""
                                        if (audioUrl.isBlank() && href.isNotBlank() && (rel.equals("enclosure", true) || type.startsWith("audio/", true))) {
                                            audioUrl = href
                                        }
                                    }
                                    (tagName.equals("podcast:chapters", true) || tagName.equals("chapters", true)) -> {
                                        chaptersUrl = parser.getAttributeValue(null, "url") ?: ""
                                    }
                                    tagName.equals("content:encoded", true) || tagName.equals("content", true) -> {
                                        val content = readTextSafe()
                                        if (content.isNotBlank()) description = content
                                    }
                                    tagName.equals("description", true) || tagName.equals("summary", true) -> {
                                        val desc = readTextSafe()
                                        if (description.isBlank() && desc.isNotBlank()) description = desc
                                    }
                                    tagName.equals("itunes:summary", true) -> {
                                        val sum = readTextSafe()
                                        if (description.isBlank() && sum.isNotBlank()) description = sum
                                    }
                                }
                            } else {
                                // Channel level tags (Podcasting 1.0 & 2.0 & Atom)
                                when {
                                    tagName.equals("title", true) && !insideImage -> channelTitle = readTextSafe()
                                    tagName.equals("itunes:author", true) || tagName.equals("author", true) -> {
                                        val a = readTextSafe()
                                        if (a.isNotBlank()) channelAuthor = a
                                    }
                                    tagName.equals("podcast:person", true) -> {
                                        val p = readTextSafe()
                                        if (channelAuthor.isBlank() && p.isNotBlank()) channelAuthor = p
                                    }
                                    tagName.equals("itunes:image", true) -> channelImage = parser.getAttributeValue(null, "href") ?: channelImage
                                    tagName.equals("logo", true) || tagName.equals("icon", true) -> {
                                        val img = readTextSafe()
                                        if (channelImage.isBlank() && img.isNotBlank()) channelImage = img
                                    }
                                    tagName.equals("url", true) && insideImage -> channelImage = readTextSafe()
                                    (tagName.equals("description", true) || tagName.equals("itunes:summary", true) || tagName.equals("subtitle", true)) && !insideImage -> {
                                        val d = readTextSafe()
                                        if (channelDescription.isBlank() && d.isNotBlank()) channelDescription = d
                                    }
                                    tagName.equals("link", true) && !insideImage -> {
                                        val href = parser.getAttributeValue(null, "href")
                                        val rel = parser.getAttributeValue(null, "rel") ?: ""
                                        if (!href.isNullOrBlank() && !rel.equals("self", true)) {
                                            if (channelLink.isBlank()) channelLink = href.trim()
                                        } else {
                                            val l = readTextSafe()
                                            if (channelLink.isBlank() && l.isNotBlank()) channelLink = l
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if ((tagName.equals("item", true) || tagName.equals("entry", true)) && insideItem) {
                                val cleanAudioUrl = audioUrl.trim()
                                if (cleanAudioUrl.isNotBlank() && seenAudioUrls.add(cleanAudioUrl)) {
                                    episodes.add(Episode(title, pubDate, cleanAudioUrl, duration, description, chaptersUrl.trim()))
                                }
                                insideItem = false
                            } else if (tagName.equals("image", true)) {
                                insideImage = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            e.printStackTrace()
            return@withContext FeedFetchResult.Error("The URL did not return a valid RSS/XML podcast feed.")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext FeedFetchResult.Error("Could not connect to feed: ${e.localizedMessage ?: "Network error"}")
        }

        if (!sawRssOrFeedRoot && channelTitle.isBlank() && episodes.isEmpty()) {
            return@withContext FeedFetchResult.Error("No valid podcast RSS feed found at this URL.")
        }

        val finalTitle = channelTitle.ifBlank { "Custom Podcast" }
        val finalAuthor = channelAuthor.ifBlank { "Unknown" }
        val result = ParsedFeed(finalTitle, finalAuthor, channelImage, episodes, channelDescription, channelLink)
        feedCache[cleanFeedUrl] = result
        feedCache[rawFeedUrl] = result
        if (episodes.isNotEmpty()) {
            FeedCredentialsManager.registerEpisodesForFeed(null, cleanFeedUrl, episodes.map { it.audioUrl })
            GPodderSyncManager.registerFeedEpisodes(null, cleanFeedUrl, episodes)
        }
        FeedFetchResult.Success(cleanFeedUrl, result)
    }

    suspend fun fetchPodcastFeed(feedUrl: String): ParsedFeed? = withContext(Dispatchers.IO) {
        when (val res = fetchPodcastFeedResult(feedUrl)) {
            is FeedFetchResult.Success -> res.parsedFeed
            else -> getCachedFeed(feedUrl)
        }
    }
}

// ============================================================================
// 2. VIEWMODEL (State, Presets, & MediaController)
// ============================================================================

class PodcastViewModel(private val app: Application) : AndroidViewModel(app) {
    var searchQuery by mutableStateOf("")
    var podcasts by mutableStateOf<List<Podcast>>(emptyList())
    var suggestedPodcasts by mutableStateOf<List<Podcast>>(
        listOf(
            Podcast(
                title = "99% Invisible",
                author = "Roman Mars",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/68/74/3f/68743fa6-c534-e01f-d03e-bb157eb15f5c/mza_11214174082973215888.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.simplecast.com/BqbsxVfO"
            ),
            Podcast(
                title = "Darknet Diaries",
                author = "Jack Rhysider",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts112/v4/79/0c/90/790c9059-1fb7-26dc-2114-56339006e1bb/mza_10219822976063918899.jpg/600x600bb.jpg",
                feedUrl = "https://podcast.darknetdiaries.com"
            ),
            Podcast(
                title = "Hardcore History",
                author = "Dan Carlin",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts126/v4/0b/68/54/0b685427-9954-2392-f2eb-5c8ff64bf749/mza_7991378772933267736.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.feedburner.com/dancarlin/history?format=xml"
            ),
            Podcast(
                title = "Song Exploder",
                author = "Hrishikesh Hirway",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts122/v4/c9/9c/b6/c99cb638-21a2-24a5-c006-783847746368/mza_11367761233389904372.jpg/600x600bb.jpg",
                feedUrl = "https://feed.songexploder.net/SongExploder"
            ),
            Podcast(
                title = "The Vergecast",
                author = "The Verge",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/0f/1c/71/0f1c71bc-6a23-623c-307f-2d6b66f9814d/mza_13479369191529237073.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.megaphone.fm/vergecast"
            ),
            Podcast(
                title = "Freakonomics Radio",
                author = "Freakonomics Radio + Stitcher",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/a7/7f/98/a77f9883-0b2f-2cf4-0a5f-ef3053df0e75/mza_403078916705116220.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.simplecast.com/Y8lFbOT4"
            ),
            Podcast(
                title = "Lex Fridman Podcast",
                author = "Lex Fridman",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts115/v4/5b/57/e0/5b57e08f-2e57-08b6-9a3f-8b07b09d2c79/mza_15238926846885143536.jpg/600x600bb.jpg",
                feedUrl = "https://lexfridman.com/feed/podcast/"
            ),
            Podcast(
                title = "Planet Money",
                author = "NPR",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/54/2c/08/542c082d-248c-9495-c192-0704c0056f53/mza_17148528408398569713.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.npr.org/510289/podcast.xml"
            ),
            Podcast(
                title = "Ologies with Alie Ward",
                author = "Alie Ward",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/1c/4f/41/1c4f419e-8604-8b20-5853-c5d0b1a4f78b/mza_13788233099116750292.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.simplecast.com/FO6kxYGj"
            ),
            Podcast(
                title = "Acquired",
                author = "Ben Gilbert and David Rosenthal",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts116/v4/12/14/c6/1214c612-8421-c21d-52fa-54396331518b/mza_6892356137028493028.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.transistor.fm/acquired"
            ),
            Podcast(
                title = "Stuff You Should Know",
                author = "iHeartPodcasts",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts211/v4/85/d1/36/85d136b1-58b7-bd23-0356-3ee453ba71a3/mza_12440562626968273517.jpg/600x600bb.jpg",
                feedUrl = "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/A91018A4-EA4F-4130-BF55-AE270180C327/44710ECC-10BB-48D1-93C7-AE270180C33E/podcast.rss"
            ),
            Podcast(
                title = "Radiolab",
                author = "WNYC Studios",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/c2/21/d9/c221d95b-2612-63eb-9843-90e53d1007dc/mza_4010838340033827392.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.simplecast.com/EmVW7VGp"
            ),
            Podcast(
                title = "Hard Fork",
                author = "The New York Times",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts112/v4/5f/44/73/5f4473be-ff22-c628-4372-64ef55b52809/mza_11246537178436617638.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.simplecast.com/l2i9YnTd"
            ),
            Podcast(
                title = "Waveform: The MKBHD Podcast",
                author = "Vox Media Podcast Network",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/d1/36/c4/d136c493-2c91-bc0c-563e-3eb4ff86d25f/mza_13407755449464785560.jpg/600x600bb.jpg",
                feedUrl = "https://feeds.megaphone.fm/STU4418364045"
            ),
            Podcast(
                title = "Accidental Tech Podcast",
                author = "Marco Arment, Casey Liss, John Siracusa",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts113/v4/f2/42/67/f2426705-a258-0f50-f9e0-9658bd4e4431/mza_4991841458504992708.jpg/600x600bb.jpg",
                feedUrl = "https://atp.fm/rss"
            ),
            Podcast(
                title = "Syntax - Tasty Web Development Treats",
                author = "Wes Bos & Scott Tolinski",
                artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Podcasts126/v4/c9/e8/13/c9e8138e-f66f-ef13-7d5b-8fc41b9e6c8f/mza_16565611432528551302.jpg/600x600bb.jpg",
                feedUrl = "https://feed.syntax.fm/rss"
            )
        )
    )
    private val generalSuggestionTopics = listOf(
        "top podcasts",
        "science podcasts",
        "history podcasts",
        "true crime podcasts",
        "comedy podcasts",
        "business & economics podcasts",
        "storytelling podcasts",
        "design & culture podcasts",
        "news & politics podcasts",
        "gaming & pop culture podcasts",
        "health & psychology podcasts",
        "music & film podcasts",
        "mystery & investigative podcasts",
        "space & future podcasts"
    )
    private val techSuggestionTopics = listOf(
        "technology podcasts",
        "tech news & gadgets",
        "software engineering & programming",
        "artificial intelligence & machine learning",
        "cybersecurity & hacking podcasts",
        "linux & open source podcasts",
        "android & mobile tech podcasts",
        "startups & silicon valley podcasts"
    )
    private var suggestionTopicIndex = -1
    private var techTopicIndex = -1
    var hasLoadedOnlineSuggestions by mutableStateOf(false)
    var isLoadingSuggestions by mutableStateOf(false)
    var nowPlayingTabIndex by mutableIntStateOf(0) // 0 = Player, 1 = Chapters, 2 = Notes

    // Subscriptions screen selected podcast & episodes
    var subscriptionSelectedPodcast by mutableStateOf<Podcast?>(null)
    var subscriptionEpisodes by mutableStateOf<List<Episode>>(emptyList())

    // Discover screen selected podcast & episodes
    var discoverSelectedPodcast by mutableStateOf<Podcast?>(null)
    var discoverEpisodes by mutableStateOf<List<Episode>>(emptyList())

    // Settings screen selected category
    var settingsSelectedCategory by mutableStateOf<SettingsCategory?>(null)

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

    // Local Audio Files (Imported from device)
    private val localAudioManager = LocalAudioManager.getInstance(app)
    val localAudioFiles = localAudioManager.localFiles
    val localSubscriptionTitle = localAudioManager.subscriptionTitle
    val isImportingLocalAudio = localAudioManager.isImporting

    private fun mergeLocalAudioIntoSubscriptions(
        base: List<Podcast>,
        localFiles: List<LocalAudioFile>,
        localTitle: String
    ): List<Podcast> {
        if (localFiles.isEmpty()) {
            return base.filterNot { LocalAudioManager.isLocalPodcast(it) }
        }
        val localPodcast = localAudioManager.buildLocalPodcast(localTitle)
        val idx = base.indexOfFirst { LocalAudioManager.isLocalPodcast(it) }
        return if (idx >= 0) {
            base.toMutableList().apply { this[idx] = localPodcast }
        } else {
            listOf(localPodcast) + base
        }
    }

    private fun loadInitialSubscriptions(): List<Podcast> {
        val base = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
        return mergeLocalAudioIntoSubscriptions(
            base = base,
            localFiles = localAudioManager.localFiles.value,
            localTitle = localAudioManager.subscriptionTitle.value
        )
    }

    private val rawSubscriptionsFlow = app.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[stringPreferencesKey("subscriptions")] }
        .distinctUntilChanged()
        .map { json ->
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

    // Subscriptions (automatically includes Local Audio Files collection when files are added)
    val subscriptions = combine(
        rawSubscriptionsFlow,
        localAudioFiles,
        localSubscriptionTitle
    ) { baseSubs, localFiles, localTitle ->
        mergeLocalAudioIntoSubscriptions(baseSubs, localFiles, localTitle)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, loadInitialSubscriptions())

    var currentThemeConfig by mutableIntStateOf(
        app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE).getInt("theme_config", 0)
    )
        private set

    private val _themeConfig = MutableStateFlow(currentThemeConfig)
    val themeConfig = _themeConfig.asStateFlow()

    val playbackPositions = app.dataStore.data
        .map { prefs -> prefs[stringPreferencesKey("playback_positions")] }
        .distinctUntilChanged()
        .map { json ->
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

    val showMiniPlayer = app.dataStore.data
        .map { it[booleanPreferencesKey("show_mini_player")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val volumeBoostEnabled = app.dataStore.data
        .map { it[booleanPreferencesKey("volume_boost_enabled")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var globalPlaybackSpeed by mutableFloatStateOf(PlaybackSpeedManager.loadGlobalSpeed(app))
        private set

    var subscriptionPlaybackSpeeds by mutableStateOf(PlaybackSpeedManager.loadSpeedsMap(app))
        private set

    val currentPlaybackSpeed: Float
        get() = getPlaybackSpeedForPodcast(currentPodcast)

    fun getPlaybackSpeedForPodcast(podcast: Podcast?): Float {
        return PlaybackSpeedManager.getSpeedForPodcast(
            context = app,
            podcast = podcast,
            speedsMap = subscriptionPlaybackSpeeds,
            subscriptions = subscriptions.value,
            globalSpeed = globalPlaybackSpeed
        )
    }

    fun updateGlobalPlaybackSpeed(speed: Float) {
        val clamped = PlaybackSpeedManager.clampSpeed(speed)
        globalPlaybackSpeed = clamped
        PlaybackSpeedManager.saveGlobalSpeed(app, clamped)
        val effectiveSpeed = getPlaybackSpeedForPodcast(currentPodcast)
        controller?.setPlaybackSpeed(effectiveSpeed)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    prefs[androidx.datastore.preferences.core.floatPreferencesKey(PlaybackSpeedManager.KEY_GLOBAL_SPEED)] = clamped
                }
            } catch (_: Exception) {}
        }
    }

    fun setPlaybackSpeedForPodcast(podcast: Podcast?, speed: Float) {
        val clamped = PlaybackSpeedManager.clampSpeed(speed)
        val updated = subscriptionPlaybackSpeeds.toMutableMap()
        if (podcast == null) {
            updated["__default__"] = clamped
        } else {
            val primaryKey = PlaybackSpeedManager.normalizeKey(podcast.feedUrl, podcast.title)
            updated[primaryKey] = clamped
            if (podcast.feedUrl.isNotBlank()) {
                updated[podcast.feedUrl.trim()] = clamped
            }
            if (podcast.title.isNotBlank()) {
                updated[PlaybackSpeedManager.normalizeKey(null, podcast.title)] = clamped
                val matchedSub = subscriptions.value.find {
                    it.title.trim().equals(podcast.title.trim(), ignoreCase = true) && it.feedUrl.isNotBlank()
                }
                if (matchedSub != null) {
                    updated[PlaybackSpeedManager.normalizeKey(matchedSub.feedUrl, matchedSub.title)] = clamped
                    updated[matchedSub.feedUrl.trim()] = clamped
                }
            }
        }
        subscriptionPlaybackSpeeds = updated
        PlaybackSpeedManager.saveSpeedsMap(app, updated)
        if (podcast == null ||
            podcast.feedUrl == currentPodcast?.feedUrl ||
            (podcast.title.isNotBlank() && podcast.title.equals(currentPodcast?.title, ignoreCase = true))
        ) {
            controller?.setPlaybackSpeed(clamped)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = appGson.toJson(updated)
                app.dataStore.edit { prefs ->
                    prefs[stringPreferencesKey(PlaybackSpeedManager.KEY_SPEEDS_MAP)] = json
                }
            } catch (_: Exception) {}
        }
    }

    fun setPlaybackSpeedForCurrentPodcast(speed: Float) {
        setPlaybackSpeedForPodcast(currentPodcast, speed)
    }

    val cachePlayingEpisode = app.dataStore.data
        .map { it[booleanPreferencesKey("cache_playing_episode")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showUnplayedBadge = app.dataStore.data
        .map { it[booleanPreferencesKey("show_unplayed_badge")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showDownloadedBadge = app.dataStore.data
        .map { it[booleanPreferencesKey("show_downloaded_badge")] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Downloads for Offline Listening
    private val downloadManager = PodcastDownloadManager.getInstance(app)
    val downloadedEpisodes = downloadManager.downloadedEpisodes
    val activeDownloads = downloadManager.activeDownloads

    // Subscription EpisodeUrls Map (normalizedFeedUrl -> List<String> of episode audioUrls) for subscription badges
    private val _subscriptionEpisodesMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val subscriptionEpisodesMap = _subscriptionEpisodesMap.asStateFlow()
    private val refreshingSubscriptionFeeds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun loadSubscriptionEpisodesCache() {
        try {
            val sp = app.getSharedPreferences("expressopod_sub_episodes_cache", Context.MODE_PRIVATE)
            val json = sp.getString("feed_episodes_map", null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val loaded: Map<String, List<String>>? = appGson.fromJson(json, type)
                if (loaded != null) {
                    _subscriptionEpisodesMap.value = loaded
                }
            }
        } catch (_: Exception) {}
    }

    fun recordFeedEpisodesForBadges(feedUrl: String, episodes: List<Episode>) {
        val normKey = FeedCredentialsManager.normalizeUrlKey(feedUrl)
        if (normKey.isBlank()) return
        val urls = episodes.mapNotNull { it.audioUrl.trim().takeIf { u -> u.isNotBlank() } }.distinct()
        val currentMap = _subscriptionEpisodesMap.value
        if (currentMap[normKey] == urls) return
        val updated = currentMap.toMutableMap().apply {
            put(normKey, urls)
            if (feedUrl.trim() != normKey) {
                put(feedUrl.trim(), urls)
            }
        }
        _subscriptionEpisodesMap.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sp = app.getSharedPreferences("expressopod_sub_episodes_cache", Context.MODE_PRIVATE)
                sp.edit().putString("feed_episodes_map", appGson.toJson(updated)).apply()
            } catch (_: Exception) {}
        }
    }

    fun refreshSubscriptionFeedsInBackground(subs: List<Podcast>) {
        if (subs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            subs.forEach { podcast ->
                if (LocalAudioManager.isLocalPodcast(podcast)) {
                    recordFeedEpisodesForBadges(podcast.feedUrl, localAudioManager.getEpisodes())
                    return@forEach
                }
                val normKey = FeedCredentialsManager.normalizeUrlKey(podcast.feedUrl)
                if (normKey.isNotBlank()) {
                    val cached = NetworkModule.getCachedFeed(podcast.feedUrl)
                    if (cached != null && cached.episodes.isNotEmpty()) {
                        recordFeedEpisodesForBadges(podcast.feedUrl, cached.episodes)
                    } else if (refreshingSubscriptionFeeds.add(normKey)) {
                        launch(Dispatchers.IO) {
                            try {
                                val parsed = NetworkModule.fetchPodcastFeed(podcast.feedUrl)
                                if (parsed != null && parsed.episodes.isNotEmpty()) {
                                    recordFeedEpisodesForBadges(podcast.feedUrl, parsed.episodes)
                                }
                            } catch (_: Exception) {
                            } finally {
                                refreshingSubscriptionFeeds.remove(normKey)
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom / Private RSS Feed Credentials State
    val authenticatedFeedUrls = FeedCredentialsManager.authenticatedFeedUrls
    var showCustomFeedDialog by mutableStateOf(false)
    var customFeedDialogInitialUrl by mutableStateOf("")
    var customFeedDialogEditPodcast by mutableStateOf<Podcast?>(null)
    var customFeedDialogForceAuthExpanded by mutableStateOf(false)

    fun openAddCustomFeedDialog(initialUrl: String = "", forceAuthExpanded: Boolean = false) {
        customFeedDialogEditPodcast = null
        customFeedDialogInitialUrl = initialUrl.trim()
        customFeedDialogForceAuthExpanded = forceAuthExpanded
        showCustomFeedDialog = true
    }

    fun openEditFeedCredentialsDialog(podcast: Podcast) {
        customFeedDialogEditPodcast = podcast
        customFeedDialogInitialUrl = podcast.feedUrl
        customFeedDialogForceAuthExpanded = true
        showCustomFeedDialog = true
    }

    fun dismissCustomFeedDialog() {
        showCustomFeedDialog = false
        customFeedDialogEditPodcast = null
        customFeedDialogInitialUrl = ""
        customFeedDialogForceAuthExpanded = false
    }

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
    val gpodderSyncState = GPodderSyncManager.syncState
    var positionsVersion by mutableIntStateOf(0)

    init {
        FeedCredentialsManager.init(app)
        GPodderSyncManager.init(app)
        loadSubscriptionEpisodesCache()
        viewModelScope.launch {
            subscriptions.collect { subs ->
                refreshSubscriptionFeedsInBackground(subs)
            }
        }
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
            val prefs = app.dataStore.data.firstOrNull() ?: return@launch
            val played = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")] ?: emptySet()
            val archived = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")] ?: emptySet()

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

            // If the restored episode is already finished/played or archived, clear it so mini player doesn't linger
            val ep = currentEpisode
            if (ep != null && !isPlaying && (played.contains(ep.audioUrl) || archived.contains(ep.audioUrl))) {
                clearCurrentPlayback()
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
            .filterNot { it.episode.audioUrl == currentEpisode?.audioUrl }
        if (initialQueue.isNotEmpty()) {
            upNextQueue = initialQueue
        }
        viewModelScope.launch {
            app.dataStore.data.collect { prefs ->
                val qJson = prefs[stringPreferencesKey("up_next_queue")]
                if (!qJson.isNullOrBlank()) {
                    val parsed = QueueStorageManager.parseQueueJson(qJson)
                        .filterNot { it.episode.audioUrl == currentEpisode?.audioUrl }
                    if (upNextQueue.isEmpty() && parsed.isNotEmpty()) {
                        upNextQueue = parsed
                    }
                }
                val speedsJson = prefs[stringPreferencesKey(PlaybackSpeedManager.KEY_SPEEDS_MAP)]
                if (!speedsJson.isNullOrBlank() && subscriptionPlaybackSpeeds.isEmpty()) {
                    val parsedSpeeds = PlaybackSpeedManager.parseSpeedsJson(speedsJson)
                    if (parsedSpeeds.isNotEmpty()) {
                        subscriptionPlaybackSpeeds = parsedSpeeds
                        PlaybackSpeedManager.saveSpeedsMap(app, parsedSpeeds)
                        controller?.setPlaybackSpeed(getPlaybackSpeedForPodcast(currentPodcast))
                    }
                }
                val dsGlobalSpeed = prefs[androidx.datastore.preferences.core.floatPreferencesKey(PlaybackSpeedManager.KEY_GLOBAL_SPEED)]
                val spSettingsForSpeed = app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
                if (dsGlobalSpeed != null && !spSettingsForSpeed.contains(PlaybackSpeedManager.KEY_GLOBAL_SPEED)) {
                    val clampedGlobal = PlaybackSpeedManager.clampSpeed(dsGlobalSpeed)
                    globalPlaybackSpeed = clampedGlobal
                    PlaybackSpeedManager.saveGlobalSpeed(app, clampedGlobal)
                    controller?.setPlaybackSpeed(getPlaybackSpeedForPodcast(currentPodcast))
                }
            }
        }

        // Synchronously load saved theme preference from SharedPreferences
        val spSettings = app.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
        if (spSettings.contains("theme_config")) {
            val savedThemePref = spSettings.getInt("theme_config", 0)
            if (currentThemeConfig != savedThemePref) {
                currentThemeConfig = savedThemePref
                _themeConfig.value = savedThemePref
            }
        } else {
            // One-time migration from DataStore if present
            viewModelScope.launch {
                try {
                    val dsTheme = app.dataStore.data.firstOrNull()?.get(intPreferencesKey("theme_config"))
                    if (dsTheme != null && !spSettings.contains("theme_config")) {
                        currentThemeConfig = dsTheme
                        _themeConfig.value = dsTheme
                        spSettings.edit().putInt("theme_config", dsTheme).apply()
                    }
                } catch (_: Exception) {}
            }
        }

        initMediaController()
        loadSuggestedPodcasts()
        search()
    }

    fun prepareControllerWithCurrentEpisode() {
        val ctrl = controller ?: return
        val pod = currentPodcast ?: return
        val ep = currentEpisode ?: return

        ctrl.setPlaybackSpeed(getPlaybackSpeedForPodcast(pod))

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
            ?: localAudioManager.getLocalUri(ep.audioUrl)
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

    fun clearCurrentPlayback() {
        val prevEpUrl = currentEpisode?.audioUrl
        currentEpisode = null
        currentPodcast = null
        isPlaying = false
        currentPosition = 0L
        duration = 1L
        currentChapters = emptyList()

        controller?.let {
            it.stop()
            it.clearMediaItems()
        }

        val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        statePrefs.edit()
            .remove("last_podcast")
            .remove("last_episode")
            .putLong("last_position", 0L)
            .apply()

        if (prevEpUrl != null) {
            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            posPrefs.edit().remove(prevEpUrl).apply()
        }

        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("last_podcast"))
                prefs.remove(stringPreferencesKey("last_episode"))
            }
        }
        syncWidget()
    }

    fun stopOrSkipCurrentEpisode() {
        advanceOrClearCurrentEpisode()
    }

    private fun advanceOrClearCurrentEpisode() {
        if (upNextQueue.isNotEmpty()) {
            val wasPlaying = isPlaying
            val nextItem = upNextQueue.first()
            upNextQueue = upNextQueue.drop(1)
            saveUpNextQueue()
            playEpisode(nextItem.podcast, nextItem.episode, autoPlay = wasPlaying, fromQueue = isPlayingQueue, keepPreviousInQueue = false)
        } else {
            clearCurrentPlayback()
        }
    }

    fun syncPlaybackState() {
        val ctrl = controller
        if (ctrl != null) {
            if (ctrl.playbackState == Player.STATE_ENDED && upNextQueue.isEmpty()) {
                clearCurrentPlayback()
                return
            }
            if (ctrl.currentPosition > 0L) {
                currentPosition = ctrl.currentPosition
            }
            if (ctrl.duration > 0L) {
                duration = ctrl.duration
            }
            isPlaying = ctrl.isPlaying
            val targetSpeed = getPlaybackSpeedForPodcast(currentPodcast)
            if (kotlin.math.abs(ctrl.playbackParameters.speed - targetSpeed) >= 0.02f) {
                ctrl.setPlaybackSpeed(targetSpeed)
            }
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

    fun saveCurrentPlaybackPosition(shouldQueueSyncAction: Boolean = false) {
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
            positionsVersion++
            return
        }
        val pos = if (ctrl != null && ctrl.currentPosition > 0L) ctrl.currentPosition else currentPosition
        val totalDur = if (ctrl != null && ctrl.duration > 0L) ctrl.duration else if (duration > 1L) duration else parseDurationMs(ep.durationText)
        if (pos > 0L) {
            val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            posPrefs.edit().putLong(ep.audioUrl, pos).apply()
            GPodderSyncManager.onPlaybackPositionUpdated(
                context = app,
                podcastUrl = pod?.feedUrl,
                episodeUrl = ep.audioUrl,
                positionMs = pos,
                durationMs = totalDur,
                shouldQueueSyncAction = shouldQueueSyncAction
            )
            if (shouldQueueSyncAction) {
                positionsVersion++
            }
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
        saveCurrentPlaybackPosition(shouldQueueSyncAction = true)
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
                    if (!playing) saveCurrentPlaybackPosition(shouldQueueSyncAction = true)
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
                        val endedPod = currentPodcast
                        if (endedEp != null) {
                            val shouldKeepUnarchived = keepUnarchivedEpisodes.value.contains(endedEp.audioUrl)
                            val shouldAutoArchive = autoArchiveEnabled.value && !shouldKeepUnarchived
                            markEpisodePlayed(endedEp, true, fromPlaybackCompletion = true)
                            if (shouldAutoArchive) {
                                toggleArchive(endedEp, true, fromPlaybackCompletion = true)
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
                            playEpisode(nextItem.podcast, nextItem.episode, autoPlay = true, fromQueue = true, keepPreviousInQueue = false)
                        } else {
                            if (isPlayingQueue) {
                                isPlayingQueue = false
                                playbackNavTarget = PlaybackNavTarget.SubscriptionsHome
                            } else {
                                if (discoverSelectedPodcast == null && subscriptionSelectedPodcast == null && endedPod != null) {
                                    subscriptionSelectedPodcast = endedPod
                                    loadSubscriptionPodcastEpisodes(endedPod)
                                }
                                playbackNavTarget = PlaybackNavTarget.EpisodeList
                            }
                            clearCurrentPlayback()
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

    private var searchJob: kotlinx.coroutines.Job? = null

    fun search() {
        val query = searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            discoverSelectedPodcast = null
            discoverEpisodes = emptyList()
            try {
                // If the query looks like a direct RSS feed URL, attempt to fetch it directly first
                if (SecurityUtils.looksLikeFeedUrl(query)) {
                    when (val feedResult = NetworkModule.fetchPodcastFeedResult(query)) {
                        is NetworkModule.FeedFetchResult.Success -> {
                            val parsed = feedResult.parsedFeed
                            val directPod = Podcast(
                                title = parsed.channelTitle.ifBlank { "Custom Podcast" },
                                author = parsed.channelAuthor.ifBlank { "Custom Feed" },
                                artworkUrl = parsed.channelImage,
                                feedUrl = feedResult.feedUrl,
                                description = parsed.channelDescription,
                                isCustomFeed = true
                            )
                            podcasts = listOf(directPod)
                            isLoading = false
                            return@launch
                        }
                        is NetworkModule.FeedFetchResult.AuthRequired -> {
                            isLoading = false
                            openAddCustomFeedDialog(initialUrl = query, forceAuthExpanded = true)
                            return@launch
                        }
                        is NetworkModule.FeedFetchResult.Error -> {
                            // Fall through to iTunes search or show custom feed option
                        }
                    }
                }

                val results = NetworkModule.itunesApi.search(query).results.orEmpty()
                val seenFeeds = HashSet<String>()
                podcasts = results.mapNotNull { item ->
                    val feedUrl = item?.feedUrl?.trim()
                    val title = item?.collectionName?.trim()
                    if (!feedUrl.isNullOrBlank() && !title.isNullOrBlank() && seenFeeds.add(feedUrl)) {
                        Podcast(
                            title = title,
                            author = item.artistName?.trim()?.ifBlank { "Unknown" } ?: "Unknown",
                            artworkUrl = item.artworkUrl600?.trim() ?: "",
                            feedUrl = feedUrl
                        )
                    } else null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Search error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        podcasts = emptyList()
        discoverSelectedPodcast = null
        discoverEpisodes = emptyList()
        errorMessage = null
        isLoading = false
        if (!hasLoadedOnlineSuggestions && !isLoadingSuggestions) {
            loadSuggestedPodcasts()
        }
    }

    fun loadSuggestedPodcasts(forceRefresh: Boolean = false) {
        if ((!forceRefresh && hasLoadedOnlineSuggestions) || isLoadingSuggestions) return
        viewModelScope.launch {
            isLoadingSuggestions = true
            val currentUrls = suggestedPodcasts.map { it.feedUrl }.toSet()
            try {
                suggestionTopicIndex = (suggestionTopicIndex + 1) % generalSuggestionTopics.size
                techTopicIndex = (techTopicIndex + 1) % techSuggestionTopics.size
                val generalTopic = generalSuggestionTopics[suggestionTopicIndex]
                val techTopic = techSuggestionTopics[techTopicIndex]

                val generalDeferred = async { NetworkModule.itunesApi.searchWithLimit(generalTopic, 30).results.orEmpty() }
                val techDeferred = async { NetworkModule.itunesApi.searchWithLimit(techTopic, 30).results.orEmpty() }

                val generalResults = generalDeferred.await()
                val techResults = techDeferred.await()

                fun mapItems(items: List<ItunesItem?>, seen: HashSet<String>): List<Podcast> {
                    return items.mapNotNull { item ->
                        val feedUrl = item?.feedUrl?.trim()
                        val title = item?.collectionName?.trim()
                        if (!feedUrl.isNullOrBlank() && !title.isNullOrBlank() && seen.add(feedUrl)) {
                            Podcast(
                                title = title,
                                author = item.artistName?.trim()?.ifBlank { "Unknown" } ?: "Unknown",
                                artworkUrl = item.artworkUrl600?.trim() ?: "",
                                feedUrl = feedUrl
                            )
                        } else null
                    }
                }

                val seenFeeds = HashSet<String>()
                val parsedTech = mapItems(techResults, seenFeeds)
                val parsedGeneral = mapItems(generalResults, seenFeeds)

                val techPool = if (forceRefresh) {
                    val unseen = parsedTech.filterNot { currentUrls.contains(it.feedUrl) }
                    if (unseen.size >= 6) unseen.shuffled() else parsedTech.shuffled()
                } else {
                    parsedTech
                }
                val generalPool = if (forceRefresh) {
                    val unseen = parsedGeneral.filterNot { currentUrls.contains(it.feedUrl) }
                    if (unseen.size >= 6) unseen.shuffled() else parsedGeneral.shuffled()
                } else {
                    parsedGeneral
                }

                // Interleave tech podcasts with general podcasts so tech shows are always mixed in
                val mixed = mutableListOf<Podcast>()
                val maxLen = maxOf(techPool.size, generalPool.size)
                for (i in 0 until maxLen) {
                    if (i < techPool.size) mixed.add(techPool[i])
                    if (i < generalPool.size) mixed.add(generalPool[i])
                }

                if (mixed.isNotEmpty()) {
                    suggestedPodcasts = mixed.take(24)
                    hasLoadedOnlineSuggestions = true
                } else if (forceRefresh) {
                    suggestedPodcasts = suggestedPodcasts.shuffled()
                }
            } catch (_: Exception) {
                if (forceRefresh && suggestedPodcasts.size > 1) {
                    var reshuffled = suggestedPodcasts.shuffled()
                    if (reshuffled == suggestedPodcasts) {
                        reshuffled = suggestedPodcasts.reversed()
                    }
                    suggestedPodcasts = reshuffled
                }
            } finally {
                isLoadingSuggestions = false
            }
        }
    }

    fun refreshSuggestedPodcasts() {
        loadSuggestedPodcasts(forceRefresh = true)
    }

    fun getSavedPosition(audioUrl: String): Long {
        // Observe positionsVersion so Compose recomposes when positions are updated via playback or gPodder sync
        @Suppress("UNUSED_VARIABLE")
        val rev = positionsVersion
        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        return posPrefs.getLong(audioUrl, 0L)
    }

    fun getEpisodeDurationMs(episode: Episode): Long {
        @Suppress("UNUSED_VARIABLE")
        val rev = positionsVersion
        val parsed = parseDurationMs(episode.durationText)
        if (parsed > 0L) return parsed
        return GPodderSyncManager.getSavedDurationMs(app, episode.audioUrl)
    }

    fun onGPodderSyncCompleted() {
        positionsVersion++
        val ep = currentEpisode
        if (ep != null && !isPlaying) {
            val played = playedEpisodes.value
            val archived = archivedEpisodes.value
            if (played.contains(ep.audioUrl) || archived.contains(ep.audioUrl)) {
                clearCurrentPlayback()
            } else {
                val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                val syncedPos = posPrefs.getLong(ep.audioUrl, 0L)
                if (syncedPos != currentPosition) {
                    currentPosition = syncedPos
                    controller?.let { ctrl ->
                        if (!ctrl.isPlaying && ctrl.currentMediaItem?.mediaId == ep.audioUrl) {
                            ctrl.seekTo(syncedPos)
                        }
                    }
                }
            }
        }
        refreshSubscriptionFeedsInBackground(subscriptions.value)
        syncWidget()
    }

    fun loadSubscriptionPodcastEpisodes(podcast: Podcast) {
        if (LocalAudioManager.isLocalPodcast(podcast)) {
            val localPod = localAudioManager.buildLocalPodcast()
            val eps = localAudioManager.getEpisodes()
            subscriptionSelectedPodcast = localPod
            subscriptionEpisodes = eps
            recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, eps)
            isLoading = false
            return
        }
        subscriptionSelectedPodcast = podcast
        val cached = NetworkModule.getCachedFeed(podcast.feedUrl)
        if (cached != null && cached.episodes.isNotEmpty()) {
            subscriptionEpisodes = cached.episodes
            recordFeedEpisodesForBadges(podcast.feedUrl, cached.episodes)
            isLoading = false
        } else {
            isLoading = true
        }

        viewModelScope.launch {
            val parsed = NetworkModule.fetchPodcastFeed(podcast.feedUrl)
            if (parsed != null) {
                subscriptionEpisodes = parsed.episodes
                recordFeedEpisodesForBadges(podcast.feedUrl, parsed.episodes)
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
            recordFeedEpisodesForBadges(podcast.feedUrl, cached.episodes)
            isLoading = false
        } else {
            isLoading = true
        }

        viewModelScope.launch {
            val parsed = NetworkModule.fetchPodcastFeed(podcast.feedUrl)
            if (parsed != null) {
                discoverEpisodes = parsed.episodes
                recordFeedEpisodesForBadges(podcast.feedUrl, parsed.episodes)
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

    fun playEpisode(
        podcast: Podcast,
        episode: Episode,
        autoPlay: Boolean = false,
        fromQueue: Boolean = false,
        keepPreviousInQueue: Boolean = true
    ) {
        val prevPodcast = currentPodcast
        val prevEpisode = currentEpisode

        isPlayingQueue = fromQueue
        saveCurrentPlaybackPosition()

        currentPodcast = podcast
        currentEpisode = episode

        val filteredQueue = upNextQueue.filterNot {
            it.episode.audioUrl == episode.audioUrl ||
                (keepPreviousInQueue && prevEpisode != null && it.episode.audioUrl == prevEpisode.audioUrl)
        }
        val shouldPushPreviousToQueue = keepPreviousInQueue &&
            prevPodcast != null &&
            prevEpisode != null &&
            prevEpisode.audioUrl.isNotBlank() &&
            prevEpisode.audioUrl != episode.audioUrl &&
            !playedEpisodes.value.contains(prevEpisode.audioUrl) &&
            !archivedEpisodes.value.contains(prevEpisode.audioUrl)

        val updatedQueue = if (shouldPushPreviousToQueue && prevPodcast != null && prevEpisode != null) {
            listOf(QueueItem(prevPodcast, prevEpisode)) + filteredQueue
        } else {
            filteredQueue
        }
        if (updatedQueue != upNextQueue) {
            upNextQueue = updatedQueue
            saveUpNextQueue()
        }

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
            ?: localAudioManager.getLocalUri(episode.audioUrl)
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

        val targetSpeed = getPlaybackSpeedForPodcast(podcast)
        controller?.apply {
            setPlaybackSpeed(targetSpeed)
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
        val matchedSub = subscriptions.value.find {
            it.title.trim().equals(downloaded.podcastTitle.trim(), ignoreCase = true) ||
                (downloaded.podcastArtworkUrl.isNotBlank() && it.artworkUrl == downloaded.podcastArtworkUrl)
        }
        val podcast = Podcast(
            title = downloaded.podcastTitle,
            author = matchedSub?.author?.ifBlank { "Downloaded" } ?: "Downloaded",
            artworkUrl = downloaded.podcastArtworkUrl.ifBlank { matchedSub?.artworkUrl ?: "" },
            feedUrl = matchedSub?.feedUrl ?: ""
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
        downloadManager.downloadEpisodes(podcast, episodes)
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
            ?: localAudioManager.getFormattedSize(audioUrl)
    }

    private fun persistLocalPodcastInSubscriptions(localPod: Podcast) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    val json = prefs[stringPreferencesKey("subscriptions")]
                    var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                    if (current.isEmpty()) {
                        val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                        if (backup.isNotEmpty()) current = backup.toMutableList()
                    }
                    val idx = current.indexOfFirst { LocalAudioManager.isLocalPodcast(it) }
                    if (idx >= 0) {
                        current[idx] = localPod
                    } else {
                        current.add(0, localPod)
                    }
                    val updatedJson = Gson().toJson(current)
                    prefs[stringPreferencesKey("subscriptions")] = updatedJson
                    SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                }
            } catch (_: Exception) {}
        }
    }

    private fun removeLocalPodcastFromSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    val json = prefs[stringPreferencesKey("subscriptions")]
                    var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                    if (current.isEmpty()) {
                        val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                        if (backup.isNotEmpty()) current = backup.toMutableList()
                    }
                    if (current.removeAll { LocalAudioManager.isLocalPodcast(it) }) {
                        val updatedJson = Gson().toJson(current)
                        prefs[stringPreferencesKey("subscriptions")] = updatedJson
                        if (current.isEmpty()) {
                            SubscriptionStorageManager.recordExplicitEmpty(app)
                        } else {
                            SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun importLocalAudioFiles(uris: List<Uri>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val added = localAudioManager.importAudioUris(uris)
            if (added.isNotEmpty()) {
                val eps = localAudioManager.getEpisodes()
                recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, eps)
                val localPod = localAudioManager.buildLocalPodcast()
                if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
                    subscriptionSelectedPodcast = localPod
                    subscriptionEpisodes = eps
                }
                persistLocalPodcastInSubscriptions(localPod)
            }
            onComplete(added.size)
        }
    }

    fun renameLocalAudioFile(fileId: String, newTitle: String) {
        val updatedFile = localAudioManager.renameAudioFile(fileId, newTitle) ?: return
        val eps = localAudioManager.getEpisodes()
        recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, eps)
        if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
            subscriptionEpisodes = eps
        }
        if (currentEpisode?.audioUrl == updatedFile.audioUrl) {
            val updatedEp = currentEpisode!!.copy(title = updatedFile.title)
            currentEpisode = updatedEp
            val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            statePrefs.edit().putString("last_episode", appGson.toJson(updatedEp)).apply()
            viewModelScope.launch {
                try {
                    app.dataStore.edit { it[stringPreferencesKey("last_episode")] = appGson.toJson(updatedEp) }
                } catch (_: Exception) {}
            }
            syncWidget()
        }
        if (upNextQueue.any { it.episode.audioUrl == updatedFile.audioUrl }) {
            upNextQueue = upNextQueue.map { q ->
                if (q.episode.audioUrl == updatedFile.audioUrl) {
                    q.copy(episode = q.episode.copy(title = updatedFile.title))
                } else q
            }
            saveUpNextQueue()
            syncWidget()
        }
    }

    fun deleteLocalAudioFile(fileId: String) {
        val removed = localAudioManager.deleteAudioFile(fileId) ?: return
        val audioUrl = removed.audioUrl
        if (currentEpisode?.audioUrl == audioUrl) {
            advanceOrClearCurrentEpisode()
        }
        if (upNextQueue.any { it.episode.audioUrl == audioUrl }) {
            removeFromUpNextByUrl(audioUrl)
        }
        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        posPrefs.edit().remove(audioUrl).apply()

        val remainingEps = localAudioManager.getEpisodes()
        recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, remainingEps)
        if (remainingEps.isEmpty()) {
            if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
                subscriptionSelectedPodcast = null
                subscriptionEpisodes = emptyList()
            }
            removeLocalPodcastFromSubscriptions()
        } else {
            val localPod = localAudioManager.buildLocalPodcast()
            if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
                subscriptionSelectedPodcast = localPod
                subscriptionEpisodes = remainingEps
            }
            persistLocalPodcastInSubscriptions(localPod)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    val playedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                    val archKey = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                    val keepKey = androidx.datastore.preferences.core.stringSetPreferencesKey("keep_unarchived_episodes")
                    prefs[playedKey]?.let { prefs[playedKey] = it - audioUrl }
                    prefs[archKey]?.let { prefs[archKey] = it - audioUrl }
                    prefs[keepKey]?.let { prefs[keepKey] = it - audioUrl }
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteAllLocalAudioFiles() {
        val removedList = localAudioManager.deleteAllAudioFiles()
        val removedUrls = removedList.map { it.audioUrl }.toSet()
        if (currentEpisode != null && removedUrls.contains(currentEpisode?.audioUrl)) {
            advanceOrClearCurrentEpisode()
        }
        if (upNextQueue.any { removedUrls.contains(it.episode.audioUrl) }) {
            upNextQueue = upNextQueue.filterNot { removedUrls.contains(it.episode.audioUrl) }
            saveUpNextQueue()
            syncWidget()
        }
        if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
            subscriptionSelectedPodcast = null
            subscriptionEpisodes = emptyList()
        }
        recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, emptyList())
        removeLocalPodcastFromSubscriptions()

        val posPrefs = app.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        val posEdit = posPrefs.edit()
        removedUrls.forEach { posEdit.remove(it) }
        posEdit.apply()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.dataStore.edit { prefs ->
                    val playedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                    val archKey = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                    val keepKey = androidx.datastore.preferences.core.stringSetPreferencesKey("keep_unarchived_episodes")
                    prefs[playedKey]?.let { prefs[playedKey] = it - removedUrls }
                    prefs[archKey]?.let { prefs[archKey] = it - removedUrls }
                    prefs[keepKey]?.let { prefs[keepKey] = it - removedUrls }
                }
            } catch (_: Exception) {}
        }
    }

    fun updateLocalSubscriptionTitle(newTitle: String) {
        localAudioManager.updateSubscriptionTitle(newTitle)
        val localPod = localAudioManager.buildLocalPodcast(localAudioManager.subscriptionTitle.value)
        if (LocalAudioManager.isLocalPodcast(subscriptionSelectedPodcast)) {
            subscriptionSelectedPodcast = localPod
        }
        if (LocalAudioManager.isLocalPodcast(currentPodcast)) {
            currentPodcast = localPod
            val statePrefs = app.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            statePrefs.edit().putString("last_podcast", appGson.toJson(localPod)).apply()
            viewModelScope.launch {
                try {
                    app.dataStore.edit { it[stringPreferencesKey("last_podcast")] = appGson.toJson(localPod) }
                } catch (_: Exception) {}
            }
            syncWidget()
        }
        if (upNextQueue.any { LocalAudioManager.isLocalPodcast(it.podcast) }) {
            upNextQueue = upNextQueue.map { q ->
                if (LocalAudioManager.isLocalPodcast(q.podcast)) {
                    q.copy(podcast = localPod)
                } else q
            }
            saveUpNextQueue()
            syncWidget()
        }
        if (localAudioFiles.value.isNotEmpty()) {
            persistLocalPodcastInSubscriptions(localPod)
        }
    }

    fun playLocalAudioFile(file: LocalAudioFile, autoPlay: Boolean = true) {
        val localPod = localAudioManager.buildLocalPodcast()
        val episode = file.toEpisode()
        playEpisode(localPod, episode, autoPlay = autoPlay)
    }

    fun openLocalAudioInSubscriptions() {
        if (localAudioFiles.value.isEmpty()) return
        val localPod = localAudioManager.buildLocalPodcast()
        val eps = localAudioManager.getEpisodes()
        discoverSelectedPodcast = null
        subscriptionSelectedPodcast = localPod
        subscriptionEpisodes = eps
        recordFeedEpisodesForBadges(LocalAudioManager.LOCAL_FEED_URL, eps)
        playbackNavTarget = PlaybackNavTarget.EpisodeList
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
        if (currentEpisode?.audioUrl == episode.audioUrl) return
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
                    NetworkModule.executeAuthenticatedCall(
                        url = episode.chaptersUrl,
                        feedUrl = currentPodcast?.feedUrl
                    ).use { resp ->
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
            app.updateAllPodcastWidgets()
        }
    }

    fun updateTheme(theme: Int) {
        if (currentThemeConfig == theme && _themeConfig.value == theme) return
        currentThemeConfig = theme
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

    fun toggleArchive(episode: Episode, archive: Boolean, fromPlaybackCompletion: Boolean = false) {
        val activePodcastUrl = (subscriptionSelectedPodcast ?: discoverSelectedPodcast ?: currentPodcast)?.feedUrl
        if (archive && !fromPlaybackCompletion && currentEpisode?.audioUrl == episode.audioUrl) {
            advanceOrClearCurrentEpisode()
        }
        viewModelScope.launch {
            if (archive) {
                deleteDownload(episode.audioUrl)
            }
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                val playedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                val currentPlayed = prefs[playedKey]?.toMutableSet() ?: mutableSetOf()
                if (archive) {
                    current.add(episode.audioUrl)
                    if (fromPlaybackCompletion) {
                        currentPlayed.add(episode.audioUrl)
                        prefs[playedKey] = currentPlayed
                    }
                } else {
                    current.remove(episode.audioUrl)
                    // When an episode is unarchived, mark it as unplayed
                    if (currentPlayed.remove(episode.audioUrl)) {
                        prefs[playedKey] = currentPlayed
                    }
                }
                prefs[key] = current
            }
            GPodderSyncManager.onEpisodeArchiveStateChanged(
                context = app,
                podcastUrl = activePodcastUrl,
                episodeUrl = episode.audioUrl,
                archived = archive,
                fromPlaybackCompletion = fromPlaybackCompletion,
                durationMs = parseDurationMs(episode.durationText)
            )
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
                    // If episode was previously archived, unarchive it and mark it as unplayed
                    val archiveKey = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                    val archivedSet = prefs[archiveKey]?.toMutableSet()
                    if (archivedSet != null && archivedSet.remove(episode.audioUrl)) {
                        prefs[archiveKey] = archivedSet
                        val playedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                        val playedSet = prefs[playedKey]?.toMutableSet()
                        if (playedSet != null && playedSet.remove(episode.audioUrl)) {
                            prefs[playedKey] = playedSet
                        }
                    }
                }
                prefs[key] = current
            }
        }
    }

    fun archiveAllEpisodes(episodes: List<Episode>) {
        val activePodcastUrl = (subscriptionSelectedPodcast ?: discoverSelectedPodcast ?: currentPodcast)?.feedUrl
        val pinned = keepUnarchivedEpisodes.value
        val unpinnedEpisodes = episodes.filter { !pinned.contains(it.audioUrl) }
        if (currentEpisode != null && unpinnedEpisodes.any { it.audioUrl == currentEpisode?.audioUrl }) {
            advanceOrClearCurrentEpisode()
        }
        viewModelScope.launch {
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
            unpinnedEpisodes.forEach { ep ->
                if (ep.audioUrl.isNotBlank()) {
                    GPodderSyncManager.onEpisodeArchiveStateChanged(
                        context = app,
                        podcastUrl = activePodcastUrl,
                        episodeUrl = ep.audioUrl,
                        archived = true,
                        durationMs = parseDurationMs(ep.durationText)
                    )
                }
            }
        }
    }

    fun unarchiveAllEpisodes(episodes: List<Episode>) {
        val activePodcastUrl = (subscriptionSelectedPodcast ?: discoverSelectedPodcast ?: currentPodcast)?.feedUrl
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("archived_episodes")
                val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
                val playedKey = androidx.datastore.preferences.core.stringSetPreferencesKey("played_episodes")
                val currentPlayed = prefs[playedKey]?.toMutableSet() ?: mutableSetOf()
                episodes.forEach { ep ->
                    current.remove(ep.audioUrl)
                    currentPlayed.remove(ep.audioUrl)
                }
                prefs[key] = current
                prefs[playedKey] = currentPlayed
            }
            episodes.forEach { ep ->
                if (ep.audioUrl.isNotBlank()) {
                    GPodderSyncManager.onEpisodeArchiveStateChanged(
                        context = app,
                        podcastUrl = activePodcastUrl,
                        episodeUrl = ep.audioUrl,
                        archived = false,
                        durationMs = parseDurationMs(ep.durationText)
                    )
                }
            }
        }
    }

    fun markEpisodePlayed(episode: Episode, played: Boolean, fromPlaybackCompletion: Boolean = false) {
        val activePodcastUrl = (subscriptionSelectedPodcast ?: discoverSelectedPodcast ?: currentPodcast)?.feedUrl
        val epDurationMs = parseDurationMs(episode.durationText).let { if (it > 0L) it else if (currentEpisode?.audioUrl == episode.audioUrl && duration > 1L) duration else 0L }
        if (played && !fromPlaybackCompletion && currentEpisode?.audioUrl == episode.audioUrl) {
            advanceOrClearCurrentEpisode()
        }
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
            positionsVersion++
            GPodderSyncManager.onEpisodePlayedStateChanged(
                context = app,
                podcastUrl = activePodcastUrl,
                episodeUrl = episode.audioUrl,
                played = played,
                durationMs = epDurationMs
            )
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

    fun setShowMiniPlayer(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("show_mini_player")] = enabled }
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

    fun setShowUnplayedBadge(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("show_unplayed_badge")] = enabled }
        }
    }

    fun setShowDownloadedBadge(enabled: Boolean) {
        viewModelScope.launch {
            app.dataStore.edit { it[booleanPreferencesKey("show_downloaded_badge")] = enabled }
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
            app.updateAllPodcastWidgets()
        }
    }

    fun toggleSubscription(podcast: Podcast) {
        viewModelScope.launch {
            var wasRemoved = false
            app.dataStore.edit { prefs ->
                val json = prefs[stringPreferencesKey("subscriptions")]
                var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                if (current.isEmpty()) {
                    val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                    if (backup.isNotEmpty()) current = backup.toMutableList()
                }

                if (current.any { it.feedUrl == podcast.feedUrl }) {
                    current.removeAll { it.feedUrl == podcast.feedUrl }
                    wasRemoved = true
                } else {
                    current.add(podcast)
                    wasRemoved = false
                }
                val updatedJson = Gson().toJson(current)
                prefs[stringPreferencesKey("subscriptions")] = updatedJson
                if (current.isEmpty()) {
                    SubscriptionStorageManager.recordExplicitEmpty(app)
                } else {
                    SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
                }
            }
            if (wasRemoved) {
                GPodderSyncManager.onSubscriptionRemoved(app, podcast.feedUrl)
            } else {
                GPodderSyncManager.onSubscriptionAdded(app, podcast.feedUrl)
            }
        }
    }

    suspend fun addOrUpdateCustomFeed(
        rawUrl: String,
        username: String,
        password: String,
        existingPodcast: Podcast? = null,
        navigateOnSuccess: Boolean = true
    ): NetworkModule.FeedFetchResult {
        val normalized = SecurityUtils.normalizeFeedUrl(rawUrl)
            ?: return NetworkModule.FeedFetchResult.Error("Please enter a valid RSS feed URL (http:// or https://).")
        val (cleanUrl, embeddedCreds) = FeedCredentialsManager.extractEmbeddedCredentials(normalized)
        val finalUser = username.trim().ifBlank { embeddedCreds?.username ?: "" }
        val finalPass = if (username.isNotBlank() || password.isNotBlank()) password else (embeddedCreds?.password ?: "")

        val fetchResult = NetworkModule.fetchPodcastFeedResult(
            rawFeedUrl = cleanUrl,
            username = finalUser.ifBlank { null },
            password = finalPass.ifBlank { null }
        )

        if (fetchResult is NetworkModule.FeedFetchResult.Success) {
            val resolvedFeedUrl = fetchResult.feedUrl
            val parsed = fetchResult.parsedFeed

            FeedCredentialsManager.saveCredentials(app, resolvedFeedUrl, finalUser, finalPass)
            if (parsed.episodes.isNotEmpty()) {
                FeedCredentialsManager.registerEpisodesForFeed(app, resolvedFeedUrl, parsed.episodes.map { it.audioUrl })
                recordFeedEpisodesForBadges(resolvedFeedUrl, parsed.episodes)
            }

            val updatedPodcast = Podcast(
                title = parsed.channelTitle.ifBlank { existingPodcast?.title?.ifBlank { "Custom Podcast" } ?: "Custom Podcast" },
                author = parsed.channelAuthor.ifBlank { existingPodcast?.author?.ifBlank { "Private Feed" } ?: "Private Feed" },
                artworkUrl = parsed.channelImage.ifBlank { existingPodcast?.artworkUrl ?: "" },
                feedUrl = resolvedFeedUrl,
                description = parsed.channelDescription.ifBlank { existingPodcast?.description ?: "" },
                isCustomFeed = existingPodcast?.isCustomFeed ?: true
            )

            app.dataStore.edit { prefs ->
                val json = prefs[stringPreferencesKey("subscriptions")]
                var current: MutableList<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(json).toMutableList()
                if (current.isEmpty()) {
                    val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(app)
                    if (backup.isNotEmpty()) current = backup.toMutableList()
                }

                val existingIdx = current.indexOfFirst {
                    it.feedUrl == resolvedFeedUrl || (existingPodcast != null && it.feedUrl == existingPodcast.feedUrl)
                }
                if (existingIdx != -1) {
                    current[existingIdx] = updatedPodcast
                } else {
                    current.add(0, updatedPodcast)
                }

                val updatedJson = Gson().toJson(current)
                prefs[stringPreferencesKey("subscriptions")] = updatedJson
                SubscriptionStorageManager.saveSubscriptionsToBackup(app, updatedJson)
            }
            GPodderSyncManager.onSubscriptionAdded(app, resolvedFeedUrl)

            if (subscriptionSelectedPodcast?.feedUrl == resolvedFeedUrl ||
                (existingPodcast != null && subscriptionSelectedPodcast?.feedUrl == existingPodcast.feedUrl)
            ) {
                subscriptionSelectedPodcast = updatedPodcast
                subscriptionEpisodes = parsed.episodes
            }
            if (discoverSelectedPodcast?.feedUrl == resolvedFeedUrl ||
                (existingPodcast != null && discoverSelectedPodcast?.feedUrl == existingPodcast.feedUrl)
            ) {
                discoverSelectedPodcast = updatedPodcast
                discoverEpisodes = parsed.episodes
            }

            if (navigateOnSuccess && existingPodcast == null) {
                discoverSelectedPodcast = null
                subscriptionSelectedPodcast = updatedPodcast
                subscriptionEpisodes = parsed.episodes
                playbackNavTarget = PlaybackNavTarget.EpisodeList
            }
        }

        return fetchResult
    }

    fun removeFeedCredentials(podcast: Podcast) {
        FeedCredentialsManager.removeCredentials(app, podcast.feedUrl)
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

                    val currentSubs = subscriptions.value.filterNot { LocalAudioManager.isLocalPodcast(it) }
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
                GPodderSyncManager.onSubscriptionsAdded(app, fullSubs.map { it.feedUrl })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // gPodder Sync Methods
    fun saveGPodderConfig(
        enabled: Boolean,
        serverType: GPodderServerType = gpodderSyncState.value.config.serverType,
        serverUrl: String,
        username: String,
        password: String,
        intervalMinutes: Int
    ): GPodderConfig {
        return GPodderSyncManager.saveConfig(
            context = app,
            enabled = enabled,
            serverType = serverType,
            serverUrl = serverUrl,
            username = username,
            password = password,
            syncIntervalMinutes = intervalMinutes
        )
    }

    fun setGPodderSyncEnabled(enabled: Boolean) {
        GPodderSyncManager.setSyncEnabled(app, enabled)
    }

    fun updateGPodderSyncInterval(intervalMinutes: Int) {
        GPodderSyncManager.updateSyncInterval(app, intervalMinutes)
    }

    suspend fun testGPodderConnection(
        serverType: GPodderServerType = gpodderSyncState.value.config.serverType,
        serverUrl: String,
        username: String,
        password: String
    ): GPodderConnectionTestResult {
        return GPodderSyncManager.testConnection(
            context = app,
            serverType = serverType,
            rawServerUrl = serverUrl,
            username = username,
            password = password,
            saveOnSuccess = true
        )
    }

    fun syncGPodderNow(onResult: (GPodderSyncResult) -> Unit = {}) {
        saveCurrentPlaybackPosition(shouldQueueSyncAction = true)
        GPodderSyncManager.syncNowAsync(app) { res ->
            onGPodderSyncCompleted()
            onResult(res)
        }
    }

    fun disconnectGPodder() {
        GPodderSyncManager.disconnectServer(app)
    }

    fun clearGPodderTestResult() {
        GPodderSyncManager.clearTestResult()
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
                            if (context != null) {
                                vm.upNextQueue = QueueStorageManager.loadQueueFromBackup(context)
                                    .filterNot { it.episode.audioUrl == vm.currentEpisode?.audioUrl }
                                vm.saveUpNextQueue()
                            } else {
                                vm.removeFromUpNextByUrl(playedUrl)
                            }
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
                GPodderSyncManager.ACTION_GPODDER_SYNC_COMPLETED -> {
                    viewModelRef?.onGPodderSyncCompleted()
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
            addAction(GPodderSyncManager.ACTION_GPODDER_SYNC_COMPLETED)
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
            val themeConfig = vm.currentThemeConfig

            LaunchedEffect(intent) {
                handleNotificationIntent(intent, vm)
            }

            ExpressivePodcastTheme(themeConfig = themeConfig) {
                PodcastAppScreen(vm = vm, themeConfig = themeConfig)
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

private fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) return start
    if (f >= 1f) return stop
    return ColorScheme(
        primary = lerp(start.primary, stop.primary, f),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, f),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, f),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, f),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, f),
        secondary = lerp(start.secondary, stop.secondary, f),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, f),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, f),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, f),
        tertiary = lerp(start.tertiary, stop.tertiary, f),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, f),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, f),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, f),
        background = lerp(start.background, stop.background, f),
        onBackground = lerp(start.onBackground, stop.onBackground, f),
        surface = lerp(start.surface, stop.surface, f),
        onSurface = lerp(start.onSurface, stop.onSurface, f),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, f),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, f),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, f),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, f),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, f),
        error = lerp(start.error, stop.error, f),
        onError = lerp(start.onError, stop.onError, f),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, f),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, f),
        outline = lerp(start.outline, stop.outline, f),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, f),
        scrim = lerp(start.scrim, stop.scrim, f),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, f),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, f),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, f),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, f),
        surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, f),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, f),
        surfaceContainerLowest = lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, f)
    )
}

@Composable
fun ExpressivePodcastTheme(themeConfig: Int = 0, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = when (themeConfig) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val (lightScheme, darkScheme) = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val lightContext = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                }
            )
            val darkContext = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
            )
            val light = try {
                dynamicLightColorScheme(lightContext)
            } catch (_: Exception) {
                lightColorScheme()
            }
            val dark = try {
                dynamicDarkColorScheme(darkContext)
            } catch (_: Exception) {
                darkColorScheme()
            }
            light to dark
        } else {
            lightColorScheme() to darkColorScheme()
        }
    }

    val themeFraction by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ThemeFadeFraction"
    )

    val colorScheme = remember(lightScheme, darkScheme, themeFraction) {
        lerpColorScheme(lightScheme, darkScheme, themeFraction)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.isForceDarkAllowed = false
        }
        SideEffect {
            val window = (context as? Activity)?.window
            if (window != null) {
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

fun View.performSubtleHaptic() {
    if (!performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun rememberSubtleHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performSubtleHaptic() }
    }
}

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APPEARANCE_AND_DISPLAY(
        title = "Appearance & Display Options",
        subtitle = "Light, dark, or system theme & keep screen awake",
        icon = Icons.Default.Palette
    ),
    PLAYBACK(
        title = "Playback Options",
        subtitle = "Mini player, time display, speed & skip intervals",
        icon = Icons.Default.GraphicEq
    ),
    PODCAST_MANAGEMENT(
        title = "Podcast Management",
        subtitle = "Episode badges, auto-archive & downloads",
        icon = Icons.Default.Podcasts
    ),
    LOCAL_AUDIO_FILES(
        title = "Local Audio Files",
        subtitle = "Add, rename, delete & set subscriptions title",
        icon = Icons.Default.AudioFile
    ),
    NOTIFICATIONS(
        title = "Background Activity & Notifications",
        subtitle = "New episode alerts & streaming cache",
        icon = Icons.Default.NotificationsActive
    ),
    BACKUP(
        title = "Backup",
        subtitle = "OPML import/export & gPodder synchronization",
        icon = Icons.Default.Backup
    ),
    ABOUT(
        title = "About ExpressoPod",
        subtitle = "App version & information",
        icon = Icons.Default.Info
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastAppScreen(vm: PodcastViewModel = viewModel(), themeConfig: Int = vm.currentThemeConfig) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
    val currentColorScheme = MaterialTheme.colorScheme
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
        enabled = activeTab != 0 || vm.subscriptionSelectedPodcast != null || vm.discoverSelectedPodcast != null || vm.settingsSelectedCategory != null
    ) {
        when {
            activeTab == 0 && vm.subscriptionSelectedPodcast != null -> {
                vm.subscriptionSelectedPodcast = null
            }
            activeTab == 1 && vm.discoverSelectedPodcast != null -> {
                vm.discoverSelectedPodcast = null
            }
            activeTab == 3 && vm.settingsSelectedCategory != null -> {
                vm.settingsSelectedCategory = null
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
    val showMiniPlayer by vm.showMiniPlayer.collectAsState()
    val isGridView by vm.isGridView.collectAsState()

    Scaffold(
        containerColor = currentColorScheme.background,
        contentColor = currentColorScheme.onBackground,
        topBar = {
            TopAppBar(
                modifier = Modifier.background(currentColorScheme.surface),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = currentColorScheme.onSurface,
                    titleContentColor = currentColorScheme.onSurface,
                    actionIconContentColor = currentColorScheme.onSurface
                ),
                title = {
                    val headerTitle = when (activeTab) {
                        0 -> "Subscriptions"
                        1 -> "Discover"
                        2 -> "Now Playing"
                        3 -> "Settings"
                        4 -> "Up Next"
                        else -> "Subscriptions"
                    }
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
                            headerTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        if (activeTab == 0 && vm.subscriptionSelectedPodcast == null) {
                            IconButton(
                                onClick = {
                                    subtleHaptic()
                                    vm.updateGridView(!isGridView)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    if (isGridView) Icons.Default.Menu else Icons.Default.GridView,
                                    contentDescription = "Toggle View",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        val upNextTotalCount = vm.upNextQueue.size + if (vm.currentEpisode != null && vm.currentPodcast != null) 1 else 0
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(
                                    onClick = {
                                        subtleHaptic()
                                        activeTab = 4
                                    },
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, radius = 22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (upNextTotalCount > 0) {
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
                                                targetState = upNextTotalCount,
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
                val isMiniPlayerVisible = showMiniPlayer && currentEpisode != null && currentPodcast != null && (activeTab != 2 || vm.nowPlayingTabIndex != 0)

                AnimatedVisibility(
                    visible = isMiniPlayerVisible,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    if (currentEpisode != null && currentPodcast != null) {
                        MiniPlayer(
                            podcast = currentPodcast,
                            episode = currentEpisode,
                            isPlaying = vm.isPlaying,
                            currentPos = vm.currentPosition,
                            duration = vm.duration,
                            onTogglePlayPause = { vm.togglePlayPause() },
                            onSkipBackward = { vm.skipRewind() },
                            onSkipForward = { vm.skipForward() },
                            onClick = {
                                vm.nowPlayingTabIndex = 0
                                activeTab = 2
                            }
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
                            val itemIconColor = if (selected) currentColorScheme.onSecondaryContainer else currentColorScheme.onSurfaceVariant
                            val itemTextColor = if (selected) currentColorScheme.onSurface else currentColorScheme.onSurfaceVariant
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    subtleHaptic()
                                    if (index == 0) {
                                        vm.subscriptionSelectedPodcast = null
                                    }
                                    if (index == 3 && activeTab == 3) {
                                        vm.settingsSelectedCategory = null
                                    }
                                    activeTab = index
                                },
                                icon = {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = itemIconColor,
                                        modifier = Modifier.size(iconSize)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        color = itemTextColor,
                                        style = tabTextStyle.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = currentColorScheme.onSecondaryContainer,
                                    selectedTextColor = currentColorScheme.onSurface,
                                    indicatorColor = currentColorScheme.secondaryContainer,
                                    unselectedIconColor = currentColorScheme.onSurfaceVariant,
                                    unselectedTextColor = currentColorScheme.onSurfaceVariant
                                ),
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
                            },
                            onOpenLocalAudioSettings = {
                                vm.settingsSelectedCategory = SettingsCategory.LOCAL_AUDIO_FILES
                                activeTab = 3
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
                        SettingsScreen(vm = vm, themeConfig = themeConfig)
                    }
                    4 -> {
                        UpNextScreen(
                            vm = vm,
                            onClose = { activeTab = 0 },
                            onOpenNowPlaying = {
                                vm.nowPlayingTabIndex = 0
                                activeTab = 2
                            }
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

        if (vm.showCustomFeedDialog) {
            CustomRssFeedDialog(vm = vm)
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
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtleHaptic = rememberSubtleHaptic()
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
            .clickable {
                subtleHaptic()
                onClick()
            }
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        subtleHaptic()
                        onSkipBackward()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FastRewind,
                        contentDescription = "Skip Backward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        subtleHaptic()
                        onTogglePlayPause()
                    },
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

                IconButton(
                    onClick = {
                        subtleHaptic()
                        onSkipForward()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Skip Forward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: PodcastViewModel, themeConfig: Int = vm.currentThemeConfig) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
    val rewindPreset by vm.rewindSec.collectAsState()
    val forwardPreset by vm.forwardSec.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val showMiniPlayer by vm.showMiniPlayer.collectAsState()
    val showRemainingTime by vm.showRemainingTime.collectAsState()
    val cachePlayingEpisode by vm.cachePlayingEpisode.collectAsState()
    val showUnplayedBadge by vm.showUnplayedBadge.collectAsState()
    val showDownloadedBadge by vm.showDownloadedBadge.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()
    val mutedPodcastUrls by vm.mutedNotificationPodcasts.collectAsState()
    val podcastsToNotifyExpanded by vm.podcastsToNotifyExpanded.collectAsState()
    val localAudioFiles by vm.localAudioFiles.collectAsState()
    val localSubscriptionTitle by vm.localSubscriptionTitle.collectAsState()
    val isImportingLocalAudio by vm.isImportingLocalAudio.collectAsState()

    val launcherAddLocalAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.importLocalAudioFiles(uris) { addedCount ->
                if (addedCount > 0) {
                    Toast.makeText(
                        context,
                        "Added $addedCount audio ${if (addedCount == 1) "file" else "files"} to \"${vm.localSubscriptionTitle.value}\"",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "Could not import selected file(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

    val selectedCategory = vm.settingsSelectedCategory

    AnimatedContent(
        targetState = selectedCategory,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { width -> width / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut())
            } else {
                (slideInHorizontally { width -> -width / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
            }
        },
        label = "SettingsCategoryTransition"
    ) { category ->
        when (category) {
            null -> {
                val listScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(listScrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsCategory.entries.forEach { cat ->
                        SettingsCategoryItemCard(
                            category = cat,
                            onClick = {
                                subtleHaptic()
                                vm.settingsSelectedCategory = cat
                            }
                        )
                    }
                }
            }

            SettingsCategory.ABOUT -> {
                AboutScreen(onBack = { vm.settingsSelectedCategory = null })
            }

            else -> {
                androidx.activity.compose.BackHandler {
                    vm.settingsSelectedCategory = null
                }

                val detailScrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                subtleHaptic()
                                vm.settingsSelectedCategory = null
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(detailScrollState)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (category) {
                            SettingsCategory.APPEARANCE_AND_DISPLAY -> {
                                ThemeSettingsCard(
                                    currentTheme = themeConfig,
                                    onSelectTheme = { vm.updateTheme(it) }
                                )
                                KeepScreenOnCard(
                                    keepScreenOn = keepScreenOn,
                                    onToggleKeepScreenOn = { vm.setKeepScreenOn(it) }
                                )
                            }

                            SettingsCategory.PLAYBACK -> {
                                ShowMiniPlayerCard(
                                    showMiniPlayer = showMiniPlayer,
                                    onToggleShowMiniPlayer = { vm.setShowMiniPlayer(it) }
                                )
                                ShowRemainingTimeCard(
                                    showRemainingTime = showRemainingTime,
                                    onToggleShowRemainingTime = { vm.setShowRemainingTime(it) }
                                )
                                GlobalPlaybackSpeedCard(
                                    globalSpeed = vm.globalPlaybackSpeed,
                                    onUpdateGlobalSpeed = { vm.updateGlobalPlaybackSpeed(it) }
                                )
                                SkipPresetsCard(
                                    rewindPreset = rewindPreset,
                                    forwardPreset = forwardPreset,
                                    onUpdateRewind = { vm.updatePresets(it, forwardPreset) },
                                    onUpdateForward = { vm.updatePresets(rewindPreset, it) }
                                )
                            }

                            SettingsCategory.PODCAST_MANAGEMENT -> {
                                EpisodeBadgesSettingsCard(
                                    showUnplayedBadge = showUnplayedBadge,
                                    onToggleUnplayedBadge = { vm.setShowUnplayedBadge(it) },
                                    showDownloadedBadge = showDownloadedBadge,
                                    onToggleDownloadedBadge = { vm.setShowDownloadedBadge(it) }
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
                            }

                            SettingsCategory.LOCAL_AUDIO_FILES -> {
                                LocalAudioFilesSettingsSection(
                                    localFiles = localAudioFiles,
                                    subscriptionTitle = localSubscriptionTitle,
                                    isImporting = isImportingLocalAudio,
                                    currentPlayingUrl = vm.currentEpisode?.audioUrl,
                                    isPlaying = vm.isPlaying,
                                    onUpdateSubscriptionTitle = { newTitle ->
                                        vm.updateLocalSubscriptionTitle(newTitle)
                                    },
                                    onAddAudioFiles = {
                                        launcherAddLocalAudio.launch(arrayOf("audio/*"))
                                    },
                                    onRenameAudioFile = { fileId, newTitle ->
                                        vm.renameLocalAudioFile(fileId, newTitle)
                                    },
                                    onDeleteAudioFile = { fileId ->
                                        vm.deleteLocalAudioFile(fileId)
                                    },
                                    onDeleteAllAudioFiles = {
                                        vm.deleteAllLocalAudioFiles()
                                    },
                                    onPlayAudioFile = { file ->
                                        if (vm.currentEpisode?.audioUrl == file.audioUrl) {
                                            vm.togglePlayPause()
                                        } else {
                                            vm.playLocalAudioFile(file, autoPlay = true)
                                        }
                                    },
                                    onOpenInSubscriptions = {
                                        vm.openLocalAudioInSubscriptions()
                                    }
                                )
                            }

                            SettingsCategory.NOTIFICATIONS -> {
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
                                    subscriptions = subscriptions.filterNot { LocalAudioManager.isLocalPodcast(it) },
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
                            }

                            SettingsCategory.BACKUP -> {
                                OpmlSubscriptionsCard(
                                    onImport = { launcherImport.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                                    onExport = { launcherExport.launch("subscriptions.opml") }
                                )
                                GPodderSyncCard(vm = vm)
                            }

                            SettingsCategory.ABOUT -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItemCard(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val cardPadding = if (screenWidthDp < 360) 14.dp else 18.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open ${category.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    val subtleHaptic = rememberSubtleHaptic()
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
            .clickable {
                subtleHaptic()
                onClick()
            }
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
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleKeepScreenOn(!keepScreenOn)
                },
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
                onCheckedChange = {
                    subtleHaptic()
                    onToggleKeepScreenOn(it)
                }
            )
        }
    }
}

@Composable
private fun ShowMiniPlayerCard(
    showMiniPlayer: Boolean,
    onToggleShowMiniPlayer: (Boolean) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleShowMiniPlayer(!showMiniPlayer)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = if (showMiniPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Show Mini Player",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Display mini player at the bottom when away from Now Playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = showMiniPlayer,
                onCheckedChange = {
                    subtleHaptic()
                    onToggleShowMiniPlayer(it)
                }
            )
        }
    }
}

@Composable
private fun ShowRemainingTimeCard(
    showRemainingTime: Boolean,
    onToggleShowRemainingTime: (Boolean) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleShowRemainingTime(!showRemainingTime)
                },
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
                onCheckedChange = {
                    subtleHaptic()
                    onToggleShowRemainingTime(it)
                }
            )
        }
    }
}

@Composable
private fun StreamingCacheCard(
    cachePlayingEpisode: Boolean,
    onToggleCachePlayingEpisode: (Boolean) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleCachePlayingEpisode(!cachePlayingEpisode)
                },
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
                onCheckedChange = {
                    subtleHaptic()
                    onToggleCachePlayingEpisode(it)
                }
            )
        }
    }
}

@Composable
private fun GlobalPlaybackSpeedCard(
    globalSpeed: Float,
    onUpdateGlobalSpeed: (Float) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    val isCustomGlobalSpeed = kotlin.math.abs(globalSpeed - 1.0f) >= 0.05f
    var lastHapticStep by remember { mutableIntStateOf((globalSpeed * 20f).toInt()) }

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
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Global Playback Speed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Default speed for all subscriptions without a custom speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = PlaybackSpeedManager.formatSpeed(globalSpeed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (isCustomGlobalSpeed) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable {
                            subtleHaptic()
                            onUpdateGlobalSpeed(1.0f)
                        }
                    ) {
                        Text(
                            text = "1.0x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = globalSpeed,
            onValueChange = { raw ->
                val step = kotlin.math.round(raw * 20f).toInt()
                if (step != lastHapticStep) {
                    lastHapticStep = step
                    subtleHaptic()
                }
                onUpdateGlobalSpeed(raw)
            },
            valueRange = PlaybackSpeedManager.MIN_SPEED..PlaybackSpeedManager.MAX_SPEED,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0.5x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "1.0x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "5.0x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val subtleHaptic = rememberSubtleHaptic()
    val activeCount = subscriptions.count { !mutedPodcastUrls.contains(it.feedUrl) }
    val totalCount = subscriptions.size

    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleNotifications(!notificationsEnabled)
                },
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
                onCheckedChange = {
                    subtleHaptic()
                    onToggleNotifications(it)
                }
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
                        onClick = {
                            subtleHaptic()
                            onSyncNow()
                        },
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
                    .clickable {
                        subtleHaptic()
                        onToggleListExpanded(!isListExpanded)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalCount == 0) "No subscriptions yet" else "$activeCount of $totalCount podcasts active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    subtleHaptic()
                    onToggleListExpanded(!isListExpanded)
                }) {
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
                            onClick = {
                                subtleHaptic()
                                onSetAllPodcastNotifications(true)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = activeCount < totalCount
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                subtleHaptic()
                                onSetAllPodcastNotifications(false)
                            },
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
                                        .clickable {
                                            subtleHaptic()
                                            onTogglePodcastNotification(podcast.feedUrl, !isNotified)
                                        }
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
                                            subtleHaptic()
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
private fun EpisodeBadgesSettingsCard(
    showUnplayedBadge: Boolean,
    onToggleUnplayedBadge: (Boolean) -> Unit,
    showDownloadedBadge: Boolean,
    onToggleDownloadedBadge: (Boolean) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Podcasts,
                contentDescription = null,
                tint = if (showUnplayedBadge || showDownloadedBadge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Subscription Episode Badges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Display episode count badges on subscribed podcasts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleUnplayedBadge(!showUnplayedBadge)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (showUnplayedBadge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (showUnplayedBadge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Podcasts,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Unplayed Episodes Badge",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show count of unplayed & unarchived episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = showUnplayedBadge,
                onCheckedChange = {
                    subtleHaptic()
                    onToggleUnplayedBadge(it)
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggleDownloadedBadge(!showDownloadedBadge)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (showDownloadedBadge) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (showDownloadedBadge) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Downloaded Episodes Badge",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show count of downloaded unplayed episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = showDownloadedBadge,
                onCheckedChange = {
                    subtleHaptic()
                    onToggleDownloadedBadge(it)
                }
            )
        }
    }
}

@Composable
private fun OpmlSubscriptionsCard(
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
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
                onClick = {
                    subtleHaptic()
                    onImport()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import OPML")
            }
            FilledTonalButton(
                onClick = {
                    subtleHaptic()
                    onExport()
                },
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
private fun GPodderSyncCard(
    vm: PodcastViewModel
) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
    val syncState by vm.gpodderSyncState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var serverTypeSelection by remember(syncState.config.serverType) {
        mutableStateOf(syncState.config.serverType)
    }
    var serverUrlInput by remember(syncState.config.serverUrl) {
        mutableStateOf(syncState.config.serverUrl)
    }
    var usernameInput by remember(syncState.config.username) {
        mutableStateOf(syncState.config.username)
    }
    var passwordInput by remember(syncState.config.password) {
        mutableStateOf(syncState.config.password)
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val isConfigured = syncState.config.isConfigured
    val selectedInterval = GPodderSyncInterval.fromMinutes(syncState.config.syncIntervalMinutes)

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Disconnect gPodder?") },
            text = {
                Text("This will remove your sync credentials from this device and stop automatic synchronization. Your local podcasts and episode history will remain on your device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        subtleHaptic()
                        showDisconnectDialog = false
                        vm.disconnectGPodder()
                        serverTypeSelection = GPodderServerType.STANDARD
                        serverUrlInput = ""
                        usernameInput = ""
                        passwordInput = ""
                        Toast.makeText(context, "gPodder disconnected", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingCard {
        // Top Header Row with Title and Sync Toggle Switch
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
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "gPodder Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sync subscriptions, playback times & status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = syncState.config.enabled,
                onCheckedChange = { enable ->
                    subtleHaptic()
                    if (enable) {
                        if (serverUrlInput.isBlank() || usernameInput.isBlank() || passwordInput.isBlank()) {
                            Toast.makeText(context, "Please enter server URL, username, and password first", Toast.LENGTH_LONG).show()
                        } else {
                            vm.saveGPodderConfig(
                                enabled = true,
                                serverType = serverTypeSelection,
                                serverUrl = serverUrlInput.trim(),
                                username = usernameInput.trim(),
                                password = passwordInput,
                                intervalMinutes = selectedInterval.minutes
                            )
                            vm.setGPodderSyncEnabled(true)
                            Toast.makeText(context, "gPodder sync enabled", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        vm.setGPodderSyncEnabled(false)
                        Toast.makeText(context, "gPodder sync disabled", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect to gpodder.net, a custom self-hosted server, or Nextcloud with the gPodder Sync app installed. Synchronizes podcast subscriptions, played/unplayed status, and exact playback positions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        // Server Service Type Selector (Standard gPodder vs Nextcloud)
        Text(
            text = "Sync Service",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GPodderServerType.entries.forEach { sType ->
                val isSelected = sType == serverTypeSelection
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        subtleHaptic()
                        serverTypeSelection = sType
                        vm.clearGPodderTestResult()
                        if (serverUrlInput.isBlank() || serverUrlInput == "https://gpodder.net" || serverUrlInput == "https://cloud.example.com") {
                            serverUrlInput = sType.defaultPlaceholder
                        }
                    },
                    label = {
                        Text(sType.label)
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = serverTypeSelection.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(14.dp))

        // Server URL Field
        OutlinedTextField(
            value = serverUrlInput,
            onValueChange = {
                serverUrlInput = it
                vm.clearGPodderTestResult()
            },
            label = {
                Text(
                    if (serverTypeSelection == GPodderServerType.NEXTCLOUD) {
                        "Nextcloud URL (HTTP or HTTPS)"
                    } else {
                        "Server URL (HTTP or HTTPS)"
                    }
                )
            },
            placeholder = { Text(serverTypeSelection.defaultPlaceholder) },
            supportingText = {
                Text(
                    if (serverTypeSelection == GPodderServerType.NEXTCLOUD) {
                        "e.g. https://cloud.example.com or with /index.php/apps/gpoddersync"
                    } else {
                        "e.g. https://gpodder.net or http://your-server:8080"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (serverUrlInput.isNotBlank()) {
                        IconButton(onClick = {
                            subtleHaptic()
                            serverUrlInput = ""
                            vm.clearGPodderTestResult()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear URL")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                subtleHaptic()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    serverUrlInput = clipText
                                    vm.clearGPodderTestResult()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        )

        Spacer(Modifier.height(10.dp))

        // Username Field
        OutlinedTextField(
            value = usernameInput,
            onValueChange = {
                usernameInput = it
                vm.clearGPodderTestResult()
            },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (usernameInput.isNotBlank()) {
                    IconButton(onClick = {
                        subtleHaptic()
                        usernameInput = ""
                        vm.clearGPodderTestResult()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear username")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
        )

        Spacer(Modifier.height(10.dp))

        // Password Field
        OutlinedTextField(
            value = passwordInput,
            onValueChange = {
                passwordInput = it
                vm.clearGPodderTestResult()
            },
            label = {
                Text(
                    if (serverTypeSelection == GPodderServerType.NEXTCLOUD) {
                        "Password or App Password"
                    } else {
                        "Password"
                    }
                )
            },
            supportingText = if (serverTypeSelection == GPodderServerType.NEXTCLOUD) {
                { Text("Nextcloud app passwords (created in Settings > Security) are supported") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                IconButton(onClick = {
                    subtleHaptic()
                    passwordVisible = !passwordVisible
                }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
        )

        Spacer(Modifier.height(14.dp))

        // Test Connection & Save Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    subtleHaptic()
                    keyboardController?.hide()
                    coroutineScope.launch {
                        val trimmedUrl = serverUrlInput.trim()
                        val trimmedUser = usernameInput.trim()
                        if (trimmedUrl.isBlank() || trimmedUser.isBlank() || passwordInput.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        // Save current inputs
                        vm.saveGPodderConfig(
                            enabled = syncState.config.enabled,
                            serverType = serverTypeSelection,
                            serverUrl = trimmedUrl,
                            username = trimmedUser,
                            password = passwordInput,
                            intervalMinutes = selectedInterval.minutes
                        )

                        val result = vm.testGPodderConnection(
                            serverType = serverTypeSelection,
                            serverUrl = trimmedUrl,
                            username = trimmedUser,
                            password = passwordInput
                        )
                        when (result) {
                            is GPodderConnectionTestResult.Success -> {
                                Toast.makeText(context, "Connection test passed!", Toast.LENGTH_SHORT).show()
                            }
                            is GPodderConnectionTestResult.Error -> {
                                Toast.makeText(context, "Test failed: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !syncState.isTestingConnection && serverUrlInput.isNotBlank() && usernameInput.isNotBlank() && passwordInput.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (syncState.isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Testing...")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Connection")
                }
            }

            FilledTonalButton(
                onClick = {
                    subtleHaptic()
                    keyboardController?.hide()
                    val trimmedUrl = serverUrlInput.trim()
                    val trimmedUser = usernameInput.trim()
                    if (trimmedUrl.isBlank() || trimmedUser.isBlank() || passwordInput.isBlank()) {
                        Toast.makeText(context, "Please enter server URL, username, and password", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.saveGPodderConfig(
                            enabled = syncState.config.enabled,
                            serverType = serverTypeSelection,
                            serverUrl = trimmedUrl,
                            username = trimmedUser,
                            password = passwordInput,
                            intervalMinutes = selectedInterval.minutes
                        )
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(0.7f)
            ) {
                Text("Save")
            }
        }

        // Test Result Banner
        syncState.testResult?.let { testResult ->
            Spacer(Modifier.height(12.dp))
            val isSuccess = testResult is GPodderConnectionTestResult.Success
            val bgColor = if (isSuccess) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            }
            val contentColor = if (isSuccess) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
            val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error
            val message = when (testResult) {
                is GPodderConnectionTestResult.Success -> {
                    if (testResult.remoteSubscriptionCount != null) {
                        "${testResult.message} (${testResult.remoteSubscriptionCount} remote subscriptions)"
                    } else {
                        testResult.message
                    }
                }
                is GPodderConnectionTestResult.Error -> testResult.message
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = bgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            subtleHaptic()
                            vm.clearGPodderTestResult()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))

        // Duration / Frequency Setting Section: 15 minutes, 30 minutes, 1 hour, 12 hours, 24 hours
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Sync Frequency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedInterval.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GPodderSyncInterval.entries.forEach { interval ->
                val isSelected = interval == selectedInterval
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        subtleHaptic()
                        vm.updateGPodderSyncInterval(interval.minutes)
                    },
                    label = {
                        Text(interval.chipLabel)
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))

        // Last Sync Status & Manual Sync Now Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sync Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (syncState.lastSyncTimestampMs > 0L) {
                    val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                        syncState.lastSyncTimestampMs,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    )
                    Text(
                        text = "Last synced: $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!syncState.lastSyncMessage.isNullOrBlank()) {
                        Text(
                            text = syncState.lastSyncMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (syncState.lastSyncSuccess == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = if (isConfigured) "Never synced yet" else "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    subtleHaptic()
                    vm.syncGPodderNow { res ->
                        when (res) {
                            is GPodderSyncResult.Success -> {
                                Toast.makeText(context, "Sync complete: ${res.message}", Toast.LENGTH_SHORT).show()
                            }
                            is GPodderSyncResult.Error -> {
                                Toast.makeText(context, "Sync failed: ${res.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = isConfigured && !syncState.isSyncing
            ) {
                if (syncState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sync Now")
                }
            }
        }

        // Disconnect Account Option (if configured)
        if (isConfigured) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    subtleHaptic()
                    showDisconnectDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Disconnect gPodder Account")
            }
        }
    }
}

@Composable
private fun AutoArchiveCard(
    autoArchiveEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    subtleHaptic()
                    onToggle(!autoArchiveEnabled)
                },
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
                onCheckedChange = {
                    subtleHaptic()
                    onToggle(it)
                }
            )
        }
    }
}

@Composable
private fun DownloadsSettingsCard(
    downloadCount: Int,
    onRemoveAllDownloads: () -> Unit
) {
    val subtleHaptic = rememberSubtleHaptic()
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
                        subtleHaptic()
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
                TextButton(onClick = {
                    subtleHaptic()
                    showConfirmDialog = false
                }) {
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
                onClick = {
                    subtleHaptic()
                    showConfirmDialog = true
                },
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
private fun LocalAudioFilesSettingsSection(
    localFiles: List<LocalAudioFile>,
    subscriptionTitle: String,
    isImporting: Boolean,
    currentPlayingUrl: String?,
    isPlaying: Boolean,
    onUpdateSubscriptionTitle: (String) -> Unit,
    onAddAudioFiles: () -> Unit,
    onRenameAudioFile: (String, String) -> Unit,
    onDeleteAudioFile: (String) -> Unit,
    onDeleteAllAudioFiles: () -> Unit,
    onPlayAudioFile: (LocalAudioFile) -> Unit,
    onOpenInSubscriptions: () -> Unit
) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
    val keyboardController = LocalSoftwareKeyboardController.current

    var titleText by remember(subscriptionTitle) { mutableStateOf(subscriptionTitle) }
    var fileToRename by remember { mutableStateOf<LocalAudioFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<LocalAudioFile?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Ensure any un-saved non-blank title edits are persisted if the user navigates back
    val currentTitleText by rememberUpdatedState(titleText)
    val currentSavedTitle by rememberUpdatedState(subscriptionTitle)
    val currentOnUpdateTitle by rememberUpdatedState(onUpdateSubscriptionTitle)
    DisposableEffect(Unit) {
        onDispose {
            val trimmed = currentTitleText.trim()
            if (trimmed.isNotBlank() && trimmed != currentSavedTitle) {
                currentOnUpdateTitle(trimmed)
            }
        }
    }

    // Rename Dialog
    fileToRename?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Rename Audio File", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a new title for this audio file:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Audio Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (renameText.isNotEmpty()) {
                                IconButton(onClick = {
                                    subtleHaptic()
                                    renameText = ""
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val cleaned = renameText.trim()
                                if (cleaned.isNotBlank()) {
                                    onRenameAudioFile(targetFile.id, cleaned)
                                    Toast.makeText(context, "Renamed to \"$cleaned\"", Toast.LENGTH_SHORT).show()
                                    fileToRename = null
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        subtleHaptic()
                        val cleaned = renameText.trim()
                        if (cleaned.isNotBlank()) {
                            onRenameAudioFile(targetFile.id, cleaned)
                            Toast.makeText(context, "Renamed to \"$cleaned\"", Toast.LENGTH_SHORT).show()
                            fileToRename = null
                        }
                    },
                    enabled = renameText.trim().isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    subtleHaptic()
                    fileToRename = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single File Delete Confirmation Dialog
    fileToDelete?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Delete Audio File?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Remove \"${targetFile.title}\" from ExpressoPod?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        subtleHaptic()
                        val deletedTitle = targetFile.title
                        onDeleteAudioFile(targetFile.id)
                        fileToDelete = null
                        Toast.makeText(context, "Deleted \"$deletedTitle\"", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    subtleHaptic()
                    fileToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Delete All Local Audio Files?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This will remove all ${localFiles.size} imported audio file(s) from ExpressoPod and remove the collection from Subscriptions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        subtleHaptic()
                        showDeleteAllConfirm = false
                        onDeleteAllAudioFiles()
                        Toast.makeText(context, "All local audio files deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    subtleHaptic()
                    showDeleteAllConfirm = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 1. Subscription Title Customization Card
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Subscriptions,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Title in Subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize the title that shows for these files on the Subscriptions screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = titleText,
            onValueChange = { titleText = it },
            label = { Text("Subscriptions Title") },
            placeholder = { Text(LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            },
            trailingIcon = {
                if (titleText.isNotEmpty()) {
                    IconButton(onClick = {
                        subtleHaptic()
                        titleText = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear title")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val finalTitle = titleText.trim().ifBlank { LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE }
                    titleText = finalTitle
                    onUpdateSubscriptionTitle(finalTitle)
                    keyboardController?.hide()
                    Toast.makeText(context, "Subscriptions title set to \"$finalTitle\"", Toast.LENGTH_SHORT).show()
                }
            )
        )

        Spacer(Modifier.height(10.dp))

        val isTitleModified = titleText.trim() != subscriptionTitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    subtleHaptic()
                    val finalTitle = titleText.trim().ifBlank { LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE }
                    titleText = finalTitle
                    onUpdateSubscriptionTitle(finalTitle)
                    keyboardController?.hide()
                    Toast.makeText(context, "Subscriptions title set to \"$finalTitle\"", Toast.LENGTH_SHORT).show()
                },
                enabled = isTitleModified || titleText.isBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Title")
            }

            if (subscriptionTitle != LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE) {
                OutlinedButton(
                    onClick = {
                        subtleHaptic()
                        titleText = LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE
                        onUpdateSubscriptionTitle(LocalAudioManager.DEFAULT_SUBSCRIPTION_TITLE)
                        keyboardController?.hide()
                        Toast.makeText(context, "Reset to default title", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Reset")
                }
            }
        }
    }

    // 2. Add Audio Files Card
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Import Audio Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add audio files downloaded elsewhere on your device to play in Subscriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                subtleHaptic()
                onAddAudioFiles()
            },
            enabled = !isImporting,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Importing Audio Files...")
            } else {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Audio Files", fontWeight = FontWeight.Bold)
            }
        }

        if (localFiles.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = {
                    subtleHaptic()
                    onOpenInSubscriptions()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Subscriptions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open \"$subscriptionTitle\" in Subscriptions")
            }
        }
    }

    // 3. Added Audio Files List (Rename / Delete / Play)
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
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (localFiles.isNotEmpty()) "Added Audio Files (${localFiles.size})" else "Added Audio Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (localFiles.isNotEmpty()) {
                            "Rename or delete your imported audio files"
                        } else {
                            "Files you add will appear here and in Subscriptions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (localFiles.size > 1) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        subtleHaptic()
                        showDeleteAllConfirm = true
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Delete All")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (localFiles.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No audio files added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap 'Add Audio Files' above to import audio files from your device. Once added, they will appear automatically in your Subscriptions screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                localFiles.forEach { file ->
                    val isThisCurrent = currentPlayingUrl == file.audioUrl
                    val isThisPlaying = isThisCurrent && isPlaying
                    val metaParts = buildList {
                        if (file.durationText.isNotBlank()) add(formatDurationText(file.durationText))
                        if (file.sizeBytes > 0L) add(LocalAudioManager.formatBytes(file.sizeBytes))
                        if (file.pubDate.isNotBlank()) add(file.pubDate)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isThisCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        },
                        border = if (isThisCurrent) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    subtleHaptic()
                                    onPlayAudioFile(file)
                                },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isThisPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (metaParts.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = metaParts.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val strippedOriginal = file.originalFileName.substringBeforeLast('.')
                                if (file.originalFileName.isNotBlank() && !strippedOriginal.equals(file.title, ignoreCase = true)) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "File: ${file.originalFileName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    subtleHaptic()
                                    renameText = file.title
                                    fileToRename = file
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename ${file.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    subtleHaptic()
                                    fileToDelete = file
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${file.title}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
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
            FilledTonalIconButton(onClick = {
                subtleHaptic()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "About ExpressoPod",
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
    val subtleHaptic = rememberSubtleHaptic()
    val isDarkBg = (MaterialTheme.colorScheme.background.red * 0.299f +
        MaterialTheme.colorScheme.background.green * 0.587f +
        MaterialTheme.colorScheme.background.blue * 0.114f) < 0.5f

    val playedBaseColor = if (isDarkBg) {
        lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.background, 0.35f)
    } else {
        lerp(MaterialTheme.colorScheme.surfaceDim, MaterialTheme.colorScheme.outlineVariant, 0.25f)
    }

    val baseColor = when {
        isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isPlayed -> playedBaseColor
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
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
        border = when {
            isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            isPlayed && !isCurrentlyPlaying -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = {
                    subtleHaptic()
                    onPlay()
                },
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
                if (LocalAudioManager.isLocalPodcast(podcast)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.AudioFile,
                                contentDescription = "Local audio file",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (downloadSize != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = downloadSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                } else if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable {
                                subtleHaptic()
                                onCancelDownload()
                            },
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
                        onClick = {
                            subtleHaptic()
                            onDeleteDownload()
                        },
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
                        onClick = {
                            subtleHaptic()
                            onDownload()
                        },
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
                    onClick = {
                        subtleHaptic()
                        onAddToUpNext()
                    },
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
                    onClick = {
                        subtleHaptic()
                        onTogglePlayed()
                    },
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
                        onClick = {
                            subtleHaptic()
                            onToggleArchive()
                        },
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
                            onClick = {
                                subtleHaptic()
                                onToggleKeepUnarchived()
                            },
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
                            onClick = {
                                subtleHaptic()
                                onToggleKeepUnarchived()
                            },
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
    allEpisodes: List<Episode> = episodes,
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
    onUnarchiveAll: (List<Episode>) -> Unit = {},
    onOpenLocalAudioSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()
    var expanded by remember { mutableStateOf(false) }

    val hasSelected = selectedEpisodeUrls.isNotEmpty()
    val candidateEpisodes = if (hasSelected) {
        episodes.filter { selectedEpisodeUrls.contains(it.audioUrl) }
    } else {
        episodes
    }
    val downloadCandidates = if (hasSelected) {
        candidateEpisodes
    } else if (filterMode == 2 && allEpisodes.isNotEmpty()) {
        allEpisodes
    } else {
        candidateEpisodes
    }

    val selectedArchivedCount = candidateEpisodes.count { archivedEpisodes.contains(it.audioUrl) }
    val selectedUnarchivedCount = candidateEpisodes.size - selectedArchivedCount

    val isUnarchive = if (hasSelected) {
        selectedArchivedCount > 0 && (selectedUnarchivedCount == 0 || showArchived || filterMode == 3)
    } else {
        showArchived || filterMode == 3
    }

    Box {
        IconButton(onClick = {
            subtleHaptic()
            expanded = true
        }) {
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
                        subtleHaptic()
                        expanded = false
                        onSelectAll()
                    }
                )
            }
            if (LocalAudioManager.isLocalPodcast(podcast)) {
                DropdownMenuItem(
                    text = { Text("Manage Local Audio Files") },
                    leadingIcon = {
                        Icon(Icons.Default.AudioFile, contentDescription = null)
                    },
                    onClick = {
                        subtleHaptic()
                        expanded = false
                        onClearSelection()
                        onOpenLocalAudioSettings()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(if (hasSelected) "Download Selected" else "Download All") },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    },
                    onClick = {
                        subtleHaptic()
                        expanded = false
                        val toDownload = downloadCandidates.filter { ep ->
                            val cleanUrl = ep.audioUrl.trim()
                            !downloadedEpisodes.any { it.audioUrl == cleanUrl && java.io.File(it.localPath).exists() } && !activeDownloads.containsKey(cleanUrl)
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
            }
            if (isUnarchive) {
                DropdownMenuItem(
                    text = { Text(if (hasSelected) "Unarchive Selected" else "Unarchive All") },
                    leadingIcon = {
                        Icon(Icons.Default.Unarchive, contentDescription = null)
                    },
                    onClick = {
                        subtleHaptic()
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
                        subtleHaptic()
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
    onEpisodeSelected: (Podcast, Episode) -> Unit,
    onOpenLocalAudioSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val subtleHaptic = rememberSubtleHaptic()
    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
    val playedEpisodes by vm.playedEpisodes.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val localAudioFiles by vm.localAudioFiles.collectAsState()
    val activeDownloads by vm.activeDownloads.collectAsState()
    val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val subscriptions by vm.subscriptions.collectAsState()

    val localAudioSet = remember(localAudioFiles) { localAudioFiles.map { it.audioUrl }.toSet() }

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

    val uniqueEpisodes = remember(episodes) {
        episodes.filter { it.audioUrl.isNotBlank() }.distinctBy { it.audioUrl }
    }
    val displayedEpisodes = when (filterMode) {
        1 -> uniqueEpisodes.filter { ep -> !playedEpisodes.contains(ep.audioUrl) && !archivedEpisodes.contains(ep.audioUrl) }
        2 -> uniqueEpisodes.filter { ep -> downloadedEpisodes.any { it.audioUrl == ep.audioUrl } || localAudioSet.contains(ep.audioUrl) }
        3 -> uniqueEpisodes.filter { ep -> archivedEpisodes.contains(ep.audioUrl) }
        else -> if (showArchived) uniqueEpisodes else uniqueEpisodes.filter { ep -> !archivedEpisodes.contains(ep.audioUrl) }
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
                IconButton(onClick = {
                    subtleHaptic()
                    selectedEpisodeUrls = emptySet()
                }) {
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
                FilledTonalIconButton(onClick = {
                    subtleHaptic()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                if (LocalAudioManager.isLocalPodcast(podcast)) {
                    IconButton(onClick = {
                        subtleHaptic()
                        onOpenLocalAudioSettings()
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Manage Local Audio Files",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    val isSubscribed = subscriptions.any { it.feedUrl == podcast.feedUrl }
                    IconButton(onClick = {
                        subtleHaptic()
                        vm.toggleSubscription(podcast)
                    }) {
                        Icon(
                            if (isSubscribed) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            PodcastOptionsMenu(
                podcast = podcast,
                episodes = displayedEpisodes,
                allEpisodes = if (showArchived) uniqueEpisodes else uniqueEpisodes.filter { ep -> !archivedEpisodes.contains(ep.audioUrl) },
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
                onUnarchiveAll = { vm.unarchiveAllEpisodes(it) },
                onOpenLocalAudioSettings = onOpenLocalAudioSettings
            )
        }

        val detailConfig = LocalConfiguration.current
        val isCompactDetail = detailConfig.screenHeightDp < 840 || detailConfig.screenWidthDp < 370

        val (savedIndex, savedOffset) = remember(podcast.feedUrl) {
            vm.getEpisodeListPosition(podcast.feedUrl)
        }
        val episodeListState = rememberSaveable(podcast.feedUrl, saver = LazyListState.Saver) {
            LazyListState(savedIndex, savedOffset)
        }

        val isScrolled by remember {
            derivedStateOf {
                episodeListState.firstVisibleItemIndex > 0 || episodeListState.firstVisibleItemScrollOffset > 30
            }
        }
        val shouldShrinkHeader = selectedTab == 0 && isScrolled

        val maxArtSize = if (isCompactDetail) 116.dp else 140.dp
        val minArtSize = if (isCompactDetail) 56.dp else 66.dp
        val animatedArtSize by animateDpAsState(
            targetValue = if (shouldShrinkHeader) minArtSize else maxArtSize,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "headerArtSize"
        )
        val animatedCorner by animateDpAsState(
            targetValue = if (shouldShrinkHeader) 14.dp else (if (isCompactDetail) 22.dp else 26.dp),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "headerCorner"
        )
        val animatedTitleSize by animateFloatAsState(
            targetValue = if (shouldShrinkHeader) 15f else (if (isCompactDetail) 18f else 22f),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "headerTitleSize"
        )
        val animatedSpacerHeight by animateDpAsState(
            targetValue = if (shouldShrinkHeader) 4.dp else (if (isCompactDetail) 6.dp else 10.dp),
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "headerSpacerHeight"
        )

        // Centered Podcast Artwork & Title Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = if (shouldShrinkHeader) 4.dp else (if (isCompactDetail) 6.dp else 10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(animatedCorner),
                shadowElevation = if (shouldShrinkHeader) 2.dp else 4.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.size(animatedArtSize)
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

            Spacer(modifier = Modifier.height(animatedSpacerHeight))

            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = animatedTitleSize.sp,
                    lineHeight = (animatedTitleSize * 1.25f).sp
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (shouldShrinkHeader) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            AnimatedVisibility(visible = !shouldShrinkHeader && podcast.author.isNotBlank() && podcast.author != "Unknown") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                onClick = {
                    subtleHaptic()
                    selectedTab = 0
                },
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
                onClick = {
                    subtleHaptic()
                    selectedTab = 1
                },
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
                    val downloadedCount = episodes.count { ep -> downloadedSet.contains(ep.audioUrl) || localAudioSet.contains(ep.audioUrl) }
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
                            onClick = {
                                subtleHaptic()
                                filterMode = 0
                            },
                            label = { Text(if (showArchived) "All (${episodes.size})" else "All ($unarchivedCount)") },
                            leadingIcon = if (filterMode == 0) {
                                { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = filterMode == 1,
                            onClick = {
                                subtleHaptic()
                                filterMode = 1
                            },
                            label = { Text("Unplayed ($unplayedCount)") },
                            leadingIcon = if (filterMode == 1) {
                                { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = filterMode == 2,
                            onClick = {
                                subtleHaptic()
                                filterMode = 2
                            },
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
                            onClick = {
                                subtleHaptic()
                                filterMode = 3
                            },
                            label = { Text("Archived ($archivedCount)") },
                            leadingIcon = if (filterMode == 3) {
                                { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = showArchived,
                            onClick = {
                                subtleHaptic()
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
                                val isInUpNext = isCurrent || vm.upNextQueue.any { it.episode.audioUrl == ep.audioUrl }

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
                                                subtleHaptic()
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
                        if (LocalAudioManager.isLocalPodcast(podcast)) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "Local Audio Collection",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Add new files, rename or delete existing files, or customize the title shown in Subscriptions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            subtleHaptic()
                                            onOpenLocalAudioSettings()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AudioFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Manage Local Audio Files")
                                    }
                                }
                            }
                        } else {
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
                                                subtleHaptic()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("RSS Feed URL", podcast.feedUrl))
                                                Toast.makeText(context, "RSS feed URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy RSS Feed URL", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    val authenticatedFeeds by vm.authenticatedFeedUrls.collectAsState()
                                    val normalizedFeedKey = remember(podcast.feedUrl) { FeedCredentialsManager.normalizeUrlKey(podcast.feedUrl) }
                                    val hasCredentials = authenticatedFeeds.contains(normalizedFeedKey)
                                    val savedCreds = remember(podcast.feedUrl, authenticatedFeeds) {
                                        FeedCredentialsManager.getCredentials(podcast.feedUrl)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                                            .clickable {
                                                subtleHaptic()
                                                vm.openEditFeedCredentialsDialog(podcast)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                if (hasCredentials) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (hasCredentials) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    "Feed Authentication",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    if (hasCredentials && !savedCreds?.username.isNullOrBlank()) {
                                                        "Authenticated as ${savedCreds?.username} (Tap to edit)"
                                                    } else if (hasCredentials) {
                                                        "Password configured (Tap to edit)"
                                                    } else {
                                                        "No login configured (Tap to set username/password)"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (hasCredentials) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Feed Credentials",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val isSubscribed = subscriptions.any { it.feedUrl == podcast.feedUrl }
                                    FilledTonalButton(
                                        onClick = {
                                            subtleHaptic()
                                            vm.toggleSubscription(podcast)
                                        },
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
                                                .clickable {
                                                    subtleHaptic()
                                                    vm.togglePodcastNotification(podcast.feedUrl, !isNotified)
                                                }
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
                                                onCheckedChange = { checked ->
                                                    subtleHaptic()
                                                    vm.togglePodcastNotification(podcast.feedUrl, checked)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(vm: PodcastViewModel, onEpisodeSelected: (Podcast, Episode) -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val subtleHaptic = rememberSubtleHaptic()

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
                placeholder = { Text("Search podcasts or paste RSS URL...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        subtleHaptic()
                        keyboardController?.hide()
                        vm.discoverSelectedPodcast = null
                        vm.search()
                    },
                    onSearch = {
                        subtleHaptic()
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
                            IconButton(onClick = {
                                subtleHaptic()
                                vm.clearSearch()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                subtleHaptic()
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                AssistChip(
                    onClick = {
                        subtleHaptic()
                        val prefill = if (SecurityUtils.looksLikeFeedUrl(vm.searchQuery)) vm.searchQuery else ""
                        vm.openAddCustomFeedDialog(initialUrl = prefill)
                    },
                    label = { Text("Add Custom / Private RSS Feed") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    itemsIndexed(
                        items = vm.podcasts,
                        key = { index, podcast -> "${podcast.feedUrl}_$index" }
                    ) { _, podcast ->
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    subtleHaptic()
                                    vm.loadDiscoverPodcastEpisodes(podcast)
                                }
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
                // Suggested Podcasts Grid
                val subscriptions by vm.subscriptions.collectAsState()
                val subscribedUrls = remember(subscriptions) { subscriptions.map { it.feedUrl }.toSet() }

                LaunchedEffect(Unit) {
                    if (!vm.hasLoadedOnlineSuggestions && !vm.isLoadingSuggestions) {
                        vm.loadSuggestedPodcasts()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Suggested Podcasts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = vm.isLoadingSuggestions,
                    onRefresh = {
                        subtleHaptic()
                        vm.refreshSuggestedPodcasts()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = vm.suggestedPodcasts,
                            key = { it.feedUrl }
                        ) { podcast ->
                            val isSubscribed = subscribedUrls.contains(podcast.feedUrl)
                            Surface(
                                onClick = {
                                    subtleHaptic()
                                    vm.loadDiscoverPodcastEpisodes(podcast)
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = podcast.artworkUrl,
                                            contentDescription = podcast.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSubscribed) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = "Subscribed",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = podcast.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = podcast.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
fun ExpressivePlayerScreen(
    vm: PodcastViewModel,
    rewindSec: Int,
    forwardSec: Int,
    onOpenPresets: () -> Unit
) {
    val episode = vm.currentEpisode
    val podcast = vm.currentPodcast
    val context = LocalContext.current
    val subtleHaptic = rememberSubtleHaptic()

    val selectedTab = vm.nowPlayingTabIndex // 0 = Player, 1 = Chapters, 2 = Notes

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
    val isCompactHeight = playerScreenHeight < 860

    val playerHorizPadding = if (playerScreenWidth < 360) 12.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = playerHorizPadding,
                end = playerHorizPadding,
                top = if (isCompactHeight) 4.dp else 8.dp,
                bottom = if (isCompactHeight) 4.dp else 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expressive Top Tab Navigation: Player vs Chapters vs Notes (Slimmed)
        val tabBarHeight = if (isCompactHeight) 38.dp else 44.dp
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
                onClick = {
                    subtleHaptic()
                    vm.nowPlayingTabIndex = 0
                },
                modifier = Modifier.height(tabBarHeight)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
                onClick = {
                    subtleHaptic()
                    vm.nowPlayingTabIndex = 1
                },
                modifier = Modifier.height(tabBarHeight)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
                onClick = {
                    subtleHaptic()
                    vm.nowPlayingTabIndex = 2
                },
                modifier = Modifier.height(tabBarHeight)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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

        Spacer(Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

        if (selectedTab == 0) {
            // ================= PLAYER TAB =================
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val availH = maxHeight
                val availW = maxWidth
                val hasChapters = vm.currentChapters.isNotEmpty() && vm.currentChapter != null

                // Dynamically compute component sizes from exact available height so all controls fit without scrolling
                val reservedForControlsAndText = if (hasChapters) 325.dp else 290.dp
                val computedArtSize = (availH - reservedForControlsAndText).coerceIn(88.dp, 240.dp)
                val artworkSize = minOf(computedArtSize, availW * 0.65f)

                val isTight = availH < 560.dp || availW < 370.dp
                val isVeryTight = availH < 490.dp || availW < 340.dp

                val skipBtnSize = when {
                    isVeryTight -> 52.dp
                    isTight -> 58.dp
                    else -> 66.dp
                }

                val playBtnHeight = when {
                    isVeryTight -> 56.dp
                    isTight -> 62.dp
                    else -> 70.dp
                }

                val dynamicPlayWidth = when {
                    isVeryTight -> if (vm.isPlaying) 96.dp else 78.dp
                    isTight -> if (vm.isPlaying) 110.dp else 86.dp
                    else -> playWidth
                }

                val buttonSpacing = when {
                    availW < 340.dp -> 10.dp
                    availW < 380.dp -> 14.dp
                    else -> 20.dp
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Expressive Squircle Artwork Container
                    Surface(
                        shape = RoundedCornerShape(if (artworkSize < 160.dp) 22.dp else 30.dp),
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
                                    modifier = Modifier.size((artworkSize * 0.35f).coerceAtLeast(40.dp)),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Episode Title & Podcast Subtitle
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = episode?.title ?: "Select an Episode",
                            style = if (isTight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = podcast?.title ?: "Use Discover to search podcasts",
                            style = if (isTight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
                        val activeDownloads by vm.activeDownloads.collectAsState()
                        val isDownloaded = episode?.let { ep -> downloadedEpisodes.any { it.audioUrl == ep.audioUrl } } ?: false
                        val isDownloading = episode?.let { ep -> activeDownloads.containsKey(ep.audioUrl) } ?: false
                        val downloadProgress = episode?.let { ep -> activeDownloads[ep.audioUrl] }

                        Spacer(Modifier.height(if (isTight) 4.dp else 6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (podcast != null && LocalAudioManager.isLocalPodcast(podcast)) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.AudioFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        val size = episode?.let { vm.getFormattedDownloadSize(it.audioUrl) }
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            if (size != null) "Local File • $size" else "Local File",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else if (isDownloaded) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        subtleHaptic()
                                        episode?.let { vm.deleteDownload(it.audioUrl) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.OfflinePin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        val size = episode?.let { vm.getFormattedDownloadSize(it.audioUrl) }
                                        Spacer(Modifier.width(5.dp))
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
                                    onClick = {
                                        subtleHaptic()
                                        episode?.let { vm.cancelDownload(it.audioUrl) }
                                    },
                                    label = {
                                        val pct = ((downloadProgress ?: 0f) * 100).toInt()
                                        Text("Downloading $pct%")
                                    },
                                    leadingIcon = {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(30.dp)
                                )
                            } else if (episode != null && podcast != null) {
                                AssistChip(
                                    onClick = {
                                        subtleHaptic()
                                        vm.downloadEpisode(podcast, episode)
                                    },
                                    label = { Text("Download") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(30.dp)
                                )
                            }

                            val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
                            if (episode != null) {
                                val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
                                val isPinned = keepUnarchivedEpisodes.contains(episode.audioUrl)
                                if (autoArchiveEnabled || isPinned) {
                                    AssistChip(
                                        onClick = {
                                            subtleHaptic()
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
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(30.dp)
                                    )
                                } else {
                                    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
                                    val isArchived = archivedEpisodes.contains(episode.audioUrl)
                                    AssistChip(
                                        onClick = {
                                            subtleHaptic()
                                            vm.toggleArchive(episode, !isArchived)
                                        },
                                        label = { Text(if (isArchived) "Archived" else "Archive") },
                                        leadingIcon = {
                                            Icon(
                                                if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(30.dp)
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
                                    .clickable {
                                        subtleHaptic()
                                        vm.nowPlayingTabIndex = 1
                                    }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = "Chapter",
                                        modifier = Modifier.size(15.dp),
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
                            onValueChange = { vm.seekTo(it) },
                            onValueChangeFinished = { subtleHaptic() },
                            modifier = Modifier.height(if (isTight) 28.dp else 36.dp)
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
                                    subtleHaptic()
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
                            onClick = {
                                subtleHaptic()
                                vm.skipRewind()
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(skipBtnSize),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Replay,
                                    contentDescription = "Rewind",
                                    modifier = Modifier.size(if (isTight) 20.dp else 24.dp)
                                )
                                Text(
                                    "-${rewindSec}s",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isTight) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineBreak = LineBreak.Heading,
                                        hyphens = Hyphens.None
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                subtleHaptic()
                                vm.togglePlayPause()
                            },
                            shape = RoundedCornerShape(playCorner),
                            modifier = Modifier.size(width = dynamicPlayWidth, height = playBtnHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = playColor,
                                contentColor = playContentColor
                            )
                        ) {
                            Icon(
                                imageVector = if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(if (isTight) 30.dp else 36.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                subtleHaptic()
                                vm.skipForward()
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(skipBtnSize),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Forward30,
                                    contentDescription = "Fast Forward",
                                    modifier = Modifier.size(if (isTight) 20.dp else 24.dp)
                                )
                                Text(
                                    "+${forwardSec}s",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isTight) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineBreak = LineBreak.Heading,
                                        hyphens = Hyphens.None
                                    )
                                )
                            }
                        }
                    }

                    // Quick Action Chips: Volume Boost & Playback Speed
                    val volumeBoost by vm.volumeBoostEnabled.collectAsState()
                    val currentSpeed = vm.currentPlaybackSpeed
                    val isCustomSpeed = kotlin.math.abs(currentSpeed - 1.0f) >= 0.05f
                    var showSpeedPopup by remember { mutableStateOf(false) }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = volumeBoost,
                            onClick = {
                                subtleHaptic()
                                vm.setVolumeBoostEnabled(!volumeBoost)
                            },
                            label = { Text("Volume Boost") },
                            leadingIcon = {
                                Icon(
                                    if (volumeBoost) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(30.dp)
                        )

                        Box {
                            FilterChip(
                                selected = isCustomSpeed || showSpeedPopup,
                                onClick = {
                                    subtleHaptic()
                                    showSpeedPopup = !showSpeedPopup
                                },
                                label = { Text("Speed • ${PlaybackSpeedManager.formatSpeed(currentSpeed)}") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = "Playback Speed",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(30.dp)
                            )

                            if (showSpeedPopup) {
                                val density = LocalDensity.current
                                val verticalMarginPx = with(density) { 8.dp.roundToPx() }
                                val horizontalMarginPx = with(density) { 12.dp.roundToPx() }
                                val popupPositionProvider = remember(verticalMarginPx, horizontalMarginPx) {
                                    object : PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: IntRect,
                                            windowSize: IntSize,
                                            layoutDirection: LayoutDirection,
                                            popupContentSize: IntSize
                                        ): IntOffset {
                                            val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                                                .coerceIn(
                                                    horizontalMarginPx,
                                                    (windowSize.width - popupContentSize.width - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
                                                )
                                            val aboveY = anchorBounds.top - popupContentSize.height - verticalMarginPx
                                            val y = if (aboveY >= verticalMarginPx) {
                                                aboveY
                                            } else {
                                                anchorBounds.bottom + verticalMarginPx
                                            }
                                            return IntOffset(x, y)
                                        }
                                    }
                                }

                                Popup(
                                    popupPositionProvider = popupPositionProvider,
                                    onDismissRequest = { showSpeedPopup = false },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        tonalElevation = 8.dp,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier.width(248.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Speed,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = PlaybackSpeedManager.formatSpeed(currentSpeed),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isCustomSpeed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                                    modifier = Modifier.clickable(enabled = isCustomSpeed) {
                                                        subtleHaptic()
                                                        vm.setPlaybackSpeedForCurrentPodcast(1.0f)
                                                    }
                                                ) {
                                                    Text(
                                                        text = "1.0x",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCustomSpeed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            var lastSpeedStep by remember { mutableIntStateOf(kotlin.math.round(currentSpeed * 20f).toInt()) }
                                            Slider(
                                                value = currentSpeed,
                                                onValueChange = { raw ->
                                                    val step = kotlin.math.round(raw * 20f).toInt()
                                                    if (step != lastSpeedStep) {
                                                        lastSpeedStep = step
                                                        subtleHaptic()
                                                    }
                                                    vm.setPlaybackSpeedForCurrentPodcast(raw)
                                                },
                                                valueRange = PlaybackSpeedManager.MIN_SPEED..PlaybackSpeedManager.MAX_SPEED,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(28.dp)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "0.5x",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = if (podcast != null) podcast.title else "Default",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 6.dp)
                                                )
                                                Text(
                                                    text = "5.0x",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        onClick = {
                                            subtleHaptic()
                                            vm.skipToPreviousChapter()
                                        },
                                        enabled = vm.currentChapterIndex > 0
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Chapter")
                                    }
                                    IconButton(
                                        onClick = {
                                            subtleHaptic()
                                            vm.skipToNextChapter()
                                        },
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
                                        subtleHaptic()
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
                                                subtleHaptic()
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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
    val subtleHaptic = rememberSubtleHaptic()

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
                            subtleHaptic()
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
                            subtleHaptic()
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
    val subtleHaptic = rememberSubtleHaptic()

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
                            subtleHaptic()
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
                                subtleHaptic()
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
    onOpenNotificationSettings: () -> Unit = {},
    onOpenLocalAudioSettings: () -> Unit = {}
) {
    val subscriptions by vm.subscriptions.collectAsState()
    var orderedSubscriptions by remember(subscriptions) { mutableStateOf(subscriptions) }
    var isDraggingGrid by remember { mutableStateOf(false) }
    var isDraggingList by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val subtleHaptic = rememberSubtleHaptic()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(subscriptions) {
        if (!isDraggingGrid && !isDraggingList) {
            orderedSubscriptions = subscriptions
        }
    }

    val displaySubscriptions = if (orderedSubscriptions.isEmpty() && subscriptions.isNotEmpty()) subscriptions else orderedSubscriptions

    val isGridView by vm.isGridView.collectAsState()
    val authenticatedFeeds by vm.authenticatedFeedUrls.collectAsState()
    val archivedEpisodes by vm.archivedEpisodes.collectAsState()
    val playedEpisodes by vm.playedEpisodes.collectAsState()
    val downloadedEpisodes by vm.downloadedEpisodes.collectAsState()
    val localAudioFiles by vm.localAudioFiles.collectAsState()
    val activeDownloads by vm.activeDownloads.collectAsState()
    val keepUnarchivedEpisodes by vm.keepUnarchivedEpisodes.collectAsState()
    val autoArchiveEnabled by vm.autoArchiveEnabled.collectAsState()
    val showUnplayedBadge by vm.showUnplayedBadge.collectAsState()
    val showDownloadedBadge by vm.showDownloadedBadge.collectAsState()
    val subEpisodesMap by vm.subscriptionEpisodesMap.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(displaySubscriptions) {
        if (displaySubscriptions.isNotEmpty()) {
            vm.refreshSubscriptionFeedsInBackground(displaySubscriptions)
        }
    }

    val downloadedAudioSet = remember(downloadedEpisodes) { downloadedEpisodes.map { it.audioUrl }.toSet() }

    fun getSubscriptionBadgeCounts(podcast: Podcast): Pair<Int, Int> {
        if (LocalAudioManager.isLocalPodcast(podcast)) {
            val unplayedCount = localAudioFiles.count { item ->
                item.audioUrl.isNotBlank() && !playedEpisodes.contains(item.audioUrl) && !archivedEpisodes.contains(item.audioUrl)
            }
            val downloadedCount = localAudioFiles.count { item ->
                item.audioUrl.isNotBlank() && !playedEpisodes.contains(item.audioUrl) && !archivedEpisodes.contains(item.audioUrl)
            }
            return Pair(unplayedCount, downloadedCount)
        }
        val normKey = FeedCredentialsManager.normalizeUrlKey(podcast.feedUrl)
        val cachedFeed = NetworkModule.getCachedFeed(podcast.feedUrl)
        val episodeUrls = when {
            cachedFeed != null && cachedFeed.episodes.isNotEmpty() -> cachedFeed.episodes.map { it.audioUrl }
            subEpisodesMap[normKey] != null -> subEpisodesMap[normKey]!!
            subEpisodesMap[podcast.feedUrl.trim()] != null -> subEpisodesMap[podcast.feedUrl.trim()]!!
            else -> emptyList()
        }
        val unplayedCount = episodeUrls.count { url ->
            url.isNotBlank() && !playedEpisodes.contains(url) && !archivedEpisodes.contains(url)
        }
        val downloadedCount = if (episodeUrls.isNotEmpty()) {
            val epSet = episodeUrls.toSet()
            downloadedEpisodes.count { dl ->
                !playedEpisodes.contains(dl.audioUrl) &&
                    !archivedEpisodes.contains(dl.audioUrl) &&
                    (epSet.contains(dl.audioUrl) ||
                        (dl.feedUrl.isNotBlank() && FeedCredentialsManager.normalizeUrlKey(dl.feedUrl) == normKey) ||
                        (dl.feedUrl.isBlank() && dl.podcastTitle.equals(podcast.title, ignoreCase = true)))
            }
        } else {
            downloadedEpisodes.count { dl ->
                !playedEpisodes.contains(dl.audioUrl) &&
                    !archivedEpisodes.contains(dl.audioUrl) &&
                    ((dl.feedUrl.isNotBlank() && FeedCredentialsManager.normalizeUrlKey(dl.feedUrl) == normKey) ||
                        (dl.feedUrl.isBlank() && dl.podcastTitle.equals(podcast.title, ignoreCase = true)))
            }
        }
        return Pair(unplayedCount, downloadedCount)
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (vm.subscriptionSelectedPodcast != null) {
            PodcastDetailView(
                podcast = vm.subscriptionSelectedPodcast!!,
                episodes = vm.subscriptionEpisodes,
                vm = vm,
                onBack = { vm.subscriptionSelectedPodcast = null },
                onEpisodeSelected = onEpisodeSelected,
                onOpenLocalAudioSettings = onOpenLocalAudioSettings
            )
        } else if (displaySubscriptions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        "No subscriptions yet. Go to Discover to search or add a custom RSS feed!",
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (isGridView) {
                val gridState = rememberLazyGridState()
                val reorderableLazyGridState = rememberReorderableLazyGridState(gridState) { from, to ->
                    subtleHaptic()
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
                            val isAuthenticated = authenticatedFeeds.contains(FeedCredentialsManager.normalizeUrlKey(podcast.feedUrl))
                            val (unplayedCount, downloadedCount) = getSubscriptionBadgeCounts(podcast)

                            Surface(
                                onClick = {
                                    subtleHaptic()
                                    vm.loadSubscriptionPodcastEpisodes(podcast)
                                },
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
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = podcast.artworkUrl,
                                        contentDescription = podcast.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (LocalAudioManager.isLocalPodcast(podcast)) {
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                        ) {
                                            Text(
                                                text = podcast.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (isAuthenticated) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "Authenticated Feed",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                    val displayUnplayed = showUnplayedBadge && unplayedCount > 0
                                    val displayDownloaded = showDownloadedBadge && downloadedCount > 0
                                    if (displayUnplayed || displayDownloaded) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (displayDownloaded) {
                                                SubscriptionEpisodeBadge(
                                                    count = downloadedCount,
                                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                                    contentColor = MaterialTheme.colorScheme.onTertiary,
                                                    compact = true
                                                )
                                            }
                                            if (displayUnplayed) {
                                                SubscriptionEpisodeBadge(
                                                    count = unplayedCount,
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    compact = true
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
                val listState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
                    subtleHaptic()
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
                            val isAuthenticated = authenticatedFeeds.contains(FeedCredentialsManager.normalizeUrlKey(podcast.feedUrl))
                            val (unplayedCount, downloadedCount) = getSubscriptionBadgeCounts(podcast)

                            Surface(
                                onClick = {
                                    subtleHaptic()
                                    vm.loadSubscriptionPodcastEpisodes(podcast)
                                },
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
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 2
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isAuthenticated) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "Private Authenticated Feed",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = podcast.author,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val displayUnplayed = showUnplayedBadge && unplayedCount > 0
                                        val displayDownloaded = showDownloadedBadge && downloadedCount > 0
                                        if (displayUnplayed || displayDownloaded) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (displayUnplayed) {
                                                    SubscriptionEpisodeBadge(
                                                        count = unplayedCount,
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                        icon = Icons.Default.Podcasts,
                                                        compact = false
                                                    )
                                                }
                                                if (displayDownloaded) {
                                                    SubscriptionEpisodeBadge(
                                                        count = downloadedCount,
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary,
                                                        icon = Icons.Default.DownloadDone,
                                                        compact = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (LocalAudioManager.isLocalPodcast(podcast)) {
                                        IconButton(onClick = {
                                            subtleHaptic()
                                            onOpenLocalAudioSettings()
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Manage Local Audio Files", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            subtleHaptic()
                                            vm.toggleSubscription(podcast)
                                        }) {
                                            Icon(Icons.Default.Star, contentDescription = "Unsubscribe", tint = MaterialTheme.colorScheme.primary)
                                        }
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

@Composable
fun SubscriptionEpisodeBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    compact: Boolean = false
) {
    val displayCount = if (count > 99) "99+" else "$count"
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (compact) 3.dp else 1.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = if (compact) 22.dp else 24.dp, minHeight = if (compact) 20.dp else 22.dp)
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null && !compact) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = displayCount,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = 12.sp
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CustomRssFeedDialog(vm: PodcastViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subtleHaptic = rememberSubtleHaptic()
    val editingPodcast = vm.customFeedDialogEditPodcast
    val isEditing = editingPodcast != null

    val existingCreds = remember(editingPodcast, vm.customFeedDialogInitialUrl) {
        val targetUrl = editingPodcast?.feedUrl ?: vm.customFeedDialogInitialUrl
        FeedCredentialsManager.getCredentials(targetUrl)
    }

    var feedUrl by remember(editingPodcast, vm.customFeedDialogInitialUrl) {
        mutableStateOf(editingPodcast?.feedUrl ?: vm.customFeedDialogInitialUrl)
    }
    var requiresAuth by remember(editingPodcast, existingCreds, vm.customFeedDialogForceAuthExpanded) {
        mutableStateOf(existingCreds != null || vm.customFeedDialogForceAuthExpanded)
    }
    var username by remember(editingPodcast, existingCreds) {
        mutableStateOf(existingCreds?.username ?: "")
    }
    var password by remember(editingPodcast, existingCreds) {
        mutableStateOf(existingCreds?.password ?: "")
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Automatically extract embedded credentials if user pastes a URL with user:pass@host
    LaunchedEffect(feedUrl) {
        if (feedUrl.contains("@") && (feedUrl.startsWith("http", ignoreCase = true) || feedUrl.startsWith("feed", ignoreCase = true))) {
            val (cleanUrl, embedded) = FeedCredentialsManager.extractEmbeddedCredentials(feedUrl)
            if (embedded != null && embedded.isValid) {
                feedUrl = cleanUrl
                requiresAuth = true
                if (username.isBlank()) username = embedded.username
                if (password.isBlank()) password = embedded.password
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                vm.dismissCustomFeedDialog()
            }
        },
        icon = {
            Icon(
                if (isEditing) Icons.Default.Lock else Icons.Default.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (isEditing) "Feed Authentication" else "Add Custom RSS Feed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEditing) {
                        "Configure username and password for \"${editingPodcast?.title ?: "Podcast"}\"."
                    } else {
                        "Enter a custom or private podcast RSS feed URL (such as Patreon, Substack, Memberful, Supercast, or self-hosted feeds)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = feedUrl,
                    onValueChange = {
                        feedUrl = it
                        errorMessage = null
                    },
                    label = { Text("RSS Feed URL") },
                    placeholder = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                    enabled = !isSubmitting && !isEditing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Default.RssFeed, contentDescription = null)
                    },
                    trailingIcon = {
                        if (!isEditing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (feedUrl.isNotBlank()) {
                                    IconButton(onClick = {
                                        subtleHaptic()
                                        feedUrl = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear URL")
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            subtleHaptic()
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                            if (!clipText.isNullOrBlank()) {
                                                feedUrl = clipText
                                                errorMessage = null
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                    }
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = if (requiresAuth) ImeAction.Next else ImeAction.Done
                    )
                )

                // Authentication Toggle Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isSubmitting) {
                            subtleHaptic()
                            requiresAuth = !requiresAuth
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (requiresAuth) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (requiresAuth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Requires Username & Password",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enable for password-protected private feeds",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = requiresAuth,
                            onCheckedChange = {
                                subtleHaptic()
                                requiresAuth = it
                            },
                            enabled = !isSubmitting
                        )
                    }
                }

                AnimatedVisibility(visible = requiresAuth) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                errorMessage = null
                            },
                            label = { Text("Username") },
                            placeholder = { Text("Enter username or email") },
                            singleLine = true,
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text("Password") },
                            placeholder = { Text("Enter password or token") },
                            singleLine = true,
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    subtleHaptic()
                                    passwordVisible = !passwordVisible
                                }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )

                        if (isEditing && existingCreds != null && editingPodcast != null) {
                            OutlinedButton(
                                onClick = {
                                    subtleHaptic()
                                    vm.removeFeedCredentials(editingPodcast)
                                    Toast.makeText(context, "Removed saved credentials", Toast.LENGTH_SHORT).show()
                                    vm.dismissCustomFeedDialog()
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove Saved Credentials")
                            }
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    subtleHaptic()
                    val trimmedUrl = feedUrl.trim()
                    if (trimmedUrl.isBlank()) {
                        errorMessage = "Please enter an RSS feed URL."
                        return@Button
                    }
                    if (requiresAuth && username.isBlank() && password.isBlank()) {
                        errorMessage = "Please enter a username or password, or turn off the authentication toggle."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        val userToUse = if (requiresAuth) username else ""
                        val passToUse = if (requiresAuth) password else ""
                        when (val result = vm.addOrUpdateCustomFeed(
                            rawUrl = trimmedUrl,
                            username = userToUse,
                            password = passToUse,
                            existingPodcast = editingPodcast
                        )) {
                            is NetworkModule.FeedFetchResult.Success -> {
                                isSubmitting = false
                                val title = result.parsedFeed.channelTitle.ifBlank { editingPodcast?.title ?: "Podcast" }
                                Toast.makeText(
                                    context,
                                    if (isEditing) "Credentials saved for $title" else "Subscribed to $title!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                vm.dismissCustomFeedDialog()
                            }
                            is NetworkModule.FeedFetchResult.AuthRequired -> {
                                isSubmitting = false
                                requiresAuth = true
                                errorMessage = result.message
                            }
                            is NetworkModule.FeedFetchResult.Error -> {
                                isSubmitting = false
                                errorMessage = result.message
                            }
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying...")
                } else {
                    Text(if (isEditing) "Save & Verify" else "Add Feed")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    subtleHaptic()
                    vm.dismissCustomFeedDialog()
                },
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}
