package mathcanvas.render.typeset;

import static mathcanvas.render.typeset.Boxes.TYPESETTER;
import static mathcanvas.render.typeset.Boxes.all;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * The box tree's invariants over random layout trees of every construct, each node with a path of
 * its own, in both styles and at random sizes: every box contains its children, and hit-testing a
 * box's centre finds that box or one inside it, wherever no box outside its line of descent covers
 * the centre.
 */
class BoxProperties {

  @Provide
  Arbitrary<Layout> layouts() {
    return Arbitraries.randomValue(random -> new Generator(random).layout(4));
  }

  @Property
  void everyBoxContainsItsChildren(@ForAll("layouts") Layout layout, @ForAll boolean display) {
    Box root = TYPESETTER.typeset(layout, 20, display);
    for (Box box : all(root)) {
      for (Box child : box.children()) {
        assertThat(child.x()).isGreaterThanOrEqualTo(box.x() - 1e-9);
        assertThat(child.x() + child.width()).isLessThanOrEqualTo(box.x() + box.width() + 1e-9);
        assertThat(child.top()).isGreaterThanOrEqualTo(box.top() - 1e-9);
        assertThat(child.bottom()).isLessThanOrEqualTo(box.bottom() + 1e-9);
      }
    }
  }

  @Property
  void hitTestingABoxsCentreFindsItOrABoxInside(
      @ForAll("layouts") Layout layout, @ForAll boolean display) {
    Box root = TYPESETTER.typeset(layout, 20, display);
    Map<Box, Box> parents = new IdentityHashMap<>();
    for (Box box : all(root)) {
      for (Box child : box.children()) {
        parents.put(child, box);
      }
    }
    List<Box> boxes = all(root);
    int checked = 0;
    for (Box box : boxes) {
      if (box.path() == null || box.width() <= 0 || box.ascent() + box.descent() <= 0) {
        continue;
      }
      double cx = box.x() + box.width() / 2;
      double cy = (box.top() + box.bottom()) / 2;
      boolean clear = true;
      for (Box other : boxes) {
        if (other.path() != null
            && other.contains(cx, cy)
            && !inside(other, box, parents)
            && !inside(box, other, parents)) {
          clear = false;
          break;
        }
      }
      if (!clear) {
        continue;
      }
      Path hit = root.hitTest(cx, cy).orElseThrow();
      Box found = box.find(hit).orElse(null);
      assertThat(found).as("hit %s at the centre of %s", hit, box.path()).isNotNull();
      checked++;
    }
    Statistics.collect(checked > 0 ? "some box checked" : "no box clear");
  }

  /** Whether {@code inner} is {@code outer} or below it. */
  private static boolean inside(Box inner, Box outer, Map<Box, Box> parents) {
    for (Box b = inner; b != null; b = parents.get(b)) {
      if (b == outer) {
        return true;
      }
    }
    return false;
  }

  @Example
  void parsedExpressionsTypesetInBothStyles() {
    for (String text :
        List.of(
            "x^2 + 3x - 1",
            "x/(2*pi)",
            "-x/2",
            "sqrt(x + 1)",
            "cbrt(x^2 + 1)",
            "e^(x^2)",
            "∫[0, 1] x^2 dx",
            "∫ sin(x)/x dx",
            "Σ[k=1,n] 1/k^2",
            "lim[x → 0+] sin(x)/x",
            "d/dx x^2",
            "d^2/dx^2 sin(x)",
            "[[1, 2], [3, 4]]",
            "⟨2x, 2y⟩",
            "{-sqrt(2), sqrt(2)}",
            "abs(x - 1)",
            "x_1 + x_ab + θ + Ω",
            "n!",
            "u × v",
            "x -> x^2",
            "x <= 1",
            "f(x, y)")) {
      for (boolean display : List.of(true, false)) {
        Box root = TYPESETTER.typeset(Boxes.display(text), 20, display);
        assertThat(root.width()).as(text).isPositive();
        assertThat(root.ascent()).as(text).isPositive();
      }
    }
  }

  /** Random layout trees, each node given a fresh path so a box names its node. */
  private static final class Generator {
    private final Random random;
    private int next;

    Generator(Random random) {
      this.random = random;
    }

    private Path path() {
      return new Path(List.of(next++));
    }

