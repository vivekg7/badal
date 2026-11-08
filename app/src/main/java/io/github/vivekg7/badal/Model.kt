package io.github.vivekg7.badal

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/*
 * Everything Badal knows, as one value saved to one JSON file (Store), and the
 * pure rules that move it on. Nothing here touches Android, so the rules are
 * unit-tested on the JVM (PlanTest).
 */

@Serializable
enum class Screen { HOME, LOCK }

@Serializable
enum class Order { SHUFFLE, IN_ORDER }

/** FILL crops the picture to cover the screen; WHOLE fits all of it over a blurred copy. */
@Serializable
enum class Fit { FILL, WHOLE }

/** A list of pictures, in the order the user sees and "in order" plays them. Ids name the files (Images). */
@Serializable
data class Album(
    val id: Long,
    val name: String,
    val images: List<Long> = emptyList(),
    /** Favourites: Shuffle shows each of these twice a round. */
    val starred: Set<Long> = emptySet(),
)

/**
 * Use [albumId] from [start] until [end], in minutes after midnight, on [days]
 * (ISO numbers, Monday 1 to Sunday 7; empty is every day). A range may run past
 * midnight, and that part belongs to the day it started: Friday 22:00–06:00
 * holds until Saturday 06:00.
 */
@Serializable
data class TimeRange(
    val start: Int,
    val end: Int,
    val albumId: Long,
    val days: Set<Int> = emptySet(),
) {
    fun holds(
        day: Int,
        minute: Int,
    ): Boolean {
        fun on(d: Int) = days.isEmpty() || d in days
        val yesterday = (day + 5) % 7 + 1
        return when {
            start == end -> on(day)
            start < end -> on(day) && minute in start until end
            else -> (on(day) && minute >= start) || (on(yesterday) && minute < end)
        }
    }
}

/** What the user set for one screen. */
@Serializable
data class Rotation(
    val enabled: Boolean = false,
    val albumId: Long? = null,
    /** Lock screen only: show the home screen's picture, changed with it. */
    val sameAsHome: Boolean = false,
    val order: Order = Order.SHUFFLE,
    /** Change each time the screen turns off, so the next wake shows a new picture. */
    val onLock: Boolean = true,
    /** Also change this often; 0 is off. */
    val everyMinutes: Int = 0,
    /** Time-of-day albums; the first range that holds the time wins over [albumId]. */
    val ranges: List<TimeRange> = emptyList(),
    /** For pictures without a frame of their own in [frames]. */
    val fit: Fit = Fit.FILL,
    /** Darken by this many percent, so a busy picture does not hide the clock or icons. */
    val dim: Int = 0,
    /** Lock screen: darken by this many percent behind the clock, fading out below it (plan 003). */
    val shade: Int = 0,
    /** Pictures the user framed by hand for this screen, by id. */
    val frames: Map<Long, Frame> = emptyMap(),
) {
    fun frame(id: Long) = frames[id] ?: Framing.of(fit)

    /** With [id] framed as [f]; null puts it back on [fit]. */
    fun framed(
        id: Long,
        f: Frame?,
    ) = copy(frames = if (f == null) frames - id else frames + (id to f))
}

/** Where one screen is in its album. */
@Serializable
data class Progress(
    val current: Long? = null,
    /** The album [current] was picked from; when the active album differs, it is time to change. */
    val album: Long? = null,
    val changedAt: Long = 0,
    /** Shuffle: the pictures not shown yet this round, so none repeats before all have been seen. */
    val bag: List<Long> = emptyList(),
    /** Earlier pictures, newest last, for Previous. */
    val history: List<Long> = emptyList(),
)

