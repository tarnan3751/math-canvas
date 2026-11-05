package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import mathcanvas.app.document.Theme;
import mathcanvas.render.MathView;
import org.junit.jupiter.api.Test;

/**
 * The window's layout keeps every label whole and squeezes nothing: at the size it opens at, with
 * the keypad; with the canvas, which takes the side panels' room so the plane keeps its width; and
 * at its narrowest.
 */
class LayoutTest extends WorksheetHarness {

  /** The key under which {@code Tooltip.install} keeps a node's tooltip in its properties. */
  private static final String TOOLTIP = "javafx.scene.control.Tooltip";

  /**
   * What is shown cut short, as {@code text → shown}: a label's text, a field's prompt wider than
   * the field, and an Objects row wider than the panel without the tooltip that shows it whole.
   */
  private List<String> truncated() throws Exception {
    return get(
        () -> {
          List<String> out = new ArrayList<>();
          collect(stage.getScene().getRoot(), out);
          return out;
        });
  }

  private static void collect(Node node, List<String> out) {
    if (!node.isVisible()) {
      return;
    }
    if (node.getStyleClass().contains("algebra-row")) {
      // an Objects row: its form fits, or the row's form carries the whole text in a tooltip
      for (Node form : node.lookupAll("StackPane")) {
        if (form instanceof Parent p
            && !p.getChildrenUnmodifiable().isEmpty()
            && p.getChildrenUnmodifiable().getFirst() instanceof Node shown
            && (shown instanceof MathView || shown instanceof Label)
            && shown.prefWidth(-1) > p.getLayoutBounds().getWidth()
            && !p.getProperties().containsKey(TOOLTIP)) {
          out.add("row " + node.getId() + " cut without its tooltip");
        }
      }
      return;
    }
    if (node instanceof TextInputControl field
        && field.getText().isEmpty()
        && field.getPromptText() != null
        && !field.getPromptText().isEmpty()) {
      Text prompt = new Text(field.getPromptText());
      prompt.setFont(field.getFont());
      double room = field.getWidth() - field.snappedLeftInset() - field.snappedRightInset();
      if (field.lookup(".content") instanceof Region content) {
        room -= content.snappedLeftInset() + content.snappedRightInset();
      }
      if (prompt.getLayoutBounds().getWidth() > room) {
        out.add("prompt " + field.getPromptText() + " → wider than its field");
      }
    }
    if (node instanceof Labeled l && l.getText() != null && !l.getText().isEmpty()) {
      Node shown = l.lookup(".text");
      if (shown instanceof Text t && !t.getText().equals(l.getText())) {
        out.add(l.getText() + " → " + t.getText());
      }
    }
    if (node instanceof Parent p) {
      for (Node child : p.getChildrenUnmodifiable()) {
        collect(child, out);
      }
    }
  }

