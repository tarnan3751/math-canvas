#!/usr/bin/env bash
# Measures every number Phase 11 is judged by (PLAN.md section 7, Phase 11), each timing as the
# median of five runs with its spread (docs/reference/performance.md, which records the results):
#   1. startup       app's StartupBenchmarkTest through tools/ui_tests.sh, five JVMs: the window
#                    shown, the first cell ready and the first result of 1 + 1, cold and warm
#   2. cell latency  app's CellLatencyBenchmarkTest through tools/ui_tests.sh, once (the test
#                    itself runs its twenty inputs five times measured)
#   3. corpus        tools/run_corpus.sh, five times: the wall time and statements per second; then
#                    the CLI once over the same files with --stats, the rewriter's rule tries and
#                    rewrites and the ten rules tried most
#   4. canvas        canvas's CanvasBenchmarkTest and SpaceBenchmarkTest through tools/ui_tests.sh,
#                    five times each: the frame times they report
#   5. perf suite    ./gradlew :engine:perfTest --rerun, five times: the wall time of each class,
#                    read from its JUnit report
#   6. sizes         ./gradlew assemble jpackage once: the installer, the jlink image, each
#                    module's jar (of the version in gradle.properties only); the source lines of
#                    every module, main, test and resources (code lines by cloc when it is
#                    installed, else non-blank lines), the ten largest source files, and the keys
#                    of every .properties file the modules ship
#
#   tools/measure.sh [--force] [--record]
#
# It refuses to start when the one-minute load average is above 4, since a loaded machine measures
# the other load, unless --force is given; the load at the start and at the end is recorded either
# way. It writes the raw output of every run under build/measure/<UTC timestamp>/, the results as
# build/measure/<UTC timestamp>.json, and a Markdown table on standard output. --record also
# appends that table to docs/reference/performance.md under a heading with the commit, the date
# and the machine (CPU, cores, memory, OS, JDK). The UI runs take tools/ui_tests.sh's machine-wide
# lock, so the script waits while another lane holds it. It is not part of the gate: it is run by
# hand at phase boundaries, and takes twenty to thirty minutes, most of it in the UI runs (which
# pass -Pbenchmarks, the startup and cell-latency benchmarks being left out of an ordinary UI run)
# and the installer. It needs python3 (the
# aggregation) and writes build/ and .gradle/ in the tree it runs in, like any Gradle run, so it
# runs in a clone, never in the coordinator's main tree by an agent.
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/lib.sh

force=0
record=0
for arg in "$@"; do
  case "$arg" in
    --force) force=1 ;;
    --record) record=1 ;;
    *) echo "usage: tools/measure.sh [--force] [--record]" >&2; exit 2 ;;
  esac
done

command -v python3 >/dev/null || { echo "measure: needs python3" >&2; exit 2; }
if [ "$record" = 1 ] && [ ! -f docs/reference/performance.md ]; then
  echo "measure: --record needs docs/reference/performance.md" >&2
  exit 2
fi

load_start="$(load_average)"
one_minute="${load_start%% *}"
if [ "$force" = 0 ] && [ "$one_minute" != unknown ] \
  && python3 -I -c 'import sys; sys.exit(0 if float(sys.argv[1]) > 4 else 1)' "$one_minute"; then
  echo "measure: the load average is $load_start, above 4: the numbers would measure the other" \
    "load; run it on an idle machine, or with --force to measure anyway" >&2
  exit 2
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
raw="build/measure/$stamp"
mkdir -p "$raw"
runs=5
say() { echo "measure: $*" >&2; }
now() { python3 -I -c 'import time; print(time.monotonic())'; }

say "load average $load_start; raw output in $raw/"
say "building the CLI and the UI tests"
./gradlew -q --console=plain :cli:installDist :app:uiTestClasses :canvas:uiTestClasses \
  >"$raw/build.log" 2>&1 || { tail -50 "$raw/build.log" >&2; exit 1; }

