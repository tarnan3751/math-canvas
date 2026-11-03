package mathcanvas.engine.simplify;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.testing.RandomAssumptions;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/**
 * The rules of the Phase 9c debt batch in the standard list, on the shapes they take: a negative
 * number times a sum of negative terms ({@code negate-negative-sum}), an integer multiple of a
 * fraction beside the fraction times a sum of constants ({@code combine-rational-term}), and the
 * logarithm of a perfect power ({@code log-of-perfect-power}), each inside random elementary
 * expressions. Sound at random points within random facts, and terminating; how often each rule
 * fires is reported.
 */
class DebtRulesProperties {

  @BeforeProperty
  void begin() {
    SoundnessChecks.begin();
  }

  @AfterProperty
  void end(Reporter reporter) {
    SoundnessChecks.end(reporter);
  }

  private static Arbitrary<Num> rationals() {
    return Combinators.combine(
            Arbitraries.integers().between(-9, 9), Arbitraries.integers().between(1, 6))
        .as((p, q) -> Num.of(Rational.of(p, q)));
  }

  private static Arbitrary<Num> negatives() {
    return Combinators.combine(
            Arbitraries.integers().between(1, 9), Arbitraries.integers().between(1, 4))
        .as((p, q) -> Num.of(Rational.of(-p, q)));
  }

  /** A negative coefficient times a sum whose every term has one, times a random factor. */
  private static Arbitrary<Expr> negatedSums() {
    Arbitrary<Expr> term =
        Combinators.combine(negatives(), ExprArbitraries.elementaryExprs())
            .as((c, e) -> (Expr) Product.of(c, e));
    return Combinators.combine(
            negatives(),
            term.list().ofMinSize(1).ofMaxSize(3),
            negatives(),
            ExprArbitraries.elementaryExprs())
        .as(
            (c, terms, k, extra) -> {
              List<Expr> all = new ArrayList<>(terms);
              all.add(k);
              return (Expr) Product.of(c, Sum.of(all), extra);
            });
  }

  /**
   * An integer multiple of a fraction plus the fraction times a sum of a number and a square root,
   * plus a random term.
   */
  private static Arbitrary<Expr> rationalTerms() {
    Arbitrary<Num> fractions =
        Combinators.combine(
                Arbitraries.integers().between(1, 7), Arbitraries.integers().between(2, 6))
            .as((p, q) -> Num.of(Rational.of(p, q)))
            .filter(n -> !(n.value() instanceof Int));
    return Combinators.combine(
            Arbitraries.integers().between(-5, 5),
            fractions,
            rationals(),
            Arbitraries.integers().between(2, 30),
            ExprArbitraries.elementaryExprs())
        .as(
            (k, q, d, r, rest) ->
                (Expr)
                    Sum.of(
                        Product.of(Num.of(k), q),
                        Product.of(q, Sum.of(d, Power.of(Num.of(r), Num.of(Rational.of(1, 2))))),
                        rest));
  }

  /** The logarithm of a rational to a power from 2 to 4, beside a random term. */
  private static Arbitrary<Expr> logsOfPowers() {
    return Combinators.combine(
            Arbitraries.integers().between(2, 12),
            Arbitraries.integers().between(1, 5),
            Arbitraries.integers().between(2, 4),
            ExprArbitraries.elementaryExprs())
        .as(
            (p, q, k, rest) ->
                (Expr)
                    Sum.of(
                        Apply.of(Symbol.of("log"), Power.of(Num.of(Rational.of(p, q)), Num.of(k))),
                        rest));
  }

  @Provide
  Arbitrary<Expr> shapes() {
    return Arbitraries.oneOf(negatedSums(), rationalTerms(), logsOfPowers());
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 3000)
  void soundInTheStandardList(
      @ForAll("shapes") Expr e,
      @ForAll("assumptions") Assumptions a,
      @ForAll java.util.Random random) {
    List<String> fired =
        Simplifier.simplify(Canonical.of(e), Context.DEFAULT.withAssumptions(a).withTrace(true))
            .trace()
            .rewrites()
            .stream()
            .map(Rewrite::rule)
            .toList();
    for (String rule :
        List.of("negate-negative-sum", "combine-rational-term", "log-of-perfect-power")) {
      Statistics.label(rule).collect(fired.contains(rule));
    }
    SoundnessChecks.check(Simplifier.standard(), e, a, random);
  }

  @Property(tries = 300)
  void terminatesInTheStandardList(
      @ForAll("assumptions") Assumptions a, @ForAll java.util.Random random) {
    SoundnessChecks.terminates(Simplifier.standard(), shapes(), a, random, 20);
  }
}
