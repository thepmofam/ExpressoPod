package com.rpmtech.expressopod

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.appwidget.updateAll
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

object MediaCacheManager {
    private var simpleCache: SimpleCache? = null

    @androidx.media3.common.util.UnstableApi
    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media_stream_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024L) // 250 MB LRU Cache
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }
}

@androidx.media3.common.util.UnstableApi
class PodcastNotificationBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : androidx.media3.common.util.BitmapLoader {

    companion object {
        private const val MAX_BITMAP_DIM = 512
        private const val MAX_IMAGE_BYTES = 25 * 1024 * 1024
    }

    private val bitmapCache = LruCache<String, Bitmap>(16)
    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, ListenableFuture<Bitmap>>()
    @Volatile
    private var fallbackBitmap: Bitmap? = null

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val (savedPod, savedEp) = loadSavedPodcastAndEpisode()
        val fallbackArtUrl = ArtworkResolver.resolveBestArtworkUrl(context, savedPod, savedEp)
        val cacheKey = "bytes:${data.contentHashCode()}:$fallbackArtUrl"
        getCachedBitmap(cacheKey)?.let { return Futures.immediateFuture(it) }

        return executeCoalesced(cacheKey) {
            val decoded = decodeArtworkBytesOrUrl(data, savedPod?.feedUrl)
                ?: fallbackArtUrl.takeIf { it.isNotBlank() }?.let { fetchOrDecodeFromUrl(it, savedPod?.feedUrl) }
                ?: fetchFeedArtworkIfNeeded(savedPod)
            if (decoded != null) {
                putCachedBitmap(cacheKey, decoded)
                decoded
            } else {
                getOrCreateFallbackBitmap()
            }
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val rawUrl = uri.toString().trim()
        val (savedPod, savedEp) = loadSavedPodcastAndEpisode()
        val resolvedArtUrl = ArtworkResolver.resolveBestArtworkUrl(context, savedPod, savedEp)
        val primaryUrl = rawUrl.ifBlank { resolvedArtUrl }
        val secondaryUrl = resolvedArtUrl.takeIf { it.isNotBlank() && it != primaryUrl }
        val cacheKey = "url:$primaryUrl"

        getCachedBitmap(cacheKey)?.let { return Futures.immediateFuture(it) }

        return executeCoalesced(cacheKey) {
            val bmp = primaryUrl.takeIf { it.isNotBlank() }?.let { fetchOrDecodeFromUrl(it, savedPod?.feedUrl) }
                ?: secondaryUrl?.let { fetchOrDecodeFromUrl(it, savedPod?.feedUrl) }
                ?: fetchFeedArtworkIfNeeded(savedPod)
            if (bmp != null) {
                putCachedBitmap(cacheKey, bmp)
                bmp
            } else {
                getOrCreateFallbackBitmap()
            }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        val (savedPod, savedEp) = loadSavedPodcastAndEpisode()
        val metadataUriStr = metadata.artworkUri?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val resolvedSavedUrl = ArtworkResolver.resolveBestArtworkUrl(context, savedPod, savedEp)
        val primaryUrl = metadataUriStr ?: resolvedSavedUrl.takeIf { it.isNotBlank() }
        val secondaryUrl = resolvedSavedUrl.takeIf { it.isNotBlank() && it != primaryUrl }
        val embeddedBytes = metadata.artworkData?.takeIf { it.isNotEmpty() }
        val feedUrl = savedPod?.feedUrl?.trim()?.takeIf { it.isNotBlank() }

        val cacheKey = when {
            !primaryUrl.isNullOrBlank() -> "url:$primaryUrl"
            embeddedBytes != null -> "bytes:${embeddedBytes.contentHashCode()}"
            !feedUrl.isNullOrBlank() -> "feed:$feedUrl"
            else -> "meta:${metadata.title ?: ""}:${metadata.artist ?: ""}"
        }

        getCachedBitmap(cacheKey)?.let { return Futures.immediateFuture(it) }

        return executeCoalesced(cacheKey) {
            val bmp = primaryUrl?.let { fetchOrDecodeFromUrl(it, feedUrl) }
                ?: secondaryUrl?.let { fetchOrDecodeFromUrl(it, feedUrl) }
                ?: embeddedBytes?.let { decodeArtworkBytesOrUrl(it, feedUrl) }
                ?: fetchFeedArtworkIfNeeded(savedPod)
                ?: decodeCachedWidgetArtworkIfMatches(primaryUrl ?: secondaryUrl)
            if (bmp != null) {
                putCachedBitmap(cacheKey, bmp)
                if (!primaryUrl.isNullOrBlank()) {
                    putCachedBitmap("url:$primaryUrl", bmp)
                }
                bmp
            } else {
                getOrCreateFallbackBitmap()
            }
        }
    }

    private fun getCachedBitmap(key: String): Bitmap? {
        return synchronized(bitmapCache) {
            val bmp = bitmapCache.get(key)
            if (bmp != null && !bmp.isRecycled) bmp else null
        }
    }

    private fun putCachedBitmap(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            synchronized(bitmapCache) {
                bitmapCache.put(key, bitmap)
            }
        }
    }

    private fun executeCoalesced(
        key: String,
        loaderBlock: suspend () -> Bitmap
    ): ListenableFuture<Bitmap> {
        inFlightRequests[key]?.let { existing ->
            if (!existing.isCancelled) return existing
        }
        val future = SettableFuture.create<Bitmap>()
        val prev = inFlightRequests.putIfAbsent(key, future)
        if (prev != null && !prev.isCancelled) {
            return prev
        }
        scope.launch(Dispatchers.IO) {
            try {
                val bmp = loaderBlock()
                future.set(bmp)
            } catch (_: Throwable) {
                try {
                    future.set(getOrCreateFallbackBitmap())
                } catch (e: Throwable) {
                    future.setException(e)
                }
            } finally {
                inFlightRequests.remove(key, future)
            }
        }
        return future
    }

