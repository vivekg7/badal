# 012 — Curves, fractals and spirographs

Status: built (2026-11-17). Tested on the Android 17 emulator (Pixel 10a),
from contact sheets of every new preset.

## Problem

Vivek asked for more designs made from space-filling curves and fractals, and
for spirographs, sending pictures of two kinds he liked:

- multicolour spirographs, several curves in different colours woven together;
- line-art rosettes, a polygon drawn again and again, each copy a little turned
  and smaller.

Plan 005 had rejected fractals and L-systems. They make pictures of things
(trees, ferns) more than backgrounds, and need a different set of controls.

## Decisions

**Only the fractals that fill the screen like a texture, and fit the
controls a design already has.** That answers both reasons 005 gave. A
space-filling curve covers the screen evenly, and Size, Weight, Rotation,
the point and the seed each mean the obvious thing for it:

- Size is the curve's cell size, which also sets its order (how many times
  it subdivides);
- Weight is how wide the line is.

Trees, ferns and Mandelbrot views stay out. So does reaction-diffusion,
whose spots and stripes look good but take seconds to simulate.

**19 new patterns in three new groups (75 in all, 356 presets):**

- **Curves:** Hilbert, Moore (rounded, a closed loop), Peano, Sierpiński,
  the Gosper flowsnake, Dragons, and Meander.
  - Dragons: four dragon curves from the point, a quarter turn apart, one
    ink each. Together they fill the plane, so the inks make four
    interlocking regions.
  - Meander: one closed line round a random spanning tree, so every seed
    gives another.
- **Fractals:** an Apollonian gasket (circles in every gap between three
  circles), a Sierpiński carpet, a Penrose tiling (de Bruijn's pentagrid),
  circle packing, and Blocks (the screen split in two again and again,
  Mondrian-style).
- **Spirograph:**
  - Spirograph (a wheel rolling inside a ring) and Loops (a wheel rolling
    round another wheel), drawn once per ink. Each copy is a little turned,
    so the colours weave as in the pictures Vivek sent. The gears are real
    Spirograph tooth counts, and the seed picks a pair.
  - Rosettes: small ones all over the screen.
  - Guilloche: a banknote rosette.
  - Whirl and Whirls: the nested-polygon rosette from his second picture,
    alone or tiled. Each tile whirls the other way to its neighbours.
  - Harmonograph: a fading pendulum figure.

**Geometry in `Figures` (Figures.kt), pure, like `Framing`.** The L-systems,
the meander, the gasket, the packing and the Penrose tiling return plain
numbers, so `FiguresTest` checks them on the JVM:

- the grid curves visit each point once, and Moore and Meander close;
- Gosper and Dragon never retrace a step;
- gasket circles touch but never overlap, and packed circles keep their gap;
- Penrose rhombs have unit sides, and fat to thin comes out at the golden
  ratio.

`Painter` only scales and colours what `Figures` returns.

**Colour along a single line.** Until now a pattern's inks were spread over
separate marks. A curve is one mark, so it blends through the inks along its
length (`Pen.trail`). It counts only the part on screen, so a curve far larger
than the screen still shows every ink. It changes colour halfway along a
step, never at a corner, so rounded corners stay round.

**Within plan 005's time budget.** At 1080×2400 most patterns draw in
5–60 ms. Rosettes take about 0.25 s, and Packing about 0.7 s, the slowest of
all; it tries at most 40,000 places per size.

**No new fields in `Design`.** Shapes differ by seed instead: the gears of a
spirograph, the polygon of a whirl, the ratio of a harmonograph, the split of
a gasket. Shuffle steps through them, and saved recipes still load.

## Rejected

- **A "rounded corners" switch for curves:** another control that applies to
  only some patterns, which plan 005 rejected. Instead, Moore, the flowsnake,
  Dragons and Meander are drawn rounded, while Hilbert, Peano and Sierpiński
  keep their sharp corners.
- **Julia and Mandelbrot sets:** shown whole, they are an object on a blank
  ground. Zoomed in, they would need a list of hand-picked views, and every
  pixel has to be worked out, which is slow at full size.
- **The hat monotile:** a good story, but its substitution rules are a few
  hundred lines for a look close to Penrose's.
- **Reaction-diffusion and strange attractors:** seconds of simulation, or a
  picture more than a background.
