package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.stat.Statistics;

/**
 * The builtin functions of {@code docs/language.md} §12.1 on numbers.
 *
 * <p>A call whose arguments are all numbers evaluates. When every argument is exact, it evaluates
 * only to an exact result: the tower's exact operations ({@code abs}, {@code floor}, {@code gcd},
 * {@code factorial}, exact roots, …) and the values at 0 and 1 that need no rule ({@code exp(0)},
 * {@code log(1)}, {@code sin(0)}, {@code acos(1)}, {@code atan2} on the axes); anything else, such
 * as {@code sin(1)} or {@code sin(π/6)}, stays for the rule sets. When any argument is inexact, it
 * evaluates through the tower's elementary functions at the precision. A call with any other
 * argument stays as it is. A domain error of the exact path ({@code mod(1, 0)}, {@code log(0)},
 * {@code factorial(-1)}) is {@code undefined}; in the inexact path a zero argument of a logarithm
 * or a reciprocal is {@code undefined}, and any other failure of the tower (an argument too large
 * to reduce, a result out of range) is the error "number out of range at this precision".
 *
 * <p>In degrees or gradians (§14.2) a trigonometric function converts a plain-number argument (an
 * integer, rational or decimal) to radians, exactly: {@code sin(30)} becomes {@code sin(π/6)},
 * which is not plain and is never converted again. An inverse function of a plain number answers in
 * the mode's unit: {@code asin(1/2)} becomes {@code 180·asin(1/2)/π}. Numeric evaluation of a value
 * is in radians, since evaluation has already applied the mode.
 */
final class Functions {

  /** Guard digits of composed inexact functions, rounded once at the end. */
  private static final int GUARD = 10;

  private static final Set<String> TRIG = Set.of("sin", "cos", "tan", "cot", "sec", "csc");
  private static final Set<String> INVERSE_TRIG =
      Set.of("asin", "acos", "atan", "acot", "asec", "acsc");

  private Functions() {}

