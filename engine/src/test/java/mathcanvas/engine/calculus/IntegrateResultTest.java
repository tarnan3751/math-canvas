package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import org.junit.jupiter.api.Test;

/** The final pass on an indefinite integral's antiderivative ({@link IntegrateResult}). */
class IntegrateResultTest {

  private static final Context TRACED = Context.DEFAULT.withTrace(true);

  private static Rewriter.Result integrate(String input) {
    return IntegrateCases.integrate(input, TRACED);
  }

  private static List<String> texts(Rewriter.Result r) {
    return Narration.steps(r.trace(), TRACED, StepText.standard()).stream()
        .map(Step::text)
        .toList();
  }

  @Test
  void aConstantFactorIsDistributedOverASumThatHoldsTheVariable() {
    Rewriter.Result r = integrate("integrate(x^2 e^x, x)");
    assertThat(IntegrateCases.fired(r)).contains("distribute");
    assertThat(r.expr()).isEqualTo(IntegrateCases.parseCanonical("x^2 e^x - 2x e^x + 2e^x"));
    assertThat(texts(r).getLast())
        .isEqualTo(
            "Multiply the constant -2 into the sum: x² e^x - 2(x e^x - e^x) = x² e^x - 2x e^x"
                + " + 2 e^x");
    TraceChecks.assertChain(r);
  }

  @Test
  void onlyTheTopLevelIsDistributed() {
    Rewriter.Result r = integrate("integrate(1/(x^2 + 2x + 5), x)");
    assertThat(IntegrateCases.fired(r)).doesNotContain("distribute");
    assertThat(r.expr()).isEqualTo(IntegrateCases.parseCanonical("atan((x + 1)/2)/2"));
  }

  @Test
  void aTermFreeOfTheVariableIsDropped() {
    Rewriter.Result r = integrate("integrate(x/(x + a), x)");
    assertThat(IntegrateCases.fired(r)).contains("constant-term");
    assertThat(r.expr()).isEqualTo(IntegrateCases.parseCanonical("x - a log(abs(x + a))"));
    assertThat(texts(r).getLast())
        .isEqualTo(
            "Antiderivatives differ only by a constant, so drop the constant term a:"
                + " a - a log(|a + x|) + x = -a log(|a + x|) + x");
    TraceChecks.assertChain(r);
    assertThat(IntegrateCases.fired(integrate("integrate(x + 1, x)")))
        .doesNotContain("constant-term");
  }

  @Test
  void aDefiniteValueKeepsItsConstantTerms() {
    Rewriter.Result r = integrate("integrate(x + a, x, 0, 1)");
    assertThat(IntegrateCases.fired(r)).doesNotContain("constant-term", "distribute");
    assertThat(r.expr()).isEqualTo(IntegrateCases.simplified("a + 1/2", Context.DEFAULT));
  }

  @Test
  void towersFlattenThroughNestedConstantFactors() {
    Rewriter.Result r = integrate("integrate(x log(x)^4, x)");
    assertThat(r.expr())
        .isEqualTo(
            IntegrateCases.parseCanonical(
                "3x^2/4 - 3x^2 log(x)/2 + 3x^2 log(x)^2/2 - x^2 log(x)^3 + x^2 log(x)^4/2"));
    assertThat(integrate("integrate(sin(x)^3, x)").expr())
        .isEqualTo(IntegrateCases.parseCanonical("-cos(x) + cos(x)^3/3"));
  }

  @Test
  void aDefiniteValueDistributesNumbersOnly() {
    mathcanvas.engine.expr.Symbol x = mathcanvas.engine.expr.Symbol.of("x");
    assertThat(
            IntegrateResult.apply(
                    IntegrateCases.parseCanonical("-(-cot(1) - 1) - cot(2) - 2"),
                    x,
                    true,
                    Context.DEFAULT)
                .expr())
        .isEqualTo(IntegrateCases.parseCanonical("cot(1) - cot(2) - 1"));
    assertThat(
            IntegrateResult.apply(
                    IntegrateCases.parseCanonical("sqrt(2) (pi + 1)"), x, true, Context.DEFAULT)
                .expr())
        .isEqualTo(IntegrateCases.parseCanonical("sqrt(2) (pi + 1)"));
  }
}
