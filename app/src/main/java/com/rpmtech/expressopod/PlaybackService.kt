package com.rpmtech.expressopod

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.updateAll
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var saveJob: kotlinx.coroutines.Job? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    companion object {
        const val ACTION_PLAY_PAUSE = "com.rpmtech.expressopod.PLAY_PAUSE"
        const val ACTION_REWIND = "com.rpmtech.expressopod.REWIND"
        const val ACTION_FORWARD = "com.rpmtech.expressopod.FORWARD"
        const val ACTION_PLAY_QUEUE_INDEX = "com.rpmtech.expressopod.PLAY_QUEUE_INDEX"
    }

    private fun setupVolumeBoost(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(700) // +7.0 dB voice boost (noticeably richer speech boost while preventing distortion)
                val prefs = applicationContext.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
                enabled = prefs.getBoolean("volume_boost_enabled", false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateVolumeBoost(enabled: Boolean) {
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
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

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ExpressoPod/1.0")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(MediaCacheManager.getCache(this))
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Dynamic DataSource Factory: checks cache_playing_episode preference on every stream request
        val dynamicDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val prefs = applicationContext.getSharedPreferences("expressopod_settings", Context.MODE_PRIVATE)
            val cacheEnabled = prefs.getBoolean("cache_playing_episode", true)
            if (cacheEnabled) {
                cacheDataSourceFactory.createDataSource()
            } else {
                defaultDataSourceFactory.createDataSource()
            }
        }

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dynamicDataSourceFactory))
            .build()

        setupVolumeBoost(player.audioSessionId)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidgetState(player)
                if (!isPlaying) savePlaybackPosition(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("position", player.currentPosition)
                })
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidgetState(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", player.isPlaying)
                    putExtra("position", player.currentPosition)
                })
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateWidgetState(player)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", player.isPlaying)
                    putExtra("position", player.currentPosition)
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

        // Listen for volume boost preference changes in real-time
        serviceScope.launch {
            applicationContext.dataStore.data
                .map { it[booleanPreferencesKey("volume_boost_enabled")] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    updateVolumeBoost(enabled)
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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        saveJob = serviceScope.launch {
            var lastMinuteBucket = -1L
            while (true) {
                kotlinx.coroutines.delay(1000)
                mediaSession?.player?.let { player ->
                    if (player.isPlaying) {
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
                    })
                } else {
                    if (player.currentMediaItem == null) {
                        serviceScope.launch {
                            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                            val epJson = statePrefs.getString("last_episode", null)
                            val podJson = statePrefs.getString("last_podcast", null)
                            val lastPos = statePrefs.getLong("last_position", 0L)
                            if (epJson != null && podJson != null) {
                                try {
                                    val ep = Gson().fromJson(epJson, Episode::class.java)
                                    val pod = Gson().fromJson(podJson, Podcast::class.java)
                                    val metadata = MediaMetadata.Builder()
                                        .setTitle(ep.title)
                                        .setArtist(pod.title)
                                        .setArtworkUri(Uri.parse(pod.artworkUrl))
                                        .build()
                                    val item = MediaItem.Builder()
                                        .setMediaId(ep.audioUrl)
                                        .setUri(ep.audioUrl)
                                        .setMediaMetadata(metadata)
                                        .build()
                                    player.setMediaItem(item, lastPos)
                                    player.prepare()
                                    player.play()
                                    updateWidgetState(player)
                                    sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                                        setPackage(packageName)
                                        putExtra("is_playing", true)
                                        putExtra("position", lastPos)
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
                    })
                }
            }
            ACTION_PLAY_QUEUE_INDEX -> {
                val index = intent.getIntExtra("queue_index", 0)
                serviceScope.launch {
                    val prefs = applicationContext.dataStore.data.first()
                    val queueJson = prefs[stringPreferencesKey("widget_queue_json")] ?: "[]"
                    val itemType = object : TypeToken<List<WidgetQueueItem>>() {}.type
                    val queueList: List<WidgetQueueItem> = try {
                        Gson().fromJson(queueJson, itemType) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (index in queueList.indices) {
                        val item = queueList[index]
                        val updatedQueue = queueList.toMutableList().apply { removeAt(index) }
                        val updatedQueueJson = Gson().toJson(updatedQueue)

                        val newEp = Episode(
                            title = item.title,
                            audioUrl = item.audioUrl,
                            durationText = item.durationText
                        )
                        val newPod = Podcast(
                            title = item.podcastTitle,
                            author = item.podcastTitle,
                            artworkUrl = item.artworkUrl,
                            feedUrl = ""
                        )

                        val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                        statePrefs.edit()
                            .putString("last_podcast", Gson().toJson(newPod))
                            .putString("last_episode", Gson().toJson(newEp))
                            .putLong("last_position", 0L)
                            .apply()

                        val fullQueue = QueueStorageManager.loadQueueFromBackup(applicationContext)
                        if (fullQueue.isNotEmpty()) {
                            val filteredFull = fullQueue.filterNot { it.episode.audioUrl == item.audioUrl }
                            QueueStorageManager.saveQueueToBackup(applicationContext, Gson().toJson(filteredFull))
                        }

                        applicationContext.dataStore.edit {
                            it[stringPreferencesKey("widget_queue_json")] = updatedQueueJson
                            it[stringPreferencesKey("last_podcast")] = Gson().toJson(newPod)
                            it[stringPreferencesKey("last_episode")] = Gson().toJson(newEp)
                        }

                        val metadata = MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.podcastTitle)
                            .setArtworkUri(Uri.parse(item.artworkUrl))
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(item.audioUrl)
                            .setUri(item.audioUrl)
                            .setMediaMetadata(metadata)
                            .build()

                        player.setMediaItem(mediaItem, 0L)
                        player.prepare()
                        player.play()

                        sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_QUEUE_CHANGED").apply {
                            setPackage(packageName)
                            putExtra("played_url", item.audioUrl)
                        })

                        updateWidgetState(player)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun savePlaybackPosition(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val pos = player.currentPosition
        val duration = player.duration
        val isEnded = player.playbackState == Player.STATE_ENDED || (duration > 0 && pos >= duration - 2000L)
        if (isEnded) {
            val sharedPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            sharedPrefs.edit().remove(mediaId).apply()
            val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
            statePrefs.edit().putLong("last_position", 0L).apply()
            return
        }
        if (pos <= 0) return
        val sharedPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong(mediaId, pos).apply()
        val statePrefs = applicationContext.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
        statePrefs.edit().putLong("last_position", pos).apply()
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

        val finalArtUrl = (if (matchesSavedEpisode && !savedPod?.artworkUrl.isNullOrBlank()) savedPod?.artworkUrl else null)
            ?: itemMeta?.artworkUri?.toString()?.takeIf { it.isNotBlank() }
            ?: playerMeta.artworkUri?.toString()?.takeIf { it.isNotBlank() }
            ?: savedPod?.artworkUrl
            ?: ""

        val remainingMs = if (duration > 0L && duration >= pos) (duration - pos) else 0L
        val remainingText = if (remainingMs > 0L) formatWidgetRemainingTime(remainingMs) else ""

        serviceScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            val queueJson = prefs[stringPreferencesKey("widget_queue_json")] ?: "[]"
            applicationContext.updateWidgetData(
                title = title,
                podcast = artist,
                isPlaying = playing,
                remainingText = remainingText,
                artUrl = finalArtUrl,
                queueJson = queueJson
            )
            PodcastWidget().updateAll(this@PlaybackService)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let {
            if (it.isPlaying) it.pause()
            savePlaybackPosition(it)
        }
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.player?.let { savePlaybackPosition(it) }
        saveJob?.cancel()
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