package mathcanvas.engine.eval;

import java.math.MathContext;
import java.math.RoundingMode;
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
  static final List<String> STEPS = List.of("quadrature", "none", "angle-mode", "digits");

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
   */
  record Outcome(Expr value, Trace step) {}

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
        yield new Outcome(value, traced ? step(e, value, "quadrature", digitsBound) : null);
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
