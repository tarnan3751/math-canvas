# Performance

The numbers Phase 11 is judged by (`PLAN.md` §7, Phase 11): how fast the app starts and answers,
how much work the simplifier does over the corpus, how long a canvas frame takes, how long the
engine's performance tests run, and how big the installer and the source tree are. One command
measures all of them, `tools/measure.sh` (`testing-tools.md`, **Measuring**), and `--record`
appends its table under **Recorded runs** with a heading of the commit and the date, so each
section there is one measurement, the oldest first.

## How to read this

- **Medians and ranges.** Every timing is the median of five runs, with the smallest and largest
  of the five beside it. A change is a change when its median moves by more than the range of
  either measurement; inside the range it is noise.
- **The load.** Each section names the machine (CPU, cores, memory, OS, JDK) and the load average
  at the start and at the end. The script refuses to start above a one-minute load of 4 unless
  forced; a measurement taken under load times the other work as well, so it is compared only with
  one taken under a similar load, and the budgets are set from an idle one.
- **Startup.** `StartupBenchmarkTest` opens the app on a data directory of its own, on an empty
  worksheet, and reads three moments at the first pulse after each: the window shown, the first
  cell ready to type, and the first result of `1 + 1`. *Cold* is the first launch in a JVM: since
  the process start, which includes the test worker's own start, JUnit and the toolkit's start, and
  since the call that opens the window. *Warm* is the median of five more launches in the same JVM,
  from that call. The window is shown and the cell ready at the same pulse, so the two read alike.
- **Cell latency.** `CellLatencyBenchmarkTest` times `EvaluationJob` on the test thread, printing
  and typesetting included and no window, on twenty typical inputs; the last is a slider drag, the
  three cells that read the slider's variable run again with a new value. *First* is the input's
  first run in a JVM that has just loaded the engine; the median and range are of five later runs.
- **The worksheet.** `WorksheetBenchmarkTest` opens a worksheet of one hundred cells with the
  canvas shown and times five things a person does, each from the action on the FX thread to the
  first pulse that shows its whole effect: a cell appended through the input bar, Enter in the
  fiftieth cell, the slider that ten cells and five graphs read set to a new value, a cell's steps
  opened, and the worksheet scrolled end to end in twenty steps; beside each, the FX thread's CPU
  time over the same span, which other load disturbs less than the latency. One unmeasured run, then
  the median and range of five. `tools/measure.sh` does not run it yet: it is run with
  `tools/ui_tests.sh --tests '*WorksheetBenchmarkTest' -Pbenchmarks`.
- **Corpus.** The wall time of `tools/run_corpus.sh`, a Gradle up-to-date check and the CLI's start
  included, and the statements it checks per second; the rule tries and rewrites are those of the
  CLI's `--stats` over the same files, which are exact and change only with the engine. A rule
  tried many times and rewriting rarely is where matching time goes.
- **Canvas.** The figures `CanvasBenchmarkTest` (ten 2,000-point curves under a slider drag) and
  `SpaceBenchmarkTest` (a 200 × 200 surface orbiting) print, median and 95th percentile over their
  frames (`canvas.md`, **The frame budget**). The whole frame is the one a user feels.
- **Engine perf suite.** The wall time of each `*PerformanceTest` class of `:engine:perfTest`, from
  its JUnit report. Their own budgets, asserted in the tests, stay as they are.
- **Sizes.** The installer for the platform, the jlink image, each module's jar, source lines per
  module (main sources, tests and their data, main resources; code lines by `cloc` when it is
  installed, else non-blank lines), the ten largest source files, and the key count of every
  `.properties` file the modules ship.

## Budgets

To be set from this baseline: each figure gets a budget, measured on an idle machine, that Phase
11's changes must meet or improve.

## Recorded runs

### 2026-11-05 at `c403877459`

Apple M5, 10 cores, 24 GB, macOS 26.5.1, openjdk version "25.0.4.1" 2026-08-18 LTS. Load average 2.39 3.00 4.32 at the start, 3.13 3.32 4.14 at the end. Timings are the median of 5 runs and their range.

#### Startup

| Moment | Median | Range |
|---|---|---|
| cold, since the process start: window shown | 889.1 ms | 858.2 ms to 925.9 ms |
| cold, since the process start: first cell ready | 889.1 ms | 858.2 ms to 925.9 ms |
| cold, since the process start: first result of 1 + 1 | 1,046.2 ms | 1,006.6 ms to 1,116.1 ms |
| cold, since the window is opened: window shown | 429.1 ms | 419.2 ms to 447.6 ms |
| cold, since the window is opened: first cell ready | 429.1 ms | 419.2 ms to 447.6 ms |
| cold, since the window is opened: first result of 1 + 1 | 587.1 ms | 567.6 ms to 640.8 ms |
| warm, median of 5, since opened: window shown | 65.7 ms | 60.5 ms to 69.0 ms |
| warm, median of 5, since opened: first cell ready | 65.7 ms | 60.5 ms to 69.0 ms |
| warm, median of 5, since opened: first result of 1 + 1 | 95.6 ms | 88.8 ms to 99.8 ms |

