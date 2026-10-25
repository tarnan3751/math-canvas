package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.canonical;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.provider.Gcd;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.TerminationProbe;
import org.junit.jupiter.api.Test;

/** One positive and one negative case for every rule of the rational set, and each decline. */
class RationalRulesTest {

  @Test
  void everyRule() {
    new RuleCases(RuleSets.RATIONAL)
        .fires("cancel-common-factor", "(x^2 - 1)/(x - 1)", "x + 1")
        .skips("cancel-common-factor", "(x^2 + 1)/(x - 1)")
        .fires("cancel-common-content", "2/(2x + 2)", "1/(x + 1)")
        .skips("cancel-common-content", "(2x + 2)/(3x - 3)")
        // no denominator: the set's scope is quotients, as cancel's is
        .skips("cancel-common-content", "3(x/2 + 1)")
        .skips("cancel-common-content", "-(x/2 + 1)")
        .fires("cancel-denominator-content", "1/(x/2 + 1)", "2/(x + 2)")
        .skips("cancel-denominator-content", "1/(2x + 2)")
        .fires("combine-fractions", "1/(x - 1) - 1/(x + 1)", "2/(x^2 - 1)")
        .skips("combine-fractions", "1/x + 1/y")
        .covered();
  }

  @Test
  void theBriefsExamples() {
    new RuleCases(RuleSets.RATIONAL)
        .fires("cancel-common-factor", "x/(x^2 + x)", "1/(x + 1)")
        .fires("cancel-common-factor", "(x^2 + x)/x", "x + 1")
        .fires("cancel-common-factor", "(x^2 - 4)/(x + 2) + 2", "x")
        // Kernels are opaque: log(x) is a variable of the polynomials.
        .fires("cancel-common-factor", "(log(x)^2 - 1)/(log(x) - 1)", "log(x) + 1")
        .fires("cancel-common-factor", "(x + 1)^5/(x^2 - 1)", "(x + 1)^4/(x - 1)")
        .fires(
            "cancel-common-factor",
            "(x^10 - 1)/(x - 1)",
            "x^9 + x^8 + x^7 + x^6 + x^5 + x^4 + x^3 + x^2 + x + 1")
        .fires("cancel-common-factor", "(x^2 - y^2)/(x - y)", "x + y")
        // The numeric content gathers into one coefficient in lowest terms, as cancel gives it.
        .fires("cancel-common-content", "(2x + 2)/(2y + 2)", "(x + 1)/(y + 1)")
        .fires("cancel-common-content", "(x/2 + 1)/3", "(x + 2)/6")
        .fires("cancel-common-content", "(6x + 6)/(4x - 2)", "3(x + 1)/(2x - 1)")
        .fires("cancel-common-content", "(x/3 + 1)/(2x + 2)", "(x + 3)/(6(x + 1))")
        .fires("cancel-denominator-content", "4/(x/2 + 1)", "8/(x + 2)")
        .fires("cancel-common-content", "(x/3 + 1)/y", "(x + 3)/(3y)")
        // the whole content is already 1: no factor is cancelled and nothing grows; the
        // denominator power (x + 1/2)^-1 alone is made primitive
        .skips("cancel-common-factor", "(x - 2)^2/(2(x - 1)(x + 1/2))")
        .skips("cancel-common-content", "(x - 2)^2/(2(x - 1)(x + 1/2))")
        .fires(
            "cancel-denominator-content",
            "(x - 2)^2/(2(x - 1)(x + 1/2))",
            "(x - 2)^2/((x - 1)(2x + 1))")
        .fires("cancel-common-content", "(x + 1)/((y/2 + 1)(2z + 2))", "(x + 1)/((y + 2)(z + 1))")
        // a numerator that is zero though not written as 0 cancels the denominator whole
        .fires("cancel-common-factor", "((2b - 9/4) - (2b - 9/4))/(2(x^2 + 1))", "0")
        // with coprime bases the smaller of the factored and the expanded form is taken: expanded
        // for 1/(x - 1) - 1/(x + 1) and here, where (√x - 1)(√x + 1) canonicalises to x - 1;
        // factored for the second
        .fires("combine-fractions", "1/(sqrt(x) - 1) + 1/(sqrt(x) + 1)", "2 sqrt(x)/(x - 1)")
        .fires("combine-fractions", "1/(x + 1)^2 - 1/(x + 1)", "-x/(x + 1)^2")
        .fires("combine-fractions", "1/x + 1/x^2", "(x + 1)/x^2")
        .fires("combine-fractions", "1/(x - 1) + 1/(1 - x)", "0")
        .skips("cancel-common-factor", "1/(1 - x)")
        .skips("combine-fractions", "x + 1/(x - 1)");
  }

