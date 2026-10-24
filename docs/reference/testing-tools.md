# Tests and tools

How the build is put together, how tests run, what the gate checks, and how CI, releases and
packaging work. Read `CLAUDE.md` first; this file assumes its **Commands**.

## The build

Gradle 9.8.1 through the committed wrapper (`./gradlew`; never a system Gradle), Kotlin DSL
everywhere.

- **`settings.gradle.kts`** includes the five modules (`engine`, `render`, `canvas`, `app`,
  `cli`), applies the foojay toolchain resolver, and includes `build-logic` as a plugin build.
  The foojay plugin's version is written there because a settings `plugins {}` block cannot read
  the version catalog.
- **`gradle/libs.versions.toml`** is the version catalog: every other library, plugin and tool
  version. A comment beside a pin explains any version held back on purpose.
- **`gradle.properties`** holds the project `version` (a `-SNAPSHOT` between releases; the
  release workflow overrides it with `-Pversion=X.Y.Z`) and turns on the build cache, the
  configuration cache and parallel execution.
- **`build-logic/`** is an included build of precompiled Kotlin script plugins, one per
  convention, with the small Kotlin classes they use beside them:
  - `mathcanvas.java-conventions`, applied by every module: the `java-library` plugin, the
    Java 25 toolchain, UTF-8, `-Xlint:all -Werror` for main and test code, the test
    dependencies, Spotless, and the per-module test summary.
  - `mathcanvas.javafx-conventions`, applied by `render`, `canvas` and `app`: the OpenJFX
    `javafx-base`, `javafx-graphics` and `javafx-controls` jars for the platform running the
    build (`mac-aarch64`, `mac`, `win`, `linux`, `linux-aarch64`, chosen from `os.name` and
    `os.arch`). The OpenJFX Gradle plugin is not used.
  - `mathcanvas.javafx-free`, applied by `engine` and `cli`: the module-boundary check below.

**`java-library` and `api`.** A module's `requires transitive` in its `module-info.java` is
declared as an `api` dependency in its `build.gradle.kts` (`render` on `engine`, `canvas` on
`render`), which is why the conventions apply `java-library` rather than `java`. With
`implementation` the transitive module would be missing from the compile module path of the
modules further down.

**JavaFX jars are declared non-transitive.** The OpenJFX POMs name their platform jars through a
Maven profile property that Gradle does not evaluate, so transitively they contribute only the
empty platform-less jars (automatic modules named `javafx.baseEmpty` and so on), which would
land on the module path and break jlink. Each module therefore declares all three platform jars
itself, each with `isTransitive = false`.

**JDK 25 for the daemon and the toolchain.** `gradle/gradle-daemon-jvm.properties` requires the
daemon itself to run on an Adoptium JDK 25, and lists download URLs per platform so Gradle
provisions one when none is installed. This matters because google-java-format parses sources
with the running JVM's compiler, which must be at least as new as the sources. The JDK that
starts `./gradlew` only launches the wrapper client; any JDK 17 or newer does. The compile
toolchain is `languageVersion = 25` with no vendor constraint, found locally or provisioned
through foojay. Downloaded JDKs live under `~/.gradle/jdks`. To move to a new JDK, regenerate the
criteria with `./gradlew updateDaemonJvm --jvm-version=<N> --jvm-vendor=adoptium` and change the
toolchain in `mathcanvas.java-conventions`.

**The version resource.** `engine/src/main/resources/mathcanvas/engine/version.properties`
holds `version=${version}`; `engine`'s `processResources` expands it from the Gradle project
version and `mathcanvas.engine.Version.current()` reads it. No version string is written in
code.

## Formatting

Spotless runs as part of `check`, so `./gradlew build` fails on a formatting difference;
`./gradlew spotlessApply` fixes it.

- Java: google-java-format (default Google style) and unused-import removal. The version is
  pinned in the catalog; 1.37.0 is held back because Spotless 8.10.4 cannot drive it.
- `misc` (root project): Kotlin scripts and the `build-logic` Kotlin sources, `gradle/*.toml`,
  the GitHub workflow YAML, `tools/*.sh` and `.editorconfig` get trailing whitespace trimmed and
  a final newline. Its targets are explicit roots (`*.kts`, `*/*.kts`, `build-logic/src/**`, and
  so on), never a recursive glob from the project root: walking `.gradle/` and `build/` would
  make every build miss the configuration cache.
