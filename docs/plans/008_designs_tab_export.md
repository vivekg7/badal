# 008 — The designs in a tab of their own, and saving to the gallery

Status: built (2026-11-09). Tested on the Android 17 emulator.

## Problem

The 280 premade designs ([005](005_designs.md)) were four taps deep: the Albums
tab, make an album, open it, then the palette button. Vivek asked for them up
front, with fewer clicks, for example as a tab in the bottom bar. He also
asked for a way to get wallpapers out of the app as PNG or JPEG, without
making the APK much larger.

## Decisions

**A fifth tab, Designs**, between Lock and Albums (`Shell`). It is the same
gallery. It has its own top bar, because the title counts a selection, with
Help where the other tabs have it. Tapping a design opens the designer over
the tabs. Its scroll position is kept like any tab's.

**Albums come last.** From the tab, "Add to album" (or "Add 3" after a
long-press) asks which album, with "New album" in the same dialog
(`AddToAlbum`). With no albums yet, it goes straight to naming one. The
palette button inside an album stays, and adds straight to that album.

**Save to gallery** writes to `Pictures/Badal` through MediaStore, which needs
no permission for an app's own files (`Images.export`). A photo goes out as
its original file. A design goes out as the PNG it was drawn into, at the
screen's size. Neither is re-encoded, so it costs nothing in quality and adds
no code to the APK beyond the call. It is in a picture's menu, in the
selection bar for several, and in the designer for a design not yet added
to an album.

## Rejected

- **Choosing PNG or JPEG.** Each file is already in the better of the two for
  what it is. Re-encoding a photo as PNG makes it several times larger, and a
  gradient as JPEG bands.
- **A Share button.** It needs a `FileProvider` to hand out Badal's private
  files. Once a picture is in the gallery, the gallery shares it.
- **Designs replacing the Albums tab**, or appearing inside it. They are a
  source of pictures, not a place where pictures live, and an album full of
  unadded designs would blur that.
