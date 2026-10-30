# App

How the desktop application in `app/` works: the document model, the command stack, the
evaluation worker, the files in the data directory and their schema, the settings and the theme,
and the worksheet window with its panels. Read `CLAUDE.md` first; this file assumes its
**Commands** and **Architecture rules**. The module `mathcanvas.app` requires `mathcanvas.canvas`
(and through it `render` and `engine`), `javafx.controls` and `com.fasterxml.jackson.core`.

| Package | What it holds |
|---|---|
| `mathcanvas.app` | `Main`, the entry point; `Workspace`, the loaded document with its worker and store |
| `mathcanvas.app.document` | the immutable state, its cells and outputs, the settings, the commands, the undo stack |
| `mathcanvas.app.eval` | `EvaluationJob`, one run of cells on the engine; `Document`, the stack with evaluation; `Typeset`, typesetting on the worker under the cap |
| `mathcanvas.app.json` | the JSON value tree and its reading and writing |
| `mathcanvas.app.persist` | the data directory, the two files, their migrations, the store |
| `mathcanvas.app.ui` | the window, the cell view and editor, the keypad, the panels, the settings dialog, the theme binding, the strings |

## The document model

`DocumentState(cells, settings, nextId, canvas, names, viewport)` is the document at one moment,
immutable: every change makes a new state, so the undo stack keeps states and needs no inverse
operations. There is always at least one cell; ids are distinct and below `nextId`, the id the next
new cell gets. The worksheet and the canvas are two views of this one state: `canvas` is the
`CanvasState` of `docs/reference/canvas.md` (the objects, their geometry and the canvas's own
`nextId`), `names` gives each named point the variable it binds in the cells (a variable name, not
a slider's variable, given once), and `viewport` is the canvas's view of the plane. Changes go
through the withers (`withCells`, `withSettings`, `withCanvas`, `withViewport`), which keep the
rest; `withCanvas` drops the names of points no longer on the canvas.

- `Cell(id, input, evaluated, output)`: the id stays with the cell through edits and moves; the
  input is the typed text; `evaluated` says the cell was submitted, so every re-evaluation runs
  it; a blank cell is never evaluated.
- `Output` is `None` (never run), `Pending` (waiting for the worker), `Cancelled` (stopped before
  it finished; nothing of it stays) or `Done(results, after, inspection)`: one `Result` per
  statement run, or the one parse error, the engine `Environment` after the cell, which the next
  cell is evaluated in, and the `Inspection` of that environment, what the variables inspector
  shows: each name with its definition printed as text, as a layout and as the box tree typeset
  from it, and the assumptions printed, all on the worker.
- `Result` is `Value(value, trace, quiet, text, shown, layout, box, steps, format)`,
  `Defined(echo, text, layout, box)` or `Failed(message, at, line)`. Everything a cell shows is
  printed and typeset on the worker, so the FX thread only shows it: a value's text, its layout
  tree and the box tree typeset from it (none over the typesetting cap), the display tree `shown`
  whose paths a selection names (the
  value itself in a format other than exact), the curated steps of a traced value (`Result.Step`:
  its text, and the expression after it as text, layout and box), and the format it was printed in; a
  definition's echo as text and as the layout of its head and body joined by `:=`; a failure's
  position in the cell and the source line it was on, for the caret.
- `DocumentSettings(precision, angleMode, numberFormat, numberDigits)` belong to the document and
  change through its commands, so an undo reverts them; `engine()` gives the engine `Settings` and
  `format()` the printers' `Format` (`EXACT`, `DECIMAL` or `SCIENTIFIC` with `numberDigits`
  significant digits, 1 to 10,000, default 10, `FRACTION`).
- `Preferences(theme, showSteps, keypadShown)` belong to the person, not the document: outside the
  state and the undo history, so Ctrl+Z never changes them, and saved beside the document
  settings. The window binds to them through `ui.PreferenceProperties`.

## Commands and the undo stack

A `Command` maps a state to the next. A command that does not apply (a cell that no longer
exists) returns the state unchanged, and nothing is recorded.

