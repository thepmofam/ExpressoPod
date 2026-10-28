# ExpressoPod ☕🎙️

**ExpressoPod** is a modern, expressive, and high-performance podcast player for Android built with **Jetpack Compose**, **Material 3 Expressive**, **AndroidX Media3 (ExoPlayer)**, and **Jetpack Glance**.

---

## ✨ Features

- **🎨 Material 3 Expressive UI**: Fluid layouts, responsive animations, and seamless dark, light, and system theme adaptation.
- **🎧 Robust Background Audio**: Powered by **AndroidX Media3** and `MediaSessionService` with persistent playback notifications and lock screen media controls.
- **📑 Chapter Support**: Full chapter navigation supporting Podcasting 2.0 JSON metadata as well as interactive timestamps parsed directly from show notes.
- **📝 Formatted Show Notes**: Rich HTML and Markdown notes rendering with custom styling, clickable web links, and interactive seekable timestamps.
- **📥 Offline Downloads**: Download favorite episodes for offline playback with background download management.
- **🔀 Up Next Queue**: Customizable playback queue with intuitive long-press drag-and-drop reordering (`reorderable`).
- **📱 Home Screen Widget**: Interactive Material 3 widget powered by **Jetpack Glance** for quick play, pause, skip, and rewind directly from your launcher.
- **🔔 New Episode Notifications**: Periodic background checking via **WorkManager** to notify you when your favorite subscriptions release new episodes.
- **⚡ High Performance & Optimized**:
  - Memory and disk image caching with **Coil**.
  - On-disk HTTP response caching with **OkHttp**.
  - Non-blocking asynchronous state persistence.
  - Precompiled regular expressions and optimized $O(1)$ Compose collection lookups.
- **🛡️ Secure by Design**:
  - Whitelisted web URI intent redirection.
  - Hardened XML parsers against DTD / entity expansion.
  - Canonical storage boundary checks preventing path traversal.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Expressive (`androidx.compose.material3:1.4.0-alpha04`)
- **Audio Engine**: [AndroidX Media3](https://developer.android.com/media/media3) (`media3-exoplayer`, `media3-session`, `media3-common`)
- **Widgets**: [Jetpack Glance](https://developer.android.com/jetpack/compose/glance) (`glance-appwidget`, `glance-material3`)
- **Background Work**: [AndroidX WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & [OkHttp 4](https://square.github.io/okhttp/) with disk caching
- **Image Loading**: [Coil Compose](https://coil-kt.github.io/coil/)
- **Data Persistence**: [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) & SharedPreferences
- **Language**: 100% [Kotlin](https://kotlinlang.org/)

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 35 (Compile SDK: 35, Min SDK: 26)
- JDK 17+

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
./gradlew assembleRelease
```
The output APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`
