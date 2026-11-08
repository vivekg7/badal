package io.github.vivekg7.badal

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [State] in one JSON file, rewritten whole on every change. A few hundred
 * pictures make a few kilobytes, and everything runs in one process, so a
 * database would buy nothing (docs/plans/001_wallpaper_rotator.md).
 */
class Store(
    file: File,
) {
    private val file = AtomicFile(file)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val mutex = Mutex()
    private val flow = MutableStateFlow(load())
    val state: StateFlow<State> = flow

    suspend fun update(change: (State) -> State): State =
        mutex.withLock {
            val next = change(flow.value)
            if (next != flow.value) {
                withContext(Dispatchers.IO) {
                    val out = file.startWrite()
                    try {
                        out.write(json.encodeToString(State.serializer(), next).toByteArray())
                        file.finishWrite(out)
                    } catch (e: Exception) {
                        file.failWrite(out)
                        throw e
                    }
                }
                flow.value = next
            }
            next
        }

    private fun load(): State =
        try {
            if (file.baseFile.exists()) json.decodeFromString(State.serializer(), String(file.readFully())) else State()
        } catch (e: Exception) {
            // Unreadable means a bug, not a user mistake: start clean rather than
            // crash on every launch, but keep the old file aside to recover from.
            Log.e("Badal", "state.json unreadable, starting empty", e)
            file.baseFile.renameTo(File(file.baseFile.path + ".bad"))
            State()
        }
}
