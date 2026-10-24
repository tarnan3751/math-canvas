package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.math.MathContext;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/** Exact rational arithmetic on polynomial coefficients, through the tower. */
final class Q {

  private static final MathContext EXACT = MathContext.UNLIMITED;

  private Q() {}

  static RealNumeric add(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.add(b, EXACT);
  }

  static RealNumeric subtract(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.subtract(b, EXACT);
  }

  static RealNumeric multiply(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.multiply(b, EXACT);
  }

  static RealNumeric divide(RealNumeric a, RealNumeric b) {
    return (RealNumeric) a.divide(b, EXACT);
  }

  /**
   * {@code a^n}, every coefficient power of the algebra: refused past the canonical form's folding
   * bound, {@code bits(a)·n > Canonical.MAX_FOLD_BITS} (the numerator's and denominator's bit
   * lengths), unless {@code |a|} is 1 or {@code n ≤ 1}.
   *
   * @throws AlgebraLimitException past the bound
   */
  static RealNumeric pow(RealNumeric a, int n) {
    checkPower(a, n);
    return (RealNumeric) a.pow(n, EXACT);
  }

  /** Throws when {@code a^n} is past the folding bound; for powers built term by term. */
  static void checkPower(RealNumeric a, int n) {
    if (n <= 1 || a.abs().isOne() || a.isZero()) {
      return;
    }
    long bits =
        a instanceof Int i ? i.value().bitLength() : (long) num(a).bitLength() + den(a).bitLength();
    if (bits * n > Canonical.MAX_FOLD_BITS) {
      throw new AlgebraLimitException("the unit's power is too large to compute");
    }
  }

  static RealNumeric of(BigInteger n) {
    return Int.of(n);
  }

  static RealNumeric of(BigInteger num, BigInteger den) {
    return Rational.of(num, den);
  }

  static BigInteger num(RealNumeric q) {
    return switch (q) {
      case Int i -> i.value();
      case Rational r -> r.num();
      case Real x -> throw new IllegalArgumentException("not exact: " + x);
    };
  }

  static BigInteger den(RealNumeric q) {
    return switch (q) {
      case Int i -> BigInteger.ONE;
      case Rational r -> r.den();
      case Real x -> throw new IllegalArgumentException("not exact: " + x);
    };
  }

  static boolean isRational(Object q) {
    return q instanceof Int || q instanceof Rational;
  }
}
