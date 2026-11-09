# 004 — Crossfade on the home and lock screens

Status: built for the home screen in v0.4.0 (2026-11-08); tested on the
Android 17 emulator, and on a Galaxy A35 (One UI, Android 17), where it fades
on the home screen. The lock screen followed in v0.6.0 (2026-11-09); tested on
the emulator and on the A35, where it fades while the lock screen is in view.

## Problem

A still wallpaper swaps in one frame. That is fine on lock, which changes
while the screen is off, but a change on a timer, at a set hour or from Next
can happen while the home screen is in view, and it jumps. Plan 001 rejected a
live wallpaper as the default and kept it as "worth trying later as an
optional home-screen mode"; a crossfade is the reason to try it.

## Decisions

**An opt-in live wallpaper, for each screen on its own** (`LiveWallpaper`,
the Crossfade switch under each screen's Look). The switch opens the system's
preview of Badal's live wallpaper, where the user taps Set wallpaper and picks
the screen; Android allows no other way for an app to become the live
wallpaper. Turning it off sets that screen's picture as a still wallpaper,
which replaces the live one. Whether it is on is the system's to say
(`Engine.live`, read per screen from `WallpaperManager.getWallpaperInfo`), not
a saved setting: the user can pick another wallpaper elsewhere at any time,
and a saved flag would then be wrong.

**One engine per screen.** Since Android 14 a live wallpaper can be set on
the lock screen alone, and the system then runs a separate `Engine` for it;
`getWallpaperFlags` says which screen an engine draws, and
`onWallpaperFlagsChanged` when that changes. An engine for the home screen
follows `homeNow`, one for the lock screen alone follows `lockNow`, each with
its screen's frame, Darken and (lock) Shade.

**"Home screen and lock screen" counts as home only.** One engine then draws
both screens, so it can't show two pictures. The system reports no live
wallpaper of the lock screen's own in that case (checked on the emulator), so
the engine keeps setting the lock screen's still picture when Badal changes
it, which splits it off, as before.

**The engine still picks; the live wallpaper only draws.** While Badal is a
screen's live wallpaper, `Engine.commit` skips `setBitmap` for that screen,
which would replace it, and the live wallpaper follows the store, drawn by the
same `Images.render`. So every trigger, Previous, Next and time ranges work
unchanged, and there is no second set of rules to keep in step. Changes there
also stop showing the spinner: there is no `setBitmap` to wait for. That also
saves work: a `setBitmap` takes about a second and has the system write a
full-size image each time.

**Fade only when someone can see it.** A change while the screen is visible
fades over 0.9 s, easing out. A change while it is hidden (screen off, an app
in front) swaps at once, and a fade under way finishes at once if the screen
goes off: replaying it on the next look would be the visible swap 001
avoided. On the lock screen that means a change on lock is in place before
you wake the phone, as with a still wallpaper; what fades there is a change on
a timer, at a set hour or from the Next tile while it is in view. A new frame or Darken setting on the same picture redraws in place.
After several quick Nexts only the last picture is drawn. The picture is
uploaded to the GPU once (a hardware bitmap), so the fade costs no CPU, and
nothing draws between changes.

**Colours are reported** (`onComputeColors`, from the full picture before the
GPU copy), so the system palette and themed icons follow the picture as they
do with a still wallpaper. On the emulator the palette followed each change.

## On Samsung

- One UI opens its preview from `ACTION_CHANGE_LIVE_WALLPAPER` and takes
  Badal as the home screen's live wallpaper; the fade shows as on the
  emulator. Its Set wallpaper dialog also offers "Lock screen" alone, and
  with Badal set there the lock screen fades too (A35, v0.6.0).
- Samsung's wallpaper dimming in dark mode applies to still wallpapers only
  (001); with Crossfade on, use Darken instead.

## Rejected

- **Crossfade by setting intermediate still wallpapers:** each `setBitmap`
  takes about a second and rewrites a full-size file, so a fade would take
  many seconds and wear the storage.
- **A live wallpaper that also watches for screen-off**, replacing
  `LockWatcher`: the system keeps a live wallpaper's process bound, so it
  could. Not yet: the lock screen still needs the watcher when Crossfade is
  off, and two ways to hear one broadcast is a bug waiting to happen. Worth
  doing if the watcher keeps being killed on Samsung.
- **Fading on wake** after a change on lock: keep the old picture until the
  lock screen shows, then fade to the new one. Every change would be seen
  happening, but each wake would start with a moving picture, which is the
  visible swap 001 avoided, only slower. Asked and declined.
- **Other transitions** (slide, zoom, a setting for the duration): one fade,
  fixed, is the change asked for; settings for it would explain more than
  they give.
- **Parallax with the home screen pages:** One UI's still wallpapers don't
  scroll either, and a picture framed by hand (002) would move off its frame.
