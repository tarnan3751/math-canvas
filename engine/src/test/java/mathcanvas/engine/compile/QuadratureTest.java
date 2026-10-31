package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.compile.Quadrature.Estimate;
import mathcanvas.engine.compile.Quadrature.Method;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** Gauss–Kronrod and the Riemann sums in {@code double} over compiled functions of {@code x}. */
class QuadratureTest {

  private static final Symbol X = Symbol.of("x");

  static Compiled f(String text) {
    return Compiled.compile(
        Evaluator.evaluate(Parser.parseExpression(text, Names.NONE), Environment.EMPTY, false)
            .value(),
        List.of(X),
        Settings.DEFAULT);
  }

  private static Estimate integrate(String text, double a, double b) {
    return Quadrature.integrate(f(text), a, b, Cancellation.NEVER);
  }

  private static void close(Estimate e, double expected) {
    assertThat(e.converged()).as("%s", e).isTrue();
    assertThat(e.value()).isCloseTo(expected, within(1e-10 * Math.max(1, Math.abs(expected))));
    assertThat(Math.abs(e.value() - expected)).isLessThanOrEqualTo(Math.max(e.error(), 1e-15));
  }

  @Test
  void knownIntegrals() {
    close(integrate("x^3", 0, 1), 0.25);
    close(integrate("e^x", 0, 1), Math.E - 1);
    close(integrate("sin(x)", 0, Math.PI), 2);
    close(integrate("1/sqrt(x)", 0, 1), 2);
    close(integrate("e^(-x^2)", -10, 10), Math.sqrt(Math.PI));
    close(integrate("1/(1+x^2)", -1, 1), Math.PI / 2);
  }

  @Test
  void anIntegralThatCancels() {
    Estimate e = integrate("sin(x)", -Math.PI, Math.PI);
    assertThat(e.converged()).isTrue();
    assertThat(e.value()).isCloseTo(0, within(1e-12));
  }

  @Test
  void anIntegralBeyondTheDoubleRangeIsInfiniteAndNotConverged() {
    Estimate e = integrate("10^300", 0, 1e10);
    assertThat(e.value()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(Double.isFinite(e.error())).isFalse();
    assertThat(e.converged()).isFalse();
  }

  @Test
  void reversedEqualAndInfiniteBounds() {
    assertThat(integrate("x^3", 1, 0).value()).isCloseTo(-0.25, within(1e-15));
    assertThat(integrate("x^3", 1, 1)).isEqualTo(new Estimate(0, 0, true));
    assertThat(integrate("x^3", 0, Double.POSITIVE_INFINITY).value()).isNaN();
  }

  @Test
  void aNonFiniteSampleIsNaN() {
    // the centre of [−1, 1] is a node
    assertThat(integrate("1/x", -1, 1).value()).isNaN();
    assertThat(integrate("sqrt(x)", -1, 1).value()).isNaN();
  }

  @Test
  void theIntervalCapEndsAnUnsettledIntegral() {
    Estimate e = integrate("sin(1/x)", 1e-6, 1);
    assertThat(e.converged()).isFalse();
    assertThat(e.value()).isFinite();
  }

  @Test
  void cancellationStopsTheBisection() {
    AtomicInteger checks = new AtomicInteger();
    Cancellation after = () -> checks.incrementAndGet() > 3;
    assertThatThrownBy(() -> Quadrature.integrate(f("sin(1/x)"), 1e-6, 1, after))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  void theResultIsDeterministic() {
    assertThat(integrate("sin(1/x)", 1e-3, 1)).isEqualTo(integrate("sin(1/x)", 1e-3, 1));
  }

  @Test
  void riemannSumsOfALine() {
    Compiled line = f("x");
    // left: (n − 1)/(2n), right: (n + 1)/(2n); midpoint and trapezoid exact
    assertThat(Quadrature.riemann(line, 0, 1, 4, Method.LEFT)).isCloseTo(3 / 8.0, within(1e-15));
    assertThat(Quadrature.riemann(line, 0, 1, 4, Method.RIGHT)).isCloseTo(5 / 8.0, within(1e-15));
    assertThat(Quadrature.riemann(line, 0, 1, 4, Method.MIDPOINT)).isCloseTo(0.5, within(1e-15));
    assertThat(Quadrature.riemann(line, 0, 1, 4, Method.TRAPEZOID)).isCloseTo(0.5, within(1e-15));
  }

  @Test
  void riemannHeights() {
    Compiled square = f("x^2");
    assertThat(Quadrature.riemannHeights(square, 0, 2, 2, Method.LEFT)).containsExactly(0, 1);
    assertThat(Quadrature.riemannHeights(square, 0, 2, 2, Method.RIGHT)).containsExactly(1, 4);
    assertThat(Quadrature.riemannHeights(square, 0, 2, 2, Method.MIDPOINT))
        .containsExactly(0.25, 2.25);
    assertThat(Quadrature.riemannHeights(square, 0, 2, 2, Method.TRAPEZOID))
        .containsExactly(0, 1, 4);
  }

  @Test
  void riemannSumsConvergeAtTheirOrders() {
    Compiled square = f("x^2");
    double third = 1 / 3.0;
    for (Method m : Method.values()) {
      double coarse = Math.abs(Quadrature.riemann(square, 0, 1, 10, m) - third);
      double fine = Math.abs(Quadrature.riemann(square, 0, 1, 20, m) - third);
      double ratio = coarse / fine;
      // first order for the ends, second for the midpoint and trapezoid
      double order = m == Method.LEFT || m == Method.RIGHT ? 2 : 4;
      assertThat(ratio).as("%s", m).isCloseTo(order, within(0.3));
    }
  }

  @Test
  void aRiemannSumWithANonFiniteSampleIsNaN() {
    assertThat(Quadrature.riemann(f("1/x"), 0, 1, 4, Method.LEFT)).isNaN();
    assertThat(Quadrature.riemannHeights(f("1/x"), 0, 1, 2, Method.LEFT)[0]).isNaN();
    assertThat(Quadrature.riemann(f("1/x"), 0, 1, 4, Method.RIGHT))
        .isCloseTo(25 / 12.0, within(1e-12));
  }

  @Test
  void refusals() {
    assertThatThrownBy(() -> Quadrature.riemann(f("x"), 0, 1, 0, Method.LEFT))
        .isInstanceOf(IllegalArgumentException.class);
    Compiled two =
        Compiled.compile(
            Evaluator.evaluate(
                    Parser.parseExpression("x + y", Names.NONE), Environment.EMPTY, false)
                .value(),
            List.of(X, Symbol.of("y")),
            Settings.DEFAULT);
    assertThatThrownBy(() -> Quadrature.integrate(two, 0, 1, Cancellation.NEVER))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
