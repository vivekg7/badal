package io.github.vivekg7.badal

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything Badal keeps, as one zip the user saves where they like (plan 010).
 * Android's own backup is off, because it would upload every picture, so
 * without this a new phone starts empty. Restoring replaces everything.
 */
class Backup(
    private val context: Context,
    private val store: Store,
    private val engine: Engine,
) {
    private val dirs = listOf("img", "thumb", "design")

    /** The only names a backup may hold, so a zip can't write anywhere else ("../"). */
    private val names = Regex("""state\.json|img/\d+|thumb/\d+\.jpg|design/\d+\.json""")

    suspend fun save(to: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val home = context.filesDir
            try {
                context.contentResolver.openOutputStream(to)!!.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        // Photos and PNGs are compressed already; deflating them again is slow for nothing.
                        zip.setLevel(Deflater.BEST_SPEED)
                        val files = listOf(File(home, "state.json")) + dirs.flatMap { File(home, it).listFiles().orEmpty().sorted() }
                        for (f in files.filter { it.isFile }) {
                            zip.putNextEntry(ZipEntry(f.relativeTo(home).path))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("Badal", "can't save the backup", e)
                false
            }
        }

    /** Replaces everything with the backup at [from]; false, and nothing changed, if it isn't one. */
    suspend fun restore(from: Uri): Boolean {
        val restored =
            withContext(Dispatchers.IO) {
                val home = context.filesDir
                // Unpacked beside the live files first: same volume, so each folder moves in at once.
                val tmp = File(home, "restore").apply { deleteRecursively() }
                try {
                    context.contentResolver.openInputStream(from)!!.use { raw ->
                        ZipInputStream(raw.buffered()).use { zip ->
                            while (true) {
                                val e = zip.nextEntry ?: break
                                if (e.isDirectory || !names.matches(e.name)) continue
                                File(tmp, e.name).apply { parentFile!!.mkdirs() }.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    val state = File(tmp, "state.json").takeIf { it.exists() }?.let { store.decode(it.readText()) } ?: return@withContext null
                    for (d in dirs) {
                        val live = File(home, d)
                        live.deleteRecursively()
                        if (!File(tmp, d).renameTo(live)) live.mkdirs()
                    }
                    state
                } catch (e: Exception) {
                    Log.e("Badal", "can't restore the backup", e)
                    null
                } finally {
                    tmp.deleteRecursively()
                }
            } ?: return false
        store.update { restored }
        engine.settle(Screen.entries.toSet())
        return true
    }
}
