package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.DoubleUnaryOperator;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.Test;

/**
 * The acceptance suite of numeric ODEs: the solutions match the analytic ones within 1e-8 relative
 * over {@code [0, 5]}: {@code y' = y}, {@code y' = −2ty}, the harmonic oscillator as a system, the
 * stiff-ish {@code y' = −50(y − cos t)}, and the logistic equation, each on its grid; and with a
 * random initial value.
 */
class NumericOdeProperties {

  /** An equation, its unknowns, its conditions, and the analytic first unknown. */
  record Case(String call, DoubleUnaryOperator exact) {}

  private static List<Case> suite(double c) {
    String y0 = Double.toString(c);
    return List.of(
        new Case("y'(t) = y(t), y(t), y(0) = " + y0, t -> c * Math.exp(t)),
        new Case("y'(t) = -2t y(t), y(t), y(0) = " + y0, t -> c * Math.exp(-t * t)),
        new Case(
            "[x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = " + y0 + ", y(0) = 0",
            t -> c * Math.cos(t)),
        new Case(
            "y'(t) = -50(y(t) - cos(t)), y(t), y(0) = " + y0,
            t ->
                2500.0 / 2501 * Math.cos(t)
                    + 50.0 / 2501 * Math.sin(t)
                    + (c - 2500.0 / 2501) * Math.exp(-50 * t)),
        new Case(
            "y'(t) = y(t)(1 - y(t)), y(t), y(0) = " + y0,
            t -> c * Math.exp(t) / (1 - c + c * Math.exp(t))));
  }

  @Test
  void theStandardSuite() {
    check(1.0);
  }

  @Property(tries = 20)
  void randomInitialValues(@ForAll @DoubleRange(min = 0.1, max = 2) double c) {
    check(c);
  }

  private static void check(double c) {
    for (Case k : suite(c)) {
      Matrix m = (Matrix) Run.value("odesolve(" + k.call() + ", 0, 5, steps = 50)");
      for (var row : m.rows()) {
        double t = d((Num) row.getFirst());
        double y = d((Num) row.get(1));
        double exact = k.exact().applyAsDouble(t);
        assertThat(Math.abs(y - exact))
            .as(k.call() + " at " + t)
            .isLessThanOrEqualTo(1e-8 * Math.max(Math.abs(exact), 1e-3));
      }
    }
  }

  private static double d(Num n) {
    return ((RealNumeric) n.value()).toDouble();
  }
}