  @Test
  void declines() {
    new RuleCases(RuleSets.RATIONAL)
        // the gcd is 1
        .skips("cancel-common-factor", "(x^2 - 2)/(x - sqrt(2))")
        .skips("cancel-common-factor", "sin(x)/cos(x)")
        // a refused kernel
        .skips("cancel-common-factor", "(x^2 - 1)/(x = 1)")
        .skips("cancel-common-factor", "(x - ∞)/(x^2 - ∞)")
        // past the degree bound: the algebra's limit makes the rule decline
        .skips("cancel-common-factor", "(x^20000 - 1)/(x - 1)")
        // the size condition, and all-or-nothing combining
        .skips("combine-fractions", "1/(x - 1) - 1/(x + 1) + 1/y");
    // the size measure counts a numeral's digits: twenty pairs with 20-digit coefficients decline
    StringBuilder pairs = new StringBuilder("0");
    for (int k = 1; k <= 20; k++) {
      String c = "1234567890123456789" + k;
      pairs
          .append(" + ")
          .append(c)
          .append("/(x - ")
          .append(k)
          .append(") - ")
          .append(c)
          .append("/(x + ")
          .append(k)
          .append(")");
    }
    new RuleCases(RuleSets.RATIONAL).skips("combine-fractions", pairs.toString());
  }

  @Test
  void theGcdComesFromTheProvider() {
    AtomicInteger calls = new AtomicInteger();
    Provider<Gcd, Polynomial> counting =
        new Provider<>() {
          @Override
          public String name() {
            return "counting";
          }

          @Override
          public java.util.Optional<Polynomial> apply(
              Gcd input, Context ctx, mathcanvas.engine.provider.Steps steps) {
            calls.incrementAndGet();
            return java.util.Optional.empty();
          }
        };
    Context ctx =
        Context.DEFAULT.withProviders(Context.DEFAULT.providers().register(Gcd.class, counting, 0));
    Expr result = Rewriter.rewrite(parse("(x^2 - 1)/(x - 1)"), RuleSets.RATIONAL, ctx).expr();
    assertThat(result).isEqualTo(canonical("x + 1"));
    assertThat(calls.get()).isEqualTo(1);
  }

  /**
   * A common factor and then a content: {@code (2x² - 2)/(4x - 4)} cancels {@code x - 1} (the
   * contents stay), then gathers the content {@code 2/4} into {@code 1/2}; with the standard list
   * it takes two rewrites and stops.
   */
  @Test
  void aFactorThenAContentAndThenNothing() {
    new RuleCases(RuleSets.RATIONAL)
        .fires("cancel-common-factor", "(2x^2 - 2)/(4x - 4)", "(x + 1)/2")
        .fires("cancel-common-content", "(2x^2 - 2)/(4x - 4)", "(x + 1)/2");
    List<Rule> rules = new ArrayList<>();
    for (RuleSet set : Simplifier.standard()) {
      rules.addAll(set.rules());
    }
    TerminationProbe.Report report =
        TerminationProbe.run(
            new RuleSet(Simplifier.COMBINED, rules),
            List.of(parse("(2x^2 - 2)/(4x - 4)")),
            Context.DEFAULT);
    assertThat(report.maxRewrites()).isEqualTo(2);
  }

