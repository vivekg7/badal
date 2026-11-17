package io.github.vivekg7.badal.ui

import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.vivekg7.badal.Finish
import io.github.vivekg7.badal.Frame
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Pictures full screen (plan 013), from [start]: each one whole, on black.
 * Swipe between them; pinch or double-tap to zoom in. A tap hides the system
 * bars and the top bar, and another brings them back. A dialog, so it opens
 * over any page or dialog.
 *
 * [key] names picture `i`, as a number or string: the pager saves it in a
 * Bundle. [load] gives picture `i` at full size; it runs off the main thread, and
 * [thumb] stands in while it does. [actions] go in the top bar, for the
 * picture in view.
 */
@Composable
fun Viewer(
    count: Int,
    start: Int,
    onClose: () -> Unit,
    key: (Int) -> Any,
    load: suspend (Int) -> ImageBitmap?,
    thumb: @Composable (Int) -> ImageBitmap? = { null },
    actions: @Composable RowScope.(Int) -> Unit = {},
) = Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    val pager = rememberPagerState(start.coerceIn(0, max(0, count - 1))) { count }
    var chrome by rememberSaveable { mutableStateOf(true) }
    // Only the picture in view zooms; swiping to another starts it afresh.
    val zoom = remember(pager.settledPage) { Zoom() }
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(chrome) {
        window?.insetsController?.run {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chrome) show(WindowInsets.Type.systemBars()) else hide(WindowInsets.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Zoomed in, a drag moves the picture instead of turning the page.
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = zoom.scale == 1f, key = key) { page ->
            val small = thumb(page)
            val large by produceState<ImageBitmap?>(null, key(page)) {
                value = withContext(Dispatchers.IO) { runCatching { load(page) }.getOrNull() }
            }
            val shown = large ?: small
            val z = zoom.takeIf { page == pager.settledPage }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(z, shown) { if (z != null && shown != null) z.pinch(this, shown) }
                    .pointerInput(z, shown) {
                        detectTapGestures(onTap = { chrome = !chrome }, onDoubleTap = { at ->
                            if (z == null || shown == null) return@detectTapGestures
                            if (z.scale > 1f) z.reset() else z.change(2.5f, at, Offset.Zero, size.toSize(), fitted(shown, size))
                        })
                    },
            ) {
                if (shown == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                shown?.let {
                    Image(
                        it,
                        if (count > 1) "Picture ${page + 1} of $count" else "Picture",
                        Modifier.fillMaxSize().graphicsLayer {
                            if (z == null) return@graphicsLayer
                            scaleX = z.scale
                            scaleY = z.scale
                            translationX = z.offset.x
                            translationY = z.offset.y
                        },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        if (chrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(4.dp, 4.dp, 8.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClose) { Icon(Icons.Close, "Close", tint = Color.White) }
                if (count > 1) Text("${pager.currentPage + 1} of $count", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (count > 0) actions(pager.currentPage)
            }
        }
    }
}

/** Album pictures [ids] full screen, from [start], as they are kept. */
@Composable
fun PictureViewer(
    ids: List<Long>,
    start: Long,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.app
    val (sw, sh) = remember { app.images.screen() }
    Viewer(
        ids.size,
        ids.indexOf(start),
        onClose,
        key = { ids[it] },
        load = { app.images.whole(ids[it], sw, sh).asImageBitmap() },
        thumb = { rememberThumb(ids[it]) },
    )
}

/** Picture [id] full screen exactly as the wallpaper shows it: placed by [frame], finished as [finish] says. */
@Composable
fun WallpaperViewer(
    id: Long,
    frame: Frame,
    finish: Finish,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.app
    val (sw, sh) = remember { app.images.screen() }
    Viewer(1, 0, onClose, key = { id }, load = { gpu(app.images.render(id, sw, sh, frame, finish)) })
}

/** How big [b] shows, fitted whole into [box], before any zoom. */
private fun fitted(
    b: ImageBitmap,
    box: IntSize,
): Size {
    val k = min(box.width / b.width.toFloat(), box.height / b.height.toFloat())
    return Size(b.width * k, b.height * k)
}

/** The zoom of the picture in view: a scale about the screen's centre, then a shift. */
@Stable
private class Zoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * Zoom by [k] about [at] and move by [pan], in a [box] where the picture is
     * [fit] big unzoomed. The point under [at] stays under it, and the picture's
     * edges never come in past the screen's.
     */
    fun change(
        k: Float,
        at: Offset,
        pan: Offset,
        box: Size,
        fit: Size,
    ) {
        val s = (scale * k).coerceIn(1f, MAX)
        val q = at - box.center
        val o = q - (q - offset) * (s / scale) + pan
        val mx = max(0f, (fit.width * s - box.width) / 2)
        val my = max(0f, (fit.height * s - box.height) / 2)
        scale = s
        offset = Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    /**
     * Two fingers pinch and move it. One finger moves it only when zoomed in;
     * unzoomed, its drag is left to the pager.
     */
    suspend fun pinch(
        scope: PointerInputScope,
        picture: ImageBitmap,
    ) = scope.awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val e = awaitPointerEvent()
            // Unspecified once the last finger lifts: nothing to move about then.
            val at = e.calculateCentroid()
            if (at.isSpecified && (e.changes.size > 1 || scale > 1f)) {
                change(e.calculateZoom(), at, e.calculatePan(), size.toSize(), fitted(picture, size))
                e.changes.forEach { it.consume() }
            }
        } while (e.changes.any { it.pressed })
        // Pinched back to about its size: snap to it, so the pager swipes again.
        if (scale < 1.05f) reset()
    }

    companion object {
        const val MAX = 6f
    }
}
