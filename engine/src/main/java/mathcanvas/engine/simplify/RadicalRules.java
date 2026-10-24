package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.fraction;
import static mathcanvas.engine.simplify.Rules.known;
import static mathcanvas.engine.simplify.Rules.positiveInteger;
import static mathcanvas.engine.simplify.Rules.rational;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code radical}. A power of an exact rational to a non-integer rational exponent is
 * brought to the textbook form: a negative radicand gives {@code (-1)^e}, the integer part of the
 * exponent comes out ({@code 2^(3/2)} is {@code 2√2}, {@code 2^(-1/2)} is {@code √2/2}), a
 * fraction's denominator leaves the radicand, perfect powers are taken out ({@code √8} is {@code
 * 2√2}), and radicals of numbers with one exponent combine ({@code √2·√3} is {@code √6}). The fixed
 * form is a positive integer free of {@code q}-th powers to an exponent in (0, 1). {@code √(a +
 * b√c)} is denested when {@code a² - b²c} is a rational square, and {@code root} and {@code cbrt}
 * become powers for a non-negative radicand ({@code docs/reference/engine.md},
 * <b>Simplification</b>).
 */
final class RadicalRules {

  /** The largest radicand, in bits, that {@code reduce-radicand-power} decomposes. */
  static final int REDUCE_BITS = 1_024;

  private RadicalRules() {}

  static final RuleSet RULES =
      new RuleSet(
          "radical",
          List.of(
              Rule.of(
                  "negative-radicand",
                  Pattern.parse("n_^e_")
                      .where(
                          "n_",
                          n ->
                              rational(n) instanceof RealNumeric q
                                  && q.signum() < 0
                                  && !q.equals(Int.MINUS_ONE))
                      .where("e_", e -> fraction(e) != null),
                  template("(-1)^e_ * (-n_)^e_")),
              Rule.computed(
                  "split-integer-exponent",
                  numericPower(e -> e.compareValue(Int.ONE) > 0),
                  RadicalRules::splitExponent),
              Rule.computed(
                  "rationalise-denominator",
                  numericPower(e -> e.signum() < 0),
                  RadicalRules::splitExponent),
              Rule.computed(
                  "split-fraction-radicand",
                  Pattern.parse("n_^e_")
                      .where("n_", n -> fraction(n) instanceof Rational q && q.signum() > 0)
                      .where("e_", e -> fraction(e) instanceof Rational q && inUnitInterval(q)),
                  RadicalRules::splitFraction),
              Rule.computed(
                  "reduce-radicand-power",
                  Pattern.parse("n_^e_")
                      .where(
                          "n_",
                          n ->
                              positiveInteger(n) instanceof Int i
                                  && i.value().bitLength() > 1
                                  && i.value().bitLength() <= REDUCE_BITS)
                      .where("e_", e -> fraction(e) instanceof Rational q && inUnitInterval(q)),
                  RadicalRules::reduce),
              Rule.computed(
                  "extract-perfect-power",
                  Pattern.parse("n_^e_")
                      .where("n_", n -> positiveInteger(n) != null)
                      .where("e_", e -> fraction(e) instanceof Rational q && inUnitInterval(q)),
                  RadicalRules::extract),
              Rule.of(
                  "combine-numeric-radicals",
                  Pattern.parse("a_^e_ * b_^e_ * rest___")
                      .where("a_", RadicalRules::positiveRational)
                      .where("b_", RadicalRules::positiveRational)
                      .where("e_", e -> fraction(e) != null),
                  template("(a_ * b_)^e_ * rest___")),
              Rule.computed(
                  "denest-square-root",
                  Pattern.parse("(a_ + b_ * c_^(1/2))^(1/2)")
                      .optional("b_", Num.ONE)
                      .where("a_", a -> rational(a) instanceof RealNumeric q && q.signum() > 0)
                      .where("b_", b -> rational(b) != null)
                      .where("c_", c -> positiveInteger(c) != null),
                  RadicalRules::denest),
              Rule.of(
                  "root-of-odd-power",
                  known(Pattern.parse("root(x_^n_, n_)"), Assumptions::isReal, "x_")
                      .where("n_", RadicalRules::oddDegree),
                  template("x_")),
              Rule.of(
                  "root-of-nonnegative",
                  known(Pattern.parse("root(x_, n_)"), Assumptions::isNonNegative, "x_")
                      .where("n_", n -> positiveInteger(n) != null),
                  template("x_^(1/n_)")),
              Rule.of(
                  "root-of-negative",
                  known(Pattern.parse("root(x_, n_)"), Assumptions::isNegative, "x_")
                      .where("n_", RadicalRules::oddDegree),
                  template("-root(-x_, n_)")),
              Rule.of(
                  "root-of-negative-even",
                  known(Pattern.parse("root(x_, n_)"), Assumptions::isNegative, "x_")
                      .where(
                          "n_", n -> positiveInteger(n) instanceof Int k && !k.value().testBit(0)),
                  template("undefined")),
              Rule.of(
                  "cbrt-of-cube",
                  known(Pattern.parse("cbrt(x_^3)"), Assumptions::isReal, "x_"),
                  template("x_")),
              Rule.of(
                  "cbrt-of-nonnegative",
                  known(Pattern.parse("cbrt(x_)"), Assumptions::isNonNegative, "x_"),
                  template("x_^(1/3)")),
              Rule.of(
                  "cbrt-of-negative",
                  known(Pattern.parse("cbrt(x_)"), Assumptions::isNegative, "x_"),
                  template("-cbrt(-x_)"))));

