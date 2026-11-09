package io.github.vivekg7.badal

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.webkit.MimeTypeMap
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pictures, copied into the app's own storage when they are added: a
 * picture deleted from the gallery, or a lost URI grant, can't break a
 * rotation. Each is `img/<id>` as the original bytes, plus `thumb/<id>.jpg`
 * for the app's grids. A picture Badal drew (plan 005) is a PNG at the
 * screen's size, with its recipe in `design/<id>.json` to edit it again.
 */
class Images(
    private val context: Context,
) {
    private val originals = File(context.filesDir, "img").apply { mkdirs() }
    private val thumbs = File(context.filesDir, "thumb").apply { mkdirs() }
    private val designs = File(context.filesDir, "design").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun file(id: Long) = File(originals, id.toString())

    fun thumb(id: Long) = File(thumbs, "$id.jpg")

    /** Copies one picture under [id]; false if it can't be read as an image. */
    fun add(
        uri: Uri,
        id: Long,
    ): Boolean =
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file(id).outputStream().use { input.copyTo(it) } }
            thumbnail(id, sized(id, THUMB))
            true
        } catch (e: Exception) {
            Log.w("Badal", "can't add $uri", e)
            delete(id)
            false
        }

    /**
     * Draws [d] at the screen's size and keeps it under [id], with its recipe.
     * Drawn once, not each time it is shown: then it is a picture like any other,
     * and framing, Darken and the crossfade need nothing new (plan 005).
     */
    fun make(
        d: Design,
        id: Long,
    ): Boolean =
        try {
            val (w, h) = screen()
            val b = Painter.render(d, w, h)
            file(id).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            thumbnail(id, Bitmap.createScaledBitmap(b, THUMB, THUMB * h / w, true))
            b.recycle()
            designFile(id).writeText(json.encodeToString(Design.serializer(), d))
            true
        } catch (e: Exception) {
            Log.w("Badal", "can't draw design $id", e)
            delete(id)
            false
        }

    /** The recipe of a picture Badal drew; null for a photo, or one it can't read. */
    fun design(id: Long): Design? =
        try {
            designFile(id).takeIf { it.exists() }?.let { json.decodeFromString(Design.serializer(), it.readText()) }
        } catch (e: Exception) {
            null
        }

    private fun designFile(id: Long) = File(designs, "$id.json")

    private fun thumbnail(
        id: Long,
        small: Bitmap,
    ) {
        thumb(id).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        small.recycle()
    }

    /** The picture with its short side at least [side] pixels, or as large as it is; for the thumbnail and the framing editor. */
    fun sized(
        id: Long,
        side: Int,
    ): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(id))) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            d.setTargetSampleSize(max(1, min(info.size.width, info.size.height) / side))
        }

    fun delete(id: Long) {
        file(id).delete()
        thumb(id).delete()
        designFile(id).delete()
    }

    /**
     * The pictures directly inside a folder the user picked, by document id; not
     * recursive. Null when the folder can't be read (its grant lapsed, or it is
     * gone), which a followed album must not take for "now empty".
     */
    fun inFolder(tree: Uri): Map<String, Uri>? {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                buildMap {
                    while (c.moveToNext()) {
                        if (c.getString(1)?.startsWith("image/") == true) put(c.getString(0), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("Badal", "can't read folder $tree", e)
            null
        }
    }

    /**
     * Saves picture [id] to the phone's gallery (Pictures/Badal) as it is kept: a
     * photo as its original file, a design as its PNG. Writing its own files to
     * MediaStore needs no permission.
     */
    fun export(id: Long): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(id).path, bounds)
        val type = bounds.outMimeType ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "jpg"
        return save("badal-$id.$ext", type) { out -> file(id).inputStream().use { it.copyTo(out) } }
    }

    /** Draws [d] at the screen's size and saves it to the gallery as a PNG, without adding it to an album. */
    fun export(d: Design): Boolean {
        val (w, h) = screen()
        val b = Painter.render(d, w, h)
        return save("badal-${System.currentTimeMillis()}.png", "image/png") { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.also { b.recycle() }
    }

    private fun save(
        name: String,
        type: String,
        write: (OutputStream) -> Unit,
    ): Boolean {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, type)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Badal")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        return try {
            resolver.openOutputStream(uri)!!.use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            Log.w("Badal", "can't save $name", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    /** The screen in pixels, portrait. The display mode is the panel's real resolution, also from a service. */
    fun screen(): Pair<Int, Int> {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        return min(mode.physicalWidth, mode.physicalHeight) to max(mode.physicalWidth, mode.physicalHeight)
    }

    /**
     * The picture as it will look on a [w]×[h] screen: placed by [frame] over a
     * blurred copy where it leaves gaps, then finished as [finish] says: its colour
     * taken out, blurred, dimmed, and shaded at the top ([Shade]).
     */
    fun render(
        id: Long,
        w: Int,
        h: Int,
        frame: Frame,
        finish: Finish,
    ): Bitmap {
        val (dim, shade, blur, gray) = finish
        val src =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file(id))) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // A 50 MP camera photo is 200 MB decoded. Shrink while decoding, but
                // never below what the screen shows of it, zoom included: the worst
                // of both orientations is used, because the size here may be before
                // the EXIF rotation.
                val iw = info.size.width
                val ih = info.size.height
                d.setTargetSampleSize(max(1, (minOf(iw / w, ih / h, iw / h, ih / w) / max(1f, frame.zoom)).toInt()))
            }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (gray > 0) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1 - gray / 100f) })
        val p = Framing.place(src.width, src.height, w.toFloat(), h.toFloat(), frame)
        if (Framing.gaps(p, src.width, src.height, w.toFloat(), h.toFloat())) {
            val cover = max(w / src.width.toFloat(), h / src.height.toFloat())
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
            tiny.recycle()
        }
        canvas.drawBitmap(
            src,
            Matrix().apply {
                setScale(p.scale, p.scale)
                postTranslate(p.left, p.top)
            },
            paint,
        )
        if (blur > 0) {
            // Shrunk, box-blurred and scaled back up, as for the gaps: the more blur, the smaller.
            val k = blurScale(blur)
            // Six passes, not three: scaled up this far, three still leave a faint lattice.
            val soft = blurred(blurred(shrunk(out, k)))
            canvas.drawBitmap(soft, Matrix().apply { setScale(w / soft.width.toFloat(), h / soft.height.toFloat()) }, Paint(Paint.FILTER_BITMAP_FLAG))
            soft.recycle()
        }
        if (dim > 0) canvas.drawColor(Color.argb(dim * 255 / 100, 0, 0, 0))
        if (shade > 0) {
            val colors = Shade.stops.map { (_, k) -> Color.argb((shade * 255 * k / 100).roundToInt(), 0, 0, 0) }
            val positions = Shade.stops.map { it.first }
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(), colors.toIntArray(), positions.toFloatArray(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = gradient })
        }
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

    /**
     * [b] made [k] times smaller by halving it, each step averaging every pixel in.
     * One large bilinear step samples only a few, and a fine pattern (dots, grain)
     * comes out as blotches that blur into squares.
     */
    private fun shrunk(
        b: Bitmap,
        k: Int,
    ): Bitmap {
        val w = max(1, b.width / k)
        val h = max(1, b.height / k)
        var cur = b
        while (cur.width / 2 >= w && cur.height / 2 >= h) {
            val half = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== b) cur.recycle()
            cur = half
        }
        if (cur.width == w && cur.height == h) return cur
        return Bitmap.createScaledBitmap(cur, w, h, true).also { if (cur !== b) cur.recycle() }
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

    companion object {
        private const val THUMB = 360

        /** How many times smaller the picture is made to blur it by [blur] percent; previews blur to match. */
        fun blurScale(blur: Int) = 2 + blur / 4

        /** The blur's radius on a full-size wallpaper, as a fraction of its width (three passes of a 5-pixel box). */
        fun blurRadius(
            blur: Int,
            width: Int,
        ) = if (blur == 0) 0f else 2.5f * blurScale(blur) / width
    }
}
