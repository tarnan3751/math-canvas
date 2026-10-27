package mathcanvas.render.typeset;

import static mathcanvas.render.font.MathConstant.AXIS_HEIGHT;
import static mathcanvas.render.font.MathConstant.DELIMITED_SUB_FORMULA_MIN_HEIGHT;
import static mathcanvas.render.font.MathConstant.DISPLAY_OPERATOR_MIN_HEIGHT;
import static mathcanvas.render.font.MathConstant.FRACTION_DENOMINATOR_DISPLAY_STYLE_SHIFT_DOWN;
import static mathcanvas.render.font.MathConstant.FRACTION_DENOMINATOR_GAP_MIN;
import static mathcanvas.render.font.MathConstant.FRACTION_DENOMINATOR_SHIFT_DOWN;
import static mathcanvas.render.font.MathConstant.FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN;
import static mathcanvas.render.font.MathConstant.FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP;
import static mathcanvas.render.font.MathConstant.FRACTION_NUMERATOR_GAP_MIN;
import static mathcanvas.render.font.MathConstant.FRACTION_NUMERATOR_SHIFT_UP;
import static mathcanvas.render.font.MathConstant.FRACTION_NUM_DISPLAY_STYLE_GAP_MIN;
import static mathcanvas.render.font.MathConstant.FRACTION_RULE_THICKNESS;
import static mathcanvas.render.font.MathConstant.LOWER_LIMIT_BASELINE_DROP_MIN;
import static mathcanvas.render.font.MathConstant.LOWER_LIMIT_GAP_MIN;
import static mathcanvas.render.font.MathConstant.RADICAL_DEGREE_BOTTOM_RAISE_PERCENT;
import static mathcanvas.render.font.MathConstant.RADICAL_DISPLAY_STYLE_VERTICAL_GAP;
import static mathcanvas.render.font.MathConstant.RADICAL_EXTRA_ASCENDER;
import static mathcanvas.render.font.MathConstant.RADICAL_KERN_AFTER_DEGREE;
import static mathcanvas.render.font.MathConstant.RADICAL_KERN_BEFORE_DEGREE;
import static mathcanvas.render.font.MathConstant.RADICAL_RULE_THICKNESS;
import static mathcanvas.render.font.MathConstant.RADICAL_VERTICAL_GAP;
import static mathcanvas.render.font.MathConstant.SCRIPT_PERCENT_SCALE_DOWN;
import static mathcanvas.render.font.MathConstant.SCRIPT_SCRIPT_PERCENT_SCALE_DOWN;
import static mathcanvas.render.font.MathConstant.SPACE_AFTER_SCRIPT;
import static mathcanvas.render.font.MathConstant.SUBSCRIPT_BASELINE_DROP_MIN;
import static mathcanvas.render.font.MathConstant.SUBSCRIPT_SHIFT_DOWN;
import static mathcanvas.render.font.MathConstant.SUBSCRIPT_TOP_MAX;
import static mathcanvas.render.font.MathConstant.SUB_SUPERSCRIPT_GAP_MIN;
import static mathcanvas.render.font.MathConstant.SUPERSCRIPT_BASELINE_DROP_MAX;
import static mathcanvas.render.font.MathConstant.SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT;
import static mathcanvas.render.font.MathConstant.SUPERSCRIPT_BOTTOM_MIN;
import static mathcanvas.render.font.MathConstant.SUPERSCRIPT_SHIFT_UP;
import static mathcanvas.render.font.MathConstant.SUPERSCRIPT_SHIFT_UP_CRAMPED;
import static mathcanvas.render.font.MathConstant.UPPER_LIMIT_BASELINE_RISE_MIN;
import static mathcanvas.render.font.MathConstant.UPPER_LIMIT_GAP_MIN;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import mathcanvas.engine.print.layout.BigOp;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Delimited;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Glyph;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.print.layout.Matrix;
import mathcanvas.engine.print.layout.Piecewise;
import mathcanvas.engine.print.layout.Radical;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Scripts;
import mathcanvas.engine.print.layout.Space;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.print.layout.Text;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.font.GlyphConstruction;
import mathcanvas.render.font.MathConstant;
import mathcanvas.render.font.OpenTypeFont;

/**
 * Typesets a layout tree into a box tree: MathML Core §3 over the font's MATH constants, with TeX's
 * rules (The TeXbook, Appendix G) where Core is silent: the four styles and cramping, the
 * inter-atom spacing table, delimiter sizing with TeX's factor and shortfall, matrix struts. A
 * construct the font cannot set (a character it lacks) is measured by the {@link Fallback}; nothing
 * throws for want of a glyph. Immutable and safe to share.
 */
public final class Typesetter {

  /** Space on each side of a fraction, in ems: TeX's {@code \nulldelimiterspace}, 1.2pt at 10pt. */
  public static final double FRACTION_PADDING = 0.12;

  /** The space between matrix columns and between piecewise columns, in ems ({@code \quad}). */
  public static final double COLUMN_GAP = 1.0;

  /** A matrix row's least height, in ems: TeX's array strut, 0.7 of a 12pt baseline at 10pt. */
  public static final double STRUT_HEIGHT = 0.7 * 1.2;

