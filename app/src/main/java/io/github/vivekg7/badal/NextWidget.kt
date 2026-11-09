package io.github.vivekg7.badal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import kotlinx.coroutines.launch

/**
 * A home-screen widget (plan 010): the picture on the home screen, or on the
 * lock screen when only that one changes, with Previous and Next. It is drawn
 * again after every change the engine saves, so it needs no update period.
 */
class NextWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) = update(context, context.app.store.state.value)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        val app = context.app
        val action = intent.action
        if (action != ACTION_NEXT && action != ACTION_PREVIOUS) return
        val pending = goAsync()
        app.scope.launch {
            try {
                val st = app.store.state.value
                if (action == ACTION_NEXT) app.engine.next(st.driven) else st.driven.forEach { app.engine.previous(it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_NEXT = "io.github.vivekg7.badal.WIDGET_NEXT"
        private const val ACTION_PREVIOUS = "io.github.vivekg7.badal.WIDGET_PREVIOUS"

        fun update(
            context: Context,
            st: State,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextWidget::class.java))
            if (ids.isEmpty()) return
            val screen = if (!st.home.enabled && st.lock.enabled) Screen.LOCK else Screen.HOME
            // Half the thumbnail's size: a widget's picture travels to the launcher in one binder call, which holds 1 MB.
            val thumb =
                st.progress(screen).current?.let {
                    BitmapFactory.decodeFile(
                        context.app.images
                            .thumb(it)
                            .path,
                        BitmapFactory.Options().apply { inSampleSize = 2 },
                    )
                }

            fun send(action: String) =
                PendingIntent.getBroadcast(context, 0, Intent(context, NextWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val views =
                RemoteViews(context.packageName, R.layout.widget).apply {
                    if (thumb != null) setImageViewBitmap(R.id.picture, thumb) else setImageViewResource(R.id.picture, R.drawable.widget_empty)
                    setOnClickPendingIntent(R.id.picture, open)
                    setOnClickPendingIntent(R.id.previous, send(ACTION_PREVIOUS))
                    setOnClickPendingIntent(R.id.next, send(ACTION_NEXT))
                }
            manager.updateAppWidget(ids, views)
        }
    }
}