    private fun loadSavedPodcastAndEpisode(): Pair<Podcast?, Episode?> {
        return try {
            val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val podJson = statePrefs.getString("last_podcast", null)
            val epJson = statePrefs.getString("last_episode", null)
            val pod = if (!podJson.isNullOrBlank()) appGson.fromJson(podJson, Podcast::class.java) else null
            val ep = if (!epJson.isNullOrBlank()) appGson.fromJson(epJson, Episode::class.java) else null
            Pair(pod, ep)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private suspend fun fetchFeedArtworkIfNeeded(savedPod: Podcast?): Bitmap? {
        val feedUrl = savedPod?.feedUrl?.trim() ?: return null
        if (!feedUrl.startsWith("http://", ignoreCase = true) && !feedUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return try {
            val parsed = NetworkModule.fetchPodcastFeed(feedUrl)
            val channelImg = ArtworkResolver.normalizeUrl(parsed?.channelImage, feedUrl)
            if (channelImg.isNotBlank()) {
                val bmp = fetchOrDecodeFromUrl(channelImg, feedUrl)
                if (bmp != null) {
                    try {
                        val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                        val curPodJson = statePrefs.getString("last_podcast", null)
                        val curPod = if (!curPodJson.isNullOrBlank()) appGson.fromJson(curPodJson, Podcast::class.java) else null
                        if (curPod != null && ArtworkResolver.feedsEquivalent(curPod.feedUrl, feedUrl) && curPod.artworkUrl.isBlank()) {
                            statePrefs.edit()
                                .putString("last_podcast", appGson.toJson(curPod.copy(artworkUrl = channelImg)))
                                .apply()
                        }
                    } catch (_: Exception) {}
                }
                bmp
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeCachedWidgetArtworkIfMatches(targetUrl: String?): Bitmap? {
        if (targetUrl.isNullOrBlank()) return null
        return try {
            val file = File(context.filesDir, "widget_artwork.png")
            if (file.exists() && file.length() > 0L) {
                decodeSampledBitmapFromFile(file, MAX_BITMAP_DIM)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun decodeArtworkBytesOrUrl(data: ByteArray, feedUrl: String?): Bitmap? {
        decodeSampledBitmapFromBytes(data, MAX_BITMAP_DIM)?.let { return it }
        // ID3v2 APIC frames with MIME type "-->" store an ASCII/UTF-8 URL instead of raw image bytes
        if (data.size in 8..2048) {
            val possibleUrl = try {
                String(data, Charsets.UTF_8).trim().trim('\u0000')
            } catch (_: Exception) {
                ""
            }
            if (possibleUrl.startsWith("http://", ignoreCase = true) || possibleUrl.startsWith("https://", ignoreCase = true)) {
                return fetchOrDecodeFromUrl(possibleUrl, feedUrl)
            }
        }
        return null
    }

    private suspend fun fetchOrDecodeFromUrl(rawUrl: String, feedUrl: String?): Bitmap? {
        val cleanUrl = ArtworkResolver.normalizeUrl(rawUrl, feedUrl)
        if (cleanUrl.isBlank()) return null

        getCachedBitmap("url:$cleanUrl")?.let { return it }

        val uri = try { Uri.parse(cleanUrl) } catch (_: Exception) { null }
        val scheme = uri?.scheme?.lowercase()

        if (scheme == "file" || cleanUrl.startsWith("/")) {
            val path = if (scheme == "file") uri.path else cleanUrl
            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists() && f.length() > 0L) {
                    decodeSampledBitmapFromFile(f, MAX_BITMAP_DIM)?.let { return it }
                }
            }
        } else if (scheme == "content" && uri != null) {
            decodeSampledBitmapFromContentUri(uri, MAX_BITMAP_DIM)?.let { return it }
        }

        // 1. Try shared Coil ImageLoader (checks memory cache, 64MB disk cache, and OkHttp with auth)
        loadViaCoil(cleanUrl)?.let { return it }

        // 2. Fallback to direct OkHttp authenticated call + BitmapFactory downsampling
        loadViaOkHttp(cleanUrl, feedUrl)?.let { return it }

        // 3. If cleartext http:// URL failed (e.g. redirect or SSL upgrade needed), retry with https://
        if (cleanUrl.startsWith("http://", ignoreCase = true)) {
            val httpsUrl = "https://" + cleanUrl.substring(7)
            loadViaCoil(httpsUrl)?.let { return it }
            loadViaOkHttp(httpsUrl, feedUrl)?.let { return it }
        }

        return null
    }

    private suspend fun loadViaCoil(url: String): Bitmap? {
        return try {
            val loader = coil.Coil.imageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .size(MAX_BITMAP_DIM, MAX_BITMAP_DIM)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build()
            val result = loader.execute(request)
            val drawable = (result as? coil.request.SuccessResult)?.drawable ?: return null
            drawableToSoftwareBitmap(drawable, MAX_BITMAP_DIM)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadViaOkHttp(url: String, feedUrl: String?): Bitmap? {
        return try {
            NetworkModule.executeAuthenticatedCall(
                url = url,
                feedUrl = feedUrl,
                acceptHeader = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                client = NetworkModule.downloadHttpClient
            ).use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentLen = body.contentLength()
                if (contentLen > MAX_IMAGE_BYTES) return null
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
                decodeSampledBitmapFromBytes(bytes, MAX_BITMAP_DIM)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToSoftwareBitmap(drawable: Drawable, maxDim: Int): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return ensureSoftwareAndScaled(drawable.bitmap, maxDim)
        }
        val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxDim
        val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxDim
        val scale = minOf(maxDim.toFloat() / intrinsicW.toFloat(), maxDim.toFloat() / intrinsicH.toFloat(), 1f)
        val w = (intrinsicW * scale).toInt().coerceAtLeast(1)
        val h = (intrinsicH * scale).toInt().coerceAtLeast(1)
        return try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, maxDim: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim, maxDim)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            ensureSoftwareAndScaled(decoded, maxDim)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampledBitmapFromFile(file: File, maxDim: Int): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim, maxDim)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            ensureSoftwareAndScaled(decoded, maxDim)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampledBitmapFromContentUri(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            decodeSampledBitmapFromBytes(bytes, maxDim)
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun ensureSoftwareAndScaled(src: Bitmap, maxDim: Int): Bitmap {
        val soft = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else if (src.config == null) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val w = soft.width
        val h = soft.height
        if (w <= 0 || h <= 0 || (w <= maxDim && h <= maxDim)) {
            return soft
        }
        val scale = minOf(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(soft, targetW, targetH, true)
        } catch (_: Throwable) {
            soft
        }
    }

    @Synchronized
    private fun getOrCreateFallbackBitmap(): Bitmap {
        val existing = fallbackBitmap
        if (existing != null && !existing.isRecycled) return existing

        val size = MAX_BITMAP_DIM
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.rgb(36, 30, 48)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val fgDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_notification)
        if (fgDrawable != null) {
            val iconSize = (size * 0.72f).toInt()
            val offset = (size - iconSize) / 2
            fgDrawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
            fgDrawable.draw(canvas)
        } else {
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
            }
            val cx = size / 2f
            val cy = size / 2f
            val rect = RectF(cx - 80f, cy - 80f, cx + 80f, cy + 80f)
            canvas.drawRoundRect(rect, 36f, 36f, barPaint)
        }
        fallbackBitmap = bmp
        return bmp
    }
}

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var saveJob: kotlinx.coroutines.Job? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var isVolumeBoostEnabled: Boolean = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasSystemAudioFocus: Boolean = false
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var serviceWifiLock: WifiManager.WifiLock? = null
    @Volatile
    private var currentRewindMs: Long = 15_000L
    @Volatile
    private var currentForwardMs: Long = 30_000L
    private var lastHandledEndedMediaId: String? = null
    private var isHandlingCompletion: Boolean = false

    companion object {
        const val ACTION_PLAY_PAUSE = "com.rpmtech.expressopod.PLAY_PAUSE"
        const val ACTION_REWIND = "com.rpmtech.expressopod.REWIND"
        const val ACTION_FORWARD = "com.rpmtech.expressopod.FORWARD"
        const val ACTION_PLAY_QUEUE_INDEX = "com.rpmtech.expressopod.PLAY_QUEUE_INDEX"
    }

    private fun buildSavedEpisodeMediaItem(): Pair<MediaItem, Long>? {
        val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        val epJson = statePrefs.getString("last_episode", null) ?: return null
        val podJson = statePrefs.getString("last_podcast", null) ?: return null
        val lastPos = statePrefs.getLong("last_position", 0L)
        return try {
            val ep = Gson().fromJson(epJson, Episode::class.java) ?: return null
            val rawPod = Gson().fromJson(podJson, Podcast::class.java) ?: return null
            if (ep.audioUrl.isBlank()) return null
            val pod = ArtworkResolver.enrichPodcastArtwork(applicationContext, rawPod, ep)
            if (pod != rawPod) {
                statePrefs.edit().putString("last_podcast", Gson().toJson(pod)).apply()
            }
            val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
            val epDurationMs = parseDurationMs(ep.durationText)
            val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos

            val metadata = MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(pod.title)
                .setArtworkUri(ArtworkResolver.resolveBestArtworkUri(applicationContext, pod, ep))
                .build()
            val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(ep.audioUrl)
                ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(ep.audioUrl)
            val playbackUri = localUri ?: Uri.parse(ep.audioUrl)
            val item = MediaItem.Builder()
                .setMediaId(ep.audioUrl)
                .setUri(playbackUri)
                .setMediaMetadata(metadata)
                .build()
            Pair(item, savedPos)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setupVolumeBoost(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        currentAudioSessionId = audioSessionId
        val prefs = applicationContext.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
        if (!isVolumeBoostEnabled && prefs.contains("volume_boost_enabled")) {
            isVolumeBoostEnabled = prefs.getBoolean("volume_boost_enabled", false)
        }
        applyVolumeBoostState()
    }

    private fun updateVolumeBoost(enabled: Boolean) {
        if (isVolumeBoostEnabled == enabled && (if (enabled) loudnessEnhancer != null else loudnessEnhancer == null)) {
            return
        }
        isVolumeBoostEnabled = enabled
        applyVolumeBoostState()
    }

    private fun applyVolumeBoostState() {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (isVolumeBoostEnabled && currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET && currentAudioSessionId != 0) {
                loudnessEnhancer = LoudnessEnhancer(currentAudioSessionId).apply {
                    setTargetGain(700) // +7.0 dB voice boost (noticeably richer speech boost while preventing distortion)
                    enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it }
        val player = mediaSession?.player
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasSystemAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val mode = am?.mode ?: AudioManager.MODE_NORMAL
                val isPhoneOrVoipCall = mode == AudioManager.MODE_IN_CALL ||
                    mode == AudioManager.MODE_IN_COMMUNICATION ||
                    mode == AudioManager.MODE_RINGTONE ||
                    mode == AudioManager.MODE_CALL_SCREENING
                if (isPhoneOrVoipCall) {
                    hasSystemAudioFocus = false
                    player?.pause()
                } else if (player?.playWhenReady == true &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED
                ) {
                    // Re-assert AUDIOFOCUS_GAIN if a non-call event (e.g. lock-screen sound, AOD transition,
                    // or background app UI sound) displaced focus while playback is still active,
                    // so Android's standby/Doze controller continues to recognize this service as the active media owner.
                    hasSystemAudioFocus = false
                    requestSystemAudioFocus()
                } else {
                    hasSystemAudioFocus = false
                }
            }
        }
    }

    private fun requestSystemAudioFocus() {
        try {
            val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
                val res = am.requestAudioFocus(req)
                hasSystemAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                hasSystemAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonSystemAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
            hasSystemAudioFocus = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquirePlaybackLocks() {
        try {
            if (serviceWakeLock == null) {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                serviceWakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ExpressoPod:PlaybackWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (serviceWakeLock?.isHeld == false) {
                serviceWakeLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (serviceWifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                serviceWifiLock = wm?.createWifiLock(lockMode, "ExpressoPod:PlaybackWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (serviceWifiLock?.isHeld == false) {
                serviceWifiLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releasePlaybackLocks() {
        try {
            if (serviceWakeLock?.isHeld == true) {
                serviceWakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (serviceWifiLock?.isHeld == true) {
                serviceWifiLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncPlaybackFocusAndLocks(player: Player) {
        val active = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        if (active) {
            requestSystemAudioFocus()
            acquirePlaybackLocks()
        } else {
            abandonSystemAudioFocus()
            releasePlaybackLocks()
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()
        
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.app_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        FeedCredentialsManager.init(this)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExpressoPod/1.0")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        val resolvingHttpDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            httpDataSourceFactory
        ) { dataSpec ->
            val rawUriStr = dataSpec.uri.toString()
            val scheme = dataSpec.uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return@Factory dataSpec
            }
            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            val lastPodJson = statePrefs.getString("last_podcast", null)
            val lastFeedUrl = if (lastPodJson != null) {
                try { Gson().fromJson(lastPodJson, Podcast::class.java)?.feedUrl } catch (_: Exception) { null }
            } else null

            val creds = FeedCredentialsManager.getCredentialsForUrl(rawUriStr, lastFeedUrl)
            if (creds != null) {
                val resolved = FeedCredentialsManager.resolveStreamForExoPlayer(rawUriStr, lastFeedUrl)
                val targetUri = if (resolved != null && resolved.finalUrl.isNotBlank()) {
                    Uri.parse(resolved.finalUrl)
                } else {
                    Uri.parse(FeedCredentialsManager.extractEmbeddedCredentials(rawUriStr).first)
                }
                val builder = dataSpec.buildUpon()
                    .setUri(targetUri)
                    .setKey(dataSpec.key ?: rawUriStr)
                if (resolved?.attachAuthHeader == true) {
                    val updatedHeaders = HashMap(dataSpec.httpRequestHeaders)
                    updatedHeaders["Authorization"] = creds.toBasicAuthHeader()
                    builder.setHttpRequestHeaders(updatedHeaders)
                }
                builder.build()
            } else {
                dataSpec
            }
        }

        val cacheHttpDataSourceFactory = CacheDataSource.Factory()
            .setCache(MediaCacheManager.getCache(this))
            .setUpstreamDataSourceFactory(resolvingHttpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Dynamic HTTP DataSource Factory: checks cache_playing_episode preference on every HTTP stream request,
        // while DefaultDataSource handles file:// and content:// local audio files directly.
        val dynamicHttpDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val prefs = applicationContext.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
            val cacheEnabled = prefs.getBoolean("cache_playing_episode", true)
            if (cacheEnabled) {
                cacheHttpDataSourceFactory.createDataSource()
            } else {
                resolvingHttpDataSourceFactory.createDataSource()
            }
        }

        val dynamicDataSourceFactory = DefaultDataSource.Factory(this, dynamicHttpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dynamicDataSourceFactory))
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(currentRewindMs)
            .setSeekForwardIncrementMs(currentForwardMs)
            .build()

        setupVolumeBoost(exoPlayer.audioSessionId)
        exoPlayer.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForSavedPodcast(applicationContext))

        // Pre-populate saved episode item (staying in STATE_IDLE until play is requested)
        buildSavedEpisodeMediaItem()?.let { (savedItem, savedPos) ->
            exoPlayer.setMediaItems(listOf(savedItem), 0, savedPos)
        }

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getMediaMetadata(): MediaMetadata {
                val base = super.getMediaMetadata()
                val itemMeta = currentMediaItem?.mediaMetadata
                val builder = base.buildUpon()
                if (!itemMeta?.title.isNullOrBlank()) {
                    builder.setTitle(itemMeta?.title)
                }
                if (!itemMeta?.artist.isNullOrBlank()) {
                    builder.setArtist(itemMeta?.artist)
                }
                val resolvedArtUri = itemMeta?.artworkUri
                    ?: base.artworkUri
                    ?: run {
                        try {
                            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                            val podJson = statePrefs.getString("last_podcast", null)
                            val epJson = statePrefs.getString("last_episode", null)
                            val pod = if (!podJson.isNullOrBlank()) Gson().fromJson(podJson, Podcast::class.java) else null
                            val ep = if (!epJson.isNullOrBlank()) Gson().fromJson(epJson, Episode::class.java) else null
                            ArtworkResolver.resolveBestArtworkUri(applicationContext, pod, ep)
                        } catch (_: Exception) {
                            null
                        }
                    }
                if (resolvedArtUri != null) {
                    builder.setArtworkUri(resolvedArtUri)
                }
                return builder.build()
            }

            override fun play() {
                if (playbackState == Player.STATE_ENDED &&
                    (isHandlingCompletion || currentMediaItem?.mediaId == lastHandledEndedMediaId)
                ) {
                    return
                }
                requestSystemAudioFocus()
                acquirePlaybackLocks()
                if (currentMediaItem == null) {
                    buildSavedEpisodeMediaItem()?.let { (savedItem, savedPos) ->
                        setPlaybackSpeed(PlaybackSpeedManager.getSpeedForSavedPodcast(applicationContext))
                        setMediaItems(listOf(savedItem), 0, savedPos)
                        prepare()
                    }
                } else if (playbackState == Player.STATE_IDLE) {
                    prepare()
                } else if (playbackState == Player.STATE_ENDED) {
                    seekTo(0L)
                }
                super.play()
            }

            override fun getSeekBackIncrement(): Long = currentRewindMs

            override fun getSeekForwardIncrement(): Long = currentForwardMs

            override fun seekBack() {
                val target = (currentPosition - currentRewindMs).coerceAtLeast(0L)
                seekTo(target)
                savePlaybackPosition(this)
                updateWidgetState(this)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("position", target)
                    putExtra("media_id", currentMediaItem?.mediaId)
                    putExtra("playback_state", playbackState)
                })
            }

            override fun seekForward() {
                val dur = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                val target = (currentPosition + currentForwardMs).coerceAtMost(dur)
                seekTo(target)
                savePlaybackPosition(this)
                updateWidgetState(this)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("position", target)
                    putExtra("media_id", currentMediaItem?.mediaId)
                    putExtra("playback_state", playbackState)
                })
            }

            override fun seekToNext() {
                seekForward()
            }

            override fun seekToNextMediaItem() {
                seekForward()
            }

            override fun seekToPrevious() {
                seekBack()
            }

            override fun seekToPreviousMediaItem() {
                seekBack()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SET_SPEED_AND_PITCH -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SET_SPEED_AND_PITCH
                    )
                    .build()
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaybackFocusAndLocks(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackFocusAndLocks(player)
                updateWidgetState(player)
                if (!isPlaying) savePlaybackPosition(player, shouldQueueSyncAction = true)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("position", player.currentPosition)
                    putExtra("media_id", player.currentMediaItem?.mediaId)
                    putExtra("playback_state", player.playbackState)
                })
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId
                if (!newId.isNullOrBlank() && newId != lastHandledEndedMediaId) {
                    lastHandledEndedMediaId = null
                }
                updateWidgetState(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", player.isPlaying)
                    putExtra("position", player.currentPosition)
                    putExtra("media_id", newId)
                    putExtra("playback_state", player.playbackState)
                })
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaybackFocusAndLocks(player)
                if (playbackState == Player.STATE_READY) {
                    val curId = player.currentMediaItem?.mediaId
                    if (!curId.isNullOrBlank() && curId != lastHandledEndedMediaId) {
                        lastHandledEndedMediaId = null
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    val endedMediaId = player.currentMediaItem?.mediaId
                    val endedDurationMs = player.duration.takeIf { it > 0L } ?: 0L
                    savePlaybackPosition(player, shouldQueueSyncAction = true)
                    if (!endedMediaId.isNullOrBlank()) {
                        handleEpisodeCompletion(player, endedMediaId, endedDurationMs)
                    }
                    return
                }
                updateWidgetState(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", player.isPlaying)
                    putExtra("position", player.currentPosition)
                    putExtra("media_id", player.currentMediaItem?.mediaId)
                    putExtra("playback_state", playbackState)
                })
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateWidgetState(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", player.isPlaying)
                    putExtra("position", player.currentPosition)
                    putExtra("media_id", player.currentMediaItem?.mediaId)
                    putExtra("playback_state", player.playbackState)
                })
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupVolumeBoost(audioSessionId)
            }
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                try {
                    val chapterList = mutableListOf<Chapter>()
                    for (i in 0 until metadata.length()) {
                        val entry = metadata.get(i)
                        if (entry is androidx.media3.extractor.metadata.id3.ChapterFrame) {
                            var title = "Chapter ${chapterList.size + 1}"
                            for (j in 0 until entry.subFrameCount) {
                                val sub = entry.getSubFrame(j)
                                if (sub is androidx.media3.extractor.metadata.id3.TextInformationFrame) {
                                    val v = sub.values.firstOrNull() ?: sub.value
                                    if (!v.isNullOrBlank()) {
                                        title = v
                                        break
                                    }
                                }
                            }
                            chapterList.add(
                                Chapter(
                                    title = title,
                                    startMs = entry.startTimeMs.toLong(),
                                    endMs = entry.endTimeMs.toLong()
                                )
                            )
                        }
                    }
                    if (chapterList.isNotEmpty()) {
                        val currentAudioUrl = player.currentMediaItem?.mediaId
                        if (currentAudioUrl != null) {
                            val json = Gson().toJson(chapterList)
                            applicationContext.getSharedPreferences("expressopod_chapters", Context.MODE_PRIVATE)
                                .edit()
                                .putString(currentAudioUrl, json)
                                .apply()
                            sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_CHAPTERS_UPDATED").apply {
                                setPackage(packageName)
                                putExtra("audio_url", currentAudioUrl)
                                putExtra("chapters_json", json)
                            })
                        }
                    }
                } catch (_: Exception) {}
            }
        })

        // Listen for volume boost and skip preset preference changes in real-time
        serviceScope.launch {
            applicationContext.dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[booleanPreferencesKey("volume_boost_enabled")] ?: false,
                        prefs[intPreferencesKey("rewind_sec")] ?: 15,
                        prefs[intPreferencesKey("forward_sec")] ?: 30
                    )
                }
                .distinctUntilChanged()
                .collect { (enabled, rSec, fSec) ->
                    updateVolumeBoost(enabled)
                    currentRewindMs = rSec * 1000L
                    currentForwardMs = fSec * 1000L
                }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.rpmtech.expressopod.ACTION_NOW_PLAYING"
            putExtra("from_media_notification", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    .buildUpon()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SET_SPEED_AND_PITCH
                    )
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(playerCommands)
                    .build()
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val restored = buildSavedEpisodeMediaItem()
                return if (restored != null) {
                    Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(restored.first),
                            0,
                            restored.second
                        )
                    )
                } else {
                    Futures.immediateFailedFuture(UnsupportedOperationException("No saved episode"))
                }
            }
        }

        val notificationBitmapLoader = PodcastNotificationBitmapLoader(applicationContext, serviceScope)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setBitmapLoader(notificationBitmapLoader)
            .build()
        addSession(mediaSession!!)

        saveJob = serviceScope.launch {
            var lastMinuteBucket = -1L
            while (true) {
                kotlinx.coroutines.delay(1000)
                mediaSession?.player?.let { player ->
                    if (player.isPlaying) {
                        if (!hasSystemAudioFocus) {
                            requestSystemAudioFocus()
                        }
                        acquirePlaybackLocks()
                        savePlaybackPosition(player)
                        val dur = player.duration.takeIf { it > 0L } ?: 0L
                        val pos = player.currentPosition
                        val remMins = if (dur > pos) (dur - pos) / 60000L else 0L
                        if (remMins != lastMinuteBucket && remMins >= 0L) {
                            lastMinuteBucket = remMins
                            updateWidgetState(player)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val player = mediaSession?.player ?: return START_STICKY

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    player.pause()
                    savePlaybackPosition(player)
                    updateWidgetState(player)
                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("is_playing", false)
                        putExtra("position", player.currentPosition)
                        putExtra("media_id", player.currentMediaItem?.mediaId)
                        putExtra("playback_state", player.playbackState)
                    })
                } else {
                    if (player.playbackState == Player.STATE_ENDED &&
                        (isHandlingCompletion || player.currentMediaItem?.mediaId == lastHandledEndedMediaId)
                    ) {
                        return START_STICKY
                    }
                    if (player.currentMediaItem == null) {
                        serviceScope.launch {
                            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                            val epJson = statePrefs.getString("last_episode", null)
                            val podJson = statePrefs.getString("last_podcast", null)
                            val lastPos = statePrefs.getLong("last_position", 0L)
                            if (epJson != null && podJson != null) {
                                try {
                                    val ep = Gson().fromJson(epJson, Episode::class.java)
                                    val rawPod = Gson().fromJson(podJson, Podcast::class.java)
                                    val pod = ArtworkResolver.enrichPodcastArtwork(applicationContext, rawPod, ep)
                                    val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                                    val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
                                    val epDurationMs = parseDurationMs(ep.durationText)
                                    val startPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos
                                    val metadata = MediaMetadata.Builder()
                                        .setTitle(ep.title)
                                        .setArtist(pod.title)
                                        .setArtworkUri(ArtworkResolver.resolveBestArtworkUri(applicationContext, pod, ep))
                                        .build()
                                    val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(ep.audioUrl)
                                        ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(ep.audioUrl)
                                    val item = MediaItem.Builder()
                                        .setMediaId(ep.audioUrl)
                                        .setUri(localUri ?: Uri.parse(ep.audioUrl))
                                        .setMediaMetadata(metadata)
                                        .build()
                                    lastHandledEndedMediaId = null
                                    player.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForPodcast(applicationContext, pod))
                                    player.setMediaItems(listOf(item), 0, startPos)
                                    player.prepare()
                                    player.play()
                                    updateWidgetState(player)
                                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                                        setPackage(packageName)
                                        putExtra("is_playing", true)
                                        putExtra("position", startPos)
                                        putExtra("media_id", ep.audioUrl)
                                        putExtra("playback_state", player.playbackState)
                                    })
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0L)
                        }
                        player.play()
                        updateWidgetState(player)
                        sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                            setPackage(packageName)
                            putExtra("is_playing", true)
                            putExtra("position", player.currentPosition)
                            putExtra("media_id", player.currentMediaItem?.mediaId)
                            putExtra("playback_state", player.playbackState)
                        })
                    }
                }
            }
            ACTION_REWIND -> {
                serviceScope.launch {
                    val prefs = applicationContext.dataStore.data.first()
                    val rewindSec = prefs[intPreferencesKey("rewind_sec")] ?: 15
                    player.seekTo((player.currentPosition - rewindSec * 1000L).coerceAtLeast(0L))
                    savePlaybackPosition(player)
                    updateWidgetState(player)
                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("is_playing", player.isPlaying)
                        putExtra("position", player.currentPosition)
                        putExtra("media_id", player.currentMediaItem?.mediaId)
                        putExtra("playback_state", player.playbackState)
                    })
                }
            }
            ACTION_FORWARD -> {
                serviceScope.launch {
                    val prefs = applicationContext.dataStore.data.first()
                    val forwardSec = prefs[intPreferencesKey("forward_sec")] ?: 30
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + forwardSec * 1000L).coerceAtMost(duration))
                    savePlaybackPosition(player)
                    updateWidgetState(player)
                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("is_playing", player.isPlaying)
                        putExtra("position", player.currentPosition)
                        putExtra("media_id", player.currentMediaItem?.mediaId)
                        putExtra("playback_state", player.playbackState)
                    })
                }
            }
            ACTION_PLAY_QUEUE_INDEX -> {
                val index = intent.getIntExtra("queue_index", 0)
                serviceScope.launch {
                    val prefs = applicationContext.dataStore.data.first()
                    val playedSet = prefs[stringSetPreferencesKey("played_episodes")] ?: emptySet()
                    val archivedSet = prefs[stringSetPreferencesKey("archived_episodes")] ?: emptySet()

                    val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                    val prevPodJson = statePrefs.getString("last_podcast", null)
                    val prevEpJson = statePrefs.getString("last_episode", null)
                    val prevPod = if (prevPodJson != null) {
                        try { Gson().fromJson(prevPodJson, Podcast::class.java) } catch (_: Exception) { null }
                    } else null
                    val prevEp = if (prevEpJson != null) {
                        try { Gson().fromJson(prevEpJson, Episode::class.java) } catch (_: Exception) { null }
                    } else null

                    val fullQueue = QueueStorageManager.loadQueueFromBackup(applicationContext)
                        .filterNot {
                            (prevEp != null && it.episode.audioUrl == prevEp.audioUrl) ||
                                playedSet.contains(it.episode.audioUrl) ||
                                archivedSet.contains(it.episode.audioUrl)
                        }

                    val queueJson = prefs[stringPreferencesKey("widget_queue_json")] ?: "[]"
                    val itemType = object : TypeToken<List<WidgetQueueItem>>() {}.type
                    val widgetQueueList: List<WidgetQueueItem> = try {
                        Gson().fromJson(queueJson, itemType) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val targetQueueEntry: QueueItem? = fullQueue.getOrNull(index) ?: run {
                        val wItem = widgetQueueList.getOrNull(index) ?: return@run null
                        val matched = fullQueue.find { it.episode.audioUrl == wItem.audioUrl }
                        matched ?: QueueItem(
                            podcast = Podcast(
                                title = wItem.podcastTitle,
                                author = wItem.podcastTitle,
                                artworkUrl = wItem.artworkUrl,
                                feedUrl = ""
                            ),
                            episode = Episode(
                                title = wItem.title,
                                audioUrl = wItem.audioUrl,
                                durationText = wItem.durationText
                            )
                        )
                    }

                    if (targetQueueEntry != null && targetQueueEntry.episode.audioUrl.isNotBlank()) {
                        val wasPrevEnded = prevEp != null &&
                            player.currentMediaItem?.mediaId == prevEp.audioUrl &&
                            (player.playbackState == Player.STATE_ENDED ||
                                (player.duration > 0L && player.currentPosition >= player.duration - 2000L))
                        savePlaybackPosition(player)

                        val newEp = targetQueueEntry.episode
                        val newPod = ArtworkResolver.enrichPodcastArtwork(applicationContext, targetQueueEntry.podcast, newEp)

                        val filteredFull = fullQueue.filterNot {
                            it.episode.audioUrl == newEp.audioUrl ||
                                (prevEp != null && it.episode.audioUrl == prevEp.audioUrl)
                        }
                        val shouldKeepPrev = !wasPrevEnded &&
                            prevPod != null &&
                            prevEp != null &&
                            prevEp.audioUrl.isNotBlank() &&
                            prevEp.audioUrl != newEp.audioUrl &&
                            !playedSet.contains(prevEp.audioUrl) &&
                            !archivedSet.contains(prevEp.audioUrl)

                        val updatedFullQueue = if (shouldKeepPrev && prevPod != null && prevEp != null) {
                            listOf(QueueItem(prevPod, prevEp)) + filteredFull
                        } else {
                            filteredFull
                        }
                        val updatedFullQueueJson = Gson().toJson(updatedFullQueue)
                        QueueStorageManager.saveQueueToBackup(applicationContext, updatedFullQueueJson)

                        val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                        val updatedWidgetQueue = updatedFullQueue.take(3).map { qItem ->
                            val epDur = parseDurationMs(qItem.episode.durationText)
                            val sPos = posPrefs.getLong(qItem.episode.audioUrl, 0L)
                            val qRemMs = if (epDur > sPos) (epDur - sPos) else epDur
                            val qRemText = if (qRemMs > 0L) formatWidgetRemainingTime(qRemMs) else ""
                            WidgetQueueItem(
                                title = qItem.episode.title,
                                podcastTitle = qItem.podcast.title,
                                durationText = qItem.episode.durationText,
                                audioUrl = qItem.episode.audioUrl,
                                artworkUrl = qItem.podcast.artworkUrl,
                                remainingTimeText = qRemText
                            )
                        }
                        val updatedQueueJson = Gson().toJson(updatedWidgetQueue)

                        val rawNewPos = posPrefs.getLong(newEp.audioUrl, 0L)
                        val newEpDurMs = parseDurationMs(newEp.durationText)
                        val startPos = if (newEpDurMs > 0L && rawNewPos >= newEpDurMs - 2000L) 0L else rawNewPos

                        val newPodJson = Gson().toJson(newPod)
                        val newEpJson = Gson().toJson(newEp)

                        statePrefs.edit()
                            .putString("last_podcast", newPodJson)
                            .putString("last_episode", newEpJson)
                            .putLong("last_position", startPos)
                            .apply()

                        applicationContext.dataStore.edit {
                            it[stringPreferencesKey("up_next_queue")] = updatedFullQueueJson
                            it[stringPreferencesKey("widget_queue_json")] = updatedQueueJson
                            it[stringPreferencesKey("last_podcast")] = newPodJson
                            it[stringPreferencesKey("last_episode")] = newEpJson
                        }

                        val metadata = MediaMetadata.Builder()
                            .setTitle(newEp.title)
                            .setArtist(newPod.title)
                            .setArtworkUri(ArtworkResolver.resolveBestArtworkUri(applicationContext, newPod, newEp))
                            .build()
                        val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(newEp.audioUrl)
                            ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(newEp.audioUrl)
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(newEp.audioUrl)
                            .setUri(localUri ?: Uri.parse(newEp.audioUrl))
                            .setMediaMetadata(metadata)
                            .build()

                        lastHandledEndedMediaId = null
                        player.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForPodcast(applicationContext, newPod))
                        player.setMediaItems(listOf(mediaItem), 0, startPos)
                        player.prepare()
                        player.play()

                        sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_QUEUE_CHANGED").apply {
                            setPackage(packageName)
                            putExtra("played_url", newEp.audioUrl)
                            putExtra("new_podcast_json", newPodJson)
                            putExtra("new_episode_json", newEpJson)
                            putExtra("new_position", startPos)
                            putExtra("queue_empty", false)
                            putExtra("from_completion", false)
                        })

                        updateWidgetState(player)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun handleEpisodeCompletion(player: Player, endedMediaId: String, endedDurationMs: Long) {
        if (isHandlingCompletion || lastHandledEndedMediaId == endedMediaId) {
            return
        }
        isHandlingCompletion = true
        lastHandledEndedMediaId = endedMediaId

        serviceScope.launch {
            try {
                val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                val prevPodJson = statePrefs.getString("last_podcast", null)
                val prevEpJson = statePrefs.getString("last_episode", null)
                val prevPod = if (prevPodJson != null) {
                    try { Gson().fromJson(prevPodJson, Podcast::class.java) } catch (_: Exception) { null }
                } else null
                val prevEp = if (prevEpJson != null) {
                    try { Gson().fromJson(prevEpJson, Episode::class.java) } catch (_: Exception) { null }
                } else null

                val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
                posPrefs.edit().remove(endedMediaId).apply()
                statePrefs.edit().putLong("last_position", 0L).apply()

                var shouldAutoArchive = true
                var updatedPlayed: Set<String> = emptySet()
                var updatedArchived: Set<String> = emptySet()

                applicationContext.dataStore.edit { prefs ->
                    val playedKey = stringSetPreferencesKey("played_episodes")
                    val archivedKey = stringSetPreferencesKey("archived_episodes")
                    val keepKey = stringSetPreferencesKey("keep_unarchived_episodes")
                    val autoArchiveKey = booleanPreferencesKey("auto_archive_enabled")

                    val curPlayed = (prefs[playedKey] ?: emptySet()).toMutableSet()
                    val curArchived = (prefs[archivedKey] ?: emptySet()).toMutableSet()
                    val keepUnarchived = prefs[keepKey] ?: emptySet()
                    val autoArchive = prefs[autoArchiveKey] ?: true

                    curPlayed.add(endedMediaId)
                    shouldAutoArchive = autoArchive && !keepUnarchived.contains(endedMediaId)
                    if (shouldAutoArchive) {
                        curArchived.add(endedMediaId)
                    }
                    prefs[playedKey] = curPlayed
                    prefs[archivedKey] = curArchived
                    updatedPlayed = curPlayed
                    updatedArchived = curArchived
                }

                if (shouldAutoArchive) {
                    PodcastDownloadManager.getInstance(applicationContext).deleteDownload(endedMediaId)
                }

                val parsedDurMs = prevEp?.takeIf { it.audioUrl == endedMediaId }?.let { parseDurationMs(it.durationText) } ?: 0L
                val savedDurMs = GPodderSyncManager.getSavedDurationMs(applicationContext, endedMediaId)
                val bestDurationMs = maxOf(endedDurationMs, parsedDurMs, savedDurMs)
                val podcastFeedUrl = prevPod?.takeIf { prevEp?.audioUrl == endedMediaId }?.feedUrl?.takeIf { it.isNotBlank() }
                    ?: GPodderSyncManager.findPodcastUrlForEpisode(applicationContext, endedMediaId, prevPod?.feedUrl)
                GPodderSyncManager.onEpisodePlayedStateChanged(
                    context = applicationContext,
                    podcastUrl = podcastFeedUrl,
                    episodeUrl = endedMediaId,
                    played = true,
                    durationMs = bestDurationMs
                )
                if (shouldAutoArchive) {
                    GPodderSyncManager.onEpisodeArchiveStateChanged(
                        context = applicationContext,
                        podcastUrl = podcastFeedUrl,
                        episodeUrl = endedMediaId,
                        archived = true,
                        fromPlaybackCompletion = true,
                        durationMs = bestDurationMs
                    )
                }

                // If user manually started a different episode while we were updating DataStore, do not clobber it
                if (player.playbackState != Player.STATE_ENDED || player.currentMediaItem?.mediaId != endedMediaId) {
                    return@launch
                }

                val fullQueue = QueueStorageManager.loadQueueFromBackup(applicationContext)
                    .filterNot {
                        it.episode.audioUrl.isBlank() ||
                            it.episode.audioUrl == endedMediaId ||
                            updatedPlayed.contains(it.episode.audioUrl) ||
                            updatedArchived.contains(it.episode.audioUrl)
                    }

                if (fullQueue.isNotEmpty()) {
                    val nextItem = fullQueue.first()
                    val remainingQueue = fullQueue.drop(1)
                    val updatedQueueJson = Gson().toJson(remainingQueue)
                    QueueStorageManager.saveQueueToBackup(applicationContext, updatedQueueJson)

                    val newEp = nextItem.episode
                    val newPod = ArtworkResolver.enrichPodcastArtwork(applicationContext, nextItem.podcast, newEp)
                    val rawNewPos = posPrefs.getLong(newEp.audioUrl, 0L)
                    val newEpDurMs = parseDurationMs(newEp.durationText)
                    val startPos = if (newEpDurMs > 0L && rawNewPos >= newEpDurMs - 2000L) 0L else rawNewPos

                    val newPodJson = Gson().toJson(newPod)
                    val newEpJson = Gson().toJson(newEp)
                    statePrefs.edit()
                        .putString("last_podcast", newPodJson)
                        .putString("last_episode", newEpJson)
                        .putLong("last_position", startPos)
                        .apply()

                    applicationContext.dataStore.edit { prefs ->
                        prefs[stringPreferencesKey("up_next_queue")] = updatedQueueJson
                        prefs[stringPreferencesKey("last_podcast")] = newPodJson
                        prefs[stringPreferencesKey("last_episode")] = newEpJson
                    }

                    val metadata = MediaMetadata.Builder()
                        .setTitle(newEp.title)
                        .setArtist(newPod.title)
                        .setArtworkUri(ArtworkResolver.resolveBestArtworkUri(applicationContext, newPod, newEp))
                        .build()
                    val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(newEp.audioUrl)
                        ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(newEp.audioUrl)
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(newEp.audioUrl)
                        .setUri(localUri ?: Uri.parse(newEp.audioUrl))
                        .setMediaMetadata(metadata)
                        .build()

                    lastHandledEndedMediaId = null
                    player.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForPodcast(applicationContext, newPod))
                    player.setMediaItems(listOf(mediaItem), 0, startPos)
                    player.prepare()
                    player.play()

                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_QUEUE_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("played_url", newEp.audioUrl)
                        putExtra("ended_url", endedMediaId)
                        putExtra("new_podcast_json", newPodJson)
                        putExtra("new_episode_json", newEpJson)
                        putExtra("new_position", startPos)
                        putExtra("queue_empty", false)
                        putExtra("from_completion", true)
                    })

                    updateWidgetState(player)
                } else {
                    QueueStorageManager.saveQueueToBackup(applicationContext, "[]")
                    statePrefs.edit()
                        .remove("last_podcast")
                        .remove("last_episode")
                        .putLong("last_position", 0L)
                        .apply()
                    applicationContext.dataStore.edit { prefs ->
                        prefs[stringPreferencesKey("up_next_queue")] = "[]"
                        prefs.remove(stringPreferencesKey("last_podcast"))
                        prefs.remove(stringPreferencesKey("last_episode"))
                    }
                    player.stop()
                    player.clearMediaItems()
                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_QUEUE_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("ended_url", endedMediaId)
                        putExtra("queue_empty", true)
                        putExtra("from_completion", true)
                    })
                    updateWidgetState(player)
                }
            } finally {
                isHandlingCompletion = false
            }
        }
    }

    private fun savePlaybackPosition(player: Player, shouldQueueSyncAction: Boolean = false) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val pos = player.currentPosition
        val duration = player.duration
        val isEnded = player.playbackState == Player.STATE_ENDED || (duration > 0 && pos >= duration - 2000L)
        if (isEnded) {
            val sharedPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            sharedPrefs.edit().remove(mediaId).apply()
            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            statePrefs.edit().putLong("last_position", 0L).apply()
            if (shouldQueueSyncAction) {
                GPodderSyncManager.onEpisodePlayedStateChanged(
                    context = applicationContext,
                    podcastUrl = null,
                    episodeUrl = mediaId,
                    played = true,
                    durationMs = if (duration > 0) duration else 0L
                )
            }
            return
        }
        if (pos <= 0) return
        val sharedPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong(mediaId, pos).apply()
        val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        statePrefs.edit().putLong("last_position", pos).apply()
        GPodderSyncManager.onPlaybackPositionUpdated(
            context = applicationContext,
            podcastUrl = null,
            episodeUrl = mediaId,
            positionMs = pos,
            durationMs = if (duration > 0) duration else 0L,
            shouldQueueSyncAction = shouldQueueSyncAction
        )
    }

    private fun updateWidgetState(player: Player) {
        val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        val epJson = statePrefs.getString("last_episode", null)
        val podJson = statePrefs.getString("last_podcast", null)
        val savedEp = if (epJson != null) {
            try { Gson().fromJson(epJson, Episode::class.java) } catch (_: Exception) { null }
        } else null
        val savedPod = if (podJson != null) {
            try { Gson().fromJson(podJson, Podcast::class.java) } catch (_: Exception) { null }
        } else null

        val currentMediaId = player.currentMediaItem?.mediaId
        val matchesSavedEpisode = currentMediaId == null || savedEp == null || currentMediaId == savedEp.audioUrl

        val itemMeta = player.currentMediaItem?.mediaMetadata
        val playerMeta = player.mediaMetadata

        val title = (if (matchesSavedEpisode && !savedEp?.title.isNullOrBlank()) savedEp?.title else null)
            ?: itemMeta?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: playerMeta.title?.toString()?.takeIf { it.isNotBlank() }
            ?: savedEp?.title
            ?: "No Episode Playing"

        val artist = (if (matchesSavedEpisode && !savedPod?.title.isNullOrBlank()) savedPod?.title else null)
            ?: itemMeta?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: playerMeta.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: savedPod?.title
            ?: "Expressive Casts"

        val playing = player.isPlaying
        val pos = player.currentPosition
        var duration = player.duration.takeIf { it > 0L } ?: 0L

        if (duration <= 0L && savedEp != null) {
            duration = parseDurationMs(savedEp.durationText)
        }

        val resolvedSavedArt = ArtworkResolver.resolveBestArtworkUrl(applicationContext, savedPod, savedEp)
        val finalArtUrl = (if (matchesSavedEpisode && resolvedSavedArt.isNotBlank()) resolvedSavedArt else null)
            ?: itemMeta?.artworkUri?.toString()?.takeIf { it.isNotBlank() }
            ?: playerMeta.artworkUri?.toString()?.takeIf { it.isNotBlank() }
            ?: resolvedSavedArt.ifBlank { savedPod?.artworkUrl ?: "" }

        val remainingMs = if (duration > 0L && duration >= pos) (duration - pos) else 0L
        val remainingText = if (remainingMs > 0L) formatWidgetRemainingTime(remainingMs) else ""

        val activeEpUrl = currentMediaId?.takeIf { it.isNotBlank() } ?: savedEp?.audioUrl
        val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)

        serviceScope.launch {
            val dsPrefs = try { applicationContext.dataStore.data.first() } catch (_: Exception) { null }
            val playedSet = dsPrefs?.get(stringSetPreferencesKey("played_episodes")) ?: emptySet()
            val archivedSet = dsPrefs?.get(stringSetPreferencesKey("archived_episodes")) ?: emptySet()
            val fullQueue = QueueStorageManager.loadQueueFromBackup(applicationContext)
                .filterNot {
                    (!activeEpUrl.isNullOrBlank() && it.episode.audioUrl == activeEpUrl) ||
                        playedSet.contains(it.episode.audioUrl) ||
                        archivedSet.contains(it.episode.audioUrl)
                }
            val widgetQueueItems = fullQueue.take(3).map { qItem ->
                val epDur = parseDurationMs(qItem.episode.durationText)
                val sPos = posPrefs.getLong(qItem.episode.audioUrl, 0L)
                val qRemMs = if (epDur > sPos) (epDur - sPos) else epDur
                val qRemText = if (qRemMs > 0L) formatWidgetRemainingTime(qRemMs) else ""
                WidgetQueueItem(
                    title = qItem.episode.title,
                    podcastTitle = qItem.podcast.title,
                    durationText = qItem.episode.durationText,
                    audioUrl = qItem.episode.audioUrl,
                    artworkUrl = qItem.podcast.artworkUrl,
                    remainingTimeText = qRemText
                )
            }
            val queueJson = Gson().toJson(widgetQueueItems)

            applicationContext.updateWidgetData(
                title = title,
                podcast = artist,
                isPlaying = playing,
                remainingText = remainingText,
                artUrl = finalArtUrl,
                queueJson = queueJson
            )
            applicationContext.updateAllPodcastWidgets()
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val keepInForeground = startInForegroundRequired ||
            session.player.isPlaying ||
            (session.player.playWhenReady &&
                session.player.playbackState != Player.STATE_IDLE &&
                session.player.playbackState != Player.STATE_ENDED)
        super.onUpdateNotification(session, keepInForeground)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let {
            if (it.isPlaying) it.pause()
            savePlaybackPosition(it)
        }
        abandonSystemAudioFocus()
        releasePlaybackLocks()
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.player?.let { savePlaybackPosition(it) }
        saveJob?.cancel()
        abandonSystemAudioFocus()
        releasePlaybackLocks()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}