package io.github.vivekg7.badal.ui

import android.app.AlarmManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.vivekg7.badal.LockWatcher
import io.github.vivekg7.badal.NextTile
import io.github.vivekg7.badal.R
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(
    state: State,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    // Grants change in the system's settings: read them again on every return.
    val resumed = resumes()
    val unrestricted =
        remember(resumed) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val exact = remember(resumed) { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val uri = Uri.parse("package:${context.packageName}")
    val label = stringResource(R.string.tile)
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SwitchSetting(Icons.Split, "Different on each screen", state.distinct) { on ->
            app.scope.launch { app.store.update { it.copy(distinct = on) } }
        }
        SwitchSetting(Icons.Saver, "Pause on battery saver", state.saverPause) { on ->
            app.scope.launch {
                app.store.update { it.copy(saverPause = on) }
                app.engine.settle()
            }
        }

        Section("Background") {
            // Samsung stops restricted apps, and changes on lock stop with them.
            val warn = !unrestricted && LockWatcher.needed(state)
            Setting(
                Icons.Battery,
                "Battery",
                enabled = true,
                onClick =
                    if (unrestricted) {
                        null
                    } else {
                        (
                            {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
                            }
                        )
                    },
            ) {
                Value(
                    if (unrestricted) "Unrestricted" else "Restricted",
                    opens = !unrestricted,
                    color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Setting(
                Icons.Alarm,
                "Exact timing",
                onClick =
                    if (exact) {
                        null
                    } else {
                        (
                            {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
                            }
                        )
                    },
            ) { Value(if (exact) "On" else "Off", opens = !exact) }
        }

        Section("More") {
            Setting(Icons.Tile, "Quick Settings tile", onClick = {
                context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(context, NextTile::class.java),
                    label,
                    Icon.createWithResource(context, R.drawable.ic_glyph),
                    context.mainExecutor,
                ) {}
            }) { Value("Add") }
            Setting(Icons.Help, "Help", onClick = onHelp) { Value("") }
            Setting(Icons.Info, "Badal $version") { Value("Offline", opens = false) }
        }
    }
}
