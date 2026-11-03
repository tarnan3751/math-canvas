package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.Rules.known;
import static mathcanvas.engine.simplify.Rules.scalar;
import static mathcanvas.engine.simplify.Rules.template;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code basic}: identities the canonical form does not apply, sound over the reals
 * under the facts each rule checks (symbols are real unless assumed complex). Absolute value and
 * sign resolve when the facts decide them and otherwise move inward over products and real powers;
 * a power of a power or of a product combines or splits when the base allows it; {@code (-1)^n} and
 * {@code 0^x} resolve on parity and sign; a builtin function of {@code undefined} is {@code
 * undefined}. Every rule moves an operator into a strictly smaller argument or removes it, so the
 * set never undoes its own work ({@code docs/reference/engine.md}, <b>Simplification</b>).
 */
final class BasicRules {

  private BasicRules() {}

  static final RuleSet RULES =
      new RuleSet(
          "basic",
          List.of(
              Rule.of(
                  "undefined-argument",
                  Pattern.of(Apply.of(Symbol.of("h_"), Symbol.of("args___")))
                      .where(BasicRules::functionOfUndefined),
                  template("undefined")),
              Rule.of("abs-of-abs", Pattern.parse("abs(abs(x_))"), template("abs(x_)")),
              Rule.of(
                  "abs-of-nonnegative",
                  known(Pattern.parse("abs(x_)"), Assumptions::isNonNegative, "x_"),
                  template("x_")),
              Rule.of(
                  "abs-of-nonpositive",
                  known(Pattern.parse("abs(x_)"), Assumptions::isNonPositive, "x_"),
                  template("-x_")),
              Rule.computed(
                  "abs-of-closed-constant",
                  Pattern.parse("abs(c_)").where("c_", BasicRules::closed),
                  BasicRules::closedSign),
              Rule.computed(
                  "abs-of-complex-number",
                  Pattern.parse("abs(z_)")
                      .where("z_", z -> z instanceof Num n && n.value() instanceof Complex),
                  BasicRules::modulus),
              Rule.of(
                  "abs-of-product",
                  Pattern.parse("abs(x_ * rest__)"),
                  template("abs(x_) * abs(rest__)")),
              Rule.of(
                  "abs-of-power",
                  known(Pattern.parse("abs(x_^n_)"), Assumptions::isReal, "n_"),
                  template("abs(x_)^n_")),
              Rule.of(
                  "abs-to-even-power",
                  known(
                      known(Pattern.parse("abs(x_)^n_"), Assumptions::isEven, "n_"),
                      Assumptions::isReal,
                      "x_"),
                  template("x_^n_")),
              Rule.of(
                  "sign-of-positive",
                  known(Pattern.parse("sign(x_)"), Assumptions::isPositive, "x_"),
                  template("1")),
              Rule.of(
                  "sign-of-negative",
                  known(Pattern.parse("sign(x_)"), Assumptions::isNegative, "x_"),
                  template("-1")),
              Rule.of(
                  "sign-of-zero",
                  known(Pattern.parse("sign(x_)"), Assumptions::isZero, "x_"),
                  template("0")),
              Rule.of(
                  "sign-of-product",
                  known(Pattern.parse("sign(x_ * rest__)"), Assumptions::isReal, "x_", "rest__"),
                  template("sign(x_) * sign(rest__)")),
              Rule.of(
                  "sign-times-abs",
                  known(Pattern.parse("sign(x_) * abs(x_) * rest___"), Assumptions::isReal, "x_"),
                  template("x_ * rest___")),
              Rule.of(
                  "power-of-power-nonnegative",
                  known(
                      known(
                          scalar(Pattern.parse("(x_^a_)^b_"), "b_"),
                          Assumptions::isNonNegative,
                          "x_"),
                      Assumptions::isReal,
                      "a_"),
                  template("x_^(a_ * b_)")),
              Rule.of(
                  "power-of-even-power",
                  known(
                      known(scalar(Pattern.parse("(x_^n_)^b_"), "b_"), Assumptions::isReal, "x_"),
                      Assumptions::isEven,
                      "n_"),
                  template("abs(x_)^(n_ * b_)")),
              Rule.of(
                  "power-of-product",
                  known(
                      scalar(Pattern.parse("(x_ * rest__)^a_"), "a_"),
                      Assumptions::isNonNegative,
                      "x_"),
                  template("x_^a_ * rest__^a_")),
              Rule.of(
                  "minus-one-to-even-power",
                  known(Pattern.parse("(-1)^n_"), Assumptions::isEven, "n_"),
                  template("1")),
              Rule.of(
                  "minus-one-to-odd-power",
                  known(Pattern.parse("(-1)^n_"), Assumptions::isOdd, "n_"),
                  template("-1")),
              Rule.of(
                  "zero-to-positive-power",
                  known(Pattern.parse("0^x_"), Assumptions::isPositive, "x_"),
                  template("0")),
              Rule.computed(
                  "negate-negative-sum",
                  Pattern.parse("c_ * s_ * rest___")
                      .where("c_", BasicRules::negativeNumber)
                      .where("s_", BasicRules::negativeSum),
                  BasicRules::negateSum)));

