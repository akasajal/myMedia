package com.ishaan.myMedia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.ishaan.myMedia.R
import com.ishaan.myMedia.data.repository.PrivacyRepository

// Service-backed collections support the app's API 26 minimum without loading an entire album into Binder.
@Suppress("DEPRECATION")
class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { PhotoWidgetStore(context).remove(it) }
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val store = PhotoWidgetStore(context)
        // Read everything before remapping, since new IDs can overlap old IDs.
        val configs = oldIds.map(store::read)
        oldIds.forEach(store::remove)
        newIds.forEachIndexed { index, id ->
            configs.getOrNull(index)?.let { store.save(id, it) }
            AppWidgetManager.getInstance(context).updateAppWidgetOptions(id,
                Bundle().apply { putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) })
            update(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id !in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PhotoWidgetProvider::class.java))) return
            val config = PhotoWidgetStore(context).read(id) ?: return
            if (widgetPhotosAllowed(PrivacyRepository(context).getPrivacyPrefs().pinEnabled, config)) {
                AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id,
                    RemoteViews(context.packageName, layout(config)).apply { showNext(R.id.widget_slideshow) })
            }
        }
    }

    companion object {
        private const val ACTION_NEXT = "com.ishaan.myMedia.widget.NEXT"

        private fun layout(config: PhotoWidgetConfig?): Int = when (config?.intervalSeconds) {
            30 -> R.layout.photo_widget_30
            60 -> R.layout.photo_widget_60
            300 -> R.layout.photo_widget_300
            else -> R.layout.photo_widget_15
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, PhotoWidgetProvider::class.java)).forEach { update(context, it) }
        }

        fun update(context: Context, id: Int) {
            val config = PhotoWidgetStore(context).read(id)
            val locked = !widgetPhotosAllowed(PrivacyRepository(context).getPrivacyPrefs().pinEnabled, config) && config != null
            val views = RemoteViews(context.packageName, if (locked || config == null) R.layout.photo_widget_empty else layout(config))
            val configure = Intent(context, PhotoWidgetActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("mymedia-widget://configure/$id"))
            val configurePending = PendingIntent.getActivity(context, id, configure,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_configure, configurePending)
            views.setOnClickPendingIntent(R.id.widget_empty, configurePending)
            views.setTextViewText(R.id.widget_empty, context.getString(when {
                locked -> R.string.widget_locked
                config == null -> R.string.widget_setup
                else -> R.string.widget_no_images
            }))
            if (locked || config == null) {
                AppWidgetManager.getInstance(context).updateAppWidget(id, views)
                return
            }
            views.setViewVisibility(R.id.widget_next, if (config.imageUri == null) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_slideshow, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            run {
                // A fresh adapter identity discards launcher-cached rows after privacy/library changes.
                val adapter = Intent(context, PhotoWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("mymedia-widget://adapter/$id/${System.nanoTime()}"))
                views.setRemoteAdapter(R.id.widget_slideshow, adapter)
                views.setEmptyView(R.id.widget_slideshow, R.id.widget_empty)
                val open = Intent(context, PhotoWidgetActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(PhotoWidgetActivity.EXTRA_VIEW, true)
                    .setData(Uri.parse("mymedia-widget://open/$id"))
                views.setPendingIntentTemplate(R.id.widget_slideshow, PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
                val next = Intent(context, PhotoWidgetProvider::class.java).setAction(ACTION_NEXT)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("mymedia-widget://next/$id"))
                views.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(context, id, next,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            AppWidgetManager.getInstance(context).updateAppWidget(id, views)
        }
    }
}
