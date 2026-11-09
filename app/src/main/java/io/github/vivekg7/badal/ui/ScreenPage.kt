package io.github.vivekg7.badal.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Fit
import io.github.vivekg7.badal.Framing
import io.github.vivekg7.badal.LiveWallpaper
import io.github.vivekg7.badal.Order
import io.github.vivekg7.badal.Plan
import io.github.vivekg7.badal.Rotation
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.TimeRange
import io.github.vivekg7.badal.app
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

private val intervals = listOf(0, 15, 30, 60, 120, 180, 360, 720, 1440)

/**
 * One screen's settings: what it shows, when it changes, how it looks. Each is
 * one line; what they mean is in Help (plan 006). Whether Badal changes this
 * screen at all is the switch in the top bar (Shell).
 */
@Composable
fun ScreenPage(
    screen: Screen,
    state: State,
    toAlbums: () -> Unit,
    onFrame: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val r = state.rotation(screen)
    val follows = screen == Screen.LOCK && state.follows

    fun edit(change: (Rotation) -> Rotation) = app.scope.launch { app.engine.edit(screen, change) }
    var picking by remember { mutableStateOf(false) }
    var choosingInterval by remember { mutableStateOf(false) }
    var addingRange by remember { mutableStateOf(false) }
    // Whether Badal is the live wallpaper is the system's to say: ask again on every return from its picker.
    val resumed = resumes()
    var live by remember(resumed) { mutableStateOf(screen in app.engine.live()) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Preview(screen, state, follows, onFrame)

        if (screen == Screen.LOCK) {
            SwitchSetting(Icons.Link, "Same as home", r.sameAsHome, enabled = state.home.enabled) { on -> edit { it.copy(sameAsHome = on) } }
        }

        if (!follows) {
            Section("Pictures") {
                val album = state.album(r.albumId)
                Setting(Icons.Albums, "Album", onClick = { if (state.albums.isEmpty()) toAlbums() else picking = true }) {
                    Value(if (r.picture != null && state.pool(r.picture) != null) "One picture" else album?.name ?: "Choose")
                }
                Setting(Icons.Shuffle, "Order") {
                    Segments(listOf("Shuffle" to Order.SHUFFLE, "In order" to Order.IN_ORDER), r.order) { o -> edit { it.copy(order = o) } }
                }
            }

            Section("Change") {
                SwitchSetting(Icons.Lock, "On each lock", r.onLock) { on -> edit { it.copy(onLock = on) } }
                Setting(Icons.Timer, "Every", onClick = { choosingInterval = true }) { Value(minutesLabel(r.everyMinutes)) }
                Setting(Icons.Clock, "By the hour") {
                    IconButton({ if (state.albums.isEmpty()) toAlbums() else addingRange = true }) { Icon(Icons.Add, "Add hours") }
                }
                r.ranges.forEachIndexed { i, range ->
                    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${timeLabel(context, range.start)} – ${timeLabel(context, range.end)}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${daysLabel(range.days)} · ${state.album(range.albumId)?.name ?: "No album"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton({ edit { it.copy(ranges = it.ranges.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Close, "Remove") }
                    }
                }
            }
        }

        Section("Look") {
            Setting(Icons.Fit, "Fit") {
                Segments(listOf("Fill" to Fit.FILL, "Whole" to Fit.WHOLE), r.fit) { f -> edit { it.copy(fit = f) } }
            }
            SliderSetting(Icons.Darken, "Darken", r.dim) { v -> edit { it.copy(dim = v) } }
            SliderSetting(Icons.Blur, "Blur", r.blur, max = 100) { v -> edit { it.copy(blur = v) } }
            SliderSetting(Icons.Gray, "Grayscale", r.gray, max = 100) { v -> edit { it.copy(gray = v) } }
            if (screen == Screen.LOCK) SliderSetting(Icons.Shade, "Shade", r.shade) { v -> edit { it.copy(shade = v) } }
            if (r.enabled || live) {
                SwitchSetting(Icons.Fade, "Crossfade", live) { on ->
                    if (on) {
                        val where = if (screen == Screen.HOME) "Home screen" else "Lock screen"
                        Toast.makeText(context, "Choose “$where”", Toast.LENGTH_LONG).show()
                        chooseLive(context)
                    } else {
                        live = false
                        app.scope.launch { app.engine.still(screen) }
                    }
                }
            }
        }
    }

    if (picking) {
        AlbumPicker(state.albums, onPick = { a ->
            picking = false
            edit { it.copy(albumId = a.id, picture = null) }
        }, onDismiss = { picking = false }, onPicture = { a, id ->
            picking = false
            edit { it.copy(albumId = a.id, picture = id) }
        })
    }
    if (choosingInterval) {
        IntervalDialog(r.everyMinutes, onDone = { m ->
            choosingInterval = false
            edit { it.copy(everyMinutes = m) }
        }, onDismiss = { choosingInterval = false })
    }
    if (addingRange) {
        RangeDialog(state, onDone = { range ->
            addingRange = false
            edit { it.copy(ranges = it.ranges + range) }
        }, onDismiss = { addingRange = false })
    }
}

/** The system's preview of Badal's live wallpaper, where the user sets it (plan 004). */
private fun chooseLive(context: Context) {
    val badal = ComponentName(context, LiveWallpaper::class.java)
    try {
        context.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, badal))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    }
}

