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
| `mathcanvas.app.text` | `Messages`, the user-facing texts from the resource bundle, for every package |
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
- `Result` is `Value(value, trace, quiet, text, shown, layout, box, steps, format, numeric)`,
  `Defined(echo, text, layout, box)` or `Failed(message, at, line)`. Everything a cell shows is
  printed and typeset on the worker, so the FX thread only shows it: a value's text, its layout
  tree and the box tree typeset from it (none over the typesetting cap), the display tree `shown`
  whose paths a selection names (the
  value itself in a format other than exact), the curated steps of a traced value (`Result.Step`:
  its text, and the expression after it as text, layout and box), and the format it was printed in; a
  definition's echo as text and as the layout of its head and body joined by `:=`; a failure's
  position in the cell and the source line it was on, for the caret.
- `DocumentSettings(precision, angleMode, numberFormat, numberDigits)` belong to the document and
  change through its commands, so an undo reverts them; `engine()` gives the engine `Settings`
  (with the quadrature digits `numberDigits` under the decimal and scientific formats, 0 under the
  others, so a definite integral with no closed form is numeric only there, `docs/language.md`
  §10.2; the engine computes at most 100 digits, so under a format of more digits such an integral
  stays unevaluated with the step that names the cap) and `format()` the printers' `Format` (`EXACT`, `DECIMAL` or `SCIENTIFIC` with `numberDigits`
  significant digits, 1 to 10,000, default 10, `FRACTION`).
- `Preferences(theme, showSteps, keypadShown, canvasShown, exportFolder)` belong to the person, not
  the document: outside the state and the undo history, so Ctrl+Z never changes them, and saved
  beside the document settings; `canvasShown` is the canvas mode, `exportFolder` the folder of the
  last canvas export. The window binds to them through
  `ui.PreferenceProperties`.

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
| `AddObject(make, name)` | adds a canvas object with the canvas's next id, and names it when it is a point given a name; added in turn (`AddObject.named`, `AddObject.inTurn`), it takes the series colour least used among the drawn objects (every object but sliders) when the command runs, the lowest index among equals, so new objects take the colours one after another and a deleted object's colour is taken first; the rule is `CanvasState.nextColour()`, which a command adding several objects together (a family of intersections) uses to give them all one colour |
| `ReplaceObject(object)` | redefines the object with the same id, keeping its place and name; a cycle is refused with a `CycleException` naming it, and nothing changes |
| `RemoveObject(id)` | removes an object and every object built on it, and their names |
| `MovePoint(id, x, y, parameter)` | moves a free point to `(x, y)`, or a point on a path to the parameter the caller projected onto the path (`(x, y)` becoming its last position) |
| `SetSlider(id, value)` | sets a slider's value, snapped to the nearest step from its minimum (halves rounding up) and clamped to its range |
| `SetStyle(id, style)` | changes how an object is drawn |
| `Sequence(commands)` | runs the commands in turn as one undo step, each on the state the one before left, so the ids `AddObject` gives count on from the canvas's next id; never merged; holds no `Submit`, whose evaluation the document starts only for a submission it sees itself |

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

`EvaluationJob.run(cells, before, canvas, cancellation, tracing, format, inspections, sink)`
evaluates a run of cells in order, each in an engine `Session` built on the environment the cell
before it stored (`before` for the first, unless its input names its own) with the canvas's
variables bound over it (**Canvas variables**, below), tracing every value when `tracing` is set
(the Show steps preference) and printing values and steps in `format` (definitions echo as typed).
Each cell's text is parsed as a script with the names the cells before it defined
(`Session.names()`), and its statements run in order past
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
- **What it runs.** The running job's token is cancelled, the generation is incremented, and a new
  job evaluates the pending cells in order, with their texts at that moment. Each starts from the
  environment stored after the nearest earlier `Done` cell (the previous cell of the run's when no
  finished cell lies between them), or the empty environment with the document's settings, so a
  cancelled job leaves nothing to restore: the session is per cell. A stopped cell in between is
  skipped: the cells after it are evaluated without what it would have defined.
- **Generations.** Each result arrives on the FX thread tagged with its job's generation and is
  applied (through `amend`, to the cell with its id) only if that generation is still current; a
  superseded job's results are dropped. `busy` is true from a job's start until its end arrives
  in the current generation.
- **Stop.** `stop` requests the current job's cancellation: its remaining cells become cancelled
  and `busy` falls. They stay cancelled: submitting a new cell evaluates only that cell (and the
  pending ones), and a stopped cell runs again only when it is itself submitted.

**Canvas variables.** The sliders and the named points are engine variables of the cells
(`CanvasBindings`): a slider's variable is bound to its exact value, a point's name to the vector
of its coordinates as decimals (`A := [1.5, -2.0]`, so `nth(A, 1)` is its abscissa; a point with no
position binds nothing). They are bound before the first cell: each cell's environment is the one
the cell before it stored with every canvas variable it does not define bound over it, and a cell
stores its environment without them (nor does the inspector show them), so no stored environment
holds a stale value. A cell that defines or clears the name itself shadows it from there on.

