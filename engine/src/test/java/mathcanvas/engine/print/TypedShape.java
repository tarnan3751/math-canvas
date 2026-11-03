package mathcanvas.engine.print;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;

/**
 * The tree the parser builds for the faithful printing of a tree: every number in the raw shape it
 * is typed as ({@link PrintForm#typed}: {@code -3} is {@code -(3)}, {@code 1/2} is {@code 1·2⁻¹}),
 * except the {@code -1} of a {@code -x} and of a divisor, which the printer writes as a sign and a
 * {@code /}. Shared by the round-trip properties and the parser's fuzz test.
 */
public final class TypedShape {

  private TypedShape() {}

  /**
   * Returns the typed shape of a tree.
   *
   * @param e the tree
   * @return the tree with every number in the shape it is typed as
   */
  public static Expr of(Expr e) {
    return switch (e) {
      case Num n -> PrintForm.typed(n.value());
      case mathcanvas.engine.expr.Based b -> b;
      case Constant c -> c;
      case Bool b -> b;
      case Symbol s -> s;
      case mathcanvas.engine.expr.Unit u -> u;
      case Sum s -> Sum.of(map(s.terms()));
      case Product p -> {
        if (PrintForm.isNegation(p)) {
          yield Product.of(Num.MINUS_ONE, of(p.factors().get(1)));
        }
        List<Expr> factors = new ArrayList<>();
        for (int k = 0; k < p.factors().size(); k++) {
          Expr f = p.factors().get(k);
          factors.add(
              k > 0 && PrintForm.isReciprocal(f)
                  ? Power.of(of(((Power) f).base()), Num.MINUS_ONE)
                  : of(f));
        }
        yield Product.of(factors);
      }
      case Power p -> Power.of(of(p.base()), PrintForm.isSqrt(p) ? p.exponent() : of(p.exponent()));
      case Apply a -> Apply.of(a.head(), map(a.args()));
      case Lambda l -> Lambda.of(l.params(), of(l.body()));
      case Relation r -> Relation.of(r.op(), of(r.left()), of(r.right()));
      case ListExpr l -> ListExpr.of(map(l.items()));
      case Vector v -> Vector.of(map(v.items()));
      case SetExpr s -> SetExpr.of(map(s.items()));
      case Matrix m -> Matrix.of(m.rows().stream().map(TypedShape::map).toList());
      case Interval i -> i;
      case Piecewise p -> p;
      case Derivative d -> Derivative.of(of(d.expr()), d.var(), d.order());
      case Integral i ->
          i.isDefinite()
              ? Integral.definite(of(i.integrand()), i.var(), of(i.lower()), of(i.upper()))
              : Integral.indefinite(of(i.integrand()), i.var());
      case Limit l -> Limit.of(of(l.expr()), l.var(), of(l.point()), l.direction());
      case BigSum s -> BigSum.of(of(s.body()), s.var(), of(s.from()), of(s.to()));
      case BigProduct s -> BigProduct.of(of(s.body()), s.var(), of(s.from()), of(s.to()));
    };
  }

  private static List<Expr> map(List<Expr> items) {
    return items.stream().map(TypedShape::of).toList();
  }
}
