package io.github.vivekg7.badal.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Album
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Decoded thumbnails, by picture id; 48 MB holds about seventy. */
private val thumbs =
    object : LruCache<Long, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(
            key: Long,
            value: ImageBitmap,
        ) = value.width * value.height * 4
    }

/** A picture's thumbnail, cropped to fill [modifier]; [dim] darkens it like the wallpaper. */
@Composable
fun Thumb(
    id: Long?,
    modifier: Modifier = Modifier,
    dim: Int = 0,
    fit: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bitmap by produceState(id?.let { thumbs.get(it) }, id) {
        if (value == null && id != null) {
            value =
                withContext(Dispatchers.IO) {
                    BitmapFactory
                        .decodeFile(
                            context.app.images
                                .thumb(id)
                                .path,
                        )?.asImageBitmap()
                        ?.also { thumbs.put(id, it) }
                }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = fit) }
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

@Composable
fun AlbumPicker(
    albums: List<Album>,
    onPick: (Album) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an album") },
        text = {
            if (albums.isEmpty()) {
                Text("No albums yet. Make one in the Albums tab.")
            } else {
                LazyColumn {
                    items(albums, key = { it.id }) { a ->
                        ListItem(
                            headlineContent = { Text(a.name) },
                            supportingContent = { Text(plural(a.images.size, "picture")) },
                            leadingContent = { Thumb(a.images.firstOrNull(), Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) },
                            modifier = Modifier.clickable { onPick(a) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
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
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        content()
    }
}
