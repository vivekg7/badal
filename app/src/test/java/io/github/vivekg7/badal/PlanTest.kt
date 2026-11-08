package io.github.vivekg7.badal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class PlanTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(
        h: Int,
        m: Int = 0,
        day: Int = 8,
    ) = LocalDateTime
        .of(2026, 11, day, h, m)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val pics = (1L..5L).toList()
    private val base =
        State(
            albums = listOf(Album(100, "Day", pics), Album(200, "Night", listOf(11L, 12L))),
            home = Rotation(enabled = true, albumId = 100),
            lock = Rotation(enabled = true, albumId = 100),
        )

    @Test
    fun shuffleShowsEveryPictureBeforeRepeating() {
        var p = Progress()
        val seen = mutableListOf<Long>()
        repeat(5) {
            p = Plan.pick(pics, Order.SHUFFLE, p, 100, null, Random(it))!!
            seen += p.current!!
        }
        assertEquals(pics.toSet(), seen.toSet())
        // A new round never starts with the picture that ended the last one.
        val last = p.current
        p = Plan.pick(pics, Order.SHUFFLE, p, 100, null, Random(9))!!
        assertNotEquals(last, p.current)
    }

    @Test
    fun inOrderWrapsAround() {
        var p = Progress()
        val seen = (1..7).map { Plan.pick(pics, Order.IN_ORDER, p, 100, null)!!.also { p = it }.current }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 1L, 2L), seen)
    }

    @Test
    fun avoidsTheOtherScreensPicture() {
        val p = Plan.pick(pics, Order.IN_ORDER, Progress(current = 1, album = 100), 100, avoid = 2)!!
        assertEquals(3L, p.current)
        // With only one picture, it is shown anyway.
        assertEquals(7L, Plan.pick(listOf(7L), Order.SHUFFLE, Progress(), 100, avoid = 7)!!.current)
    }

    @Test
    fun aNewAlbumStartsAFreshRound() {
        val p = Plan.pick(listOf(11L, 12L), Order.SHUFFLE, Progress(current = 1, album = 100, bag = listOf(2, 3)), 200, null)!!
        assertTrue(p.current in listOf(11L, 12L))
        assertTrue(p.bag.all { it in listOf(11L, 12L) })
    }

    @Test
    fun previousThenNextReturns() {
        var p = Progress()
        repeat(3) { p = Plan.pick(pics, Order.SHUFFLE, p, 100, null, Random(it))!! }
        val third = p.current
        val second = p.history.last()
        p = Plan.back(p) { true }!!
        assertEquals(second, p.current)
        p = Plan.pick(pics, Order.SHUFFLE, p, 100, null)!!
        assertEquals(third, p.current)
    }

    @Test
    fun rangesRunPastMidnight() {
        val night = TimeRange(22 * 60, 6 * 60, 200)
        assertTrue(23 * 60 in night)
        assertTrue(5 * 60 + 59 in night)
        assertFalse(6 * 60 in night)
        assertFalse(12 * 60 in night)
        val r = base.home.copy(ranges = listOf(night))
        assertEquals(200L, Plan.albumAt(r, at(23), zone))
        assertEquals(100L, Plan.albumAt(r, at(9), zone))
    }

    @Test
    fun nextEdgeIsTheNearestStartOrEnd() {
        val r = base.home.copy(ranges = listOf(TimeRange(22 * 60, 6 * 60, 200)))
        assertEquals(at(22), Plan.nextEdge(r, at(9), zone))
        assertEquals(at(6, day = 9), Plan.nextEdge(r, at(23), zone))
        assertNull(Plan.nextEdge(base.home, at(9), zone))
    }

    @Test
    fun dueAfterTheIntervalOrWhenTheAlbumChanges() {
        val st =
            base.copy(
                home = base.home.copy(everyMinutes = 60, ranges = listOf(TimeRange(22 * 60, 6 * 60, 200))),
                homeNow = Progress(current = 1, album = 100, changedAt = at(9)),
            )
        assertFalse(Plan.due(st, Screen.HOME, at(9, 30), zone))
        assertTrue(Plan.due(st, Screen.HOME, at(10), zone))
        val fresh = st.copy(homeNow = st.homeNow.copy(changedAt = at(21, 50)))
        assertTrue(Plan.due(fresh, Screen.HOME, at(22), zone))
    }

    @Test
    fun intervalsSleepAndEdgesWake() {
        val st =
            base.copy(
                home = base.home.copy(everyMinutes = 30),
                lock = base.lock.copy(ranges = listOf(TimeRange(9 * 60 + 10, 18 * 60, 200))),
                homeNow = Progress(current = 1, album = 100, changedAt = at(9)),
            )
        assertEquals(Alarm(at(9, 10), true), Plan.nextAlarm(st, at(9, 5), zone))
        assertEquals(Alarm(at(9, 30), false), Plan.nextAlarm(st, at(9, 15), zone))
        assertNull(Plan.nextAlarm(base, at(9), zone))
    }

    @Test
    fun lockFollowsHome() {
        val st = base.copy(lock = base.lock.copy(sameAsHome = true))
        assertEquals(listOf(Screen.HOME), st.driven)
        val out = Plan.advance(st, listOf(Screen.HOME, Screen.LOCK), at(9), zone)
        assertEquals(out.homeNow.current, out.lockNow.current)
    }

    @Test
    fun advanceKeepsTheScreensApart() {
        repeat(20) { seed ->
            val out = Plan.advance(base, listOf(Screen.HOME, Screen.LOCK), at(9), zone, Random(seed))
            assertNotEquals(out.homeNow.current, out.lockNow.current)
        }
    }

    @Test
    fun anEmptyAlbumChangesNothing() {
        val st = base.copy(home = base.home.copy(albumId = 999))
        assertEquals(st.homeNow, Plan.advance(st, listOf(Screen.HOME), at(9), zone).homeNow)
    }
}
