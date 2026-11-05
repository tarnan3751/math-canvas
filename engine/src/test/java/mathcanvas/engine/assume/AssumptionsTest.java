package mathcanvas.engine.assume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.assume.Facts.Bound;
import mathcanvas.engine.assume.Facts.Domain;
import mathcanvas.engine.assume.Facts.Parity;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Relation.Op;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

class AssumptionsTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol Z = Symbol.of("z");
  private static final Symbol N = Symbol.of("n");
  private static final Tri T = Tri.TRUE;
  private static final Tri F = Tri.FALSE;
  private static final Tri U = Tri.UNKNOWN;

  private static Num n(long k) {
    return Num.of(k);
  }

  private static Relation rel(Op op, Expr l, Expr r) {
    return Relation.of(op, l, r);
  }

  private static Relation in(Symbol s, Constant d) {
    return rel(Op.IN, s, d);
  }

  private static Relation parity(Symbol s, long r) {
    return rel(Op.EQ, Apply.of(Symbol.of("mod"), s, n(2)), n(r));
  }

  private static Assumptions with(Relation... relations) {
    Assumptions a = Assumptions.EMPTY;
    for (Relation r : relations) {
      a = a.with(r);
    }
    return a;
  }

  private static Apply f(String name, Expr arg) {
    return Apply.of(Symbol.of(name), arg);
  }

  @Test
  void aSymbolIsRealByDefault() {
    assertThat(Assumptions.EMPTY.facts(X)).isEqualTo(Facts.NONE);
    assertThat(Facts.NONE.domain()).isEqualTo(Domain.REALS);
    assertThat(Assumptions.EMPTY.isReal(X)).isEqualTo(T);
    assertThat(Assumptions.EMPTY.isPositive(X)).isEqualTo(U);
    assertThat(Assumptions.EMPTY.isRational(X)).isEqualTo(U);
  }

  @Test
  void signsAndBoundsAreRecorded() {
    assertThat(with(rel(Op.GT, X, n(0))).facts(X).lower()).isEqualTo(new Bound(Int.ZERO, true));
    assertThat(with(rel(Op.GE, X, n(0))).facts(X).lower()).isEqualTo(new Bound(Int.ZERO, false));
    assertThat(with(rel(Op.LT, X, n(0))).facts(X).upper()).isEqualTo(new Bound(Int.ZERO, true));
    assertThat(with(rel(Op.LE, X, n(0))).facts(X).upper()).isEqualTo(new Bound(Int.ZERO, false));
    assertThat(with(rel(Op.NE, X, n(0))).facts(X).nonZero()).isTrue();
    // A raw -1 is canonicalised before it is read.
    assertThat(with(rel(Op.GT, X, Exprs.neg(n(1)))).facts(X).lower().value())
        .isEqualTo(Int.MINUS_ONE);
    assertThat(with(rel(Op.LE, X, Num.of(Real.of("2.5")))).facts(X).upper().value())
        .isEqualTo(Real.of("2.5"));
    // The tighter bound wins; on a tie the strict one.
    Assumptions range = with(rel(Op.GE, X, n(0)), rel(Op.GT, X, n(1)), rel(Op.GE, X, n(1)));
    assertThat(range.facts(X).lower()).isEqualTo(new Bound(Int.ONE, true));
    assertThat(with(rel(Op.LE, X, n(5)), rel(Op.LT, X, n(7))).facts(X).upper())
        .isEqualTo(new Bound(Int.of(5), false));
  }

  @Test
  void domainsAndParitiesAreRecorded() {
    assertThat(with(in(X, Constant.INTEGERS)).facts(X).domain()).isEqualTo(Domain.INTEGERS);
    assertThat(with(in(X, Constant.RATIONALS)).facts(X).domain()).isEqualTo(Domain.RATIONALS);
    assertThat(with(in(X, Constant.REALS)).facts(X).domain()).isEqualTo(Domain.REALS);
    assertThat(with(in(X, Constant.COMPLEXES)).facts(X).domain()).isEqualTo(Domain.COMPLEXES);
    // Narrowing keeps the narrowest.
    assertThat(with(in(X, Constant.INTEGERS), in(X, Constant.REALS)).facts(X).domain())
        .isEqualTo(Domain.INTEGERS);
    Facts even = with(parity(N, 0)).facts(N);
    assertThat(even.parity()).isEqualTo(Parity.EVEN);
    assertThat(even.domain()).isEqualTo(Domain.INTEGERS);
    assertThat(with(parity(N, 1)).facts(N).parity()).isEqualTo(Parity.ODD);
  }

  @Test
  void complexWidensOnlyAnUnconstrainedSymbolAndRealNarrowsItBack() {
    Assumptions z = with(in(Z, Constant.COMPLEXES));
    assertThat(z.isReal(Z)).isEqualTo(U);
    assertThat(with(rel(Op.NE, Z, n(0)), in(Z, Constant.COMPLEXES)).isNonZero(Z)).isEqualTo(T);
    assertThatThrownBy(() -> z.with(rel(Op.GT, Z, n(0))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contradicts");
    assertThatThrownBy(() -> z.with(parity(Z, 0))).isInstanceOf(IllegalArgumentException.class);
    for (Relation real :
        List.of(
            rel(Op.GT, Z, n(0)), parity(Z, 1), in(Z, Constant.INTEGERS), in(Z, Constant.REALS))) {
      assertThatThrownBy(() -> with(real, in(Z, Constant.COMPLEXES)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("contradicts");
    }
    Assumptions back = z.with(in(Z, Constant.REALS));
    assertThat(back.isReal(Z)).isEqualTo(T);
    assertThat(back.relations()).isEqualTo(SetExpr.of(in(Z, Constant.REALS)));
    assertThat(back.with(rel(Op.GT, Z, n(0))).isPositive(Z)).isEqualTo(T);
  }

  @Test
  void rejectedForms() {
    List<Relation> rejected =
        List.of(
            rel(Op.EQ, X, n(3)),
            rel(Op.NE, X, n(2)),
            rel(Op.GT, n(0), X),
            rel(Op.GT, Sum.of(X, Y), n(0)),
            rel(Op.GT, X, Y),
            rel(Op.GT, X, Constant.PI),
            rel(Op.GT, X, Num.of(Complex.I)),
            rel(Op.IN, X, Symbol.of("Integers")),
            rel(Op.IN, X, Constant.PI),
            rel(Op.EQ, Apply.of(Symbol.of("mod"), X, n(3)), n(0)),
            rel(Op.EQ, Apply.of(Symbol.of("mod"), X, n(2)), n(2)),
            rel(Op.EQ, Apply.of(Symbol.of("mod"), Sum.of(X, n(1)), n(2)), n(0)),
            rel(Op.GT, n(2), n(1)));
    for (Relation r : rejected) {
      assertThatThrownBy(() -> Assumptions.EMPTY.with(r))
          .as(r.toString())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageNotContaining("contradicts");
    }
  }

  @Test
  void contradictionsThrowNamingBothFacts() {
    assertThatThrownBy(() -> with(rel(Op.LT, X, n(0)), rel(Op.GT, X, n(0))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("`x > 0` contradicts what is already assumed: `x < 0`");
    assertThatThrownBy(() -> with(parity(N, 0), rel(Op.GE, N, n(1)), parity(N, 1)))
        .hasMessage("`n mod 2 = 1` contradicts what is already assumed: `n mod 2 = 0`, `n ≥ 1`");
    List<List<Relation>> contradictions =
        List.of(
            List.of(rel(Op.GT, X, n(0)), rel(Op.LT, X, n(0))),
            List.of(rel(Op.GE, X, n(1)), rel(Op.LT, X, n(1))),
            List.of(rel(Op.GE, X, n(0)), rel(Op.LE, X, n(0)), rel(Op.NE, X, n(0))),
            List.of(parity(N, 0), parity(N, 1)),
            List.of(in(N, Constant.INTEGERS), rel(Op.GT, N, n(0)), rel(Op.LT, N, n(1))),
            List.of(
                in(N, Constant.INTEGERS),
                rel(Op.GT, N, Num.of(Rational.of(1, 2))),
                rel(Op.LT, N, Num.of(Rational.of(3, 2))),
                parity(N, 0)),
            List.of(parity(N, 1), rel(Op.GE, N, n(2)), rel(Op.LE, N, Num.of(Real.of("2.9")))),
            List.of(
                in(N, Constant.INTEGERS),
                rel(Op.GT, N, Num.of(Rational.of(-1, 2))),
                rel(Op.LT, N, Num.of(Rational.of(1, 2))),
                rel(Op.NE, N, n(0))));
    for (List<Relation> c : contradictions) {
      assertThatThrownBy(() -> with(c.toArray(Relation[]::new)))
          .as(c.toString())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("contradicts");
    }
    // Consistent neighbours of those.
    with(in(N, Constant.INTEGERS), rel(Op.GE, N, n(0)), rel(Op.LT, N, n(1)));
    with(parity(N, 1), rel(Op.GE, N, n(2)), rel(Op.LE, N, n(3)));
    with(rel(Op.GE, X, n(0)), rel(Op.LE, X, n(0)));
  }

  @Test
  void relationsAreTheCanonicalSetOfWhatIsInForce() {
    Assumptions a = with(rel(Op.GT, X, n(0)), in(N, Constant.INTEGERS), rel(Op.GT, X, n(0)));
    assertThat(a.relations())
        .isEqualTo(Canonical.of(SetExpr.of(rel(Op.GT, X, n(0)), in(N, Constant.INTEGERS))));
    assertThat(a.relations().items()).hasSize(2);
    assertThat(Assumptions.EMPTY.relations()).isEqualTo(SetExpr.of());
  }

  @Test
  void numbersAndConstants() {
    Assumptions a = Assumptions.EMPTY;
    assertThat(a.isPositive(n(3))).isEqualTo(T);
    assertThat(a.isNegative(n(3))).isEqualTo(F);
    assertThat(a.isZero(n(0))).isEqualTo(T);
    assertThat(a.isEven(n(4))).isEqualTo(T);
    assertThat(a.isOdd(n(-3))).isEqualTo(T);
    assertThat(a.isInteger(Num.of(Rational.of(1, 2)))).isEqualTo(F);
    assertThat(a.isRational(Num.of(Rational.of(1, 2)))).isEqualTo(T);
    assertThat(a.isEven(Num.of(Rational.of(1, 2)))).isEqualTo(F);
    // A Real is taken by its value.
    assertThat(a.isInteger(Num.of(Real.of("2.0")))).isEqualTo(T);
    assertThat(a.isRational(Num.of(Real.of("0.5")))).isEqualTo(T);
    assertThat(a.isNonPositive(Num.of(Real.of("-0.5")))).isEqualTo(T);
    assertThat(a.isReal(Num.of(Complex.I))).isEqualTo(F);
    assertThat(a.isPositive(Num.of(Complex.I))).isEqualTo(F);
    assertThat(a.isNonZero(Num.of(Complex.I))).isEqualTo(T);
    assertThat(a.isReal(Num.of(new Complex(Real.of("1"), Real.of("0.0"))))).isEqualTo(T);
    assertThat(a.isPositive(Constant.PI)).isEqualTo(T);
    assertThat(a.isPositive(Constant.E)).isEqualTo(T);
    assertThat(a.isRational(Constant.PI)).isEqualTo(F);
    assertThat(a.isReal(Constant.INFINITY)).isEqualTo(F);
    assertThat(a.isReal(Constant.UNDEFINED)).isEqualTo(U);
    assertThat(a.isZero(Constant.UNDEFINED)).isEqualTo(U);
    assertThat(a.isReal(new ListExpr(List.of(X)))).isEqualTo(U);
  }

  @Test
  void symbolsFromTheirFacts() {
    Assumptions a = with(rel(Op.GT, X, n(2)), rel(Op.LE, Y, n(0)), parity(N, 0));
    assertThat(a.isPositive(X)).isEqualTo(T);
    assertThat(a.isNonZero(X)).isEqualTo(T);
    assertThat(a.isNegative(X)).isEqualTo(F);
    assertThat(a.isNonPositive(Y)).isEqualTo(T);
    assertThat(a.isNegative(Y)).isEqualTo(U);
    assertThat(a.isPositive(Y)).isEqualTo(F);
    assertThat(a.isEven(N)).isEqualTo(T);
    assertThat(a.isOdd(N)).isEqualTo(F);
    assertThat(a.isInteger(N)).isEqualTo(T);
    assertThat(a.isRational(N)).isEqualTo(T);
    assertThat(a.isInteger(X)).isEqualTo(U);
    assertThat(a.isEven(X)).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(-1))).isPositive(X)).isEqualTo(U);
    assertThat(with(rel(Op.NE, X, n(0))).isNonZero(X)).isEqualTo(T);
    assertThat(with(rel(Op.NE, X, n(0)), rel(Op.GE, X, n(0))).isPositive(X)).isEqualTo(T);
    assertThat(with(in(X, Constant.RATIONALS)).isRational(X)).isEqualTo(T);
  }

  @Test
  void aSumIsSignedByItsSymbolsBounds() {
    Assumptions unit = with(rel(Op.GE, Y, n(-1)), rel(Op.LE, Y, n(1)));
    Expr oneMinusSquare = Canonical.of(Sum.of(n(1), Exprs.neg(Power.of(Y, n(2)))));
    assertThat(unit.isNonNegative(oneMinusSquare)).isEqualTo(T);
    assertThat(unit.isPositive(oneMinusSquare)).isEqualTo(U);
    assertThat(unit.isReal(Canonical.of(Power.of(oneMinusSquare, Num.of(Rational.of(1, 2))))))
        .isEqualTo(T);
    // open ends leave 0 out; a bound that does not decide leaves the sign open
    Assumptions open = with(rel(Op.GT, Y, n(-1)), rel(Op.LT, Y, n(1)));
    assertThat(open.isPositive(oneMinusSquare)).isEqualTo(T);
    assertThat(with(rel(Op.GE, Y, n(-2)), rel(Op.LE, Y, n(1))).isNonNegative(oneMinusSquare))
        .isEqualTo(U);
    // a negative range squared, an odd power, and a lower bound only
    Assumptions negative = with(rel(Op.GE, Y, n(-3)), rel(Op.LE, Y, n(-2)));
    assertThat(negative.isPositive(Canonical.of(Sum.of(Power.of(Y, n(2)), n(-4))))).isEqualTo(U);
    assertThat(negative.isNonNegative(Canonical.of(Sum.of(Power.of(Y, n(2)), n(-4))))).isEqualTo(T);
    assertThat(negative.isNegative(Canonical.of(Sum.of(Power.of(Y, n(3)), n(7))))).isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(2))).isPositive(Canonical.of(Sum.of(X, n(-1))))).isEqualTo(T);
    assertThat(Assumptions.EMPTY.isPositive(Canonical.of(Sum.of(X, n(-1))))).isEqualTo(U);
    // a negative multiple of a range that holds 0 flips it: 1 − y is non-negative there
    assertThat(unit.isNonNegative(Canonical.of(Sum.of(n(1), Exprs.neg(Y))))).isEqualTo(T);
    assertThat(unit.isNonNegative(Canonical.of(Sum.of(n(2), Product.of(n(-3), Y))))).isEqualTo(U);
    assertThat(unit.isNonNegative(Canonical.of(Sum.of(n(3), Product.of(n(-3), Y))))).isEqualTo(T);
  }

  @Test
  void aLogarithmOfAPositiveValueIsReal() {
    Assumptions none = Assumptions.EMPTY;
    assertThat(none.isReal(f("log", n(2)))).isEqualTo(T);
    assertThat(none.isPositive(f("log", n(2)))).isEqualTo(T);
    assertThat(none.isNegative(f("log", Num.of(Rational.of(1, 2))))).isEqualTo(T);
    assertThat(none.isZero(f("log", n(1)))).isEqualTo(T);
    assertThat(none.isReal(f("log", X))).isEqualTo(U);
    assertThat(none.isReal(f("log", n(-2)))).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(0))).isReal(f("log", X))).isEqualTo(T);
    assertThat(with(rel(Op.GT, X, n(0))).isPositive(f("log", X))).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(1))).isPositive(f("log", X))).isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(1))).isNonNegative(f("log", X))).isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(1))).isPositive(f("log", X))).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(0)), rel(Op.LT, X, n(1))).isNegative(f("log", X))).isEqualTo(T);
    // π has no range: real, sign unknown
    assertThat(none.isReal(f("log", Constant.PI))).isEqualTo(T);
    assertThat(none.isPositive(f("log", Constant.PI))).isEqualTo(U);
  }

  @Test
  void sums() {
    Assumptions pos = with(rel(Op.GT, X, n(0)), rel(Op.GT, Y, n(0)));
    assertThat(pos.isPositive(Sum.of(X, Y))).isEqualTo(T);
    Assumptions nonneg = with(rel(Op.GE, X, n(0)), rel(Op.GE, Y, n(0)));
    assertThat(nonneg.isNonNegative(Sum.of(X, Y))).isEqualTo(T);
    assertThat(nonneg.isPositive(Sum.of(X, Y))).isEqualTo(U);
    assertThat(nonneg.isPositive(Sum.of(n(1), X, Y))).isEqualTo(T);
    assertThat(with(rel(Op.GT, X, n(0)), rel(Op.LT, Y, n(0))).isPositive(Sum.of(X, Y)))
        .isEqualTo(U);
    assertThat(with(rel(Op.LT, X, n(0))).isNegative(Sum.of(n(-1), X))).isEqualTo(T);
    // x² + 1 is positive for the default real x.
    assertThat(Assumptions.EMPTY.isPositive(Sum.of(n(1), Power.of(X, n(2))))).isEqualTo(T);
    assertThat(with(in(X, Constant.COMPLEXES)).isPositive(Sum.of(n(1), Power.of(X, n(2)))))
        .isEqualTo(U);
    // Closure, and the one-lacking rule.
    Assumptions ints = with(in(N, Constant.INTEGERS), in(Y, Constant.INTEGERS));
    assertThat(ints.isInteger(Sum.of(N, Y))).isEqualTo(T);
    assertThat(ints.isInteger(Sum.of(Num.of(Rational.of(1, 2)), N))).isEqualTo(F);
    assertThat(ints.isRational(Sum.of(Constant.PI, N))).isEqualTo(F);
    assertThat(ints.isReal(Sum.of(Num.of(Complex.I), N))).isEqualTo(F);
    assertThat(ints.isInteger(Sum.of(X, N))).isEqualTo(U);
    assertThat(with(parity(N, 1), parity(Y, 1)).isEven(Sum.of(N, Y))).isEqualTo(T);
    assertThat(with(parity(N, 1), parity(Y, 0)).isOdd(Sum.of(N, Y))).isEqualTo(T);
    assertThat(with(in(Z, Constant.COMPLEXES)).isReal(Sum.of(X, Z))).isEqualTo(U);
  }

  @Test
  void products() {
    Assumptions a = with(rel(Op.GT, X, n(0)), rel(Op.LT, Y, n(0)));
    assertThat(a.isNegative(Product.of(X, Y))).isEqualTo(T);
    assertThat(a.isPositive(Product.of(n(-1), Y))).isEqualTo(T);
    assertThat(a.isPositive(Product.of(n(-2), X, Y))).isEqualTo(T);
    assertThat(a.isPositive(Product.of(X, Z))).isEqualTo(U);
    assertThat(a.isNonZero(Product.of(X, Y))).isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(0))).isNonNegative(Product.of(n(3), X))).isEqualTo(T);
    assertThat(Assumptions.EMPTY.isZero(Product.of(n(0), X))).isEqualTo(T);
    assertThat(a.isReal(Product.of(Num.of(Complex.I), X))).isEqualTo(F);
    assertThat(Assumptions.EMPTY.isReal(Product.of(Num.of(Complex.I), X))).isEqualTo(U);
    assertThat(a.isRational(Product.of(Constant.PI, n(2)))).isEqualTo(F);
    Assumptions ints = with(in(N, Constant.INTEGERS), parity(Y, 0));
    assertThat(ints.isInteger(Product.of(n(3), N, Y))).isEqualTo(T);
    assertThat(ints.isEven(Product.of(N, Y))).isEqualTo(T);
    assertThat(ints.isEven(Product.of(n(3), N))).isEqualTo(U);
    assertThat(with(parity(N, 1)).isOdd(Product.of(n(3), N))).isEqualTo(T);
    assertThat(ints.isInteger(Product.of(Num.of(Rational.of(1, 2)), N))).isEqualTo(U);
  }

  @Test
  void powers() {
    Assumptions none = Assumptions.EMPTY;
    assertThat(none.isNonNegative(Power.of(X, n(2)))).isEqualTo(T);
    assertThat(none.isPositive(Power.of(X, n(2)))).isEqualTo(U);
    assertThat(with(rel(Op.NE, X, n(0))).isPositive(Power.of(X, n(2)))).isEqualTo(T);
    assertThat(none.isPositive(Power.of(X, n(-2)))).isEqualTo(T);
    assertThat(with(rel(Op.LT, X, n(0))).isNegative(Power.of(X, n(3)))).isEqualTo(T);
    assertThat(with(in(Z, Constant.COMPLEXES)).isNonNegative(Power.of(Z, n(2)))).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(0))).isPositive(Power.of(X, Y))).isEqualTo(T);
    assertThat(with(rel(Op.GT, X, n(0))).isPositive(Power.of(X, Num.of(Rational.of(1, 2)))))
        .isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(0))).isNonNegative(Power.of(X, Num.of(Rational.of(1, 2)))))
        .isEqualTo(T);
    assertThat(none.isReal(Power.of(X, Num.of(Rational.of(1, 2))))).isEqualTo(U);
    assertThat(none.isPositive(Power.of(Constant.E, X))).isEqualTo(T);
    // 0^x is 0 for x > 0 but 1 for x = 0.
    assertThat(none.isZero(Power.of(n(0), X))).isEqualTo(U);
    assertThat(with(rel(Op.GT, X, n(0))).isZero(Power.of(n(0), X))).isEqualTo(T);
    assertThat(with(in(Z, Constant.COMPLEXES)).isPositive(Power.of(Constant.E, Z))).isEqualTo(U);
    assertThat(with(in(Z, Constant.COMPLEXES)).isNonZero(Power.of(Constant.E, Z))).isEqualTo(T);
    Assumptions n = with(in(N, Constant.INTEGERS));
    assertThat(n.isInteger(Power.of(N, n(3)))).isEqualTo(T);
    assertThat(n.isInteger(Power.of(N, n(-1)))).isEqualTo(U);
    assertThat(n.isRational(Power.of(N, n(-1)))).isEqualTo(T);
    assertThat(with(in(X, Constant.RATIONALS)).isRational(Power.of(X, n(2)))).isEqualTo(T);
    assertThat(with(parity(N, 1)).isOdd(Power.of(N, n(5)))).isEqualTo(T);
  }

  @Test
  void functions() {
    Assumptions none = Assumptions.EMPTY;
    Assumptions z = with(in(Z, Constant.COMPLEXES));
    assertThat(z.isNonNegative(f("abs", Z))).isEqualTo(T);
    assertThat(z.isPositive(f("abs", Z))).isEqualTo(U);
    assertThat(with(rel(Op.NE, X, n(0))).isPositive(f("abs", X))).isEqualTo(T);
    assertThat(with(in(N, Constant.INTEGERS)).isInteger(f("abs", N))).isEqualTo(T);
    assertThat(none.isPositive(f("exp", X))).isEqualTo(T);
    assertThat(z.isPositive(f("exp", Z))).isEqualTo(U);
    assertThat(z.isNonZero(f("exp", Z))).isEqualTo(T);
    assertThat(with(rel(Op.GE, X, n(0))).isNonNegative(f("sqrt", X))).isEqualTo(T);
    assertThat(with(rel(Op.GT, X, n(0))).isPositive(f("sqrt", X))).isEqualTo(T);
    assertThat(none.isNonNegative(f("sqrt", X))).isEqualTo(U);
    assertThat(none.isReal(f("asin", X))).isEqualTo(U);
    assertThat(none.isReal(Apply.of(Symbol.of("abs"), X, Y))).isEqualTo(U);
    // Neither abs nor exp of an infinite argument is a finite real.
    assertThat(none.isReal(f("abs", Constant.INFINITY))).isEqualTo(U);
    assertThat(none.isPositive(f("exp", Constant.INFINITY))).isEqualTo(U);
    assertThat(none.isNonZero(f("exp", Product.of(n(-1), Constant.INFINITY)))).isEqualTo(U);
    assertThat(none.isNonNegative(f("abs", Sum.of(X, Constant.UNDEFINED)))).isEqualTo(U);
    assertThat(none.isReal(f("abs", Num.of(Complex.I)))).isEqualTo(T);
  }

  @Test
  void circularAndHyperbolicFunctions() {
    Assumptions none = Assumptions.EMPTY;
    Assumptions z = with(in(Z, Constant.COMPLEXES));
    for (String name : List.of("sin", "cos", "tan", "cot", "sec", "csc", "sinh", "tanh", "atan")) {
      assertThat(none.isReal(f(name, X))).as(name).isEqualTo(T);
      assertThat(none.isPositive(f(name, X))).as(name).isEqualTo(U);
      assertThat(none.isZero(f(name, X))).as(name).isEqualTo(U);
      assertThat(z.isReal(f(name, Z))).as(name).isEqualTo(U);
    }
    assertThat(none.isPositive(f("cosh", X))).isEqualTo(T);
    assertThat(none.isNonZero(f("cosh", X))).isEqualTo(T);
    assertThat(z.isReal(f("cosh", Z))).isEqualTo(U);
    // sin(x)² is non-negative, so |sin x|² and √(sin²x) resolve.
    assertThat(none.isNonNegative(Power.of(f("sin", X), n(2)))).isEqualTo(T);
    assertThat(none.isReal(f("asin", X))).isEqualTo(U);
    assertThat(none.isReal(f("sin", Constant.INFINITY))).isEqualTo(U);
  }

  @Test
  void anExpressionUndefinedUnderTheFactsHasNoSign() {
    // x >= 0 and x <= 0 leave x = 0, where 1/x is undefined.
    Assumptions zero = with(rel(Op.GE, X, n(0)), rel(Op.LE, X, n(0)));
    Expr inverse = Power.of(X, n(-1));
    // The emptiness reaches the sums and products around it.
    for (Expr e :
        List.of(
            inverse,
            Product.of(Y, inverse),
            Sum.of(inverse, n(1)),
            Power.of(inverse, n(2)),
            f("abs", inverse))) {
      for (Tri answer :
          List.of(
              zero.isPositive(e),
              zero.isNegative(e),
              zero.isNonNegative(e),
              zero.isNonPositive(e),
              zero.isZero(e),
              zero.isNonZero(e))) {
        assertThat(answer).as(e.toString()).isEqualTo(U);
      }
    }
    assertThat(zero.isZero(X)).isEqualTo(T);
  }

  @Test
  void theConstructorChecksTheFactsAgainstTheRelations() {
    Assumptions a = with(rel(Op.GT, X, n(0)));
    assertThat(new Assumptions(a.bySymbol(), a.recorded())).isEqualTo(a);
    java.util.TreeMap<String, Facts> forged = new java.util.TreeMap<>(a.bySymbol());
    forged.put("y", new Facts(Domain.INTEGERS, null, null, Parity.NONE, false));
    assertThatThrownBy(() -> new Assumptions(forged, a.recorded()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Assumptions(
                    new java.util.TreeMap<>(), List.of(rel(Op.GT, X, n(0)), rel(Op.LT, X, n(0)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contradicts");
    assertThatThrownBy(
            () -> new Assumptions(new java.util.TreeMap<>(), List.of(rel(Op.EQ, X, n(1)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withoutDropsTheFactsAboutOneSymbol() {
    Assumptions a = with(rel(Op.GT, X, n(0)), parity(N, 0), rel(Op.LT, X, n(5)));
    Assumptions b = a.without(X);
    assertThat(b.facts(X)).isEqualTo(Facts.NONE);
    assertThat(b.facts(N).parity()).isEqualTo(Parity.EVEN);
    assertThat(b.relations()).isEqualTo(SetExpr.of(parity(N, 0)));
    assertThat(b.without(N)).isEqualTo(Assumptions.EMPTY);
    assertThat(a.without(Y)).isSameAs(a);
  }

  @Test
  void triLogic() {
    assertThat(T.and(U)).isEqualTo(U);
    assertThat(F.and(U)).isEqualTo(F);
    assertThat(T.or(U)).isEqualTo(T);
    assertThat(F.or(U)).isEqualTo(U);
    assertThat(U.not()).isEqualTo(U);
    assertThat(Tri.of(false)).isEqualTo(F);
  }
}