@Serializable
data class State(
    val albums: List<Album> = emptyList(),
    val home: Rotation = Rotation(),
    val lock: Rotation = Rotation(),
    val homeNow: Progress = Progress(),
    val lockNow: Progress = Progress(),
    /** Never show the same picture on both screens at once, when the album has another. */
    val distinct: Boolean = true,
    /** While battery saver is on, skip changes on lock and on a timer; time-of-day albums still switch. */
    val saverPause: Boolean = false,
    val nextId: Long = 1,
) {
    fun rotation(s: Screen) = if (s == Screen.HOME) home else lock

    fun progress(s: Screen) = if (s == Screen.HOME) homeNow else lockNow

    fun with(
        s: Screen,
        r: Rotation,
    ) = if (s == Screen.HOME) copy(home = r) else copy(lock = r)

    fun with(
        s: Screen,
        p: Progress,
    ) = if (s == Screen.HOME) copy(homeNow = p) else copy(lockNow = p)

    fun album(id: Long?) = albums.firstOrNull { it.id == id }

    /** The lock screen copies the home screen's picture. */
    val follows get() = lock.enabled && lock.sameAsHome && home.enabled

    /** The screens that pick their own pictures. */
    val driven: List<Screen>
        get() =
            buildList {
                if (home.enabled) add(Screen.HOME)
                if (lock.enabled && !follows) add(Screen.LOCK)
            }
}

fun Screen.other() = if (this == Screen.HOME) Screen.LOCK else Screen.HOME

/** When the next alarm is due, and whether it should wake a sleeping phone to do it. */
data class Alarm(
    val at: Long,
    val wake: Boolean,
)

object Plan {
    const val MINUTE = 60_000L
    private const val HISTORY = 30

    // An alarm may be delivered a little late but never early; this keeps a
    // tick that lands seconds before the mark from waiting a whole interval.
    private const val SLACK = 30_000L

    fun albumAt(
        r: Rotation,
        t: Long,
        zone: ZoneId,
    ): Long? {
        val at = Instant.ofEpochMilli(t).atZone(zone)
        val m = at.hour * 60 + at.minute
        return r.ranges.firstOrNull { it.holds(at.dayOfWeek.value, m) }?.albumId ?: r.albumId
    }

    /**
     * The first moment after [t] at which the time ranges switch to another album,
     * within the coming week; null if they never do. A range limited to some days
     * still starts and ends at the same minutes, just not every day, so only those
     * minutes on the next eight days are tried.
     */
    fun nextEdge(
        r: Rotation,
        t: Long,
        zone: ZoneId,
    ): Long? {
        if (r.ranges.isEmpty()) return null
        val now = Instant.ofEpochMilli(t).atZone(zone)
        val minutes = r.ranges.flatMap { listOf(it.start, it.end) }.distinct()
        return (0L..7L)
            .flatMap { d ->
                minutes.map { m ->
                    now
                        .toLocalDate()
                        .plusDays(d)
                        .atTime(m / 60, m % 60)
                        .atZone(zone)
                }
            }.filter { it.isAfter(now) }
            .map { it.toInstant().toEpochMilli() }
            .filter { albumAt(r, it, zone) != albumAt(r, it - MINUTE, zone) }
            .minOrNull()
    }

    /** [paused]: battery saver is on and the user asked to wait it out, so intervals don't count. */
    fun due(
        st: State,
        s: Screen,
        t: Long,
        zone: ZoneId,
        paused: Boolean = false,
    ): Boolean {
        val r = st.rotation(s)
        val p = st.progress(s)
        if (!paused && r.everyMinutes > 0 && t >= p.changedAt + r.everyMinutes * MINUTE - SLACK) return true
        return albumAt(r, t, zone) != p.album
    }

    /**
     * The next alarm across both screens, or null when nothing is timed. Interval
     * changes don't wake the phone: nobody sees a wallpaper change in a dark
     * pocket, so it waits for the next wake. A time range's edge does, so the new
     * album is already there when the screen comes on. While [paused], an
     * interval tries again one interval later rather than every minute.
     */
    fun nextAlarm(
        st: State,
        t: Long,
        zone: ZoneId,
        paused: Boolean = false,
    ): Alarm? {
        val alarms =
            st.driven.flatMap { s ->
                val r = st.rotation(s)
                val p = st.progress(s)
                val hasImages = st.album(albumAt(r, t, zone))?.images?.isNotEmpty() == true
                val every = r.everyMinutes * MINUTE
                listOfNotNull(
                    if (r.everyMinutes > 0 && hasImages) Alarm(maxOf(p.changedAt + every, t + if (paused) every else MINUTE), false) else null,
                    nextEdge(r, t, zone)?.let { Alarm(it, true) },
                )
            }
        val first = alarms.minByOrNull { it.at } ?: return null
        // Two alarms close together: one wake-up covers both.
        return first.copy(wake = alarms.any { it.wake && it.at <= first.at + MINUTE })
    }