| Command | Effect |
|---|---|
| `EditInput(id, text)` | sets a cell's text (typing, or a recalled input); the output stays until the cell is submitted |
| `Submit(id)` | a cell with text becomes evaluated and pending (even if it was stopped), and so does every evaluated cell after it that was not stopped; submitting the last cell adds a blank cell after it; a blank cell becomes not evaluated |
| `DeleteCell(id)` | removes a cell unless it is the only one; when it was evaluated, the evaluated cells after it that were not stopped become pending |
| `SetSettings(settings)` | replaces the document settings; a change that alters what is shown (the precision, the angle mode, or the printers' format: the number format, and the digits of the decimal and scientific formats) makes every evaluated cell that was not stopped pending, so the worker computes or prints it again; a change that alters nothing shown (the digits of the exact or fraction format, or the same settings) re-evaluates nothing |
| `Append(text)` | adds a submitted cell with the text after the last cell with text (before a blank last cell, which stays last, or else at the end with a blank cell after it): how the inspector runs `clear(name)` |
| `AddObject(make, name)` | adds a canvas object with the canvas's next id, and names it when it is a point given a name |
| `ReplaceObject(object)` | redefines the object with the same id, keeping its place and name; a cycle is refused with a `CycleException` naming it, and nothing changes |
| `RemoveObject(id)` | removes an object and every object built on it, and their names |
| `MovePoint(id, x, y, parameter)` | moves a free point to `(x, y)`, or a point on a path to the parameter the caller projected onto the path (`(x, y)` becoming its last position) |
| `SetSlider(id, value)` | sets a slider's value, snapped to the nearest step from its minimum (halves rounding up) and clamped to its range |
| `SetStyle(id, style)` | changes how an object is drawn |

A cell the user stopped (`Cancelled`) is made pending only by its own submission: Escape means what
it says, and the cell stays `(cancelled)` until Enter is pressed on it.

`CommandStack` holds the current state and the undo and redo stacks of whole states. `execute`
pushes the state before a command and clears the redo stack; `undo` and `redo` move whole states
between the stacks, so each restores exactly what was there. At most 500 steps are kept, the oldest
dropped first.

**Merging.** A command's `mergeKey` (an `EditInput`'s is its cell, a `MovePoint`'s or a
`SetSlider`'s its object, so a drag is one step) makes consecutive commands one step: a command
whose key equals the last step's, within one second of the command before it, replaces the current
state and keeps the step's starting state. Merged edits that come back to that starting state (a
character typed and deleted) remove the step. Any other command, an undo, a redo or `endMerge` (the
window calls it when the focus moves to a field) ends the step. The clock is passed in, so the tests
control it.

**Amending.** `amend` changes the current state without recording a step: it is how evaluation
results enter, and the canvas's geometry, and a new viewport (`Document.setViewport`: a pan or a
zoom is not an undo step, as scrolling is not, but the listeners hear of it so it is saved; an undo
or a redo keeps the viewport of the moment). An undo therefore returns to the state before the
last command with the outputs it had then, which may be pending; the document re-evaluates what is
pending after any undo or redo.

## Evaluation

`EvaluationJob.run(cells, before, cancellation, tracing, format, sink)` evaluates a run of cells in
order on one engine `Session` built on the environment `before`, tracing every value when `tracing`
is set (the Show steps preference) and printing values and steps in `format` (definitions echo as
typed). Each cell's text is parsed as a script with the
names the cells before it defined (`Session.names()`), and its statements run in order past
failures, as in the CLI's REPL; a parse error is the cell's one result. An evaluation error is
placed at its node's position in the cell (`Script.positionOf`), else at the statement's start.
Each value is printed on the worker: in the exact format its display tree (`Display.of`) to text and
to a layout tree; in another format `display(value, cancellation)` of the formatted text and layout
printers. A traced value's steps are narrated (`Narration.steps`, with the job's token in the
context) and each step's expression printed the same way. Each layout is then typeset on the
worker with the render module's shared thread-safe typesetter (`Typesetters.standard()`, with the
job's token), results and echoes at 18 px and steps at 15 px, so the FX thread only shows the box
trees (`MathView.setBox`). A layout over `Typesetter.MAX_TYPESET_NODES` (20,000) nodes
(`Typesetter.wouldExceed`) is neither kept nor typeset, since its box tree and nodes would cost the
window too much: the cell shows the capped text instead. Each finished cell goes to the sink at once, with the
environment after it. When the cancellation is requested, at the engine's next check
(`docs/reference/engine.md`, **Cancellation**) or while a value is printed or narrated (a
`CancelledException` from the printers or the narration counts the same), the cell being run and
every later cell are reported `Cancelled`: nothing of the stopped cell stays, and the cells before it keep their
outputs and environments.

