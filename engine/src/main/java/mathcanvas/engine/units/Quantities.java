package mathcanvas.engine.units;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.units.DimensionException.Kind;

/**
 * Quantities ({@code docs/language.md} §14.26): a term's magnitude and unit part, the dimension of
 * an expression, and the numbers units stand for.
 *
 * <p>A <b>unit factor</b> of a product is a unit or a power of one; the <b>unit part</b> of a term
 * is the product of its unit factors (1 when it has none) and its <b>magnitude</b> the product of
 * the rest. The <b>dimension</b> of an expression follows its arithmetic: a unit has the table's, a
 * number, a symbol and a constant none, a product adds, a power scales by a rational exponent, a
 * sum's terms must agree, an exact 0 agreeing with any dimension ({@code x _m + 2 _cm = 0}). {@code
 * abs}, {@code sqrt}, {@code cbrt}, {@code root}, {@code min} and {@code max} carry their
 * arguments' dimension and {@code sign} has none; any other application has none, its arguments
 * having been checked when it was evaluated, and so has a relation or a collection. An unevaluated
 * form has its body's dimension.
 */
public final class Quantities {

  /** The functions that take a quantity (§14.26); every other builtin function needs a number. */
  public static final Set<String> FUNCTIONS =
      Set.of("abs", "sign", "sqrt", "cbrt", "root", "min", "max");

  private Quantities() {}

  /**
   * Whether a factor is a unit or a power of one.
   *
   * @param f the factor
   * @return whether it is a unit factor
   */
  public static boolean isUnitFactor(Expr f) {
    return f instanceof Unit || (f instanceof Power p && p.base() instanceof Unit);
  }

  /**
   * The unit factors of a canonical term, in order.
   *
   * @param term the term
   * @return its unit factors
   */
  public static List<Expr> unitFactors(Expr term) {
    if (term instanceof Product p) {
      return p.factors().stream().filter(Quantities::isUnitFactor).toList();
    }
    return isUnitFactor(term) ? List.of(term) : List.of();
  }

  /**
   * The unit part of a canonical term: the product of its unit factors, 1 when none.
   *
   * @param term the term
   * @return the unit part
   */
  public static Expr unitPart(Expr term) {
    return product(unitFactors(term));
  }

  /**
   * The magnitude of a canonical term: the product of its factors that are no unit factors.
   *
   * @param term the term
   * @return the magnitude
   */
  public static Expr magnitude(Expr term) {
    if (term instanceof Product p) {
      return product(p.factors().stream().filter(f -> !isUnitFactor(f)).toList());
    }
    return isUnitFactor(term) ? Num.ONE : term;
  }

  private static Expr product(List<Expr> factors) {
    return factors.isEmpty()
        ? Num.ONE
        : factors.size() == 1 ? factors.getFirst() : Canonical.of(Product.of(factors));
  }

  /**
   * Whether an expression holds a unit anywhere in its arithmetic.
   *
   * @param e the expression
   * @return whether it does
   */
  public static boolean hasUnit(Expr e) {
    return switch (e) {
      case Unit _ -> true;
      case Sum s -> s.terms().stream().anyMatch(Quantities::hasUnit);
      case Product p -> p.factors().stream().anyMatch(Quantities::hasUnit);
      case Power p -> hasUnit(p.base()) || hasUnit(p.exponent());
      default -> false;
    };
  }

  /**
   * Whether an expression's arithmetic holds a unit of angle, radians or degrees.
   *
   * @param e the expression
   * @return whether it does
   */
  public static boolean hasAngle(Expr e) {
    return switch (e) {
      case Unit u -> Units.info(u).isAngle();
      case Sum s -> s.terms().stream().anyMatch(Quantities::hasAngle);
      case Product p -> p.factors().stream().anyMatch(Quantities::hasAngle);
      case Power p -> hasAngle(p.base());
      default -> false;
    };
  }

  /**
   * The dimension of an expression.
   *
   * @param e the expression, canonical
   * @return its dimension
   * @throws DimensionException if its dimensions do not fit
   */
  public static Dimension dimension(Expr e) {
    return switch (e) {
      case Unit u -> Units.info(u).dimension();
      case Product p -> {
        Dimension d = Dimension.NONE;
        for (Expr f : p.factors()) {
          d = d.times(dimension(f));
        }
        yield d;
      }
      case Power p -> power(p);
      case Sum s -> agreeing(s.terms(), Kind.ADD);
      case Apply a -> apply(a);
      case Derivative d -> dimension(d.expr());
      case Integral i -> dimension(i.integrand());
      case Limit l -> dimension(l.expr());
      case BigSum s -> dimension(s.body());
      case Relation r -> {
        agreeing(List.of(r.left(), r.right()), Kind.COMPARE);
        yield Dimension.NONE;
      }
      case BigProduct _ -> Dimension.NONE;
      default -> Dimension.NONE;
    };
  }

  /**
   * Whether an expression is the exact number 0, which has every dimension: {@code 0 _m} is 0, so
   * {@code x _m + 2 _cm = 0} and {@code max(0, 3 _m)} are quantities of one dimension.
   *
   * @param e the expression
   * @return whether it is the exact 0
   */
  public static boolean isZero(Expr e) {
    return e.equals(Num.ZERO);
  }

