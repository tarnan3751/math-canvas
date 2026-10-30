package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import org.junit.jupiter.api.Test;

/**
 * The trigonometric rules in degrees and gradians agree with evaluation ({@code docs/language.md}
 * §14.2): a symbolic value simplified and then given a decimal ({@code subs}) has the value the
 * expression has with that decimal written in, which the evaluator computes through its inexact
 * path in the session's unit without any rule; and at every multiple of a twelfth of a half turn
 * the exact value a circular function takes in the unit is its decimal value.
 */
class AngleModeProperties {

  private static final List<Settings> MODES = List.of(Run.DEGREES, Run.GRADIANS);

  private static final List<String> CIRCULAR = List.of("sin", "cos", "tan", "cot", "sec", "csc");

  private static final MathContext READ = new MathContext(30);

  /** A value past this magnitude is a pole within rounding. */
  private static final BigDecimal POLE = new BigDecimal("1e15");

  /**
   * A symbolic argument shifted by multiples of a twelfth of a half turn, by quarter and half
   * turns, by {@code π} (which no rule may move in these modes), and the inverse reflections, each
   * simplified and given a decimal, agree with the expression evaluated at that decimal.
   */
  @Property(tries = 400)
  void simplifiedAndEvaluatedAgreeInEachUnit(@ForAll Random random) {
    Settings mode = MODES.get(random.nextInt(MODES.size()));
    int h = mode == Run.DEGREES ? 180 : 200;
    String f = CIRCULAR.get(random.nextInt(CIRCULAR.size()));
    String x = decimal(random, 0.05, 0.95);
    String j = String.valueOf(random.nextInt(9) - 4);
    String k = String.valueOf(random.nextInt(9) - 4);
    // a symbol that multiplies π: an exact 0 one time in three, else a small integer
    String y = random.nextInt(3) == 0 ? "0" : String.valueOf(random.nextInt(7) - 3);
    String source =
        switch (random.nextInt(10)) {
          case 0 ->
              f + "(" + coefficient(random) + "x + " + (random.nextInt(49) - 24) * h / 12 + ")";
          case 1 -> f + "(x + " + (random.nextInt(9) - 4) * h + ")";
          case 2 -> f + "(x " + (random.nextBoolean() ? "+ " : "- ") + h / 2 + ")";
          case 3 -> f + "(x + " + (random.nextInt(7) - 3) + "pi/2)";
          case 4 -> List.of("acos(-x)", "acos(-2x/3)", "atan(1/x)").get(random.nextInt(3));
          case 5 -> List.of("asec(-1/x)", "asec(-2/x)").get(random.nextInt(2));
          // an integer symbol times whole half turns, which leaves a plain number
          case 6 -> {
            // under an inverse function too, which reads the closed rest the rule leaves
            String turns =
                (1 + random.nextInt(3)) * 2 * h + "j + " + (random.nextInt(25) - 12) * h / 12;
            yield switch (random.nextInt(3)) {
              case 0 -> f + "(" + turns + ")";
              case 1 -> "asin(sin(" + turns + "))";
              default -> "atan(tan(" + turns + "))";
            };
          }
          // a coefficient of π that can vanish, beside a plain term or alone
          case 8 ->
              List.of(
                      f + "(pi y + x + pi/2)",
                      f + "(pi y + 2 + pi/2)",
                      f + "(pi k + 2 pi + x)",
                      f + "(pi k + pi y + x)",
                      f + "(pi k + pi y)")
                  .get(random.nextInt(5));
          // reflections of a constant-typed argument, 0 where its coefficient vanishes
          case 9 -> List.of("acos(-pi y/8)", "atan(pi y)", "asin(-pi y/8)").get(random.nextInt(3));
          // constant-typed: π beside plain-typed terms, read in radians at every value
          default ->
              List.of(f + "(pi k)", f + "(x + pi)", f + "(x + 2 pi k)", f + "(pi k + pi/3)")
                  .get(random.nextInt(4));
        };
    // numeric(…) reads what stays symbolic, a decimal beside π, in radians, as §14.2 reads a
    // non-plain argument; a call of plain numbers is evaluated in the unit before it. The raw side
    // evaluates the expression with the decimal written in; the other evaluates the expression,
    // puts the decimal into its simplified value, and evaluates that, as a plotted value is used.
    Map<String, String> point = Map.of("x", x, "j", j, "k", k, "y", y);
    String written = source;
    for (Map.Entry<String, String> v : point.entrySet()) {
      written = written.replace(v.getKey(), "(" + v.getValue() + ")");
    }
    Optional<BigDecimal> raw = number("numeric(" + written + ")", mode);
    Optional<BigDecimal> simplified = intoValue(source, point, mode);
    // at a pole both sides are the reciprocal of rounding noise: no evidence
    boolean compared =
        raw.isPresent()
            && simplified.isPresent()
            && raw.get().abs().compareTo(POLE) < 0
            && simplified.get().abs().compareTo(POLE) < 0;
    Statistics.label("compared").collect(compared);
    Statistics.label("compared").coverage(c -> c.check(true).percentage(p -> p > 80));
    if (compared) {
      assertClose(simplified.get(), raw.get(), source + " at " + point + " in " + mode.angleMode());
    }
  }

