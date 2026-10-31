package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * The numeric path of a definite integral ({@code docs/language.md} §10.2): only under {@code
 * numeric} or with quadrature digits set, with its step. The integrands have no elementary
 * antiderivative, so the exact path leaves them however far it reaches.
 */
class NumericIntegralTest {

  /** A decimal number format of 10 digits, as the worksheet sets it. */
  private static final Settings DECIMAL = Settings.DEFAULT.withQuadratureDigits(10);

  private static Outcome.Value traced(String script, Settings settings) {
    Session s = new Session(Environment.EMPTY.withSettings(settings));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  /**
   * A trace whose step leaves the node as it was ({@code numeric.none}, {@code
   * numeric.angle-mode}): the step stands at the root on the node, and the replay ends in the
   * value.
   */
  private static void replays(Outcome.Value v) {
    Rewrite step =
        v.trace().rewrites().stream()
            .filter(r -> r.ruleSet().equals("numeric"))
            .findFirst()
            .orElseThrow();
    assertThat(step.after()).isEqualTo(step.before()).isInstanceOf(Integral.class);
    assertThat(v.trace().result()).isEqualTo(v.value());
  }

  private static List<String> rules(Outcome.Value v) {
    return v.trace().rewrites().stream().map(r -> r.ruleSet() + "." + r.rule()).toList();
  }

  @Test
  void numericEvaluatesAnIntegralWithNoClosedForm() {
    assertThat(shown("numeric(integrate(e^(-x^2), x, 0, 1))"))
        .isEqualTo("0.746824132812427025399467436131853");
    assertThat(shown("numeric(∫[0, 1] e^(-x^2) dx, 50)"))
        .isEqualTo("0.74682413281242702539946743613185300535449968681261");
    assertThat(shown("numeric(integrate(x^x, x, 0, 1), 20)")).isEqualTo("0.78343051071213440706");
  }

  @Test
  void infiniteAndConstantBounds() {
    assertThat(shown("numeric(integrate(e^(-x^2), x, -∞, ∞), 20)"))
        .isEqualTo("1.7724538509055160273");
    assertThat(shown("numeric(integrate(sin(x^2), x, 0, √π), 15)")).isEqualTo("0.894831469484145");
  }

  @Test
  void theDecimalFormatEvaluatesAtItsDigits() {
    Expr v = value("integrate(e^(-x^2), x, -∞, ∞)", DECIMAL);
    assertThat(v).isEqualTo(new Num(Real.of("1.772453851")));
    assertThat(value("∫[0, 1] e^(-x^2) dx", DECIMAL)).isEqualTo(new Num(Real.of("0.7468241328")));
  }

  @Test
  void theExactFormatsAnswerWithTheNode() {
    assertThat(value("integrate(e^(-x^2), x, 0, 1)")).isInstanceOf(Integral.class);
    assertThat(rules(traced("steps(integrate(e^(-x^2), x, 0, 1))", Settings.DEFAULT)))
        .noneMatch(r -> r.startsWith("numeric."));
  }

  @Test
  void theStepComesWithTheValue() {
    Outcome.Value v = traced("steps(integrate(e^(-x^2), x, 0, 1))", DECIMAL);
    assertThat(rules(v)).contains("numeric.quadrature");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    Rewrite step =
        v.trace().rewrites().stream().filter(r -> r.rule().equals("quadrature")).findFirst().get();
    assertThat(step.bindings().get("digits_")).isEqualTo(Num.of(10));
    List<Step> steps = Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .contains("No closed form found; the value is numeric, 10 digits");
  }

  @Test
  void numericAtTheTopKeepsTheStep() {
    Outcome.Value v = traced("steps(numeric(integrate(e^(-x^2), x, 0, 1), 12))", Settings.DEFAULT);
    assertThat(rules(v)).contains("numeric.quadrature");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    assertThat(v.value()).isEqualTo(new Num(Real.of("0.746824132812")));
  }

  @Test
  void aNestedIntegralIsNumericAndUntraced() {
    Outcome.Value v =
        traced("steps(numeric(2 integrate(e^(-x^2), x, 0, 1), 12))", Settings.DEFAULT);
    assertThat(v.value()).isEqualTo(new Num(Real.of("1.49364826562")));
    assertThat(rules(v)).noneMatch(r -> r.startsWith("numeric."));
  }

  @Test
  void aDivergentIntegralStaysWithItsStep() {
    Outcome.Value v = traced("steps(integrate(e^(x^2)/x, x, 0, 1))", DECIMAL);
    assertThat(v.value()).isInstanceOf(Integral.class);
    assertThat(rules(v)).contains("numeric.none");
    replays(v);
    Outcome.Value n = traced("steps(numeric(integrate(e^(x^2)/x, x, 0, 1)))", Settings.DEFAULT);
    assertThat(n.value()).isInstanceOf(Integral.class);
    assertThat(rules(n)).contains("numeric.none");
    replays(n);
  }

  @Test
  void aParameterOrAnIndefiniteIntegralIsLeftWithoutAStep() {
    Outcome.Value v = traced("steps(integrate(e^(-a x^2), x, 0, 1))", DECIMAL);
    assertThat(v.value()).isInstanceOf(Integral.class);
    assertThat(rules(v)).noneMatch(r -> r.startsWith("numeric."));
    assertThat(value("integrate(e^(-x^2), x, 0, b)", DECIMAL)).isInstanceOf(Integral.class);
    assertThat(value("integrate(e^(-x^2), x)", DECIMAL)).isInstanceOf(Integral.class);
  }

  @Test
  void anAngleOfTheVariableInDegreesIsLeft() {
    Settings degrees = DECIMAL.withAngleMode(AngleMode.DEGREES);
    Outcome.Value v = traced("steps(integrate(sin(x^2), x, 0, 1))", degrees);
    assertThat(v.value()).isInstanceOf(Integral.class);
    assertThat(rules(v)).contains("numeric.angle-mode");
    replays(v);
    // no angle of the variable: evaluated in every mode
    assertThat(value("integrate(e^(-x^2), x, 0, 1)", degrees))
        .isEqualTo(new Num(Real.of("0.7468241328")));
  }

  /**
   * A feature narrower than the coarse levels' nodes (review-9): the true value or no value, never
   * the value without the feature. At 10 digits, the app's default, where review-9 found the wrong
   * values; the spike on {@code [0, 1]} is narrower than the levels below the minimum see, and
   * cheaper than review-9's {@code 10^6}, whose samples at {@code exp(−490,000)} take a minute to
   * run to the cap.
   */
  @Test
  void aNarrowFeatureGivesTheTrueValueOrNone() {
    String[][] cases = {
      {"integrate(e^(-10^5 (x - 0.3)^2) + 1, x, 0, 1)", "1.005604991216397928699311"},
      {"integrate(e^(-(x - 30)^2) + e^(-x^2), x, -∞, ∞)", "3.544907701811032054596334966682290"},
      {"integrate(e^(-10^4 (x - 5)^2), x, 0, ∞)", "0.01772453850905516027298167483341145"},
    };
    for (String[] c : cases) {
      Expr v = value("numeric(" + c[0] + ", 10)");
      if (v instanceof Num n) {
        java.math.BigDecimal want = new java.math.BigDecimal(c[1]);
        assertThat(((Real) n.value()).value().subtract(want).abs())
            .as("%s", c[0])
            .isLessThanOrEqualTo(want.round(new java.math.MathContext(10)).ulp());
      } else {
        assertThat(v).as("%s", c[0]).isInstanceOf(Integral.class);
      }
    }
  }

  @Test
  void theDigitsAreCappedAtAHundred() {
    Outcome.Value v = traced("steps(numeric(integrate(e^(-x^2), x, 0, 1), 101))", Settings.DEFAULT);
    assertThat(v.value()).isInstanceOf(Integral.class);
    assertThat(rules(v)).contains("numeric.digits");
    // at 100 the rule runs: an undefined sample at the first node refuses at once, not the cap
    Outcome.Value at =
        traced("steps(numeric(integrate(e^(x^2)/(x - 1/2), x, 0, 1), 100))", Settings.DEFAULT);
    assertThat(rules(at)).contains("numeric.none").doesNotContain("numeric.digits");
  }

  @Test
  void onlyAValueTheQuadratureGaveIsMarked() {
    // a closed form, as the exact path (4a's provider) gives one under a decimal format: no mark
    Expr closed = Run.canonical("1/2");
    assertThat(NumericIntegral.of(closed, 10, true, true, closed, Cancellation.NEVER).numeric())
        .isFalse();
    // the integral the exact path leaves: the quadrature gives it, marked
    Expr left = value("integrate(e^(-x^2), x, 0, 1)");
    NumericIntegral.Outcome out =
        NumericIntegral.of(left, 10, true, true, left, Cancellation.NEVER);
    assertThat(out.value()).isInstanceOf(Num.class);
    assertThat(out.numeric()).isTrue();
    // a refusal is not marked
    Expr divergent = value("integrate(e^(x^2)/x, x, 0, 1)");
    assertThat(
            NumericIntegral.of(divergent, 10, true, true, divergent, Cancellation.NEVER).numeric())
        .isFalse();
    // a value derived from a quadrature keeps the mark
    assertThat(traced("round(integrate(e^(-x^2), x, 0, 1))", DECIMAL).numeric()).isTrue();
  }

  @Test
  void theOutcomeIsMarkedNumericWhenAQuadratureRan() {
    assertThat(traced("integrate(e^(-x^2), x, 0, 1)", DECIMAL).numeric()).isTrue();
    assertThat(traced("2 integrate(e^(-x^2), x, 0, 1)", DECIMAL).numeric()).isTrue();
    assertThat(traced("numeric(integrate(e^(-x^2), x, 0, 1))", Settings.DEFAULT).numeric())
        .isTrue();
    assertThat(traced("integrate(e^(-x^2), x, 0, 1)", Settings.DEFAULT).numeric()).isFalse();
    assertThat(traced("integrate(e^(x^2)/x, x, 0, 1)", DECIMAL).numeric()).isFalse();
    assertThat(traced("numeric(1/3)", Settings.DEFAULT).numeric()).isFalse();
  }

  @Test
  void theSettingsCarryTheDigits() {
    assertThat(Settings.DEFAULT.quadratureDigits()).isZero();
    assertThat(DECIMAL.withPrecision(50).quadratureDigits()).isEqualTo(10);
    assertThat(DECIMAL.withAngleMode(AngleMode.GRADIANS).quadratureDigits()).isEqualTo(10);
    assertThatThrownBy(() -> Settings.DEFAULT.withQuadratureDigits(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
