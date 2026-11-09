# 007 — Less memory, less work, faster start

Status: built (v0.6.0, 2026-11-09). Tested on the Android 17 emulator. Not
measured against v0.5.0.

## Problem

Badal runs for days in the background. The lock watcher keeps its process up,
and so does the live wallpaper while Crossfade is on. So what it holds and
does when no one is looking matters more than how fast a page draws. Vivek
asked for it to be as light on CPU, memory and battery as possible, and fast.

Much was already lean: no internet, one alarm at a time, interval changes
that don't wake the phone, the watcher running only while a screen changes
on lock, and thumbnails made once at import. What follows is what was left.

## Decisions

**Thumbnails and design tiles are hardware bitmaps** (`rememberThumb`, `gpu`
in Common.kt). A software bitmap lives on the Java heap, and the GPU keeps
its own copy to draw it, so each one costs twice. A hardware bitmap is
uploaded once and kept only in graphics memory. Nothing reads their pixels;
they are only drawn.

**The caches empty when the app leaves the screen** (`dropCaches`, from
`MainActivity.onTrimMemory`). Up to 48 MB of thumbnails and 32 MB of design
tiles used to stay in a process that the watcher or the live wallpaper keeps
alive for days. What is on screen keeps its own reference, so coming back
draws without a reload.

**The live wallpaper frees its full-size software picture** right after making
the GPU copy and reading its colours, not whenever the garbage collector gets
to it: about 10 MB per screen on a 1080 × 2340 panel.

**Crossfade saves the most** (plan 004). On a screen where Badal is the live
wallpaper, a change is a state update and one draw. There is no `setBitmap`,
which takes about a second and has the system re-encode and write a full-size
image on every change. With Crossfade on both screens, nothing is written to
the system at all.

**Launcher shortcuts are set when the app opens** (`MainActivity`), not in
`App.onCreate`. The process also starts for every alarm, boot and package
update, and setting the shortcuts each time was a system call and a write
that changed nothing.

**All of Badal's own code is compiled ahead of time** (`baseline-prof.txt`).
The libraries already ship profiles for their hot paths, but our code ran
interpreted and then JIT-compiled in every new process. That covered the
alarm and screen-off paths, which always start cold, and `Painter`'s loops.
Badal's code is small, so a wildcard over the whole package costs little: R8
keeps 834 of its rules in a profile of 9,863. ART applies it at install
through the profile installer the libraries bring.

## Rejected

- **RGB_565 thumbnails**, half the memory again: gradients band, and most
  designs are gradients.
- **A smaller thumbnail cache:** with hardware bitmaps it already costs half,
  and a small one decodes again on every scroll.
- **A Macrobenchmark module to record a profile:** a second module and a
  device run after each change, for code small enough to compile whole.
- **Drawing wallpapers below the panel's resolution:** visibly soft.
- **Hearing screen-off in the live wallpaper instead of the watcher:** see
  plan 004.
