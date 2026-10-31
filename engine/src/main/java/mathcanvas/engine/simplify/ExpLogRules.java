package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.known;
import static mathcanvas.engine.simplify.Rules.scalar;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code exp-log}. Every logarithm becomes the natural {@code log} ({@code ln}, {@code
 * log10}, {@code log2} and {@code log(a, b)} through {@code log(a)/log(b)}, undefined for the base
 * 1, exactly {@code p/q} when {@code a = b^(p/q)} for positive rationals) and {@code exp(x)}
 * becomes {@code e^x}. Logs of products and powers are expanded under the facts that make it sound
 * over the principal branch, {@code e^(c·log x)} collapses to {@code x^c} for a positive {@code x},
 * and the log of a negative value is the log of its opposite plus {@code iπ}. The set never
 * recombines {@code log a + log b}, so expansion and collapse cannot alternate ({@code
 * docs/reference/engine.md}, <b>Simplification</b>).
 */
final class ExpLogRules {

  /** The largest numerator or denominator, in bits, {@code log-base-exact} decomposes. */
  static final int MAX_BITS = 4_096;

  private ExpLogRules() {}

  static final RuleSet RULES =
      new RuleSet(
          "exp-log",
          List.of(
              Rule.of("exp-to-power", Pattern.parse("exp(x_)"), template("e^x_")),
              Rule.of("ln-to-log", Pattern.parse("ln(x_)"), template("log(x_)")),
              Rule.of("log10-to-base", Pattern.parse("log10(x_)"), template("log(x_, 10)")),
              Rule.of("log2-to-base", Pattern.parse("log2(x_)"), template("log(x_, 2)")),
              Rule.of("log-to-base-one", Pattern.parse("log(a_, 1)"), template("undefined")),
              Rule.of("log-of-zero-to-base", Pattern.parse("log(0, b_)"), template("undefined")),
              Rule.computed("log-base-exact", Pattern.parse("log(a_, b_)"), ExpLogRules::exact),
              Rule.of(
                  "log-change-of-base",
                  Pattern.parse("log(a_, b_)"),
                  template("log(a_) / log(b_)")),
              Rule.of("log-of-zero", Pattern.parse("log(0)"), template("undefined")),
              Rule.of("log-of-one", Pattern.parse("log(1)"), template("0")),
              Rule.of("log-of-e", Pattern.parse("log(e)"), template("1")),
              Rule.of("sinh-of-zero", Pattern.parse("sinh(0)"), template("0")),
              Rule.of("cosh-of-zero", Pattern.parse("cosh(0)"), template("1")),
              Rule.of("tanh-of-zero", Pattern.parse("tanh(0)"), template("0")),
              Rule.of("sech-of-zero", Pattern.parse("sech(0)"), template("1")),
              Rule.of("csch-of-zero", Pattern.parse("csch(0)"), template("undefined")),
              Rule.of("coth-of-zero", Pattern.parse("coth(0)"), template("undefined")),
              Rule.of(
                  "log-of-exp",
                  known(Pattern.parse("log(e^x_)"), Assumptions::isReal, "x_"),
                  template("x_")),
              Rule.of(
                  "exp-of-log",
                  known(
                      scalar(Pattern.parse("e^(c___ * log(x_) + r___)"), "c___", "r___"),
                      Assumptions::isPositive,
                      "x_"),
                  template("x_^c___ * e^r___")),
              Rule.of(
                  "log-of-power",
                  known(
                      known(Pattern.parse("log(x_^n_)"), Assumptions::isPositive, "x_"),
                      Assumptions::isReal,
                      "n_"),
                  template("n_ * log(x_)")),
              Rule.of(
                  "log-of-even-power",
                  known(
                          known(Pattern.parse("log(x_^n_)"), Assumptions::isReal, "x_"),
                          Assumptions::isEven,
                          "n_")
                      .where(
                          List.of("n_", "x_"),
                          (b, a) ->
                              a.isNonZero(b.get("n_")) == Tri.TRUE
                                  || a.isNonZero(b.get("x_")) == Tri.TRUE),
                  template("n_ * log(abs(x_))")),
              Rule.of(
                  "log-of-product",
                  known(Pattern.parse("log(x_ * rest__)"), Assumptions::isPositive, "x_"),
                  template("log(x_) + log(rest__)")),
              Rule.computed(
                  "log-of-reciprocal",
                  Pattern.parse("log(q_)")
                      .where(
                          "q_",
                          q ->
                              Rules.fraction(q) instanceof Rational r
                                  && r.num().equals(BigInteger.ONE)),
                  m ->
                      Optional.of(
                          Exprs.neg(
                              Apply.of(
                                  Symbol.of("log"),
                                  Num.of(Int.of(Rules.fraction(m.bindings().get("q_")).den())))))),
              Rule.of(
                  "log-of-negative",
                  known(Pattern.parse("log(x_)"), Assumptions::isNegative, "x_"),
                  template("log(-x_) + i*pi"))));

