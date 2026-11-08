package io.github.vivekg7.badal.ui

import android.text.format.DateUtils
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

/** One screen's settings: what it shows, when it changes, how it looks. */
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
    val name = if (screen == Screen.HOME) "home screen" else "lock screen"

    fun edit(change: (Rotation) -> Rotation) = app.scope.launch { app.engine.edit(screen, change) }
    var picking by remember { mutableStateOf(false) }
    var choosingInterval by remember { mutableStateOf(false) }
    var addingRange by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Preview(screen, state, follows, onFrame)

        ListItem(
            headlineContent = { Text("Change the $name") },
            supportingContent = { Text(if (r.enabled) "Badal picks the pictures" else "Off: Badal leaves this wallpaper alone") },
            trailingContent = { Switch(r.enabled, { on -> edit { it.copy(enabled = on) } }) },
        )
        if (screen == Screen.LOCK) {
            ListItem(
                headlineContent = { Text("Same picture as the home screen") },
                supportingContent = { Text(if (state.home.enabled) "Changes together with it" else "Turn on the home screen first") },
                trailingContent = { Switch(r.sameAsHome, { on -> edit { it.copy(sameAsHome = on) } }, enabled = state.home.enabled) },
            )
        }

        if (!follows) {
            Section("Pictures") {
                val album = state.album(r.albumId)
                ListItem(
                    headlineContent = { Text("Album") },
                    supportingContent = {
                        Text(
                            album?.let { "${it.name} · ${plural(it.images.size, "picture")}" } ?: "None yet: choose one",
                        )
                    },
                    leadingContent = {
                        Thumb(
                            album?.images?.firstOrNull(),
                            Modifier.width(40.dp).height(40.dp).clip(MaterialTheme.shapes.small),
                        )
                    },
                    modifier = Modifier.clickable { if (state.albums.isEmpty()) toAlbums() else picking = true },
                )
                Choice(
                    "Order",
                    listOf("Shuffle" to Order.SHUFFLE, "In order" to Order.IN_ORDER),
                    r.order,
                ) { o -> edit { it.copy(order = o) } }
                if (r.order == Order.SHUFFLE) {
                    Text(
                        "Every picture shows once before any repeats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            Section("When to change") {
                ListItem(
                    headlineContent = { Text("Each time the screen locks") },
                    supportingContent = { Text("The next time you wake the phone, there's a new picture") },
                    trailingContent = { Switch(r.onLock, { on -> edit { it.copy(onLock = on) } }) },
                )
                ListItem(
                    headlineContent = { Text("Every") },
                    supportingContent = { Text(minutesLabel(r.everyMinutes)) },
                    modifier = Modifier.clickable { choosingInterval = true },
                )
                ListItem(
                    headlineContent = { Text("By time of day") },
                    supportingContent = {
                        Text(
                            if (r.ranges.isEmpty()) {
                                "A different album for set hours, such as dark pictures at night"
                            } else {
                                "Outside these hours: ${state.album(r.albumId)?.name ?: "no album"}"
                            },
                        )
                    },
                )
                r.ranges.forEachIndexed { i, range ->
                    ListItem(
                        headlineContent = { Text("${timeLabel(context, range.start)} – ${timeLabel(context, range.end)}") },
                        supportingContent = { Text("${daysLabel(range.days)} · ${state.album(range.albumId)?.name ?: "Album deleted"}") },
                        leadingContent = {
                            Thumb(
                                state.album(range.albumId)?.images?.firstOrNull(),
                                Modifier.width(40.dp).height(40.dp).clip(MaterialTheme.shapes.small),
                            )
                        },
                        trailingContent = {
                            IconButton({
                                edit {
                                    it.copy(
                                        ranges =
                                            it.ranges.filterIndexed {
                                                j,
                                                _,
                                                ->
                                                j != i
                                            },
                                    )
                                }
                            }) { Icon(Icons.Close, "Remove") }
                        },
                    )
                }
                OutlinedButton({ if (state.albums.isEmpty()) toAlbums() else addingRange = true }, Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Add, null)
                    Text("Add hours", Modifier.padding(start = 8.dp))
                }
            }
        }

        Section("Look") {
            Choice(
                "Fit",
                listOf("Fill the screen" to Fit.FILL, "Whole picture" to Fit.WHOLE),
                r.fit,
            ) { f -> edit { it.copy(fit = f) } }
            Text(
                if (r.frames.isEmpty()) {
                    "Or frame a picture yourself: tap it above, or in its album."
                } else {
                    "For all but the ${plural(r.frames.size, "picture")} you framed yourself."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // The slider moves freely and only sets the wallpaper when let go.
            var dim by remember(r.dim) { mutableFloatStateOf(r.dim.toFloat()) }
            ListItem(
                headlineContent = { Text("Darken") },
                supportingContent = {
                    Slider(
                        dim,
                        { dim = it },
                        valueRange = 0f..70f,
                        steps = 13,
                        onValueChangeFinished = { edit { it.copy(dim = dim.toInt()) } },
                    )
                },
                trailingContent = { Text("${dim.toInt()}%") },
            )
            if (screen == Screen.LOCK) {
                var shade by remember(r.shade) { mutableFloatStateOf(r.shade.toFloat()) }
                ListItem(
                    headlineContent = { Text("Shade behind the clock") },
                    supportingContent = {
                        Column {
                            Text("Darkens only the top, fading out below the clock")
                            Slider(
                                shade,
                                { shade = it },
                                valueRange = 0f..70f,
                                steps = 13,
                                onValueChangeFinished = { edit { it.copy(shade = shade.toInt()) } },
                            )
                        }
                    },
                    trailingContent = { Text("${shade.toInt()}%") },
                )
            }
        }
    }

    if (picking) {
        AlbumPicker(state.albums, onPick = { a ->
            picking = false
            edit { it.copy(albumId = a.id) }
        }, onDismiss = { picking = false })
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

/** The current picture as the screen shows it, with Previous, Next and Frame. */
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
    // "Changed 5 minutes ago" goes stale while the page stays open; re-read the clock now and then.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(sw / sh.toFloat())
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = id != null) { id?.let(onFrame) },
        ) {
            Framed(rememberThumb(id), id?.let { r.frame(it) } ?: Framing.of(r.fit), r.dim, r.shade, Modifier.fillMaxSize())
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White)
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !r.enabled -> {
                        "Not changed by Badal"
                    }

                    id == null -> {
                        "No picture yet"
                    }

                    now - p.changedAt < Plan.MINUTE -> {
                        "Changed just now"
                    }

                    else -> {
                        "Changed " + DateUtils.getRelativeTimeSpanString(p.changedAt, now, DateUtils.MINUTE_IN_MILLIS)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (r.enabled) {
                Text(
                    nextLabel(context, screen, state, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun nextLabel(
    context: android.content.Context,
    screen: Screen,
    state: State,
    now: Long,
): String {
    if (screen == Screen.LOCK && state.follows) return "Follows the home screen"
    val r = state.rotation(screen)
    val p = state.progress(screen)
    val zone = ZoneId.systemDefault()
    val paused = context.app.engine.paused(state)
    val parts =
        buildList {
            if (r.onLock && !paused) add("on the next lock")
            if (r.everyMinutes > 0 && !paused) {
                val at = maxOf(p.changedAt + r.everyMinutes * Plan.MINUTE, now)
                add("around ${momentLabel(context, at, now)}")
            }
            Plan.nextEdge(r, now, zone)?.let { add("at ${momentLabel(context, it, now)}") }
        }
    val next = if (parts.isEmpty()) "Changes only when you tap Next" else "Next change " + parts.joinToString(", or ")
    return if (paused && (r.onLock || r.everyMinutes > 0)) "Paused while battery saver is on. $next" else next
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (text, value) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(text) }
            }
        }
    }
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
        title = { Text("Change every") },
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
        title = { Text("Album for set hours") },
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
