# Canvas

How `canvas/` draws the coordinate plane: the viewport and its ticks, immediate-mode painting with
a screen and an SVG backend, the three-layer view, and world-coordinate hit testing. Read
`CLAUDE.md` first; `PLAN.md` §5.3 is the design and §12 item 4 the painting decision. The module
`mathcanvas.canvas` requires `mathcanvas.render` (and through it `mathcanvas.engine` and
`javafx.graphics`) and exports four packages:

| Package | What | JavaFX |
|---|---|---|
| `mathcanvas.canvas.geom` | `Viewport`, `Ticks` | none |
| `mathcanvas.canvas.paint` | `Painter`, its values (`Rgba`, `Stroke`, `TextStyle`, `Anchor`), `ScreenPainter`, `SvgPainter`, `GridPainter`, `CanvasTheme` | none |
| `mathcanvas.canvas.hit` | `HitIndex`, `Hit` | none |
| `mathcanvas.canvas.view` | `CanvasView`, `Layer`, `PlotContent`, the screen backend `FxPainter` (package-private), `canvas-view.css` | yes |

The first three are pure Java, so their tests run without a display. The build checks that:
`canvas`'s own `checkJavafxFree` over those packages, main and test, and the gate's boundary grep
(`docs/reference/testing-tools.md`, **The module-boundary check**). This series knows nothing about
expressions: it draws the polylines, points and text it is handed.

## Viewport

`Viewport` is an immutable record: the world window (`xMin`, `xMax`, `yMin`, `yMax`), the pixel size
(`width`, `height`) and `aspectLocked`. The world's y goes up and the screen's down: `toScreenX(x) =
(x − xMin) · width / (xMax − xMin)`, `toScreenY(y) = (yMax − y) · height / (yMax − yMin)`, with
`toWorldX`, `toWorldY` the inverses and `pixelsPerUnitX`, `pixelsPerUnitY` the scales. The
constructor rejects a non-finite bound or size, an empty span, a span that overflows (finite bounds
such as ±1e308, which would leave no pixels per unit), and a size that is not positive.

- **Aspect lock.** A locked viewport has square units: the constructor widens the axis with fewer
  units per pixel about its centre until the two match. Squaring is skipped when they already
  agree to a relative 1e-12, so a locked viewport is not nudged by rounding on every operation;
  far from the origin its squareness is as good as the few ulps of its bounds allow.
- **Pan.** `pan(dx, dy)` moves the content by a pixel offset, as a drag does: the world point under
  a pixel ends up under the pixel `(dx, dy)` away.
- **Zoom.** `zoom(factor, sx, sy)` (above 1 zooms in) keeps the world point under the screen point
  `(sx, sy)` fixed, one factor on both axes, so a locked viewport stays locked. It returns the
  viewport unchanged when a span would exceed `MAX_SPAN` (1e15), fall below `MIN_SPAN` (1e-100),
  or fall below `MIN_RELATIVE_SPAN` (1e-12) of the window's largest coordinate, where neighbouring
  pixels would share a double.
- **Resize.** `resize(w, h)` keeps the centre and the scale, so a larger view shows more of the
  plane rather than stretching it. `withAspectLocked(b)` locks (and squares) or unlocks.
- `Viewport.centred(cx, cy, unitsPerPixel, w, h, locked)` builds one about a centre.

**Ticks.** `Ticks.forAxis(min, max, pixels, labelWidth)` picks the major step `m · 10^k`, `m` in {1,
2, 5}, as the smallest such step at least `Ticks.MIN_SPACING` (60) pixels apart whose widest visible
label, plus `Ticks.LABEL_GAP` (12) pixels, fits between neighbouring ticks (or that shows at most
one tick). `labelWidth` gives a label's extent along the axis in pixels: the view measures the text
in the font it draws it in, and `Ticks.estimate(fontSize)` (0.6 of the font size per character)
stands in without one, which the three-argument `forAxis` uses at 12 px. Labels of a few digits are
therefore 60 to 150 pixels apart (a 1-2-5 sequence cannot always land within a narrower band, since
between 2 and 5 the spacing grows two and a half times); long ones, far from the origin or deep in a
zoom (`1.00000000006×10⁷`), further. The minor step is a fifth of the major for `m` = 1 or 5 and a
half for `m` = 2, so it is itself a 1-2-5 value. Tick `i` sits at `i · step`; `first(min)` and
`last(max)` give the visible indices. `label(i)` computes the value exactly (`BigDecimal`, so no
`0.30000000000000004`) and writes it with the fewest digits that show it (trailing zeros dropped, so
neighbours always differ), with a true minus sign `−`, and in scientific form (`2.5×10⁻⁵`, `1×10⁶`)
when its magnitude is at least 10⁶ or below 10⁻⁴; zero is `0`.

## Painting

