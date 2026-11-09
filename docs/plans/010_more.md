# 010 — One picture, Blur and Grayscale, followed folders, backups, a widget

Status: built (2026-11-09). Tested on the Android 17 emulator.

Five smaller additions Vivek asked for together. Each fits in what was
already there.

## One picture as a screen's source

**`Rotation.picture`**, set from the album picker: the picture button beside
an album opens its pictures, and choosing one makes it the screen's only
picture. Album and picture ids both come from `nextId`, so they never clash.
A source id can name either, and `State.pool` turns it into the pictures to
show. By the hour and modes still put other albums up, and the picture comes
back when they end.

Nothing changes while one picture is all a screen may show (`Plan.rotates`).
So it sets no interval alarm, and the lock watcher doesn't run for it.
Removing the picture clears it, as deleting an album clears `albumId`.

Rejected: a "Keep this one" item in a picture's menu that froze rotation. It
was a second way to say the same thing, and harder to undo.

## Blur and Grayscale

Two sliders in Look, beside Darken (`Rotation.blur`, `gray`). Together with
Darken and Shade they make up a screen's `Finish`, which is all a redraw
depends on besides the frame. The engine, the live wallpaper and the previews
key on it, so a new effect is one field there.

- **Grayscale** is a saturation colour matrix on the paint the picture is
  drawn with.
- **Blur** shrinks the finished picture, box-blurs it six times, and scales
  it back up. The more blur, the smaller it is made (`Images.blurScale`).
  - It shrinks by halving. One large bilinear step samples a few pixels, and
    a fine pattern came out as blotches that blurred into squares.
  - Six passes, not the gaps' three: scaled up this far, three left a faint
    lattice.
- **The previews blur to match** with a `BlurEffect` of the same radius for
  their size (`Images.blurRadius`).

Rejected: `RenderEffect` blur off-screen through a `HardwareRenderer`. It
gives a true Gaussian, but needs an `ImageReader` round trip for a bitmap
drawn once per change.

## Followed folders

Adding a folder asks "Follow this folder?". Followed (`Album.folder`, a tree URI
with a lasting read grant):

- its new pictures join the album;
- ones deleted from it leave;
- one removed from the album by hand stays out, because the album remembers
  every document it has seen and what it became (`Album.docs`).

**When:** each time the app comes up, and once a day otherwise, piggybacking on
a screen-off or alarm that happens anyway (`Library.rescan(daily)`). No extra
alarm, and no foreground work of its own.

**An unreadable folder changes nothing**, rather than reading as empty and
emptying the album. A lapsed grant or a restore on another phone causes that.

Rejected: watching the folder live (`ContentObserver`). It needs a running
process, for something a once-a-day look does well enough.

## Backup and restore

Android's backup is off (plan 001), so a new phone started empty. Settings
now has Save a backup and Restore a backup (`Backup`).

- **Save** zips `state.json` and the `img`, `thumb` and `design` folders, at
  the fastest compression level: photos and PNGs are compressed already. It
  goes wherever the user picks with the system's file picker, so no
  permission is needed.
- **Restore** replaces everything, after a confirmation. It unpacks beside
  the live files and checks `state.json` first. Then it moves each folder
  in, saves the state and redraws both screens.
- **Safety:** only the names a backup can hold are unpacked, so a zip can't
  write elsewhere.
- **Other screen sizes:** designs were drawn for the old phone's screen and
  are cropped to the new one like any picture.

Rejected: Android's backup with only `state.json`. It brings back settings
that name pictures the new phone doesn't have.

## Home-screen widget

`NextWidget` shows the picture on the home screen (or on the lock screen when
only that one changes), with Previous and Next. It is RemoteViews: Glance would
be a dependency for one layout.

- **No update period:** the engine redraws it after every change it saves.
- **Half-size thumbnail:** a widget's bitmap travels to the launcher in one
  binder call, which holds 1 MB.
- **Settings → Home screen widget → Add** asks the launcher to pin it, beside
  the Quick Settings tile's Add.
