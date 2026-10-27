# App

How the desktop application in `app/` works: the document model, the command stack, the
evaluation worker, the files in the data directory and their schema, the settings and the theme,
and the minimal worksheet window of Phase 2b. Read `CLAUDE.md` first; this file assumes its
**Commands** and **Architecture rules**. The module `mathcanvas.app` requires `mathcanvas.canvas`
(and through it `render` and `engine`), `javafx.controls` and `com.fasterxml.jackson.core`.

| Package | What it holds |
|---|---|
| `mathcanvas.app` | `Main`, the entry point; `Workspace`, the loaded document with its worker and store |
| `mathcanvas.app.document` | the immutable state, its cells and outputs, the settings, the commands, the undo stack |
| `mathcanvas.app.eval` | `EvaluationJob`, one run of cells on the engine; `Document`, the stack with evaluation |
| `mathcanvas.app.json` | the JSON value tree and its reading and writing |
| `mathcanvas.app.persist` | the data directory, the two files, their migrations, the store |
| `mathcanvas.app.ui` | the window, the cell view, the theme binding, the strings |

## The document model

`DocumentState(cells, settings, nextId)` is the document at one moment, immutable: every change
makes a new state, so the undo stack keeps states and needs no inverse operations. There is always
at least one cell; ids are distinct and below `nextId`, the id the next new cell gets. Canvas
objects join this state in Phase 3.

- `Cell(id, input, evaluated, output)`: the id stays with the cell through edits and moves; the
  input is the typed text; `evaluated` says the cell was submitted, so every re-evaluation runs
  it; a blank cell is never evaluated.
- `Output` is `None` (never run), `Pending` (waiting for the worker), `Cancelled` (stopped before
  it finished; nothing of it stays) or `Done(results, after)`: one `Result` per statement run, or
  the one parse error, and the engine `Environment` after the cell, which the next cell is
  evaluated in.
- `Result` is `Value(value, trace, quiet, text)`, `Defined(echo, text)` or
  `Failed(message, at, line)`. The text is rendered on the worker (`Printer.display` for a value,
  `Printer.echo` for a definition, the Unicode printer), so the FX thread only shows it; a failure
  keeps its position in the cell and the source line it was on, for the caret.
- `DocumentSettings(precision, angleMode, numberFormat)` belong to the document and change through
  its commands, so an undo reverts them; `engine()` gives the engine `Settings`. `NumberFormat`
  (`EXACT`, `DECIMAL`, `SCIENTIFIC`, `FRACTION`) is stored and saved; the worksheet applies it
  from Phase 2c.
- `Preferences(theme)` belong to the person, not the document: outside the state and the undo
  history, so Ctrl+Z never changes the theme, and saved beside the document settings.

## Commands and the undo stack

A `Command` maps a state to the next. A command that does not apply (a cell that no longer
exists) returns the state unchanged, and nothing is recorded.

| Command | Effect |
|---|---|
| `EditInput(id, text)` | sets a cell's text (typing, or a recalled input); the output stays until the cell is submitted |
| `Submit(id)` | a cell with text becomes evaluated and pending (even if it was stopped), and so does every evaluated cell after it that was not stopped; submitting the last cell adds a blank cell after it; a blank cell becomes not evaluated |
| `DeleteCell(id)` | removes a cell unless it is the only one; when it was evaluated, the evaluated cells after it that were not stopped become pending |
| `SetSettings(settings)` | replaces the document settings; a new precision or angle mode makes every evaluated cell that was not stopped pending, a new number format nothing |

A cell the user stopped (`Cancelled`) is made pending only by its own submission: Escape means what
it says, and the cell stays `(cancelled)` until Enter is pressed on it.

`CommandStack` holds the current state and the undo and redo stacks of whole states. `execute`
pushes the state before a command and clears the redo stack; `undo` and `redo` move whole states
between the stacks, so each restores exactly what was there. At most 500 steps are kept, the oldest
dropped first.

**Merging.** A command's `mergeKey` (an `EditInput`'s is its cell) makes consecutive commands one
step: a command whose key equals the last step's, within one second of the command before it,
replaces the current state and keeps the step's starting state. Merged edits that come back to that
starting state (a character typed and deleted) remove the step. Any other command, an undo, a redo
or `endMerge` (the window calls it when the focus moves to a field) ends the step. The clock is
passed in, so the tests control it.

**Amending.** `amend` changes the current state without recording a step: it is how evaluation
results enter. An undo therefore returns to the state before the last command with the outputs it
had then, which may be pending; the document re-evaluates what is pending after any undo or redo.

## Evaluation

`EvaluationJob.run(cells, before, cancellation, sink)` evaluates a run of cells in order on one
engine `Session` built on the environment `before`. Each cell's text is parsed as a script with the
names the cells before it defined (`Session.names()`), and its statements run in order past
failures, as in the CLI's REPL; a parse error is the cell's one result. An evaluation error is
placed at its node's position in the cell (`Script.positionOf`), else at the statement's start.
Each finished cell goes to the sink at once, with the environment after it. When the cancellation
is requested, at the engine's next check (`docs/reference/engine.md`, **Cancellation**) or while
a value is rendered (`Printer.display(e, cancellation)`), the cell being run and every later cell
are reported `Cancelled`: nothing of the stopped cell stays, and the cells before it keep their
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

