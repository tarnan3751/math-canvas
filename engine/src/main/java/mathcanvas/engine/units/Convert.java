package mathcanvas.engine.units;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement.Valued.Result;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * {@code convert(q, unit)} ({@code docs/language.md} §14.26): a quantity written in other units of
 * its dimension, with exact factors, through the rule set {@code convert}.
 *
 * <p>The operation rewrites the node {@code conversion(q, target, q)}, a head of its own whose
 * third argument keeps the quantity as given for the narration. {@code to-base} writes the first
 * unit of {@code q} that is not a coherent SI base unit in base units ({@code 1 km = 1000 m},
 * {@code 1 N = 1 kg m/s²}), one unit per rewrite; then {@code to-target}, or {@code
 * to-defined-target} when the target is not in base units ({@code 1 J = 1 kg m²/s²}), divides the
 * magnitude by the target's factor. A bare temperature to a single unit of temperature converts on
 * the absolute scale in one step, {@code affine}. {@link UnitsNarrator} makes the run one step:
 * {@code 1 km = 1000 m. 1 h = 3600 s. So 5 km/h = 25/18 m/s}.
 */
public final class Convert {

  /** The rule set's name. */
  public static final String RULE_SET = "convert";

  private static final Symbol HEAD = Symbol.of("conversion");

  private static final Pattern PATTERN =
      Pattern.of(Apply.of(HEAD, Symbol.of("q_"), Symbol.of("t_"), Symbol.of("o_")));

  /** The rule set. */
  public static final RuleSet RULES =
      new RuleSet(
          RULE_SET,
          List.of(
              Rule.valued("affine", PATTERN, Set.of("quantity", "result"), Convert::affine),
              Rule.valued("to-base", PATTERN, Set.of("unit", "definition"), Convert::toBase),
              Rule.valued(
                  "to-target",
                  PATTERN,
                  Set.of("quantity", "result", "num", "den", "target"),
                  m -> toTarget(m, false)),
              Rule.valued(
                  "to-defined-target",
                  PATTERN,
                  Set.of("quantity", "result", "unit", "definition", "num", "den", "target"),
                  m -> toTarget(m, true))));