  @Test
  void theExactValuesInEachUnitAreTheDecimalOnes() {
    for (Settings mode : MODES) {
      int h = mode == Run.DEGREES ? 180 : 200;
      for (String f : CIRCULAR) {
        for (int k = -24; k <= 24; k++) {
          String angle = (k * h) + "/12";
          Expr exact = Run.value(f + "(" + angle + ")", mode);
          Optional<BigDecimal> decimal = number(f + "(" + angle + " + 0.0)", mode);
          String what = f + "(" + angle + ") in " + mode.angleMode();
          // the exact value is the table's, not a conversion left standing
          assertThat(exact.toString()).as(what).doesNotContain("Apply[" + f);
          if (exact.equals(Constant.UNDEFINED)) {
            // a pole: the decimal is the reciprocal of rounding noise, or none
            decimal.ifPresent(
                v -> assertThat(v.abs()).as(what).isGreaterThan(new BigDecimal("1e20")));
            continue;
          }
          Optional<BigDecimal> value = number("numeric(" + f + "(" + angle + "))", mode);
          assertThat(value).as(what).isPresent();
          assertThat(decimal).as(what).isPresent();
          assertClose(value.get(), decimal.get(), what);
        }
      }
      // inverse functions of plain numbers answer in the unit (a radical is no plain number, so
      // asin(√2/2) is π/4 in every mode, §14.2)
      for (String g : List.of("asin", "acos", "atan")) {
        for (String v : List.of("0", "1/2", "-1/2", "1", "-1")) {
          Optional<BigDecimal> exact = number("numeric(" + g + "(" + v + "))", mode);
          Optional<BigDecimal> decimal = number(g + "(numeric(" + v + "))", mode);
          if (exact.isPresent() && decimal.isPresent()) {
            assertClose(exact.get(), decimal.get(), g + "(" + v + ") in " + mode.angleMode());
          }
        }
      }
    }
  }

  /**
   * {@code source} evaluated under {@code x > 0} and {@code j, k ∈ ℤ}, the point's values put into
   * its value, and that evaluated and made numeric, all in {@code mode}.
   */
  private static Optional<BigDecimal> intoValue(
      String source, Map<String, String> point, Settings mode) {
    Assumptions facts = Assumptions.EMPTY;
    for (String r : List.of("x > 0", "j ∈ ℤ", "k ∈ ℤ")) {
      facts = facts.with((Relation) Parser.parseExpression(r, Names.NONE));
    }
    Environment env = Environment.EMPTY.withSettings(mode).withAssumptions(facts);
    try {
      Expr value =
          Evaluator.evaluate(Parser.parseExpression(source, Names.NONE), env, false).value();
      TreeMap<String, Expr> values = new TreeMap<>();
      point.forEach((name, v) -> values.put(name, Parser.parseExpression(v, Names.NONE)));
      Expr at = Substitution.apply(value, values);
      Expr result = Evaluator.evaluate(Apply.of(Symbol.of("numeric"), at), env, false).value();
      if (!(result instanceof Num n) || !n.value().im().isZero()) {
        return Optional.empty();
      }
      return Optional.of(n.value().re().toReal(READ).value());
    } catch (EvaluationException undefinedOrOutOfRange) {
      return Optional.empty();
    }
  }

  private static String coefficient(Random random) {
    return List.of("", "2", "-", "3/2").get(random.nextInt(4));
  }

  private static String decimal(Random random, double lo, double hi) {
    return BigDecimal.valueOf(lo + (hi - lo) * random.nextDouble())
        .round(new MathContext(6))
        .toString();
  }

  /** The value of the last statement as a real decimal, or empty when it is no real number. */
  private static Optional<BigDecimal> number(String script, Settings mode) {
    Outcome o = Run.last(script, mode);
    if (!(o instanceof Outcome.Value v) || !(v.value() instanceof Num n)) {
      return Optional.empty();
    }
    Numeric value = n.value();
    if (!value.im().isZero()) {
      return Optional.empty();
    }
    return Optional.of(value.re().toReal(READ).value());
  }

  private static void assertClose(BigDecimal actual, BigDecimal expected, String what) {
    BigDecimal tolerance = expected.abs().max(BigDecimal.ONE).multiply(new BigDecimal("1e-25"));
    assertThat(actual.subtract(expected).abs())
        .as(what + ": " + actual + " vs " + expected)
        .isLessThanOrEqualTo(tolerance);
  }
}
