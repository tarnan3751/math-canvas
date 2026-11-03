package mathcanvas.engine.parse;

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
 * The bracket constructor's rule ({@code docs/language.md} §13.2), which the parser applies to
 * {@code […]} and the evaluator applies again after evaluation: a Vector when every element is a
 * scalar, a Matrix when every element is a vector and all have one length, a List otherwise.
 */
public final class Brackets {

  /** The operations whose values are sets or truth values, never scalars (Phase 8a). */
  private static final java.util.Set<String> NOT_SCALAR =
      java.util.Set.of(
          "union",
          "intersect",
          "setdiff",
          "symdiff",
          "subsets",
          "cartesian",
          "and",
          "or",
          "xor",
          "not",
          "implies");

  private Brackets() {}

  /**
   * Builds the node for {@code [items]}.
   *
   * @param items the elements
   * @return a {@link Vector}, a {@link Matrix} or a {@link ListExpr}
   */
  public static Expr of(List<Expr> items) {
    if (items.isEmpty()) {
      return ListExpr.of(items);
    }
    if (allScalar(items)) {
      return Vector.of(items);
    }
    if (items.getFirst() instanceof Vector first
        && items.stream()
            .allMatch(e -> e instanceof Vector v && v.items().size() == first.items().size())) {
      return Matrix.of(items.stream().map(e -> ((Vector) e).items()).toList());
    }
    return ListExpr.of(items);
  }

  /**
   * Whether every expression of a list is scalar.
   *
   * @param items the expressions
   * @return true if all are scalar
   */
  public static boolean allScalar(List<Expr> items) {
    for (Expr item : items) {
      if (!isScalar(item)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether an expression is scalar: a number, a constant other than the domains, a symbol, or
   * arithmetic, function application and the unevaluated forms over scalars. Truth values,
   * relations, collections and lambdas are not.
   *
   * @param e the expression
   * @return true if it is scalar
   */
  public static boolean isScalar(Expr e) {
    return switch (e) {
      case Num n -> true;
      case Symbol s -> true;
      case Constant c ->
          switch (c.kind()) {
            case PI, E, INFINITY, UNDEFINED -> true;
            case INTEGERS, RATIONALS, REALS, COMPLEXES -> false;
          };
      case Sum s -> allScalar(s.terms());
      case Product p -> allScalar(p.factors());
      case Power p -> isScalar(p.base()) && isScalar(p.exponent());
      case Apply a -> !NOT_SCALAR.contains(a.head().name()) && allScalar(a.args());
      case Derivative d -> isScalar(d.expr());
      case Integral i ->
          isScalar(i.integrand())
              && (!i.isDefinite() || (isScalar(i.lower()) && isScalar(i.upper())));
      case Limit l -> isScalar(l.expr()) && isScalar(l.point());
      case BigSum s -> isScalar(s.body()) && isScalar(s.from()) && isScalar(s.to());
      case BigProduct s -> isScalar(s.body()) && isScalar(s.from()) && isScalar(s.to());
      case Bool b -> false;
      case Lambda l -> false;
      case Relation r -> false;
      case ListExpr l -> false;
      case Vector v -> false;
      case Matrix m -> false;
      case SetExpr s -> false;
      case Interval i -> false;
      case Piecewise p -> false;
    };
  }
}
