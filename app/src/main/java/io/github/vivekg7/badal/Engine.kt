package io.github.vivekg7.badal

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Turns [Plan]'s decisions into wallpapers. Every change goes through one
 * mutex, so a screen-off, an alarm and a tap can't interleave. The engine
 * only ever writes the two Progress values back; the user's settings and
 * albums are the UI's to change.
 */
class Engine(
    private val context: Context,
    private val store: Store,
    private val images: Images,
) {
    private val mutex = Mutex()
    private val zone get() = ZoneId.systemDefault()
    private val drawing = MutableStateFlow(false)

    /** True while a wallpaper is being drawn and set, which takes a second or so. */
    val busy: StateFlow<Boolean> = drawing

    /** The screen went off: change the screens that change on lock. */
    suspend fun screenOff() = change { st -> if (paused(st)) emptyList() else st.driven.filter { st.rotation(it).onLock } }

    /** The alarm fired: change whatever is due. */
    suspend fun tick() =
        change { st ->
            val sense = sense()
            st.driven.filter { Plan.due(st, it, now(), zone, paused(st), sense) }
        }

    suspend fun next(screens: Collection<Screen>) = change { screens }

    suspend fun previous(s: Screen) =
        mutex.withLock {
            val st = store.state.value
            val p = Plan.back(st.progress(s)) { images.file(it).exists() } ?: return@withLock
            commit(st, Plan.synced(st.with(s, p)))
        }

    /** Put one chosen picture on [screens] now, whether or not Badal rotates them. */
    suspend fun show(
        id: Long,
        screens: Collection<Screen>,
    ) = mutex.withLock {
        val st = store.state.value
        var out = st
        val sense = sense()
        for (s in screens) out = out.with(s, Plan.show(out.progress(s), id, Plan.sourceAt(out, s, now(), zone, sense), now()))
        commit(st, Plan.synced(out), force = screens.toSet())
    }

    /**
     * The screens Badal is the live wallpaper of, which draw their pictures
     * themselves (plan 004). A lock screen that shares the home screen's live
     * wallpaper has none of its own and isn't counted: it shows the home picture.
     */
    fun live(): Set<Screen> {
        val wm = WallpaperManager.getInstance(context)
        val badal = ComponentName(context, LiveWallpaper::class.java)
        return Screen.entries.filter { wm.getWallpaperInfo(it.flag)?.component == badal }.toSet()
    }

    /** Leave the live wallpaper on [s]: setting its picture as a still one replaces it. */
    suspend fun still(s: Screen) =
        mutex.withLock {
            val st = store.state.value
            if (st.progress(s).current == null) {
                WallpaperManager.getInstance(context).clear(s.flag)
            } else {
                commit(st, st, force = setOf(s), live = live() - s)
            }
        }

    /** Change one screen's settings, then bring the wallpapers in line with them. */
    suspend fun edit(
        s: Screen,
        change: (Rotation) -> Rotation,
    ) {
        val before = store.state.value.rotation(s)
        val st = store.update { it.with(s, change(it.rotation(s))) }
        val after = st.rotation(s)
        val shown = st.progress(s).current
        val redraw =
            after.enabled &&
                (
                    !before.enabled ||
                        after.finish != before.finish ||
                        after.sameAsHome != before.sameAsHome ||
                        (shown != null && after.frame(shown) != before.frame(shown))
                )
        settle(if (redraw) setOf(s) else emptySet())
    }

    /** Frame picture [id] on each screen in [frames] (null: back to the screen's fit), and redraw the screens showing it. */
    suspend fun frame(
        id: Long,
        frames: Map<Screen, Frame?>,
    ) {
        val st = store.update { st -> frames.entries.fold(st) { acc, (s, f) -> acc.with(s, acc.rotation(s).framed(id, f)) } }
        settle(frames.keys.filter { st.rotation(it).enabled && st.progress(it).current == id }.toSet())
    }

    /**
     * After any change to settings or albums: screens with no picture yet, or whose
     * album is no longer the active one, get a new picture; [redraw] screens are
     * drawn again; and the alarm and the lock watcher are set for the new settings.
     */
    suspend fun settle(redraw: Set<Screen> = emptySet()) =
        mutex.withLock {
            val st = store.state.value
            val sense = sense()
            val stale =
                st.driven.filter {
                    st.progress(it).current == null ||
                        Plan.sourceAt(st, it, now(), zone, sense) != st.progress(it).album
                }
            commit(st, Plan.advance(st, stale, now(), zone, sense = sense), force = redraw)
            LockWatcher.sync(context, store.state.value)
        }

    private suspend fun change(which: (State) -> Collection<Screen>) =
        mutex.withLock {
            val st = store.state.value
            commit(st, Plan.advance(st, which(st), now(), zone, sense = sense()))
        }

    /** Draws what changed between [before] and [after], saves the new progress, and sets the next alarm. */
    private suspend fun commit(
        before: State,
        after: State,
        force: Set<Screen> = emptySet(),
        live: Set<Screen> = live(),
    ) {
        val wanted =
            Screen.entries.mapNotNull { s ->
                val id = after.progress(s).current ?: return@mapNotNull null
                val touched = s in force || (after.rotation(s).enabled && id != before.progress(s).current)
                // The live wallpaper follows homeNow / lockNow on its own; a still one would replace it.
                if (touched && s !in live) s to id else null
            }
        drawing.value = wanted.isNotEmpty()
        // Busy until the new progress is saved, so the app never shows the old picture as done.
        val saved =
            try {
                withContext(Dispatchers.IO) {
                    val (w, h) = images.screen()
                    val drawn = mutableMapOf<List<Any>, android.graphics.Bitmap>()
                    for ((s, id) in wanted) {
                        val r = after.rotation(s)
                        try {
                            // Set each screen on its own, never both flags at once: that makes
                            // the lock screen follow the home screen from then on.
                            val frame = r.frame(id)
                            val bmp = drawn.getOrPut(listOf(id, frame, r.finish)) { images.render(id, w, h, frame, r.finish) }
                            WallpaperManager.getInstance(context).setBitmap(bmp, null, true, s.flag)
                        } catch (e: Exception) {
                            Log.e("Badal", "can't set $s to $id", e)
                        }
                    }
                    drawn.values.forEach { it.recycle() }
                }
                store.update { it.copy(homeNow = after.homeNow, lockNow = after.lockNow) }
            } finally {
                drawing.value = false
            }
        schedule(saved)
        NextWidget.update(context, saved)
    }

    private fun schedule(st: State) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val alarm = Plan.nextAlarm(st, now(), zone, paused(st), sense()) ?: return am.cancel(pi)
        val type = if (alarm.wake) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
        // Exact needs the user's grant (Settings tab); without it Android may
        // deliver a few minutes late, which is fine for a wallpaper.
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(type, alarm.at, pi) else am.setAndAllowWhileIdle(type, alarm.at, pi)
    }

    /**
     * What modes can go by (plan 009), read fresh each time: none of it needs a
     * permission. The lock watcher hears these change and calls [settle].
     */
    fun sense(): Sense {
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return Sense(
            quiet = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
        )
    }

    /** Switch [id] on by hand, or none (null); from the app, a launcher shortcut or a routine. */
    suspend fun mode(id: Long?) {
        store.update { it.copy(handMode = id) }
        settle()
    }

    /** Battery saver is on, and the user asked Badal to wait it out. */
    fun paused(st: State) = st.saverPause && context.getSystemService(PowerManager::class.java).isPowerSaveMode

    private fun now() = System.currentTimeMillis()
}

private val Screen.flag get() = if (this == Screen.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
