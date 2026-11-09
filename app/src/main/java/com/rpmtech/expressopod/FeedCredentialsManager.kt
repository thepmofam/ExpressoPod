package com.rpmtech.expressopod

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Credentials
import okhttp3.Request
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Keep
data class FeedCredentials(
    @SerializedName("username") val username: String = "",
    @SerializedName("password") val password: String = ""
) {
    val isValid: Boolean
        get() = username.isNotBlank() || password.isNotBlank()

    fun toBasicAuthHeader(): String {
        return Credentials.basic(username, password, Charsets.UTF_8)
    }
}

/**
 * Manages authentication credentials (username & password) for custom / private podcast RSS feeds.
 * Encrypts credentials at rest using Android Keystore (AES-256-GCM) and provides fast lookups
 * for RSS feed fetching, episode downloads, background sync, and ExoPlayer media streaming.
 */
object FeedCredentialsManager {
    private const val PREFS_NAME = "expressopod_feed_credentials"
    private const val KEY_ENCRYPTED_MAP = "encrypted_credentials_v1"
    private const val KEY_AUDIO_FEED_MAP = "audio_to_feed_map_v1"
    private const val KEYSTORE_ALIAS = "expressopod_rss_auth_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private val gson = Gson()
    private val credsMap = ConcurrentHashMap<String, FeedCredentials>()
    private val audioToFeedMap = ConcurrentHashMap<String, String>()

