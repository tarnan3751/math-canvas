package mathcanvas.engine.print;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
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
import mathcanvas.engine.number.RealNumeric;

/**
 * The display shape of a canonical expression: the raw tree whose faithful printing is the
 * conventional notation ({@code docs/language.md} §15). It changes how a value is written, never
 * what it is: the canonical form of the result is the input.
 *
 * <ul>
 *   <li>A sum's terms are in descending total degree, the sum of the numeric exponents of the
 *       symbols in a term (a symbolic exponent, a constant, a number or an application counts 0),
 *       ties in canonical order, and the numeric terms (the split parts of a complex number
 *       included) last: {@code x³ + 3x² + 3x + 1}, {@code x² cos(x) + 2x sin(x)}, {@code 2πk +
 *       π/6}, {@code -2 sin(x) + 3}. The first term carries its sign in its coefficient ({@code
 *       -2x² + 3x - 1}); a later term with a negative coefficient becomes a subtraction of the
 *       negated coefficient, a coefficient being negative when it is a negative real, a negative
 *       imaginary or a complex number with a negative real part ({@code a - 2i x}, {@code a - (1 -
 *       2i) x}). A complex number in a sum is split into its real and imaginary terms. A sum that
 *       is a factor of a product with a numeric coefficient, whose first term reads with a minus,
 *       is led by its first positive term of the same degree instead ({@code -(√3 - √2)}, {@code
 *       2(y - x)}); in a sum of rationals and numeric radicals a positive rational term leads
 *       ({@code (3 - √5)/2}, {@code (1 + √17)/4}).
 *   <li>A product's commuting factors become a quotient: a rational coefficient's denominator and
 *       every power with a negative numeric exponent go below the line ({@code x/(2π)}, {@code
 *       x²/2}, {@code 1/√x}; a number's power only with an integer exponent, so {@code 2^(-1/2)}
 *       stays), and a negative coefficient signs the quotient ({@code -1/x}, {@code -x/2}). Factors
 *       are displayed in groups, each in canonical order: the coefficient; constants and symbols
 *       with their powers (a power of a constant only with a numeric exponent: {@code π²} joins,
 *       {@code e^x} does not); sums and their powers; everything else ({@code 2πr}, {@code 2x
 *       sin(x)}, {@code (x - 1) e^x}). The printers put a space between a number and a call ({@code
 *       2 sin(x)}).
 *   <li>A quantity, a product with units, is displayed as its magnitude times its unit part, each
 *       displayed as a product of its own ({@code 25/18 _m/_s}, not {@code 25 _m/(18 _s)}).
 *   <li>Factors built from vectors and matrices never go below the line: they follow the commuting
 *       numerator in their order, keep their negative powers ({@code A·B^(-1)}), and the
 *       denominator divides the whole product ({@code M/2}, {@code 2M/3}).
 *   <li>A set of constants is in ascending order of value, the non-real members after the real ones
 *       by real and then imaginary part, ties in canonical order: {@code {-√2, √2}}, {@code {-i,
 *       i}}; any other set keeps its canonical order ({@link ConstantOrder}).
 * </ul>
 */
public final class Display {

  private static final Num I = Num.of(Complex.I);

  private final Cancellation cancellation;

  private Display(Cancellation cancellation) {
    this.cancellation = cancellation;
  }

  /**
   * Returns the display shape of a canonical expression.
   *
   * @param e an expression in canonical form
   * @return a raw tree to print
   */
  public static Expr of(Expr e) {
    return of(e, Cancellation.NEVER);
  }

