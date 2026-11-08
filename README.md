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
- **Frame each picture for each screen**: drag and pinch it into place, with
  a guide showing where the lock-screen clock and the dock sit
  ([plan 002](docs/plans/002_framing.md)).
- **Quick Settings tile** and **launcher shortcuts** for Next.
- Pictures are copied into the app, so gallery clean-ups don't break an
  album. No internet permission.

Why it is built this way, and what was rejected: [docs/plans/](docs/plans/).

## Samsung setup

For **change on lock** to keep working, Badal must be allowed to run:

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
