package mathcanvas.engine.testing;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * A test-only numeric evaluator over the number tower, for the soundness properties of every rule
 * set: an expression at a point (a value per symbol), exact where the tower is exact and otherwise
 * at the given precision, in radians.
 *
 * <p>It knows numbers, {@code π} and {@code e}, sums, products, powers (of {@code e} through {@code
 * exp}, integer exponents through {@code pow(int)}, others through the principal {@code pow}), and
 * the functions {@code abs}, {@code sign} (of a real value), {@code sqrt}, {@code cbrt}, {@code
 * root(x, n)}, {@code exp}, {@code log} and {@code ln} (one argument natural, {@code log(x, b)}
 * base {@code b}), {@code log10}, {@code log2}, the six circular and six hyperbolic functions and
 * their inverses (by their principal formulas, as the tower's counterclockwise continuity defines
 * them off the real domain), {@code atan2} of reals, and {@code floor}, {@code ceil}, {@code round}
 * and {@code trunc} of a real value. Anything else, a symbol without a value, a point where the
 * value is undefined, and a value outside the bounds below give empty. The sign of an inexact value
 * is inexact.
 *
 * <p>The bounds keep every evaluation fast, since such a point is no useful sample: a power of an
 * exact base other than 0 and 1 whose digits times its exponent's numerator exceed 4,000 bits, and
 * of an inexact base whose decimal exponent (plus one) times the exponent's numerator exceeds 10⁴;
 * {@code exp} or a power of {@code e} with an argument of modulus above 10⁴; a logarithm of a value
 * whose decimal exponent is beyond ±10⁴; a circular or hyperbolic function of an argument of
 * modulus above 10⁶; a circular function of an argument whose imaginary part, or a hyperbolic one
 * of an argument whose real part, exceeds 50 in modulus, where the value passes {@code e^50} and an
 * identity such as {@code cos²z + sin²z = 1} cancels more digits than a 100-digit recheck carries;
 * and any inexact value, an operand or a result, whose decimal exponent is beyond ±10⁴.
 */
public final class NumericEvaluator {

  /** The largest power evaluated: the base's bits times the exponent's numerator. */
  private static final long MAX_BITS = 4_000;

  /** The largest modulus of an argument of {@code exp}. */
  private static final BigDecimal MAX_EXP = BigDecimal.TEN.pow(4);

  /** The largest modulus of an argument of a circular or hyperbolic function. */
  private static final BigDecimal MAX_ANGLE = BigDecimal.TEN.pow(6);

  /** The largest decimal exponent, either way, of an inexact value or a logarithm's argument. */
  private static final int MAX_EXPONENT = 10_000;

  private NumericEvaluator() {}

  /**
   * Returns the value of {@code e} at {@code point}.
   *
   * @param e the expression
   * @param point the values of its symbols
   * @param mc the precision of inexact results
   * @return the value, or empty where it is undefined or unknown
   */
  public static Optional<Numeric> eval(Expr e, Map<Symbol, Numeric> point, MathContext mc) {
    return eval(e, point, mc, false);
  }

  /**
   * Returns the value of {@code e} at {@code point}, or empty also where a logarithm, a square root
   * or a non-integer power is taken of an inexact complex number within rounding of the negative
   * real axis, its branch cut: there the side the rounding noise picks decides the value, so it
   * says nothing about the expression (the true value of {@code (-8/81)^(1/3)} cubed lies on the
   * cut, and its computed imaginary part is noise of either sign). The jump of {@code sign} at 0
   * counts as such a cut: {@code sign} of an inexact value within rounding of zero is empty too
   * ({@code sign(log(e^(2/3·log(log e))))} is {@code sign(0)}, computed as the sign of noise). So
   * is a root or a non-integer power of an inexact value within rounding of zero, which magnifies
   * the noise ({@code root(log(log e, 2^(b/3)), 4)} is 0, computed near {@code 10^-9}). Poles count
   * likewise: a circular or hyperbolic function whose denominator ({@code cos} for {@code tan} and
   * {@code sec}, {@code sin} for {@code cot} and {@code csc}, {@code sinh} and {@code cosh} for
   * theirs) is an inexact value within rounding of zero, or such a value to a negative power or as
   * the argument of {@code asec}, {@code acsc}, {@code acot}, {@code acoth}, {@code asech} or
   * {@code acsch}, is empty ({@code tan(3π/2)} and {@code -cot(π)} are each one over a different
   * rounding noise). So are the inverse functions within rounding of their cuts ({@code asin},
   * {@code acos}, {@code atanh} of the real axis outside [-1, 1], {@code atan} and {@code asinh} of
   * the imaginary axis outside [-i, i]), and {@code sin} or {@code cos} whose inexact value is
   * within rounding of zero.
   *
   * @param e the expression
   * @param point the values of its symbols
   * @param mc the precision of inexact results
   * @return the value, or empty where it is undefined, unknown, or on a branch cut within rounding
   */
  public static Optional<Numeric> evalAwayFromCuts(
      Expr e, Map<Symbol, Numeric> point, MathContext mc) {
    return eval(e, point, mc, true);
  }

