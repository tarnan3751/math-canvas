# Canvas

How `canvas/` draws the coordinate plane and models what is on it: the viewport and its ticks,
immediate-mode painting with a screen and an SVG backend, the three-layer view, world-coordinate hit
testing, the object model with its dependency graph and path parameters, and the sampler that turns
the objects' expressions into curves and regions. Read `CLAUDE.md` first; `PLAN.md` §5.3 is the
design and §12 item 4 the painting decision. The module `mathcanvas.canvas` requires
`mathcanvas.render` (and through it `mathcanvas.engine` and `javafx.graphics`) and exports these
packages:

| Package | What | JavaFX |
|---|---|---|
| `mathcanvas.canvas.geom` | `Viewport`, `Ticks` | none |
| `mathcanvas.canvas.paint` | `Painter`, its values (`Rgba`, `Stroke`, `TextStyle`, `Anchor`), `ScreenPainter`, `SvgPainter`, `GridPainter`, `CanvasTheme` | none |
| `mathcanvas.canvas.hit` | `HitIndex`, `Hit` | none |
| `mathcanvas.canvas.model` | `CanvasObject` and its records, `ObjectId`, `Style`, `Geometry`, `Polyline`, `Sampler`, `CanvasState`, `Recompute`, `Recomputes` | none |
| `mathcanvas.canvas.sample` | `Samplers`, the `Sampler` over the compiler | none |
| `mathcanvas.canvas.tool` | `Tool` and the tools, `Scene`, `Pointer`, `Edit`, `Snap`, `PathProjection` | none |
| `mathcanvas.canvas.view` | `CanvasView`, `Layer`, `PlotContent`, `CanvasContent`, `Export`, the screen backend `FxPainter` (package-private), `canvas-view.css` | yes |

All but `view` are pure Java, so their tests run without a display. The build checks that:
`canvas`'s own `checkJavafxFree` over those packages, main and test, and the gate's boundary grep
(`docs/reference/testing-tools.md`, **The module-boundary check**). The painting knows nothing
about expressions: it draws the fills, polylines, points and text it is handed, which
`CanvasContent` builds from the objects' geometry (**Graph kinds**). The object model holds
expressions but never evaluates one: it asks a `Sampler`, which `sample` implements.

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
the edge would cut it in half. `CanvasTheme` carries the background, axis, grid, minor-grid and text
colours and eight series colours; `CanvasTheme.LIGHT` is the light theme, for drawing without a view.

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
- **Content.** `PlotContent` holds the filled regions (`Fill`: xs, ys, colour, rings ended by a
  non-finite value), the finished polylines (`Curve`: xs, ys, stroke), the points (`Marker`: x, y,
  radius, colour) and the labels (`Label`: text, world point, pixel offset, anchor, style), drawn in
  that order, with their `HitIndex`; `PlotContent.of(fills, curves, points, labels)` (or
  `of(curves, points)`) builds the index, the polylines being items 0 to n − 1 and the points the
  items after; fills and labels are not hit. Sampling and
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
`CanvasTheme` by `theme()` with the view's own background colour (`-fx-background-color`). The user-agent stylesheet `canvas-view.css` sets the light theme
(equal to `CanvasTheme.LIGHT`) and a white background on `.canvas-view`, and the dark theme on
`.root.dark .canvas-view`, matching the root style class the app's theme switch sets
(`docs/reference/app.md`), so both themes work wherever the view is placed; the UI test checks
the dark rule through the root ancestor. A theme overrides them on `.canvas-view` in the scene's
stylesheet. Content colours are the caller's: content drawn in the light series colours stays so
in the dark theme.

## Export

`Export` draws the plane as a picture through the same calls as the screen: the theme's
background as a path filling the viewport, then `GridPainter` with the tick labels measured by the
function the caller passes (the view's `labelWidth`, on the FX thread, gives the view's ticks;
`Ticks.estimate` draws without a view), then the `PlotContent`. The
overlay (the hovered point, selections, drag previews) is interaction state and is left out.

- **SVG.** `Export.svg(viewport, theme, content, labelWidth)` runs the calls through `SvgPainter`
  and returns the document, the viewport's pixel size, on whatever thread the measure may run on.
  With the view's measure that is the FX thread, which a user's export accepts: building the
  document is bounded work, the same drawing as one frame, and writing the file is the caller's,
  off the FX thread. Tick labels and other text are `<text>`
  elements in the platform's sans-serif font, so a browser shows the text the screen shows in a
  font of its own.
