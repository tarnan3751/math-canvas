package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/**
 * Laplace transforms by table ({@link Laplace}): the table, the shift, multiplication by {@code
 * tⁿ}, partial fractions; {@code ilaplace(laplace(f)) = f} over random table forms; the transform
 * of a derivative of an unknown function in terms of its initial values; all or nothing.
 */
class LaplaceTest {

  private static final Symbol T = Symbol.of("t");
  private static final Symbol S = Symbol.of("s");

  private static Rewriter.Result run(String head, Expr f, Symbol v, Symbol w) {
    return Laplace.of(Apply.of(Symbol.of(head), f, v, w), Context.DEFAULT.withTrace(true));
  }

  private static String laplace(String f) {
    return UnicodePrinter.INSTANCE.display(run("laplace", canonical(f), T, S).expr());
  }

  private static String ilaplace(String f) {
    return UnicodePrinter.INSTANCE.display(run("ilaplace", canonical(f), S, T).expr());
  }

  @Test
  void theTable() {
    assertThat(laplace("1")).isEqualTo("1/s");
    assertThat(laplace("t^3")).isEqualTo("6/s⁴");
    assertThat(laplace("e^(2t)")).isEqualTo("1/(s - 2)");
    assertThat(laplace("sin(3t)")).isEqualTo("3/(s² + 9)");
    assertThat(laplace("cos(t) + 2t")).isEqualTo("s/(s² + 1) + 2/s²");
    assertThat(laplace("cosh(2t)")).isEqualTo("s/(s² - 4)");
    assertThat(laplace("t e^(-t)")).isEqualTo("1/(s + 1)²");
    assertThat(laplace("e^(2t) sin(t)")).isEqualTo("1/((s - 2)² + 1)");
    assertThat(laplace("t sin(t)")).isEqualTo("2s/(s² + 1)²");
  }

  @Test
  void theInverseTable() {
    assertThat(ilaplace("1/(s - 2)")).isEqualTo("e^(2t)");
    assertThat(ilaplace("1/((s - 1)(s + 2))")).isEqualTo("e^t/3 - e^(-2t)/3");
    assertThat(ilaplace("s/(s^2 + 4)")).isEqualTo("cos(2t)");
    assertThat(ilaplace("(s + 1)/(s^2 + 2s + 5)")).isEqualTo("e^(-t) cos(2t)");
    assertThat(ilaplace("1/(s+1)^4")).isEqualTo("t³ e^(-t)/6");
    assertThat(ilaplace("1/(s^2 - 2)")).isEqualTo("√2 sinh(t √2)/2");
    // a repeated quadratic, of either sign
    assertThat(ilaplace("1/(s^2 + 1)^2")).isEqualTo("(-t cos(t) + sin(t))/2");
    assertThat(ilaplace("s/(s^2 + 1)^2")).isEqualTo("t sin(t)/2");
    assertThat(ilaplace("(s^2 + 2)/(s^2 + 1)^2")).isEqualTo("sin(t) + (-t cos(t) + sin(t))/2");
    assertThat(ilaplace("s/(s^2 - 2)^2")).isEqualTo("t √2 sinh(t √2)/4");
  }

  @Test
  void theRepeatedQuadraticInvertsTheTransform() {
    for (String f : List.of("t sin(2t)", "sin(t) - t cos(t)", "e^(-t) t sin(3t)", "t cos(t)")) {
      Expr transform = run("laplace", canonical(f), T, S).expr();
      Expr back = run("ilaplace", transform, S, T).expr();
      Expr difference =
          mathcanvas.engine.algebra.Algebra.expand(
                  Canonical.of(Sum.of(back, Product.of(Num.MINUS_ONE, canonical(f)))),
                  mathcanvas.engine.rule.Context.DEFAULT)
              .expr();
      assertThat(difference).as("%s → %s → %s", f, transform, back).isEqualTo(Num.ZERO);
    }
  }

  @Test
  void thePrimeNotationIsADerivative() {
    Symbol y = Symbol.of("y");
    Rewriter.Result primed = run("laplace", Apply.of(Symbol.of("y''"), T), T, S);
    Rewriter.Result derivative = run("laplace", Derivative.of(Apply.of(y, T), T, 2), T, S);
    assertThat(primed.expr()).isEqualTo(derivative.expr());
  }