- **Slider statements.** `name := slider(min, max, step)` as a whole statement never reaches the
  engine (`docs/language.md` §12.3): the job evaluates the three to exact decimals, binds the name
  to the slider's value (its minimum when it is new, else its value snapped into the new range),
  echoes the statement as typed, and lists the slider in the cell's output (`Output.Done.sliders`),
  so the cells after it in the run see it at once. When the output arrives the document creates
  or updates the canvas `Slider` (an amend, so it belongs to the submission's undo step). Only a
  cell the user submits does that: a cell run again for another reason (it reads a canvas variable
  that moved, the settings changed, an undo, an earlier cell submitted) binds the name to the
  existing slider's value, or, when there is none, to the minimum as a plain value of its own, and
  creates nothing, so a slider deleted on the canvas stays deleted until its cell is submitted
  again. A slider outlives its cell: it goes only when it is deleted on the canvas. The name of a
  point, one of the curves' own variables (`x`, `y`, `t`, `θ`, which no slider or point may take),
  a range that is not decimal or reads a canvas variable, an empty range and a step that is not
  positive are the cell's errors.
- **What a cell reads.** Each finished cell records the names its expressions read, through the
  definitions they use (`Output.Done.reads`, computed on the worker), and whether it runs
  `assume`. When a canvas variable changes (a drag of a slider or a point, an object added or
  removed, an undo), only the finished cells that read it become pending, and each runs from the
  environment the cell before it stored, since a definition is stored as typed and so does not
  depend on the values it reads; a reader that runs `assume`, whose effect does, makes itself and
  every evaluated cell after it pending. A change while cells are pending restarts the job. The
  cells before a slider statement that read the new slider run again when its run finishes, unless
  it was stopped.

**The canvas job.** The canvas's geometry is derived output, like a cell's: `CanvasJob` recomputes
it on the same worker and it enters through `amend`. The document gathers what the next job does
from every change (its own commands, undo and redo, a cell's sliders, a new viewport, a new theme):
the objects whose definitions changed (a change of style only rebuilds the content), the canvas
variables whose values changed, and whether the window moved. A job works on a snapshot:

- it adds the worksheet definitions that differ from the environment the previous job used, and
  every definition that reads one of them, to the changed variables, so a graph of `f(x)` follows
  `a` through `f(x) := a x`;
- an object that depends on itself through the worksheet (a graph whose definition reads a point
  on it, through a cell) is a cycle the canvas's own graph cannot see: it is undefined, its row
  says so, and it is not recomputed, so the cells and the canvas never feed each other forever;
- it runs `CanvasState.recompute` with the changed objects (and, after a new window, the objects
  sampled over it) and the changed variables, a sampler bound by the document's `Sampling` to the
  environment after the last cell with the canvas variables, to the view's size in pixels and to
  the job's cancellation, and the window of the viewport; the app's `Sampling.of(new Samplers())`
  binds the canvas's sampler (`docs/reference/canvas.md`, **Sampling**) with one memo of compiled
  evaluators for the document's life, so even a point landing on a path the job does not sample
  again, or a tangent's slope, stops with the job;
- it prints every object's algebra panel row (`AlgebraRows`, memoised per record, name and drawn
  position, owned by the document and touched only by its worker) and builds the drawn content
  through the document's `Content`: in the app `Content.STANDARD`, the canvas's
  `CanvasContent.build`, which takes no cancellation, so the job's token is checked before and after
  it; the labels it is given are the points' names and, for a shaded area or a Riemann sum with a
  value, the value text (`A = …`, `S = …`) in the coordinates' format, and an undefined area or
  sum's row gets the reason `Areas.problem` finds: "the bounds must be numbers a < b inside the
  graph's domain", "n must be a whole number from 1 to 1,000", "a height is not a finite number", or
  "the integral did not converge" (an area).

**The generation rule.** At most one canvas job runs; a change never cancels it but is gathered for
the next, which starts when it ends, so a drag keeps the canvas moving rather than restarting it at
every step. When a result arrives, each object whose record is still the one the job started from
takes the job's record (its tracking state) and geometry; an object redefined meanwhile keeps its
own and is already gathered. An object whose variables moved meanwhile shows the job's geometry and
is recomputed by the next job. The result's content is shown when every object took the job's record
and the names did not change; otherwise the next job rebuilds it. The points a recompute moved are
canvas variables: their readers among the cells run again and the objects that read them are
recomputed in the next job. Stop cancels the running job, whose result is dropped, so the geometry
stays as it was, and drops what was gathered: a stopped object waits for its next change, as a
stopped cell does. An object with no geometry yet that the stopped job was recomputing is **held**:
no keystroke, finished evaluation, pan or change of another object brings its recompute back; it
resumes only when its own record changes (an edit, an undo), when a variable it reads changes (a
slider it reads moves, a definition it reads is re-evaluated) or when an object it is built on is
recomputed; submitting its algebra panel row again, even unchanged, resumes it too. An object no job
has tried yet (loaded while the start-up cells run, when Stop stops only the cells) stays gathered,
so the job after the cells draws it. At `start` the whole canvas is recomputed once, after the first
evaluation of the pending cells. A canvas with no objects runs no job. `busy` is true while either
job runs, a canvas recompute as well as an evaluation, so the Stop button and the indicator follow
both. `CanvasFrame` carries the canvas, the content, which object each drawn item is, the rows, and
each function graph's slope; the window binds to `canvasFrameProperty()`. The slopes are the
engine's derivative of each graph's body, `diff(body, x)` left for the sampler, which evaluates it
in the job's environment and compiles it on the worker under the job's token, so the readout and
the tangent tool evaluate it on the FX thread with no engine work (`CanvasFrame.slope(id, x)`). A
graph the job did not recompute keeps its previous evaluator, and the frame keeps only the slopes of
graphs whose record the applied result is, keyed by that record.

**Entries.** `Document.submitEntry(target, text, onError)` reads an algebra panel row's text on the
worker (`Entries`), parsed with the user functions of the environment after the last cell, so `f(x)`
is a call when a cell defines `f`: `y = e` with `e` free of `y` is a function graph in `x`, another
equation an implicit curve, `<`, `≤`, `>` or `≥` an inequality region, `[x, y]` (or `A = [x, y]`,
naming it) a free point, a name whose value is a 2-vector of real numbers (`u`) an arrow bound to
it from the origin (a name holding anything else is refused with `u is not a vector of two real
numbers`, and a point's name since the arrow would follow the point one recompute late), on a bound
arrow's row a name rebinding it (its tail kept; any other text on a vector's row is refused), and
on a slider's row a number (or `a = number`) its value. An object's
expressions are stored as parsed and made canonical at `Canonical.DEFAULT_CONTEXT`, never evaluated,
so they depend neither on the worksheet's values nor on the precision; a point's coordinates and a
slider's value are evaluated. Back on the FX thread the entry becomes one command (`AddObject`,
`ReplaceObject`, `MovePoint`, `SetSlider`), unless the row's object changed meanwhile; a parse
error, an entry that describes no object, a cycle or a kind the object cannot take is told to
`onError` and changes nothing; so is a point named after one of the curves' own variables (`x`, `y`,
`t`, `θ`). A point entered with no name takes `DocumentState.nextPointName()` (`A` to `Z`, then
`A_1` …, skipping the names in use) when its `AddObject` runs (`AddObject.named`), so two quick
entries get two names. A new object from an entry is added in turn, so it takes the next series
colour. Stop stops an entry being read, as it stops the jobs: nothing changes and
nothing is told.

