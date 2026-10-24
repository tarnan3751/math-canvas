package mathcanvas.engine.eval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
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
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The passes of {@code numeric} and {@code exact} over an evaluated value ({@code docs/language.md}
 * §14.1).
 *
 * <p>{@link #numeric} makes every exact number inexact at the precision, replaces {@code π} and
 * {@code e} by their values, folds the arithmetic and evaluates every builtin function over numbers
 * through the inexact path, in radians. Symbols, {@code ∞}, {@code undefined}, truth values and
 * lambda bodies stay, and so do integer exponents ({@code x^2} is not {@code x^2.0}) and the
 * variables, bounds, points and orders of the unevaluated forms, whose bodies are made numeric.
 * {@link #exact} turns every decimal into the rational it denotes.
 */
final class NumericForm {

  /** The guard digits the numeric pass works with beyond the precision asked for. */
  static final int GUARD = 10;

  /** The largest decimal exponent, either way, that {@code exact} expands. */
  static final int MAX_EXACT_SCALE = 100_000;

  private NumericForm() {}

  /**
   * The numeric form of an evaluated value at {@code digits} digits: the pass runs with {@link
   * #GUARD} more digits, so that the rounding of each operation does not reach the result, and each
   * decimal of the result is then rounded to {@code digits}.
   *
   * @param e the value, canonical
   * @param digits the digits
   * @param node the raw call, for errors
   * @return the canonical numeric form
   * @throws EvaluationException if a function's value is out of range at this precision
   */
  static Expr numeric(Expr e, int digits, Expr node) {
    MathContext mc = new MathContext(digits, RoundingMode.HALF_EVEN);
    Expr guarded = numeric(e, new MathContext(digits + GUARD, RoundingMode.HALF_EVEN), node);
    return Canonical.of(round(guarded, mc), mc);
  }

  /** Every decimal of a tree rounded to {@code mc}, raw. */
  private static Expr round(Expr e, MathContext mc) {
    if (e instanceof Num n) {
      return n.value().isExact() ? n : new Num(n.value().toReal(mc));
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(round(op, mc));
    }
    return Operands.rebuild(e, out);
  }

  /**
   * The numeric form of an evaluated value, each operation rounded to {@code mc}.
   *
   * @param e the value, canonical
   * @param mc the precision
   * @param node the raw call, for errors
   * @return the canonical numeric form
   * @throws EvaluationException if a function's value is out of range at this precision
   */
  static Expr numeric(Expr e, MathContext mc, Expr node) {
    return switch (e) {
      case Num n -> new Num(n.value().toReal(mc));
      case Constant c ->
          switch (c.kind()) {
            case PI -> new Num(Elementary.pi(mc));
            case E -> new Num(Elementary.e(mc));
            case INFINITY, UNDEFINED, INTEGERS, RATIONALS, REALS, COMPLEXES -> c;
          };
      case Symbol _, Bool _, Lambda _ -> e;
      case Apply a -> apply(a, mc, node);
      case Power p ->
          Canonical.of(
              new Power(
                  numeric(p.base(), mc, node),
                  isInteger(p.exponent()) ? p.exponent() : numeric(p.exponent(), mc, node)),
              mc);
      case Derivative d -> new Derivative(numeric(d.expr(), mc, node), d.var(), d.order());
      case Integral i ->
          i.isDefinite()
              ? Integral.definite(numeric(i.integrand(), mc, node), i.var(), i.lower(), i.upper())
              : Integral.indefinite(numeric(i.integrand(), mc, node), i.var());
      case Limit l -> new Limit(numeric(l.expr(), mc, node), l.var(), l.point(), l.direction());
      case BigSum s -> new BigSum(numeric(s.body(), mc, node), s.var(), s.from(), s.to());
      case BigProduct p -> new BigProduct(numeric(p.body(), mc, node), p.var(), p.from(), p.to());
      case Sum _,
          Product _,
          Relation _,
          ListExpr _,
          Vector _,
          SetExpr _,
          Matrix _,
          Interval _,
          Piecewise _ ->
          Canonical.of(Operands.rebuild(e, all(Operands.of(e), mc, node)), mc);
    };
  }

  private static boolean isInteger(Expr e) {
    return e instanceof Num n && n.value() instanceof Int;
  }

  private static List<Expr> all(List<Expr> items, MathContext mc, Expr node) {
    List<Expr> out = new ArrayList<>(items.size());
    for (Expr item : items) {
      out.add(numeric(item, mc, node));
    }
    return out;
  }

  /**
   * A builtin function over numbers is evaluated; a command's held arguments, and a call that is
   * not a builtin's, stay.
   */
  private static Expr apply(Apply a, MathContext mc, Expr node) {
    Builtin b = Builtins.lookup(a.head().name()).orElse(null);
    if (b == null) {
      return a;
    }
    List<Expr> args = new ArrayList<>(a.args().size());
    for (int k = 0; k < a.args().size(); k++) {
      Expr arg = a.args().get(k);
      args.add(b.isHeld(k + 1) ? arg : numeric(arg, mc, node));
    }
    if (b.kind() == Builtin.Kind.FUNCTION && args.stream().allMatch(x -> x instanceof Num)) {
      return Functions.numeric(a.head(), args, mc, node);
    }
    return Canonical.of(new Apply(a.head(), args), mc);
  }

  /**
   * The exact form of a value: every decimal is the rational it denotes ({@code 0.1} is {@code
   * 1/10}), raw.
   *
   * @param e the value
   * @param node the raw call, for errors
   * @return the tree with exact numbers
   * @throws EvaluationException if a decimal's exponent is beyond {@link #MAX_EXACT_SCALE}
   */
  static Expr exact(Expr e, Expr node) {
    if (e instanceof Num n) {
      return n.value().isExact() ? n : new Num(exact(n.value(), node));
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(exact(op, node));
    }
    return Operands.rebuild(e, out);
  }

  private static Numeric exact(Numeric x, Expr node) {
    return switch (x) {
      case Real r -> exact(r, node);
      case Complex c -> Complex.of(exactPart(c.re(), node), exactPart(c.im(), node));
      case Int i -> i;
      case Rational q -> q;
    };
  }

  private static RealNumeric exactPart(RealNumeric x, Expr node) {
    return x instanceof Real r ? exact(r, node) : x;
  }

  /**
   * The exact value of a real number: a decimal's rational, any other number itself.
   *
   * @param x the number
   * @param node the raw call, for errors
   * @return the exact value
   * @throws EvaluationException if a decimal's exponent is beyond {@link #MAX_EXACT_SCALE}
   */
  static RealNumeric exactValue(RealNumeric x, Expr node) {
    return exactPart(x, node);
  }

  private static RealNumeric exact(Real r, Expr node) {
    BigDecimal v = r.value();
    int scale = v.scale();
    if (Math.abs((long) scale) > MAX_EXACT_SCALE) {
      throw new EvaluationException("number out of range at this precision", node);
    }
    BigInteger unscaled = v.unscaledValue();
    return scale >= 0
        ? Rational.of(unscaled, BigInteger.TEN.pow(scale))
        : Int.of(unscaled.multiply(BigInteger.TEN.pow(-scale)));
  }
}
