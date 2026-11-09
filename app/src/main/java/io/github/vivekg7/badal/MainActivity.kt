package io.github.vivekg7.badal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vivekg7.badal.ui.BadalTheme
import io.github.vivekg7.badal.ui.Shell
import io.github.vivekg7.badal.ui.dropCaches
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Here, not in App: the process also starts for every alarm and screen-off,
        // and the shortcuts only need setting once someone has opened the app.
        ShortcutActivity.publish(this, app.store.state.value)
        setContent { BadalTheme { Shell() } }
    }

    /** Followed folders (plan 010) are looked through each time the app comes up, so new photos are there to see. */
    override fun onStart() {
        super.onStart()
        app.scope.launch { app.library.rescan() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) dropCaches()
    }
}
