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
  `StartupTypedLaterBenchmarkTest` opens the app the same way but types `1 + 1` a second after the
  cell is ready, as a person does, and times the first result from the typing, cold and warm: the
  engine loads while the window waits (`app.md`, the worker's warm-up), so this is the first
  answer a person waits for.
- **Cell latency.** `CellLatencyBenchmarkTest` times `EvaluationJob` on the test thread, printing
  and typesetting included and no window, on twenty typical inputs; the last is a slider drag, the
  three cells that read the slider's variable run again with a new value. *First* is the input's
  first run in a JVM that has just loaded the engine; the median and range are of five later runs.
- **The worksheet.** `WorksheetBenchmarkTest` opens a worksheet of one hundred cells with the
  canvas shown and times six things a person does, each from the action on the FX thread to the
  first pulse that shows its whole effect: a cell appended through the input bar, Enter in the
  fiftieth cell, every cell run again (the angle mode switched), the slider that ten cells and five
  graphs read set to a new value, a cell's steps opened, and the worksheet scrolled end to end in
  twenty steps; beside each, the FX thread's CPU time over the same span, which other load
  disturbs less than the latency. One unmeasured run, then the median and range of five.
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

Set from the run at `919353aea3` under **Recorded runs**, each with room for the noise of a
machine that is doing a little else. Before a release, run `tools/measure.sh` on an idle machine
and compare: a figure over its budget is a regression to fix, or to explain here with a new
budget, before the tag. The worksheet's budgets are on the FX thread's CPU time, which varies by
about 15 % between runs; its latencies vary too much with the load to carry one. The engine's own
`*PerformanceTest` budgets are asserted by the gate and stay as they are.

| Figure | Recorded | Budget |
|---|---|---|
| Cold start, since the process start: first cell ready | 929 ms | 1,150 ms |
| Cold start, since the window is opened: first cell ready | 450 ms | 560 ms |
| Cold start, since the window is opened: first result of `1 + 1` | 561 ms | 700 ms |
| Warm start, since the window is opened: first cell ready | 69 ms | 90 ms |
| Cold start, `1 + 1` typed a second after ready: its result, since the typing | 66 ms | 100 ms |
| A cell's latency, the median of each of the twenty inputs | 0.13 ms to 5.73 ms | 10 ms |
| Worksheet of 100 cells, FX thread CPU: append a cell | 9.3 ms | 13 ms |
| Worksheet of 100 cells, FX thread CPU: re-run the 50th cell | 43.0 ms | 56 ms |
| Worksheet of 100 cells, FX thread CPU: re-run every cell | 41.7 ms | 55 ms |
| Worksheet of 100 cells, FX thread CPU: a slider that 10 cells and 5 graphs read | 10.6 ms | 14 ms |
| Corpus: wall time of `tools/run_corpus.sh` | 8.20 s | 10.5 s |
| Corpus: rule tries (exact, so any rise has a cause in the engine) | 1,540,636 | 1,620,000 |
| Canvas: whole frame, median | 8.34 ms | 10 ms |
| Canvas: whole frame, 95th percentile | 16.8 ms | 20 ms |
| 3D view, orbiting: whole frame, median | 24.7 ms | 30 ms |
| 3D view, at rest: whole frame, median | 58.9 ms | 70 ms |
| Engine perf suite, all classes | 8.43 s | 11 s |
| Installer (`.dmg`) | 45.7 MB | 48 MB |
| jlink image | 63.5 MB | 67 MB |

### What Phase 11 changed

Between the baseline at `c403877459` and the run at `919353aea3`, both on the same machine under
a similar load:

- **Faster.** The corpus runs in 8.20 s where it took 11.87 s (228 statements a second where it
  was 157): the series for π, ln 2 and ln 10 are kept per precision and no longer summed again
  on every use. Adding a cell to a worksheet of 100 takes about 11 ms where alternating runs on the
  old code measured about 43 ms: the cell views stay in the scene, so JavaFX no longer restyles
  every one of them. A first result typed a second after the window opens arrives in about 66 ms
  where alternating runs without the warm-up measured about 159 ms. Results that the worker
  delivers together are shown by one refresh, about a seventh less FX thread time when every
  cell runs again.
- **Unchanged within the noise.** Cold and warm startup to the first cell, a cell's latency, the
  canvas's frame, the 3D view's frame and the engine's perf suite. The 3D orbit is paced by the
  render thread's rasteriser (`canvas.md`, **The frame budget**), and the cold start is mostly
  class loading and CSS in JavaFX itself.
- **Not smaller.** The installer is 45.7 MB as before. The Java runtime and JavaFX are most of
  the 63.5 MB image (the JVM's own library is 19 MB of it, and the project's four jars are about
  4 MB), JavaFX's own modules require `java.desktop`, and the image is already stripped and
  compressed, so little of ours could shrink it. The main sources are 113,906 non-blank lines where they were 113,630: the cleanup merged
  duplicated helpers, removed dead code and split the largest classes (`Evaluator.java` 4,245 →
  3,063 lines, `Parser.java` 2,194 → 1,894), and the measuring and the speed work added about as
  much as that removed. The tests grew by about 880 lines (the fixtures' pinned bytes, the
  benchmarks, the tests of the new code).

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

### 2026-11-05 at `919353aea3`

Apple M5, 10 cores, 24 GB, macOS 26.5.1, openjdk version "25.0.4.1" 2026-08-18 LTS. Load average 2.52 3.21 3.30 at the start, 3.16 3.35 3.34 at the end. Timings are the median of 5 runs and their range.

#### Startup

| Moment | Median | Range |
|---|---|---|
| cold, since the process start: window shown | 929.2 ms | 902.2 ms to 994.4 ms |
| cold, since the process start: first cell ready | 929.2 ms | 902.2 ms to 994.4 ms |
| cold, since the process start: first result of 1 + 1 | 1,061.2 ms | 1,013.4 ms to 1,103.1 ms |
| cold, since the window is opened: window shown | 450.4 ms | 440.2 ms to 466.7 ms |
| cold, since the window is opened: first cell ready | 450.4 ms | 440.2 ms to 466.7 ms |
| cold, since the window is opened: first result of 1 + 1 | 561.4 ms | 553.4 ms to 589.2 ms |
| warm, median of 5, since opened: window shown | 68.6 ms | 63.7 ms to 69.5 ms |
| warm, median of 5, since opened: first cell ready | 68.6 ms | 63.7 ms to 69.5 ms |
| warm, median of 5, since opened: first result of 1 + 1 | 93.0 ms | 92.0 ms to 95.1 ms |
| cold, typed a second after ready: first result of 1 + 1, since the typing | 65.8 ms | 57.6 ms to 72.0 ms |
| warm, median of 5, typed a second after ready: first result of 1 + 1, since the typing | 24.0 ms | 21.1 ms to 25.9 ms |

#### Cell latency

| Input | First | Median | Range |
|---|---|---|---|
| arithmetic | 82.3 ms | 0.13 ms | 0.11 ms to 0.37 ms |
| simplify trig | 7.40 ms | 0.22 ms | 0.17 ms to 0.47 ms |
| simplify rational | 5.24 ms | 0.37 ms | 0.32 ms to 0.52 ms |
| factor | 12.3 ms | 0.95 ms | 0.62 ms to 1.56 ms |
| expand | 3.66 ms | 0.76 ms | 0.49 ms to 1.04 ms |
| diff | 20.9 ms | 0.89 ms | 0.62 ms to 1.29 ms |
| integrate by parts | 4.94 ms | 1.18 ms | 0.86 ms to 1.51 ms |
| integrate twice by parts | 6.84 ms | 2.58 ms | 1.87 ms to 3.40 ms |
| definite integral | 3.85 ms | 1.17 ms | 0.91 ms to 1.29 ms |
| solve cubic | 3.71 ms | 0.63 ms | 0.51 ms to 1.26 ms |
| det 4x4 | 1.62 ms | 0.25 ms | 0.19 ms to 0.69 ms |
| inverse 4x4 | 1.72 ms | 0.56 ms | 0.44 ms to 1.85 ms |
| dsolve | 9.84 ms | 3.69 ms | 2.20 ms to 5.93 ms |
| limit | 7.44 ms | 2.06 ms | 1.49 ms to 2.95 ms |
| taylor | 10.6 ms | 5.73 ms | 2.86 ms to 6.93 ms |
| steps diff | 8.09 ms | 1.12 ms | 0.94 ms to 1.67 ms |
| steps integrate | 3.84 ms | 1.72 ms | 1.15 ms to 2.59 ms |
| steps solve | 1.41 ms | 0.72 ms | 0.56 ms to 1.15 ms |
| plot | 1.06 ms | 0.28 ms | 0.17 ms to 0.37 ms |
| slider drag, 3 cells | 3.91 ms | 1.37 ms | 0.95 ms to 1.96 ms |

#### The worksheet

| Action, 100 cells | Latency | Range | FX thread CPU | Range |
|---|---|---|---|---|
| append a cell | 11.2 ms | 9.29 ms to 20.2 ms | 9.29 ms | 7.53 ms to 12.6 ms |
| re-run the 50th cell | 59.0 ms | 51.3 ms to 87.2 ms | 43.0 ms | 32.3 ms to 53.4 ms |
| re-run every cell | 57.0 ms | 52.6 ms to 64.3 ms | 41.7 ms | 34.7 ms to 49.5 ms |
| slider, 10 cells and 5 graphs | 23.8 ms | 23.0 ms to 48.3 ms | 10.6 ms | 10.0 ms to 15.1 ms |
| open a cell's steps | 1.81 ms | 1.08 ms to 7.33 ms | 0.87 ms | 0.77 ms to 1.52 ms |
| scroll end to end, 20 steps | 99.1 ms | 85.4 ms to 102.2 ms | 2.52 ms | 1.20 ms to 2.58 ms |

#### Corpus

| Figure | Median | Range |
|---|---|---|
| `tools/run_corpus.sh` wall time (28 files, 1870 statements) | 8.20 s | 8.04 s to 8.23 s |
| statements per second | 228 | 227 to 232 |
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
| canvas: recompute (sample + hit index, test thread), median | 2.21 ms | 2.20 ms to 2.22 ms |
| canvas: recompute (sample + hit index, test thread), p95 | 2.75 ms | 2.70 ms to 2.88 ms |
| canvas: paint (record dirty layers, FX thread), median | 0.29 ms | 0.29 ms to 0.29 ms |
| canvas: paint (record dirty layers, FX thread), p95 | 0.34 ms | 0.33 ms to 0.35 ms |
| canvas: whole frame (recompute to painted), median | 8.34 ms | 8.33 ms to 8.36 ms |
| canvas: whole frame (recompute to painted), p95 | 16.81 ms | 16.72 ms to 16.88 ms |
| space: orbiting, 50 x 50: paint (FX thread), median | 0.89 ms | 0.87 ms to 0.92 ms |
| space: orbiting, 50 x 50: paint (FX thread), p95 | 1.24 ms | 1.20 ms to 1.26 ms |
| space: orbiting, 50 x 50: whole frame, median | 24.74 ms | 24.33 ms to 24.77 ms |
| space: orbiting, 50 x 50: whole frame, p95 | 25.55 ms | 25.24 ms to 26.59 ms |
| space: at rest, 200 x 200: paint (FX thread), median | 7.07 ms | 7.01 ms to 7.16 ms |
| space: at rest, 200 x 200: paint (FX thread), p95 | 7.94 ms | 7.86 ms to 7.97 ms |
| space: at rest, 200 x 200: whole frame, median | 58.93 ms | 58.60 ms to 59.18 ms |
| space: at rest, 200 x 200: whole frame, p95 | 67.30 ms | 66.66 ms to 67.49 ms |

#### Engine perf suite (`:engine:perfTest`)

| Class | Median | Range |
|---|---|---|
| AlgebraPerformanceTest | 1.41 s | 1.38 s to 1.41 s |
| CalculusPerformanceTest | 0.25 s | 0.24 s to 0.27 s |
| IntegrationPerformanceTest | 2.15 s | 2.11 s to 2.15 s |
| LimitPerformanceTest | 0.07 s | 0.06 s to 0.07 s |
| CompiledPerformanceTest | 0.04 s | 0.04 s to 0.05 s |
| NumericOdePerformanceTest | 0.31 s | 0.30 s to 0.32 s |
| CanonicalPerformanceTest | 0.02 s | 0.02 s to 0.02 s |
| LinalgPerformanceTest | 0.15 s | 0.15 s to 0.16 s |
| NumericLinalgPerformanceTest | 0.08 s | 0.08 s to 0.08 s |
| MatcherPerformanceTest | 0.00 s | 0.00 s to 0.00 s |
| NumberTheoryPerformanceTest | 2.46 s | 2.45 s to 2.50 s |
| SimplifierPerformanceTest | 0.40 s | 0.39 s to 0.42 s |
| SolvePerformanceTest | 1.12 s | 1.11 s to 1.14 s |
| **all classes** | 8.43 s | 8.41 s to 8.54 s |

#### Sizes

| Artefact | Size |
|---|---|
| installer math-canvas-1.1.0.dmg | 45.7 MB |
| jlink image app/build/image | 63.5 MB |
| jar engine-1.1.0.jar | 1.6 MB |
| jar render-1.1.0.jar | 856 KB |
| jar canvas-1.1.0.jar | 557 KB |
| jar app-1.1.0.jar | 1,020 KB |
| jar cli-1.1.0.jar | 46 KB |

Source lines (non-blank lines):

| Module | Main | Test | Resources |
|---|---|---|---|
| engine | 68,155 | 43,719 | 694 |
| render | 2,744 | 948 | 93 |
| canvas | 20,639 | 12,494 | 36 |
| app | 18,906 | 19,358 | 1,816 |
| cli | 1,077 | 527 | 9 |
| docs | 552 | 324 | 407 |
| build-logic | 744 | 0 | 0 |
| tools | 1,089 | 0 | 0 |
| **all** | 113,906 | 77,370 | 3,055 |

| Largest source file | Non-blank lines |
|---|---|
| `engine/src/main/java/mathcanvas/engine/eval/Evaluator.java` | 3,063 |
| `app/src/uiTest/java/mathcanvas/app/CanvasModeTest.java` | 2,252 |
| `engine/src/main/java/mathcanvas/engine/parse/Parser.java` | 1,894 |
| `engine/src/main/java/mathcanvas/engine/calculus/LimitMethods.java` | 1,716 |
| `app/src/main/java/mathcanvas/app/eval/Document.java` | 1,442 |
| `engine/src/main/java/mathcanvas/engine/print/InfixPrinter.java` | 1,286 |
| `app/src/main/java/mathcanvas/app/ui/WorksheetWindow.java` | 1,196 |
| `app/src/main/java/mathcanvas/app/persist/CanvasCodec.java` | 1,170 |
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
