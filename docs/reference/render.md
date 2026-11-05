# Render

How `render/` typesets: the engine's layout tree (`docs/reference/engine.md`, **The layout
tree**) and the bundled font's metrics to a box tree, and the box tree to JavaFX nodes. Read
`CLAUDE.md` first. The module `mathcanvas.render` requires `mathcanvas.engine` and
`javafx.graphics`, and exports three packages, one per layer:

| Package | What | JavaFX |
|---|---|---|
| `mathcanvas.render.font` | the font reader: metrics, character map, outlines, MATH, script alternates | none |
| `mathcanvas.render.typeset` | the typesetter: layout tree to box tree | none |
| `mathcanvas.render` | `MathView`, the box tree as nodes; `MathFont`, the font loaded into JavaFX | yes |

The first two are pure Java, so their tests run without a display and the canvas's painter can
read the box tree without the view. The build checks that: `render`'s own `checkJavafxFree` over
those packages, which the gate's boundary step reports (`docs/reference/testing-tools.md`, **The
module-boundary check**).

## The font

The bundled font is STIX Two Math, the unmodified `STIXTwoMath-Regular.ttf` under
`mathcanvas/render/fonts/` with its OFL beside it (`MathFontResourceTest` checks its hash). JavaFX
25 has no public access to glyph identifiers, advances, outlines or the MATH table, and most of
the font's size variants and assembly parts have no code point, so `render` reads the file itself.
`OpenTypeFont.stix()` reads it once; `OpenTypeFont.read(bytes)` reads any TrueType-flavoured font
with the same tables.

