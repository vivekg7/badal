package io.github.vivekg7.badal

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.launch

/** A Quick Settings button: next picture on every screen Badal changes. */
class NextTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state =
                if (app.store.state.value.driven
                        .isEmpty()
                ) {
                    Tile.STATE_UNAVAILABLE
                } else {
                    Tile.STATE_INACTIVE
                }
            updateTile()
        }
    }

    override fun onClick() {
        val engine = app.engine
        app.scope.launch { engine.next(app.store.state.value.driven) }
    }
}
