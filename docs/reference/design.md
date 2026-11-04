# The design: Material 3 over JavaFX

The app follows Material Design 3 (the owner's choice of 2026-11-04 from five mocked options),
adapted to a desktop window and to JavaFX's controls. This file is the specification every UI
change follows: the tokens, the type, the spacing, and how each part of the app maps to a
Material component. The CSS lives in `app/src/main/resources/mathcanvas/app/app.css`; the
tokens below are its variables (`-mc-*`), one set per theme.

## Tokens

| Token | Light | Dark | Used for |
|---|---|---|---|
| `-mc-surface` | `#FEF7FF` | `#141218` | the window background |
| `-mc-surface-container` | `#F3EDF7` | `#211F26` | cards: cells, the canvas card, the Variables card, the keypad keys, the navigation rail |
| `-mc-surface-container-high` | `#E6E0E9` | `#2B2930` | the digit keys, hover on a card |
| `-mc-secondary-container` | `#E8DEF8` | `#4A4458` | the selected state of a rail item or a toggle, the slider track |
| `-mc-primary` | `#6750A4` | `#D0BCFF` | filled buttons, the slider's value, the plotted graph of the selected cell, links, the focus ring |
| `-mc-on-primary` | `#FFFFFF` | `#381E72` | text on `-mc-primary` |
| `-mc-primary-container` | `#EADDFF` | `#4F378B` | tonal buttons (the command keys `solve`, `simplify`, `steps`; the rail's New button), the "On the canvas" chip |
| `-mc-on-primary-container` | `#21005D` | `#EADDFF` | text on `-mc-primary-container` |
| `-mc-on-surface` | `#1D1B20` | `#E6E0E9` | body text, maths results |
| `-mc-on-surface-variant` | `#49454F` | `#CAC4D0` | secondary text: the typed input, hints, the status line, labels |
| `-mc-outline` | `#79747E` | `#938F99` | outlined buttons, the input bar's border |
| `-mc-outline-variant` | `#CAC4D0` | `#49454F` | dividers, the grid of the canvas |
| `-mc-error` | `#B3261E` | `#F2B8B5` | error text and the caret under an error |
| `-mc-error-container` | `#F9DEDC` | `#8C1D18` | the background of an error cell |
| `-mc-plane` | `#FFFFFF` | `#1D1B20` | the canvas plane |
| `-mc-link` | `#6750A4` | `#D0BCFF` | hyperlinks (the update notice) |

Every colour pair that carries text meets 4.5:1 (the existing `ContrastTest` checks every text
colour against its background in both themes and stays). Graphs on the canvas keep their own
palette (the eight series colours of `canvas.md`), chosen to read on `-mc-plane` in both themes.

State layers, as Material defines them: hover adds the content colour at 8 % over the
component, pressed at 12 %, focus shows a 2 px `-mc-primary` ring outside the shape (JavaFX:
`-fx-background-insets` with a second layer, never `-fx-border`). Disabled text is
`-mc-on-surface` at 38 %.

## Type

Roboto, bundled under `app/src/main/resources/mathcanvas/app/fonts/` (Roboto is Apache-2.0:
its licence goes to `legal/Roboto-LICENSE.txt` and `NOTICE` lists it), loaded with
`Font.loadFont` at startup beside STIX Two Math; `system-ui` is the fallback. The maths results
stay in STIX Two Math through `render`, unchanged.

| Role | Font | Size | Weight | Where |
|---|---|---|---|---|
| Title | Roboto | 22 px | 400 | the app bar's worksheet title |
| Body | Roboto | 14 px | 400 | labels, tooltips, dialogs, the Variables card, the algebra panel |
| Label | Roboto | 12 px | 500 | the rail's labels, the "VARIABLES" heading, chips, the status line |
| Button | Roboto | 14 px | 500 | every button |
| Input | Roboto Mono, else the platform's monospace | 13 px | 400 | what the person typed in a cell, the input bar |
| Result | STIX Two Math | 22 px | 400 | the typeset result of a cell |

Line height 1.4 for body text. No letter-spacing except the Label role at 0.04 em in capitals.

## Spacing and shape

The spacing scale is 4, 8, 12, 16, 24 px. Cards have 14 px vertical and 16 px horizontal
padding and sit 12 px apart. The window's content has 24 px side margins beside the rail and
16 px at the bottom.

| Part | Corner radius |
|---|---|
| Cells, the canvas card, the Variables card, the keypad's container | 16 px |
| Buttons (filled, tonal, outlined), chips, the rail's indicator | fully rounded (20 px at 40 px tall) |
| Keypad keys (36 px tall) | 18 px |
| The input bar | 28 px |
| The canvas plane inside its card | 12 px |
| Dialogs | 28 px |
| The rail's New button | 16 px |

Buttons are 40 px tall with 20 px side padding; keypad keys are 36 px; the rail is 80 px wide
with 56 × 32 px indicators; the app bar is 64 px; the status row is 32 px. Every target is at
least 36 px tall and 44 px where the pointer is the only input (the rail, the app bar).

## The screen

```
┌───────┬──────────────────────────────────────────────────────────────────────┐
│ rail  │ app bar: title · saved state                  Undo  Redo  Steps  Run │
│       ├──────────────────────────┬──────────────────┬────────────────────────┤
│ New   │ worksheet (cards)        │ canvas card      │ keypad (tonal keys)    │
│ Work- │   input, result          │   tools (chips)  │                        │
│ sheet │   input, slider          │   plane          │ Variables card         │
│ Canvas│   …                      │   readout        │                        │
│ Hist. │ input bar (pill + Enter) │                  │                        │
│ Help  ├──────────────────────────┴──────────────────┴────────────────────────┤
│ Sett. │ status row: Ready · Exact · Radians · version                        │
└───────┴──────────────────────────────────────────────────────────────────────┘
```

- **The navigation rail** replaces the old toolbar's view toggles and most of its buttons: a
  tonal New button, then Worksheet, Canvas, History, Help, and Settings at the bottom, each an
  icon (inline, 20 px, from the Material Symbols outlined set, drawn as SVG paths in the
  resources) over a 12 px label. The selected item has the `-mc-secondary-container`
  indicator. Worksheet and Canvas are toggles that show or hide their column; both on is the
  default. The keypad's visibility is a toggle in the rail too (Keypad), below Canvas.
- **The app bar** holds the worksheet's title (editable on click, the file's name without its
  extension, "Untitled worksheet" for a new one), the saved state in Label type, and on the
  right the Undo and Redo outlined buttons, Show steps as a tonal toggle, and Run all as the
  one filled button. Stop appears in place of Run all while something runs.
- **A cell** is a `-mc-surface-container` card: the typed input in Input type and
  `-mc-on-surface-variant` on top, the result below in Result type; a slider cell shows the
  Material slider (a 16 px track in `-mc-secondary-container`, the value part in
  `-mc-primary`, a 4 × 24 px handle); a plotted cell shows the "On the canvas" chip; Steps is
  a text button in `-mc-primary` that expands the explanation inside the card. An error cell
  has an `-mc-error-container` strip under the input with the message in `-mc-error` and the
  caret. The selected cell has a 2 px `-mc-primary` ring.
- **The input bar** is a 56 px pill at the bottom of the worksheet with the hint "Type an
  expression" and a filled Enter button; it never scrolls away.
- **The canvas card** holds a row of tool chips (the selected one tonal, the rest outlined),
  the plane with 12 px corners, and the readout row in Label type. The algebra panel is a
  second card under the plane when shown; the 3D view's camera controls are chips in the same
  row.
- **The keypad** is a 6-column grid of 36 px pill keys, 6 px apart: function keys on
  `-mc-surface-container`, digits on `-mc-surface-container-high`, the command keys (`solve`,
  `simplify`, `steps`, `expand`, `factor`, `numeric`) tonal. The matrix editor and the data
  editor open as dialogs from their keys.
- **The Variables card** lists each name in Result type with its kind in
  `-mc-on-surface-variant` and a Clear text button.
- **Dialogs** (Settings, the matrix editor, the data editor, export) are Material dialogs: 28 px
  corners, a 24 px title, the content, and text buttons right-aligned, the confirming one
  filled. The Help window is a non-modal window with the same tokens: a search field (an
  outlined text field with a leading icon), the list, and the detail pane as a card.
- **The status row** is Label type in `-mc-on-surface-variant`: the state on the left, the
  modes and the version on the right; the update notice is a link in `-mc-link` there.
- **Menus** keep JavaFX's menu bar (macOS puts it in the system bar) with the same items as
  today; the rail and the app bar duplicate the common ones.

## Dark theme

Dark uses the second column of tokens with the same shapes and type. The canvas plane is
`-mc-plane` dark with `-mc-outline-variant` grid lines; the graph palette has its dark
variants. The theme follows the Settings choice (System, Light, Dark) as today.

## Motion

Only what JavaFX gives cheaply: a 150 ms fade on dialogs and on the Steps expansion, no
movement of cards, no ripples (a pressed state layer instead).

## What does not change

The worksheet's behaviour, the command stack, the FX-thread rule, the canvas's painting
through `Painter`, the typesetting in `render`, the ids the UI tests use (`#toolbar-undo` and
the rest keep their ids even when the control moves to the rail or the app bar), and the
accessible names of 9b.
