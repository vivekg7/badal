package io.github.vivekg7.badal

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Albums and their pictures. Each change ends with [Engine.settle], so the wallpapers follow. */
class Library(
    private val store: Store,
    private val images: Images,
    private val engine: Engine,
) {
    private val progress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Pictures copied so far and the total, while adding. */
    val adding: StateFlow<Pair<Int, Int>?> = progress

    suspend fun create(name: String): Long {
        var id = 0L
        store.update {
            id = it.nextId
            it.copy(albums = it.albums + Album(id, name), nextId = id + 1)
        }
        return id
    }

    suspend fun rename(
        id: Long,
        name: String,
    ) {
        store.update { st -> st.copy(albums = st.albums.map { if (it.id == id) it.copy(name = name) else it }) }
    }

    /** Puts the album's pictures in [order], which "in order" then plays. Ignored if it isn't the same pictures. */
    suspend fun reorder(
        albumId: Long,
        order: List<Long>,
    ) {
        store.update { st ->
            st.copy(albums = st.albums.map { if (it.id == albumId && it.images.toSet() == order.toSet()) it.copy(images = order) else it })
        }
    }

    /** Marks pictures as favourites ([on]) or not; Shuffle shows a favourite twice a round. */
    suspend fun star(
        albumId: Long,
        ids: Set<Long>,
        on: Boolean,
    ) {
        store.update { st ->
            st.copy(albums = st.albums.map { if (it.id == albumId) it.copy(starred = if (on) it.starred + ids else it.starred - ids) else it })
        }
    }

    suspend fun delete(id: Long) {
        val album = store.state.value.album(id) ?: return
        store.update { st ->
            fun Rotation.without() =
                copy(albumId = albumId.takeIf { it != id }, ranges = ranges.filter { it.albumId != id }, frames = frames - album.images.toSet())
            st.copy(albums = st.albums.filter { it.id != id }, home = st.home.without(), lock = st.lock.without())
        }
        withContext(Dispatchers.IO) { album.images.forEach(images::delete) }
        engine.settle()
    }

    /** Copies [uris] into the album, keeping their order; ones that aren't readable images are skipped. */
    suspend fun add(
        albumId: Long,
        uris: List<Uri>,
    ): Int {
        if (uris.isEmpty()) return 0
        var first = 0L
        store.update {
            first = it.nextId
            it.copy(nextId = first + uris.size)
        }
        val added = mutableListOf<Long>()
        progress.value = 0 to uris.size
        withContext(Dispatchers.IO) {
            uris.forEachIndexed { i, uri ->
                if (images.add(uri, first + i)) added += first + i
                progress.value = i + 1 to uris.size
            }
        }
        store.update { st -> st.copy(albums = st.albums.map { if (it.id == albumId) it.copy(images = it.images + added) else it }) }
        progress.value = null
        engine.settle()
        return added.size
    }

    suspend fun addFolder(
        albumId: Long,
        tree: Uri,
    ) = add(albumId, withContext(Dispatchers.IO) { images.inFolder(tree) })

    /** Removes pictures from the album and the phone. One that is on screen now stays there until the next change. */
    suspend fun remove(
        albumId: Long,
        ids: Set<Long>,
    ) {
        store.update { st ->
            st.copy(
                albums = st.albums.map { if (it.id == albumId) it.copy(images = it.images - ids, starred = it.starred - ids) else it },
                home = st.home.copy(frames = st.home.frames - ids),
                lock = st.lock.copy(frames = st.lock.frames - ids),
            )
        }
        withContext(Dispatchers.IO) { ids.forEach(images::delete) }
        engine.settle()
    }
}