`Painter` is the one way math objects are drawn, immediate-mode, on the screen and into an SVG
export alike (`PLAN.md` §12 item 4). Every coordinate is in world units and the backend maps it
through its `viewport()`; widths, radii, dash lengths, offsets and font sizes are in pixels.

- **Paths.** `beginPath()`, `moveTo(x, y)`, `lineTo(x, y)`, `polyline(xs, ys, n)` and
  `closePath()` build a path; `stroke(Stroke)` and `fill(Rgba)` draw it and leave it in place
  until the next `beginPath()`. `polyline` starts a new subpath at its first point and after every
  non-finite value, which is how a sampled function's gap is drawn. A filled region is a closed
  path, filled by the non-zero rule.
- **Points and text.** `point(x, y, radius, colour)` draws a filled disc; `text(s, x, y, dx, dy,
  anchor, style)` draws a line of text with the `Anchor` (start, middle or end across; top,
  middle, baseline or bottom up) at the world point moved by `(dx, dy)` pixels.
- **Values.** `Rgba` (components in [0, 1]; `Rgba.rgb(0xRRGGBB)`, `hex()`), `Stroke` (colour,
  width, dash pattern; round caps and joins; `Stroke.solid`, `Stroke.dashed`) and `TextStyle`
  (size, colour; the platform's sans-serif font) are records.

**Clipping.** `ScreenPainter`, the base of both backends, keeps the path in world coordinates and
clips it, in world coordinates, to the viewport grown by `MARGIN` (64 pixels) on every side before
the backend sees it: strokes segment by segment (Liang–Barsky), a subpath that leaves and re-enters
becoming two; fills as polygons (Sutherland–Hodgman), so a region keeps its shape. Only the
clipped part is mapped to pixels, so no finite point, however far, reaches the backend as a huge or
infinite pixel coordinate: a sampled asymptote at 10¹⁷, a construction line or a half-plane between
points 10³⁰⁰ away and a spike to 1e308 are all clipped along their own segments and keep their
direction. Only a non-finite world value is a gap.

Each segment is clipped from its **anchor**, its point nearest the window's centre (an end itself
when the centre projects beyond it). The coordinates are scaled by a power of two to below 2, and
the anchor is the foot of the perpendicular from the centre to the line `dy·x − dx·y = x0·y1 −
y0·x1`. That line's constant is computed with fused multiply-adds as a compensated difference of
products, so when the line passes near the window it comes out nearly exact however far the ends
are: y = x/2 between ±1e300 is not rounded to y = 0. Liang–Barsky's parameters run from the
anchor, so the edges' parameters stay small enough to tell apart even when both ends are 10³⁰⁵
away. A clipped end is the segment's own end point when no edge cut it. Otherwise it is where the
segment crosses the edge that did, that edge's coordinate exact and the other interpolated from the
anchor. The polygon clipper's crossings are computed the same way. A point or text whose anchor
falls outside the grown window is not drawn. `ScreenPainter` hands a backend the constant
coordinate of a horizontal or vertical two-point line through `snap`, the identity by default.

**The screen backend.** `FxPainter` (in `view`) issues the calls on a `Canvas`'s
`GraphicsContext` in the canvas's pixels. Its `snap` puts a horizontal or vertical two-point line,
the grid and the axes, on the pixel grid: at a half pixel for an odd width, a whole one for an
even width, so a 1-pixel axis covers one row of pixels in its colour rather than two half-tinted
rows. Curves are not snapped. It collects the path and strokes each open subpath of an opaque
stroke in pieces of `PIECE` (16) segments, each its own path: the Prism render thread's cost of
stroking a path grows with the device-pixel area of its bounding box, which a long curve makes
nearly the whole canvas, and the pieces' boxes are small (16 was chosen by measuring 8, 16, 32 and
64 against whole paths with the pulse logger; 8 and 16 were fastest). Neighbouring pieces share an
end point and their round caps cover the joint, so no seam shows, and a dashed stroke carries its
dash offset from piece to piece. A translucent stroke, whose overlapping caps would darken the
joints, and a closed subpath are stroked whole. `SvgPainter` writes each stroke whole.

**The SVG backend.** `SvgPainter(viewport)` collects the calls and `document()` returns a standalone
SVG document the size of the viewport (`width`, `height`, `viewBox="0 0 w h"`) in its pixel
coordinates, y down and text upright, with no group transform and no `vector-effect`. Each call is
one element: a stroke `<path d="M…L…" fill="none" stroke="#rrggbb" stroke-width=…>` with round caps
and joins and `stroke-dasharray` when dashed; a fill `<path d="…Z" fill=…>`; a point `<circle>`; a
text `<text font-family="sans-serif" font-size=…>` with `text-anchor` and `dominant-baseline`
(`hanging`, `central`, `text-after-edge`) for its anchor, escaped. An opacity below 1 is written as
`stroke-opacity` or `fill-opacity`; a path clipping left empty writes nothing. Numbers have at most
two decimals and no trailing zeros, so the same calls always give the same bytes.