- `markdown` (root project): the root `*.md` files and `docs/**/*.md` get only a final newline,
  so two-space hard line breaks stay legal, matching `.editorconfig`.
- There are no per-file licence headers.

`.gitattributes` keeps every text file LF in the working tree on every platform (`gradlew.bat`
CRLF), so a Windows checkout does not trip the final-newline check.

## Tests

Tests run on the classpath: there is no `module-info.java` under any `src/test`, and a test in
package `mathcanvas.x` sees that module's main classes and resources as if they were on the
classpath. Every module gets JUnit Jupiter (through the JUnit BOM), jqwik for property tests,
AssertJ, and `junit-platform-launcher`, all on the JUnit Platform. jqwik property classes sit
beside the JUnit tests; by convention they are named `*Properties`.

- **The summary line.** When a module's tests finish, the build prints
  `<module>: N tests completed, M failed, K skipped`; the gate reports every module's count and
  the total. In a plain `./gradlew build` a test task that is up to date or comes from the build
  cache does not run and prints nothing; `./gradlew test --rerun` forces a run, which is what the
  gate does.
- **jqwik's database.** jqwik records failing and recent seeds in a database file, which the
  test task points at `build/jqwik-database` (the `jqwik.database` system property) so it never
  appears in a module's source tree. `./gradlew clean` discards it.
- **No display in tests.** The unit tests never start the JavaFX toolkit; a test of a
  JavaFX-facing class checks what it can without it (the font test checks the resource's
  hash). Anything that needs a window is a UI test (**UI tests**).

`tools/run_tests.sh` runs the tests: every module, one module (`tools/run_tests.sh engine`), or
one module filtered by a Gradle `--tests` pattern (`tools/run_tests.sh engine '*Version*'`).

## UI tests

UI tests drive the real application window with TestFX. They live in `app`'s `uiTest` source
set (`app/src/uiTest/java`), which sees the main classes and every unit-test dependency, plus
`testfx-core` and `testfx-junit5`. Like the unit tests they run on the classpath, so the
`uiTest` task starts the JVM with `--enable-native-access=ALL-UNNAMED` for JavaFX's native
libraries, and JavaFX logs a one-line "Unsupported JavaFX configuration" warning, which is
expected.

- **The task.** `./gradlew :app:uiTest` is a `Test` task on the JUnit Platform with the same
  summary line (`app: N tests completed, M failed, K skipped`). It is not part of `check` or
  `build`, so `./gradlew build` and the gate never open a window, but `check` depends on
  `uiTestClasses`, so a compile error in a UI test fails the build and the gate headlessly. The
  task is never up to date or served from the build cache: every run opens the window and
  writes fresh screenshots. It passes `-Dmathcanvas.dataDir=<dir>` to the tests from the Gradle
  property `uiTestDataDir` (default `app/build/ui-test-data`), the data directory the app will
  read in Phase 2.
- **Writing a test.** A test class is `@ExtendWith(ApplicationExtension.class)`; its `@Start`
  method receives the primary stage and calls `new Main().start(stage)`. The test method runs
  on the test thread, so it reads and snapshots nodes on the FX thread through
  `WaitForAsyncUtils.asyncFx` and `waitFor`. The robot is TestFX's default, `glass`, which on
  JavaFX 25 is the public `javafx.scene.robot.Robot`. The rendering pipeline is the platform's
  real one (`prism.order` is not set).
- **Screenshots.** A test writes its screenshots as PNG under `app/build/ui-screenshots/`
  (`MainWindowTest` writes `main-window.png`), taken with `Scene.snapshot(null)` on the FX
  thread and converted through a `BufferedImage` and `ImageIO`, never with the robot's screen
  capture.
- **macOS permissions.** Snapshots need none, and a test without input needs none, which is
  why `MainWindowTest` is input-free. A future interaction test (clicks, typing) moves the real
  pointer and keyboard through the robot, which needs the Accessibility permission for the JVM
  that runs the tests; screen captures through the robot would also need Screen Recording.