  @Test
  void whatNoRuleTakesStaysWholeWithItsNote() {
    Rewriter.Result r = run("laplace", canonical("e^(t^2) + t"), T, S);
    assertThat(r.expr()).isInstanceOf(Apply.class);
    assertThat(r.trace().rewrites().getLast().rule()).isEqualTo("none");
    TraceChecks.assertChain(r);
    assertThat(run("ilaplace", canonical("e^(-s)/s"), S, T).expr()).isInstanceOf(Apply.class);
    // review-28: a power of t beyond 10 stays with its note, and no split of an irreducible
    // quadratic
    Rewriter.Result high = run("laplace", canonical("t^12 sin(t)"), T, S);
    assertThat(high.expr()).isInstanceOf(Apply.class);
    assertThat(high.trace().rewrites().getLast().rule()).isEqualTo("none");
    Rewriter.Result quadratic = run("ilaplace", canonical("1/(s^2 + 2s + 5)"), S, T);
    assertThat(quadratic.trace().rewrites().stream().map(Rewrite::rule))
        .doesNotContain("partial-fractions");
    assertThat(run("laplace", canonical("e^(s t)"), T, S).expr())
        .isEqualTo(mathcanvas.engine.expr.Constant.UNDEFINED);
  }

  @Test
  void theTransformOfADerivativeUsesTheInitialValues() {
    Symbol y = Symbol.of("y");
    Expr second = Derivative.of(Apply.of(y, T), T, 2);
    Rewriter.Result r = run("laplace", second, T, S);
    Expr expected =
        Canonical.of(
            Sum.of(
                Product.of(
                    Power.of(S, Num.of(2)), Apply.of(Symbol.of("laplace"), Apply.of(y, T), T, S)),
                Product.of(Num.MINUS_ONE, S, Laplace.initialValue(y, 0)),
                Product.of(Num.MINUS_ONE, Laplace.initialValue(y, 1))));
    assertThat(r.expr()).isEqualTo(expected);
    assertThat(Laplace.initialValue(y, 1)).isEqualTo(Apply.of(Symbol.of("y'"), Num.ZERO));
    TraceChecks.assertChain(r);
  }

  private static final List<String> FORMS =
      List.of(
          "%a",
          "%a t",
          "%a t^2",
          "e^(%b t)",
          "%a sin(%c t)",
          "%a cos(%c t)",
          "t e^(%b t)",
          "e^(%b t) sin(%c t)",
          "e^(%b t) cos(%c t)",
          "%a t^3 e^(%b t)",
          "sinh(%c t)");

  /**
   * Over sums of two random table forms: the transform at {@code s = 5} is the integral of {@code
   * f(t) e^(−5t)}, and {@code ilaplace(laplace(f)) = f}.
   */
  @Property(tries = 80)
  void theInverseUndoesTheTransform(@ForAll long seed) {
    Random r = new Random(seed);
    String f = form(r) + " + " + form(r);
    Expr given = canonical(f);
    Rewriter.Result forward = run("laplace", given, T, S);
    assertThat(forward.expr()).as("laplace of %s", f).isNotInstanceOf(Apply.class);
    Rewriter.Result back = run("ilaplace", forward.expr(), S, T);
    assertThat(back.expr()).as("ilaplace of %s", forward.expr()).isNotInstanceOf(Apply.class);
    // the transform at s = 5 is the integral of f(t) e^(-5t) over [0, ∞), here [0, 40]
    Compiled transform = Compiled.compile(forward.expr(), List.of(S), Settings.DEFAULT);
    Compiled given0 = Compiled.compile(given, List.of(T), Settings.DEFAULT);
    mathcanvas.engine.compile.Quadrature.Estimate integral =
        mathcanvas.engine.compile.Quadrature.integrate(
            t -> given0.eval(t) * Math.exp(-5 * t),
            0,
            40,
            1e-12,
            mathcanvas.engine.Cancellation.NEVER);
    assertThat(Math.abs(integral.value() - transform.eval(5)))
        .as("laplace of %s at s = 5", f)
        .isLessThanOrEqualTo(1e-8 * Math.max(1, Math.abs(integral.value())));
    // equal as functions of t: compared at points, as sinh and its exponentials need not match
    Compiled before = Compiled.compile(given, List.of(T), Settings.DEFAULT);
    Compiled after = Compiled.compile(back.expr(), List.of(T), Settings.DEFAULT);
    for (double t : new double[] {0.1, 0.7, 1.3, 2.9}) {
      double a = before.eval(t);
      double b = after.eval(t);
      assertThat(Math.abs(a - b))
          .as("%s back from %s at t = %s", f, forward.expr(), t)
          .isLessThanOrEqualTo(1e-9 * Math.max(1, Math.abs(a)));
    }
  }

  private static String form(Random r) {
    int a = 1 + r.nextInt(4);
    int b = r.nextInt(5) - 2;
    int c = 1 + r.nextInt(3);
    return "("
        + FORMS
            .get(r.nextInt(FORMS.size()))
            .replace("%a", Integer.toString(a))
            .replace("%b", Integer.toString(b == 0 ? 1 : b))
            .replace("%c", Integer.toString(c))
        + ")";
  }
}