  /** A matrix row's least depth, in ems: 0.3 of a 12pt baseline at 10pt. */
  public static final double STRUT_DEPTH = 0.3 * 1.2;

  /** TeX's {@code \delimiterfactor}: a stretched fence covers at least this part, per mille. */
  public static final int DELIMITER_FACTOR = 901;

  /**
   * TeX's {@code \delimitershortfall}, 5pt at 10pt: a fence falls short by at most this, in ems.
   */
  public static final double DELIMITER_SHORTFALL = 0.5;

  /**
   * The spacing table of The TeXbook, ch. 18, by left and right class in {@link Cls} order: 0 none,
   * 1 thin, 2 medium, 3 thick; a negative entry applies in display and text styles only.
   */
  private static final int[][] SPACING = {
    {0, 1, -2, -3, 0, 0, 0, -1},
    {1, 1, 0, -3, 0, 0, 0, -1},
    {-2, -2, 0, 0, -2, 0, 0, -2},
    {-3, -3, 0, 0, -3, 0, 0, -3},
    {0, 0, 0, 0, 0, 0, 0, 0},
    {0, 1, -2, -3, 0, 0, 0, -1},
    {-1, -1, 0, -1, -1, -1, -1, -1},
    {-1, 1, -2, -3, -1, 0, -1, -1}
  };

  private final OpenTypeFont font;
  private final Fallback fallback;
  private final double upem;
  private final String otherwise;

  /**
   * Creates a typesetter.
   *
   * @param font the math font
   * @param fallback measures text the font lacks
   */
  public Typesetter(OpenTypeFont font, Fallback fallback) {
    this.font = Objects.requireNonNull(font);
    this.fallback = Objects.requireNonNull(fallback);
    this.upem = font.unitsPerEm();
    this.otherwise =
        ResourceBundle.getBundle("mathcanvas.render.typeset.messages")
            .getString("piecewise.otherwise");
  }

  /**
   * Typesets a layout.
   *
   * @param layout the layout
   * @param size the font size in pixels: one em
   * @param display whether in display style (big operators large, limits above and below, fractions
   *     full size) rather than text style
   * @return the root box, its baseline at y = 0 and its left edge at x = 0
   */
  public Box typeset(Layout layout, double size, boolean display) {
    if (!(size > 0)) {
      throw new IllegalArgumentException("size " + size);
    }
    N root = layout(layout, new St(display ? 0 : 1, false, size));
    return place(root, 0, 0);
  }

  // The style: level 0 display, 1 text, 2 script, 3 scriptscript.

  private record St(int level, boolean cramped, double base) {

    boolean display() {
      return level == 0;
    }

    St sup() {
      return new St(level < 2 ? 2 : 3, cramped, base);
    }

    St sub() {
      return new St(level < 2 ? 2 : 3, true, base);
    }

    St num() {
      return new St(level == 0 ? 1 : level == 1 ? 2 : 3, cramped, base);
    }

    St den() {
      return new St(level == 0 ? 1 : level == 1 ? 2 : 3, true, base);
    }

    St cramp() {
      return new St(level, true, base);
    }

    St text() {
      return new St(Math.max(level, 1), cramped, base);
    }

    St scriptScript() {
      return new St(3, cramped, base);
    }
  }

  /** A style's font size in pixels: the base size scaled down in the script styles. */
  private double size(St st) {
    return switch (st.level()) {
      case 0, 1 -> st.base();
      case 2 -> st.base() * font.math().constants().get(SCRIPT_PERCENT_SCALE_DOWN) / 100;
      default -> st.base() * font.math().constants().get(SCRIPT_SCRIPT_PERCENT_SCALE_DOWN) / 100;
    };
  }

  /** A font constant in pixels at a style's size. */
  private double c(MathConstant constant, St st) {
    return font.math().constants().get(constant) * size(st) / upem;
  }

  // Relative boxes: a node's children are placed at offsets from its origin, dy down.

  private sealed interface Ink permits GlyphInk, RuleInk, TextInk {}

  private record GlyphInk(int glyph, double scale) implements Ink {}

  private record RuleInk() implements Ink {}

  private record TextInk(String text, double size) implements Ink {}

  private record Placed(N node, double dx, double dy) {}

  private record N(
      double width,
      double height,
      double depth,
      double italic,
      boolean slanted,
      Path path,
      Ink ink,
      List<Placed> kids) {

    static N group(List<Placed> kids, double width, Path path) {
      double height = 0;
      double depth = 0;
      for (Placed k : kids) {
        height = Math.max(height, k.node().height() - k.dy());
        depth = Math.max(depth, k.node().depth() + k.dy());
      }
      return new N(width, height, depth, 0, false, path, null, kids);
    }

    N withPath(Path p) {
      return new N(width, height, depth, italic, slanted, p, ink, kids);
    }

    N withItalic(double i) {
      return new N(width, height, depth, i, slanted, path, ink, kids);
    }

    N slanted(boolean s) {
      return new N(width, height, depth, italic, s, path, ink, kids);
    }
  }

  private static N empty() {
    return new N(0, 0, 0, 0, false, null, null, List.of());
  }

  private static N rule(double width, double height, double depth) {
    return new N(width, height, depth, 0, false, null, new RuleInk(), List.of());
  }

