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
        assertTrue(night.holds(1, 23 * 60))
        assertTrue(night.holds(1, 5 * 60 + 59))
        assertFalse(night.holds(1, 6 * 60))
        assertFalse(night.holds(1, 12 * 60))
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

    @Test
    fun favouritesShowTwiceARoundButNeverTwiceInARow() {
        repeat(50) { seed ->
            val round = Plan.round(pics, setOf(1L, 2L), last = 1L, Random(seed))
            // 1 closed the last round, so it comes once more this round; 2 twice; the rest once.
            assertEquals(listOf(1L, 2L, 2L, 3L, 4L, 5L), round.sorted())
            assertNotEquals(1L, round.first())
            round.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun aFavouriteComesUpTwiceAsOften() {
        var p = Progress()
        val seen = mutableListOf<Long>()
        repeat(60) {
            p = Plan.pick(pics, Order.SHUFFLE, p, 100, null, Random(it), starred = setOf(3L))!!
            seen += p.current!!
        }
        val three = seen.count { it == 3L }
        assertTrue("3 showed $three times in 60", three in 18..22)
    }

    @Test
    fun aRangeCanBeLimitedToSomeDays() {
        // Friday and Saturday nights; the part after midnight belongs to the night it started.
        val weekend = TimeRange(22 * 60, 6 * 60, 200, days = setOf(5, 6))
        assertTrue(weekend.holds(5, 23 * 60))
        assertTrue(weekend.holds(7, 2 * 60)) // Sunday 02:00 is still Saturday night
        assertFalse(weekend.holds(7, 23 * 60))
        assertFalse(weekend.holds(5, 2 * 60)) // Friday 02:00 is Thursday night
        val r = base.home.copy(ranges = listOf(weekend))
        assertEquals(200L, Plan.albumAt(r, at(23, day = 7), zone))
        assertEquals(100L, Plan.albumAt(r, at(23, day = 8), zone))
    }

    @Test
    fun nextEdgeSkipsDaysWithoutTheRange() {
        // Monday only: on Sunday morning the next switch is Monday 09:00, not Sunday's.
        val r = base.home.copy(ranges = listOf(TimeRange(9 * 60, 17 * 60, 200, days = setOf(1))))
        assertEquals(at(9, day = 9), Plan.nextEdge(r, at(8, day = 8), zone))
        assertEquals(at(17, day = 9), Plan.nextEdge(r, at(10, day = 9), zone))
        // A range that covers every minute of every day never switches.
        assertNull(Plan.nextEdge(base.home.copy(ranges = listOf(TimeRange(0, 0, 200))), at(9), zone))
    }

    @Test
    fun batterySaverPausesIntervalsButNotAlbums() {
        val st =
            base.copy(
                home = base.home.copy(everyMinutes = 60, ranges = listOf(TimeRange(22 * 60, 6 * 60, 200))),
                homeNow = Progress(current = 1, album = 100, changedAt = at(9)),
            )
        assertFalse(Plan.due(st, Screen.HOME, at(10), zone, paused = true))
        assertTrue(Plan.due(st.copy(homeNow = st.homeNow.copy(changedAt = at(21))), Screen.HOME, at(22), zone, paused = true))
        // Paused, an overdue interval tries again an interval later, not every minute.
        assertEquals(Alarm(at(11, 30), false), Plan.nextAlarm(st, at(10, 30), zone, paused = true))
        assertEquals(Alarm(at(10, 31), false), Plan.nextAlarm(st, at(10, 30), zone))
    }

    @Test
    fun modesWinOverTheScreensAlbumWhileTheyHold() {
        val night = Mode(300, "Night", Cue.DARK, home = 200)
        val st = base.copy(modes = listOf(night), home = base.home.copy(ranges = listOf(TimeRange(9 * 60, 17 * 60, 100))))
        assertEquals(200L, Plan.sourceAt(st, Screen.HOME, at(10), zone, Sense(dark = true)))
        assertEquals(100L, Plan.sourceAt(st, Screen.HOME, at(10), zone, Sense()))
        // A mode with no album for a screen leaves it as it is.
        assertEquals(100L, Plan.sourceAt(st, Screen.LOCK, at(10), zone, Sense(dark = true)))
        // Its cue starting is a change of album, so the screen is due.
        val shown = st.copy(homeNow = Progress(current = 1, album = 100, changedAt = at(9)))
        assertTrue(Plan.due(shown, Screen.HOME, at(10), zone, sense = Sense(dark = true)))
        assertFalse(Plan.due(shown, Screen.HOME, at(10), zone))
    }

    @Test
    fun aModeSwitchedOnByHandWinsOverCues() {
        val work = Mode(300, "Work", Cue.HAND, home = 100, lock = 100)
        val quiet = Mode(301, "Quiet", Cue.QUIET, home = 200)
        val st = base.copy(modes = listOf(quiet, work), handMode = 300, home = base.home.copy(albumId = 999))
        assertEquals(100L, Plan.sourceAt(st, Screen.HOME, at(10), zone, Sense(quiet = true)))
        assertEquals(200L, Plan.sourceAt(st.copy(handMode = null), Screen.HOME, at(10), zone, Sense(quiet = true)))
        // A mode changed from By hand to a cue follows the cue, though it was on.
        val changed = st.copy(modes = listOf(work.copy(cue = Cue.DARK)))
        assertEquals(999L, Plan.sourceAt(changed, Screen.HOME, at(10), zone, Sense()))
        // A hand mode never comes on by itself.
        assertEquals(999L, Plan.sourceAt(st.copy(handMode = null), Screen.HOME, at(10), zone, Sense(dark = true, charging = true)))
    }

    @Test
    fun onePictureIsAScreensWholeSource() {
        val st = base.copy(home = base.home.copy(picture = 3, everyMinutes = 30))
        val out = Plan.advance(st, listOf(Screen.HOME), at(9), zone, Random(1))
        assertEquals(3L, out.homeNow.current)
        assertEquals(3L, Plan.advance(out, listOf(Screen.HOME), at(10), zone, Random(2)).homeNow.current)
        // Nothing to change to, so no interval alarm and no need to watch for locks.
        assertNull(Plan.nextAlarm(out.copy(lock = Rotation()), at(9, 5), zone))
        assertFalse(Plan.rotates(st, Screen.HOME))
        assertTrue(Plan.rotates(st.copy(modes = listOf(Mode(300, "Night", Cue.DARK, home = 200))), Screen.HOME))
        // A picture that is no longer in any album names nothing.
        assertNull(st.pool(42))
    }
}