`settings.json` holds the document settings and the preferences. The angle mode is `radians`,
`degrees` or `gradians`; the number format `exact`, `decimal`, `scientific` or `fraction`; the
theme `system`, `light` or `dark`; the precision an integer from 1 to 10,000.

```json
{
  "version": 1,
  "precision": 34,
  "angleMode": "radians",
  "numberFormat": "exact",
  "theme": "system"
}
```

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

Version 1 is the first; its fixtures are `session-v1.json` and `settings-v1.json`.

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

Precision and angle mode reach the engine through the environment each job starts from; changing
them re-evaluates every evaluated cell. The minimal window has no settings panel: the settings come
from `settings.json` and change through `SetSettings` (Phase 2c adds the panel).

The theme is Modena plus the app's stylesheet, `app.css`, whose colours are looked-up colours on
`.root` redefined under `.root.dark` (`-fx-base`, `-fx-background`,
`-fx-control-inner-background`, and the app's `-mc-text`, `-mc-muted`, `-mc-error`); Modena
derives its own colours from the first three, so the controls follow. `ThemeBinding` puts the style class `dark` on the scene's root when the theme
is `DARK`, or when it is `SYSTEM` and the scene's colour scheme
(`Scene.getPreferences().colorSchemeProperty()`, which follows the platform's) is dark, and keeps
it in step with both.

## Strings

Every user-facing text is in the bundle `mathcanvas.app.messages` (root locale), read through
`ui.Messages.text(key, args…)` with `MessageFormat`, so a single quote is written twice: the window
title (`math-canvas {0}`), the toolbar, the theme names, the cell's prompt, the pending and
cancelled notes, the status line's messages and the start-up errors. `MessagesTest` checks that
every key the code names is in the bundle.

## The window

`WorksheetWindow` is the minimal worksheet of Phase 2b: a toolbar (Undo, Redo, Stop beside a busy
indicator shown while a job runs, and a theme button that cycles System, Light, Dark), the cells
in a scroll pane, and a status line. The title is `math-canvas <version>`. A `CellView` is a text
field and a label per line of output: a value's display or a definition's echo in the math font
(`MathFont`), quiet statements showing nothing; an error as its message, its source line and a
caret under the column (`Caret.under`), in a monospaced font; `…` while pending; `(cancelled)`.
A result or an error message longer than `Shown.MAX_SHOWN` (10,000) code points is cut there, as
the CLI cuts its messages, followed by `… (n more characters)` from the bundle; the full text stays
in the cell's `Result`.
The view only follows the document's state: typing sends `EditInput`, and a state change sets a
field's text only when it differs.

| Key | In a cell |
|---|---|
| Enter | submit the cell and move to the next one (the new cell after the last) |
| Up, Down | move to the cell above or below |
| Alt+Up, Alt+Down | recall the inputs of the cells above into this one: older, then newer, back to what was typed |
| Escape | stop the evaluation |
| Backspace in an empty cell | delete the cell, unless it is the only one, and move up |
| Shortcut+Z | undo the document (not the field's own undo) |
| Shortcut+Shift+Z, Shortcut+Y | redo |

After an undo or a redo, the first cell it changed takes the focus. Phase 2c replaces the output
labels with typeset math and adds the keypad, the variables inspector, the steps and the number
formats.

## Tests

The unit tests are in `app/src/test/java/mathcanvas/app/` and never start the toolkit:

- `json/JsonTest`, `json/JsonProperties` (random trees, lone surrogates in strings included,
  survive a UTF-8 write and read);
- `document/CommandsTest`, `document/CommandStackTest`, `document/CommandStackProperties` (random
  sessions: undo after redo and redo after undo restore exactly; undoing everything returns to the
  start and redoing everything to the end);
- `eval/EvaluationJobTest` (the real engine: definitions across cells, errors with positions,
  several statements, cancellation between cells and inside a slow expansion) and
  `eval/DocumentTest` (the document on queued executors: pending to done, stale results dropped,
  typing not restarting, a stopped cell staying cancelled until it is submitted, undo and redo
  re-evaluating, settings, loading);
- `persist/FilesTest` (the version 1 fixtures and the strict decoders), `persist/MigrationsTest`
  (a made-up three-version history), `persist/DataDirectoryTest`, `persist/StoreTest` (with a
  fixed clock: setting aside, a file that cannot be renamed, saving, a failed write);
- `ui/MessagesTest`, `ui/ShownTest`.

The UI tests are `app/src/uiTest/java/mathcanvas/app/WorksheetWindowTest`, the Phase 2 acceptance:
`1/3 + 1/6` evaluates to `1/2` and the next cell takes the focus; recall; undo and redo by key and
by button; a long value cut with its note; both themes, with screenshots;
`expand((x + y + z + w + 1)^30)` stopped by Escape within a second, with the FX thread answering
meanwhile, and a later cell evaluated without re-running it; a restart keeping the session and the theme; an
unreadable session set aside and reported. How they run is in `docs/reference/testing-tools.md`,
**UI tests**.