  private static Optional<Numeric> eval(
      Expr e, Map<Symbol, Numeric> point, MathContext mc, boolean awayFromCuts) {
    try {
      return Optional.ofNullable(value(e, point, mc, awayFromCuts));
    } catch (ArithmeticException | IllegalArgumentException undefined) {
      return Optional.empty();
    }
  }

  /**
   * Throws when an inverse function is taken of an inexact value within rounding of its cut: the
   * real axis outside [-1, 1] for {@code asin}, {@code acos} and {@code atanh}, the imaginary axis
   * outside [-i, i] for {@code atan} and {@code asinh}; for {@code asec}, {@code acsc} and {@code
   * acot}, whose argument is a reciprocal, the reciprocal's cut.
   */
  private static void checkInverseCut(String name, Numeric x, MathContext mc) {
    boolean onReal = REAL_CUT.contains(name);
    if (x.isExact() || x.isZero() || !onReal && !IMAGINARY_CUT.contains(name)) {
      return;
    }
    Numeric v = RECIPROCAL_ARGUMENT.contains(name) ? Int.ONE.divide(x, mc) : x;
    BigDecimal along = (onReal ? v.re() : v.im()).toReal(mc).value().abs();
    BigDecimal across = (onReal ? v.im() : v.re()).toReal(mc).value().abs();
    if (along.compareTo(BigDecimal.ONE) > 0
        && across.compareTo(along.scaleByPowerOfTen(20 - mc.getPrecision())) <= 0) {
      throw new ArithmeticException("on a branch cut within rounding");
    }
  }

  private static final Set<String> REAL_CUT = Set.of("asin", "acos", "atanh", "asec", "acsc");

  private static final Set<String> IMAGINARY_CUT = Set.of("atan", "asinh", "acot");

  private static final Set<String> RECIPROCAL_ARGUMENT = Set.of("asec", "acsc", "acot");

  /** Throws when {@code x} is an inexact complex number within rounding of the negative axis. */
  private static void checkCut(Numeric x, MathContext mc, boolean awayFromCuts) {
    if (awayFromCuts
        && x instanceof Complex c
        && !c.isExact()
        && c.re().signum() < 0
        && c.im()
                .toReal(mc)
                .value()
                .abs()
                .compareTo(
                    c.re().toReal(mc).value().abs().scaleByPowerOfTen(20 - mc.getPrecision()))
            <= 0) {
      throw new ArithmeticException("on a branch cut within rounding");
    }
  }

  private static Numeric value(
      Expr e, Map<Symbol, Numeric> point, MathContext mc, boolean awayFromCuts) {
    Numeric v = compute(e, point, mc, awayFromCuts);
    return v != null && !v.isExact() && decimalExponentBeyond(v, MAX_EXPONENT, mc) ? null : v;
  }

