package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Support.add;
import static mathcanvas.engine.solve.Support.div;
import static mathcanvas.engine.solve.Support.mul;
import static mathcanvas.engine.solve.Support.num;
import static mathcanvas.engine.solve.Support.pow;
import static mathcanvas.engine.solve.Support.q;
import static mathcanvas.engine.solve.Support.sub;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Steps;

/**
 * The root formulas: the quadratic formula over any coefficients free of the unknown, and the cubic
 * (Cardano, with the trigonometric form for three real roots) and quartic (Ferrari, through the
 * resolvent cubic) formulas over rational coefficients. The roots are exact and raw; the caller
 * simplifies them. See Cohen, <i>Computer Algebra and Symbolic Computation: Mathematical
 * Methods</i>, §4.2, and Nickalls, "A new approach to solving the cubic", Math. Gazette 77 (1993),
 * for the trigonometric form.
 */
final class Formulas {

  private static final Num HALF = Num.of(Rational.of(1, 2));
  private static final Num THIRD = Num.of(Rational.of(1, 3));

  private Formulas() {}

  /**
   * The quadratic formula for {@code a·x² + b·x + c}.
   *
   * @param delta the discriminant {@code b² − 4ac}, expanded
   * @param square the completed square's sides: {@code (x + b/(2a))²} and {@code Δ/(4a²)}
   * @param roots the two roots, {@code (−b − √Δ)/(2a)} then {@code (−b + √Δ)/(2a)}, raw
   */
  record Quadratic(Expr delta, Expr squareLeft, Expr squareRight, List<Expr> roots) {}

  static Quadratic quadratic(Polynomial a, Polynomial b, Polynomial c, Symbol x) {
    Expr delta = b.pow(2).subtract(a.multiply(c).multiply(q(4))).toExpr();
    Expr ae = a.toExpr();
    Expr be = b.toExpr();
    Expr twoA = Product.of(Num.of(2), ae);
    Expr left = Power.of(Sum.of(x, Exprs.div(be, twoA)), Num.of(2));
    Expr right = Exprs.div(delta, Product.of(Num.of(4), Power.of(ae, Num.of(2))));
    Expr root = Exprs.sqrt(delta);
    List<Expr> roots =
        List.of(
            Exprs.div(Sum.of(Exprs.neg(be), Exprs.neg(root)), twoA),
            Exprs.div(Sum.of(Exprs.neg(be), root), twoA));
    return new Quadratic(delta, left, right, roots);
  }

  /**
   * The cubic's form: {@code binomial} ({@code p = 0}), {@code trigonometric} or {@code radical}.
   */
  record Cubic(String form, List<Expr> roots) {}

  /**
   * The cubic formula for {@code c[3]x³ + c[2]x² + c[1]x + c[0]}, rational coefficients,
   * square-free (its discriminant is not zero). The first root is real: the largest real root.
   */
  static Cubic cubic(RealNumeric[] c) {
    RealNumeric a = c[3];
    RealNumeric b = div(c[2], a);
    RealNumeric cc = div(c[1], a);
    RealNumeric d = div(c[0], a);
    // x = t − b/3: t³ + p·t + q
    RealNumeric p = sub(cc, div(mul(b, b), q(3)));
    RealNumeric qq = add(sub(div(mul(q(2), pow(b, 3)), q(27)), div(mul(b, cc), q(3))), d);
    Expr shift = num(div(b, q(-3)));
    List<Expr> ts = new ArrayList<>();
    String form;
    RealNumeric disc = sub(mul(q(-4), pow(p, 3)), mul(q(27), mul(qq, qq)));
    if (p.isZero()) {
      // t³ = −q: the real cube root times the cube roots of unity
      form = "binomial";
      Expr rho = realCubeRoot(qq.negate(), 0, Num.ZERO, qq.negate().signum());
      Expr omega =
          Sum.of(
              Num.of(Rational.of(-1, 2)),
              Product.of(HALF, Exprs.sqrt(Num.of(3)), Num.of(Complex.I)));
      Expr omegaBar =
          Sum.of(
              Num.of(Rational.of(-1, 2)),
              Product.of(Num.of(Rational.of(-1, 2)), Exprs.sqrt(Num.of(3)), Num.of(Complex.I)));
      ts.add(rho);
      ts.add(Product.of(omega, rho));
      ts.add(Product.of(omegaBar, rho));
    } else if (disc.signum() > 0) {
      // three real roots: t_k = 2√(−p/3)·cos(acos((3q/(2p))·√(−3/p))/3 − 2πk/3)
      form = "trigonometric";
      Expr m = Product.of(Num.of(2), Exprs.sqrt(num(div(p, q(-3)))));
      Expr arg = Product.of(num(div(mul(q(3), qq), mul(q(2), p))), Exprs.sqrt(num(div(q(-3), p))));
      Expr theta = Product.of(THIRD, Apply.of(Symbol.of("acos"), arg));
      for (int k = 0; k < 3; k++) {
        Expr angle =
            k == 0
                ? theta
                : Sum.of(theta, Product.of(Num.of(Rational.of(-2L * k, 3)), Constant.PI));
        ts.add(Product.of(m, Apply.of(Symbol.of("cos"), angle)));
      }
    } else {
      // one real root: C + D with C, D the real cube roots of −q/2 ± √(q²/4 + p³/27)
      form = "radical";
      RealNumeric s = add(div(mul(qq, qq), q(4)), div(pow(p, 3), q(27)));
      Expr sqrtS = Exprs.sqrt(num(s));
      RealNumeric minusHalfQ = div(qq, q(-2));
      // signs decided exactly: −q/2 ± √S against (q/2)² = S − p³/27
      int sign1 = qq.signum() < 0 || p.signum() > 0 || qq.isZero() ? 1 : -1;
      int sign2 = qq.signum() < 0 && p.signum() < 0 ? 1 : -1;
      Expr cr = realCubeRoot(minusHalfQ, 1, sqrtS, sign1);
      Expr dr = realCubeRoot(minusHalfQ, -1, sqrtS, sign2);
      Expr sum = Sum.of(cr, dr);
      Expr half = Product.of(Num.of(Rational.of(-1, 2)), sum);
      Expr imag =
          Product.of(HALF, Exprs.sqrt(Num.of(3)), Num.of(Complex.I), Sum.of(cr, Exprs.neg(dr)));
      ts.add(sum);
      ts.add(Sum.of(half, Exprs.neg(imag)));
      ts.add(Sum.of(half, imag));
    }
    List<Expr> roots = new ArrayList<>(3);
    for (Expr t : ts) {
      roots.add(Sum.of(t, shift));
    }
    return new Cubic(form, roots);
  }

