package mathcanvas.engine.compile;

import java.math.BigInteger;

/**
 * The builtin functions in {@code double}, each agreeing with the engine's value at the same point
 * wherever that value is real and within the {@code double} range ({@code docs/reference/
 * engine.md}, <b>Compilation</b>). A value the engine calls undefined, or a non-real one, is NaN; a
 * NaN argument gives NaN.
 */
final class RealFunctions {

  /** {@code ln 2}. */
  private static final double LN2 = 0.6931471805599453;

  /** Past this magnitude {@code asinh} and {@code acosh} are {@code ln 2x}. */
  private static final double LARGE = 0x1p28;

  /** Below this magnitude {@code asech} and {@code acsch} are {@code ln(2/|x|)}. */
  private static final double TINY = 0x1p-28;

  /** The largest {@code n} with a finite {@code n!}. */
  private static final int MAX_FACTORIAL = 170;

  /** {@code n!} for {@code n} from 0 to 170, each the nearest double to the exact value. */
  private static final double[] FACTORIALS = new double[MAX_FACTORIAL + 1];

  static {
    BigInteger f = BigInteger.ONE;
    for (int n = 0; n <= MAX_FACTORIAL; n++) {
      if (n > 0) {
        f = f.multiply(BigInteger.valueOf(n));
      }
      FACTORIALS[n] = f.doubleValue();
    }
  }

  private RealFunctions() {}

  /** {@code x^y}: {@code 0^y} is 0 for {@code y > 0}, 1 for {@code y = 0} and NaN below. */
  static double pow(double x, double y) {
    if (Double.isNaN(x) || Double.isNaN(y)) {
      return Double.NaN;
    }
    if (x == 0) {
      return y > 0 ? 0 : y == 0 ? 1 : Double.NaN;
    }
    // A negative base with a non-integer exponent is NaN here, as its principal value is complex.
    return Math.pow(x, y);
  }

  /**
   * {@code x^y} for an integer {@code y} too large for an {@code int}, of which {@code odd} keeps
   * the parity a double loses beyond 2^53: {@code (-1)^(2^60 + 1)} is −1.
   */
  static double pow(double x, double y, boolean odd) {
    if (Double.isNaN(x)) {
      return x;
    }
    if (x == 0) {
      return y > 0 ? 0 : Double.NaN;
    }
    double a = Math.abs(x);
    // y is +∞ from 2^1024 on, and Math.pow(1, ∞) is NaN: ±1 to any integer power is ±1.
    double r = a == 1 ? 1 : Math.pow(a, y);
    return x < 0 && odd ? -r : r;
  }

  /** {@code x^n} for an integer {@code n} other than 0 and 1; {@code 0^-n} is NaN. */
  static double pow(double x, int n) {
    return x == 0 && n < 0 ? Double.NaN : Math.pow(x, n);
  }

  /** {@code 1/x}, NaN at 0. */
  static double inverse(double x) {
    return x == 0 ? Double.NaN : 1 / x;
  }

  /** The natural logarithm, NaN at 0 and below. */
  static double log(double x) {
    return x == 0 ? Double.NaN : Math.log(x);
  }

  /** {@code log(x, b)}: NaN at {@code x = 0}, {@code b = 0}, {@code b = 1} and negative ones. */
  static double log(double x, double b) {
    return x == 0 || b == 0 || b == 1 ? Double.NaN : Math.log(x) / Math.log(b);
  }

  static double log10(double x) {
    return x == 0 ? Double.NaN : Math.log10(x);
  }

  static double log2(double x) {
    return x == 0 ? Double.NaN : Math.log(x) / LN2;
  }

  /** {@code root(x, n)} for {@code n ≥ 1}: the real root, negative for odd {@code n}. */
  static double root(double x, int n) {
    if (x < 0) {
      return n % 2 == 0 ? Double.NaN : -Math.pow(-x, 1.0 / n);
    }
    return Math.pow(x, 1.0 / n);
  }

  /** −1, 0 or 1. */
  static double sign(double x) {
    return x > 0 ? 1 : x < 0 ? -1 : x == 0 ? 0 : Double.NaN;
  }

  /** Toward zero. */
  static double trunc(double x) {
    return x < 0 ? Math.ceil(x) : Math.floor(x);
  }

  /** To the nearest integer, halves away from zero. */
  static double round(double x) {
    double t = trunc(x);
    // x - t is exact: |t| ≤ |x|, both of one sign, and below 2^52 they differ by less than 1.
    return Math.abs(x - t) >= 0.5 ? t + Math.copySign(1, x) : t;
  }

  /** {@code a − b·⌊a/b⌋}, with the sign of {@code b}; NaN at {@code b = 0}. */
  static double mod(double a, double b) {
    // The remainder is exact; the one correction rounds once.
    double r = a % b;
    return r != 0 && (r < 0) != (b < 0) ? r + b : r;
  }

  private static boolean isInteger(double x) {
    return Double.isFinite(x) && x == Math.rint(x);
  }