  private Box place(N n, double x, double baseline) {
    return switch (n.ink()) {
      case GlyphInk g ->
          new Box.GlyphBox(
              x, baseline, n.width(), n.height(), n.depth(), n.path(), g.glyph(), g.scale());
      case RuleInk r -> new Box.RuleBox(x, baseline, n.width(), n.height(), n.depth(), n.path());
      case TextInk t ->
          new Box.TextBox(
              x, baseline, n.width(), n.height(), n.depth(), n.path(), t.text(), t.size());
      case null -> {
        List<Box> children = new ArrayList<>();
        double left = x;
        double right = x + n.width();
        double top = baseline - n.height();
        double bottom = baseline + n.depth();
        for (Placed k : n.kids()) {
          Box child = place(k.node(), x + k.dx(), baseline + k.dy());
          children.add(child);
          left = Math.min(left, child.x());
          right = Math.max(right, child.x() + child.width());
          top = Math.min(top, child.top());
          bottom = Math.max(bottom, child.bottom());
        }
        yield new Box.Group(
            left, baseline, right - left, baseline - top, bottom - baseline, n.path(), children);
      }
    };
  }

  // Dispatch.

  private N layout(Layout layout, St st) {
    return switch (layout) {
      case Row r -> row(r, st);
      case Glyph g -> glyph(g, st);
      case Text t -> text(t.text(), t.style() == Style.ITALIC, st, t.path());
      case Space s -> new N(space(s, st), 0, 0, 0, false, null, null, List.of());
      case Fraction f -> fraction(f, st);
      case Scripts s -> scripts(s, st);
      case Radical r -> radical(r, st);
      case Delimited d -> delimited(d, st);
      case Matrix m -> matrix(m, st);
      case BigOp b -> bigOp(b, st);
      case Piecewise p -> piecewise(p, st);
    };
  }

  private double space(Space s, St st) {
    return s.kind() == Space.Kind.THIN ? 3 * size(st) / 18 : 0;
  }

  // Characters.

  /** The character a typesetter draws for a layout character. */
  static int mathCodePoint(int c, Style style) {
    if (c == '-') {
      return 0x2212;
    }
    if (c == '·') {
      return 0x22C5;
    }
    if (style != Style.ITALIC) {
      return c;
    }
    if (c == 'h') {
      return 0x210E;
    }
    if (c >= 'a' && c <= 'z') {
      return 0x1D44E + c - 'a';
    }
    if (c >= 'A' && c <= 'Z') {
      return 0x1D434 + c - 'A';
    }
    if (c >= 'α' && c <= 'ω') {
      return 0x1D6FC + c - 'α';
    }
    return c;
  }

  private N glyph(Glyph g, St st) {
    int cp = mathCodePoint(g.codePoint(), g.style());
    int glyph = font.glyph(cp);
    if (glyph == 0) {
      return fallback(Character.toString(g.codePoint()), st, g.path());
    }
    return glyphNode(font.scriptGlyph(glyph, st.level() - 1), size(st), g.path())
        .slanted(g.style() == Style.ITALIC);
  }

  private N glyphNode(int glyph, double size, Path path) {
    double scale = size / upem;
    OpenTypeFont.Bounds b = font.bounds(glyph);
    return new N(
        font.advance(glyph) * scale,
        Math.max(0, b.yMax()) * scale,
        Math.max(0, -b.yMin()) * scale,
        font.math().italicsCorrection(glyph) * scale,
        false,
        path,
        new GlyphInk(glyph, scale),
        List.of());
  }

  private N text(String text, boolean italic, St st, Path path) {
    List<Placed> kids = new ArrayList<>();
    double x = 0;
    for (int k = 0; k < text.length(); ) {
      int c = text.codePointAt(k);
      k += Character.charCount(c);
      int glyph = font.glyph(mathCodePoint(c, italic ? Style.ITALIC : Style.UPRIGHT));
      if (glyph == 0) {
        return fallback(text, st, path);
      }
      N n = glyphNode(font.scriptGlyph(glyph, st.level() - 1), size(st), null);
      kids.add(new Placed(n, x, 0));
      x += n.width();
    }
    N last = kids.isEmpty() ? null : kids.getLast().node();
    N group = N.group(kids, x, path);
    return italic && last != null ? group.withItalic(last.italic()).slanted(true) : group;
  }

  private N fallback(String text, St st, Path path) {
    Fallback.Metrics m = fallback.measure(text, size(st));
    return new N(
        m.width(), m.ascent(), m.descent(), 0, false, path, new TextInk(text, size(st)), List.of());
  }

  // Rows: atoms spaced by class, nested rows spaced as part of the enclosing row.

  private sealed interface Token permits Atom, Gap, Linear, Open, Close {}

  /** An atom, with its class as its left and as its right neighbour sees it. */
  private record Atom(N node, Cls left, Cls right, Layout source) implements Token {}

  private record Gap(double width) implements Token {}

  /** A space of the linear text, whose width depends on its neighbours. */
  private record Linear() implements Token {}

  private record Open(Path path) implements Token {}

  private record Close() implements Token {}

