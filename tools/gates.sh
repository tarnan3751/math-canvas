#!/usr/bin/env bash
# The gate: everything a change must pass before it is reported, reviewed or landed
# (CLAUDE.md, **Agent workflow**). Runs, in order:
#   1. layout      the five modules the map names exist (engine render canvas app cli)
#   2. format      ./gradlew spotlessCheck
#   3. build       ./gradlew build test --rerun   (every module compiled, every test run but the
#                  performance tests: the tests are forced to run, never served from the build
#                  cache, so every module's test count is always reported, with the total)
#   4. perf        ./gradlew perfTest --rerun   (the wall-clock *PerformanceTest classes, in
#                  their own task, after the build and only when it passed, reported with the
#                  load average; their budgets assume an idle development machine, so a failure
#                  under load is first run again on an idle one)
#   5. boundary    ./gradlew checkJavafxFree (the mathcanvas.javafx-free convention): no javafx.*
#                  reference in engine or cli, nor in render's font and typeset packages, nor in
#                  canvas's geom, paint, hit, model, sample, tool and space packages
#   6. corpus      tools/run_corpus.sh: the golden .mc corpora checked through the installed CLI,
#                  reported with the runner's summary line (docs/reference/cli.md)
# The gate stays headless: it never runs the UI tests, which open windows (tools/ui_tests.sh).
#
#   tools/gates.sh [--quick]     (or GATE_QUICK=1 tools/gates.sh)
#
# --quick is the per-commit profile: layout, format, every source set compiled (main, test and
# app's uiTest), the unit tests only (-PquickGate: every test class but the *Properties ones),
# and the boundary; it skips the property classes, the performance tests and the corpus, and its
# summary line says "(quick)". Without it the gate is the full one above, which reviews and landing run.
# Prints one line per gate, the failing gate's output in full, and exits non-zero when any
# gate fails. When the build fails, the test reports of this run that fail and every module's
# jqwik database are kept in build/gate-failures/<UTC timestamp>-<pid>/<module>/, whose path the
# failure line names: report it, and the failing seed, before running anything again.
# Every Gradle run writes build/ and .gradle/ in the tree it runs in, which is why the gate
# runs only in a lane's clone, never in the coordinator's main tree by an agent.
set -uo pipefail

cd "$(dirname "$0")/.."
. tools/lib.sh

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

scratch="$(mktemp -d "${TMPDIR:-/tmp}/math-canvas-gates.XXXXXX")"
trap 'rm -rf "$scratch"' EXIT
failed=0

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
build_failed=0
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
  build_failed=1
  kept="$(keep_failures "$scratch/marker" engine render canvas app cli docs)"
  report build FAILED "${kept:+failing reports and seeds kept in $kept}" "$scratch/build.txt"
fi

# 4. Performance tests -----------------------------------------------------------------
if [ "$quick" = 1 ]; then
  report perf skipped "quick profile"
elif [ $build_failed -ne 0 ]; then
  report perf skipped "the build failed"
else
  touch "$scratch/perf-marker"
  if ./gradlew --console=plain perfTest --rerun >"$scratch/perf.txt" 2>&1; then
    total=0
    while IFS=' ' read -r _ n; do
      total=$((total + n))
    done < <(grep -oE '^[a-z]+: [0-9]+ tests? completed' "$scratch/perf.txt" | sed -E 's/ tests? completed$//')
    report perf ok "$total tests completed, load average $(load_average)"
  else
    kept="$(keep_failures "$scratch/perf-marker" engine render canvas app cli docs)"
    report perf FAILED "load average $(load_average); the budgets assume an idle development machine, so run it again on one before reading a regression${kept:+; failing reports kept in $kept}" "$scratch/perf.txt"
  fi
fi

# 5. Module boundary --------------------------------------------------------------------
# engine and cli must not reference JavaFX, nor render's font reader and typesetter, nor canvas's
# viewport, painter, hit testing, object model, sampler, tools and 3D scene. The build's check
# already ran when the build passed (up to date here); this reports it on a line of its own, and
# runs it when the build failed or the quick profile skipped it. --continue checks every module,
# so every reference is listed; a failure that lists none means the check itself did not run (a
# build script that does not configure).
if ./gradlew -q --console=plain --continue checkJavafxFree >"$scratch/boundary.txt" 2>&1; then
  report boundary ok "no javafx.* in engine, cli, render's font and typeset, or canvas's geom, paint, hit, model, sample, tool and space"
else
  refs="$(grep -cE 'references JavaFX|requires a JavaFX|resolves org\.openjfx' \
    "$scratch/boundary.txt")"
  if [ "$refs" -gt 0 ]; then
    report boundary FAILED "$refs reference(s)" "$scratch/boundary.txt"
  else
    report boundary FAILED "checkJavafxFree did not run: see the log" "$scratch/boundary.txt"
  fi
fi

# 6. Golden corpora through the CLI ----------------------------------------------------
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
