package io.github.vivekg7.badal.ui

import android.app.AlarmManager
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vivekg7.badal.Cue
import io.github.vivekg7.badal.LockWatcher
import io.github.vivekg7.badal.Mode
import io.github.vivekg7.badal.NextTile
import io.github.vivekg7.badal.NextWidget
import io.github.vivekg7.badal.R
import io.github.vivekg7.badal.Screen
import io.github.vivekg7.badal.ShortcutActivity
import io.github.vivekg7.badal.State
import io.github.vivekg7.badal.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    var editing by remember { mutableStateOf<Mode?>(null) }
    var restoring by remember { mutableStateOf<Uri?>(null) }
    // Which mode is in force: read again with each change and each return to the app.
    val active = remember(resumed, state) { state.mode(app.engine.sense())?.id }
    val saving =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { to ->
            if (to == null) return@rememberLauncherForActivityResult
            Toast.makeText(context, "Saving the backup…", Toast.LENGTH_SHORT).show()
            app.scope.launch {
                val ok = app.backup.save(to)
                withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show() }
            }
        }
    val opening = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoring = it }

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

        // Other albums while something holds (plan 009): by hand, or on a cue Badal can see.
        Section("Modes") {
            state.modes.forEach { m ->
                Setting(Icons.Modes, m.name, onClick = { editing = m }) {
                    if (m.cue == Cue.HAND) {
                        Switch(state.handMode == m.id, { on -> app.scope.launch { app.engine.mode(if (on) m.id else null) } })
                    } else {
                        Value(
                            cueLabel(m.cue),
                            color = if (active == m.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Setting(Icons.Add, "Add a mode", onClick = { editing = Mode(0, "") })
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
            Setting(Icons.Image, "Home screen widget", onClick = {
                context.getSystemService(AppWidgetManager::class.java).requestPinAppWidget(ComponentName(context, NextWidget::class.java), null, null)
            }) { Value("Add") }
            Setting(Icons.Save, "Save a backup", onClick = { saving.launch("badal-${LocalDate.now()}.zip") }) { Value("") }
            Setting(Icons.Restore, "Restore a backup", onClick = { opening.launch(arrayOf("application/zip")) }) { Value("") }
            Setting(Icons.Help, "Help", onClick = onHelp) { Value("") }
            Setting(Icons.Info, "Badal $version") { Value("Offline", opens = false) }
        }
    }

    editing?.let { m ->
        ModeDialog(m, state, onDismiss = { editing = null }, onDone = { done ->
            editing = null
            app.scope.launch {
                val st =
                    app.store.update { st ->
                        when {
                            done == null -> {
                                st.copy(modes = st.modes.filter { it.id != m.id }, handMode = st.handMode.takeIf { it != m.id })
                            }

                            m.id == 0L -> {
                                st.copy(modes = st.modes + done.copy(id = st.nextId), nextId = st.nextId + 1)
                            }

                            else -> {
                                st.copy(
                                    modes = st.modes.map { if (it.id == m.id) done else it },
                                    // No longer By hand: it follows its cue now, not the switch.
                                    handMode = st.handMode.takeIf { it != m.id || done.cue == Cue.HAND },
                                )
                            }
                        }
                    }
                // A mode switched by hand gets its own launcher shortcut, for Samsung's routines too.
                ShortcutActivity.publish(context, st)
                app.engine.settle()
            }
        })
    }
    restoring?.let { from ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Replace everything?") },
            text = { Text("Every album, picture and setting in Badal is replaced by the backup's.") },
            confirmButton = {
                TextButton({
                    restoring = null
                    app.scope.launch {
                        val ok = app.backup.restore(from)
                        withContext(Dispatchers.Main) {
                            // Thumbnails are kept by id, and the backup's ids name other pictures.
                            dropCaches()
                            ShortcutActivity.publish(context, app.store.state.value)
                            Toast.makeText(context, if (ok) "Restored" else "That isn't a Badal backup", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton({ restoring = null }) { Text("Cancel") } },
        )
    }
}

private fun cueLabel(c: Cue) =
    when (c) {
        Cue.HAND -> "By hand"
        Cue.QUIET -> "Do Not Disturb"
        Cue.DARK -> "Dark theme"
        Cue.CHARGING -> "Charging"
    }

/** A mode's name, what switches it on, and the album for each screen; [onDone] with null deletes it. */
@Composable
private fun ModeDialog(
    start: Mode,
    state: State,
    onDismiss: () -> Unit,
    onDone: (Mode?) -> Unit,
) {
    var m by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<Screen?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id == 0L) "New mode" else "Mode") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(m.name, { m = m.copy(name = it) }, singleLine = true, label = { Text("Name") })
                Text("On", Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Cue.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().selectable(m.cue == c) { m = m.copy(cue = c) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(m.cue == c, null)
                        Text(cueLabel(c), Modifier.padding(start = 12.dp))
                    }
                }
                Text(
                    "Albums",
                    Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                for (s in Screen.entries) {
                    val id = m.album(s)
                    Row(Modifier.fillMaxWidth().clickable { picking = s }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s == Screen.HOME) Icons.Home else Icons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.album(id)?.name ?: "Unchanged", Modifier.padding(start = 12.dp).weight(1f))
                        if (id != null) {
                            IconButton({ m = if (s == Screen.HOME) m.copy(home = null) else m.copy(lock = null) }) { Icon(Icons.Close, "Leave unchanged") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ onDone(m.copy(name = m.name.trim())) }, enabled = m.name.isNotBlank() && (m.home != null || m.lock != null)) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (start.id != 0L) TextButton({ onDone(null) }) { Text("Delete") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
    picking?.let { s ->
        AlbumPicker(state.albums, onPick = { a ->
            m = if (s == Screen.HOME) m.copy(home = a.id) else m.copy(lock = a.id)
            picking = null
        }, onDismiss = { picking = null })
    }
}
