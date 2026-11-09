package io.github.vivekg7.badal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch

/**
 * The launcher icon's long-press shortcuts: next home, next lock, and one per
 * mode switched by hand, plus "Modes off" (plan 009). Samsung's Modes and
 * Routines can run these too, so a phone mode can switch a Badal mode. Nothing
 * to show: it starts the change on the app's scope and closes at once.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val st = app.store.state.value
        intent.action?.let { getSystemService(ShortcutManager::class.java).reportShortcutUsed(it) }
        if (intent.action == ACTION_MODE) {
            val id = intent.getLongExtra(EXTRA_MODE, 0).takeIf { it != 0L }
            val name = st.modes.firstOrNull { it.id == id }?.name
            Toast.makeText(this, if (name == null) "Modes off" else "$name on", Toast.LENGTH_SHORT).show()
            app.scope.launch { app.engine.mode(st.modes.firstOrNull { it.id == id }?.id) }
            finish()
            return
        }
        val screens =
            when (intent.action) {
                ACTION_NEXT_HOME -> listOf(Screen.HOME)
                ACTION_NEXT_LOCK -> listOf(if (st.follows) Screen.HOME else Screen.LOCK)
                else -> st.driven
            }
        app.scope.launch { app.engine.next(screens) }
        finish()
    }

    companion object {
        // Dynamic, not res/xml/shortcuts.xml: a static shortcut names its package,
        // and the debug build has a different one.
        fun publish(
            context: Context,
            st: State,
        ) {
            fun shortcut(
                id: String,
                label: String,
                icon: Int,
                intent: Intent,
            ) = ShortcutInfo
                .Builder(context, id)
                .setShortLabel(label)
                .setIcon(Icon.createWithResource(context, icon))
                .setIntent(intent)
                .build()

            fun next(
                action: String,
                label: String,
                icon: Int,
            ) = shortcut(action, label, icon, Intent(context, ShortcutActivity::class.java).setAction(action))

            fun mode(
                id: Long,
                label: String,
            ) = shortcut(
                "mode:$id",
                label,
                R.drawable.ic_shortcut_mode,
                Intent(context, ShortcutActivity::class.java).setAction(ACTION_MODE).putExtra(EXTRA_MODE, id),
            )
            val manager = context.getSystemService(ShortcutManager::class.java)
            val byHand = st.modes.filter { it.cue == Cue.HAND }
            val all =
                listOf(
                    next(ACTION_NEXT_HOME, "Next home screen", R.drawable.ic_shortcut_home),
                    next(ACTION_NEXT_LOCK, "Next lock screen", R.drawable.ic_shortcut_lock),
                ) + byHand.map { mode(it.id, it.name) } + listOfNotNull(if (byHand.isEmpty()) null else mode(0, "Modes off"))
            manager.dynamicShortcuts = all.take(manager.maxShortcutCountPerActivity)
        }

        const val ACTION_NEXT_HOME = "io.github.vivekg7.badal.NEXT_HOME"
        const val ACTION_NEXT_LOCK = "io.github.vivekg7.badal.NEXT_LOCK"
        const val ACTION_MODE = "io.github.vivekg7.badal.MODE"
        const val EXTRA_MODE = "mode"
    }
}