| Table | What is read |
|---|---|
| `head`, `maxp` | units per em, the `loca` format, the glyph count |
| `hhea`, `hmtx`, `OS/2` | ascender, descender, advances, x-height |
| `cmap` | the (3,10) format 12 subtable: `glyph(codePoint)`, 0 when the font has none |
| `loca`, `glyf` | each glyph's bounding box, and its outline: simple glyphs, and composites with offsets, scales and 2×2 transforms (point-matched components are refused; the bundled font has none) |
| `MATH` | the 56 constants (`MathConstant`, in the table's order and names), italics corrections, and the vertical and horizontal glyph constructions: size variants and assemblies, with the least connector overlap |
| `GSUB` | the alternates of the `ssty` feature, an alternate substitution (lookup type 3, through extension lookups): `scriptGlyph(glyph, level)`, level 1 the script form, 2 the scriptscript form |

The MATH table's top-accent attachments, extended-shape coverage and kerning, and the `flac` and
`dtls` single substitutions, are not read yet: they are for accents, which no layout node needs.
The reader keeps the tables' structure (`Coverage`, `Gsub` by feature) so they are an addition.

An `Outline` is the glyph's contours as `MoveTo`, `LineTo`, `QuadTo` and `Close` commands in font
units, y up, the TrueType implied on-curve points made explicit, filled with the non-zero rule.
Outlines are parsed on first use and cached per glyph, from any thread.

## Typesetting

`new Typesetter(font, fallback).typeset(layout, size, display)` returns the root `Box`. The size
is one em in pixels; `display` chooses display style over text style. The rules are MathML Core §3
over the MATH constants, and TeX's (The TeXbook, Appendix G) where Core is silent. A character the
font lacks is set as a `TextBox`, measured by the `Fallback` (the view measures with JavaFX, the
tests with fixed metrics); nothing throws for want of a glyph.

### Threading and the node cap

`typeset` may run on any thread, several at once. `OpenTypeFont` is immutable once read (final
fields, unmodifiable tables) and shared through `OpenTypeFont.stix()`; its outline cache is a
concurrent map. The typesetter holds no mutable state, and the box tree it returns is immutable, so
a worker can typeset and hand the tree to the FX thread (**The view**). The `Fallback` must be
thread-safe too. `Typesetters.standard()` is the shared typesetter, the one every `MathView` uses:
its fallback measures text the font lacks with a fresh JavaFX `Text` in the default font and keeps
each `(text, size)` measurement in a concurrent map, so it is measured once. A view typesetting on
the FX thread fills the map for the layouts it is given; a worker that meets a text not yet measured
measures it once off the FX thread, with a `Text` attached to no scene, which JavaFX permits. That
first off-thread measurement is the one residual: under a stress run (four workers typesetting
missing-glyph text 500 times each while the FX thread re-typesets every pulse) it gave the same
metrics every time. Worker code that calls `Typesetters.standard()` loads no JavaFX scene class
until such a measurement. `typeset(layout, size, display, cancellation)` checks the
`engine.Cancellation` at every box it builds and places, and a thread interrupt counts as a request,
so a large tree stops within milliseconds with a `CancelledException`.

`Typesetter.MAX_TYPESET_NODES` (20,000) is the most layout nodes a caller should typeset: beyond it
the box tree and its JavaFX nodes cost the window too much, and the worksheet shows the result as
text instead. `Typesetter.wouldExceed(layout)` tells, counting at most that many nodes.

### The box tree

`Box` is sealed: `Group` (children), `GlyphBox` (a glyph and its scale in pixels per font unit),
`RuleBox` (a filled rectangle, the box itself) and `TextBox` (fallback text and its size). Every box
has `x`, `baseline`, `width`, `ascent` and `descent` in pixels, y down, from the left end of the
root's baseline, and the `rule.Path` of the subexpression it shows, or none. A glyph's outline point
`(u, v)` is drawn at `(x + u·scale, baseline − v·scale)`. A glyph's box is its advance across and
its outline's bounds up and down. A group's box is the union of its own extent and its children's,
so every box contains its children. `hitTest(x, y)` is the path of the deepest box with a path that
contains the point; `find(path)` is the outermost box with that path.

### Styles

Four styles, display, text, script and scriptscript, each cramped or not, as TeX's: a superscript
or an upper limit goes to the script style (scriptscript from script), a subscript and a lower
limit too, cramped; a numerator to the next smaller style (text from display), a denominator too,
cramped; a radicand is cramped, an index scriptscript; matrix and piecewise cells are in text
style. Script sizes are `scriptPercentScaleDown` (70%) and `scriptScriptPercentScaleDown` (55%) of
the base size, not compounded, and use the `ssty` glyphs.

### Rows and spacing

A row's atoms are spaced by TeX's table (The TeXbook, ch. 18) on their classes: thin 3 mu, medium
4 mu, thick 5 mu, with a mu one eighteenth of the style's em, and the parenthesised entries left
out in the script styles. A binary sign first in a row, or after a binary sign, an operator, a
relation, an opening fence or punctuation is ordinary (unary `−x`), and so is one before a
relation, a closing fence or punctuation, or last. A nested row adds no spacing of its own: its
atoms are spaced as part of the enclosing row, and it is a box only for selection. A fenced body
or a matrix is an opening fence to its left neighbour and a closing fence to its right one, so a
call `sin(x)` takes no space after the name. A `LINEAR` space has no width, the classes spacing
its neighbours, except between two names of which one has several letters, where it is a thin
space (as `\mathrm{ab}\,\mathrm{cd}`), so the product `ab cd` and the name `abcd` stay apart;
between a number and a name (`2x`) there is none. A `THIN` space is 3 mu. An
italic glyph followed by anything but an italic glyph is followed by its italics correction.

Characters: `-` is set as U+2212 and `·` as U+22C5; an italic Latin or lower-case Greek letter as
its Mathematical Italic code point (`h` as U+210E). A unit (the layout's `UnitText`,
`docs/language.md` §14.26) is set upright, as an upright text is, without its underscore: `km`,
`µm`, `Ω`, `°C`; the solidus between units is an ordinary glyph, so `5 km/h` stays on its line.

### Constructs

| Construct | Rule | Constants |
|---|---|---|
| Fraction | the bar centred on the axis; the numerator raised by the larger of its shift and the gap above the bar plus its depth, the denominator lowered likewise; both centred; padding each side | `fractionNumerator(DisplayStyle)ShiftUp`, `fractionDenominator(DisplayStyle)ShiftDown`, `fractionNum(DisplayStyle)GapMin`, `fractionDenom(DisplayStyle)GapMin`, `fractionRuleThickness`, `axisHeight` |
| Scripts | the superscript after the base's italics correction, the subscript at its advance (a large operator's: the superscript at the advance, the subscript tucked left by the correction, below); shifts as Core's, then the gap between them, then the superscript raised to its highest bottom with the subscript lowered by as much less; space after | `superscriptShiftUp(Cramped)`, `superscriptBaselineDropMax`, `superscriptBottomMin`, `subscriptShiftDown`, `subscriptBaselineDropMin`, `subscriptTopMax`, `subSuperscriptGapMin`, `superscriptBottomMaxWithSubscript`, `spaceAfterScript` |
| Radical | the sign grown to the radicand plus gap plus rule, any excess height split into the gap; the bar from the sign's advance over the radicand; an index tucked over the sign | `radical(DisplayStyle)VerticalGap`, `radicalRuleThickness`, `radicalExtraAscender`, `radicalKernBeforeDegree`, `radicalKernAfterDegree`, `radicalDegreeBottomRaisePercent` |
| Fences | stretched only when the body is at least the least delimited height; then to `max(2δ·901/1000, 2δ − 0.5 em)` with `δ` the body's larger extent from the axis (TeX's factor and shortfall); the smallest variant that reaches it, else the assembly, else the largest variant; centred on the axis | `delimitedSubFormulaMinHeight`, `axisHeight`, `minConnectorOverlap` |
| Big operator | in display style the first variant reaching the display operator height, else the largest (`∫` its 2347-unit variant, `∑` its largest, 1326); centred on the axis; `∑`, `∏`, `lim` with limits below and above in display style, as scripts in text style; `∫` always with scripts; the body after it with operator spacing. In this font an operator's advance already covers its ink (the display `∫`: advance 1032, ink to 957, italics correction 540), so as scripts the upper bound goes at the advance and the lower bound is tucked left by the correction, TeX's rule for an operator with scripts | `displayOperatorMinHeight`, `upperLimitGapMin`, `upperLimitBaselineRiseMin`, `lowerLimitGapMin`, `lowerLimitBaselineDropMin` |
| Matrix | cells centred in columns, rows on a strut, the table centred on the axis, between stretched brackets | `axisHeight` |
| Piecewise | a stretched brace, then the values and conditions left-aligned in two columns, the last row the value otherwise and the word *otherwise* (from `mathcanvas.render.typeset.messages`, key `piecewise.otherwise`) | `axisHeight` |