  /**
   * Returns the display shape of a canonical expression, checking {@code cancellation} while the
   * members of a set of constants are valued ({@link ConstantOrder}).
   *
   * @param e an expression in canonical form
   * @param cancellation the request to stop
   * @return a raw tree to print
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Expr of(Expr e, Cancellation cancellation) {
    return new Display(cancellation).shape(e);
  }

  private Expr shape(Expr e) {
    return switch (e) {
      case Num n -> n;
      case mathcanvas.engine.expr.Based b -> b;
      case Constant c -> c;
      case Bool b -> b;
      case Symbol s -> s;
      case Unit u -> u;
      case Sum s -> sum(s, false);
      case Product p -> product(p.factors());
      case Power p -> power(p);
      case Apply a -> Apply.of(a.head(), map(a.args()));
      case Lambda l -> Lambda.of(l.params(), shape(l.body()));
      case Relation r -> Relation.of(r.op(), shape(r.left()), shape(r.right()));
      case ListExpr l -> ListExpr.of(map(l.items()));
      case Vector v -> Vector.of(map(v.items()));
      case SetExpr s -> SetExpr.of(map(ConstantOrder.of(s.items(), cancellation)));
      case Matrix m -> {
        List<List<Expr>> rows = new ArrayList<>();
        for (List<Expr> row : m.rows()) {
          rows.add(map(row));
        }
        yield Matrix.of(rows);
      }
      case Interval i ->
          Interval.of(shape(i.lower()), shape(i.upper()), i.lowerClosed(), i.upperClosed());
      case Piecewise p -> {
        List<Piecewise.Piece> pieces = new ArrayList<>();
        for (Piecewise.Piece q : p.pieces()) {
          pieces.add(new Piecewise.Piece(shape(q.value()), shape(q.condition())));
        }
        yield Piecewise.of(pieces, shape(p.otherwise()));
      }
      case Derivative d -> Derivative.of(shape(d.expr()), d.var(), d.order());
      case Integral i ->
          i.isDefinite()
              ? Integral.definite(shape(i.integrand()), i.var(), shape(i.lower()), shape(i.upper()))
              : Integral.indefinite(shape(i.integrand()), i.var());
      case Limit l -> Limit.of(shape(l.expr()), l.var(), shape(l.point()), l.direction());
      case BigSum s -> BigSum.of(shape(s.body()), s.var(), shape(s.from()), shape(s.to()));
      case BigProduct s -> BigProduct.of(shape(s.body()), s.var(), shape(s.from()), shape(s.to()));
    };
  }

  private List<Expr> map(List<Expr> items) {
    List<Expr> out = new ArrayList<>(items.size());
    for (Expr item : items) {
      out.add(shape(item));
    }
    return out;
  }

  /**
   * A sum in display order. Under a numeric coefficient ({@code underCoefficient}) the order also
   * avoids a leading minus where it can: in a sum of rationals and rational multiples of numeric
   * radicals a positive rational term leads ({@code (3 - √5)/2}, {@code (1 + √17)/4}); otherwise a
   * leading negative term gives way to the first positive term of its degree ({@code (√17 - 1)/4},
   * {@code -(√3 - √2)}, {@code -(y - x)}). Only the order changes, so the text reads back as the
   * same value.
   */
  private Expr sum(Sum s, boolean underCoefficient) {
    List<Expr> terms = new ArrayList<>();
    List<Expr> numbers = new ArrayList<>();
    for (Expr t : s.terms()) {
      if (t instanceof Num n && n.value() instanceof Complex c) {
        if (!dropsRealPart(c)) {
          numbers.add(Num.of(c.re()));
        }
        numbers.add(Product.of(Num.of(c.im()), I));
      } else if (t instanceof Num) {
        numbers.add(t);
      } else {
        terms.add(t);
      }
    }
    terms.sort((a, b) -> degree(b).compareValue(degree(a)));
    terms.addAll(numbers);
    if (underCoefficient) {
      leadPositive(terms);
    }
    List<Expr> out = new ArrayList<>();
    for (Expr t : terms) {
      out.add(out.isEmpty() ? shape(t) : term(t));
    }
    return Sum.of(out);
  }

  /**
   * Moves a positive term to the front of a sum under a coefficient (see {@link #sum}): a positive
   * rational in a sum of rationals and numeric radicals, else the first positive term of the
   * leading term's degree when the leading term reads as negative.
   */
  private void leadPositive(List<Expr> terms) {
    if (terms.stream().allMatch(Display::numericRadicalTerm)) {
      for (int k = 0; k < terms.size(); k++) {
        if (terms.get(k) instanceof Num n
            && n.value() instanceof RealNumeric r
            && r.isExact()
            && r.signum() > 0) {
          terms.addFirst(terms.remove(k));
          return;
        }
      }
    }
    if (!readsNegative(terms.getFirst())) {
      return;
    }
    RealNumeric lead = degree(terms.getFirst());
    for (int k = 1; k < terms.size(); k++) {
      Expr t = terms.get(k);
      if (!readsNegative(t) && degree(t).compareValue(lead) == 0) {
        terms.addFirst(terms.remove(k));
        return;
      }
    }
  }

