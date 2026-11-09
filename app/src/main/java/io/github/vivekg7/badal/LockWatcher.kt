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
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.launch

/**
 * Android sends "screen off" only to receivers registered by a running app, so
 * changing on every lock needs a process that stays up: this foreground
 * service. It runs only while a screen changes on lock or a mode has a cue to
 * wait for (plan 009): Do Not Disturb, charging and the dark theme are also
 * told only to a running app. It does nothing but wait for those.
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
                        if (intent.action == Intent.ACTION_SCREEN_OFF) {
                            app.engine.screenOff()
                            // A followed folder gets its daily look while nobody is watching.
                            app.library.rescan(daily = true)
                        } else {
                            app.engine.settle()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    private var night = 0

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Change on lock", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_glyph)
                .setContentTitle("Waiting to change the wallpaper")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val heard =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        // System broadcasts all; NOT_EXPORTED keeps other apps from sending them.
        registerReceiver(screenOff, heard, RECEIVER_NOT_EXPORTED)
        night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    /** The dark theme came on or went off. */
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        val now = c.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (now == night) return
        night = now
        app.scope.launch { app.engine.settle() }
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

        fun needed(st: State) =
            st.driven.any { st.rotation(it).onLock && Plan.rotates(st, it) } ||
                st.modes.any { m -> m.cue != Cue.HAND && st.driven.any { m.album(it) != null } }

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
