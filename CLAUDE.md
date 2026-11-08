# Project rules

What Badal is and why it is built this way:
[`docs/plans/001_wallpaper_rotator.md`](docs/plans/001_wallpaper_rotator.md).
Read the plans before proposing a change; rejected ideas are listed there.

## Product rules

- **Offline.** No `INTERNET` permission, and no feature that needs one.
- **Rotation rules live in `Plan` (Model.kt) and stay pure**: no Android
  types, so `PlanTest` covers them on the JVM. A new trigger or order is a
  change to `Plan` plus a test, then wiring in `Engine`.
- **Framing geometry lives in `Framing` (Frame.kt) and stays pure.** The
  renderer and every in-app preview place pictures through it, so a preview
  can't drift from the wallpaper (plan 002).
- **Set home and lock with separate `setBitmap` calls**, never both flags at
  once (plan 001). While Badal is the live wallpaper (`Engine.live`), never
  set the home screen at all: a still wallpaper replaces it (plan 004).
- **The engine writes only `homeNow` / `lockNow`**; settings and albums are
  changed through `Engine.edit` and `Library`, each of which ends in
  `Engine.settle()` so the wallpapers, the alarm and the lock watcher follow.
- **`State` is saved as JSON**: a new field needs a default, so old files
  still load. Never rename or remove a field without reading the old name.

## How to work here

- Anything larger than a bug fix gets a numbered plan in `docs/plans/`: the
  problem, the decision and why, and what was rejected.
- `make fmt` before committing; `make check` must pass.
- Test on a device (`ANDROID_SERIAL=… make install`), not only in unit tests:
  screen-off, alarms and wallpaper setting only show their bugs there. Check
  `adb shell dumpsys wallpaper`, `dumpsys alarm` and
  `dumpsys activity services <package>`.
- Keep it compact: no wrappers that only forward, no abstraction with one
  caller. Add a dependency only when twenty lines of our own will not do.
