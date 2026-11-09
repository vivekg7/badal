# Badal

Rotating wallpapers for Android 15+, with separate albums for the home screen
and the lock screen. _Badal_ (बदल) means "change".

- **A separate album for each screen**, or the lock screen copies the home
  screen.
- **Change each time the screen locks**, so every unlock shows a new picture.
- **Change every _n_ minutes**: presets from 15 minutes to a day, or any
  number.
- **Albums by time of day**, e.g. dark pictures from 22:00 to 06:00, every day
  or only on chosen days.
- **Shuffle** (no repeats until every picture has shown) **or in order**, with
  Previous and Next. **Drag to reorder** an album; **star favourites** to show
  them twice as often in Shuffle.
- **Different pictures on each screen**, when both use the same album.
- **Fill or fit**: crop to fill the screen, or show the whole picture over a
  blurred copy. **Darken** up to 70% so the clock and icons stay readable, or
  on the lock screen **shade only behind the clock**
  ([plan 003](docs/plans/003_shade_order_favourites_days.md)).
- **Frame each picture for each screen**: drag and pinch it into place, with
  a guide showing where the lock-screen clock and the dock sit
  ([plan 002](docs/plans/002_framing.md)).
- **Crossfade** on the home screen, the lock screen or both, if you like:
  Badal becomes that screen's live wallpaper and fades to each new picture
  ([plan 004](docs/plans/004_crossfade.md)).
- **Make your own abstract wallpapers**: plain colours, gradients and mesh
  gradients, with 56 patterns over them (dots in grids, rings, rays
  and spirals; stripes, waves, contours; tiles, Bauhaus, marble…). Start from
  any of 280 premade designs, change colours, size and weight, and edit
  them again later ([plan 005](docs/plans/005_designs.md)).
- **Designs in their own tab**, a tap from anywhere; add any to an album, or
  **save it to your gallery**, as you can any picture
  ([plan 008](docs/plans/008_designs_tab_export.md)).
- **Modes**: other albums while Do Not Disturb, the dark theme or charging is
  on, or switched by hand. Samsung's Modes and Routines can switch them
  through Badal's shortcuts ([plan 009](docs/plans/009_modes.md)).
- **Just one picture** on a screen, if you like. **Blur** and **Grayscale**
  beside Darken. **Albums that follow a folder**, so new photos in it join.
  **Backup and restore** everything to one zip file. A **home-screen widget**
  with Previous and Next ([plan 010](docs/plans/010_more.md)).
- **Pause on battery saver**, if you like: no changes on lock or on a timer
  while it's on.
- **Quick Settings tile** and **launcher shortcuts** for Next.
- **One line per setting**, with a Help page that explains each
  ([plan 006](docs/plans/006_less_text.md)). Light on memory and battery
  while it waits in the background ([plan 007](docs/plans/007_resources.md)).
- Pictures are copied into the app, so gallery clean-ups don't break an
  album; designs are drawn into it at the screen's size. No internet
  permission, and no new permission for any of the above.

Why it is built this way, and what was rejected: [docs/plans/](docs/plans/).

## Samsung setup

For **change on lock**, and modes that come on by themselves, to keep
working, Badal must be allowed to run:

1. Badal → Settings → Battery → allow (this sets it to Unrestricted).
2. If it still stops after a day: Settings → Battery → Background usage
   limits → Never sleeping apps → add Badal.
3. Turn off **Dynamic lock screen** (Lock screen → Wallpaper services), or it
   replaces Badal's lock-screen picture.

The lock-screen clock's place and style are One UI's to set (long-press the
lock screen); no app can change them. Frame pictures around it instead.

## Build

Needs the Android SDK (`local.properties` → `sdk.dir`) and a JDK; on a Mac
with only Android Studio, the Makefile uses Studio's bundled one.

```sh
make check                              # ktlint, Android lint, unit tests
ANDROID_SERIAL=emulator-5554 make install
make release                            # minified APK, signed if the key is set up
make archive                            # build, verify, keep it in local/releases/
```

### Signing

`assembleRelease` signs the APK when `local/keystore.properties` exists:

```properties
storeFile=local/badal-release.jks
storePassword=…
keyAlias=badal
keyPassword=…
```

`local/` is gitignored, so neither the key nor its password reaches the
repository. Without the file, release still builds, unsigned.

**The key cannot be recovered.** Lose it and no installed copy of Badal can be
updated, because Android refuses an update signed by a different key. Keep a
backup off this machine.

### Archiving a release

`make archive` (`scripts/archive-apk.sh`) builds the release APK and copies it
into `local/releases/v<version>/` as `badal-v<version>.apk`, with a `.sha256`
and the R8 `mapping.txt` that turns a minified stack trace from that build back
into names. The version is read from the built APK, so the name cannot disagree
with what shipped.

It is not wired into `assembleRelease`, so a build made while working cannot
overwrite an archived release. The script also refuses an unsigned APK,
archives a dirty tree as `v<version>-dirty-g<commit>/`, and never replaces an
existing archive without `--force`. Bump `versionCode` and `versionName` in
`app/build.gradle.kts` for each release.

## Credits

Icons are path data from Google's
[Material Icons](https://github.com/google/material-design-icons)
(Apache 2.0).
