#!/usr/bin/env bash
# The golden corpora through the CLI (docs/reference/cli.md, **The corpus**).
#   tools/run_corpus.sh [file.mc ...]
# Builds the CLI once (./gradlew -q :cli:installDist), then checks the files given, or every
# *.mc file under engine/src/test/corpus/, sorted, with `mathcanvas-cli --check --unicode`. Prints
# `ok <file> (<n> statements)` per passing file, `FAILED <file> (…)` and a unified diff of the
# expected and actual expectation lines per failing one, and last
# `corpus: <files> files, <statements> statements, <failures> failures`; exits 1 on any failure.
# The installed CLI runs on the build's Java toolchain, whatever JDK is on the PATH: installDist
# writes its home to cli/build/toolchain/java-home. The tool writes build/ and .gradle/ in the tree
# it runs in, like any Gradle run.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
caller="$PWD"
cd "$root"

if [ $# -gt 0 ]; then
  files=()
  for f in "$@"; do
    case "$f" in
      /*) files+=("$f") ;;
      *) files+=("$caller/$f") ;;
    esac
  done
else
  files=()
  while IFS= read -r f; do
    files+=("$f")
  done < <(find engine/src/test/corpus -name '*.mc' -type f | LC_ALL=C sort)
fi
if [ ${#files[@]} -eq 0 ]; then
  echo "corpus: no .mc files"
  exit 1
fi

./gradlew -q --console=plain :cli:installDist
JAVA_HOME="$(cat cli/build/toolchain/java-home)"
export JAVA_HOME
exec cli/build/install/mathcanvas-cli/bin/mathcanvas-cli --check --unicode "${files[@]}"