  /** Whether the side panels (the keypad's) are stacked under the cells, not in a column. */
  private boolean sideStacked() throws Exception {
    return get(
        () -> {
          for (Node n = stage.getScene().lookup("#keypad"); n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("worksheet-column")) {
              return true;
            }
          }
          return false;
        });
  }

  private boolean keypadOn() throws Exception {
    return get(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).isSelected());
  }

  @Test
  void nothingIsCutShortOrSqueezedAtAnyWidth() throws Exception {
    enter("f(x) := x^2 - 2x");
    waitUntil(30, () -> !busyOnFx());
    settle();
    // the window opens at 1280 px: the worksheet and the keypad side by side. A screen narrower
    // than that (a CI runner at 1024 px) clamps the window, and the widths below mean nothing.
    assumeTrue(
        get(() -> stage.getScene().getWidth()) == 1280.0, "the screen is narrower than 1280 px");
    assertThat(sideStacked()).isFalse();
    assertThat(truncated()).isEmpty();
    shootBothThemes("layout-wide");

    // the canvas: the plane keeps its width, and an Objects row too wide for the panel shows its
    // whole equation in a tooltip
    fx(() -> window.canvasShownProperty().set(true));
    fx(
        () ->
            main.document()
                .submitEntry(
                    java.util.Optional.empty(),
                    "y = x^4/24 - x^3/6 + x^2/2 - x + 1 + sin(3x)/7",
                    error -> {}));
    waitUntil(30, () -> main.document().state().canvas().objects().size() == 1);
    settle();
    // with no room for the side column, showing the canvas turned the keypad off, its toggle too
    assertThat(keypadOn()).isFalse();
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isFalse();
    assertThat(get(() -> window.canvasPane().view().getWidth())).isGreaterThanOrEqualTo(420.0);
    // the tool chips take at most two rows
    long rows =
        get(
            () ->
                ((Parent) stage.getScene().lookup(".canvas-toolbar"))
                    .getChildrenUnmodifiable().stream()
                        .filter(Node::isVisible)
                        .map(n -> Math.round(n.getLayoutY()))
                        .distinct()
                        .count());
    assertThat(rows).isLessThanOrEqualTo(2L);
    assertThat(truncated()).isEmpty();
    shootBothThemes("layout-canvas");

    // turned on again, the keypad stacks under the cells in at most half the column's height
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    settle();
    assertThat(sideStacked()).isTrue();
    double column =
        get(() -> stage.getScene().lookup(".worksheet-column").getLayoutBounds().getHeight());
    assertThat(get(() -> stage.getScene().lookup(".side-area").getLayoutBounds().getHeight()))
        .isLessThanOrEqualTo(column / 2 + 1);
    assertThat(
            get(
                () ->
                    stage
                        .getScene()
                        .lookup(".scroll-pane.worksheet")
                        .getLayoutBounds()
                        .getHeight()))
        .isGreaterThanOrEqualTo(column / 2 - 1);
    assertThat(truncated()).isEmpty();
    shootBothThemes("layout-stacked");

    // hiding the worksheet gives the side panels their column back
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-worksheet")).fire());
    settle();
    assertThat(sideStacked()).isFalse();
    assertThat(truncated()).isEmpty();
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-worksheet")).fire());

    // at the least width the worksheet and the canvas keep their least widths
    fx(() -> stage.setWidth(stage.getMinWidth()));
    settle();
    assertThat(sideStacked()).isTrue();
    assertThat(
            get(
                () ->
                    stage.getScene().lookup(".scroll-pane.worksheet").getLayoutBounds().getWidth()))
        .isGreaterThanOrEqualTo(360.0);
    assertThat(get(() -> window.canvasPane().view().getWidth())).isGreaterThanOrEqualTo(420.0);
    assertThat(truncated()).isEmpty();
    // and nothing runs off the window's right edge
    for (String id : List.of("#run-all", "#canvas-pane")) {
      assertThat(
              get(
                  () ->
                      stage
                          .getScene()
                          .lookup(id)
                          .localToScene(stage.getScene().lookup(id).getLayoutBounds())
                          .getMaxX()))
          .as(id)
          .isLessThanOrEqualTo(get(() -> stage.getScene().getWidth()));
    }
    shootBothThemes("layout-narrow");
  }

  /** The cells whose outputs' viewport is shorter than what it shows, by index. */
  private List<Integer> cutOutputs() throws Exception {
    return get(
        () -> {
          List<Integer> out = new ArrayList<>();
          for (int k = 0; k < window.cellViews().size(); k++) {
            for (Node pane : window.cellViews().get(k).lookupAll(".cell-outputs")) {
              Node viewport = pane.lookup(".viewport");
              Node content = ((javafx.scene.control.ScrollPane) pane).getContent();
              if (pane.isVisible()
                  && viewport.getLayoutBounds().getHeight()
                      < content.getLayoutBounds().getHeight() - 0.5) {
                out.add(k);
              }
            }
          }
          return out;
        });
  }

  @Test
  void aLongTextWrapsAndAWideResultKeepsItsHeight() throws Exception {
    // a result over the typesetting cap is its text, wrapped; a wide typeset one scrolls sideways
    // with its bar under it, never over it
    enter("expand((x + y + z + 1)^30)");
    enter("expand((x + y + 1)^12)");
    waitUntil(120, () -> !busyOnFx() && window.cellViews().get(1).outputTexts().size() == 1);
    settle();
    assertThat(cutOutputs()).isEmpty();
    // the long text is many lines tall, all in view in its card
    assertThat(
            get(
                () ->
                    window
                        .cellViews()
                        .get(0)
                        .lookup(".cell-outputs")
                        .getLayoutBounds()
                        .getHeight()))
        .isGreaterThan(200.0);
    shootBothThemes("layout-long-results");
    // and the wide one, at the worksheet's end
    for (Theme theme : List.of(Theme.LIGHT, Theme.DARK)) {
      fx(() -> window.themeProperty().set(theme));
      settle();
      fx(
          () ->
              ((javafx.scene.control.ScrollPane) stage.getScene().lookup(".scroll-pane.worksheet"))
                  .setVvalue(1));
      settle();
      screenshot("layout-wide-result-" + (theme == Theme.LIGHT ? "light" : "dark") + ".png");
    }
    assertThat(cutOutputs()).isEmpty();
  }

  /** A screenshot in each theme, the theme left dark as the system's may be. */
  private void shootBothThemes(String name) throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    settle();
    screenshot(name + "-light.png");
    fx(() -> window.themeProperty().set(Theme.DARK));
    settle();
    screenshot(name + "-dark.png");
  }

  private void settle() throws Exception {
    for (int k = 0; k < 3; k++) {
      fx(() -> stage.getScene().getRoot().layout());
      Thread.sleep(200);
    }
  }
}
