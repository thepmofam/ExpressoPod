# ExpressoPod Proguard & R8 Rules

# Preserve all model data classes used with Gson and DataStore across builds
-keepclassmembers class com.rpmtech.expressopod.Podcast { *; }
-keepclassmembers class com.rpmtech.expressopod.Episode { *; }
-keepclassmembers class com.rpmtech.expressopod.DownloadedEpisode { *; }
-keepclassmembers class com.rpmtech.expressopod.QueueItem { *; }
-keepclassmembers class com.rpmtech.expressopod.Chapter { *; }
-keep class com.rpmtech.expressopod.Podcast { *; }
-keep class com.rpmtech.expressopod.Episode { *; }
-keep class com.rpmtech.expressopod.DownloadedEpisode { *; }
-keep class com.rpmtech.expressopod.QueueItem { *; }
-keep class com.rpmtech.expressopod.Chapter { *; }

# Preserve any class annotated with @Keep or @SerializedName
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Preserve Gson reflection & type tokens
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }

# Glance
-keep class androidx.glance.** { *; }

# Reorderable
-keep class sh.calvin.reorderable.** { *; }
