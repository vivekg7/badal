# 005 — Wallpapers Badal draws itself

Status: built (v0.5.0, 2026-11-08). Tested on the Android 17 emulator.

## Problem

Every picture so far came from the gallery. Abstract wallpapers, such as a plain
colour, a gradient, or dots on black, are the kind people want a lot of. They
are easy to describe and tedious to find or make one by one. Vivek asked for
them inside the app:

- solid and gradient backgrounds, with a colour picker;
- many patterns over them, especially dots in several arrangements (a grid,
  rings from a point, rays from a source);
- hundreds of premade ones, and a way to change any of them.

## Decisions

**A design is a recipe** (`Design`, Design.kt): a background (`Fill`: solid,
linear, radial, sweep, or a mesh of soft blobs, in one to five colours), a
pattern over it (`Motif`, in one to five inks, with Opacity, Size, Weight,
Rotation, a centre point and a seed), and a finish (grain, vignette). Plain
values, no Android types, so the presets are tested on the JVM (`PresetsTest`).
`Painter` draws one onto a canvas. Every size in it is a fraction of the
screen's width, so a gallery tile is the wallpaper in miniature, and a seed
always gives the same picture.

**Drawn once, then it is a picture like any other.** Adding a design renders it
at the panel's exact size into `img/<id>` (PNG) with its thumbnail, as a
gallery copy would be. Albums, Shuffle, favourites, framing, Darken, the shade
and the crossfade then need nothing new. The recipe is kept beside it
(`design/<id>.json`) only so it can be edited again. Drawing takes 0.05–1 s
on the emulator. Grain and Mesh are the slowest; the mesh is worked out on a
grid a sixth of the size and smoothed on the way up. A PNG is 0.2–3 MB, and
grain makes it the larger.

**Editing makes a new picture in the old one's place** (`Library.redesign`).
It gets a new id at the same position, keeps the star, and drops any framing.
A screen showing the old one switches to the new one. An id always names the
same pixels, so nothing that compares ids (the engine's redraw check, the live
wallpaper's `Look`) can miss a change.

**56 patterns, by kind:**

- **Dots:** grid, offset, halftone, burst, rings, sunflower (golden angle),
  rays and vortex out from a point, wave, scatter, stars.
- **Lines:** stripes, grid, crosshatch, plaid, waves, zigzag, rings, sunburst,
  spiral, moiré, graph paper, isometric grid, nested squares.
- **Shapes:** checks, triangles, honeycomb, cubes, diamonds, pluses, crosses,
  circles, scales (seigaiha), bricks, Truchet arcs, the "10 PRINT" maze,
  weave, Bauhaus tiles, confetti, bubbles.
- **Art:** sun, rainbow, arches, pebbles, dunes, ridges, bokeh, low poly,
  stained-glass cells, topographic contours (marching squares over Perlin
  noise), flow and swirl fields, marble, ridgelines, aurora, camouflage blobs.

The last few kinds came from looking up what pattern generators and 2026
wallpaper write-ups offer once the first set was built. Generators commonly
offer graph paper, isometric grids, and flow fields of several kinds. Trend
pieces point to organic shapes, marbling, arches and soft gradients with
grain. The dots were what Vivek named, so they got the most variety.

A pattern is drawn whole into a layer, then laid over at its opacity, so where
its own marks overlap they don't add up. Low poly and Cells take their colours
from the background under them; for those, Opacity is how much the shading
varies.

**Presets come from tables, not a list of 280** (`Presets`):

- 16 plain colours, 24 gradients and 16 meshes;
- each pattern in four of 28 palettes. A palette is background colours and
  inks that go together, spread so neighbours differ.

Stars and Aurora only get dark palettes. "Surprise me" combines the same
parts at random. Each pattern has a base look (`Presets.base`) tuned on
contact sheets of every preset. Picking a pattern in the designer starts from
that look, keeping your colours.

**Where it lives:**

- **Gallery.** The palette button in an album opens it. Chips jump to each
  section. Tap a design to change it; long-press to pick several and add
  them as they are.
- **Designer.** A live preview over the settings: drag on it to move a
  centred pattern or a radial fill. The pattern row shows each pattern in
  your own colours.
- **Colour picker.** Hue, saturation and brightness by hand, a hex code, or
  a swatch.
- **Editing.** "Edit design…" in a picture's tap menu opens the designer on
  its recipe.

## Rejected

- **Drawing at show time from the recipe instead of keeping a PNG.** It would
  be crisp at any size and cost almost no storage. But the renderer, the
  framing editor and the thumbnails would each need a second path for "a
  picture that isn't a file", and a slow pattern would delay every change.
  The wallpaper is drawn for this phone's screen, and backup is off, so it
  never meets another screen size.
- **Editing in place under the same id.** The engine and the live wallpaper
  would see the same id and not redraw it.
- **A dependency for colour picking or noise.** The picker is about 150 lines,
  and Perlin noise about 60.
- **A weight per pattern parameter** (separate dot size, gap, line length…):
  Size and Weight mean the obvious thing for every pattern. More sliders
  would mean explaining which apply to which.
- **Following the system palette (Material You) in designs.** The wallpaper
  sets the palette, not the other way round; a design that changes with it
  would change under the user.
- **Fractals and L-systems** came up in the search. They make pictures of
  things (trees, ferns) more than backgrounds, and need a different set of
  controls.
