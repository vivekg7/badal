# 002 — Frame each picture, per screen

Status: built (2026-11-08). Tested on a Galaxy A35 (One UI, Android 17).

## Problem

Fill crops every picture around its centre, and Whole shrinks it to fit. Neither
is right for every picture: a portrait shot with the face near the top loses the
face, and a landscape loses whichever side matters. The lock screen makes it
worse, because the clock covers the top third: what suits the home screen
often sits under the clock there.

Vivek asked for each picture to be aligned on its own, on each screen, and for
control over the lock-screen clock "if possible".

## Decisions

**A frame per picture per screen, stored on the screen's `Rotation`**
(`frames: Map<Long, Frame>`). A picture with no frame follows the screen's
Fit, which stays as the default for everything not framed by hand. The same
picture can sit low on the lock screen, under the clock, and centred on the
home screen. Removing a picture or its album drops its frames.

**A frame is a focus point and a zoom, in fractions of the picture**
(`Frame(x, y, zoom)`), not a pixel crop rectangle. Fractions fit the
thumbnail, the editor's larger copy and the full picture alike, so one pure
function, `Framing.place`, positions the picture for all three, and the
preview can't drift from the wallpaper. Zoom is relative to "just covers the
screen": 1 is Fill, and below 1 shows the picture whole over its blurred copy,
down to `0`, meaning "as small as fits", which is what Whole stands for. So
Fill and Whole are two points on one scale, and the editor needs no mode
switch.

**Edges always hold.** A picture larger than the screen never leaves a gap; one
smaller stays wholly on screen. The editor stores the frame after clamping
(`Framing.settled`), so dragging past an edge isn't remembered and the next
drag back moves at once. Zoom stops at 4×: beyond that, a phone photo is
visibly soft on a 1080 px panel.

**The editor works on the real screen shape** (`Images.screen()`), with drag to
move, pinch to zoom around the fingers, and double-tap to switch between Fill
and Whole. It opens from the screen tab's preview (tap it, or the crop button)
and from a picture in an album ("Frame it…"). Nothing is saved until Save,
and then only the screens currently showing that picture are drawn again.

**The clock can't be moved, so the editor shows where it is.** Android has no
API for another app to place, size or style the lock-screen clock, and One UI
keeps it to its own lock-screen editor. The editor draws a guide clock, date
and shortcut buttons roughly where One UI puts them (dock icons on the home
screen), so a face isn't framed under the clock. It is a guide only: the
user can move the real clock, and on the A35 it was a stacked two-line style
in the same region.

## Also in this change

- **The current picture didn't update in the app.** `Thumb` used
  `produceState`, which keeps its value when its key changes, and only loaded
  when that value was empty. So after a change it went on showing the old
  thumbnail. It now always replaces the value, keeping the old picture until
  the new one has loaded so nothing blinks.
- **A spinner while a wallpaper is being set** (`Engine.busy`), with Previous
  and Next disabled. Setting takes about a second, which looked like a missed
  tap.
- **"Changed 5 minutes ago" keeps time** while the page stays open.

## Rejected

- **A crop rectangle in pixels:** tied to one resolution, and can't express a
  picture smaller than the screen.
- **One frame per picture for both screens:** the clock covers a different part
  of the lock screen than the dock covers of the home screen, which was the
  point of asking.
- **Saving as you drag:** each save redraws the wallpaper at full size (about a
  second and a full-screen bitmap). Saving once, on Save, keeps the editor
  smooth.
- **Detecting faces to frame automatically:** needs ML Kit and its model, a
  dependency several times the size of the app, for what one drag does. Worth
  revisiting as a default for pictures nobody framed.
