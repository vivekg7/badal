package io.github.vivekg7.badal.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Album
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
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
            Text(
                "Make an album for each mood or season, then pick one for the home screen and one for the lock screen.",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        usedBy(state, a) ?: plural(a.images.size, "picture"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { naming = true },
            icon = { Icon(Icons.Add, null) },
            text = { Text("New album") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
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
            "home".takeIf { uses(Screen.HOME) },
            "lock".takeIf { uses(Screen.LOCK) && !state.follows },
        )
    return if (on.isEmpty()) null else "${plural(a.images.size, "picture")} · on ${on.joinToString(" & ")}"
}

/** One album's pictures. Tap one to show it now; long-press to select several. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumPage(
    album: Album,
    state: State,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val adding by app.library.adding.collectAsState()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var tapped by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

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
            if (tree != null) adding { app.library.addFolder(album.id, tree) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) album.name else "${selected.size} selected") },
                navigationIcon = {
                    if (selected.isEmpty()) {
                        IconButton(onBack) { Icon(Icons.Back, "Back") }
                    } else {
                        IconButton({ selected = emptySet() }) { Icon(Icons.Close, "Clear selection") }
                    }
                },
                actions = {
                    if (selected.isEmpty()) {
                        IconButton({ renaming = true }) { Icon(Icons.Edit, "Rename") }
                    }
                    IconButton({ deleting = true }) { Icon(Icons.Delete, if (selected.isEmpty()) "Delete album" else "Remove pictures") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton({ folder.launch(null) }) { Icon(Icons.Folder, "Add a folder") }
                ExtendedFloatingActionButton(
                    onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = { Icon(Icons.Add, null) },
                    text = { Text("Add pictures") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            adding?.let { (done, total) ->
                LinearProgressIndicator({ done / total.toFloat() }, Modifier.fillMaxWidth())
                Text("Adding $done of $total", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (album.images.isEmpty() && adding == null) {
                Text(
                    "No pictures yet. Add some from your gallery, or every picture in a folder.",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 160.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(album.images, key = { it }) { id ->
                    val isSelected = id in selected
                    val onScreen = id == state.homeNow.current || id == state.lockNow.current
                    Box(
                        Modifier
                            .aspectRatio(9f / 16f)
                            .clip(MaterialTheme.shapes.small)
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
                            ).combinedClickable(
                                onClick = {
                                    if (selected.isEmpty()) {
                                        tapped = id
                                    } else {
                                        selected =
                                            if (isSelected) selected - id else selected + id
                                    }
                                },
                                onLongClick = { selected = selected + id },
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
                    }
                }
            }
        }
    }

    tapped?.let { id ->
        fun show(screens: List<Screen>) {
            tapped = null
            app.scope.launch { app.engine.show(id, screens) }
        }
        AlertDialog(
            onDismissRequest = { tapped = null },
            title = { Text("Show this picture now") },
            text = {
                Column {
                    Thumb(
                        id,
                        Modifier
                            .fillMaxWidth(
                                0.5f,
                            ).aspectRatio(9f / 16f)
                            .clip(MaterialTheme.shapes.medium)
                            .align(Alignment.CenterHorizontally),
                    )
                    ListItem(headlineContent = { Text("On the home screen") }, modifier = Modifier.clickable { show(listOf(Screen.HOME)) })
                    if (!state.follows) {
                        ListItem(
                            headlineContent = { Text("On the lock screen") },
                            modifier = Modifier.clickable { show(listOf(Screen.LOCK)) },
                        )
                    }
                    ListItem(
                        headlineContent = { Text("On both") },
                        modifier = Modifier.clickable { show(listOf(Screen.HOME, Screen.LOCK)) },
                    )
                }
            },
            confirmButton = { TextButton({ tapped = null }) { Text("Cancel") } },
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
