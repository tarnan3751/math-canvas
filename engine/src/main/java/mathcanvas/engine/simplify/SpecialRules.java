package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.scalar;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code special}: the exact values of the gamma function at the integers and the
 * half-integers ({@code Γ(n) = (n − 1)!}, {@code Γ(1/2) = √π}, its poles at 0 and the negative
 * integers undefined), the beta function through the gamma function where all three values are
 * exact, and the symmetries of the error functions ({@code erf(−u) = −erf(u)}, {@code erfc(−u) = 2
 * − erfc(u)}) with their limits at infinity. Every rule removes a {@code gamma} or {@code beta}, or
 * takes a sign out of an argument that looks negative, so the set terminates ({@code
 * docs/reference/engine.md}, <b>Simplification</b>).
 */
final class SpecialRules {

  /**
   * The largest {@code n} whose {@code Γ(n)}, or {@code Γ(n + 1/2)} and {@code Γ(1/2 − n)}, is
   * computed: {@code 10000!} has about 118,000 bits, well under the fold limit.
   */
  static final int MAX_ARGUMENT = 10_000;

  private SpecialRules() {}

  static final RuleSet RULES =
      new RuleSet(
          "special",
          List.of(
              Rule.computed(
                  "gamma-integer",
                  Pattern.parse("gamma(n_)").where("n_", n -> Rules.positiveInteger(n) != null),
                  SpecialRules::gammaInteger),
              Rule.of(
                  "gamma-pole",
                  Pattern.parse("gamma(n_)").where("n_", SpecialRules::pole),
                  template("undefined")),
              Rule.computed(
                  "gamma-half-integer",
                  Pattern.parse("gamma(q_)").where("q_", q -> half(q) != null),
                  SpecialRules::gammaHalfInteger),
              Rule.computed(
                  "beta-gamma",
                  Pattern.parse("beta(a_, b_)")
                      .where("a_", SpecialRules::positiveHalves)
                      .where("b_", SpecialRules::positiveHalves),
                  SpecialRules::betaGamma),
              Rule.computed(
                  "erf-odd",
                  scalar(Pattern.parse("erf(u_)").where("u_", TrigTables::looksNegative), "u_"),
                  m ->
                      Optional.of(
                          Product.of(
                              Num.MINUS_ONE,
                              Apply.of(
                                  Symbol.of("erf"), TrigTables.negate(m.bindings().get("u_")))))),
              Rule.computed(
                  "erfc-odd",
                  scalar(Pattern.parse("erfc(u_)").where("u_", TrigTables::looksNegative), "u_"),
                  m ->
                      Optional.of(
                          Sum.of(
                              Num.of(2),
                              Product.of(
                                  Num.MINUS_ONE,
                                  Apply.of(
                                      Symbol.of("erfc"),
                                      TrigTables.negate(m.bindings().get("u_"))))))),
              Rule.of("erf-infinity", Pattern.parse("erf(inf)"), template("1")),
              Rule.of("erfc-infinity", Pattern.parse("erfc(inf)"), template("0"))));

  /** {@code Γ(n) = (n − 1)!} for a positive integer {@code n} up to {@link #MAX_ARGUMENT}. */
  private static Optional<Expr> gammaInteger(Match m) {
    Int n = Rules.positiveInteger(m.bindings().get("n_"));
    if (n.value().compareTo(BigInteger.valueOf(MAX_ARGUMENT)) > 0) {
      return Optional.empty();
    }
    return Optional.of(Num.of(Int.of(n.value().subtract(BigInteger.ONE)).factorial()));
  }

  /** Whether a number node is an integer at or below 0, a pole of {@code Γ}. */
  private static boolean pole(Expr e) {
    return e instanceof Num n && n.value() instanceof Int i && i.signum() <= 0;
  }

  /**
   * The odd numerator {@code m} of a half-integer {@code m/2} within the bounds, or null.
   *
   * @param e an expression
   * @return {@code m}
   */
  private static BigInteger half(Expr e) {
    if (!(Rules.fraction(e) instanceof Rational q) || !q.den().equals(BigInteger.TWO)) {
      return null;
    }
    return q.num().abs().compareTo(BigInteger.valueOf(2L * MAX_ARGUMENT)) > 0 ? null : q.num();
  }

  /**
   * {@code Γ(n + 1/2) = (2n)!/(4ⁿ n!)·√π} for {@code n ≥ 0}, and {@code Γ(1/2 − n) = (−4)ⁿ
   * n!/(2n)!·√π} for {@code n > 0}.
   */
  private static Optional<Expr> gammaHalfInteger(Match m) {
    BigInteger num = half(m.bindings().get("q_"));
    RealNumeric coefficient;
    if (num.signum() > 0) {
      int n = num.subtract(BigInteger.ONE).shiftRight(1).intValueExact();
      BigInteger top = Int.of(2L * n).factorial().value();
      BigInteger bottom = BigInteger.valueOf(4).pow(n).multiply(Int.of(n).factorial().value());
      coefficient = Rational.of(top, bottom);
    } else {
      int n = BigInteger.ONE.subtract(num).shiftRight(1).intValueExact();
      BigInteger top = BigInteger.valueOf(-4).pow(n).multiply(Int.of(n).factorial().value());
      coefficient = Rational.of(top, Int.of(2L * n).factorial().value());
    }
    return Optional.of(
        Product.of(Num.of(coefficient), Power.of(Constant.PI, Num.of(Rational.of(1, 2)))));
  }

  /** Whether a number node is a positive integer or half-integer within the bounds. */
  private static boolean positiveHalves(Expr e) {
    return Rules.positiveInteger(e) instanceof Int i
            && i.value().compareTo(BigInteger.valueOf(MAX_ARGUMENT)) <= 0
        || half(e) instanceof BigInteger m && m.signum() > 0;
  }

  /**
   * {@code B(a, b) = Γ(a)Γ(b)/Γ(a + b)} when {@code a + b} is within {@link #MAX_ARGUMENT}, so
   * every value is exact by the rules above; past it the call stays.
   */
  private static Optional<Expr> betaGamma(Match m) {
    Num a = (Num) m.bindings().get("a_");
    Num b = (Num) m.bindings().get("b_");
    Num total = Num.of(a.value().add(b.value(), Canonical.DEFAULT_CONTEXT));
    if (((RealNumeric) total.value()).compareValue(Int.of(MAX_ARGUMENT)) > 0) {
      return Optional.empty();
    }
    Symbol gamma = Symbol.of("gamma");
    return Optional.of(
        Product.of(
            Apply.of(gamma, a),
            Apply.of(gamma, b),
            Power.of(Apply.of(gamma, total), Num.MINUS_ONE)));
  }
}
