#!/usr/bin/env bash
# The gate: everything a change must pass before it is reported, reviewed or landed
# (CLAUDE.md, **Agent workflow**). Runs, in order:
#   1. layout      the five modules the map names exist (engine render canvas app cli)
#   2. format      ./gradlew spotlessCheck
#   3. build       ./gradlew build test --rerun   (every module compiled, every test run: the
#                  tests are forced to run, never served from the build cache, so every
#                  module's test count is always reported, with the total)
#   4. boundary    no javafx.* reference under engine/ or cli/, nor in render's font and typeset
#                  packages, nor in canvas's geom, paint, hit and model packages
#   5. corpus      tools/run_corpus.sh: the golden .mc corpora checked through the installed CLI,
#                  reported with the runner's summary line (docs/reference/cli.md)
# The gate stays headless: it never runs the UI tests, which open windows (tools/ui_tests.sh).
#
#   tools/gates.sh [--quick]     (or GATE_QUICK=1 tools/gates.sh)
#
# --quick is the per-commit profile: layout, format, every source set compiled (main, test and
# app's uiTest), the unit tests only (-PquickGate: every test class but the *Properties ones),
# and the boundary; it skips the property classes and the corpus, and its summary line says
# "(quick)". Without it the gate is the full one above, which reviews and landing run.
# Prints one line per gate, the failing gate's output in full, and exits non-zero when any
# gate fails. When the build fails, the test reports of this run that fail and every module's
# jqwik database are kept in build/gate-failures/<UTC timestamp>-<pid>/<module>/, whose path the
# failure line names: report it, and the failing seed, before running anything again. Before
# Phase 0 lands there is no Gradle wrapper; the gate says so and passes, so a docs-only series can
# land on the empty repository.
# Every Gradle run writes build/ and .gradle/ in the tree it runs in, which is why the gate
# runs only in a lane's clone, never in the coordinator's main tree by an agent.
set -uo pipefail

cd "$(dirname "$0")/.."

quick="${GATE_QUICK:-0}"
for arg in "$@"; do
  case "$arg" in
    --quick) quick=1 ;;
    *) echo "usage: tools/gates.sh [--quick]" >&2; exit 2 ;;
  esac
done
[ "$quick" = 1 ] || quick=0
profile=""
[ "$quick" = 0 ] || profile=" (quick)"

if [ ! -x ./gradlew ]; then
  echo "gate: no Gradle wrapper yet (Phase 0 has not landed); nothing to check"
  echo "gates: all passed$profile"
  exit 0
fi

scratch="$(mktemp -d "${TMPDIR:-/tmp}/math-canvas-gates.XXXXXX")"
trap 'rm -rf "$scratch"' EXIT
failed=0