  /**
   * The one dimension of expressions that must agree, an exact 0 agreeing with any.
   *
   * @param items the expressions
   * @param kind the error when two disagree
   * @return their dimension, none when every item is 0
   * @throws DimensionException if two disagree
   */
  private static Dimension agreeing(List<Expr> items, Kind kind) {
    Expr first = null;
    Dimension d = Dimension.NONE;
    for (Expr t : items) {
      if (isZero(t)) {
        continue;
      }
      Dimension dt = dimension(t);
      if (first == null) {
        first = t;
        d = dt;
      } else if (!dt.equals(d)) {
        throw new DimensionException(kind, first, t, null);
      }
    }
    return d;
  }

  private static Dimension power(Power p) {
    Dimension base = dimension(p.base());
    if (!dimension(p.exponent()).isNone()) {
      throw new DimensionException(Kind.EXPONENT_UNITS, p.exponent(), null, null);
    }
    if (base.isNone()) {
      return base;
    }
    if (p.exponent() instanceof Num n && n.value() instanceof RealNumeric r && r.isExact()) {
      return base.pow(r);
    }
    throw new DimensionException(Kind.EXPONENT, p, null, null);
  }

  private static Dimension apply(Apply a) {
    String name = a.head().name();
    if (!FUNCTIONS.contains(name) || a.args().isEmpty()) {
      return Dimension.NONE;
    }
    Dimension d = dimension(a.args().getFirst());
    return switch (name) {
      case "sign" -> Dimension.NONE;
      case "sqrt" -> d.pow(Rational.of(1, 2));
      case "cbrt" -> d.pow(Rational.of(1, 3));
      case "root" -> {
        if (d.isNone()) {
          yield d;
        }
        if (a.args().size() == 2
            && a.args().get(1) instanceof Num n
            && n.value() instanceof Int k
            && k.signum() != 0) {
          yield d.pow(Rational.of(BigInteger.ONE, k.value()));
        }
        throw new DimensionException(Kind.EXPONENT, a, null, null);
      }
      case "min", "max" -> agreeing(a.args(), Kind.COMPARE);
      default -> d;
    };
  }

  /**
   * The exact factor of a unit part to the coherent SI units of its dimension, canonical.
   *
   * @param unitPart a product of unit factors, or 1
   * @return the factor
   */
  public static Expr factor(Expr unitPart) {
    List<Expr> out = new ArrayList<>();
    for (Expr f : unitFactors(unitPart)) {
      if (f instanceof Unit u) {
        out.add(Units.info(u).factor());
      } else {
        Power p = (Power) f;
        out.add(Power.of(Units.info((Unit) p.base()).factor(), p.exponent()));
      }
    }
    if (out.isEmpty()) {
      return Num.ONE;
    }
    out.addFirst(Num.ONE);
    return Canonical.of(Product.of(out));
  }

  private static final Dimension TEMPERATURE = Dimension.of(0, 0, 0, 0, 1, 0, 0);

  /**
   * Whether a term is a bare temperature: a magnitude without units times one unit of temperature
   * to the first power ({@code 20 _degC}, {@code x _K}).
   *
   * @param term the term
   * @return whether it is
   */
  public static boolean isBareTemperature(Expr term) {
    List<Expr> units = unitFactors(term);
    return units.size() == 1
        && units.getFirst() instanceof Unit u
        && Units.info(u).dimension().equals(TEMPERATURE)
        && !hasUnit(magnitude(term));
  }

  /**
   * A quantity's value in the coherent SI units of its dimension: the magnitude times the unit
   * part's factor, term by term in a sum; on the absolute scale for a bare temperature when {@code
   * absolute} (20 °C is 293.15 K), otherwise as a difference (20 °C is 20 K).
   *
   * @param e the quantity
   * @param absolute whether a bare temperature is taken on the absolute scale
   * @return its value, canonical
   */
  public static Expr inBase(Expr e, boolean absolute) {
    if (e instanceof Sum s) {
      return Canonical.of(Sum.of(s.terms().stream().map(t -> inBase(t, absolute)).toList()));
    }
    Expr magnitude = magnitude(e);
    Expr part = unitPart(e);
    if (absolute && part instanceof Unit u && Units.info(u).offset() != null) {
      magnitude = Sum.of(magnitude, Num.of(Units.info(u).offset()));
    }
    return Canonical.of(Product.of(magnitude, factor(part)));
  }

  /**
   * A quantity as one term: a sum of quantities of one dimension written in one unit, as the rule
   * {@code add-quantities} writes it; any other expression as it is.
   *
   * @param e the quantity, canonical
   * @param mc the precision
   * @return the quantity as one term
   */
  public static Expr single(Expr e, java.math.MathContext mc) {
    if (!(e instanceof Sum)) {
      return e;
    }
    return mathcanvas.engine.rule.Rewriter.rewrite(
            e,
            UnitRules.RULES,
            new mathcanvas.engine.rule.Context(
                mathcanvas.engine.assume.Assumptions.EMPTY, mc, false, 10_000))
        .expr();
  }

  /**
   * The number a dimensionless expression stands for: every unit replaced by its factor, so {@code
   * _km/_m} is 1000 and {@code 30 _deg} is {@code π/6}.
   *
   * @param e a dimensionless expression
   * @return the number, canonical
   */
  public static Expr number(Expr e) {
    return Canonical.of(replaced(e));
  }

  private static Expr replaced(Expr e) {
    return switch (e) {
      case Unit u -> Units.info(u).factor();
      case Sum s -> Sum.of(s.terms().stream().map(Quantities::replaced).toList());
      case Product p -> Product.of(p.factors().stream().map(Quantities::replaced).toList());
      case Power p -> Power.of(replaced(p.base()), p.exponent());
      default -> e;
    };
  }
}
