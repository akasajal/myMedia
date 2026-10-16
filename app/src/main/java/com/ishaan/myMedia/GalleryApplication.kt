package com.ishaan.myMedia

import android.app.Application
import android.content.SharedPreferences
import com.ishaan.myMedia.widget.PhotoWidgetProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GalleryApplication : Application(), ImageLoaderFactory {
    // Keep a strong reference: SharedPreferences retains listeners weakly.
    private val widgetPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pin_enabled" || key == "saf_uris" || key == "recycle_bin" ||
            key == "saf_cache" || key?.startsWith("folder_visible_") == true) {
            PhotoWidgetProvider.updateAll(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("gallery_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(widgetPreferencesListener)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
