# 004 — Crossfade on the home screen

Status: built (2026-11-08). Tested on the Android 17 emulator.

## Problem

A still wallpaper swaps in one frame. That is fine on lock, which changes
while the screen is off, but a change on a timer, at a set hour or from Next
can happen while the home screen is in view, and it jumps. Plan 001 rejected a
live wallpaper as the default and kept it as "worth trying later as an
optional home-screen mode"; a crossfade is the reason to try it.

## Decisions

**An opt-in live wallpaper for the home screen only** (`LiveWallpaper`, the
Crossfade switch under the home screen's Look). The switch opens the system's
preview of Badal's live wallpaper, where the user sets it; Android allows no
other way for an app to become the live wallpaper. Turning it off sets the
home picture as a still wallpaper, which replaces the live one. Whether it is
on is the system's to say (`Engine.live`, read from `WallpaperManager`), not a
saved setting: the user can pick another wallpaper elsewhere at any time, and a
saved flag would then be wrong.

**The engine still picks; the live wallpaper only draws.** While Badal is the
live wallpaper, `Engine.commit` skips `setBitmap` for the home screen, which
would replace it, and the live wallpaper follows `homeNow`, the home
screen's frame and Darken from the store, drawn by the same `Images.render`.
So every trigger, Previous, Next and time ranges work unchanged, and there is
no second set of rules to keep in step. Home changes also stop showing the
spinner: there is no `setBitmap` to wait for.

**Fade only when someone can see it.** A change while the home screen is
visible fades over 0.9 s, easing out. A change while it is hidden (screen off,
an app in front) swaps at once, and a fade under way finishes at once if the
screen goes off: replaying it on the next look would be the visible swap 001
avoided. A new frame or Darken setting on the same picture redraws in place.
After several quick Nexts only the last picture is drawn. The picture is
uploaded to the GPU once (a hardware bitmap), so the fade costs no CPU, and
nothing draws between changes.

**Colours are reported** (`onComputeColors`, from the full picture before the
GPU copy), so the system palette and themed icons follow the picture as they
do with a still wallpaper. On the emulator the palette followed each change.

**The lock screen is untouched.** If the user picks "Home screen and lock
screen" in the system's dialog, the live wallpaper shows the home picture on
both, and the lock screen's first `setBitmap` (when lock rotation is on) splits
it off as a still picture again; the emulator showed exactly that.

## Unverified on Samsung

- Whether One UI offers third-party live wallpapers for the home screen
  through `ACTION_CHANGE_LIVE_WALLPAPER`, and what it does to the lock screen.
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
- **Other transitions** (slide, zoom, a setting for the duration): one fade,
  fixed, is the change asked for; settings for it would explain more than
  they give.
- **Parallax with the home screen pages:** One UI's still wallpapers don't
  scroll either, and a picture framed by hand (002) would move off its frame.
