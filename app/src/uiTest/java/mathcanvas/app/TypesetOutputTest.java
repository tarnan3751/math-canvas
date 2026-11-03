package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import mathcanvas.app.ui.CellView;
import mathcanvas.app.ui.Copy;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.MathView;
import mathcanvas.render.typeset.Box;
import org.junit.jupiter.api.Test;

/**
 * Results are typeset: a value is a {@code MathView} of its layout, a click selects a
 * subexpression, and Copy puts it on the clipboard as Unicode and LaTeX.
 */
class TypesetOutputTest extends WorksheetHarness {

  private MathView view(int cell) {
    return (MathView) window.cellViews().get(cell).lookup(".cell-math");
  }

  /** The layout the worker typeset the view's box tree from. */
  private Layout layout(int cell) {
    return (Layout) view(cell).getProperties().get(CellView.LAYOUT);
  }

  @Test
  void aValueIsTypesetAsItsLayout() throws Exception {
    enter("1/3 + 1/6");
    waitForOutput(0, "1/2");

    assertThat(get(() -> layout(0))).isInstanceOf(Fraction.class);
    // the worker typeset it: the view shows its box without a layout of its own
    assertThat(get(() -> view(0).getLayout())).isNull();
    assertThat(get(() -> view(0).box().orElseThrow().width())).isPositive();
    enter("f(x) := x^2 + 1");
    waitForOutput(1, "f(x) := x² + 1");
    assertThat(get(() -> view(1).box())).isPresent();
    enter("[[1, 2], [3, 4]]^-1");
    enter("sqrt(8) + x/(2 pi)");
    waitUntil(
        30,
        () ->
            window.cellViews().get(3).outputTexts().size() == 1
                && window.cellViews().get(3).lookup(".cell-math") != null);
    screenshot("worksheet-typeset.png");
  }

  @Test
  void unitsAreSetUpright() throws Exception {
    enter("5 _km/_h");
    waitForOutput(0, "5 _km/_h");
    enter("convert(5 _km/_h, _m/_s)");
    waitForOutput(1, "25/18 _m/_s");
    enter("20 _degC + 10 _degF");
    waitForOutput(2, "230/9 _degC");
    assertThat(get(() -> layout(0).toString())).contains("UnitText[shown=km").contains("shown=h");
    assertThat(get(() -> view(0).box().orElseThrow().width())).isPositive();
    screenshot("worksheet-units.png");
  }

  @Test
  void aClickSelectsASubexpressionAndCopyPutsItOnTheClipboard() throws Exception {
    enter("x/(2 pi)");
    waitForOutput(0, "x/(2π)");

    Path denominator =
        get(
            () -> {
              MathView v = view(0);
              Path den = ((Fraction) layout(0)).den().path();
              Box box = v.box().orElseThrow().find(den).orElseThrow();
              Point2D origin = v.origin();
              double x = origin.getX() + box.x() + box.width() / 2;
              double y = origin.getY() + (box.top() + box.bottom()) / 2;
              Point2D scene = v.localToScene(x, y);
              Point2D screen = v.localToScreen(x, y);
              v.fireEvent(
                  new MouseEvent(
                      MouseEvent.MOUSE_CLICKED,
                      scene.getX(),
                      scene.getY(),
                      screen.getX(),
                      screen.getY(),
                      MouseButton.PRIMARY,
                      1,
                      false,
                      false,
                      false,
                      false,
                      false,
                      false,
                      false,
                      true,
                      false,
                      true,
                      null));
              return den;
            });
    // a click selects the deepest subexpression under it, here inside the denominator
    Path clicked = get(() -> view(0).getSelection());
    assertThat(clicked.indices()).startsWith(denominator.indices().toArray(Integer[]::new));
    fx(() -> view(0).select(denominator));
    screenshot("worksheet-selected.png");

    fx(
        () ->
            ((ContextMenu) view(0).getProperties().get(CellView.MENU))
                .getItems()
                .getFirst()
                .fire());
    assertThat(get(() -> Clipboard.getSystemClipboard().getString())).isEqualTo("2π");
    assertThat(get(() -> (String) Clipboard.getSystemClipboard().getContent(Copy.LATEX)))
        .contains("\\pi");

    // nothing selected: the whole value
    fx(() -> view(0).select(null));
    fx(
        () ->
            ((ContextMenu) view(0).getProperties().get(CellView.MENU))
                .getItems()
                .getFirst()
                .fire());
    assertThat(get(() -> Clipboard.getSystemClipboard().getString())).isEqualTo("x/(2π)");
  }

  @Test
  void aGeneralSolutionShowsItsParameterNote() throws Exception {
    enter("solve(sin(x) = 1/2, x)");
    waitForOutput(0, "{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ");
    screenshot("worksheet-general-solution.png");
    // the parameter is an integer in the cells after it
    enter("simplify(cos(2π k))");
    waitForOutput(1, "1");
    enter("solve(x^2 = 4, x)");
    waitForOutput(2, "{-2, 2}");
  }
}
