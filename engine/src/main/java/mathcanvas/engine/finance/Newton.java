package mathcanvas.engine.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The numeric rates ({@code docs/language.md} §14.28): {@code rate} and {@code irr} by Newton's
 * method in decimals ten digits beyond the session's precision, from the guess (0.1 by default), at
 * most {@link #MAX_ITERATIONS} steps, stopping when a step is below the session's last digit; the
 * rate must stay above −1. A root at 0 is found first, by the equation at {@code r = 0} checked
 * exactly, and a rate below the session's last digit is 0. No convergence is an error.
 */
public final class Newton {

  /** The rule set of the numeric steps. */
  public static final String RULE_SET = "numeric";

  /** The steps {@code numeric.rate} and {@code numeric.irr}, their {@code digits_} the digits. */
  public static final java.util.List<String> STEPS = java.util.List.of("rate", "irr");

  /** The most Newton steps. */
  public static final int MAX_ITERATIONS = 100;

  private Newton() {}

  /** A function and its derivative at a rate. */
  private interface F {
    RealNumeric[] at(RealNumeric r, MathContext mc);
  }

  /**
   * The rate of the TVM equation {@code pv·(1+r)ⁿ + pmt·(1 + r·type)((1+r)ⁿ − 1)/r + fv = 0},
   * solved divided by {@code (1+r)ⁿ}.
   *
   * @param n the periods
   * @param pmt the payment
   * @param pv the present value
   * @param fv the future value
   * @param type 0 or 1
   * @param guess the starting rate
   * @param mc the session's precision
   * @param c the request to stop
   * @return the rate
   * @throws Tvm.FinanceException if Newton's method does not converge
   */
  public static RealNumeric rate(
      RealNumeric n,
      RealNumeric pmt,
      RealNumeric pv,
      RealNumeric fv,
      RealNumeric type,
      RealNumeric guess,
      MathContext mc,
      Cancellation c) {
    // the equation divided by (1 + r)^n, which keeps Newton's steps from running off to -1:
    // pv + pmt·(1 + r·type)·(1 - q)/r + fv·q = 0 with q = (1 + r)^-n
    F f =
        (r, w) -> {
          RealNumeric one = Int.ONE;
          RealNumeric base = add(one, r, w);
          RealNumeric q = (RealNumeric) base.pow(n.negate(), w);
          RealNumeric dq = mul(n.negate(), (RealNumeric) base.pow(sub(n.negate(), one, w), w), w);
          RealNumeric k = add(one, mul(r, type, w), w);
          RealNumeric a = div(sub(one, q, w), r, w);
          RealNumeric da = div(sub(mul(dq.negate(), r, w), sub(one, q, w), w), mul(r, r, w), w);
          RealNumeric value = add(add(pv, mul(mul(pmt, k, w), a, w), w), mul(fv, q, w), w);
          RealNumeric d =
              add(mul(pmt, add(mul(type, a, w), mul(k, da, w), w), w), mul(fv, dq, w), w);
          return new RealNumeric[] {value, d};
        };
    // at r = 0 the equation is pv + pmt·n + fv = 0
    RealNumeric atZero = add(add(pv, mul(pmt, n, mc), mc), fv, mc);
    return solve("rate", f, atZero, guess, mc, c);
  }

  /**
   * The internal rate of return: the root of {@code Σ cₖ/(1 + r)ᵏ} from {@code k = 0}.
   *
   * @param flows the cash flows
   * @param guess the starting rate
   * @param mc the session's precision
   * @param c the request to stop
   * @return the rate
   * @throws Tvm.FinanceException if the flows do not change sign or Newton's method does not
   *     converge
   */
  public static RealNumeric irr(
      List<RealNumeric> flows, RealNumeric guess, MathContext mc, Cancellation c) {
    boolean positive = flows.stream().anyMatch(v -> v.signum() > 0);
    boolean negative = flows.stream().anyMatch(v -> v.signum() < 0);
    if (!positive || !negative) {
      throw new Tvm.FinanceException("`irr` needs cash flows of both signs");
    }
    F f =
        (r, w) -> {
          RealNumeric inverse = div(Int.ONE, add(Int.ONE, r, w), w);
          RealNumeric value = Int.ZERO;
          RealNumeric d = Int.ZERO;
          RealNumeric power = Int.ONE;
          for (int k = 0; k < flows.size(); k++) {
            value = add(value, mul(flows.get(k), power, w), w);
            // d/dr (1 + r)^-k = -k (1 + r)^(-k-1)
            d = sub(d, mul(mul(Int.of(k), flows.get(k), w), mul(power, inverse, w), w), w);
            power = mul(power, inverse, w);
          }
          return new RealNumeric[] {value, d};
        };
    // at r = 0 the flows' sum
    RealNumeric atZero = Int.ZERO;
    for (RealNumeric v : flows) {
      atZero = add(atZero, v, mc);
    }
    return solve("irr", f, atZero, guess, mc, c);
  }

  private static RealNumeric solve(
      String name, F f, RealNumeric atZero, RealNumeric guess, MathContext mc, Cancellation c) {
    if (atZero.isZero()) {
      return Int.ZERO;
    }
    MathContext w = new MathContext(mc.getPrecision() + 10);
    RealNumeric r = guess.isExact() ? guess.toReal(w) : guess;
    if (r.isZero()) {
      r = new Real(BigDecimal.ONE.movePointLeft(mc.getPrecision() / 2));
    }
    BigDecimal tolerance = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 2);
    RealNumeric[] fd = null;
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      c.check();
      try {
        fd = fd == null ? f.at(r, w) : fd;
      } catch (ArithmeticException e) {
        break;
      }
      if (fd[1].isZero()) {
        break;
      }
      RealNumeric step = div(fd[0], fd[1], w);
      // a damped step: halved while it leaves the rates above -1 or does not reduce |f|
      RealNumeric next = null;
      RealNumeric[] nextFd = null;
      for (int halving = 0; halving < 60 && next == null; halving++) {
        RealNumeric candidate = sub(r, step, w);
        if (candidate.compareValue(Int.MINUS_ONE) > 0 && !candidate.isZero()) {
          try {
            RealNumeric[] at = f.at(candidate, w);
            if (at[0].abs().compareValue(fd[0].abs()) <= 0 || halving == 59) {
              next = candidate;
              nextFd = at;
            }
          } catch (ArithmeticException e) {
            // too far: halve
          }
        }
        if (next == null) {
          step = div(step, Int.TWO, w);
        }
      }
      if (next == null) {
        break;
      }
      r = next;
      fd = nextFd;
      BigDecimal scale = BigDecimal.ONE.max(decimal(r, w).abs());
      if (decimal(step, w).abs().compareTo(tolerance.multiply(scale)) <= 0) {
        BigDecimal root = decimal(r, w);
        // a rate below the session's last digit is 0
        return root.abs().compareTo(BigDecimal.ONE.movePointLeft(mc.getPrecision())) < 0
            ? Int.ZERO
            : new Real(root.round(mc));
      }
    }
    throw new Tvm.FinanceException(
        "`" + name + "` found no answer from the guess " + guess + ": try another guess");
  }

  private static BigDecimal decimal(RealNumeric r, MathContext w) {
    return r.toReal(w).value();
  }

  private static RealNumeric add(RealNumeric a, RealNumeric b, MathContext w) {
    return (RealNumeric) a.add(b, w);
  }

  private static RealNumeric sub(RealNumeric a, RealNumeric b, MathContext w) {
    return (RealNumeric) a.subtract(b, w);
  }

  private static RealNumeric mul(RealNumeric a, RealNumeric b, MathContext w) {
    return (RealNumeric) a.multiply(b, w);
  }

  private static RealNumeric div(RealNumeric a, RealNumeric b, MathContext w) {
    return (RealNumeric) a.divide(b, w);
  }
}
