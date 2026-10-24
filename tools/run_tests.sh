#!/usr/bin/env bash
# Runs the test suite.
#   tools/run_tests.sh                       every module
#   tools/run_tests.sh engine                one module
#   tools/run_tests.sh engine '*Parser*'     one module, tests matching a Gradle --tests pattern
# Writes build/ and .gradle/ in the tree it runs in, so an agent runs it only in its clone.
set -uo pipefail
cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "run_tests: no Gradle wrapper yet (Phase 0 has not landed)" >&2
  exit 2
fi

module="${1:-}"
pattern="${2:-}"
if [ -z "$module" ]; then
  exec ./gradlew --console=plain test
fi
[ -f "$module/build.gradle.kts" ] || { echo "run_tests: no module $module" >&2; exit 2; }
if [ -n "$pattern" ]; then
  exec ./gradlew --console=plain ":$module:test" --tests "$pattern"
fi
exec ./gradlew --console=plain ":$module:test"
