package io.github.vivekg7.badal

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
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

    /** The screen went off: change the screens that change on lock. */
    suspend fun screenOff() = change { st -> st.driven.filter { st.rotation(it).onLock } }

    /** The alarm fired: change whatever is due. */
    suspend fun tick() = change { st -> st.driven.filter { Plan.due(st, it, now(), zone) } }

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
        for (s in screens) out = out.with(s, Plan.show(out.progress(s), id, Plan.albumAt(out.rotation(s), now(), zone), now()))
        commit(st, Plan.synced(out), force = screens.toSet())
    }

    /** Change one screen's settings, then bring the wallpapers in line with them. */
    suspend fun edit(
        s: Screen,
        change: (Rotation) -> Rotation,
    ) {
        val before = store.state.value.rotation(s)
        val after = store.update { it.with(s, change(it.rotation(s))) }.rotation(s)
        val redraw =
            after.enabled &&
                (!before.enabled || after.fit != before.fit || after.dim != before.dim || after.sameAsHome != before.sameAsHome)
        settle(if (redraw) setOf(s) else emptySet())
    }

    /**
     * After any change to settings or albums: screens with no picture yet, or whose
     * album is no longer the active one, get a new picture; [redraw] screens are
     * drawn again; and the alarm and the lock watcher are set for the new settings.
     */
    suspend fun settle(redraw: Set<Screen> = emptySet()) =
        mutex.withLock {
            val st = store.state.value
            val stale =
                st.driven.filter {
                    st.progress(it).current == null ||
                        Plan.albumAt(st.rotation(it), now(), zone) != st.progress(it).album
                }
            commit(st, Plan.advance(st, stale, now(), zone), force = redraw)
            LockWatcher.sync(context, store.state.value)
        }

    private suspend fun change(which: (State) -> Collection<Screen>) =
        mutex.withLock {
            val st = store.state.value
            commit(st, Plan.advance(st, which(st), now(), zone))
        }

    /** Draws what changed between [before] and [after], saves the new progress, and sets the next alarm. */
    private suspend fun commit(
        before: State,
        after: State,
        force: Set<Screen> = emptySet(),
    ) {
        val wanted =
            Screen.entries.mapNotNull { s ->
                val id = after.progress(s).current ?: return@mapNotNull null
                val touched = s in force || (after.rotation(s).enabled && id != before.progress(s).current)
                if (touched) s to id else null
            }
        withContext(Dispatchers.IO) {
            val (w, h) = images.screen()
            val drawn = mutableMapOf<Triple<Long, Fit, Int>, android.graphics.Bitmap>()
            for ((s, id) in wanted) {
                val r = after.rotation(s)
                try {
                    // Set each screen on its own, never both flags at once: that makes
                    // the lock screen follow the home screen from then on.
                    val bmp = drawn.getOrPut(Triple(id, r.fit, r.dim)) { images.render(id, w, h, r.fit, r.dim) }
                    val flag = if (s == Screen.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
                    WallpaperManager.getInstance(context).setBitmap(bmp, null, true, flag)
                } catch (e: Exception) {
                    Log.e("Badal", "can't set $s to $id", e)
                }
            }
            drawn.values.forEach { it.recycle() }
        }
        val saved = store.update { it.copy(homeNow = after.homeNow, lockNow = after.lockNow) }
        schedule(saved)
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
        val alarm = Plan.nextAlarm(st, now(), zone) ?: return am.cancel(pi)
        val type = if (alarm.wake) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
        // Exact needs the user's grant (Settings tab); without it Android may
        // deliver a few minutes late, which is fine for a wallpaper.
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(type, alarm.at, pi) else am.setAndAllowWhileIdle(type, alarm.at, pi)
    }

    private fun now() = System.currentTimeMillis()
}
