package mathcanvas.engine.expr;

import java.math.MathContext;
import java.util.Map;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * A test-only exact evaluator of arithmetic expressions at a point, for the soundness properties.
 * Symbols, {@code π} and {@code e} take the values given (the canonical form never uses the
 * constants' values, so any values serve); {@code f} and {@code g} are fixed polynomials. The
 * result is null where the expression is undefined at the point, inexact, too large, or not
 * arithmetic.
 */
final class TestEvaluator {

  private static final MathContext EXACT = MathContext.UNLIMITED;
  private static final int MAX_BITS = 20_000;

  private final Map<Expr, Numeric> values;

  /**
   * An evaluator at one point.
   *
   * @param values the values of the symbols and constants
   */
  TestEvaluator(Map<Expr, Numeric> values) {
    this.values = values;
  }

  /**
   * Returns the exact value of {@code e}, or null.
   *
   * @param e the expression
   * @return its value, or null
   */
  Numeric eval(Expr e) {
    try {
      Numeric value = evaluate(e);
      return value != null && value.isExact() && !tooLarge(value) ? value : null;
    } catch (ArithmeticException | IllegalArgumentException undefined) {
      return null;
    }
  }

  private Numeric evaluate(Expr e) {
    if (e instanceof Num n) {
      return n.value();
    }
    if (e instanceof Symbol || e instanceof Constant) {
      return values.get(e);
    }
    if (e instanceof Sum s) {
      Numeric total = Int.ZERO;
      for (Expr t : s.terms()) {
        Numeric v = eval(t);
        if (v == null) {
          return null;
        }
        total = total.add(v, EXACT);
      }
      return total;
    }
    if (e instanceof Product p) {
      Numeric total = Int.ONE;
      for (Expr f : p.factors()) {
        Numeric v = eval(f);
        if (v == null) {
          return null;
        }
        total = total.multiply(v, EXACT);
      }
      return total;
    }
    if (e instanceof Power p) {
      Numeric base = eval(p.base());
      Numeric exponent = eval(p.exponent());
      if (base == null || exponent == null) {
        return null;
      }
      if (exponent instanceof Int k) {
        long bits = Math.max(bits(base.re()), bits(base.im()));
        if (k.value().bitLength() > 16 || bits * Math.abs(k.value().intValue()) > MAX_BITS) {
          return null;
        }
      }
      if (exponent.re() instanceof Rational q && q.den().bitLength() > 6) {
        return null;
      }
      return base.pow(exponent, EXACT);
    }
    if (e instanceof Apply a) {
      Numeric total = a.head().name().equals("f") ? Int.ONE : Int.of(-3);
      for (Expr arg : a.args()) {
        Numeric v = eval(arg);
        if (v == null) {
          return null;
        }
        total =
            a.head().name().equals("f")
                ? total.add(v.multiply(v, EXACT), EXACT)
                : total.multiply(v.subtract(Int.TWO, EXACT), EXACT);
      }
      return total;
    }
    return null;
  }

  private static boolean tooLarge(Numeric value) {
    return Math.max(bits(value.re()), bits(value.im())) > MAX_BITS;
  }

  private static long bits(RealNumeric x) {
    return switch (x) {
      case Int i -> i.value().bitLength();
      case Rational q -> Math.max(q.num().bitLength(), q.den().bitLength());
      case Real _ -> 0;
    };
  }
}
