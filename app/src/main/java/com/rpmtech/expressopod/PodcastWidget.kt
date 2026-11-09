package com.rpmtech.expressopod

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class WidgetQueueItem(
    val title: String,
    val podcastTitle: String,
    val durationText: String = "",
    val audioUrl: String = "",
    val artworkUrl: String = "",
    val remainingTimeText: String = ""
)

fun formatWidgetRemainingTime(remainingMs: Long): String {
    if (remainingMs <= 0L) return ""
    val totalSec = remainingMs / 1000L
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
        hours > 0L -> "${hours}h left"
        else -> "${minutes.coerceAtLeast(1L)}m left"
    }
}

fun queueArtworkFile(context: Context, artUrl: String, fallbackIndex: Int): File {
    val fileName = if (artUrl.isNotBlank()) {
        "widget_queue_art_${artUrl.hashCode().toUInt().toString(16)}.png"
    } else {
        "widget_queue_art_$fallbackIndex.png"
    }
    return File(context.filesDir, fileName)
}

suspend fun Context.cacheWidgetArtwork(artUrl: String): Boolean {
    if (artUrl.isBlank()) return false
    return withContext(Dispatchers.IO) {
        try {
            val loader = coil.ImageLoader(this@cacheWidgetArtwork)
            val request = coil.request.ImageRequest.Builder(this@cacheWidgetArtwork)
                .data(artUrl)
                .size(512, 512)
                .allowHardware(false)
                .build()
            val result = (loader.execute(request) as? coil.request.SuccessResult)?.drawable
            val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val file = File(filesDir, "widget_artwork.png")
                val tmpFile = File(filesDir, "widget_artwork_${System.nanoTime()}.tmp")
                FileOutputStream(tmpFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                if (!tmpFile.renameTo(file)) {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

suspend fun Context.cacheQueueArtworks(queue: List<WidgetQueueItem>) {
    withContext(Dispatchers.IO) {
        val loader = coil.ImageLoader(this@cacheQueueArtworks)
        queue.take(3).forEachIndexed { index, item ->
            if (item.artworkUrl.isNotBlank()) {
                val file = queueArtworkFile(this@cacheQueueArtworks, item.artworkUrl, index)
                if (!file.exists() || file.length() == 0L) {
                    try {
                        val req = coil.request.ImageRequest.Builder(this@cacheQueueArtworks)
                            .data(item.artworkUrl)
                            .size(120, 120)
                            .allowHardware(false)
                            .build()
                        val result = (loader.execute(req) as? coil.request.SuccessResult)?.drawable
                        val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val tmpFile = File(filesDir, "${file.name}.${System.nanoTime()}.tmp")
                            FileOutputStream(tmpFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            if (!tmpFile.renameTo(file)) {
                                tmpFile.copyTo(file, overwrite = true)
                                tmpFile.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}

suspend fun Context.updateWidgetData(
    title: String,
    podcast: String,
    isPlaying: Boolean,
    remainingText: String = "",
    artUrl: String = "",
    queueJson: String = "[]"
) {
    var cachedArtUrl = ""
    var cachedQueueJson = ""
    dataStore.edit { prefs ->
        cachedArtUrl = prefs[stringPreferencesKey("widget_cached_art_url")] ?: ""
        cachedQueueJson = prefs[stringPreferencesKey("widget_cached_queue_json")] ?: ""
        prefs[stringPreferencesKey("widget_title")] = title
        prefs[stringPreferencesKey("widget_podcast")] = podcast
        prefs[booleanPreferencesKey("widget_playing")] = isPlaying
        prefs[stringPreferencesKey("widget_remaining_time")] = remainingText
        prefs[stringPreferencesKey("widget_art_url")] = artUrl
        prefs[stringPreferencesKey("widget_queue_json")] = queueJson
    }

    val mainArtFile = File(filesDir, "widget_artwork.png")
    if (artUrl.isNotBlank() && (artUrl != cachedArtUrl || !mainArtFile.exists() || mainArtFile.length() == 0L)) {
        val success = cacheWidgetArtwork(artUrl)
        if (success) {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("widget_cached_art_url")] = artUrl
            }
        }
    }

    try {
        val itemType = object : TypeToken<List<WidgetQueueItem>>() {}.type
        val queueList: List<WidgetQueueItem> = Gson().fromJson(queueJson, itemType) ?: emptyList()
        if (queueList.isNotEmpty()) {
            val anyMissing = queueList.take(3).withIndex().any { (idx, item) ->
                item.artworkUrl.isNotBlank() && !queueArtworkFile(this, item.artworkUrl, idx).exists()
            }
            if (queueJson != cachedQueueJson || anyMissing) {
                cacheQueueArtworks(queueList)
                dataStore.edit { prefs ->
                    prefs[stringPreferencesKey("widget_cached_queue_json")] = queueJson
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun createCircularBitmap(src: Bitmap): Bitmap {
    val size = minOf(src.width, src.height).coerceAtLeast(1)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    if (src.width != src.height) {
        val matrix = Matrix()
        val dx = (size - src.width) / 2f
        val dy = (size - src.height) / 2f
        matrix.setTranslate(dx, dy)
        shader.setLocalMatrix(matrix)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = shader
    }
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    return output
}

suspend fun Context.updateAllPodcastWidgets() {
    PodcastWidget().updateAll(this)
    TurntableWidget().updateAll(this)
}

class PodcastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodcastWidget()
}

class TurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TurntableWidget()
}

class TurntableWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        withContext(Dispatchers.IO) {
            val artFile = File(context.filesDir, "widget_artwork.png")
            if (!artFile.exists() || artFile.length() == 0L) {
                val statePrefs = context.getSharedPreferences("expressopod_state", Context.MODE_PRIVATE)
                val podJson = statePrefs.getString("last_podcast", null)
                val savedArtUrl = if (podJson != null) {
                    try {
                        Gson().fromJson(podJson, Podcast::class.java)?.artworkUrl ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                } else ""
                if (savedArtUrl.isNotBlank()) {
                    context.cacheWidgetArtwork(savedArtUrl)
                }
            }
        }

        provideContent {
            val prefs by context.dataStore.data.collectAsState(initial = null)
            val title = prefs?.get(stringPreferencesKey("widget_title")) ?: "ExpressoPod"
            val isPlaying = prefs?.get(booleanPreferencesKey("widget_playing")) ?: false
            val currentArtUrl = prefs?.get(stringPreferencesKey("widget_art_url")) ?: ""

            val artFile = remember { File(context.filesDir, "widget_artwork.png") }
            val circularArtBitmap: Bitmap? = remember(artFile.lastModified(), title, currentArtUrl) {
                if (artFile.exists() && artFile.length() > 0) {
                    try {
                        val raw = BitmapFactory.decodeFile(artFile.absolutePath)
                        if (raw != null) createCircularBitmap(raw) else null
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }

            val size = LocalSize.current
            val diameter = minOf(size.width, size.height).coerceAtLeast(96.dp)
            val buttonSize = (diameter * 0.34f).coerceIn(44.dp, 80.dp)
            val buttonInset = (diameter * 0.08f).coerceIn(8.dp, 22.dp)
            val iconSize = (buttonSize * 0.52f).coerceIn(22.dp, 42.dp)

            GlanceTheme {
                val fallbackDiscColor = GlanceTheme.colors.surfaceVariant
                val fallbackIconColor = GlanceTheme.colors.onSecondaryContainer
                val buttonBgColor = GlanceTheme.colors.primaryContainer
                val buttonContentColor = GlanceTheme.colors.onPrimaryContainer

                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = GlanceModifier.size(diameter),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        // Turntable Circle (Edge-to-edge Circular Podcast Cover Art, no border)
                        Box(
                            modifier = GlanceModifier
                                .size(diameter)
                                .clickable(actionStartActivity<MainActivity>()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (circularArtBitmap != null) {
                                Image(
                                    provider = ImageProvider(circularArtBitmap),
                                    contentDescription = title,
                                    modifier = GlanceModifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = GlanceModifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.bg_widget_circle),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(fallbackDiscColor),
                                        modifier = GlanceModifier.fillMaxSize()
                                    )
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_launcher_foreground),
                                        contentDescription = title,
                                        colorFilter = ColorFilter.tint(fallbackIconColor),
                                        modifier = GlanceModifier.size(diameter * 0.6f)
                                    )
                                }
                            }
                        }

                        // Bottom-left overlapping Circle Play/Pause button (the only button on the widget)
                        Box(
                            modifier = GlanceModifier
                                .padding(start = buttonInset, bottom = buttonInset)
                                .size(buttonSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .clickable(actionRunCallback<PlayPauseWidgetAction>()),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.bg_widget_circle),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(buttonBgColor),
                                    modifier = GlanceModifier.fillMaxSize()
                                )

                                Image(
                                    provider = ImageProvider(
                                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                                    ),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    colorFilter = ColorFilter.tint(buttonContentColor),
                                    modifier = GlanceModifier.size(iconSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class PodcastWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs by context.dataStore.data.collectAsState(initial = null)
            val title = prefs?.get(stringPreferencesKey("widget_title")) ?: "Tap app to start listening"
            val isPlaying = prefs?.get(booleanPreferencesKey("widget_playing")) ?: false
            val remainingText = prefs?.get(stringPreferencesKey("widget_remaining_time")) ?: ""
            val rewindSec = prefs?.get(intPreferencesKey("rewind_sec")) ?: 15
            val forwardSec = prefs?.get(intPreferencesKey("forward_sec")) ?: 30
            val queueJson = prefs?.get(stringPreferencesKey("widget_queue_json")) ?: "[]"
            val currentArtUrl = prefs?.get(stringPreferencesKey("widget_art_url")) ?: ""

            val queueList: List<WidgetQueueItem> = remember(queueJson) {
                try {
                    val itemType = object : TypeToken<List<WidgetQueueItem>>() {}.type
                    Gson().fromJson(queueJson, itemType) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val artFile = remember { File(context.filesDir, "widget_artwork.png") }
            val artBitmap: Bitmap? = remember(artFile.lastModified(), title, currentArtUrl) {
                if (artFile.exists() && artFile.length() > 0) {
                    try {
                        BitmapFactory.decodeFile(artFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            GlanceTheme {
                val cardBackground = GlanceTheme.colors.widgetBackground
                val buttonBackground = GlanceTheme.colors.secondaryContainer
                val buttonContent = GlanceTheme.colors.onSecondaryContainer
                val textPrimary = GlanceTheme.colors.onSurface
                val textSecondary = GlanceTheme.colors.onSurfaceVariant

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(cardBackground)
                        .cornerRadius(28.dp)
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        // -------------------------------------------------------------
                        // TOP SECTION: Big Art on Left, Title + Time + Controls on Right
                        // -------------------------------------------------------------
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Big Podcast Art Thumbnail
                            Box(
                                modifier = GlanceModifier
                                    .size(90.dp)
                                    .cornerRadius(16.dp)
                                    .background(buttonBackground)
                                    .clickable(actionStartActivity<MainActivity>()),
                                contentAlignment = Alignment.Center
                            ) {
                                if (artBitmap != null) {
                                    Image(
                                        provider = ImageProvider(artBitmap),
                                        contentDescription = "Podcast Artwork",
                                        modifier = GlanceModifier
                                            .fillMaxSize()
                                            .cornerRadius(16.dp)
                                    )
                                } else {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_launcher_foreground),
                                        contentDescription = "Podcast Artwork",
                                        modifier = GlanceModifier.size(56.dp)
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(12.dp))

                            // Right Column: Title at top, remaining time right underneath,
                            // and 3 rounded square playback controls at the bottom right.
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(90.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = title,
                                    style = TextStyle(
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .clickable(actionStartActivity<MainActivity>())
                                )

                                if (remainingText.isNotBlank()) {
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Text(
                                        text = remainingText,
                                        style = TextStyle(
                                            color = textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = GlanceModifier.defaultWeight())

                                // Row of 3 rounded square control buttons
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    SquareIconButton(
                                        imageProvider = ImageProvider(R.drawable.ic_widget_rewind),
                                        contentDescription = "Skip Back ${rewindSec}s",
                                        onClick = actionRunCallback<RewindWidgetAction>(),
                                        backgroundColor = buttonBackground,
                                        contentColor = buttonContent,
                                        modifier = GlanceModifier.size(40.dp)
                                    )
                                    Spacer(modifier = GlanceModifier.width(8.dp))
                                    SquareIconButton(
                                        imageProvider = ImageProvider(if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        onClick = actionRunCallback<PlayPauseWidgetAction>(),
                                        backgroundColor = buttonBackground,
                                        contentColor = buttonContent,
                                        modifier = GlanceModifier.size(40.dp)
                                    )
                                    Spacer(modifier = GlanceModifier.width(8.dp))
                                    SquareIconButton(
                                        imageProvider = ImageProvider(R.drawable.ic_widget_forward),
                                        contentDescription = "Skip Forward ${forwardSec}s",
                                        onClick = actionRunCallback<ForwardWidgetAction>(),
                                        backgroundColor = buttonBackground,
                                        contentColor = buttonContent,
                                        modifier = GlanceModifier.size(40.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(12.dp))

                        // -------------------------------------------------------------
                        // BOTTOM SECTION: Up Next Queue (Up to 3 items deep)
                        // Each row: [Thumbnail] Title + Time Left [Play Button]
                        // -------------------------------------------------------------
                        if (queueList.isEmpty()) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Queue is empty",
                                    style = TextStyle(
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        } else {
                            queueList.take(3).forEachIndexed { index, item ->
                                val queueArtFile = remember(index, item.artworkUrl, item.audioUrl) {
                                    queueArtworkFile(context, item.artworkUrl, index)
                                }
                                val legacyArtFile = remember(index) {
                                    File(context.filesDir, "widget_queue_art_$index.png")
                                }
                                val queueArtBitmap: Bitmap? = remember(queueArtFile.lastModified(), legacyArtFile.lastModified(), item.artworkUrl, item.audioUrl) {
                                    if (queueArtFile.exists() && queueArtFile.length() > 0) {
                                        try {
                                            BitmapFactory.decodeFile(queueArtFile.absolutePath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else if (item.artworkUrl.isNotBlank() && item.artworkUrl == currentArtUrl && artBitmap != null) {
                                        artBitmap
                                    } else if (legacyArtFile.exists() && legacyArtFile.length() > 0) {
                                        try {
                                            BitmapFactory.decodeFile(legacyArtFile.absolutePath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }

                                val itemTime = item.remainingTimeText.ifBlank {
                                    val durMs = parseDurationMs(item.durationText)
                                    if (durMs > 0L) formatWidgetRemainingTime(durMs) else ""
                                }

                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail Artwork on Left
                                    Box(
                                        modifier = GlanceModifier
                                            .size(46.dp)
                                            .cornerRadius(12.dp)
                                            .background(buttonBackground)
                                            .clickable(
                                                actionRunCallback<PlayQueueItemWidgetAction>(
                                                    actionParametersOf(PlayQueueItemWidgetAction.QueueIndexKey to index)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (queueArtBitmap != null) {
                                            Image(
                                                provider = ImageProvider(queueArtBitmap),
                                                contentDescription = item.title,
                                                modifier = GlanceModifier
                                                    .fillMaxSize()
                                                    .cornerRadius(12.dp)
                                            )
                                        } else {
                                            Image(
                                                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                                                contentDescription = item.title,
                                                modifier = GlanceModifier.size(34.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = GlanceModifier.width(12.dp))

                                    // Middle: Episode title and time left
                                    Column(
                                        modifier = GlanceModifier
                                            .defaultWeight()
                                            .clickable(
                                                actionRunCallback<PlayQueueItemWidgetAction>(
                                                    actionParametersOf(PlayQueueItemWidgetAction.QueueIndexKey to index)
                                                )
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = TextStyle(
                                                color = textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1
                                        )
                                        if (itemTime.isNotBlank()) {
                                            Spacer(modifier = GlanceModifier.height(2.dp))
                                            Text(
                                                text = itemTime,
                                                style = TextStyle(
                                                    color = textSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = GlanceModifier.width(8.dp))

                                    // Right: Rounded square play button
                                    SquareIconButton(
                                        imageProvider = ImageProvider(R.drawable.ic_widget_play),
                                        contentDescription = "Play ${item.title}",
                                        onClick = actionRunCallback<PlayQueueItemWidgetAction>(
                                            actionParametersOf(PlayQueueItemWidgetAction.QueueIndexKey to index)
                                        ),
                                        backgroundColor = buttonBackground,
                                        contentColor = buttonContent,
                                        modifier = GlanceModifier.size(42.dp)
                                    )
                                }

                                if (index < queueList.take(3).lastIndex) {
                                    Spacer(modifier = GlanceModifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class PlayPauseWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_PAUSE
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            }
        }
    }
}

class RewindWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_REWIND
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            }
        }
    }
}

class ForwardWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_FORWARD
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            }
        }
    }
}

class PlayQueueItemWidgetAction : ActionCallback {
    companion object {
        val QueueIndexKey = ActionParameters.Key<Int>("queue_index")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[QueueIndexKey] ?: 0
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY_QUEUE_INDEX
            putExtra("queue_index", index)
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