    Layout layout(int depth) {
      if (depth == 0) {
        return leaf();
      }
      return switch (random.nextInt(11)) {
        case 0, 1 -> leaf();
        case 2 -> {
          List<Layout> items = new ArrayList<>();
          int n = 1 + random.nextInt(4);
          for (int k = 0; k < n; k++) {
            if (k > 0 && random.nextBoolean()) {
              items.add(new Space(random.nextBoolean() ? Space.Kind.LINEAR : Space.Kind.THIN));
            }
            items.add(layout(depth - 1));
          }
          yield new Row(items, random.nextBoolean() ? path() : null);
        }
        case 3 ->
            new Fraction(
                layout(depth - 1),
                layout(depth - 1),
                random.nextBoolean(),
                random.nextBoolean(),
                false,
                path());
        case 4 -> {
          boolean sub = random.nextBoolean();
          boolean sup = !sub || random.nextBoolean();
          yield new Scripts(
              layout(depth - 1),
              sub ? layout(depth - 1) : null,
              sup ? layout(depth - 1) : null,
              sub ? Scripts.Form.UNDERSCORE : null,
              sup ? Scripts.Form.CARET : null,
              path());
        }
        case 5 ->
            new Radical(
                random.nextBoolean() ? new Text("3", Style.UPRIGHT, Cls.ORD, null) : null,
                layout(depth - 1),
                false,
                path());
        case 6 -> {
          int[][] fences = {{'(', ')'}, {'[', ']'}, {'{', '}'}, {'|', '|'}, {'⟨', '⟩'}, {'[', ')'}};
          int[] f = fences[random.nextInt(fences.length)];
          yield new Delimited(f[0], layout(depth - 1), f[1], path());
        }
        case 7 -> {
          int rows = 1 + random.nextInt(3);
          int columns = 1 + random.nextInt(3);
          List<List<Layout>> cells = new ArrayList<>();
          for (int r = 0; r < rows; r++) {
            List<Layout> row = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
              row.add(layout(depth - 2 < 0 ? 0 : depth - 2));
            }
            cells.add(row);
          }
          yield new Matrix(cells, '[', ']', path());
        }
        case 8 -> {
          int[] signs = {'∫', '∑', '∏'};
          int sign = signs[random.nextInt(signs.length)];
          yield new BigOp(
              new Glyph(sign, Style.UPRIGHT, Cls.OP, null),
              random.nextBoolean() ? layout(depth - 2 < 0 ? 0 : depth - 2) : null,
              random.nextBoolean() ? layout(depth - 2 < 0 ? 0 : depth - 2) : null,
              layout(depth - 1),
              sign != '∫',
              path());
        }
        case 9 ->
            new BigOp(
                new Text("lim", Style.UPRIGHT, Cls.OP, null),
                layout(0),
                null,
                layout(depth - 1),
                true,
                path());
        default -> {
          List<Piecewise.Piece> pieces = new ArrayList<>();
          int n = 1 + random.nextInt(2);
          for (int k = 0; k < n; k++) {
            pieces.add(new Piecewise.Piece(layout(depth - 2 < 0 ? 0 : depth - 2), leaf()));
          }
          yield new Piecewise(pieces, leaf(), path());
        }
      };
    }

    private Layout leaf() {
      String letters = "xyzafθΩ";
      return switch (random.nextInt(5)) {
        case 0 ->
            new Glyph(
                letters.codePointAt(random.nextInt(letters.length())),
                Style.ITALIC,
                Cls.ORD,
                path());
        case 1 -> new Text(Integer.toString(random.nextInt(1000)), Style.UPRIGHT, Cls.ORD, path());
        case 2 -> new Text("sin", Style.UPRIGHT, Cls.OP, path());
        case 3 -> {
          String ops = "+-=·<→,!";
          int c = ops.codePointAt(random.nextInt(ops.length()));
          Cls cls =
              switch (c) {
                case '+', '-', '·' -> Cls.BIN;
                case '=', '<', '→' -> Cls.REL;
                case ',' -> Cls.PUNCT;
                default -> Cls.CLOSE;
              };
          yield new Glyph(c, Style.UPRIGHT, cls, null);
        }
        default -> new Glyph(0x4E00, Style.UPRIGHT, Cls.ORD, path());
      };
    }
  }
}
