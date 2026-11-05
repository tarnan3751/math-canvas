package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.app.ui.CellView;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.render.MathView;
import org.junit.jupiter.api.Test;

/**
 * Discrete results in the worksheet: ifactor's product, a Diophantine equation's parameter note, a
 * truth table and a recurrence's constants are typeset.
 */
class DiscreteOutputTest extends WorksheetHarness {

  private Layout layout(int cell) {
    MathView view = (MathView) window.cellViews().get(cell).lookup(".cell-math");
    return (Layout) view.getProperties().get(CellView.LAYOUT);
  }

  @Test
  void theNumberTheoryAndDiscreteValuesAreTypeset() throws Exception {
    enter("ifactor(360)");
    waitForOutput(0, "2³ · 3² · 5");
    enter("diophantine(3x + 5y = 7, [x, y])");
    waitForOutput(1, "{[x = 5k + 4, y = -3k - 1]}, k ∈ ℤ");
    enter("truthtable(p implies q, [p, q])");
    waitForOutput(
        2,
        "[[p, q, p ⟹ q], [true, true, true], [true, false, false], [false, true, true], [false,"
            + " false, true]]");
    enter("rsolve(a(n + 1) = 2 a(n) + 1, a(n))");
    waitForOutput(3, "a(n) = C₁·2^n - 1, C₁ ∈ ℝ");
    enter("{1, 2} ∪ {2, 3}");
    waitForOutput(4, "{1, 2, 3}");
    for (int cell = 0; cell < 5; cell++) {
      int c = cell;
      waitUntil(30, () -> window.cellViews().get(c).lookup(".cell-math") != null);
      assertThat(get(() -> layout(c))).isNotNull();
    }
    assertThat(get(() -> layout(2))).isInstanceOf(mathcanvas.engine.print.layout.Matrix.class);
    screenshot("worksheet-discrete.png");
  }
}