  private void tokens(Layout layout, St st, List<Token> out) {
    switch (layout) {
      case Row r -> {
        out.add(new Open(r.path()));
        for (Layout item : r.items()) {
          tokens(item, st, out);
        }
        out.add(new Close());
      }
      case Space s -> {
        if (s.kind() == Space.Kind.LINEAR) {
          out.add(new Linear());
        } else {
          out.add(new Gap(space(s, st)));
        }
      }
      default -> out.add(new Atom(layout(layout, st), left(layout), right(layout), layout));
    }
  }

  private N row(Row row, St st) {
    List<Token> tokens = new ArrayList<>();
    tokens(row, st, tokens);
    linearSpaces(tokens, st);
    List<Atom> atoms = new ArrayList<>();
    for (Token t : tokens) {
      if (t instanceof Atom a) {
        atoms.add(a);
      }
    }
    Cls[] left = new Cls[atoms.size()];
    Cls[] right = new Cls[atoms.size()];
    classes(atoms, left, right);
    // Positions.
    double[] xs = new double[atoms.size()];
    double x = 0;
    int k = 0;
    Atom previous = null;
    for (Token t : tokens) {
      if (t instanceof Gap g) {
        x += g.width();
      } else if (t instanceof Atom a) {
        if (previous != null) {
          if (previous.node().slanted() && !a.node().slanted()) {
            x += previous.node().italic();
          }
          x += spacing(right[k - 1], left[k], st);
        }
        xs[k++] = x;
        x += a.node().width();
        previous = a;
      }
    }
    if (previous != null && previous.node().slanted()) {
      x += previous.node().italic();
    }
    // Groups.
    Deque<List<Placed>> stack = new ArrayDeque<>();
    Deque<Path> paths = new ArrayDeque<>();
    N result = null;
    k = 0;
    for (Token t : tokens) {
      switch (t) {
        case Open o -> {
          stack.push(new ArrayList<>());
          paths.push(o.path() == null ? Path.ROOT : o.path());
        }
        case Atom a -> stack.peek().add(new Placed(a.node(), xs[k++], 0));
        case Gap g -> {}
        case Linear l -> throw new IllegalStateException("an unresolved space");
        case Close c -> {
          List<Placed> kids = stack.pop();
          Path p = paths.pop();
          Path path = p == Path.ROOT ? null : p;
          if (stack.isEmpty()) {
            result = N.group(kids, x, path);
          } else if (path == null) {
            stack.peek().addAll(kids);
          } else {
            double x0 = kids.isEmpty() ? 0 : kids.getFirst().dx();
            double end = x0;
            List<Placed> shifted = new ArrayList<>();
            for (Placed kid : kids) {
              shifted.add(new Placed(kid.node(), kid.dx() - x0, kid.dy()));
              end = Math.max(end, kid.dx() + kid.node().width());
            }
            stack.peek().add(new Placed(N.group(shifted, end - x0, path), x0, 0));
          }
        }
      }
    }
    return result;
  }

  /**
   * Resolves the linear text's spaces. Between two names of which one has several letters, a space
   * is a thin space, as {@code \mathrm{ab}\,\mathrm{cd}}, so the product {@code ab cd} and the name
   * {@code abcd} stay apart; any other linear space has no width, the classes spacing its
   * neighbours.
   */
  private void linearSpaces(List<Token> tokens, St st) {
    for (int k = 0; k < tokens.size(); k++) {
      if (tokens.get(k) instanceof Linear) {
        Atom before = neighbour(tokens, k, -1);
        Atom after = neighbour(tokens, k, 1);
        boolean thin =
            before != null
                && after != null
                && name(before.source())
                && name(after.source())
                && (before.source() instanceof Text || after.source() instanceof Text);
        tokens.set(k, new Gap(thin ? 3 * size(st) / 18 : 0));
      }
    }
  }

  /** The nearest atom from a token in a direction, past group bounds, or null past a space. */
  private static Atom neighbour(List<Token> tokens, int from, int step) {
    for (int k = from + step; k >= 0 && k < tokens.size(); k += step) {
      switch (tokens.get(k)) {
        case Atom a -> {
          return a;
        }
        case Open o -> {}
        case Close c -> {}
        case Gap g -> {
          return null;
        }
        case Linear l -> {
          return null;
        }
      }
    }
    return null;
  }

  /** Whether a layout is a name: an ordinary letter, or an ordinary run of letters. */
  private static boolean name(Layout layout) {
    return switch (layout) {
      case Glyph g -> g.cls() == Cls.ORD && Character.isLetter(g.codePoint());
      case Text t ->
          t.cls() == Cls.ORD
              && !t.text().isEmpty()
              && t.text().codePoints().allMatch(Character::isLetter);
      default -> false;
    };
  }

  /** The atoms' classes after TeX's rules 5 and 6: a binary sign that cannot be one is ordinary. */
  private static void classes(List<Atom> atoms, Cls[] left, Cls[] right) {
    for (int k = 0; k < atoms.size(); k++) {
      left[k] = atoms.get(k).left();
      right[k] = atoms.get(k).right();
      if (left[k] == Cls.BIN
          && (k == 0
              || switch (right[k - 1]) {
                case BIN, OP, REL, OPEN, PUNCT -> true;
                default -> false;
              })) {
        left[k] = Cls.ORD;
        right[k] = Cls.ORD;
      }
      if ((left[k] == Cls.REL || left[k] == Cls.CLOSE || left[k] == Cls.PUNCT)
          && k > 0
          && right[k - 1] == Cls.BIN) {
        left[k - 1] = Cls.ORD;
        right[k - 1] = Cls.ORD;
      }
    }
    int last = atoms.size() - 1;
    if (last >= 0 && right[last] == Cls.BIN) {
      left[last] = Cls.ORD;
      right[last] = Cls.ORD;
    }
  }