  /** A value the function does not have: the call is {@code undefined}. */
  private static final class Undefined extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Undefined() {
      super(null, null, false, false);
    }
  }

  /**
   * Applies a builtin function to evaluated, canonical arguments, none undefined.
   *
   * @param head the function's name
   * @param args the arguments
   * @param mc the precision
   * @param mode the angle mode
   * @param node the raw call, for errors
   * @param c the request to stop, checked in the elementary functions
   * @return the canonical value
   * @throws EvaluationException for an error of §16
   */
  static Expr apply(
      Symbol head, List<Expr> args, MathContext mc, AngleMode mode, Expr node, Cancellation c) {
    String name = head.name();
    switch (name) {
      case "nth", "length", "dot", "cross" -> {
        return LinearAlgebra.collection(head, args, mc, node);
      }
      default -> {}
    }
    List<Numeric> xs = new ArrayList<>(args.size());
    boolean exact = true;
    for (Expr arg : args) {
      if (!(arg instanceof Num n)) {
        return new Apply(head, args);
      }
      xs.add(n.value());
      exact &= n.value().isExact();
    }
    if (!exact) {
      return inexact(head, args, xs, mc, mode, node, c);
    }
    try {
      Expr value = exact(name, args, xs, mc, mode, node);
      return value == null ? new Apply(head, args) : Canonical.of(value, mc);
    } catch (ArithmeticException | Undefined domain) {
      return Constant.UNDEFINED;
    }
  }

  /**
   * Evaluates a builtin function on numbers through the inexact path: what {@code numeric} does to
   * a call over numbers, in radians.
   *
   * @param head the function's name
   * @param args the arguments, numbers
   * @param mc the precision
   * @param node the raw call, for errors
   * @param c the request to stop, checked in the elementary functions
   * @return the value, or the call when the function has no numeric value here
   */
  static Expr numeric(Symbol head, List<Expr> args, MathContext mc, Expr node, Cancellation c) {
    if (Statistics.FUNCTIONS.contains(head.name())) {
      // a distribution evaluation left (a discrete law past its exact bound): the provider's value
      Context ctx =
          new Context(
              Assumptions.EMPTY,
              mc,
              false,
              Evaluator.REWRITE_CAP,
              Providers.defaults(),
              new SessionNumerics(0, c),
              c);
      try {
        return Statistics.numeric(new Apply(head, args), ctx).orElse(new Apply(head, args));
      } catch (IllegalArgumentException wrong) {
        throw new EvaluationException(wrong.getMessage(), node);
      }
    }
    List<Numeric> xs = new ArrayList<>(args.size());
    for (Expr arg : args) {
      xs.add(((Num) arg).value());
    }
    return inexact(head, args, xs, mc, AngleMode.RADIANS, node, c);
  }

  // The inexact path.

  private static Expr inexact(
      Symbol head,
      List<Expr> args,
      List<Numeric> xs,
      MathContext mc,
      AngleMode mode,
      Expr node,
      Cancellation c) {
    try {
      Numeric r = inexactValue(head.name(), xs, mc, mode, node, c);
      return r == null ? new Apply(head, args) : new Num(r.toReal(mc));
    } catch (Undefined u) {
      return Constant.UNDEFINED;
    } catch (ArithmeticException tooLarge) {
      throw new EvaluationException("number out of range at this precision", node);
    }
  }

  private static Numeric inexactValue(
      String name, List<Numeric> xs, MathContext mc, AngleMode mode, Expr node, Cancellation c) {
    Numeric x = xs.getFirst();
    MathContext w = new MathContext(mc.getPrecision() + GUARD, RoundingMode.HALF_EVEN);
    if (TRIG.contains(name)) {
      if (mode != AngleMode.RADIANS && x instanceof RealNumeric) {
        x = x.multiply(Elementary.pi(w, c), w).divide(Int.of(mode.halfTurn()), w);
      }
      return switch (name) {
        case "sin" -> Elementary.sin(x, w, c);
        case "cos" -> Elementary.cos(x, w, c);
        case "tan" -> Elementary.tan(x, w, c);
        case "cot" -> div(Elementary.cos(x, w, c), Elementary.sin(x, w, c), w);
        case "sec" -> div(Int.ONE, Elementary.cos(x, w, c), w);
        default -> div(Int.ONE, Elementary.sin(x, w, c), w);
      };
    }
    if (INVERSE_TRIG.contains(name)) {
      Numeric r =
          switch (name) {
            case "asin" -> Elementary.asin(x, w, c);
            case "acos" -> Elementary.acos(x, w, c);
            case "atan" -> Elementary.atan(x, w, c);
            case "acot" ->
                x.isZero()
                    ? Elementary.pi(w, c).divide(Int.TWO, w)
                    : Elementary.atan(div(Int.ONE, x, w), w, c);
            case "asec" -> Elementary.acos(div(Int.ONE, x, w), w, c);
            default -> Elementary.asin(div(Int.ONE, x, w), w, c);
          };
      return x instanceof RealNumeric ? toUnit(r, mode, w, c) : r;
    }
    return switch (name) {
      case "sqrt" -> x.sqrt(w);
      case "sinh" -> Elementary.sinh(x, w, c);
      case "cosh" -> Elementary.cosh(x, w, c);
      case "tanh" -> Elementary.tanh(x, w, c);
      case "coth" -> div(Elementary.cosh(x, w, c), Elementary.sinh(x, w, c), w);
      case "sech" -> div(Int.ONE, Elementary.cosh(x, w, c), w);
      case "csch" -> div(Int.ONE, Elementary.sinh(x, w, c), w);
      case "asinh", "acosh", "atanh", "acoth", "asech", "acsch" ->
          inverseHyperbolic(name, x, mc, c);
      case "exp" -> Elementary.exp(x, w, c);
      case "ln" -> log(x, w, c);
      case "log" -> xs.size() == 1 ? log(x, w, c) : logBase(x, xs.get(1), w, c);
      case "log10" -> logBase(x, Int.of(10), w, c);
      case "log2" -> logBase(x, Int.TWO, w, c);
      case "atan2" -> atan2(x, xs.get(1), mode, w, c);
      case "abs" -> x.modulus(w);
      case "sign" -> x instanceof RealNumeric r ? Int.of(r.signum()).toReal(w) : null;
      case "floor" -> x instanceof RealNumeric r ? r.floor() : null;
      case "ceil" -> x instanceof RealNumeric r ? r.ceil() : null;
      case "round" -> x instanceof RealNumeric r ? r.round() : null;
      case "trunc" -> x instanceof RealNumeric r ? r.truncate() : null;
      case "mod" -> mod(x, xs.get(1), node);
      case "factorial" -> inexactFactorial(x, w);
      case "gcd", "lcm" -> integerValued(xs) ? exactValue(gcdLcm(name, toInts(xs)), w) : null;
      case "binomial" ->
          integerValued(xs)
              ? exactValue(binomial(toInt((RealNumeric) x), toInt((RealNumeric) xs.get(1))), w)
              : null;
      case "min", "max" -> extreme(name, xs, node).toReal(w);
      case "re" -> x.re();
      case "im" -> x.im();
      case "conj" -> x.conj();
      case "arg" -> x.argument(w, c);
      case "cbrt" -> x instanceof RealNumeric r ? r.root(3, w, c) : null;
      case "root" -> root(x, xs.get(1), w, c);
      case "erf", "erfc", "gamma", "beta" -> special(name, xs, mc, node, c);
      default -> null;
    };
  }

  /**
   * A special function of real numbers in {@code double}, through the distribute provider: at most
   * 16 digits, fewer at a lower precision; {@code undefined} at a pole of {@code Γ} or {@code B}. A
   * complex argument leaves the call.
   */
  private static Numeric special(
      String name, List<Numeric> xs, MathContext mc, Expr node, Cancellation c) {
    double[] v = new double[xs.size()];
    for (int i = 0; i < v.length; i++) {
      if (!(xs.get(i) instanceof RealNumeric r)) {
        return null;
      }
      v[i] = r.toDouble();
      if (!Double.isFinite(v[i])) {
        throw new EvaluationException(name + "'s argument is beyond the double range", node);
      }
    }
    if (name.equals("gamma") && pole(v[0]) || name.equals("beta") && (pole(v[0]) || pole(v[1]))) {
      throw new Undefined();
    }
    Law law =
        switch (name) {
          case "erf" -> Law.ERF;
          case "erfc" -> Law.ERFC;
          case "gamma" -> Law.GAMMA;
          default -> Law.BETA;
        };
    double[] parameters = law == Law.BETA ? new double[] {v[1]} : new double[0];
    Context ctx =
        new Context(Assumptions.EMPTY, mc, false, 1, Providers.defaults(), Numerics.NONE, c);
    DistributionValue value =
        ctx.providers()
            .run(new Distribute(law, Kind.VALUE, parameters, new double[] {v[0]}), ctx, Steps.NONE)
            .value();
    if (value.status() != DistributionValue.Status.OK) {
      throw new EvaluationException(name + "'s value is beyond the double range", node);
    }
    MathContext out =
        new MathContext(Math.min(mc.getPrecision(), SPECIAL_DIGITS), RoundingMode.HALF_EVEN);
    return Real.of(BigDecimal.valueOf(value.value()).round(out));
  }

  /** The digits of a special function's value: a {@code double}'s. */
  private static final int SPECIAL_DIGITS = 16;

  /** Whether {@code x} is an integer at or below 0, a pole of {@code Γ}. */
  private static boolean pole(double x) {
    return x <= 0 && x == Math.rint(x);
  }

  private static Numeric div(Numeric a, Numeric b, MathContext w) {
    if (b.isZero()) {
      throw new Undefined();
    }
    return a.divide(b, w);
  }

  private static Numeric toUnit(Numeric radians, AngleMode mode, MathContext w, Cancellation c) {
    if (mode == AngleMode.RADIANS) {
      return radians;
    }
    return radians.multiply(Int.of(mode.halfTurn()), w).divide(Elementary.pi(w, c), w);
  }

  private static Numeric log(Numeric x, MathContext w, Cancellation c) {
    if (x.isZero()) {
      throw new Undefined();
    }
    return Elementary.log(x, w, c);
  }

  private static Numeric logBase(Numeric x, Numeric b, MathContext w, Cancellation c) {
    if (b.isZero() || b.isOne()) {
      throw new Undefined();
    }
    return log(x, w, c).divide(Elementary.log(b, w, c), w);
  }

  /**
   * The inverse hyperbolic functions, faithful at the precision. Each is a logarithmic formula
   * whose differences with 1 ({@code x − 1}, {@code 1 − x²}, …) are formed exactly on the decimal
   * argument; the formula is evaluated at rising working precisions until two evaluations agree to
   * a hundredth of an ulp, as the tower's elementary functions are, since an argument near a point
   * of cancellation ({@code acoth(1 + 10^-30)}) loses as many digits as it has there. Past the
   * precision's reach the leading term is the value: {@code asinh} and {@code atanh} of a tiny
   * {@code x} are {@code x}, {@code acoth} and {@code acsch} of a huge one {@code 1/x}.
   */
  private static Numeric inverseHyperbolic(String name, Numeric x, MathContext mc, Cancellation c) {
    int p = mc.getPrecision();
    MathContext w = new MathContext(p + GUARD, RoundingMode.HALF_EVEN);
    if (x instanceof RealNumeric r && !r.isZero()) {
      long exponent = exponent(r);
      if ((name.equals("asinh") || name.equals("atanh")) && exponent < -(p + GUARD)) {
        return r;
      }
      if ((name.equals("acoth") || name.equals("acsch")) && exponent > p + GUARD) {
        return Int.ONE.divide(r, w);
      }
    }
    int digits = x instanceof Real d ? d.value().precision() : 0;
    long cap = 4L * p + 400 + digits;
    int working = p + GUARD;
    Numeric previous = inverseHyperbolicAt(name, x, working, c);
    while (true) {
      c.check();
      int next = working + Math.max(GUARD, working / 2);
      if (next > cap) {
        throw new ArithmeticException("no agreement within the working-precision cap");
      }
      Numeric current = inverseHyperbolicAt(name, x, next, c);
      // Two zeros agree only where the value is zero by the argument; elsewhere a zero is a
      // cancellation the working precision has not yet resolved (acosh(1 + 10^-140)).
      if (agree(previous, current, p) && (!current.isZero() || zeroAt(name, x))) {
        return current;
      }
      previous = current;
      working = next;
    }
  }

  /**
   * Whether an inverse hyperbolic function is zero at {@code x}: at 0, or at 1 for acosh, asech.
   */
  private static boolean zeroAt(String name, Numeric x) {
    return switch (name) {
      case "asinh", "atanh" -> x.isZero();
      case "acosh", "asech" -> x.isOne();
      default -> false;
    };
  }

  /** The decimal exponent of a non-zero real: {@code ⌊log10 |x|⌋}. */
  private static long exponent(RealNumeric r) {
    BigDecimal v = r.abs().toReal(new MathContext(3)).value();
    return (long) v.precision() - v.scale() - 1;
  }

  /** Whether two evaluations agree to a hundredth of an ulp at {@code p} digits, part by part. */
  private static boolean agree(Numeric a, Numeric b, int p) {
    MathContext fine = new MathContext(p + GUARD, RoundingMode.HALF_EVEN);
    BigDecimal scale = magnitude(b, fine);
    if (scale.signum() == 0) {
      return magnitude(a, fine).signum() == 0;
    }
    BigDecimal bound = scale.movePointLeft(p + 2);
    Numeric difference = a.subtract(b, fine);
    return magnitude(difference, fine).compareTo(bound) <= 0;
  }

  private static BigDecimal magnitude(Numeric x, MathContext mc) {
    return x.re().abs().toReal(mc).value().max(x.im().abs().toReal(mc).value());
  }

  /** One evaluation of an inverse hyperbolic function at {@code digits} working digits. */
  private static Numeric inverseHyperbolicAt(String name, Numeric x, int digits, Cancellation c) {
    MathContext w = new MathContext(digits, RoundingMode.HALF_EVEN);
    MathContext exact = MathContext.UNLIMITED;
    if (!(x instanceof RealNumeric r)) {
      return switch (name) {
        case "asinh" -> asinh(x, w, c);
        case "acosh" -> acosh(x, w, c);
        case "atanh" -> atanh(x, w, c);
        case "acoth" -> atanh(div(Int.ONE, x, w), w, c);
        case "asech" -> acosh(div(Int.ONE, x, w), w, c);
        default -> asinh(div(Int.ONE, x, w), w, c);
      };
    }
    return switch (name) {
      case "asinh" -> asinh(r, w, c);
      case "acosh" -> acosh(r, w, c);
      case "atanh", "acoth" -> {
        // ½ ln |(1 + x)/(1 − x)|, real inside the function's real domain. Outside it, the principal
        // formula atanh x = ½(log(1 + x) − log(1 − x)) adds −π/2·i for x > 1 and +π/2·i for x < −1
        // (counterclockwise continuity), and acoth x = atanh(1/x) the same by the sign of x, +π/2·i
        // at 0.
        RealNumeric plus = (RealNumeric) Int.ONE.add(r, exact);
        RealNumeric minus = (RealNumeric) Int.ONE.subtract(r, exact);
        Numeric re =
            log(div(plus.abs().toReal(w), minus.abs().toReal(w), w), w, c).divide(Int.TWO, w);
        boolean inside = r.abs().compareValue(Int.ONE) < 0;
        if (name.equals("acoth")) {
          // acoth x = ½ ln |(x + 1)/(x − 1)|, the same real part, real outside [-1, 1].
          inside = !inside;
        }
        if (inside) {
          yield re;
        }
        Numeric half = Elementary.pi(w, c).divide(Int.TWO, w);
        yield Complex.of(
            (RealNumeric) re.toReal(w), (RealNumeric) (r.signum() > 0 ? half.negate() : half), w);
      }
      case "asech" -> {
        if (r.isZero()) {
          throw new Undefined();
        }
        if (r.signum() < 0 || r.compareValue(Int.ONE) > 0) {
          // Outside (0, 1]: acosh(1/x), the principal formula.
          yield acosh(Int.ONE.divide(r, w), w, c);
        }
        Numeric root = Int.ONE.subtract(r.multiply(r, exact), exact).toReal(w).sqrt(w);
        yield log(Int.ONE.add(root, w).divide(r, w), w, c);
      }
      default -> {
        if (r.isZero()) {
          throw new Undefined();
        }
        Numeric root = Int.ONE.add(r.multiply(r, exact), exact).toReal(w).sqrt(w);
        Numeric value = log(Int.ONE.add(root, w).divide(r.abs(), w), w, c);
        yield r.signum() < 0 ? value.negate() : value;
      }
    };
  }

  /** {@code asinh x = log(x + √(x² + 1))}, by symmetry for a negative real {@code x}. */
  private static Numeric asinh(Numeric x, MathContext w, Cancellation c) {
    if (x instanceof RealNumeric r && r.signum() < 0) {
      return asinh(r.negate(), w, c).negate();
    }
    return Elementary.log(x.add(x.multiply(x, w).add(Int.ONE, w).sqrt(w), w), w, c);
  }

  /**
   * {@code acosh x = log(x + √(x + 1)·√(x − 1))}, Kahan's form, right on both sides of 1, with
   * {@code x ± 1} formed exactly for a real {@code x}; on {@code [-1, 1]} it is {@code i·acos(x)}
   * exactly, with no rounding residue in the real part, and {@code acosh(1)} is a real 0.
   */
  private static Numeric acosh(Numeric x, MathContext w, Cancellation c) {
    if (x instanceof RealNumeric r && r.abs().compareValue(Int.ONE) <= 0 && !r.isOne()) {
      return Complex.of(Int.ZERO, (RealNumeric) Elementary.acos(r, w, c), w);
    }
    MathContext sides = x instanceof RealNumeric ? MathContext.UNLIMITED : w;
    Numeric root =
        x.add(Int.ONE, sides)
            .toReal(w)
            .sqrt(w)
            .multiply(x.subtract(Int.ONE, sides).toReal(w).sqrt(w), w);
    return log(x.add(root, w), w, c);
  }

  /** {@code atanh x = (log(1 + x) − log(1 − x))/2}; undefined at {@code ±1}. */
  private static Numeric atanh(Numeric x, MathContext w, Cancellation c) {
    return log(Int.ONE.add(x, w), w, c)
        .subtract(log(Int.ONE.subtract(x, w), w, c), w)
        .divide(Int.TWO, w);
  }

  private static Numeric atan2(
      Numeric y, Numeric x, AngleMode mode, MathContext w, Cancellation c) {
    if (!(y instanceof RealNumeric ry && x instanceof RealNumeric rx)) {
      return null;
    }
    if (rx.isZero()) {
      if (ry.isZero()) {
        throw new Undefined();
      }
      Numeric half = Elementary.pi(w, c).divide(Int.TWO, w);
      return toUnit(ry.signum() > 0 ? half : half.negate(), mode, w, c);
    }
    Numeric r = Elementary.atan(ry.divide(rx, w), w, c);
    if (rx.signum() < 0) {
      Numeric pi = Elementary.pi(w, c);
      r = ry.signum() < 0 ? r.subtract(pi, w) : r.add(pi, w);
    }
    return toUnit(r, mode, w, c);
  }

  /**
   * {@code a − b·floor(a/b)} computed exactly on the decimals' values and rounded once, so it lies
   * in {@code [0, b)} (or {@code (b, 0]}) however far apart the operands are.
   */
  private static Numeric mod(Numeric a, Numeric b, Expr node) {
    if (!(a instanceof RealNumeric ra && b instanceof RealNumeric rb)) {
      return null;
    }
    if (rb.isZero()) {
      throw new Undefined();
    }
    RealNumeric p = NumericForm.exactValue(ra, node);
    RealNumeric q = NumericForm.exactValue(rb, node);
    RealNumeric quotient = (RealNumeric) p.divide(q, MathContext.UNLIMITED);
    return p.subtract(q.multiply(quotient.floor(), MathContext.UNLIMITED), MathContext.UNLIMITED);
  }

  /** Whether every argument is real with an integer value. */
  private static boolean integerValued(List<Numeric> xs) {
    for (Numeric x : xs) {
      if (!(x instanceof RealNumeric r) || !r.isInteger()) {
        return false;
      }
    }
    return true;
  }

  private static List<Numeric> toInts(List<Numeric> xs) {
    List<Numeric> out = new ArrayList<>(xs.size());
    for (Numeric x : xs) {
      out.add(toInt((RealNumeric) x));
    }
    return out;
  }

  /** An exact result made inexact, or null when there is none. */
  private static Numeric exactValue(Expr exact, MathContext w) {
    return exact instanceof Num n ? n.value().toReal(w) : null;
  }

  private static Numeric inexactFactorial(Numeric x, MathContext w) {
    if (!(x instanceof RealNumeric r) || !r.isInteger()) {
      return null;
    }
    Expr exact = factorial(toInt(r));
    return exact instanceof Num n ? n.value().toReal(w) : null;
  }

  private static Numeric root(Numeric x, Numeric n, MathContext w, Cancellation c) {
    if (!(x instanceof RealNumeric r) || !(n instanceof Int k) || !fitsDegree(k)) {
      return null;
    }
    int degree = k.value().intValue();
    if (degree % 2 == 0 && r.signum() < 0) {
      throw new Undefined();
    }
    return r.root(degree, w, c);
  }

  // The exact path.

  /** The exact value, raw, or null when the call stays; a domain error throws. */
  private static Expr exact(
      String name, List<Expr> args, List<Numeric> xs, MathContext mc, AngleMode mode, Expr node) {
    Expr arg = args.getFirst();
    Numeric x = xs.getFirst();
    boolean plain = x instanceof RealNumeric;
    if (TRIG.contains(name)) {
      Expr angle =
          mode != AngleMode.RADIANS && plain
              ? Canonical.of(Product.of(arg, mode.toRadians()), mc)
              : arg;
      if (angle instanceof Num a && a.value().isZero()) {
        return switch (name) {
          case "sin", "tan" -> Num.ZERO;
          case "cos", "sec" -> Num.ONE;
          default -> Constant.UNDEFINED;
        };
      }
      return new Apply(Symbol.of(name), List.of(angle));
    }
    if (INVERSE_TRIG.contains(name)) {
      Expr r =
          (name.equals("asin") || name.equals("atan")) && x.isZero()
                  || name.equals("acos") && x.isOne()
              ? Num.ZERO
              : new Apply(Symbol.of(name), args);
      return mode != AngleMode.RADIANS && plain ? Product.of(r, mode.fromRadians()) : r;
    }
    return switch (name) {
      case "sqrt" -> Power.of(arg, Num.of(Rational.of(1, 2)));
      case "sinh", "tanh", "asinh", "atanh" -> x.isZero() ? Num.ZERO : null;
      case "cosh", "sech" -> x.isZero() ? Num.ONE : null;
      case "coth", "csch" -> x.isZero() ? Constant.UNDEFINED : null;
      case "acosh" -> x.isOne() ? Num.ZERO : null;
      case "exp" -> x.isZero() ? Num.ONE : null;
      case "erf" -> x.isZero() ? Num.ZERO : null;
      case "erfc" -> x.isZero() ? Num.ONE : null;
      case "ln", "log10", "log2" -> exactLog(x);
      case "log" -> xs.size() == 1 ? exactLog(x) : exactLog(x, xs.get(1));
      case "atan2" -> exactAtan2(x, xs.get(1), mode);
      case "abs" -> exactAbs(x);
      case "sign" -> plain ? Num.of(((RealNumeric) x).signum()) : null;
      case "floor" -> plain ? new Num(((RealNumeric) x).floor()) : null;
      case "ceil" -> plain ? new Num(((RealNumeric) x).ceil()) : null;
      case "round" -> plain ? new Num(((RealNumeric) x).round()) : null;
      case "trunc" -> plain ? new Num(((RealNumeric) x).truncate()) : null;
      case "mod" -> exactMod(x, xs.get(1));
      case "gcd", "lcm" -> gcdLcm(name, xs);
      case "factorial" -> x instanceof Int n ? factorial(n) : null;
      case "binomial" -> x instanceof Int n && xs.get(1) instanceof Int k ? binomial(n, k) : null;
      case "min", "max" -> new Num(extreme(name, xs, node));
      case "re" -> new Num(x.re());
      case "im" -> new Num(x.im());
      case "conj" -> new Num(x.conj());
      case "arg" -> plain ? (((RealNumeric) x).signum() < 0 ? Constant.PI : Num.ZERO) : null;
      case "numerator" -> x instanceof Rational || x instanceof Int ? numerator(x) : null;
      case "denominator" -> x instanceof Rational || x instanceof Int ? denominator(x) : null;
      case "cbrt" -> plain ? exactRoot((RealNumeric) x, 3, mc) : null;
      case "root" ->
          plain && xs.get(1) instanceof Int k && fitsDegree(k)
              ? exactRoot((RealNumeric) x, k.value().intValue(), mc)
              : null;
      default -> null;
    };
  }

  private static Expr exactLog(Numeric x) {
    if (x.isZero()) {
      throw new Undefined();
    }
    return x.isOne() ? Num.ZERO : null;
  }

  private static Expr exactLog(Numeric x, Numeric b) {
    if (x.isZero() || b.isZero() || b.isOne()) {
      throw new Undefined();
    }
    if (x.isOne()) {
      return Num.ZERO;
    }
    return x.equals(b) ? Num.ONE : null;
  }

  private static Expr exactAtan2(Numeric y, Numeric x, AngleMode mode) {
    if (!(y instanceof RealNumeric ry && x instanceof RealNumeric rx)) {
      return null;
    }
    Expr r;
    if (ry.isZero() && rx.isZero()) {
      throw new Undefined();
    } else if (ry.isZero()) {
      r = rx.signum() > 0 ? Num.ZERO : Constant.PI;
    } else if (rx.isZero()) {
      r = Product.of(Num.of(Rational.of(ry.signum(), 2)), Constant.PI);
    } else {
      r = new Apply(Symbol.of("atan2"), List.of(new Num(y), new Num(x)));
    }
    return mode == AngleMode.RADIANS ? r : Product.of(r, mode.fromRadians());
  }

  private static Expr exactAbs(Numeric x) {
    if (x instanceof RealNumeric r) {
      return new Num(r.abs());
    }
    Num re = new Num(x.re());
    Num im = new Num(x.im());
    return Power.of(
        Sum.of(Power.of(re, Num.of(2)), Power.of(im, Num.of(2))), Num.of(Rational.of(1, 2)));
  }

  private static Expr exactMod(Numeric a, Numeric b) {
    if (!(a instanceof RealNumeric ra && b instanceof RealNumeric rb)) {
      return null;
    }
    if (rb.isZero()) {
      throw new Undefined();
    }
    if (ra instanceof Int ia && rb instanceof Int ib) {
      return new Num(ia.mod(ib));
    }
    RealNumeric q = (RealNumeric) ra.divide(rb, MathContext.UNLIMITED);
    return new Num(
        ra.subtract(rb.multiply(q.floor(), MathContext.UNLIMITED), MathContext.UNLIMITED));
  }

  private static Expr gcdLcm(String name, List<Numeric> xs) {
    Int acc = null;
    for (Numeric x : xs) {
      if (!(x instanceof Int n)) {
        return null;
      }
      acc = acc == null ? n : name.equals("gcd") ? acc.gcd(n) : acc.lcm(n);
    }
    return new Num(acc);
  }

  /** {@code n!} for a non-negative {@code n} whose result stays under the fold limit. */
  private static Expr factorial(Int n) {
    if (n.signum() < 0) {
      throw new Undefined();
    }
    BigInteger v = n.value();
    if (v.bitLength() > 31 || bits(v.longValue(), v.longValue()) > Canonical.MAX_FOLD_BITS) {
      return null;
    }
    return new Num(n.factorial());
  }

  /** {@code binomial(n, k)} when the result stays under the fold limit. */
  private static Expr binomial(Int n, Int k) {
    BigInteger nv = n.value();
    BigInteger kv = k.value();
    if (kv.signum() < 0 || (nv.signum() >= 0 && kv.compareTo(nv) > 0)) {
      return new Num(n.binomial(k));
    }
    BigInteger terms = nv.signum() >= 0 ? kv.min(nv.subtract(kv)) : kv;
    BigInteger top = nv.abs().add(kv);
    if (terms.bitLength() > 31
        || top.bitLength() > 62
        || bits(terms.longValue(), top.longValue()) > Canonical.MAX_FOLD_BITS) {
      return null;
    }
    return new Num(n.binomial(k));
  }

  /** An upper bound of the bits of a product of {@code count} factors of at most {@code max}. */
  private static long bits(long count, long max) {
    return count * (64 - Long.numberOfLeadingZeros(Math.max(1, max)));
  }

  private static RealNumeric extreme(String name, List<Numeric> xs, Expr node) {
    RealNumeric best = null;
    for (Numeric x : xs) {
      if (!(x instanceof RealNumeric r)) {
        throw new EvaluationException("complex numbers are not ordered", node);
      }
      int c = best == null ? 0 : r.compareValue(best);
      if (best == null || (name.equals("min") ? c < 0 : c > 0)) {
        best = r;
      }
    }
    return best;
  }

  private static Expr numerator(Numeric x) {
    return new Num(x instanceof Int i ? i.numerator() : ((Rational) x).numerator());
  }

  private static Expr denominator(Numeric x) {
    return new Num(x instanceof Int i ? i.denominator() : ((Rational) x).denominator());
  }

  /** The real root when it is exact; even roots of negative numbers are undefined. */
  private static Expr exactRoot(RealNumeric x, int n, MathContext mc) {
    if (n % 2 == 0 && x.signum() < 0) {
      throw new Undefined();
    }
    RealNumeric r = x.root(n, mc);
    return r.isExact() ? new Num(r) : null;
  }

  private static boolean fitsDegree(Int k) {
    return k.signum() > 0 && k.value().bitLength() < 32;
  }

  private static Int toInt(RealNumeric r) {
    return switch (r) {
      case Int i -> i;
      case Rational q -> q.floor();
      case Real d -> Int.of(d.value().toBigIntegerExact());
    };
  }
}