#### Cell latency

| Input | First | Median | Range |
|---|---|---|---|
| arithmetic | 80.1 ms | 0.14 ms | 0.10 ms to 0.21 ms |
| simplify trig | 7.22 ms | 0.20 ms | 0.18 ms to 0.29 ms |
| simplify rational | 5.15 ms | 0.35 ms | 0.28 ms to 0.42 ms |
| factor | 12.0 ms | 0.69 ms | 0.61 ms to 1.47 ms |
| expand | 3.45 ms | 0.73 ms | 0.50 ms to 0.95 ms |
| diff | 20.8 ms | 0.93 ms | 0.62 ms to 1.64 ms |
| integrate by parts | 5.15 ms | 1.17 ms | 0.84 ms to 1.36 ms |
| integrate twice by parts | 8.20 ms | 2.32 ms | 1.88 ms to 3.07 ms |
| definite integral | 3.39 ms | 1.00 ms | 0.91 ms to 1.34 ms |
| solve cubic | 3.61 ms | 0.56 ms | 0.50 ms to 0.71 ms |
| det 4x4 | 1.83 ms | 0.27 ms | 0.19 ms to 0.34 ms |
| inverse 4x4 | 3.66 ms | 0.52 ms | 0.44 ms to 1.10 ms |
| dsolve | 11.4 ms | 3.13 ms | 2.76 ms to 4.64 ms |
| limit | 8.99 ms | 1.96 ms | 1.62 ms to 3.36 ms |
| taylor | 10.2 ms | 5.52 ms | 3.86 ms to 7.87 ms |
| steps diff | 8.64 ms | 1.07 ms | 0.94 ms to 1.51 ms |
| steps integrate | 4.24 ms | 1.35 ms | 1.23 ms to 1.87 ms |
| steps solve | 1.33 ms | 0.64 ms | 0.60 ms to 0.89 ms |
| plot | 0.97 ms | 0.23 ms | 0.22 ms to 0.29 ms |
| slider drag, 3 cells | 2.63 ms | 1.29 ms | 1.09 ms to 1.57 ms |

#### Corpus

| Figure | Median | Range |
|---|---|---|
| `tools/run_corpus.sh` wall time (28 files, 1870 statements) | 11.87 s | 11.63 s to 11.92 s |
| statements per second | 157 | 157 to 161 |
| rule tries (one `--stats` run) | 1,540,636 | exact |
| rewrites (one `--stats` run) | 21,675 | exact |

| Rule tried most | Tries | Rewrites |
|---|---|---|
| `diff higher-order-constant` | 450,377 | 1 |
| `diff higher-order` | 450,376 | 54 |
| `standard combine-trig-factors` | 63,082 | 21 |
| `replace replace` | 37,781 | 2,712 |
| `diff merge-orders` | 15,779 | 1 |
| `diff constant` | 15,778 | 1,777 |
| `diff undefined` | 15,778 | 0 |
| `diff fundamental-theorem` | 14,001 | 3 |
| `diff matrix` | 13,998 | 0 |
| `diff piecewise` | 13,998 | 0 |

#### Canvas

| Frame figure | Median | Range |
|---|---|---|
| canvas: recompute (sample + hit index, test thread), median | 2.20 ms | 2.18 ms to 2.21 ms |
| canvas: recompute (sample + hit index, test thread), p95 | 2.75 ms | 2.69 ms to 2.77 ms |
| canvas: paint (record dirty layers, FX thread), median | 0.29 ms | 0.29 ms to 0.30 ms |
| canvas: paint (record dirty layers, FX thread), p95 | 0.34 ms | 0.34 ms to 0.35 ms |
| canvas: whole frame (recompute to painted), median | 8.36 ms | 8.34 ms to 8.38 ms |
| canvas: whole frame (recompute to painted), p95 | 16.69 ms | 16.67 ms to 16.71 ms |
| space: orbiting, 50 x 50: paint (FX thread), median | 0.96 ms | 0.92 ms to 0.98 ms |
| space: orbiting, 50 x 50: paint (FX thread), p95 | 1.24 ms | 1.20 ms to 1.25 ms |
| space: orbiting, 50 x 50: whole frame, median | 24.81 ms | 24.21 ms to 24.85 ms |
| space: orbiting, 50 x 50: whole frame, p95 | 25.44 ms | 25.11 ms to 25.66 ms |
| space: at rest, 200 x 200: paint (FX thread), median | 7.05 ms | 6.91 ms to 7.14 ms |
| space: at rest, 200 x 200: paint (FX thread), p95 | 7.92 ms | 7.69 ms to 7.96 ms |
| space: at rest, 200 x 200: whole frame, median | 59.11 ms | 58.37 ms to 59.16 ms |
| space: at rest, 200 x 200: whole frame, p95 | 67.22 ms | 66.70 ms to 67.41 ms |

