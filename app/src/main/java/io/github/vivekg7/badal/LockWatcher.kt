package io.github.vivekg7.badal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Android sends "screen off" only to receivers registered by a running app, so
 * changing on every lock needs a process that stays up: this foreground
 * service. It runs only while a screen changes on lock, and does nothing but
 * wait for that broadcast.
 *
 * Badal never asks to post notifications, so on Android 13+ its notification
 * is hidden; the service shows in the Task Manager instead.
 */
class LockWatcher : Service() {
    private val screenOff =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val pending = goAsync()
                app.scope.launch {
                    try {
                        app.engine.screenOff()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Change on lock", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_glyph)
                .setContentTitle("Changing wallpaper on each lock")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ) = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "watcher"

        fun needed(st: State) = st.driven.any { st.rotation(it).onLock }

        /** Start or stop the watcher to match [st]. */
        fun sync(
            context: Context,
            st: State,
        ) {
            val intent = Intent(context, LockWatcher::class.java)
            if (!needed(st)) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Started from the background with no exemption. Android restarts a
                // sticky service it stopped itself, and the next boot or app launch
                // starts it again.
                Log.w("Badal", "can't start the lock watcher now", e)
            }
        }
    }
}