`tools/ui_tests.sh [gradle args]` is the way to run them: it runs `./gradlew :app:uiTest` with
any extra arguments (`tools/ui_tests.sh --tests '*MainWindow*'`), passes a fresh scratch data
directory under `${TMPDIR:-/tmp}` as `-PuiTestDataDir` and removes it afterwards, prints where
the screenshots are, and exits with Gradle's status. It holds a machine-wide lock,
`${TMPDIR:-/tmp}/math-canvas-ui.lock`, for the whole run, so two lanes never drive the display
at once: a second run waits for the first, for at most 30 minutes. macOS takes it with
`lockf -k -t 1800` and Linux with `flock -w 1800`, both kernel locks released when the process
exits, so a crashed run leaves no stale lock; Windows (Git Bash) takes none, since CI runners
run one job at a time.

**Test-only dependencies.** TestFX is licensed under the EUPL 1.1, a copyleft licence. That is
acceptable because it is a test-only dependency (`uiTestImplementation`) that never enters the
distribution: test-only dependencies may carry any OSI licence, and nothing copyleft ships
(`PLAN.md` §11). `NOTICE` lists only the components that ship, so it does not name TestFX.
Monocle is not used: it is GPL-family and has no JavaFX 25 release, and JavaFX 25 itself has no
headless platform, so UI tests need a real display (or Xvfb on Linux).

## The module-boundary check

`checkJavafxFree`, registered by `mathcanvas.javafx-free` and run by `check`, fails the build
when the module (`engine` or `cli`):

- requires a `javafx.*` module in its `module-info.java`;
- has the token `javafx.` anywhere in a `.java` file under `src/main` or `src/test`, comments
  included;
- resolves any `org.openjfx` artifact on its main or test, compile or runtime classpath.

The failure names each offending line or configuration. The gate's own grep for `javafx.` in
`engine` and `cli` is a second guard that runs even when the build fails.

## The gate

`tools/gates.sh` runs, in order, printing one line per step and the failing step's output:

1. **layout**: the five modules each have a `build.gradle.kts`.
2. **format**: `./gradlew spotlessCheck`.
3. **build**: `./gradlew build test --rerun`, which compiles every module and runs the Spotless
   check, the boundary check and every test. `--rerun` applies to the test tasks only, so
   compilation stays incremental and cached while the tests always run and their counts are
   always reported: every module's summary line, by module name, and the total, as in
   `gate build     ok (engine: N, render: M tests completed, T total)`.
4. **boundary**: a grep for `javafx.` under `engine/src` and `cli/src`.
5. **corpus**: `tools/run_corpus.sh` once it exists (Phase 1); until then it passes.

It exits non-zero when any step fails. It never runs the UI tests, which open windows; the gate
stays headless. It writes `build/` and `.gradle/` in the tree it runs in, so it runs in a working
clone, not in the main tree.

## Landing

`tools/land_series.sh <patch-dir> <base-commit> <tree-hash> [--dry-run]` applies a delivered
`git format-patch` series to `main` in the repository it is run in. It refuses an unclean tree,
a HEAD other than the base on `main`, a subject that is not a Conventional Commit, a message
with a trailer, a patch that does not apply, and a final tree other than the reported one,
resetting to the base after any refusal once `git am` has started; then it runs the gate and
leaves the commits in place, unpushed.

## Changelog