#### Engine perf suite (`:engine:perfTest`)

| Class | Median | Range |
|---|---|---|
| AlgebraPerformanceTest | 1.39 s | 1.36 s to 1.44 s |
| CalculusPerformanceTest | 0.25 s | 0.24 s to 0.30 s |
| IntegrationPerformanceTest | 2.10 s | 2.00 s to 2.19 s |
| LimitPerformanceTest | 0.06 s | 0.06 s to 0.07 s |
| CompiledPerformanceTest | 0.05 s | 0.04 s to 0.07 s |
| NumericOdePerformanceTest | 0.31 s | 0.30 s to 0.32 s |
| CanonicalPerformanceTest | 0.02 s | 0.02 s to 0.02 s |
| LinalgPerformanceTest | 0.16 s | 0.15 s to 0.19 s |
| NumericLinalgPerformanceTest | 0.08 s | 0.08 s to 0.08 s |
| MatcherPerformanceTest | 0.00 s | 0.00 s to 0.00 s |
| NumberTheoryPerformanceTest | 2.44 s | 2.42 s to 2.51 s |
| SimplifierPerformanceTest | 0.40 s | 0.38 s to 0.42 s |
| SolvePerformanceTest | 1.13 s | 1.09 s to 1.14 s |
| **all classes** | 8.48 s | 8.27 s to 8.61 s |

#### Sizes

| Artefact | Size |
|---|---|
| installer math-canvas-1.0.0.dmg | 45.7 MB |
| jlink image app/build/image | 63.5 MB |
| jar engine-0.1.0-SNAPSHOT.jar | 1.5 MB |
| jar engine-1.0.0.jar | 1.5 MB |
| jar render-0.1.0-SNAPSHOT.jar | 856 KB |
| jar render-1.0.0.jar | 856 KB |
| jar canvas-0.1.0-SNAPSHOT.jar | 550 KB |
| jar canvas-1.0.0.jar | 550 KB |
| jar app-0.1.0-SNAPSHOT.jar | 509 KB |
| jar app-1.0.0.jar | 1,018 KB |
| jar cli-0.1.0-SNAPSHOT.jar | 44 KB |
| jar cli-1.0.0.jar | 46 KB |

Source lines (non-blank lines):

| Module | Main | Test | Resources |
|---|---|---|---|
| engine | 67,985 | 43,559 | 695 |
| render | 2,748 | 948 | 93 |
| canvas | 20,690 | 12,494 | 36 |
| app | 18,788 | 18,641 | 1,816 |
| cli | 1,077 | 527 | 9 |
| docs | 552 | 324 | 404 |
| build-logic | 691 | 0 | 0 |
| tools | 1,099 | 0 | 0 |
| **all** | 113,630 | 76,493 | 3,053 |

| Largest source file | Non-blank lines |
|---|---|
| `engine/src/main/java/mathcanvas/engine/eval/Evaluator.java` | 4,245 |
| `app/src/uiTest/java/mathcanvas/app/CanvasModeTest.java` | 2,252 |
| `engine/src/main/java/mathcanvas/engine/parse/Parser.java` | 2,194 |
| `engine/src/main/java/mathcanvas/engine/calculus/LimitMethods.java` | 1,736 |
| `app/src/main/java/mathcanvas/app/eval/Document.java` | 1,412 |
| `engine/src/main/java/mathcanvas/engine/print/InfixPrinter.java` | 1,286 |
| `app/src/main/java/mathcanvas/app/persist/CanvasCodec.java` | 1,170 |
| `app/src/main/java/mathcanvas/app/ui/WorksheetWindow.java` | 1,145 |
| `app/src/main/java/mathcanvas/app/ui/CanvasPane.java` | 1,113 |
| `render/src/main/java/mathcanvas/render/typeset/Typesetter.java` | 1,085 |

| Bundle | Keys |
|---|---|
| `engine/src/main/resources/mathcanvas/engine/rule/narration.properties` | 626 |
| `engine/src/main/resources/mathcanvas/engine/version.properties` | 1 |
| `render/src/main/resources/mathcanvas/render/typeset/messages.properties` | 1 |
| `app/src/main/resources/mathcanvas/app/app.properties` | 2 |
| `app/src/main/resources/mathcanvas/app/icons.properties` | 15 |
| `app/src/main/resources/mathcanvas/app/messages.properties` | 479 |
| `app/src/main/resources/mathcanvas/app/messages_fr.properties` | 53 |
| `cli/src/main/resources/mathcanvas/cli/messages.properties` | 6 |