  /** Why a conversion is refused. */
  public static final class ConvertException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    ConvertException(String message) {
      super(message);
    }
  }

  private Convert() {}

  /**
   * Converts a quantity.
   *
   * @param q the quantity, evaluated and canonical
   * @param target the target unit part, evaluated and canonical
   * @param ctx the context: precision, trace, cancellation
   * @return the converted quantity and the trace
   * @throws ConvertException if the target is no unit, holds two units of one dimension, or has
   *     another dimension
   * @throws DimensionException if the quantity's dimensions do not fit
   */
  public static Rewriter.Result of(Expr q, Expr target, Context ctx) {
    if (!isUnits(target)) {
      throw new ConvertException("convert needs a unit such as _m/_s");
    }
    List<Expr> factors = Quantities.unitFactors(target);
    for (int i = 0; i < factors.size(); i++) {
      for (int j = i + 1; j < factors.size(); j++) {
        if (dimension(factors.get(i)).equals(dimension(factors.get(j)))) {
          throw new ConvertException("convert's target must not hold two units of one dimension");
        }
      }
    }
    Expr one = q;
    if (q instanceof Sum) {
      one = Rewriter.rewrite(q, UnitRules.RULES, ctx.withTrace(false)).expr();
    }
    Dimension from = Quantities.dimension(one);
    Dimension to = Quantities.dimension(target);
    if (Quantities.isZero(one) && to.equals(Units.info(Units.unit("K")).dimension())) {
      // 0 _degC is the number 0 in canonical form, so the scale it was on is gone (§14.26)
      throw new ConvertException(
          "a temperature of 0 is the number 0, which keeps no unit: write 0.0 _degC or 273.15 _K");
    }
    if (!from.equals(to)) {
      throw new ConvertException(
          "cannot convert " + text(Quantities.unitPart(one)) + " to " + text(target));
    }
    return Rewriter.rewrite(new Apply(HEAD, List.of(one, target, one)), RULES, ctx);
  }

  /** A unit part for messages as the Unicode printer shows it: {@code km/h}, {@code a number}. */
  private static String text(Expr part) {
    return part.equals(Num.ONE)
        ? "a number"
        : mathcanvas.engine.print.UnicodePrinter.INSTANCE.display(part).replace("_", "");
  }

  private static boolean isUnits(Expr e) {
    return switch (e) {
      case Unit _ -> true;
      case Power p -> p.base() instanceof Unit && p.exponent() instanceof Num;
      case Product p -> p.factors().stream().allMatch(Convert::isUnits);
      default -> false;
    };
  }

  private static Dimension dimension(Expr unitFactor) {
    return Units.info((Unit) (unitFactor instanceof Power p ? p.base() : unitFactor)).dimension();
  }

  /** Whether a unit is a coherent SI base unit, its own definition. */
  private static boolean isBase(Unit u) {
    return Dimension.BASE.contains(u.name());
  }

  private static Optional<Result> affine(Match m) {
    if (!(m.subject() instanceof Apply a && a.head().equals(HEAD))) {
      return Optional.empty();
    }
    Expr q = a.args().get(0);
    Expr t = a.args().get(1);
    if (!Quantities.isBareTemperature(q) || !(t instanceof Unit target)) {
      return Optional.empty();
    }
    Unit source = (Unit) Quantities.unitPart(q);
    Units.Info si = Units.info(source);
    Units.Info ti = Units.info(target);
    if (!ti.dimension().equals(si.dimension()) || (si.offset() == null && ti.offset() == null)) {
      return Optional.empty();
    }
    // kelvins = (magnitude + offset) · factor; the target's magnitude = kelvins / factor - offset
    Expr kelvins = Product.of(Sum.of(Quantities.magnitude(q), offset(si)), si.factor());
    Expr magnitude =
        Sum.of(
            Product.of(kelvins, Power.of(ti.factor(), Num.MINUS_ONE)),
            Product.of(Num.MINUS_ONE, offset(ti)));
    Expr result = Canonical.of(Product.of(expanded(magnitude), target), m.context().precision());
    return Optional.of(result(result, a.args().get(2), result));
  }

  /** A magnitude multiplied out when it is a polynomial: {@code 9x/5 + 32}, not {@code 9(…)/5}. */
  private static Expr expanded(Expr magnitude) {
    try {
      return Polynomial.of(magnitude).toExpr();
    } catch (IllegalArgumentException notPolynomial) {
      return magnitude;
    }
  }

  private static Expr offset(Units.Info info) {
    return info.offset() == null ? Num.ZERO : Num.of(info.offset());
  }

  private static Optional<Result> toBase(Match m) {
    if (!(m.subject() instanceof Apply a && a.head().equals(HEAD))) {
      return Optional.empty();
    }
    Expr q = a.args().get(0);
    for (Expr f : Quantities.unitFactors(q)) {
      Unit u = (Unit) (f instanceof Power p ? p.base() : f);
      if (isBase(u)) {
        continue;
      }
      Expr definition =
          Canonical.of(Product.of(Units.info(u).factor(), base(u)), m.context().precision());
      List<Expr> factors = new ArrayList<>();
      for (Expr g : q instanceof Product p ? p.factors() : List.of(q)) {
        factors.add(
            g.equals(f)
                ? (f instanceof Power p ? Power.of(definition, p.exponent()) : definition)
                : g);
      }
      factors.add(Num.ONE);
      Expr converted = Canonical.of(Product.of(factors), m.context().precision());
      TreeMap<String, Expr> values = new TreeMap<>();
      values.put("unit", u);
      values.put("definition", definition);
      Expr node =
          Canonical.of(new Apply(HEAD, List.of(converted, a.args().get(1), a.args().get(2))));
      return Optional.of(new Result(node, values));
    }
    return Optional.empty();
  }

  /** The coherent base units of a unit's dimension: {@code _kg _m _s^-2} for the newton. */
  private static Expr base(Unit u) {
    List<Expr> factors = new ArrayList<>();
    factors.add(Num.ONE);
    Dimension d = Units.info(u).dimension();
    for (int k = 0; k < Dimension.BASE.size(); k++) {
      if (!d.exponents().get(k).isZero()) {
        factors.add(Power.of(Units.unit(Dimension.BASE.get(k)), Num.of(d.exponents().get(k))));
      }
    }
    return factors.size() == 1 ? Num.ONE : Canonical.of(Product.of(factors));
  }

  private static Optional<Result> toTarget(Match m, boolean defined) {
    if (!(m.subject() instanceof Apply a && a.head().equals(HEAD))) {
      return Optional.empty();
    }
    Expr q = a.args().get(0);
    Expr t = a.args().get(1);
    for (Expr f : Quantities.unitFactors(q)) {
      if (!isBase((Unit) (f instanceof Power p ? p.base() : f))) {
        return Optional.empty();
      }
    }
    boolean baseTarget =
        Quantities.unitFactors(t).stream()
            .allMatch(f -> isBase((Unit) (f instanceof Power p ? p.base() : f)));
    if (defined == baseTarget) {
      return Optional.empty();
    }
    Expr magnitude =
        Product.of(Quantities.magnitude(q), Power.of(Quantities.factor(t), Num.MINUS_ONE));
    Expr converted = Canonical.of(Product.of(magnitude, t), m.context().precision());
    TreeMap<String, Expr> values =
        new TreeMap<>(result(converted, a.args().get(2), converted).values());
    if (defined) {
      values.put("unit", t);
      values.put("definition", Canonical.of(Product.of(Quantities.factor(t), baseOf(t))));
    }
    quotient(a.args().get(2), t)
        .ifPresent(
            nd -> {
              values.put("num", Num.of(nd[0]));
              values.put("den", Num.of(nd[1]));
              values.put("target", t);
            });
    return Optional.of(new Result(converted, values));
  }

  /**
   * The magnitude of a conversion as the quotient before it is reduced, its numerator and
   * denominator, when the quantity's magnitude and every factor are rational, the target has a unit
   * above the line, and the quotient is not in lowest terms ({@code 5 km/h = 5000/3600 m/s}).
   */
  private static Optional<Int[]> quotient(Expr q, Expr target) {
    BigInteger[] nd = Quantities.magnitude(q) instanceof Num n ? fraction(n.value()) : null;
    boolean above = false;
    for (Expr f : Quantities.unitFactors(target)) {
      above |=
          !(f instanceof Power p
              && p.exponent() instanceof Num e
              && e.value() instanceof Int k
              && k.signum() < 0);
    }
    if (nd == null
        || !above
        || !scale(nd, Quantities.unitFactors(q), false)
        || !scale(nd, Quantities.unitFactors(target), true)
        || nd[1].equals(BigInteger.ONE)
        || nd[0].gcd(nd[1]).equals(BigInteger.ONE)) {
      return Optional.empty();
    }
    return Optional.of(new Int[] {Int.of(nd[0]), Int.of(nd[1])});
  }

  /** An exact rational's numerator and denominator, or null. */
  private static BigInteger[] fraction(Numeric v) {
    return switch (v) {
      case Int i -> new BigInteger[] {i.value(), BigInteger.ONE};
      case Rational r -> new BigInteger[] {r.numerator().value(), r.denominator().value()};
      default -> null;
    };
  }

  /** Multiplies {@code nd} by each unit factor's factor, or divides when {@code inverse}. */
  private static boolean scale(BigInteger[] nd, List<Expr> factors, boolean inverse) {
    for (Expr f : factors) {
      Unit u = (Unit) (f instanceof Power p ? p.base() : f);
      int k = 1;
      if (f instanceof Power p) {
        if (!(p.exponent() instanceof Num e
            && e.value() instanceof Int i
            && i.value().bitLength() < 16)) {
          return false;
        }
        k = i.value().intValue();
      }
      BigInteger[] factor = Units.info(u).factor() instanceof Num v ? fraction(v.value()) : null;
      if (factor == null) {
        return false;
      }
      BigInteger top = factor[0].pow(Math.abs(k));
      BigInteger bottom = factor[1].pow(Math.abs(k));
      boolean divide = (k < 0) != inverse;
      nd[0] = nd[0].multiply(divide ? bottom : top);
      nd[1] = nd[1].multiply(divide ? top : bottom);
    }
    return true;
  }

  /** A unit part in coherent base units, without its factor. */
  private static Expr baseOf(Expr part) {
    List<Expr> factors = new ArrayList<>();
    factors.add(Num.ONE);
    for (Expr f : Quantities.unitFactors(part)) {
      Unit u = (Unit) (f instanceof Power p ? p.base() : f);
      factors.add(f instanceof Power p ? Power.of(base(u), p.exponent()) : base(u));
    }
    return factors.size() == 1 ? Num.ONE : Canonical.of(Product.of(factors));
  }

  private static Result result(Expr expr, Expr quantity, Expr converted) {
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("quantity", quantity);
    values.put("result", converted);
    return new Result(expr, values);
  }
}
