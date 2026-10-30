package mathcanvas.app.eval;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.LayoutPrinter;
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

/**
 * What the worksheet typesets. {@code MathView} typesets on the FX thread, so a layout tree over
 * {@link #MAX_TYPESET_NODES} nodes is not typeset at all: the cell shows its capped text instead (a
 * 1.26 MB expansion typeset on the FX thread froze the window at three frames a second).
 */
public final class Typeset {

  /** The most layout nodes a cell typesets. */
  public static final int MAX_TYPESET_NODES = 20_000;

  private Typeset() {}

  /**
   * A layout tree if it is small enough to typeset.
   *
   * @param layout the tree
   * @return the tree, or null when it has more than {@link #MAX_TYPESET_NODES} nodes
   */
  public static Layout capped(Layout layout) {
    return nodes(layout, MAX_TYPESET_NODES + 1) > MAX_TYPESET_NODES ? null : layout;
  }

  /**
   * Counts a tree's nodes, stopping once the count passes a limit.
   *
   * @param layout the tree
   * @param limit where counting may stop
   * @return the count, or a number at least the limit
   */
  static int nodes(Layout layout, int limit) {
    int[] count = {0};
    count(layout, count, limit);
    return count[0];
  }

  private static void count(Layout layout, int[] count, int limit) {
    if (layout == null || count[0] >= limit) {
      return;
    }
    count[0]++;
    switch (layout) {
      case Row r -> r.items().forEach(i -> count(i, count, limit));
      case Fraction f -> {
        count(f.num(), count, limit);
        count(f.den(), count, limit);
      }
      case Scripts s -> {
        count(s.base(), count, limit);
        count(s.sub(), count, limit);
        count(s.sup(), count, limit);
      }
      case Radical r -> {
        count(r.index(), count, limit);
        count(r.radicand(), count, limit);
      }
      case Delimited d -> count(d.body(), count, limit);
      case Matrix m -> m.rows().forEach(row -> row.forEach(c -> count(c, count, limit)));
      case BigOp b -> {
        count(b.operator(), count, limit);
        count(b.lower(), count, limit);
        count(b.upper(), count, limit);
        count(b.body(), count, limit);
      }
      case Piecewise p -> {
        for (Piecewise.Piece piece : p.pieces()) {
          count(piece.value(), count, limit);
          count(piece.condition(), count, limit);
        }
        count(p.otherwise(), count, limit);
      }
      case Glyph g -> {}
      case Text t -> {}
      case Space s -> {}
    }
  }

  /**
   * The layout of a definition's echo: its head and its body as parsed, joined by {@code :=}.
   *
   * @param printer the layout printer
   * @param definition the definition
   * @return the layout, or null when it is too large to typeset
   */
  public static Layout echo(LayoutPrinter printer, Statement.Definition definition) {
    Expr head =
        definition.params().isEmpty()
            ? definition.name()
            : Apply.of(definition.name(), definition.params());
    return capped(
        new Row(
            List.of(
                printer.print(head),
                new Text(":=", Style.UPRIGHT, Cls.REL, null),
                printer.print(definition.body())),
            null));
  }
}
