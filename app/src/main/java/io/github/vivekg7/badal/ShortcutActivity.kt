package io.github.vivekg7.badal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import kotlinx.coroutines.launch

/**
 * The launcher icon's long-press shortcuts. Nothing to show: it starts the
 * change on the app's scope and closes at once.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val st = app.store.state.value
        val screens =
            when (intent.action) {
                ACTION_NEXT_HOME -> listOf(Screen.HOME)
                ACTION_NEXT_LOCK -> listOf(if (st.follows) Screen.HOME else Screen.LOCK)
                else -> st.driven
            }
        intent.action?.let { getSystemService(ShortcutManager::class.java).reportShortcutUsed(it) }
        app.scope.launch { app.engine.next(screens) }
        finish()
    }

    companion object {
        // Dynamic, not res/xml/shortcuts.xml: a static shortcut names its package,
        // and the debug build has a different one.
        fun publish(context: Context) {
            fun shortcut(
                action: String,
                label: String,
                icon: Int,
            ) = ShortcutInfo
                .Builder(context, action)
                .setShortLabel(label)
                .setIcon(Icon.createWithResource(context, icon))
                .setIntent(Intent(context, ShortcutActivity::class.java).setAction(action))
                .build()
            context.getSystemService(ShortcutManager::class.java).dynamicShortcuts =
                listOf(
                    shortcut(ACTION_NEXT_HOME, "Next home screen", R.drawable.ic_shortcut_home),
                    shortcut(ACTION_NEXT_LOCK, "Next lock screen", R.drawable.ic_shortcut_lock),
                )
        }

        const val ACTION_NEXT_HOME = "io.github.vivekg7.badal.NEXT_HOME"
        const val ACTION_NEXT_LOCK = "io.github.vivekg7.badal.NEXT_LOCK"
    }
}
