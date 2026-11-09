package io.github.vivekg7.badal.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import io.github.vivekg7.badal.Album
import io.github.vivekg7.badal.Design
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AlbumsPage(
    state: State,
    onOpen: (Long) -> Unit,
) {
    val app = LocalContext.current.app
    var naming by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (state.albums.isEmpty()) {
            Text("No albums yet", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.albums, key = { it.id }) { a ->
                Column(Modifier.clip(MaterialTheme.shapes.medium).clickable { onOpen(a.id) }) {
                    Thumb(a.images.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
                    Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        listOfNotNull(a.images.size.toString(), usedBy(state, a)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FloatingActionButton({ naming = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Add, "New album") }
    }
    if (naming) {
        NameDialog("New album", "", onDone = { name ->
            naming = false
            app.scope.launch { onOpen(app.library.create(name)) }
        }, onDismiss = { naming = false })
    }
}

private fun usedBy(
    state: State,
    a: Album,
): String? {
    fun uses(s: Screen) = state.rotation(s).let { r -> r.enabled && (r.albumId == a.id || r.ranges.any { it.albumId == a.id }) }
    val on =
        listOfNotNull(
            "Home".takeIf { uses(Screen.HOME) },
            "Lock".takeIf { uses(Screen.LOCK) && !state.follows },
        )
    return if (on.isEmpty()) null else on.joinToString(", ")
}

/**
 * One album's pictures. Tap one to show it now; long-press to select several.
 * In reorder mode, hold one and drag it to a new place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumPage(
    album: Album,
    state: State,
    onBack: () -> Unit,
    onFrame: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val adding by app.library.adding.collectAsState()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var tapped by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Uri?>(null) } // a folder, waiting for "follow it?"
    var following by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val drag = remember(album.id) { Drag(grid).apply { order = album.images } }
    // Pictures added or removed; never mid-drag, nor in the moment between a drop and its save.
    LaunchedEffect(album.images) { if (drag.held == null) drag.order = album.images }

    fun added(n: Int) = Toast.makeText(context, if (n == 0) "Nothing added" else "Added ${plural(n, "picture")}", Toast.LENGTH_SHORT).show()

    // On the app's scope, so leaving the album doesn't stop a long copy.
    fun adding(work: suspend () -> Int) =
        app.scope.launch {
            val n = work()
            withContext(Dispatchers.Main) { added(n) }
        }
    val photos =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) adding { app.library.add(album.id, uris) }
        }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            picked = tree
        }
    if (reordering) BackHandler { reordering = false }

    // Making wallpapers (plan 005): the gallery, then the designer over it.
    var gallery by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var redesigning by rememberSaveable { mutableStateOf<Long?>(null) }
    // Out here, so the gallery is where you left it when you come back from the designer.
    val galleryGrid = rememberLazyGridState()
    draft?.let { json ->
        val close = {
            draft = null
            redesigning = null
        }
        BackHandler(onBack = close)
        DesignPage(decodeDesign(json), editing = redesigning != null, onBack = close, onDone = { d ->
            val old = redesigning
            close()
            gallery = false
            if (old != null) app.scope.launch { app.library.redesign(album.id, old, d) } else adding { app.library.make(album.id, listOf(d)) }
        })
        return
    }
    if (gallery) {
        BackHandler { gallery = false }
        GalleryPage(galleryGrid, onBack = { gallery = false }, onOpen = { draft = it.encoded() }, onAdd = { list ->
            gallery = false
            adding { app.library.make(album.id, list) }
        })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            reordering -> "Hold and drag"
                            selected.isEmpty() -> album.name
                            else -> "${selected.size} selected"
                        },
                    )
                },
                navigationIcon = {
                    when {
                        reordering -> IconButton({ reordering = false }) { Icon(Icons.Check, "Done") }
                        selected.isEmpty() -> IconButton(onBack) { Icon(Icons.Back, "Back") }
                        else -> IconButton({ selected = emptySet() }) { Icon(Icons.Close, "Clear selection") }
                    }
                },
                actions = {
                    if (reordering) return@TopAppBar
                    if (selected.isEmpty()) {
                        if (album.folder != null) IconButton({ following = true }) { Icon(Icons.Sync, "Follows a folder") }
                        if (album.images.size > 1) IconButton({ reordering = true }) { Icon(Icons.Reorder, "Reorder") }
                        IconButton({ renaming = true }) { Icon(Icons.Edit, "Rename") }
                    } else {
                        // Star them all, unless they all are already: then unstar.
                        val all = album.starred.containsAll(selected)
                        IconButton({
                            val ids = selected
                            selected = emptySet()
                            app.scope.launch { app.library.star(album.id, ids, !all) }
                        }) { Icon(if (all) Icons.Star else Icons.StarOutline, if (all) "Remove from favourites" else "Add to favourites") }
                        IconButton({
                            val ids = selected
                            selected = emptySet()
                            export(context) { app -> ids.count { app.images.export(it) } }
                        }) { Icon(Icons.Download, "Save to gallery") }
                    }
                    IconButton({ deleting = true }) { Icon(Icons.Delete, if (selected.isEmpty()) "Delete album" else "Remove pictures") }
                },
            )
        },
        floatingActionButton = {
            if (reordering) return@Scaffold
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton({ gallery = true }) { Icon(Icons.Palette, "Make a wallpaper") }
                SmallFloatingActionButton({ folder.launch(null) }) { Icon(Icons.Folder, "Add a folder") }
                FloatingActionButton({ photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Add, "Add pictures")
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            adding?.let { (done, total) ->
                LinearProgressIndicator({ done / total.toFloat() }, Modifier.fillMaxWidth())
                Text("Adding $done of $total", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (album.images.isEmpty() && adding == null) {
                Text("No pictures yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                GridCells.Fixed(3),
                state = grid,
                contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 160.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .onGloballyPositioned { drag.box = it.boundsInRoot() }
                        .then(
                            if (reordering) {
                                Modifier.pointerInput(
                                    album.id,
                                ) { drag.detect(this) { app.scope.launch { app.library.reorder(album.id, it) } } }
                            } else {
                                Modifier
                            },
                        ),
            ) {
                items(drag.order, key = { it }) { id ->
                    val isSelected = id in selected
                    val held = id == drag.held
                    val onScreen = id == state.homeNow.current || id == state.lockNow.current
                    Box(
                        Modifier
                            .then(if (held) Modifier.zIndex(1f) else Modifier.animateItem())
                            .aspectRatio(9f / 16f)
                            .onGloballyPositioned { drag.bounds[id] = it.boundsInRoot() }
                            .graphicsLayer {
                                if (held) {
                                    val at = drag.offset(id)
                                    translationX = at.x
                                    translationY = at.y
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                    shadowElevation = 12.dp.toPx()
                                    shape = RoundedCornerShape(8.dp)
                                    clip = true
                                }
                            }.clip(MaterialTheme.shapes.small)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        4.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.small,
                                    )
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (reordering) {
                                    Modifier
                                } else {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (selected.isEmpty()) {
                                                tapped = id
                                            } else {
                                                selected =
                                                    if (isSelected) selected - id else selected + id
                                            }
                                        },
                                        onLongClick = { selected = selected + id },
                                    )
                                },
                            ),
                    ) {
                        Thumb(id, Modifier.fillMaxSize(), dim = if (selected.isNotEmpty() && !isSelected) 40 else 0)
                        if (onScreen) {
                            Icon(
                                Icons.Check,
                                "On screen now",
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(2.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        if (id in album.starred) {
                            Icon(
                                Icons.Star,
                                "Favourite",
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(3.dp)
                                    .size(16.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    tapped?.let { id ->
        val design by produceState<Design?>(null, id) { value = withContext(Dispatchers.IO) { app.images.design(id) } }

        fun show(screens: List<Screen>) {
            tapped = null
            app.scope.launch { app.engine.show(id, screens) }
        }
        Dialog({ tapped = null }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(vertical = 24.dp)) {
                    Thumb(
                        id,
                        Modifier
                            .fillMaxWidth(0.4f)
                            .aspectRatio(9f / 16f)
                            .clip(MaterialTheme.shapes.medium)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(8.dp))
                    Setting(Icons.Home, "Show on home", onClick = { show(listOf(Screen.HOME)) })
                    if (!state.follows) Setting(Icons.Lock, "Show on lock", onClick = { show(listOf(Screen.LOCK)) })
                    Setting(Icons.Split, "Show on both", onClick = { show(listOf(Screen.HOME, Screen.LOCK)) })
                    val starred = id in album.starred
                    Setting(if (starred) Icons.Star else Icons.StarOutline, if (starred) "Unfavourite" else "Favourite", onClick = {
                        tapped = null
                        app.scope.launch { app.library.star(album.id, setOf(id), !starred) }
                    })
                    design?.let { found ->
                        Setting(Icons.Palette, "Edit design", onClick = {
                            tapped = null
                            redesigning = id
                            draft = found.encoded()
                        })
                    }
                    Setting(Icons.Crop, "Frame", onClick = {
                        tapped = null
                        onFrame(id)
                    })
                    Setting(Icons.Download, "Save to gallery", onClick = {
                        tapped = null
                        export(context) { if (it.images.export(id)) 1 else 0 }
                    })
                }
            }
        }
    }
    picked?.let { tree ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Follow this folder?") },
            text = { Text("New pictures in it join the album, and ones deleted from it leave.") },
            confirmButton = {
                TextButton({
                    picked = null
                    // A lasting grant, or the folder can't be read again after a reboot.
                    context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    album.folder?.let { old -> release(context, old) }
                    adding { app.library.follow(album.id, tree) }
                }) { Text("Follow") }
            },
            dismissButton = {
                TextButton({
                    picked = null
                    adding { app.library.addFolder(album.id, tree) }
                }) { Text("Add once") }
            },
        )
    }
    if (following) {
        AlertDialog(
            onDismissRequest = { following = false },
            title = { Text("Follows a folder") },
            text = { Text("New pictures in it join this album when Badal opens, and once a day. Ones deleted from it leave.") },
            confirmButton = { TextButton({ following = false }) { Text("Close") } },
            dismissButton = {
                TextButton({
                    following = false
                    album.folder?.let { release(context, it) }
                    app.scope.launch { app.library.unfollow(album.id) }
                }) { Text("Stop following") }
            },
        )
    }
    if (renaming) {
        NameDialog("Rename album", album.name, onDone = { name ->
            renaming = false
            app.scope.launch { app.library.rename(album.id, name) }
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        val all = selected.isEmpty()
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(if (all) "Delete “${album.name}”?" else "Remove ${plural(selected.size, "picture")}?") },
            text = { Text("Badal's copies are deleted. The originals in your gallery stay.") },
            confirmButton = {
                TextButton({
                    deleting = false
                    if (all) {
                        onBack()
                        app.scope.launch { app.library.delete(album.id) }
                    } else {
                        val ids = selected
                        selected = emptySet()
                        app.scope.launch { app.library.remove(album.id, ids) }
                    }
                }) { Text(if (all) "Delete" else "Remove") }
            },
            dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } },
        )
    }
}

/** Give back the lasting grant to a folder no longer followed. */
private fun release(
    context: Context,
    folder: String,
) = try {
    context.contentResolver.releasePersistableUriPermission(Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION)
} catch (e: SecurityException) {
    // Already gone, as after a restore on another phone.
}

/**
 * Hold-and-drag reordering for the album grid, with no library. The held
 * picture moves through the list as it passes over others, and is drawn under
 * the finger wherever its slot is; it is saved when let go. Slots are read
 * from the grid's layout info, which is current on every frame; [bounds], in
 * root coordinates, only finds the picture under the finger at the start.
 */
@Stable
private class Drag(
    val grid: LazyGridState,
) {
    var order by mutableStateOf(emptyList<Long>())
    var held by mutableStateOf<Long?>(null)
    val bounds = mutableMapOf<Long, Rect>()
    var box = Rect.Zero

    private var start = Offset.Zero // the held picture's slot when picked up, in layout-info coordinates
    private var before = emptyList<Long>()
    private var moved by mutableStateOf(Offset.Zero)

    /** How far to draw the held picture from its slot, so it stays under the finger. */
    fun offset(id: Long) = slot(id)?.let { start + moved - it.offset.toOffset() } ?: Offset.Zero

    private fun slot(id: Long) = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    /**
     * Hold a picture, then drag it. Read in the Initial pass, ahead of the grid's
     * own scrolling, which would otherwise take the moves; once held, every move
     * is consumed, so the grid doesn't scroll under the finger.
     */
    suspend fun detect(
        scope: PointerInputScope,
        save: (List<Long>) -> Unit,
    ) = coroutineScope {
        val area = this
        scope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val id = visible().firstOrNull { bounds[it]?.contains(box.topLeft + down.position) == true } ?: return@awaitEachGesture
            // Null when the press outlasts the long-press timeout without moving or lifting.
            val let =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }
            if (let != null) return@awaitEachGesture
            held = id
            before = order
            start = slot(id)?.offset?.toOffset() ?: return@awaitEachGesture
            moved = Offset.Zero
            val scrolling = area.launch { scroll(id) }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                    event.changes.forEach { it.consume() }
                    if (!c.pressed) break
                    moved += c.position - c.previousPosition
                    swap()
                }
            } finally {
                scrolling.cancel()
                val done = order
                held = null
                if (done != before) save(done)
            }
        }
    }

    /** Near the top or bottom edge, scroll, and keep swapping as the grid moves under the finger. */
    private suspend fun scroll(id: Long) {
        while (true) {
            val info = grid.layoutInfo
            val y = start.y + moved.y + (slot(id)?.size?.height ?: 0) / 2
            val edge = info.viewportSize.height * 0.12f
            val top = info.viewportStartOffset + edge
            val bottom = info.viewportEndOffset - edge
            val speed =
                when {
                    y < top -> (y - top) / 4
                    y > bottom -> (y - bottom) / 4
                    else -> 0f
                }
            if (speed != 0f) {
                grid.scrollBy(speed)
                swap()
            }
            withFrameNanos { }
        }
    }

    private fun visible() = grid.layoutInfo.visibleItemsInfo.map { it.key as Long }

    private fun LazyGridItemInfo.holds(p: Offset) = p.x >= offset.x && p.x < offset.x + size.width && p.y >= offset.y && p.y < offset.y + size.height

    /** Move the held picture to the slot under its centre. */
    private fun swap() {
        val id = held ?: return
        val size = slot(id)?.size ?: return
        val centre = start + moved + Offset(size.width / 2f, size.height / 2f)
        val target =
            grid.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key != id && it.holds(centre) }
                ?.key ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target)
        if (from < 0 || to < 0) return
        // The grid keeps its first visible item in place: when that is one of the
        // two, ask it to stay where it is instead of jumping with it.
        if (grid.firstVisibleItemIndex in setOf(from, to)) {
            grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        }
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
