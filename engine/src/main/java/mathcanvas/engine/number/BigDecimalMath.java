package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import mathcanvas.engine.Cancellation;

/**
 * The real elementary kernels on {@code BigDecimal}, each evaluated at a working context {@code w}
 * and accurate to a few units in its last place for well-conditioned arguments. {@link Elementary}
 * runs them inside its agreement loop and rounds the result to the caller's context.
 *
 * <p>The methods follow R. P. Brent and P. Zimmermann, <i>Modern Computer Arithmetic</i>
 * (Cambridge, 2010), chapter 4, and J.-M. Muller, <i>Elementary Functions: Algorithms and
 * Implementation</i> (3rd ed., Birkhäuser, 2016), chapters 6 and 11:
 *
 * <ul>
 *   <li>{@code exp}: the argument divided by {@code 2^k} (exact in decimal; {@code k} grows with
 *       the argument's magnitude and the square root of the precision), the Taylor series, then
 *       {@code k} squarings, with {@code 0.3k} extra digits for the error they amplify;
 *   <li>{@code log}: arguments in {@code [1/2, 2)} directly, others reduced by powers of 10 and 2
 *       to {@code [3/4, 3/2)}; the series {@code log x = 2 atanh((x-1)/(x+1))}; {@code ln 2 = 2
 *       atanh(1/3)} and {@code ln 10 = 3 ln 2 + 2 atanh(1/9)};
 *   <li>{@code sin}, {@code cos}: reduction modulo {@code π/2} with {@code π} carried to the
 *       argument's integer digits beyond the working precision (Cody and Waite's scheme with an
 *       extended {@code π}), then the Taylor series on {@code |r| ≤ π/4};
 *   <li>{@code atan}: {@code π/2 - atan(1/x)} above 1, two argument halvings {@code x/(1 + sqrt(1 +
 *       x²))}, then the Taylor series;
 *   <li>{@code π}: Machin's formula {@code 16 atan(1/5) - 4 atan(1/239)}.
 * </ul>
 *
 * <p>Every series checks the {@link Cancellation} at each term, and {@code exp} at each squaring,
 * so a kernel of tens of thousands of digits stops within a term of a request.
 *
 * <p>The series of the constants ({@code atan(1/5)} and {@code atan(1/239)} for {@code π}, {@code
 * atanh(1/3)} and {@code atanh(1/9)} for {@code ln 2} and {@code ln 10}) are remembered by their
 * context, the most recently used {@value #REMEMBERED} values: each is a function of its context
 * alone, so a remembered one is the one the series would give, and every reduction and logarithm of
 * the agreement loop no longer sums them again.
 *
 * @param cancellation the request to stop
 */
record BigDecimalMath(Cancellation cancellation) {

  /** The kernels that are never cancelled (but by a thread interrupt). */
  static final BigDecimalMath NEVER = new BigDecimalMath(Cancellation.NEVER);

  /** Digits each kernel carries beyond its working context. */
  private static final int GUARD = 10;

  private static final BigDecimal TWO = BigDecimal.TWO;
  private static final BigDecimal HALF = new BigDecimal("0.5");
  private static final BigDecimal THREE_HALVES = new BigDecimal("1.5");

  BigDecimal pi(MathContext w) {
    MathContext c = extend(w, GUARD);
    BigDecimal sixteenths = atanInverse(5, c).multiply(BigDecimal.valueOf(16));
    BigDecimal quarters = atanInverse(239, c).multiply(BigDecimal.valueOf(4));
    return sixteenths.subtract(quarters, w);
  }

  /**
   * Beyond this magnitude {@code e^x} leaves {@code BigDecimal}'s exponent range (ln 10 · 2^31).
   */
  private static final BigDecimal EXP_LIMIT = new BigDecimal("4.95E9");

  BigDecimal exp(BigDecimal x, MathContext w) {
    if (x.signum() == 0) {
      return BigDecimal.ONE;
    }
    if (x.abs().compareTo(EXP_LIMIT) > 0) {
      throw new ArithmeticException(
          (x.signum() > 0 ? "overflow" : "underflow") + ": exp of " + x + " is out of range");
    }
    // |x| < 10^digits; 2^k with k ≥ 3.33·digits + 8 brings it below 2^-8. A further √w halvings
    // balance the squarings against the series' terms (Brent and Zimmermann, §4.3), so a
    // thousands-digit exp takes hundreds of multiplications, not thousands.
    int digits = Math.max(0, x.precision() - x.scale());
    int k = (digits * 10 + 2) / 3 + 8 + BigInteger.valueOf(w.getPrecision()).sqrt().intValue();
    MathContext c = extend(w, GUARD + (k * 3) / 10 + 1);
    BigDecimal r = x.multiply(BigDecimal.valueOf(5).pow(k)).scaleByPowerOfTen(-k).round(c);
    BigDecimal sum = BigDecimal.ONE;
    BigDecimal term = BigDecimal.ONE;
    for (int n = 1; ; n++) {
      term = term.multiply(r).divide(BigDecimal.valueOf(n), c);
      if (negligible(term, sum, c)) {
        break;
      }
      sum = sum.add(term, c);
    }
    for (int i = 0; i < k; i++) {
      cancellation.check();
      sum = sum.multiply(sum, c);
    }
    return sum.round(w);
  }