  private static Numeric compute(
      Expr e, Map<Symbol, Numeric> point, MathContext mc, boolean awayFromCuts) {
    switch (e) {
      case Num n -> {
        return n.value();
      }
      case Constant c -> {
        return switch (c.kind()) {
          case PI -> Elementary.pi(mc);
          case E -> Elementary.e(mc);
          case INFINITY, UNDEFINED, INTEGERS, RATIONALS, REALS, COMPLEXES -> null;
        };
      }
      case Symbol s -> {
        return point.get(s);
      }
      case Sum s -> {
        Numeric total = Int.ZERO;
        for (Expr t : s.terms()) {
          Numeric v = value(t, point, mc, awayFromCuts);
          if (v == null) {
            return null;
          }
          total = total.add(v, mc);
        }
        return total;
      }
      case Product p -> {
        Numeric total = Int.ONE;
        for (Expr f : p.factors()) {
          Numeric v = value(f, point, mc, awayFromCuts);
          if (v == null) {
            return null;
          }
          total = total.multiply(v, mc);
        }
        return total;
      }
      case Power p -> {
        Numeric exponent = value(p.exponent(), point, mc, awayFromCuts);
        if (p.base().equals(Constant.E)) {
          // e^z is exp(z): through pow it would be exp(z·log e), whose rounded log e costs a huge
          // exponent all its digits.
          return exponent == null || modulusAbove(exponent, MAX_EXP, mc)
              ? null
              : Elementary.exp(exponent, mc);
        }
        Numeric base = value(p.base(), point, mc, awayFromCuts);
        if (base == null || exponent == null) {
          return null;
        }
        if (awayFromCuts
            && !base.isExact()
            && exponent.re().signum() < 0
            && withinRoundingOfZero(base, mc)) {
          // A negative power of noise around a true zero: a pole within rounding.
          throw new ArithmeticException("at a pole within rounding");
        }
        if (tooLarge(base, exponent, mc)) {
          // A power that large is not a useful sample point.
          return null;
        }
        if (exponent instanceof Int i && i.value().bitLength() < 16) {
          return base.pow(i.value().intValueExact(), mc);
        }
        checkCut(base, mc, awayFromCuts);
        checkRootOfZero(base, mc, awayFromCuts);
        return base.pow(exponent, mc);
      }
      case Apply a -> {
        List<Numeric> args = new ArrayList<>();
        for (Expr x : a.args()) {
          Numeric v = value(x, point, mc, awayFromCuts);
          if (v == null) {
            return null;
          }
          args.add(v);
        }
        return function(a.head().name(), args, mc, awayFromCuts);
      }
      case Bool _,
          Lambda _,
          Relation _,
          ListExpr _,
          Vector _,
          Matrix _,
          SetExpr _,
          Interval _,
          Piecewise _,
          Derivative _,
          Integral _,
          Limit _,
          BigSum _,
          BigProduct _ -> {
        return null;
      }
    }
  }

  /**
   * Whether a power is past the bounds: an exact base's bits times the exponent's numerator past
   * {@link #MAX_BITS}, the size of the exact result; an inexact base's decimal exponent, plus one,
   * times the exponent's numerator past {@link #MAX_EXPONENT}, the size of the result's exponent,
   * since an inexact result is rounded to the precision whatever the exponent ({@code 1.26^-88} is
   * about {@code 10^-9}, though its base's 34 digits times 88 pass 4,000 bits). An exact 0 or 1 is
   * never too large: its powers do not grow ({@code 0^(5625/16)} is 0). -1 keeps the bound: its
   * powers do not grow either, but a rational one costs a reduction of {@code π·q} that grows with
   * the exponent's digits.
   */
  private static boolean tooLarge(Numeric base, Numeric exponent, MathContext mc) {
    BigInteger p =
        switch (exponent) {
          case Int i -> i.value();
          case Rational q -> q.num();
          case Real _, Complex _ -> BigInteger.ZERO;
        };
    if (base instanceof Int i && (i.isZero() || i.isOne())) {
      return false;
    }
    if (base.isExact()) {
      return BigInteger.valueOf(bits(base))
              .multiply(p.abs())
              .compareTo(BigInteger.valueOf(MAX_BITS))
          > 0;
    }
    long magnitude =
        Math.max(Math.abs(decimalExponent(base.re(), mc)), Math.abs(decimalExponent(base.im(), mc)))
            + 1;
    return BigInteger.valueOf(magnitude)
            .multiply(p.abs())
            .compareTo(BigInteger.valueOf(MAX_EXPONENT))
        > 0;
  }

  /** The size of a value's digits in bits. */
  private static long bits(Numeric v) {
    return switch (v) {
      case Int i -> i.value().bitLength() + 1;
      case Rational q -> q.num().bitLength() + q.den().bitLength();
      case Real r -> r.value().unscaledValue().bitLength() + 1;
      case Complex c -> bits(c.re()) + bits(c.im());
    };
  }

