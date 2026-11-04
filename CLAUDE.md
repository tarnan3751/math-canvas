# math-canvas (working name)

A desktop math workspace grown from the author's first Java project, Scientific-Calculator. A
symbolic engine written from scratch (exact arithmetic, canonical forms, a rule-based simplifier
that records every step), one canvas for graphing and interactive geometry, and coverage from
arithmetic through multivariable calculus and linear algebra. Java 25, JavaFX 25, Gradle 9 with
JPMS. Solo developer with an agent team.

This file is the **map**: what the project is, where each area lives, the commands, the
conventions and the workflow. The scope, the phases, the status, the risks and every recorded
decision are in `PLAN.md`, which lives at the root of the owner's main working tree (the
coordinator's checkout, whose path every brief names) and is **gitignored on purpose**, so a
clone does not carry it: read it from the main tree by that path. How each area works is in its
**reference file** under `docs/reference/`, written when the area lands. Read this file, then
`PLAN.md`'s sections for the phase in progress, then the reference file of every area a task
touches, then the header comments of the modules involved. A section named in bold is a heading
of this file or of a doc.

## Status

**Version 1.0.0 has shipped: every planned phase has landed.** The language has programs (blocks,
`if`, `for`, `while`, user functions with `return`, `print`, a step budget and narrated runs) in
worksheet cells and `.mc` scripts; the app has a Help window with a runnable example for every
command generated from the language reference, an opt-in update check against GitHub Releases,
accessible names and a keyboard order on every control, a locale setting with a bundle scaffold,
an icon of its own and its name in one build property, and a documentation site built from
`docs/` and attached to every release by the release workflow. The engine computes exactly, simplifies through
the `basic`, `rational`, `exp-log`, `radical`, `trig`, `logic`, `special` and `units` rule sets
with narrated steps (the trig identities keyed to the unit of their argument in every angle
mode), does polynomial algebra, differentiates (successively and partially, the `∂` notation,
the chain rule in several variables narrated, `grad`, `jacobian`, `hessian`, `divergence`,
`curl`, `laplacian`, Lagrange multipliers), integrates (the table, linearity, substitution, by
parts, the trig identities and substitutions, completing the square, partial fractions and
Hermite reduction for rational functions, improper integrals through limits, a pole in the
interval declared divergent, iterated integrals with the inner levels first, line and surface
integrals), takes limits (substitution, the limit laws, poles, cancellation, conjugates, combined
logarithms, leading terms, L'Hôpital, series expansion, with a numeric probe that only confirms),
builds Taylor polynomials and closed-form sums, applies integration to arc length, unsigned
areas, volumes of revolution and interval optimisation, does exact linear algebra with every row
operation recorded (determinants by cofactors or elimination, inverses, rank, reduced row
echelon form, linear solves, null and column spaces, Gram–Schmidt, the characteristic polynomial
by Berkowitz, exact eigenvalues and eigenvectors where the characteristic polynomial solves,
numeric ones only under the numeric rule, diagonalization), takes the numeric decompositions
(LU, QR, Cholesky, SVD, least squares, the matrix exponential) through Hipparchus behind the
`Decompose` provider with their values marked numeric, solves ordinary differential equations
symbolically (`dsolve`: separable, linear, exact, Bernoulli, constant coefficients to order 8
with undetermined coefficients and resonance, and systems through generalised eigenvectors and
variation of parameters, every solution substituted back before it is returned, constants `C₁…`
recorded as `C₁ ∈ ℝ`, the prime notation `y'(x)`) and numerically (`odesolve` through Hipparchus
ODE's Dormand–Prince 5(4) behind the `OdeSolve` provider, marked numeric), takes Laplace
transforms and their inverses by table, solves polynomial systems by elimination and linear
recurrences (`rsolve`, with the same check and constants as `dsolve`), does number theory
(primality by Miller–Rabin and Baillie–PSW, `ifactor` by trial division, Brent's rho and ECM
with a work cap, the divisor functions, modular arithmetic with narration, the Chinese remainder
theorem, linear Diophantine equations, combinatorics, the infix `mod`), sets with `∪ ∩ \ ∈`,
boolean expressions with `and or not xor implies` and truth tables, descriptive statistics
exact on exact data, the common distributions (exact where rational, numeric through Hipparchus
Core behind the `Distribute` provider, `erf`, `gamma` and `beta` with their rules), regression
with narrated normal equations, hypothesis tests and confidence intervals with narration,
quantities with units (`3 _m`, `5 _km/_h`) as positive constants with dimensional analysis,
exact conversion factors from the SI brochure and NIST SP 811 and affine temperatures, integer
literals and display in other bases, the bitwise functions, the time value of money in Excel's
conventions with a `tvm` solver, solves trigonometric equations with general solutions in the
angle mode (a fresh integer parameter `k`, recorded as `k ∈ ℤ`), converts degrees, minutes and
seconds, solves triangles by the laws of sines and cosines in every case, gives the shape
formulas, coordinate geometry and the conics from their definitions, and solves, all behind the
provider seam, under cooperative cancellation; a definite integral with no closed form is
evaluated numerically only under `numeric` or a decimal format, by our own double-exponential
rule cross-checked against adaptive Gauss–Kronrod (an iterated one by nested Gauss–Kronrod in
`double` to the digits two runs agree on, at most twelve), and says so; calculus works in
radians in every angle mode; expressions compile to `double` evaluators for plotting; the CLI
runs statements, scripts and the twenty-eight golden corpora, which the gate checks;
`docs/language.md` is the language and `docs/reference/engine.md` and `cli.md` describe the
areas. The desktop app is a typeset worksheet and a canvas over one document model: cells
evaluated on a cancellable worker with a badge on numeric results, results typeset by `render`
(units upright), a matrix editor and a data editor on the keypad, a unit circle view, and beside
them a coordinate plane painted immediate-mode through `Painter` (JavaFX Canvas and SVG) in
three layers with world-coordinate hit testing, an object model with a dependency graph and path
parameters that keep a point on its curve and an intersection's identity through tangency
(circles included), adaptive sampling of Cartesian, parametric, polar and implicit graphs and
inequalities, sliders bound to cell variables, an algebra panel showing every object's equation
and a Statistics heading with the summaries of plotted data, analysis tools (roots, extrema,
intersections, tangents, points on curves, a trace readout), shaded areas with live values,
Riemann sums on a slider, secant-to-tangent and Taylor demonstrations, vectors as arrows with
their sums and projections drawn, the geometry palette (segments, lines, rays, circles,
perpendiculars, parallels, bisectors, tangents, midpoints, polygons, regular polygons, angles)
with snapping and preview, reflections, rotations, translations and dilations, live distance,
angle, area and slope measurements, loci, every degenerate configuration undefined and never an
exception, with Apache Commons Geometry for the predicates, vector fields, slope fields with
solution curves integrated through a draggable point, contour plots, histograms, bar charts, box
plots, scatter plots with fitted curves and residual plots, and a 3D view (surfaces
`z = f(x, y)`, parametric surfaces, space curves, gradient arrows, an orbit camera kept in the
session, a coarse mesh while the camera moves) painted depth-sorted through the same `Painter`,
saved canvases in the session file (version 5, with migrations that rename leading-underscore
names and names that became keywords), and PNG and SVG export of either view; `docs/reference/render.md`, `canvas.md` and
`app.md` describe the areas. CI builds on three platforms and drafts installers from tags.
`PLAN.md` §7 lists the ten phases with their sizes, §9 the process, §11 the decisions, §12 the
resolved questions and §14 the deferred graduate-depth path.

## Stack (pinned)

- **Java 25 LTS**, provisioned by Gradle toolchains: the system JDK does not matter.
- **JavaFX 25.** **Gradle 9.x**, Kotlin DSL, a version catalog, a `module-info.java` in every
  module.
- **JUnit 5, AssertJ, jqwik** (property tests), **TestFX** (UI tests).
- **Hipparchus** for numerics (decompositions, distributions, ODE integrators, special
  functions), never for anything symbolic; Hipparchus Core is in since Phase 5, as the automatic
  module `hipparchus.core`. Hipparchus ODE is in since Phase 7, as `hipparchus.ode` (the same
  transform list). **Apache Commons Geometry** for geometric predicates (in since Phase 6, as its named automatic modules).
- **Jackson Core** (the streaming parser and generator only, no databind) for the app's JSON files
  and the generated help the CLI reads.
- **STIX Two Math** (OFL) bundled; our own typesetting in `render`.
- **Spotless** with google-java-format. **Apache-2.0**; no GPL dependency, ever.

Check before writing an API call. JavaFX 8 and 11 answers, Java 8 through 17 idioms and Swing
answers are the most common mistakes here. Look a signature up in the real artifact: the sources
jars under `~/.gradle/caches/modules-2/files-2.1/`, the toolchain JDK's `lib/src.zip` under
`~/.gradle/jdks/`, or `javap -cp <jar> <class>`.

## Commands

| What | Command |
|---|---|
| The gate (format, build with every test, module boundary, the performance tests on an idle machine, corpus); its quick per-commit profile (compile, unit tests, no properties, performance tests or corpus) | `tools/gates.sh`; `tools/gates.sh --quick` |
| Run all tests | `tools/run_tests.sh` |
| Run one module's tests, optionally filtered | `tools/run_tests.sh engine '*Parser*'` |
| Format | `./gradlew spotlessApply` |
| Build everything | `./gradlew build` |
| Run the app (the owner only: it opens the real user data directory) | `./gradlew :app:run` |
| Run the app on an isolated data directory (a relative one resolves against `app/`) | `./gradlew :app:run --args="--data-dir=<dir>"` |
| UI tests, one run on the machine at a time | `tools/ui_tests.sh` |
| The CLI | `./gradlew :cli:run --args="[options] <file.mc> \| -e '<statements>'"` |
| Golden corpora through the CLI | `tools/run_corpus.sh` |
| Package installers | `./gradlew jpackage` |
| The documentation site, into `build/site/` | `./gradlew :docs:site` |
| Land a delivered series | `tools/land_series.sh <patch-dir> <base-commit> <tree-hash>` |
| The changelog section for a release, from the commit log | `tools/changelog.sh <version> [<from-ref>] [<to-ref>]` |

**Any Gradle run writes files**: `build/` and `.gradle/` in the tree it runs in, and a normal app
run reads and writes the owner's user data directory (projects, settings, history) under the OS
application-support folder. The gate and the UI tests run the app only with `--data-dir` pointed
at scratch.

**Run the quick gate at every commit and the full gate at the head of a series, on a fresh
clone; the landing runs the full gate again.**

## Where things live

| Area | Code | Reference |
|---|---|---|
| Engine: number tower, expressions and canonical form, lexer and parser, printers, rules and trace, assumptions, pattern matcher, simplifier, algebra, differentiation, vector and matrix arithmetic (decompositions are Phase 5), solving, backend providers, compilation to `double` evaluators for plotting | `engine/` | `docs/reference/engine.md` |
| The language: grammar, commands and functions, hold semantics, implicit multiplication, aliases | `docs/language.md`, the parser package in `engine/` | `docs/language.md` is the reference |
| Typesetting: layout tree to themed, interactive nodes | `render/` | `docs/reference/render.md` |
| Canvas: object model, dependency graph, path parameters, `Painter`, hit testing, tools | `canvas/` | `docs/reference/canvas.md` |
| App: document model, command stack, worksheet, modes, persistence, settings | `app/` | `docs/reference/app.md` |
| CLI: REPL and script runner | `cli/` | `docs/reference/cli.md` |
| Tests and tools: gate, test runner, corpus runner, UI harness, landing | `*/src/test/`, `tools/` | `docs/reference/testing-tools.md` (Phase 0) |
| The documentation site: its generator, its link check | `docs/` (the `docs` Gradle project) | `docs/reference/testing-tools.md`, **The site** |

## Architecture rules

These make the engine testable without a window, keep every feature composable, and keep the
door open to the graduate-depth path in `PLAN.md` §14. Treat them as hard rules.

### Module boundaries

- `engine ← render ← canvas ← app`, and `cli → engine`. Nothing in `engine` or `cli` references
  a `javafx.*` type; the gate fails if it does.
- Every module has a `module-info.java`. No split packages. Non-modular dependencies are
  automatic modules.

### `engine/` is pure, exact, and rule-driven

- **Immutable, hash-consed expression trees** with a total canonical ordering: sums and products
  are flattened n-ary with sorted operands and collected coefficients, so structural equality is
  mathematical equality for a large class of expressions.
- **Exact by default.** `Integer` (BigInteger), `Rational`, `Real` (BigDecimal under a
  MathContext), `Complex`, with promotion rules. `double` appears only in the compiled plotting
  evaluators and in explicitly numeric requests. `BigDecimal` is compared with `compareTo`, never
  `equals`.
- **Every transformation is a named `Rule`** written as *pattern → replacement, if condition*
  against the general matcher (sequence and optional patterns, commutative matching, side
  conditions). No hand-coded tree walks that rewrite. Rules query the **assumptions** context
  before firing. Every operation can produce a `Trace` of `Rewrite` records; narration is
  curated per operation and lives beside its rules.
- **Heavyweight operations go through the provider interface** (factor, gcd, solve, integrate,
  limit, decompositions). Our implementations are the default and only providers; any provider's
  result is normalized to canonical form before it reaches a caller.
- **Soundness is proven numerically.** Every rule set ships with property tests that evaluate
  `simplify(e)` and `e` at random points; derivatives are checked against difference quotients;
  solutions substitute back. Golden corpora are `.mc` files run through the CLI.
- **The simplifier terminates**: fixpoint iteration under a cap, and a rule that can loop with
  another is a bug.
- **Unevaluated forms are first class.** A command that cannot complete returns its own node
  (`Integral`, `Limit`, `Sum`); nothing returns a string or fails silently.
- **No clock, no randomness, no I/O** in `engine`.

### `render/`, `canvas/`, `app/` are presentation

- **One document model.** Worksheet cells, canvas objects, variables and settings live in one
  `Document`; the worksheet and the canvas are two views of it, never two models kept in sync.
- **Every mutation goes through the command stack** (undo and redo). No widget mutates the
  document directly.
- **Engine work never runs on the FX thread.** Evaluation, simplification and sampling run on
  the worker pool with cancellation; results come back through `Platform.runLater`. Canvas
  drawing happens only on the FX thread.
- **The canvas has one object model.** Plotted functions, constructions, sliders, fields and
  regions are nodes in one dependency DAG, recomputed in topological order; intersections and
  points on paths carry a path parameter so identity survives a drag through tangency.
- **Painting is immediate-mode through `Painter`** (Canvas and SVG backends), three stacked
  layers, our own world-coordinate hit testing. No scene-graph nodes for math objects.
- **Plotting never touches exact arithmetic.** Expressions are compiled once to `double`
  evaluators and cached.
- **Strings are externalized** in resource bundles. **Persistence is versioned JSON** with a
  migration per version; a file saved by any release opens in every later one.
- **No network.** The only network call the app may make is the optional, off-by-default update
  check.

## Folder layout

```
engine/          pure Java: numbers, expressions, parse, print, rules, assumptions, match,
                 simplify, algebra, calculus, linalg, solve, providers, compile; src/test with corpora
render/          JavaFX typesetting over the engine's layout tree; the bundled STIX Two Math font
canvas/          coordinate plane, object model, dependency graph, Painter (Canvas, SVG), tools
app/             document model, command stack, worksheet, modes, settings, persistence,
                 entry point; resource bundles
cli/             REPL and .mc script runner
docs/            language.md; reference/ (one file per area, written as it lands); images/; the
                 site generator (the `docs` Gradle project, src/)
legal/           the full licence text of every component that ships (`NOTICE` names them)
tools/           gates.sh, run_tests.sh, run_corpus.sh, land_series.sh, changelog.sh, ui_tests.sh,
                 make_icons.sh
.claude/agents/  scout, researcher, developer, reviewer
.github/         workflows (Phase 0)
PLAN.md          the plan: gitignored, read from the main tree
```

## Java and JavaFX gotchas for this project

- `BigDecimal.equals` compares scale: `new BigDecimal("1.0").equals(new BigDecimal("1"))` is
  false. Use `compareTo`, and strip or normalize scale in canonical form.
- Hash-consing needs structural `equals` and `hashCode` on every node; a node with a mutable
  field breaks the whole engine.
- `Math.pow`, `Math.sqrt` and friends are `double`; they never appear in exact paths.
- JavaFX: touching a node off the FX thread throws at best and corrupts at worst. `Canvas`
  drawing commands are queued per pulse; a thousand small `strokeLine` calls beat one giant
  `Path`.
- JPMS: a `requires transitive` leak from `render` into `engine` consumers is a boundary bug;
  reflection into another module needs `opens`, which is a smell here.
- Gradle: use `./gradlew`, never a system Gradle. The toolchain download on a fresh clone is
  slow once; that is the first gate run in a lane.
- A jar with neither `module-info.class` nor `Automatic-Module-Name` goes on Gradle's classpath,
  where a named module cannot read it, whatever the JDK would call it on the module path. The
  conventions plugin's `AutomaticModuleName` transform adds the attribute to the jars it lists
  (Hipparchus Core → `hipparchus.core`); a new such dependency joins that list, and jlink merges
  it (`docs/reference/testing-tools.md`, **Jars without a module name**).
- A test that touches file-system paths builds them with `Path.of(...)` from segments or from the
  JUnit temp directory and compares `Path` objects, never POSIX string literals: `/tmp/a` is
  `D:\tmp\a` on Windows and `/x` is not absolute there, and only the Windows CI job catches it.
- Pattern-matching `switch` over sealed node types must be exhaustive; a `default` branch over
  the expression hierarchy hides a missing case.
- Records are the default for value types; a class is used only for identity or mutability,
  which in `engine` means never.

## Seeing the app

The coordinator runs the app on the main tree (`./gradlew :app:run`) and reads the screenshots
the UI tests produce. Agents see the app only through the UI harness in their own clones, on an
isolated data directory. Headless runs (the engine's tests, the CLI, the corpus) render nothing
and catch most errors.

## Agent workflow

Work on this repository is split across a coordinator and four project subagents
(`.claude/agents/`). The split is by judgment required, and by how much context each seat can
afford to hold.

- **Coordinator** (the top-level session) — talks to the owner, makes design decisions within
  `PLAN.md` or escalates them, writes briefs (each naming its **tier**, below), approves plans
  where a plan is required, routes work and review findings, reads the real diff of every tier A
  change and of every fold, and lands series. It does not read large files itself (the scout
  does), and does not write feature code in the main tree: trivial edits only, when spawning an
  agent would cost more than the edit.
- **`developer`** (Opus) — works in a clone of its own (**Lanes**), implements the brief, runs
  the quick gate at every commit and the full gate at the series head, verifies behaviour
  through the CLI, the corpus and the UI harness in its clone, updates the docs the change invalidates, and delivers a patch series on
  the current `main` with the tree it reproduces. Never writes to the main working tree. Plans
  first only when the brief asks (tier A) or when it meets an open decision. **Continue the same
  agent from plan to implementation** (`SendMessage`); respawning loses what it learned planning.
- **`reviewer`** (Opus) — independent review of the series, applied in a clone of its own: the
  full pass for tier A, the targeted pass for tier B (**Tiers**). Report-only; it is given the
  code and the brief, never the developer's conclusions.
- **`scout`** / **`researcher`** (Sonnet) — read-only codebase lookups (including API shapes
  from the sources jars and the toolchain JDK) and outside research (Java 25, JavaFX 25,
  Hipparchus, Gradle 9 docs and issues). Neither runs Gradle. A load-bearing research fact (an
  API shape, a JavaFX behaviour, a licence term) is verified by Opus or the coordinator before a
  decision rests on it.

**Tiers.** Every brief names its tier, which sets the plan and the review:

- **A** — engine semantics: the number tower, canonical form and ordering, the matcher, the
  assumptions system, the rule engine and trace, any simplifier rule set's semantics, the
  grammar in `docs/language.md`, the provider interface; the persistence schema and migrations;
  the canvas dependency graph and path-parameter continuity; the `Painter` interface; module
  boundaries; and any change to a golden corpus's expected output or a property test's
  tolerance. A plan before code, approved by the coordinator, who escalates design decisions to
  the owner; then the implementation; then the full review: soundness probed at random points,
  termination checked, old project files loaded, every gate, the UI harness driven.
- **B** — features inside existing frameworks: new rules within an existing rule set with their
  tests, narration text, new canvas tools, worksheet and mode UI, typesetting constructs,
  tooling, CI. No plan round trip: the brief carries the decisions and the developer stops only
  at an open decision. The targeted review, about thirty minutes: the diff read, the delivered tree
  checked against the developer's full-gate run, the changed behaviour driven once.
- **C** — docs, comments, config. No reviewer: the gate and the coordinator's own read.

Small later-scope items are **batched** by area, up to about five a slice, one commit each, one
brief and one review.

**Briefs.** A brief is the whole of what an agent knows about the task, so it carries:

- **Title and tier.**
- **Goal** — what changes for the user or the tooling, in two or three sentences, and what is
  explicitly out of scope.
- **Decisions made** — every choice the coordinator has already settled (names, syntax, which
  module, which rule set, which file), so the developer never has to guess; for tier A, the plan
  is where remaining choices surface. Decisions cite `PLAN.md` §11 by row where one applies.
- **Read** — `CLAUDE.md`, the `PLAN.md` sections for the phase, the reference files of the
  areas touched, and any module or doc the task depends on; the scout's findings when there are
  any, as `path:line`.
- **Verification** — the tests expected (unit, property, corpus), and what the UI run must show
  when the change is visible.
- **Lane** — the clone to use, whether the lane holds the display (one UI-test run at a time),
  and the base commit the series must be built on.
- **Delivery** — the plan format (tier A) or the report format, as the agent definition states.

**Lanes.** Every Gradle run writes files in the tree it runs in (`build/`, `.gradle/`), and a
normal app run opens the owner's data directory, so the main working tree is the coordinator's
and stays at `HEAD`: no agent writes to it or its index. A developer or a reviewer works in a
`git clone` of its own under its scratch directory; the first `tools/gates.sh` run provisions the
toolchain and warms the Gradle cache. Two developers may run at once on disjoint areas, under
three rules: only one **UI-test** run happens on the machine at a time, which `tools/ui_tests.sh`
enforces with a machine-wide lock, so a lane just runs it and waits (the gate, the tests, the
CLI and the corpus run any time); the second lane rebases its series onto `main` before
delivery; a lane's docs changes stay within its area's reference file, the map only where a
status line changes. A lane runs the app only through the UI harness or with `--data-dir`
pointed at scratch; **nobody runs the app normally in a clone**, because that opens the owner's
data directory. Lanes, gate clones, deliveries and notes live under a persistent directory
beside the main tree (`~/Development/math-canvas-lanes/`), never under the session scratchpad
or `/tmp`: a reboot wiped `/private/tmp` on 2026-10-28 and took every clone and a reviewed
series with it. No lane runs `./gradlew --stop`: it stops every Gradle daemon of the user, so a
gate running in another lane dies mid-build; a lane that must free memory lets its daemons idle
out.

**Review routing.** The reviewer's findings go back to the **same** developer (`SendMessage`),
which folds them into the series; the coordinator reads the fold's diff; then the reviewer
re-reviews. A FAIL loops until it passes; PASS WITH FIXES is folded and read, re-reviewed when
the fold touched engine semantics or the verdict was FAIL; otherwise the coordinator reads the
fold and lands. Anything the developer did outside the brief (**Adjacent changes**)
is kept or vetoed by the coordinator, not the reviewer.

**Delivery and landing.** A series is `git format-patch` files on the current `main`, one logical
change per commit (refactors separate from features), each a **Conventional Commit**:
`type(scope): Summary`, with `type` one of `feat`, `fix`, `refactor`, `perf`, `test`, `docs`,
`build`, `ci`, `chore`, `style`, `revert`; `scope` a module name or `tools`, `docs`, `ci`,
`build`; the summary an imperative sentence in sentence case, no trailing period, at most 72
characters; a body of plain paragraphs saying what and why; **no trailers**: no commit in this
repository carries one, the coordinator's own included. The series comes with the write-tree
hash it reproduces and the full gate's tail at its head, the quick gate having run at every
commit; small series land together in one batch under one gate. Once the review's findings are
folded and the fold's diff read, the coordinator lands it with
`tools/land_series.sh <patch-dir> <base-commit> <tree-hash>` (clean tree and HEAD at the base
required; `git am`; the messages checked; the tree checked, with a rollback on mismatch; the gate
run; the commits left in place). The coordinator pushes `main` to `origin` after each landing.
The owner reviews at phase gates and whenever they choose.

Constraints:

- **Decisions come from `PLAN.md`** (§11 decisions, §12 resolved questions, §5 architecture).
  An agent that finds an open decision stops and reports; it does not guess.
- **The architecture rules above are not negotiable**, and neither are: exact arithmetic
  outside plotting, rules through the matcher, provenance of every rewrite in the trace,
  canonical normalization of every provider result, the FX-thread rule, the command stack, the
  module boundary, Spotless formatting, Conventional Commits, no trailers, no GPL dependency.
- **Temporary instrumentation is tagged `[verify]`** and gone before a report:
  `grep -rn "\[verify\]" engine render canvas app cli tools` returns nothing.

## Release notes

- jpackage builds `.dmg`, `.msi` and `.deb` from jlink runtime images; the installers are
  unsigned until the 1.0 signing decision, and the README documents the Gatekeeper and
  SmartScreen workaround.
- `CHANGELOG.md` is generated from the Conventional Commit log per release; tags are `vX.Y.Z`.
- The bundled STIX Two Math font ships with its OFL text; `NOTICE` lists every third-party
  component and licence, and `legal/` holds each licence's full text.
