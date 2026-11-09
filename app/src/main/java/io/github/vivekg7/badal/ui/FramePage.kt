package io.github.vivekg7.badal.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Finish
import io.github.vivekg7.badal.Fit
import io.github.vivekg7.badal.Frame
import io.github.vivekg7.badal.Framing
import io.github.vivekg7.badal.Images
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.Shade
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A picture placed by [frame] over a blurred copy of itself, then finished as [finish] says: the wallpaper, small. */
@Composable
fun Framed(
    bitmap: ImageBitmap?,
    frame: Frame,
    finish: Finish,
    modifier: Modifier = Modifier,
) {
    val (dim, shade, blur, gray) = finish
    val app = LocalContext.current.app
    val width = remember { app.images.screen().first }
    val colours = if (gray > 0) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1 - gray / 100f) }) else null
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // As much blur, for its size, as the wallpaper gets (Images.render).
                    val r = Images.blurRadius(blur, width) * size.width
                    if (r > 0) renderEffect = BlurEffect(r, r, TileMode.Clamp)
                },
            ) {
                Image(bitmap, null, Modifier.fillMaxSize().blur(16.dp), contentScale = ContentScale.Crop, colorFilter = colours)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.Black.copy(alpha = 60 / 255f))
                    val p = Framing.place(bitmap.width, bitmap.height, size.width, size.height, frame)
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(p.left.roundToInt(), p.top.roundToInt()),
                        dstSize = IntSize((bitmap.width * p.scale).roundToInt(), (bitmap.height * p.scale).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                        colorFilter = colours,
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                if (dim > 0) drawRect(Color.Black.copy(alpha = dim / 100f))
                if (shade > 0) {
                    val stops = Shade.stops.map { (at, k) -> at to Color.Black.copy(alpha = shade * k / 100f) }.toTypedArray()
                    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
                }
            }
        }
    }
}

/**
 * Frame one picture for each screen: drag to move it, pinch to zoom, double-tap
 * to switch between filling the screen and showing it whole. Nothing is saved
 * until Save; then the screens showing this picture are drawn again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FramePage(
    id: Long,
    start: Screen,
    state: State,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.app
    val (sw, sh) = remember { app.images.screen() }
    val thumb = rememberThumb(id)
    // The thumbnail is too soft to judge a crop by at this size; it stands in while the picture loads.
    val large by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { runCatching { app.images.sized(id, LARGE).asImageBitmap() }.getOrNull() }
    }
    val shown = large ?: thumb
    var screen by rememberSaveable { mutableStateOf(start) }
    val saved = remember(id) { Screen.entries.associateWith { state.rotation(it).frames[id] } }
    var frames by remember(id) { mutableStateOf(saved) }
    var guides by rememberSaveable { mutableStateOf(true) }
    val r = state.rotation(screen)
    val frame = frames[screen] ?: Framing.of(r.fit)

    fun set(f: Frame?) {
        frames = frames + (screen to f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frame picture") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Close, "Discard") } },
                actions = {
                    TextButton({
                        val changed = frames.filter { (s, f) -> f != saved[s] }
                        if (changed.isNotEmpty()) app.scope.launch { app.engine.frame(id, changed) }
                        onClose()
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Home screen" to Screen.HOME, "Lock screen" to Screen.LOCK).forEachIndexed { i, (label, s) ->
                    SegmentedButton(
                        selected = s == screen,
                        onClick = { screen = s },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(sw / sh.toFloat(), matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(28.dp))
                        // Keyed on what the gesture reads, so it never works on a stale picture or screen.
                        .pointerInput(shown, screen, r.fit) {
                            val b = shown ?: return@pointerInput
                            detectTransformGestures { c, pan, zoom, _ ->
                                val now = frames[screen] ?: Framing.of(r.fit)
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                set(Framing.moved(b.width, b.height, w, h, now, pan.x, pan.y, zoom, c.x, c.y))
                            }
                        }.pointerInput(screen, r.fit) {
                            detectTapGestures(onDoubleTap = {
                                val now = frames[screen] ?: Framing.of(r.fit)
                                set(if (now.zoom < 0.999f) Frame() else Framing.of(Fit.WHOLE))
                            })
                        },
                ) {
                    Framed(shown, frame, r.finish, Modifier.fillMaxSize())
                    if (guides) Guides(screen, Modifier.fillMaxSize())
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton({ set(Frame()) }) { Text("Fill") }
                OutlinedButton({ set(Framing.of(Fit.WHOLE)) }) { Text("Whole") }
                // Back to the screen's Fit; off when it already follows it.
                TextButton({ set(null) }, enabled = frames[screen] != null) { Text("Reset") }
                // Roughly where One UI puts them; Badal can't move them.
                FilterChip(guides, { guides = !guides }, { Text(if (screen == Screen.HOME) "Dock" else "Clock") })
            }
        }
    }
}

/**
 * What covers the wallpaper, drawn roughly where One UI puts it, so a face isn't
 * framed under the clock. Only a guide: the system draws the real ones, and
 * neither their place nor their style is open to other apps.
 */
@Composable
private fun Guides(
    screen: Screen,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        // Sizes as fractions of the frame's width, so the guide scales with it.
        val unit = maxWidth / 100
        val ink = TextStyle(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 6f))

        fun size(percent: Float) = with(density) { (unit * percent).toSp() }
        val now = LocalDateTime.now()
        val clock = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"))
        Text(clock, Modifier.align(Alignment.TopStart).padding(unit * 6, unit * 2.5f), style = ink.copy(fontSize = size(3.6f)))
        if (screen == Screen.LOCK) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMMM")), style = ink.copy(fontSize = size(4.4f)))
                Text(clock, style = ink.copy(fontSize = size(22f), fontWeight = FontWeight.Light))
            }
            for (corner in listOf(Alignment.BottomStart, Alignment.BottomEnd)) {
                Box(
                    Modifier
                        .align(corner)
                        .padding(unit * 8)
                        .size(unit * 11)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                )
            }
        } else {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = unit * 8, end = unit * 8, bottom = maxHeight * 0.06f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(4) {
                    Box(Modifier.size(unit * 14).clip(RoundedCornerShape(30)).background(Color.White.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

private const val LARGE = 1440