  /** The natural logarithm of a positive {@code x}. */
  BigDecimal log(BigDecimal x, MathContext w) {
    if (x.compareTo(BigDecimal.ONE) == 0) {
      return BigDecimal.ZERO;
    }
    if (x.compareTo(HALF) >= 0 && x.compareTo(TWO) < 0) {
      return logNearOne(x, extend(w, GUARD)).round(w);
    }
    // Outside [1/2, 2) |log x| ≥ ln 2, so rounding x to the working precision first changes the
    // result by a relative 10^-c at most, and an argument of a million digits is not carried
    // through the reduction.
    x = x.round(extend(w, GUARD));
    int e = x.precision() - x.scale() - 1;
    BigDecimal m = x.scaleByPowerOfTen(-e);
    int halvings = 0;
    while (m.compareTo(THREE_HALVES) >= 0) {
      m = m.multiply(HALF);
      halvings++;
    }
    MathContext c = extend(w, GUARD + String.valueOf(Math.abs((long) e)).length());
    BigDecimal ln2 = ln2(c);
    BigDecimal result = logNearOne(m, c).add(ln2.multiply(BigDecimal.valueOf(halvings)), c);
    if (e != 0) {
      BigDecimal ln10 =
          ln2.multiply(BigDecimal.valueOf(3)).add(atanhInverse(9, c).multiply(TWO), c);
      result = result.add(ln10.multiply(BigDecimal.valueOf(e)), c);
    }
    return result.round(w);
  }

  BigDecimal sin(BigDecimal x, MathContext w) {
    if (x.signum() == 0) {
      return BigDecimal.ZERO;
    }
    MathContext c = extend(w, GUARD);
    Reduced reduced = reduce(x, c);
    BigDecimal value =
        switch (reduced.quadrant) {
          case 0 -> sinSeries(reduced.r, c);
          case 1 -> cosSeries(reduced.r, c);
          case 2 -> sinSeries(reduced.r, c).negate();
          default -> cosSeries(reduced.r, c).negate();
        };
    return value.round(w);
  }

  BigDecimal cos(BigDecimal x, MathContext w) {
    if (x.signum() == 0) {
      return BigDecimal.ONE;
    }
    MathContext c = extend(w, GUARD);
    Reduced reduced = reduce(x, c);
    BigDecimal value =
        switch (reduced.quadrant) {
          case 0 -> cosSeries(reduced.r, c);
          case 1 -> sinSeries(reduced.r, c).negate();
          case 2 -> cosSeries(reduced.r, c).negate();
          default -> sinSeries(reduced.r, c);
        };
    return value.round(w);
  }

  BigDecimal atan(BigDecimal x, MathContext w) {
    if (x.signum() == 0) {
      return BigDecimal.ZERO;
    }
    MathContext c = extend(w, GUARD);
    BigDecimal a = x.abs();
    boolean inverted = a.compareTo(BigDecimal.ONE) > 0;
    if (inverted) {
      a = BigDecimal.ONE.divide(a, c);
    }
    for (int i = 0; i < 2; i++) {
      a = a.divide(BigDecimal.ONE.add(BigDecimal.ONE.add(a.multiply(a, c), c).sqrt(c), c), c);
    }
    BigDecimal result = atanSeries(a, c).multiply(BigDecimal.valueOf(4));
    if (inverted) {
      result = pi(c).multiply(HALF).subtract(result, c);
    }
    return (x.signum() < 0 ? result.negate() : result).round(w);
  }

  /** The angle of the point {@code (x, y)} in {@code (-π, π]}; 0 at the origin. */
  BigDecimal atan2(BigDecimal y, BigDecimal x, MathContext w) {
    if (x.signum() == 0) {
      return y.signum() == 0 ? BigDecimal.ZERO : halfPi(w, y.signum());
    }
    MathContext c = extend(w, GUARD);
    BigDecimal angle = atan(y.divide(x, c), c);
    if (x.signum() > 0) {
      return angle.round(w);
    }
    BigDecimal pi = pi(c);
    return (y.signum() >= 0 ? angle.add(pi, c) : angle.subtract(pi, c)).round(w);
  }

