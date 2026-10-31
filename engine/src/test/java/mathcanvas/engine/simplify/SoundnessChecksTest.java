package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.canonical;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static mathcanvas.engine.simplify.Rules.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * The skip policy of the soundness checks: a point whose input is noise magnified around a true
 * zero is skipped, an ill-conditioned point is skipped and counted against the cap, and a wrong
 * rule still fails. The cases that collect statistics run as jqwik examples or properties.
 */
class SoundnessChecksTest {

  /**
   * {@code log(log e, …)} is exactly 0, computed as noise near 10^-34; its fourth root near 10^-9.
   */
  private static final String ROOT_OF_NOISE = "root(log(log(e), (2^b)^(1/3)), 4)";

  @Test
  void theSimplificationIsZero() {
    assertThat(Simplifier.simplify(parse(ROOT_OF_NOISE), Context.DEFAULT).expr())
        .isEqualTo(canonical("0"));
  }

  @Test
  void theRecheckEvaluationRefusesARootOfAZeroWithinRounding() {
    // At b = -2 the noise survives 100 digits: the root is near 10^-25, where the answer is 0.
    Expr e = canonical(ROOT_OF_NOISE);
    Map<Symbol, Numeric> point = Map.of(Symbol.of("b"), Int.of(-2));
    assertThat(NumericEvaluator.eval(e, point, new MathContext(100))).isPresent();
    assertThat(NumericEvaluator.evalAwayFromCuts(e, point, new MathContext(100))).isEmpty();
  }

