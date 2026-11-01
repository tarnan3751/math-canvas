package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeSet;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.testing.AdaptiveSimpson;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * The rational and root techniques of integration against references: over proper rational
 * functions whose denominators are products of distinct and repeated linear factors and irreducible
 * quadratics, each integrand is integrated whole, {@code d/dx ∫ f dx − f} cancels to 0 and the
 * difference quotient of the antiderivative agrees with it at points away from the poles, and a
 * definite integral between the poles agrees with adaptive Simpson quadrature; over the three root
 * forms {@code √(a² − u²)}, {@code √(u² + a²)} and {@code √(u² − a²)} (the last with the side as a
 * fact), times a power of {@code u} or as a reciprocal, the antiderivative's difference quotient
 * agrees with the integrand; and the rule set terminates on both.
 */
class RationalIntegrationProperties {

  private static final Symbol X = Symbol.of("x");

  private static final Context CONTEXT =
      Context.DEFAULT.withNumerics((e, mc) -> NumericEvaluator.eval(e, Map.of(), mc));

  private static final MathContext DIGITS = new MathContext(34);

  private static Expr parse(String text) {
    return IntegrateCases.parse(text);
  }

  /** A proper rational function and the real roots of its denominator, in eighths. */
  private record RationalCase(Expr f, TreeSet<Integer> poles, String text) {}

  private static final int[] ROOTS = {-16, -8, -4, 0, 4, 8, 12, 16};

  private static RationalCase rational(Random r) {
    List<String> factors = new ArrayList<>();
    TreeSet<Integer> poles = new TreeSet<>();
    int degree = 0;
    int linear = 1 + r.nextInt(2);
    while (poles.size() < linear) {
      poles.add(ROOTS[r.nextInt(ROOTS.length)]);
    }
    for (int p : poles) {
      int k = 1 + r.nextInt(3);
      factors.add("(x - " + p + "/8)^" + k);
      degree += k;
    }
    if (r.nextBoolean()) {
      // x² + bx + c with b² < 4c: irreducible over the reals
      int b = r.nextInt(5) - 2;
      int c = b * b / 4 + 1 + r.nextInt(4);
      int k = 1 + r.nextInt(2);
      factors.add("(x^2 + " + b + "x + " + c + ")^" + k);
      degree += 2 * k;
    }
    StringBuilder num = new StringBuilder();
    int top = r.nextInt(degree);
    for (int k = 0; k <= top; k++) {
      num.append(k == 0 ? "" : " + ").append(r.nextInt(7) - 3).append(" x^").append(k);
    }
    String text = "(" + num + ")/(" + String.join(" ", factors) + ")";
    return new RationalCase(parse(text), poles, text);
  }

  /** A rational point at 1/64 steps in {@code (lo, hi)}. */
  private static RealNumeric point(double lo, double hi, Random random) {
    long a = (long) Math.ceil(lo * 64) + 1;
    long b = (long) Math.floor(hi * 64) - 1;
    return Rational.of(a + (long) random.nextInt((int) (b - a + 1)), 64);
  }

  /** An interval between two consecutive poles, or beyond the last, at least 1/2 long. */
  private static double[] gap(TreeSet<Integer> poles, Random random) {
    List<Double> ends = new ArrayList<>();
    for (int p : poles) {
      ends.add(p / 8.0);
    }
    int k = random.nextInt(ends.size() + 1);
    double lo = k == 0 ? ends.getFirst() - 3 : ends.get(k - 1) + 0.1;
    double hi = k == ends.size() ? ends.getLast() + 3 : ends.get(k) - 0.1;
    return new double[] {lo, hi};
  }

  @Property(tries = 150, seed = "4211")
  void theDerivativeOfARationalAntiderivativeIsTheIntegrand(@ForAll Random random) {
    RationalCase c = rational(random);
    Expr F = Calculus.integrate(c.f(), X, null, null, CONTEXT).expr();
    assertThat(IntegrateRules.complete(F, X)).as("∫ %s = %s", c.text(), F).isTrue();
    // d/dx log|u| = sign(u) u′/|u| = u′/u: the absolute values and signs go
    Expr d = withoutSigns(Calculus.differentiate(F, X, 1, CONTEXT).expr());
    Expr difference = Canonical.of(Sum.of(d, Product.of(Num.MINUS_ONE, c.f())));
    Expr cancelled = Algebra.cancel(Simplifier.simplify(difference, CONTEXT).expr(), CONTEXT);
    assertThat(cancelled).as("d/dx ∫ %s − f, with ∫ = %s", c.text(), F).isEqualTo(Num.ZERO);
    double[] g = gap(c.poles(), random);
    if (g[1] - g[0] < 0.1) {
      return;
    }
    Map<Symbol, Numeric> at = Map.of(X, point(g[0], g[1], random));
    DifferenceQuotient.Outcome o = DifferenceQuotient.check(F, c.f(), X, at, Facts.NONE);
    Statistics.label("compared").collect(o);
  }

  /** {@code e} with {@code |w|} as {@code w} and {@code sign(w)} as 1. */
  private static Expr withoutSigns(Expr e) {
    Expr out = e;
    for (String name : List.of("abs", "sign")) {
      out =
          mathcanvas.engine.rule.Rewriter.rewrite(
                  out,
                  new mathcanvas.engine.rule.RuleSet(
                      "test",
                      List.of(
                          mathcanvas.engine.rule.Rule.computed(
                              name,
                              mathcanvas.engine.match.Pattern.parse(name + "(w_)"),
                              m ->
                                  Optional.of(
                                      name.equals("abs") ? m.bindings().get("w_") : Num.ONE)))),
                  CONTEXT.withTrace(false))
              .expr();
    }
    return out;
  }

