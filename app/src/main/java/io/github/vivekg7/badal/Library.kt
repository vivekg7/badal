package io.github.vivekg7.badal

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Albums and their pictures. Each change ends with [Engine.settle], so the wallpapers follow. */
class Library(
    private val store: Store,
    private val images: Images,
    private val engine: Engine,
) {
    private val progress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** One look through a folder at a time, or two would both add its new pictures. */
    private val scanning = Mutex()

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
            val gone = album.images.toSet()

            fun Rotation.without() =
                copy(
                    albumId = albumId.takeIf { it != id },
                    picture = picture.takeIf { it !in gone },
                    ranges = ranges.filter { it.albumId != id },
                    frames = frames - gone,
                )
            st.copy(
                albums = st.albums.filter { it.id != id },
                home = st.home.without(),
                lock = st.lock.without(),
                modes = st.modes.map { it.copy(home = it.home.takeIf { a -> a != id }, lock = it.lock.takeIf { a -> a != id }) },
            )
        }
        withContext(Dispatchers.IO) { album.images.forEach(images::delete) }
        engine.settle()
    }

    /** Copies [uris] into the album, keeping their order; ones that aren't readable images are skipped. */
    suspend fun add(
        albumId: Long,
        uris: List<Uri>,
    ): Int = fill(albumId, uris.size) { i, id -> images.add(uris[i], id) }.size

    /** Draws [designs] into the album, in order (plan 005). */
    suspend fun make(
        albumId: Long,
        designs: List<Design>,
    ): Int = fill(albumId, designs.size) { i, id -> images.make(designs[i], id) }.size

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

    /** Puts [n] new pictures into the album, each made by [make] under its id; returns the ids of those made, by index. */
    private suspend fun fill(
        albumId: Long,
        n: Int,
        make: (Int, Long) -> Boolean,
    ): Map<Int, Long> {
        if (n == 0) return emptyMap()
        var first = 0L
        store.update {
            first = it.nextId
            it.copy(nextId = first + n)
        }
        val added = mutableMapOf<Int, Long>()
        progress.value = 0 to n
        withContext(Dispatchers.IO) {
            for (i in 0 until n) {
                if (make(i, first + i)) added[i] = first + i
                progress.value = i + 1 to n
            }
        }
        store.update { st -> st.copy(albums = st.albums.map { if (it.id == albumId) it.copy(images = it.images + added.values) else it }) }
        progress.value = null
        engine.settle()
        return added
    }

    /** Every picture in a folder, copied once. */
    suspend fun addFolder(
        albumId: Long,
        tree: Uri,
    ) = add(
        albumId,
        withContext(Dispatchers.IO) {
            images
                .inFolder(tree)
                ?.values
                ?.toList()
                .orEmpty()
        },
    )

    /**
     * Follow a folder (plan 010): its pictures now, and from then on, each time the
     * folders are looked through ([rescan]), whatever was added to it or deleted
     * from it. The caller holds a lasting read grant for [tree]. Returns how many
     * were added now.
     */
    suspend fun follow(
        albumId: Long,
        tree: Uri,
    ): Int {
        store.update { st -> st.copy(albums = st.albums.map { if (it.id == albumId) it.copy(folder = tree.toString(), docs = emptyMap()) else it }) }
        return scanning.withLock { scan(albumId) }
    }

    /** Stop following; the pictures stay. */
    suspend fun unfollow(albumId: Long) {
        store.update { st -> st.copy(albums = st.albums.map { if (it.id == albumId) it.copy(folder = null, docs = emptyMap()) else it }) }
    }

    /** Look through every followed folder; with [daily], only if that was last done a day ago or more. */
    suspend fun rescan(daily: Boolean = false) =
        scanning.withLock {
            val st = store.state.value
            val now = System.currentTimeMillis()
            if (st.albums.none { it.folder != null } || (daily && now - st.scannedAt < DAY)) return@withLock
            store.update { it.copy(scannedAt = now) }
            st.albums.filter { it.folder != null }.forEach { scan(it.id) }
        }

    /**
     * Add the folder's new pictures and remove those deleted from it. A picture
     * removed from the album by hand stays known, so it doesn't come back. An
     * unreadable folder changes nothing. Returns how many were added.
     */
    private suspend fun scan(albumId: Long): Int {
        val a = store.state.value.album(albumId) ?: return 0
        val found = withContext(Dispatchers.IO) { images.inFolder(Uri.parse(a.folder ?: return@withContext null)) } ?: return 0
        val new = found.keys.filter { it !in a.docs }
        val gone = a.docs.filterKeys { it !in found }
        val made = fill(albumId, new.size) { i, id -> images.add(found.getValue(new[i]), id) }
        store.update { st ->
            st.copy(
                albums =
                    st.albums.map {
                        if (it.id == albumId) it.copy(docs = it.docs - gone.keys + made.mapKeys { (i, _) -> new[i] }) else it
                    },
            )
        }
        if (gone.isNotEmpty()) remove(albumId, gone.values.toSet())
        return made.size
    }

    /** Removes pictures from the album and the phone. One that is on screen now stays there until the next change. */
    suspend fun remove(
        albumId: Long,
        ids: Set<Long>,
    ) {
        store.update { st ->
            st.copy(
                albums = st.albums.map { if (it.id == albumId) it.copy(images = it.images - ids, starred = it.starred - ids) else it },
                home = st.home.copy(frames = st.home.frames - ids, picture = st.home.picture.takeIf { it !in ids }),
                lock = st.lock.copy(frames = st.lock.frames - ids, picture = st.lock.picture.takeIf { it !in ids }),
            )
        }
        withContext(Dispatchers.IO) { ids.forEach(images::delete) }
        engine.settle()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