  /** Whether a term reads with a minus: a negative number, or a negative coefficient leads it. */
  private boolean readsNegative(Expr t) {
    if (t instanceof Num n) {
      return negative(n.value());
    }
    return t instanceof Product p && p.factors().getFirst() instanceof Num n && negative(n.value());
  }

  /**
   * Whether a term is an exact rational, a power of a positive integer to a non-integer rational
   * exponent (a numeric radical), or a rational multiple of one.
   */
  private static boolean numericRadicalTerm(Expr t) {
    if (t instanceof Num n) {
      return n.value() instanceof RealNumeric r && r.isExact();
    }
    if (t instanceof Product p
        && p.factors().size() == 2
        && p.factors().getFirst() instanceof Num n
        && n.value() instanceof RealNumeric r
        && r.isExact()) {
      return numericRadical(p.factors().get(1));
    }
    return numericRadical(t);
  }

  private static boolean numericRadical(Expr t) {
    return t instanceof Power w
        && w.base() instanceof Num b
        && b.value() instanceof Int i
        && i.signum() > 0
        && w.exponent() instanceof Num e
        && e.value() instanceof Rational;
  }

  /**
   * Whether a complex number's real part is left out: when it is zero ({@code 2i}, {@code 2.0i}).
   */
  static boolean dropsRealPart(Complex c) {
    return c.re().isZero();
  }

  /** The total degree of a term: the sum of the numeric exponents of its symbols. */
  private RealNumeric degree(Expr t) {
    if (t instanceof Product p) {
      Numeric sum = Int.ZERO;
      for (Expr f : p.factors()) {
        sum = sum.add(degree(f), MathContext.DECIMAL128);
      }
      return (RealNumeric) sum;
    }
    if (t instanceof Symbol) {
      return Int.ONE;
    }
    if (t instanceof Power p
        && p.base() instanceof Symbol
        && p.exponent() instanceof Num n
        && n.value() instanceof RealNumeric r) {
      return r;
    }
    return Int.ZERO;
  }

  /** A term after the first of a sum; a negative coefficient becomes a subtraction. */
  private Expr term(Expr t) {
    if (t instanceof Num n && n.value() instanceof RealNumeric r && r.signum() < 0) {
      return Product.of(Num.MINUS_ONE, Num.of(r.negate()));
    }
    if (t instanceof Product p && p.factors().getFirst() instanceof Num n && negative(n.value())) {
      List<Expr> factors = new ArrayList<>(p.factors());
      Numeric positive = n.value().negate();
      if (positive.isOne() && positive.isExact()) {
        factors.removeFirst();
      } else {
        factors.set(0, Num.of(positive));
      }
      Expr rest = factors.size() == 1 ? factors.getFirst() : Product.of(factors);
      return Product.of(Num.MINUS_ONE, shape(rest));
    }
    return shape(t);
  }

  /**
   * Whether a coefficient reads as negative, so that a term it leads is a subtraction: a negative
   * real, a negative imaginary ({@code -i}, {@code -2i}), or a complex number with a negative real
   * part ({@code -1 + 2i}).
   */
  private boolean negative(Numeric c) {
    return switch (c) {
      case RealNumeric r -> r.signum() < 0;
      case Complex z -> z.re().isZero() ? z.im().signum() < 0 : z.re().signum() < 0;
    };
  }

  private Expr product(List<Expr> factors) {
    // a quantity: its magnitude, then its units, each displayed alone (§14.26): 25/18 _m/_s
    List<Expr> magnitude = new ArrayList<>();
    List<Expr> units = new ArrayList<>();
    for (Expr f : factors) {
      (f instanceof Unit || (f instanceof Power p && p.base() instanceof Unit) ? units : magnitude)
          .add(f);
    }
    if (!units.isEmpty() && !magnitude.isEmpty()) {
      // a magnitude of -1 is the sign of the units: -_m, -_m/_s
      return magnitude.equals(List.of(Num.MINUS_ONE))
          ? Product.of(Num.MINUS_ONE, quotient(units))
          : Product.of(quotient(magnitude), quotient(units));
    }
    return quotient(factors);
  }

