package mathcanvas.render.font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A TrueType-flavoured OpenType font read from its bytes: {@code head}, {@code maxp}, {@code hhea},
 * {@code hmtx}, {@code OS/2}, the (3,10) format 12 character map, {@code loca} and {@code glyf}
 * (simple and composite glyphs), {@code MATH}, and the {@code ssty} alternates of {@code GSUB}.
 * Immutable once read, its fields final and its tables unmodifiable, so it is safely shared between
 * threads; outlines are parsed on first use into a concurrent cache, from any thread.
 */
public final class OpenTypeFont {

  private static final String STIX = "/mathcanvas/render/fonts/STIXTwoMath-Regular.ttf";

  private static final int ON_CURVE = 0x01;
  private static final int X_SHORT = 0x02;
  private static final int Y_SHORT = 0x04;
  private static final int REPEAT = 0x08;
  private static final int X_SAME_OR_POSITIVE = 0x10;
  private static final int Y_SAME_OR_POSITIVE = 0x20;

  private static final int ARGS_ARE_WORDS = 0x0001;
  private static final int ARGS_ARE_XY_VALUES = 0x0002;
  private static final int HAS_SCALE = 0x0008;
  private static final int MORE_COMPONENTS = 0x0020;
  private static final int HAS_XY_SCALE = 0x0040;
  private static final int HAS_TWO_BY_TWO = 0x0080;

  private final Bytes b;
  private final int unitsPerEm;
  private final int glyphCount;
  private final int ascender;
  private final int descender;
  private final int xHeight;
  private final int[] advances;
  private final int[] cmapStart;
  private final int[] cmapEnd;
  private final int[] cmapGlyph;
  private final int[] locations;
  private final int glyf;
  private final MathTable math;
  private final Map<Integer, int[]> scriptAlternates;
  private final Map<Integer, Outline> outlines = new ConcurrentHashMap<>();

  private OpenTypeFont(byte[] data) {
    b = new Bytes(data);
    Map<String, Integer> tables = new HashMap<>();
    int count = b.u16(4);
    for (int k = 0; k < count; k++) {
      int record = 12 + 16 * k;
      tables.put(b.tag(record), b.offset32(record + 8));
    }
    int head = table(tables, "head");
    unitsPerEm = b.u16(head + 18);
    boolean longOffsets = b.s16(head + 50) == 1;
    glyphCount = b.u16(table(tables, "maxp") + 4);
    int hhea = table(tables, "hhea");
    ascender = b.s16(hhea + 4);
    descender = b.s16(hhea + 6);
    int metrics = b.u16(hhea + 34);
    int os2 = table(tables, "OS/2");
    xHeight = b.u16(os2) >= 2 ? b.s16(os2 + 86) : 0;
    int hmtx = table(tables, "hmtx");
    advances = new int[glyphCount];
    for (int g = 0; g < glyphCount; g++) {
      advances[g] = b.u16(hmtx + 4 * Math.min(g, metrics - 1));
    }
    int loca = table(tables, "loca");
    locations = new int[glyphCount + 1];
    for (int g = 0; g <= glyphCount; g++) {
      locations[g] = longOffsets ? b.offset32(loca + 4 * g) : 2 * b.u16(loca + 2 * g);
    }
    glyf = table(tables, "glyf");
    int cmap = cmap(table(tables, "cmap"));
    int groups = b.offset32(cmap + 12);
    cmapStart = new int[groups];
    cmapEnd = new int[groups];
    cmapGlyph = new int[groups];
    for (int k = 0; k < groups; k++) {
      int group = cmap + 16 + 12 * k;
      cmapStart[k] = b.offset32(group);
      cmapEnd[k] = b.offset32(group + 4);
      cmapGlyph[k] = b.offset32(group + 8);
    }
    math = MathTable.read(b, table(tables, "MATH"));
    scriptAlternates = Map.copyOf(Gsub.alternates(b, table(tables, "GSUB"), "ssty"));
  }

