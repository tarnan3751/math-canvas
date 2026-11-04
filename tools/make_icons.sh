#!/usr/bin/env bash
# Rasterises the application icon, app/icon/math-canvas.svg, into the files the build packages
# (docs/reference/testing-tools.md, **Packaging**):
#
#   app/icon/math-canvas.png       512 x 512: the Linux package icon
#   app/icon/png/math-canvas-N.png N x N for N = 16, 32, 64, 128, 256: with the 512 one, the
#                                  window's icon list (app's processResources copies them in)
#   app/icon/math-canvas.icns      the macOS bundle icon, 16 to 1024 (512@2x)
#   app/icon/math-canvas.ico       the Windows icon: PNG entries of 16 to 256
#
#   tools/make_icons.sh
#
# The outputs are committed, like the bundled font, so the build never needs a rasteriser; run
# this after editing the SVG and commit what it writes. It runs on macOS: the SVG is drawn once at
# 1024 px by rsvg-convert when it is installed (Homebrew's librsvg), else by Quick Look
# (qlmanage), scaled down by sips, packed by iconutil into the .icns, and written into the .ico by
# Python 3's standard library (struct, no PIL). A missing tool stops it with its name.
set -euo pipefail

cd "$(dirname "$0")/.."
icon=app/icon
svg="$icon/math-canvas.svg"
sizes="16 32 64 128 256"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "make_icons.sh: $1 is required ($2)" >&2
    exit 1
  fi
}
need sips "macOS's image tool"
need iconutil "macOS's icon packer"
need python3 "writes the .ico"
if ! command -v rsvg-convert >/dev/null 2>&1; then
  need qlmanage "macOS's Quick Look, used when rsvg-convert is not installed"
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/make_icons.XXXXXX")"
trap 'rm -rf "$work"' EXIT

# The master: 1024 x 1024 with transparent corners.
if command -v rsvg-convert >/dev/null 2>&1; then
  rsvg-convert -w 1024 -h 1024 "$svg" -o "$work/master.png"
else
  qlmanage -t -s 1024 -o "$work" "$svg" >/dev/null 2>&1
  mv "$work/math-canvas.svg.png" "$work/master.png"
fi

scale() { # <size> <out>
  sips -s format png -z "$1" "$1" "$work/master.png" --out "$2" >/dev/null
}

mkdir -p "$icon/png"
scale 512 "$icon/math-canvas.png"
for n in $sizes; do
  scale "$n" "$icon/png/math-canvas-$n.png"
done

# The .icns: iconutil reads an .iconset directory of the sizes macOS names.
set_dir="$work/math-canvas.iconset"
mkdir "$set_dir"
for n in 16 32 128 256 512; do
  scale "$n" "$set_dir/icon_${n}x${n}.png"
  scale $((n * 2)) "$set_dir/icon_${n}x${n}@2x.png"
done
iconutil -c icns -o "$icon/math-canvas.icns" "$set_dir"

# The .ico: a header, one 16-byte directory entry per image (a width or height of 256 is
# written as 0), then the PNG files themselves.
python3 -I - "$icon/math-canvas.ico" $(for n in $sizes; do echo "$icon/png/math-canvas-$n.png"; done) <<'PY'
import struct
import sys

out, pngs = sys.argv[1], sys.argv[2:]
images = [open(path, "rb").read() for path in pngs]
offset = 6 + 16 * len(images)
header = struct.pack("<HHH", 0, 1, len(images))
entries = b""
for data in images:
    width, height = struct.unpack(">II", data[16:24])
    entries += struct.pack(
        "<BBBBHHII", width % 256, height % 256, 0, 0, 1, 32, len(data), offset)
    offset += len(data)
with open(out, "wb") as f:
    f.write(header + entries + b"".join(images))
PY

echo "make_icons.sh: wrote $icon/math-canvas.png, $icon/png/, $icon/math-canvas.icns and $icon/math-canvas.ico"
