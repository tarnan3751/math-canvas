# math-canvas

A desktop math workspace: a symbolic engine written from scratch, with exact arithmetic,
canonical forms and a rule-based simplifier that records every step, and one canvas for
graphing and interactive geometry, covering arithmetic through multivariable calculus and
linear algebra. Built with Java 25 and JavaFX 25. `math-canvas` is a working name.

It grows out of the author's first Java project,
[Scientific-Calculator](https://github.com/tarnan3751/Scientific-Calculator), which stays as it
was as a snapshot of where this started.

## Status

Pre-alpha and private. The project skeleton builds, tests, packages and opens a window; the
mathematics comes next.

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
