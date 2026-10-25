package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.canonical;
import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.last;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

/** The algebra commands of §14.9 through a session: values, traces and errors. */
class AlgebraCommandsTest {

  /**
   * An algebra command's rewriting has a statement's scaled cap, not the base one: distributing
   * 12,000 products takes more rewrites than the base cap's 10,000.
   */
  @Test
  void anAlgebraCommandHasTheScaledCap() {
    StringBuilder terms = new StringBuilder();
    for (int j = 1; j <= 12_000; j++) {
      terms.append(j == 1 ? "" : " + ").append("(a_").append(j).append(" + 1) b_").append(j);
    }
    Expr e = value("expand(" + terms + ")");
    assertThat(e).isInstanceOf(Sum.class);
    assertThat(((Sum) e).terms()).hasSize(24_000);
    Expr inner = value("1 + expand(" + terms + ")");
    assertThat(((Sum) inner).terms()).hasSize(24_001);
  }

  @Test
  void expandFactorCollectAndCancel() {
    assertThat(shown("expand((x + 1)^3)")).isEqualTo("x³ + 3x² + 3x + 1");
    assertThat(shown("a := 2; expand((x + a)^2)")).isEqualTo("x² + 4x + 4");
    assertThat(shown("factor(x^4 - 1)")).isEqualTo("(x - 1)(x + 1)(x² + 1)");
    assertThat(shown("factor(x^2 - 5x + 6)")).isEqualTo("(x - 3)(x - 2)");
    assertThat(shown("a := 2; factor(x^2 - a^2)")).isEqualTo("(x - 2)(x + 2)");
    assertThat(value("collect(a x + b x + x^2, x)")).isEqualTo(canonical("x^2 + (a + b) x"));
    assertThat(value("x := 2; collect(x y + y, x)")).isEqualTo(canonical("3y"));
    assertThat(shown("cancel((x^2 - 1)/(x - 1))")).isEqualTo("x + 1");
    assertThat(shown("2 factor(x^2 - 1)")).isEqualTo("2(x - 1)(x + 1)");
  }

  @Test
  void gcdAndLcmOfPolynomials() {
    assertThat(shown("gcd(x^2 - 1, x^2 + 2x + 1)")).isEqualTo("x + 1");
    assertThat(shown("lcm(x^2 - 1, x + 1)")).isEqualTo("x² - 1");
    assertThat(shown("gcd(x^2 - 1, x^3 - 1, x^2 - 2x + 1)")).isEqualTo("x - 1");
    assertThat(shown("gcd(12, 18)")).isEqualTo("6");
    assertThat(shown("gcd(1/2, 1/3)")).isEqualTo("1/6");
    assertThat(shown("gcd(2.0, 4)")).isEqualTo("2.0");
    // exact rationals are polynomials, the gcd of their contents (§14.9)
    assertThat(shown("gcd(1/2, 2)")).isEqualTo("1/2");
    assertThat(shown("gcd(x, {1, 2})")).isEqualTo("gcd(x, {1, 2})");
  }

  @Test
  void apart() {
    assertThat(shown("apart(1/(x^2 - 1), x)")).isEqualTo("1/(2(x - 1)) - 1/(2(x + 1))");
    assertThat(shown("apart((2x + 3)/(x + 1)^3, x)")).isEqualTo("1/(x + 1)³ + 2/(x + 1)²");
    assertThat(shown("x := 3; apart(1/(x (x + 1)), x)")).isEqualTo("1/12");
    assertThat(shown("apart(1/(x y), x)")).isEqualTo("1/(x y)");
    assertThat(shown("apart(1/(x + ∞), x)")).isEqualTo("apart(1/(x + ∞), x)");
    assertThat(error("apart(1/x, 2)")).isEqualTo("apart needs a variable");
  }

  @Test
  void numeratorAndDenominatorAreStructural() {
    assertThat(shown("numerator(2x/(y z^2))")).isEqualTo("2x");
    assertThat(shown("denominator(2x/(y z^2))")).isEqualTo("y z²");
    assertThat(shown("numerator(x/2)")).isEqualTo("x");
    assertThat(shown("denominator(x/2)")).isEqualTo("2");
    assertThat(shown("numerator(1/x^2)")).isEqualTo("1");
    assertThat(shown("denominator(1/x^2)")).isEqualTo("x²");
    assertThat(shown("numerator(1/x + 1/y)")).isEqualTo("1/x + 1/y");
    assertThat(shown("denominator(1/x + 1/y)")).isEqualTo("1");
    assertThat(shown("numerator(3/4)")).isEqualTo("3");
    assertThat(shown("denominator(3/4)")).isEqualTo("4");
    assertThat(shown("numerator(sin(x))")).isEqualTo("sin(x)");
    assertThat(shown("numerator({1, 2})")).isEqualTo("numerator({1, 2})");
    assertThat(shown("denominator(x = 1)")).isEqualTo("denominator(x = 1)");
  }