A subscripted name raised to a power (`x₁²`) has its subscript and superscript stacked on the
base as TeX sets `x_1^2`, the name still a box with its own path.

An assembly (Core §5.4) takes the fewest repetitions of its extenders that reach the size with the
least overlap, then spreads the overlap so the size is met exactly, never below
`minConnectorOverlap` nor above the connectors' lengths. Each part is placed by its ink: its ink's
bottom at the bottom of its full advance, whatever its origin. In the bundled font 22 of the 83
parts of the 32 vertical assemblies have ink below their origin (the bar `|` and `‖` by 220 units,
`∣` by 194, the vertical arrows by 160, the radical's bottom by 5). The assembled glyph's height
and depth are its parts' ink.

The constants with no MATH equivalent are named on `Typesetter`, all in ems:

| Constant | Value | Source |
|---|---|---|
| `FRACTION_PADDING` | 0.12 | TeX's `\nulldelimiterspace`, 1.2pt at 10pt, each side of a fraction |
| `COLUMN_GAP` | 1.0 | between matrix columns (twice `\arraycolsep`) and piecewise columns (`\quad`) |
| `STRUT_HEIGHT`, `STRUT_DEPTH` | 0.84, 0.36 | TeX's array strut: 0.7 and 0.3 of a 12pt baseline at 10pt |
| `DELIMITER_FACTOR` | 901 per mille | TeX's `\delimiterfactor` |
| `DELIMITER_SHORTFALL` | 0.5 | TeX's `\delimitershortfall`, 5pt at 10pt |

## The view

`MathView` is a `Region` showing one layout tree. Its properties are `layout` (null shows
nothing; any change, by a setter or a binding, clears the selection), `fontSize` (pixels, default
20; below `MIN_FONT_SIZE`, 4 px, or not a number, it is drawn at 4 px), `displayStyle` (default
true) and `selection` (a `rule.Path` into the printed tree, or null). The view is touched only
on the FX thread, like every node.

Its content is whichever was set last. A `layout` is typeset on the FX thread, again when the size
or the style changes, which suits callers with no worker (tests, small labels). `setBox(box)`
shows a box tree typeset elsewhere and only builds its nodes; it sets `layout` to null, so the box
is then the content (it throws, as any setter does, if `layout` is bound). A worker typesets the
layout with `Typesetters.standard()` (`MathView.typesetter()` returns the same instance) and the
job's token (**Threading and the node cap**), and the FX thread builds the nodes, about 4-10 ms for
a thousand-node tree. A box set this way is not re-typeset when the size or the style changes;
whoever typeset it typesets it again and sets the new tree, and setting a layout afterwards, even
the one shown before, typesets it. Setting either clears the selection; selection, hit-testing and
drawing are the same for both paths.

It draws every glyph into one `Path` (style class `math-glyph`), every bar into another
(`math-rule`), fallback text as `Text` nodes (`math-glyph`), and the selection as a `Rectangle`
behind them (`math-selection`), the outermost box with the selected path. Each glyph is scaled and
placed at draw time from the font's outline cache, which holds each glyph once in font units; there
is no cache per size, since a thousand-node view builds in about 15-30 ms without one. A worksheet
that redraws many large cells often may measure again and add a bounded cache then. The pref and min
sizes are the box tree's, plus insets; `getBaselineOffset()` is the root's ascent, so a view aligns
on its baseline in a row of nodes.

**Theming.** The user-agent stylesheet `math-view.css` fills glyphs and bars with the looked-up
colour `-math-ink` and the selection with `-math-selection-fill`, defined on `.math-view`. A theme
sets them on `.math-view` in the scene's stylesheet, which wins over the user-agent stylesheet;
setting them on `.root` alone does not reach the view, since the closer `.math-view` definition is
found first.

**Selection.** `hitTest(x, y)` (view coordinates) returns the deepest subexpression's path at a
point; `select(path)` sets the selection; a click selects the path under it, or clears the
selection on empty space. `box()` and `origin()` give the box tree and where its origin sits in
the view, and `selectionBounds()` the highlight's bounds.

## Tests

In `render/src/test/java/mathcanvas/render/`:

- `MathFontResourceTest`: the bundled font is the unmodified upstream file, its licence beside it;
- `font/OpenTypeFontTest`: the reader against values parsed from the file independently: the
  headers, character map, MATH constants, variants and assemblies, italics corrections, `ssty`
  alternates; every glyph's outline against the box `glyf` records for it (within a unit for the
  rounded composites); a composite as its components; every assembly part's ink within its full
  advance;