`Document` owns the `CommandStack` and the evaluation, and is confined to one thread, the FX thread
in the app. It is built with a worker `Executor` and a `ui` executor (`Platform::runLater`), so the
unit tests run both as queues by hand and never start the toolkit. Every change goes through its
`execute`, `undo` and `redo`; listeners hear of every change of the state, results included, and
the window binds to its `busy`, `canUndo` and `canRedo` properties.

- **When a job starts.** At `start` (after loading), after every `Submit`, and after any other
  change that alters which cells are pending (an undo, a deletion, new settings). Typing never
  does, so it never interrupts a running job.
- **What it runs.** The running job's token is cancelled, the generation is incremented, and a
  new job evaluates the pending cells in order, with their texts at that moment. The environment
  before the first is the one stored after the nearest earlier `Done` cell, or the empty
  environment with the document's settings, so a cancelled job leaves nothing to restore: the
  session is per job. A stopped cell in between is skipped: the cells after it are evaluated
  without what it would have defined.
- **Generations.** Each result arrives on the FX thread tagged with its job's generation and is
  applied (through `amend`, to the cell with its id) only if that generation is still current; a
  superseded job's results are dropped. `busy` is true from a job's start until its end arrives
  in the current generation.
- **Stop.** `stop` requests the current job's cancellation: its remaining cells become cancelled
  and `busy` falls. They stay cancelled: submitting a new cell evaluates only that cell (and the
  pending ones), and a stopped cell runs again only when it is itself submitted.

The worker is a single daemon thread, `mathcanvas-eval`, with a 2 MB stack (the parser's nesting
cap needs more than 1 MB, `PLAN.md` §11); it is never the FX thread. At exit the document's job
is cancelled and the worker shut down.

## The data directory

`DataDirectory.resolve` finds it, in order:

1. the argument `--data-dir=<dir>` or `--data-dir <dir>` (the first one given);
2. the system property `mathcanvas.dataDir`;
3. the platform's folder for application data: `~/Library/Application Support/math-canvas` on
   macOS; `%APPDATA%\math-canvas` on Windows (`~\AppData\Roaming\math-canvas` when `APPDATA` is
   unset); on Linux and other systems `$XDG_DATA_HOME/math-canvas` when that variable holds an
   absolute path, else `~/.local/share/math-canvas`.

A relative directory resolves against the working directory, which under `./gradlew :app:run` is
`app/`. A directory that is asked for but not given is an error, never a reason to fall back to the
platform's folder, which holds the owner's real data: `--data-dir=` with nothing (or blanks) after
it, `--data-dir` as the last argument or followed by another option (`--…`), or a blank property.
`Main.main` checks this before launching and stops with the bundle's message on standard error and
status 2; any other argument is reported (`math-canvas: ignoring the argument …`) and ignored. The
directory is created when the store opens it; if it cannot be, the app runs without saving and
says so in the status line.

## The files

Two files in the data directory, each a JSON object with a `version` member:

`session.json` holds the worksheet's cells, each its input text and whether it was evaluated.
Outputs are not stored: on loading, the evaluated cells are pending and evaluate on the worker. A
stored cell that is evaluated but blank loads as not evaluated.

```json
{
  "version": 1,
  "cells": [
    {
      "input": "f(x) := x^2 + 1",
      "evaluated": true
    },
    {
      "input": "",
      "evaluated": false
    }
  ]
}
```

`settings.json` holds the document settings and the preferences, at version 2. The angle mode is
`radians`, `degrees` or `gradians`; the number format `exact`, `decimal`, `scientific` or
`fraction`, and `numberDigits` the digits of the decimal and scientific formats; the theme
`system`, `light` or `dark`; the precision and the digits integers from 1 to 10,000.

```json
{
  "version": 2,
  "precision": 34,
  "angleMode": "radians",
  "numberFormat": "exact",
  "numberDigits": 10,
  "theme": "system",
  "showSteps": false,
  "keypadShown": true
}
```

Version 1 had no `numberDigits`, `showSteps` or `keypadShown`; the migration from version 1 adds
their defaults (10, false, true). A version 1 file is read as version 2 and written as version 2
the next time the settings change.

