package io.github.vivekg7.badal.ui

import android.app.AlarmManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.vivekg7.badal.LockWatcher
import io.github.vivekg7.badal.NextTile
import io.github.vivekg7.badal.R
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(state: State) {
    val context = LocalContext.current
    val app = context.app
    // Grants change in the system's settings: read them again on every return.
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val unrestricted =
        remember(resumed) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val exact = remember(resumed) { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val uri = Uri.parse("package:${context.packageName}")

    Column(Modifier.verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text("Different pictures on each screen") },
            supportingContent = { Text("When home and lock share an album, never show the same picture on both at once") },
            trailingContent = {
                Switch(state.distinct, { on -> app.scope.launch { app.store.update { it.copy(distinct = on) } } })
            },
        )

        ListItem(
            headlineContent = { Text("Pause on battery saver") },
            supportingContent = { Text("While it's on, skip changes on lock and on a timer. Albums for set hours still switch.") },
            trailingContent = {
                Switch(state.saverPause, { on ->
                    app.scope.launch {
                        app.store.update { it.copy(saverPause = on) }
                        app.engine.settle()
                    }
                })
            },
        )

        Section("Running in the background") {
            ListItem(
                headlineContent = { Text("Battery") },
                supportingContent = {
                    Text(
                        when {
                            unrestricted -> "Unrestricted: Badal keeps watching for the screen to lock"

                            LockWatcher.needed(
                                state,
                            ) -> "Restricted. Samsung phones stop restricted apps, and changes on lock stop with them. Tap to allow."

                            else -> "Restricted. Only needed for changing on each lock."
                        },
                    )
                },
                modifier =
                    Modifier.clickable(enabled = !unrestricted) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
                    },
            )
            ListItem(
                headlineContent = { Text("Exact timing") },
                supportingContent = {
                    Text(
                        if (exact) {
                            "On: intervals and set hours change on the minute"
                        } else {
                            "Off: Android may change a few minutes late to save battery. Tap to allow."
                        },
                    )
                },
                modifier =
                    Modifier.clickable(enabled = !exact) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
                    },
            )
        }

        Section("Shortcuts") {
            val label = stringResource(R.string.tile)
            ListItem(
                headlineContent = { Text("Add a Quick Settings tile") },
                supportingContent = {
                    Text(
                        "“Next wallpaper” in the panel you swipe down. Long-press the app icon for next home or lock.",
                    )
                },
                modifier =
                    Modifier.clickable {
                        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                            ComponentName(context, NextTile::class.java),
                            label,
                            Icon.createWithResource(context, R.drawable.ic_glyph),
                            context.mainExecutor,
                        ) {}
                    },
            )
        }

        Section("About") {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ListItem(
                headlineContent = { Text("Badal $version") },
                supportingContent = { Text("Your pictures stay on this phone. Badal has no internet access.") },
            )
        }
    }
}