  /**
   * A layout's class as its left neighbour sees it: a fenced body or a matrix begins with an
   * opening fence, as in TeX's {@code \sin(x)}, where an inner atom would take a thin space.
   */
  private static Cls left(Layout layout) {
    return switch (layout) {
      case Delimited d when d.open() != 0 -> Cls.OPEN;
      case Matrix m -> Cls.OPEN;
      default -> layout.cls();
    };
  }

  /** A layout's class as its right neighbour sees it: a closing fence is a close. */
  private static Cls right(Layout layout) {
    return switch (layout) {
      case Delimited d when d.close() != 0 -> Cls.CLOSE;
      case Matrix m -> Cls.CLOSE;
      default -> layout.cls();
    };
  }

  private double spacing(Cls left, Cls right, St st) {
    int entry = SPACING[left.ordinal()][right.ordinal()];
    if (entry < 0 && st.level() >= 2) {
      return 0;
    }
    int mu =
        switch (Math.abs(entry)) {
          case 1 -> 3;
          case 2 -> 4;
          case 3 -> 5;
          default -> 0;
        };
    return mu * size(st) / 18;
  }

  // Fractions (MathML Core §3.3.2).

  private N fraction(Fraction f, St st) {
    N num = layout(f.num(), st.num());
    N den = layout(f.den(), st.den());
    boolean display = st.display();
    double t = c(FRACTION_RULE_THICKNESS, st);
    double axis = c(AXIS_HEIGHT, st);
    double up =
        Math.max(
            c(
                display ? FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP : FRACTION_NUMERATOR_SHIFT_UP,
                st),
            axis
                + t / 2
                + c(display ? FRACTION_NUM_DISPLAY_STYLE_GAP_MIN : FRACTION_NUMERATOR_GAP_MIN, st)
                + num.depth());
    double down =
        Math.max(
            c(
                display
                    ? FRACTION_DENOMINATOR_DISPLAY_STYLE_SHIFT_DOWN
                    : FRACTION_DENOMINATOR_SHIFT_DOWN,
                st),
            c(display ? FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN : FRACTION_DENOMINATOR_GAP_MIN, st)
                + den.height()
                - axis
                + t / 2);
    double pad = FRACTION_PADDING * size(st);
    double inner = Math.max(num.width(), den.width());
    List<Placed> kids =
        List.of(
            new Placed(num, pad + (inner - num.width()) / 2, -up),
            new Placed(rule(inner, t / 2, t / 2), pad, -axis),
            new Placed(den, pad + (inner - den.width()) / 2, down));
    return N.group(kids, inner + 2 * pad, f.path());
  }

  // Scripts (MathML Core §3.4).

  private N scripts(Scripts s, St st) {
    if (s.sub() == null && s.base() instanceof Scripts inner && inner.sup() == null) {
      return subscriptedPower(inner, s, st);
    }
    N base = layout(s.base(), st);
    N sub = s.sub() == null ? null : layout(s.sub(), st.sub());
    N sup = s.sup() == null ? null : layout(s.sup(), st.sup());
    return attach(base, sub, sup, st, s.path(), base.italic(), false);
  }

  /**
   * A power of a subscripted base, {@code x₁²}: the subscript and the superscript stacked on the
   * base as TeX sets {@code x_1^2}, the base and its subscript still one box with the base's path.
   */
  private N subscriptedPower(Scripts inner, Scripts outer, St st) {
    N base = layout(inner.base(), st);
    N sub = layout(inner.sub(), st.sub());
    N sup = layout(outer.sup(), st.sup());
    N both = attach(base, sub, sup, st, outer.path(), base.italic(), false);
    Placed b = both.kids().get(0);
    Placed up = both.kids().get(1);
    Placed down = both.kids().get(2);
    N subscripted =
        N.group(
            List.of(b, down),
            Math.max(b.node().width(), down.dx() + down.node().width()),
            inner.path());
    return N.group(List.of(new Placed(subscripted, 0, 0), up), both.width(), outer.path());
  }