  private Expr quotient(List<Expr> factors) {
    Numeric coefficient = null;
    List<Expr> rest = factors;
    if (factors.getFirst() instanceof Num n) {
      coefficient = n.value();
      rest = factors.subList(1, factors.size());
    }
    List<Expr> numerator = new ArrayList<>();
    List<Expr> denominator = new ArrayList<>();
    List<Expr> fixed = new ArrayList<>();
    for (Expr f : rest) {
      if (!Canonical.commutes(f)) {
        fixed.add(shape(f));
      } else if (f instanceof Power p && belowTheLine(p)) {
        RealNumeric r = (RealNumeric) ((Num) p.exponent()).value();
        RealNumeric positive = r.negate();
        denominator.add(
            shape(
                positive.isOne() && positive.isExact()
                    ? p.base()
                    : Power.of(p.base(), Num.of(positive))));
      } else if (f instanceof Sum sum && coefficient != null) {
        numerator.add(sum(sum, true));
      } else {
        numerator.add(shape(f));
      }
    }
    List<Expr> top = new ArrayList<>();
    List<Expr> bottom = new ArrayList<>();
    boolean negative = false;
    if (coefficient instanceof Complex c && dropsRealPart(c)) {
      coefficient = c.im();
      top.add(I);
    }
    if (coefficient instanceof RealNumeric r) {
      if (r.signum() < 0) {
        negative = true;
        r = r.negate();
      }
      if (r instanceof Rational q) {
        bottom.add(Num.of(q.denominator()));
        r = q.numerator();
      }
      if (!(r.isOne() && r.isExact())) {
        top.addFirst(Num.of(r));
      }
    } else if (coefficient != null) {
      top.add(Num.of(coefficient));
    }
    top.addAll(grouped(numerator));
    bottom.addAll(grouped(denominator));
    top.addAll(fixed);
    if (top.isEmpty()) {
      top.add(Num.ONE);
    }
    if (negative) {
      Expr first = top.getFirst();
      // -1 alone before a quotient is written -1, not as the sign of its divisor: -1/x.
      top.set(
          0,
          first.equals(Num.ONE)
              ? Exprs.neg(Num.ONE)
              : first instanceof Num n && n.value() instanceof RealNumeric r
                  ? Num.of(r.negate())
                  : Product.of(Num.MINUS_ONE, first));
    }
    List<Expr> out = new ArrayList<>(top);
    if (!bottom.isEmpty()) {
      out.add(Power.of(bottom.size() == 1 ? bottom.getFirst() : Product.of(bottom), Num.MINUS_ONE));
    }
    return out.size() == 1 ? out.getFirst() : Product.of(out);
  }

  /**
   * Constants and symbols with their powers, then sums and their powers, then the rest; each in
   * order. A power of a constant joins the first group only with a numeric exponent, so {@code e^x}
   * stays with the rest: {@code (x - 1) e^x}.
   */
  private List<Expr> grouped(List<Expr> factors) {
    List<Expr> out = new ArrayList<>();
    for (int group = 0; group < 3; group++) {
      for (Expr f : factors) {
        if (group(f) == group) {
          out.add(f);
        }
      }
    }
    return out;
  }

  private int group(Expr f) {
    if (f instanceof Symbol || f instanceof Constant) {
      return 0;
    }
    if (f instanceof Power p) {
      if (p.base() instanceof Symbol
          || (p.base() instanceof Constant && p.exponent() instanceof Num)) {
        return 0;
      }
      if (p.base() instanceof Sum) {
        return 1;
      }
      return 2;
    }
    return f instanceof Sum ? 1 : 2;
  }

  /**
   * Whether a commuting power goes below the line: its exponent is a negative number, and an
   * integer unless the base is not a number. {@code 2^(-1/2)} stays a power, since {@code 1/√2}
   * reads back as {@code (2^(1/2))^(-1)}, whose inner root of a number may fold first.
   */
  private boolean belowTheLine(Power p) {
    return p.exponent() instanceof Num n
        && n.value() instanceof RealNumeric r
        && r.signum() < 0
        && (r.isInteger() || !(p.base() instanceof Num));
  }

  private Expr power(Power p) {
    if (p.exponent() instanceof Num n && n.value() instanceof RealNumeric r && r.signum() < 0) {
      if (Canonical.commutes(p) && belowTheLine(p)) {
        return product(List.of(p));
      }
      if (n.equals(Num.MINUS_ONE)) {
        // A matrix's inverse stays a power, written B^(-1), never a quotient.
        return Power.of(shape(p.base()), Exprs.neg(Num.ONE));
      }
    }
    return Power.of(shape(p.base()), shape(p.exponent()));
  }
}
