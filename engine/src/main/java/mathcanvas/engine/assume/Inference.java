package mathcanvas.engine.assume;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import mathcanvas.engine.assume.Facts.Bound;
import mathcanvas.engine.assume.Facts.Domain;
import mathcanvas.engine.assume.Facts.Parity;
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
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;

/**
 * The structural inference behind the queries: what is known of an expression, from its numbers,
 * constants, the facts about its symbols and the closure rules of sums, products, powers and a few
 * functions. A query, not a rewrite.
 *
 * @param real whether the value is real
 * @param rational whether it is rational
 * @param integer whether it is an integer
 * @param zero whether it is zero
 * @param signs the signs a real value may have ({@link #NEG}, {@link #ZERO}, {@link #POS}); every
 *     sign when the value may not be real
 * @param parity the parity of an integer value
 */
record Inference(Tri real, Tri rational, Tri integer, Tri zero, int signs, Parity parity) {

  /** A negative value. */
  static final int NEG = 1;

  /** Zero. */
  static final int ZERO = 2;

  /** A positive value. */
  static final int POS = 4;

  private static final int ALL = NEG | ZERO | POS;

  private static final Inference UNKNOWN =
      new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, ALL, Parity.NONE);

  /** Normalises the implications between the fields. */
  Inference {
    if (parity != Parity.NONE) {
      integer = Tri.TRUE;
      if (parity == Parity.ODD) {
        zero = Tri.FALSE;
      }
    }
    if (integer == Tri.TRUE) {
      rational = Tri.TRUE;
    }
    if (rational == Tri.TRUE) {
      real = Tri.TRUE;
    }
    if (real == Tri.FALSE) {
      rational = Tri.FALSE;
    }
    if (rational == Tri.FALSE) {
      integer = Tri.FALSE;
    }
    if (real == Tri.TRUE) {
      if (zero == Tri.FALSE) {
        signs &= ~ZERO;
      } else if (zero == Tri.TRUE) {
        signs &= ZERO;
      }
      // No sign at all: the value is undefined wherever the facts hold, so nothing is known.
      zero =
          signs == 0
              ? Tri.UNKNOWN
              : signs == ZERO ? Tri.TRUE : (signs & ZERO) == 0 ? Tri.FALSE : Tri.UNKNOWN;
    } else {
      signs = ALL;
    }
  }

  /**
   * What is known of {@code e} under {@code facts}.
   *
   * @param e the expression
   * @param facts the assumptions
   * @return what is known
   */
  static Inference of(Expr e, Assumptions facts) {
    return switch (e) {
      case Num n -> number(n.value());
      case mathcanvas.engine.expr.Based b -> number(b.value());
      case Constant c ->
          switch (c.kind()) {
            case PI, E ->
                new Inference(Tri.TRUE, Tri.FALSE, Tri.FALSE, Tri.FALSE, POS, Parity.NONE);
            case INFINITY ->
                new Inference(Tri.FALSE, Tri.FALSE, Tri.FALSE, Tri.FALSE, ALL, Parity.NONE);
            case UNDEFINED, INTEGERS, RATIONALS, REALS, COMPLEXES -> UNKNOWN;
          };
      case Symbol s -> symbol(facts.facts(s));
      // a unit is a positive real constant (§14.26)
      case Unit _ -> new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, Tri.FALSE, POS, Parity.NONE);
      case Sum s -> bounded(sum(s.terms().stream().map(t -> of(t, facts)).toList()), s, facts);
      case Product p -> product(p.factors().stream().map(f -> of(f, facts)).toList());
      case Power p -> power(of(p.base(), facts), p.exponent(), of(p.exponent(), facts));
      case Apply a -> apply(a, facts);
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
          BigProduct _ ->
          UNKNOWN;
    };
  }

  /**
   * Whether the value is real with its sign among {@code allowed}.
   *
   * @param allowed the allowed signs
   * @return the answer
   */
  Tri signIn(int allowed) {
    if (real == Tri.FALSE) {
      return Tri.FALSE;
    }
    if (real != Tri.TRUE || signs == 0) {
      return Tri.UNKNOWN;
    }
    if ((signs & ~allowed) == 0) {
      return Tri.TRUE;
    }
    return (signs & allowed) == 0 ? Tri.FALSE : Tri.UNKNOWN;
  }

  /**
   * Whether the value is an integer of parity {@code p}.
   *
   * @param p the parity
   * @return the answer
   */
  Tri parityIs(Parity p) {
    if (parity != Parity.NONE) {
      return Tri.of(parity == p);
    }
    return integer == Tri.FALSE ? Tri.FALSE : Tri.UNKNOWN;
  }

  // The kinds.

  private static Inference number(Numeric value) {
    if (value instanceof Complex c) {
      if (!c.im().isZero()) {
        return new Inference(Tri.FALSE, Tri.FALSE, Tri.FALSE, Tri.FALSE, ALL, Parity.NONE);
      }
      return number(c.re());
    }
    RealNumeric r = (RealNumeric) value;
    int sign = r.signum() < 0 ? NEG : r.signum() > 0 ? POS : ZERO;
    Parity parity = Parity.NONE;
    if (r.isInteger()) {
      parity = integerValue(r).testBit(0) ? Parity.ODD : Parity.EVEN;
    }
    // A Real is taken by its value: a decimal is rational, and integral when its value is.
    return new Inference(
        Tri.TRUE, Tri.TRUE, Tri.of(r.isInteger()), Tri.of(r.isZero()), sign, parity);
  }

  private static BigInteger integerValue(RealNumeric r) {
    return switch (r) {
      case Int i -> i.value();
      case Rational q -> q.num();
      case Real d -> d.value().toBigIntegerExact();
    };
  }

  private static Inference symbol(Facts f) {
    Tri zero = f.nonZero() ? Tri.FALSE : Tri.UNKNOWN;
    if (f.domain() == Domain.COMPLEXES) {
      return new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, zero, ALL, Parity.NONE);
    }
    int signs = ALL & lowerSigns(f.lower()) & upperSigns(f.upper());
    return new Inference(
        Tri.TRUE,
        f.domain().compareTo(Domain.RATIONALS) <= 0 ? Tri.TRUE : Tri.UNKNOWN,
        f.domain() == Domain.INTEGERS ? Tri.TRUE : Tri.UNKNOWN,
        zero,
        signs,
        f.parity());
  }

  /** The signs a value above the bound may have. */
  private static int lowerSigns(Bound b) {
    if (b == null) {
      return ALL;
    }
    int s = b.value().signum();
    if (s > 0 || (s == 0 && b.strict())) {
      return POS;
    }
    return s == 0 ? ZERO | POS : ALL;
  }

  /** The signs a value below the bound may have. */
  private static int upperSigns(Bound b) {
    if (b == null) {
      return ALL;
    }
    int s = b.value().signum();
    if (s < 0 || (s == 0 && b.strict())) {
      return NEG;
    }
    return s == 0 ? NEG | ZERO : ALL;
  }

  private static Inference sum(List<Inference> terms) {
    // A sum with one term lacking a property, the others having it, lacks it too.
    Tri real = closure(terms, Inference::real, t -> true);
    Tri rational = closure(terms, Inference::rational, t -> true);
    Tri integer = closure(terms, Inference::integer, t -> true);
    int signs = ALL;
    if (real == Tri.TRUE) {
      boolean neg = false;
      boolean pos = false;
      boolean allZero = true;
      for (Inference t : terms) {
        neg |= (t.signs & NEG) != 0;
        pos |= (t.signs & POS) != 0;
        allZero &= (t.signs & ZERO) != 0;
      }
      signs = (neg ? NEG : 0) | (pos ? POS : 0) | (allZero || (neg && pos) ? ZERO : 0);
      // A term undefined wherever the facts hold leaves the sum undefined there too.
      if (terms.stream().anyMatch(t -> t.signs == 0)) {
        signs = 0;
      }
    }
    Parity parity = Parity.NONE;
    if (terms.stream().allMatch(t -> t.parity != Parity.NONE)) {
      long odd = terms.stream().filter(t -> t.parity == Parity.ODD).count();
      parity = odd % 2 == 0 ? Parity.EVEN : Parity.ODD;
    }
    return new Inference(real, rational, integer, Tri.UNKNOWN, signs, parity);
  }

  /**
   * A real sum whose terms' signs leave both signs open, narrowed by the range its symbols' bounds
   * give ({@link Range}): {@code 1 − y²} is non-negative for {@code −1 ≤ y ≤ 1}.
   */
  private static Inference bounded(Inference sum, Sum s, Assumptions facts) {
    // with no facts no symbol is bounded, and the signs say all a range would
    if (sum.real != Tri.TRUE
        || (sum.signs & NEG) == 0
        || (sum.signs & POS) == 0
        || facts.bySymbol().isEmpty()) {
      return sum;
    }
    Range r = Range.of(s, facts).orElse(null);
    if (r == null) {
      return sum;
    }
    return new Inference(
        sum.real, sum.rational, sum.integer, sum.zero, sum.signs & r.signs(), sum.parity);
  }

  private static Inference product(List<Inference> factors) {
    Tri real = closure(factors, Inference::real, t -> t.zero == Tri.FALSE);
    Tri rational =
        closure(factors, Inference::rational, t -> t.zero == Tri.FALSE && t.real == Tri.TRUE);
    Tri integer = factors.stream().allMatch(t -> t.integer == Tri.TRUE) ? Tri.TRUE : Tri.UNKNOWN;
    Tri zero = Tri.FALSE;
    for (Inference t : factors) {
      zero = zero.or(t.zero);
    }
    int signs = POS;
    for (Inference t : factors) {
      signs = multiply(signs, t.signs);
    }
    Parity parity = Parity.NONE;
    if (integer != Tri.TRUE) {
      parity = Parity.NONE;
    } else if (factors.stream().anyMatch(t -> t.parity == Parity.EVEN)) {
      parity = Parity.EVEN;
    } else if (factors.stream().allMatch(t -> t.parity == Parity.ODD)) {
      parity = Parity.ODD;
    }
    return new Inference(real, rational, integer, zero, signs, parity);
  }

  private static int multiply(int a, int b) {
    if (a == 0 || b == 0) {
      // A factor undefined wherever the facts hold leaves the product undefined there too.
      return 0;
    }
    int out = 0;
    if ((a & ZERO) != 0 || (b & ZERO) != 0) {
      out |= ZERO;
    }
    if (((a & POS) != 0 && (b & POS) != 0) || ((a & NEG) != 0 && (b & NEG) != 0)) {
      out |= POS;
    }
    if (((a & POS) != 0 && (b & NEG) != 0) || ((a & NEG) != 0 && (b & POS) != 0)) {
      out |= NEG;
    }
    return out;
  }

  /**
   * A closure property of a sum or product: true when every operand has it; false when exactly one
   * operand lacks it and every other operand satisfies {@code others} (a real plus a non-real is
   * not real; a non-zero real times a non-real is not real); unknown otherwise.
   */
  private static Tri closure(
      List<Inference> operands, Function<Inference, Tri> property, Predicate<Inference> others) {
    int lacking = -1;
    for (int i = 0; i < operands.size(); i++) {
      Tri t = property.apply(operands.get(i));
      if (t == Tri.UNKNOWN) {
        return Tri.UNKNOWN;
      }
      if (t == Tri.FALSE) {
        if (lacking >= 0) {
          return Tri.UNKNOWN;
        }
        lacking = i;
      }
    }
    if (lacking < 0) {
      return Tri.TRUE;
    }
    for (int i = 0; i < operands.size(); i++) {
      if (i != lacking && !others.test(operands.get(i))) {
        return Tri.UNKNOWN;
      }
    }
    return Tri.FALSE;
  }

  private static Inference power(Inference base, Expr exponent, Inference exp) {
    Tri zero = base.zero == Tri.FALSE ? Tri.FALSE : Tri.UNKNOWN;
    if (exponent instanceof Num n && n.value() instanceof Int i) {
      BigInteger k = i.value();
      boolean positive = k.signum() > 0;
      if (base.zero == Tri.TRUE && positive) {
        zero = Tri.TRUE;
      }
      if (base.real != Tri.TRUE) {
        return new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, zero, ALL, Parity.NONE);
      }
      // Zero to a negative power is undefined; the value is taken where it is defined.
      int baseSigns = positive ? base.signs : base.signs & ~ZERO;
      int signs;
      if (k.testBit(0)) {
        signs = baseSigns;
      } else {
        signs = (baseSigns & ZERO) | ((baseSigns & (NEG | POS)) != 0 ? POS : 0);
      }
      Tri integer = base.integer == Tri.TRUE && k.signum() >= 0 ? Tri.TRUE : Tri.UNKNOWN;
      Tri rational = base.rational == Tri.TRUE ? Tri.TRUE : Tri.UNKNOWN;
      Parity parity = positive && base.integer == Tri.TRUE ? base.parity : Parity.NONE;
      return new Inference(Tri.TRUE, rational, integer, zero, signs, parity);
    }
    if (base.real == Tri.TRUE && exp.real == Tri.TRUE && (base.signs & NEG) == 0) {
      // A non-negative base to a real power: positive when the base is; 0^0 is 1.
      int signs = base.signs;
      if ((signs & ZERO) != 0 && exp.zero != Tri.FALSE) {
        signs |= POS;
      }
      return new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, zero, signs, Parity.NONE);
    }
    return new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, zero, ALL, Parity.NONE);
  }

  private static Inference apply(Apply a, Assumptions facts) {
    // abs, exp and sqrt of an argument that may be infinite are no finite value: abs(∞) is ∞
    // and exp(-∞) is 0. The circular functions, sinh, tanh and atan of a real argument are real
    // where they are defined, and cosh of one is positive; log of a positive one is real.
    if (a.args().size() != 1 || !finite(a.args().getFirst())) {
      return UNKNOWN;
    }
    Inference arg = of(a.args().get(0), facts);
    return switch (a.head().name()) {
      case "abs" ->
          new Inference(
              Tri.TRUE,
              arg.rational == Tri.TRUE ? Tri.TRUE : Tri.UNKNOWN,
              arg.integer == Tri.TRUE ? Tri.TRUE : Tri.UNKNOWN,
              arg.zero,
              arg.real == Tri.TRUE && arg.signs == 0 ? 0 : ZERO | POS,
              arg.parity);
      case "exp" ->
          arg.real == Tri.TRUE
              ? new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, Tri.FALSE, POS, Parity.NONE)
              : new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, Tri.FALSE, ALL, Parity.NONE);
      case "sqrt" ->
          arg.real == Tri.TRUE && (arg.signs & NEG) == 0
              ? new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, arg.zero, arg.signs, Parity.NONE)
              : new Inference(Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, arg.zero, ALL, Parity.NONE);
      case "sin", "cos", "tan", "cot", "sec", "csc", "sinh", "tanh", "atan" ->
          arg.real == Tri.TRUE
              ? new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, ALL, Parity.NONE)
              : UNKNOWN;
      case "log" -> {
        // the logarithm of a positive value is real, its sign the side of 1 the value is on
        if (arg.real != Tri.TRUE || arg.signs != POS) {
          yield UNKNOWN;
        }
        int signs =
            Range.of(Sum.of(a.args().getFirst(), Num.MINUS_ONE), facts)
                .map(Range::signs)
                .orElse(ALL);
        yield new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, Tri.UNKNOWN, signs, Parity.NONE);
      }
      case "cosh" ->
          arg.real == Tri.TRUE
              ? new Inference(Tri.TRUE, Tri.UNKNOWN, Tri.UNKNOWN, Tri.FALSE, POS, Parity.NONE)
              : UNKNOWN;
      default -> UNKNOWN;
    };
  }

  /**
   * Whether {@code e} is arithmetic over numbers, symbols, {@code π} and {@code e}, so finite where
   * it is defined: no infinity, no undefined value, no other kind.
   */
  private static boolean finite(Expr e) {
    return switch (e) {
      case Num _, mathcanvas.engine.expr.Based _, Symbol _, Unit _ -> true;
      case Constant c -> c.kind() == Constant.Kind.PI || c.kind() == Constant.Kind.E;
      case Sum s -> s.terms().stream().allMatch(Inference::finite);
      case Product p -> p.factors().stream().allMatch(Inference::finite);
      case Power p -> finite(p.base()) && finite(p.exponent());
      case Apply a -> a.args().stream().allMatch(Inference::finite);
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
          BigProduct _ ->
          false;
    };
  }
}
