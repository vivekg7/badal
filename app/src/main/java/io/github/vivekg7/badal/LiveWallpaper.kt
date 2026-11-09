package io.github.vivekg7.badal

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home or lock screen as a live wallpaper, for the one thing a still one
 * can't do: fade from one picture to the next (docs/plans/004_crossfade.md).
 * Each screen it is set on gets its own [Live] engine, which draws whatever the
 * engine picked for that screen; the engine stops setting a still wallpaper
 * there while this one is set ([Engine.live]).
 */
class LiveWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = Live()

    /** What the screen shows: a new one is drawn again, and fades in if only the picture changed. */
    private data class Look(
        val id: Long,
        val frame: Frame,
        val finish: Finish,
    )

    private inner class Live : Engine() {
        private val scope: CoroutineScope = MainScope()
        private val size = MutableStateFlow<Pair<Int, Int>?>(null)

        /** The screen this engine draws: the lock screen only when it is set there alone. */
        private val screen = MutableStateFlow(Screen.HOME)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val ease = DecelerateInterpolator()
        private var shown: Bitmap? = null
        private var shownId: Long? = null

        /** The picture fading out under [shown], until [FADE_MS] after [fadeFrom]. */
        private var leaving: Bitmap? = null
        private var fadeFrom = 0L
        private var colors: WallpaperColors? = null
        private val frame = Choreographer.FrameCallback { draw() }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            onWallpaperFlagsChanged(wallpaperFlags)
            val looks =
                combine(app.store.state, screen) { st, s ->
                    val r = st.rotation(s)
                    // A lock screen Badal doesn't change yet still shows something: the home picture.
                    (st.progress(s).current ?: st.homeNow.current)?.let { Look(it, r.frame(it), r.finish) }
                }
            scope.launch {
                // Conflated: after several quick Nexts, draw only the last.
                combine(size.filterNotNull(), looks) { s, l -> s to l }
                    .distinctUntilChanged()
                    .conflate()
                    .collect { (s, look) -> show(look, s.first, s.second) }
            }
        }

        private suspend fun show(
            look: Look?,
            w: Int,
            h: Int,
        ) {
            val (bmp, c) =
                withContext(Dispatchers.Default) {
                    try {
                        // Colours from the full picture, then a hardware copy that is
                        // uploaded to the GPU once instead of on every frame of the fade.
                        val full = look?.let { app.images.render(it.id, w, h, it.frame, it.finish) }
                        val out = full?.copy(Bitmap.Config.HARDWARE, false) to full?.let { WallpaperColors.fromBitmap(it) }
                        full?.recycle()
                        out
                    } catch (e: Exception) {
                        Log.e("Badal", "live wallpaper can't draw ${look?.id}", e)
                        null to null
                    }
                }
            // Fade only when someone can see it, and only to a different picture:
            // a new frame or finish (Darken, Blur…) replaces the picture in place.
            val fade = isVisible && shown != null && look?.id != shownId
            leaving = if (fade) shown else null
            fadeFrom = SystemClock.uptimeMillis()
            shown = bmp
            shownId = look?.id
            draw()
            if (c != colors) {
                colors = c
                notifyColorsChanged()
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas =
                try {
                    holder.lockHardwareCanvas()
                } catch (e: Exception) {
                    null
                } ?: return
            val t = ((SystemClock.uptimeMillis() - fadeFrom) / FADE_MS.toFloat()).coerceIn(0f, 1f)
            try {
                val dst = Rect(0, 0, canvas.width, canvas.height)
                canvas.drawColor(Color.BLACK)
                leaving?.let {
                    paint.alpha = 255
                    canvas.drawBitmap(it, null, dst, paint)
                }
                shown?.let {
                    paint.alpha = if (leaving == null) 255 else (ease.getInterpolation(t) * 255).toInt()
                    canvas.drawBitmap(it, null, dst, paint)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            if (leaving == null) return
            if (t < 1f) Choreographer.getInstance().postFrameCallback(frame) else leaving = null
        }

        /** Put the new picture in place at once: a fade that nobody sees would only replay later. */
        private fun settleFade() {
            Choreographer.getInstance().removeFrameCallback(frame)
            if (leaving != null) {
                leaving = null
                draw()
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            size.value = width to height
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()

        override fun onWallpaperFlagsChanged(which: Int) {
            val home = which and WallpaperManager.FLAG_SYSTEM != 0
            screen.value = if (!home && which and WallpaperManager.FLAG_LOCK != 0) Screen.LOCK else Screen.HOME
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) settleFade()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            Choreographer.getInstance().removeFrameCallback(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onComputeColors() = colors

        override fun onDestroy() {
            scope.cancel()
            Choreographer.getInstance().removeFrameCallback(frame)
            super.onDestroy()
        }
    }

    private companion object {
        const val FADE_MS = 900
    }
}
