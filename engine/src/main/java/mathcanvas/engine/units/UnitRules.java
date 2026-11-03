package mathcanvas.engine.units;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement.Valued.Result;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule set {@code units} ({@code docs/language.md} §14.26), first in the standard list: the
 * identities between units of one dimension, which the canonical form, treating units as unrelated
 * constants, does not apply.
 *
 * <ul>
 *   <li>{@code add-quantities}: a sum of at least two terms with units, or of terms with and
 *       without units that are all dimensionless, every term of one dimension, becomes one quantity
 *       in the unit part of its first term with units in canonical order, each term's magnitude
 *       times its factor over the target's ({@code 3 _m + 2 _cm} is {@code 151/50 _m}, {@code x _m
 *       + 2 _cm} is {@code (x + 1/50) _m}); a dimensionless sum becomes a number ({@code 1 + 30
 *       _deg} is {@code 1 + π/6}). Temperatures add as differences.
 *   <li>{@code merge-units}: in a product, of two different units with one dimension the later in
 *       the order is written in the earlier ({@code _km _m} is {@code 1000 _m²}, {@code _km/_m} is
 *       1000). Derived units are never expanded: {@code _N _m} stays.
 *   <li>{@code cancel-dimensionless}: a product of two or more different units whose dimensions
 *       cancel is their factor ({@code 3 _h _Hz} is 10800); a lone {@code _rad} or {@code _deg}
 *       stays.
 * </ul>
 *
 * <p>The set terminates: each rule lowers the number of different units in a sum or a product, and
 * no rule of the standard list makes a unit. A rule whose dimensions do not fit declines; the
 * evaluator reports the error.
 */
public final class UnitRules {

  private UnitRules() {}

  /** The rule set. */
  public static final RuleSet RULES =
      new RuleSet(
          "units",
          List.of(
              Rule.computed(
                  "add-quantities", Pattern.parse("x_ + rest__"), UnitRules::addQuantities),
              Rule.valued(
                  "merge-units",
                  Pattern.parse("x_ * rest__"),
                  Set.of("unit", "definition"),
                  UnitRules::mergeUnits),
              Rule.computed(
                  "cancel-dimensionless",
                  Pattern.parse("x_ * rest__"),
                  UnitRules::cancelDimensionless)));

  private static Optional<Expr> addQuantities(Match match) {
    if (!(match.subject() instanceof Sum s)) {
      return Optional.empty();
    }
    MathContext mc = match.context().precision();
    Expr target = null;
    int withUnits = 0;
    for (Expr t : s.terms()) {
      Expr part = Quantities.unitPart(t);
      if (!part.equals(Num.ONE)) {
        withUnits++;
        if (target == null) {
          target = part;
        }
      }
    }
    if (withUnits == 0) {
      return Optional.empty();
    }
    Dimension d;
    try {
      d = Quantities.dimension(s);
    } catch (DimensionException e) {
      return Optional.empty();
    }
    if (withUnits < 2 && !d.isNone()) {
      return Optional.empty();
    }
    Expr unit = d.isNone() ? Num.ONE : target;
    Expr targetFactor = Quantities.factor(unit);
    List<Expr> magnitudes = new ArrayList<>();
    for (Expr t : s.terms()) {
      Expr factor = Quantities.factor(Quantities.unitPart(t));
      magnitudes.add(
          Product.of(Quantities.magnitude(t), factor, Power.of(targetFactor, Num.MINUS_ONE)));
    }
    Expr sum = Canonical.of(Sum.of(magnitudes), mc);
    return Optional.of(unit.equals(Num.ONE) ? sum : Canonical.of(Product.of(sum, unit), mc));
  }

  /** A product's unit factors as units and exponents, or null when an exponent is no number. */
  private static List<Expr[]> units(Product p) {
    List<Expr[]> out = new ArrayList<>();
    for (Expr f : p.factors()) {
      if (f instanceof Unit u) {
        out.add(new Expr[] {u, Num.ONE});
      } else if (f instanceof Power w && w.base() instanceof Unit u) {
        if (!(w.exponent() instanceof Num n && n.value() instanceof RealNumeric)) {
          return null;
        }
        out.add(new Expr[] {u, w.exponent()});
      }
    }
    return out;
  }

  private static Optional<Result> mergeUnits(Match match) {
    if (!(match.subject() instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr[]> units = units(p);
    if (units == null || units.size() < 2) {
      return Optional.empty();
    }
    for (int i = 0; i < units.size(); i++) {
      Unit first = (Unit) units.get(i)[0];
      Dimension d = Units.info(first).dimension();
      for (int j = i + 1; j < units.size(); j++) {
        Unit later = (Unit) units.get(j)[0];
        if (!Units.info(later).dimension().equals(d)) {
          continue;
        }
        MathContext mc = match.context().precision();
        Expr ratio =
            Canonical.of(
                Product.of(
                    Units.info(later).factor(),
                    Power.of(Units.info(first).factor(), Num.MINUS_ONE)),
                mc);
        Expr definition = Canonical.of(Product.of(ratio, first), mc);
        List<Expr> factors = new ArrayList<>();
        for (Expr f : p.factors()) {
          boolean isLater = f.equals(later) || (f instanceof Power w && w.base().equals(later));
          factors.add(
              isLater ? Power.of(definition, f instanceof Power w ? w.exponent() : Num.ONE) : f);
        }
        TreeMap<String, Expr> values = new TreeMap<>();
        values.put("unit", later);
        values.put("definition", definition);
        return Optional.of(new Result(Canonical.of(Product.of(factors), mc), values));
      }
    }
    return Optional.empty();
  }

  private static Optional<Expr> cancelDimensionless(Match match) {
    if (!(match.subject() instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr[]> units = units(p);
    if (units == null || units.size() < 2) {
      return Optional.empty();
    }
    Dimension d = Dimension.NONE;
    for (Expr[] u : units) {
      RealNumeric n = (RealNumeric) ((Num) u[1]).value();
      if (!n.isExact()) {
        return Optional.empty();
      }
      d = d.times(Units.info((Unit) u[0]).dimension().pow(n));
    }
    if (!d.isNone()) {
      return Optional.empty();
    }
    return Optional.of(Quantities.number(p));
  }
}
