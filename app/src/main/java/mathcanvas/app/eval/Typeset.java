package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
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
import mathcanvas.engine.print.layout.UnitText;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.Typesetters;
import mathcanvas.render.typeset.Box;
import mathcanvas.render.typeset.Typesetter;

/**
 * What the worksheet typesets, and where: the worker prints each result to a layout tree and
 * typesets it with the shared typesetter ({@link Typesetters#standard()}, thread-safe, with the
 * job's cancellation), so the FX thread only shows the box tree ({@code MathView.setBox}). A layout
 * over {@link Typesetter#MAX_TYPESET_NODES} nodes is not typeset at all ({@link
 * Typesetter#wouldExceed}): the cell shows its capped text instead, since its box tree and nodes
 * would cost the window too much (a 1.26 MB expansion froze it at three frames a second).
 */
public final class Typeset {

  /** The size of a cell's results, in pixels. */
  public static final double RESULT_SIZE = 22;

  /** The size of a step's expression, in pixels. */
  public static final double STEP_SIZE = 15;

  /** The size of a definition in the variables inspector, in pixels, in text style. */
  public static final double INSPECTOR_SIZE = 14;

  private Typeset() {}

  /**
   * A layout tree if it is small enough to typeset.
   *
   * @param layout the tree
   * @return the tree, or null when it has more than {@link Typesetter#MAX_TYPESET_NODES} nodes
   */
  public static Layout capped(Layout layout) {
    return Typesetter.wouldExceed(layout) ? null : layout;
  }

  /**
   * Typesets a layout tree on this thread, in display style.
   *
   * @param layout the tree, or null
   * @param size the size in pixels
   * @param cancellation the request to stop
   * @return the box tree, or null when the layout is null
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Box box(Layout layout, double size, Cancellation cancellation) {
    return box(layout, size, true, cancellation);
  }

  /**
   * Typesets a layout tree on this thread.
   *
   * @param layout the tree, or null
   * @param size the size in pixels
   * @param display display style rather than text style
   * @param cancellation the request to stop
   * @return the box tree, or null when the layout is null
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Box box(Layout layout, double size, boolean display, Cancellation cancellation) {
    return layout == null
        ? null
        : Typesetters.standard().typeset(layout, size, display, cancellation);
  }

  /**
   * The outermost node of a layout tree that prints a subexpression.
   *
   * @param layout the tree
   * @param path the subexpression's path
   * @return the node, or null when no node has the path
   */
  public static Layout find(Layout layout, Path path) {
    if (layout == null) {
      return null;
    }
    if (path.equals(pathOf(layout))) {
      return layout;
    }
    for (Layout child : children(layout)) {
      Layout found = find(child, path);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static Path pathOf(Layout layout) {
    return switch (layout) {
      case Row r -> r.path();
      case Fraction f -> f.path();
      case Scripts s -> s.path();
      case Radical r -> r.path();
      case Delimited d -> d.path();
      case Matrix m -> m.path();
      case BigOp b -> b.path();
      case Piecewise p -> p.path();
      case Glyph g -> g.path();
      case Text t -> t.path();
      case UnitText u -> u.path();
      case Space s -> null;
    };
  }

  private static List<Layout> children(Layout layout) {
    List<Layout> out = new ArrayList<>();
    switch (layout) {
      case Row r -> out.addAll(r.items());
      case Fraction f -> out.addAll(List.of(f.num(), f.den()));
      case Scripts s -> {
        out.add(s.base());
        out.add(s.sub());
        out.add(s.sup());
      }
      case Radical r -> {
        out.add(r.index());
        out.add(r.radicand());
      }
      case Delimited d -> out.add(d.body());
      case Matrix m -> m.rows().forEach(out::addAll);
      case BigOp b -> {
        out.add(b.operator());
        out.add(b.lower());
        out.add(b.upper());
        out.add(b.body());
      }
      case Piecewise p -> {
        for (Piecewise.Piece piece : p.pieces()) {
          out.add(piece.value());
          out.add(piece.condition());
        }
        out.add(p.otherwise());
      }
      case Glyph g -> {}
      case Text t -> {}
      case UnitText u -> {}
      case Space s -> {}
    }
    out.removeIf(Objects::isNull);
    return out;
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
    return defines(printer, head, definition.body());
  }

  /**
   * The layout of a stored definition, as the inspector shows it: {@code name := body} or {@code
   * name(params) := body}.
   *
   * @param printer the layout printer
   * @param name the name
   * @param definition the definition
   * @return the layout, or null when it is too large to typeset
   */
  public static Layout definition(LayoutPrinter printer, String name, Definition definition) {
    Symbol symbol = Symbol.of(name);
    return switch (definition) {
      case Definition.Value v -> defines(printer, symbol, v.body());
      case Definition.Computed c -> defines(printer, symbol, c.value());
      case Definition.Function f -> defines(printer, Apply.of(symbol, f.params()), f.body());
      case Definition.Procedure p -> function(printer, Apply.of(symbol, p.params()));
    };
  }

  /** The head of a function defined with {@code function}: {@code function f(n)} (§19). */
  static Layout function(LayoutPrinter printer, Expr head) {
    return capped(
        new Row(
            List.of(
                new Text("function", Style.UPRIGHT, Cls.ORD, null),
                new Space(Space.Kind.LINEAR),
                printer.print(head)),
            null));
  }

  private static Layout defines(LayoutPrinter printer, Expr head, Expr body) {
    return capped(
        new Row(
            List.of(
                printer.print(head),
                new Text(":=", Style.UPRIGHT, Cls.REL, null),
                printer.print(body)),
            null));
  }
}
