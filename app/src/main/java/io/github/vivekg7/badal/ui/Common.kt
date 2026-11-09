package io.github.vivekg7.badal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.vivekg7.badal.Album
import io.github.vivekg7.badal.App
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems

/** Counts the activity's resumes: key a value the system's settings can change on it, to read it again on every return. */
@Composable
fun resumes(): Int {
    val context = LocalContext.current
    var n by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) n++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return n
}

/**
 * Decoded thumbnails, by picture id; 48 MB holds about seventy. They are
 * hardware bitmaps: uploaded to the GPU once and kept there, instead of a copy
 * on the Java heap plus another in the GPU's texture cache.
 */
private val thumbs =
    object : LruCache<Long, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(
            key: Long,
            value: ImageBitmap,
        ) = value.width * value.height * 4
    }

/**
 * A picture's thumbnail, loaded off the main thread. When [id] changes, the old
 * thumbnail stays until the new one is ready, so the picture swaps without a
 * blank frame. (produceState keeps its value across keys: the loader must
 * always replace it, never only fill it when empty.)
 */
@Composable
fun rememberThumb(id: Long?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState(id?.let { thumbs.get(it) }, id) {
        value =
            id?.let {
                thumbs.get(it) ?: withContext(Dispatchers.IO) {
                    BitmapFactory
                        .decodeFile(
                            context.app.images
                                .thumb(it)
                                .path,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE },
                        )?.asImageBitmap()
                        ?.also { b -> thumbs.put(it, b) }
                }
            }
    }
    return bitmap
}

/** [b] as a hardware bitmap (see [thumbs]); the software one is let go. */
fun gpu(b: Bitmap): ImageBitmap = (b.copy(Bitmap.Config.HARDWARE, false)?.also { b.recycle() } ?: b).asImageBitmap()

/**
 * Let go of decoded pictures once the app is out of sight. The process often
 * stays up long after (the lock watcher, the live wallpaper), and nothing on
 * screen needs them then.
 */
fun dropCaches() {
    thumbs.evictAll()
    dropDrawn()
}

/** A picture's thumbnail, cropped to fill [modifier]; [dim] darkens it like the wallpaper. */
@Composable
fun Thumb(
    id: Long?,
    modifier: Modifier = Modifier,
    dim: Int = 0,
) {
    val bitmap = rememberThumb(id)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (dim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim / 100f)))
    }
}

fun minutesLabel(m: Int): String =
    when {
        m <= 0 -> "Off"
        m % 1440 == 0 -> plural(m / 1440, "day")
        m % 60 == 0 -> plural(m / 60, "hour")
        m > 60 -> "${plural(m / 60, "hour")} ${plural(m % 60, "minute")}"
        else -> plural(m, "minute")
    }

fun plural(
    n: Int,
    word: String,
) = if (n == 1) "1 $word" else "$n ${word}s"

fun timeLabel(
    context: Context,
    minute: Int,
): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern))
}

/** A day by its ISO number, Monday 1: "Mon". */
fun dayName(d: Int): String = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** Days a time range holds on, empty meaning all of them: "Every day", "Weekdays", "Sat, Sun". */
fun daysLabel(days: Set<Int>): String =
    when (days) {
        emptySet<Int>(), (1..7).toSet() -> "Every day"
        (1..5).toSet() -> "Weekdays"
        setOf(6, 7) -> "Weekends"
        else -> days.sorted().joinToString(", ", transform = ::dayName)
    }

/**
 * Choose an album. With [onNew], a button to make one; with [onPicture], each
 * album also opens to choose a single picture from it (plan 010).
 */
@Composable
fun AlbumPicker(
    albums: List<Album>,
    onPick: (Album) -> Unit,
    onDismiss: () -> Unit,
    onNew: (() -> Unit)? = null,
    onPicture: ((Album, Long) -> Unit)? = null,
) {
    var inside by remember { mutableStateOf<Album?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(inside?.name ?: "Choose an album") },
        text = {
            val a = inside
            when {
                a != null -> {
                    LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        gridItems(a.images, key = { it }) { id ->
                            Thumb(id, Modifier.aspectRatio(9f / 16f).clip(MaterialTheme.shapes.small).clickable { onPicture?.invoke(a, id) })
                        }
                    }
                }

                albums.isEmpty() -> {
                    Text("No albums yet. Make one in the Albums tab.")
                }

                else -> {
                    LazyColumn {
                        items(albums, key = { it.id }) { a ->
                            ListItem(
                                headlineContent = { Text(a.name) },
                                supportingContent = { Text(plural(a.images.size, "picture")) },
                                leadingContent = { Thumb(a.images.firstOrNull(), Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) },
                                trailingContent = {
                                    if (onPicture != null && a.images.isNotEmpty()) {
                                        IconButton({ inside = a }) { Icon(Icons.Image, "Choose one picture") }
                                    }
                                },
                                modifier = Modifier.clickable { onPick(a) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                inside != null -> TextButton({ inside = null }) { Text("Back") }
                onNew != null -> TextButton(onNew) { Text("New album") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Saves to the phone's gallery off the main thread with [save], which returns how many it saved, then says so. */
fun export(
    context: Context,
    save: (App) -> Int,
) {
    val app = context.app
    app.scope.launch(Dispatchers.IO) {
        val n = save(app)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, if (n == 0) "Couldn't save" else "Saved to Pictures/Badal", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        content()
    }
}

/**
 * One line of settings: an icon, a short label, and its control or value at
 * the end. What each one means is in Help, not under it (plan 006).
 */
@Composable
fun Setting(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    end: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 16.dp, end = 8.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        end()
    }
}

/** A setting that is a switch; the whole line toggles it. */
@Composable
fun SwitchSetting(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = Setting(
    icon,
    label,
    Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    enabled,
) { Switch(checked, null, enabled = enabled) }

/** A setting's value, at the end of its line, with a chevron when tapping it opens something. */
@Composable
fun RowScope.Value(
    text: String,
    opens: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    if (opens) Icon(Icons.Chevron, null, Modifier.padding(start = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A percentage slider on one line; it moves freely and reports only when let go, which is when a wallpaper is drawn again. */
@Composable
fun SliderSetting(
    icon: ImageVector,
    label: String,
    value: Int,
    max: Int = 70,
    onDone: (Int) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Setting(icon, label) {
        // In steps of 5, but without the step marks: fourteen dots on a short slider are noise.
        val snapped = (v / 5).roundToInt() * 5
        Slider(v, { v = it }, Modifier.weight(1.4f), valueRange = 0f..max.toFloat(), onValueChangeFinished = { onDone(snapped) })
        Text("$snapped%", Modifier.width(44.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Two or three choices as one small segmented row, at the end of a setting's line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segments(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, (text, value) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text(text, maxLines = 1) }
        }
    }
}