# ui <pattern> <module> <report> <copy>: runs the matching UI test alone and copies its report
ui() {
  local log="$4.log"
  rm -f "$2/build/ui-screenshots/$3"
  if ! tools/ui_tests.sh --tests "$1" -Pbenchmarks >"$log" 2>&1 || [ ! -f "$2/build/ui-screenshots/$3" ]; then
    say "the UI test $1 failed:"
    tail -50 "$log" >&2
    exit 1
  fi
  cp "$2/build/ui-screenshots/$3" "$4"
}

for k in $(seq 1 $runs); do
  say "startup, run $k of $runs"
  ui '*StartupBenchmarkTest' app startup-benchmark.txt "$raw/startup-$k.txt"
done
say "cell latency"
ui '*CellLatencyBenchmarkTest' app cell-latency.txt "$raw/cell-latency.txt"

for k in $(seq 1 $runs); do
  say "corpus, run $k of $runs"
  start="$(now)"
  tools/run_corpus.sh >"$raw/corpus-$k.txt" 2>&1 || { tail -50 "$raw/corpus-$k.txt" >&2; exit 1; }
  end="$(now)"
  echo "wall $start $end" >>"$raw/corpus-$k.txt"
done
say "corpus statistics"
files=()
while IFS= read -r f; do
  files+=("$f")
done < <(find engine/src/test/corpus -name '*.mc' -type f | LC_ALL=C sort)
JAVA_HOME="$(cat cli/build/toolchain/java-home)" \
  cli/build/install/mathcanvas-cli/bin/mathcanvas-cli --check --unicode --stats "${files[@]}" \
  >"$raw/stats-out.txt" 2>"$raw/stats.txt" || { tail -50 "$raw/stats-out.txt" >&2; exit 1; }

for k in $(seq 1 $runs); do
  say "canvas, run $k of $runs"
  ui '*CanvasBenchmarkTest' canvas canvas-benchmark.txt "$raw/canvas-$k.txt"
  ui '*SpaceBenchmarkTest' canvas space-benchmark.txt "$raw/space-$k.txt"
done