    /** Move [screens] on to their next picture. A lock screen that follows home takes home's. */
    fun advance(
        st: State,
        screens: Collection<Screen>,
        t: Long,
        zone: ZoneId,
        random: Random = Random,
    ): State {
        var out = st
        // Home first, so the lock screen can steer clear of home's new picture.
        for (s in listOf(Screen.HOME, Screen.LOCK)) {
            if (s !in screens || s !in out.driven) continue
            val r = out.rotation(s)
            val album = albumAt(r, t, zone)
            val a = out.album(album)
            val avoid = if (out.distinct && out.rotation(s.other()).enabled) out.progress(s.other()).current else null
            val p = pick(a?.images.orEmpty(), r.order, out.progress(s), album, avoid, random, a?.starred.orEmpty()) ?: continue
            out = out.with(s, p.copy(changedAt = t))
        }
        return synced(out)
    }

    /** [starred] pictures come up twice in each Shuffle round; In order plays the album as it is. */
    fun pick(
        images: List<Long>,
        order: Order,
        p: Progress,
        album: Long?,
        avoid: Long?,
        random: Random = Random,
        starred: Set<Long> = emptySet(),
    ): Progress? {
        if (images.isEmpty()) return null
        val sameAlbum = p.album == album
        var bag = emptyList<Long>()
        val next =
            when (order) {
                Order.IN_ORDER -> {
                    val from = if (sameAlbum) images.indexOf(p.current) else -1
                    val seq = (1..images.size).map { images[(from + it).mod(images.size)] }
                    seq.firstOrNull { it != avoid } ?: seq.first()
                }

                Order.SHUFFLE -> {
                    val present = images.toSet()
                    bag = if (sameAlbum) p.bag.filter { it in present } else emptyList()
                    if (bag.isEmpty()) bag = round(images, starred, p.current, random)
                    val chosen = bag.firstOrNull { it != avoid } ?: bag.first()
                    bag = bag - chosen
                    chosen
                }
            }
        return Progress(next, album, p.changedAt, bag, pushed(p))
    }

    /**
     * A new Shuffle round: every picture once and each favourite twice, never the
     * same one twice in a row, and not opening with [last], which closed the
     * round before. ([last] is dropped from the round entirely, as long as there
     * is something else to show.)
     */
    fun round(
        images: List<Long>,
        starred: Set<Long>,
        last: Long?,
        random: Random = Random,
    ): List<Long> {
        val all = (images + images.filter { it in starred }).shuffled(random).toMutableList()
        if (images.size > 1 && last != null) all.remove(last)
        // Pull a repeat apart by swapping in the next different picture; at worst
        // (one favourite left over at the end) a repeat stays, as with one picture.
        for (i in all.indices) {
            val before = if (i == 0) last else all[i - 1]
            if (all[i] != before) continue
            val j = (i + 1 until all.size).firstOrNull { all[it] != before } ?: break
            all[i] = all[j].also { all[j] = all[i] }
        }
        return all
    }

    /** Go back to the picture before; it is put first in line, so Next returns to where you were. */
    fun back(
        p: Progress,
        exists: (Long) -> Boolean,
    ): Progress? {
        val history = p.history.filter(exists)
        val prev = history.lastOrNull() ?: return null
        return p.copy(current = prev, history = history.dropLast(1), bag = listOfNotNull(p.current) + p.bag)
    }

    /** Show one picture the user chose. */
    fun show(
        p: Progress,
        id: Long,
        album: Long?,
        t: Long,
    ) = Progress(id, album, t, p.bag - id, pushed(p))

    fun synced(st: State) = if (st.follows) st.copy(lockNow = st.homeNow) else st

    private fun pushed(p: Progress) = (p.history + listOfNotNull(p.current)).takeLast(HISTORY)
}
