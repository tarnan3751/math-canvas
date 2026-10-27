package mathcanvas.engine.simplify;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.RandomAssumptions;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * The radical rule set alone: sound and defined at points within random assumptions, and
 * terminating.
 */
class RadicalProperties {

  private static final List<RuleSet> SETS = List.of(RuleSets.RADICAL);

  @BeforeProperty
  void begin() {
    SoundnessChecks.begin();
  }

  @AfterProperty
  void end(Reporter reporter) {
    SoundnessChecks.end(reporter);
  }

  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.elementaryExprs();
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 5000)
  void sound(@ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(SETS, e, a, random);
  }

  @Property(tries = 200)
  void terminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(SETS, exprs(), a, random, 20);
  }

  /**
   * A sum radicand with content, {@code (k·(c₁x + c₂y + c₀))^(±1/n)} written out as a sum, where
   * {@code k = d^n·r}: the radical set is compared with the input at random points and at points on
   * the radicand's zero and on either side of it, where the radicand is negative and the value
   * complex.
   */
  @Property(tries = 2000)
  void soundOnSumRadicands(
      @ForAll @IntRange(min = 2, max = 5) int n,
      @ForAll @IntRange(min = 1, max = 3) int d,
      @ForAll @IntRange(min = 1, max = 3) int dd,
      @ForAll @IntRange(min = 1, max = 6) int r,
      @ForAll @IntRange(min = -5, max = 5) int c0,
      @ForAll("nonZero") int c1,
      @ForAll @IntRange(min = -5, max = 5) int c2,
      @ForAll boolean reciprocal,
      @ForAll Random random) {
    RealNumeric k =
        (RealNumeric)
            Rational.of(BigInteger.valueOf(d).pow(n), BigInteger.valueOf(dd).pow(n))
                .multiply(Int.of(r), MathContext.UNLIMITED);
    Symbol x = Symbol.of("x");
    Symbol y = Symbol.of("y");
    Expr radicand =
        Sum.of(
            Product.of(Num.of(times(k, c1)), x),
            Product.of(Num.of(times(k, c2)), y),
            Num.of(times(k, c0)));
    Expr e = Power.of(radicand, Num.of(Rational.of(reciprocal ? -1 : 1, n)));
    List<Map<Symbol, Numeric>> points =
        new ArrayList<>(Soundness.points(ExprArbitraries.SYMBOLS, Assumptions.EMPTY, 4, random));
    for (int i = 0; i < 2; i++) {
      RealNumeric yv = Rational.of(random.nextInt(-20, 21), random.nextInt(1, 5));
      // c1·x + c2·y + c0 = 0 at x = -(c2·y + c0)/c1
      RealNumeric zero =
          (RealNumeric)
              times(yv, c2)
                  .add(Int.of(c0), MathContext.UNLIMITED)
                  .negate()
                  .divide(Int.of(c1), MathContext.UNLIMITED);
      for (int side = -1; side <= 1; side++) {
        Map<Symbol, Numeric> point = new LinkedHashMap<>();
        for (Symbol s : ExprArbitraries.SYMBOLS) {
          point.put(s, Int.ONE);
        }
        point.put(x, zero.add(Int.of(side), MathContext.UNLIMITED));
        point.put(y, yv);
        points.add(point);
      }
    }
    SoundnessChecks.check(SETS, e, Assumptions.EMPTY, points);
  }

  @Provide
  Arbitrary<Integer> nonZero() {
    return Arbitraries.integers().between(-5, 5).filter(i -> i != 0);
  }

  /** The standard list over sum radicands with content and the elementary expressions. */
  @Property(tries = 200)
  void theStandardListTerminatesOnSumRadicands(@ForAll Random random) {
    Arbitrary<Expr> radicands =
        Combinators.combine(
                Arbitraries.integers().between(2, 5),
                Arbitraries.integers().between(1, 4),
                ExprArbitraries.elementaryExprs(),
                ExprArbitraries.elementaryExprs())
            .as(
                (n, d, u, v) ->
                    Power.of(
                        Sum.of(
                            Product.of(Num.of(Int.of(BigInteger.valueOf(d).pow(n))), u),
                            Product.of(Num.of(Int.of(BigInteger.valueOf(d).pow(n))), v)),
                        Num.of(Rational.of(1, n))));
    SoundnessChecks.terminates(
        Simplifier.standard(),
        Arbitraries.oneOf(radicands, exprs()),
        Assumptions.EMPTY,
        random,
        20);
  }

  private static RealNumeric times(RealNumeric a, int b) {
    return (RealNumeric) a.multiply(Int.of(b), MathContext.UNLIMITED);
  }
}