`CHANGELOG.md` holds one section per release, newest first, each generated from the
Conventional Commit log by `tools/changelog.sh <version> [<from-ref>|--] [<to-ref>]` and
committed as generated, never edited by hand. The script prints a heading
`## <version> — <date>` (the ISO date of `<to-ref>`'s commit), then one subsection per commit
type present: Features, Fixes, Performance, Refactoring, Build, CI, Documentation, Tests,
Maintenance (`chore` and `style`), Reverts, and Other for a subject that is not a Conventional
Commit by the rule `tools/land_series.sh` checks. Each commit is
``- **scope:** Summary (`sha`)``, oldest first; a subject with the `!` breaking marker gets
`**BREAKING:**` before its summary, as in ``- **engine:** **BREAKING:** Summary (`sha`)``.
`<to-ref>` defaults to `HEAD`; `<from-ref>` defaults to the previous tag reachable from
`<to-ref>`, or to the whole history when there is none (`--` keeps that default while naming a
`<to-ref>`). To add a release after tagging it, run, for example,
`tools/changelog.sh 0.2.0 v0.1.0 v0.2.0` and paste the output below the file's header, above
the previous release. The script is plain bash and git, so it runs in Git Bash on Windows.

## Running the app

`./gradlew :app:run` runs the application on the module path. In Phase 0 it reads and writes
nothing; from Phase 2 a normal run opens the owner's data directory, so a working clone runs it
only with `--data-dir` on scratch, or through the UI harness. On first start the JavaFX runtime
extracts its own native libraries from the OpenJFX jars into `~/.openjfx/cache`; that is the
toolkit's cache, not application data, and it happens for the packaged app too. Both `:app:run`
and the jlink launcher start the JVM with `--enable-native-access=javafx.graphics`: JavaFX loads
those libraries through `System.load`, a restricted method in JDK 25 that otherwise prints a
warning at startup and will be blocked in a future release. If a fully self-contained image is
wanted, Gluon's platform jmods are the Phase 9 option.

## Packaging

The `org.beryx.jlink` plugin on `app` builds the runtime image and the installers locally:

- `./gradlew jlink` builds a runtime image holding only the needed JDK and JavaFX modules and
  ours, stripped (`--strip-debug --no-header-files --no-man-pages --compress zip-6`), at
  `app/build/image`; its launcher is `app/build/image/bin/math-canvas`.
- `./gradlew jpackage` turns the image into the installer for the platform running the build,
  under `app/build/jpackage/`: a `.dmg` on macOS, an `.msi` on Windows (the WiX Toolset must be
  installed), a `.deb` on Linux (`fakeroot` must be installed). The package name is
  `math-canvas`, the vendor Tahseen Arnan, the macOS bundle identifier
  `io.github.tarnan3751.mathcanvas`.

**The package version.** jpackage (JDK 25) rejects a macOS app version whose first component is
0, and Windows needs two to four numeric components with major and minor at most 255. The
package version is the project version with any `-qualifier` stripped (`0.1.0-SNAPSHOT` packages
as `0.1.0`); on macOS only, while the major is 0, the bundle version is `1.<minor>.<patch>`, so
`0.1.0` gives `math-canvas-1.1.0.dmg`. The real version is always in the window title. The rule
is stated beside the code in `app/build.gradle.kts`.

## CI and releases

`.github/workflows/build.yml` runs `./gradlew build` on `ubuntu-latest`, `windows-latest` and
`macos-latest` for every push to `main` and every pull request, with read-only permissions and
a newer push to the same ref cancelling the older run. `actions/setup-java` installs Temurin 25,
which satisfies both the daemon criteria and the toolchain; `gradle/actions/setup-gradle`
caches the Gradle home.

A second job, `ui-tests`, runs once `build` passes, on the same three runners, and runs
`tools/ui_tests.sh` in bash. On Linux it installs `xvfb`, `libgtk-3-0` and `libgl1` and runs
the script under `xvfb-run -a`, a virtual display; on macOS and Windows it runs on the runner's
own desktop session. Every leg uploads `app/build/ui-screenshots/**` as the artifact
`ui-screenshots-<os>`, even when the tests fail, so the screenshots can be fetched with
`gh run download <run-id> -n ui-screenshots-<os>` and read. All three legs are required: a
JavaFX window opens and snapshots correctly on the hosted Linux, macOS and Windows runners (first
seen on 2026-10-24, run 40094215974).

`.github/workflows/release.yml` runs on a pushed tag `vX.Y.Z`. Each platform runs
`./gradlew build jpackage -Pversion=X.Y.Z` (Linux installs `fakeroot` first), renames its
installer to `math-canvas-<version>-<os>-<arch>.<dmg|msi|deb>` from the runner's OS and
architecture, and uploads it as a workflow artifact. A final job downloads the three and runs
`gh release create` with `--draft --prerelease --generate-notes`: the release stays a draft
until the owner publishes it. The installers are unsigned; the README documents the Gatekeeper
and SmartScreen workarounds.

`.github/dependabot.yml` proposes Gradle and GitHub Actions updates monthly. It ignores major
updates of `org.junit:junit-bom`, `org.junit.jupiter:*` and `org.junit.platform:*`: JUnit stays
on 5.x until jqwik supports JUnit Platform 6 (`PLAN.md` §11).
