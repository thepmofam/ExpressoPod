package com.rpmtech.expressopod

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.Keep
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs

@Keep
enum class GPodderSyncInterval(
    val minutes: Int,
    val chipLabel: String,
    val description: String
) {
    MINUTES_15(15, "15m", "Every 15 minutes"),
    MINUTES_30(30, "30m", "Every 30 minutes"),
    HOURS_1(60, "1h", "Every 1 hour"),
    HOURS_12(720, "12h", "Every 12 hours"),
    HOURS_24(1440, "24h", "Every 24 hours");

    companion object {
        fun fromMinutes(minutes: Int): GPodderSyncInterval {
            return entries.find { it.minutes == minutes } ?: HOURS_1
        }
    }
}

@Keep
enum class GPodderServerType(
    val id: String,
    val label: String,
    val description: String,
    val defaultPlaceholder: String
) {
    STANDARD(
        id = "standard",
        label = "Standard gPodder",
        description = "gpodder.net or custom gPodder v2 server",
        defaultPlaceholder = "https://gpodder.net"
    ),
    NEXTCLOUD(
        id = "nextcloud",
        label = "Nextcloud",
        description = "Nextcloud with gPodder Sync app",
        defaultPlaceholder = "https://cloud.example.com"
    );

    companion object {
        fun fromId(id: String?): GPodderServerType {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: STANDARD
        }
    }
}

@Keep
data class GPodderConfig(
    val enabled: Boolean = false,
    val serverType: GPodderServerType = GPodderServerType.STANDARD,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val deviceId: String = DEFAULT_DEVICE_ID,
    val syncIntervalMinutes: Int = GPodderSyncInterval.HOURS_1.minutes
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    val isNextcloud: Boolean
        get() = serverType == GPodderServerType.NEXTCLOUD

    companion object {
        const val DEFAULT_DEVICE_ID = "expressopod-android"
    }
}

@Keep
data class GPodderEpisodeAction(
    @SerializedName("podcast") val podcast: String,
    @SerializedName("episode") val episode: String,
    @SerializedName("device") val device: String? = null,
    @SerializedName("action") val action: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("started") val started: Int? = null,
    @SerializedName("position") val position: Int? = null,
    @SerializedName("total") val total: Int? = null
)

@Keep
data class GPodderSubscriptionPullResponse(
    @SerializedName("add") val add: List<String?>? = emptyList(),
    @SerializedName("remove") val remove: List<String?>? = emptyList(),
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("update_urls") val updateUrls: List<List<String?>?>? = null
)

@Keep
data class GPodderSubscriptionPushRequest(
    @SerializedName("add") val add: List<String>,
    @SerializedName("remove") val remove: List<String>
)

@Keep
data class GPodderPushResponse(
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("update_urls") val updateUrls: List<List<String?>?>? = null
)

@Keep
data class GPodderEpisodePullResponse(
    @SerializedName("actions") val actions: List<GPodderEpisodeAction?>? = emptyList(),
    @SerializedName("timestamp") val timestamp: Long? = null
)

sealed class GPodderConnectionTestResult {
    data class Success(
        val normalizedServerUrl: String,
        val message: String,
        val remoteSubscriptionCount: Int? = null
    ) : GPodderConnectionTestResult()

    data class Error(val message: String) : GPodderConnectionTestResult()
}

sealed class GPodderSyncResult {
    data class Success(
        val message: String,
        val addedSubs: Int,
        val removedSubs: Int,
        val pushedSubs: Int,
        val pulledActions: Int,
        val pushedActions: Int,
        val timestampMs: Long
    ) : GPodderSyncResult()

    data class Error(val message: String) : GPodderSyncResult()
}

data class GPodderSyncState(
    val config: GPodderConfig = GPodderConfig(),
    val isTestingConnection: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTimestampMs: Long = 0L,
    val lastSyncSuccess: Boolean? = null,
    val lastSyncMessage: String? = null,
    val testResult: GPodderConnectionTestResult? = null,
    val positionsRevision: Int = 0
)

/**
 * Manages full bidirectional gPodder sync:
 * - Supports any custom gPodder v2 server (and Nextcloud gPodder Sync) over HTTP or HTTPS
 * - Authenticates with username & password (encrypted at rest via Android Keystore)
 * - Tests connection & validates server endpoints
 * - Syncs podcast subscriptions (add / remove / URL rewrites)
 * - Syncs episode play state (played / unplayed), status (archived / active / in-progress), and times (position, started, total duration, timestamps)
 * - Schedules background periodic sync every 15m, 30m, 1h, 12h, or 24h
 */
object GPodderSyncManager {
    private const val PREFS_NAME = "expressopod_gpodder_sync"
    private const val KEY_ENABLED = "gpodder_enabled"
    private const val KEY_SERVER_TYPE = "gpodder_server_type"
    private const val KEY_SERVER_URL = "gpodder_server_url"
    private const val KEY_USERNAME = "gpodder_username"
    private const val KEY_ENCRYPTED_PASSWORD = "gpodder_encrypted_password"
    private const val KEY_DEVICE_ID = "gpodder_device_id"
    private const val KEY_SYNC_INTERVAL_MIN = "gpodder_sync_interval_min"
    private const val KEY_LAST_SUB_TIMESTAMP = "gpodder_last_sub_timestamp"
    private const val KEY_LAST_EP_TIMESTAMP = "gpodder_last_ep_timestamp"
    private const val KEY_LAST_SYNC_WALL_MS = "gpodder_last_sync_wall_ms"
    private const val KEY_LAST_SYNC_SUCCESS = "gpodder_last_sync_success"
    private const val KEY_LAST_SYNC_HAS_RESULT = "gpodder_last_sync_has_result"
    private const val KEY_LAST_SYNC_MESSAGE = "gpodder_last_sync_message"
    private const val KEY_SYNCED_IDENTITY = "gpodder_synced_identity"

    private const val KEY_KNOWN_SYNCED_SUBS = "gpodder_known_synced_subs_json"
    private const val KEY_PENDING_SUB_ADDS = "gpodder_pending_sub_adds_json"
    private const val KEY_PENDING_SUB_REMOVES = "gpodder_pending_sub_removes_json"
    private const val KEY_PENDING_EP_ACTIONS = "gpodder_pending_ep_actions_json"

    private const val KEY_SNAPSHOT_PLAYED = "gpodder_snapshot_played_json"
    private const val KEY_SNAPSHOT_ARCHIVED = "gpodder_snapshot_archived_json"
    private const val KEY_SNAPSHOT_POSITIONS = "gpodder_snapshot_positions_json"

    private const val KEY_EPISODE_FEED_MAP = "gpodder_episode_to_feed_map_json"
    private const val KEY_EPISODE_DURATION_MAP = "gpodder_episode_durations_map_json"

    private const val KEYSTORE_ALIAS = "expressopod_gpodder_auth_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    const val ACTION_GPODDER_SYNC_COMPLETED = "com.rpmtech.expressopod.ACTION_GPODDER_SYNC_COMPLETED"

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private var quickPushJob: Job? = null

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val episodeToFeedMap = ConcurrentHashMap<String, String>()
    private val episodeDurationSecMap = ConcurrentHashMap<String, Int>()
    private val sessionStartPosSecMap = ConcurrentHashMap<String, Int>()