/** The current picture as the screen shows it (tap to frame it), when it changes next, and Previous / Next. */
@Composable
private fun Preview(
    screen: Screen,
    state: State,
    follows: Boolean,
    onFrame: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val r = state.rotation(screen)
    val p = state.progress(screen)
    val id = p.current
    val busy by app.engine.busy.collectAsState()
    val (sw, sh) = remember { app.images.screen() }
    // "5 minutes ago" goes stale while the page stays open; re-read the clock now and then.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(sw / sh.toFloat())
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = id != null) { id?.let(onFrame) },
        ) {
            Framed(rememberThumb(id), id?.let { r.frame(it) } ?: Framing.of(r.fit), r.finish, Modifier.fillMaxSize())
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White)
        }
        Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    !r.enabled -> "Off"
                    id == null -> "No picture yet"
                    now - p.changedAt < Plan.MINUTE -> "Just now"
                    else -> DateUtils.getRelativeTimeSpanString(p.changedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (r.enabled) {
                Text(
                    nextLabel(context, screen, state, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!follows) {
                        FilledTonalIconButton({ app.scope.launch { app.engine.previous(screen) } }, enabled = p.history.isNotEmpty() && !busy) {
                            Icon(Icons.Previous, "Previous")
                        }
                        FilledIconButton({ app.scope.launch { app.engine.next(listOf(screen)) } }, enabled = !busy) {
                            Icon(Icons.Next, "Next")
                        }
                    }
                    if (id != null) FilledTonalIconButton({ onFrame(id) }) { Icon(Icons.Crop, "Frame this picture") }
                }
            }
        }
    }
}

/** Seven round toggles, Monday first. */
@Composable
private fun Days(
    days: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val on = d in days
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .toggleable(on, role = Role.Checkbox) { onChange(if (it) days + d else days - d) }
                    .semantics { contentDescription = dayName(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale),
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** When the screen changes next, in a few words: "Next: on lock or ~21:00". */
private fun nextLabel(
    context: android.content.Context,
    screen: Screen,
    state: State,
    now: Long,
): String {
    if (screen == Screen.LOCK && state.follows) return "With home"
    val r = state.rotation(screen)
    val p = state.progress(screen)
    val paused = context.app.engine.paused(state)
    if (paused && (r.onLock || r.everyMinutes > 0) && r.ranges.isEmpty()) return "Paused: battery saver"
    // A mode in force names itself first: "Work · Next: on lock".
    val mode =
        state
            .mode(context.app.engine.sense())
            ?.takeIf { it.album(screen) != null }
            ?.let { "${it.name} · " }
            .orEmpty()
    if (!Plan.rotates(state, screen)) return mode + "One picture"
    val parts =
        buildList {
            if (r.onLock && !paused) add("on lock")
            if (r.everyMinutes > 0 && !paused) add("~" + momentLabel(context, maxOf(p.changedAt + r.everyMinutes * Plan.MINUTE, now), now))
            Plan.nextEdge(r, now, ZoneId.systemDefault())?.let { add(momentLabel(context, it, now)) }
        }
    return mode + if (parts.isEmpty()) "Only on Next" else "Next: " + parts.joinToString(" or ")
}

/** A time, with the day when it isn't today: "21:00", "Mon 09:00". */
private fun momentLabel(
    context: android.content.Context,
    t: Long,
    now: Long,
): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(t).atZone(zone)
    val time = timeLabel(context, at.hour * 60 + at.minute)
    return if (at.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) time else "${dayName(at.dayOfWeek.value)} $time"
}

@Composable
private fun IntervalDialog(
    current: Int,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf(if (current in intervals) "" else current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Every") },
        text = {
            Column {
                intervals.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().selectable(m == current && custom.isEmpty()) { onDone(m) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(m == current && custom.isEmpty(), null)
                        Text(minutesLabel(m), Modifier.padding(start = 12.dp))
                    }
                }
                OutlinedTextField(
                    custom,
                    { custom = it.filter(Char::isDigit).take(5) },
                    label = { Text("Or minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton({ onDone(custom.toIntOrNull() ?: current) }, enabled = (custom.toIntOrNull() ?: 0) > 0) { Text("Set") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(
    state: State,
    onDone: (TimeRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var start by remember { mutableIntStateOf(22 * 60) }
    var end by remember { mutableIntStateOf(6 * 60) }
    var album by remember { mutableStateOf(state.albums.firstOrNull()) }
    var days by remember { mutableStateOf((1..7).toSet()) }
    var editing by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("By the hour") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("From") },
                    supportingContent = { Text(timeLabel(context, start)) },
                    modifier =
                        Modifier.clickable {
                            editing =
                                true
                        },
                )
                ListItem(
                    headlineContent = { Text("Until") },
                    supportingContent = { Text(timeLabel(context, end)) },
                    modifier =
                        Modifier.clickable {
                            editing =
                                false
                        },
                )
                ListItem(
                    headlineContent = { Text("Album") },
                    supportingContent = { Text(album?.name ?: "Choose") },
                    modifier =
                        Modifier.clickable {
                            picking =
                                true
                        },
                )
                Text(
                    if (days.isEmpty()) "Choose at least one day" else daysLabel(days),
                    Modifier.padding(start = 16.dp, top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Days(days, { days = it }, Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                { album?.let { onDone(TimeRange(start, end, it.id, if (days.size == 7) emptySet() else days)) } },
                enabled = album != null && days.isNotEmpty(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
    editing?.let { isStart ->
        val m = if (isStart) start else end
        // Keyed, so the second picker starts from its own time, not the first one's.
        val time =
            key(isStart) {
                rememberTimePickerState(
                    m / 60,
                    m % 60,
                    android.text.format.DateFormat
                        .is24HourFormat(context),
                )
            }
        AlertDialog(
            onDismissRequest = { editing = null },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton({
                    val picked = time.hour * 60 + time.minute
                    if (isStart) start = picked else end = picked
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
    if (picking) {
        AlbumPicker(state.albums, onPick = {
            album = it
            picking = false
        }, onDismiss = { picking = false })
    }
}
