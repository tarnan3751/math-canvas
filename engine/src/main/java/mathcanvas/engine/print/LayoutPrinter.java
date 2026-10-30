package mathcanvas.engine.print;

import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.print.layout.BigOp;
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
import mathcanvas.engine.print.layout.Text;

/**
 * The layout printer: an expression to the layout tree {@code render} typesets. It shares the
 * Unicode printer's walk, so the tree has the Unicode text's structure, parentheses and order:
 * {@link #flatten} of a printed tree is exactly what {@link UnicodePrinter} prints. Each node
 * carries the path of the subexpression it prints in the tree that was printed: the held tree for
 * {@link #print}, the display tree ({@link Display#of}) for {@link #display}.
 */
public final class LayoutPrinter {

  /** The printer. */
  public static final LayoutPrinter INSTANCE =
      new LayoutPrinter(new InfixPrinter<>(true, LayoutSink.INSTANCE, Format.EXACT));

  private final InfixPrinter<Layout> walk;

  private LayoutPrinter(InfixPrinter<Layout> walk) {
    this.walk = walk;
  }

  /**
   * Returns how this printer writes numbers.
   *
   * @return the format
   */
  public Format format() {
    return walk.format();
  }

  /**
   * Returns a layout printer that writes numbers in a format, as {@link Printer#withFormat} does;
   * its layouts flatten to the Unicode printer's text in the same format.
   *
   * @param format the format
   * @return the printer
   */
  public LayoutPrinter withFormat(Format format) {
    return new LayoutPrinter(walk.withFormat(format));
  }

  /**
   * Lays out a tree as it holds its operands.
   *
   * @param e the expression
   * @return the layout, its paths into {@code e}
   */
  public Layout print(Expr e) {
    return walk.build(e);
  }

  /**
   * Lays out a tree as it holds its operands, checking a cancellation at every node.
   *
   * @param e the expression
   * @param cancellation the request to stop
   * @return the layout, its paths into {@code e}
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public Layout print(Expr e, Cancellation cancellation) {
    return walk.build(e, cancellation);
  }

  /**
   * Lays out a canonical value in its display shape.
   *
   * @param canonical an expression in canonical form
   * @return the layout, its paths into {@code Display.of(canonical)}
   */
  public Layout display(Expr canonical) {
    return display(canonical, Cancellation.NEVER);
  }

  /**
   * Lays out a canonical value in its display shape, stopping at a request while the shape is built
   * ({@link Display#of(Expr, Cancellation)}) and while it is laid out.
   *
   * @param canonical an expression in canonical form
   * @param cancellation the request to stop
   * @return the layout, its paths into {@code Display.of(canonical)}
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public Layout display(Expr canonical, Cancellation cancellation) {
    return print(
        Display.of(NumberFormat.prepare(canonical, format(), cancellation), cancellation),
        cancellation);
  }

  /**
   * Returns the linear text of a layout: the Unicode printer's text for the tree it was printed
   * from.
   *
   * @param layout the layout
   * @return the text
   */
  public static String flatten(Layout layout) {
    StringBuilder out = new StringBuilder();
    flatten(layout, out);
    return out.toString();
  }

  private static void flatten(Layout layout, StringBuilder out) {
    switch (layout) {
      case Row r -> r.items().forEach(item -> flatten(item, out));
      case Glyph g -> out.appendCodePoint(g.codePoint());
      case Text t -> out.append(t.text());
      case Space s -> out.append(' ');
      case Fraction f -> {
        wrapped(f.num(), f.numWrapped(), out);
        out.append(f.spaced() ? " / " : "/");
        wrapped(f.den(), f.denWrapped(), out);
      }
      case Scripts s -> {
        flatten(s.base(), out);
        if (s.sub() != null) {
          script(s.sub(), s.subForm(), false, out);
        }
        if (s.sup() != null) {
          script(s.sup(), s.supForm(), true, out);
        }
      }
      case Radical r -> {
        String index = r.index() == null ? "" : flatten(r.index());
        out.append(
            switch (index) {
              case "" -> "√";
              case "3" -> "∛";
              case "4" -> "∜";
              default -> throw new IllegalArgumentException("a radical of index " + index);
            });
        wrapped(r.radicand(), r.wrapped(), out);
      }
      case Delimited d -> {
        if (d.open() != 0) {
          out.appendCodePoint(d.open());
        }
        flatten(d.body(), out);
        if (d.close() != 0) {
          out.appendCodePoint(d.close());
        }
      }
      case Matrix m -> {
        out.append('[');
        for (int r = 0; r < m.rows().size(); r++) {
          out.append(r > 0 ? ", [" : "[");
          items(m.rows().get(r), out);
          out.append(']');
        }
        out.append(']');
      }
      case BigOp b -> {
        flatten(b.operator(), out);
        if (b.lower() != null || b.upper() != null) {
          out.append('[');
          if (b.lower() != null) {
            flatten(b.lower(), out);
          }
          if (b.upper() != null) {
            out.append(", ");
            flatten(b.upper(), out);
          }
          out.append(']');
        }
        out.append(' ');
        flatten(b.body(), out);
      }
      case Piecewise p -> {
        out.append("piecewise(");
        for (Piecewise.Piece q : p.pieces()) {
          flatten(q.value(), out);
          out.append(", ");
          flatten(q.condition(), out);
          out.append(", ");
        }
        flatten(p.otherwise(), out);
        out.append(')');
      }
    }
  }

  private static void items(List<Layout> items, StringBuilder out) {
    for (int k = 0; k < items.size(); k++) {
      if (k > 0) {
        out.append(", ");
      }
      flatten(items.get(k), out);
    }
  }

  private static void wrapped(Layout layout, boolean wrapped, StringBuilder out) {
    if (wrapped) {
      out.append('(');
    }
    flatten(layout, out);
    if (wrapped) {
      out.append(')');
    }
  }

  private static void script(Layout script, Scripts.Form form, boolean sup, StringBuilder out) {
    String text = flatten(script);
    switch (form) {
      case CARET -> out.append('^').append(text);
      case CARET_WRAPPED -> out.append("^(").append(text).append(')');
      case CHARACTERS -> {
        String characters = sup ? PrintForm.superscript(text) : PrintForm.subscripts(text);
        if (characters == null) {
          throw new IllegalArgumentException("no script characters for " + text);
        }
        out.append(characters);
      }
      case APPENDED -> out.append(text);
      case UNDERSCORE -> out.append('_').append(text);
    }
  }
}
