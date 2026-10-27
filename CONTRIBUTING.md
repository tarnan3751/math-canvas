# Contributing to math-canvas

Thank you for looking. This file says how to build the project, what a change must pass, how
commits are written, how work is reviewed, and how to report a wrong result.
[`CLAUDE.md`](CLAUDE.md) is the map of the code and the working method; read it before a change
of any size.

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## Building

You need a JDK 17 or newer to launch the Gradle wrapper; the build provisions JDK 25 (and the
JavaFX 25 modules) for itself on the first run, which takes a while once. Always use the wrapper,
never a system Gradle.

```
./gradlew build                                    # format check, compile, tests, boundary
./gradlew spotlessApply                            # format the code (google-java-format)
./gradlew :app:run --args="--data-dir=/tmp/mc"     # the app, on a scratch data directory
./gradlew :cli:run --args="-e 'factor(x^4 - 1)'"   # the engine from the command line
```

Run the app with `--data-dir` pointed at a scratch directory while you work on it: without it, the
app reads and writes your real data directory (projects, settings, history) under the operating
system's application-support folder.

## What a change must pass

```
tools/gates.sh
```

The gate is the one command every change passes before it is reviewed or merged: the five modules
are present, Spotless finds nothing to reformat, `./gradlew build test --rerun` compiles every
module and runs every test, no JavaFX type is referenced from `engine` or `cli` (nor from
`render`'s font and typeset packages), and the golden corpora run through the CLI with no
failure. It prints one line per step and the failing step's output in full.
[`docs/reference/testing-tools.md`](docs/reference/testing-tools.md) describes each step.

**Tests.** `tools/run_tests.sh` runs every module's tests, `tools/run_tests.sh engine` one module's,
and `tools/run_tests.sh engine '*Parser*'` those matching a pattern. The engine's soundness is
checked numerically by property tests (jqwik): a rule set is compared with its input at random
points, derivatives against difference quotients, solutions by substitution. When a property
fails, the failing reports and the jqwik database are kept under `build/gate-failures/`; give that
path and the failing seed when you report it.

**UI tests.** `tools/ui_tests.sh` runs the TestFX tests, which open real windows, so they need a
display (Xvfb on Linux). It holds a machine-wide lock, so only one run drives the display at a
time, and it gives the app a fresh scratch data directory. Screenshots land in
`app/build/ui-screenshots/`. The gate does not run the UI tests; CI runs them on all three
platforms.

**Corpora.** The golden `.mc` files under `engine/src/test/corpus/` pin the engine's output.
Changing an expected output, or a property test's tolerance, is a change to the engine's
semantics and is reviewed as one (see **How work is reviewed**).

## Commits

Every commit is a [Conventional Commit](https://www.conventionalcommits.org/):

```
type(scope): Summary in the imperative, in sentence case

A body of plain paragraphs saying what changed and why.
```

- `type` is one of `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`,
  `style`, `revert`; `scope` is a module (`engine`, `render`, `canvas`, `app`, `cli`) or `tools`,
  `docs`, `ci`, `build`, and may be left out.
- The summary is an imperative sentence in sentence case, with no trailing period, at most 72
  characters: `fix(engine): Round far-apart complex quotients once`.
- One logical change per commit; a refactor goes in its own commit, before the change that needs
  it.
- No trailers: no `Signed-off-by`, `Co-authored-by` or similar lines.
- Run `./gradlew spotlessApply` before committing; the gate fails on a formatting difference.

The changelog for a release is generated from these messages (`tools/changelog.sh`), so the
summary is what users read.

## How work is reviewed

The project's working method is the one [`CLAUDE.md`](CLAUDE.md) sets out under **Agent
workflow**, and it applies to every contributor. Each change has a tier:

- **A**: engine semantics (the number tower, canonical form, the matcher, assumptions, a rule
  set's meaning, the grammar), the persistence format, the canvas's dependency graph, module
  boundaries, or any change to a golden corpus or a property tolerance. Open an issue first and
  agree a plan before writing code; the change gets a full review, with soundness probed at
  random points and old project files loaded.
- **B**: features inside the existing frameworks: new rules in an existing set with their tests,
  a canvas tool, worksheet UI, a typesetting construct, tooling. A pull request with the gate
  passing and a targeted review.
- **C**: documentation, comments, configuration.

The rules in `CLAUDE.md` under **Architecture rules** are not negotiable: the engine stays exact
and free of JavaFX, every rewrite is a named rule through the matcher, every document change goes
through the command stack, no engine work runs on the FX thread, and no GPL dependency is added.

A change that makes a statement in the documentation false updates that statement in the same
change: [`docs/language.md`](docs/language.md) for the language, the files under
[`docs/reference/`](docs/reference/) for how each area works, and `CLAUDE.md` for the map.

## Where decisions are recorded

The project's plan, with its phases and the full log of design decisions, is kept privately by the
maintainer. Decisions reach the repository through the documents they shape and the commits that
make them: the language reference, the reference file of each area, and the commit messages, whose
bodies say why. If something there looks like an open question, ask in an issue before deciding
it in code.

## Reporting a wrong result

A wrong answer from the engine is the most useful report there is. Please give:

1. a short `.mc` file that reproduces it, the smaller the better, for example:

   ```
   assume(x >= 0)
   sqrt(x^2)
   ```

2. what the CLI prints for it with the steps:

   ```
   ./gradlew :cli:run --args="--steps $PWD/repro.mc"
   ```

3. what you expected, and why (a reference, or a numeric check);
4. the version: `./gradlew :cli:run --args="--version"`, or the app's window title, or the commit.

The **Wrong result** issue form asks for exactly these. For a crash or a hang, use the **Crash or
hang** form; for an idea, **Feature request**.

## Licence

math-canvas is licensed under the [Apache License 2.0](LICENSE). By contributing, you agree that
your contribution is licensed under the same terms (section 5 of the licence), and that you have
the right to submit it. Dependencies that ship must be under a licence compatible with Apache-2.0,
never GPL; [`NOTICE`](NOTICE) lists every component and its licence.