Cell ids and the undo history are not saved; ids are numbered from 1 on loading. Reading is
strict: a missing or ill-typed member, or a value outside its set, makes the file unreadable;
members the reader does not know are ignored. An exact number, when a later version stores one, is
written as a JSON string of its printed text, never as a JSON number.

**The JSON.** `Json` is a small immutable tree (object with ordered members, array, string, number
kept as its decimal text, boolean, null), read and written by `JsonIo` through Jackson Core's
streaming parser and generator, so there is no databind, no reflection and no `opens`. Reading is
strict JSON: a duplicate member name, anything after the root value, comments, single quotes and
`NaN` are errors; a leading byte order mark is skipped. Writing indents by two spaces, writes a
member as `"name": value`, ends lines with LF on every platform and ends the text with a LF. An
unpaired surrogate in a string value is written as its `\uXXXX` escape, which reads back as the
same code unit, so every string value round-trips exactly; a member name may not hold one (Jackson
refuses it in a name). Jackson's limits (nesting depth, number and string lengths) are I/O errors
with its message.

**Migrations.** Each file has its `Migrations(current, steps)`: the version this release writes
and, for each earlier version `n`, the step that turns a version-`n` tree into a version-`n + 1`
one. Reading parses the tree, checks `version` is a positive integer no newer than `current`
(a newer file is unreadable, never downgraded), applies the steps from the file's version up,
and only then decodes, so the decoder knows the current shape alone. To change a file's shape:

1. bump `current` in its `Migrations` and add the step from the old version, which rewrites the
   tree only;
2. change the encoder and the decoder to the new shape;
3. keep the old version's fixture (`app/src/test/resources/mathcanvas/app/persist/`, one per
   version, never edited) and add one for the new version; the fixture tests decode every kept
   version, so each release proves it reads every earlier file.

The fixtures are `session-v1.json`, `settings-v1.json` and `settings-v2.json`; `FilesTest` reads
both settings versions to the same settings and checks that a version 1 file, loaded and saved, is
the version 2 fixture byte for byte (`StoreTest` does the same through the store).

**The store.** `Store` keeps the files. Loading gives the decoded value, nothing for a missing
file, or, for a file that cannot be read (an I/O error, text that is not UTF-8, JSON that is not
valid, a wrong shape or a newer version), renames it aside as
`<name>.unreadable-<UTC yyyyMMdd'T'HHmmss'Z'>[-k].json` and gives the reason; the app starts
without it and the status line names it, the reason in the line's tooltip. A file that cannot even
be renamed is never written for the rest of the run. So a file is never silently overwritten.
Saving is debounced: each change asks to save the trees whose content changed, and the store writes
them one second after the last request, on its own daemon thread, `mathcanvas-io`. A file is
written whole to `<name>.tmp` as UTF-8, forced to the disk, and moved over the old one (atomically
where the file system can), so a crash leaves the old file or the new one. A failed write is
reported in the status line. At exit, `Main.stop` flushes on the FX thread and waits at most five
seconds; nothing else blocks the FX thread on the store. Two instances on one data directory are
not guarded against (deferred, `PLAN.md` §11).

## Settings and the theme

Precision and angle mode reach the engine through the environment each job starts from, and the
number format and its digits reach the printers through the job; changing any of them evaluates the
evaluated cells again. The settings dialog (`ui.SettingsDialog`, the toolbar's Settings or
Shortcut+,) shows the document settings (precision, angle mode, number format, the digits, enabled
for the decimal and scientific formats) and the preferences (theme, show steps, show the keypad).
OK applies the document settings as one `SetSettings`, so an undo reverts them, and the preferences
outside the undo history; Cancel discards; Reset to defaults fills in the defaults and keeps the
dialog open. It is shown without blocking and takes the window's stylesheet and theme.

The theme is Modena plus the app's stylesheet, `app.css`, whose colours are looked-up colours on
`.root` redefined under `.root.dark` (`-fx-base`, `-fx-background`,
`-fx-control-inner-background`, and the app's `-mc-text`, `-mc-muted`, `-mc-error`,
`-mc-selection`); Modena derives its own colours from the first three, so the controls follow, and
`.math-view` sets `MathView`'s `-math-ink` and `-math-selection-fill` from the app's colours, so the
typeset math follows too. The prompt text of the inputs is `-mc-muted`, readable in both themes.
`ThemeBinding` puts the style class `dark` on the scene's root when the theme is `DARK`, or when it
is `SYSTEM` and the scene's colour scheme (`Scene.getPreferences().colorSchemeProperty()`, which
follows the platform's) is dark, and keeps it in step with both.

