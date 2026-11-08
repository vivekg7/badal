# 003 — Clock shade, album order, favourites, days of the week

Status: built (v0.3.0, 2026-11-08). Tested on the Android 17 emulator, and the shade
on a Galaxy A35 (One UI, Android 17) against its real clock.

## Problem

Four gaps left by 001 and 002:

- **Darken** dims the whole picture to keep the lock-screen clock readable, so a
  bright sky behind the clock costs the whole picture its colour.
- **In order** plays an album in the order pictures were added, and nothing
  could change that.
- **Every picture is equal** in Shuffle. Some deserve to come up more often.
- **Time ranges hold every day.** A weekend album, or a work-hours one on
  weekdays only, wasn't possible.

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

**Favourites come up twice per Shuffle round** (`Album.starred`). The bag of
pictures not yet shown this round holds a favourite twice, so "no repeats until
everything has shown" still holds, and a favourite still can't come up twice
in a row (`Plan.round` pulls neighbours apart). Twice, fixed, not a weight
setting: it is the smallest step that is noticeable, and one more setting
would have to explain what "weight 3" means. **In order ignores stars**: it
plays the album as arranged. Stars belong to the album, so a picture's star
goes when it is removed.

**A time range can hold on chosen days** (`TimeRange.days`, ISO numbers; empty
means every day, which is what older saved ranges load as). The part of a range
after midnight belongs to the day it started: Friday 22:00–06:00 holds until
Saturday morning, which is what "Friday night" means. `nextEdge` now tries each
start and end minute over the next eight days, and keeps only the moments
where the album actually changes. So a Saturday range doesn't wake the phone at
22:00 on Tuesday. As a side effect, a range that uses the screen's own album
schedules no alarm at all.

**Pause on battery saver** (`State.saverPause`, off by default). While saver is
on, changes on lock and on a timer are skipped. Time-of-day albums still switch:
they happen a few times a day at most, and "night pictures at night" is a
promise the user set up on purpose. A paused interval retries one interval
later, not every minute, so the pause doesn't itself cost wake-ups. When saver
goes off, the next lock or interval changes the picture. Badal doesn't listen
for saver going off, which would mean another registered receiver.

## Rejected

- **A shade that adapts to how bright the picture is behind the clock.** It
  would be right more often, but it would make the shade a judgement Badal makes
  per picture, and a fixed one is predictable. Worth trying if 50% turns out
  wrong for dark pictures.
- **Drag handles on each picture**, to reorder without a mode: they crowd a
  three-column grid of thumbnails.
- **A weight per favourite** (1×–5×): see above.
- **Day chips on every range in the list**, editable in place: ranges are
  added once and rarely changed. Remove and add again.
