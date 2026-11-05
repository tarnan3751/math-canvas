package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import mathcanvas.app.document.Welcome;
import mathcanvas.app.eval.Document;
import mathcanvas.app.text.Messages;
import mathcanvas.app.ui.CellView;
import mathcanvas.app.ui.WorksheetWindow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * The first launch: an empty data directory opens the welcome worksheet, its notes shown as text,
 * its steps run, its slider and graphs on the canvas; the slider moves the graph that reads it, the
 * steps open, and a start without the worksheet opens an empty one.
 */
class WelcomeTest extends WorksheetHarness {

  @Override
  protected boolean firstLaunch() {
    return true;
  }

  private Document document() {
    return main.document();
  }

  private int indexOf(String input) throws Exception {
    return get(
        () -> {
          List<CellView> views = window.cellViews();
          for (int k = 0; k < views.size(); k++) {
            if (views.get(k).field().getText().equals(input)) {
              return k;
            }
          }
          return -1;
        });
  }

  /** The graph a cell drew, the k-th of its plots. */
  private ObjectId graph(String input) throws Exception {
    int k = indexOf(input);
    return get(() -> document().state().plotsOf(document().state().cells().get(k).id()).getFirst());
  }

  private Geometry geometry(ObjectId id) throws Exception {
    return get(() -> document().state().canvas().geometryOf(id));
  }

  @Test
  void theFirstLaunchTeachesAndTheSecondDoesNot() throws Exception {
    List<String> cells = Welcome.cells();
    waitUntil(10, () -> window.cellViews().size() == cells.size() + 1);

    // six notes, each shown as its text in the editor's place
    waitUntil(
        10, () -> window.cellViews().stream().filter(v -> !v.noteText().isEmpty()).count() == 6);
    assertThat(get(() -> window.cellViews().getFirst().noteText()))
        .isEqualTo("Type an expression and press Enter. Try changing a number.");

    // at 1280 by 800 the canvas is shown beside the worksheet and the keypad is off, its rail
    // toggle with it, so the first three cards of the welcome are in view
    assertThat(get(() -> stage.getScene().getWidth())).isEqualTo(1280.0);
    assertThat(get(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).isSelected()))
        .isFalse();
    waitUntil(
        10,
        () -> {
          Node viewport = stage.getScene().lookup(".scroll-pane.worksheet");
          Bounds shown = viewport.localToScene(viewport.getLayoutBounds());
          for (int k = 0; k < 3; k++) {
            CellView card = window.cellViews().get(k);
            Bounds b = card.localToScene(card.getLayoutBounds());
            if (b.getMinY() < shown.getMinY() - 0.5 || b.getMaxY() > shown.getMaxY() + 0.5) {
              return false;
            }
          }
          return true;
        });

    // every step ran
    waitForOutput(indexOf("sqrt(8) + 1/3"), "2√2 + 1/3");
    waitForOutput(indexOf("f(x) := x^2 - 2x"), "f(x) := x² - 2x");
    waitForOutput(indexOf("plot(f(x))"), "y = x² - 2x");
    String sliderCell = "a := slider(-3, 3, 1)\nplot(f(x) + a)";
    waitForOutput(indexOf(sliderCell), "a := slider(-3, 3, 1)", "y = x² - 2x - 3");
    int steps = indexOf("steps(solve(f(x) = 0, x))");
    waitForOutput(steps, "{0, 2}", "Steps (2)");

    // the slider and the two graphs are on the canvas, which is shown
    waitUntil(
        30,
        () ->
            document().state().canvas().objects().stream()
                    .filter(o -> o instanceof FunctionGraph)
                    .count()
                == 2);
    ObjectId moving = graph(sliderCell);
    waitUntil(30, () -> document().state().canvas().geometryOf(moving) instanceof Geometry.Curve);
    Geometry before = geometry(moving);
    ObjectId slider =
        get(
            () ->
                document().state().canvas().objects().stream()
                    .filter(o -> o instanceof mathcanvas.canvas.model.Slider)
                    .map(CanvasObject::id)
                    .findFirst()
                    .orElseThrow());

    // drag the slider: the graph that reads it moves, and its cell shows the new equation
    fx(
        () -> {
          Slider control =
              (Slider)
                  window
                      .canvasPane()
                      .algebraPanel()
                      .row(slider)
                      .orElseThrow()
                      .lookup(".algebra-slider");
          control.setValueChanging(true);
          control.setValue(2);
          control.setValueChanging(false);
        });
    waitUntil(
        30,
        () ->
            document().state().canvas().object(slider).orElseThrow()
                    instanceof mathcanvas.canvas.model.Slider s
                && s.value().compareTo(BigDecimal.TWO) == 0);
    waitForOutput(indexOf(sliderCell), "a := slider(-3, 3, 1)", "y = x² - 2x + 2");
    waitUntil(30, () -> !document().state().canvas().geometryOf(moving).equals(before));

    // the steps open
    fx(() -> ((TitledPane) window.cellViews().get(steps).lookup(".cell-steps")).setExpanded(true));
    assertThat(
            get(
                () ->
                    ((TitledPane) window.cellViews().get(steps).lookup(".cell-steps"))
                        .isExpanded()))
        .isTrue();
    screenshot("welcome.png");

    // a second start without the worksheet: an empty worksheet, the welcome not shown again
    fx(
        () -> {
          main.stop();
          stage.close();
        });
    java.nio.file.Files.delete(directory.resolve("session.json"));
    main = new Main();
    fx(
        () -> {
          stage = new Stage();
          window = main.start(stage, directory);
        });
    WorksheetWindow again = window;
    assertThat(get(() -> again.cellViews().size())).isEqualTo(1);
    // the input bar keeps its short prompt; the example to try is a line under it
    assertThat(get(() -> again.cellViews().getFirst().field().getPromptText()))
        .isEqualTo(Messages.text("cell.prompt"));
    assertThat(get(() -> again.cellViews().getFirst().exampleText()))
        .isEqualTo(Messages.text("cell.prompt.empty"));
    // the canvas, shown since the welcome, is empty: its hint says what to do
    assertThat(get(() -> stage.getScene().lookup("#canvas-empty").isVisible())).isTrue();
    screenshot("welcome-empty.png");
  }
}
