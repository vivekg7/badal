package io.github.vivekg7.badal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vivekg7.badal.ui.BadalTheme
import io.github.vivekg7.badal.ui.Shell
import io.github.vivekg7.badal.ui.dropCaches

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Here, not in App: the process also starts for every alarm and screen-off,
        // and the shortcuts only need setting once someone has opened the app.
        ShortcutActivity.publish(this)
        setContent { BadalTheme { Shell() } }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) dropCaches()
    }
}
