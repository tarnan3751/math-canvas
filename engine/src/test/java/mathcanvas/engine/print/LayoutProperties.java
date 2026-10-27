package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.match.Operands;
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
import mathcanvas.engine.rule.Path;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The layout printer's oracle: a layout tree flattens to exactly the Unicode printer's text, for
 * raw trees printed as held and canonical values displayed; and every path in it names a
 * subexpression of the printed tree, below the path of the nearest node above it that has one.
 */
class LayoutProperties {

  @Provide
  Arbitrary<Expr> raw() {
    return ExprArbitraries.exprs();
  }

  @Provide
  Arbitrary<Expr> canonical() {
    return ExprArbitraries.exprs().map(Canonical::of);
  }

  @Property
  void aHeldTreeFlattensToItsUnicodeText(@ForAll("raw") Expr e) {
    Layout layout = LayoutPrinter.INSTANCE.print(e);
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo(UnicodePrinter.INSTANCE.print(e));
    paths(layout, e, Path.ROOT);
  }

  @Property
  void aDisplayedValueFlattensToItsUnicodeText(@ForAll("canonical") Expr c) {
    Layout layout = LayoutPrinter.INSTANCE.display(c);
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo(UnicodePrinter.INSTANCE.display(c));
    paths(layout, Display.of(c), Path.ROOT);
  }

  /** Checks the paths at and below {@code layout}, {@code above} the nearest path above it. */
  private static void paths(Layout layout, Expr printed, Path above) {
    Path here = above;
    if (layout.path() != null) {
      List<Integer> a = above.indices();
      List<Integer> p = layout.path().indices();
      assertThat(p.size()).as("%s under %s", p, a).isGreaterThanOrEqualTo(a.size());
      assertThat(p.subList(0, a.size())).as("%s under %s", p, a).isEqualTo(a);
      Expr e = printed;
      for (int i : p) {
        List<Expr> operands = Operands.of(e);
        assertThat(i).as("%s in %s", p, printed).isBetween(0, operands.size() - 1);
        e = operands.get(i);
      }
      here = layout.path();
    }
    for (Layout child : children(layout)) {
      paths(child, printed, here);
    }
  }

  private static List<Layout> children(Layout layout) {
    return switch (layout) {
      case Row r -> r.items();
      case Glyph g -> List.of();
      case Text t -> List.of();
      case Space s -> List.of();
      case Fraction f -> List.of(f.num(), f.den());
      case Scripts s -> nonNull(s.base(), s.sub(), s.sup());
      case Radical r -> nonNull(r.index(), r.radicand());
      case Delimited d -> List.of(d.body());
      case Matrix m -> m.rows().stream().flatMap(List::stream).toList();
      case BigOp b -> nonNull(b.operator(), b.lower(), b.upper(), b.body());
      case Piecewise p -> {
        List<Layout> out = new java.util.ArrayList<>();
        for (Piecewise.Piece q : p.pieces()) {
          out.add(q.value());
          out.add(q.condition());
        }
        out.add(p.otherwise());
        yield out;
      }
    };
  }

  private static List<Layout> nonNull(Layout... layouts) {
    return java.util.Arrays.stream(layouts).filter(l -> l != null).toList();
  }
}