  @Property(tries = 50)
  void aCorrectSimplificationOfARootOfNoiseIsNotAFailure(@ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), parse(ROOT_OF_NOISE), Assumptions.EMPTY, random);
  }

  /** A sound rule of the test's own: sin(u)² + cos(u)² = 1. */
  private static final RuleSet UNIT =
      new RuleSet(
          "unit",
          List.of(
              Rule.of(
                  "pythagorean",
                  Pattern.parse("sin(u_)^2 + cos(u_)^2 + rest___"),
                  template("1 + rest___"))));

  @Example
  void anIllConditionedPointIsSkippedAndCounted() {
    // sin(u)² + cos(u)² - 1 is rounding noise, at 34 digits non-zero for u = 2 and at 100 digits
    // for u = 3; times 10^120 neither evaluation has a correct digit, while the rule's 0 is
    // exact.
    SoundnessChecks.begin();
    SoundnessChecks.check(
        List.of(UNIT),
        parse("10^120 (sin(2)^2 + cos(2)^2 - 1) + 10^120 (sin(3)^2 + cos(3)^2 - 1)"),
        Assumptions.EMPTY,
        new Random(1));
    int[] tally = SoundnessChecks.tally();
    assertThat(tally[1]).as("ill-conditioned").isEqualTo(SoundnessChecks.POINTS);
    Recording reporter = new Recording();
    assertThatThrownBy(() -> SoundnessChecks.end(reporter)).isInstanceOf(AssertionError.class);
    assertThat(reporter.values)
        .containsEntry("ill-conditioned points skipped", "6 of 0 compared (no points compared)");
  }

  @Example
  void aWrongRuleAtAWellConditionedPointStillFails() {
    RuleSet wrong =
        new RuleSet(
            "wrong",
            List.of(Rule.of("sine-is-cosine", Pattern.parse("sin(u_)"), template("cos(u_)"))));
    SoundnessChecks.begin();
    assertThatThrownBy(
            () ->
                SoundnessChecks.check(
                    List.of(wrong), parse("sin(x)"), Assumptions.EMPTY, new Random(1)))
        .isInstanceOf(AssertionError.class);
  }

  @Example
  void aSoundRuleCountsItsPointsAndPassesTheCap() {
    SoundnessChecks.begin();
    SoundnessChecks.check(
        List.of(UNIT), parse("sin(x)^2 + cos(x)^2"), Assumptions.EMPTY, new Random(1));
    assertThat(SoundnessChecks.tally()).containsExactly(SoundnessChecks.POINTS, 0);
    Recording reporter = new Recording();
    SoundnessChecks.end(reporter);
    assertThat(reporter.values)
        .containsEntry("ill-conditioned points skipped", "0 of 6 compared (0.0000%)");
  }

  /**
   * {@code (2u)^n → 2^n·u^n} is sound, but at {@code a = 0}, {@code x = -16} the input is {@code
   * 0^65536 = 0} while the rewrite holds {@code 2^65536}, past the numeric evaluator's bounds: no
   * evidence that the rewrite is undefined, so the point is skipped and counted (CI met it on
   * Windows).
   */
  @Example
  void aRewriteBeyondTheEvaluatorsBoundsIsSkippedAndCounted() {
    Expr e = parse("(2 a b y)^((1/2)^x)");
    assertThat(Simplifier.simplify(e, Context.DEFAULT).expr())
        .isEqualTo(canonical("2^((1/2)^x) (a b y)^((1/2)^x)"));
    SoundnessChecks.begin();
    SoundnessChecks.check(
        Simplifier.standard(),
        e,
        Assumptions.EMPTY,
        List.of(
            Map.of(
                Symbol.of("a"), Int.ZERO,
                Symbol.of("b"), Rational.of(31, 3),
                Symbol.of("x"), Int.of(-16),
                Symbol.of("y"), Int.of(15))));
    assertThat(SoundnessChecks.tally()).containsExactly(0, 1);
  }

  /**
   * A rational exponent past the bounds signals them on the inexact pass too: at {@code a = 41/8},
   * {@code y = 19} the exponent {@code a^(1+y)} is near {@code 1.4e14}, so {@code (16b)^(a^(1+y))}
   * at {@code b = 0} is {@code 0}, while its rewrite holds {@code 16^(a^(1+y))}, past the bounds
   * exactly and as {@code 16^(5.125^20)} (the basic set's property met it). At {@code b = 1/2}
   * neither side has a value, and nothing is checked.
   */
  @Example
  void aRationalExponentBeyondTheBoundsIsSkippedAndCounted() {
    Expr e = parse("(16 b)^(a^(1 + y))");
    assertThat(Simplifier.simplify(e, Context.DEFAULT).expr())
        .isEqualTo(canonical("16^(a^(1 + y)) b^(a^(1 + y))"));
    for (Numeric b : List.<Numeric>of(Int.ZERO, Rational.of(1, 2))) {
      SoundnessChecks.begin();
      SoundnessChecks.check(
          Simplifier.standard(),
          e,
          Assumptions.EMPTY,
          List.of(
              Map.of(
                  Symbol.of("a"), Rational.of(41, 8),
                  Symbol.of("b"), b,
                  Symbol.of("x"), Int.of(7),
                  Symbol.of("y"), Int.of(19))));
      assertThat(SoundnessChecks.tally()).containsExactly(0, b.isZero() ? 1 : 0);
    }
  }

  /** A rewrite that is undefined where its input is defined, not beyond a bound, still fails. */
  @Example
  void aRewriteUndefinedWhereTheInputIsDefinedStillFails() {
    RuleSet wrong =
        new RuleSet(
            "wrong", List.of(Rule.of("pole", Pattern.parse("abs(x)"), template("x + 1/(x - 1)"))));
    SoundnessChecks.begin();
    assertThatThrownBy(
            () ->
                SoundnessChecks.check(
                    List.of(wrong),
                    parse("abs(x)"),
                    Assumptions.EMPTY,
                    List.of(Map.of(Symbol.of("x"), Int.ONE))))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("is defined at");
  }

  /**
   * A pole beside a factor past the evaluator's bounds, or as a large negative power of a zero,
   * still fails, whatever the order of the operands: an undefined operand makes its node undefined,
   * and a zero base of either kind is never past a bound. The standard list expands {@code (2ay)^n}
   * to {@code 2^n·(ay)^n} while a wrong rule turns {@code abs(w)} into a pole.
   */
  @Example
  void aPoleBesideOrUnderABoundStillFails() {
    Map<Symbol, Numeric> huge =
        Map.of(
            Symbol.of("a"),
            Int.ZERO,
            Symbol.of("x"),
            Int.of(-16),
            Symbol.of("y"),
            Int.of(15),
            Symbol.of("w"),
            Int.ONE);
    assertUndefinedRewriteFails("(2 a y)^((1/2)^x) + abs(w)", "abs(w)", "tan(w pi/2)", huge);
    assertUndefinedRewriteFails("(2 a y)^((1/2)^x) abs(w)", "abs(w)", "w/(y - 15)", huge);
    Map<Symbol, Numeric> one = Map.of(Symbol.of("w"), Int.ONE);
    assertUndefinedRewriteFails("abs(w)", "abs(w)", "(w - 1)^(-20000) + w", one);
    assertUndefinedRewriteFails("abs(w)", "abs(w)", "(w - 1)^(-65536) + w", one);
  }

  /**
   * Simplifies {@code e} with the standard list and a wrong rule {@code pattern → replacement} at
   * {@code point}, where {@code e} has an exact value and the rewrite is undefined, and asserts the
   * definedness failure.
   */
  private static void assertUndefinedRewriteFails(
      String e, String pattern, String replacement, Map<Symbol, Numeric> point) {
    RuleSet wrong =
        new RuleSet(
            "wrong", List.of(Rule.of("undefined", Pattern.parse(pattern), template(replacement))));
    List<RuleSet> sets = new ArrayList<>(Simplifier.standard());
    sets.add(wrong);
    SoundnessChecks.begin();
    assertThatThrownBy(
            () -> SoundnessChecks.check(sets, parse(e), Assumptions.EMPTY, List.of(point)))
        .as("%s with %s → %s", e, pattern, replacement)
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("is defined at");
  }

  /**
   * {@code (c·u)^n} with a positive constant {@code c} and a large integer power {@code n} never
   * reports a definedness mismatch at a zero of {@code u}, where the input is 0 and the rewrite's
   * {@code c^n} passes the evaluator's bounds.
   */
  @Property(tries = 200)
  void aLargePowerOfAPositiveConstantTimesZeroIsNoMismatch(
      @ForAll("constants") String c,
      @ForAll("powers") String n,
      @ForAll @IntRange(min = 10, max = 17) int x,
      @ForAll @IntRange(min = -20, max = 20) int y) {
    SoundnessChecks.begin();
    SoundnessChecks.check(
        Simplifier.standard(),
        parse("(" + c + " a y)^(" + n + ")"),
        Assumptions.EMPTY,
        List.of(
            Map.of(
                Symbol.of("a"), Int.ZERO, Symbol.of("x"), Int.of(x), Symbol.of("y"), Int.of(y))));
  }

  /**
   * {@code (c·u)^(p/q)^(1+y)}, a large rational exponent, at {@code u} = 0, 1/2, 1 or 2: the
   * rewrite {@code c^…·u^…} compares, or is skipped where a side passes the bounds, but never
   * reports a definedness mismatch.
   */
  @Property(tries = 300)
  void aLargeRationalPowerOfAPositiveConstantTimesUIsNoMismatch(
      @ForAll("constants") String c,
      @ForAll @IntRange(min = 9, max = 60) int p,
      @ForAll @IntRange(min = 2, max = 8) int q,
      @ForAll @IntRange(min = 5, max = 25) int y,
      @ForAll("bases") Numeric u) {
    Assume.that(p > q);
    SoundnessChecks.begin();
    SoundnessChecks.check(
        Simplifier.standard(),
        parse("(" + c + " b)^(a^(1 + y))"),
        Assumptions.EMPTY,
        List.of(
            Map.of(
                Symbol.of("a"), Rational.of(p, q), Symbol.of("b"), u, Symbol.of("y"), Int.of(y))));
  }

  @Provide
  Arbitrary<Numeric> bases() {
    return Arbitraries.of(Int.ZERO, Rational.of(1, 2), Int.ONE, Int.TWO);
  }

  @Provide
  Arbitrary<String> constants() {
    return Arbitraries.of("2", "3", "5", "7/2", "10", "1/3");
  }

  /**
   * Exponents that are a large integer at the point: {@code 2^x}, {@code 3^x}, {@code (1/2)^-x}.
   */
  @Provide
  Arbitrary<String> powers() {
    return Arbitraries.of("2^x", "3^x", "(1/2)^(-x)");
  }

  /** A reporter that keeps the values published to it. */
  private static final class Recording implements Reporter {
    final Map<String, String> values = new TreeMap<>();

    @Override
    public void publishValue(String key, String value) {
      values.put(key, value);
    }

    @Override
    public void publishReport(String key, Object object) {
      values.put(key, String.valueOf(object));
    }

    @Override
    public void publishReports(String key, Map<String, Object> objects) {
      values.put(key, String.valueOf(objects));
    }
  }
}