  @Test
  void refusedInputsStayAsCalls() {
    assertThat(shown("factor(∞ x)")).isEqualTo("factor(∞x)");
    assertThat(shown("expand(∞ (x + 1))")).isEqualTo("expand(∞·(x + 1))");
    assertThat(shown("cancel(∞ x)")).isEqualTo("cancel(∞x)");
    assertThat(shown("collect(∞ x, x)")).isEqualTo("collect(∞x, x)");
    assertThat(shown("expand([(x + 1)^2, true])")).isEqualTo("expand([(x + 1)², true])");
    assertThat(shown("cancel(undefined x)")).isEqualTo("cancel(undefined)");
    assertThat(shown("apart(x = 1, x)")).isEqualTo("apart(x = 1, x)");
  }

  @Test
  void relationsAndCollectionsAreTakenItemByItem() {
    assertThat(shown("factor(x^2 - 1 = 0)")).isEqualTo("(x - 1)(x + 1) = 0");
    assertThat(shown("cancel((x^2 - 1)/(x - 1) = 0)")).isEqualTo("x + 1 = 0");
    assertThat(shown("expand({(x + 1)^2, 2})")).isEqualTo("{2, x² + 2x + 1}");
    assertThat(value("collect([a x + x, x^2 + b x^2], x)"))
        .isEqualTo(canonical("[(a + 1) x, (b + 1) x^2]"));
    assertThat(value("expand([[(x + 1)^2, 1], [2, 3]])"))
        .isEqualTo(canonical("[[x^2 + 2x + 1, 1], [2, 3]]"));
    assertThat(value("factor([[x^2 - 1, x], [2x + 2, 3]])"))
        .isEqualTo(canonical("[[(x - 1)(x + 1), x], [2(x + 1), 3]]"));
    assertThat(value("cancel([[x/x, 1/x + 1/y]])")).isEqualTo(canonical("[[1, (x + y)/(x y)]]"));
  }

  @Test
  void decimalsAreKernels() {
    assertThat(shown("gcd(x^2 - 1.0, x - 1)")).isEqualTo("1");
    assertThat(shown("factor(x^2 - 2.0)")).isEqualTo("x² - 2.0");
  }

  @Test
  void limitsAreEvaluationErrors() {
    assertThat(error("factor(x^10001 - 1)")).isEqualTo("the polynomial's degree exceeds 10,000");
    assertThat(error("cancel((3x + 6)^-1000000000)"))
        .isEqualTo("the unit's power is too large to compute");
    assertThat(error("gcd(x^10001 - 1, x - 1)"))
        .isEqualTo("the polynomial's degree exceeds 10,000");
    assertThat(error("factor((x + 2)^1073741823 (-x - 2)^1073741823 (x^2 + 4x + 4))"))
        .isEqualTo("the exponents are too large");
  }

  @Test
  void stepsShowTheCommandsTraceThenTheSimplifiers() {
    Outcome o = last("steps(factor(x^4 - 1))");
    Trace t = ((Outcome.Value) o).trace();
    assertThat(t.rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "rational-root");
    assertThat(t.initial()).isEqualTo(canonical("x^4 - 1"));
    replays(t, ((Outcome.Value) o).value());
    Outcome e = last("steps(expand((x + 1)^2 (x - 1)))");
    Trace te = ((Outcome.Value) e).trace();
    assertThat(te.rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("binomial-theorem", "distribute");
    replays(te, ((Outcome.Value) e).value());
    Outcome g = last("steps(gcd(x^2 - 1, x + 1))");
    assertThat(((Outcome.Value) g).trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("default");
    // a command inside an expression keeps no trace of its own
    Outcome nested = last("steps(2 factor(x^2 - 1))");
    assertThat(((Outcome.Value) nested).trace().rewrites()).isEmpty();
    Outcome plain = last("factor(x^4 - 1)");
    assertThat(((Outcome.Value) plain).trace()).isEqualTo(Trace.NONE);
  }

  private static void replays(Trace t, Expr value) {
    List<Expr> roots = t.roots();
    for (int i = 0; i < roots.size(); i++) {
      Rewrite w = t.rewrites().get(i);
      Expr at = roots.get(i);
      for (int k : w.path().indices()) {
        at = Operands.of(at).get(k);
      }
      assertThat(at).isEqualTo(w.after());
    }
    assertThat(Canonical.of(roots.getLast())).isEqualTo(value);
  }
}
