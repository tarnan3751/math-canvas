#!/usr/bin/env bash
# Runs the test suite.
#   tools/run_tests.sh                       every module
#   tools/run_tests.sh engine                one module
#   tools/run_tests.sh engine '*Parser*'     one module, tests matching a Gradle --tests pattern
# A pattern that names performance tests (one matching *PerformanceTest*) runs the module's
# perfTest task, where the wall-clock *PerformanceTest classes live; test leaves them out, and
# neither form without a pattern runs them (docs/reference/testing-tools.md, **Tests**).
# Writes build/ and .gradle/ in the tree it runs in, so an agent runs it only in its clone.
# When the tests fail, the failing test reports of this run and the modules' jqwik databases are
# kept in build/gate-failures/<UTC timestamp>-<pid>/<module>/, whose path it prints: report it, and
# the failing seed, before running anything again.
set -uo pipefail
cd "$(dirname "$0")/.."
. tools/lib.sh

module="${1:-}"
pattern="${2:-}"
if [ -z "$module" ]; then
  modules=(engine render canvas app cli)
  task=(test)
else
  [ -f "$module/build.gradle.kts" ] || { echo "run_tests: no module $module" >&2; exit 2; }
  modules=("$module")
  task=(":$module:test")
  if [ -n "$pattern" ]; then
    case "$pattern" in
      *PerformanceTest*) task=(":$module:perfTest") ;;
    esac
    task+=(--tests "$pattern")
  fi
fi
# The marker tells this run's test reports from the previous run's (keep_failures).
marker="$(mktemp "${TMPDIR:-/tmp}/math-canvas-tests.XXXXXX")"
trap 'rm -f "$marker"' EXIT
./gradlew --console=plain "${task[@]}"
status=$?
if [ $status -ne 0 ]; then
  kept="$(keep_failures "$marker" "${modules[@]}")"
  if [ -n "$kept" ]; then
    echo "run_tests: failing reports and seeds kept in $kept"
  fi
fi
exit $status
