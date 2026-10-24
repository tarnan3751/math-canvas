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
 * <p>It knows numbers, {@code π} and {@code e}, sums, products, powers (integer exponents through
 * {@code pow(int)}, others through the principal {@code pow}), and the functions {@code abs},
 * {@code sqrt}, {@code cbrt}, {@code root(x, n)}, {@code exp}, {@code log} and {@code ln} (one
 * argument natural, {@code log(x, b)} base {@code b}), {@code log10}, {@code log2}, the circular
 * and hyperbolic functions and {@code asin}, {@code acos}, {@code atan}. Anything else, a symbol
 * without a value, a point where the value is undefined, and a value outside the bounds below give
 * empty.
 *
 * <p>The bounds keep every evaluation fast, since such a point is no useful sample: a power whose
 * base's digits times its exponent's numerator exceed 4,000 bits; {@code exp} or a power of {@code
 * e} with an argument of modulus above 10⁴; a logarithm of a value whose decimal exponent is beyond
 * ±10⁴; a circular or hyperbolic function of an argument of modulus above 10⁶; and any inexact
 * value, an operand or a result, whose decimal exponent is beyond ±10⁴.
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
    try {
      return Optional.ofNullable(value(e, point, mc));
    } catch (ArithmeticException | IllegalArgumentException undefined) {
      return Optional.empty();
    }
  }

  private static Numeric value(Expr e, Map<Symbol, Numeric> point, MathContext mc) {
    Numeric v = compute(e, point, mc);
    return v != null && !v.isExact() && decimalExponentBeyond(v, MAX_EXPONENT, mc) ? null : v;
  }

  private static Numeric compute(Expr e, Map<Symbol, Numeric> point, MathContext mc) {
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
          Numeric v = value(t, point, mc);
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
          Numeric v = value(f, point, mc);
          if (v == null) {
            return null;
          }
          total = total.multiply(v, mc);
        }
        return total;
      }
      case Power p -> {
        Numeric base = value(p.base(), point, mc);
        Numeric exponent = value(p.exponent(), point, mc);
        if (base == null || exponent == null) {
          return null;
        }
        if (tooLarge(base, exponent)) {
          // A power that large is not a useful sample point.
          return null;
        }
        if (p.base().equals(Constant.E) && modulusAbove(exponent, MAX_EXP, mc)) {
          return null;
        }
        if (exponent instanceof Int i && i.value().bitLength() < 16) {
          return base.pow(i.value().intValueExact(), mc);
        }
        return base.pow(exponent, mc);
      }
      case Apply a -> {
        List<Numeric> args = new ArrayList<>();
        for (Expr x : a.args()) {
          Numeric v = value(x, point, mc);
          if (v == null) {
            return null;
          }
          args.add(v);
        }
        return function(a.head().name(), args, mc);
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

  /** Whether {@code base^exponent} would be formed with more than {@link #MAX_BITS} bits. */
  private static boolean tooLarge(Numeric base, Numeric exponent) {
    BigInteger p =
        switch (exponent) {
          case Int i -> i.value();
          case Rational q -> q.num();
          case Real _, Complex _ -> BigInteger.ZERO;
        };
    return BigInteger.valueOf(bits(base)).multiply(p.abs()).compareTo(BigInteger.valueOf(MAX_BITS))
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

  private static final Set<String> ANGLE_FUNCTIONS =
      Set.of("sin", "cos", "tan", "sinh", "cosh", "tanh");

  private static Numeric function(String name, List<Numeric> args, MathContext mc) {
    if (name.startsWith("log") || name.equals("ln")) {
      for (Numeric a : args) {
        if (decimalExponentBeyond(a, MAX_EXPONENT, mc)) {
          return null;
        }
      }
    }
    if (args.size() == 2) {
      return switch (name) {
        case "log" -> Elementary.log(args.get(0), mc).divide(Elementary.log(args.get(1), mc), mc);
        case "root" ->
            args.get(0) instanceof RealNumeric x && args.get(1) instanceof Int n
                ? x.root(n.value().intValueExact(), mc)
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
    return switch (name) {
      case "abs" -> x.modulus(mc);
      case "sqrt" -> x.sqrt(mc);
      case "cbrt" -> x instanceof RealNumeric r ? r.root(3, mc) : null;
      case "exp" -> Elementary.exp(x, mc);
      case "log", "ln" -> Elementary.log(x, mc);
      case "log10" -> Elementary.log(x, mc).divide(Elementary.log(Int.of(10), mc), mc);
      case "log2" -> Elementary.log(x, mc).divide(Elementary.log(Int.TWO, mc), mc);
      case "sin" -> Elementary.sin(x, mc);
      case "cos" -> Elementary.cos(x, mc);
      case "tan" -> Elementary.tan(x, mc);
      case "sinh" -> Elementary.sinh(x, mc);
      case "cosh" -> Elementary.cosh(x, mc);
      case "tanh" -> Elementary.tanh(x, mc);
      case "asin" -> Elementary.asin(x, mc);
      case "acos" -> Elementary.acos(x, mc);
      case "atan" -> Elementary.atan(x, mc);
      default -> null;
    };
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
}
