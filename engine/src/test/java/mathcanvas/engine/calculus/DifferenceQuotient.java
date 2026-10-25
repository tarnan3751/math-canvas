package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.Soundness;

/**
 * Compares a derivative with the central difference quotient of its function at a point, along the
 * real direction of {@code x}, values complex or real (principal branches).
 *
 * <p>The function is evaluated at {@code x₀}, {@code x₀ ± h}, {@code x₀ ± h/2} and {@code x₀ ± 10h}
 * with {@code h = 10⁻¹⁰}, at 34 digits. The two central quotients {@code q(h)} and {@code q(h/2)}
 * differ by about {@code h² f'''/8}, and the one-sided quotients at {@code x₀} by about {@code h
 * f''}; where either pair differs by more than the tolerance the function is not smooth there (a
 * pole, a kink, a branch cut crossed or followed) and the point is skipped, as is one where the
 * argument of a logarithm or of a non-integer power crosses the negative real axis between {@code
 * x₀ ± 10h} (the jump there can be smooth on either side, so neither pair need see it), as is one
 * where a step function ({@code sign}, {@code floor}, {@code ceil}, {@code round}, {@code trunc})
 * jumps between {@code x₀ ± 10h}, and one where the central quotient at {@code 10h} differs from
 * the one at {@code h} by more than {@code 10⁻³} relative: a function smooth at the scale of {@code
 * h} agrees to {@code O(h²)}, while one that oscillates or wraps faster than {@code h} ({@code
 * (−1)^w} for a huge {@code w}, a logarithm winding through many turns per step) does not, and no
 * precision repairs that. Otherwise the Richardson value {@code (4q(h/2) − q(h))/3}, whose
 * truncation error is {@code O(h⁴)}, must agree with the derivative within {@code 10⁻⁶} relative,
 * plus a floor of {@code 10⁻¹⁸} times the function's size for the rounding of its values, which the
 * quotient divides by {@code h}. A point that disagrees is checked again at 60, 120, 240 and 480
 * digits away from the branch cuts ({@link NumericEvaluator#evalAwayFromCuts}): a value on a cut
 * within rounding is no evidence either way, and neither is a disagreement whose values still move
 * between the last two precisions.
 *
 * <p><b>Precondition:</b> callers must keep evaluated exponents below {@value #MAX_EXPONENT} in
 * magnitude at the point ({@link #exponentsBounded}); beyond that the finite difference carries no
 * information ({@code (−1)^w} for a huge {@code w} aliases to the same value at every step, which
 * no gate can see) and the checker may fail on a correct derivative, so it refuses such a function.
 */
final class DifferenceQuotient {

  /** What a point showed. */
  enum Outcome {
    COMPARED,
    RECHECKED,
    UNDEFINED,
    NOT_SMOOTH,
    ACROSS_SCALES,
    ACROSS_A_JUMP,
    UNRESOLVED,
    ON_A_CUT,
    OUTSIDE_THE_FACTS
  }

  /** The precisions of the rechecks of a disagreeing point, in order. */
  static final List<MathContext> RECHECKS =
      List.of(
          new MathContext(60), new MathContext(120), new MathContext(240), new MathContext(480));

  private static final Real H = Real.of("1e-10");
  private static final Real HALF_H = Real.of("5e-11");
  private static final Real WIDE_H = Real.of("1e-9");
  private static final BigDecimal SCALES = new BigDecimal("1e-3");
  private static final BigDecimal RELATIVE = new BigDecimal("1e-6");
  private static final BigDecimal FLOOR = new BigDecimal("1e-18");

  private DifferenceQuotient() {}