    @Keep
    data class ResolvedStreamInfo(
        val finalUrl: String,
        val attachAuthHeader: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val resolvedStreamCache = ConcurrentHashMap<String, ResolvedStreamInfo>()
    private const val RESOLVED_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    private val _authenticatedFeedUrls = MutableStateFlow<Set<String>>(emptySet())
    val authenticatedFeedUrls: StateFlow<Set<String>> = _authenticatedFeedUrls.asStateFlow()

    @Volatile
    private var isInitialized = false
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        loadFromPrefs(context.applicationContext)
        isInitialized = true
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            appContext?.let { init(it) }
        }
    }

    fun normalizeUrlKey(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return SecurityUtils.normalizeFeedUrl(url)?.trim() ?: url.trim()
    }

    fun hasCredentials(feedUrl: String?): Boolean {
        ensureInitialized()
        val key = normalizeUrlKey(feedUrl)
        if (key.isEmpty()) return false
        return credsMap[key]?.isValid == true
    }

    fun getCredentials(feedUrl: String?): FeedCredentials? {
        ensureInitialized()
        val key = normalizeUrlKey(feedUrl)
        if (key.isEmpty()) return null
        return credsMap[key]?.takeIf { it.isValid }
    }

    fun saveCredentials(context: Context, feedUrl: String, username: String, password: String) {
        init(context)
        val key = normalizeUrlKey(feedUrl)
        if (key.isEmpty()) return

        val trimmedUser = username.trim()
        val cleanCreds = FeedCredentials(trimmedUser, password)
        if (cleanCreds.isValid) {
            credsMap[key] = cleanCreds
        } else {
            credsMap.remove(key)
        }
        resolvedStreamCache.clear()
        _authenticatedFeedUrls.value = credsMap.keys.toSet()
        persistToPrefs(context.applicationContext)
    }

    fun removeCredentials(context: Context, feedUrl: String) {
        saveCredentials(context, feedUrl, "", "")
    }

    fun registerEpisodesForFeed(context: Context?, feedUrl: String, audioUrls: Collection<String>) {
        if (context != null) init(context) else ensureInitialized()
        val normFeed = normalizeUrlKey(feedUrl)
        if (normFeed.isEmpty() || audioUrls.isEmpty()) return
        var changed = false
        for (audioUrl in audioUrls) {
            val cleanAudio = audioUrl.trim()
            if (cleanAudio.isNotNotBlank() && audioToFeedMap[cleanAudio] != normFeed) {
                audioToFeedMap[cleanAudio] = normFeed
                changed = true
            }
        }
        if (changed) {
            (context?.applicationContext ?: appContext)?.let { persistAudioMapToPrefs(it) }
        }
    }

    private fun String.isNotNotBlank(): Boolean = this.isNotBlank()

    /**
     * Resolves credentials for any target URL (RSS feed, episode audio URL, chapter URL, or artwork).
     */
    fun getCredentialsForUrl(targetUrl: String?, fallbackFeedUrl: String? = null): FeedCredentials? {
        ensureInitialized()
        if (targetUrl.isNullOrBlank() && fallbackFeedUrl.isNullOrBlank()) return null

        // 1. Check embedded credentials in the target URL itself (e.g. https://user:pass@host/path)
        if (!targetUrl.isNullOrBlank()) {
            val (_, embedded) = extractEmbeddedCredentials(targetUrl)
            if (embedded != null && embedded.isValid) {
                return embedded
            }
        }

        // 2. Direct match on targetUrl as a feedUrl
        if (!targetUrl.isNullOrBlank()) {
            getCredentials(targetUrl)?.let { return it }
        }

        // 3. Match via registered episode audioUrl -> feedUrl mapping
        if (!targetUrl.isNullOrBlank()) {
            val mappedFeed = audioToFeedMap[targetUrl.trim()]
            if (!mappedFeed.isNullOrBlank()) {
                getCredentials(mappedFeed)?.let { return it }
            }
        }

        // 4. Match via explicit fallbackFeedUrl (e.g. podcast.feedUrl)
        if (!fallbackFeedUrl.isNullOrBlank()) {
            getCredentials(fallbackFeedUrl)?.let { return it }
        }

        // 5. Host-based match if only one authenticated feed shares the exact host
        if (!targetUrl.isNullOrBlank()) {
            val targetHost = getHostLower(targetUrl)
            if (!targetHost.isNullOrBlank()) {
                val matchingCreds = credsMap.entries.firstOrNull { (feedKey, creds) ->
                    creds.isValid && getHostLower(feedKey) == targetHost
                }?.value
                if (matchingCreds != null) return matchingCreds
            }
        }

        return null
    }

    /**
     * Extracts user:password from URLs formatted as https://username:password@example.com/feed.xml
     * Returns Pair(cleanedUrlWithoutUserInfo, extractedCredentialsOrNull).
     */
    fun extractEmbeddedCredentials(rawUrl: String): Pair<String, FeedCredentials?> {
        val trimmed = rawUrl.trim()
        return try {
            val normalized = SecurityUtils.normalizeFeedUrl(trimmed) ?: trimmed
            val uri = Uri.parse(normalized)
            val userInfo = uri.userInfo
            if (userInfo.isNullOrBlank()) {
                Pair(normalized, null)
            } else {
                val colonIndex = userInfo.indexOf(':')
                val user = if (colonIndex >= 0) Uri.decode(userInfo.substring(0, colonIndex)) else Uri.decode(userInfo)
                val pass = if (colonIndex >= 0) Uri.decode(userInfo.substring(colonIndex + 1)) else ""
                val host = uri.host ?: return Pair(normalized, null)
                val portPart = if (uri.port != -1) ":${uri.port}" else ""
                val cleanAuthority = "$host$portPart"
                val cleanUri = uri.buildUpon().encodedAuthority(cleanAuthority).build().toString()
                Pair(cleanUri, FeedCredentials(user, pass).takeIf { it.isValid })
            }
        } catch (_: Exception) {
            Pair(trimmed, null)
        }
    }

    /**
     * Determines whether an Authorization header can be safely attached directly to a URL.
     * Pre-signed CDN URLs (e.g. AWS S3 X-Amz-Signature, Google Cloud Storage, CloudFront) reject
     * requests that include both query-string signatures and an HTTP Authorization header.
     */
    fun hasConflictingQueryAuth(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("x-amz-signature=") ||
            lower.contains("x-amz-credential=") ||
            lower.contains("x-goog-signature=") ||
            lower.contains("sig=") && lower.contains("se=") && lower.contains("sv=") || // Azure SAS
            lower.contains(" awSAccessKeyId=".lowercase(Locale.ROOT)) ||
            lower.contains("?signature=") ||
            lower.contains("&signature=")
    }

    fun shouldAttachAuthDirectly(targetUrl: String, feedUrl: String? = null): Boolean {
        if (hasConflictingQueryAuth(targetUrl)) return false
        if (feedUrl.isNullOrBlank()) return true
        val targetHost = getHostLower(targetUrl) ?: return false
        val feedHost = getHostLower(feedUrl) ?: return true
        return targetHost == feedHost || targetHost.endsWith(".$feedHost") || feedHost.endsWith(".$targetHost")
    }

    /**
     * Resolves an episode stream URL for ExoPlayer when the podcast has Basic Auth credentials.
     * Follows redirects via OkHttp (which sends Basic Auth to the podcast server and automatically
     * strips Authorization when redirecting across domains to S3 / CDNs) so ExoPlayer can stream
     * either direct Basic Auth audio or 302-redirected signed CDN URLs seamlessly.
     */
    fun resolveStreamForExoPlayer(audioUrl: String, feedUrl: String?): ResolvedStreamInfo? {
        val creds = getCredentialsForUrl(audioUrl, feedUrl) ?: return null
        val cached = resolvedStreamCache[audioUrl]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestampMs) < RESOLVED_CACHE_TTL_MS) {
            return cached
        }

        return try {
            val cleanUrl = extractEmbeddedCredentials(audioUrl).first
            val reqBuilder = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "ExpressoPod/1.0")
                .header("Range", "bytes=0-0")
            if (!hasConflictingQueryAuth(cleanUrl)) {
                reqBuilder.header("Authorization", creds.toBasicAuthHeader())
            }
            NetworkModule.okHttp.newCall(reqBuilder.build()).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val finalHasAuth = resp.request.header("Authorization") != null && !hasConflictingQueryAuth(finalUrl)
                val info = ResolvedStreamInfo(
                    finalUrl = finalUrl,
                    attachAuthHeader = finalHasAuth,
                    timestampMs = now
                )
                if (resp.isSuccessful || resp.code == 206) {
                    resolvedStreamCache[audioUrl] = info
                }
                info
            }
        } catch (_: Exception) {
            ResolvedStreamInfo(
                finalUrl = audioUrl,
                attachAuthHeader = shouldAttachAuthDirectly(audioUrl, feedUrl),
                timestampMs = now
            )
        }
    }

    private fun getHostLower(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            Uri.parse(url.trim()).host?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existingKey != null) return existingKey

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptString(plainText: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$ivB64:$dataB64"
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptString(encryptedPayload: String): String? {
        return try {
            val parts = encryptedPayload.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFromPrefs(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encrypted = sp.getString(KEY_ENCRYPTED_MAP, null)
            if (!encrypted.isNullOrBlank()) {
                val json = decryptString(encrypted)
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, FeedCredentials>>() {}.type
                    val loaded: Map<String, FeedCredentials>? = gson.fromJson(json, type)
                    if (loaded != null) {
                        credsMap.clear()
                        loaded.forEach { (k, v) ->
                            if (k.isNotBlank() && v.isValid) {
                                credsMap[k] = v
                            }
                        }
                        _authenticatedFeedUrls.value = credsMap.keys.toSet()
                    }
                }
            }

            val audioMapJson = sp.getString(KEY_AUDIO_FEED_MAP, null)
            if (!audioMapJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loadedAudioMap: Map<String, String>? = gson.fromJson(audioMapJson, type)
                if (loadedAudioMap != null) {
                    audioToFeedMap.clear()
                    audioToFeedMap.putAll(loadedAudioMap)
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistToPrefs(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (credsMap.isEmpty()) {
                sp.edit().remove(KEY_ENCRYPTED_MAP).apply()
                return
            }
            val json = gson.toJson(HashMap(credsMap))
            val encrypted = encryptString(json)
            if (encrypted != null) {
                sp.edit().putString(KEY_ENCRYPTED_MAP, encrypted).apply()
            }
        } catch (_: Exception) {}
    }

    private fun persistAudioMapToPrefs(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Keep size bounded
            if (audioToFeedMap.size > 5000) {
                val keysToKeep = audioToFeedMap.keys.take(4000)
                val trimmed = audioToFeedMap.filterKeys { it in keysToKeep }
                audioToFeedMap.clear()
                audioToFeedMap.putAll(trimmed)
            }
            val json = gson.toJson(HashMap(audioToFeedMap))
            sp.edit().putString(KEY_AUDIO_FEED_MAP, json).apply()
        } catch (_: Exception) {}
    }
}
