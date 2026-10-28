package com.rpmtech.expressopod

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class ExpressoPodApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        try {
            NetworkModule.initCache(cacheDir)
        } catch (_: Exception) {}
    }

    override fun newImageLoader(): ImageLoader {
        return try {
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .okHttpClient {
                    NetworkModule.okHttp
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
        } catch (_: Exception) {
            ImageLoader.Builder(this).build()
        }
    }
}
