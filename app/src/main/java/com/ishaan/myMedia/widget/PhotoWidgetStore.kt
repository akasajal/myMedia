package com.ishaan.myMedia.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class PhotoWidgetConfig(
    val imageUri: String? = null,
    val folders: Set<String> = emptySet(),
    val intervalSeconds: Int = 15,
    val shuffle: Boolean = false,
    val fit: Boolean = false,
    val allowWhileLocked: Boolean = false,
    val revision: Long = 0,
)

internal fun widgetPhotosAllowed(lockEnabled: Boolean, config: PhotoWidgetConfig?): Boolean =
    config != null && (!lockEnabled || config.allowWhileLocked)

/** Folder selections include descendants, with path boundaries preserved. */
internal fun widgetFolderMatches(album: String, folders: Set<String>): Boolean =
    folders.any { album == it || album.startsWith("${it.trimEnd('/')}/") }

internal fun widgetFolderVisible(album: String, isVisible: (String) -> Boolean): Boolean {
    var path = album.trimEnd('/')
    while (path.isNotEmpty()) {
        if (!isVisible(path)) return false
        path = path.substringBeforeLast('/', "")
    }
    return true
}

internal val widgetIntervals = listOf(15, 30, 60, 300)

internal const val MAX_WIDGET_PIXELS = 2_000_000L

/** Match physical widget pixels, bounded by a screen-sized budget and a 2 MP ceiling. */
internal fun widgetBitmapSize(widthDp: Int, heightDp: Int, density: Float = 1f,
    maxPixels: Long = MAX_WIDGET_PIXELS): Pair<Int, Int> {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val w = widthDp.coerceAtLeast(1).toDouble() * safeDensity
    val h = heightDp.coerceAtLeast(1).toDouble() * safeDensity
    val budget = maxPixels.coerceIn(1, MAX_WIDGET_PIXELS)
    val scale = minOf(1.0, sqrt(budget / (w * h)))
    return (w * scale).toInt().coerceIn(1, budget.toInt()) to
        (h * scale).toInt().coerceIn(1, budget.toInt())
}

class PhotoWidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("photo_widgets", Context.MODE_PRIVATE)

    fun read(id: Int): PhotoWidgetConfig? = try {
        prefs.getString(id.toString(), null)?.let { raw ->
            val json = JSONObject(raw)
            val array = json.optJSONArray("folders") ?: JSONArray()
            PhotoWidgetConfig(
                imageUri = json.optString("image").ifBlank { null },
                folders = (0 until array.length()).map { array.getString(it) }.toSet(),
                intervalSeconds = json.optInt("interval", 15).takeIf { it in widgetIntervals } ?: 15,
                shuffle = json.optBoolean("shuffle"),
                fit = json.optBoolean("fit"),
                allowWhileLocked = json.optBoolean("allowWhileLocked", false),
                revision = json.optLong("revision", 0),
            )
        }
    } catch (_: Exception) { null }

    fun save(id: Int, config: PhotoWidgetConfig) {
        val json = JSONObject().apply {
            put("image", config.imageUri.orEmpty())
            put("folders", JSONArray(config.folders.toList()))
            put("interval", config.intervalSeconds)
            put("shuffle", config.shuffle)
            put("fit", config.fit)
            put("allowWhileLocked", config.allowWhileLocked)
            // Each save must replace the launcher's existing slideshow, even at the same interval.
            put("revision", (read(id)?.revision ?: 0) + 1)
        }
        prefs.edit().putString(id.toString(), json.toString()).apply()
    }

    fun remove(id: Int) { prefs.edit().remove(id.toString()).apply() }
}