**Searches.** `Document.addIntersections(first, second, onAdded, onRefused)` and
`addFeatures(graph, features, onAdded, onRefused)` find, on the worker under a token Stop cancels,
the places a family has now inside the visible window (`canvas.model.Candidates`, with a sampler
bound to the environment after the last cell, over the paths' current geometry, which is sampled
over the window widened by half: the places are filtered by their abscissa, each kept with its
index among all the family's candidates). On the FX thread they add a named member for each such
place whose index no member of the family has yet (an `Intersection` of the pair with the lower id
first, or a `CriticalPoint` per feature), all of them one `Sequence`, one undo step, and tell
`onAdded` the last member added, which the canvas selects. The new members of one family take one
colour, the one the next drawn object would take when the family is added
(`CanvasState.nextColour`), so each family has its own. Nothing is added, and `onRefused` is told,
when a path is not drawn, when there is no place in view, when there are more than 50 (`more than
50 places in view; zoom in`: a cloud of named points helps nobody), when every place already has
its member, or when a path's record changed meanwhile; the records compared are the paths', which a
recompute never rewrites. The members then follow their places as `docs/reference/canvas.md`'s
family matching says, its edge rule included (**Object model**).

On the row of a point on a path with a parameter, the entry is its **coordinate**
(`Entries.onPath`): a number, or `name = number` whatever the name, evaluated in the environment to
a real number, the point's new parameter; the point lands where the path is at that parameter, found
on the worker with a sampler bound to the environment, and the entry becomes a `MovePoint`. A value
that is not a real number, or a parameter outside the path's domain or where the path is undefined,
is refused. Since a recompute rewrites a point's record (where it last landed), the check that
nothing changed meanwhile compares the point's path and kind of parameter and the path's record.

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

`session.json` holds the worksheet's cells, each its input text and whether it was evaluated, and
the canvas, at version 3. Outputs and geometry are not stored: on loading, the evaluated cells are
pending and evaluate on the worker, and then every canvas object is recomputed there, undefined
until it is (`Document.start`). A stored cell that is evaluated but blank loads as not evaluated.

```json
{
  "version": 3,
  "cells": [
    { "input": "a := slider(0, 5, 0.5)", "evaluated": true },
    { "input": "", "evaluated": false }
  ],
  "canvas": {
    "viewport": { "xMin": -8.0, "xMax": 8.0, "yMin": -6.0, "yMax": 6.0,
                  "width": 640.0, "height": 480.0, "aspectLocked": true },
    "nextId": 4,
    "objects": [
      { "id": 1, "kind": "slider",
        "style": { "colour": 0, "width": 2.0, "dashed": false, "visible": true, "label": false },
        "variable": "a", "min": 0, "max": 5, "step": 0.5, "value": 1.5 },
      { "id": 2, "kind": "functionGraph", "style": { … },
        "body": "a*x^2", "variable": "x", "domain": [null, null] },
      { "id": 3, "kind": "freePoint", "name": "P", "style": { … }, "x": 1.0, "y": 2.0 }
    ]
  }
}
```

(The files are written one member to a line; the example is folded.) The `canvas` member
(`persist.CanvasCodec`) holds:

- **the viewport**, its world window, its pixel size and its aspect lock, restored exactly, so the
  view's resize keeps its centre and scale on a window of another size and nothing drifts from run
  to run (a locked viewport is read through its constructor, which may square one zoomed in past a
  ten-thousandth of its coordinates again, in the last places);
- **`nextId`**, so ids are never reused after a restart;
- **the objects** in drawing order, each `id`, `kind` (`freePoint`, `pointOnPath`, `intersection`,
  `criticalPoint`, `functionGraph`, `parametricCurve`, `polarCurve`, `implicitCurve`,
  `inequalityRegion`, `shadedArea`, `riemannSum`,
  `slider`, `text`, `segment`, `line`, `arrow`, `vectorSum`, `projection`), `name` on a named point
  (a slider's name is its variable), `style` (`colour`, `width`, `dashed`, `visible`, `label`), and
  the fields of its record: parents as ids; expressions as the ASCII printer writes them (`*` for
  every product, so a name before `(` is a call, which is how they are read back, then brought to
  canonical form at the default precision); doubles as `Double.toString` writes them, NaN as `null`
  (a point on a path not yet landed, an anchor's missing parameter); a slider's numbers as exact
  JSON numbers, never through a double, as `BigDecimal.toString` writes them without trailing zeros
  (an exponent where the plain form would carry zeros, `1E+2`, so a slider at `1e1001` is a few
  characters, not a thousand); a domain as `[lower, upper]`, `null` for an infinite end, closedness
  not kept (an end written as text reads closed); a point on a path's `param` and `parameter`, its
  `last` position and its `at`, the expression it is placed at or `null`; an intersection's `index`
  and `anchor` (`null`, or `x`, `y`, `t1`, `t2`, `tangent` and `approach`, itself `null` or `{t, x,
  y}`); a critical point's `path`, `feature` (`root`, `min` or `max`), `index` and `anchor`, written
  as an intersection's; a shaded area's `upper`, `lower` (an id or `null` for the x-axis), `from`
  and `to`; a Riemann sum's `graph`, `from`, `to`, `n` and `method` (`left`, `right`, `midpoint` or
  `trapezoid`); a line as `through: [a, b]` or as `point`, `slope` and `at`; an arrow's `tail` (a
  point's id, or `null` for the origin) and either `head` (likewise) or `variable` (the name it is
  bound to); a vector sum's and a projection's `u` and `v`.

Each kind's case in the codec, with its fixture entry and round-trip test, lands with the kind: a
kind this release does not know is refused, never dropped. The vector kinds were added under
version 3 with no migration, since a new kind changes no existing object's shape: a version 3 file
saved by 0.5.0 loads unchanged, and a file holding them is refused by 0.5.0 by its kind. Version 2
had no `at`: the migration from version 2 gives every point on a path `"at": null`, so version 3
reads it strictly, and an older release refuses a version 3 file by its version.

`settings.json` holds the document settings and the preferences, at version 4. The angle mode is
`radians`, `degrees` or `gradians`; the number format `exact`, `decimal`, `scientific` or
`fraction`, and `numberDigits` the digits of the decimal and scientific formats; the theme
`system`, `light` or `dark`; the precision and the digits integers from 1 to 10,000.

```json
{
  "version": 4,
  "precision": 34,
  "angleMode": "radians",
  "numberFormat": "exact",
  "numberDigits": 10,
  "theme": "system",
  "showSteps": false,
  "keypadShown": true,
  "canvasShown": false,
  "exportFolder": null
}
```

Version 1 had no `numberDigits`, `showSteps` or `keypadShown`; the migration from version 1 adds
their defaults (10, false, true). Version 2 had no `canvasShown` (whether the canvas is shown beside
the worksheet); the migration from version 2 adds false. Version 3 had no `exportFolder` (the folder
of the last canvas export, a string, `null` before the first); the migration from version 3 adds
null. An older file is read as version 4 and written as version 4 the next time the settings
change.

Session version 1 had no `canvas`; the migration from version 1 adds an empty one at a new
document's viewport, so a migrated session opens as a new document would.

Cell ids and the undo history are not saved; cell ids are numbered from 1 on loading, canvas ids
are kept. Reading is strict: a missing or ill-typed member, or a value outside its set, makes the
file unreadable, and so does anything the canvas's records or `CanvasState` refuse (a parent that
does not exist or is of the wrong kind, a cycle, an id at or above `nextId`, two sliders or names
of one variable, a name on an object that is not a point, a viewport that is not finite and
positive); members the reader does not know are ignored. An exact number is written as a JSON number
from its decimal text, never through a binary double. The canvas's expressions are parsed and
brought to canonical form when the session is loaded, in `Workspace.open` on the FX thread at
start-up: bounded by the parser's nesting cap, about 24 ms for the largest text a user can make,
and accepted as such, the decode on the worker deferred to the start-up polish (`PLAN.md` §11).