  /**
   * Checks {@code derivative} against the quotient of {@code f} in {@code x} at {@code point}.
   *
   * @param facts the facts about {@code x}, which {@code x₀ ± 10h} must keep
   * @throws AssertionError if they disagree at a smooth point away from the cuts
   */
  static Outcome check(Expr f, Expr derivative, Symbol x, Map<Symbol, Numeric> point, Facts facts) {
    if (!exponentsBounded(f, point)) {
      throw new IllegalArgumentException("an exponent of " + f + " exceeds 10⁴ at " + point);
    }
    Numeric x0 = point.get(x);
    if (!keeps(facts, x0)) {
      return Outcome.OUTSIDE_THE_FACTS;
    }
    if (acrossAJump(f, x, point)) {
      return Outcome.ACROSS_A_JUMP;
    }
    if (acrossACut(f, x, point)) {
      return Outcome.ON_A_CUT;
    }
    Comparison first = compare(f, derivative, x, point, Soundness.PRECISION, false);
    if (first.result() != Result.DISAGREE) {
      return first.result().outcome;
    }
    for (MathContext mc : RECHECKS) {
      Comparison c = compare(f, derivative, x, point, mc, true);
      switch (c.result()) {
        case AGREE -> {
          return Outcome.RECHECKED;
        }
        case UNDEFINED -> {
          return Outcome.ON_A_CUT;
        }
        case NOT_SMOOTH, ACROSS_SCALES -> {
          return c.result().outcome;
        }
        case DISAGREE -> {}
      }
      if (mc == RECHECKS.getLast()) {
        // Values still moving between the last two precisions are limited by the precision (a
        // logarithm of 1 + 10⁻¹³⁶), not evidence against the derivative.
        Comparison before =
            compare(f, derivative, x, point, RECHECKS.get(RECHECKS.size() - 2), true);
        if (before.result() != Result.DISAGREE
            || !close(before.derivative(), c.derivative(), BigDecimal.ZERO, mc)
            || !close(before.quotient(), c.quotient(), BigDecimal.ZERO, mc)) {
          return Outcome.UNRESOLVED;
        }
        assertThat(c.result())
            .as(
                "%s and the quotient of %s at %s: %s vs %s",
                derivative, f, point, c.derivative(), c.quotient())
            .isNotEqualTo(Result.DISAGREE);
      }
    }
    return Outcome.COMPARED;
  }

  /** A comparison's result, and the derivative and quotient values when both were computed. */
  private record Comparison(Result result, Numeric derivative, Numeric quotient) {}

  private enum Result {
    AGREE(Outcome.COMPARED),
    DISAGREE(Outcome.COMPARED),
    UNDEFINED(Outcome.UNDEFINED),
    NOT_SMOOTH(Outcome.NOT_SMOOTH),
    ACROSS_SCALES(Outcome.ACROSS_SCALES);

    final Outcome outcome;

    Result(Outcome outcome) {
      this.outcome = outcome;
    }
  }