  /**
   * Reads a font.
   *
   * @param data the font file
   * @return the font
   * @throws IllegalArgumentException if a table this reader needs is missing or malformed
   */
  public static OpenTypeFont read(byte[] data) {
    try {
      return new OpenTypeFont(data.clone());
    } catch (IndexOutOfBoundsException e) {
      throw new IllegalArgumentException("a malformed font", e);
    }
  }

  /**
   * Returns the bundled STIX Two Math, read once.
   *
   * @return the font
   */
  public static OpenTypeFont stix() {
    return Stix.FONT;
  }

  /**
   * Returns the bytes of the bundled STIX Two Math as read for {@link #stix()}, so that whoever
   * loads the same font elsewhere (JavaFX, for text in the math font) does not read it again.
   *
   * @return a stream over the font file
   */
  public static InputStream stixFile() {
    return new ByteArrayInputStream(Stix.DATA);
  }

  private static final class Stix {
    static final byte[] DATA = read();
    static final OpenTypeFont FONT = new OpenTypeFont(DATA);

    private Stix() {}

    private static byte[] read() {
      try (InputStream in = OpenTypeFont.class.getResourceAsStream(STIX)) {
        if (in == null) {
          throw new IllegalStateException("the math font is missing: " + STIX);
        }
        return in.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static int table(Map<String, Integer> tables, String tag) {
    Integer at = tables.get(tag);
    if (at == null) {
      throw new IllegalArgumentException("no " + tag + " table");
    }
    return at;
  }

  /** The (3,10) format 12 subtable: the full Unicode map. */
  private int cmap(int at) {
    for (int k = 0; k < b.u16(at + 2); k++) {
      int record = at + 4 + 8 * k;
      if (b.u16(record) == 3 && b.u16(record + 2) == 10) {
        int sub = at + b.offset32(record + 4);
        if (b.u16(sub) == 12) {
          return sub;
        }
      }
    }
    throw new IllegalArgumentException("no (3,10) format 12 character map");
  }

  /**
   * Returns the font's units per em.
   *
   * @return the units
   */
  public int unitsPerEm() {
    return unitsPerEm;
  }

  /**
   * Returns the number of glyphs.
   *
   * @return the count
   */
  public int glyphCount() {
    return glyphCount;
  }

  /**
   * Returns the ascender of {@code hhea}.
   *
   * @return font units
   */
  public int ascender() {
    return ascender;
  }

  /**
   * Returns the descender of {@code hhea}, negative below the baseline.
   *
   * @return font units
   */
  public int descender() {
    return descender;
  }

  /**
   * Returns the x-height of {@code OS/2}.
   *
   * @return font units
   */
  public int xHeight() {
    return xHeight;
  }

  /**
   * Returns the glyph of a character.
   *
   * @param codePoint the character
   * @return the glyph, or 0 (the missing glyph) when the font has none
   */
  public int glyph(int codePoint) {
    int lo = 0;
    int hi = cmapStart.length - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      if (codePoint < cmapStart[mid]) {
        hi = mid - 1;
      } else if (codePoint > cmapEnd[mid]) {
        lo = mid + 1;
      } else {
        return cmapGlyph[mid] + codePoint - cmapStart[mid];
      }
    }
    return 0;
  }

  /**
   * Returns a glyph's advance width.
   *
   * @param glyph the glyph
   * @return font units
   */
  public int advance(int glyph) {
    return advances[glyph];
  }

  /**
   * Returns a glyph's bounding box as {@code glyf} records it.
   *
   * @param glyph the glyph
   * @return the box, all zero for a glyph without an outline
   */
  public Bounds bounds(int glyph) {
    int at = locations[glyph];
    if (locations[glyph + 1] == at) {
      return new Bounds(0, 0, 0, 0);
    }
    int g = glyf + at;
    return new Bounds(b.s16(g + 2), b.s16(g + 4), b.s16(g + 6), b.s16(g + 8));
  }

  /**
   * A bounding box in font units, y up.
   *
   * @param xMin the left
   * @param yMin the bottom
   * @param xMax the right
   * @param yMax the top
   */
  public record Bounds(int xMin, int yMin, int xMax, int yMax) {}

  /**
   * Returns a glyph's outline.
   *
   * @param glyph the glyph
   * @return the outline, empty for a glyph without one
   */
  public Outline outline(int glyph) {
    return outlines.computeIfAbsent(glyph, g -> new Outline(commands(g, 0)));
  }

  /**
   * Returns the MATH table.
   *
   * @return the table
   */
  public MathTable math() {
    return math;
  }

  /**
   * Returns a glyph's form for a script level, through the {@code ssty} alternates: level 1 is the
   * script style, level 2 the scriptscript style, each falling back to the nearest one the font
   * has.
   *
   * @param glyph the glyph
   * @param level 0, 1 or 2
   * @return the glyph to draw
   */
  public int scriptGlyph(int glyph, int level) {
    int[] alternates = scriptAlternates.get(glyph);
    if (level <= 0 || alternates == null || alternates.length == 0) {
      return glyph;
    }
    return alternates[Math.min(level, alternates.length) - 1];
  }

  // Outlines.

  private List<Outline.Command> commands(int glyph, int depth) {
    if (depth > 8) {
      throw new IllegalArgumentException("composite glyphs nested too deeply at " + glyph);
    }
    int at = locations[glyph];
    if (locations[glyph + 1] == at) {
      return List.of();
    }
    int g = glyf + at;
    int contours = b.s16(g);
    return contours >= 0 ? simple(g, contours) : composite(g, depth);
  }

  private List<Outline.Command> simple(int g, int contours) {
    int[] ends = new int[contours];
    for (int k = 0; k < contours; k++) {
      ends[k] = b.u16(g + 10 + 2 * k);
    }
    int points = contours == 0 ? 0 : ends[contours - 1] + 1;
    int at = g + 10 + 2 * contours;
    at += 2 + b.u16(at);
    int[] flags = new int[points];
    for (int k = 0; k < points; ) {
      int flag = b.u8(at++);
      flags[k++] = flag;
      if ((flag & REPEAT) != 0) {
        int repeats = b.u8(at++);
        for (int r = 0; r < repeats; r++) {
          flags[k++] = flag;
        }
      }
    }
    int[] xs = new int[points];
    int[] ys = new int[points];
    int x = 0;
    for (int k = 0; k < points; k++) {
      int flag = flags[k];
      if ((flag & X_SHORT) != 0) {
        int dx = b.u8(at++);
        x += (flag & X_SAME_OR_POSITIVE) != 0 ? dx : -dx;
      } else if ((flag & X_SAME_OR_POSITIVE) == 0) {
        x += b.s16(at);
        at += 2;
      }
      xs[k] = x;
    }
    int y = 0;
    for (int k = 0; k < points; k++) {
      int flag = flags[k];
      if ((flag & Y_SHORT) != 0) {
        int dy = b.u8(at++);
        y += (flag & Y_SAME_OR_POSITIVE) != 0 ? dy : -dy;
      } else if ((flag & Y_SAME_OR_POSITIVE) == 0) {
        y += b.s16(at);
        at += 2;
      }
      ys[k] = y;
    }
    List<Outline.Command> out = new ArrayList<>();
    int first = 0;
    for (int end : ends) {
      contour(xs, ys, flags, first, end, out);
      first = end + 1;
    }
    return out;
  }

  /**
   * One contour's points to commands: an off-curve point between two on-curve ones is a control
   * point; two off-curve points in a row imply the on-curve point midway.
   */
  private static void contour(
      int[] xs, int[] ys, int[] flags, int first, int last, List<Outline.Command> out) {
    int n = last - first + 1;
    if (n < 1) {
      return;
    }
    // Start at an on-curve point: the first, else the last, else the midpoint of the two.
    int start;
    double sx;
    double sy;
    if ((flags[first] & ON_CURVE) != 0) {
      start = 0;
      sx = xs[first];
      sy = ys[first];
    } else if ((flags[last] & ON_CURVE) != 0) {
      start = n - 1;
      sx = xs[last];
      sy = ys[last];
    } else {
      start = -1;
      sx = (xs[first] + xs[last]) / 2.0;
      sy = (ys[first] + ys[last]) / 2.0;
    }
    out.add(new Outline.MoveTo(sx, sy));
    boolean pending = false;
    double cx = 0;
    double cy = 0;
    // Walk the n points after the start, wrapping round, and end back at the start.
    int steps = start < 0 ? n : n - 1;
    for (int s = 1; s <= steps; s++) {
      int k = first + Math.floorMod(start + s, n);
      double px = xs[k];
      double py = ys[k];
      if ((flags[k] & ON_CURVE) != 0) {
        if (pending) {
          out.add(new Outline.QuadTo(cx, cy, px, py));
          pending = false;
        } else {
          out.add(new Outline.LineTo(px, py));
        }
      } else {
        if (pending) {
          double mx = (cx + px) / 2;
          double my = (cy + py) / 2;
          out.add(new Outline.QuadTo(cx, cy, mx, my));
        }
        cx = px;
        cy = py;
        pending = true;
      }
    }
    if (pending) {
      out.add(new Outline.QuadTo(cx, cy, sx, sy));
    }
    out.add(new Outline.Close());
  }

  private List<Outline.Command> composite(int g, int depth) {
    List<Outline.Command> out = new ArrayList<>();
    int at = g + 10;
    int flags;
    do {
      flags = b.u16(at);
      int component = b.u16(at + 2);
      at += 4;
      if ((flags & ARGS_ARE_XY_VALUES) == 0) {
        throw new IllegalArgumentException("a composite glyph placed by point matching");
      }
      double dx;
      double dy;
      if ((flags & ARGS_ARE_WORDS) != 0) {
        dx = b.s16(at);
        dy = b.s16(at + 2);
        at += 4;
      } else {
        dx = b.s8(at);
        dy = b.s8(at + 1);
        at += 2;
      }
      double a = 1;
      double bb = 0;
      double c = 0;
      double d = 1;
      if ((flags & HAS_SCALE) != 0) {
        a = d = b.f2dot14(at);
        at += 2;
      } else if ((flags & HAS_XY_SCALE) != 0) {
        a = b.f2dot14(at);
        d = b.f2dot14(at + 2);
        at += 4;
      } else if ((flags & HAS_TWO_BY_TWO) != 0) {
        a = b.f2dot14(at);
        bb = b.f2dot14(at + 2);
        c = b.f2dot14(at + 4);
        d = b.f2dot14(at + 6);
        at += 8;
      }
      for (Outline.Command command : commands(component, depth + 1)) {
        out.add(transform(command, a, bb, c, d, dx, dy));
      }
    } while ((flags & MORE_COMPONENTS) != 0);
    return out;
  }

  private static Outline.Command transform(
      Outline.Command command, double a, double b, double c, double d, double dx, double dy) {
    return switch (command) {
      case Outline.MoveTo m ->
          new Outline.MoveTo(a * m.x() + c * m.y() + dx, b * m.x() + d * m.y() + dy);
      case Outline.LineTo l ->
          new Outline.LineTo(a * l.x() + c * l.y() + dx, b * l.x() + d * l.y() + dy);
      case Outline.QuadTo q ->
          new Outline.QuadTo(
              a * q.cx() + c * q.cy() + dx,
              b * q.cx() + d * q.cy() + dy,
              a * q.x() + c * q.y() + dx,
              b * q.x() + d * q.y() + dy);
      case Outline.Close close -> close;
    };
  }
}
