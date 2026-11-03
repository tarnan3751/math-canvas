package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.units.Dimension;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Quantities on random data (§14.26): the {@code units} rule set is sound when the base units take
 * random positive values (a unit is its factor times the base units' values, so an identity between
 * units holds only if it holds for every choice), every sum of one dimension simplifies within the
 * rewrite cap to a single quantity, and every sum of two dimensions is refused.
 */
class UnitsProperties {

  private static final MathContext MC = new MathContext(40);

  /** Units of one dimension each: length, time, energy, speed, mass, dimensionless angles. */
  private static final List<List<String>> GROUPS =
      List.of(
          List.of("_m", "_cm", "_km", "_in", "_ft", "_mi", "_nmi", "_um"),
          List.of("_s", "_ms", "_min", "_h", "_d", "_yr"),
          List.of("_J", "_kWh", "_eV", "_cal", "_kJ", "_N _m", "_W _h", "_kg _m^2/_s^2"),
          List.of("_m/_s", "_km/_h", "_mph", "_ft/_min"),
          List.of("_kg", "_g", "_lb", "_oz", "_t", "_ton"),
          List.of("_rad", "_deg", "_km/_m", "_h _Hz"));

  /** A sum of quantities of one dimension. */
  record Case(String text, int group) {}

  @Provide
  Arbitrary<Case> sums() {
    return Arbitraries.randomValue(UnitsProperties::sum);
  }

  static Case sum(Random random) {
    int g = random.nextInt(GROUPS.size());
    List<String> units = GROUPS.get(g);
    StringBuilder text = new StringBuilder();
    int terms = 2 + random.nextInt(3);
    for (int k = 0; k < terms; k++) {
      if (k > 0) {
        text.append(random.nextBoolean() ? " + " : " - ");
      }
      text.append(magnitude(random)).append(' ').append(units.get(random.nextInt(units.size())));
    }
    if (random.nextInt(4) == 0) {
      text.insert(0, "(").append(")^2");
    }
    return new Case(text.toString(), g);
  }

  private static String magnitude(Random random) {
    return switch (random.nextInt(4)) {
      case 0 -> String.valueOf(1 + random.nextInt(50));
      case 1 -> (1 + random.nextInt(20)) + "/" + (1 + random.nextInt(9));
      case 2 -> "x";
      default -> "(" + (1 + random.nextInt(9)) + " + y)";
    };
  }

  private static Map<Symbol, Numeric> point(Random random) {
    Map<Symbol, Numeric> p = new HashMap<>();
    for (String base : Dimension.BASE) {
      p.put(Symbol.of("_" + base), Rational.of(1 + random.nextInt(97), 1 + random.nextInt(89)));
    }
    p.put(Symbol.of("x"), Rational.of(1 + random.nextInt(30), 7));
    p.put(Symbol.of("y"), Rational.of(1 + random.nextInt(30), 11));
    return p;
  }

  @Property(tries = 200)
  void theUnitsRulesAreSoundForEveryChoiceOfTheBaseUnits(
      @ForAll("sums") Case c, @ForAll("seeds") long seed) {
    Expr raw = Run.canonical(c.text());
    Expr value = Run.value(c.text());
    Random random = new Random(seed);
    for (int k = 0; k < 3; k++) {
      Map<Symbol, Numeric> point = point(random);
      Optional<Numeric> before = NumericEvaluator.eval(raw, point, MC);
      Optional<Numeric> after = NumericEvaluator.eval(value, point, MC);
      assertThat(before).as(c.text()).isPresent();
      assertThat(after).as(c.text() + " → " + value).isPresent();
      Numeric diff = before.get().subtract(after.get(), MC);
      Numeric scale = before.get().modulus(MC).add(mathcanvas.engine.number.Int.ONE, MC);
      assertThat(
              diff.modulus(MC).toDouble()
                  / ((mathcanvas.engine.number.RealNumeric) scale).toDouble())
          .as(c.text() + " → " + value)
          .isLessThan(1e-25);
    }
  }

  @Property(tries = 200)
  void aSumOfOneDimensionBecomesOneQuantity(@ForAll("sums") Case c) {
    Expr value = Run.value(c.text());
    // one unit part remains: no sum of quantities with different unit parts
    String shown = Run.shown(c.text());
    assertThat(value).as(c.text()).isNotNull();
    if (!(value instanceof mathcanvas.engine.expr.Sum s)) {
      return;
    }
    long withUnits =
        s.terms().stream()
            .filter(
                t ->
                    !mathcanvas.engine.units.Quantities.unitPart(t)
                        .equals(mathcanvas.engine.expr.Num.ONE))
            .count();
    assertThat(withUnits).as(shown).isLessThanOrEqualTo(1);
  }

  @Property(tries = 100)
  void aSumOfTwoDimensionsIsRefused(@ForAll("sums") Case a, @ForAll("sums") Case b) {
    if (a.group() == b.group()
        || a.group() == GROUPS.size() - 1
        || b.group() == GROUPS.size() - 1
        // an exact 0 has every dimension: (x _in - x _in)^2 + x _s is x _s
        || Run.value(a.text()).equals(mathcanvas.engine.expr.Num.ZERO)
        || Run.value(b.text()).equals(mathcanvas.engine.expr.Num.ZERO)) {
      return;
    }
    assertThat(Run.error(a.text() + " + " + b.text())).startsWith("cannot add");
  }

  @Provide
  Arbitrary<Long> seeds() {
    return Arbitraries.longs();
  }
}
