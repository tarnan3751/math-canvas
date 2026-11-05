#!/usr/bin/env bash
# What the tools share, sourced by gates.sh, run_tests.sh and measure.sh from the repository root
# (`. tools/lib.sh` after their `cd`); it runs nothing itself.

# keep_failures <marker> <module>...: copies each module's test reports, of test and of perfTest,
# newer than <marker> (written by this run) that hold a <failure or <error element into
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
    for f in "$m"/build/test-results/test/*.xml "$m"/build/test-results/perfTest/*.xml; do
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

# load_average: the system's load averages over 1, 5 and 15 minutes, or "unknown" where the
# platform does not say (Windows).
load_average() {
  local l
  l="$(uptime 2>/dev/null | sed -nE 's/.*load averages?: *//p' | tr -s ', ' ' ')"
  echo "${l:-unknown}"
}
