package mathcanvas.render.typeset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.render.font.OpenTypeFont;

/** What the typesetter tests share: a typesetter with fixed fallback metrics, and box walks. */
final class Boxes {

  /** Fallback text: 0.6 em a character, 0.7 em up, 0.2 em down. */
  static final Fallback FIXED =
      (text, size) ->
          new Fallback.Metrics(
              0.6 * size * text.codePointCount(0, text.length()), 0.7 * size, 0.2 * size);

  static final Typesetter TYPESETTER = new Typesetter(OpenTypeFont.stix(), FIXED);

  private Boxes() {}

  static Layout display(String text) {
    Expr e = Parser.parseExpression(text, Set.of("f")::contains);
    return LayoutPrinter.INSTANCE.display(Canonical.of(e));
  }

  static Layout held(String text) {
    return LayoutPrinter.INSTANCE.print(Parser.parseExpression(text, Set.of("f")::contains));
  }

  /** Every box of a tree, in pre-order. */
  static List<Box> all(Box root) {
    List<Box> out = new ArrayList<>();
    collect(root, out);
    return out;
  }

  private static void collect(Box box, List<Box> out) {
    out.add(box);
    for (Box child : box.children()) {
      collect(child, out);
    }
  }

  static <T extends Box> List<T> all(Box root, Class<T> type) {
    return all(root).stream().filter(type::isInstance).map(type::cast).toList();
  }
}
