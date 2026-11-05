# math-canvas

math-canvas is a desktop math app. You type a problem into a worksheet and get an exact answer
back, with the steps if you want to see them, and there's a canvas next to it for graphs and
geometry. It covers basic arithmetic up through calculus, linear algebra, differential equations,
statistics and number theory.

It grew out of my first Java project,
[Scientific-Calculator](https://github.com/tarnan3751/Scientific-Calculator), which I've left up
as it was. The name is a working title.

## What it looks like

![The worksheet in the light theme](docs/images/worksheet-light.png)

Each cell shows what you typed and the answer, written out the way you'd write it on paper. The
last cell has its steps open. The keypad and your variables are on the right.

![The worksheet in the dark theme](docs/images/worksheet-dark.png)

The same worksheet in the dark theme. There are more screenshots, including the canvas, on the
[docs site](https://tarnan3751.github.io/math-canvas/screenshots.html).

## What it can do

- Exact answers: `1/3 + 1/6` gives `1/2` and `sqrt(8)` gives `2√2`. You only get a decimal when
  you ask for one, and approximate values are labeled as approximate.
- Steps: turn on Show steps, or wrap an expression in `steps(...)`, to see how an answer was
  worked out, one line at a time.
- Algebra and calculus: expand, factor and simplify, solve equations and systems, differentiate,
  integrate, and take limits, sums and Taylor polynomials.
- Linear algebra: determinants, inverses, row reduction with each row operation shown,
  eigenvalues and eigenvectors, and the LU, QR, Cholesky and SVD decompositions.
- Differential equations: exact solutions for the standard types, numeric solutions for the rest.
- Statistics, number theory and more: distributions, regression and hypothesis tests; primes,
  factoring and modular arithmetic; units and unit conversion; loan and investment calculations.
- A canvas: graph functions, curves and inequalities, build geometry you can drag around, add
  sliders, and draw tangents, areas, Riemann sums, vector and slope fields and 3D surfaces.
- Small programs: conditionals, loops and your own functions, in a cell or a script file.
- Built-in help that lists every function and command with an example you can run.

## Installing

Download the installer for your system from the
[releases page](https://github.com/tarnan3751/math-canvas/releases). It comes with its own Java
runtime, so you don't need to install anything else.

The installers aren't code-signed, so macOS and Windows will warn you the first time you open the
app. The steps below show how to get past that.

### macOS

1. Download `math-canvas-<version>-macos-arm64.dmg` (Apple silicon).
2. Open it and drag math-canvas into Applications.
3. Open the app. macOS will say it can't verify the developer. Close that message, go to
   **System Settings → Privacy & Security**, and click **Open Anyway** next to math-canvas, then
   **Open**.

If you'd rather use Terminal, this does the same thing:

```
xattr -d com.apple.quarantine /Applications/math-canvas.app
```

On macOS 14 and earlier you can also right-click the app and choose **Open**. To uninstall, drag
the app to the Trash.

### Windows

1. Download `math-canvas-<version>-windows-x64.msi` and run it.
2. If SmartScreen says Windows protected your PC, click **More info**, then **Run anyway**.
3. The installer puts the app in `C:\Program Files\math-canvas\` and adds Start menu and desktop
   shortcuts.

To uninstall, go to **Settings → Apps → Installed apps**.

### Linux

1. Download `math-canvas-<version>-linux-x64.deb` (Debian, Ubuntu and relatives).
2. Install it with `sudo apt install ./math-canvas-<version>-linux-x64.deb`.
3. Launch it from your application menu, or run `/opt/math-canvas/bin/math-canvas`.

To uninstall, run `sudo apt remove math-canvas`.

## Building from source

You need a JDK installed (17 or newer) to start Gradle. The project itself uses Java 25, but
Gradle downloads that on the first build, so you don't have to install it yourself.

```
./gradlew :app:run       # run the app
./gradlew build          # check formatting, compile, run the tests
./gradlew jpackage       # build an installer for your platform in app/build/jpackage/
tools/gates.sh           # every check a change has to pass
```

The app's name comes from the `appName` property in `app/build.gradle.kts`. Change it there and
the launcher, the installers and the window title all follow.

Releases are built by CI. Pushing a `vX.Y.Z` tag builds the three installers and the docs site
and attaches them to a draft release on GitHub.

## Command line

The engine also runs without the window. You can pass it a statement, give it a `.mc` script, or
use it as a prompt:

```
./gradlew :cli:run --args="-e 'factor(x^4 - 1)'"   # run a statement
./gradlew :cli:run --args="$PWD/script.mc"         # run a script (relative paths start in cli/)
./gradlew :cli:run                                 # interactive prompt, type exit to quit
```

Results print in Unicode by default. Use `--ascii` or `--latex` for plain text or LaTeX.

| What | Input | Output |
|---|---|---|
| Exact arithmetic | `1/3 + 1/6` | `1/2` |
| Simplifying | `sqrt(8)` | `2√2` |
| Assumptions | `sqrt(x^2)`, then `assume(x >= 0); sqrt(x^2)` | `\|x\|`, then `x` |
| Factoring | `factor(x^4 - 1)` | `(x - 1)(x + 1)(x² + 1)` |
| Derivatives | `d/dx (x^2 sin x)` | `x² cos(x) + 2x sin(x)` |
| Integrals | `integrate(x e^x, x)` | `x e^x - e^x` |
| Equations | `solve(x^2 - 5x + 6 = 0, x)` | `{2, 3}` |
| Systems | `solve({x + y = 3, x - y = 1}, [x, y])` | `{[x = 2, y = 1]}` |
| Matrices | `[[1, 2], [3, 4]] * [[5, 6], [7, 8]]` | `[[19, 22], [43, 50]]` |

`steps(...)` prints how it got there, and `--steps` does that for every statement:

```
> steps(sqrt(8))
  step 1: Write the radicand as a power of a smaller base: √8 = 2√2
  = 2√2
2√2
```

## Documentation

- The [docs site](https://tarnan3751.github.io/math-canvas/) has a getting-started guide.
- [`docs/language.md`](docs/language.md) describes the language: the syntax and every function
  and command.
- [`docs/reference/`](docs/reference/) explains how each part of the app works, and includes the
  [performance numbers](docs/reference/performance.md).
- [`CHANGELOG.md`](CHANGELOG.md) lists what changed in each version.

## Contributing

Bug reports and pull requests are welcome. [`CONTRIBUTING.md`](CONTRIBUTING.md) covers how to
build, which checks a change has to pass and how commits are formatted. If you get a wrong answer,
please open an issue with what you typed and what came back. There's a
[code of conduct](CODE_OF_CONDUCT.md) too.

## License

Apache 2.0, see [LICENSE](LICENSE). The bundled fonts and libraries keep their own licenses, which
are listed in [NOTICE](NOTICE).