  /** The gcd of integer values, NaN for any other. */
  static double gcd(double a, double b) {
    if (!isInteger(a) || !isInteger(b)) {
      return Double.NaN;
    }
    a = Math.abs(a);
    b = Math.abs(b);
    while (b != 0) {
      double t = a % b;
      a = b;
      b = t;
    }
    return a;
  }

  /** The lcm of integer values (0 when either is 0), NaN for any other. */
  static double lcm(double a, double b) {
    double g = gcd(a, b);
    if (Double.isNaN(g)) {
      return g;
    }
    return g == 0 ? 0 : Math.abs(a / g * b);
  }

  /** {@code n!} of an integer {@code n ≥ 0}, infinite above 170; NaN for any other value. */
  static double factorial(double x) {
    if (!isInteger(x) && x != Double.POSITIVE_INFINITY || x < 0) {
      return Double.NaN;
    }
    return x > MAX_FACTORIAL ? Double.POSITIVE_INFINITY : FACTORIALS[(int) x];
  }

  /**
   * {@code binomial(n, k)} of integers, as the engine defines it: 0 for {@code k < 0} and for
   * {@code k > n ≥ 0}, and {@code (-1)^k binomial(k - n - 1, k)} for a negative {@code n}.
   */
  static double binomial(double n, double k) {
    if (!isInteger(n) || !isInteger(k)) {
      return Double.NaN;
    }
    if (k < 0 || (n >= 0 && k > n)) {
      return 0;
    }
    double sign = 1;
    if (n < 0) {
      n = k - n - 1;
      sign = k % 2 == 0 ? 1 : -1;
    }
    double m = Math.min(k, n - k);
    double r = 1;
    // binomial(n, i) grows at least as 2^i while i ≤ n/2, so the loop overflows within about
    // 1,100 steps if it does not end first.
    for (double i = 1; i <= m && r != Double.POSITIVE_INFINITY; i++) {
      // Dividing first keeps the running value within range while the result is.
      r = r / i * (n - m + i);
    }
    return sign * r;
  }

  /** The cotangent in radians, NaN at 0. */
  static double cot(double x) {
    return inverse(Math.tan(x));
  }

  /** The cosecant in radians, NaN at 0. */
  static double csc(double x) {
    return inverse(Math.sin(x));
  }

  /** {@code acot x = atan(1/x)}, and {@code π/2} at 0. */
  static double acot(double x) {
    return x == 0 ? Math.PI / 2 : Math.atan(1 / x);
  }

  /** {@code asec x = acos(1/x)}, NaN inside (−1, 1). */
  static double asec(double x) {
    double a = Math.abs(x);
    if (!(a >= 1)) {
      return Double.NaN;
    }
    // Near |x| = 1, acos(1/x) loses the digits 1/x rounds away; atan(√(a² − 1)) keeps them.
    double r = a > 2 ? Math.acos(1 / a) : Math.atan(Math.sqrt((a - 1) * (a + 1)));
    return x > 0 ? r : Math.PI - r;
  }

  /** {@code acsc x = asin(1/x)}, NaN inside (−1, 1). */
  static double acsc(double x) {
    double a = Math.abs(x);
    if (!(a >= 1)) {
      return Double.NaN;
    }
    double r = a > 2 ? Math.asin(1 / a) : Math.atan(1 / Math.sqrt((a - 1) * (a + 1)));
    return Math.copySign(r, x);
  }

  /** {@code atan2(y, x)}, NaN at the origin, {@code π} on the negative real axis. */
  static double atan2(double y, double x) {
    // The engine has no signed zero: adding 0 turns −0 into +0.
    return y == 0 && x == 0 ? Double.NaN : Math.atan2(y + 0.0, x + 0.0);
  }

  /** The hyperbolic cotangent, NaN at 0. */
  static double coth(double x) {
    return inverse(Math.tanh(x));
  }

  /** The hyperbolic cosecant, NaN at 0. */
  static double csch(double x) {
    return inverse(Math.sinh(x));
  }

  /** {@code asinh x = ln(x + √(x² + 1))}, odd, in fdlibm's forms. */
  static double asinh(double x) {
    double a = Math.abs(x);
    double r;
    if (a > LARGE) {
      r = Math.log(a) + LN2;
    } else if (a > 2) {
      r = Math.log(2 * a + 1 / (Math.sqrt(a * a + 1) + a));
    } else {
      double s = a * a;
      r = Math.log1p(a + s / (1 + Math.sqrt(1 + s)));
    }
    return Math.copySign(r, x);
  }

  /** {@code acosh x = ln(x + √(x² − 1))}, NaN below 1. */
  static double acosh(double x) {
    if (x < 1) {
      return Double.NaN;
    }
    if (x > LARGE) {
      return Math.log(x) + LN2;
    }
    if (x > 2) {
      return Math.log(2 * x - 1 / (x + Math.sqrt(x * x - 1)));
    }
    return acosh1p(x - 1);
  }

