package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import mathcanvas.engine.compile.CompileOracle.Outcome;
import mathcanvas.engine.compile.UnsupportedException.Kind;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.testing.Recheck;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/**
 * Compiled evaluators against the engine at random double points ({@link CompileOracle}), over the
 * shared generators' expressions with their symbols renamed to the arguments, evaluated in the mode
 * first as the canvas will do. Ill-conditioned points are skipped and capped at one in 20 of the
 * points compared ({@link Recheck#end}), points beyond the oracle's bounds and points out of the
 * double range at one in 10 each.
 */
class CompiledProperties {

  static final Symbol X = Symbol.of("x");
  static final Symbol Y = Symbol.of("y");

  /** The functions a refusal may name: user functions and calls that stay symbolic. */
  static final Set<String> REFUSABLE =
      Set.of("f", "g", "root", "factorial", "binomial", "gcd", "lcm", "numerator", "denominator");

  /**
   * The most points beyond the oracle's bounds a property run may skip: one in this many of the
   * points it compared (10%).
   */
  static final int BOUNDED_DIVISOR = 10;

  /**
   * The most points out of the double range a property run may skip: one in this many of the points
   * it compared (10%).
   */
  static final int OUT_OF_RANGE_DIVISOR = 10;

  /** The points compared, beyond the bounds and out of range in the current property run. */
  private static int comparedPoints;

  private static int boundedPoints;

  private static int outOfRangePoints;

  @BeforeProperty
  void begin() {
    Recheck.begin();
    comparedPoints = 0;
    boundedPoints = 0;
    outOfRangePoints = 0;
  }

  /**
   * Caps the ill-conditioned points a property run skips ({@link Recheck#end}), and the points
   * beyond the oracle's bounds at one in {@link #BOUNDED_DIVISOR} of the points compared.
   */
  @AfterProperty
  void end(Reporter reporter) {
    Recheck.end(reporter);
    reporter.publishValue(
        "points beyond the bounds skipped", boundedPoints + " of " + comparedPoints + " compared");
    assertThat((long) boundedPoints * BOUNDED_DIVISOR)
        .as("points beyond the bounds skipped: %d of %d compared", boundedPoints, comparedPoints)
        .isLessThanOrEqualTo(comparedPoints);
    reporter.publishValue(
        "points out of range skipped", outOfRangePoints + " of " + comparedPoints + " compared");
    assertThat((long) outOfRangePoints * OUT_OF_RANGE_DIVISOR)
        .as("points out of range skipped: %d of %d compared", outOfRangePoints, comparedPoints)
        .isLessThanOrEqualTo(comparedPoints);
  }

  @Property(tries = 300)
  void agreesWithNumericInOneVariable(@ForAll Random random) {
    Expr raw = ExprArbitraries.trigExprs().generator(1000).next(random).value();
    check(rename(raw, Map.of("a", X, "b", X, "y", X)), List.of(X), Settings.DEFAULT, random);
  }

  @Property(tries = 300)
  void agreesWithNumericInTwoVariables(@ForAll Random random) {
    Expr raw = ExprArbitraries.elementaryExprs().generator(1000).next(random).value();
    check(rename(raw, Map.of("a", X, "b", Y)), List.of(X, Y), Settings.DEFAULT, random);
  }

  @Property(tries = 300)
  void agreesWithEvaluationInDegreesAndGradians(@ForAll Random random) {
    Expr raw = ExprArbitraries.trigExprs().generator(1000).next(random).value();
    AngleMode mode = random.nextInt(4) == 0 ? AngleMode.GRADIANS : AngleMode.DEGREES;
    check(
        rename(raw, Map.of("a", X, "b", X, "y", X)),
        List.of(X),
        Settings.DEFAULT.withAngleMode(mode),
        random);
  }

  private static void check(Expr raw, List<Symbol> args, Settings settings, Random random) {
    Environment env = Environment.EMPTY.withSettings(settings);
    Expr value;
    try {
      value = Evaluator.evaluate(raw, env, false).value();
    } catch (EvaluationException refused) {
      Statistics.label("compiled").collect("not evaluated");
      return;
    }
    if (settings.angleMode() != AngleMode.RADIANS && reconverts(value)) {
      // Evaluating the value again in the mode would convert a plain constant argument twice
      // (docs/language.md §14.2): such a value is no test of the compiled one.
      Statistics.label("compiled").collect("converts again");
      return;
    }
    Compiled compiled;
    try {
      compiled = Compiled.compile(value, args, settings);
    } catch (UnsupportedException refused) {
      assertThat(refused.kind())
          .as("%s: %s", value, refused.getMessage())
          .isNotEqualTo(Kind.FREE_SYMBOL);
      if (refused.kind() == Kind.FUNCTION) {
        assertThat(refused.subject()).as("%s", value).isIn(REFUSABLE);
      }
      Statistics.label("compiled").collect("refused: " + refused.kind());
      return;
    }
    Statistics.label("compiled").collect("compiled");
    int compared = 0;
    for (int k = 0; k < 8; k++) {
      double[] point = new double[args.size()];
      for (int j = 0; j < point.length; j++) {
        point[j] = point(random, settings.angleMode());
      }
      Outcome outcome = CompileOracle.check(value, args, settings, compiled, point);
      Statistics.label("points").collect(outcome);
      switch (outcome) {
        case AGREE -> compared++;
        case ILL_CONDITIONED -> Recheck.illConditioned();
        case BOUNDED -> boundedPoints++;
        case OUT_OF_RANGE -> outOfRangePoints++;
        case QUARTER_TURN -> {}
      }
    }
    Recheck.compared(compared);
    comparedPoints += compared;
  }

  /**
   * A point: full-mantissa doubles in [−10, 10] most often, then small integers and halves (in
   * degrees, multiples of 15 and 45), 0 and ±1, values near 1, tiny and large ones.
   */
  static double point(Random random, AngleMode mode) {
    return switch (random.nextInt(10)) {
      case 0, 1, 2, 3 -> random.nextDouble() * 20 - 10;
      case 4 ->
          mode == AngleMode.RADIANS
              ? random.nextInt(21) - 10
              : (random.nextInt(49) - 24) * (random.nextBoolean() ? 15 : 45);
      case 5 -> (random.nextInt(41) - 20) / 2.0;
      case 6 -> random.nextInt(3) - 1;
      case 7 -> 1 + (random.nextDouble() - 0.5) * 1e-6;
      case 8 -> (random.nextDouble() - 0.5) * 1e-6;
      default -> (random.nextDouble() - 0.5) * 2e3;
    };
  }

  /** Whether a value holds a circular function of numbers, which evaluation in a mode converts. */
  static boolean reconverts(Expr e) {
    if (e instanceof Apply a
        && CompileOracle.CIRCULAR_NAMES.contains(a.head().name())
        && a.args().stream()
            .allMatch(op -> op instanceof Num n && n.value() instanceof RealNumeric)) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (reconverts(op)) {
        return true;
      }
    }
    return false;
  }

  /** {@code e} with symbols renamed, raw. */
  static Expr rename(Expr e, Map<String, Symbol> names) {
    if (e instanceof Symbol s) {
      return names.getOrDefault(s.name(), s);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(rename(op, names));
    }
    return Operands.rebuild(e, out);
  }
}
