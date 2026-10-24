#!/usr/bin/env bash
# Runs the UI tests (TestFX, app/src/uiTest), which open real windows on the display.
#   tools/ui_tests.sh                          every UI test
#   tools/ui_tests.sh --tests '*MainWindow*'   any extra arguments go to Gradle
# Runs ./gradlew :app:uiTest under a machine-wide lock, ${TMPDIR:-/tmp}/math-canvas-ui.lock,
# so two lanes never drive the display at once: a second run waits for the first (at most 30
# minutes). macOS uses lockf(1), Linux flock(1); Windows (Git Bash) takes no lock, since a CI
# runner runs one job at a time. The app's data directory is a fresh scratch directory under
# ${TMPDIR:-/tmp} (-PuiTestDataDir), never the owner's. Screenshots land in
# app/build/ui-screenshots/. Exits with Gradle's status. tools/gates.sh does not run the UI
# tests: the gate stays headless.
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
gradle=(./gradlew --console=plain :app:uiTest "-PuiTestDataDir=$gradle_data_dir" "$@")

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
echo "ui_tests: screenshots in app/build/ui-screenshots/"
exit $status
