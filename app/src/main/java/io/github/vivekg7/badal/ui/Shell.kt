package io.github.vivekg7.badal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.app

private val tabs =
    listOf(
        "Home screen" to Icons.Home,
        "Lock screen" to Icons.Lock,
        "Albums" to Icons.Albums,
        "Settings" to Icons.Settings,
    )

/** Four tabs; an open album covers them. No navigation library: that is the whole back stack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shell() {
    val state by LocalContext.current.app.store.state
        .collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val album = state.album(open)
    if (album != null) {
        BackHandler { open = null }
        AlbumPage(album, state, onBack = { open = null })
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[tab].first) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
            when (tab) {
                0 -> ScreenPage(Screen.HOME, state, toAlbums = { tab = 2 })
                1 -> ScreenPage(Screen.LOCK, state, toAlbums = { tab = 2 })
                2 -> AlbumsPage(state, onOpen = { open = it })
                else -> SettingsPage(state)
            }
        }
    }
}