# keep_failures <marker> <module>...: copies each module's test reports newer than <marker> (written
# by this run) that hold a <failure or <error element into
# build/gate-failures/<UTC timestamp>-<pid>/<module>/ at the tree root and, when it copied at least
# one, every module's jqwik database (build/jqwik-database, the failing seeds and samples) beside
# them, so the next run, which rewrites the reports and the database, cannot lose them; prints that
# directory, or nothing when no report was copied. Nothing ever deletes these directories but a
# ./gradlew clean.
keep_failures() {
  local marker="$1" dest kept=0 m f
  shift
  dest="build/gate-failures/$(date -u +%Y%m%dT%H%M%SZ)-$$"
  for m in "$@"; do
    for f in "$m"/build/test-results/test/*.xml; do
      if [ -f "$f" ] && [ "$f" -nt "$marker" ] && grep -qE '<(failure|error)[ >]' "$f"; then
        mkdir -p "$dest/$m" && cp "$f" "$dest/$m/" && kept=1
      fi
    done
  done
  if [ $kept -eq 0 ]; then
    return 0
  fi
  for m in "$@"; do
    if [ -e "$m/build/jqwik-database" ]; then
      mkdir -p "$dest/$m" && cp -R "$m/build/jqwik-database" "$dest/$m/"
    fi
  done
  echo "$dest"
}

# report <name> <status> <detail> [<output-file>]: a status other than ok or skipped fails the gate
report() {
  local name="$1" status="$2" detail="$3" output="${4:-}"
  printf 'gate %-9s %s%s\n' "$name" "$status" "${detail:+ ($detail)}"
  if [ "$status" != "ok" ] && [ "$status" != "skipped" ]; then
    failed=1
    if [ -n "$output" ] && [ -s "$output" ]; then
      sed 's/^/    /' "$output" | tail -200
    fi
  fi
}

# 1. Layout ---------------------------------------------------------------------------
missing=""
for m in engine render canvas app cli; do
  [ -f "$m/build.gradle.kts" ] || missing="$missing $m"
done
if [ -z "$missing" ]; then
  report layout ok "engine render canvas app cli"
else
  echo "modules without a build file:$missing" >"$scratch/layout.txt"
  report layout FAILED "missing:$missing" "$scratch/layout.txt"
fi

# 2. Format ---------------------------------------------------------------------------
if ./gradlew -q --console=plain spotlessCheck >"$scratch/format.txt" 2>&1; then
  report format ok ""
else
  report format FAILED "run ./gradlew spotlessApply" "$scratch/format.txt"
fi

# 3. Build and tests --------------------------------------------------------------------
# --rerun applies to the test tasks only: compilation stays incremental and cached.
# The marker tells this run's test reports from the previous run's (keep_failures).
touch "$scratch/marker"
if [ "$quick" = 1 ]; then
  build=(-PquickGate testClasses uiTestClasses test --rerun)
else
  build=(build test --rerun)
fi
if ./gradlew --console=plain "${build[@]}" >"$scratch/build.txt" 2>&1; then
  # Each module with tests prints `<module>: N tests completed, M failed, K skipped`; the
  # modules finish in any order, so they are listed by name.
  counts="" total=0
  while IFS=' ' read -r module n; do
    counts="${counts:+$counts, }${module} $n"
    total=$((total + n))
  done < <(grep -oE '^[a-z]+: [0-9]+ tests? completed' "$scratch/build.txt" | sed -E 's/ tests? completed$//' | sort)
  if [ -n "$counts" ]; then
    report build ok "$counts tests completed, $total total"
  else
    report build ok "BUILD SUCCESSFUL"
  fi
else
  kept="$(keep_failures "$scratch/marker" engine render canvas app cli)"
  report build FAILED "${kept:+failing reports and seeds kept in $kept}" "$scratch/build.txt"
fi

# 4. Module boundary --------------------------------------------------------------------
# engine and cli must not reference JavaFX, nor render's font reader and typesetter, nor canvas's
# viewport, painter and hit testing; the Gradle
# build enforces this too, this is the belt to its braces and runs even when the build above
# failed.
: >"$scratch/boundary.txt"
for d in engine/src cli/src \
  render/src/main/java/mathcanvas/render/font render/src/main/java/mathcanvas/render/typeset \
  render/src/test/java/mathcanvas/render/font render/src/test/java/mathcanvas/render/typeset \
  canvas/src/main/java/mathcanvas/canvas/geom canvas/src/main/java/mathcanvas/canvas/paint \
  canvas/src/main/java/mathcanvas/canvas/hit canvas/src/test/java/mathcanvas/canvas/geom \
  canvas/src/test/java/mathcanvas/canvas/paint canvas/src/test/java/mathcanvas/canvas/hit \
  canvas/src/main/java/mathcanvas/canvas/model canvas/src/test/java/mathcanvas/canvas/model; do
  [ -d "$d" ] && grep -rnE '\bjavafx\.' "$d" --include='*.java' >>"$scratch/boundary.txt" || true
done
if [ ! -s "$scratch/boundary.txt" ]; then
  report boundary ok "no javafx.* in engine, cli, render's font and typeset, or canvas's geom, paint, hit and model"
else
  report boundary FAILED "$(wc -l <"$scratch/boundary.txt" | tr -d ' ') reference(s)" "$scratch/boundary.txt"
fi

# 5. Golden corpora through the CLI ----------------------------------------------------
if [ "$quick" = 1 ]; then
  report corpus skipped "quick profile"
elif tools/run_corpus.sh >"$scratch/corpus.txt" 2>&1; then
  report corpus ok "$(tail -1 "$scratch/corpus.txt")"
else
  report corpus FAILED "" "$scratch/corpus.txt"
fi

if [ $failed -eq 0 ]; then
  echo "gates: all passed$profile"
else
  echo "gates: FAILED$profile"
fi
exit $failed
