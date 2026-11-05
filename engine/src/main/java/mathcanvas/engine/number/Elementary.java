package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.BiFunction;
import mathcanvas.engine.Cancellation;

/**
 * The elementary functions on any {@link Numeric} at a {@link MathContext}, in {@code BigDecimal}
 * end to end. They serve explicit numeric requests only, never the simplifier: an exact argument is
 * converted to a {@code Real}, and every result is inexact ({@code exp(0)} is {@code Real 1}), so
 * exact special values are left to the rules.
 *
 * <p><b>Accuracy.</b> A real result is faithfully rounded: its error is below one unit in the last
 * place at the context's precision {@code p}. The context's rounding mode is honoured only within
 * that bound. Each part of a complex result is within one unit in its own last place, except a part
 * smaller than {@code 10^-p} times the larger part, which is within one unit in the last place of
 * {@code 10^-p} times the larger part. The bound is reached by Ziv's strategy (Muller,
 * <i>Elementary Functions</i>, §11.4): the function is evaluated at {@code p + 10} digits and at a
 * higher working precision, and the result is accepted when the two agree to a thousandth of the
 * bound; otherwise the working precision grows and the function is evaluated again. The working
 * precision is capped at {@code 4p + 400} digits: an evaluation that has not converged there throws
 * {@link ArithmeticException}. The argument reduction of {@code sin} and {@code cos} adds the
 * angle's integer digits on top, and an angle whose decimal exponent exceeds the cap is refused
 * with {@link ArithmeticException} ("argument too large to reduce at this precision"): the real
 * argument of {@code sin}, {@code cos} and {@code tan}, the real part of a complex one ({@code
 * 2·re} for {@code tan}), the imaginary part of a complex argument of {@code exp}, {@code sinh},
 * {@code cosh} ({@code 2·im} for {@code tanh}), and the angle of {@code exp(y log x)} in a power.
 * The agreement test is a heuristic: two evaluations at different precisions agreeing is strong
 * evidence, not a proof. Under {@link MathContext#UNLIMITED} every function throws {@link
 * ArithmeticException} ("an inexact result needs a finite precision").
 *
 * <p><b>Domains and branches.</b> A real argument outside a function's real domain gives the
 * principal complex value: {@code log(-1)} is {@code πi}, {@code asin(2)} is {@code π/2 -
 * 1.3169…i}. Branch cuts follow counterclockwise continuity (W. Kahan, "Branch cuts for complex
 * elementary functions", 1987; G. L. Steele, <i>Common Lisp the Language</i>, 2nd ed., §12.5.3), as
 * Mathematica does: {@code log} is cut along the negative real axis and continuous with quadrant II
 * there ({@code arg} in {@code (-π, π]}); {@code asin} and {@code acos} along the real axis outside
 * {@code [-1, 1]}, continuous with quadrant II on the left and quadrant IV on the right; {@code
 * atan} along the imaginary axis outside {@code [-i, i]}, continuous with quadrant III below and
 * quadrant I above. {@code log 0}, {@code atan(±i)}, a pole of {@code tan} or {@code tanh}, and a
 * result outside {@code BigDecimal}'s exponent range ({@code exp} checks its argument's magnitude
 * first) throw {@link ArithmeticException}.
 *
 * <p>The complex functions use the textbook formulas through the tower's own arithmetic at the
 * working precision: {@code exp(a + bi) = e^a (cos b + i sin b)}, {@code log z = log|z| + i arg z},
 * {@code sin(a + bi) = sin a cosh b + i cos a sinh b}, {@code tan(a + bi) = (sin 2a + i sinh 2b) /
 * (cos 2a + cosh 2b)}, {@code asin z = -i log(iz + sqrt(1 - z²))}, {@code acos z = -i log(z + i
 * sqrt(1 - z²))}, {@code atan z = (i/2)(log(1 - iz) - log(1 + iz))}; the agreement loop recovers
 * the digits their cancellations cost.
 *
 * <p>Each function has an overload taking a {@link Cancellation}, checked at every iteration of the
 * agreement loop and at every term of the series beneath it, so a high-precision evaluation stops
 * within a term of a request; the overloads without one are never cancelled but by a thread
 * interrupt.
 */