  /** The arcsine of {@code x} in {@code [-1, 1]}. */
  BigDecimal asin(BigDecimal x, MathContext w) {
    int toOne = x.abs().compareTo(BigDecimal.ONE);
    if (toOne == 0) {
      return halfPi(w, x.signum());
    }
    MathContext c = extend(w, GUARD);
    // 1 - x² is exact, so the quotient keeps its accuracy near ±1.
    BigDecimal cosine = BigDecimal.ONE.subtract(x.multiply(x)).sqrt(c);
    return atan(x.divide(cosine, c), c).round(w);
  }

  /** The arccosine of {@code x} in {@code [-1, 1]}: {@code 2 atan(sqrt((1 - x)/(1 + x)))}. */
  BigDecimal acos(BigDecimal x, MathContext w) {
    if (x.compareTo(BigDecimal.ONE.negate()) == 0) {
      return pi(w);
    }
    MathContext c = extend(w, GUARD);
    BigDecimal ratio = BigDecimal.ONE.subtract(x).divide(BigDecimal.ONE.add(x), c);
    return atan(ratio.sqrt(c), c).multiply(TWO).round(w);
  }

  BigDecimal sinh(BigDecimal x, MathContext w) {
    MathContext c = extend(w, GUARD);
    if (x.abs().compareTo(BigDecimal.ONE) < 0) {
      return sinhSeries(x, c).round(w);
    }
    BigDecimal e = exp(x, c);
    return e.subtract(BigDecimal.ONE.divide(e, c), c).multiply(HALF).round(w);
  }

  BigDecimal cosh(BigDecimal x, MathContext w) {
    MathContext c = extend(w, GUARD);
    BigDecimal e = exp(x, c);
    return e.add(BigDecimal.ONE.divide(e, c), c).multiply(HALF).round(w);
  }

  BigDecimal tanh(BigDecimal x, MathContext w) {
    MathContext c = extend(w, GUARD);
    BigDecimal a = x.abs();
    BigDecimal value;
    if (a.compareTo(BigDecimal.ONE) < 0) {
      value = sinhSeries(a, c).divide(cosh(a, c), c);
    } else if (a.compareTo(BigDecimal.valueOf(2L * c.getPrecision())) > 0) {
      // 1 - tanh |x| < 2e^(-2|x|), far below the last digit.
      value = BigDecimal.ONE;
    } else {
      BigDecimal e2 = exp(a.multiply(TWO), c);
      value = BigDecimal.ONE.subtract(TWO.divide(e2.add(BigDecimal.ONE), c), c);
    }
    return (x.signum() < 0 ? value.negate() : value).round(w);
  }

  private record Reduced(int quadrant, BigDecimal r) {}

  /** {@code x = k·π/2 + r} with {@code |r| ≤ π/4}, and {@code k mod 4}. */
  private Reduced reduce(BigDecimal x, MathContext c) {
    int integerDigits = Math.max(0, x.precision() - x.scale());
    MathContext extended = extend(c, integerDigits + 5);
    BigDecimal halfPi = pi(extended).multiply(HALF);
    BigInteger k = x.divide(halfPi, extended).setScale(0, RoundingMode.HALF_EVEN).toBigInteger();
    BigDecimal r = x.subtract(halfPi.multiply(new BigDecimal(k)), extended).round(c);
    return new Reduced(k.mod(BigInteger.valueOf(4)).intValue(), r);
  }

  private BigDecimal sinSeries(BigDecimal r, MathContext c) {
    BigDecimal square = r.multiply(r, c);
    BigDecimal sum = r;
    BigDecimal term = r;
    for (long n = 1; ; n++) {
      term = term.multiply(square).divide(BigDecimal.valueOf(-(2 * n) * (2 * n + 1)), c);
      if (negligible(term, sum, c)) {
        return sum;
      }
      sum = sum.add(term, c);
    }
  }

  private BigDecimal cosSeries(BigDecimal r, MathContext c) {
    BigDecimal square = r.multiply(r, c);
    BigDecimal sum = BigDecimal.ONE;
    BigDecimal term = BigDecimal.ONE;
    for (long n = 1; ; n++) {
      term = term.multiply(square).divide(BigDecimal.valueOf(-(2 * n - 1) * (2 * n)), c);
      if (negligible(term, sum, c)) {
        return sum;
      }
      sum = sum.add(term, c);
    }
  }

  private BigDecimal sinhSeries(BigDecimal x, MathContext c) {
    BigDecimal square = x.multiply(x, c);
    BigDecimal sum = x;
    BigDecimal term = x;
    for (long n = 1; ; n++) {
      term = term.multiply(square).divide(BigDecimal.valueOf((2 * n) * (2 * n + 1)), c);
      if (negligible(term, sum, c)) {
        return sum;
      }
      sum = sum.add(term, c);
    }
  }

