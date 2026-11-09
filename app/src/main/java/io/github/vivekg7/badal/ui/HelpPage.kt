package io.github.vivekg7.badal.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * What every setting means, so the settings themselves can be one short line
 * each (plan 006). Questions, grouped the way the tabs are; tap one to open it.
 */
private val help =
    listOf(
        "Home and lock" to
            listOf(
                "What does the switch at the top do?" to
                    "It decides whether Badal changes that screen at all. Off, Badal leaves the wallpaper you have alone.",
                "What are Previous and Next?" to
                    "Next puts the next picture up now. Previous goes back one, and Next then returns to where you were. " +
                    "Tap the picture, or the crop button, to frame it.",
                "What does Same as home do?" to
                    "The lock screen shows the home screen's picture and changes with it. Its own Darken and Shade still apply.",
                "Shuffle or In order?" to
                    "Shuffle shows every picture once, in a random order, before any repeats. Favourites come up twice a round. " +
                    "In order plays the album as it is arranged (reorder it from the album's menu).",
                "Can a screen show just one picture?" to
                    "Yes. Tap Album, then the picture button beside an album, and choose one. It stays until By the hour or " +
                    "a mode puts another album up.",
            ),
        "When it changes" to
            listOf(
                "On each lock" to
                    "A new picture each time the screen turns off, so the next wake shows it. This needs Badal running in the " +
                    "background: see Battery below.",
                "Every" to
                    "Also change after this long. A change that falls due while the phone sleeps waits for you to wake it.",
                "By the hour" to
                    "Use another album at set hours, such as dark pictures at night, on the days you pick. Outside them the " +
                    "Album above is used. A range may run past midnight.",
                "Why didn't it change on time?" to
                    "Without Exact timing (Settings) Android may run a change a few minutes late. With Pause on battery saver, " +
                    "changes on lock and on a timer wait while battery saver is on; By the hour still switches.",
            ),
        "Look" to
            listOf(
                "Fit: Fill or Whole?" to
                    "Fill crops the picture to cover the screen. Whole shows all of it over a blurred copy. A picture you framed " +
                    "by hand keeps its frame.",
                "How do I frame a picture?" to
                    "Tap it on the Home or Lock tab, or choose Frame in its album. Drag to move, pinch to zoom, double-tap to " +
                    "switch between Fill and Whole. Each screen keeps its own frame.",
                "Darken and Shade" to
                    "Darken dims the whole picture, so a busy one doesn't hide icons or the clock. Shade, on the lock screen, " +
                    "darkens only the top and fades out below the clock.",
                "Blur and Grayscale" to
                    "Blur softens the whole picture, so the clock and icons stand out from it. Grayscale takes out its " +
                    "colour, some or all of it.",
                "What is Crossfade?" to
                    "The picture fades into the next instead of switching, when a change happens while you're looking. Badal " +
                    "becomes that screen's live wallpaper: in the preview that opens, tap Set wallpaper and choose the screen " +
                    "you turned it on for. Turn it off to go back to a still wallpaper.",
                "Crossfade is on, but Samsung's dark mode doesn't dim the wallpaper" to
                    "One UI dims only still wallpapers. Use Darken instead.",
            ),
        "Designs" to
            listOf(
                "Making a wallpaper" to
                    "The Designs tab has hundreds of ready designs. Tap one to change its colours, pattern and finish, then " +
                    "Add to album. Long-press several to add them as they are. In the designer, drag on the preview to move a " +
                    "pattern that grows from a point, or a radial or sweep fill. Edit design, in a picture's menu, changes it later.",
                "Saving to the gallery" to
                    "The download button in the designer saves the design to your gallery, in Pictures/Badal, without adding " +
                    "it to an album. In an album, Save to gallery is in a picture's menu, or select several. Photos are " +
                    "saved as their original file, designs as PNG.",
            ),
        "Modes" to
            listOf(
                "What is a mode?" to
                    "Other albums for a while, for one screen or both: calm pictures at work, dark ones in bed. A mode on " +
                    "By hand stays on until you switch it off, here or with its shortcut. The others are on while Do Not " +
                    "Disturb, the dark theme or charging is. A mode by hand wins over those, and any mode over By the hour.",
                "Can Samsung's modes switch Badal's?" to
                    "Android doesn't tell other apps which of its modes (Work, Sleep, Driving…) is on. But each mode on By " +
                    "hand gets a shortcut (long-press Badal's icon), and Modes and Routines can run one: add the action " +
                    "\"Open an app or do an app action\", choose Badal and the mode, and add Modes off to what happens when it " +
                    "ends. Sleep and similar modes turn on Do Not Disturb, which a Do Not Disturb mode follows by itself.",
            ),
        "Albums" to
            listOf(
                "Are my pictures copied?" to
                    "Yes. Badal keeps its own copy of each, so deleting one from the gallery doesn't break a rotation. " +
                    "Removing a picture from Badal never touches the original.",
                "How do I add pictures?" to
                    "In an album: + adds from the gallery, the folder button adds every picture in a folder, and the palette " +
                    "makes a wallpaper and adds it here.",
                "Following a folder" to
                    "Adding a folder asks whether to follow it. Followed, its new pictures join the album each time Badal " +
                    "opens and once a day, and ones deleted from it leave. Removing one from the album yourself keeps it " +
                    "out. The circling arrows in the album's top bar stop following.",
                "Selecting, favourites, reordering" to
                    "Tap a picture to show it now, star, frame or save it, or edit its design. Long-press to select several, then " +
                    "star, save or remove them together. The up-down arrows in the top bar reorder the album: hold a picture " +
                    "and drag it.",
            ),
        "Settings" to
            listOf(
                "Different on each screen" to
                    "When home and lock use the same album, they never show the same picture at once.",
                "Battery: Restricted or Unrestricted?" to
                    "Changing on each lock needs Badal to wait in the background for the screen to turn off, and so do modes " +
                    "that come on by themselves. Samsung stops restricted apps, and those changes stop with them. Tap it, " +
                    "open the app's battery setting there and choose Unrestricted. Nothing else needs this.",
                "Shortcuts and the widget" to
                    "The Quick Settings tile puts the next picture on every screen Badal changes. Long-press the app's icon " +
                    "for next home, next lock and the modes. The widget (Settings, or long-press the home screen and " +
                    "choose Widgets) shows the picture now, with Previous and Next.",
                "Backups" to
                    "Save a backup puts every album, picture and setting into one zip file, wherever you choose. Restore a " +
                    "backup replaces everything in Badal with what it holds, on this phone or a new one. Keep it somewhere " +
                    "private: it has your pictures in it.",
                "Privacy" to
                    "Badal has no internet access. Your pictures never leave the phone, and Android's backup is off for it: " +
                    "Save a backup instead.",
            ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpPage(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Back, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            for ((group, questions) in help) {
                item(group) {
                    Text(
                        group,
                        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(questions, key = { it.first }) { (q, a) ->
                    val shown = open == q
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = if (shown) null else q }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .animateContentSize(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(
                                Icons.Expand,
                                null,
                                Modifier.rotate(if (shown) 180f else 0f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (shown) {
                            Text(
                                a,
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
