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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
    @Volatile
    private var currentRewindMs: Long = 15_000L
    @Volatile
    private var currentForwardMs: Long = 30_000L

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
            val pod = Gson().fromJson(podJson, Podcast::class.java) ?: return null
            if (ep.audioUrl.isBlank()) return null
            val posPrefs = applicationContext.getSharedPreferences("expressopod_positions", Context.MODE_PRIVATE)
            val rawSavedPos = posPrefs.getLong(ep.audioUrl, lastPos)
            val epDurationMs = parseDurationMs(ep.durationText)
            val savedPos = if (epDurationMs > 0L && rawSavedPos >= epDurationMs - 2000L) 0L else rawSavedPos

            val metadata = MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(pod.title)
                .setArtworkUri(Uri.parse(pod.artworkUrl))
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
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dynamicDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(currentRewindMs)
            .setSeekForwardIncrementMs(currentForwardMs)
            .build()

        setupVolumeBoost(exoPlayer.audioSessionId)
        exoPlayer.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForSavedPodcast(applicationContext))

        // Pre-populate saved episode if available so Bluetooth / lockscreen / headset play works immediately
        buildSavedEpisodeMediaItem()?.let { (savedItem, savedPos) ->
            exoPlayer.setMediaItem(savedItem, savedPos)
            exoPlayer.prepare()
        }

        val player = object : ForwardingPlayer(exoPlayer) {
            override fun play() {
                if (currentMediaItem == null) {
                    buildSavedEpisodeMediaItem()?.let { (savedItem, savedPos) ->
                        setPlaybackSpeed(PlaybackSpeedManager.getSpeedForSavedPodcast(applicationContext))
                        setMediaItem(savedItem, savedPos)
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidgetState(player)
                if (!isPlaying) savePlaybackPosition(player, shouldQueueSyncAction = true)
                sendBroadcast(Intent("com.rpmtech.expressopod.ACTION_PLAYBACK_STATE_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("position", player.currentPosition)
                })
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    savePlaybackPosition(player, shouldQueueSyncAction = true)
                }
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

        // Listen for volume boost and skip preset preference changes in real-time
        serviceScope.launch {
            applicationContext.dataStore.data
                .collect { prefs ->
                    val enabled = prefs[booleanPreferencesKey("volume_boost_enabled")] ?: false
                    updateVolumeBoost(enabled)
                    val rSec = prefs[intPreferencesKey("rewind_sec")] ?: 15
                    val fSec = prefs[intPreferencesKey("forward_sec")] ?: 30
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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
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
                                    val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(ep.audioUrl)
                                        ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(ep.audioUrl)
                                    val item = MediaItem.Builder()
                                        .setMediaId(ep.audioUrl)
                                        .setUri(localUri ?: Uri.parse(ep.audioUrl))
                                        .setMediaMetadata(metadata)
                                        .build()
                                    player.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForPodcast(applicationContext, pod))
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
                        savePlaybackPosition(player)
                        val item = queueList[index]

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
                        val matchedQueueEntry = fullQueue.find { it.episode.audioUrl == item.audioUrl }
                        val newEp = matchedQueueEntry?.episode ?: Episode(
                            title = item.title,
                            audioUrl = item.audioUrl,
                            durationText = item.durationText
                        )
                        val newPod = matchedQueueEntry?.podcast ?: Podcast(
                            title = item.podcastTitle,
                            author = item.podcastTitle,
                            artworkUrl = item.artworkUrl,
                            feedUrl = ""
                        )

                        val filteredFull = fullQueue.filterNot {
                            it.episode.audioUrl == item.audioUrl ||
                                (prevEp != null && it.episode.audioUrl == prevEp.audioUrl)
                        }
                        val updatedFullQueue = if (prevPod != null && prevEp != null && prevEp.audioUrl.isNotBlank() && prevEp.audioUrl != item.audioUrl) {
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

                        statePrefs.edit()
                            .putString("last_podcast", Gson().toJson(newPod))
                            .putString("last_episode", Gson().toJson(newEp))
                            .putLong("last_position", startPos)
                            .apply()

                        applicationContext.dataStore.edit {
                            it[stringPreferencesKey("up_next_queue")] = updatedFullQueueJson
                            it[stringPreferencesKey("widget_queue_json")] = updatedQueueJson
                            it[stringPreferencesKey("last_podcast")] = Gson().toJson(newPod)
                            it[stringPreferencesKey("last_episode")] = Gson().toJson(newEp)
                        }

                        val metadata = MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.podcastTitle)
                            .setArtworkUri(Uri.parse(item.artworkUrl))
                            .build()
                        val localUri = PodcastDownloadManager.getInstance(applicationContext).getLocalUriIfDownloaded(item.audioUrl)
                            ?: LocalAudioManager.getInstance(applicationContext).getLocalUri(item.audioUrl)
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(item.audioUrl)
                            .setUri(localUri ?: Uri.parse(item.audioUrl))
                            .setMediaMetadata(metadata)
                            .build()

                        player.setPlaybackSpeed(PlaybackSpeedManager.getSpeedForPodcast(applicationContext, newPod))
                        player.setMediaItem(mediaItem, startPos)
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
            applicationContext.updateAllPodcastWidgets()
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