# 003 — Clock shade, album order

Status: built (2026-11-08). Tested on the Android 17 emulator, and the shade
on a Galaxy A35 (One UI, Android 17) against its real clock.

## Problem

Two gaps left by 001 and 002:

- **Darken** dims the whole picture to keep the lock-screen clock readable, so a
  bright sky behind the clock costs the whole picture its colour.
- **In order** plays an album in the order pictures were added, and nothing
  could change that.

## Decisions

**A shade behind the clock, lock screen only** (`Rotation.shade`, 0–70%). It
is a black gradient at full strength over the top 30% of the screen, where One
UI puts the clock and date (and where 002's guide draws them), easing out to
nothing by 55%. The stops live in one place (`Shade`, Frame.kt), read by the
renderer and the previews alike, for the same reason as `Framing`: the
preview can't drift from the wallpaper. Several stops instead of two, so the
fade has no visible edge. It sits on top of Darken; the two add up. The home
screen doesn't get one: its clock is a widget the user places anywhere. On
the A35, One UI's stacked clock and its date end at about 31% of the height,
right where the fade begins, and the fade showed no band.

**Reorder in a mode of its own** (the swap icon on an album). Long-press
already selects pictures, so hold-and-drag can't share it without guessing
which was meant. In the mode, hold a picture and drag it. Others make room as
it passes over them, and holding near the top or bottom edge scrolls. The
order is saved on each drop. No library: the drag is about 110 lines
(`Drag`, Albums.kt). Two details cost a try each on the emulator, and are noted
in the code:

- The grid's own scrolling takes move events before a gesture on the grid sees
  them, so the gesture reads the Initial pass and consumes once a picture is
  held.
- A cell's `onGloballyPositioned` wasn't called again after its last move,
  leaving the held picture a column off. The held slot comes from the grid's
  layout info instead, which is current every frame.

## Rejected

- **A shade that adapts to how bright the picture is behind the clock.** It
  would be right more often, but it would make the shade a judgement Badal makes
  per picture, and a fixed one is predictable. Worth trying if 50% turns out
  wrong for dark pictures.
- **Drag handles on each picture**, to reorder without a mode: they crowd a
  three-column grid of thumbnails.
