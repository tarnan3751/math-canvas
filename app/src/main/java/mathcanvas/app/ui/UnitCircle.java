package mathcanvas.app.ui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.OptionalInt;
import mathcanvas.engine.eval.AngleMode;

/**
 * The unit circle view's arithmetic, apart from the view: the special angles (the multiples of 15°,
 * {@code k·π/12} for {@code k} from 0 to 23), snapping a dragged angle to them, the steps of the
 * arrow keys, the angle written in each unit, and the call a special angle sends to a cell. Angles
 * are radians in {@code [0, 2π)}.
 */
final class UnitCircle {

  /** The number of special angles in a turn. */
  static final int SPECIAL = 24;

  /** How near, in degrees, a dragged angle snaps to a special one. */
  static final double SNAP_DEGREES = 3;

  /** How near, in degrees, an angle is taken to be a special one exactly. */
  private static final double EXACT_DEGREES = 1e-9;

  private UnitCircle() {}

  /**
   * An angle in {@code [0, 2π)}.
   *
   * @param radians any angle
   * @return the same angle within one turn
   */
  static double normalize(double radians) {
    double r = radians % (2 * Math.PI);
    if (r < 0) {
      r += 2 * Math.PI;
    }
    return r >= 2 * Math.PI ? 0 : r;
  }

  /**
   * The angle of a point seen from the centre, the y axis pointing up.
   *
   * @param dx the point's offset to the right
   * @param dy the point's offset upwards
   * @return the angle, in {@code [0, 2π)}
   */
  static double angleAt(double dx, double dy) {
    return normalize(Math.atan2(dy, dx));
  }

  /**
   * The special angle within a number of degrees of an angle.
   *
   * @param radians the angle
   * @param degrees how near
   * @return its {@code k}, from 0 to 23, or empty
   */
  static OptionalInt near(double radians, double degrees) {
    double d = Math.toDegrees(normalize(radians));
    long k = Math.round(d / 15);
    return Math.abs(d - 15 * k) <= degrees
        ? OptionalInt.of((int) (k % SPECIAL))
        : OptionalInt.empty();
  }

  /**
   * The special angle a dragged angle snaps to: within {@link #SNAP_DEGREES} of it.
   *
   * @param radians the angle
   * @return its {@code k}, or empty
   */
  static OptionalInt snap(double radians) {
    return near(radians, SNAP_DEGREES);
  }

  /**
   * The special angle an angle is, to rounding.
   *
   * @param radians the angle
   * @return its {@code k}, or empty
   */
  static OptionalInt special(double radians) {
    return near(radians, EXACT_DEGREES);
  }

  /**
   * The angle an arrow key steps to: the next multiple of 15° in its direction, or with Shift 1°
   * on.
   *
   * @param radians the angle
   * @param up counterclockwise rather than clockwise
   * @param fine one degree rather than to the next special angle
   * @return the new angle, in {@code [0, 2π)}
   */
  static double step(double radians, boolean up, boolean fine) {
    double d = Math.toDegrees(normalize(radians));
    double next;
    if (fine) {
      next = d + (up ? 1 : -1);
    } else {
      // the next multiple of 15 strictly beyond, an angle a hair off a multiple counting as on it
      double q = d / 15;
      long k = Math.round(q);
      double on = Math.abs(q - k) * 15 <= EXACT_DEGREES ? k : q;
      next = 15 * (up ? Math.floor(on) + 1 : Math.ceil(on) - 1);
    }
    return normalize(Math.toRadians(next));
  }

  /**
   * A special angle in radians: {@code 0}, {@code π/6}, {@code 3π/2}.
   *
   * @param k the multiple of {@code π/12}
   * @return the text
   */
  static String radians(int k) {
    BigInteger g = BigInteger.valueOf(k).gcd(BigInteger.valueOf(12));
    if (k == 0) {
      return "0";
    }
    int n = k / g.intValue();
    int d = 12 / g.intValue();
    String top = n == 1 ? "π" : n + "π";
    return d == 1 ? top : top + "/" + d;
  }

  /**
   * A special angle in the unit of an angle mode, as a number: {@code 30} degrees, {@code 100/3}
   * gradians; in radians as {@link #radians}.
   *
   * @param k the multiple of 15°
   * @param mode the unit
   * @return the text
   */
  static String inUnit(int k, AngleMode mode) {
    return switch (mode) {
      case RADIANS -> radians(k);
      case DEGREES -> Integer.toString(15 * k);
      case GRADIANS -> {
        // 15° is 50/3 gradians
        int g = BigInteger.valueOf(50L * k).gcd(BigInteger.valueOf(3)).intValue();
        int n = 50 * k / g;
        int d = 3 / g;
        yield d == 1 ? Integer.toString(n) : n + "/" + d;
      }
    };
  }

  /**
   * An angle that is not special, as a decimal in a unit: two decimals in degrees and gradians,
   * four in radians, trailing zeros dropped.
   *
   * @param radians the angle
   * @param mode the unit
   * @return the text
   */
  static String decimal(double radians, AngleMode mode) {
    double v =
        switch (mode) {
          case RADIANS -> radians;
          case DEGREES -> Math.toDegrees(radians);
          case GRADIANS -> radians * 200 / Math.PI;
        };
    BigDecimal d =
        BigDecimal.valueOf(v).setScale(mode == AngleMode.RADIANS ? 4 : 2, RoundingMode.HALF_EVEN);
    return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
  }

  /**
   * A value read from a {@code double} while the angle is dragged: rounded to a number of
   * significant digits, at most 15, trailing zeros dropped, and {@code 0} below {@code 1e-12}.
   *
   * @param value the value
   * @param digits the session's digits
   * @return the text
   */
  static String live(double value, int digits) {
    if (Math.abs(value) < 1e-12) {
      return "0";
    }
    BigDecimal d =
        new BigDecimal(value).round(new MathContext(Math.min(digits, 15), RoundingMode.HALF_EVEN));
    return d.stripTrailingZeros().toPlainString();
  }

  /**
   * The tangent of an angle read from {@code double}s, or NaN where it is undefined: at 90° and
   * 270°, where the cosine is within {@code 1e-12} of 0.
   *
   * @param radians the angle
   * @return the tangent, or NaN
   */
  static double tan(double radians) {
    double c = Math.cos(radians);
    return Math.abs(c) < 1e-12 ? Double.NaN : Math.sin(radians) / c;
  }

  /**
   * The call a special angle sends to a cell, in the language's ASCII form and the angle mode's
   * unit, so the cell evaluates it in that mode: {@code sin(pi/6)} in radians, {@code sin(30)} in
   * degrees, {@code sin(100/3)} in gradians.
   *
   * @param function {@code sin}, {@code cos} or {@code tan}
   * @param k the multiple of 15°
   * @param mode the angle mode
   * @return the text
   */
  static String call(String function, int k, AngleMode mode) {
    return function + "(" + inUnit(k, mode).replace("π", "pi") + ")";
  }
}
