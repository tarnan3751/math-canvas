# CLI

How the command-line runner in `cli/` works: the REPL, the script runner and the golden corpus
checker over the engine's `Session`. Read `CLAUDE.md` first; this file assumes its **Commands**.
The module `mathcanvas.cli` requires `mathcanvas.engine`, and Jackson Core for the generated help
(**Help**) only, and the build's boundary check
applies to it (`docs/reference/testing-tools.md`, **The module-boundary check**).

## Invocation

```
mathcanvas-cli [options] [file.mc ...]
```

- With files, each is run as a script, in order, in one session, so a later file sees the
  definitions of an earlier one.
- With `-e TEXT` (`--eval TEXT`), `TEXT` is run as a script; several are run in order, mixed with
  the files in command-line order.
- With neither, the REPL reads statements from standard input (**The REPL**).
- With `--check`, the files are golden corpus files to check (**The corpus**).

The launcher is `cli/build/install/mathcanvas-cli/bin/mathcanvas-cli` after
`./gradlew :cli:installDist` (it runs on `JAVA_HOME`, or the `java` on the `PATH`, which must be
Java 25); `./gradlew :cli:run --args="…"` runs the same entry point on the build's toolchain, with
standard input passed through. Both give the JVM a 64 MB thread stack (`-Xss64m`, the
application's default JVM arguments), since a recursion to the language's call limit outgrows the
default (`docs/language.md` §19.5).

| Option | Effect |
|---|---|
| `-e`, `--eval TEXT` | run `TEXT` as a script |
| `--ascii`, `--unicode`, `--latex` | the printer for results and echoes; Unicode by default, the last one given wins |
| `--precision N` | the session precision in digits, 1 to 10,000 (default 34) |
| `--angle rad\|deg\|grad` | the angle mode (default `rad`) |
| `--steps` | trace every evaluation and print its steps (`Session.setTracing`) |
| `--check` | check the files as corpus files; takes files only, not `--eval` |
| `--stats` | count the rewriter's work over the whole run (`RewriteCounts`) and print it on standard error when the run ends, an error's included: `stats: <tries> rule tries, <rewrites> rewrites`, then the ten rules tried most, one per line, `stats: <tries> tries <rewrites> rewrites  <rule set> <rule>` (the simplifier's rules under the combined set `standard`); standard output is unchanged; `tools/measure.sh` reads it over the corpus |
| `--version` | print `mathcanvas-cli <version>` (`Version.current()`) |
| `-h`, `--help` | print the usage |

An unknown option (anything else beginning with `-`), an option without its value, a bad value,
or `--check` without a file or with `--eval` prints `mathcanvas-cli: <reason>` and the usage on
standard error and exits 2.

Text is read and written as UTF-8 whatever the platform's default. Every line written, to standard
output and standard error, the checker's report included, ends in LF on every platform, never the
platform's separator, so the output is the same on Windows; CR LF and lone CR line ends in a file,
in `--eval` text or on standard input read as LF before lexing, which leaves line and column
numbers unchanged. The CLI writes no file; it reads standard input and the files named, and
nothing else.

## Units and statements

Input is cut into **units** (`Units`): a line, or the lines that an open bracket joins (the lexer's
own rule, `docs/language.md` §2), or the lines of a block not yet closed by its `end` (`for … end`,
`function … end`, §19): a unit ends where the lexer emits a newline outside brackets and the text so
far does not end inside a block, which the parser tells (`ParseException.incomplete()`, the text
parsed with no user functions, since they do not change where a block ends). So its statements are
complete, and a program over many lines is one unit; a block never closed runs to the end of the
input and fails there with "expected `end`". The block test parses the unit again at each of its newlines, so
it grows with the square of a block's length; that is kept for 1.0, since a block of a few hundred
lines parses in milliseconds. A unit is parsed whole, with the session's names (`Session.names()`)
and the unit's own definitions, and then its statements run in order. So a parse error anywhere in
a unit runs none of that unit's statements, while the units before it have run. A lexical error
ends its unit at the error's line and the next unit starts on the line after.

## Output

For each statement, on standard output:

- the values its program printed (`docs/language.md` §19.2, `Outcome.Value.run`), one line each
  through the printer's display form, in order, before anything else the statement prints, whether
  it is quiet, has no value or fails;
- a printing value: its display form through the chosen printer (`Printer.display`), followed by
  `, k ∈ ℤ` for each integer parameter of a general solution in it (`Outcome.Value.parameters`,
  `docs/language.md` §14.7);
- a definition: its echo through the same printer, the head and the body as parsed
  (`f(x) := x² + 1`, `g := t → t²`); a function defined with `function`, its head (`function
  fact(n)`);
- a statement with no value (a loop, `Outcome.Executed`): nothing more;
- a quiet statement (ended by `;`): nothing;
- a traced value (`steps(e)` at the top of a statement, or any value under `--steps`): first its
  narration steps, from `Narration.steps` (each operation's curated narrator, **Narration** in
  `engine.md`) with the chosen printer, two lines each,

  ```
    step 1: <text>
    = <the whole expression after the step>
  ```

  then the value. A statement that runs a program narrates its run first (`docs/language.md`
  §19.6), whether it has a value or not, the steps numbered on through the value's. A value whose
  evaluation recorded no rewrite has no steps (arithmetic folded on
  the way, as in `2 + 3`, records none): one line says so instead, `  (no steps to show)`, from
  the CLI's bundle `mathcanvas.cli.messages`, then the value.

- a `help(name)` statement (`docs/language.md` §12.2), not ended by `;`: the name's help instead of
  its value (`Help`), from `help.json`, which build-logic's `GenerateHelp` writes into the CLI's
  resources from `docs/language.md` as it does the app's (`docs/reference/app.md`, **Help**), read
  with Jackson's streaming parser: the name with its category, then, indented by two,
  its arguments, its alias, its description and its examples, `input → result`:

  ```
  abs — Functions
    arguments: 1
    also written |x|
    The absolute value of a number, or the modulus of a complex number
    examples:
    abs(-3) → 3
  ```

  `help` takes a function's or a command's name; a name the reference does not list prints `no help
  for <name>: help takes a function's or a command's name; the operators, mod included, are in the
  app's Help window` (`help(mod)` does not parse: `mod` is an infix word). Inside an expression
  (`[help(abs)]`) help is an ordinary value, the name.

The CLI has no number format, so its engine settings leave the quadrature digits at 0: a definite
integral with no closed form is evaluated numerically only under `numeric(…)` (`docs/language.md`
§10.2), and `steps(numeric(integrate(…)))` or `--steps` shows the step that says the value is
numeric and to how many digits (the corpus `numeric-integration.mc`).

## Errors

A parse or an evaluation error goes to standard error as

```
<name>:<line>:<column>: <message>
<the source line>
<spaces>^
```

where `<name>` is the file as given, `<eval>` for `--eval` and `<stdin>` in the REPL; the line and
column are 1-based, the column in code points, and the caret line copies each tab of the source
line before the column so the caret lines up. A parse error is at its own position. An evaluation
error is at the position of the node it carries, from the unit's `Script.positionOf`, or at the
start of the failing statement when the unit holds no position for that node (a shared constant, or
a node from the body of a definition made in an earlier unit). In an evaluation message, the text
after the first `": "`, where the engine embeds a node's display, is cut to 200 code points
followed by `…`; the engine's message is unchanged.

A cancelled statement (`Outcome.Cancelled`, `engine.md` **Cancellation**), which the CLI never
causes, would be reported the same way as `statement stopped` at the statement's start.

A file that cannot be read gives `mathcanvas-cli: cannot read <file>: <reason>` (`no such file`,
`not UTF-8 text`, or the system's message).

## Exit status

| Status | When |
|---|---|
| 0 | a clean run; the REPL at its end |
| 1 | scripts and `--eval`: the first error, which stops the run; a file that cannot be read; the REPL: standard input that cannot be read; `--check`: any failure |
| 2 | a bad command line |

## The REPL

The REPL reads standard input line by line. A line that leaves a bracket or a block open is joined
with the next, and the prompt becomes `… `; otherwise the prompt is `> `. The prompts are shown only when
standard input is a terminal (`System.console().isTerminal()`), so piped input gives only results.
Each unit is run and printed; an error is reported and the REPL goes on, with the statements after
the failing one in the same unit still run. `exit` or `quit` on a line of its own (outside a
continuation), or the end of input, ends it with status 0; a unit still open at the end of input is
run, and reports its error. Line numbers in errors count the lines read. There is no history and no
line editing beyond what the terminal gives.

## The corpus

A **golden corpus file** (`.mc`) is an ordinary script whose expectations are full-line comments
after a statement:

| Comment | Expects |
|---|---|
| `# => <text>` | a line the statement prints on standard output, one comment per printed line, in order |
| `# !! <gist>` | that the statement fails, with a message containing `<gist>` |
| `# steps:` then `#   step N: <text>` and `#   = <expr>` lines | the narration steps printed before the value (or the line saying there are none); each line is the printed one with `# ` before it |

A step line is exactly `#`, three spaces and the text (which does not begin with a space). The
`# steps:` block ends at the first line not of that form: a `# =>` line, or any other comment, such
as one indented by four spaces, and no line after that is read as a step.

An expectation belongs to the statements that start on the last line, before the comment, on which
any statement starts: normally the statement just above it, and all the statements of that line
when several share it (their printed lines are compared in order, and only the last of a line of
`;`-separated statements prints). A statement with no expectation is setup: it is run and its
output is ignored, but it must not fail. The expected text is the Unicode printer's output. Any
other comment is ignored; a statement the engine cannot answer correctly yet is kept as
`# later: <statement>` and `# later: # => <text>` lines, so the brief that lands the answer
removes the prefix.

`mathcanvas-cli --check [--unicode] file.mc …` (`Checker`) runs each file in a session of its own,
past failures, and compares it statement by statement. It prints `ok <file> (<n> statements)`,
where `n` counts the statements that carry an expectation, or `FAILED <file> (<n> statements, <k>
failures)` followed by a unified diff: `--- <file> (expected)`, `+++ <file> (actual)`, and a hunk
per differing statement,

```
@@ <file>:<line> @@
 <the statement's source line>
-<the expectation comments>
+<the actual output as expectation comments>
```

whose `+` lines can be pasted back into the file. The last line is `corpus: <files> files,
<statements> statements, <failures> failures`, and the status is 1 on any failure. An expectation
before any statement is a failure too. A corpus file is a normal script, so `mathcanvas-cli
file.mc` just runs it, stopping at its first error.

The corpora live in `engine/src/test/corpus/`: `arithmetic.mc` (the number tower),
`definitions.mc` (§14.3 and the definition errors), `collections.mc` (sets, vectors, matrices and
their errors), `language.mc` (the rows of `PLAN.md` §6), `algebra.mc` (the algebra commands of
§14.9), `simplify.mc` (the standard simplifier's rule sets, with their facts, examples and steps),
`differentiation.mc` (the derivative rules, what stays unevaluated, higher orders, the free
variable and the steps), `integration.mc` (the integration techniques, definite integrals, what
stays unevaluated, the free variable and the steps, `docs/language.md` §14.13), `applications.mc`
(arc length, area, volumes, extrema and optimisation, `docs/language.md` §14.15), `solve.mc`
(solving, `docs/language.md` §14.11), `limits.mc` (limits by each method, one-sided, at infinity,
what stays and the steps, §10.3), `series.mc` (Taylor polynomials, sums and products by family,
§10.4 and §14.14), `linalg.mc` (determinants, row reduction, inverses, the subspaces,
`linsolve`, Gram–Schmidt, the norms, the characteristic polynomial, eigenvalues, eigenvectors and
diagonalization, and their steps, §14.16), `numeric-linalg.mc` (the numeric decompositions, their
digits, what stays and the errors, §14.17) and `trig-equations.mc` (general solutions by each step
of the trigonometric path, the parameter, what goes to the scan, and degrees, minutes and seconds,
§14.18) and `geometry.mc` (the triangle solver's cases and steps, shape formulas, coordinate
geometry and conics, §14.19), and `odes.mc` (`dsolve` by each class, initial conditions, systems,
what stays, the constants and the prime notation, §5 and §14.22), and `numeric-odes.mc` (`odesolve`
at a point and on a grid, systems, orders above one and the errors, §14.22), and `number-theory.mc`
(primality, the primes, `ifactor`'s product, the divisor functions, modular arithmetic, the Chinese
remainder theorem and Diophantine equations with their steps, and the counting functions, §14.23),
and `discrete.mc` (the boolean operators and the rule set `logic`, truth tables and recurrences,
§8.4 and §14.24), and `units.mc` (quantities, sums and products of units, temperatures, comparisons,
functions and the dimension errors, §3.6 and §14.26), and `bases.mc` (base literals, integers shown
in other bases, the digits, the bitwise functions and the errors, §3.1 and §14.27), and `finance.mc`
(the time value of money, the rates, cash flows and the schedule, §14.28), and `statistics.mc`
(descriptive statistics, counts, decimal and constant data, the distributions against the
NIST/SEMATECH tables, regression against Anscombe's first set and the NIST StRD Norris and Pontius
datasets, the hypothesis tests and the confidence intervals with their steps, and the errors,
§14.25), and `programs.mc` (blocks, loops, functions, scope, `print`, recursion, the
lists' updates, the steps of a call and the errors, §19); the set operators are in `collections.mc`.

**The runner.** `tools/run_corpus.sh [file.mc …]` builds `:cli:installDist` quietly, then runs
`mathcanvas-cli --check --unicode` once over the files given (relative to the caller's directory) or
every `*.mc` under `engine/src/test/corpus/` and its subdirectories, sorted, and exits with its
status. It runs the launcher on the build's Java toolchain, whatever JDK is on the `PATH`:
`installDist` depends on `writeJavaHome`, which writes the toolchain's home to
`cli/build/toolchain/java-home`. The gate's corpus step runs it and reports its last line.

**The JUnit mirror.** `CorpusTest` runs the same checker in-process over every corpus file, found
recursively and sorted as the runner finds them, one parameterised case per file, so `./gradlew
build` (and CI, which runs only the build) covers the corpora too; the `cli` test task passes the
directory as the system property `mathcanvas.corpus` and declares it an input, so editing a corpus
file reruns the tests.

## Tests

The tests are in `cli/src/test/java/mathcanvas/cli/`:

- `MainTest`: the printers, echoes, settings, programs' output and their lines in the REPL, the
  steps of a run, error positions and carets, files in one session,
  unreadable files, LF output and CR LF input, the REPL (errors, brackets, `exit`, prompts), bad
  command lines, `--version`;
- `UnitsTest`: units, open brackets and blocks, lexical errors;
- `RunnerTest`: the steps lines from a hand-built trace, the message cut, the caret, a name's help
  and a name without one;
- `CheckerTest`: passing and failing files, the diff, setup errors, steps expectations and where
  a steps block ends, the summary;
- `CorpusTest`: the corpora.