**The JSON.** `Json` is a small immutable tree (object with ordered members, array, string, number
kept as its decimal text, boolean, null), read and written by `JsonIo` through Jackson Core's
streaming parser and generator, so there is no databind, no reflection and no `opens`. Reading is
strict JSON: a duplicate member name, anything after the root value, comments, single quotes and
`NaN` are errors; a leading byte order mark is skipped. Writing indents by two spaces, writes a
member as `"name": value`, ends lines with LF on every platform and ends the text with a LF. An
unpaired surrogate in a string value is written as its `\uXXXX` escape, which reads back as the
same code unit, so every string value round-trips exactly; a member name may not hold one (Jackson
refuses it in a name). Jackson's limits (nesting depth, string lengths, and numbers of more than a
million characters, `JsonIo.MAX_NUMBER_LENGTH`, raised from Jackson's 1,000 so an exact decimal the
app wrote is never refused) are I/O errors with its message.

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

The fixtures are `session-v1.json` to `session-v3.json`, `session-v2-canvas.json` (a version 2
session holding the version 2 every-kind canvas) and `session-v3-canvas.json` (it saved again),
`settings-v1.json` to `settings-v4.json`, and `canvas-every-kind.json`, one canvas member with every
kind and form at the current version; `FilesTest` reads every settings version to the same settings
and checks that a version 1, 2 or 3 file, loaded and saved, is the version 4 fixture byte for byte,
that the version 1 and 2 sessions, loaded and saved, are the version 3 fixture byte for byte, that
the version 2 canvas session saves as `session-v3-canvas.json` with every point on a path at no
expression, and that a version 3 point on a path without `at` is refused (`StoreTest` does the
version 1 session through the store).

