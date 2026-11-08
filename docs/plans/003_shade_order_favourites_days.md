# 003 — Clock shade

Status: built (2026-11-08). Tested on the Android 17 emulator, and the shade
on a Galaxy A35 (One UI, Android 17) against its real clock.

## Problem

A gap left by 001 and 002:

- **Darken** dims the whole picture to keep the lock-screen clock readable, so a
  bright sky behind the clock costs the whole picture its colour.

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

## Rejected

- **A shade that adapts to how bright the picture is behind the clock.** It
  would be right more often, but it would make the shade a judgement Badal makes
  per picture, and a fixed one is predictable. Worth trying if 50% turns out
  wrong for dark pictures.
