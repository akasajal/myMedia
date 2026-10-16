package com.ishaan.myMedia.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.ishaan.myMedia.R
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.data.model.MediaType
import com.ishaan.myMedia.data.repository.MediaRepository
import com.ishaan.myMedia.data.repository.PrivacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal suspend fun widgetImages(context: Context, config: PhotoWidgetConfig): List<MediaItem> {
    if (PrivacyRepository(context).getPrivacyPrefs().pinEnabled) return emptyList()
    val repo = MediaRepository(context)
    val visibility = mutableMapOf<String, Boolean>()
    return repo.loadVisibleMedia().filter { item ->
        item.mediaType != MediaType.VIDEO &&
            visibility.getOrPut(item.albumId) { widgetFolderVisible(item.albumId, repo::isFolderVisible) } &&
            (if (config.imageUri != null) item.uri.toString() == config.imageUri else widgetFolderMatches(item.albumId, config.folders))
    }.distinctBy { it.uri }.let { if (config.shuffle) it.shuffled() else it }
}

class PhotoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = PhotoFactory(
        applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class PhotoFactory(private val context: Context, private val id: Int) : RemoteViewsService.RemoteViewsFactory {
    private var config = PhotoWidgetConfig()
    private var items = emptyList<MediaItem>()
    private var bitmapSize = 600 to 300
    override fun onCreate() = Unit
    override fun onDataSetChanged() {
        config = PhotoWidgetStore(context).read(id) ?: run { items = emptyList(); return }
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        bitmapSize = widgetBitmapSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150))
        items = runBlocking(Dispatchers.IO) {
            try { widgetImages(context, config) } catch (_: Exception) { emptyList() }
        }
    }
    override fun getCount() = items.size
    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, if (config.fit) R.layout.photo_widget_item_fit else R.layout.photo_widget_item)
        val item = items.getOrNull(position) ?: return views
        // Recheck on every request, even when the launcher retained this factory.
        if (PrivacyRepository(context).getPrivacyPrefs().pinEnabled ||
            !widgetFolderVisible(item.albumId, MediaRepository(context)::isFolderVisible)) return views
        val bitmap = runBlocking(Dispatchers.IO) {
            try {
                val (width, height) = bitmapSize
                val request = ImageRequest.Builder(context).data(item.uri).size(width, height)
                    .scale(if (config.fit) Scale.FIT else Scale.FILL).allowHardware(false).build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()?.let { source ->
                    // Render a bounded bitmap without stretching portrait or panoramic photos.
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
                        val canvas = Canvas(output)
                        canvas.drawColor(android.graphics.Color.rgb(22, 19, 29))
                        val sx = width.toFloat() / source.width
                        val sy = height.toFloat() / source.height
                        val scale = if (config.fit) minOf(sx, sy) else maxOf(sx, sy)
                        val w = source.width * scale
                        val h = source.height * scale
                        canvas.drawBitmap(source, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                }
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setContentDescription(R.id.widget_image, context.getString(R.string.widget_photo_description, item.name))
            views.setViewVisibility(R.id.widget_image_error, android.view.View.GONE)
            views.setOnClickFillInIntent(R.id.widget_image, Intent().putExtra(PhotoWidgetActivity.EXTRA_URI, item.uri.toString()))
        } else {
            views.setTextViewText(R.id.widget_image_error, context.getString(R.string.widget_unreadable))
        }
        return views
    }
    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.photo_widget_loading)
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() { items = emptyList() }
}
