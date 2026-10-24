#!/usr/bin/env bash
# The gate: everything a change must pass before it is reported, reviewed or landed
# (CLAUDE.md, **Agent workflow**). Runs, in order:
#   1. layout      the five modules the map names exist (engine render canvas app cli)
#   2. format      ./gradlew spotlessCheck
#   3. build       ./gradlew build test --rerun   (every module compiled, every test run: the
#                  tests are forced to run, never served from the build cache, so every
#                  module's test count is always reported, with the total)
#   4. boundary    no javafx.* reference under engine/ or cli/
#   5. corpus      tools/run_corpus.sh, once it exists (Phase 1): the golden .mc files through the CLI
# Prints one line per gate, the failing gate's output in full, and exits non-zero when any
# gate fails. Before Phase 0 lands there is no Gradle wrapper; the gate says so and passes,
# so a docs-only series can land on the empty repository.
# Every Gradle run writes build/ and .gradle/ in the tree it runs in, which is why the gate
# runs only in a lane's clone, never in the coordinator's main tree by an agent.
set -uo pipefail

cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "gate: no Gradle wrapper yet (Phase 0 has not landed); nothing to check"
  echo "gates: all passed"
  exit 0
fi

scratch="$(mktemp -d "${TMPDIR:-/tmp}/math-canvas-gates.XXXXXX")"
trap 'rm -rf "$scratch"' EXIT
failed=0

# report <name> <status> <detail> [<output-file>]
report() {
  local name="$1" status="$2" detail="$3" output="${4:-}"
  printf 'gate %-9s %s%s\n' "$name" "$status" "${detail:+ ($detail)}"
  if [ "$status" != "ok" ]; then
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
if ./gradlew --console=plain build test --rerun >"$scratch/build.txt" 2>&1; then
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
  report build FAILED "" "$scratch/build.txt"
fi

# 4. Module boundary --------------------------------------------------------------------
# engine and cli must not reference JavaFX; the Gradle build enforces this too, this is the
# belt to its braces and runs even when the build above failed.
: >"$scratch/boundary.txt"
for m in engine cli; do
  [ -d "$m/src" ] && grep -rnE '\bjavafx\.' "$m/src" --include='*.java' >>"$scratch/boundary.txt" || true
done
if [ ! -s "$scratch/boundary.txt" ]; then
  report boundary ok "no javafx.* in engine or cli"
else
  report boundary FAILED "$(wc -l <"$scratch/boundary.txt" | tr -d ' ') reference(s)" "$scratch/boundary.txt"
fi

# 5. Golden corpora through the CLI (Phase 1) -------------------------------------------
if [ -x tools/run_corpus.sh ]; then
  if tools/run_corpus.sh >"$scratch/corpus.txt" 2>&1; then
    report corpus ok "$(tail -1 "$scratch/corpus.txt")"
  else
    report corpus FAILED "" "$scratch/corpus.txt"
  fi
else
  report corpus ok "no corpus runner yet"
fi

if [ $failed -eq 0 ]; then
  echo "gates: all passed"
else
  echo "gates: FAILED"
fi
exit $failed
