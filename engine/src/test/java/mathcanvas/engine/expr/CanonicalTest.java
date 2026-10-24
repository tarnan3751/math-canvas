package mathcanvas.engine.expr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.List;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

class CanonicalTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol F = Symbol.of("f");
  private static final Expr UNDEFINED = Constant.UNDEFINED;
  private static final Expr INF = Constant.INFINITY;

  private static Num n(long k) {
    return Num.of(k);
  }

  private static Num q(long p, long d) {
    return Num.of(Rational.of(p, d));
  }

  private static Num r(String text) {
    return Num.of(Real.of(text));
  }

  private static Expr c(Expr e) {
    return Canonical.of(e);
  }

  private static Power pow(Expr b, Expr e) {
    return Power.of(b, e);
  }

  @Test
  void examplesFromTheBriefAndThePlan() {
    assertThat(c(Sum.of(X, Product.of(n(2), X)))).isEqualTo(Product.of(n(3), X));
    assertThat(c(Exprs.neg(pow(n(2), n(2))))).isEqualTo(n(-4));
    assertThat(c(pow(n(2), pow(n(3), n(2))))).isEqualTo(n(512));
    assertThat(c(Sum.of(Exprs.div(n(1), n(3)), Exprs.div(n(1), n(6))))).isEqualTo(q(1, 2));
    assertThat(c(Sum.of(q(1, 3), q(1, 6)))).isEqualTo(q(1, 2));
    assertThat(c(Sum.of(Product.of(n(1), Exprs.inv(n(3))), q(1, 6)))).isEqualTo(q(1, 2));
  }

  @Test
  void sumsFlattenFoldCollectAndSort() {
    assertThat(c(Sum.of(X, X))).isEqualTo(Product.of(n(2), X));
    assertThat(c(Exprs.sub(X, X))).isEqualTo(Num.ZERO);
    assertThat(c(Sum.of(Product.of(n(2), X), Product.of(n(3), X)))).isEqualTo(Product.of(n(5), X));
    assertThat(c(Sum.of(Y, Sum.of(n(1), X), n(2)))).isEqualTo(Sum.of(n(3), X, Y));
    assertThat(c(Sum.of(X, n(0)))).isEqualTo(X);
    assertThat(c(Sum.of(n(2), n(-2)))).isEqualTo(Num.ZERO);
    assertThat(c(Sum.of(Product.of(n(2), X, Y), Product.of(Y, X))))
        .isEqualTo(Product.of(n(3), X, Y));
    // Like terms meet even when they do not sort together.
    assertThat(c(Sum.of(X, Y, X))).isEqualTo(Sum.of(Product.of(n(2), X), Y));
    // A coefficient sum of one leaves a sum to be flattened into the outer one.
    Expr ab = Sum.of(X, Y);
    assertThat(c(Sum.of(Product.of(n(2), ab), Exprs.neg(ab), Exprs.neg(X)))).isEqualTo(Y);
  }

  @Test
  void productsFlattenFoldCombineAndSort() {
    assertThat(c(Product.of(X, X))).isEqualTo(pow(X, n(2)));
    assertThat(c(Product.of(X, Exprs.inv(X)))).isEqualTo(Num.ONE);
    assertThat(c(Product.of(pow(X, Symbol.of("a")), pow(X, Symbol.of("b")))))
        .isEqualTo(pow(X, Sum.of(Symbol.of("a"), Symbol.of("b"))));
    assertThat(c(Product.of(Y, n(2), Product.of(X, n(3))))).isEqualTo(Product.of(n(6), X, Y));
    assertThat(c(Product.of(X, n(1)))).isEqualTo(X);
    assertThat(c(Product.of(X, n(0)))).isEqualTo(Num.ZERO);
    assertThat(c(Exprs.neg(X))).isEqualTo(Product.of(n(-1), X));
    assertThat(c(Product.of(n(2), Sum.of(X, n(1))))).isEqualTo(Product.of(n(2), Sum.of(n(1), X)));
    assertThat(c(Product.of(Exprs.sqrt(n(2)), Exprs.sqrt(n(2))))).isEqualTo(n(2));
    Expr root = pow(Product.of(X, Y), q(1, 2));
    assertThat(c(Product.of(root, root))).isEqualTo(Product.of(X, Y));
    assertThat(c(Product.of(Exprs.inv(X), root, root))).isEqualTo(Y);
    // A number and a power of the same number stay apart.
    assertThat(c(Product.of(n(2), pow(n(2), X)))).isEqualTo(Product.of(n(2), pow(n(2), X)));
  }

  @Test
  void aCollapsedPowerOfAPowerMergesWithItsBase() {
    // The parse of (x^-3 x^2)^(1/2) (x^-3 x^2)^(1/2) x: the group of (x^-1)^(1/2) collapses to
    // x^-1, which must then combine with x in the same pass.
    Expr half = pow(pow(X, n(-1)), q(1, 2));
    assertThat(c(Product.of(half, half, X))).isEqualTo(Num.ONE);
    Expr minusHalf = pow(pow(X, n(-1)), q(-1, 2));
    assertThat(c(Product.of(minusHalf, minusHalf, X))).isEqualTo(pow(X, n(2)));
    Expr third = pow(pow(X, n(2)), q(1, 3));
    Expr twoThirds = pow(pow(X, n(2)), q(2, 3));
    assertThat(c(Product.of(third, twoThirds, X, Y))).isEqualTo(Product.of(pow(X, n(3)), Y));
  }

  @Test
  void oneCanonicalisationIsAFixedPoint() {
    // Counterexamples to idempotence found by the property runs; each needed a second pass.
    Expr inverse = pow(X, n(-1));
    Expr whole = Product.of(pow(inverse, q(2, 3)), pow(inverse, q(1, 3)));
    assertFixedPoint(Product.of(whole, pow(whole, n(-1))), Num.ONE);
    for (Expr base : List.of(Symbol.of("a"), Constant.REALS)) {
      Expr cube = pow(base, n(3));
      Expr b = Product.of(pow(cube, n(4)), pow(cube, q(-1, 2)));
      assertFixedPoint(Product.of(pow(b, n(3)), pow(b, n(1))), pow(base, n(42)));
    }
    Expr m = Matrix.of(List.of(List.of(X, Y)));
    Expr term = Product.of(q(1, 6), m, X);
    assertFixedPoint(Exprs.sub(term, term), Product.of(n(0), m));
    Expr symbolic = Product.of(pow(Constant.REALS, q(1, 3)), X, pow(X, Exprs.sub(X, q(1, 2))));
    assertFixedPoint(
        symbolic, Product.of(pow(Constant.REALS, q(1, 3)), pow(X, Sum.of(q(1, 2), X))));
  }

  private static void assertFixedPoint(Expr raw, Expr expected) {
    Expr once = c(raw);
    assertThat(once).as("%s", raw).isEqualTo(expected);
    assertThat(c(once)).as("%s", raw).isEqualTo(once);
  }

  @Test
  void powers() {
    assertThat(c(pow(X, n(1)))).isEqualTo(X);
    assertThat(c(pow(X, n(0)))).isEqualTo(Num.ONE);
    assertThat(c(pow(n(0), n(0)))).isEqualTo(Num.ONE);
    assertThat(c(pow(n(1), X))).isEqualTo(Num.ONE);
    assertThat(c(pow(n(0), n(2)))).isEqualTo(Num.ZERO);
    assertThat(c(pow(n(0), q(1, 2)))).isEqualTo(Num.ZERO);
    assertThat(c(pow(n(0), n(-1)))).isEqualTo(UNDEFINED);
    assertThat(c(pow(n(0), Num.of(Complex.I)))).isEqualTo(UNDEFINED);
    assertThat(c(pow(n(0), X))).isEqualTo(pow(n(0), X));
    assertThat(c(pow(pow(X, Symbol.of("a")), n(2))))
        .isEqualTo(pow(X, Product.of(n(2), Symbol.of("a"))));
    assertThat(c(pow(pow(X, n(2)), q(1, 2)))).isEqualTo(pow(pow(X, n(2)), q(1, 2)));
    assertThat(c(pow(pow(X, q(1, 2)), n(2)))).isEqualTo(X);
    assertThat(c(pow(Product.of(n(2), X), n(2)))).isEqualTo(Product.of(n(4), pow(X, n(2))));
    assertThat(c(pow(Exprs.neg(X), n(2)))).isEqualTo(pow(X, n(2)));
    assertThat(c(pow(Product.of(X, Y), q(1, 2)))).isEqualTo(pow(Product.of(X, Y), q(1, 2)));
    assertThat(c(pow(Sum.of(X, n(1)), n(2)))).isEqualTo(pow(Sum.of(n(1), X), n(2)));
  }

  @Test
  void numericPowersFoldOnlyWhenNothingExactIsLost() {
    assertThat(c(pow(n(4), q(1, 2)))).isEqualTo(n(2));
    assertThat(c(pow(n(2), q(1, 2)))).isEqualTo(pow(n(2), q(1, 2)));
    assertThat(c(pow(n(-8), q(1, 3)))).isEqualTo(pow(n(-8), q(1, 3)));
    assertThat(c(pow(n(-4), q(1, 2)))).isEqualTo(Num.of(Complex.of(Int.ZERO, Int.TWO)));
    assertThat(c(pow(q(8, 27), q(2, 3)))).isEqualTo(q(4, 9));
    assertThat(c(pow(n(3), n(-1)))).isEqualTo(q(1, 3));
    assertThat(c(pow(Num.of(Complex.I), n(2)))).isEqualTo(n(-1));
    assertThat(c(pow(n(2), Num.of(Complex.I)))).isEqualTo(pow(n(2), Num.of(Complex.I)));
    assertThat(c(pow(n(1), Num.of(Complex.I)))).isEqualTo(Num.ONE);
    assertThat(c(pow(n(1), r("2.5")))).isEqualTo(r("1.0"));
    // An inexact operand: the result is inexact anyway, so it folds.
    assertThat(c(pow(r("2.0"), n(2)))).isEqualTo(r("4"));
    assertThat(c(pow(r("2.0"), q(1, 2))))
        .isEqualTo(Num.of(Real.of("1.414213562373095048801688724209698")));
    assertThat(c(pow(r("0.0"), n(-1)))).isEqualTo(UNDEFINED);
    // An integer power past MAX_FOLD_BITS stays; a Real base has no limit.
    assertThat(c(pow(n(2), n(1 << 21)))).isEqualTo(pow(n(2), n(1 << 21)));
    assertThat(c(pow(n(2), n(1 << 19)))).isInstanceOf(Num.class);
    assertThat(c(pow(n(1), n(Long.MAX_VALUE)))).isEqualTo(Num.ONE);
    assertThat(c(pow(n(-1), n(Long.MAX_VALUE)))).isEqualTo(n(-1));
    assertThat(c(pow(r("1.5"), n(1 << 21)))).isInstanceOf(Num.class);
    assertThat(c(pow(n(2), q(1, 1_000_000_000)))).isEqualTo(pow(n(2), q(1, 1_000_000_000)));
    // A rational base counts its numerator's and denominator's bits together.
    assertThat(c(pow(q(2, 3), n(-(1 << 19))))).isEqualTo(pow(q(2, 3), n(-(1 << 19))));
    assertThat(c(pow(q(2, 3), n(1 << 17)))).isInstanceOf(Num.class);
  }

  @Test
  void identityRulesNeedExactNumbers() {
    assertThat(c(Sum.of(X, r("0.0")))).isEqualTo(Sum.of(r("0.0"), X));
    assertThat(c(Product.of(r("1.0"), X))).isEqualTo(Product.of(r("1.0"), X));
    assertThat(c(pow(X, r("1.0")))).isEqualTo(pow(X, r("1.0")));
    assertThat(c(pow(X, r("0.0")))).isEqualTo(pow(X, r("0.0")));
    assertThat(c(Sum.of(Product.of(n(2), X), Product.of(r("-2.0"), X))))
        .isEqualTo(Product.of(r("0.0"), X));
    assertThat(c(Sum.of(r("1.0"), r("-1.0"), X))).isEqualTo(Sum.of(r("0.0"), X));
  }

  @Test
  void realsFoldInTheContextGiven() {
    Expr third = Sum.of(r("1.0"), Exprs.inv(n(3)));
    assertThat(Canonical.of(third, new MathContext(5))).isEqualTo(Num.of(Real.of("1.3333")));
    assertThat(c(third)).isEqualTo(Num.of(Real.of("1.333333333333333333333333333333333")));
  }

  @Test
  void infinityAndUndefined() {
    assertThat(c(Exprs.sub(INF, INF))).isEqualTo(UNDEFINED);
    assertThat(c(Exprs.sub(UNDEFINED, UNDEFINED))).isEqualTo(UNDEFINED);
    assertThat(c(Product.of(INF, Exprs.inv(INF)))).isEqualTo(UNDEFINED);
    assertThat(c(Product.of(n(0), INF))).isEqualTo(UNDEFINED);
    assertThat(c(Product.of(n(0), Sum.of(n(1), INF)))).isEqualTo(UNDEFINED);
    assertThat(c(Sum.of(X, UNDEFINED))).isEqualTo(UNDEFINED);
    assertThat(c(Product.of(X, UNDEFINED))).isEqualTo(UNDEFINED);
    assertThat(c(pow(UNDEFINED, n(0)))).isEqualTo(UNDEFINED);
    assertThat(c(pow(INF, n(0)))).isEqualTo(UNDEFINED);
    assertThat(c(pow(n(1), INF))).isEqualTo(UNDEFINED);
    // Infinity is otherwise carried, not evaluated.
    assertThat(c(Sum.of(INF, INF))).isEqualTo(Product.of(n(2), INF));
    assertThat(c(Sum.of(INF, n(1)))).isEqualTo(Sum.of(n(1), INF));
    // An application of infinity is opaque: f(∞) - f(∞) is 0.
    Expr fInf = Apply.of(F, INF);
    assertThat(c(Exprs.sub(fInf, fInf))).isEqualTo(Num.ZERO);
  }

  @Test
  void matricesAndVectorsDoNotCommute() {
    Expr a = Matrix.of(List.of(List.of(n(1), n(2)), List.of(n(3), n(4))));
    Expr b = Matrix.of(List.of(List.of(n(0), n(1)), List.of(n(1), n(0))));
    Expr v = Vector.of(n(1), X);
    assertThat(c(Product.of(b, a))).isEqualTo(Product.of(b, a));
    assertThat(c(Product.of(b, Y, a, n(2), X))).isEqualTo(Product.of(n(2), X, Y, b, a));
    assertThat(c(Product.of(a, a))).isEqualTo(Product.of(a, a));
    assertThat(c(pow(Product.of(a, b), n(2)))).isEqualTo(pow(Product.of(a, b), n(2)));
    assertThat(c(Product.of(v, a, Product.of(b, v)))).isEqualTo(Product.of(v, a, b, v));
    assertThat(c(Sum.of(a, a))).isEqualTo(Product.of(n(2), a));
    assertThat(c(Product.of(n(0), a))).isEqualTo(Product.of(n(0), a));
    // A zero coefficient with non-commuting factors is the zero of their shape.
    assertThat(c(Product.of(n(0), X, a))).isEqualTo(Product.of(n(0), a));
    assertThat(c(Product.of(X, a, n(0), b, Y))).isEqualTo(Product.of(n(0), a, b));
    assertThat(c(Product.of(n(0), INF, a))).isEqualTo(UNDEFINED);
    assertThat(c(Sum.of(Product.of(n(0), INF, a), n(1)))).isEqualTo(UNDEFINED);
    assertThat(c(Sum.of(Product.of(n(0), X, a), Y))).isEqualTo(Sum.of(Y, Product.of(n(0), a)));
    assertThat(c(Exprs.sub(a, a))).isEqualTo(Product.of(n(0), a));
    assertThat(c(Exprs.sub(Product.of(X, a), Product.of(X, a)))).isEqualTo(Product.of(n(0), a));
    assertThat(c(Sum.of(Exprs.sub(a, a), a))).isEqualTo(a);
    // A zero power of a non-commuting base is the identity of its shape, so it stays.
    assertThat(c(pow(a, n(0)))).isEqualTo(pow(a, n(0)));
    assertThat(c(pow(pow(Product.of(a, b), X), n(0))))
        .isEqualTo(pow(pow(Product.of(a, b), X), n(0)));
    assertThat(c(pow(a, n(1)))).isEqualTo(a);
    Expr rootA = pow(Product.of(X, a), q(1, 2));
    assertThat(c(Exprs.sub(rootA, rootA))).isEqualTo(Product.of(n(0), rootA));
    // Sums, products and powers built from matrices do not commute either.
    Expr aPlusOne = Sum.of(n(1), a);
    assertThat(c(Product.of(b, aPlusOne))).isEqualTo(Product.of(b, aPlusOne));
    Expr root = pow(Product.of(X, a), q(1, 2));
    assertThat(c(Product.of(b, root, X))).isEqualTo(Product.of(X, b, root));
  }

  @Test
  void relations() {
    Relation.Op lt = Relation.Op.LT;
    assertThat(c(Relation.of(lt, n(2), n(3)))).isEqualTo(Bool.TRUE);
    assertThat(c(Relation.of(Relation.Op.EQ, n(1), r("1.0")))).isEqualTo(Bool.TRUE);
    assertThat(c(Relation.of(Relation.Op.NE, n(1), Num.of(Complex.I)))).isEqualTo(Bool.TRUE);
    assertThat(c(Relation.of(Relation.Op.GE, Sum.of(n(1), n(1)), n(2)))).isEqualTo(Bool.TRUE);
    assertThat(c(Relation.of(lt, n(1), Num.of(Complex.I))))
        .isEqualTo(Relation.of(lt, n(1), Num.of(Complex.I)));
    assertThat(c(Relation.of(lt, n(5), INF))).isEqualTo(Bool.TRUE);
    assertThat(c(Relation.of(Relation.Op.GT, Exprs.neg(INF), n(5)))).isEqualTo(Bool.FALSE);
    assertThat(c(Relation.of(Relation.Op.EQ, n(5), INF))).isEqualTo(Bool.FALSE);
    assertThat(c(Relation.of(lt, Num.of(Complex.I), INF)))
        .isEqualTo(Relation.of(lt, Num.of(Complex.I), INF));
    assertThat(c(Relation.of(lt, X, Sum.of(n(1), n(2))))).isEqualTo(Relation.of(lt, X, n(3)));
    // No moving of terms across, and membership is not decided here.
    assertThat(c(Relation.of(Relation.Op.EQ, Sum.of(X, n(1)), n(0))))
        .isEqualTo(Relation.of(Relation.Op.EQ, Sum.of(n(1), X), n(0)));
    assertThat(c(Relation.of(Relation.Op.IN, n(2), Constant.INTEGERS)))
        .isEqualTo(Relation.of(Relation.Op.IN, n(2), Constant.INTEGERS));
  }

  @Test
  void setsAreSortedWithoutDuplicates() {
    assertThat(c(SetExpr.of(Y, X, Sum.of(n(1), n(1)), n(2), r("2.0"))))
        .isEqualTo(SetExpr.of(n(2), r("2.0"), X, Y));
    assertThat(c(SetExpr.of())).isEqualTo(SetExpr.of());
  }

  @Test
  void otherKindsKeepTheirStructure() {
    Expr two = Sum.of(n(1), n(1));
    assertThat(c(ListExpr.of(Y, two, Y))).isEqualTo(ListExpr.of(Y, n(2), Y));
    assertThat(c(Vector.of(two))).isEqualTo(Vector.of(n(2)));
    assertThat(c(Apply.of(F, two, X))).isEqualTo(Apply.of(F, n(2), X));
    assertThat(c(Apply.of(Symbol.of("sin"), n(0)))).isEqualTo(Apply.of(Symbol.of("sin"), n(0)));
    assertThat(c(Lambda.of(X, Sum.of(X, X)))).isEqualTo(Lambda.of(X, Product.of(n(2), X)));
    assertThat(c(Integral.indefinite(Sum.of(X, X), X)))
        .isEqualTo(Integral.indefinite(Product.of(n(2), X), X));
    assertThat(c(Integral.definite(X, X, two, two))).isEqualTo(Integral.definite(X, X, n(2), n(2)));
    assertThat(c(Derivative.of(Product.of(X, X), X, 2)))
        .isEqualTo(Derivative.of(pow(X, n(2)), X, 2));
    assertThat(c(Limit.of(X, X, two, Limit.Direction.LEFT)))
        .isEqualTo(Limit.of(X, X, n(2), Limit.Direction.LEFT));
    assertThat(c(BigSum.of(X, X, n(1), two))).isEqualTo(BigSum.of(X, X, n(1), n(2)));
    assertThat(c(BigProduct.of(X, X, n(1), two))).isEqualTo(BigProduct.of(X, X, n(1), n(2)));
    assertThat(c(Interval.of(two, INF, true, false)))
        .isEqualTo(Interval.of(n(2), INF, true, false));
    assertThat(c(Matrix.of(List.of(List.of(two))))).isEqualTo(Matrix.of(List.of(List.of(n(2)))));
    assertThat(c(Piecewise.of(List.of(new Piecewise.Piece(two, Bool.TRUE)), UNDEFINED)))
        .isEqualTo(Piecewise.of(List.of(new Piecewise.Piece(n(2), Bool.TRUE)), UNDEFINED));
  }

  @Test
  void deepRawChainsDoNotRecursePerOperand() {
    Expr sum = Symbol.of("x0");
    for (int k = 1; k < 100_000; k++) {
      sum = Sum.of(sum, Symbol.of("x" + k));
    }
    assertThat(((Sum) c(sum)).terms()).hasSize(100_000);
  }
}