**The store.** `Store` keeps the files. Loading gives the decoded value, nothing for a missing
file, or, for a file that cannot be read (an I/O error, text that is not UTF-8, JSON that is not
valid, a wrong shape or a newer version), renames it aside as
`<name>.unreadable-<UTC yyyyMMdd'T'HHmmss'Z'>[-k].json` and gives the reason; the app starts
without it and the status line names it, the reason in the line's tooltip. A file that cannot even
be renamed is never written for the rest of the run. So a file is never silently overwritten.
Saving is debounced: each change asks to save the files whose content changed, and the store writes
them one second after the last request, on its own daemon thread, `mathcanvas-io`. The session is
compared as `SessionFile.Stored` (the cells' texts, the canvas objects, names, `nextId` and the
viewport, never the geometry), cheap since an unchanged object is the same object, and handed to
the store as a supplier (`saveLater(name, Supplier<Json>)`), which makes the tree on the store's
thread, once, only if it is written: the expressions are printed there, not on the FX thread at
every slider step. A file is
written whole to `<name>.tmp` as UTF-8, forced to the disk, and moved over the old one (atomically
where the file system can), so a crash leaves the old file or the new one. A failed write is
reported in the status line. At exit, `Main.stop` flushes on the FX thread and waits at most five
seconds; nothing else blocks the FX thread on the store. Two instances on one data directory are
not guarded against (deferred, `PLAN.md` §11).

## Settings and the theme

Precision and angle mode reach the engine through the environment each job starts from, and the
number format and its digits reach the printers through the job, and the engine as the quadrature
digits of a decimal or scientific format; changing any of them evaluates the
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
`text.Messages.text(key, args…)` with `MessageFormat`, so a single quote is written twice: the
window title (`math-canvas {0}`), the menus, the toolbar, the theme, angle and format names, the
cell's prompt and hint, the pending, cancelled and copy texts, the steps, the keypad's labels and
tooltips, the matrix editor, the panels, the canvas's toolbar, the algebra panel and the trace
readout, the settings dialog, the status line's messages and the start-up errors.
`MessagesTest` checks that every key the code names, and every key built from an enum or a keypad
key, is in the bundle.

## The window

`WorksheetWindow` is the worksheet: menus and a toolbar, the cells in a scroll pane, the canvas
beside them when it is shown, the side panels, and a status line. The title is `math-canvas
<version>`. The view only follows the document's state: typing sends `EditInput`, and a state change
sets a field's text only when it differs. The current cell (where the keypad, the history and the
inspector insert) and the keypad's focused key follow the scene's focus owner.

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
a traced value that rewrote nothing says so. A value in whose statement a definite integral was
evaluated by quadrature (`numeric`, from the engine's `Outcome.Value`) has a small badge beside it,
"numeric", whose tooltip says that no closed form was found and the value is numeric, whether steps
are shown or not (`PLAN.md` §10: never a silent numeric answer); with steps on, the quadrature's
step is in the expander.

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

**The matrix editor.** The keypad's `matrix` and `vector` keys, a group of their own under the grid,
open the matrix editor (`MatrixEditor`) on the current cell, as a click or as the keypad's focused
key that Tab accepts: a popup under the cell's editor, in the window's stylesheet and theme, with a
rows spinner and, for a matrix, a columns spinner (1 to 12 each), and a grid of text fields, one per
entry. A new matrix is 2×2 and a new vector three rows of one column. Tab and Shift+Tab, Up and
Down, and Left and Right at a field's ends move between the fields; a field resized away keeps its
text for when the grid grows again. Enter (or Insert) closes the editor and puts the language's
literal in place of the cell's selection, `[[1, 2], [3, 4]]` for a matrix and `[a, b, c]` for a
vector, an empty field written `0`; Escape, Cancel, a click outside or the window losing the focus
closes it with nothing changed. When the caret is inside a matrix or vector literal, either key
opens the editor on that literal, filled with its entries and of its shape, a ragged one sized to
its longest row with the missing entries empty, and Enter (or Replace) replaces it. The literal is
found by the editor's own bracket scan (`MatrixLiteral`, over `Brackets`), not the parser: the
innermost balanced `[…]` group around the caret whose comma-separated items are all scalars (a
vector) or all `[…]` rows of scalars (a matrix), a vector that is a matrix's row standing for the
matrix, and never a `[` after an operand or a head, spaces and tabs between them allowed: a letter,
a digit, `_`, `'`, a closing bracket, a subscript or superscript character, `! % °`, or `∑ ∏ ∫`
(indexing, or the bounds of a sum, a product or an integral). The new text is one `EditInput`
between two `endMerge`s, so one undo removes it; the caret goes after the literal and the focus back
to the cell. The editor builds text only: the cell evaluates it when submitted, as anything typed.

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

**The canvas.** The Canvas toggle on the toolbar, or View › Canvas, shows the canvas side
(`CanvasPane`) to the right of the cells in a split; hiding it leaves the cells alone. The side is
a small toolbar (the Select and Point tools, the analysis and vector tools below, the 1:1 aspect
lock and the coordinate entry), which wraps onto as many rows as the side's width needs (a
`FlowPane` with the toolbar's style rather than a `ToolBar`, which would hide what does not fit in
an overflow menu, the coordinate entry among it), over the algebra panel and
the coordinate plane (`CanvasView`, `docs/reference/canvas.md`). The pane only follows the
document: the plane draws the content of the document's canvas frame (`canvasFrameProperty()`,
built on the worker), the view's viewport after a pan, a zoom, a resize or the aspect lock goes to
the document through `Document.setViewport` (no undo step, saved with the session), and a viewport
the document changes comes back to the view. The colours the view's CSS gives it are sent to the
document (`setCanvasTheme`) when the pane enters the scene and whenever the theme changes, so the
content is built in them. While the pointer is over an object the status line shows the **trace
readout** in place of the notice, `x = …, y = …`: the coordinates of the nearest point of what is
hovered, each rounded to the decimal place of one pixel at the viewport's scale and printed as a
decimal in the document's number format (`Readout`). On a curve it adds `slope = …`, rounded to
the finer of the two places: a function graph's from the frame's slope, another curve's the secant
of the hovered segment of its samples; where a graph's derivative is undefined, no slope. Escape on
the canvas stops the running jobs, as in a cell. Whether the canvas is shown is the `canvasShown`
preference, so it is saved and restored.