public final class Elementary {

  private Elementary() {}

  private static final int FIRST_GUARD = 10;
  private static final Complex MINUS_I = new Complex(Int.ZERO, Int.MINUS_ONE);

  /**
   * Returns {@code π}.
   *
   * @param mc the context
   * @return {@code π} to within one ulp
   */
  public static Real pi(MathContext mc) {
    return pi(mc, Cancellation.NEVER);
  }

  /**
   * Returns {@code π}.
   *
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return {@code π} to within one ulp
   */
  public static Real pi(MathContext mc, Cancellation cancellation) {
    return (Real) evaluate(mc, cancellation, (w, m) -> new Real(m.pi(w)));
  }

  /**
   * Returns {@code e}.
   *
   * @param mc the context
   * @return {@code e} to within one ulp
   */
  public static Real e(MathContext mc) {
    return e(mc, Cancellation.NEVER);
  }

  /**
   * Returns {@code e}.
   *
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return {@code e} to within one ulp
   */
  public static Real e(MathContext mc, Cancellation cancellation) {
    return (Real) evaluate(mc, cancellation, (w, m) -> new Real(m.exp(BigDecimal.ONE, w)));
  }

  /**
   * Returns {@code e^x}.
   *
   * @param x the argument
   * @param mc the context
   * @return the exponential
   */
  public static Numeric exp(Numeric x, MathContext mc) {
    return exp(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns {@code e^x}.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the exponential
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric exp(Numeric x, MathContext mc, Cancellation cancellation) {
    if (x instanceof Complex) {
      checkReducible(x, true, 1, mc.getPrecision());
    }
    return evaluate(mc, cancellation, (w, m) -> expAt(x, w, m));
  }

  /**
   * Returns the principal natural logarithm; a negative real gives {@code log|x| + πi}.
   *
   * @param x the argument
   * @param mc the context
   * @return the logarithm
   * @throws ArithmeticException if {@code x} is zero
   */
  public static Numeric log(Numeric x, MathContext mc) {
    return log(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the principal natural logarithm; a negative real gives {@code log|x| + πi}.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the logarithm
   * @throws ArithmeticException if {@code x} is zero
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric log(Numeric x, MathContext mc, Cancellation cancellation) {
    return evaluate(mc, cancellation, (w, m) -> logAt(x, w, m));
  }

  /**
   * Returns the sine, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @return the sine
   */
  public static Numeric sin(Numeric x, MathContext mc) {
    return sin(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the sine, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the sine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric sin(Numeric x, MathContext mc, Cancellation cancellation) {
    checkReducible(x, false, 1, mc.getPrecision());
    return evaluate(mc, cancellation, (w, m) -> sinAt(x, w, m));
  }

  /**
   * Returns the cosine, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @return the cosine
   */
  public static Numeric cos(Numeric x, MathContext mc) {
    return cos(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the cosine, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the cosine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric cos(Numeric x, MathContext mc, Cancellation cancellation) {
    checkReducible(x, false, 1, mc.getPrecision());
    return evaluate(mc, cancellation, (w, m) -> cosAt(x, w, m));
  }

  /**
   * Returns the tangent, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @return the tangent
   */
  public static Numeric tan(Numeric x, MathContext mc) {
    return tan(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the tangent, of an argument in radians.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the tangent
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric tan(Numeric x, MathContext mc, Cancellation cancellation) {
    checkReducible(x, false, x instanceof Complex ? 2 : 1, mc.getPrecision());
    return evaluate(mc, cancellation, (w, m) -> tanAt(x, w, m));
  }

  /**
   * Returns the principal arctangent; real in {@code (-π/2, π/2)} for a real argument.
   *
   * @param x the argument
   * @param mc the context
   * @return the arctangent
   * @throws ArithmeticException if {@code x} is {@code ±i}
   */
  public static Numeric atan(Numeric x, MathContext mc) {
    return atan(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the principal arctangent; real in {@code (-π/2, π/2)} for a real argument.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the arctangent
   * @throws ArithmeticException if {@code x} is {@code ±i}
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric atan(Numeric x, MathContext mc, Cancellation cancellation) {
    return evaluate(mc, cancellation, (w, m) -> atanAt(x, w, m));
  }

  /**
   * Returns the principal arcsine; real in {@code [-π/2, π/2]} for a real argument in {@code [-1,
   * 1]}.
   *
   * @param x the argument
   * @param mc the context
   * @return the arcsine
   */
  public static Numeric asin(Numeric x, MathContext mc) {
    return asin(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the principal arcsine; real in {@code [-π/2, π/2]} for a real argument in {@code [-1,
   * 1]}.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the arcsine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric asin(Numeric x, MathContext mc, Cancellation cancellation) {
    return evaluate(mc, cancellation, (w, m) -> asinAt(x, w, m));
  }

  /**
   * Returns the principal arccosine; real in {@code [0, π]} for a real argument in {@code [-1, 1]}.
   *
   * @param x the argument
   * @param mc the context
   * @return the arccosine
   */
  public static Numeric acos(Numeric x, MathContext mc) {
    return acos(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the principal arccosine; real in {@code [0, π]} for a real argument in {@code [-1, 1]}.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the arccosine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric acos(Numeric x, MathContext mc, Cancellation cancellation) {
    return evaluate(mc, cancellation, (w, m) -> acosAt(x, w, m));
  }

  /**
   * Returns the hyperbolic sine.
   *
   * @param x the argument
   * @param mc the context
   * @return the hyperbolic sine
   */
  public static Numeric sinh(Numeric x, MathContext mc) {
    return sinh(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the hyperbolic sine.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the hyperbolic sine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric sinh(Numeric x, MathContext mc, Cancellation cancellation) {
    if (x instanceof Complex) {
      checkReducible(x, true, 1, mc.getPrecision());
    }
    return evaluate(mc, cancellation, (w, m) -> sinhAt(x, w, m));
  }

  /**
   * Returns the hyperbolic cosine.
   *
   * @param x the argument
   * @param mc the context
   * @return the hyperbolic cosine
   */
  public static Numeric cosh(Numeric x, MathContext mc) {
    return cosh(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the hyperbolic cosine.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the hyperbolic cosine
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric cosh(Numeric x, MathContext mc, Cancellation cancellation) {
    if (x instanceof Complex) {
      checkReducible(x, true, 1, mc.getPrecision());
    }
    return evaluate(mc, cancellation, (w, m) -> coshAt(x, w, m));
  }

  /**
   * Returns the hyperbolic tangent.
   *
   * @param x the argument
   * @param mc the context
   * @return the hyperbolic tangent
   */
  public static Numeric tanh(Numeric x, MathContext mc) {
    return tanh(x, mc, Cancellation.NEVER);
  }

  /**
   * Returns the hyperbolic tangent.
   *
   * @param x the argument
   * @param mc the context
   * @param cancellation the request to stop, checked at every term of the series
   * @return the hyperbolic tangent
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Numeric tanh(Numeric x, MathContext mc, Cancellation cancellation) {
    if (x instanceof Complex) {
      checkReducible(x, true, 2, mc.getPrecision());
    }
    return evaluate(mc, cancellation, (w, m) -> tanhAt(x, w, m));
  }

  /** {@code x^y = exp(y log x)} with the principal logarithm; the inexact case of {@code pow}. */
  static Numeric power(Numeric x, Numeric y, MathContext mc, Cancellation cancellation) {
    return evaluate(
        mc,
        cancellation,
        (w, m) -> {
          if (x instanceof RealNumeric a && a.signum() > 0 && y instanceof RealNumeric b) {
            BigDecimal exponent = b.decimal(w).multiply(m.log(a.decimal(w), w), w);
            return new Real(m.exp(exponent, w));
          }
          Numeric exponent = Arithmetic.multiply(y, logAt(x, w, m), w);
          checkReducible(exponent.im(), mc.getPrecision());
          return expAt(exponent, w, m);
        });
  }

  /** The real {@code n}-th root of a positive {@code x}, {@code exp(log(x)/n)}. */
  static Real root(RealNumeric x, int n, MathContext mc, Cancellation cancellation) {
    BigDecimal degree = BigDecimal.valueOf(n);
    return (Real)
        evaluate(
            mc,
            cancellation,
            (w, m) -> new Real(m.exp(m.log(x.decimal(w), w).divide(degree, w), w)));
  }

  /** The principal argument in {@code (-π, π]}; that of zero is zero. */
  static RealNumeric argument(Numeric x, MathContext mc, Cancellation cancellation) {
    return switch (x) {
      case RealNumeric r when r.signum() >= 0 -> r.isExact() ? Int.ZERO : new Real(BigDecimal.ZERO);
      case RealNumeric _ -> pi(mc, cancellation);
      case Complex z ->
          (Real)
              evaluate(
                  mc,
                  cancellation,
                  (w, m) -> new Real(m.atan2(z.im().decimal(w), z.re().decimal(w), w)));
    };
  }

  // The functions at a working context, before the final rounding.

  private static Numeric expAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.exp(r.decimal(w), w));
      case Complex z -> {
        BigDecimal scale = m.exp(z.re().decimal(w), w);
        BigDecimal b = z.im().decimal(w);
        yield complex(scale.multiply(m.cos(b, w), w), scale.multiply(m.sin(b, w), w));
      }
    };
  }

  private static Numeric logAt(Numeric x, MathContext w, BigDecimalMath m) {
    if (x.isZero()) {
      throw new ArithmeticException("logarithm of zero");
    }
    return switch (x) {
      case RealNumeric r when r.signum() > 0 -> new Real(m.log(r.decimal(w), w));
      case RealNumeric r -> complex(m.log(r.negate().decimal(w), w), m.pi(w));
      case Complex z -> {
        BigDecimal a = z.re().decimal(w);
        BigDecimal b = z.im().decimal(w);
        BigDecimal squares = a.multiply(a).add(b.multiply(b));
        yield complex(m.log(squares, w).divide(BigDecimal.TWO, w), m.atan2(b, a, w));
      }
    };
  }

  private static Numeric sinAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.sin(r.decimal(w), w));
      case Complex z -> {
        BigDecimal a = z.re().decimal(w);
        BigDecimal b = z.im().decimal(w);
        yield complex(m.sin(a, w).multiply(m.cosh(b, w), w), m.cos(a, w).multiply(m.sinh(b, w), w));
      }
    };
  }

  private static Numeric cosAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.cos(r.decimal(w), w));
      case Complex z -> {
        BigDecimal a = z.re().decimal(w);
        BigDecimal b = z.im().decimal(w);
        yield complex(
            m.cos(a, w).multiply(m.cosh(b, w), w), m.sin(a, w).multiply(m.sinh(b, w), w).negate());
      }
    };
  }

  private static Numeric tanAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> {
        BigDecimal a = r.decimal(w);
        BigDecimal cosine = m.cos(a, w);
        if (cosine.signum() == 0) {
          throw new ArithmeticException("pole of tan");
        }
        yield new Real(m.sin(a, w).divide(cosine, w));
      }
      case Complex z -> {
        BigDecimal a2 = z.re().decimal(w).multiply(BigDecimal.TWO);
        BigDecimal b2 = z.im().decimal(w).multiply(BigDecimal.TWO);
        BigDecimal den = m.cos(a2, w).add(m.cosh(b2, w), w);
        if (den.signum() == 0) {
          throw new ArithmeticException("pole of tan");
        }
        yield complex(m.sin(a2, w).divide(den, w), m.sinh(b2, w).divide(den, w));
      }
    };
  }

  private static Numeric atanAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.atan(r.decimal(w), w));
      case Complex z -> {
        Numeric iz = Arithmetic.multiply(Complex.I, z, w);
        Numeric difference =
            Arithmetic.add(
                logAt(Arithmetic.add(Int.ONE, iz.negate(), w), w, m),
                logAt(Arithmetic.add(Int.ONE, iz, w), w, m).negate(),
                w);
        yield Arithmetic.multiply(new Complex(Int.ZERO, Rational.of(1, 2)), difference, w);
      }
    };
  }

  private static Numeric asinAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r when r.abs().compareValue(Int.ONE) <= 0 ->
          new Real(m.asin(r.decimal(w), w));
      case RealNumeric r -> {
        // asin x = ±(π/2 - i log(|x| + sqrt(x² - 1))), by the quadrant IV / II continuity.
        BigDecimal halfPi = m.pi(w).divide(BigDecimal.TWO, w);
        BigDecimal l = acosh(r.abs().decimal(w), w, m);
        yield r.signum() > 0 ? complex(halfPi, l.negate()) : complex(halfPi.negate(), l);
      }
      case Complex z -> {
        Numeric root =
            Powers.sqrt(Arithmetic.add(Int.ONE, Arithmetic.multiply(z, z, w).negate(), w), w);
        Numeric sum = Arithmetic.add(Arithmetic.multiply(Complex.I, z, w), root, w);
        yield Arithmetic.multiply(MINUS_I, logAt(sum, w, m), w);
      }
    };
  }

  private static Numeric acosAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r when r.abs().compareValue(Int.ONE) <= 0 ->
          new Real(m.acos(r.decimal(w), w));
      case RealNumeric r -> {
        // acos x = i log(x + sqrt(x² - 1)) for x > 1, π - i log(|x| + sqrt(x² - 1)) for x < -1.
        BigDecimal l = acosh(r.abs().decimal(w), w, m);
        yield r.signum() > 0 ? complex(BigDecimal.ZERO, l) : complex(m.pi(w), l.negate());
      }
      case Complex z -> {
        Numeric root =
            Powers.sqrt(Arithmetic.add(Int.ONE, Arithmetic.multiply(z, z, w).negate(), w), w);
        Numeric sum = Arithmetic.add(z, Arithmetic.multiply(Complex.I, root, w), w);
        yield Arithmetic.multiply(MINUS_I, logAt(sum, w, m), w);
      }
    };
  }

  private static Numeric sinhAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.sinh(r.decimal(w), w));
      case Complex z -> {
        BigDecimal a = z.re().decimal(w);
        BigDecimal b = z.im().decimal(w);
        yield complex(m.sinh(a, w).multiply(m.cos(b, w), w), m.cosh(a, w).multiply(m.sin(b, w), w));
      }
    };
  }

  private static Numeric coshAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.cosh(r.decimal(w), w));
      case Complex z -> {
        BigDecimal a = z.re().decimal(w);
        BigDecimal b = z.im().decimal(w);
        yield complex(m.cosh(a, w).multiply(m.cos(b, w), w), m.sinh(a, w).multiply(m.sin(b, w), w));
      }
    };
  }

  private static Numeric tanhAt(Numeric x, MathContext w, BigDecimalMath m) {
    return switch (x) {
      case RealNumeric r -> new Real(m.tanh(r.decimal(w), w));
      case Complex z -> {
        BigDecimal a2 = z.re().decimal(w).multiply(BigDecimal.TWO);
        BigDecimal b2 = z.im().decimal(w).multiply(BigDecimal.TWO);
        BigDecimal den = m.cosh(a2, w).add(m.cos(b2, w), w);
        if (den.signum() == 0) {
          throw new ArithmeticException("pole of tanh");
        }
        yield complex(m.sinh(a2, w).divide(den, w), m.sin(b2, w).divide(den, w));
      }
    };
  }

  /** {@code log(x + sqrt(x² - 1))} for {@code x > 1}. */
  private static BigDecimal acosh(BigDecimal x, MathContext w, BigDecimalMath m) {
    BigDecimal root = x.multiply(x).subtract(BigDecimal.ONE).sqrt(w);
    return m.log(x.add(root, w), w);
  }

  // The agreement loop.

  /**
   * Evaluates {@code f} at rising working precisions until two evaluations agree, then rounds to
   * {@code mc}; see the class comment.
   */
  private static Numeric evaluate(
      MathContext mc,
      Cancellation cancellation,
      BiFunction<MathContext, BigDecimalMath, Numeric> f) {
    int p = mc.getPrecision();
    if (p == 0) {
      throw new ArithmeticException(Arithmetic.NEEDS_PRECISION);
    }
    BigDecimalMath m = new BigDecimalMath(cancellation);
    int cap = cap(p);
    int w = p + FIRST_GUARD;
    Numeric previous = f.apply(working(w), m);
    while (true) {
      cancellation.check();
      if (w >= cap) {
        throw new ArithmeticException(
            "no agreement within the working-precision cap of " + cap + " digits");
      }
      int next = Math.min(cap, w + Math.max(FIRST_GUARD, w / 2));
      Numeric current = f.apply(working(next), m);
      if (agree(previous, current, p)) {
        return current.toReal(mc);
      }
      previous = current;
      w = next;
    }
  }

  /** The working-precision cap for a result of {@code p} digits. */
  private static int cap(int p) {
    return 4 * p + 400;
  }

  /**
   * Refuses an argument of {@code sin} or {@code cos} whose decimal exponent exceeds the
   * working-precision cap: reducing it modulo {@code π/2} would need {@code π} to that many digits.
   */
  private static void checkReducible(RealNumeric angle, int p) {
    if (angle.isZero()) {
      return;
    }
    BigDecimal magnitude = angle.abs().toReal(new MathContext(3)).value();
    if (magnitude.precision() - magnitude.scale() - 1 > cap(p)) {
      throw new ArithmeticException("argument too large to reduce at this precision: " + angle);
    }
  }

  /** {@link #checkReducible} on the angle a function reduces: a part of {@code x}, scaled. */
  private static void checkReducible(Numeric x, boolean imaginary, int scale, int p) {
    RealNumeric angle = imaginary ? x.im() : x.re();
    checkReducible(Arithmetic.multiply(angle, Int.of(scale), MathContext.UNLIMITED), p);
  }

  /** Whether each part differs by at most a thousandth of its accuracy bound at {@code p}. */
  private static boolean agree(Numeric a, Numeric b, int p) {
    MathContext reading = working(p + 3 * FIRST_GUARD);
    BigDecimal reA = a.re().decimal(reading);
    BigDecimal imA = a.im().decimal(reading);
    BigDecimal reB = b.re().decimal(reading);
    BigDecimal imB = b.im().decimal(reading);
    BigDecimal floor = reB.abs().max(imB.abs()).scaleByPowerOfTen(-p);
    return within(reA, reB, floor, p) && within(imA, imB, floor, p);
  }

  private static boolean within(BigDecimal a, BigDecimal b, BigDecimal floor, int p) {
    BigDecimal scale = b.abs().max(floor);
    if (scale.signum() == 0) {
      return a.signum() == 0;
    }
    // One ulp of scale at p digits is 10^(exponent - p + 1); a thousandth of it, 10^(exponent - p -
    // 2).
    int exponent = scale.precision() - scale.scale() - 1;
    BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(exponent - p - 2);
    return a.subtract(b).abs().compareTo(tolerance) <= 0;
  }

  private static MathContext working(int digits) {
    return new MathContext(digits, RoundingMode.HALF_EVEN);
  }

  private static Numeric complex(BigDecimal re, BigDecimal im) {
    return new Complex(new Real(re), new Real(im));
  }
}
