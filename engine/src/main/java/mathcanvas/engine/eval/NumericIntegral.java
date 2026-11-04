package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.compile.UnsupportedException;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Quadrature;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;

/**
 * The numeric path of a definite integral the exact path leaves ({@code docs/language.md} §10.2):
 * under {@code numeric(…)}, or a decimal number format, an {@code Integral} with numeric bounds
 * whose integrand is a function of its variable alone is evaluated by the double-exponential rule
 * ({@link Quadrature}) over the numeric form of the integrand at each point. The trace step is
 * {@code numeric.quadrature} with the digits, or {@code numeric.none} when the rule gives no value;
 * in degrees and gradians an integrand with an angle of the variable is left with {@code
 * numeric.angle-mode}, since the numeric form is in radians.
 */
final class NumericIntegral {

  /** The rule set of the steps. */
  static final String RULE_SET = "numeric";

  /** The steps it records, each narrated by the key {@code numeric.<step>}. */
  static final List<String> STEPS =
      List.of("quadrature", "none", "angle-mode", "digits", "cubature", "cubature-capped");

  /**
   * The most digits an iterated integral's value has: its nested quadrature is in {@code double}
   * ({@link #iterated}).
   */
  static final int MAX_CUBATURE_DIGITS = 12;

  /** The fewest digits the two runs must agree on for a value. */
  static final int MIN_CUBATURE_DIGITS = 8;

  /**
   * The most samples of the innermost integrand both runs take together; past it the integral is
   * left (measured: {@code docs/reference/engine.md}, <b>The numeric path of an iterated
   * integral</b>).
   */
  static final long SAMPLE_BUDGET = 20_000_000;

  /** The tolerance of the nested quadrature's value; its check runs at the default, 100× looser. */
  private static final double CUBATURE_TOLERANCE = 1e-12;

  /**
   * The most digits an integral is evaluated to: beyond, the node is left with the step {@code
   * numeric.digits} (a 300-digit run took 522 s under load, and the decimal format allows 10,000).
   */
  static final int MAX_DIGITS = 100;

  /** The relative and the absolute (of the integral of the absolute value) cross-check bounds. */
  private static final double CHECK_RELATIVE = 1e-8;

  private static final double CHECK_ABSOLUTE = 1e-10;