- `typeset/TypesetterTest`: positions against the MATH constants at 1000 px, where a pixel is a
  font unit: the fraction bar on the axis and its gaps, script shifts (cramped too), fences
  covering their body, an assembled fence, stretched bars centred on the axis and as tall as
  parentheses, the radical's rule and gap, the limits' gaps, the integral's display variant and
  its bounds at the advance and tucked by the correction (and a text-style sum's), matrix columns,
  the piecewise brace, class spacing and the thin space between names, the fallback, and a
  thousand-node expression within the 120 s whole-run budget;
- `typeset/TypesetterThreadsTest`: eight threads typeset the same thousand-node layout at once,
  each tree equal to the one typeset alone; a request stops typesetting at every box, and an
  interrupt does; the node cap counts layout nodes;
- `typeset/BoxProperties`: over random layout trees of every construct, in both styles, every box
  contains its children, and hit-testing a box's centre finds the box or one inside it wherever no
  box outside its line of descent covers the centre; and a list of parsed expressions typesets.

`app/src/uiTest/java/mathcanvas/app/MathViewTest` (run by `tools/ui_tests.sh`) shows each
construct in a harness window and writes `app/build/ui-screenshots/math-view.png`, selects a
subexpression with a synthetic mouse event fired at the view (no robot, so no macOS Accessibility
permission), writes `math-view-selected.png`, clears the selection with a click on empty space,
builds a thousand-node view within the budget, and checks that a new layout through a binding
clears the selection and that a size of 0 is drawn at the minimum. A box typeset on the test
thread and set with `setBox` draws pixel for pixel as the same layout typeset by the view, hits
and selects alike, and is not re-typeset by a size change; a layout, a box and the layout again
each become the content in turn; four workers typeset missing-glyph text 500 times each, equal to
a reference, while the view re-typesets every pulse; the nodes of a thousand-node box build within
the budget.