  private static Comparison compare(
      Expr f, Expr derivative, Symbol x, Map<Symbol, Numeric> point, MathContext mc, boolean away) {
    try {
      Numeric x0 = point.get(x);
      Numeric plus = value(f, x, x0.add(H, mc), point, mc, away);
      Numeric minus = value(f, x, x0.subtract(H, mc), point, mc, away);
      Numeric halfPlus = value(f, x, x0.add(HALF_H, mc), point, mc, away);
      Numeric halfMinus = value(f, x, x0.subtract(HALF_H, mc), point, mc, away);
      Numeric widePlus = value(f, x, x0.add(WIDE_H, mc), point, mc, away);
      Numeric wideMinus = value(f, x, x0.subtract(WIDE_H, mc), point, mc, away);
      Numeric at = x0.add(Real.of("0.0"), mc);
      Numeric middle = value(f, x, at, point, mc, away);
      Numeric d = value(derivative, x, at, point, mc, away);
      if (plus == null
          || minus == null
          || halfPlus == null
          || halfMinus == null
          || widePlus == null
          || wideMinus == null
          || middle == null
          || d == null) {
        return new Comparison(Result.UNDEFINED, null, null);
      }
      Numeric q1 = plus.subtract(minus, mc).divide(H.multiply(Int.TWO, mc), mc);
      Numeric q2 = halfPlus.subtract(halfMinus, mc).divide(H, mc);
      BigDecimal scale =
          size(plus, mc).max(size(minus, mc)).max(size(halfPlus, mc)).max(size(halfMinus, mc));
      BigDecimal floor = FLOOR.multiply(scale.max(BigDecimal.ONE), mc);
      Numeric forward = plus.subtract(middle, mc).divide(H, mc);
      Numeric backward = middle.subtract(minus, mc).divide(H, mc);
      if (!close(q1, q2, floor, mc) || !close(forward, backward, floor, mc)) {
        return new Comparison(Result.NOT_SMOOTH, null, null);
      }
      Numeric wide = widePlus.subtract(wideMinus, mc).divide(WIDE_H.multiply(Int.TWO, mc), mc);
      if (!within(q1, wide, SCALES, floor, mc)) {
        return new Comparison(Result.ACROSS_SCALES, null, null);
      }
      Numeric richardson = q2.multiply(Int.of(4), mc).subtract(q1, mc).divide(Int.of(3), mc);
      return new Comparison(
          close(d, richardson, floor, mc) ? Result.AGREE : Result.DISAGREE, d, richardson);
    } catch (ArithmeticException overflow) {
      return new Comparison(Result.UNDEFINED, null, null);
    }
  }

  /** The largest magnitude of an evaluated exponent the checker accepts. */
  static final int MAX_EXPONENT = 10_000;

  /**
   * Whether every exponent in {@code e} that has a value at {@code point}, its values made inexact
   * as the checker evaluates them, has a magnitude of at most {@link #MAX_EXPONENT}.
   */
  static boolean exponentsBounded(Expr e, Map<Symbol, Numeric> point) {
    Map<Symbol, Numeric> inexact = new HashMap<>();
    point.forEach((s, v) -> inexact.put(s, v.add(Real.of("0.0"), Soundness.PRECISION)));
    return bounded(e, inexact);
  }

  private static boolean bounded(Expr e, Map<Symbol, Numeric> point) {
    if (e instanceof Power p) {
      Optional<Numeric> v = NumericEvaluator.eval(p.exponent(), point, Soundness.PRECISION);
      if (v.isPresent()
          && size(v.get(), Soundness.PRECISION).compareTo(BigDecimal.valueOf(MAX_EXPONENT)) > 0) {
        return false;
      }
    }
    for (Expr o : Operands.of(e)) {
      if (!bounded(o, point)) {
        return false;
      }
    }
    return true;
  }

  private static final Set<String> LOGARITHMS = Set.of("log", "ln", "log10", "log2");

  /**
   * Whether the argument of a logarithm or of a power with a non-integer exponent in {@code f}
   * crosses the negative real axis, the principal logarithm's cut, between {@code x₀ − 10h} and
   * {@code x₀ + 10h}: the sign of its imaginary part differs at the two ends while its real part is
   * negative at one of them. The function then takes its values on the two sides of the cut, and
   * the quotient measures the jump, not the derivative. An argument that stays on the negative real
   * axis does not cross it.
   */
  private static boolean acrossACut(Expr f, Symbol x, Map<Symbol, Numeric> point) {
    Expr argument =
        switch (f) {
          case Apply a when LOGARITHMS.contains(a.head().name()) -> a.args().getFirst();
          case Power p when !(p.exponent() instanceof Num n && n.value() instanceof Int) ->
              p.base();
          default -> null;
        };
    if (argument != null && crosses(argument, x, point)) {
      return true;
    }
    if (f instanceof Apply a && a.head().name().equals("log") && a.args().size() == 2) {
      if (crosses(a.args().get(1), x, point)) {
        return true;
      }
    }
    for (Expr o : Operands.of(f)) {
      if (acrossACut(o, x, point)) {
        return true;
      }
    }
    return false;
  }