  /** {@code x - x³/3 + x⁵/5 - …} for small {@code |x|}. */
  private BigDecimal atanSeries(BigDecimal x, MathContext c) {
    BigDecimal square = x.multiply(x, c);
    BigDecimal power = x;
    BigDecimal sum = x;
    for (long n = 1; ; n++) {
      power = power.multiply(square, c).negate();
      BigDecimal term = power.divide(BigDecimal.valueOf(2 * n + 1), c);
      if (negligible(term, sum, c)) {
        return sum;
      }
      sum = sum.add(term, c);
    }
  }

  /** {@code 2 atanh((x - 1)/(x + 1))} for {@code x} near 1. */
  private BigDecimal logNearOne(BigDecimal x, MathContext c) {
    BigDecimal s = x.subtract(BigDecimal.ONE).divide(x.add(BigDecimal.ONE), c);
    if (s.signum() == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal square = s.multiply(s, c);
    BigDecimal power = s;
    BigDecimal sum = s;
    for (long n = 1; ; n++) {
      power = power.multiply(square, c);
      BigDecimal term = power.divide(BigDecimal.valueOf(2 * n + 1), c);
      if (negligible(term, sum, c)) {
        return sum.multiply(TWO);
      }
      sum = sum.add(term, c);
    }
  }

  private BigDecimal ln2(MathContext c) {
    return atanhInverse(3, c).multiply(TWO);
  }

  /** {@code atanh(1/n) = Σ 1/((2k+1) n^(2k+1))}. */
  private BigDecimal atanhInverse(int n, MathContext c) {
    return inverseSeries(n, c, false);
  }

  /** {@code atan(1/n) = Σ (-1)^k/((2k+1) n^(2k+1))}. */
  private BigDecimal atanInverse(int n, MathContext c) {
    return inverseSeries(n, c, true);
  }

  /** How many constants {@link #remembered} keeps. */
  private static final int REMEMBERED = 64;

  private record Key(String constant, MathContext c) {}

  /** The remembered constants, least recently used first; guarded by itself. */
  private static final Map<Key, BigDecimal> CONSTANTS =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, BigDecimal> eldest) {
          return size() > REMEMBERED;
        }
      };

  /** The value of {@code constant} at {@code c}, computed by {@code f} unless remembered. */
  private static BigDecimal remembered(
      String constant, MathContext c, Function<MathContext, BigDecimal> f) {
    Key key = new Key(constant, c);
    BigDecimal value;
    synchronized (CONSTANTS) {
      value = CONSTANTS.get(key);
    }
    if (value == null) {
      // Computed outside the lock, so a cancellation or another thread's long series does not wait.
      value = f.apply(c);
      synchronized (CONSTANTS) {
        CONSTANTS.put(key, value);
      }
    }
    return value;
  }

  private BigDecimal inverseSeries(int n, MathContext c, boolean alternating) {
    return remembered(
        (alternating ? "atan(1/" : "atanh(1/") + n + ")", c, w -> sum(n, w, alternating));
  }

  private BigDecimal sum(int n, MathContext c, boolean alternating) {
    BigDecimal square = BigDecimal.valueOf((long) n * n);
    BigDecimal power = BigDecimal.ONE.divide(BigDecimal.valueOf(n), c);
    BigDecimal sum = power;
    for (long k = 1; ; k++) {
      power = power.divide(square, c);
      BigDecimal term = power.divide(BigDecimal.valueOf(2 * k + 1), c);
      if (negligible(term, sum, c)) {
        return sum;
      }
      sum = alternating && k % 2 == 1 ? sum.subtract(term, c) : sum.add(term, c);
    }
  }

  private BigDecimal halfPi(MathContext w, int sign) {
    BigDecimal halfPi = pi(extend(w, 2)).multiply(HALF).round(w);
    return sign < 0 ? halfPi.negate() : halfPi;
  }

  /** Whether {@code term} is below the last digit of {@code sum} at {@code c}. */
  private boolean negligible(BigDecimal term, BigDecimal sum, MathContext c) {
    cancellation.check();
    if (term.signum() == 0) {
      return true;
    }
    int termExponent = term.precision() - term.scale() - 1;
    int sumExponent = sum.precision() - sum.scale() - 1;
    return sum.signum() != 0 && termExponent < sumExponent - c.getPrecision() - 1;
  }

  static MathContext extend(MathContext w, int digits) {
    return new MathContext(w.getPrecision() + digits, RoundingMode.HALF_EVEN);
  }
}
