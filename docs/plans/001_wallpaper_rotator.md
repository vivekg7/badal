# 001 — A wallpaper rotator for home and lock screens

Status: built (v0.1.0, 2026-11-08). Tested on the Android 17 emulator, and on a
Galaxy A35 (One UI, Android 17): there a `FLAG_LOCK` bitmap replaces Samsung's
own layered lock-screen wallpaper.

## Problem

Android lets you pick one home wallpaper and one lock wallpaper. Vivek wants
both to rotate through his own pictures, with a separate list for each screen:

- change each time the phone is locked, so every unlock shows a new picture;
- change on a timer (custom intervals);
- shuffle or play in order;
- a different album for set hours of the day;
- room to add more ways to customise later.

The target is Android 15+ only, mostly Samsung Galaxy A-series phones
(One UI 7+).

## Decisions

**Native Kotlin and Jetpack Compose, minSdk 35.** One activity, no DI
framework, no navigation library. The build copies galla's (AGP 9, version
catalog, ktlint from its own jar), so both build from one Gradle cache.
Android 15+ only means no compatibility branches.

**Static wallpapers through `WallpaperManager.setBitmap`, one screen at a
time.** Badal renders each picture to the panel's exact size (crop, or fit
over a blurred copy, then darken) and sets `FLAG_SYSTEM` and `FLAG_LOCK` in
separate calls. Never both flags in one call: that makes the lock screen
follow the home screen from then on, and the next home-only change would
overwrite the lock screen too.

**Change on lock = change when the screen turns off.** The picture is ready
before the next wake, so neither screen visibly swaps. `ACTION_SCREEN_OFF` is
delivered only to receivers registered by a running process, so
`LockWatcher` is a foreground service (type `specialUse`) that runs only while
a screen uses this trigger. Badal never asks for notification permission, so
on Android 13+ the service's notification is hidden. Samsung stops
battery-restricted apps, so the Settings tab asks to be unrestricted.

**Timers and time ranges use one AlarmManager alarm**, recalculated after
every change (`Plan.nextAlarm`). Interval changes use a non-waking `RTC`
alarm: a wallpaper changing in a dark pocket helps nobody, so it waits for the
next wake. A time range's start or end uses `RTC_WAKEUP`, so the night album is
already in place when the screen comes on. Exact if the user grants it,
otherwise inexact: a few minutes late is fine for a wallpaper. Reboot, app
update, clock and time-zone changes all reset the alarm and the watcher
(`RestartReceiver`).

**Shuffle never repeats until every picture has shown** (a "bag" per screen),
and a new round never opens with the picture that closed the last one. With
"different pictures on each screen" on, home and lock steer clear of each
other when the album has a choice. Previous walks back through the last 30
pictures, and the one you left is put first in line, so Next returns to it.

**Time ranges** ("22:00–06:00 → Night") override the screen's album while
they hold; outside them the screen's own album applies. A range may run past
midnight. When the active album changes, the next picture comes from the new
album at once.

**Pictures are copied into app storage** (`files/img/<id>`, plus a 360 px
thumbnail). A picture deleted from the gallery, or a URI grant lost after a
reboot, can't break a rotation. Folders are copied once, not kept in sync.

**State is one JSON file** (`files/state.json`, kotlinx.serialization),
rewritten whole on each change behind a mutex. A few hundred pictures make a
few kilobytes, everything runs in one process, and the pure rules in `Plan`
are unit-tested without Android. The engine writes back only the two
`Progress` values, so it can't overwrite a settings change the user makes
while a wallpaper is being set.

**Offline and private.** No `INTERNET` permission. Backup is off:
Android's backup would upload every picture copy.

**Shortcuts:** a Quick Settings tile ("Next wallpaper") and long-press
launcher shortcuts for next home and next lock. They are dynamic shortcuts,
because a static `shortcuts.xml` names the package and the debug build has a
different one.

## Rejected

- **A live wallpaper (`WallpaperService`)** would draw the home screen itself,
  with instant swaps and crossfades and no foreground service. Rejected for
  now: One UI's lock screen doesn't take third-party live wallpapers, and
  Samsung's home-screen wallpaper options (dimming, dark mode) work on static
  images only. It's worth trying later as an optional home-screen mode.
- **WorkManager** for intervals: its minimum period is 15 minutes, and it adds
  a dependency where one alarm does.
- **Room**: no queries that a list in memory can't answer. It would bring KSP
  and migrations for a few kilobytes of state.
- **Coil or Glide** for thumbnails: thumbnails are made once at import time,
  and a 30-line LRU cache shows them.
- **Keeping URIs instead of copies:** photo-picker grants can lapse, and
  gallery deletions would leave holes.

## Known limits, worth checking on the phone

- With "Colour palette" following the wallpaper, each change can re-tint the
  system UI. Pixel does this on its own; One UI only when its palette is set
  to follow the wallpaper.
- Samsung's "Dynamic lock screen" or a lock-screen wallpaper service can take
  the lock screen back from Badal. Turn those off.
- If the phone kills the watcher anyway (Device care, Sleeping apps), add
  Badal to "Never sleeping apps".

Per-picture framing was added by [002](002_framing.md); the clock shade and
reordering by
[003](003_shade_order_favourites_days.md).

## Ideas for later

Live-folder albums (re-scan a folder), a grayscale or blur effect, a crossfade (needs the live wallpaper), a
"change only while charging" or "not on battery saver" rule, a day-of-week
schedule, and weighted favourites.
