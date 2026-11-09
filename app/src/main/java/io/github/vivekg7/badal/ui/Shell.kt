package io.github.vivekg7.badal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Design
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.app
import kotlinx.coroutines.launch

private val tabs =
    listOf(
        Tab("Home screen", "Home", Icons.Home),
        Tab("Lock screen", "Lock", Icons.Lock),
        // Its own top bar: the gallery's title counts a selection (GalleryPage).
        Tab("Make a wallpaper", "Designs", Icons.Palette),
        Tab("Albums", "Albums", Icons.Albums),
        Tab("Settings", "Settings", Icons.Settings),
    )

private const val DESIGNS = 2
private const val ALBUMS = 3

private data class Tab(
    val title: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Five tabs; an open album, the designer, the framing editor or Help covers
 * them. No navigation library: that is the whole back stack. A covered page leaves
 * composition, so each tab and the open album keep their saved state (scroll
 * positions above all) in [pages] and come back as they were left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shell() {
    val app = LocalContext.current.app
    val state by app.store.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var framing by rememberSaveable { mutableStateOf<Pair<Long, Screen>?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    // A design opened from the Designs tab (plan 008), as JSON, and the ones on their way into an album.
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf<List<Design>?>(null) }
    val pages = rememberSaveableStateHolder()
    placing?.let { designs ->
        AddToAlbum(designs, state, onDone = { added ->
            placing = null
            if (added) draft = null
        })
    }
    if (help) {
        BackHandler { help = false }
        HelpPage(onBack = { help = false })
        return
    }
    framing?.let { (id, screen) ->
        if (state.albums.any { id in it.images }) {
            BackHandler { framing = null }
            FramePage(id, screen, state, onClose = { framing = null })
            return
        }
    }
    draft?.let { json ->
        BackHandler { draft = null }
        DesignPage(decodeDesign(json), editing = false, onBack = { draft = null }, onDone = { placing = listOf(it) })
        return
    }
    val album = state.album(open)
    if (album != null) {
        // Leaving an album forgets it: open it again and it starts at the top.
        val close = {
            pages.removeState("album:${album.id}")
            open = null
        }
        BackHandler(onBack = close)
        pages.SaveableStateProvider("album:${album.id}") {
            AlbumPage(album, state, onBack = close, onFrame = { framing = it to Screen.HOME })
        }
        return
    }
    val screen = listOf(Screen.HOME, Screen.LOCK).getOrNull(tab)
    Scaffold(
        topBar = {
            if (tab == DESIGNS) return@Scaffold
            TopAppBar(
                title = { Text(tabs[tab].title) },
                actions = {
                    IconButton({ help = true }) { Icon(Icons.Help, "Help") }
                    // Whether Badal changes this screen at all.
                    if (screen != null) {
                        Switch(
                            state.rotation(screen).enabled,
                            { on -> app.scope.launch { app.engine.edit(screen) { it.copy(enabled = on) } } },
                            Modifier.padding(start = 4.dp, end = 12.dp).semantics { contentDescription = "Change the ${tabs[tab].title.lowercase()}" },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
            pages.SaveableStateProvider("tab:$tab") {
                when (tab) {
                    0 -> {
                        ScreenPage(Screen.HOME, state, toAlbums = { tab = ALBUMS }, onFrame = { framing = it to Screen.HOME })
                    }

                    1 -> {
                        ScreenPage(Screen.LOCK, state, toAlbums = { tab = ALBUMS }, onFrame = { framing = it to Screen.LOCK })
                    }

                    DESIGNS -> {
                        GalleryPage(
                            rememberLazyGridState(),
                            onBack = null,
                            onHelp = { help = true },
                            onOpen = { draft = it.encoded() },
                            onAdd = { placing = it },
                        )
                    }

                    ALBUMS -> {
                        AlbumsPage(state, onOpen = { open = it })
                    }

                    else -> {
                        SettingsPage(state, onHelp = { help = true })
                    }
                }
            }
        }
    }
}
