# 009 — Modes: other albums for a while

Status: built (v0.7.0, 2026-11-09). Tested on the Android 17 emulator: dark
theme, Do Not Disturb, and the launcher shortcut. On a Galaxy A35 (One UI,
Android 17), Modes and Routines lists a mode's shortcut under "Open an app or
do an app action" and runs it. It lists only the shortcuts published at the
time, so a mode on By hand must exist first.

## Problem

Vivek asked whether Badal can tell the phone's mode (Work, Sleep, Exercise,
Driving…) and use a different set of wallpapers in each.

## What an app can see

- **Not the mode itself.** Android 15's Modes API lets an app read and change
  only the rules it created, not the user's or the system's. Samsung's Modes
  and Routines has no public API for this either.
- **Without any permission:** whether Do Not Disturb (or a mode that silences
  the phone) is on (`NotificationManager.currentInterruptionFilter`), whether
  the dark theme is on, and whether the phone is charging. Sleep, Driving and
  similar modes usually turn on Do Not Disturb.
- **With permissions or Google Play services:** driving or walking from
  activity recognition, Wi-Fi networks or location for "at work". Each needs a
  permission, Badal's first dependency on Play services, or both.

## Decisions

**A mode is a name, a cue, and an album for home, lock or both** (`Mode`,
Model.kt). Null for a screen leaves it as it is. The cue is By hand, Do Not
Disturb, Dark theme or Charging (`Cue`).

**Precedence:** a mode switched on by hand wins. After it comes the first mode
whose cue holds, in the order listed. Any mode wins over By the hour. That
gives `Plan.sourceAt`, which checks the mode first and then `albumAt`. A mode
starting or ending changes the screen's source, and the engine already moves a
screen to a new picture when its source changes (`settle`), so nothing else
was needed. The rules stay pure: what the phone is doing comes in as a `Sense`
value, which `Engine.sense()` reads fresh each time. `PlanTest` covers it.

**Hearing a cue change needs a running process.** Do Not Disturb and charging
broadcasts, and configuration changes, reach only registered receivers. So
the lock watcher also runs while a mode has a cue, and listens for these too,
calling `settle()` on each. The engine reads the cues again at every alarm,
lock and tap anyway.

**Samsung's modes reach Badal through shortcuts.** Each mode on By hand gets a
dynamic launcher shortcut, and there is one more, "Modes off"
(`ShortcutActivity`). Modes and Routines can run an app's shortcut from "Open
an app or do an app action". A Samsung Work mode can then switch Badal's
Work mode on, and its end action switches it off. The same shortcuts are on
the launcher icon's long-press menu for doing it by hand.

**In Settings**, under Modes: one line per mode, with a switch for a mode by
hand and its cue for the others, the cue highlighted while it holds. Tapping a
line edits it. On the Home and Lock tabs, the status line names a mode in
force: "Work · Next: on lock".

## Rejected

- **Activity recognition** (driving, walking): Google Play services as a
  dependency, plus the `ACTIVITY_RECOGNITION` permission. Badal is offline and
  dependency-light on purpose.
- **Wi-Fi or location for "at work"**: needs location permission, for a
  wallpaper.
- **Creating Android Modes rules from Badal** (`AutomaticZenRule`): the
  arrow points the wrong way. Badal would own a Do Not Disturb rule the user
  then has to switch, and One UI shows these as its own modes.
- **A mode per screen inside its settings** (like By the hour): the point is
  one switch for both screens.
