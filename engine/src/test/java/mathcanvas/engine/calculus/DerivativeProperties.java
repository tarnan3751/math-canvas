package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffArbitraries.A;
import static mathcanvas.engine.calculus.DiffArbitraries.X;
import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Relation.Op;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.Contexts;
import mathcanvas.engine.testing.RandomAssumptions;
import mathcanvas.engine.testing.Soundness;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Derivative soundness: the symbolic derivative agrees with the numeric difference quotient ({@link
 * DifferenceQuotient}) at points within random assumptions, for generated expressions, for every
 * function of the table, and for second derivatives; and the rule set terminates. A point is
 * compared only within the domain of the function differentiated ({@link
 * DifferenceQuotient#defined}).
 */
class DerivativeProperties {

  /** The points compared per expression. */
  private static final int POINTS = 3;

  /**
   * The most rewrites one generated derivative may take: about five times the most measured (under
   * 200).
   */
  static final int MAX_REWRITES = 1_000;

  /** The least share of tries, in percent, that must compare at least one point. */
  private static final double COMPARED = 70;

  private static final Context CONTEXT = Contexts.withRewriteCap(Context.DEFAULT, 100_000);

  /** The inner functions of the table property, each smooth on the reals. */
  private static final List<Expr> INNER =
      List.of(parse("x"), parse("2x + 1/3"), parse("x^2/4"), parse("-x/3"), parse("3x - 1/2"));

  @Provide
  Arbitrary<Expr> exprs() {
    return DiffArbitraries.exprs();
  }

  /**
   * Facts about {@code x} (none, a sign or the complex domain) and random facts about {@code a}.
   */
  @Provide
  Arbitrary<Assumptions> assumptions() {
    return Arbitraries.randomValue(
        random -> {
          Assumptions a = RandomAssumptions.draw(List.of(A), random);
          Relation r =
              switch (random.nextInt(10)) {
                case 0 -> Relation.of(Op.GT, X, Num.ZERO);
                case 1 -> Relation.of(Op.GE, X, Num.ZERO);
                case 2 -> Relation.of(Op.LT, X, Num.ZERO);
                case 3 -> Relation.of(Op.LE, X, Num.ZERO);
                case 4 -> Relation.of(Op.IN, X, mathcanvas.engine.expr.Constant.COMPLEXES);
                default -> null;
              };
          return r == null ? a : a.with(r);
        });
  }

  private static void label(String name, List<DifferenceQuotient.Outcome> outcomes) {
    for (DifferenceQuotient.Outcome o : outcomes) {
      Statistics.label(name).collect(o);
    }
    Statistics.label("compared")
        .collect(
            outcomes.contains(DifferenceQuotient.Outcome.COMPARED)
                || outcomes.contains(DifferenceQuotient.Outcome.RECHECKED));
    Statistics.label("compared").coverage(c -> c.check(true).percentage(p -> p >= COMPARED));
  }

  /** Checks {@code derivative} against the quotient of {@code f}, at points where {@code e} is. */
  private static List<DifferenceQuotient.Outcome> check(
      Expr e, Expr f, Expr derivative, Assumptions a, Random random) {
    List<DifferenceQuotient.Outcome> outcomes = new ArrayList<>();
    for (Map<Symbol, Numeric> point : Soundness.points(List.of(X, A), a, POINTS, random)) {
      // The checker's precondition: no exponent beyond its bound at the point.
      if (!DifferenceQuotient.exponentsBounded(f, point)) {
        Statistics.label("exponent out of range").collect(true);
        continue;
      }
      if (!DifferenceQuotient.defined(e, point)) {
        Statistics.label("outside the domain").collect(true);
        continue;
      }
      outcomes.add(DifferenceQuotient.check(f, derivative, X, point, a.facts(X)));
    }
    return outcomes;
  }

  @Property(tries = 5000)
  void theDerivativeAgreesWithTheDifferenceQuotient(
      @ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    Expr d = Calculus.differentiate(e, X, 1, CONTEXT.withAssumptions(a)).expr();
    label("points", check(e, e, d, a, random));
  }

  @Property(tries = 1000)
  void theSecondDerivativeIsTheDerivativeOfTheFirst(
      @ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    Context ctx = CONTEXT.withAssumptions(a);
    Expr first = Calculus.differentiate(e, X, 1, ctx).expr();
    Expr second = Calculus.differentiate(e, X, 2, ctx).expr();
    label("points", check(e, first, second, a, random));
  }

  @Property(tries = 3000)
  void everyFunctionOfTheTable(@ForAll Random random) {
    String f = DiffArbitraries.FUNCTIONS.get(random.nextInt(DiffArbitraries.FUNCTIONS.size()));
    Expr e = Apply.of(Symbol.of(f), INNER.get(random.nextInt(INNER.size())));
    Expr d = Calculus.differentiate(e, X, 1, Context.DEFAULT).expr();
    assertThat(d).as("d/dx %s", e).matches(r -> !(r instanceof Derivative));
    Map<Symbol, Numeric> point =
        Map.of(X, Rational.of(random.nextInt(1201) - 600, 1 + random.nextInt(200)));
    boolean compared = false;
    if (DifferenceQuotient.defined(e, point)) {
      DifferenceQuotient.Outcome o =
          DifferenceQuotient.check(e, d, X, point, Assumptions.EMPTY.facts(X));
      compared =
          o == DifferenceQuotient.Outcome.COMPARED || o == DifferenceQuotient.Outcome.RECHECKED;
    }
    Statistics.label(f).collect(compared);
    Statistics.label(f).coverage(c -> c.check(true).percentage(p -> p >= 80));
  }

  @Property(tries = 200)
  void theRuleSetTerminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    List<Expr> sample = new ArrayList<>();
    Arbitrary<Expr> exprs = exprs();
    for (int i = 0; i < 20; i++) {
      Expr e = exprs.generator(1000).next(random).value();
      sample.add(Derivative.of(e, X, 1 + random.nextInt(2)));
    }
    TerminationProbe.Report report =
        TerminationProbe.run(RuleSets.DIFF, sample, CONTEXT.withAssumptions(a));
    assertThat(report.maxRewrites()).as("rewrites of %s", report.worst()).isLessThan(MAX_REWRITES);
    Statistics.label("most rewrites").collect(report.maxRewrites());
  }
}
