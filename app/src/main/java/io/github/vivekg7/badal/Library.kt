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
    ): Int = fill(albumId, uris.size) { i, id -> images.add(uris[i], id) }

    /** Draws [designs] into the album, in order (plan 005). */
    suspend fun make(
        albumId: Long,
        designs: List<Design>,
    ): Int = fill(albumId, designs.size) { i, id -> images.make(designs[i], id) }

    /**
     * Draws [d] in place of picture [old]: under a new id, so an id always names
     * the same pixels and nothing that compares ids is fooled. A screen showing
     * the old one shows the new one.
     */
    suspend fun redesign(
        albumId: Long,
        old: Long,
        d: Design,
    ) {
        var id = 0L
        store.update {
            id = it.nextId
            it.copy(nextId = id + 1)
        }
        progress.value = 0 to 1
        val made = withContext(Dispatchers.IO) { images.make(d, id) }
        progress.value = null
        if (!made) return
        val st =
            store.update { st ->
                fun Album.swapped() = copy(images = images.map { if (it == old) id else it }, starred = if (old in starred) starred - old + id else starred)
                st.copy(
                    albums = st.albums.map { if (it.id == albumId) it.swapped() else it },
                    home = st.home.copy(frames = st.home.frames - old),
                    lock = st.lock.copy(frames = st.lock.frames - old),
                )
            }
        val showing = Screen.entries.filter { st.progress(it).current == old }
        if (showing.isNotEmpty()) engine.show(id, showing)
        withContext(Dispatchers.IO) { images.delete(old) }
        engine.settle()
    }

    /** Puts [n] new pictures into the album, each made by [make] under its id; returns how many were made. */
    private suspend fun fill(
        albumId: Long,
        n: Int,
        make: (Int, Long) -> Boolean,
    ): Int {
        if (n == 0) return 0
        var first = 0L
        store.update {
            first = it.nextId
            it.copy(nextId = first + n)
        }
        val added = mutableListOf<Long>()
        progress.value = 0 to n
        withContext(Dispatchers.IO) {
            for (i in 0 until n) {
                if (make(i, first + i)) added += first + i
                progress.value = i + 1 to n
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