    private val _syncState = MutableStateFlow(GPodderSyncState())
    val syncState: StateFlow<GPodderSyncState> = _syncState.asStateFlow()

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val utcFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)

    private val gpodderHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostKey = url.host.lowercase(Locale.ROOT)
                    val list = cookieStore.getOrPut(hostKey) { mutableListOf() }
                    synchronized(list) {
                        for (c in cookies) {
                            list.removeAll { it.name == c.name }
                            list.add(c)
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val hostKey = url.host.lowercase(Locale.ROOT)
                    val list = cookieStore[hostKey] ?: return emptyList()
                    synchronized(list) {
                        val now = System.currentTimeMillis()
                        list.removeAll { it.expiresAt < now }
                        return list.toList()
                    }
                }
            })
            .build()
    }

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val ctx = context.applicationContext
        appContext = ctx
        loadStateFromPrefs(ctx)
        isInitialized = true
    }

    private fun ensureInitialized(context: Context? = null) {
        if (!isInitialized) {
            val ctx = context?.applicationContext ?: appContext
            if (ctx != null) {
                init(ctx)
            }
        }
    }

    /**
     * Normalizes a user-entered gPodder server URL.
     * Supports both http:// and https:// (including custom ports, LAN hostnames, IPv4/IPv6, and subpaths).
     * Defaults to https:// if no scheme is specified.
     */
    fun normalizeServerUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var trimmed = rawUrl.trim()
        if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null

        val hasHttp = trimmed.startsWith("http://", ignoreCase = true)
        val hasHttps = trimmed.startsWith("https://", ignoreCase = true)
        if (!hasHttp && !hasHttps) {
            if (trimmed.contains("://")) return null
            trimmed = "https://$trimmed"
        } else if (hasHttp) {
            trimmed = "http://" + trimmed.substring(7)
        } else if (hasHttps) {
            trimmed = "https://" + trimmed.substring(8)
        }

        // Remove trailing slashes
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }

        // Strip accidental suffix if user pasted a full endpoint path
        val suffixesToStrip = listOf(
            "/api/2",
            "/index.php/apps/gpoddersync/subscriptions",
            "/index.php/apps/gpoddersync/episode_action"
        )
        for (suffix in suffixesToStrip) {
            if (trimmed.endsWith(suffix, ignoreCase = true)) {
                if (suffix == "/api/2") {
                    trimmed = trimmed.dropLast(suffix.length)
                } else {
                    trimmed = trimmed.dropLast(suffix.length) + "/index.php/apps/gpoddersync"
                }
                break
            }
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }

        val httpUrl = trimmed.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null
        if (httpUrl.host.isBlank()) return null

        // Rebuild clean base URL without query or fragment, and without embedded userInfo
        val cleanBuilder = httpUrl.newBuilder()
            .username("")
            .password("")
            .query(null)
            .fragment(null)
        var cleanStr = cleanBuilder.build().toString()
        while (cleanStr.endsWith("/")) {
            cleanStr = cleanStr.dropLast(1)
        }
        return cleanStr
    }

    fun clearTestResult() {
        _syncState.value = _syncState.value.copy(testResult = null)
    }

    fun saveConfig(
        context: Context,
        enabled: Boolean = _syncState.value.config.enabled,
        serverType: GPodderServerType = _syncState.value.config.serverType,
        serverUrl: String = _syncState.value.config.serverUrl,
        username: String = _syncState.value.config.username,
        password: String = _syncState.value.config.password,
        deviceId: String = _syncState.value.config.deviceId,
        syncIntervalMinutes: Int = _syncState.value.config.syncIntervalMinutes
    ): GPodderConfig {
        init(context)
        val cleanUrl = normalizeServerUrl(serverUrl) ?: serverUrl.trim()
        val cleanUser = username.trim()
        val cleanDevice = deviceId.trim().ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
            .replace(Regex("[^a-zA-Z0-9._-]"), "-")
        val validInterval = GPodderSyncInterval.fromMinutes(syncIntervalMinutes).minutes

        val newConfig = GPodderConfig(
            enabled = enabled,
            serverType = serverType,
            serverUrl = cleanUrl,
            username = cleanUser,
            password = password,
            deviceId = cleanDevice,
            syncIntervalMinutes = validInterval
        )

        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prevIdentity = sp.getString(KEY_SYNCED_IDENTITY, "") ?: ""
        val newIdentity = "${newConfig.serverType.id}|${cleanUrl.lowercase(Locale.ROOT)}|${cleanUser.lowercase(Locale.ROOT)}|$cleanDevice"

        val editor = sp.edit()
            .putBoolean(KEY_ENABLED, newConfig.enabled)
            .putString(KEY_SERVER_TYPE, newConfig.serverType.id)
            .putString(KEY_SERVER_URL, newConfig.serverUrl)
            .putString(KEY_USERNAME, newConfig.username)
            .putString(KEY_DEVICE_ID, newConfig.deviceId)
            .putInt(KEY_SYNC_INTERVAL_MIN, newConfig.syncIntervalMinutes)

        if (password.isNotEmpty()) {
            val encrypted = encryptString(password)
            if (encrypted != null) {
                editor.putString(KEY_ENCRYPTED_PASSWORD, encrypted)
            } else {
                editor.putString(KEY_ENCRYPTED_PASSWORD, "plain:" + Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            }
        } else {
            editor.remove(KEY_ENCRYPTED_PASSWORD)
        }

        // If server or username changed, reset delta timestamps so the new server performs a full initial sync
        if (prevIdentity.isNotBlank() && prevIdentity != newIdentity) {
            editor.putLong(KEY_LAST_SUB_TIMESTAMP, 0L)
                .putLong(KEY_LAST_EP_TIMESTAMP, 0L)
                .remove(KEY_KNOWN_SYNCED_SUBS)
                .remove(KEY_SNAPSHOT_PLAYED)
                .remove(KEY_SNAPSHOT_ARCHIVED)
                .remove(KEY_SNAPSHOT_POSITIONS)
            cookieStore.clear()
        }
        editor.putString(KEY_SYNCED_IDENTITY, newIdentity)
        editor.apply()

        _syncState.value = _syncState.value.copy(config = newConfig)

        if (newConfig.enabled && newConfig.isConfigured) {
            GPodderSyncWorker.schedulePeriodicSync(context.applicationContext, newConfig.syncIntervalMinutes)
        } else {
            GPodderSyncWorker.cancelPeriodicSync(context.applicationContext)
        }

        return newConfig
    }

    fun updateSyncInterval(context: Context, intervalMinutes: Int) {
        init(context)
        val cur = _syncState.value.config
        saveConfig(
            context = context,
            enabled = cur.enabled,
            serverType = cur.serverType,
            serverUrl = cur.serverUrl,
            username = cur.username,
            password = cur.password,
            deviceId = cur.deviceId,
            syncIntervalMinutes = intervalMinutes
        )
    }

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        init(context)
        val cur = _syncState.value.config
        val updated = saveConfig(
            context = context,
            enabled = enabled,
            serverType = cur.serverType,
            serverUrl = cur.serverUrl,
            username = cur.username,
            password = cur.password,
            deviceId = cur.deviceId,
            syncIntervalMinutes = cur.syncIntervalMinutes
        )
        if (enabled && updated.isConfigured) {
            syncNowAsync(context)
        }
    }

    fun disconnectServer(context: Context) {
        init(context)
        GPodderSyncWorker.cancelPeriodicSync(context.applicationContext)
        cookieStore.clear()
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_SERVER_TYPE, GPodderServerType.STANDARD.id)
            .putString(KEY_SERVER_URL, "")
            .putString(KEY_USERNAME, "")
            .remove(KEY_ENCRYPTED_PASSWORD)
            .putLong(KEY_LAST_SUB_TIMESTAMP, 0L)
            .putLong(KEY_LAST_EP_TIMESTAMP, 0L)
            .putLong(KEY_LAST_SYNC_WALL_MS, 0L)
            .putBoolean(KEY_LAST_SYNC_HAS_RESULT, false)
            .remove(KEY_LAST_SYNC_MESSAGE)
            .remove(KEY_SYNCED_IDENTITY)
            .remove(KEY_KNOWN_SYNCED_SUBS)
            .remove(KEY_PENDING_SUB_ADDS)
            .remove(KEY_PENDING_SUB_REMOVES)
            .remove(KEY_PENDING_EP_ACTIONS)
            .remove(KEY_SNAPSHOT_PLAYED)
            .remove(KEY_SNAPSHOT_ARCHIVED)
            .remove(KEY_SNAPSHOT_POSITIONS)
            .apply()

        _syncState.value = GPodderSyncState(
            config = GPodderConfig(
                enabled = false,
                serverType = GPodderServerType.STANDARD,
                syncIntervalMinutes = _syncState.value.config.syncIntervalMinutes
            ),
            positionsRevision = _syncState.value.positionsRevision
        )
    }

    /**
     * Tests the connection to the user-specified gPodder or Nextcloud server and validates credentials.
     */
    suspend fun testConnection(
        context: Context,
        serverType: GPodderServerType = _syncState.value.config.serverType,
        rawServerUrl: String,
        username: String,
        password: String,
        saveOnSuccess: Boolean = true
    ): GPodderConnectionTestResult = withContext(Dispatchers.IO) {
        init(context)
        val trimmedUrl = rawServerUrl.trim()
        if (trimmedUrl.isBlank()) {
            val err = GPodderConnectionTestResult.Error("Please enter a server link (http:// or https://).")
            _syncState.value = _syncState.value.copy(isTestingConnection = false, testResult = err)
            return@withContext err
        }

        val normalizedUrl = normalizeServerUrl(trimmedUrl)
        if (normalizedUrl == null) {
            val err = GPodderConnectionTestResult.Error("Invalid server URL. Make sure it uses http:// or https:// and includes a valid host.")
            _syncState.value = _syncState.value.copy(isTestingConnection = false, testResult = err)
            return@withContext err
        }

        val cleanUser = username.trim()
        if (cleanUser.isBlank() || password.isEmpty()) {
            val err = GPodderConnectionTestResult.Error("Please enter both username and password to test the server connection.")
            _syncState.value = _syncState.value.copy(isTestingConnection = false, testResult = err)
            return@withContext err
        }

        _syncState.value = _syncState.value.copy(isTestingConnection = true, testResult = null)

        val deviceId = _syncState.value.config.deviceId.ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
        val result = verifyServerAndCredentials(
            baseUrl = normalizedUrl,
            serverType = serverType,
            username = cleanUser,
            password = password,
            deviceId = deviceId
        )

        if (result is GPodderConnectionTestResult.Success && saveOnSuccess) {
            saveConfig(
                context = context,
                enabled = _syncState.value.config.enabled,
                serverType = serverType,
                serverUrl = result.normalizedServerUrl,
                username = cleanUser,
                password = password,
                deviceId = deviceId,
                syncIntervalMinutes = _syncState.value.config.syncIntervalMinutes
            )
        }

        _syncState.value = _syncState.value.copy(
            isTestingConnection = false,
            testResult = result
        )
        result
    }

    private fun executeGPodderCall(
        url: String,
        method: String,
        username: String,
        password: String,
        jsonBody: String? = null
    ): Response {
        val basicAuth = Credentials.basic(username, password, Charsets.UTF_8)
        val body = when {
            method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true) -> {
                (jsonBody ?: "").toRequestBody(if (jsonBody != null) jsonMediaType else null)
            }
            else -> null
        }

        val initialReq = Request.Builder()
            .url(url)
            .method(method, body)
            .header("User-Agent", "ExpressoPod/1.1 (Android; gPodderSync)")
            .header("Accept", "application/json")
            .header("Authorization", basicAuth)
            .build()

        val response = gpodderHttpClient.newCall(initialReq).execute()
        // If a cross-scheme redirect (e.g. http -> https) stripped Authorization and resulted in 401, retry directly against final URL
        if ((response.code == 401 || response.code == 403) && response.request.header("Authorization") == null) {
            val redirectedUrl = response.request.url.toString()
            response.close()
            val retryBody = when {
                method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true) -> {
                    (jsonBody ?: "").toRequestBody(if (jsonBody != null) jsonMediaType else null)
                }
                else -> null
            }
            val retryReq = Request.Builder()
                .url(redirectedUrl)
                .method(method, retryBody)
                .header("User-Agent", "ExpressoPod/1.1 (Android; gPodderSync)")
                .header("Accept", "application/json")
                .header("Authorization", basicAuth)
                .build()
            return gpodderHttpClient.newCall(retryReq).execute()
        }
        return response
    }

    private fun isNextcloudGPodderUrl(baseUrl: String): Boolean {
        return baseUrl.contains("/index.php/apps/gpoddersync", ignoreCase = true) ||
            baseUrl.contains("/apps/gpoddersync", ignoreCase = true) ||
            baseUrl.endsWith("/gpoddersync", ignoreCase = true)
    }

    private fun verifyNextcloudCandidateUrls(
        baseUrl: String,
        username: String,
        password: String
    ): GPodderConnectionTestResult {
        val candidates = mutableListOf<String>()
        if (isNextcloudGPodderUrl(baseUrl)) {
            candidates.add(baseUrl)
        } else {
            candidates.add("$baseUrl/index.php/apps/gpoddersync")
            candidates.add("$baseUrl/apps/gpoddersync")
            candidates.add(baseUrl)
        }

        var lastAuthError: GPodderConnectionTestResult.Error? = null
        var lastHttpError: GPodderConnectionTestResult.Error? = null

        for (candidate in candidates.distinct()) {
            val res = verifyNextcloudEndpoint(candidate, username, password)
            if (res is GPodderConnectionTestResult.Success) {
                return res
            } else if (res is GPodderConnectionTestResult.Error) {
                if (res.message.contains("Authentication failed", ignoreCase = true)) {
                    lastAuthError = res
                } else {
                    lastHttpError = res
                }
            }
        }

        return lastAuthError ?: lastHttpError ?: GPodderConnectionTestResult.Error(
            "Could not reach Nextcloud gPodder Sync app at $baseUrl. Ensure the 'gPodder Sync' app is installed and enabled in your Nextcloud instance."
        )
    }

    private fun verifyServerAndCredentials(
        baseUrl: String,
        serverType: GPodderServerType,
        username: String,
        password: String,
        deviceId: String
    ): GPodderConnectionTestResult {
        return try {
            val encodedUser = Uri.encode(username)
            val encodedDevice = Uri.encode(deviceId)

            // 1. If Nextcloud is selected or URL explicitly contains Nextcloud gpoddersync path, verify Nextcloud endpoints
            if (serverType == GPodderServerType.NEXTCLOUD || isNextcloudGPodderUrl(baseUrl)) {
                return verifyNextcloudCandidateUrls(baseUrl, username, password)
            }

            // 2. Standard gPodder API v2 login check: POST /api/2/auth/{username}/login.json
            val loginUrl = "$baseUrl/api/2/auth/$encodedUser/login.json"
            var loginHttpCode: Int
            executeGPodderCall(
                url = loginUrl,
                method = "POST",
                username = username,
                password = password,
                jsonBody = null
            ).use { resp ->
                loginHttpCode = resp.code
                if (resp.code == 401 || resp.code == 403) {
                    return GPodderConnectionTestResult.Error(
                        "Authentication failed (HTTP ${resp.code}). Please verify your username and password."
                    )
                }
            }

            // 3. Register / update device on gPodder server (optional on some minimal servers, required on others)
            val deviceUrl = "$baseUrl/api/2/devices/$encodedUser/$encodedDevice.json"
            val devicePayload = """{"caption":"ExpressoPod on Android","type":"mobile"}"""
            try {
                executeGPodderCall(
                    url = deviceUrl,
                    method = "POST",
                    username = username,
                    password = password,
                    jsonBody = devicePayload
                ).use { devResp ->
                    if (devResp.code == 401 || devResp.code == 403) {
                        return GPodderConnectionTestResult.Error(
                            "Authentication failed (HTTP ${devResp.code}). Please verify your username and password."
                        )
                    }
                }
            } catch (_: Exception) {}

            // 4. Verify subscriptions endpoint returns valid gPodder JSON
            val subsUrl = "$baseUrl/api/2/subscriptions/$encodedUser/$encodedDevice.json?since=0"
            executeGPodderCall(
                url = subsUrl,
                method = "GET",
                username = username,
                password = password
            ).use { subsResp ->
                if (subsResp.code == 401 || subsResp.code == 403) {
                    return GPodderConnectionTestResult.Error(
                        "Authentication failed (HTTP ${subsResp.code}). Please verify your username and password."
                    )
                }
                if (subsResp.isSuccessful) {
                    val bodyStr = subsResp.body?.string()?.trim().orEmpty()
                    val parsedCount = parseSubscriptionCountFromTestJson(bodyStr)
                    if (parsedCount != null) {
                        val subSummary = if (parsedCount == 1) "1 subscription on server" else "$parsedCount subscriptions on server"
                        return GPodderConnectionTestResult.Success(
                            normalizedServerUrl = baseUrl,
                            message = "Connection successful! Verified gPodder server ($subSummary).",
                            remoteSubscriptionCount = parsedCount
                        )
                    }
                }

                // Fallback A: Check simple subscriptions list /subscriptions/{username}.json
                val simpleSubsUrl = "$baseUrl/subscriptions/$encodedUser.json"
                executeGPodderCall(
                    url = simpleSubsUrl,
                    method = "GET",
                    username = username,
                    password = password
                ).use { simpleResp ->
                    if (simpleResp.code == 401 || simpleResp.code == 403) {
                        return GPodderConnectionTestResult.Error(
                            "Authentication failed (HTTP ${simpleResp.code}). Please verify your username and password."
                        )
                    }
                    if (simpleResp.isSuccessful) {
                        val bodyStr = simpleResp.body?.string()?.trim().orEmpty()
                        val parsedCount = parseSubscriptionCountFromTestJson(bodyStr)
                        if (parsedCount != null) {
                            return GPodderConnectionTestResult.Success(
                                normalizedServerUrl = baseUrl,
                                message = "Connection successful! Verified gPodder server ($parsedCount subscriptions).",
                                remoteSubscriptionCount = parsedCount
                            )
                        }
                    }
                }

                // Fallback B: Check if server is a Nextcloud instance where user entered root URL
                if (subsResp.code == 404 || loginHttpCode == 404 || loginHttpCode == 405) {
                    val ncResult = verifyNextcloudCandidateUrls(baseUrl, username, password)
                    if (ncResult is GPodderConnectionTestResult.Success) {
                        return ncResult
                    }
                }

                // If login itself returned 200 OK and devices list is valid JSON, also accept
                if (loginHttpCode in 200..299) {
                    val devicesListUrl = "$baseUrl/api/2/devices/$encodedUser.json"
                    executeGPodderCall(
                        url = devicesListUrl,
                        method = "GET",
                        username = username,
                        password = password
                    ).use { devListResp ->
                        if (devListResp.isSuccessful) {
                            val devBody = devListResp.body?.string()?.trim().orEmpty()
                            if (devBody.startsWith("[") || devBody.startsWith("{")) {
                                return GPodderConnectionTestResult.Success(
                                    normalizedServerUrl = baseUrl,
                                    message = "Connection successful! Authenticated with gPodder server."
                                )
                            }
                        }
                    }
                }

                return GPodderConnectionTestResult.Error(
                    "Server responded with HTTP ${subsResp.code}, but did not return a valid gPodder API response. Please check the server link."
                )
            }
        } catch (e: javax.net.ssl.SSLException) {
            GPodderConnectionTestResult.Error(
                "SSL/TLS error connecting to server (${e.localizedMessage ?: "Handshake failed"}). Check if the server uses http:// instead of https://."
            )
        } catch (e: java.net.UnknownHostException) {
            GPodderConnectionTestResult.Error(
                "Could not resolve server host. Please check the server URL and your internet connection."
            )
        } catch (e: java.net.ConnectException) {
            GPodderConnectionTestResult.Error(
                "Could not connect to server (${e.localizedMessage ?: "Connection refused"}). Check the server URL and port."
            )
        } catch (e: java.net.SocketTimeoutException) {
            GPodderConnectionTestResult.Error(
                "Connection timed out while contacting the gPodder server."
            )
        } catch (e: Exception) {
            GPodderConnectionTestResult.Error(
                "Connection failed: ${e.localizedMessage ?: "Unexpected error"}."
            )
        }
    }

    private fun verifyNextcloudEndpoint(
        ncBaseUrl: String,
        username: String,
        password: String
    ): GPodderConnectionTestResult {
        return try {
            val subsUrl = "$ncBaseUrl/subscriptions?since=0"
            executeGPodderCall(
                url = subsUrl,
                method = "GET",
                username = username,
                password = password
            ).use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    return GPodderConnectionTestResult.Error(
                        "Authentication failed (HTTP ${resp.code}). Please verify your username and password."
                    )
                }
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string()?.trim().orEmpty()
                    val count = parseSubscriptionCountFromTestJson(bodyStr)
                    if (count != null) {
                        return GPodderConnectionTestResult.Success(
                            normalizedServerUrl = ncBaseUrl,
                            message = "Connection successful! Verified Nextcloud gPodder Sync ($count subscriptions).",
                            remoteSubscriptionCount = count
                        )
                    }
                }
                GPodderConnectionTestResult.Error(
                    "gPodder API endpoint not found at $ncBaseUrl (HTTP ${resp.code})."
                )
            }
        } catch (e: Exception) {
            GPodderConnectionTestResult.Error("Connection failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseSubscriptionCountFromTestJson(bodyStr: String): Int? {
        if (bodyStr.isBlank()) return null
        return try {
            val element = JsonParser.parseString(bodyStr)
            when {
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has("add") && obj.get("add").isJsonArray) {
                        obj.getAsJsonArray("add").size()
                    } else if (obj.has("timestamp")) {
                        0
                    } else {
                        null
                    }
                }
                element.isJsonArray -> element.asJsonArray.size()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Registers feed episodes so we always know the podcast feed URL and duration for any episode audioUrl.
     */
    fun registerFeedEpisodes(context: Context?, feedUrl: String, episodes: List<Episode>) {
        ensureInitialized(context)
        val cleanFeed = feedUrl.trim()
        if (cleanFeed.isBlank() || episodes.isEmpty()) return
        var mapChanged = false
        var durChanged = false
        for (ep in episodes) {
            val audioUrl = ep.audioUrl.trim()
            if (audioUrl.isNotBlank()) {
                if (episodeToFeedMap[audioUrl] != cleanFeed) {
                    episodeToFeedMap[audioUrl] = cleanFeed
                    mapChanged = true
                }
                val durMs = parseDurationMs(ep.durationText)
                if (durMs > 0L) {
                    val durSec = (durMs / 1000L).toInt()
                    if (durSec > 0 && episodeDurationSecMap[audioUrl] != durSec) {
                        episodeDurationSecMap[audioUrl] = durSec
                        durChanged = true
                    }
                }
            }
        }
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null && (mapChanged || durChanged)) {
            syncScope.launch {
                persistEpisodeMetadataMaps(ctx)
            }
        }
    }

    fun getSavedDurationMs(context: Context?, audioUrl: String): Long {
        ensureInitialized(context)
        val sec = episodeDurationSecMap[audioUrl.trim()] ?: return 0L
        return if (sec > 0) sec * 1000L else 0L
    }

    fun findPodcastUrlForEpisode(context: Context, audioUrl: String, fallbackPodcastUrl: String? = null): String {
        ensureInitialized(context)
        val cleanAudio = audioUrl.trim()
        if (cleanAudio.isBlank()) return fallbackPodcastUrl?.trim().orEmpty()

        if (!fallbackPodcastUrl.isNullOrBlank() && !LocalAudioManager.isLocalFeedUrl(fallbackPodcastUrl)) {
            episodeToFeedMap[cleanAudio] = fallbackPodcastUrl.trim()
            return fallbackPodcastUrl.trim()
        }

        episodeToFeedMap[cleanAudio]?.takeIf { it.isNotBlank() }?.let { return it }

        // Checkexpressopod_sub_episodes_cache
        try {
            val sp = context.applicationContext.getSharedPreferences("expressopod_sub_episodes_cache", Context.MODE_PRIVATE)
            val json = sp.getString("feed_episodes_map", null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val loaded: Map<String, List<String>>? = appGson.fromJson(json, type)
                loaded?.forEach { (feed, urls) ->
                    if (urls.contains(cleanAudio) && !LocalAudioManager.isLocalFeedUrl(feed)) {
                        episodeToFeedMap[cleanAudio] = feed
                        return feed
                    }
                }
            }
        } catch (_: Exception) {}

        // Check last_podcast if last_episode matches
        try {
            val statePrefs = context.applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val lastEpJson = statePrefs.getString("last_episode", null)
            val lastPodJson = statePrefs.getString("last_podcast", null)
            if (lastEpJson != null && lastPodJson != null) {
                val lastEp = appGson.fromJson(lastEpJson, Episode::class.java)
                val lastPod = appGson.fromJson(lastPodJson, Podcast::class.java)
                if (lastEp?.audioUrl?.trim() == cleanAudio && !lastPod?.feedUrl.isNullOrBlank()) {
                    val feed = lastPod.feedUrl.trim()
                    if (!LocalAudioManager.isLocalFeedUrl(feed)) {
                        episodeToFeedMap[cleanAudio] = feed
                        return feed
                    }
                }
            }
        } catch (_: Exception) {}

        return ""
    }

    fun onSubscriptionAdded(context: Context, feedUrl: String) {
        onSubscriptionsAdded(context, listOf(feedUrl))
    }

    fun onSubscriptionsAdded(context: Context, feedUrls: Collection<String>) {
        init(context)
        val cleanUrls = feedUrls.map { it.trim() }
            .filter { it.isNotBlank() && !LocalAudioManager.isLocalFeedUrl(it) }
        if (cleanUrls.isEmpty()) return

        val ctx = context.applicationContext
        synchronized(this) {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val adds = loadStringSetJson(sp.getString(KEY_PENDING_SUB_ADDS, null)).toMutableSet()
            val removes = loadStringSetJson(sp.getString(KEY_PENDING_SUB_REMOVES, null)).toMutableSet()
            for (u in cleanUrls) {
                adds.add(u)
                removes.remove(u)
            }
            sp.edit()
                .putString(KEY_PENDING_SUB_ADDS, appGson.toJson(adds))
                .putString(KEY_PENDING_SUB_REMOVES, appGson.toJson(removes))
                .apply()
        }
        triggerDebouncedBackgroundSync(ctx)
    }

    fun onSubscriptionRemoved(context: Context, feedUrl: String) {
        init(context)
        val cleanUrl = feedUrl.trim()
        if (cleanUrl.isBlank() || LocalAudioManager.isLocalFeedUrl(cleanUrl)) return

        val ctx = context.applicationContext
        synchronized(this) {
            val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val adds = loadStringSetJson(sp.getString(KEY_PENDING_SUB_ADDS, null)).toMutableSet()
            val removes = loadStringSetJson(sp.getString(KEY_PENDING_SUB_REMOVES, null)).toMutableSet()
            adds.remove(cleanUrl)
            removes.add(cleanUrl)
            sp.edit()
                .putString(KEY_PENDING_SUB_ADDS, appGson.toJson(adds))
                .putString(KEY_PENDING_SUB_REMOVES, appGson.toJson(removes))
                .apply()
        }
        triggerDebouncedBackgroundSync(ctx)
    }

    fun onPlaybackPositionUpdated(
        context: Context,
        podcastUrl: String?,
        episodeUrl: String,
        positionMs: Long,
        durationMs: Long,
        shouldQueueSyncAction: Boolean = true
    ) {
        init(context)
        val cleanEp = episodeUrl.trim()
        if (cleanEp.isBlank() || cleanEp.startsWith("local://") || cleanEp.startsWith("content://") || cleanEp.startsWith("file://")) return

        val ctx = context.applicationContext
        val resolvedPodcast = findPodcastUrlForEpisode(ctx, cleanEp, podcastUrl)
        val posSec = (positionMs / 1000L).toInt().coerceAtLeast(0)
        val knownDurSec = if (durationMs > 0L) {
            (durationMs / 1000L).toInt().also { episodeDurationSecMap[cleanEp] = it }
        } else {
            episodeDurationSecMap[cleanEp] ?: 0
        }

        val startSec = sessionStartPosSecMap.getOrPut(cleanEp) { posSec }

        if (!shouldQueueSyncAction || resolvedPodcast.isBlank()) return

        val deviceId = _syncState.value.config.deviceId.ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
        val action = GPodderEpisodeAction(
            podcast = resolvedPodcast,
            episode = cleanEp,
            device = deviceId,
            action = "play",
            timestamp = formatGPodderTimestamp(System.currentTimeMillis()),
            started = startSec.coerceAtLeast(0),
            position = posSec,
            total = if (knownDurSec > 0) maxOf(knownDurSec, posSec) else null
        )
        enqueueEpisodeAction(ctx, action)
        triggerDebouncedBackgroundSync(ctx, delayMs = 8_000L)
    }

    fun onEpisodePlayedStateChanged(
        context: Context,
        podcastUrl: String?,
        episodeUrl: String,
        played: Boolean,
        durationMs: Long = 0L
    ) {
        init(context)
        val cleanEp = episodeUrl.trim()
        if (cleanEp.isBlank() || cleanEp.startsWith("local://") || cleanEp.startsWith("content://") || cleanEp.startsWith("file://")) return

        val ctx = context.applicationContext
        val resolvedPodcast = findPodcastUrlForEpisode(ctx, cleanEp, podcastUrl)
        if (resolvedPodcast.isBlank()) return

        val knownDurSec = if (durationMs > 0L) {
            (durationMs / 1000L).toInt().also { episodeDurationSecMap[cleanEp] = it }
        } else {
            episodeDurationSecMap[cleanEp] ?: 0
        }
        val deviceId = _syncState.value.config.deviceId.ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
        val nowIso = formatGPodderTimestamp(System.currentTimeMillis())

        if (played) {
            val totalSec = if (knownDurSec > 0) knownDurSec else 1
            val startedSec = sessionStartPosSecMap.remove(cleanEp)?.coerceIn(0, totalSec) ?: 0
            val playCompleteAction = GPodderEpisodeAction(
                podcast = resolvedPodcast,
                episode = cleanEp,
                device = deviceId,
                action = "play",
                timestamp = nowIso,
                started = startedSec,
                position = totalSec,
                total = totalSec
            )
            enqueueEpisodeAction(ctx, playCompleteAction)
        } else {
            sessionStartPosSecMap[cleanEp] = 0
            // Send "new" action to mark episode as unplayed/new in gPodder
            val newAction = GPodderEpisodeAction(
                podcast = resolvedPodcast,
                episode = cleanEp,
                device = deviceId,
                action = "new",
                timestamp = nowIso
            )
            enqueueEpisodeAction(ctx, newAction)
        }
        triggerDebouncedBackgroundSync(ctx)
    }

    fun onEpisodeArchiveStateChanged(
        context: Context,
        podcastUrl: String?,
        episodeUrl: String,
        archived: Boolean,
        fromPlaybackCompletion: Boolean = false,
        durationMs: Long = 0L
    ) {
        init(context)
        val cleanEp = episodeUrl.trim()
        if (cleanEp.isBlank() || cleanEp.startsWith("local://") || cleanEp.startsWith("content://") || cleanEp.startsWith("file://")) return

        val ctx = context.applicationContext
        val resolvedPodcast = findPodcastUrlForEpisode(ctx, cleanEp, podcastUrl)
        if (resolvedPodcast.isBlank()) return

        if (durationMs > 0L) {
            episodeDurationSecMap[cleanEp] = (durationMs / 1000L).toInt()
        }

        // If archived automatically on playback completion, onEpisodePlayedStateChanged already recorded the 100% play action
        if (archived && fromPlaybackCompletion) return

        val deviceId = _syncState.value.config.deviceId.ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
        val nowIso = formatGPodderTimestamp(System.currentTimeMillis())

        val action = if (archived) {
            GPodderEpisodeAction(
                podcast = resolvedPodcast,
                episode = cleanEp,
                device = deviceId,
                action = "delete",
                timestamp = nowIso
            )
        } else {
            sessionStartPosSecMap[cleanEp] = 0
            GPodderEpisodeAction(
                podcast = resolvedPodcast,
                episode = cleanEp,
                device = deviceId,
                action = "new",
                timestamp = nowIso
            )
        }
        enqueueEpisodeAction(ctx, action)
        triggerDebouncedBackgroundSync(ctx)
    }

    private fun enqueueEpisodeAction(context: Context, action: GPodderEpisodeAction) {
        synchronized(this) {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = loadEpisodeActionsJson(sp.getString(KEY_PENDING_EP_ACTIONS, null)).toMutableList()
            // Replace any older pending action for the exact same episode so only the latest state/position is pushed
            currentList.removeAll { it.episode == action.episode }
            currentList.add(action)
            // Keep bounded
            val bounded = if (currentList.size > 1000) currentList.takeLast(1000) else currentList
            sp.edit().putString(KEY_PENDING_EP_ACTIONS, appGson.toJson(bounded)).apply()
        }
    }

    private fun triggerDebouncedBackgroundSync(context: Context, delayMs: Long = 4_000L) {
        val cfg = _syncState.value.config
        if (!cfg.enabled || !cfg.isConfigured) return
        quickPushJob?.cancel()
        quickPushJob = syncScope.launch {
            delay(delayMs)
            performSync(context.applicationContext)
        }
    }

    fun syncNowAsync(context: Context, onComplete: ((GPodderSyncResult) -> Unit)? = null) {
        init(context)
        val appCtx = context.applicationContext
        syncScope.launch {
            val result = performSync(appCtx)
            if (onComplete != null) {
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
        }
    }

    /**
     * Performs a full bidirectional sync of subscriptions and episode play states, statuses, and times.
     */
    suspend fun performSync(context: Context): GPodderSyncResult = withContext(Dispatchers.IO) {
        init(context)
        val appCtx = context.applicationContext
        syncMutex.withLock {
            val cfg = _syncState.value.config
            if (!cfg.isConfigured) {
                val err = GPodderSyncResult.Error("Please configure server URL, username, and password first.")
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncSuccess = false,
                    lastSyncMessage = err.message
                )
                return@withLock err
            }

            val baseUrl = normalizeServerUrl(cfg.serverUrl)
            if (baseUrl == null) {
                val err = GPodderSyncResult.Error("Invalid gPodder server URL.")
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncSuccess = false,
                    lastSyncMessage = err.message
                )
                return@withLock err
            }

            _syncState.value = _syncState.value.copy(isSyncing = true)

            try {
                val username = cfg.username.trim()
                val password = cfg.password
                val deviceId = cfg.deviceId.ifBlank { GPodderConfig.DEFAULT_DEVICE_ID }
                val isNextcloud = cfg.isNextcloud || isNextcloudGPodderUrl(baseUrl)
                val effectiveBaseUrl = if (isNextcloud && !baseUrl.contains("gpoddersync", ignoreCase = true)) {
                    "$baseUrl/index.php/apps/gpoddersync"
                } else {
                    baseUrl
                }

                // 1. Authenticate & ensure device exists (for standard gPodder v2 servers)
                if (!isNextcloud) {
                    val encodedUser = Uri.encode(username)
                    val encodedDevice = Uri.encode(deviceId)
                    val loginUrl = "$effectiveBaseUrl/api/2/auth/$encodedUser/login.json"
                    executeGPodderCall(
                        url = loginUrl,
                        method = "POST",
                        username = username,
                        password = password
                    ).use { loginResp ->
                        if (loginResp.code == 401 || loginResp.code == 403) {
                            throw IllegalStateException("Authentication failed (HTTP ${loginResp.code}). Check your credentials.")
                        }
                    }

                    val deviceUrl = "$effectiveBaseUrl/api/2/devices/$encodedUser/$encodedDevice.json"
                    val devicePayload = """{"caption":"ExpressoPod on Android","type":"mobile"}"""
                    try {
                        executeGPodderCall(
                            url = deviceUrl,
                            method = "POST",
                            username = username,
                            password = password,
                            jsonBody = devicePayload
                        ).close()
                    } catch (_: Exception) {}
                }

                // 2. Sync Subscriptions (pull remote changes + push local changes)
                val subStats = syncSubscriptionsInternal(
                    context = appCtx,
                    baseUrl = effectiveBaseUrl,
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    isNextcloud = isNextcloud
                )

                // 3. Sync Episode Actions (play states, archive status, playback positions & times)
                val epStats = syncEpisodeActionsInternal(
                    context = appCtx,
                    baseUrl = effectiveBaseUrl,
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    isNextcloud = isNextcloud
                )

                persistEpisodeMetadataMaps(appCtx)

                val nowMs = System.currentTimeMillis()
                val summaryParts = mutableListOf<String>()
                if (subStats.addedLocal > 0) summaryParts.add("+${subStats.addedLocal} subs pulled")
                if (subStats.removedLocal > 0) summaryParts.add("-${subStats.removedLocal} subs removed")
                if (subStats.pushedRemote > 0) summaryParts.add("${subStats.pushedRemote} subs pushed")
                if (epStats.pulledActions > 0) summaryParts.add("${epStats.pulledActions} episode updates pulled")
                if (epStats.pushedActions > 0) summaryParts.add("${epStats.pushedActions} episode states pushed")

                val summaryMessage = if (summaryParts.isEmpty()) {
                    "Up to date (${subStats.totalLocalSubs} subscriptions synced)"
                } else {
                    "Synced: ${summaryParts.joinToString(", ")}"
                }

                val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sp.edit()
                    .putLong(KEY_LAST_SYNC_WALL_MS, nowMs)
                    .putBoolean(KEY_LAST_SYNC_HAS_RESULT, true)
                    .putBoolean(KEY_LAST_SYNC_SUCCESS, true)
                    .putString(KEY_LAST_SYNC_MESSAGE, summaryMessage)
                    .apply()

                val newRevision = _syncState.value.positionsRevision + 1
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncTimestampMs = nowMs,
                    lastSyncSuccess = true,
                    lastSyncMessage = summaryMessage,
                    positionsRevision = newRevision
                )

                // Notify MainActivity / PlaybackService so active UI & player refresh
                try {
                    appCtx.sendBroadcast(Intent(ACTION_GPODDER_SYNC_COMPLETED).apply {
                        setPackage(appCtx.packageName)
                    })
                } catch (_: Exception) {}

                GPodderSyncResult.Success(
                    message = summaryMessage,
                    addedSubs = subStats.addedLocal,
                    removedSubs = subStats.removedLocal,
                    pushedSubs = subStats.pushedRemote,
                    pulledActions = epStats.pulledActions,
                    pushedActions = epStats.pushedActions,
                    timestampMs = nowMs
                )
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Sync failed"
                val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sp.edit()
                    .putBoolean(KEY_LAST_SYNC_HAS_RESULT, true)
                    .putBoolean(KEY_LAST_SYNC_SUCCESS, false)
                    .putString(KEY_LAST_SYNC_MESSAGE, errMsg)
                    .apply()

                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncSuccess = false,
                    lastSyncMessage = errMsg
                )
                GPodderSyncResult.Error(errMsg)
            }
        }
    }

    private data class SubSyncStats(
        val addedLocal: Int,
        val removedLocal: Int,
        val pushedRemote: Int,
        val totalLocalSubs: Int
    )

    private suspend fun syncSubscriptionsInternal(
        context: Context,
        baseUrl: String,
        username: String,
        password: String,
        deviceId: String,
        isNextcloud: Boolean
    ): SubSyncStats {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTimestamp = sp.getLong(KEY_LAST_SUB_TIMESTAMP, 0L)
        val isInitialSync = lastTimestamp == 0L

        val encodedUser = Uri.encode(username)
        val encodedDevice = Uri.encode(deviceId)

        val pullUrl = if (isNextcloud) {
            "$baseUrl/subscriptions?since=$lastTimestamp"
        } else {
            "$baseUrl/api/2/subscriptions/$encodedUser/$encodedDevice.json?since=$lastTimestamp"
        }

        var serverAddList = emptyList<String>()
        var serverRemoveList = emptyList<String>()
        var serverTimestamp = lastTimestamp
        var urlRewrites = emptyMap<String, String>()

        executeGPodderCall(
            url = pullUrl,
            method = "GET",
            username = username,
            password = password
        ).use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw IllegalStateException("Authentication failed while syncing subscriptions (HTTP ${resp.code}).")
            }
            if (!resp.isSuccessful) {
                throw IllegalStateException("Subscription sync failed with HTTP ${resp.code}.")
            }
            val bodyStr = resp.body?.string()?.trim().orEmpty()
            if (bodyStr.startsWith("[")) {
                val listType = object : TypeToken<List<String?>>() {}.type
                val urls: List<String?> = appGson.fromJson(bodyStr, listType) ?: emptyList()
                serverAddList = urls.mapNotNull { it?.trim()?.takeIf { u -> u.isNotBlank() } }
            } else if (bodyStr.startsWith("{")) {
                val parsed: GPodderSubscriptionPullResponse? =
                    appGson.fromJson(bodyStr, GPodderSubscriptionPullResponse::class.java)
                serverAddList = parsed?.add?.mapNotNull { it?.trim()?.takeIf { u -> u.isNotBlank() } }.orEmpty()
                serverRemoveList = parsed?.remove?.mapNotNull { it?.trim()?.takeIf { u -> u.isNotBlank() } }.orEmpty()
                if (parsed?.timestamp != null && parsed.timestamp > 0L) {
                    serverTimestamp = parsed.timestamp
                }
                val rewrites = mutableMapOf<String, String>()
                parsed?.updateUrls?.forEach { pair ->
                    if (pair != null && pair.size >= 2) {
                        val oldU = pair[0]?.trim()
                        val newU = pair[1]?.trim()
                        if (!oldU.isNullOrBlank() && !newU.isNullOrBlank()) {
                            rewrites[oldU] = newU
                        }
                    }
                }
                urlRewrites = rewrites
            }
        }

        // Load current local subscriptions
        val prefsSnapshot = context.dataStore.data.first()
        var currentSubs = SubscriptionStorageManager.parseSubscriptionsJson(
            prefsSnapshot[stringPreferencesKey("subscriptions")]
        ).toMutableList()
        if (currentSubs.isEmpty()) {
            val backup = SubscriptionStorageManager.loadSubscriptionsFromBackup(context)
            if (backup.isNotEmpty()) {
                currentSubs = backup.toMutableList()
            }
        }

        val pendingAdds: MutableSet<String>
        val pendingRemoves: MutableSet<String>
        val knownSynced: Set<String>
        synchronized(this) {
            pendingAdds = loadStringSetJson(sp.getString(KEY_PENDING_SUB_ADDS, null)).toMutableSet()
            pendingRemoves = loadStringSetJson(sp.getString(KEY_PENDING_SUB_REMOVES, null)).toMutableSet()
            knownSynced = loadStringSetJson(sp.getString(KEY_KNOWN_SYNCED_SUBS, null))
        }

        val currentRemoteSubs = currentSubs.filterNot { LocalAudioManager.isLocalPodcast(it) }
        val currentFeedUrls = currentRemoteSubs.map { it.feedUrl.trim() }.toSet()

        // Also detect any offline additions/removals compared to knownSynced
        if (!isInitialSync && knownSynced.isNotEmpty()) {
            for (u in currentFeedUrls) {
                if (!knownSynced.contains(u) && !pendingRemoves.contains(u)) {
                    pendingAdds.add(u)
                }
            }
            for (u in knownSynced) {
                if (!currentFeedUrls.contains(u) && !pendingAdds.contains(u)) {
                    pendingRemoves.add(u)
                }
            }
        }

        var localChanged = false
        var addedLocalCount = 0
        var removedLocalCount = 0

        // Apply URL rewrites if any
        if (urlRewrites.isNotEmpty()) {
            for (i in currentSubs.indices) {
                val rewritten = urlRewrites[currentSubs[i].feedUrl]
                if (!rewritten.isNullOrBlank()) {
                    currentSubs[i] = currentSubs[i].copy(feedUrl = rewritten)
                    localChanged = true
                }
            }
        }

        // Apply server removals (only on delta syncs or when not explicitly re-added locally)
        if (!isInitialSync && serverRemoveList.isNotEmpty()) {
            val removeSet = serverRemoveList.filterNot { pendingAdds.contains(it) }.toSet()
            if (removeSet.isNotEmpty()) {
                val beforeSize = currentSubs.size
                currentSubs.removeAll { pod ->
                    !LocalAudioManager.isLocalPodcast(pod) && removeSet.any { rem -> urlsEquivalent(rem, pod.feedUrl) }
                }
                val diff = beforeSize - currentSubs.size
                if (diff > 0) {
                    removedLocalCount += diff
                    localChanged = true
                }
            }
        }

        // Determine which server-added feeds are new to this device
        val urlsToAddLocally = serverAddList.filter { remoteUrl ->
            val wasRemovedLocally = !isInitialSync && pendingRemoves.any { urlsEquivalent(it, remoteUrl) }
            val alreadySubscribed = currentSubs.any { urlsEquivalent(it.feedUrl, remoteUrl) }
            !wasRemovedLocally && !alreadySubscribed && SecurityUtils.normalizeFeedUrl(remoteUrl) != null
        }.distinct()

        if (urlsToAddLocally.isNotEmpty()) {
            // Fetch feed metadata in parallel chunks so subscriptions have full artwork, title, and episodes
            val fetchedPodcasts = coroutineScope {
                urlsToAddLocally.chunked(6).flatMap { chunk ->
                    chunk.map { feedUrl ->
                        async {
                            val normUrl = SecurityUtils.normalizeFeedUrl(feedUrl) ?: feedUrl.trim()
                            try {
                                val parsed = NetworkModule.fetchPodcastFeed(normUrl)
                                if (parsed != null) {
                                    registerFeedEpisodes(context, normUrl, parsed.episodes)
                                    saveEpisodesForBadgeCache(context, normUrl, parsed.episodes)
                                    Podcast(
                                        title = parsed.channelTitle.ifBlank { deriveTitleFromUrl(normUrl) },
                                        author = parsed.channelAuthor.ifBlank { "Podcast" },
                                        artworkUrl = parsed.channelImage,
                                        feedUrl = normUrl,
                                        description = parsed.channelDescription
                                    )
                                } else {
                                    Podcast(
                                        title = deriveTitleFromUrl(normUrl),
                                        author = "Synced from gPodder",
                                        artworkUrl = "",
                                        feedUrl = normUrl
                                    )
                                }
                            } catch (_: Exception) {
                                Podcast(
                                    title = deriveTitleFromUrl(normUrl),
                                    author = "Synced from gPodder",
                                    artworkUrl = "",
                                    feedUrl = normUrl
                                )
                            }
                        }
                    }.awaitAll()
                }
            }

            for (pod in fetchedPodcasts) {
                if (currentSubs.none { urlsEquivalent(it.feedUrl, pod.feedUrl) }) {
                    currentSubs.add(pod)
                    addedLocalCount++
                    localChanged = true
                }
            }
        }

        if (localChanged) {
            val updatedJson = appGson.toJson(currentSubs)
            context.dataStore.edit { editPrefs ->
                editPrefs[stringPreferencesKey("subscriptions")] = updatedJson
            }
            if (currentSubs.isEmpty()) {
                SubscriptionStorageManager.recordExplicitEmpty(context)
            } else {
                SubscriptionStorageManager.saveSubscriptionsToBackup(context, updatedJson)
            }
        }

        // Compute subscriptions to push to the server
        val finalLocalFeedUrls = currentSubs
            .filterNot { LocalAudioManager.isLocalPodcast(it) }
            .map { it.feedUrl.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val toPushAdd: List<String>
        val toPushRemove: List<String>

        if (isInitialSync) {
            toPushAdd = finalLocalFeedUrls.filter { localUrl ->
                serverAddList.none { urlsEquivalent(it, localUrl) }
            }
            toPushRemove = emptyList()
        } else {
            toPushAdd = pendingAdds.filter { addUrl ->
                finalLocalFeedUrls.any { urlsEquivalent(it, addUrl) } &&
                    serverAddList.none { urlsEquivalent(it, addUrl) }
            }.distinct()
            toPushRemove = pendingRemoves.filter { remUrl ->
                finalLocalFeedUrls.none { urlsEquivalent(it, remUrl) } &&
                    serverRemoveList.none { urlsEquivalent(it, remUrl) }
            }.distinct()
        }

        var pushedRemoteCount = 0
        if (toPushAdd.isNotEmpty() || toPushRemove.isNotEmpty()) {
            val pushUrl = if (isNextcloud) {
                "$baseUrl/subscription_change/create"
            } else {
                "$baseUrl/api/2/subscriptions/$encodedUser/$encodedDevice.json"
            }
            val pushPayload = appGson.toJson(
                GPodderSubscriptionPushRequest(
                    add = toPushAdd,
                    remove = toPushRemove
                )
            )
            executeGPodderCall(
                url = pushUrl,
                method = "POST",
                username = username,
                password = password,
                jsonBody = pushPayload
            ).use { pushResp ->
                if (pushResp.isSuccessful) {
                    pushedRemoteCount = toPushAdd.size + toPushRemove.size
                    val bodyStr = pushResp.body?.string()?.trim().orEmpty()
                    if (bodyStr.startsWith("{")) {
                        try {
                            val pushParsed = appGson.fromJson(bodyStr, GPodderPushResponse::class.java)
                            if (pushParsed?.timestamp != null && pushParsed.timestamp > 0L) {
                                serverTimestamp = maxOf(serverTimestamp, pushParsed.timestamp)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (serverTimestamp == 0L) {
            serverTimestamp = System.currentTimeMillis() / 1000L
        }

        synchronized(this) {
            sp.edit()
                .putLong(KEY_LAST_SUB_TIMESTAMP, serverTimestamp)
                .putString(KEY_KNOWN_SYNCED_SUBS, appGson.toJson(finalLocalFeedUrls))
                .putString(KEY_PENDING_SUB_ADDS, "[]")
                .putString(KEY_PENDING_SUB_REMOVES, "[]")
                .apply()
        }

        return SubSyncStats(
            addedLocal = addedLocalCount,
            removedLocal = removedLocalCount,
            pushedRemote = pushedRemoteCount,
            totalLocalSubs = finalLocalFeedUrls.size
        )
    }

    private data class EpisodeSyncStats(
        val pulledActions: Int,
        val pushedActions: Int
    )

    private suspend fun syncEpisodeActionsInternal(
        context: Context,
        baseUrl: String,
        username: String,
        password: String,
        deviceId: String,
        isNextcloud: Boolean
    ): EpisodeSyncStats {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastEpTimestamp = sp.getLong(KEY_LAST_EP_TIMESTAMP, 0L)
        val isInitialEpSync = lastEpTimestamp == 0L

        // Make sure we have episode->feed mappings for all current subscriptions so episode actions can be matched
        ensureSubscriptionEpisodeMappingsLoaded(context)

        val encodedUser = Uri.encode(username)
        val pullUrl = if (isNextcloud) {
            "$baseUrl/episode_action?since=$lastEpTimestamp"
        } else {
            "$baseUrl/api/2/episodes/$encodedUser.json?since=$lastEpTimestamp"
        }

        var remoteActions = emptyList<GPodderEpisodeAction>()
        var serverEpTimestamp = lastEpTimestamp

        executeGPodderCall(
            url = pullUrl,
            method = "GET",
            username = username,
            password = password
        ).use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw IllegalStateException("Authentication failed while syncing episode actions (HTTP ${resp.code}).")
            }
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string()?.trim().orEmpty()
                if (bodyStr.startsWith("{")) {
                    val parsed = appGson.fromJson(bodyStr, GPodderEpisodePullResponse::class.java)
                    remoteActions = parsed?.actions?.filterNotNull()
                        ?.filter { !it.episode.isNullOrBlank() && !it.action.isNullOrBlank() }
                        .orEmpty()
                    if (parsed?.timestamp != null && parsed.timestamp > 0L) {
                        serverEpTimestamp = parsed.timestamp
                    }
                } else if (bodyStr.startsWith("[")) {
                    val listType = object : TypeToken<List<GPodderEpisodeAction?>>() {}.type
                    val parsedList: List<GPodderEpisodeAction?>? = appGson.fromJson(bodyStr, listType)
                    remoteActions = parsedList?.filterNotNull()
                        ?.filter { !it.episode.isNullOrBlank() && !it.action.isNullOrBlank() }
                        .orEmpty()
                }
            }
        }

        val prefsSnapshot = context.dataStore.data.first()
        val playedKey = stringSetPreferencesKey("played_episodes")
        val archivedKey = stringSetPreferencesKey("archived_episodes")
        val keepUnarchivedKey = stringSetPreferencesKey("keep_unarchived_episodes")
        val autoArchiveKey = booleanPreferencesKey("auto_archive_enabled")

        val currentPlayed = (prefsSnapshot[playedKey] ?: emptySet()).toMutableSet()
        val currentArchived = (prefsSnapshot[archivedKey] ?: emptySet()).toMutableSet()
        val keepUnarchived = prefsSnapshot[keepUnarchivedKey] ?: emptySet()
        val autoArchiveEnabled = prefsSnapshot[autoArchiveKey] ?: true

        val posPrefs = context.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        val currentPositions = loadPositionsFromPrefs(posPrefs).toMutableMap()

        // Sort remote actions chronologically by timestamp so the latest action for each episode wins
        val sortedRemoteActions = remoteActions.withIndex()
            .sortedWith(compareBy<IndexedValue<GPodderEpisodeAction>> { parseGPodderTimestampMs(it.value.timestamp) }.thenBy { it.index })
            .map { it.value }

        val remoteTouchedEpisodes = HashSet<String>()
        var pulledAppliedCount = 0

        for (remote in sortedRemoteActions) {
            val rawEpisodeUrl = remote.episode.trim()
            if (rawEpisodeUrl.isBlank()) continue
            val canonicalEpUrl = resolveCanonicalEpisodeUrl(rawEpisodeUrl, remote.podcast)
            val podcastUrl = remote.podcast.trim()
            if (podcastUrl.isNotBlank()) {
                episodeToFeedMap[canonicalEpUrl] = podcastUrl
                if (rawEpisodeUrl != canonicalEpUrl) {
                    episodeToFeedMap[rawEpisodeUrl] = podcastUrl
                }
            }
            if (remote.total != null && remote.total > 0) {
                episodeDurationSecMap[canonicalEpUrl] = remote.total
                if (rawEpisodeUrl != canonicalEpUrl) {
                    episodeDurationSecMap[rawEpisodeUrl] = remote.total
                }
            }

            val act = remote.action.lowercase(Locale.ROOT).trim()
            when (act) {
                "play" -> {
                    remoteTouchedEpisodes.add(canonicalEpUrl)
                    val posSec = (remote.position ?: 0).coerceAtLeast(0)
                    val totalSec = (remote.total ?: episodeDurationSecMap[canonicalEpUrl] ?: 0).coerceAtLeast(0)
                    val isCompleted = totalSec > 0 && posSec >= maxOf(1, totalSec - 5)

                    if (isCompleted) {
                        currentPlayed.add(canonicalEpUrl)
                        currentPositions.remove(canonicalEpUrl)
                        if (autoArchiveEnabled && !keepUnarchived.contains(canonicalEpUrl)) {
                            currentArchived.add(canonicalEpUrl)
                        }
                        pulledAppliedCount++
                    } else if (posSec > 0) {
                        val posMs = posSec * 1000L
                        currentPositions[canonicalEpUrl] = posMs
                        currentPlayed.remove(canonicalEpUrl)
                        currentArchived.remove(canonicalEpUrl)
                        pulledAppliedCount++
                    } else {
                        // position == 0 -> reset to unplayed
                        currentPositions.remove(canonicalEpUrl)
                        currentPlayed.remove(canonicalEpUrl)
                        currentArchived.remove(canonicalEpUrl)
                        pulledAppliedCount++
                    }
                }
                "new" -> {
                    remoteTouchedEpisodes.add(canonicalEpUrl)
                    currentPlayed.remove(canonicalEpUrl)
                    currentArchived.remove(canonicalEpUrl)
                    currentPositions.remove(canonicalEpUrl)
                    pulledAppliedCount++
                }
                "delete" -> {
                    remoteTouchedEpisodes.add(canonicalEpUrl)
                    currentArchived.add(canonicalEpUrl)
                    pulledAppliedCount++
                }
            }
        }

        // Persist updated positions & played/archived states if remote actions changed them
        if (pulledAppliedCount > 0) {
            val posEditor = posPrefs.edit()
            posEditor.clear()
            for ((k, v) in currentPositions) {
                if (v > 0L) {
                    posEditor.putLong(k, v)
                }
            }
            posEditor.apply()

            // Also update last_position in expressopod_state if the currently loaded episode's position was synced
            val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val lastEpJson = statePrefs.getString("last_episode", null)
            if (lastEpJson != null) {
                try {
                    val lastEp = appGson.fromJson(lastEpJson, Episode::class.java)
                    val epUrl = lastEp?.audioUrl?.trim()
                    if (!epUrl.isNullOrBlank() && remoteTouchedEpisodes.contains(epUrl)) {
                        val newPosMs = currentPositions[epUrl] ?: 0L
                        statePrefs.edit().putLong("last_position", newPosMs).apply()
                    }
                } catch (_: Exception) {}
            }

            val positionsJson = appGson.toJson(currentPositions)
            context.dataStore.edit { editPrefs ->
                editPrefs[playedKey] = currentPlayed
                editPrefs[archivedKey] = currentArchived
                editPrefs[stringPreferencesKey("playback_positions")] = positionsJson
            }
        }

        // Build outgoing local actions to push to server
        val actionsToPushMap = LinkedHashMap<String, GPodderEpisodeAction>()
        synchronized(this) {
            val queued = loadEpisodeActionsJson(sp.getString(KEY_PENDING_EP_ACTIONS, null))
            for (q in queued) {
                if (q.podcast.isNotBlank() && q.episode.isNotBlank()) {
                    actionsToPushMap[q.episode] = q
                }
            }
        }

        val prevSnapshotPlayed = loadStringSetJson(sp.getString(KEY_SNAPSHOT_PLAYED, null))
        val prevSnapshotArchived = loadStringSetJson(sp.getString(KEY_SNAPSHOT_ARCHIVED, null))
        val prevSnapshotPositions = loadStringLongMapJson(sp.getString(KEY_SNAPSHOT_POSITIONS, null))
        val nowIso = formatGPodderTimestamp(System.currentTimeMillis())

        if (isInitialEpSync) {
            // On initial sync, push local played episodes and in-progress positions that weren't just overwritten by server
            for (epUrl in currentPlayed) {
                if (!remoteTouchedEpisodes.contains(epUrl) && !actionsToPushMap.containsKey(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        val durSec = (episodeDurationSecMap[epUrl] ?: 1).coerceAtLeast(1)
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "play",
                            timestamp = nowIso,
                            started = 0,
                            position = durSec,
                            total = durSec
                        )
                    }
                }
            }
            for ((epUrl, posMs) in currentPositions) {
                if (posMs > 0L && !currentPlayed.contains(epUrl) && !remoteTouchedEpisodes.contains(epUrl) && !actionsToPushMap.containsKey(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        val posSec = (posMs / 1000L).toInt().coerceAtLeast(1)
                        val durSec = episodeDurationSecMap[epUrl]?.takeIf { it > 0 }?.let { maxOf(it, posSec) }
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "play",
                            timestamp = nowIso,
                            started = 0,
                            position = posSec,
                            total = durSec
                        )
                    }
                }
            }
        } else {
            // Snapshot diffing for any state or position changes that occurred in PlaybackService or offline
            for (epUrl in (currentPlayed - prevSnapshotPlayed)) {
                if (!remoteTouchedEpisodes.contains(epUrl) && !actionsToPushMap.containsKey(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        val durSec = (episodeDurationSecMap[epUrl] ?: 1).coerceAtLeast(1)
                        val prevSec = ((prevSnapshotPositions[epUrl] ?: 0L) / 1000L).toInt().coerceIn(0, durSec)
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "play",
                            timestamp = nowIso,
                            started = prevSec,
                            position = durSec,
                            total = durSec
                        )
                    }
                }
            }
            for (epUrl in (prevSnapshotPlayed - currentPlayed)) {
                if (!remoteTouchedEpisodes.contains(epUrl) && !actionsToPushMap.containsKey(epUrl) && !currentPositions.containsKey(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "new",
                            timestamp = nowIso
                        )
                    }
                }
            }
            for ((epUrl, posMs) in currentPositions) {
                val prevPosMs = prevSnapshotPositions[epUrl] ?: 0L
                if (posMs > 0L && abs(posMs - prevPosMs) >= 3_000L && !currentPlayed.contains(epUrl) && !remoteTouchedEpisodes.contains(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        val posSec = (posMs / 1000L).toInt().coerceAtLeast(1)
                        val startSec = (prevPosMs / 1000L).toInt().coerceAtLeast(0)
                        val durSec = episodeDurationSecMap[epUrl]?.takeIf { it > 0 }?.let { maxOf(it, posSec) }
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "play",
                            timestamp = nowIso,
                            started = startSec,
                            position = posSec,
                            total = durSec
                        )
                    }
                }
            }
            for (epUrl in (currentArchived - prevSnapshotArchived)) {
                if (!remoteTouchedEpisodes.contains(epUrl) && !actionsToPushMap.containsKey(epUrl)) {
                    val feedUrl = findPodcastUrlForEpisode(context, epUrl)
                    if (feedUrl.isNotBlank()) {
                        actionsToPushMap[epUrl] = GPodderEpisodeAction(
                            podcast = feedUrl,
                            episode = epUrl,
                            device = deviceId,
                            action = "delete",
                            timestamp = nowIso
                        )
                    }
                }
            }
        }

        val actionsToUpload = actionsToPushMap.values.toList()
        var pushedActionsCount = 0

        if (actionsToUpload.isNotEmpty()) {
            val pushUrl = if (isNextcloud) {
                "$baseUrl/episode_action/create"
            } else {
                "$baseUrl/api/2/episodes/$encodedUser.json"
            }
            // Upload in chunks of 100 actions to respect server payload limits
            for (chunk in actionsToUpload.chunked(100)) {
                val jsonPayload = appGson.toJson(chunk)
                executeGPodderCall(
                    url = pushUrl,
                    method = "POST",
                    username = username,
                    password = password,
                    jsonBody = jsonPayload
                ).use { pushResp ->
                    if (pushResp.isSuccessful) {
                        pushedActionsCount += chunk.size
                        val bodyStr = pushResp.body?.string()?.trim().orEmpty()
                        if (bodyStr.startsWith("{")) {
                            try {
                                val pushParsed = appGson.fromJson(bodyStr, GPodderPushResponse::class.java)
                                if (pushParsed?.timestamp != null && pushParsed.timestamp > 0L) {
                                    serverEpTimestamp = maxOf(serverEpTimestamp, pushParsed.timestamp)
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        throw IllegalStateException("Failed to upload episode actions (HTTP ${pushResp.code}).")
                    }
                }
            }
        }

        if (serverEpTimestamp == 0L) {
            serverEpTimestamp = System.currentTimeMillis() / 1000L
        }

        synchronized(this) {
            sp.edit()
                .putLong(KEY_LAST_EP_TIMESTAMP, serverEpTimestamp)
                .putString(KEY_PENDING_EP_ACTIONS, "[]")
                .putString(KEY_SNAPSHOT_PLAYED, appGson.toJson(currentPlayed))
                .putString(KEY_SNAPSHOT_ARCHIVED, appGson.toJson(currentArchived))
                .putString(KEY_SNAPSHOT_POSITIONS, appGson.toJson(currentPositions))
                .apply()
        }

        return EpisodeSyncStats(
            pulledActions = pulledAppliedCount,
            pushedActions = pushedActionsCount
        )
    }

    private suspend fun ensureSubscriptionEpisodeMappingsLoaded(context: Context) {
        val prefsSnapshot = context.dataStore.data.first()
        val subs = SubscriptionStorageManager.parseSubscriptionsJson(
            prefsSnapshot[stringPreferencesKey("subscriptions")]
        ).ifEmpty {
            SubscriptionStorageManager.loadSubscriptionsFromBackup(context)
        }.filterNot { LocalAudioManager.isLocalPodcast(it) }

        if (subs.isEmpty()) return

        for (pod in subs) {
            val cached = NetworkModule.getCachedFeed(pod.feedUrl)
            if (cached != null && cached.episodes.isNotEmpty()) {
                registerFeedEpisodes(context, pod.feedUrl, cached.episodes)
            }
        }
    }

    private fun resolveCanonicalEpisodeUrl(rawEpisodeUrl: String, podcastUrl: String?): String {
        val trimmed = rawEpisodeUrl.trim()
        if (episodeToFeedMap.containsKey(trimmed)) return trimmed
        val schemeStripped = stripScheme(trimmed)
        val matched = episodeToFeedMap.entries.firstOrNull { (epKey, feedVal) ->
            (podcastUrl.isNullOrBlank() || urlsEquivalent(feedVal, podcastUrl)) &&
                stripScheme(epKey) == schemeStripped
        }?.key
        return matched ?: trimmed
    }

    private fun stripScheme(url: String): String {
        return url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }

    private fun urlsEquivalent(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.trim() == b.trim()) return true
        return stripScheme(a).equals(stripScheme(b), ignoreCase = true)
    }

    private fun deriveTitleFromUrl(feedUrl: String): String {
        return try {
            val uri = Uri.parse(feedUrl)
            val lastSeg = uri.lastPathSegment
                ?.removeSuffix(".xml")
                ?.removeSuffix(".rss")
                ?.replace(Regex("[-_]"), " ")
                ?.trim()
            if (!lastSeg.isNullOrBlank() && !lastSeg.equals("feed", ignoreCase = true) && !lastSeg.equals("rss", ignoreCase = true)) {
                lastSeg.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else {
                uri.host?.removePrefix("www.")?.removePrefix("feeds.") ?: "Podcast"
            }
        } catch (_: Exception) {
            "Podcast"
        }
    }

    private fun saveEpisodesForBadgeCache(context: Context, feedUrl: String, episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        try {
            val normKey = FeedCredentialsManager.normalizeUrlKey(feedUrl)
            val urls = episodes.mapNotNull { it.audioUrl.trim().takeIf { u -> u.isNotBlank() } }.distinct()
            val sp = context.getSharedPreferences("expressopod_sub_episodes_cache", Context.MODE_PRIVATE)
            val existingJson = sp.getString("feed_episodes_map", null)
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val map = if (!existingJson.isNullOrBlank()) {
                (appGson.fromJson<Map<String, List<String>>>(existingJson, type) ?: emptyMap()).toMutableMap()
            } else {
                mutableMapOf()
            }
            if (normKey.isNotBlank()) {
                map[normKey] = urls
            }
            map[feedUrl.trim()] = urls
            sp.edit().putString("feed_episodes_map", appGson.toJson(map)).apply()
        } catch (_: Exception) {}
    }

    fun formatGPodderTimestamp(epochMs: Long): String {
        return try {
            utcFormatter.format(Instant.ofEpochMilli(epochMs))
        } catch (_: Exception) {
            "2026-01-01T00:00:00Z"
        }
    }

    fun parseGPodderTimestampMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        // Pure epoch seconds or milliseconds
        trimmed.toLongOrNull()?.let { num ->
            return if (num < 100_000_000_000L) num * 1000L else num
        }
        return try {
            Instant.parse(trimmed).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val normalized = trimmed.replace(" ", "T").removeSuffix("Z")
                    LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    private fun loadPositionsFromPrefs(posPrefs: android.content.SharedPreferences): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        try {
            for ((k, v) in posPrefs.all) {
                if (k.isNotBlank() && v is Long && v > 0L) {
                    result[k] = v
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun loadStringSetJson(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<Set<String?>>() {}.type
            val raw: Set<String?>? = appGson.fromJson(json, type)
            raw?.mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun loadStringLongMapJson(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Long?>>() {}.type
            val raw: Map<String, Long?>? = appGson.fromJson(json, type)
            raw?.mapNotNull { (k, v) -> if (k.isNotBlank() && v != null) k to v else null }?.toMap() ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadEpisodeActionsJson(json: String?): List<GPodderEpisodeAction> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<GPodderEpisodeAction?>>() {}.type
            val raw: List<GPodderEpisodeAction?>? = appGson.fromJson(json, type)
            raw?.filterNotNull().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadStateFromPrefs(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = sp.getBoolean(KEY_ENABLED, false)
            val serverTypeId = sp.getString(KEY_SERVER_TYPE, GPodderServerType.STANDARD.id)
            val serverType = GPodderServerType.fromId(serverTypeId)
            val serverUrl = sp.getString(KEY_SERVER_URL, "") ?: ""
            val username = sp.getString(KEY_USERNAME, "") ?: ""
            val encryptedPass = sp.getString(KEY_ENCRYPTED_PASSWORD, null)
            val password = if (!encryptedPass.isNullOrBlank()) {
                decryptPassword(encryptedPass) ?: ""
            } else ""
            val deviceId = sp.getString(KEY_DEVICE_ID, GPodderConfig.DEFAULT_DEVICE_ID) ?: GPodderConfig.DEFAULT_DEVICE_ID
            val intervalMin = sp.getInt(KEY_SYNC_INTERVAL_MIN, GPodderSyncInterval.HOURS_1.minutes)

            val lastSyncWallMs = sp.getLong(KEY_LAST_SYNC_WALL_MS, 0L)
            val hasResult = sp.getBoolean(KEY_LAST_SYNC_HAS_RESULT, false)
            val lastSuccess = if (hasResult) sp.getBoolean(KEY_LAST_SYNC_SUCCESS, true) else null
            val lastMsg = sp.getString(KEY_LAST_SYNC_MESSAGE, null)

            val epFeedJson = sp.getString(KEY_EPISODE_FEED_MAP, null)
            if (!epFeedJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loaded: Map<String, String>? = appGson.fromJson(epFeedJson, type)
                if (loaded != null) {
                    episodeToFeedMap.clear()
                    episodeToFeedMap.putAll(loaded)
                }
            }

            val epDurJson = sp.getString(KEY_EPISODE_DURATION_MAP, null)
            if (!epDurJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                val loaded: Map<String, Int>? = appGson.fromJson(epDurJson, type)
                if (loaded != null) {
                    episodeDurationSecMap.clear()
                    episodeDurationSecMap.putAll(loaded)
                }
            }

            val config = GPodderConfig(
                enabled = enabled,
                serverType = serverType,
                serverUrl = serverUrl,
                username = username,
                password = password,
                deviceId = deviceId,
                syncIntervalMinutes = GPodderSyncInterval.fromMinutes(intervalMin).minutes
            )

            _syncState.value = GPodderSyncState(
                config = config,
                lastSyncTimestampMs = lastSyncWallMs,
                lastSyncSuccess = lastSuccess,
                lastSyncMessage = lastMsg
            )
        } catch (_: Exception) {}
    }

    private fun persistEpisodeMetadataMaps(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (episodeToFeedMap.size > 6000) {
                val keepKeys = episodeToFeedMap.keys.take(4500).toSet()
                episodeToFeedMap.keys.retainAll(keepKeys)
            }
            if (episodeDurationSecMap.size > 6000) {
                val keepKeys = episodeDurationSecMap.keys.take(4500).toSet()
                episodeDurationSecMap.keys.retainAll(keepKeys)
            }
            sp.edit()
                .putString(KEY_EPISODE_FEED_MAP, appGson.toJson(HashMap(episodeToFeedMap)))
                .putString(KEY_EPISODE_DURATION_MAP, appGson.toJson(HashMap(episodeDurationSecMap)))
                .apply()
        } catch (_: Exception) {}
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptString(plainText: String): String? {
        return try {
            val key = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val data = Base64.encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            "$iv:$data"
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptPassword(payload: String): String? {
        if (payload.startsWith("plain:")) {
            return try {
                String(Base64.decode(payload.removePrefix("plain:"), Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
        return try {
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

class GPodderSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            GPodderSyncManager.init(context)
            val cfg = GPodderSyncManager.syncState.value.config
            if (!cfg.enabled || !cfg.isConfigured) {
                return Result.success()
            }
            when (GPodderSyncManager.performSync(context)) {
                is GPodderSyncResult.Success -> Result.success()
                is GPodderSyncResult.Error -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "GPodderPeriodicSyncWork"
        private const val ONE_TIME_WORK_NAME = "GPodderImmediateSyncWork"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
            val validMinutes = GPodderSyncInterval.fromMinutes(intervalMinutes).minutes.toLong()
                .coerceAtLeast(15L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<GPodderSyncWorker>(
                validMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<GPodderSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