  @Test
  void aDecliningGcdProviderMakesTheRulesDecline() {
    Provider<Gcd, Polynomial> declining =
        new Provider<>() {
          @Override
          public String name() {
            return "declining";
          }

          @Override
          public java.util.Optional<Polynomial> apply(
              Gcd input, Context ctx, mathcanvas.engine.provider.Steps steps) {
            return java.util.Optional.empty();
          }
        };
    Context ctx =
        Context.DEFAULT.withProviders(
            Context.DEFAULT
                .providers()
                .unregister(Gcd.class, "default")
                .register(Gcd.class, declining));
    for (String source : List.of("(x^2 - 1)/(x - 1)", "1/(x - 1) - 1/(x + 1)")) {
      Expr e = canonical(source);
      assertThat(Rewriter.rewrite(e, RuleSets.RATIONAL, ctx).expr()).as(source).isEqualTo(e);
    }
  }

  /** Denominators of total degree past the bound are declined before anything is built. */
  @Test
  void aSumOfHighDegreeDeclinesAtOnce() {
    String source = "1/(x + y + 1)^60 + 1/(x + y + 2)^60 + 1/(x + y + 3)^60 + 1/(x + y + 4)^60";
    long start = System.nanoTime();
    new RuleCases(RuleSets.RATIONAL).skips("combine-fractions", source);
    long millis = (System.nanoTime() - start) / 1_000_000;
    // typically a few milliseconds locally (docs/reference/engine.md, Timing budgets)
    assertThat(millis).isLessThan(30_000);
  }

  @Test
  void theNarrationNamesTheGcdAndTheLcm() {
    // through the simplifier's narrator, which owns the set
    assertThat(texts("(x^2 - 1)/(x - 1)"))
        .containsExactly("Cancel the common factor x - 1: (x² - 1)/(x - 1) = x + 1");
    assertThat(texts("1/(x - 1) - 1/(x + 1)"))
        .containsExactly(
            "Write over the common denominator x² - 1: 1/(x - 1) - 1/(x + 1) = 2/(x² - 1)");
    assertThat(texts("1/(x + 1)^2 - 1/(x + 1)"))
        .containsExactly(
            "Write over the common denominator (x + 1)²: 1/(x + 1)² - 1/(x + 1) = -x/(x + 1)²");
    assertThat(texts("2/(2x + 2)"))
        .containsExactly("Cancel the common numeric factor 2: 2/(2x + 2) = 1/(x + 1)");
    // a content p/q with q > 1 clears the fractions, and cancels p when p > 1
    assertThat(texts("(x/2 + 1)/3"))
        .containsExactly(
            "Multiply the numerator and the denominator by 2 to clear the fractions:"
                + " (x/2 + 1)/3 = (x + 2)/6");
    assertThat(texts("1/(x/2 + 1)"))
        .containsExactly(
            "Multiply the numerator and the denominator by 2 to clear the fractions:"
                + " 1/(x/2 + 1) = 2/(x + 2)");
    assertThat(texts("(2x/3 + 2)/4"))
        .containsExactly(
            "Multiply the numerator and the denominator by 3 to clear the fractions, then cancel"
                + " the common numeric factor 2: (2x/3 + 2)/4 = (x + 3)/6");
  }

  private static List<String> texts(String source) {
    return new SimplifyNarrator(mathcanvas.engine.rule.StepText.standard())
            .narrate(
                Rewriter.rewrite(parse(source), RuleSets.RATIONAL, Context.DEFAULT.withTrace(true))
                    .trace(),
                Context.DEFAULT)
            .stream()
            .map(mathcanvas.engine.rule.Step::text)
            .toList();
  }
}
