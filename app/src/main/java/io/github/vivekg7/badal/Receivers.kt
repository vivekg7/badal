package io.github.vivekg7.badal

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** Runs [work] on the app's scope and keeps the receiver alive until it ends (10 s at most). */
private fun BroadcastReceiver.async(
    context: Context,
    work: suspend () -> Unit,
) {
    val pending = goAsync()
    context.app.scope.launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}

/** The one alarm Engine sets: for an interval or a time range's edge. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = async(context) {
        context.app.engine.tick()
        context.app.library.rescan(daily = true)
    }
}

/**
 * Alarms don't survive a reboot or an update, and a clock or time-zone change
 * moves when a time range starts. Each of these broadcasts may also start the
 * lock watcher from the background, which Android otherwise refuses.
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in actions) async(context) { context.app.engine.settle() }
    }

    private val actions =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
}