- **Image.** `Export.image(viewport, theme, content, labelWidth, scale)`, on the FX thread, draws on a canvas
  of its own, the viewport's pixel size times the scale rounded up, through `FxPainter` under a
  scale transform, and snapshots it: lines, points and text are drawn at the scale, not stretched
  from the screen's pixels. `Export.fits(viewport, scale)` says whether both sides stay within
  `MAX_IMAGE_SIDE` (8192 pixels, beyond which a canvas exceeds JavaFX's textures). Encoding and
  writing the image file are the caller's, off the FX thread.
- **Colours.** The theme gives the background, the grid and the labels; the content carries its
  own colours, so a caller exporting in another theme builds the content in that theme.

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

`GraphBenchmarkTest` is the same benchmark with real graphs: ten function graphs (`x² sin x`,
`x eˣ`, `(x + 1)³`, `x⁴ − 1`, `x² − 5x + 6`, `x² + 1` from `PLAN.md` §6, then `sin(x)/x`, `tan x`,
`e^(−x²)` and `x³ − a·x`) through the real sampler in a 1200 by 800 view, the slider `a` dragged for
120 measured frames after 20 warm-up frames, twice: once recomputing what the slider's change
recomputes (its reader), once resampling all ten every frame, the worst case of a pan. Each frame
makes the sampler for the new value from the shared memo, recomputes the canvas and builds the
content. It reports the vertices drawn and, median and 95th percentile, the recompute, the FX
paint, their sum and the whole frame to `graph-benchmark.txt`, the drawing to
`graph-benchmark.png`, and asserts only the whole run, against 60 s.

The whole frame is paced by the Prism render thread, which rasterises the recorded commands after
the FX thread hands over the frame, and before which the next pulse waits. No public API measures
it, and no printed figure includes it; the report says so. JavaFX's own pulse logger shows it:
`JAVA_TOOL_OPTIONS=-Djavafx.pulseLogger=true tools/ui_tests.sh --tests '*CanvasBenchmark*' -i`
prints each pulse's phases, the render thread's under `Painting`, and the FX thread's wait for the
previous frame under `Waiting for previous rendering`. The 16 ms figure is read against the whole
frame and that phase.

## Object model

Package `mathcanvas.canvas.model`, free of JavaFX (the gate's boundary check covers it), so it
tests headless. The model holds the algebraic side of every object (expressions, parameters,
parent ids) and the geometry computed from it; it never evaluates an expression itself, it asks
a `Sampler`.

### Objects and ids

`CanvasObject` is a sealed interface over one immutable record per kind; code over objects uses
an exhaustive `switch` with no `default`. Every object has an `ObjectId`, a `Style(colour, width,
dashed, visible, labelVisible)` (the colour an index 0 to 7 into the series palette, the width in
pixels, whether the stroke is dashed),
its `parents()` in order and the engine variables it `reads()`.

| Kind | Definition | Parameter |
|---|---|---|
| `FreePoint` | world `x`, `y` | |
| `PointOnPath` | a path id, a `ParamKind`, the parameter, the last position, an optional expression `at` | the path's |
| `Intersection` | two path ids, an index, an optional `Anchor(x, y, t1, t2, tangent, approach)`, the approach an optional `Approach(t, x, y)` | |
| `CriticalPoint` | a function graph's id, a `Feature` (`ROOT`, `MIN`, `MAX`), an index, an optional `Anchor` as an intersection's | |
| `FunctionGraph` | an `Expr` in its variable, a domain (an engine `Interval`, default (−∞, ∞)) | `X`, the abscissa |
| `ParametricCurve` | `x` and `y` in a parameter, a finite domain | `T` |
| `PolarCurve` | a radius in an angle, a finite domain (`PolarCurve.TURN`, one turn, for tools to use) | `THETA` |
| `ImplicitCurve` | an `=` relation in `x` and `y` | `NONE` |
| `InequalityRegion` | a `<`, `≤`, `>` or `≥` relation in `x` and `y` | |
| `ShadedArea` | an upper function graph id, an optional lower one (the x-axis without), bounds `from` and `to` as expressions | |
| `RiemannSum` | a function graph id, bounds `from` and `to`, a number of pieces `n` (expressions) and a `Quadrature.Method` (`LEFT`, `RIGHT`, `MIDPOINT`, `TRAPEZOID`) | |
| `Slider` | a variable name, exact `BigDecimal` min, max, step and value | |
| `Text` | world anchor, text | |
| `Segment` | two point ids | `LINE`, 0 to 1 |
| `Line` | `Through(a, b)` two point ids, or `PointSlope(point, slope, at)` | `LINE` |

Points are free points, points on paths, intersections and critical points; paths are function
graphs, parametric, polar and implicit curves, segments and lines. A point on a path and an
intersection take paths as parents, a critical point, a shaded area and a Riemann sum function
graphs (an area's two distinct); a segment and a line take points.

**Ids** are allocated in order from the state's `nextId`, never renumbered: parents refer to
them, so they are saved with the objects and survive a load, and the counter is part of the
state, so an undo restores it with the rest. An id an undo frees may be given again by the next
add, which is safe because the redo history is cleared by then. Monotonic ids rather than UUIDs:
deterministic files and tests, and no randomness; a paste between documents remaps parent ids
whichever scheme is used.

**Reads.** `reads()` is every symbol name in the object's expressions and every head of an
application that is not a builtin (a worksheet function), less the object's own variables. A
binder's variable inside an expression (a Σ index) is counted too: the over-approximation costs
at most a recompute and keeps the walk from restating the engine's scoping, and the engine has
no public free-names query.

**Sliders** are engine variables (`PLAN.md` §5.5). Their numbers are exact decimals kept without
trailing zeros, so `0.3` binds as 3/10 and `0.5` equals `0.50`. The model checks the name
(`Slider.isVariableName`): exactly one identifier as the engine's lexer reads it, in its normalised
spelling, and not a builtin, constant, domain or keyword; `x` and `y` are allowed, since a graph's
own variable shadows them.

### Geometry and the sampler

`Geometry` is the drawn side: `UNDEFINED`, a `Point`, a `Curve(path, branches)`, a
`Region(rings)` (even-odd), a `ValuedRegion(rings, value)` (a shaded area's or a Riemann sum's,
the value finite) or a `Value` (a slider's). A curve's branches are `Polyline`s, an
immutable class over copied arrays of parameter, `x` and `y`, split at every gap, whose `xs()` and
`ys()` hand the painting loop the arrays without a copy (read-only by contract). Its `Path` says how
to evaluate it away from its samples: `Parametrised(parametrisation, lo, hi, kind)` or
`POLYLINE_ONLY` (an implicit curve). A parametrisation is `Graph`, `Parametric` or `Polar` over
expressions, which only the sampler evaluates, or `Linear(px, py, dx, dy)`, which the model
evaluates itself.

`Sampler` is bound by its maker to an environment (the worksheet's definitions and the sliders'
values), immutable and thread-safe, and answers NaN wherever a value is undefined or not real:
`value(expr, locals)` (a domain's end, a slope), `point(parametrisation, t)`, `sample(parametrisation,
lo, hi, window, cancellation)` (polylines split at gaps; the window is the one the path is drawn
in, on whose scale flatness is judged and beyond which samples are cut), `contour(equation, window,
cancellation)`, `region(inequality, window, cancellation)`, and `integral(graph, a, b,
cancellation)` and `riemannHeights(graph, a, b, n, method)`, which the real sampler computes with
`compile.Quadrature` over the graph's compiled evaluator (`docs/reference/engine.md`, **Numeric
integration**) and whose defaults, for the tests' samplers, take Simpson's rule on 2,000 pieces and
the heights through `point`. `mathcanvas.canvas.sample`
implements it over the expression compiler (**Sampling**); the model's tests use a small `double`
interpreter.

`Recomputes.standard()` computes each kind's geometry from its definition, its parents' geometry,
the sampler and a `Window` (a world rectangle; the integration maps the viewport to it):

- free points and texts are their coordinates, a slider its value;
- a function graph is sampled over its domain clipped to the window widened by half its size on
  each side (`Recomputes.MARGIN`); a domain end `∞` or `-∞` is infinite, any other is the
  sampler's value, and a domain whose ends are not ordered numbers is undefined;
- parametric and polar curves are sampled over their domain, undefined unless both ends are
  finite;
- implicit curves are the sampler's contour over the widened window, regions its rings over the
  window, which the sampler widens by half itself (**Sampling**);
- a shaded area (`Areas`) is undefined unless `from` and `to` are numbers with `from < to` inside
  both graphs' domains and the integral converges (it does not across a pole or a gap); its value
  is the signed integral of the upper graph less the lower one, and its rings run along the upper
  graph and back along the lower's, or along the axis, at every abscissa either was sampled at
  (exact points at the ends, from the sampler), over the part of the interval the graphs were
  sampled on (the widened window), so a pan redraws it with the graphs' resample while the value
  does not depend on the window; where the sides cross the region is cut at the crossing, one ring
  per lobe, so no ring crosses itself;
- a Riemann sum is the sum of its rectangles: undefined unless its bounds are as an area's, `n` is
  a whole number from 1 to `RiemannSum.MAX_PIECES` (1,000) and every height is finite, and defined
  otherwise, whatever its integral does. Across a pole a sum can still be a sum of finite heights
  while the integral it approximates does not exist (`tan x` on `[0, 2]` by midpoints is −0.66),
  and a bounded graph that oscillates too fast for the double rule's cap (`sin 50x` on `[0, 40]`)
  still has its sum; only an area's value is an integral. Its rings are a rectangle per piece
  standing on the axis at its height (a trapezoid between the two ends' heights for `TRAPEZOID`),
  its value their sum; `Areas.problem` says which condition an undefined area or sum fails, for its
  row (the bounds, the number of pieces, a height that is not finite, an area's integral that does
  not converge);
- a segment is its two points, parameter 0 to 1; a line through two points is undefined while
  they coincide, a point-slope line while its slope (evaluated with `at` bound to the point's
  abscissa: the tangent form) is not a finite number; a line is drawn clipped to the widened
  window.

### The graph and recompute

`CanvasState(objects, geometry, nextId)` is the canvas at one moment: the objects in creation
order (also the drawing order), the geometry of every object recomputed so far, and the next id.
It is immutable; every edit and recompute returns a new state.

- **Edges.** An object depends on its parents and on every slider whose variable it reads.
- **Checks.** The constructor checks that `nextId` is at least 1, ids are distinct and below
  `nextId`, every parent exists and is of the right kind, every geometry is of its object's kind
  (a point for points and texts, a curve for paths, a region for a region, a value for a slider,
  undefined for any), slider variables are distinct, and the graph is acyclic (Kahn's
  algorithm). A cycle is a `CycleException` carrying the cycle (the object, then each object a
  parent of the one before, the last having the first as a parent); `cycleThrough(replacement)`
  reports it before an edit.
- **Edits.** `add(make)` gives the object `nextObjectId()`; `replace(object)` keeps its place and
  refuses a cycle; `remove(id)` takes the object and everything built on it through parents (the
  readers of a removed slider's variable stay: the variable may be defined elsewhere);
  `changedSince(before)` lists what a command changed. Geometry stays until the next recompute.
- **Recompute.** `recompute(changed, changedVariables, sampler, window, recompute, cancellation)`
  seeds with the changed objects and the readers of the changed variables, takes them and
  everything depending on them, and visits that set in topological order (ties in drawing order),
  each object once and after its parents, checking the token before each group. A `Recompute`
  works on groups: one object, or a **family**, every intersection of one ordered pair of paths or
  every critical point of one graph and one feature, recomputed together whenever any member is,
  so the members are matched to the candidates jointly. Intersection families are keyed by the
  ordered pair, so tools create an intersection with the lower object id first and one pair never
  splits into two families. A group with an undefined parent is undefined
  without being recomputed, keeping its tracking state; the state clears when the parents are
  defined again. A recompute may update an object's tracking state (a point's last position, an
  intersection's or a critical point's anchor and index) but never its parents. Each member of a
  family also receives its own geometry as the last recompute left it, beside its parents', so
  the matcher knows which members were defined in the previous frame; that is not a dependency
  edge, and it is never persisted.
  `recomputeAll` seeds with every object, `resample(window)` with the window-sampled kinds
  (graphs, implicit curves, regions, lines).

### Points on paths

A point on a path keeps a parameter in the path's own parameterisation (`ParamKind`) and its last
landed position.

| Rule | Case | Result |
|---|---|---|
| P1 | the path is parametrised with the point's kind | the point is at the parameter, which no recompute changes or clamps; outside the domain, or where the path is undefined, the point is undefined and is defined again at the same parameter when the domain grows back |
| P2 | the path was redefined with another kind of parameter | the last position is projected onto the new path once (nearest sample, then a golden-section search of the parameter through the sampler), and the projection's parameter and kind are kept from then on |
| P3 | the path has no parameter (an implicit curve) | the last position is projected onto the new polylines at every recompute, so a pan that resamples the curve does not slide the point |
| P4 | the path is undefined | the point is undefined; its parameter and last position stay |
| P5 | the point is placed at an expression (`at`, which may read sliders) | the parameter is the expression's value at every recompute, in the path's own kind, so the point follows its sliders (the secant's `a + h`); undefined where the value is not finite, outside the domain, or on a path with no parameter; a drag does not move it, and the coordinate entry places it at a typed expression that reads a defined name (a slider, a worksheet definition), or back at a number |

### Intersections

**Candidates.** Seeds are the crossings of the two curves' sampled segments (bucketed in a
uniform grid; nearly parallel segments that are not an overlap are still tested for a crossing),
every vertex of either curve whose distance to the other is a local minimum within a segment's
length and below its neighbours by more than rounding (where a tangency hides between samples;
ripples of rounding along two near-parallel curves are not seeds), at most one per sampling step,
each member's anchor, so a tracked intersection outside the sampled window is still found, and
each member's approach, so a root beside a multiple one, closer than a sampling step, is found from
the side its member came from; past 4,096 seeds they are clustered at the sampling step. When both
curves have a parameter, each seed is refined by a damped Gauss–Newton (Levenberg–Marquardt)
descent on `P₁(t₁) − P₂(t₂)` through the sampler: held first to the seed's own segments (from the
seed, then from the segments' ends, since a Newton step from inside a segment that holds an
extremum points away from a root beyond it), then over the whole domains, a root found that way
kept only within the curves' samples' bounds widened by their size. It runs until a step stops
shrinking the gap and is abandoned after five steps in a row that shrink it by less than a tenth
above the tolerance.

A refined seed is a root when the gap is within `1e-9` of the scale (the window's diagonal, kept
above zero and never raised to a floor, so the tolerances stay relative in a small window), or of
the rounding of the coordinates where that is larger, and it is not one of three signs of two
curves that only approach each other: a refinement stopped at the edge of its range with the gap
still above a millionth of the tolerance, a last step longer than a thousand tolerances, or a
tangency whose band (below) is as long as an overlap's stretch while the gap stays above a
thousandth of the tolerance (a root of high order closes the gap; `x²` against `x² + 1e-7`, within
the tolerance near the window's edge, never does). A root is a tangency when the tangents are
parallel within a sine of `1e-6`. A tangency's **band** is the length along the first curve over
which the gap stays within the tolerance, found by doubling a step each way and bisecting, so every
seed that reaches a multiple root reports the same band wherever it started.

Candidates within `1e-7` of `max(scale, |coordinates|)` are one root found twice (a closed curve's
seam at `t = 0` and `2π`); two tangencies whose bands overlap, or between which the gap stays
within the tolerance all along the first curve, are one multiple root. They are ordered by the
parameter on the first curve then the second, or by position when a curve has no parameter. A
curve with no parameter is not refined: its candidates are the crossings of the samples, as
precise as the sampling, none of them a tangency.

Limits: a root of multiplicity k is located only to about the k-th root of the tolerance (a triple
root of `x³ − a·x` to about `2e-3` at unit scale, `x⁹`'s to about `0.1`); a simple root within that
band of a multiple one is found as part of it (`x²(x − d)` for `|d|` below about `1e-3`); two
simple roots closer than a sampling step may be found as one unless a member's approach points at
the other.

**Overlap.** Two curves overlap when they coincide to about double precision (`1e-13` of the
coordinates) over a stretch of at least a hundredth of the scale: two parallel segments sharing
such a stretch (two lines), or a run of three or more consecutive vertices of one on the other
spanning it whose separation at its two ends is no more than ten times its least separation (with a
floor of `1e-15` of the coordinates). Two coincident curves differ by rounding all along the run;
a contact of high order grows from its middle to its ends, so `x⁹` against the axis is not an
overlap however long its run. Overlapping curves have no intersection: every member of the family
is undefined, its anchor kept. A count of seeds or candidates never decides it.

**Matching a family.** Each member has an index, its place among the candidates, and an anchor:
where it last was, whether that was a tangency, and its approach, `Approach(t, x, y)`, where it
last was a simple root (the parameter on the first curve, NaN on a curve with none, and the world
point). The anchor is part of the intersection's record, so it is saved with the document and
restored by an undo; for the persistence codec, `approach` is absent or a record of three numbers.

Positions along the first curve are its parameter, or on a curve with no parameter the arc length
along its current polylines (branch after branch, a point projected onto the nearest segment). A
parametrised curve whose two ends are one point is closed, its period the domain; a curve with no
parameter is closed when it is one branch whose ends meet, its period that branch's length; a
family on several branches is ordered branch by branch without wrap-around. Arc positions assume
the sampler traces a contour in the same direction from frame to frame (3c's marching squares
keeps the negative side on the left); rotations of the order are tried, a reflection is not, and a
contour whose tracing direction reversed between frames would reverse a family's order on it. This
is to be tested once the real contour exists.

Between tangencies the roots of a continuously deformed pair cannot exchange their order along the
first curve, so identity is order, not distance. The matching is `Families`, given the members'
anchors and indexes and the candidates, so any family of points on a curve is tracked by the same
rules:

| Rule | Case | Result |
|---|---|---|
| M1 | the curves overlap, or there is no candidate | every member undefined, its anchor kept where it vanished |
| M2 | members whose anchors coincide within the merge distance (after a tangency, or a shared vanishing point) | a cluster, ordered by the side each approached from (the offset of its approach's position from the anchor's, cyclic on a closed curve, in (−period/2, period/2]), or by index when a member has no approach; the cluster is one block in the order |
| M3 | members with an anchor | ordered by their anchors' positions (clusters as blocks), matched to the candidates ordered by theirs by an order-preserving matching (a dynamic programme): as many matches as there can be, then the least total distance from anchor to candidate, a cluster member charged `1e-9` of the scale for a candidate on the other side from the one it approached from; on a closed curve every rotation of the candidates is tried |
| M3a | the cost model at the ends (the edge rule): a member whose anchor lies outside the range the first curve was sampled over, or within the penalty of an end of it where the window cut the curve, is free | the penalty is half the smallest distance between adjacent candidates, at most an eighth of the sampled range's length (which alone sets it with one candidate), never below the merge distance. A free member may be left unmatched, undefined with its anchor kept, at the penalty; every candidate left new costs the penalty; a member matched costs its distance, plus the penalty if it was undefined after the last recompute, so it re-attaches only to a candidate no defined member claims within the penalty. The members that are not free are still matched as many as can be first (M3). It never applies on a closed curve, sampled whole, nor at an end of the path's own domain (a segment's end). At an exact tangency within the penalty of a window cut the second member is undefined for that frame and re-attaches as the roots separate, inside the off-screen margin of an ordinary graph. The continuity the rule gives is for continuous motion: a single step of nearly one candidate spacing (a redo across frames) relabels by nearest anchor, which no matcher can tell apart without the frames between |
| M4 | a member left over, every candidate taken, its anchor inside the sampled range | it shares the nearest tangency, if there is one, else it is undefined with its anchor kept |
| M5 | a member with no anchor yet (a new one) | the candidate at its index if it is free, else the tangency nearest its index, else undefined |
| M6 | after matching | the anchor moves to the candidate; on a simple root the index becomes its place and the approach that point; on a tangency both are kept |

The cases these give:

- a pan, where a place leaves the sampled range on one side and another comes on the other: the
  member whose place left is undefined with its anchor (an intersection is still found from its
  anchor, where the curves have a parameter, so it stays), the others keep their places, and the
  newcomer is a new place; matching as many as possible would have shifted the whole family along
  by one place. Within the range a root may move further than the gap between candidates in one
  frame (near a tangency), so the rule applies only at the range's ends;
- a translating slider (`sin(x − a)`, `a` up by 0.5 a frame and back): each defined member moves
  with its place, and a member undefined since its place left never takes a place another member
  tracked, however near its stale anchor is, since re-attaching costs the penalty besides;
- one feature in view (a maximum of `sin x` while the window pans by one a frame): with one
  candidate the penalty is an eighth of the sampled range, so the member does not jump a whole
  period to the next maximum but goes undefined and returns when its own comes back;
- two simple roots moving, even past both anchors in one frame (a drag starting near a tangency):
  each member keeps its place in the order, so they never swap;
- the order of the roots swapping by the parameter on a closed curve (an ellipse rotated against
  a line): the matching tries every rotation and keeps the members on their roots;
- two roots merging at a tangency: both members are defined at the one point;
- the roots separating again, symmetrically or not: the anchors coincide, so the members are a
  cluster and take the roots in the order of the sides they approached from; when fewer roots
  return than there are members, the side decides which member keeps one;
- a tangency at a closed curve's seam: the cyclic offset keeps the sides, where an order by the
  parameter would flip them;
- the roots vanishing: the members are undefined and anchored where they vanished; when the
  curves meet again, at the same place or elsewhere (a line rotating through one tangency to
  another), they are matched again in order, by side within a cluster, and never both to one root
  while another is free;
- a multiple root (the triple root of `x³ − a·x` against the axis at `a = 0`, reached any number
  of times): one candidate, a tangency, however its seeds converged, shared by the members that
  would otherwise have none and only while no candidate is free; when it splits again each member
  takes its own root in order;
- a family of three or more: the same.

### Critical points

A critical point is a root, a local minimum or a local maximum of a function graph
(`CriticalPoint`, its `Feature`), tracked like an intersection (`CriticalPoints`): the critical
points of one graph and one feature are a family, and the same rules M1 to M6 match them, through
`Families`, to the candidates, which are the graph's features found now (`Features`, **Tools**)
ordered by abscissa. The abscissa is the position along the curve, the parameter `t1` of the anchor
and the approach (`t2` is NaN); a graph's domain is never closed, so no rotation is tried. Features
within the sum of their spreads (the width of each one's last bracket) and the merge distance of
each other are one multiple root, a tangency, as is a root where the graph touches the axis
without crossing it (`x²` at 0); several members may share it. The features are found over the
polylines the graph was sampled to, the window widened by half, so a feature that a pan takes
beyond them is undefined, its anchor kept, until it comes back.

`Candidates` gives the places a family has now inside the visible window, as its recompute finds
them: the intersections of two paths (none when they overlap) and the features of one kind of a
graph (a multiple root once), each by its index among all the candidates over the widened window,
so a tool creates one member per place it shows, with that index.

The cases these give: the roots of `x³ − a·x` as `a` goes from 1 to −1, the outer two
undefined once their roots are gone and the middle one kept at 0, all three back in order when `a`
returns; the two roots of `x² − a` merging at the touching root at `a = 0` (shared), undefined
below it, and taking the separated roots in the order of the sides they approached from; the minimum
and the maximum of `x³ − a·x` vanishing together at the inflection and returning in place; the
roots, minima and maxima of one graph being three families, each with its own indexes.

### In the document

The integration series, not this one, wires the model into the app (`docs/reference/app.md`).
`DocumentState` gains a `CanvasState canvas` beside the cells. Canvas commands (add, replace,
remove, move a point, set a slider) map one state to the next through the pure functions here, so
the command stack's whole-state snapshots cover objects, anchors and the id counter, and a drag
merges per object through its `mergeKey`. Geometry is a derived output, like a cell's `Output`:
recomputed on the worker with a sampler bound to the environment after the last cell plus the
sliders and with the job's `Cancellation`, and entered through `amend` under the generation rule,
for objects whose definitions have not changed since (`changedSince`). An undo restores the
anchors, so an intersection's or a critical point's identity after an undo is what it was. Sliders
are bound into the
environment as exact definitions before the first cell; a changed cell passes the names it
changed, closed over the definitions that use them, as `changedVariables`. Persistence saves the
objects and `nextId`, not the geometry.

## Sampling

Package `mathcanvas.canvas.sample`: the object model's `Sampler` over the engine's compiler
(`docs/reference/engine.md`, **Compilation**). The model never evaluates an expression itself.

**Samplers and the memo.** `Samplers` is one instance per document, as the inspector's memo is, and
only the document's worker touches it; it is never static. `samplers.over(environment, settings)`
binds a `Sampler` to the environment of the moment (the worksheet's definitions and the sliders'
values), so a slider's change makes a new sampler; `over(environment, settings, width, height,
cancellation)` also gives the view's size in pixels and binds a job's cancellation, under which
every exact evaluation and compilation of an expression runs: `value` and `point`, which take no
token, stop with the job (a point landing on a path, a slope), and `sample`, `contour` and `region`
stop on either their own token or the bound one, their loops included. The two shorter overloads
bind nothing that stops them, for callers with nothing to cancel. Every sampler of a document shares
the `Samplers`' memo of compiled evaluators: at most 256, keyed by the evaluated value, the
arguments and the angle mode, the least recently used dropped first. An expression the change leaves
alone is not compiled again. Each sampler also keeps, for its own life, the evaluator of each
expression as written, so a path's point (which intersections and points on paths ask for often) is
never evaluated twice.

**Evaluation.** An expression is evaluated by the engine in the environment with the object's own
variables unbound (a graph's `x`, a curve's `t`, a polar `θ`, an implicit curve's `x` and `y`, a
slope's point), so they shadow a worksheet definition of the same name. The value is then compiled
as a function of those variables in the document's angle mode. A polar curve is compiled as the
pair `r(θ)·cos θ`, `r(θ)·sin θ`, so degree mode reads `θ` in degrees as it reads every plain angle:
`θ` is in the document's unit everywhere, its domain included. `PolarCurve.TURN` is `[0, 2π]`, the
radian default, and `PolarCurve.turn(mode)` is one turn in the mode's unit (`[0, 360]` in degrees,
`[0, 400]` in gradians), which a tool or an entry creating a polar curve uses as its domain.
An implicit curve or a region is compiled as `left − right` in `x` and `y`. Where evaluation fails
(an undefined function, a cycle) or the compiler refuses the value (a free symbol, a complex
constant, a form), the expression is undefined everywhere: its value is NaN, its point undefined,
and it has no polylines.

**Curves.** A function graph, a parametric curve and a polar curve are sampled adaptively over the
range `Recomputes` gives:

- a uniform grid of 256 samples, then each segment is halved while the two halves bend by more
  than half a degree on the screen, or an end of it is undefined, down to a minimum step of the
  range over 2¹⁴; the midpoint of every segment of the grid is a sample, so a straight line within
  the window has 511;
- the screen is the window the curve is drawn in at the view's size in pixels, or at a nominal
  1,000 by 1,000 pixels when the caller gives no size, so on a view of extreme aspect the flatness
  is only approximate; the integration may hand the view's size if that shows;
- a segment whose three samples lie beyond one side of the window widened by half its size on
  each side is not halved;
- the curve is split into polylines at an undefined sample (a domain's edge is found to within the
  minimum step), at an asymptote, and where a run of segments lies outside the widened window, the
  sample just beyond its edge kept so the curve reaches it;
- an asymptote is a segment of the minimum step that the last halving did not shorten below three
  quarters of its parent's length on the screen and whose ordinate either changes sign or jumps by
  more than the window's height (the visible window, not the widened one), or one whose two
  ordinates have opposite signs and both lie beyond the window's height. The ratio is the pole's
  signature: across a pole the half that holds it jumps more than its parent did, since its ends
  are nearer the pole, while a continuous curve's half jumps about half as much, however steep
  (`10⁶x`, `tanh(1000x)`) or flat (`0.001 sin x`) it is. A fixed jump in pixels was the first
  rule; it joined the poles of `1/(x − a)` once the window grew, since the minimum step grows with
  the window and the jump across the pole shrinks with it;
- every sample runs the compiled evaluator only, with no allocation, and the cancellation is
  checked every 1,024 samples.

**Contours.** An implicit curve is traced by marching squares over the window `Recomputes` gives
(the window widened by half): a grid of 128 by 128 cells, the zero placed on each cell edge by
linear interpolation, and every cell the zero crosses refined once into 8 by 8 subcells. A refined
cell's border values are interpolated from its corners, not evaluated, so its crossings on the
border are exactly its neighbours'; its interior is evaluated. An ambiguous saddle is resolved by
the sign at its centre. Every segment runs with the negative side on its left, decided by the
corners' signs alone, and segments are joined into polylines by the cell edge their ends lie on,
which is exact, with no distance tolerance; a closed curve ends where it starts. A cell with an
undefined corner is skipped, and a segment is dropped where the field's magnitude at its midpoint
exceeds every corner's: a pole the sign changes across (`y = 1/x`), not a zero.

**Regions.** An inequality is traced the same way, as the zero of `left − right` with the sign
turned so the region is negative, over the window widened by half (the sampler widens it, as
`Recomputes` widens a contour's), on the grid inside a border of positive values. Its boundary
therefore closes along the widened window's edge, off the screen, into rings with the region on
their left: outer rings turn counterclockwise and holes clockwise. A region is the inside of its
rings by the even-odd rule, and the painter fills by the non-zero rule (`Painter.fill`); with that
orientation the two agree, so a fill draws the region exactly. An undefined value is outside the region. Strict and non-strict inequalities
have the same rings; the drawing tells them apart.

## Graph kinds

`Recomputes.standard()` (**Object model**) gives every kind its geometry through the sampler: a
function graph over its domain clipped to the widened window, parametric and polar curves over
their domains, implicit curves as contours and inequalities as regions. `CanvasContent.build(state,
labels, theme)` turns a state's geometry into the view's `PlotContent`, in drawing order, and
returns it with the object behind each hit item (`Built.items`):

- a curve's branches become one polyline broken by gaps, stroked in the object's style;
- a region becomes a fill in its colour at a quarter opacity and its rings stroked as its
  boundary, dashed for `<` and `>`, solid for `≤` and `≥` unless the style is dashed;
- a valued region is filled and stroked the same way, its rings a hit item, and its label (from
  `labels`, the value text the app formats, `A = …` or `S = …`) written in the theme's text
  colour on a small rounded box of the theme's background at three quarters' opacity
  (`TextStyle`'s `background`, drawn by both painters, the SVG one sizing the box at 0.6 of the
  font size per character), centred at the centroid of its rings by unsigned area, or the middle
  of their bounds when they enclose almost nothing, the box moved inside the view when it would
  cross an edge (`ScreenPainter.inside`), whatever the style's label flag;
- a point becomes a disc of radius 2 pixels plus its width, with its label (from `labels`) 6
  pixels up and right in its colour when the style shows the label;
- a text is drawn at its anchor in its colour, 12 pixels high;
- a slider and an undefined object draw nothing.

An object's `Style` maps to the drawing: its colour slot to the theme's series colour (so content
is rebuilt when the theme changes), its width to the stroke width, its dashed flag to a dash of
three widths and a gap of two. A hidden object keeps its geometry, so its dependents still
recompute, but is neither drawn nor hit.

## Tools

Package `mathcanvas.canvas.tool`, free of JavaFX (the boundary checks cover it), so the tools are
tested headless. A `Tool` is a
small state machine fed the pointer's events in world coordinates (`press`, `drag`, `release`,
`move`, `cancel`), each with the `Scene` of the moment (the canvas with its geometry, the visible
window, and `Slopes`, the slope of each function graph from evaluators compiled on the worker) and
a `Pointer` (the position and the object under it within the hit tolerance, points first). A tool
never touches the document: it gives `Edit`s to a sink, which the app turns into commands, and
paints its preview on the overlay from the last event, in the theme's colours. A press a tool does
not take passes to the view, which pans; `cancel` (Escape, or another tool chosen) abandons a
construction in progress.

- `Edit.Add` adds objects as one step, with consecutive ids from the canvas's next one; each is
  made from the ids of the addition's objects up to it, so a later object names an earlier one,
  and a point may ask for the next free name. `Edit.Move` moves a point, a free one to a position
  and a point on a path to a parameter. `Edit.Intersect` (two paths, the lower id first) and
  `Edit.Find` (a function graph and the features wanted) need the worker: the app finds what is
  there now and adds one object per place found.
- `Snap.of` is what a click would use: an existing point under the pointer, else the point of a
  curve under it (`PathProjection`: the nearest point of the curve's samples, with the parameter
  interpolated between the two samples of its segment), else a free point at the pointer. The
  previews ring an existing point, mark a point on a curve in the new object's colour, and show a
  free point faintly.
- `PointOnCurveTool`: a click on a curve adds a point on it, named, and dragging on moves it along
  the curve; a press on an existing point on a curve drags it.
- `TangentTool`: a click on a function graph adds a point on it and the tangent there, one step; a
  click on an existing point on a graph adds the tangent alone. The tangent is a `Line.PointSlope`
  whose slope is the graph's derivative left unevaluated, `diff(body, x)`, with `x` bound to the
  point's abscissa: the sampler's evaluation differentiates it on the worker, in the environment,
  so the line follows the point as it is dragged and the graph as its sliders move, and it is
  undefined where the derivative is not finite (a vertical tangent). It copies the graph's body,
  so it does not follow a later redefinition of the graph. Only function graphs have tangents in
  Phase 3; parametric and polar tangents wait for a line through a point with a direction. The
  preview draws the tangent dashed at the slope the scene gives.
- `IntersectionTool`: a click on a path, then on another, asks for their intersections, the lower
  id first whichever was clicked first, so a pair is always one family; the preview highlights the
  first path and the one under the pointer.
- `FeatureTool.roots()` and `FeatureTool.extrema()`: a click on a function graph asks for its
  roots, or its minima and maxima.

**Features.** `Features.of(curve, feature, sampler, cancellation)` finds a function graph's roots,
minima or maxima from its sampled polylines, each refined on the compiled evaluator: a root where
the sampled ordinate changes sign (or is zero at a sample), refined by bisection on the function;
an extremum where the samples turn, refined by bisection on the engine's derivative of the body, or
by golden section where the derivative does not change sign there (a corner, `abs(x)`). Bisection
goes on to a double's resolution, so a root's ordinate is the function's rounding there; golden
section stops at 1e-12 of the abscissa's magnitude (`Features.PRECISION`). A sign change across a
pole is no
root, since the refined ordinate is then larger than either sample's; a graph touching the axis
(`x²` at 0) has one root there, the extremum whose refined ordinate is zero to within 1e-12 of the
largest sample, unless a crossing lies in its bracket; it is marked multiple, as is a sampled zero
between two samples of one sign. Each feature carries its spread, the width of its last bracket,
which the critical points' tracking merges by (**Object model**, **Critical points**). A domain's
or a branch's end is never an extremum. A run of two or more zero samples is an interval where the
graph is zero (`0·x`, `floor(x)` on [0, 1)), not a set of roots: it gives none, nor does a turn
whose bracket holds one, so the Roots tool refuses such a graph with its no-roots message. A root
nearer a zero run's end than one sample (`max(0, x)·(x − 0.001)` at 0.001) is below the sampling
resolution and is not found.

## In the app

The app shows the canvas beside the worksheet (`docs/reference/app.md`, **The window**): a
`CanvasView` in the app's `CanvasPane`, with the algebra panel and a toolbar. The view is used as it
is: the app sets its content and viewport, listens to its viewport and hovered hit, and adds its
own pointer handling as event filters on the view, which run before the view's own handlers. A
press the app takes (on a point to drag, or with the point tool) is consumed there, so the view
does not pan; any other press reaches the view, which pans. The app draws the selected object's
halo through `overlayProperty()` and repaints the overlay with `invalidate(Layer.OVERLAY)`. The
colours the app sends to the content builder are `CanvasView.theme()` after the view's CSS is
applied, so content and grid follow the same theme. A click or a drag finds its object through the
frame's map from hit items to object ids, and a point on a path is placed and dragged at the
parameter interpolated between the samples of the path's polylines (`Polyline.t(k)`). File →
Export draws through `Export` with the view's viewport, theme or the light one, content and
`labelWidth`; the canvas objects are saved in `session.json` (`docs/reference/app.md`, **The
files**).

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
- `view/ExportTest`: a golden SVG of the plane (`src/test/resources/mathcanvas/canvas/view/
  plane.svg`: the background, the grid, a polyline with a gap and a point), the background first
  and the objects last in another theme, and the image size limit;
- `hit/HitIndexTest`, `hit/HitIndexProperties`: projection, the anisotropic metric, points first,
  gaps, ties, a segment whose pixels overflow never hit; over random walks with gaps and far jumps,
  random points, any aspect and queries near the entries, the tree against a scan of every entry
  (the pruning), and both against an oracle written independently in the test, a plain
  point-to-segment distance in pixel space with the same priority (the metric).
- `model/`, headless: the records and their reads and checks; geometry, polylines, windows and the
  stub sampler; the state's edits, cycles, slider edges, families, cascading removal, geometry-kind
  checks, undefined propagation and cancellation; `CanvasStateProperties` over random constructions,
  in creation order and shuffled (recompute visits exactly the closure of the changed objects and
  variables, families whole, each object once and after its parents; pointing an object at a
  dependent is the reported cycle); each kind's recompute; intersection candidates, overlaps, nearly
  parallel curves at fine sampling, a zoom out over many roots, tolerances in a small window,
  cancellation, tangency, triple roots, contact of high order against genuine overlap, the cost of
  near-parallel curves, arc positions, off-window tracking, a pan keeping every member in place
  and the edge rule leaving a place that left the sampled range unmatched rather than shifting the
  family, a translating slider over a cut contour up and down with no stale anchor taking a
  tracked place, the matching's free rows and new columns; and `DegenerateCasesTest`, the Phase
  3 acceptance sweeps with the asymmetric, seam and three-member cases and the drags past both
  anchors, each frame asserting identity by object id and continuity as no jump larger than the
  branch's own movement;
- `sample/SamplersTest`: a graph's ends and samples, refinement where it bends, the environment
  substituted and shadowed, the shared and bounded memo, undefined and refused expressions, a
  domain's edge, a pole, the review's poles (`1/(x − a)` in ±50 and ±100, `0.01/(x − a)` in ±10),
  continuous curves never cut (steep, flat, kinked and jumping ones in ±1, ±10 and ±100), a steep
  line, the window cut, degrees, a closed parametric circle and polar rose, cancellation (a point
  and a value stopping on the token the sampler is bound to, a slow expansion's point within its
  deadline), the unit circle's contour (closed, within two cells), a contour across a pole, the unit
  disc's area within 3% of π, a region closed along the window's edge and one with a hole;
  `sample/SamplerProperties`: over random polynomial and trigonometric graphs and windows, every
  sample on the curve within 1e-9 of the compiled value at its x; no polyline across the pole of
  `c/(x − a)` or a pole of `c·tan x`, in windows up to ±100 and for `c` from 0.01 to 1,000; sampling
  stopping within the minimum step of a domain's edges; `sample/GraphKindsTest`: every kind through
  `Recomputes.standard()` with the real sampler, a polar rose whole over `PolarCurve.turn` in each
  angle mode, and a slider's change moving its readers and their dependents;
- `tool/PathProjectionTest`: the parameter interpolated on the nearest segment, a path with none;
- `tool/ToolsTest`: each tool over scripted clicks with the real sampler: the snap's order, the
  tangent to `x²` at 1 with slope 2 becoming 4 when a scripted drag moves its point to 2, the
  tangent at an existing point, the intersection tool's two clicks with the lower id first and
  Escape, the intersections of `x²` and `x + 2` at −1 and 2 by ordinal, the feature tools' edits,
  placing and dragging a point on a curve, and the tangent's preview in two themes;
  `sample/CriticalPointsTest`: roots vanishing under a slider and re-attaching in order, two roots
  merging at a touching root and separating in order, a minimum and a maximum annihilating and
  returning, one family per feature, a critical point refused on anything but a function graph, a
  pan leaving the place that left and keeping the others, a translating slider moving each member
  by its step, a zoom out keeping every member, a zoom in leaving a member past the range that
  returns in place, the slider swept up and down with no stale anchor taking a tracked root, and
  one maximum in view panned a period without jumping;
  `sample/CandidatesTest`: the intersections of two graphs, none for two that do not meet, a
  graph's roots, minima and maxima, a touching root once, and only the places inside the window;
  `sample/FeaturesTest`: the roots of `x³ − x` at −1, 0 and 1 and the extrema of `sin x` at
  π/2 and 3π/2 within 1e-10, an extremum near 1000 to the relative precision, touching roots (one,
  multiple), two near roots, poles, a corner and a monotonic graph;
- `view/CanvasContentTest`: the colour slots in the light and the dark palette, strokes, dashes,
  a strict region's dashed boundary, labels and texts, a hidden object neither drawn nor hit, and
  a curve's branches joined by gaps.

In `canvas/src/uiTest/java/mathcanvas/canvas/view/` (run by `tools/ui_tests.sh`): `CanvasViewTest`
shows the view in a harness window and checks three layers the size of the view, a pan and a
resize repainting every layer, content repainting the objects layer alone and a hover the overlay
alone, a scroll zooming about the pointer, and the grid in both themes with each axis one pixel
row or column of the axis colour, writing `canvas-grid-light.png` and `canvas-grid-dark.png`; and
`CanvasBenchmarkTest` and `GraphBenchmarkTest`, the benchmarks above; and `CanvasKindsTest`,
which recomputes one object of every kind with the real sampler at the view's window and size,
draws it in the light and the dark theme with colour slots, widths, dashes and labels, checks that
the hidden graph's colour is nowhere and a region's fill is its slot's colour over the background,
and writes `canvas-kinds-light.png` and `canvas-kinds-dark.png`; and `ExportImageTest`, which
exports images at scales 1 and 2, checking their size, the background in two themes and the
objects' pixels, and writes `canvas-export-2x.png`.
