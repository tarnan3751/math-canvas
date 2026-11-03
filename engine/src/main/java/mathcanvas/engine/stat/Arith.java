package mathcanvas.engine.stat;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;

/**
 * Exact arithmetic on expressions for the statistics: each operation builds the node and takes it
 * to canonical form at the context's precision, which folds numbers exactly ({@code 1/0} is {@code
 * undefined}) and keeps a constant or a radical as it is.
 */
final class Arith {

  private static final Num HALF = Num.of(Rational.of(1, 2));

  private final MathContext mc;
  private final Cancellation cancellation;

  Arith(Context ctx) {
    this.mc = ctx.precision();
    this.cancellation = ctx.cancellation();
  }

  Expr canon(Expr e) {
    return Canonical.of(e, mc, cancellation);
  }

  static Num num(long n) {
    return Num.of(n);
  }

  static Num num(BigInteger n) {
    return Num.of(Int.of(n));
  }

  Expr add(Expr a, Expr b) {
    return canon(Sum.of(a, b));
  }

  Expr sum(List<Expr> terms) {
    return terms.isEmpty() ? Num.ZERO : terms.size() == 1 ? terms.getFirst() : canon(Sum.of(terms));
  }

  Expr sub(Expr a, Expr b) {
    return canon(Sum.of(a, Product.of(Num.MINUS_ONE, b)));
  }

  Expr mul(Expr a, Expr b) {
    return canon(Product.of(a, b));
  }

  Expr mul(Expr a, Expr b, Expr c) {
    return canon(Product.of(a, b, c));
  }

  Expr div(Expr a, Expr b) {
    return canon(Product.of(a, Power.of(b, Num.MINUS_ONE)));
  }

  Expr pow(Expr a, Expr k) {
    return canon(Power.of(a, k));
  }

  Expr pow(Expr a, long k) {
    return pow(a, Num.of(k));
  }

  Expr sqrt(Expr a) {
    return canon(Power.of(a, HALF));
  }
}
