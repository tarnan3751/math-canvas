package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import org.junit.jupiter.api.Test;

/**
 * {@code odesolve}: the grid and the point, orders above one and systems, conditions inside the
 * interval, the value marked numeric, and the errors.
 */
class NumericOdeTest {

  @Test
  void theGridIsAMatrixOfRows() {
    Matrix m = (Matrix) Run.value("odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 4)");
    assertThat(m.rows()).hasSize(5);
    assertThat(d(m.rows().getFirst().getFirst())).isEqualTo(0.0);
    assertThat(d(m.rows().getLast().getFirst())).isEqualTo(1.0);
    for (var row : m.rows()) {
      assertThat(d(row.get(1))).isCloseTo(Math.exp(d(row.getFirst())), within(1e-8));
      assertThat(((Num) row.get(1)).value()).isInstanceOf(Real.class);
    }
    // a hundred intervals by default
    assertThat(((Matrix) Run.value("odesolve(y'(t) = 1, y(t), y(0) = 0, 0, 1)")).rows())
        .hasSize(101);
  }

  @Test
  void aPointOrdersAndSystems() {
    assertThat(d(Run.value("odesolve(y''(x) = -y(x), y(x), y(0) = 1, y'(0) = 0, 0, 10, at = pi)")))
        .isCloseTo(-1.0, within(1e-8));
    Vector v =
        (Vector)
            Run.value(
                "odesolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0, 0, 2,"
                    + " at = 2)");
    assertThat(d(v.items().get(0))).isCloseTo(Math.cos(2), within(1e-8));
    assertThat(d(v.items().get(1))).isCloseTo(-Math.sin(2), within(1e-8));
    // the conditions inside the interval: integrated both ways
    assertThat(d(Run.value("odesolve(y'(t) = y(t)(1 - y(t)), y(t), y(2) = 1/2, 0, 4, at = 0)")))
        .isCloseTo(1 / (1 + Math.exp(2)), within(1e-9));
  }

  @Test
  void theValueIsNumeric() {
    Outcome.Value v = (Outcome.Value) Run.last("odesolve(y'(t) = t, y(t), y(0) = 0, 0, 1, at = 1)");
    assertThat(v.numeric()).isTrue();
    assertThat(d(v.value())).isCloseTo(0.5, within(1e-12));
  }

  @Test
  void radiansInEveryAngleMode() {
    double v =
        d(Run.value("odesolve(y'(x) = cos(x), y(x), y(30) = 0, 0, 30, at = 0)", Run.DEGREES));
    assertThat(v).isCloseTo(-Math.sin(30), within(1e-8));
  }

  @Test
  void errors() {
    assertThat(Run.error("odesolve(y'(t) = y(t)^2, y(t), y(0) = 1, 0, 2, at = 2)"))
        .isEqualTo(
            "odesolve's solution is not finite on the interval: it blows up or is undefined");
    assertThat(Run.error("odesolve(y'(t) = a y(t), y(t), y(0) = 1, 0, 1)"))
        .isEqualTo("odesolve needs equations in t and the unknowns only, with real values");
    assertThat(Run.error("odesolve(y''(t) = y(t), y(t), y(0) = 1, 0, 1)"))
        .isEqualTo("odesolve needs the initial conditions y(a), y'(a), once each");
    assertThat(Run.error("odesolve(y'(t) = y(t), y(t), y(3) = 1, 0, 1)"))
        .isEqualTo("odesolve needs the initial point in [a, b]");
    assertThat(Run.error("odesolve(y'(t) = y(t), y(t), y(0) = 1, 1, 0)"))
        .isEqualTo("odesolve needs a < b");
    assertThat(Run.error("odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 0)"))
        .isEqualTo("odesolve's steps must be an integer from 1 to 100000");
    assertThat(Run.error("odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 2, at = 1)"))
        .isEqualTo("odesolve takes steps or at, not both");
    assertThat(Run.error("odesolve(sin(y'(t)) = y(t)^2 + 1, y(t), y(0) = 1, 0, 1)"))
        .isEqualTo("odesolve needs equations that solve for the highest derivatives");
  }

  private static double d(Expr e) {
    return ((RealNumeric) ((Num) e).value()).toDouble();
  }
}
