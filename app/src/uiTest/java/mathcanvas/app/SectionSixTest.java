package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.Test;

/**
 * Every example of {@code PLAN.md} section 6 in Phase 2's scope runs end to end through the
 * worksheet, and the menus reach the toolbar's actions.
 */
class SectionSixTest extends WorksheetHarness {

  /** Input and what the cell shows, in order. */
  private static final List<Map.Entry<String, String>> EXAMPLES =
      List.of(
          Map.entry("2π", "2π"),
          Map.entry("1/3 + 1/6", "1/2"),
          Map.entry("sqrt(8)", "2√2"),
          Map.entry("-2^2", "-4"),
          Map.entry("2^3^2", "512"),
          Map.entry("5!", "120"),
          Map.entry("20%", "1/5"),
          Map.entry("sin(30°)", "1/2"),
          Map.entry("expand((x+1)^3)", "x³ + 3x² + 3x + 1"),
          Map.entry("factor(x^4 - 1)", "(x - 1)(x + 1)(x² + 1)"),
          Map.entry("solve(x^2 - 5x + 6 = 0, x)", "{2, 3}"),
          Map.entry("d/dx (x^2 sin x)", "x² cos(x) + 2x sin(x)"),
          Map.entry("f(x) := x^2 + 1", "f(x) := x² + 1"),
          // the general solution of a trigonometric equation is Phase 6's: the call stays
          Map.entry("solve(sin(x) = 1/2, x)", "solve(sin(x) = 1/2, x)"));

  @Test
  void everyExampleRunsThroughTheWorksheet() throws Exception {
    for (Map.Entry<String, String> example : EXAMPLES) {
      enter(example.getKey());
    }
    for (int k = 0; k < EXAMPLES.size(); k++) {
      waitForOutput(k, EXAMPLES.get(k).getValue());
    }
    screenshot("worksheet-section6.png");
  }

  private MenuItem menuItem(String id) {
    MenuBar bar = (MenuBar) stage.getScene().getRoot().lookup(".menu-bar");
    return bar.getMenus().stream()
        .flatMap(m -> m.getItems().stream())
        .filter(i -> id.equals(i.getId()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void theMenusReachTheToolbarsActions() throws Exception {
    enter("a := 1");
    waitForOutput(0, "a := 1");
    assertThat(get(() -> menuItem("menu-undo").getAccelerator())).isNotNull();
    fx(() -> menuItem("menu-undo").fire());
    waitUntil(10, () -> window.cellViews().size() == 1);

    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isTrue();
    fx(() -> menuItem("menu-keypad").fire());
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isFalse();
    fx(() -> menuItem("menu-history").fire());
    assertThat(get(() -> stage.getScene().lookup("#history").isVisible())).isTrue();
    fx(() -> menuItem("menu-settings").fire());
    waitUntil(10, () -> window.settingsDialog() != null && window.settingsDialog().isShowing());
    fx(() -> window.settingsDialog().close());
  }
}