  /**
   * The largest imaginary part of a circular function's argument, and real part of a hyperbolic
   * function's, evaluated: beyond it the value grows past {@code e^50}, and an identity such as
   * {@code cos²z + sin²z = 1} cancels more digits than the 100-digit recheck carries.
   */
  private static final RealNumeric MAX_GROWTH = Int.of(50);

  private static final Set<String> CIRCULAR = Set.of("sin", "cos", "tan", "cot", "sec", "csc");

  private static final Set<String> HYPERBOLIC =
      Set.of("sinh", "cosh", "tanh", "coth", "sech", "csch");

  private static final Set<String> REAL_ONLY =
      Set.of("sign", "cbrt", "root", "atan2", "floor", "ceil", "round", "trunc");

  private static final Set<String> ANGLE_FUNCTIONS =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "sinh", "cosh", "tanh", "coth", "sech", "csch");

  private static Numeric function(
      String name, List<Numeric> args, MathContext mc, boolean awayFromCuts) {
    if (name.startsWith("log") || name.equals("ln")) {
      for (Numeric a : args) {
        if (decimalExponentBeyond(a, MAX_EXPONENT, mc)) {
          return null;
        }
      }
    }
    if (name.startsWith("log") || name.equals("ln") || name.equals("sqrt")) {
      for (Numeric x : args) {
        checkCut(x, mc, awayFromCuts);
      }
    }
    if (REAL_ONLY.contains(name)) {
      // A real-only function of an inexact complex number with a zero imaginary part (0.0·i, as
      // an inexact factor times zero gives) takes its real part.
      args = args.stream().map(NumericEvaluator::realIfZeroImaginary).toList();
    }
    if (name.equals("root")) {
      checkRootOfZero(args.getFirst(), mc, awayFromCuts);
    }
    if (args.size() == 2) {
      return switch (name) {
        case "log" -> Elementary.log(args.get(0), mc).divide(Elementary.log(args.get(1), mc), mc);
        case "root" ->
            args.get(0) instanceof RealNumeric x && args.get(1) instanceof Int n
                ? x.root(n.value().intValueExact(), mc)
                : null;
        case "atan2" ->
            args.get(0) instanceof RealNumeric y && args.get(1) instanceof RealNumeric x
                ? atan2(y, x, mc)
                : null;
        default -> null;
      };
    }
    if (args.size() != 1) {
      return null;
    }
    Numeric x = args.getFirst();
    if (name.equals("exp") && modulusAbove(x, MAX_EXP, mc)
        || ANGLE_FUNCTIONS.contains(name) && modulusAbove(x, MAX_ANGLE, mc)) {
      return null;
    }
    if (CIRCULAR.contains(name) && x.im().abs().compareValue(MAX_GROWTH) > 0
        || HYPERBOLIC.contains(name) && x.re().abs().compareValue(MAX_GROWTH) > 0) {
      return null;
    }
    if (awayFromCuts) {
      checkInverseCut(name, x, mc);
    }
    return switch (name) {
      case "abs" -> x.modulus(mc);
      case "sign" -> {
        if (awayFromCuts
            && !x.isExact()
            && x.modulus(mc)
                    .toReal(mc)
                    .value()
                    .abs()
                    .scaleByPowerOfTen(mc.getPrecision() - 20)
                    .compareTo(BigDecimal.ONE)
                <= 0) {
          throw new ArithmeticException("sign within rounding of zero");
        }
        // The sign of an inexact value is inexact, as the engine's numeric path gives it: an exact
        // sign of rounding noise would claim an exact value the expression does not have.
        yield x instanceof RealNumeric r
            ? r.isExact() ? Int.of(r.signum()) : Int.of(r.signum()).toReal(mc)
            : null;
      }
      case "sqrt" -> {
        checkRootOfZero(x, mc, awayFromCuts);
        yield x.sqrt(mc);
      }
      case "cbrt" -> {
        checkRootOfZero(x, mc, awayFromCuts);
        yield x instanceof RealNumeric r ? r.root(3, mc) : null;
      }
      case "exp" -> Elementary.exp(x, mc);
      case "log", "ln" -> Elementary.log(x, mc);
      case "log10" -> Elementary.log(x, mc).divide(Elementary.log(Int.of(10), mc), mc);
      case "log2" -> Elementary.log(x, mc).divide(Elementary.log(Int.TWO, mc), mc);
      case "sin" -> nonZero(Elementary.sin(x, mc), mc, awayFromCuts);
      case "cos" -> nonZero(Elementary.cos(x, mc), mc, awayFromCuts);
      case "tan" ->
          Elementary.sin(x, mc).divide(nonPole(Elementary.cos(x, mc), mc, awayFromCuts), mc);
      case "sinh" -> Elementary.sinh(x, mc);
      case "cosh" -> Elementary.cosh(x, mc);
      case "tanh" -> Elementary.tanh(x, mc);
      case "asin" -> Elementary.asin(x, mc);
      case "acos" -> Elementary.acos(x, mc);
      case "atan" -> Elementary.atan(x, mc);
      case "cot" ->
          Elementary.cos(x, mc).divide(nonPole(Elementary.sin(x, mc), mc, awayFromCuts), mc);
      case "sec" -> Int.ONE.divide(nonPole(Elementary.cos(x, mc), mc, awayFromCuts), mc);
      case "csc" -> Int.ONE.divide(nonPole(Elementary.sin(x, mc), mc, awayFromCuts), mc);
      case "acot" ->
          x.isZero()
              ? Elementary.pi(mc).divide(Int.TWO, mc)
              : Elementary.atan(reciprocal(x, mc, awayFromCuts), mc);
      case "asec" -> Elementary.acos(reciprocal(x, mc, awayFromCuts), mc);
      case "acsc" -> Elementary.asin(reciprocal(x, mc, awayFromCuts), mc);
      case "coth" ->
          Elementary.cosh(x, mc).divide(nonPole(Elementary.sinh(x, mc), mc, awayFromCuts), mc);
      case "sech" -> Int.ONE.divide(nonPole(Elementary.cosh(x, mc), mc, awayFromCuts), mc);
      case "csch" -> Int.ONE.divide(nonPole(Elementary.sinh(x, mc), mc, awayFromCuts), mc);
      case "asinh" -> asinh(x, mc);
      case "acosh" -> acosh(x, mc);
      case "atanh" -> atanh(x, mc);
      case "acoth" -> atanh(reciprocal(x, mc, awayFromCuts), mc);
      case "asech" -> acosh(reciprocal(x, mc, awayFromCuts), mc);
      case "acsch" -> asinh(reciprocal(x, mc, awayFromCuts), mc);
      case "floor" -> x instanceof RealNumeric r ? r.floor() : null;
      case "ceil" -> x instanceof RealNumeric r ? r.ceil() : null;
      case "round" -> x instanceof RealNumeric r ? r.round() : null;
      case "trunc" -> x instanceof RealNumeric r ? r.truncate() : null;
      default -> null;
    };
  }

  private static void checkRootOfZero(Numeric x, MathContext mc, boolean awayFromCuts) {
    if (awayFromCuts && !x.isExact() && !x.isZero() && withinRoundingOfZero(x, mc)) {
      throw new ArithmeticException("a root of a zero within rounding");
    }
  }

  private static boolean withinRoundingOfZero(Numeric v, MathContext mc) {
    return v.modulus(mc)
            .toReal(mc)
            .value()
            .abs()
            .scaleByPowerOfTen(mc.getPrecision() - 20)
            .compareTo(BigDecimal.ONE)
        <= 0;
  }

  /** {@code 1/x}; zero, or away from cuts an inexact value within rounding of zero, is a pole. */
  private static Numeric reciprocal(Numeric x, MathContext mc, boolean awayFromCuts) {
    if (x.isZero() || awayFromCuts && !x.isExact() && withinRoundingOfZero(x, mc)) {
      throw new ArithmeticException("division by zero");
    }
    return Int.ONE.divide(x, mc);
  }

  /**
   * Returns {@code d}, the denominator of a circular or hyperbolic function at a pole: throws when
   * it is zero, or, away from cuts, an inexact value within rounding of zero (the true value of
   * {@code cos(π/2)} is 0 and its computed value is noise, so a quotient by it says nothing about
   * the expression).
   */
  private static Numeric nonPole(Numeric d, MathContext mc, boolean awayFromCuts) {
    if (d.isZero() || awayFromCuts && !d.isExact() && withinRoundingOfZero(d, mc)) {
      throw new ArithmeticException("at a pole");
    }
    return d;
  }

  /**
   * Returns {@code v}, a value of {@code sin} or {@code cos}: away from cuts, throws when it is an
   * inexact value within rounding of zero, whose digits are noise ({@code cos(acos(10^-158))} at 34
   * digits is {@code 10^-34} or so, and its logarithm says nothing about the expression).
   */
  private static Numeric nonZero(Numeric v, MathContext mc, boolean awayFromCuts) {
    if (awayFromCuts && !v.isExact() && withinRoundingOfZero(v, mc)) {
      throw new ArithmeticException("at a zero within rounding");
    }
    return v;
  }

  // The inverse hyperbolic functions by their principal formulas, which give the tower's
  // counterclockwise-continuity values off the real domain (PLAN.md §11).

  /** {@code a/b}; a zero divisor is undefined. */
  private static Numeric quotient(Numeric a, Numeric b, MathContext mc) {
    if (b.isZero()) {
      throw new ArithmeticException("division by zero");
    }
    return a.divide(b, mc);
  }

  /** {@code asinh x = log(x + √(x² + 1))}, by symmetry for a negative real {@code x}. */
  private static Numeric asinh(Numeric x, MathContext mc) {
    if (x instanceof RealNumeric r && r.signum() < 0) {
      return asinh(r.negate(), mc).negate();
    }
    return Elementary.log(x.add(x.multiply(x, mc).add(Int.ONE, mc).sqrt(mc), mc), mc);
  }

  /** {@code acosh x = log(x + √(x + 1)·√(x − 1))}, Kahan's form. */
  private static Numeric acosh(Numeric x, MathContext mc) {
    Numeric root = x.add(Int.ONE, mc).sqrt(mc).multiply(x.subtract(Int.ONE, mc).sqrt(mc), mc);
    return Elementary.log(x.add(root, mc), mc);
  }

  /** {@code atanh x = (log(1 + x) − log(1 − x))/2}. */
  private static Numeric atanh(Numeric x, MathContext mc) {
    return Elementary.log(Int.ONE.add(x, mc), mc)
        .subtract(Elementary.log(Int.ONE.subtract(x, mc), mc), mc)
        .divide(Int.TWO, mc);
  }

  /** {@code atan2(y, x)}, the angle of the point {@code (x, y)} in {@code (-π, π]}. */
  private static Numeric atan2(RealNumeric y, RealNumeric x, MathContext mc) {
    if (x.isZero()) {
      if (y.isZero()) {
        throw new ArithmeticException("atan2(0, 0)");
      }
      Numeric half = Elementary.pi(mc).divide(Int.TWO, mc);
      return y.signum() > 0 ? half : half.negate();
    }
    Numeric r = Elementary.atan(y.divide(x, mc), mc);
    if (x.signum() < 0) {
      Numeric pi = Elementary.pi(mc);
      r = y.signum() < 0 ? r.subtract(pi, mc) : r.add(pi, mc);
    }
    return r;
  }

  /** Whether {@code |v| > bound}. */
  private static boolean modulusAbove(Numeric v, BigDecimal bound, MathContext mc) {
    return v.modulus(mc).toReal(mc).value().abs().compareTo(bound) > 0;
  }

  /** Whether a part of {@code v} has a decimal exponent beyond {@code ±limit}. */
  private static boolean decimalExponentBeyond(Numeric v, int limit, MathContext mc) {
    return Math.abs(decimalExponent(v.re(), mc)) > limit
        || Math.abs(decimalExponent(v.im(), mc)) > limit;
  }

  private static long decimalExponent(RealNumeric x, MathContext mc) {
    BigDecimal d =
        switch (x) {
          case Real r -> r.value();
          case Int i -> new BigDecimal(i.value());
          case Rational q -> q.toReal(mc).value();
        };
    return d.signum() == 0 ? 0 : (long) d.precision() - d.scale() - 1;
  }

  private static Numeric realIfZeroImaginary(Numeric x) {
    return x instanceof Complex c && c.im().isZero() ? c.re() : x;
  }
}
