# math-canvas

A desktop math workspace: a symbolic engine written from scratch, with exact arithmetic,
canonical forms and a rule-based simplifier that records every step, and one canvas for
graphing and interactive geometry, covering arithmetic through multivariable calculus and
linear algebra. Built with Java 25 and JavaFX 25. `math-canvas` is a working name.

It grows out of the author's first Java project,
[Scientific-Calculator](https://github.com/tarnan3751/Scientific-Calculator), which stays as it
was as a snapshot of where this started.

## Status

Pre-alpha and private. Phase 1, the engine core and its command-line runner, has landed: the
engine computes exactly, simplifies, does polynomial algebra, differentiates and solves, and the
CLI runs it. The desktop app is still the skeleton window until Phase 2 brings the worksheet.

## Building and running

You need only a JDK 17 or newer to launch the Gradle wrapper. The build provisions JDK 25 for
itself: the Gradle daemon and the Java toolchain are both downloaded on the first run if no
JDK 25 is installed, so no local JDK 25 is needed.

```
./gradlew build          # format check, compile, every test, the module-boundary check
./gradlew :app:run       # run the application
tools/gates.sh           # everything a change must pass
./gradlew jpackage       # the platform installer, under app/build/jpackage/
```

## Trying the engine

The CLI runs the engine on statements typed at a prompt, given on the command line, or read from
`.mc` script files, and prints each result in Unicode (`--ascii` and `--latex` choose the other
printers):

```
./gradlew :cli:run --args="-e 'factor(x^4 - 1)'"   # one or more statements
./gradlew :cli:run --args="$PWD/script.mc"         # a script file (a relative path is from cli/)
./gradlew :cli:run                                 # the REPL: a statement per line, exit to leave
```

What the engine covers, with what the CLI prints:

| Area | Input | Output |
|---|---|---|
| Exact arithmetic | `1/3 + 1/6` | `1/2` |
| Simplification | `sqrt(8)` | `2√2` |
| Simplification under assumptions | `sqrt(x^2)`, then `assume(x >= 0); sqrt(x^2)` | `\|x\|`, then `x` |
| Polynomial algebra | `factor(x^4 - 1)` | `(x - 1)(x + 1)(x² + 1)` |
| Differentiation | `d/dx (x^2 sin x)` | `x² cos(x) + 2x sin(x)` |
| Solving | `solve(x^2 - 5x + 6 = 0, x)` | `{2, 3}` |
| Linear systems | `solve({x + y = 3, x - y = 1}, [x, y])` | `{[x = 2, y = 1]}` |
| Vectors and matrices | `[[1, 2], [3, 4]] * [[5, 6], [7, 8]]` | `[[19, 22], [43, 50]]` |

`steps(e)` prints the steps that led to the value before it, and `--steps` does so for every
statement:

```
> steps(sqrt(8))
  step 1: Write the radicand as a power of a smaller base: √8 = 2√2
  = 2√2
2√2
```

[`docs/language.md`](docs/language.md) is the language: its grammar, every function and command,
and how statements evaluate. How each part of the engine and the CLI works is in
[`docs/reference/`](docs/reference/).

## Releases

Pushing a tag `vX.Y.Z` builds installers for macOS (`.dmg`), Windows (`.msi`) and Linux
(`.deb`) and attaches them to a draft release.

The installers are **unsigned**, so the operating system warns before the first launch:

- **macOS (Gatekeeper):** open the application once and dismiss the warning, then go to
  **System Settings → Privacy & Security** and choose **Open Anyway** next to math-canvas; or
  remove the quarantine flag with `xattr -d com.apple.quarantine /Applications/math-canvas.app`.
  On macOS 14 and earlier, right-click the application and choose **Open** also works.
- **Windows (SmartScreen):** choose **More info**, then **Run anyway**.

macOS rejects an application version whose first number is 0, so while the version is 0.x.y
the macOS bundle carries the version 1.x.y (0.1.0 is packaged as 1.1.0). Windows and Linux
packages carry the version without any qualifier, and the window title always shows the real
version.

## Licence

Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE). The bundled STIX Two Math font
is under the SIL Open Font License 1.1.