  /** {@code n_^e_} with {@code n_} a non-zero exact rational and {@code e_} a fraction. */
  private static Pattern numericPower(Predicate<Rational> exponent) {
    return Pattern.parse("n_^e_")
        .where("n_", n -> rational(n) instanceof RealNumeric q && q.signum() != 0)
        .where("e_", e -> fraction(e) instanceof Rational q && exponent.test(q));
  }

  private static boolean inUnitInterval(Rational q) {
    return q.signum() > 0 && q.compareValue(Int.ONE) < 0;
  }

  private static boolean positiveRational(Expr e) {
    return rational(e) instanceof RealNumeric q && q.signum() > 0;
  }

  private static boolean oddDegree(Expr e) {
    return positiveInteger(e) instanceof Int n && n.value().testBit(0);
  }

  /** {@code n^e} is {@code n^⌊e⌋·n^(e - ⌊e⌋)}. */
  private static Optional<Expr> splitExponent(Match m) {
    Expr n = m.bindings().get("n_");
    Rational e = fraction(m.bindings().get("e_"));
    Int whole = e.floor();
    RealNumeric rest = (RealNumeric) e.subtract(whole, MathContext.UNLIMITED);
    return Optional.of(Product.of(Power.of(n, Num.of(whole)), Power.of(n, Num.of(rest))));
  }

  /** {@code (N/D)^e} is {@code N^e·D^(1 - e)/D}, for {@code 0 < e < 1}. */
  private static Optional<Expr> splitFraction(Match m) {
    Rational n = fraction(m.bindings().get("n_"));
    Rational e = fraction(m.bindings().get("e_"));
    RealNumeric complement = (RealNumeric) Int.ONE.subtract(e, MathContext.UNLIMITED);
    Num den = Num.of(Int.of(n.den()));
    return Optional.of(
        Product.of(
            Power.of(Num.of(Int.of(n.num())), Num.of(e)),
            Power.of(den, Num.of(complement)),
            Exprs.inv(den)));
  }

  /** {@code n^e} is {@code m^(k·e)} when {@code n = m^k} with {@code k > 1} maximal. */
  private static Optional<Expr> reduce(Match m) {
    Int n = positiveInteger(m.bindings().get("n_"));
    Radicals.PerfectPower power = Radicals.perfectPower(n);
    if (power.exponent() == 1) {
      return Optional.empty();
    }
    Rational e = fraction(m.bindings().get("e_"));
    return Optional.of(
        Power.of(
            Num.of(power.root()),
            Num.of(e.multiply(Int.of(power.exponent()), MathContext.UNLIMITED))));
  }

  /** {@code n^(p/q)} is {@code a·(n^p/a^q)^(1/q)} for the largest {@code a} found. */
  private static Optional<Expr> extract(Match m) {
    BigInteger n = positiveInteger(m.bindings().get("n_")).value();
    Rational e = fraction(m.bindings().get("e_"));
    int q;
    int p;
    try {
      q = e.den().intValueExact();
      p = e.num().intValueExact();
    } catch (ArithmeticException tooLarge) {
      return Optional.empty();
    }
    if ((long) n.bitLength() * p > Radicals.MAX_BITS) {
      return Optional.empty();
    }
    BigInteger radicand = n.pow(p);
    BigInteger a = Radicals.perfectPowerFactor(radicand, q);
    if (a.equals(BigInteger.ONE)) {
      return Optional.empty();
    }
    BigInteger rest = radicand.divide(a.pow(q));
    return Optional.of(
        Product.of(
            Num.of(Int.of(a)),
            Power.of(Num.of(Int.of(rest)), Num.of(Rational.of(BigInteger.ONE, e.den())))));
  }

  /**
   * {@code √(a + b√c)} is {@code √((a + d)/2) ± √((a - d)/2)}, the sign of {@code b}, when {@code
   * d² = a² - b²c} for a rational {@code d ≥ 0}.
   */
  private static Optional<Expr> denest(Match m) {
    MathContext exact = MathContext.UNLIMITED;
    RealNumeric a = rational(m.bindings().get("a_"));
    RealNumeric b = rational(m.bindings().get("b_"));
    RealNumeric c = rational(m.bindings().get("c_"));
    RealNumeric d2 =
        (RealNumeric) a.multiply(a, exact).subtract(b.multiply(b, exact).multiply(c, exact), exact);
    if (d2.signum() < 0 || !d2.isPerfectSquare()) {
      return Optional.empty();
    }
    RealNumeric d = (RealNumeric) d2.sqrt(exact);
    RealNumeric two = Int.TWO;
    Expr u = Num.of(a.add(d, exact).divide(two, exact));
    Expr v = Num.of(a.subtract(d, exact).divide(two, exact));
    return Optional.of(
        Sum.of(Exprs.sqrt(u), Product.of(Num.of(Int.of(b.signum())), Exprs.sqrt(v))));
  }
}
