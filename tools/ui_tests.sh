#!/usr/bin/env bash
# Runs the UI tests (TestFX, <module>/src/uiTest of each module that has them: canvas, app), which
# open real windows on the display.
#   tools/ui_tests.sh                          every UI test of every module
#   tools/ui_tests.sh --tests '*MainWindow*'   only the matching tests, in whichever module
#   tools/ui_tests.sh <other gradle args>      passed through to Gradle
# Runs ./gradlew --continue :canvas:uiTest :app:uiTest under a machine-wide lock,
# ${TMPDIR:-/tmp}/math-canvas-ui.lock, so two lanes never drive the display at once: a second run
# waits for the first (at most 30 minutes). macOS uses lockf(1), Linux flock(1); Windows (Git Bash)
# takes no lock, since a CI runner runs one job at a time. --tests <pattern> becomes
# -PuiTestFilter=<pattern>, which every module's uiTest task applies, a module with no match running
# none (Gradle's own --tests would bind to the last task named only). --continue runs every
# module's tests even when an earlier module's fail, and the exit status still fails. The app's
# data directory is a fresh scratch directory under ${TMPDIR:-/tmp} (-PuiTestDataDir), never the
# owner's. Screenshots land in <module>/build/ui-screenshots/. Exits with Gradle's status.
# tools/gates.sh does not run the UI tests: the gate stays headless.
# Writes build/ and .gradle/ in the tree it runs in, so an agent runs it only in its clone.
set -uo pipefail
cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "ui_tests: no Gradle wrapper yet (Phase 0 has not landed)" >&2
  exit 2
fi

data_dir="$(mktemp -d "${TMPDIR:-/tmp}/math-canvas-ui-data.XXXXXX")"
trap 'rm -rf "$data_dir"' EXIT
lock="${TMPDIR:-/tmp}/math-canvas-ui.lock"
# Git Bash on Windows: the JVM needs a Windows path, not an MSYS one.
gradle_data_dir="$data_dir"
command -v cygpath >/dev/null && gradle_data_dir="$(cygpath -m "$data_dir")"
modules=(canvas app)
tasks=()
for m in "${modules[@]}"; do
  [ -d "$m/src/uiTest" ] && tasks+=(":$m:uiTest")
done
args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --tests)
      [ $# -ge 2 ] || { echo "ui_tests: --tests needs a pattern" >&2; exit 2; }
      args+=("-PuiTestFilter=$2")
      shift 2
      ;;
    --tests=*)
      args+=("-PuiTestFilter=${1#--tests=}")
      shift
      ;;
    *)
      args+=("$1")
      shift
      ;;
  esac
done
gradle=(./gradlew --console=plain --continue "${tasks[@]}" "-PuiTestDataDir=$gradle_data_dir")
gradle+=(${args[@]+"${args[@]}"})

case "$(uname -s)" in
  Darwin)
    echo "ui_tests: taking the UI lock $lock (waits while another run holds it)"
    lockf -k -t 1800 "$lock" "${gradle[@]}"
    ;;
  Linux)
    echo "ui_tests: taking the UI lock $lock (waits while another run holds it)"
    flock -w 1800 "$lock" "${gradle[@]}"
    ;;
  *)
    "${gradle[@]}"
    ;;
esac
status=$?
echo "ui_tests: screenshots in $(printf '%s/build/ui-screenshots/ ' "${modules[@]}")"
exit $status