  private static final Set<String> ANGLES =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "acot", "asec", "acsc",
          "atan2");

  private NumericIntegral() {}

  /**
   * The value of an integral and its step.
   *
   * @param value the value
   * @param step the one-step trace, or null when not traced or nothing was tried
   * @param numeric whether the quadrature gave the value, which the worksheet marks
   */
  record Outcome(Expr value, Trace step, boolean numeric) {

    /** A value the quadrature did not give: the integral as it came, or its refusal. */
    Outcome(Expr value, Trace step) {
      this(value, step, false);
    }
  }

  /**
   * The numeric value of {@code e} when it is a definite integral the rule applies to; {@code e}
   * itself otherwise.
   *
   * @param e the evaluated value of an integral
   * @param digits the digits of the value
   * @param radians whether the angle mode is radians
   * @param traced whether to record the step
   * @param node the raw node, for errors
   * @param c the request to stop
   * @return the value and its step
   */
  static Outcome of(
      Expr e, int digits, boolean radians, boolean traced, Expr node, Cancellation c) {
    if (e instanceof Integral i
        && i.isDefinite()
        && i.integrand() instanceof Integral inner
        && inner.isDefinite()) {
      return iterated(i, digits, radians, traced, node, c);
    }
    if (!(e instanceof Integral i) || !i.isDefinite() || !onlyVariable(i.integrand(), i.var())) {
      return new Outcome(e, null);
    }
    MathContext w = new MathContext(digits + Quadrature.GUARD, RoundingMode.HALF_EVEN);
    Quadrature.Bound lower = bound(i.lower(), w, node, c);
    Quadrature.Bound upper = bound(i.upper(), w, node, c);
    if (lower == null || upper == null) {
      return new Outcome(e, null);
    }
    if (!radians && hasAngle(i.integrand(), i.var())) {
      return new Outcome(e, traced ? step(e, e, "angle-mode", Bindings.EMPTY) : null);
    }
    if (digits > MAX_DIGITS) {
      Bindings cap =
          new Bindings(List.of(new Bindings.Binding("digits_", Num.of(MAX_DIGITS), null, null)));
      return new Outcome(e, traced ? step(e, e, "digits", cap) : null);
    }
    String name = i.var().name();
    Quadrature.Result r =
        Quadrature.integrate(
            (x, mc) -> sample(i.integrand(), name, x, mc, node, c),
            lower,
            upper,
            digits,
            check(i, lower, upper, c),
            c);
    return switch (r) {
      case Quadrature.Value v -> {
        Expr value = new Num(v.value());
        Bindings digitsBound =
            new Bindings(List.of(new Bindings.Binding("digits_", Num.of(digits), null, null)));
        yield new Outcome(value, traced ? step(e, value, "quadrature", digitsBound) : null, true);
      }
      case Quadrature.None _ -> new Outcome(e, traced ? step(e, e, "none", Bindings.EMPTY) : null);
    };
  }

  /**
   * The cross-check of a finite interval: Gauss–Kronrod in {@code double} over the compiled
   * integrand ({@link mathcanvas.engine.compile.Quadrature}), which bisects where the local error
   * is, so it sees a feature narrower than the coarse levels' nodes. A value stands when the double
   * rule converged and agrees within {@code 1e-8} of it or {@code 1e-10} of the integral of the
   * absolute value; an integrand that does not compile, a double rule that does not converge, or an
   * infinite bound has no cross-check. The integrand is compiled in radians, as its numeric form is
   * taken.
   */
  private static Quadrature.Check check(
      Integral i, Quadrature.Bound lower, Quadrature.Bound upper, Cancellation c) {
    Expr integrand = i.integrand();
    Symbol x = i.var();
    double a;
    double b;
    if (lower.value() != null && upper.value() != null) {
      a = lower.value().toDouble();
      b = upper.value().toDouble();
    } else {
      // an infinite end: the integral over t in [0, 1] (or [−1, 1]) of the integrand at x(t)
      // times dx/dt, whose nodes the double rule never puts on the ends where x(t) is infinite
      boolean reversed = lower.infinity() > 0 || upper.infinity() < 0;
      Quadrature.Bound lo = reversed ? upper : lower;
      Quadrature.Bound hi = reversed ? lower : upper;
      Symbol t = Symbol.of(x.name().equals("t") ? "u" : "t");
      Expr oneLess = Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, t));
      Expr at;
      Expr jacobian;
      if (lo.value() == null && hi.value() == null) {
        Expr square = Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, Power.of(t, Num.of(2))));
        at = Product.of(t, Power.of(square, Num.MINUS_ONE));
        jacobian =
            Product.of(Sum.of(Num.ONE, Power.of(t, Num.of(2))), Power.of(square, Num.of(-2)));
        a = -1;
      } else {
        Expr end = new Num(lo.value() != null ? lo.value() : hi.value());
        Expr away = Product.of(t, Power.of(oneLess, Num.MINUS_ONE));
        at = lo.value() != null ? Sum.of(end, away) : Sum.of(end, Product.of(Num.MINUS_ONE, away));
        jacobian = Power.of(oneLess, Num.of(-2));
        a = 0;
      }
      b = 1;
      TreeMap<String, Expr> map = new TreeMap<>();
      map.put(x.name(), at);
      integrand = Canonical.of(Product.of(Substitution.apply(integrand, map), jacobian));
      x = t;
      if (reversed) {
        double swap = a;
        a = b;
        b = swap;
      }
    }
    Compiled f;
    try {
      f = Compiled.compile(integrand, List.of(x), Settings.DEFAULT, c);
    } catch (UnsupportedException refused) {
      return Quadrature.Check.NONE;
    }
    mathcanvas.engine.compile.Quadrature.Estimate quick =
        mathcanvas.engine.compile.Quadrature.integrate(f, a, b, c);
    if (!quick.converged()) {
      return Quadrature.Check.NONE;
    }
    return (value, l1) ->
        Math.abs(quick.value() - value.toDouble())
            <= Math.max(
                CHECK_RELATIVE * Math.abs(value.toDouble()), CHECK_ABSOLUTE * l1.toDouble());
  }

  private static Trace step(Expr before, Expr after, String rule, Bindings bindings) {
    Rewrite r = new Rewrite(before, after, RULE_SET, rule, Path.ROOT, bindings);
    return new Trace(before, after, List.of(r), List.of());
  }

  /**
   * The integrand at {@code x}: its numeric form at {@code w}, when that is a real number; null
   * when it is undefined or complex. A value the tower leaves unevaluated is beyond its range.
   */
  private static Real sample(
      Expr integrand, String name, Real x, MathContext w, Expr node, Cancellation c) {
    TreeMap<String, Expr> map = new TreeMap<>();
    map.put(name, new Num(x));
    Expr v;
    try {
      v = NumericForm.numeric(Substitution.apply(integrand, map), w, node, c);
    } catch (EvaluationException outOfRange) {
      throw new ArithmeticException(outOfRange.getMessage());
    }
    if (v instanceof Num n) {
      return n.value() instanceof RealNumeric real ? real.toReal(w) : null;
    }
    if (v.equals(Constant.UNDEFINED)) {
      return null;
    }
    // a power or a function the tower leaves on numbers: beyond its range
    throw new ArithmeticException("out of range at this precision");
  }

  /** A bound as a number or an infinity; null when it is neither. */
  private static Quadrature.Bound bound(Expr b, MathContext w, Expr node, Cancellation c) {
    if (b.equals(Constant.INFINITY)) {
      return Quadrature.Bound.POSITIVE_INFINITY;
    }
    if (b instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst().equals(Num.MINUS_ONE)
        && p.factors().get(1).equals(Constant.INFINITY)) {
      return Quadrature.Bound.NEGATIVE_INFINITY;
    }
    if (!constant(b)) {
      return null;
    }
    Expr v = NumericForm.numeric(b, w, node, c);
    return v instanceof Num n && n.value() instanceof RealNumeric real
        ? Quadrature.Bound.finite(real.toReal(w))
        : null;
  }

  /** Whether a tree holds no symbol, no infinity and no {@code undefined}. */
  private static boolean constant(Expr e) {
    if (e instanceof Symbol || e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (!constant(op)) {
        return false;
      }
    }
    return true;
  }

  /**
   * The numeric value of an iterated integral, each level's integrand the next level's integral
   * down to an innermost integrand free of integrals, and every symbol a variable of a level
   * outside it: nested adaptive Gauss–Kronrod in {@code double} over the compiled innermost
   * integrand and bounds (each inner integral a function of the outer variables), at the tolerance
   * {@code 1e-12} and again at {@code 1e-10}; the value stands when both converge and agree in at
   * least eight leading digits (a difference under {@code 1e-15} at a value under {@code 1e-15}
   * counts as full agreement), and is given to the digits they agree in, at most twelve and at most
   * the digits asked, with the step {@code numeric.cubature}, or {@code numeric.cubature-capped}
   * when more digits were asked than given. Anything else leaves the integral, with {@code
   * numeric.none}; an infinite bound, a piece that does not compile, and in degrees or gradians an
   * angle of a variable ({@code numeric.angle-mode}) too.
   */
  private static Outcome iterated(
      Integral i, int digits, boolean radians, boolean traced, Expr node, Cancellation c) {
    List<Integral> levels = new ArrayList<>();
    Expr e = i;
    while (e instanceof Integral j && j.isDefinite()) {
      levels.add(j);
      e = j.integrand();
    }
    Expr innermost = e;
    Trace none = traced ? step(i, i, "none", Bindings.EMPTY) : null;
    if (hasIntegral(innermost)) {
      return new Outcome(i, none);
    }
    List<Symbol> vars = levels.stream().map(Integral::var).toList();
    for (int k = 0; k < levels.size(); k++) {
      Set<Symbol> outside = new HashSet<>(vars.subList(0, k));
      Integral level = levels.get(k);
      if (!onlyAmong(level.lower(), outside) || !onlyAmong(level.upper(), outside)) {
        // an infinite bound, or one with a symbol of no level outside it
        return new Outcome(i, none);
      }
    }
    if (!onlyAmong(innermost, new HashSet<>(vars))) {
      return new Outcome(i, none);
    }
    if (!radians && vars.stream().anyMatch(v -> hasAngle(innermost, v) || boundsAngle(levels, v))) {
      return new Outcome(i, traced ? step(i, i, "angle-mode", Bindings.EMPTY) : null);
    }
    Compiled f;
    List<Compiled[]> bounds = new ArrayList<>();
    try {
      f = Compiled.compile(innermost, vars, Settings.DEFAULT, c);
      for (Integral level : levels) {
        bounds.add(
            new Compiled[] {
              Compiled.compile(level.lower(), vars, Settings.DEFAULT, c),
              Compiled.compile(level.upper(), vars, Settings.DEFAULT, c)
            });
      }
    } catch (UnsupportedException refused) {
      return new Outcome(i, none);
    }
    long[] samples = {0};
    double fine;
    double coarse;
    try {
      fine = nested(f, bounds, new double[vars.size()], 0, CUBATURE_TOLERANCE, samples, c);
      coarse =
          nested(
              f,
              bounds,
              new double[vars.size()],
              0,
              mathcanvas.engine.compile.Quadrature.DEFAULT_TOLERANCE,
              samples,
              c);
    } catch (Budget spent) {
      return new Outcome(i, none);
    }
    int agree = agreeing(fine, coarse);
    if (agree < MIN_CUBATURE_DIGITS) {
      return new Outcome(i, none);
    }
    int used = Math.min(digits, Math.min(agree, MAX_CUBATURE_DIGITS));
    Expr value = new Num(new Real(new BigDecimal(fine).round(new MathContext(used))));
    Bindings digitsBound =
        new Bindings(List.of(new Bindings.Binding("digits_", Num.of(used), null, null)));
    String rule = digits > used ? "cubature-capped" : "cubature";
    return new Outcome(value, traced ? step(i, value, rule, digitsBound) : null, true);
  }

  /**
   * The leading digits two runs of the nested quadrature agree on: of their difference relative to
   * the finer value, 16 when they are equal, 0 when either is not finite; an absolute difference
   * below {@code 1e-15} at a value below {@code 1e-15} counts as full agreement (the value is 0).
   */
  private static int agreeing(double fine, double coarse) {
    if (!Double.isFinite(fine) || !Double.isFinite(coarse)) {
      return 0;
    }
    double difference = Math.abs(fine - coarse);
    if (difference == 0 || (Math.abs(fine) < 1e-15 && difference < 1e-15)) {
      return 16;
    }
    double relative = difference / Math.abs(fine);
    return relative >= 1 ? 0 : (int) Math.floor(-StrictMath.log10(relative));
  }

  /** Thrown when the nested quadrature has taken {@link #SAMPLE_BUDGET} samples. */
  private static final class Budget extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Budget() {
      super(null, null, false, false);
    }
  }

  /**
   * Level {@code k}'s integral by adaptive Gauss–Kronrod, the variables of the levels outside it
   * set in {@code point}: the innermost level at {@code tolerance}, each level around it ten times
   * looser; NaN when a level does not converge or a bound or sample is not finite.
   */
  private static double nested(
      Compiled f,
      List<Compiled[]> bounds,
      double[] point,
      int k,
      double tolerance,
      long[] samples,
      Cancellation c) {
    double a = bounds.get(k)[0].eval(point);
    double b = bounds.get(k)[1].eval(point);
    mathcanvas.engine.compile.Quadrature.Estimate est =
        mathcanvas.engine.compile.Quadrature.integrate(
            x -> {
              point[k] = x;
              if (k < bounds.size() - 1) {
                return nested(f, bounds, point, k + 1, tolerance, samples, c);
              }
              if (++samples[0] > SAMPLE_BUDGET) {
                throw new Budget();
              }
              return f.eval(point);
            },
            a,
            b,
            // each level ten times looser than the one inside it, whose error its samples carry
            tolerance * StrictMath.pow(10, bounds.size() - 1 - k),
            c);
    return est.converged() ? est.value() : Double.NaN;
  }

  /** Whether a level's bound holds an angle of {@code v}. */
  private static boolean boundsAngle(List<Integral> levels, Symbol v) {
    for (Integral level : levels) {
      if (hasAngle(level.lower(), v) || hasAngle(level.upper(), v)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasIntegral(Expr e) {
    if (e instanceof Integral) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (hasIntegral(op)) {
        return true;
      }
    }
    return false;
  }

  /** Whether every symbol of {@code e} is in {@code allowed}, with no infinity or undefined. */
  private static boolean onlyAmong(Expr e, Set<Symbol> allowed) {
    if (e instanceof Symbol s) {
      return allowed.contains(s);
    }
    if (e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (!onlyAmong(op, allowed)) {
        return false;
      }
    }
    return true;
  }

  /** Whether every symbol of the integrand is the variable. */
  private static boolean onlyVariable(Expr e, Symbol x) {
    if (e instanceof Symbol s) {
      return s.equals(x);
    }
    if (e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
      return false;
    }
    for (Expr op : Operands.of(e)) {
      if (!onlyVariable(op, x)) {
        return false;
      }
    }
    return true;
  }

  /** Whether a circular or inverse circular function has an argument holding {@code x}. */
  private static boolean hasAngle(Expr e, Symbol x) {
    if (e instanceof Apply a
        && ANGLES.contains(a.head().name())
        && Substitution.occurs(a, x.name())) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (hasAngle(op, x)) {
        return true;
      }
    }
    return false;
  }
}
