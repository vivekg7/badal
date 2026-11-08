package io.github.vivekg7.badal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.view.Display
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pictures, copied into the app's own storage when they are added: a
 * picture deleted from the gallery, or a lost URI grant, can't break a
 * rotation. Each is `img/<id>` as the original bytes, plus `thumb/<id>.jpg`
 * for the app's grids.
 */
class Images(
    private val context: Context,
) {
    private val originals = File(context.filesDir, "img").apply { mkdirs() }
    private val thumbs = File(context.filesDir, "thumb").apply { mkdirs() }

    fun file(id: Long) = File(originals, id.toString())

    fun thumb(id: Long) = File(thumbs, "$id.jpg")

    /** Copies one picture under [id]; false if it can't be read as an image. */
    fun add(
        uri: Uri,
        id: Long,
    ): Boolean =
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file(id).outputStream().use { input.copyTo(it) } }
            val small =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(id))) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    d.setTargetSampleSize(max(1, min(info.size.width, info.size.height) / THUMB))
                }
            thumb(id).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } catch (e: Exception) {
            Log.w("Badal", "can't add $uri", e)
            delete(id)
            false
        }

    fun delete(id: Long) {
        file(id).delete()
        thumb(id).delete()
    }

    /** The pictures directly inside a folder the user picked. Not recursive, and copied once, not kept in sync. */
    fun inFolder(tree: Uri): List<Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return context.contentResolver
            .query(children, columns, null, null, null)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getString(1)?.startsWith("image/") ==
                            true
                        ) {
                            add(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                        }
                    }
                }
            }.orEmpty()
    }

    /** The screen in pixels, portrait. The display mode is the panel's real resolution, also from a service. */
    fun screen(): Pair<Int, Int> {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        return min(mode.physicalWidth, mode.physicalHeight) to max(mode.physicalWidth, mode.physicalHeight)
    }

    /** The picture as it will look on a [w]×[h] screen: cropped or fitted, then dimmed. */
    fun render(
        id: Long,
        w: Int,
        h: Int,
        fit: Fit,
        dim: Int,
    ): Bitmap {
        val src =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(id))) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // A 50 MP camera photo is 200 MB decoded. Shrink while decoding, but
                // never below the screen: the worst of both orientations is used,
                // because the size here may be before the EXIF rotation.
                val iw = info.size.width
                val ih = info.size.height
                d.setTargetSampleSize(max(1, minOf(iw / w, ih / h, iw / h, ih / w)))
            }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val cover = max(w / src.width.toFloat(), h / src.height.toFloat())
        if (fit == Fit.FILL) {
            canvas.drawBitmap(src, centred(src, w, h, cover), paint)
        } else {
            val tiny =
                blurred(
                    Bitmap.createScaledBitmap(
                        src,
                        max(1, (src.width * cover / 24).roundToInt()),
                        max(1, (src.height * cover / 24).roundToInt()),
                        true,
                    ),
                )
            canvas.drawBitmap(tiny, centred(tiny, w, h, max(w / tiny.width.toFloat(), h / tiny.height.toFloat())), paint)
            canvas.drawColor(Color.argb(60, 0, 0, 0))
            canvas.drawBitmap(src, centred(src, w, h, min(w / src.width.toFloat(), h / src.height.toFloat())), paint)
            tiny.recycle()
        }
        if (dim > 0) canvas.drawColor(Color.argb(dim * 255 / 100, 0, 0, 0))
        src.recycle()
        return out
    }

    private fun centred(
        b: Bitmap,
        w: Int,
        h: Int,
        scale: Float,
    ) = Matrix().apply {
        setScale(scale, scale)
        postTranslate((w - b.width * scale) / 2, (h - b.height * scale) / 2)
    }

    /** Three box-blur passes on a bitmap a few dozen pixels across; scaling it up afterwards smooths the rest. */
    private fun blurred(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        var px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(3) { px = box(box(px, w, h, 1, w), h, w, w, 1) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { if (it != b) b.recycle() }
    }

    // One pass along lines of `len` pixels, `step` apart within a line and `next` apart between lines.
    private fun box(
        px: IntArray,
        len: Int,
        lines: Int,
        step: Int,
        next: Int,
    ): IntArray {
        val out = IntArray(px.size)
        val r = 2
        for (line in 0 until lines) {
            val base = line * next
            for (i in 0 until len) {
                var a = 0
                var rr = 0
                var g = 0
                var bb = 0
                var n = 0
                for (k in max(0, i - r)..min(len - 1, i + r)) {
                    val c = px[base + k * step]
                    a += c ushr 24
                    rr += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    bb += c and 0xFF
                    n++
                }
                out[base + i * step] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (bb / n)
            }
        }
        return out
    }

    private companion object {
        const val THUMB = 360
    }
}
