package com.rpmtech.expressopod

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object EpisodeNotificationManager {
    const val CHANNEL_ID = "channel_new_podcast_episodes"
    private const val CHANNEL_NAME = "New Podcast Episodes"
    private const val CHANNEL_DESC = "Notifications for new episodes of subscribed podcasts"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNewEpisodeNotification(context: Context, podcast: Podcast, episode: Episode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_PODCAST_EPISODES"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "OPEN_PODCAST_EPISODES")
            putExtra("feed_url", podcast.feedUrl)
            putExtra("podcast_title", podcast.title)
            putExtra("podcast_author", podcast.author)
            putExtra("podcast_artwork", podcast.artworkUrl)
            putExtra("audio_url", episode.audioUrl)
            putExtra("episode_title", episode.title)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            podcast.feedUrl.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Episode: ${episode.title}")
            .setContentText(podcast.title)
            .setSubText(podcast.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${podcast.title} has released a new episode:\n${episode.title}")
                    .setBigContentTitle("New Episode: ${episode.title}")
                    .setSummaryText(podcast.title)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(episode.audioUrl.hashCode(), notification)
    }
}