for k in $(seq 1 $runs); do
  say "engine perf suite, run $k of $runs"
  ./gradlew -q --console=plain :engine:perfTest --rerun >"$raw/perf-$k.log" 2>&1 \
    || { tail -50 "$raw/perf-$k.log" >&2; exit 1; }
  mkdir -p "$raw/perf-$k"
  cp engine/build/test-results/perfTest/*.xml "$raw/perf-$k/"
done

say "sizes: assemble and jpackage"
./gradlew -q --console=plain assemble jpackage >"$raw/package.log" 2>&1 \
  || { tail -50 "$raw/package.log" >&2; exit 1; }

load_end="$(load_average)"
say "load average at the end $load_end"
commit="$(git rev-parse --short=10 HEAD)"
git diff --quiet HEAD -- . ':(exclude)docs/reference/performance.md' || commit="$commit (with changes)"

project_version="$(sed -n 's/^version=//p' gradle.properties)"

python3 -I - "$raw" "build/measure/$stamp.json" "$commit" "$load_start" "$load_end" "$runs" \
  "$(cat cli/build/toolchain/java-home)" "$project_version" >"$raw/table.md" <<'PY'
import json, os, re, subprocess, sys, xml.etree.ElementTree as ET
from datetime import datetime, timezone

raw, out_json, commit, load_start, load_end, runs, java_home, project_version = sys.argv[1:9]
runs = int(runs)
MODULES = ["engine", "render", "canvas", "app", "cli", "docs", "build-logic"]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def median(xs):
    s = sorted(xs)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


def stat(xs):
    return {"median": median(xs), "min": min(xs), "max": max(xs), "runs": xs}


def cmd(*args):
    try:
        return subprocess.run(args, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


# --- the machine
def machine():
    cpu = cmd("sysctl", "-n", "machdep.cpu.brand_string")
    cores = cmd("sysctl", "-n", "hw.ncpu")
    memory = cmd("sysctl", "-n", "hw.memsize")
    osname = ""
    if cmd("sw_vers", "-productVersion"):
        osname = "macOS " + cmd("sw_vers", "-productVersion")
    if not cpu and os.path.exists("/proc/cpuinfo"):
        m = re.search(r"model name\s*:\s*(.*)", read("/proc/cpuinfo"))
        cpu = m.group(1) if m else ""
    if not cores:
        cores = str(os.cpu_count() or "")
    if not memory and os.path.exists("/proc/meminfo"):
        m = re.search(r"MemTotal:\s*(\d+) kB", read("/proc/meminfo"))
        memory = str(int(m.group(1)) * 1024) if m else ""
    if not osname:
        osname = cmd("uname", "-sr")
    version = subprocess.run(
        [os.path.join(java_home, "bin", "java"), "-version"], capture_output=True, text=True
    ).stderr.splitlines()
    jdk = version[0] if version else "unknown"
    gb = f"{int(memory) / 2**30:.0f} GB" if memory else "unknown memory"
    return {"cpu": cpu or "unknown", "cores": cores or "unknown", "memory": gb, "os": osname, "jdk": jdk}


# --- 1. startup
NUM = r"([0-9]+(?:\.[0-9]+)?)"
startup_rows = {}
for k in range(1, runs + 1):
    for line in read(f"{raw}/startup-{k}.txt").splitlines():
        m = re.match(
            r"startup-benchmark: (.*?)\s+shown\s+" + NUM + r" ms\s+ready\s+" + NUM
            + r" ms\s+first result\s+" + NUM + " ms", line)
        if m:
            for name, v in zip(["window shown", "first cell ready", "first result of 1 + 1"],
                               m.groups()[1:]):
                startup_rows.setdefault(f"{m.group(1)}: {name}", []).append(float(v))
startup = {k: stat(v) for k, v in startup_rows.items()}

# --- 2. cell latency
cells = {}
for line in read(f"{raw}/cell-latency.txt").splitlines():
    m = re.match(r"cell-latency: (.*?)\s+first\s+" + NUM + r"\s+median\s+" + NUM + r"\s+min\s+"
                 + NUM + r"\s+max\s+" + NUM, line)
    if m:
        cells[m.group(1)] = {k: float(v) for k, v in
                             zip(["first", "median", "min", "max"], m.groups()[1:])}

# --- 3. corpus
walls, statements = [], None
for k in range(1, runs + 1):
    text = read(f"{raw}/corpus-{k}.txt")
    w = re.search(r"^wall ([0-9.]+) ([0-9.]+)$", text, re.M)
    walls.append(float(w.group(2)) - float(w.group(1)))
    s = re.search(r"^corpus: (\d+) files, (\d+) statements, (\d+) failures$", text, re.M)
    files, statements = int(s.group(1)), int(s.group(2))
stats_text = read(f"{raw}/stats.txt")
totals = re.search(r"^stats: (\d+) rule tries, (\d+) rewrites$", stats_text, re.M)
most = [
    {"tries": int(m.group(1)), "rewrites": int(m.group(2)), "rule": m.group(3)}
    for m in re.finditer(r"^stats:\s+(\d+) tries\s+(\d+) rewrites\s+(.*)$", stats_text, re.M)
]
corpus = {
    "files": files,
    "statements": statements,
    "wall_s": stat(walls),
    "statements_per_s": stat([statements / w for w in walls]),
    "rule_tries": int(totals.group(1)),
    "rewrites": int(totals.group(2)),
    "most_tried": most,
}

# --- 4. canvas
frames = {}
for kind in ["canvas", "space"]:
    for k in range(1, runs + 1):
        for line in read(f"{raw}/{kind}-{k}.txt").splitlines():
            m = re.match(kind + r"-benchmark: (.*?)\s+median\s+" + NUM + r" ms, p95\s+" + NUM
                         + " ms", line)
            if m:
                frames.setdefault(f"{kind}: {m.group(1)}, median", []).append(float(m.group(2)))
                frames.setdefault(f"{kind}: {m.group(1)}, p95", []).append(float(m.group(3)))
canvas = {k: stat(v) for k, v in frames.items()}

# --- 5. the engine's perf suite
classes = {}
for k in range(1, runs + 1):
    d = f"{raw}/perf-{k}"
    for name in sorted(os.listdir(d)):
        suite = ET.parse(os.path.join(d, name)).getroot()
        short = suite.get("name").rsplit(".", 1)[-1]
        classes.setdefault(short, []).append(float(suite.get("time")))
perf = {k: stat(v) for k, v in classes.items()}
perf_total = stat([sum(v[i] for v in classes.values()) for i in range(runs)])


# --- 6. sizes: this version's installer and jars, not those an older build left beside them
def size_of(path):
    if os.path.isfile(path):
        return os.path.getsize(path)
    total = 0
    for root, _, names in os.walk(path):
        for n in names:
            p = os.path.join(root, n)
            if not os.path.islink(p):
                total += os.path.getsize(p)
    return total


artefacts = {}
jp = "app/build/jpackage"
for n in sorted(os.listdir(jp)) if os.path.isdir(jp) else []:
    if n.endswith((".dmg", ".msi", ".deb")) and project_version in n:
        artefacts[f"installer {n}"] = size_of(os.path.join(jp, n))
if os.path.isdir("app/build/image"):
    artefacts["jlink image app/build/image"] = size_of("app/build/image")
for m in ["engine", "render", "canvas", "app", "cli"]:
    libs = f"{m}/build/libs"
    for n in sorted(os.listdir(libs)) if os.path.isdir(libs) else []:
        if n == f"{m}-{project_version}.jar":
            artefacts[f"jar {n}"] = size_of(os.path.join(libs, n))

cloc = bool(cmd("sh", "-c", "command -v cloc"))


def text_files(root, java_only):
    out = []
    for r, dirs, names in os.walk(root):
        dirs[:] = [d for d in dirs if d != "build"]
        for n in names:
            if java_only and not n.endswith((".java", ".kt", ".kts")):
                continue
            out.append(os.path.join(r, n))
    return sorted(out)


def lines(paths):
    if not paths:
        return 0
    if cloc:
        result = cmd("cloc", "--quiet", "--csv", "--sum-one", *paths)
        rows = [l.split(",") for l in result.splitlines() if l and l[0].isdigit()]
        return int(rows[-1][-1]) if rows else 0
    count = 0
    for p in paths:
        try:
            with open(p, encoding="utf-8") as f:
                count += sum(1 for l in f if l.strip())
        except (UnicodeDecodeError, OSError):
            pass  # a binary file (a font, an image) has no lines
    return count


sources = {}
everything = []
for m in MODULES + ["tools"]:
    if m == "tools":
        main, test, res = text_files("tools", False), [], []
    elif m == "build-logic":
        main, test, res = text_files("build-logic/src", True), [], []
    else:
        main = text_files(f"{m}/src/main/java", True)
        test = [p for s in ["test", "uiTest"] for p in text_files(f"{m}/src/{s}", False)]
        res = text_files(f"{m}/src/main/resources", False)
    sources[m] = {"main": lines(main), "test": lines(test), "resources": lines(res)}
    everything += [p for p in main if p.endswith(".java")]
    everything += [p for p in test if p.endswith(".java")]


def nonblank(p):
    with open(p, encoding="utf-8") as f:
        return sum(1 for l in f if l.strip())


largest = sorted(((nonblank(p), p) for p in everything), reverse=True)[:10]

bundles = {}
for m in MODULES:
    for p in text_files(f"{m}/src/main/resources", False):
        if p.endswith(".properties"):
            keys, continued = 0, False
            for l in read(p).splitlines():
                s = l.strip()
                if not continued and s and s[0] not in "#!":
                    keys += 1
                continued = l.endswith("\\") and not l.endswith("\\\\")
            bundles[p] = keys

when = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
result = {
    "commit": commit, "date": when, "machine": machine(),
    "load_average": {"start": load_start, "end": load_end},
    "startup_ms": startup, "cell_latency_ms": cells, "corpus": corpus,
    "canvas_ms": canvas, "engine_perf_s": perf, "engine_perf_total_s": perf_total,
    "artefact_bytes": artefacts, "source_lines": sources,
    "source_lines_counted_by": "cloc" if cloc else "non-blank lines",
    "largest_sources": [{"file": p, "lines": n} for n, p in largest],
    "bundle_keys": bundles,
}
with open(out_json, "w", encoding="utf-8") as f:
    json.dump(result, f, indent=2)
    f.write("\n")


# --- the table
def ms(x):
    if x < 10:
        return f"{x:.2f} ms"
    return f"{x:,.1f} ms" if x < 10_000 else f"{x / 1000:,.2f} s"


def size(b):
    return f"{b / 2**20:,.1f} MB" if b >= 2**20 else f"{b / 2**10:,.0f} KB"


def row(name, s, unit):
    return f"| {name} | {unit(s['median'])} | {unit(s['min'])} to {unit(s['max'])} |"


m = result["machine"]
md = [
    f"### {when[:10]} at `{commit}`",
    "",
    f"{m['cpu']}, {m['cores']} cores, {m['memory']}, {m['os']}, {m['jdk']}. Load average"
    f" {load_start} at the start, {load_end} at the end. Timings are the median of {runs} runs"
    " and their range.",
    "",
    "#### Startup",
    "",
    "| Moment | Median | Range |",
    "|---|---|---|",
    *[row(k, v, ms) for k, v in startup.items()],
    "",
    "#### Cell latency",
    "",
    "| Input | First | Median | Range |",
    "|---|---|---|---|",
    *[f"| {k} | {ms(v['first'])} | {ms(v['median'])} | {ms(v['min'])} to {ms(v['max'])} |"
      for k, v in cells.items()],
    "",
    "#### Corpus",
    "",
    "| Figure | Median | Range |",
    "|---|---|---|",
    row(f"`tools/run_corpus.sh` wall time ({files} files, {statements} statements)",
        corpus["wall_s"], lambda x: f"{x:.2f} s"),
    row("statements per second", corpus["statements_per_s"], lambda x: f"{x:,.0f}"),
    f"| rule tries (one `--stats` run) | {corpus['rule_tries']:,} | exact |",
    f"| rewrites (one `--stats` run) | {corpus['rewrites']:,} | exact |",
    "",
    "| Rule tried most | Tries | Rewrites |",
    "|---|---|---|",
    *[f"| `{e['rule']}` | {e['tries']:,} | {e['rewrites']:,} |" for e in most],
    "",
    "#### Canvas",
    "",
    "| Frame figure | Median | Range |",
    "|---|---|---|",
    *[row(k, v, lambda x: f"{x:.2f} ms") for k, v in canvas.items()],
    "",
    "#### Engine perf suite (`:engine:perfTest`)",
    "",
    "| Class | Median | Range |",
    "|---|---|---|",
    *[row(k, v, lambda x: f"{x:.2f} s") for k, v in perf.items()],
    row("**all classes**", perf_total, lambda x: f"{x:.2f} s"),
    "",
    "#### Sizes",
    "",
    "| Artefact | Size |",
    "|---|---|",
    *[f"| {k} | {size(v)} |" for k, v in artefacts.items()],
    "",
    f"Source lines ({result['source_lines_counted_by']}):",
    "",
    "| Module | Main | Test | Resources |",
    "|---|---|---|---|",
    *[f"| {k} | {v['main']:,} | {v['test']:,} | {v['resources']:,} |" for k, v in sources.items()],
    f"| **all** | {sum(v['main'] for v in sources.values()):,}"
    f" | {sum(v['test'] for v in sources.values()):,}"
    f" | {sum(v['resources'] for v in sources.values()):,} |",
    "",
    "| Largest source file | Non-blank lines |",
    "|---|---|",
    *[f"| `{p}` | {n:,} |" for n, p in largest],
    "",
    "| Bundle | Keys |",
    "|---|---|",
    *[f"| `{k}` | {v:,} |" for k, v in bundles.items()],
]
print("\n".join(md))
PY

cat "$raw/table.md"
if [ "$record" = 1 ]; then
  { echo; cat "$raw/table.md"; } >>docs/reference/performance.md
  say "recorded in docs/reference/performance.md"
fi
say "results in build/measure/$stamp.json"