  /**
   * Attaches scripts to a base: the superscript after the base's italic correction, the subscript
   * at the base's advance. A large operator's advance already covers its ink in this font (the
   * display integral: advance 1032, ink to 957, correction 540), so its superscript goes at the
   * advance and its subscript is tucked left by the correction, TeX's rule for an operator with
   * scripts.
   */
  private N attach(N base, N sub, N sup, St st, Path path, double italic, boolean operator) {
    double up = 0;
    double down = 0;
    if (sup != null) {
      up =
          Math.max(
              c(st.cramped() ? SUPERSCRIPT_SHIFT_UP_CRAMPED : SUPERSCRIPT_SHIFT_UP, st),
              Math.max(
                  base.height() - c(SUPERSCRIPT_BASELINE_DROP_MAX, st),
                  c(SUPERSCRIPT_BOTTOM_MIN, st) + sup.depth()));
    }
    if (sub != null) {
      down =
          Math.max(
              c(SUBSCRIPT_SHIFT_DOWN, st),
              Math.max(
                  base.depth() + c(SUBSCRIPT_BASELINE_DROP_MIN, st),
                  sub.height() - c(SUBSCRIPT_TOP_MAX, st)));
    }
    if (sub != null && sup != null) {
      double gap = (up - sup.depth()) - (sub.height() - down);
      double min = c(SUB_SUPERSCRIPT_GAP_MIN, st);
      if (gap < min) {
        down += min - gap;
        double lift = c(SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT, st) - (up - sup.depth());
        if (lift > 0) {
          up += lift;
          down -= lift;
        }
      }
    }
    List<Placed> kids = new ArrayList<>(List.of(new Placed(base, 0, 0)));
    double width = base.width();
    double after = c(SPACE_AFTER_SCRIPT, st);
    double supX = operator ? base.width() : base.width() + italic;
    double subX = operator ? base.width() - italic : base.width();
    if (sup != null) {
      kids.add(new Placed(sup, supX, -up));
      width = Math.max(width, supX + sup.width() + after);
    }
    if (sub != null) {
      kids.add(new Placed(sub, subX, down));
      width = Math.max(width, subX + sub.width() + after);
    }
    return N.group(kids, width, path);
  }

  // Radicals (MathML Core §3.3.3).

  private N radical(Radical r, St st) {
    N radicand = layout(r.radicand(), st.cramp());
    double t = c(RADICAL_RULE_THICKNESS, st);
    double gap = c(st.display() ? RADICAL_DISPLAY_STYLE_VERTICAL_GAP : RADICAL_VERTICAL_GAP, st);
    double target = radicand.height() + radicand.depth() + gap + t;
    N sign = stretch('√', target, st);
    double total = sign.height() + sign.depth();
    if (total > target) {
      gap += (total - target) / 2;
    }
    double top = radicand.height() + gap + t;
    double raise = top - sign.height();
    double bottom = top - total;
    double x = 0;
    List<Placed> kids = new ArrayList<>();
    if (r.index() != null) {
      N index = layout(r.index(), st.scriptScript());
      double before = c(RADICAL_KERN_BEFORE_DEGREE, st);
      double indexBottom =
          bottom + total * font.math().constants().get(RADICAL_DEGREE_BOTTOM_RAISE_PERCENT) / 100.0;
      kids.add(new Placed(index, before, -(indexBottom + index.depth())));
      x = Math.max(0, before + index.width() + c(RADICAL_KERN_AFTER_DEGREE, st));
    }
    kids.add(new Placed(sign, x, -raise));
    double body = x + sign.width();
    kids.add(new Placed(rule(radicand.width(), t, 0), body, -(top - t)));
    kids.add(new Placed(radicand, body, 0));
    N group = N.group(kids, body + radicand.width(), r.path());
    return new N(
        group.width(),
        Math.max(group.height(), top + c(RADICAL_EXTRA_ASCENDER, st)),
        group.depth(),
        0,
        false,
        group.path(),
        null,
        group.kids());
  }

  // Stretchy glyphs (MathML Core §5.4).

  /**
   * A character grown vertically to at least {@code target} pixels, height and depth together: the
   * first variant that reaches it, else the assembly, else the largest variant. A character with no
   * vertical construction is its own glyph.
   */
  private N stretch(int c, double target, St st) {
    int base = font.glyph(c);
    double scale = size(st) / upem;
    GlyphConstruction construction = font.math().vertical(base);
    if (construction == null) {
      return glyphNode(base, size(st), null);
    }
    for (GlyphConstruction.Variant v : construction.variants()) {
      if (v.advance() * scale >= target) {
        return glyphNode(v.glyph(), size(st), null);
      }
    }
    if (construction.assembly() != null) {
      return assemble(construction.assembly(), target / scale, scale);
    }
    return glyphNode(construction.variants().getLast().glyph(), size(st), null);
  }

