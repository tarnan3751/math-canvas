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