  @Property(tries = 150, seed = "4212")
  void aDefiniteRationalIntegralAgreesWithQuadrature(@ForAll Random random) {
    RationalCase c = rational(random);
    double[] g = gap(c.poles(), random);
    if (g[1] - g[0] < 0.2) {
      return;
    }
    RealNumeric a = point(g[0], g[1], random);
    RealNumeric b = point(g[0], g[1], random);
    if (a.equals(b)) {
      return;
    }
    Expr value = Calculus.integrate(c.f(), X, Num.of(a), Num.of(b), CONTEXT).expr();
    boolean evaluated = !(value instanceof Integral);
    Statistics.label("evaluated").collect(evaluated);
    Statistics.label("evaluated").coverage(k -> k.check(true).percentage(p -> p >= 95));
    if (!evaluated) {
      return;
    }
    Optional<Numeric> exact = NumericEvaluator.eval(value, Map.of(), DIGITS);
    assertThat(exact).as("the value %s of ∫ %s over [%s, %s]", value, c.text(), a, b).isPresent();
    Compiled f = Compiled.compile(c.f(), List.of(X), Settings.DEFAULT);
    AdaptiveSimpson.Estimate quadrature =
        AdaptiveSimpson.integrate(f::eval, a.toDouble(), b.toDouble(), 1e-12);
    double v = decimal((RealNumeric) exact.get()).doubleValue();
    assertThat(Math.abs(v - quadrature.value()))
        .as("∫ %s over [%s, %s] = %s against %s", c.text(), a, b, value, quadrature)
        .isLessThanOrEqualTo(1e-10 * Math.max(Math.abs(v), quadrature.magnitude()));
  }

  private static BigDecimal decimal(RealNumeric r) {
    return switch (r) {
      case mathcanvas.engine.number.Int i -> new BigDecimal(i.value());
      case Rational q -> new BigDecimal(q.num()).divide(new BigDecimal(q.den()), DIGITS);
      case Real d -> d.value();
    };
  }

  /** A root form, its interval and the facts it needs. */
  private record Root(Expr f, double lo, double hi, Context context, String text) {}

  private static final String[] SQUARES = {"1", "4", "9", "1/4"};
  private static final double[] SIDES = {1, 2, 3, 0.5};
  private static final String[] SHIFTS = {"0", "1", "-1", "1/2"};

  private static Root root(Random r) {
    int k = r.nextInt(SQUARES.length);
    String a2 = SQUARES[k];
    double a = SIDES[k];
    String h = SHIFTS[r.nextInt(SHIFTS.length)];
    double hv = Double.parseDouble(h.contains("/") ? "0.5" : h);
    String u = "(x + " + h + ")";
    String power = u + "^" + r.nextInt(3);
    return switch (r.nextInt(3)) {
      case 0 -> {
        String q = a2 + " - " + u + "^2";
        String f = r.nextBoolean() ? power + " sqrt(" + q + ")" : power + "/sqrt(" + q + ")";
        yield new Root(parse(f), -a - hv, a - hv, CONTEXT, f);
      }
      case 1 -> {
        String q = u + "^2 + " + a2;
        String f =
            switch (r.nextInt(3)) {
              case 0 -> power + " sqrt(" + q + ")";
              case 1 -> "1/(" + q + ")^(3/2)";
              default -> power + "/(" + q + ")^(3/2)";
            };
        yield new Root(parse(f), -3, 3, CONTEXT, f);
      }
      default -> {
        String q = u + "^2 - " + a2;
        String f = r.nextBoolean() ? "sqrt(" + q + ")" : "sqrt(" + q + ")/" + u;
        double lo = a - hv;
        Context side =
            CONTEXT.withAssumptions(
                CONTEXT
                    .assumptions()
                    .with(
                        Relation.of(
                            Relation.Op.GT, X, Num.of(Rational.of(Math.round(lo * 2), 2)))));
        yield new Root(parse(f), lo, lo + 3, side, f + " for x > " + lo);
      }
    };
  }

  @Property(tries = 120, seed = "4213")
  void theDerivativeOfARootAntiderivativeIsTheIntegrand(@ForAll Random random) {
    Root c = root(random);
    Expr F = Calculus.integrate(c.f(), X, null, null, c.context()).expr();
    boolean whole = IntegrateRules.complete(F, X);
    Statistics.label("integrated").collect(whole);
    Statistics.label("integrated").coverage(k -> k.check(true).percentage(p -> p >= 90));
    if (!whole) {
      return;
    }
    int compared = 0;
    for (int k = 0; k < 3; k++) {
      Map<Symbol, Numeric> at = Map.of(X, point(c.lo(), c.hi(), random));
      if (!DifferenceQuotient.exponentsBounded(F, at) || !DifferenceQuotient.defined(c.f(), at)) {
        continue;
      }
      // a disagreement at a smooth point throws
      DifferenceQuotient.Outcome o = DifferenceQuotient.check(F, c.f(), X, at, Facts.NONE);
      if (o == DifferenceQuotient.Outcome.COMPARED || o == DifferenceQuotient.Outcome.RECHECKED) {
        compared++;
      }
    }
    Statistics.label("compared").collect(compared > 0);
    Statistics.label("compared").coverage(k -> k.check(true).percentage(p -> p >= 90));
  }

  @Property(tries = 60, seed = "4214")
  void theRationalAndRootRulesTerminate(@ForAll Random random) {
    List<Expr> sample = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      sample.add(Integral.indefinite(rational(random).f(), X));
      sample.add(Integral.indefinite(root(random).f(), X));
    }
    try {
      TerminationProbe.run(RuleSets.INTEGRATE, sample, CONTEXT);
    } catch (RewriteLimitException e) {
      throw new AssertionError("the cap was reached on " + sample, e);
    }
  }
}