  /**
   * The real cube root of {@code r + s·root}, whose sign is {@code sign}, as a power of a positive
   * base: {@code (r + s·root)^(1/3)}, or {@code −(−r − s·root)^(1/3)}.
   */
  private static Expr realCubeRoot(RealNumeric r, int s, Expr root, int sign) {
    RealNumeric rr = sign >= 0 ? r : r.negate();
    int ss = sign >= 0 ? s : -s;
    Expr base = ss == 0 ? num(rr) : Sum.of(num(rr), Product.of(Num.of(ss), root));
    Expr cube = Power.of(base, THIRD);
    return sign >= 0 ? cube : Exprs.neg(cube);
  }

  /**
   * The quartic formula for {@code Σ c[k]xᵏ}, rational coefficients, square-free: Ferrari's method
   * through the resolvent cubic {@code 8m³ + 8pm² + (2p² − 8r)m − q² = 0} of the depressed quartic
   * {@code y⁴ + py² + qy + r}, with a rational root of the resolvent when it has one (a positive
   * one first) and otherwise its largest real root, which is positive.
   */
  static List<Expr> quartic(RealNumeric[] c) {
    RealNumeric a = c[4];
    RealNumeric bb = div(c[3], a);
    RealNumeric cc = div(c[2], a);
    RealNumeric dd = div(c[1], a);
    RealNumeric ee = div(c[0], a);
    RealNumeric p = sub(cc, div(mul(q(3), mul(bb, bb)), q(8)));
    RealNumeric qq = add(sub(dd, div(mul(bb, cc), q(2))), div(pow(bb, 3), q(8)));
    RealNumeric r =
        add(
            sub(sub(ee, div(mul(bb, dd), q(4))), div(mul(q(3), pow(bb, 4)), q(256))),
            div(mul(mul(bb, bb), cc), q(16)));
    Expr shift = num(div(bb, q(-4)));
    List<Expr> ys = new ArrayList<>(4);
    if (qq.isZero()) {
      // biquadratic: y² = −p/2 ± √((p² − 4r)/4)
      Expr root = Exprs.sqrt(num(div(sub(mul(p, p), mul(q(4), r)), q(4))));
      Expr half = num(div(p, q(-2)));
      for (Expr z : List.of(Sum.of(half, Exprs.neg(root)), Sum.of(half, root))) {
        ys.add(Exprs.neg(Exprs.sqrt(z)));
        ys.add(Exprs.sqrt(z));
      }
    } else {
      Expr m = resolventRoot(p, qq, r);
      Expr s = Exprs.sqrt(Product.of(Num.of(2), m));
      Expr base = Sum.of(Product.of(num(p), Num.of(-2)), Product.of(Num.of(-2), m));
      Expr qOverS = Product.of(num(mul(q(2), qq)), Exprs.inv(s));
      Expr r1 = Exprs.sqrt(Sum.of(base, Exprs.neg(qOverS)));
      Expr r2 = Exprs.sqrt(Sum.of(base, qOverS));
      ys.add(Product.of(HALF, Sum.of(s, Exprs.neg(r1))));
      ys.add(Product.of(HALF, Sum.of(s, r1)));
      ys.add(Product.of(HALF, Sum.of(Exprs.neg(s), Exprs.neg(r2))));
      ys.add(Product.of(HALF, Sum.of(Exprs.neg(s), r2)));
    }
    List<Expr> roots = new ArrayList<>(4);
    for (Expr y : ys) {
      roots.add(Sum.of(y, shift));
    }
    return roots;
  }

  /** A non-zero root of the resolvent, rational when possible, else its largest real root. */
  private static Expr resolventRoot(RealNumeric p, RealNumeric qq, RealNumeric r) {
    RealNumeric[] res = {
      mul(qq, qq).negate(), sub(mul(q(2), mul(p, p)), mul(q(8), r)), mul(q(8), p), q(8)
    };
    Symbol m = Symbol.of("m");
    Polynomial poly = Polynomial.ZERO;
    for (int k = 0; k < 4; k++) {
      poly = poly.add(Polynomial.variable(m).pow(k).multiply(res[k]));
    }
    RealNumeric best = null;
    for (Factorization.Factor f : Factoring.factor(poly, Steps.NONE).factors()) {
      if (f.base().degree(m) == 1) {
        RealNumeric root = div(f.base().coefficient(0).negate(), f.base().coefficient(1));
        if (best == null || (best.signum() < 0 && root.signum() > 0)) {
          best = root;
        }
      }
    }
    if (best != null) {
      return num(best);
    }
    return cubic(res).roots().getFirst();
  }

  static RealNumeric[] coefficients(Polynomial p, int degree) {
    RealNumeric[] c = new RealNumeric[degree + 1];
    for (int k = 0; k <= degree; k++) {
      c[k] = p.coefficient(k);
    }
    return c;
  }
}
