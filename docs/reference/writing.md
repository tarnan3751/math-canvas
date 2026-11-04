# Writing for the people who use the app

Every word a person can read in the app follows this guide: the bundles (`messages.properties`
and its translations), the Help entries (generated from the §12 tables of `docs/language.md`),
the engine's error messages, the step explanations (`narration.properties`), the settings
dialog, tooltips, the status line, the welcome worksheet, the README and the getting-started
page. The reviewer of a change that touches any of them checks it against this file.

## The voice

- **Short and direct.** One idea per sentence, a verb in it, around twelve words. A label is
  one to three words. A tooltip is one sentence that says what happens when you use the thing.
- **Plain words.** Say what the person sees and does, in the words a teacher would use at a
  whiteboard. The maths terms stay (derivative, integral, matrix, radians); the engine's terms
  do not. Never, in anything a user reads: *narrate*, *narration*, *typeset*, *typesetting*,
  *held*, *hold*, *canonical*, *provider*, *marked numeric*, *rule set*, *rewrite*, *fire*,
  *stays* (for an unevaluated result), *evaluate* when *work out* or *run* will do, *outcome*,
  *session* (say *worksheet* or *file*), *document*, *node*, *seam*, *lane*, *tier*.
- **Say what happened and what to do.** An error names the thing that went wrong in the
  person's words and, when there is one, the way out: "`sin` needs one argument, for example
  `sin(x)`", not "arity mismatch". A warning says what the app did instead.
- **Talk to the person, not about the engine.** "Shown to 16 digits" rather than "the value is
  marked numeric and printed by the numeric printer". The app never says "I".
- **Consistent names.** One name per thing, everywhere, capitalised as a proper noun only when
  it is the name of a part of the app:

  | The thing | Its name | Not |
  |---|---|---|
  | The column of cells | the worksheet, Worksheet | document, session, sheet |
  | A line you type and its answer | a cell | an entry, a statement, an input |
  | The drawing area | the canvas, Canvas | the plane, the view, the graph area |
  | The buttons with functions and symbols | the keypad, Keypad | palette, keyboard |
  | The list of names you defined | Variables | inspector, environment, frame |
  | The explanation under a result | Steps, "Show steps" | trace, narration, rewrite log |
  | The window of functions and commands | Help | reference, manual, docs |
  | A number shown to 16 digits | an approximate value, "approximately" | numeric, a decimal, a float |
  | A result left unsimplified | "left as it is" | stayed, unevaluated, held |
  | The 2D/3D switch | the 3D view | space view, the scene |
  | A function you plotted | a graph | a plot, a curve object |
  | A point that follows a curve | a point on a graph | a path point, a constrained point |

- **Sentence case** for labels, buttons, menu items and headings ("Show steps", not "Show
  Steps"). No full stop on a label or a one-line tooltip; full stops in multi-sentence text.
- **Numbers and symbols as the person typed them.** Error messages quote the person's input
  in backticks as they wrote it, not in canonical form.
- **No filler.** No "please", no "simply", no "just", no "successfully", no exclamation marks,
  no "oops". No "note that". A sentence that can be cut is cut.
- **Steps explain the mathematics, not the program.** A step says what was done to the
  expression and why it is allowed, as a textbook would: "Divide both sides by 2", "Apply the
  chain rule to sin(x²)", "Substitute u = x² + 1". Never "rule `expand-product` fired" or "the
  simplifier chose". Where a step names a method, use the textbook name: integration by parts,
  partial fractions, L'Hôpital's rule, row reduction.
- **Settings describe the effect.** A setting's label is what it changes ("Angle unit"), its
  options are the values ("Radians", "Degrees", "Gradians"), its help text is one sentence on
  when to change it.

## Examples

| Before | After |
|---|---|
| The value is marked numeric and printed to 16 digits | Approximate value, shown to 16 digits |
| integrate stays: no closed form was found | No exact answer was found. Add `numeric` to get an approximate one, for example `numeric(integrate(e^(x^2), x, 0, 1))` |
| The condition did not decide: y > 0 | The app cannot tell whether `y > 0`. Use `assume(y > 0)` or `assume(y <= 0)` first |
| Trace every evaluation from now on, so each value shows its steps | Show the steps under every new result |
| a parameter cannot be global | `x` is a parameter of this function, so it cannot be declared global |
| Narrates the run under steps | Shows each statement of the program as it runs |
| Rewrite: collect-like-terms | Collect like terms |

## Checking a change

A reviewer reads every changed string once, aloud if it helps, and asks four questions: Would a
first-year student understand it without the docs? Does it use the one name for each thing? Does
it say what to do next when something went wrong? Could it be shorter? A string that fails any of
them is a finding. The test `LiteralStringsTest` keeps strings in the bundles; nothing else
enforces this guide: people do.