  /** A negative real number. */
  private static boolean negativeNumber(Expr e) {
    return e instanceof Num n && n.value() instanceof RealNumeric r && r.signum() < 0;
  }

  /** A sum of scalars every term of which is a negative number or has a negative coefficient. */
  private static boolean negativeSum(Expr e) {
    if (!(e instanceof Sum s) || !mathcanvas.engine.expr.Canonical.commutes(s)) {
      return false;
    }
    for (Expr t : s.terms()) {
      Expr c = t instanceof Product p ? p.factors().getFirst() : t;
      if (!negativeNumber(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@code c·s·rest} with a negative {@code c} and every term of {@code s} negative: {@code
   * (−c)·(−s)·rest}, the negation distributed over {@code s}.
   */
  private static Optional<Expr> negateSum(Match m) {
    Expr c = m.bindings().get("c_");
    Sum s = (Sum) m.bindings().get("s_");
    List<Expr> terms = new java.util.ArrayList<>();
    for (Expr t : s.terms()) {
      terms.add(Exprs.neg(t));
    }
    List<Expr> factors = new java.util.ArrayList<>();
    factors.add(Exprs.neg(c));
    factors.add(Sum.of(terms));
    factors.addAll(m.bindings().sequence("rest___"));
    return Optional.of(Product.of(factors));
  }

  /** Whether the match is a builtin function (not a command) with an undefined argument. */
  private static boolean functionOfUndefined(Bindings b, Assumptions a) {
    if (!(b.get("h_") instanceof Symbol head)
        || !Builtins.lookup(head.name())
            .map(f -> f.kind() == Builtin.Kind.FUNCTION)
            .orElse(false)) {
      return false;
    }
    return b.sequence("args___").contains(Constant.UNDEFINED);
  }

  /** {@code |z|} of a complex number: the square root of the sum of the squares of its parts. */
  private static Optional<Expr> modulus(Match m) {
    Numeric z = ((Num) m.bindings().get("z_")).value();
    MathContext mc = m.context().precision();
    Numeric squares = z.re().multiply(z.re(), mc).add(z.im().multiply(z.im(), mc), mc);
    return Optional.of(Exprs.sqrt(Num.of(squares)));
  }

  /** The precisions whose values must agree on a closed constant's sign. */
  private static final List<MathContext> SIGN_DIGITS =
      List.of(new MathContext(20), new MathContext(40));

  /** Below this a value is too near 0 for its sign to be read. */
  private static final BigDecimal TIE = BigDecimal.ONE.movePointLeft(15);

  /** A closed constant: no symbol, no {@code ∞} and no {@code undefined} in it, not a number. */
  private static boolean closed(Expr e) {
    return !(e instanceof Num) && closedParts(e);
  }

  private static boolean closedParts(Expr e) {
    if (e instanceof Symbol
        || e.equals(Constant.INFINITY)
        || e.equals(Constant.UNDEFINED)
        || !(e instanceof Num
            || e instanceof Constant
            || e instanceof Apply
            || e instanceof Sum
            || e instanceof Product
            || e instanceof Power)) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!closedParts(o)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@code |c|} → {@code c} or {@code −c} when the numerics give {@code c} a real value with the
   * same sign at 20 and at 40 digits, clear of 0.
   */
  private static Optional<Expr> closedSign(Match m) {
    Expr c = m.bindings().get("c_");
    int sign = 0;
    for (MathContext mc : SIGN_DIGITS) {
      Optional<Numeric> v = m.context().numerics().value(c, mc);
      if (v.isEmpty() || !(v.get() instanceof RealNumeric r)) {
        return Optional.empty();
      }
      int s = r.signum();
      BigDecimal magnitude =
          switch (r) {
            case Int i -> new BigDecimal(i.value());
            case Rational q -> new BigDecimal(q.num()).divide(new BigDecimal(q.den()), mc);
            case Real d -> d.value();
          };
      if (s == 0 || magnitude.abs().compareTo(TIE) < 0 || (sign != 0 && s != sign)) {
        return Optional.empty();
      }
      sign = s;
    }
    if (sign > 0) {
      return Optional.of(c);
    }
    // the negation written out: −(2√2 − 3) is 3 − 2√2
    return Optional.of(
        c instanceof Sum s ? Sum.of(s.terms().stream().map(Exprs::neg).toList()) : Exprs.neg(c));
  }
}