  /** An assembly at least {@code target} font units tall, its bottom on the baseline. */
  private N assemble(GlyphConstruction.Assembly assembly, double target, double scale) {
    List<GlyphConstruction.Part> parts = assembly.parts();
    double minOverlap = font.math().minConnectorOverlap();
    boolean extends_ = parts.stream().anyMatch(GlyphConstruction.Part::extender);
    List<GlyphConstruction.Part> chosen = List.of();
    for (int repeats = 0; repeats <= 1000; repeats++) {
      chosen = expand(parts, repeats);
      double size = 0;
      for (GlyphConstruction.Part p : chosen) {
        size += p.fullAdvance();
      }
      size -= (chosen.size() - 1) * minOverlap;
      if (size >= target || !extends_) {
        break;
      }
    }
    double sum = 0;
    double maxOverlap = Double.POSITIVE_INFINITY;
    for (int k = 0; k < chosen.size(); k++) {
      sum += chosen.get(k).fullAdvance();
      if (k > 0) {
        maxOverlap =
            Math.min(
                maxOverlap,
                Math.min(chosen.get(k - 1).endConnector(), chosen.get(k).startConnector()));
      }
    }
    double overlap = minOverlap;
    if (chosen.size() > 1) {
      overlap = (sum - target) / (chosen.size() - 1);
      overlap = Math.max(minOverlap, Math.min(overlap, Math.max(minOverlap, maxOverlap)));
    }
    List<Placed> kids = new ArrayList<>();
    double y = 0;
    double width = 0;
    double top = 0;
    double bottom = Double.POSITIVE_INFINITY;
    for (GlyphConstruction.Part p : chosen) {
      N part = glyphNode(p.glyph(), scale * upem, null);
      OpenTypeFont.Bounds ink = font.bounds(p.glyph());
      // A part's full advance is its ink: its ink's bottom goes at y, whatever its origin (the
      // bar's part has its ink 220 units below its origin).
      kids.add(new Placed(part, 0, -(y - ink.yMin()) * scale));
      top = Math.max(top, y + ink.yMax() - ink.yMin());
      bottom = Math.min(bottom, y);
      y += p.fullAdvance() - overlap;
      width = Math.max(width, part.width());
    }
    // The assembly's extent is its parts' ink: from the first part's ink bottom, on the baseline,
    // to the highest ink top.
    return new N(
        width,
        top * scale,
        Math.max(0, -bottom) * scale,
        assembly.italicsCorrection() * scale,
        false,
        null,
        null,
        kids);
  }

  private static List<GlyphConstruction.Part> expand(
      List<GlyphConstruction.Part> parts, int repeats) {
    List<GlyphConstruction.Part> out = new ArrayList<>();
    for (GlyphConstruction.Part p : parts) {
      for (int k = 0; k < (p.extender() ? repeats : 1); k++) {
        out.add(p);
      }
    }
    return out;
  }

  /** A fence sized for a body of the given height and depth and centred on the axis. */
  private N fence(int c, double height, double depth, St st) {
    double axis = c(AXIS_HEIGHT, st);
    N fence;
    if (height + depth < c(DELIMITED_SUB_FORMULA_MIN_HEIGHT, st)) {
      int glyph = font.glyph(c);
      if (glyph == 0) {
        return fallback(Character.toString(c), st, null);
      }
      return glyphNode(font.scriptGlyph(glyph, st.level() - 1), size(st), null);
    }
    if (font.glyph(c) == 0) {
      return fallback(Character.toString(c), st, null);
    }
    double delta = Math.max(height - axis, depth + axis);
    double size =
        Math.max(2 * delta * DELIMITER_FACTOR / 1000, 2 * delta - DELIMITER_SHORTFALL * size(st));
    fence = stretch(c, size, st);
    double shift = axis - (fence.height() - fence.depth()) / 2;
    return N.group(List.of(new Placed(fence, 0, -shift)), fence.width(), null);
  }

  private N delimited(Delimited d, St st) {
    N body = layout(d.body(), st);
    List<Placed> kids = new ArrayList<>();
    double x = 0;
    if (d.open() != 0) {
      N open = fence(d.open(), body.height(), body.depth(), st);
      kids.add(new Placed(open, 0, 0));
      x += open.width();
    }
    kids.add(new Placed(body, x, 0));
    x += body.width();
    if (d.close() != 0) {
      N close = fence(d.close(), body.height(), body.depth(), st);
      kids.add(new Placed(close, x, 0));
      x += close.width();
    }
    return N.group(kids, x, d.path());
  }

  // Tables: matrices and piecewise expressions.

  /**
   * Cells in rows on struts, each column {@code align}ed (0 left, 0.5 centred), the columns {@link
   * #COLUMN_GAP} apart, the whole centred on the axis.
   */
  private N table(List<List<N>> rows, double align, St st) {
    double em = size(st);
    int columns = rows.getFirst().size();
    double[] widths = new double[columns];
    double[] heights = new double[rows.size()];
    double[] depths = new double[rows.size()];
    for (int r = 0; r < rows.size(); r++) {
      heights[r] = STRUT_HEIGHT * em;
      depths[r] = STRUT_DEPTH * em;
      for (int k = 0; k < columns; k++) {
        N cell = rows.get(r).get(k);
        widths[k] = Math.max(widths[k], cell.width());
        heights[r] = Math.max(heights[r], cell.height());
        depths[r] = Math.max(depths[r], cell.depth());
      }
    }
    double total = 0;
    for (int r = 0; r < rows.size(); r++) {
      total += heights[r] + depths[r];
    }
    double top = c(AXIS_HEIGHT, st) + total / 2;
    List<Placed> kids = new ArrayList<>();
    double y = top;
    for (int r = 0; r < rows.size(); r++) {
      double baseline = y - heights[r];
      double x = 0;
      for (int k = 0; k < columns; k++) {
        N cell = rows.get(r).get(k);
        kids.add(new Placed(cell, x + (widths[k] - cell.width()) * align, -baseline));
        x += widths[k] + COLUMN_GAP * em;
      }
      y = baseline - depths[r];
    }
    double width = -COLUMN_GAP * em;
    for (double w : widths) {
      width += w + COLUMN_GAP * em;
    }
    N group = N.group(kids, width, null);
    return new N(width, top, total - top, 0, false, null, null, group.kids());
  }

