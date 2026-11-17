# 013 — Viewing pictures full screen

Status: built (2026-11-17). Tested on the Android 17 emulator (Pixel 10a).

## Problem

Every picture in the app was small: an album's 9:16 thumbnails (cropped),
the Home and Lock previews, the designs gallery, the designer's preview. To
see a photo whole, or a design at its real resolution, it had to be set as
the wallpaper. Vivek asked that wherever a picture shows, it can be seen full
screen.

## Decisions

**One viewer for all of them (`Viewer`, Viewer.kt).** Each picture shows
whole, on black.

- Swipe between pictures, where there are several.
- Pinch or double-tap to zoom in, up to 6×. Zoomed in, one finger moves the
  picture and the page doesn't turn. Pinched back to about its size, it snaps
  to it and swiping works again.
- A tap hides the top bar and the system bars, and another brings them back.
  It opens with them showing, so the way out is in sight.

It is a full-screen dialog, not a page, so it opens over a dialog as well as
a page (the one-picture chooser is a dialog), and no page has to give up its
place to it. A caller gives a count, a key, and how to load picture `i`.
`thumb` gives something to show while it loads, and `actions` adds buttons
to the top bar.

**Where it opens.** Each place keeps what a tap already did, except where
the viewer is the better answer:

| Where                   | How                           | Shows                                                      |
| ----------------------- | ----------------------------- | ---------------------------------------------------------- |
| An album                | View, in a picture's menu     | the album, as kept, in its order                           |
| The one-picture chooser | long-press (a tap picks)      | that album                                                 |
| The designs gallery     | tap                           | every design, with Edit and Add to album                   |
| The designer            | the button beside the preview | the design as it stands                                    |
| The Home and Lock tabs  | tap the preview               | the wallpaper as set: framed, darkened, blurred (`render`) |
| The framing editor      | the button beside Save        | the wallpaper with the frame not yet saved                 |

- **Album:** a tap still opens the menu. Making it open the viewer would have
  moved Show on home, the album's most used action, one step further away.
- **Gallery:** a tap now opens the viewer, at Vivek's choice, so the designs
  can be seen at full size and swiped through. Edit opens the designer, as a
  tap did before; Add to album adds it as it is. Long-press still selects.
- **Home and Lock:** a tap on the preview used to open the framing editor.
  The crop button beside it already does, and the viewer shows what the
  screen really looks like.
- **The designer's button sits beside the preview, not on it:** a touch on
  the preview moves a pattern's point.

**Album pictures are decoded at twice the size they show (`Images.whole`),
never larger than the original.** That keeps a 2.5× double-tap sharp. A
camera photo fitted on a phone screen then takes 15–40 MB, where the full
photo would be 50–200 MB. Designs are drawn at the screen's size, as they
would be added. The thumbnail or gallery tile stands in while it loads, and
the next and previous pictures load ahead, so a swipe never lands on a blank
page.

## Rejected

- **Opening the viewer on a tap in an album**, with the menu behind a button
  in it: see above.
- **A preview for album covers and the album list's icons:** a tap opens the
  album, where every picture can be viewed. The pattern picker's small tiles
  are swatches for choosing, not pictures.
- **Full resolution with tiled decoding** (`BitmapRegionDecoder`) for deep
  zoom: a lot of code for a wallpaper app, where a picture is never shown
  larger than a screen.
