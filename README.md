# Badal

Rotating wallpapers for Android 15+, with separate albums for the home screen
and the lock screen. _Badal_ (बदल) means "change".

- **A separate album for each screen**, or the lock screen copies the home
  screen.
- **Change each time the screen locks**, so every unlock shows a new picture.
- **Change every _n_ minutes**: presets from 15 minutes to a day, or any
  number.
- **Albums by time of day**, e.g. dark pictures from 22:00 to 06:00.
- **Shuffle** (no repeats until every picture has shown) **or in order**, with
  Previous and Next.
- **Different pictures on each screen**, when both use the same album.
- **Fill or fit**: crop to fill the screen, or show the whole picture over a
  blurred copy. **Darken** up to 70% so the clock and icons stay readable.
- **Quick Settings tile** and **launcher shortcuts** for Next.
- Pictures are copied into the app, so gallery clean-ups don't break an
  album. No internet permission.

Why it is built this way, and what was rejected:
[docs/plans/001_wallpaper_rotator.md](docs/plans/001_wallpaper_rotator.md).

## Samsung setup

For **change on lock** to keep working, Badal must be allowed to run:

1. Badal → Settings → Battery → allow (this sets it to Unrestricted).
2. If it still stops after a day: Settings → Battery → Background usage
   limits → Never sleeping apps → add Badal.
3. Turn off **Dynamic lock screen** (Lock screen → Wallpaper services), or it
   replaces Badal's lock-screen picture.

## Build

Needs the Android SDK (`local.properties` → `sdk.dir`) and a JDK; on a Mac
with only Android Studio, the Makefile uses Studio's bundled one.

```sh
make check                              # ktlint, Android lint, unit tests
ANDROID_SERIAL=emulator-5554 make install
make release                            # minified APK, unsigned
```

## Credits

Icons are path data from Google's
[Material Icons](https://github.com/google/material-design-icons)
(Apache 2.0).
