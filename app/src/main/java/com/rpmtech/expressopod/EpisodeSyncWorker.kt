package com.rpmtech.expressopod

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class EpisodeSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            FeedCredentialsManager.init(context)
        } catch (_: Exception) {}
        val prefs = context.dataStore.data.first()
        val notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: false
        if (!notificationsEnabled) {
            return Result.success()
        }

        val subscriptionsJson = prefs[KEY_SUBSCRIPTIONS]
        var subscriptions: List<Podcast> = SubscriptionStorageManager.parseSubscriptionsJson(subscriptionsJson)
        if (subscriptions.isEmpty()) {
            subscriptions = SubscriptionStorageManager.loadSubscriptionsFromBackup(context)
        }

        if (subscriptions.isEmpty()) {
            return Result.success()
        }

        val knownEpisodes = prefs[KEY_KNOWN_EPISODES]?.toMutableSet() ?: mutableSetOf()
        val isFirstRun = knownEpisodes.isEmpty()
        val newKnownEpisodes = HashSet(knownEpisodes)
        val mutedPodcastUrls = prefs[KEY_MUTED_PODCASTS] ?: emptySet()

        for (podcast in subscriptions) {
            if (LocalAudioManager.isLocalPodcast(podcast) || mutedPodcastUrls.contains(podcast.feedUrl)) {
                continue
            }
            try {
                val feed = NetworkModule.fetchPodcastFeed(podcast.feedUrl) ?: continue
                if (isFirstRun) {
                    // Record all existing episodes so we do not spam notifications for old episodes
                    feed.episodes.forEach { newKnownEpisodes.add(it.audioUrl) }
                } else {
                    for (episode in feed.episodes) {
                        if (!knownEpisodes.contains(episode.audioUrl)) {
                            // New episode discovered!
                            EpisodeNotificationManager.showNewEpisodeNotification(context, podcast, episode)
                            newKnownEpisodes.add(episode.audioUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        context.dataStore.edit { editPrefs ->
            editPrefs[KEY_KNOWN_EPISODES] = newKnownEpisodes
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "EpisodeSyncWork"
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
        val KEY_KNOWN_EPISODES = stringSetPreferencesKey("known_episodes")
        val KEY_MUTED_PODCASTS = stringSetPreferencesKey("muted_notification_podcasts")

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<EpisodeSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<EpisodeSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
