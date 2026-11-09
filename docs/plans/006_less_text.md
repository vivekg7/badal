# 006 — Settings in one line each, explained in Help

Status: built (v0.6.0, 2026-11-09). Tested on the Android 17 emulator.

## Problem

Every setting carried a sentence under it ("The next time you wake the phone,
there's a new picture"), and pages carried hints ("Long-press pictures and
star them…"). That helped on the first day and was noise on every day after.
The screen tabs scrolled for two screens, and a hint stayed until you did
what it said. Vivek asked for a minimal UI: less text, every control kept,
with a Help page to explain.

## Decisions

**Keep the four tabs** (Home, Lock, Albums, Settings). Asked, alongside one
page with both previews over the albums; Vivek chose the tabs. Each screen's
settings stay one tap away, and nothing moves for someone who knows the app.

**One line per setting** (`Setting`, Common.kt): an icon, a short label, and
its control at the end: a switch, a value with a chevron, two segments, or a
slider with its percentage. The whole line is the touch target for a switch.
No supporting text. Labels say what a thing is ("On each lock", "Every", "By
the hour", "Shade"), not what it does.

**Whether Badal changes a screen is the switch in the top bar**, beside the
Help button. It was the first row of the page; there it reads as the page's
own on/off, as in the system's settings.

**Help is a page of questions**, grouped as the tabs are (`HelpPage`), one
open at a time. Every sentence taken out of the tabs went there, rewritten
as an answer, and so did what the hints said (favourites, reordering,
long-press to select, dragging the design preview). It opens from the `?` in
every tab's top bar and from Settings.

**Status in a few words.** The preview says "5 minutes ago" and "Next: on
lock or ~21:00" (`~` is an interval, which Android may deliver late). A screen
that follows home says "With home"; one paused by battery saver says so.

**Smaller pieces elsewhere:**

- Album page: one plain `+` button with the folder and palette buttons above
  it, no empty-state essay, no hints. "Hold and drag" is the reorder title.
- A picture's menu: icons and short labels in a plain dialog (Show on home,
  lock or both, Favourite, Edit design, Frame), with no title or Cancel; tap
  outside to close.
- Framing: the "follows Fit" line went (Reset is greyed when it would do
  nothing), and the clock and dock guide is a chip beside Fill, Whole and
  Reset.
- Sliders snap to 5% without the 14 step dots.
- The designs gallery and designer lost their hint lines.

## Rejected

- **One page with both screens' previews over the album grid.** Fewer taps to
  Next on both screens, but each screen's settings would move into a sheet, and
  the albums would share the page with them. Not chosen.
- **Tooltips or a first-run tour.** Either is text that shows up whether or
  not you need it. Help is there when you ask.
- **A `?` beside each setting.** Twelve small buttons are clutter. One Help
  page is easier to scan and to keep current.
- **Hiding settings until the screen is switched on.** You may want to pick an
  album before turning it on, and settings that come and go look like a bug.