**Export.** File → Export, enabled while the canvas is shown, has PNG Image and SVG Drawing
(`ui.CanvasExport` over `canvas.view.Export`, `docs/reference/canvas.md`, **Export**): the grid
and the objects as the view shows them, without the overlay. A PNG is the view's pixel size times
a scale chosen in a dialog (1 to 4, those within 8192 pixels a side; when none is, the status line says so instead), in the
view's theme; an SVG
is the view's pixel size, in the light theme unless the dialog's current-theme box is ticked, the
objects then drawn again in the light colours from the canvas the view shows. Both are drawn on
the FX thread with the view's label measure, a user's request and the same drawing as one frame;
the file is encoded (ImageIO, through `java.desktop`, which `javafx.graphics` already brings into
the runtime image) and written on an export thread, and the outcome, a failure included, goes to
the status line. The file chooser opens in the `exportFolder` preference, set to the folder of each
export, and a name that does not end in the kind's extension gets it (`plot.v2` becomes
`plot.v2.png`); the native chooser asked nothing about the name so made, so when that file exists
the export asks before replacing it. The questions go through `CanvasExport.Dialogs` (the scale,
the theme box, the file, the replacement), which the UI tests answer.

**The algebra panel.** Every canvas object in its algebraic form, one row each in the canvas's
order, then a blank row for typing a new one (`AlgebraPanel`). A row shows the object's colour (its
series colour in the theme), a visibility box (`SetStyle`), the form typeset from the box the worker
typeset with the frame's rows (`AlgebraRow`, `MathView.setBox`; the text when the form is over the
cap; a point's coordinates in the document's number format as decimals, `eval.Coordinates`: the
decimal and scientific formats with their digits, the exact and fraction formats as decimals with
the document's digits, since a coordinate is never exact, and a coordinate below 1e-12 of the
point's larger one, or of 1, as `0`, so a root reads `⟨-1.732050808, 0⟩` by default; a change of
format prints the rows again), why the object is undefined when there is something to say, and a
delete button (`RemoveObject`, which takes the objects built on it), kept at the row's right: a form
wider than the panel is clipped, and the panel never scrolls sideways. A shaded area's row is `A =
<value>` and a Riemann sum's `S = <value> (<method>, n = <pieces>)`, the value in the coordinates'
format, `undefined` with its reason when there is none; a Riemann sum's row has a choice of the
method (`left`, `right`, `midpoint`, `trapezoid`), each choice one `ReplaceObject`. A vector's row
is `AB = ⟨3.0, 2.0⟩, length 3.605551275`, named by its tail's and head's names (the origin `O`) or
by the variable it is bound to (`u = ⟨…⟩, length …`), a vector sum's `u + v = ⟨…⟩, length …` and a
projection's `proj_v u = ⟨…⟩, component …` with the scalar component `u·v/|v|`, a sum or a
projection named as an operand in parentheses, the numbers in the coordinates' format and the row
`u = undefined` when there is none, under it why when there is something to say (`u is not a
vector of two real numbers`, `the vector projected onto has length zero`). A bound arrow's row has a
choice of its tail, `from the origin` or `from` a point, each choice one `ReplaceObject`. A slider's
row has a slider control: its drag sets the value through `SetSlider`, snapped, the values of one
drag merging into one undo step while each follows the last within the stack's merge interval (one
second), and the control follows the slider's value otherwise. A double click edits a row's text;
Enter submits it through `Document.submitEntry`, which replaces the object, and a refused text comes
back to the editor with the reason under it, as it does in the blank row; Escape, or the focus
leaving, keeps the row as it was. Escape in the blank row clears its text, and in the empty row
stops the jobs. A row whose object and printed form did not change keeps its nodes, so a slider
being dragged is never rebuilt. One object is selected at a time: a click on a row or on an object
on the canvas selects it, the row is highlighted and the plane draws a halo around the object on its
overlay; a removed object is no longer selected.

**The tools.** With Select or Point, pressing a free point or a point on a path drags it
(`MovePoint`;
the moves of one drag merge into one undo step while each follows the last within the stack's merge
interval, so a drag paused for over a second makes two steps); a point on a path moves along its
path, to the parameter of the nearest point of the path's samples, interpolated between the two
samples of its segment (`canvas.tool.PathProjection`; the point's recompute evaluates the path
there). With
Select a drag anywhere else pans and a click selects what is under the pointer, points first. With
Point a click on a curve places a `PointOnPath` there and a click elsewhere a `FreePoint`, each one
`AddObject` (undone at once; a press that places a point and drags on makes two steps, the placement
and the drag), selected, named with the first free name of `A` to `Z`, then `A_1` to `Z_1`, `A_2`
and on, that no point and no slider's variable has, given when the command runs (`AddObject.named`,
so two quick clicks give two names), drawn in the least-used series colour
(`CanvasState.nextColour`), and dragged on while the button is held.

The analysis tools on the same toolbar, **On curve**, **Tangent**, **Intersections**, **Roots**,
**Extrema**, the calculus tools **Area**, **Riemann**, **Secant** and **Taylor**, and the vector
tools **Vector**, **Sum** and **Projection**, are the
canvas's tool state machines (`docs/reference/canvas.md`, **Tools**): the pane feeds the chosen one
the pointer's events in world coordinates with the document's canvas, the visible window and the
frame's slopes, and paints its preview on the overlay; a press it does not take pans. Its edits
become commands: an addition is one `Sequence` of `AddObject`s, each in the least-used series colour
as it is added (`CanvasState.nextColour`; `AddObject.named` for the points, `AddObject.inTurn` for
the rest), one undo step, with the last object it adds selected; a move is `MovePoint`; a search for
the intersections of two paths or a graph's features goes to the document (`addIntersections`,
`addFeatures`, below), whose members of one family take one colour, and whose refusal (none in view,
all already there, the path changed) shows beside the tools. Escape abandons a tool's construction
in progress, and stops the running jobs when there is none; choosing another tool abandons it too.
With On curve, a selected point on a path with a parameter shows the **coordinate entry** in the
toolbar, its prompt the parameter's name and value written as the rows write coordinates (`x =
1.25`). The text typed there is read on the worker as the point's row in the algebra panel is
(`Document.submitEntry`, below), with a refusal shown beside the tools, and does one of two things:

- a number, or `name = number`, is the point's new parameter; a point that was placed at an
  expression is taken off it and keeps the number as a parameter of its own;
- an expression that reads a defined name, a slider or a worksheet definition (`a + h`), places the
  point at that expression (`PointOnPath.at`): it follows the expression live, at every recompute,
  and refuses drags. An expression that reads a point's name is refused ("a point on a path follows
  sliders and definitions, not another point"), since no edge of the canvas's graph would order it
  after that point.

While a calculus tool waits for a number the same entry shows, its prompt the number's name (`a
=`): the text typed is parsed, made canonical and evaluated on the worker in the environment after
the last cell with the canvas's variables (`Document.readNumber`), and handed to the tool as the
expression it keeps with its value, or refused beside the tools ("this needs a real number").

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

The menus give the other actions accelerators: File › Quit (Shortcut+Q), Evaluate › Stop
(Shortcut+.), View › Keypad, History, Variables, Show steps and Canvas (Shortcut+Shift with K, H, V,
T and G) and Settings (Shortcut+,). File › Export (PNG Image, SVG Drawing), which acts on the
canvas, is above Quit, disabled while the canvas is hidden. On macOS they are in the system menu
bar. After an undo or a redo, the first cell it changed takes the focus.

## Tests

The unit tests are in `app/src/test/java/mathcanvas/app/` and never start the toolkit:

- `json/JsonTest`, `json/JsonProperties` (random trees, lone surrogates in strings included,
  survive a UTF-8 write and read);
- `document/CommandsTest`, `document/CommandStackTest`, `document/CommandStackProperties` (random
  sessions: undo after redo and redo after undo restore exactly; undoing everything returns to the
  start and redoing everything to the end);
- `eval/CanvasJobTest` (on queued executors with a plain sampler: a graph following a slider, a
  result applied only to objects not redefined meanwhile, a moved variable shown and recomputed, a
  stopped recompute keeping the old geometry, the start-up recompute after the cells, definitions
  and points followed, a cycle through the worksheet undefined, a new viewport resampled, each
  graph's slope in the frame, following a slider and kept for a graph not recomputed, the
  coordinate entry moving a point and refused when its path changed meanwhile, intersections and a
  graph's roots and extrema added once per place, the searches' refusals and Stop, only places in
  view, more than 50 refused; the vectors' rows, a bound arrow following its variable, undefined
  with its reason and back, and a projection onto the zero vector);
- `eval/CanvasStopTest` (on a real worker thread: Escape stopping a slow recompute, a stopped
  object held through a keystroke and an evaluation, a point landing on a path the job does not
  sample again stopped by the token bound into the sampler);
- `eval/EntriesTest` (each kind of entry, stored canonical and unevaluated, worksheet functions
  called, a slider's value, a point's coordinate on a graph and a line, an arrow bound to a name
  and rebound, the refusals);
- `eval/CanvasCellsTest` (slider statements and their errors, the canvas variables bound and
  shadowed, what a cell reads), `document/CanvasCommandsTest` (the canvas commands: names, the
  cascade, moves, snapping, cycles refused with their ids, a drag merged, undo restoring objects,
  anchors, names and the id counter, a sequence as one step);
- `eval/EvaluationJobTest` (the real engine: definitions across cells, errors with positions,
  several statements, layouts, steps, the number formats, cancellation between cells and inside a
  slow expansion), `eval/DocumentTest` (the document on queued executors: pending to done, stale
  results dropped, typing not restarting, a stopped cell staying cancelled until it is submitted,
  undo and redo re-evaluating, settings, loading), `eval/TypesetTest` (the cap, the echo
  layout, finding a node by its path) and `eval/InspectionsTest` (the inspector's rows printed,
  each definition once);
- `persist/FilesTest` (the fixtures of every version and the strict decoders, the version 1 to 3
  settings saved as the version 4 fixture, the version 1 and 2 sessions as the version 3 fixture,
  the version 2 canvas through its migration, a session with a canvas of every kind written and
  read back), `persist/CanvasCodecTest` (the every-kind fixture byte for byte both ways, sliders
  at `1e1001`, `1e-1001` and `1e3000000` written short and read back exactly, a 1,001-digit
  bound, and each strict rejection, the vectors' among them), `persist/CanvasCodecProperties`
  (random valid canvases of every kind written as text and read back; a viewport, to zooms of a
  relative 10⁻¹², centres to 10¹⁵ and wide or tall windows, moving each bound under a pixel, and
  stable from the first reading on), `persist/SessionReloadTest` (a slider, graphs that read it, a
  point and tracked intersections saved, loaded into a new document and recomputed to the same
  geometry), `persist/MigrationsTest` (a made-up three-version history),
  `persist/DataDirectoryTest`, `persist/StoreTest` (with a fixed clock: setting aside, a file that
  cannot be renamed, saving, a tree made later on the store's thread once, a failed write, a tree
  that cannot be made reported with the other files still written, version 1 settings and session
  files through the store);
- `eval/CoordinatesTest` (the format coordinates take under each number format, a root's row with
  its ordinate's noise written `0`, the coordinate entry's echo);
- `text/MessagesTest`, `ui/ShownTest`, `ui/BracketsTest`, `ui/DefinitionShortcutTest`,
  `ui/ReadoutTest` (rounding to a pixel, the number formats, the slope's places),
  `ui/MatrixLiteralTest` (the literal under the caret: vectors, matrices from a row or between
  rows, the innermost, ragged rows, deeper nesting, indexing and sum bounds, empty and unbalanced
  groups, a caret outside; the literal written).

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
  `MatrixEditorTest` (a 2×2 matrix built by keyboard alone and evaluated, a literal edited in place
  and undone in one step, a ragged one padded, Escape and the focus lost, a vector, the popup in
  both themes with screenshots), `HistoryPanelTest`, `InspectorTest`, `StepsTest`,
  `SettingsDialogTest` (decimal results, the undo of the settings, reset and cancel), and
  `SectionSixTest` (every `PLAN.md` §6 example in Phase 2's scope through the worksheet, and the
  menus);
- `CanvasModeTest`, the canvas mode on real graphs: shown and hidden by the toggle and the menu,
  the viewport reaching the document without an undo step, the theme reaching the view and the
  content, the readout, the algebra panel's rows, slider control, visibility, selection both ways
  and delete, refused texts in the blank row and a row's editor, the point tool's free points and
  points on a curve, drags of a free point and along a curve, a slider cell re-printing `2a` and
  moving a graph `a·x²` (with `canvas-mode-light.png`, `canvas-mode-dark.png` and
  `canvas-algebra-panel.png`), a graph redrawn when its row is edited, a point on the graph
  following a slider and removed by undo, Escape on the canvas stopping a slow cell and a long
  recompute, the canvas surviving a restart (objects, names, scale, and the curve recomputed),
  and File → Export: a PNG twice the view's size in the dark theme, an SVG in the light theme
  whose object elements match `canvas-export-objects.txt` with every vertex on its graph (the
  bytes are not compared: the view's size and the label widths depend on the platform's fonts),
  the dialogs' questions answered (the scales offered, a cancel, a name without an extension, a
  name with another dot, a refused replacement, the theme box), and a file that cannot be written
  (kept as `canvas-export.png` and `canvas-export.svg` beside the screenshots), and the analysis
  tools: the tangent to `x²` previewed with its slope in the readout, added with its point,
  following the point dragged along the graph, and previewed in the dark theme
  (`canvas-tools-tangent.png`, `canvas-tools-tangent-dark.png`), the intersections of `x²` and `x
  + 2` at −1 and 2 after Escape abandoned a first pick, the lower id first whatever the click
  order, refused when already there (`canvas-tools-intersections.png`), the roots and extrema of
  `x³ − 3x` named in order (`canvas-tools-roots.png`), and the coordinate entry moving a point on
  a curve and showing a refusal; the calculus tools: the area under `x²` from a clicked 0 to a
  typed 2 with its row and label `A = 2.666666667`, undone in one step
  (`canvas-calculus-area.png`), a Riemann sum with its slider `n` added, its row followed through
  the slider and the method chosen in the row (`canvas-calculus-riemann.png`), the secant of `x²`
  at 1 with slope 3 becoming 2.25 as `h` is dragged to 0.25 beside the tangent of slope 2
  (`canvas-calculus-secant.png`), and the Taylor overlay of `sin` at 0 in a colour of its own,
  the polynomial `x` at the slider's 2 and `x − x³/6 + x⁵/120` once `n` is dragged to 5
  (`canvas-calculus-taylor.png`); the vector tools: two vectors from a shared point by clicks,
  their sum and the projection of one onto the other with their rows, a point dragged and the
  figure following, undone in one step, a vector from a variable re-anchored at a point from its
  row, both themes, and the session saved and loaded again with every vector recomputed
  (`canvas-vectors.png`, `canvas-vectors-bound.png`, `canvas-vectors-dark.png`); `StepsTest` shows
  the badge "numeric" beside an integral evaluated under the decimal format, with steps off
  (`worksheet-numeric.png`). A test that needs a drawing the document would not make shows a frame
  of its own on the pane, once the canvas job has delivered.

How they run is in `docs/reference/testing-tools.md`, **UI tests**.
