package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.testing.NumericEvaluator;
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

  /**
   * The statistics functions of a moderate expression, a shift plus a multiple of a bounded
   * function of {@code x} (or {@code x/100}), so that the points stay inside the functions' domains
   * and the double range: the special functions, every density and cumulative function the compiler
   * knows, with constant parameters in their domains and 1 to 50 degrees of freedom; the bounds of
   * a cumulative function include ±∞.
   */
  @Property(tries = 300)
  void agreesWithNumericForTheStatisticsFunctions(@ForAll Random random) {
    Expr bounded =
        switch (random.nextInt(5)) {
          case 0 -> apply("sin", X);
          case 1 -> apply("atan", X);
          case 2 -> apply("tanh", X);
          case 3 -> Product.of(X, Power.of(Sum.of(Num.ONE, Power.of(X, Num.of(2))), Num.MINUS_ONE));
          default -> Product.of(Num.of(Rational.of(1, 100)), X);
        };
    Expr u =
        Sum.of(
            Num.of(Rational.of(random.nextInt(21) - 10, 1 + random.nextInt(4))),
            Product.of(Num.of(Rational.of(1 + random.nextInt(10), 2)), bounded));
    Expr k = Num.of(1 + random.nextInt(50));
    Expr k2 = Num.of(1 + random.nextInt(50));
    Expr positive = Num.of(Rational.of(1 + random.nextInt(20), 1 + random.nextInt(5)));
    Expr any = Num.of(Rational.of(random.nextInt(21) - 10, 1 + random.nextInt(5)));
    Expr inf = Constant.INFINITY;
    Expr minusInf = Product.of(Num.MINUS_ONE, Constant.INFINITY);
    Expr call =
        switch (random.nextInt(16)) {
          case 0 -> apply("erf", u);
          case 1 -> apply("erfc", u);
          case 2 -> apply("gamma", u);
          case 3 -> apply("beta", u, positive);
          case 4 -> apply("normalpdf", u, any, positive);
          case 5 -> apply("tpdf", u, k);
          case 6 -> apply("chi2pdf", u, k);
          case 7 -> apply("fpdf", u, k, k2);
          case 8 -> apply("exppdf", u, positive);
          case 9 -> apply("unifpdf", u, any, Sum.of(any, positive));
          case 10 -> apply("normalcdf", minusInf, u, any, positive);
          case 11 -> apply("tcdf", u, inf, k);
          case 12 -> apply("chi2cdf", Num.ZERO, u, k);
          case 13 -> apply("fcdf", u, positive, k, k2);
          case 14 -> apply("expcdf", Num.ZERO, u, positive);
          default -> apply("unifcdf", u, positive, any, Sum.of(any, positive));
        };
    Compiled compiled = check(call, List.of(X), Settings.DEFAULT, random);
    if (compiled != null) {
      independently(call, compiled, random);
    }
  }

  /**
   * The compiled call against {@code testing.NumericEvaluator}, which computes the statistics
   * functions through Hipparchus directly, not through the engine's {@code DistributionMath} that
   * both the compiled evaluator and the engine's {@code numeric} call. Where the engine's value of
   * the call is still a call of a statistics function, each argument is compiled on its own and the
   * reference evaluates the function at the arguments' compiled values, so that an ill-conditioned
   * argument ({@code gamma} near a pole) is no difference; where both are finite they agree within
   * {@code 1e-9} relative or {@code 1e-12} absolute.
   */
  private static void independently(Expr call, Compiled compiled, Random random) {
    Expr value =
        Evaluator.evaluate(call, Environment.EMPTY.withSettings(Settings.DEFAULT), false).value();
    if (!(value instanceof Apply a && a.head().equals(((Apply) call).head()))) {
      Statistics.label("independent").collect("rewritten");
      return;
    }
    List<Compiled> args = new ArrayList<>();
    try {
      for (Expr arg : a.args()) {
        args.add(infinity(arg) != 0 ? null : Compiled.compile(arg, List.of(X), Settings.DEFAULT));
      }
    } catch (UnsupportedException | EvaluationException noRealArgument) {
      // a constant argument with no real value (asec(1 − π/4)): the call has none to compare
      Statistics.label("independent").collect("not real");
      return;
    }
    for (int k = 0; k < 8; k++) {
      double x = point(random, AngleMode.RADIANS);
      double c = compiled.eval(x);
      List<Expr> at = new ArrayList<>();
      for (int j = 0; j < args.size(); j++) {
        double v = args.get(j) == null ? infinity(a.args().get(j)) : args.get(j).eval(x);
        at.add(
            Double.isInfinite(v)
                ? (v > 0 ? Constant.INFINITY : Product.of(Num.MINUS_ONE, Constant.INFINITY))
                : Double.isNaN(v) ? null : Num.of(Real.of(new BigDecimal(v))));
      }
      if (at.contains(null)) {
        Statistics.label("independent").collect("not compared");
        continue;
      }
      Optional<Numeric> reference =
          NumericEvaluator.eval(new Apply(a.head(), at), Map.of(), MathContext.DECIMAL128);
      if (!Double.isFinite(c)
          || reference.isEmpty()
          || !(reference.get() instanceof RealNumeric r)
          || !Double.isFinite(r.toDouble())) {
        Statistics.label("independent").collect("not compared");
        continue;
      }
      double want = r.toDouble();
      Statistics.label("independent").collect("compared");
      assertThat(Math.abs(c - want))
          .as("%s at x = %s: compiled %s, Hipparchus %s", value, x, c, want)
          .isLessThanOrEqualTo(1e-9 * Math.abs(want) + 1e-12);
    }
  }

  /** {@code ±∞} for an infinite bound, 0 otherwise. */
  private static double infinity(Expr e) {
    return e.equals(Constant.INFINITY)
        ? Double.POSITIVE_INFINITY
        : e.equals(Product.of(Num.MINUS_ONE, Constant.INFINITY)) ? Double.NEGATIVE_INFINITY : 0;
  }

  private static Expr apply(String name, Expr... args) {
    return Apply.of(Symbol.of(name), args);
  }

  /** Checks a random expression at random points; the compiled evaluator, or null if none. */
  private static Compiled check(Expr raw, List<Symbol> args, Settings settings, Random random) {
    Environment env = Environment.EMPTY.withSettings(settings);
    Expr value;
    try {
      value = Evaluator.evaluate(raw, env, false).value();
    } catch (EvaluationException refused) {
      Statistics.label("compiled").collect("not evaluated");
      return null;
    }
    if (settings.angleMode() != AngleMode.RADIANS && reconverts(value)) {
      // Evaluating the value again in the mode would convert a plain constant argument twice
      // (docs/language.md §14.2): such a value is no test of the compiled one.
      Statistics.label("compiled").collect("converts again");
      return null;
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
      return null;
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
        case QUARTER_TURN, NON_REAL_PATH -> {}
      }
    }
    Recheck.compared(compared);
    comparedPoints += compared;
    return compiled;
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