## Strings

Every user-facing text is in the bundle `mathcanvas.app.messages` (root locale), read through
`ui.Messages.text(key, args…)` with `MessageFormat`, so a single quote is written twice: the window
title (`math-canvas {0}`), the menus, the toolbar, the theme, angle and format names, the cell's
prompt and hint, the pending, cancelled and copy texts, the steps, the keypad's labels and
tooltips, the panels, the settings dialog, the status line's messages and the start-up errors.
`MessagesTest` checks that every key the code names, and every key built from an enum or a keypad
key, is in the bundle.

## The window

`WorksheetWindow` is the worksheet: menus and a toolbar, the cells in a scroll pane, the side
panels, and a status line. The title is `math-canvas <version>`. The view only follows the
document's state: typing sends `EditInput`, and a state change sets a field's text only when it
differs. The current cell (where the keypad, the history and the inspector insert) and the
keypad's focused key follow the scene's focus owner.

**Cells.** A `CellView` is an input editor and its outputs. A value or a definition's echo is a
`MathView` showing the box tree the worker typeset (`setBox`, so the view does not typeset), its
layout kept in the view's properties; a click selects a subexpression (the deepest under the
pointer), and the output's context menu Copy puts the selection, or the whole output, on the
clipboard as Unicode text and as LaTeX (type `application/x-latex`). In a format other than exact,
a selection is copied as the text of its layout node only (`Typeset.find`), since the printed tree
is the value prepared for the format. A layout over the cap is shown as its text in the math font.
A value or an error message longer than `Shown.MAX_SHOWN` (10,000) code points is cut there, as
the CLI cuts its messages, followed by `… (n more characters)`; the full text stays in the cell's
`Result`. An error is its message, its source line and a caret under the column (`Caret.under`), in
a monospaced font; a pending cell shows `…`, a stopped one `(cancelled)`. A traced value has a
Steps expander under it, closed at first, each step's text with the expression after it typeset;
a traced value that rewrote nothing says so.

**The editor.** `CellEditor` is a text area as tall as its lines. The bracket at the caret (`()`,
`[]`, `{}`, `⟨⟩`) and its partner are marked, an unmatched one outlined in the error colour
(`Brackets`, one stack over the four kinds, so a closing bracket of the wrong kind matches
nothing); typing a closing bracket over the same bracket moves past it.

**The definition shortcut.** A cell whose whole text is `f(x, …) = body`, with a head not defined
before the cell and not a builtin, constant, domain or keyword, and parameters that are names, is
offered as a definition (`DefinitionShortcut`, `PLAN.md` §6): a hint under the cell says so, Enter
rewrites the cell to `f(x, …) := body` and evaluates it, and Shift+Enter evaluates the equation as
written. The parser is unchanged.

**The keypad.** A grid of keys beside the cells, shown by default and toggled from the toolbar (the
Keypad preference): digits and operators, `π e i ∞`, brackets, `x y t`, powers and roots,
`sin cos tan` with `inv` and `hyp` shift toggles (`asin`, `sinh`, `asinh`), `log ln exp`,
`abs floor ceil round`, `! % °`, `:=` and `→`, the `sum`, `integrate` and `diff` templates, and
the commands `simplify expand factor solve numeric steps`. A key inserts the language's ASCII form
at the caret of the current cell (`KeypadKeys`); a call leaves the caret between its parentheses
and a template selects its placeholder, so typing replaces it. Labels and tooltips come from the
bundle; a tooltip says what the key inserts.

**The history panel.** The inputs of the evaluated cells, newest first; a click or Enter recalls
one into the current cell as one undo step.

**The variables inspector.** The environment after the last evaluated cell: each name with its
kind (value, or function of its parameters) and its definition typeset at 14 px, then the
assumptions in force. A double click inserts the name in the current cell; a name's Clear runs
`clear(name)` as a new cell (`Append`). The rows come from the `Inspection` of the last finished
cell, printed and typeset on the worker with the cell's results: each definition's stored tree
printed to text and to a layout, and typeset at 14 px in text style, under the cap (the text, cut,
when the layout is over it), each name and definition printed once and then reused from a memo
that belongs to the document and is touched only by its worker, so an unchanged definition is not
printed again; nothing is evaluated for the panel, and the panel only
builds nodes from the finished boxes (`MathView.setBox`).