  /** {@code log(a, b)} as the rational {@code p/q} when {@code a = b^(p/q)}, else empty. */
  private static Optional<Expr> exact(Match match) {
    RealNumeric a = Rules.rational(match.bindings().get("a_"));
    RealNumeric b = Rules.rational(match.bindings().get("b_"));
    if (a == null || b == null || a.signum() <= 0 || b.signum() <= 0 || b.isOne()) {
      return Optional.empty();
    }
    if (bits(a) > MAX_BITS || bits(b) > MAX_BITS) {
      return Optional.empty();
    }
    if (a.isOne()) {
      return Optional.of(Num.ZERO);
    }
    // b = r^k with r > 1 not a perfect power; then a = b^(p/q) exactly when a = r^m, and p/q = m/k.
    Radicals.PerfectPower base = Radicals.perfectPower(b);
    Integer m = exponentOf(a, base.root());
    return m == null
        ? Optional.empty()
        : Optional.of(
            Num.of(Rational.of(BigInteger.valueOf(m), BigInteger.valueOf(base.exponent()))));
  }

  /**
   * The {@code m} with {@code a = r^m}, or null. With {@code r = N/D} in lowest terms ({@code N > D
   * ≥ 1}), {@code a = r^m} for {@code m > 0} forces {@code num(a) = N^m} and {@code den(a) = D^m},
   * and for {@code m < 0} the same with {@code a}'s parts swapped; comparing {@code a} with 1 gives
   * the sign. {@code |m|} is the least {@code m} with {@code N^m ≥} the larger part, found by
   * bisection over the bit length, and two exact powers confirm it.
   */
  private static Integer exponentOf(RealNumeric a, RealNumeric r) {
    boolean up = a.compareValue(Int.ONE) > 0;
    BigInteger top = up ? numerator(a) : denominator(a);
    BigInteger bottom = up ? denominator(a) : numerator(a);
    BigInteger n = numerator(r);
    BigInteger d = denominator(r);
    int lo = 1;
    int hi = top.bitLength();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (atLeast(n, mid, top)) {
        hi = mid;
      } else {
        lo = mid + 1;
      }
    }
    if (!n.pow(lo).equals(top) || !d.pow(lo).equals(bottom)) {
      return null;
    }
    return up ? lo : -lo;
  }

  /** Whether {@code n^m ≥ x}, for {@code n ≥ 2}, without forming a power far beyond {@code x}. */
  private static boolean atLeast(BigInteger n, int m, BigInteger x) {
    if ((long) (n.bitLength() - 1) * m >= x.bitLength()) {
      return true;
    }
    return n.pow(m).compareTo(x) >= 0;
  }

  private static BigInteger numerator(RealNumeric q) {
    return q instanceof Rational r ? r.num() : ((Int) q).value();
  }

  private static BigInteger denominator(RealNumeric q) {
    return q instanceof Rational r ? r.den() : BigInteger.ONE;
  }

  private static long bits(RealNumeric q) {
    return switch (q) {
      case Int i -> i.value().bitLength();
      case Rational r -> Math.max(r.num().bitLength(), r.den().bitLength());
      case Real _ -> Long.MAX_VALUE;
    };
  }
}