  private N matrix(Matrix m, St st) {
    St cells = st.text();
    List<List<N>> rows = new ArrayList<>();
    for (List<Layout> row : m.rows()) {
      rows.add(row.stream().map(cell -> layout(cell, cells)).toList());
    }
    N table = table(rows, 0.5, cells);
    return fenced(m.open(), table, m.close(), st, m.path());
  }

  private N piecewise(Piecewise p, St st) {
    St cells = st.text();
    List<List<N>> rows = new ArrayList<>();
    for (Piecewise.Piece piece : p.pieces()) {
      rows.add(List.of(layout(piece.value(), cells), layout(piece.condition(), cells)));
    }
    rows.add(List.of(layout(p.otherwise(), cells), text(otherwise, false, cells, null)));
    N table = table(rows, 0, cells);
    return fenced('{', table, 0, st, p.path());
  }

  private N fenced(int open, N body, int close, St st, Path path) {
    List<Placed> kids = new ArrayList<>();
    double x = 0;
    if (open != 0) {
      N fence = fence(open, body.height(), body.depth(), st);
      kids.add(new Placed(fence, 0, 0));
      x += fence.width();
    }
    kids.add(new Placed(body, x, 0));
    x += body.width();
    if (close != 0) {
      N fence = fence(close, body.height(), body.depth(), st);
      kids.add(new Placed(fence, x, 0));
      x += fence.width();
    }
    return N.group(kids, x, path);
  }

  // Big operators.

  private N bigOp(BigOp b, St st) {
    N operator = operator(b.operator(), st);
    N lower = b.lower() == null ? null : layout(b.lower(), st.sub());
    N upper = b.upper() == null ? null : layout(b.upper(), st.sup());
    N head;
    if (lower == null && upper == null) {
      head = operator;
    } else if (b.limits() && st.display()) {
      head = limits(operator, lower, upper, st);
    } else {
      head = attach(operator, lower, upper, st, null, operator.italic(), true);
    }
    N body = layout(b.body(), st);
    double gap = spacing(Cls.OP, first(b.body()), st);
    List<Placed> kids = List.of(new Placed(head, 0, 0), new Placed(body, head.width() + gap, 0));
    return N.group(kids, head.width() + gap + body.width(), b.path());
  }

  /** The class of the first atom of a layout, as its left neighbour sees it. */
  private static Cls first(Layout layout) {
    if (layout instanceof Row r) {
      for (Layout item : r.items()) {
        if (!(item instanceof Space)) {
          Cls cls = first(item);
          return cls == Cls.BIN ? Cls.ORD : cls;
        }
      }
      return Cls.ORD;
    }
    return left(layout);
  }

  /**
   * A big operator's sign: in display style the first size variant reaching the display operator
   * height, or the largest, centred on the axis; any other operator as it lays out.
   */
  private N operator(Layout operator, St st) {
    if (!(operator instanceof Glyph g) || g.cls() != Cls.OP) {
      return layout(operator, st);
    }
    int glyph = font.glyph(g.codePoint());
    if (glyph == 0) {
      return layout(operator, st);
    }
    if (st.display()) {
      GlyphConstruction construction = font.math().vertical(glyph);
      if (construction != null) {
        double min = font.math().constants().get(DISPLAY_OPERATOR_MIN_HEIGHT);
        glyph = construction.variants().getLast().glyph();
        for (GlyphConstruction.Variant v : construction.variants()) {
          if (v.advance() >= min) {
            glyph = v.glyph();
            break;
          }
        }
      }
    } else {
      glyph = font.scriptGlyph(glyph, st.level() - 1);
    }
    N sign = glyphNode(glyph, size(st), null);
    double shift = c(AXIS_HEIGHT, st) - (sign.height() - sign.depth()) / 2;
    N centred = N.group(List.of(new Placed(sign, 0, -shift)), sign.width(), null);
    return centred.withItalic(sign.italic());
  }

  /** Limits below and above an operator, each centred, shifted by half the italic correction. */
  private N limits(N operator, N lower, N upper, St st) {
    double italic = operator.italic();
    double width = operator.width();
    if (upper != null) {
      width = Math.max(width, upper.width() + italic / 2);
    }
    if (lower != null) {
      width = Math.max(width, lower.width() + italic / 2);
    }
    List<Placed> kids = new ArrayList<>();
    kids.add(new Placed(operator, (width - operator.width()) / 2, 0));
    if (upper != null) {
      double rise =
          Math.max(
              c(UPPER_LIMIT_BASELINE_RISE_MIN, st), c(UPPER_LIMIT_GAP_MIN, st) + upper.depth());
      kids.add(
          new Placed(upper, (width - upper.width()) / 2 + italic / 2, -(operator.height() + rise)));
    }
    if (lower != null) {
      double drop =
          Math.max(
              c(LOWER_LIMIT_BASELINE_DROP_MIN, st), c(LOWER_LIMIT_GAP_MIN, st) + lower.height());
      kids.add(
          new Placed(lower, (width - lower.width()) / 2 - italic / 2, operator.depth() + drop));
    }
    return N.group(kids, width, null);
  }
}
