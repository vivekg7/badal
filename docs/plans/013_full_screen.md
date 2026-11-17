# 013 — Viewing pictures full screen

Status: built (2026-11-17). Tested on the Android 17 emulator (Pixel 10a).

## Problem

An album showed its pictures only as 9:16 thumbnails, cropped, and the
picture's menu showed one a little larger. To see a photo whole, or a design
at its real resolution, it had to be set as the wallpaper, or framed.

## Decisions

**View is the first line of a picture's menu, and the thumbnail at its top
opens it too.** A tap on a picture still opens the menu. Making the tap go
straight to the viewer would have moved Show on home, the album's most used
action, one step further away.

**The viewer (`ViewPage`) shows each picture whole, on black.** It shows the
picture as it is, not as it is framed on a screen; the framing editor already
shows that.

- Swipe between the album's pictures, in the album's order.
- Pinch or double-tap to zoom in, up to 6×. Zoomed in, one finger moves the
  picture and the page doesn't turn. Pinched back to about its size, it snaps
  to it and swiping works again.
- A tap hides the close button and the system bars, and another brings them
  back. It opens with them showing, so the way out is in sight.

**Decoded at twice the size it shows (`Images.whole`), never larger than the
original.** That keeps a 2.5× double-tap sharp. A camera photo fitted on a
phone screen then takes 15–40 MB, where the full photo would be 50–200 MB.
The thumbnail stands in while it loads, and the next and previous pictures
load ahead, so a swipe never lands on a blank page.

## Rejected

- **Opening the viewer on a tap**, with the menu behind a button in it: see
  above.
- **Full resolution with tiled decoding** (`BitmapRegionDecoder`) for deep
  zoom: a lot of code for a wallpaper app, where a picture is never shown
  larger than a screen.
- **Showing it framed, with Darken and the rest:** that is the framing
  editor's job, and the screen tabs' previews.
