# 011: Publishing on Google Play

Status: in progress. The bundle, listing, graphics, privacy policy and the
battery change are built (v0.7.1, 2026-11-09; the battery row tested on the
Android 17 emulator); the Play Console steps below are still to do.

## Problem

Badal has been sideloaded so far (Taildrop to one phone). To publish it on
Google Play it needs a bundle instead of an APK, a store listing, a privacy
policy, and permissions that pass Play's review. Android 15+ stays the
minimum (`minSdk 35`, plan [001](001_wallpaper_rotator.md)): checked against
lint, Android 14 would cost nothing and Android 12 a few branches, but Vivek
chose to keep 15.

## Decisions

**Google's app signing key; ours is the upload key.** Play re-signs every app
it delivers. We let Play generate the app signing key and upload bundles signed
with `local/badal-release.jks`, which Play then knows as the upload key. A lost
upload key can be reset through Play support; a lost app signing key could not.
The cost: the Play build and our sideloaded APKs are signed differently, so one
can't update the other. The A35 moves over once (Settings → Save a backup,
uninstall, install from Play, Restore a backup). Rejected: uploading our key as
the app signing key, which keeps sideloaded installs updatable but makes our
key the one that can never be lost.

**No one-tap battery prompt.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is on
Play's restricted list, allowed for a few kinds of app (a wallpaper app is not
clearly one). The Settings → Battery row now opens Badal's App info, where
App battery usage → Unrestricted is two or three taps away, and Help says so.
`isIgnoringBatteryOptimizations` still reads the result, so the row still shows
Restricted / Unrestricted. Rejected: the settings list
(`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), which makes the user find
Badal among every app.

**Exact alarms stay.** `SCHEDULE_EXACT_ALARM` is granted by the user and off by
default since Android 14; Play restricts `USE_EXACT_ALARM`, which Badal doesn't
use. Badal works without the grant (a few minutes late is fine for a
wallpaper), so if review objects, dropping it costs only the Exact timing row.

**Bundle and APK from one build.** `make release` and `make archive` build
both; the archive keeps the AAB beside the APK and checks they are signed by
the same key. Upload the archived `mapping.txt` with each bundle, so Play's
crash reports show names.

**Listing in the fastlane layout** (`fastlane/metadata/android/en-US/`): title,
short and full description, icon, feature graphic and screenshots. It's plain
files Play Console takes by copy and upload, needs no tool, and F-Droid reads
the same layout. The icon and feature graphic are drawn from HTML with
headless Chrome; the screenshots come from the emulator at 1080×1920 (Play
refuses screenshots whose long side is more than twice the short one, which
the emulator's 1080×2424 is) with a clean demo-mode status bar, using the
built-in designs, so no one's photos appear.

**Privacy policy in the repository** ([PRIVACY.md](../../PRIVACY.md)), linked
from Play as
`https://github.com/vivekg7/badal/blob/main/PRIVACY.md`. Badal collects
nothing, so the policy says what stays on the phone and why each permission
exists.

## Play Console steps

1. **Developer account** (play.google.com/console, one-time fee, identity
   check). A personal account created after November 2023 must run a
   **closed test with at least 12 testers opted in for 14 days in a row**
   before production is unlocked, once per app. An organisation account (with
   a D-U-N-S number) skips it.
2. **Create the app**: name "Badal: Wallpaper Changer", app, free, default
   language English. Free can't later become paid.
3. **App signing**: accept Google-generated key (the default). The first
   upload registers our key as the upload key.
4. **Store listing**: copy `title.txt`, `short_description.txt`,
   `full_description.txt`; upload `images/icon.png`, `featureGraphic.png` and
   `phoneScreenshots/*`. Category: Personalization. Contact email:
   vivek@ivy.homes. Privacy policy: the URL above.
5. **App content**:
   - Privacy policy: the URL above.
   - Ads: no ads.
   - App access: everything works without an account; no instructions needed.
   - Content rating (IARC questionnaire): category "All other app types"; no
     violence, sexual content, profanity, drugs, gambling, user-to-user
     communication or location sharing. Expect "Everyone" / PEGI 3.
   - Target audience: 13 and over (not designed for children, which would bring
     the Families policy).
   - Data safety: no data collected, no data shared. Badal has no internet
     permission, so nothing leaves the device; "data is encrypted in transit"
     doesn't apply; deletion: uninstalling removes everything.
   - Foreground service declaration: type `specialUse`. Description: "Badal
     changes the wallpaper each time the screen turns off, so every unlock
     shows a new picture, and switches albums when Do Not Disturb, charging or
     the dark theme change. Android delivers ACTION_SCREEN_OFF and these
     changes only to a receiver registered by a running process. The service
     runs only while the user has turned on 'On each lock' or a mode that
     follows one of these, and stops when they turn it off. If it is
     interrupted, the wallpaper stops changing on lock." Video: screen
     recording of Lock tab → Change: On each lock → lock and unlock, showing
     the new picture.
   - Exact alarm: if asked, "the user's chosen change times and time-of-day
     albums"; user-granted from Settings → Exact timing.
6. **Closed testing**: upload `local/releases/v<version>/badal-v<version>.aab`
   and its `.mapping.txt`, add the testers' Google accounts (or a Google
   Group), send them the opt-in link, and wait out the 14 days.
7. **Production**: apply for production access with the testing answers, then
   promote the release. First review takes a few days.

## Afterwards

Each release: bump `versionCode` (Play refuses one it has seen),
`make archive`, upload the AAB and mapping, paste a changelog (also kept as
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`).