  /** {@code acosh(1 + t)} for {@code t ≥ 0}, accurate for a small {@code t}. */
  private static double acosh1p(double t) {
    return Math.log1p(t + Math.sqrt(t * (2 + t)));
  }

  /** {@code atanh x = ½ ln((1 + x)/(1 − x))}, NaN outside (−1, 1). */
  static double atanh(double x) {
    double a = Math.abs(x);
    if (!(a < 1)) {
      return Double.NaN;
    }
    double r =
        a < 0.5 ? 0.5 * Math.log1p(2 * a + 2 * a * a / (1 - a)) : 0.5 * Math.log1p(2 * a / (1 - a));
    return Math.copySign(r, x);
  }

  /** {@code acoth x = ½ ln((x + 1)/(x − 1))}, NaN inside [−1, 1]. */
  static double acoth(double x) {
    double a = Math.abs(x);
    if (!(a > 1)) {
      return Double.NaN;
    }
    // a − 1 is exact near 1, where 1/a would lose the digits atanh(1/a) needs.
    return Math.copySign(0.5 * Math.log1p(2 / (a - 1)), x);
  }

  /** {@code asech x = acosh(1/x)}, real on (0, 1], NaN elsewhere. */
  static double asech(double x) {
    if (!(x > 0 && x <= 1)) {
      return Double.NaN;
    }
    // (1 − x)/x is 1/x − 1 with one rounding: 1 − x is exact from 1/2 to 1.
    if (x < TINY) {
      // 1/x would overflow; asech x = ln 2 − ln x within x²/4.
      return LN2 - Math.log(x);
    }
    return x >= 0.5 ? acosh1p((1 - x) / x) : acosh(1 / x);
  }

  /** {@code acsch x = asinh(1/x)}, NaN at 0. */
  static double acsch(double x) {
    if (x == 0) {
      return Double.NaN;
    }
    double a = Math.abs(x);
    // 1/x would overflow; acsch x = ±(ln 2 − ln|x|) within x²/4.
    return a < TINY ? Math.copySign(LN2 - Math.log(a), x) : asinh(1 / x);
  }

  /** {@code im x} of a real {@code x}: 0, or NaN. */
  static double im(double x) {
    return Double.isNaN(x) ? x : 0;
  }

  /** {@code arg x} of a real {@code x}: 0, or {@code π} below 0, in radians in every mode. */
  static double arg(double x) {
    return x < 0 ? Math.PI : x >= 0 ? 0 : Double.NaN;
  }

  /** The circular functions, numbered for {@link #circular}. */
  static final int SIN = 0;

  static final int COS = 1;
  static final int TAN = 2;
  static final int COT = 3;
  static final int SEC = 4;
  static final int CSC = 5;

  /**
   * A circular function of an angle in a unit with {@code halfTurn} units in {@code π} radians (180
   * or 200). The angle is reduced exactly to {@code d} within an eighth of a turn of a quarter turn
   * {@code k}, so a large angle loses nothing and a multiple of the quarter turn has the exact
   * value: {@code sin 180} is 0 and {@code tan 90} is NaN, as the exact evaluation says.
   */
  static double circular(int function, double x, double halfTurn) {
    if (!Double.isFinite(x)) {
      return Double.NaN;
    }
    double quarter = halfTurn / 2;
    // Exact: the remainder of doubles is exact, and r − k·quarter is a difference of values
    // within a factor of two (Sterbenz) or r itself.
    double r = x % (2 * halfTurn);
    double k = Math.rint(r / quarter);
    double d = r - k * quarter;
    int quadrant = Math.floorMod((int) k, 4);
    double angle = d * (Math.PI / halfTurn);
    return switch (function) {
      case SIN -> quadrantSin(quadrant, angle);
      case COS -> quadrantSin((quadrant + 1) % 4, angle);
      case TAN -> quadrantTan(quadrant, angle);
      case COT -> quadrantCot(quadrant, angle);
      case SEC -> inverse(quadrantSin((quadrant + 1) % 4, angle));
      default -> inverse(quadrantSin(quadrant, angle));
    };
  }

  /** {@code sin(q·π/2 + a)}. */
  private static double quadrantSin(int q, double a) {
    return switch (q) {
      case 0 -> Math.sin(a);
      case 1 -> Math.cos(a);
      case 2 -> -Math.sin(a);
      default -> -Math.cos(a);
    };
  }

  /** {@code tan(q·π/2 + a)}: NaN at the poles, where {@code q} is odd and {@code a} is 0. */
  private static double quadrantTan(int q, double a) {
    return q % 2 == 0 ? Math.tan(a) : -inverse(Math.tan(a));
  }

  /** {@code cot(q·π/2 + a)}: NaN at the poles, where {@code q} is even and {@code a} is 0. */
  private static double quadrantCot(int q, double a) {
    return q % 2 == 0 ? inverse(Math.tan(a)) : -Math.tan(a);
  }
}