  private static boolean crosses(Expr argument, Symbol x, Map<Symbol, Numeric> point) {
    MathContext mc = Soundness.PRECISION;
    Numeric x0 = point.get(x);
    Numeric lo = value(argument, x, x0.subtract(WIDE_H, mc), point, mc, false);
    Numeric hi = value(argument, x, x0.add(WIDE_H, mc), point, mc, false);
    if (lo == null || hi == null) {
      return false;
    }
    return lo.im().signum() != hi.im().signum() && (lo.re().signum() < 0 || hi.re().signum() < 0);
  }

  private static final Set<String> STEPS = Set.of("sign", "floor", "ceil", "round", "trunc");

  /**
   * Whether a step function in {@code f} jumps between {@code x₀ − 10h} and {@code x₀ + 10h}: its
   * argument's integer part (for {@code sign}, its sign) differs at the two ends, or either end is
   * undefined. The derivative 0 of a step function holds only away from its jumps; where the
   * argument grows fast the jumps are dense, and the quotient sees them although neither quotient
   * looks rough.
   */
  private static boolean acrossAJump(Expr f, Symbol x, Map<Symbol, Numeric> point) {
    if (f instanceof Apply a && STEPS.contains(a.head().name())) {
      MathContext mc = Soundness.PRECISION;
      Numeric x0 = point.get(x);
      Numeric lo = value(a.args().getFirst(), x, x0.subtract(WIDE_H, mc), point, mc, false);
      Numeric hi = value(a.args().getFirst(), x, x0.add(WIDE_H, mc), point, mc, false);
      if (!(lo instanceof RealNumeric l && hi instanceof RealNumeric r)) {
        return true;
      }
      boolean jumps =
          a.head().name().equals("sign")
              ? l.signum() != r.signum()
              : l.floor().compareValue(r.floor()) != 0
                  || l.ceil().compareValue(r.ceil()) != 0
                  || l.round().compareValue(r.round()) != 0;
      if (jumps) {
        return true;
      }
    }
    for (Expr o : Operands.of(f)) {
      if (acrossAJump(o, x, point)) {
        return true;
      }
    }
    return false;
  }

  private static boolean close(Numeric a, Numeric b, BigDecimal floor, MathContext mc) {
    return within(a, b, RELATIVE, floor, mc);
  }

  private static boolean within(
      Numeric a, Numeric b, BigDecimal relative, BigDecimal floor, MathContext mc) {
    BigDecimal difference = size(a.subtract(b, mc), mc);
    BigDecimal bound = relative.multiply(size(a, mc).max(size(b, mc)), mc).add(floor, mc);
    return difference.compareTo(bound) <= 0;
  }

  private static BigDecimal size(Numeric v, MathContext mc) {
    return v.modulus(mc).toReal(mc).value().abs();
  }

  private static Numeric value(
      Expr e, Symbol x, Numeric at, Map<Symbol, Numeric> point, MathContext mc, boolean away) {
    Map<Symbol, Numeric> p = new HashMap<>(point);
    p.put(x, at);
    Optional<Numeric> v =
        away ? NumericEvaluator.evalAwayFromCuts(e, p, mc) : NumericEvaluator.eval(e, p, mc);
    return v.orElse(null);
  }

  /** Whether {@code x₀ ± 10h} still satisfy the bounds on {@code x}. */
  private static boolean keeps(Facts facts, Numeric x0) {
    if (!(x0 instanceof RealNumeric r)) {
      return facts.lower() == null && facts.upper() == null;
    }
    MathContext mc = Soundness.PRECISION;
    if (facts.lower() != null
        && ((RealNumeric) r.subtract(WIDE_H, mc)).compareValue(facts.lower().value()) <= 0) {
      return false;
    }
    return facts.upper() == null
        || ((RealNumeric) r.add(WIDE_H, mc)).compareValue(facts.upper().value()) < 0;
  }
}