**Busy and stop.** The progress indicator shows while a job runs, the cells it re-evaluates show
`…`, and Stop (the button, Escape or Shortcut+.) stops it.

| Key | In a cell |
|---|---|
| Enter | submit the cell and move to the next one (the new cell after the last); a cell offered as a definition is defined |
| Shift+Enter | a new line in the cell; a cell offered as a definition is evaluated as written |
| Up, Down | on the cell's first or last line, move to the cell above or below |
| Alt+Up, Alt+Down | recall the inputs of the cells above into this one: older, then newer, back to what was typed |
| Tab | accept the keypad's focused key (the last key that had the focus), or move to the keypad |
| Escape | stop the evaluation |
| Backspace in an empty cell | delete the cell, unless it is the only one, and move up |
| Shortcut+Z | undo the document (not the field's own undo) |
| Shortcut+Shift+Z, Shortcut+Y | redo |

The menus give the other actions accelerators: Evaluate › Stop (Shortcut+.), View › Keypad,
History, Variables and Show steps (Shortcut+Shift with K, H, V and T) and Settings (Shortcut+,).
On macOS they are in the system menu bar. After an undo or a redo, the first cell it changed takes
the focus.

## Tests

The unit tests are in `app/src/test/java/mathcanvas/app/` and never start the toolkit:

- `json/JsonTest`, `json/JsonProperties` (random trees, lone surrogates in strings included,
  survive a UTF-8 write and read);
- `document/CommandsTest`, `document/CommandStackTest`, `document/CommandStackProperties` (random
  sessions: undo after redo and redo after undo restore exactly; undoing everything returns to the
  start and redoing everything to the end);
- `eval/EvaluationJobTest` (the real engine: definitions across cells, errors with positions,
  several statements, layouts, steps, the number formats, cancellation between cells and inside a
  slow expansion), `eval/DocumentTest` (the document on queued executors: pending to done, stale
  results dropped, typing not restarting, a stopped cell staying cancelled until it is submitted,
  undo and redo re-evaluating, settings, loading), `eval/TypesetTest` (the cap, the echo
  layout, finding a node by its path) and `eval/InspectionsTest` (the inspector's rows printed,
  each definition once);
- `persist/FilesTest` (the fixtures of every version and the strict decoders, the version 1
  settings saved as the version 2 fixture), `persist/MigrationsTest` (a made-up three-version
  history), `persist/DataDirectoryTest`, `persist/StoreTest` (with a fixed clock: setting aside, a
  file that cannot be renamed, saving, a failed write, a version 1 settings file through the
  store);
- `ui/MessagesTest`, `ui/ShownTest`, `ui/BracketsTest`, `ui/DefinitionShortcutTest`.

The UI tests are in `app/src/uiTest/java/mathcanvas/app/`; each extends `WorksheetHarness`, which
opens the app on a stage and a data directory of its own and drives it with synthetic events:

- `WorksheetWindowTest`, the Phase 2 acceptance: `1/3 + 1/6` evaluates to `1/2` and the next cell
  takes the focus; recall; undo and redo by key and by button; a long value cut with its note; a
  full worksheet in both themes, with screenshots; `expand((x + y + z + w + 1)^30)` stopped by
  Escape within a second, with the FX thread answering meanwhile, and a later cell evaluated
  without re-running it; a restart keeping the session and the theme; an unreadable session set
  aside and reported;
- `TypesetOutputTest` (the typeset layout, a click selecting inside `x/(2π)`, Copy of the
  denominator and of the whole), `CellEditorTest`, `DefinitionShortcutUiTest`, `KeypadTest`,
  `HistoryPanelTest`, `InspectorTest`, `StepsTest`, `SettingsDialogTest` (decimal results, the
  undo of the settings, reset and cancel), and `SectionSixTest` (every `PLAN.md` §6 example in
  Phase 2's scope through the worksheet, and the menus).

How they run is in `docs/reference/testing-tools.md`, **UI tests**.