**The grid.** `GridPainter.paint(painter, theme, labelWidth)` draws, through any painter, the minor
grid, the major grid at the labelled ticks, the axes and the labels (12 px), the x axis's step
chosen with `labelWidth` (the y labels, one line each, need only their height);
`GridPainter.paint(painter, theme)` uses `Ticks.estimate`, and the view passes its own measurement.
The x labels hang below the x axis centred on their ticks, the y labels sit left of the y axis
centred on theirs, and zero is written once, below and left of the origin. When an axis is off
screen, or the y axis is within 48 pixels of the left edge, its labels are pinned inside that edge
(or moved to the axis's right). A label within 12 pixels of the end of its axis is left out, since
the edge would cut it in half. `CanvasTheme` carries the axis, grid, minor-grid and text colours and
eight series colours; `CanvasTheme.LIGHT` is the light theme, for drawing without a view.

## Layers

`CanvasView` is a `Region` holding three `Canvas` layers (`Layer`), bottom to top: `GRID` (the
grid, axes and labels), `OBJECTS` (the content) and `OVERLAY` (the hovered point, then any
`overlayProperty()` drawing: selections, drag previews). Its pref size is 640 by 480, and it sizes
the layers to itself and resizes its `viewport` property, keeping the centre and the scale. The
default viewport is square, 40 pixels to a unit, centred on the origin.

- **Dirty flags.** Each layer has a dirty flag and a frame repaints only the dirty layers. New
  content dirties `OBJECTS`; a change of hover or of the overlay drawing dirties `OVERLAY`; a
  change of viewport (a pan, a zoom, a resize) or of any theme colour dirties all three.
  `invalidate(layers…)` marks layers by hand, as a caller does when its overlay drawing changes.
- **Frames.** Marking a layer requests a pulse (`Platform.requestNextPulse()`), and a post-layout
  pulse listener, registered while the view is in a scene, paints the dirty layers, so any number
  of changes within a pulse cost one repaint, after layout and CSS. Each repaint starts with a
  `clearRect` of the whole canvas, which also drops the commands the canvas buffered since its
  last render.
- **Content.** `PlotContent` holds the finished polylines (`Curve`: xs, ys, stroke) and points
  (`Marker`: x, y, radius, colour) with their `HitIndex`; `PlotContent.of(curves, points)` builds
  the index, the polylines being items 0 to n − 1 and the points the items after. Sampling and
  indexing are the expensive part, so a worker builds the content and the FX thread only sets it.
  The arrays are not copied.
- **Input.** Dragging with the primary button pans; the scroll wheel (`1.0015^deltaY`) and the
  pinch gesture zoom about the pointer; moving the pointer hit-tests the content at
  `HitIndex.DEFAULT_TOLERANCE` and sets `hoveredHitProperty()`, which the overlay shows as a disc
  at the nearest point in the item's colour; leaving the view clears it. New content or a new
  viewport hit-tests again at the last pointer position.
- **Threading.** The view, its properties and all painting are FX thread only.

**Theming.** The colours are CSS properties of the view, `-canvas-axis`, `-canvas-grid`,
`-canvas-minor-grid`, `-canvas-text` and `-canvas-series-1` to `-canvas-series-8`, read into a
`CanvasTheme` by `theme()`. The user-agent stylesheet `canvas-view.css` sets the light theme
(equal to `CanvasTheme.LIGHT`) and a white background on `.canvas-view`, and the dark theme on
`.root.dark .canvas-view`, matching the root style class the app's theme switch sets
(`docs/reference/app.md`), so both themes work wherever the view is placed; the UI test checks
the dark rule through the root ancestor. A theme overrides them on `.canvas-view` in the scene's
stylesheet. Content colours are the caller's: content drawn in the light series colours stays so
in the dark theme.

## Hit testing

`HitIndex` finds the nearest point or polyline to a world point within a tolerance in pixels:
`nearest(x, y, viewport, tolerance)` returns an `Optional<Hit>` with the `item`, its `kind` (`POINT`
or `POLYLINE`), the `segment` (from point `segment` to `segment + 1`; −1 for a point), the parameter
`t` along it, the nearest world point `(x, y)` and the `distance` in pixels.

- **The metric.** Distances are in pixels through the viewport's scale on each axis, and the
  projection onto a segment is done in pixel space, so with the aspect unlocked the tolerance is a
  pixel circle, not a world one.
- **Priority.** A point within the tolerance wins over any polyline; otherwise the nearest polyline
  wins. Among equals, the nearer wins, then the lower item, then the lower segment.
- **The index.** A static R-tree bulk-loaded by sort-tile-recursive packing over the segments' and
  points' bounding boxes, sixteen boxes to a node, in flat arrays; a query descends the nodes whose
  boxes meet the tolerance box. A uniform grid was the alternative, but a sampled asymptote's
  segment spanning 10¹⁰ units would either crowd every other segment into one cell or land in
  thousands of cells; packing needs no tuning to the distribution. Segments with a non-finite end
  are left out, and an entry whose pixel distance is not a number (a segment whose pixel extent
  overflows) is never a hit. Built with `HitIndex.builder().polyline(item, xs, ys, n).point(item, x,
  y).build()`, immutable once built, and rebuilt whenever the polylines change, on whatever thread
  samples them.

## Benchmark

`CanvasBenchmarkTest` (a canvas UI test, run by `tools/ui_tests.sh`) is the first-week benchmark
of `PLAN.md` §7, Phase 3: ten polylines of 2,000 points in a 1200 by 800 view, and a simulated
slider drag that moves every curve and the slider's handle each frame, 120 measured frames after
20 warm-up frames. Each frame the test thread recomputes the curves and builds their content and
index, sets it on the FX thread, and waits for the frame that paints it, which must repaint the
objects and the overlay and leave the grid alone.

It prints and writes to `canvas/build/ui-screenshots/canvas-benchmark.txt`, median and 95th
percentile: the recompute; the FX thread's paint of the dirty layers, which only records canvas
commands; and beside them the whole frame from the start of the recompute to the frame painted,
which waits for a pulse and so never drops below the pulse interval. The drawing goes to
`canvas-benchmark.png`. Only the whole run is asserted, against the 30 s budget; the 16 ms figure
of §12 item 4 is reported, not asserted.

The whole frame is paced by the Prism render thread, which rasterises the recorded commands after
the FX thread hands over the frame, and before which the next pulse waits. No public API measures
it, and no printed figure includes it; the report says so. JavaFX's own pulse logger shows it:
`JAVA_TOOL_OPTIONS=-Djavafx.pulseLogger=true tools/ui_tests.sh --tests '*CanvasBenchmark*' -i`
prints each pulse's phases, the render thread's under `Painting`, and the FX thread's wait for the
previous frame under `Waiting for previous rendering`. The 16 ms figure is read against the whole
frame and that phase.

## Object model

(Phase 3a-2.)

## Tests

In `canvas/src/test/java/mathcanvas/canvas/`:

- `geom/ViewportTest`, `geom/ViewportProperties`: the maps, pan, zoom about a point, lock,
  resize and the zoom limits; over random windows, round trips, the zoom's fixed point, squareness
  through pans, zooms and resizes, and pan composition;
- `geom/TicksTest`, `geom/TicksProperties`: the step choice and labels, steps widened for long
  labels at 1e7 ± 1e-3 and 1e15 + [0, 1e4], a span of 1e307, an overflowing span refused; over
  random axes from 1e-8 to 1e9 units, every step the smallest 1-2-5 value at least 60 pixels apart
  whose labels fit, the minor step a half or a fifth, and every visible label distinct;
- `paint/SvgPainterTest`: a golden SVG file (`src/test/resources/mathcanvas/canvas/paint/
  small-drawing.svg`: the grid, a polyline with a gap, a dashed line, a translucent region, a point
  and escaped text), which is also parsed as namespaced XML; a mismatch leaves the drawing in
  `canvas/build/svg-actual/` to compare;
- `paint/ScreenPainterTest`: clipping of strokes, fills, points and text; a far first end (10¹⁷
  pixels) that leaves the near end exact, `tan` across π/2 leaving and re-entering through the
  edge, a finite spike to 1e308 drawn, a construction line and a half-plane with vertices 10⁸ and
  10³⁰⁰ away exact, a vee to 10³⁰⁵ keeping its vertex, and a non-finite value still a gap;
  `paint/GridPainterTest`: the grid's paths and labels with and without visible axes, and x labels
  far from the origin that do not overlap;
- `hit/HitIndexTest`, `hit/HitIndexProperties`: projection, the anisotropic metric, points first,
  gaps, ties, a segment whose pixels overflow never hit; over random walks with gaps and far jumps,
  random points, any aspect and queries near the entries, the tree against a scan of every entry
  (the pruning), and both against an oracle written independently in the test, a plain
  point-to-segment distance in pixel space with the same priority (the metric).

In `canvas/src/uiTest/java/mathcanvas/canvas/view/` (run by `tools/ui_tests.sh`): `CanvasViewTest`
shows the view in a harness window and checks three layers the size of the view, a pan and a
resize repainting every layer, content repainting the objects layer alone and a hover the overlay
alone, a scroll zooming about the pointer, and the grid in both themes with each axis one pixel
row or column of the axis colour, writing `canvas-grid-light.png` and `canvas-grid-dark.png`; and
`CanvasBenchmarkTest`, the benchmark above.
