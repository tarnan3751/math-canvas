#!/usr/bin/env bash
# Runs the test suite.
#   tools/run_tests.sh                       every module
#   tools/run_tests.sh engine                one module
#   tools/run_tests.sh engine '*Parser*'     one module, tests matching a Gradle --tests pattern
# Writes build/ and .gradle/ in the tree it runs in, so an agent runs it only in its clone.
# When the tests fail, the failing test reports of this run and the modules' jqwik databases are
# kept in build/gate-failures/<UTC timestamp>-<pid>/<module>/, whose path it prints: report it, and
# the failing seed, before running anything again.
set -uo pipefail
cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "run_tests: no Gradle wrapper yet (Phase 0 has not landed)" >&2
  exit 2
fi

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
