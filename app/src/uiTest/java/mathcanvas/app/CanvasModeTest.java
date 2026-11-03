package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.Theme;
import mathcanvas.app.eval.AlgebraRow;
import mathcanvas.app.eval.CanvasFrame;
import mathcanvas.app.eval.Document;
import mathcanvas.app.ui.AlgebraPanel;
import mathcanvas.app.ui.CanvasPane;
import mathcanvas.app.ui.WorksheetWindow;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.BoxPlot;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.ResidualPlot;
import mathcanvas.canvas.model.ScatterPlot;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.view.CanvasView;
import mathcanvas.canvas.view.PlotContent;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The canvas mode of the window: the canvas shown beside the cells, its viewport and theme, the
 * algebra panel, the trace readout, the point tool and dragging, the analysis tools, and Escape
 * ({@link WorksheetHarness} starts and drives the app). Pointer input is synthetic mouse events
 * fired at the canvas view, placed by world coordinates. Where a test needs drawn content or
 * printed rows the document has not built, it shows a frame of its own on the pane.
 */
class CanvasModeTest extends WorksheetHarness {

  // --- driving the canvas

  Document document() {
    return main.document();
  }

  CanvasPane pane() {
    return window.canvasPane();
  }

  CanvasView view() {
    return window.canvasPane().view();
  }

  AlgebraPanel panel() {
    return window.canvasPane().algebraPanel();
  }

  void showCanvas() throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-canvas")).fire());
    waitUntil(10, () -> view().getScene() != null && view().getWidth() > 1);
    waitUntil(10, () -> document().state().viewport().equals(view().getViewport()));
  }

  MenuItem menuItem(String id) {
    MenuBar bar = (MenuBar) stage.getScene().getRoot().lookup(".menu-bar");
    return bar.getMenus().stream()
        .flatMap(m -> m.getItems().stream())
        .filter(i -> id.equals(i.getId()))
        .findFirst()
        .orElseThrow();
  }

  /** Fires a mouse event at the view, at a point of the plane as the view shows it now. */
  void mouse(EventType<MouseEvent> type, double wx, double wy, boolean still) throws Exception {
    Viewport v = get(() -> view().getViewport());
    mouseAt(type, v.toScreenX(wx), v.toScreenY(wy), still);
  }

  /** Fires a mouse event at the view, at a point of the view. */
  void mouseAt(EventType<MouseEvent> type, double sx, double sy, boolean still) throws Exception {
    fx(
        () -> {
          Point2D at = view().localToScene(sx, sy);
          boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
          Event.fireEvent(
              view(),
              new MouseEvent(
                  type,
                  at.getX(),
                  at.getY(),
                  0,
                  0,
                  type == MouseEvent.MOUSE_MOVED ? MouseButton.NONE : MouseButton.PRIMARY,
                  1,
                  false,
                  false,
                  false,
                  false,
                  down,
                  false,
                  false,
                  true,
                  false,
                  still,
                  null));
        });
  }

  /** Clicks the view at a point of the plane: a press, a release and a click. */
  void clickAt(double wx, double wy) throws Exception {
    mouse(MouseEvent.MOUSE_PRESSED, wx, wy, true);
    mouse(MouseEvent.MOUSE_RELEASED, wx, wy, true);
    mouse(MouseEvent.MOUSE_CLICKED, wx, wy, true);
  }

  /**
   * Drags on the view from one point of the plane to another, as the view shows them when the drag
   * starts, in four moves.
   */
  void drag(double fromX, double fromY, double toX, double toY) throws Exception {
    drag(fromX, fromY, toX, toY, 4);
  }

  /** Drags in a number of moves. */
  void drag(double fromX, double fromY, double toX, double toY, int moves) throws Exception {
    Viewport v = get(() -> view().getViewport());
    double ax = v.toScreenX(fromX);
    double ay = v.toScreenY(fromY);
    double bx = v.toScreenX(toX);
    double by = v.toScreenY(toY);
    mouseAt(MouseEvent.MOUSE_PRESSED, ax, ay, true);
    for (int k = 1; k <= moves; k++) {
      double t = k / (double) moves;
      mouseAt(MouseEvent.MOUSE_DRAGGED, ax + t * (bx - ax), ay + t * (by - ay), false);
    }
    mouseAt(MouseEvent.MOUSE_RELEASED, bx, by, false);
  }

  /** Runs a command on the document and returns the id of the object it added. */
  ObjectId add(Function<ObjectId, ? extends CanvasObject> make, String name) throws Exception {
    return get(
        () -> {
          ObjectId id = document().state().canvas().nextObjectId();
          document().execute(new Command.AddObject(make, Optional.ofNullable(name)));
          return id;
        });
  }

  /** Shows a frame of the document's canvas now, drawn and listed as given. */
  void showFrame(PlotContent content, Map<Integer, ObjectId> items, Map<ObjectId, AlgebraRow> rows)
      throws Exception {
    fx(
        () ->
            window
                .canvasPane()
                .show(new CanvasFrame(document().state().canvas(), content, items, rows)));
  }

  static AlgebraRow row(String text) {
    return new AlgebraRow(text, null, Optional.empty());
  }

  /** The parabola y = x² from -3 to 3, sampled every 0.01. */
  static PlotContent.Curve parabola() {
    int n = 601;
    double[] xs = new double[n];
    double[] ys = new double[n];
    for (int k = 0; k < n; k++) {
      xs[k] = -3 + k * 0.01;
      ys[k] = xs[k] * xs[k];
    }
    return new PlotContent.Curve(xs, ys, Stroke.solid(CanvasTheme.LIGHT.series().get(0), 2));
  }

  static PlotContent.Marker marker(double x, double y) {
    return new PlotContent.Marker(x, y, 4, Rgba.rgb(0x1f77b4));
  }

  /**
   * Waits for two pulses, so the view has painted what changed: a snapshot lays the scene out but
   * does not run the post-layout pulse in which the view paints its layers.
   */
  void painted() throws Exception {
    for (int k = 0; k < 2; k++) {
      java.util.concurrent.CountDownLatch pulse = new java.util.concurrent.CountDownLatch(1);
      fx(
          () -> {
            Runnable[] once = new Runnable[1];
            once[0] =
                () -> {
                  stage.getScene().removePostLayoutPulseListener(once[0]);
                  pulse.countDown();
                };
            stage.getScene().addPostLayoutPulseListener(once[0]);
            javafx.application.Platform.requestNextPulse();
          });
      assertThat(pulse.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
  }

  /** Writes a snapshot of one node under the screenshots directory. */
  void screenshotOf(Node node, String name) throws Exception {
    WritableImage image = get(() -> node.snapshot(null, null));
    write(image, SCREENSHOTS.resolve(name));
  }

  /** Types a text into the algebra panel's blank row and submits it. */
  void enterObject(String text) throws Exception {
    fx(() -> panel().entry().requestFocus());
    enter(text);
  }

  /** Drags a slider row's control to a value. */
  void slide(ObjectId slider, double value) throws Exception {
    fx(
        () -> {
          Slider control = (Slider) panel().row(slider).orElseThrow().lookup(".algebra-slider");
          control.setValueChanging(true);
          control.setValue(value);
          control.setValueChanging(false);
        });
  }

  /** The slider of a variable, once the document has it. */
  ObjectId sliderOf(String variable) throws Exception {
    waitUntil(
        30,
        () ->
            document().state().canvas().objects().stream()
                .anyMatch(
                    o ->
                        o instanceof mathcanvas.canvas.model.Slider s
                            && s.variable().equals(variable)));
    return get(
        () ->
            document().state().canvas().objects().stream()
                .filter(
                    o ->
                        o instanceof mathcanvas.canvas.model.Slider s
                            && s.variable().equals(variable))
                .findFirst()
                .orElseThrow()
                .id());
  }

  /** The ordinates of the first curve drawn, once one is. */
  double[] drawnYs() throws Exception {
    waitUntil(30, () -> !view().getContent().curves().isEmpty());
    return get(() -> view().getContent().curves().getFirst().ys().clone());
  }

  <T> T objectOf(ObjectId id, Class<T> type) throws Exception {
    return get(() -> type.cast(document().state().canvas().object(id).orElse(null)));
  }

  // --- the tests

  @Test
  void theCanvasIsShownBesideTheCellsWithItsViewport() throws Exception {
    SplitPane split = (SplitPane) stage.getScene().lookup("#split");
    assertThat(get(() -> split.getItems().size())).isEqualTo(1);

    showCanvas();
    assertThat(get(() -> split.getItems().size())).isEqualTo(2);
    assertThat(get(() -> window.canvasShownProperty().get())).isTrue();
    assertThat(get(() -> stage.getScene().lookup("#algebra"))).isNotNull();
    // the view's size went to the document, without an undo step
    assertThat(get(() -> document().state().viewport().width()))
        .isEqualTo(get(() -> (double) Math.round(view().getWidth())));
    assertThat(get(() -> document().canUndoProperty().get())).isFalse();

    // a pan moves the document's viewport, still without an undo step
    double before = get(() -> document().state().viewport().xMin());
    drag(0, 0, 2, 1);
    assertThat(get(() -> document().state().viewport().xMin())).isCloseTo(before - 2, offset(1e-9));
    assertThat(get(() -> document().canUndoProperty().get())).isFalse();
    // the aspect lock goes to the document too
    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-aspect")).fire());
    assertThat(get(() -> document().state().viewport().aspectLocked())).isFalse();

    // View > Canvas hides it again
    assertThat(get(() -> menuItem("menu-canvas").getAccelerator()))
        .isEqualTo(KeyCombination.keyCombination("Shortcut+Shift+G"));
    fx(() -> menuItem("menu-canvas").fire());
    assertThat(get(() -> split.getItems().size())).isEqualTo(1);
    assertThat(get(() -> window.canvasShownProperty().get())).isFalse();
    assertThat(get(() -> menuItem("menu-quit").getAccelerator()))
        .isEqualTo(KeyCombination.keyCombination("Shortcut+Q"));
  }

  @Test
  void theCanvasFollowsTheTheme() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    assertThat(get(() -> view().theme().axis().hex())).isEqualTo(CanvasTheme.LIGHT.axis().hex());

    enterObject("y = x^2");
    waitUntil(30, () -> !view().getContent().curves().isEmpty());
    assertThat(get(() -> view().getContent().curves().getFirst().stroke().colour().hex()))
        .isEqualTo(CanvasTheme.LIGHT.series().getFirst().hex());

    // the content is built again in the dark colours
    fx(() -> window.themeProperty().set(Theme.DARK));
    CanvasTheme dark = get(() -> view().theme());
    assertThat(dark.axis().hex()).isNotEqualTo(CanvasTheme.LIGHT.axis().hex());
    waitUntil(
        30,
        () ->
            view()
                .getContent()
                .curves()
                .getFirst()
                .stroke()
                .colour()
                .hex()
                .equals(dark.series().getFirst().hex()));
  }

  @Test
  void theReadoutShowsTheHoveredPointInTheStatusLine() throws Exception {
    showCanvas();
    // a drawn parabola, shown by hand: nothing on the canvas, so no job replaces it
    showFrame(PlotContent.of(List.of(parabola()), List.of()), Map.of(0, new ObjectId(1)), Map.of());

    mouse(MouseEvent.MOUSE_MOVED, 1.5, 2.25, false);
    // a curve with no graph behind it gives the secant of the hovered segment of its samples
    assertThat(get(() -> window.statusLine().getText()))
        .matches("x = 1\\.5, y = 2\\.25, slope = (2\\.99|3\\.0|3\\.01)");
    mouse(MouseEvent.MOUSE_EXITED, 1.5, 2.25, false);
    assertThat(get(() -> window.statusLine().getText())).isEmpty();
    // hiding the canvas while hovering clears the readout too
    mouse(MouseEvent.MOUSE_MOVED, 1.5, 2.25, false);
    assertThat(get(() -> window.statusLine().getText())).isNotEmpty();
    fx(() -> menuItem("menu-canvas").fire());
    assertThat(get(() -> window.statusLine().getText())).isEmpty();
  }

  @Test
  void thePanelListsTheObjectsAndEditsTheirStyleAndValue() throws Exception {
    showCanvas();
    ObjectId a =
        add(
            id ->
                new mathcanvas.canvas.model.Slider(
                    id,
                    Style.DEFAULT,
                    "a",
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(5),
                    new BigDecimal("0.1"),
                    BigDecimal.ONE),
            null);
    ObjectId p = add(id -> new FreePoint(id, Style.DEFAULT, 1, 2), "A");
    waitUntil(30, () -> panel().shownIds().equals(List.of(a, p)));
    assertThat(get(() -> ((Label) panel().row(a).orElseThrow().lookup(".algebra-text"))))
        .isNull(); // typeset, not text

    // the slider's control sets the value, snapped, as one undo step
    Slider control = get(() -> (Slider) panel().row(a).orElseThrow().lookup(".algebra-slider"));
    fx(
        () -> {
          control.setValueChanging(true);
          control.setValue(2.53);
          control.setValue(3.04);
          control.setValueChanging(false);
        });
    assertThat(objectOf(a, mathcanvas.canvas.model.Slider.class).value()).isEqualByComparingTo("3");
    assertThat(get(() -> control.getValue())).isEqualTo(3.0);
    fx(() -> document().undo());
    assertThat(objectOf(a, mathcanvas.canvas.model.Slider.class).value()).isEqualByComparingTo("1");
    assertThat(get(() -> control.getValue())).isEqualTo(1.0);

    // the visibility box hides the point
    fx(() -> ((CheckBox) panel().row(p).orElseThrow().lookup(".check-box")).fire());
    assertThat(objectOf(p, FreePoint.class).style().visible()).isFalse();

    // a click on a row selects its object, and a selection on the canvas selects the row
    fx(
        () ->
            Event.fireEvent(
                panel().row(p).orElseThrow(),
                new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    0,
                    0,
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
                    null)));
    assertThat(get(() -> pane().selectionProperty().get())).isEqualTo(p);
    assertThat(get(() -> panel().row(p).orElseThrow().getStyleClass())).contains("selected");
    fx(() -> pane().selectionProperty().set(a));
    assertThat(get(() -> panel().row(p).orElseThrow().getStyleClass())).doesNotContain("selected");
    assertThat(get(() -> panel().row(a).orElseThrow().getStyleClass())).contains("selected");

    // delete removes the object, and the selection with it
    fx(() -> ((Button) panel().row(a).orElseThrow().lookup(".algebra-delete")).fire());
    assertThat(get(() -> document().state().canvas().object(a))).isEmpty();
    assertThat(get(() -> pane().selectionProperty().get())).isNull();
    waitUntil(30, () -> panel().shownIds().equals(List.of(p)));
  }

  @Test
  void aRefusedTextComesBackWithItsReason() throws Exception {
    showCanvas();
    // the blank row
    enterObject("y = = 2");
    waitUntil(30, () -> panel().entryError().isVisible());
    assertThat(get(() -> panel().entry().getText())).isEqualTo("y = = 2");
    assertThat(get(() -> panel().entryError().getText())).isNotBlank();
    assertThat(get(() -> document().state().canvas().objects())).isEmpty();
    // Escape clears the row's text and its reason, and stops nothing
    press(KeyCode.ESCAPE);
    assertThat(get(() -> panel().entry().getText())).isEmpty();
    assertThat(get(() -> panel().entryError().isVisible())).isFalse();

    // a row's editor: a double click opens it, a refused text stays with the reason, Escape leaves
    ObjectId p = add(id -> new FreePoint(id, Style.DEFAULT, 1, 2), "A");
    waitUntil(30, () -> panel().shownIds().equals(List.of(p)));
    Node r = get(() -> panel().row(p).orElseThrow());
    String shown = get(() -> document().canvasFrameProperty().get().rows().get(p).text());
    fx(
        () ->
            Event.fireEvent(
                r,
                new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    0,
                    0,
                    MouseButton.PRIMARY,
                    2,
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
                    null)));
    TextField editor = get(() -> (TextField) r.lookup(".text-field"));
    assertThat(get(() -> editor.getText())).isEqualTo(shown);
    assertThat(get(() -> stage.getScene().getFocusOwner())).isSameAs(editor);
    fx(editor::clear);
    enter("y = x^2"); // a point cannot become a graph
    waitUntil(
        30,
        () ->
            r.lookup(".text-field") == editor
                && r.lookupAll(".algebra-error").stream().anyMatch(Node::isVisible));
    assertThat(get(() -> editor.getText())).isEqualTo("y = x^2");
    press(KeyCode.ESCAPE);
    assertThat(get(() -> r.lookup(".text-field"))).isNull();
    assertThat(objectOf(p, FreePoint.class).x()).isEqualTo(1);
  }

  @Test
  void thePointToolPlacesAFreePointThatUndoRemoves() throws Exception {
    showCanvas();
    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-tool-point")).fire());
    clickAt(1, 2);

    ObjectId id = get(() -> document().state().canvas().objects().getFirst().id());
    FreePoint p = objectOf(id, FreePoint.class);
    assertThat(p.x()).isCloseTo(1, offset(1e-9));
    assertThat(p.y()).isCloseTo(2, offset(1e-9));
    assertThat(get(() -> document().state().names().get(id))).isEqualTo("A");
    assertThat(get(() -> pane().selectionProperty().get())).isEqualTo(id);
    waitUntil(30, () -> panel().shownIds().equals(List.of(id)));
    clickAt(-1, 0);
    assertThat(get(() -> document().state().names().values())).containsExactly("A", "B");

    fx(() -> document().undo());
    fx(() -> document().undo());
    assertThat(get(() -> document().state().canvas().objects())).isEmpty();
    waitUntil(30, () -> panel().shownIds().isEmpty());
    // the clicks placed points, so the view did not pan
    assertThat(get(() -> document().state().viewport())).isEqualTo(get(() -> view().getViewport()));
  }

  @Test
  void aFreePointIsDraggedAsOneUndoStep() throws Exception {
    showCanvas();
    ObjectId id = add(n -> new FreePoint(n, Style.DEFAULT, 1, 1), "A");
    waitUntil(30, () -> panel().shownIds().equals(List.of(id))); // the job has delivered
    // the point drawn, shown by hand: the drag follows the point it pressed, whatever is drawn
    showFrame(PlotContent.of(List.of(), List.of(marker(1, 1))), Map.of(0, id), Map.of());
    Viewport before = get(() -> view().getViewport());

    drag(1, 1, 2, 3);
    FreePoint p = objectOf(id, FreePoint.class);
    assertThat(p.x()).isCloseTo(2, offset(1e-9));
    assertThat(p.y()).isCloseTo(3, offset(1e-9));
    assertThat(get(() -> view().getViewport())).isEqualTo(before);

    fx(() -> document().undo());
    assertThat(objectOf(id, FreePoint.class).x()).isEqualTo(1);
    fx(() -> document().undo());
    assertThat(get(() -> document().state().canvas().objects())).isEmpty();
  }

  @Test
  void aPointPlacedOnACurveIsDraggedAlongIt() throws Exception {
    showCanvas();
    enterObject("y = x^2");
    drawnYs();
    ObjectId graph = get(() -> document().state().canvas().objects().getFirst().id());

    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-tool-point")).fire());
    clickAt(1, 1.02); // within the tolerance of the parabola
    ObjectId id = get(() -> document().state().canvas().objects().getLast().id());
    PointOnPath p = objectOf(id, PointOnPath.class);
    assertThat(p.path()).isEqualTo(graph);
    assertThat(p.kind()).isEqualTo(ParamKind.X);
    assertThat(p.parameter()).isCloseTo(1, offset(0.02));
    waitUntil(30, () -> view().getContent().points().size() == 1);

    // a drag moves it along the parabola, to the parameter nearest the pointer
    drag(p.lastX(), p.lastY(), 2, 4);
    PointOnPath moved = objectOf(id, PointOnPath.class);
    assertThat(moved.parameter()).isCloseTo(2, offset(1e-2));
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(id) instanceof Geometry.Point q
                && Math.abs(q.x() - moved.parameter()) < 1e-9
                && Math.abs(q.y() - q.x() * q.x()) < 1e-9);
    fx(() -> document().undo());
    assertThat(objectOf(id, PointOnPath.class).parameter()).isEqualTo(p.parameter());
  }

  @Test
  void aSliderInACellDrivesTheCellsAndTheGraphsThatReadIt() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    enter("a := slider(0, 5, 0.1)");
    ObjectId a = sliderOf("a");
    enter("2a");
    waitForOutput(1, "0");
    enterObject("y = a x^2");
    waitUntil(30, () -> panel().shownIds().size() == 2);
    double[] flat = drawnYs();
    // the graph, the first drawn object (the slider is not drawn), takes the first series colour,
    // curve and swatch
    ObjectId graph = get(() -> panel().shownIds().get(1));
    String first = CanvasTheme.LIGHT.series().getFirst().hex();
    assertThat(get(() -> view().getContent().curves().getFirst().stroke().colour().hex()))
        .isEqualTo(first);
    assertThat(
            get(
                () -> {
                  Color c =
                      (Color)
                          ((Circle) panel().row(graph).orElseThrow().lookup(".algebra-swatch"))
                              .getFill();
                  return new Rgba(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()).hex();
                }))
        .isEqualTo(first);

    slide(a, 2.5);
    waitUntil(30, () -> window.cellViews().get(1).outputTexts().getFirst().matches("5(\\.0)?"));
    waitUntil(
        30, () -> !java.util.Arrays.equals(flat, view().getContent().curves().getFirst().ys()));
    enterObject("[1, 2]");
    waitUntil(30, () -> panel().shownIds().size() == 3);
    // the point takes the next colour
    waitUntil(30, () -> view().getContent().points().size() == 1);
    assertThat(get(() -> view().getContent().points().getFirst().colour().hex()))
        .isEqualTo(CanvasTheme.LIGHT.series().get(1).hex());
    fx(() -> pane().selectionProperty().set(panel().shownIds().get(1)));
    mouse(MouseEvent.MOUSE_MOVED, 1, 2.5, false);
    waitUntil(10, () -> window.statusLine().getText().startsWith("x = "));
    painted();
    screenshot("canvas-mode-light.png");
    screenshotOf(panel(), "canvas-algebra-panel.png");

    fx(() -> window.themeProperty().set(Theme.DARK));
    CanvasTheme dark = get(() -> view().theme());
    waitUntil(
        30,
        () ->
            view()
                .getContent()
                .curves()
                .getFirst()
                .stroke()
                .colour()
                .hex()
                .equals(dark.series().getFirst().hex()));
    painted();
    screenshot("canvas-mode-dark.png");
  }

  @Test
  void theCanvasSurvivesARestartAndIsRecomputedOnLoad() throws Exception {
    showCanvas();
    enter("a := slider(0, 5, 0.5)");
    ObjectId a = sliderOf("a");
    enterObject("y = a x^2");
    waitUntil(30, () -> panel().shownIds().size() == 2);
    slide(a, 1.5);
    add(id -> new FreePoint(id, Style.DEFAULT, 1, 2), "P");
    drag(3, -3, 4, -2); // a pan, on empty plane
    waitUntil(30, () -> !view().getContent().points().isEmpty());
    DocumentStateParts before =
        get(
            () ->
                new DocumentStateParts(
                    document().state().canvas().objects(),
                    document().state().names(),
                    document().state().viewport()));
    drawnYs();
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"functionGraph\"", "\"name\": \"P\"");

    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      // the canvas is shown again, its objects, names and viewport as they were saved
      assertThat(get(() -> reopened[0].canvasShownProperty().get())).isTrue();
      assertThat(get(() -> again.document().state().canvas().objects()))
          .isEqualTo(before.objects());
      assertThat(get(() -> again.document().state().names())).isEqualTo(before.names());
      CanvasView view = reopened[0].canvasPane().view();
      waitUntil(10, () -> view.getWidth() > 1);
      assertThat(get(() -> view.getViewport().pixelsPerUnitX()))
          .isCloseTo(before.viewport().pixelsPerUnitX(), offset(1e-9));
      // recomputed on the worker with the slider's saved value: the curve is y = 1.5x² again
      waitUntil(30, () -> !view.getContent().curves().isEmpty());
      PlotContent.Curve curve = get(() -> view.getContent().curves().getFirst());
      for (int k = 0; k < curve.xs().length; k++) {
        if (Double.isFinite(curve.ys()[k])) {
          assertThat(curve.ys()[k]).isCloseTo(1.5 * curve.xs()[k] * curve.xs()[k], offset(1e-9));
        }
      }
      waitUntil(30, () -> !view.getContent().points().isEmpty());
      assertThat(get(() -> reopened[0].cellViews().get(0).field().getText()))
          .isEqualTo("a := slider(0, 5, 0.5)");
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  /** The menu item of an id, in a top-level menu or one of its submenus. */
  MenuItem anyMenuItem(String id) {
    MenuBar bar = (MenuBar) stage.getScene().getRoot().lookup(".menu-bar");
    java.util.ArrayDeque<MenuItem> items = new java.util.ArrayDeque<>();
    bar.getMenus().forEach(items::add);
    while (!items.isEmpty()) {
      MenuItem i = items.poll();
      if (id.equals(i.getId())) {
        return i;
      }
      if (i instanceof javafx.scene.control.Menu m) {
        items.addAll(m.getItems());
      }
    }
    throw new AssertionError("no menu item " + id);
  }

  /** The vertices of an SVG path's data, in pixels. */
  static List<double[]> vertices(String d) {
    List<double[]> out = new java.util.ArrayList<>();
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("[ML](-?[0-9.]+) (-?[0-9.]+)").matcher(d);
    while (m.find()) {
      out.add(new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))});
    }
    return out;
  }

  /**
   * File → Export of a canvas with two graphs and a point: the PNG's size and pixels, and the SVG's
   * background, labels and objects. The SVG is not compared byte for byte with a golden file: its
   * pixel size is the view's, which the window's layout and fonts set, and its tick step follows
   * the label widths the view measures in the platform's font, so its bytes differ from platform to
   * platform. The golden ({@code canvas-export-objects.txt}) is instead the ordered list of the
   * object elements, each path named by the graph every vertex in the view lies on (to 0.1 pixel)
   * and the point's circle checked at its place, which holds on every platform.
   */
  @Test
  void theCanvasIsExportedAsPngAndSvg() throws Exception {
    assertThat(get(() -> anyMenuItem("menu-export").isDisable())).isTrue();
    fx(() -> window.themeProperty().set(Theme.DARK));
    showCanvas();
    assertThat(get(() -> anyMenuItem("menu-export").isDisable())).isFalse();
    assertThat(get(() -> anyMenuItem("menu-export-png").getText())).isEqualTo("PNG Image\u2026");
    enterObject("y = x^2/4");
    enterObject("y = x/2");
    waitUntil(30, () -> panel().shownIds().size() == 2);
    ObjectId p = add(id -> new FreePoint(id, new Style(2, 2, false, true, false), 1, 2), "P");
    waitUntil(
        30,
        () -> view().getContent().curves().size() == 2 && view().getContent().points().size() == 1);
    java.nio.file.Path folder = directory.resolve("exports");
    java.nio.file.Files.createDirectories(folder);

    // PNG: twice the view's pixels, in the view's (dark) theme
    java.nio.file.Path png = folder.resolve("canvas.png");
    get(() -> window.canvasExport().png(png, 2)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    Viewport v = get(() -> view().getViewport());
    java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
    assertThat(image.getWidth()).isEqualTo((int) Math.ceil(2 * v.width()));
    assertThat(image.getHeight()).isEqualTo((int) Math.ceil(2 * v.height()));
    CanvasTheme dark = get(() -> view().theme());
    assertThat(
            String.format(
                "#%06x",
                image.getRGB((int) (2 * v.toScreenX(1)), (int) (2 * v.toScreenY(2))) & 0xffffff))
        .isEqualTo(dark.series().get(2).hex());
    // the background is the commonest colour
    Map<Integer, Integer> counts = new java.util.HashMap<>();
    for (int y = 0; y < image.getHeight(); y += 7) {
      for (int x = 0; x < image.getWidth(); x += 7) {
        counts.merge(image.getRGB(x, y) & 0xffffff, 1, Integer::sum);
      }
    }
    int commonest =
        counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    assertThat(String.format("#%06x", commonest)).isEqualTo(dark.background().hex());
    java.nio.file.Files.copy(
        png,
        SCREENSHOTS.resolve("canvas-export.png"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    // SVG: the view's pixels, in the light theme by default, the objects on their curves
    java.nio.file.Path svg = folder.resolve("canvas.svg");
    get(() -> window.canvasExport().svg(svg, false)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    java.nio.file.Files.copy(
        svg,
        SCREENSHOTS.resolve("canvas-export.svg"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    org.w3c.dom.Document doc =
        javax.xml.parsers.DocumentBuilderFactory.newDefaultNSInstance()
            .newDocumentBuilder()
            .parse(svg.toFile());
    org.w3c.dom.NodeList all = doc.getDocumentElement().getElementsByTagNameNS("*", "*");
    org.w3c.dom.Element background = (org.w3c.dom.Element) all.item(0);
    assertThat(background.getAttribute("fill")).isEqualTo("#ffffff");
    assertThat(doc.getElementsByTagNameNS("*", "text").getLength()).isPositive();
    List<String> series = CanvasTheme.LIGHT.series().stream().map(Rgba::hex).toList();
    StringBuilder objects = new StringBuilder();
    Map<String, java.util.function.DoubleUnaryOperator> curves =
        Map.of("x^2/4", x -> x * x / 4, "x/2", x -> x / 2);
    for (int k = 0; k < all.getLength(); k++) {
      org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(k);
      boolean circle = e.getLocalName().equals("circle");
      String colour = circle ? e.getAttribute("fill") : e.getAttribute("stroke");
      if (!series.contains(colour)) {
        continue; // the background, the grid, the axes, the labels
      }
      if (circle) {
        objects.append("circle r=").append(e.getAttribute("r"));
        assertThat(Double.parseDouble(e.getAttribute("cx")))
            .isCloseTo(v.toScreenX(1), offset(0.01));
        assertThat(Double.parseDouble(e.getAttribute("cy")))
            .isCloseTo(v.toScreenY(2), offset(0.01));
      } else {
        // which graph the path draws: every vertex in the view lies on it, to 0.1 pixel
        List<double[]> inView =
            vertices(e.getAttribute("d")).stream()
                .filter(at -> at[0] >= 0 && at[0] <= v.width() && at[1] >= 0 && at[1] <= v.height())
                .toList();
        assertThat(inView).hasSizeGreaterThan(1);
        String on =
            curves.entrySet().stream()
                .filter(
                    c ->
                        inView.stream()
                            .allMatch(
                                at ->
                                    Math.abs(
                                            at[1]
                                                - v.toScreenY(
                                                    c.getValue().applyAsDouble(v.toWorldX(at[0]))))
                                        <= 0.1))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(
                    () -> new AssertionError("a path on no graph: " + e.getAttribute("d")));
        objects
            .append("path y = ")
            .append(on)
            .append(" width=")
            .append(e.getAttribute("stroke-width"));
      }
      objects.append('\n');
    }
    String golden;
    try (java.io.InputStream in =
        CanvasModeTest.class.getResourceAsStream("canvas-export-objects.txt")) {
      golden =
          in == null
              ? null
              : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    java.nio.file.Files.writeString(SCREENSHOTS.resolve("canvas-export-objects.txt"), objects);
    assertThat(objects.toString()).isEqualTo(golden);

    // the chooser opens where the last export went; the outcome is in the status line
    Optional<String> where = Optional.of(folder.toAbsolutePath().toString());
    java.util.concurrent.Callable<Optional<String>> saved =
        () -> {
          try {
            return mathcanvas.app.persist.SettingsFile.decode(
                    mathcanvas.app.json.JsonIo.parse(
                        java.nio.file.Files.readString(directory.resolve("settings.json"))))
                .preferences()
                .exportFolder();
          } catch (java.io.IOException | mathcanvas.app.persist.FileFormatException e) {
            return Optional.empty();
          }
        };
    org.testfx.util.WaitForAsyncUtils.waitFor(
        10, java.util.concurrent.TimeUnit.SECONDS, () -> saved.call().equals(where));
    assertThat(get(() -> window.statusLine().getText()))
        .isEqualTo("Exported the canvas to canvas.svg.");
    assertThat(objectOf(p, FreePoint.class)).isNotNull();

    // through File → Export's questions, answered here: the scales offered with their sizes, a
    // cancel that writes nothing, a name without an extension, and the current theme's box
    List<List<String>> offered = new java.util.ArrayList<>();
    List<java.nio.file.Path> askedToReplace = new java.util.ArrayList<>();
    java.util.function.Function<String, mathcanvas.app.ui.CanvasExport.Dialogs> answering =
        name ->
            new mathcanvas.app.ui.CanvasExport.Dialogs() {
              @Override
              public Optional<Integer> scale(List<String> labels) {
                offered.add(labels);
                return name == null ? Optional.empty() : Optional.of(labels.size() - 1);
              }

              @Override
              public Optional<Boolean> currentTheme() {
                return Optional.of(true);
              }

              @Override
              public Optional<java.nio.file.Path> file(
                  String title, String filter, String extension, Optional<java.io.File> start) {
                assertThat(start.map(java.io.File::toPath)).contains(folder.toAbsolutePath());
                return Optional.of(folder.resolve(name));
              }

              @Override
              public boolean confirmReplace(java.nio.file.Path file) {
                askedToReplace.add(file);
                return false;
              }
            };
    fx(() -> window.canvasExport().setDialogs(answering.apply(null)));
    assertThat(
            get(() -> window.canvasExport().choosePng())
                .get(30, java.util.concurrent.TimeUnit.SECONDS))
        .isNull();
    assertThat(offered.getFirst())
        .containsExactly(
            "1\u00d7 ("
                + (int) Math.ceil(v.width())
                + " \u00d7 "
                + (int) Math.ceil(v.height())
                + " pixels)",
            "2\u00d7 ("
                + (int) Math.ceil(2 * v.width())
                + " \u00d7 "
                + (int) Math.ceil(2 * v.height())
                + " pixels)",
            "3\u00d7 ("
                + (int) Math.ceil(3 * v.width())
                + " \u00d7 "
                + (int) Math.ceil(3 * v.height())
                + " pixels)",
            "4\u00d7 ("
                + (int) Math.ceil(4 * v.width())
                + " \u00d7 "
                + (int) Math.ceil(4 * v.height())
                + " pixels)");
    try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(folder)) {
      assertThat(files.map(f -> f.getFileName().toString()))
          .containsExactlyInAnyOrder("canvas.png", "canvas.svg");
    }
    fx(() -> window.canvasExport().setDialogs(answering.apply("chosen")));
    assertThat(
            get(() -> window.canvasExport().choosePng())
                .get(30, java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(folder.resolve("chosen.png"));
    assertThat(javax.imageio.ImageIO.read(folder.resolve("chosen.png").toFile()).getWidth())
        .isEqualTo((int) Math.ceil(4 * v.width()));
    assertThat(
            get(() -> window.canvasExport().chooseSvg())
                .get(30, java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(folder.resolve("chosen.svg"));
    assertThat(java.nio.file.Files.readString(folder.resolve("chosen.svg")))
        .contains("fill=\"" + dark.background().hex() + "\"");
    assertThat(askedToReplace).isEmpty();
    // a name with a dot but not the extension gets it too
    fx(() -> window.canvasExport().setDialogs(answering.apply("plot.v2")));
    assertThat(
            get(() -> window.canvasExport().chooseSvg())
                .get(30, java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(folder.resolve("plot.v2.svg"));
    // the extension's adding names a file that exists: replaced only if the user agrees, here not
    fx(() -> window.canvasExport().setDialogs(answering.apply("chosen")));
    java.nio.file.attribute.FileTime written =
        java.nio.file.Files.getLastModifiedTime(folder.resolve("chosen.png"));
    long size = java.nio.file.Files.size(folder.resolve("chosen.png"));
    assertThat(
            get(() -> window.canvasExport().choosePng())
                .get(30, java.util.concurrent.TimeUnit.SECONDS))
        .isNull();
    assertThat(askedToReplace).containsExactly(folder.resolve("chosen.png"));
    assertThat(java.nio.file.Files.getLastModifiedTime(folder.resolve("chosen.png")))
        .isEqualTo(written);
    assertThat(java.nio.file.Files.size(folder.resolve("chosen.png"))).isEqualTo(size);

    // a file that cannot be written: the future fails and the status line says so
    java.nio.file.Path nowhere = folder.resolve("missing").resolve("x.svg");
    java.util.concurrent.CompletableFuture<java.nio.file.Path> failed =
        get(() -> window.canvasExport().svg(nowhere, false));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> failed.get(30, java.util.concurrent.TimeUnit.SECONDS))
        .hasCauseInstanceOf(java.io.IOException.class);
    assertThat(get(() -> window.statusLine().getText()))
        .isEqualTo("The canvas could not be exported to x.svg.");
  }

  /** What a restart must keep of the canvas. */
  record DocumentStateParts(
      List<CanvasObject> objects, Map<ObjectId, String> names, Viewport viewport) {}

  @Test
  void aGraphTypedInThePanelIsRedrawnWhenEdited() throws Exception {
    showCanvas();
    enterObject("y = x^2");
    waitUntil(30, () -> panel().shownIds().size() == 1);
    double[] square = drawnYs();
    assertThat(java.util.Arrays.stream(square).filter(Double::isFinite).min().orElseThrow())
        .isGreaterThanOrEqualTo(-1e-9);

    ObjectId id = get(() -> panel().shownIds().getFirst());
    Node r = get(() -> panel().row(id).orElseThrow());
    fx(
        () ->
            Event.fireEvent(
                r,
                new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    0,
                    0,
                    MouseButton.PRIMARY,
                    2,
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
                    null)));
    String squared = get(() -> document().canvasFrameProperty().get().rows().get(id).text());
    TextField editor = get(() -> (TextField) r.lookup(".text-field"));
    assertThat(get(() -> editor.getText())).isEqualTo(squared);
    fx(editor::clear);
    enter("y = x^3");
    waitUntil(
        30,
        () ->
            java.util.Arrays.stream(view().getContent().curves().getFirst().ys())
                .anyMatch(y -> y < -1));
    assertThat(get(() -> panel().shownIds())).containsExactly(id);
    waitUntil(
        30, () -> !document().canvasFrameProperty().get().rows().get(id).text().equals(squared));
  }

  @Test
  void aPointOnTheGraphFollowsASliderAndUndoRemovesIt() throws Exception {
    showCanvas();
    enter("a := slider(0, 5, 0.1)");
    ObjectId a = sliderOf("a");
    waitUntil(30, () -> panel().row(a).isPresent());
    slide(a, 1);
    enterObject("y = a x^2");
    waitUntil(30, () -> panel().shownIds().size() == 2);
    drawnYs();

    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-tool-point")).fire());
    clickAt(1, 1);
    ObjectId p = get(() -> document().state().canvas().objects().getLast().id());
    assertThat(objectOf(p, PointOnPath.class).parameter()).isCloseTo(1, offset(0.02));
    waitUntil(30, () -> pane().frame().state().geometryOf(p) instanceof Geometry.Point);

    slide(a, 2);
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(p) instanceof Geometry.Point q
                && Math.abs(q.y() - 2 * q.x() * q.x()) < 1e-9
                && q.y() > 1.5);

    fx(() -> document().undo()); // the slider
    fx(() -> document().undo()); // the point
    assertThat(get(() -> document().state().canvas().object(p))).isEmpty();
    waitUntil(30, () -> pane().frame().rows().get(p) == null && panel().row(p).isEmpty());
  }

  @Test
  void escapeOnTheCanvasStopsALongRecompute() throws Exception {
    showCanvas();
    // the engine evaluates the body before it is compiled: seconds of expansion on the worker
    enterObject("y = expand((x + p + q + r + 1)^30)");
    waitUntil(30, () -> document().state().canvas().objects().size() == 1);
    ObjectId id = get(() -> document().state().canvas().objects().getFirst().id());
    waitUntil(10, this::busyOnFx);
    Thread.sleep(300); // into the recompute
    fx(() -> view().requestFocus());
    assertThat(get(this::busyOnFx)).as("still recomputing").isTrue();
    press(KeyCode.ESCAPE);
    long stopped = System.nanoTime();
    waitUntil(30, () -> !busyOnFx());
    assertThat((System.nanoTime() - stopped) / 1_000_000).isLessThan(5_000);
    // stopped, the object waits for its next change: past the time the expansion takes, the job
    // has delivered no row for it, and the worker is free for a cell
    Thread.sleep(8_000);
    assertThat(get(() -> pane().frame().rows().get(id))).isNull();
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    enter("1 + 1");
    waitForOutput(0, "2");
    assertThat(get(() -> pane().frame().rows().get(id))).isNull();
  }

  @Test
  void escapeOnTheCanvasStopsASlowCell() throws Exception {
    enter("expand((x + y + z + w + 1)^30)");
    waitUntil(10, this::busyOnFx);
    showCanvas();
    fx(() -> view().requestFocus());
    press(KeyCode.ESCAPE);
    waitUntil(30, () -> !busyOnFx());
    waitForOutput(0, "(cancelled)");
  }

  @Test
  void thePointToolPlacesAPointOnACurveAtItsParameter() throws Exception {
    showCanvas();
    Expr body = Parser.parseExpression("x^2", Names.NONE);
    ObjectId graph =
        add(
            n -> new FunctionGraph(n, Style.DEFAULT, body, Symbol.of("x"), FunctionGraph.ALL),
            null);
    waitUntil(30, () -> panel().shownIds().equals(List.of(graph))); // the job has delivered
    // the parabola sampled and drawn, shown by hand
    PlotContent.Curve drawn = parabola();
    Geometry.Curve sampled =
        new Geometry.Curve(
            new Path.Parametrised(
                new Parametrisation.Graph(body, Symbol.of("x")),
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                ParamKind.X),
            List.of(new Polyline(drawn.xs(), drawn.xs(), drawn.ys())));
    fx(
        () -> {
          CanvasState c = document().state().canvas();
          pane()
              .show(
                  new CanvasFrame(
                      new CanvasState(c.objects(), Map.of(graph, sampled), c.nextId()),
                      PlotContent.of(List.of(drawn), List.of()),
                      Map.of(0, graph),
                      Map.of()));
        });

    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-tool-point")).fire());
    clickAt(1, 1.02); // within the tolerance of the parabola
    ObjectId id = get(() -> document().state().canvas().objects().getLast().id());
    PointOnPath p = objectOf(id, PointOnPath.class);
    assertThat(p.path()).isEqualTo(graph);
    assertThat(p.kind()).isEqualTo(ParamKind.X);
    assertThat(p.parameter()).isCloseTo(1, offset(0.02));
    assertThat(p.lastY()).isCloseTo(p.lastX() * p.lastX(), offset(1e-3));
    assertThat(get(() -> document().state().names().get(id))).isEqualTo("A");
    fx(() -> document().undo());
    assertThat(get(() -> document().state().canvas().object(id))).isEmpty();
  }

  // --- the analysis tools

  void chooseTool(String id) throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#" + id)).fire());
  }

  /** The objects of a kind on the canvas now, in drawing order. */
  <T> List<T> objectsOf(Class<T> type) throws Exception {
    return get(
        () ->
            document().state().canvas().objects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList());
  }

  /** The point a point object is drawn at, once it is. */
  Geometry.Point drawnAt(ObjectId id) throws Exception {
    waitUntil(30, () -> pane().frame().state().geometryOf(id) instanceof Geometry.Point);
    return get(() -> (Geometry.Point) pane().frame().state().geometryOf(id));
  }

  /** A line's slope as drawn, NaN until it is. */
  double slopeOf(ObjectId line) throws Exception {
    return get(
        () ->
            pane().frame().state().geometryOf(line) instanceof Geometry.Curve c
                    && c.path() instanceof Path.Parametrised p
                    && p.parametrisation() instanceof Parametrisation.Linear l
                ? l.dy() / l.dx()
                : Double.NaN);
  }

  /** Enters graphs in the panel and waits until each is drawn. */
  void graphs(String... equations) throws Exception {
    for (String e : equations) {
      enterObject(e);
    }
    waitUntil(30, () -> view().getContent().curves().size() == equations.length);
  }

  @Test
  void theTangentToolDrawsTheTangentThatFollowsItsPoint() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^2");
    chooseTool("canvas-tool-tangent");

    mouse(MouseEvent.MOUSE_MOVED, 1, 1, false);
    waitUntil(10, () -> window.statusLine().getText().contains("slope = 2"));
    painted();
    screenshot("canvas-tools-tangent.png");
    clickAt(1, 1);

    List<mathcanvas.canvas.model.Line> lines = objectsOf(mathcanvas.canvas.model.Line.class);
    assertThat(lines).hasSize(1);
    ObjectId line = lines.getFirst().id();
    ObjectId point = objectsOf(PointOnPath.class).getFirst().id();
    assertThat(get(() -> document().state().names().get(point))).isEqualTo("A");
    double t = objectOf(point, PointOnPath.class).parameter();
    assertThat(t).isCloseTo(1, offset(0.02));
    waitUntil(30, () -> Math.abs(slopeOf(line) - 2 * t) < 1e-9);

    // the point dragged along the graph to 2: the tangent follows
    chooseTool("canvas-tool-on-curve");
    Geometry.Point at = drawnAt(point);
    drag(at.x(), at.y(), 2, 4, 8);
    waitUntil(30, () -> Math.abs(objectOf(point, PointOnPath.class).parameter() - 2) < 0.02);
    waitUntil(
        30,
        () -> Math.abs(slopeOf(line) - 2 * objectOf(point, PointOnPath.class).parameter()) < 1e-6);

    // the preview in the dark theme
    fx(() -> window.themeProperty().set(Theme.DARK));
    CanvasTheme dark = get(() -> view().theme());
    waitUntil(
        30,
        () ->
            view()
                .getContent()
                .curves()
                .getFirst()
                .stroke()
                .colour()
                .hex()
                .equals(dark.series().getFirst().hex()));
    chooseTool("canvas-tool-tangent");
    mouse(MouseEvent.MOUSE_MOVED, -1, 1, false);
    waitUntil(10, () -> window.statusLine().getText().contains("slope = -2"));
    painted();
    screenshot("canvas-tools-tangent-dark.png");
  }

  /** The id of the object a printed row starts with, once the frame has one. */
  ObjectId rowStarting(String prefix) throws Exception {
    waitUntil(
        30,
        () ->
            pane().frame().rows().values().stream()
                .anyMatch(r -> r.text().startsWith(prefix) && !r.text().contains("undefined")));
    return get(
        () ->
            pane().frame().rows().entrySet().stream()
                .filter(e -> e.getValue().text().startsWith(prefix))
                .findFirst()
                .orElseThrow()
                .getKey());
  }

  @Test
  void theVectorToolsAddAndProjectVectorsThatFollowTheirPoints() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    showCanvas();
    chooseTool("canvas-tool-vector");
    clickAt(0, 0);
    clickAt(3, 2);
    ObjectId ab = rowStarting("AB = ");
    ObjectId a = get(() -> document().state().canvas().objects().getFirst().id());
    drawnAt(a);
    // the second vector starts at A again, snapped
    clickAt(0, 0);
    clickAt(4, -1);
    ObjectId ac = rowStarting("AC = ");
    assertThat(objectsOf(FreePoint.class)).hasSize(3);
    assertThat(rowOf(ab, "AB")).isEqualTo("AB = ⟨3.0, 2.0⟩, length 3.605551275");
    assertThat(rowOf(ac, "AC")).isEqualTo("AC = ⟨4.0, -1.0⟩, length 4.123105626");

    chooseTool("canvas-tool-vector-sum");
    clickAt(1.5, 1);
    clickAt(2, -0.5);
    ObjectId sum = rowStarting("AB + AC = ");
    assertThat(rowOf(sum, "AB + AC")).isEqualTo("AB + AC = ⟨7.0, 1.0⟩, length 7.071067812");
    chooseTool("canvas-tool-projection");
    clickAt(1.5, 1);
    clickAt(2, -0.5);
    ObjectId proj = rowStarting("proj_AC AB = ");
    assertThat(rowOf(proj, "proj_AC AB"))
        .isEqualTo("proj_AC AB = ⟨2.352941176, -0.5882352941⟩, component 2.42535625");
    // the arrows' shafts and heads drawn: four shafts and the sum's two guides and the drop
    waitUntil(30, () -> view().getContent().heads().size() == 5);
    assertThat(get(() -> view().getContent().curves().size())).isEqualTo(7);
    painted();
    screenshot("canvas-vectors.png");

    // B dragged to (1, 3): the figure follows, and an undo puts it back in one step
    chooseTool("canvas-tool-select");
    drag(3, 2, 1, 3, 8);
    rowStarting("AB = ⟨1.0, 3.0⟩");
    assertThat(rowOf(sum, "AB + AC")).isEqualTo("AB + AC = ⟨5.0, 2.0⟩, length 5.385164807");
    fx(() -> document().undo());
    rowStarting("AB = ⟨3.0, 2.0⟩");
    assertThat(rowOf(proj, "proj_AC AB")).startsWith("proj_AC AB = ⟨2.352941176");

    // a vector from a variable, re-anchored at B from its row
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    enter("u := [1, 2]");
    waitUntil(
        30,
        () ->
            document().state().cells().getFirst().output()
                instanceof mathcanvas.app.document.Output.Done);
    enterObject("u");
    ObjectId u = rowStarting("u = ");
    assertThat(rowOf(u, "u")).isEqualTo("u = ⟨1.0, 2.0⟩, length 2.236067977");
    ObjectId b = get(() -> document().state().canvas().objects().get(1).id());
    fx(
        () -> {
          @SuppressWarnings("unchecked")
          javafx.scene.control.ChoiceBox<Optional<ObjectId>> tail =
              (javafx.scene.control.ChoiceBox<Optional<ObjectId>>)
                  panel().row(u).orElseThrow().lookup("#algebra-tail-" + u.value());
          assertThat(tail.getItems()).contains(Optional.empty(), Optional.of(b));
          tail.setValue(Optional.of(b));
        });
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(u) instanceof Geometry.Arrow g
                && Math.abs(g.tail().x() - 3) < 1e-9
                && Math.abs(g.head().y() - 4) < 1e-9);
    painted();
    screenshot("canvas-vectors-bound.png");

    // the dark theme
    fx(() -> window.themeProperty().set(Theme.DARK));
    CanvasTheme dark = get(() -> view().theme());
    waitUntil(
        30,
        () ->
            view()
                .getContent()
                .heads()
                .getFirst()
                .colour()
                .hex()
                .equals(
                    dark.series().get(objectOf(ab, CanvasObject.class).style().colour()).hex()));
    painted();
    screenshot("canvas-vectors-dark.png");

    // saved and loaded again: the same objects, the vectors recomputed
    List<CanvasObject> before = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"arrow\"", "\"kind\": \"vectorSum\"", "\"kind\": \"projection\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(before);
      waitUntil(
          30,
          () ->
              reopened[0].canvasPane().frame().rows().get(proj) != null
                  && reopened[0]
                      .canvasPane()
                      .frame()
                      .rows()
                      .get(proj)
                      .text()
                      .startsWith("proj_AC AB = ⟨2.352941176")
                  && reopened[0]
                      .canvasPane()
                      .frame()
                      .rows()
                      .get(u)
                      .text()
                      .equals("u = ⟨1.0, 2.0⟩, length 2.236067977"));
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  /** Chooses a tool of the Geometry, Transform or Measure drop-down. */
  void chooseConstruction(String id) throws Exception {
    fx(
        () ->
            java.util.stream.Stream.of("#canvas-geometry", "#canvas-transform", "#canvas-measure")
                .map(m -> (javafx.scene.control.MenuButton) stage.getScene().lookup(m))
                .flatMap(m -> m.getItems().stream())
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .orElseThrow()
                .fire());
    settleLayout();
  }

  /**
   * Waits until the plane stops moving: a drop-down's label changes with the tool chosen, the
   * toolbar may re-wrap and the view move and resize, and a click or a drag computed from the
   * layout before that lands elsewhere. Settled when the view's place on the scene and its viewport
   * are the same across two pulses.
   */
  void settleLayout() throws Exception {
    Object before = null;
    for (int k = 0; k < 50; k++) {
      painted();
      Object now =
          get(() -> List.of(view().localToScene(view().getLayoutBounds()), view().getViewport()));
      if (now.equals(before)) {
        return;
      }
      before = now;
    }
    throw new AssertionError("the canvas never settled");
  }

  /** The free point drawn at a place, to its rounding. */
  ObjectId freePointAt(double x, double y) throws Exception {
    return objectsOf(FreePoint.class).stream()
        .filter(p -> Math.hypot(p.x() - x, p.y() - y) < 1e-9)
        .findFirst()
        .orElseThrow()
        .id();
  }

  /** Waits until a point is drawn at a place, to its rounding. */
  void drawnNear(ObjectId point, double x, double y) throws Exception {
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(point) instanceof Geometry.Point p
                && Math.hypot(p.x() - x, p.y() - y) < 1e-9);
  }

  @Test
  void theTransformationsMeasurementsAndALocusFollowTheFigure() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    showCanvas();
    chooseConstruction("canvas-tool-polygon");
    clickAt(0, 0);
    clickAt(4, 0);
    clickAt(1, 3);
    clickAt(0, 0);
    ObjectId triangle = rowStarting("polygon ABC");
    chooseConstruction("canvas-tool-line");
    clickAt(-2, -4);
    clickAt(-2, 4);
    rowStarting("x = -2");

    // the triangle reflected in x = −2: the same area
    chooseConstruction("canvas-tool-reflect");
    assertThat(
            get(
                () ->
                    ((javafx.scene.control.MenuButton) stage.getScene().lookup("#canvas-transform"))
                        .getText()))
        .isEqualTo("Transform: Reflect in a line or point");
    clickAt(2, 0);
    clickAt(-2, 1);
    ObjectId mirrored = rowStarting("image of polygon ABC");
    assertThat(rowOf(mirrored, "image of")).startsWith("image of polygon ABC, area 6, perimeter");
    // C rotated a quarter turn about A, the angle typed
    chooseConstruction("canvas-tool-rotate");
    clickAt(1, 3);
    clickAt(0, 0);
    typeNumber("pi/2");
    ObjectId turned = rowStarting("F = ");
    assertThat(rowOf(turned, "F")).isEqualTo("F = ⟨-3.0, 1.0⟩");

    // the distance AB and the triangle's area, with their labels on the canvas
    chooseConstruction("canvas-tool-distance");
    clickAt(0, 0);
    clickAt(4, 0);
    ObjectId ab = rowStarting("d(A, B)");
    assertThat(rowOf(ab, "d(A, B)")).isEqualTo("d(A, B) = 4");
    chooseConstruction("canvas-tool-measureArea");
    clickAt(2, 0);
    ObjectId area = rowStarting("area of polygon ABC");
    assertThat(rowOf(area, "area")).isEqualTo("area of polygon ABC = 6");
    waitUntil(
        30,
        () ->
            view().getContent().labels().stream()
                .map(l -> l.text())
                .toList()
                .containsAll(List.of("d = 4", "area = 6")));

    // the locus of the midpoint of C and a point P on the circle about A through B
    chooseConstruction("canvas-tool-circleCenterPoint");
    clickAt(0, 0);
    clickAt(4, 0);
    rowStarting("x² + y² = 16");
    chooseTool("canvas-tool-point");
    settleLayout();
    clickAt(0, 4);
    rowStarting("G = ");
    chooseConstruction("canvas-tool-midpoint");
    clickAt(0, 4);
    clickAt(1, 3);
    rowStarting("H = ");
    chooseConstruction("canvas-tool-locus");
    clickAt(0.5, 3.5);
    clickAt(0, 4);
    ObjectId locus = rowStarting("locus of H as G moves");
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(locus) instanceof Geometry.Curve c
                && !c.branches().isEmpty());
    painted();
    screenshot("canvas-transformations.png");

    // C dragged: the image, the locus and the area follow; an undo puts them back
    ObjectId vertex = freePointAt(1, 3);
    chooseTool("canvas-tool-select");
    // the drop-down's label shortens and the toolbar may re-wrap, moving the view: drag on the
    // layout that follows, then wait for the figure the drag makes
    settleLayout();
    drag(1, 3, 2, 5, 8);
    drawnNear(vertex, 2, 5);
    assertThat(rowOf(area, "area of polygon ABC = 10")).isEqualTo("area of polygon ABC = 10");
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(locus) instanceof Geometry.Curve c
                && !c.branches().isEmpty()
                && c.branches().stream()
                    .allMatch(
                        b ->
                            java.util.stream.IntStream.range(0, b.size())
                                .allMatch(
                                    k ->
                                        Math.abs(Math.hypot(b.x(k) - 1, b.y(k) - 2.5) - 2)
                                            < 1e-6)));
    fx(() -> document().undo());
    drawnNear(vertex, 1, 3);
    assertThat(rowOf(area, "area of polygon ABC = 6")).isEqualTo("area of polygon ABC = 6");

    fx(() -> window.themeProperty().set(Theme.DARK));
    painted();
    waitUntil(
        30, () -> view().getContent().labels().stream().anyMatch(l -> l.text().equals("d = 4")));
    painted();
    screenshot("canvas-transformations-dark.png");

    // saved and loaded again
    List<CanvasObject> objects = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains(
            "\"kind\": \"reflection\"",
            "\"kind\": \"rotation\"",
            "\"kind\": \"distance\"",
            "\"kind\": \"area\"",
            "\"kind\": \"locus\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(objects);
      waitUntil(
          30,
          () ->
              reopened[0].canvasPane().frame().rows().get(turned) != null
                  && reopened[0]
                      .canvasPane()
                      .frame()
                      .rows()
                      .get(turned)
                      .text()
                      .equals("F = ⟨-3.0, 1.0⟩")
                  && reopened[0].canvasPane().frame().state().geometryOf(locus)
                      instanceof Geometry.Curve);
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  @Test
  void theGeometryToolsBuildATriangleWithItsCircumcircle() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    showCanvas();
    // the triangle ABC by clicks, closed on A
    chooseConstruction("canvas-tool-polygon");
    assertThat(
            get(
                () ->
                    ((javafx.scene.control.MenuButton) stage.getScene().lookup("#canvas-geometry"))
                        .getText()))
        .isEqualTo("Geometry: Polygon");
    clickAt(0, 0);
    clickAt(4, 0);
    clickAt(1, 3);
    clickAt(0, 0);
    ObjectId triangle = rowStarting("polygon ABC");
    assertThat(rowOf(triangle, "polygon ABC")).startsWith("polygon ABC, area 6, perimeter ");
    // its circumcircle through the three vertices, snapped: about (2, 1), radius √5
    chooseConstruction("canvas-tool-circleThreePoints");
    clickAt(0, 0);
    clickAt(4, 0);
    clickAt(1, 3);
    ObjectId circle = rowStarting("(x - 2)²");
    assertThat(rowOf(circle, "(x")).isEqualTo("(x - 2)² + (y - 1)² = 5");
    assertThat(objectsOf(FreePoint.class)).hasSize(3);
    // the perpendicular bisector of AB by a click on nothing but its ends
    chooseConstruction("canvas-tool-perpendicularBisector");
    clickAt(0, 0);
    clickAt(4, 0);
    ObjectId bisector = rowStarting("x = 2");
    painted();
    screenshot("canvas-geometry.png");

    // C dragged to (2, 4): the circle follows; an undo puts it back in one step
    ObjectId c = freePointAt(1, 3);
    chooseTool("canvas-tool-select");
    // the drop-down's label shortens and the toolbar may re-wrap, moving the view: drag on the
    // layout that follows, then wait for the figure the drag makes
    settleLayout();
    drag(1, 3, 2, 4, 8);
    drawnNear(c, 2, 4);
    assertThat(rowOf(circle, "(x - 2)² + (y - 1.5)²")).isEqualTo("(x - 2)² + (y - 1.5)² = 6.25");
    fx(() -> document().undo());
    drawnNear(c, 1, 3);
    assertThat(rowOf(circle, "(x - 2)² + (y - 1)²")).isEqualTo("(x - 2)² + (y - 1)² = 5");
    assertThat(rowOf(bisector, "x")).isEqualTo("x = 2");

    // a tangent from inside the circle: the tool says so and adds nothing
    chooseConstruction("canvas-tool-tangents");
    int before = get(() -> document().state().canvas().objects().size());
    clickAt(2 + Math.sqrt(5), 1);
    mouse(MouseEvent.MOUSE_MOVED, 2, 1, false);
    waitUntil(
        10,
        () -> pane().toolMessage().getText().equals("No tangent: the point is inside the circle"));
    clickAt(2, 1);
    assertThat(get(() -> document().state().canvas().objects().size())).isEqualTo(before);

    // the dark theme
    chooseTool("canvas-tool-select");
    fx(() -> window.themeProperty().set(Theme.DARK));
    CanvasTheme dark = get(() -> view().theme());
    waitUntil(
        30,
        () ->
            view()
                .getContent()
                .fills()
                .getFirst()
                .colour()
                .hex()
                .equals(
                    dark.series()
                        .get(objectOf(triangle, CanvasObject.class).style().colour())
                        .withOpacity(0.25)
                        .hex()));
    painted();
    screenshot("canvas-geometry-dark.png");

    // saved and loaded again: the same objects, recomputed
    List<CanvasObject> objects = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"polygon\"", "\"kind\": \"circle\"", "\"bisector\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(objects);
      waitUntil(
          30,
          () ->
              reopened[0].canvasPane().frame().rows().get(circle) != null
                  && reopened[0]
                      .canvasPane()
                      .frame()
                      .rows()
                      .get(circle)
                      .text()
                      .equals("(x - 2)² + (y - 1)² = 5"));
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  /** The printed row of an object, once the frame has one. */
  String rowOf(ObjectId id, String prefix) throws Exception {
    waitUntil(
        30,
        () -> {
          AlgebraRow r = pane().frame().rows().get(id);
          return r != null && r.text().startsWith(prefix) && !r.text().contains("undefined");
        });
    return get(() -> pane().frame().rows().get(id).text());
  }

  /** Types a number into the canvas's entry, as a calculus tool asks. */
  void typeNumber(String text) throws Exception {
    waitUntil(10, () -> pane().coordinateEntry().isVisible());
    fx(() -> pane().coordinateEntry().requestFocus());
    enter(text);
  }

  @Test
  void theAreaToolShadesTheAreaUnderAGraphWithItsValue() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^2");
    chooseTool("canvas-tool-area");
    clickAt(1, 1);
    assertThat(get(() -> pane().coordinateEntry().getPromptText())).isEqualTo("a =");
    clickAt(0, -2);
    typeNumber("2");
    waitUntil(10, () -> !objectsOf(mathcanvas.canvas.model.ShadedArea.class).isEmpty());
    List<mathcanvas.canvas.model.ShadedArea> areas =
        objectsOf(mathcanvas.canvas.model.ShadedArea.class);
    ObjectId area = areas.getFirst().id();
    assertThat(rowOf(area, "A")).isEqualTo("A = 2.666666667");
    waitUntil(
        30,
        () ->
            view().getContent().labels().stream()
                .anyMatch(l -> l.text().equals("A = 2.666666667")));
    assertThat(get(() -> view().getContent().fills())).isNotEmpty();
    painted();
    screenshot("canvas-calculus-area.png");
    // undone in one step
    fx(() -> document().undo());
    waitUntil(10, () -> objectsOf(mathcanvas.canvas.model.ShadedArea.class).isEmpty());
  }

  @Test
  void theRiemannToolDrawsItsRectanglesForTheSliderN() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^2/4 + 1");
    chooseTool("canvas-tool-riemann");
    clickAt(1, 1.25);
    typeNumber("-2");
    typeNumber("3");
    ObjectId n = sliderOf("n");
    waitUntil(10, () -> !objectsOf(mathcanvas.canvas.model.RiemannSum.class).isEmpty());
    ObjectId sum = objectsOf(mathcanvas.canvas.model.RiemannSum.class).getFirst().id();
    // left sum of x²/4 + 1 over [−2, 3] in 4 pieces of 1.25: heights at −2, −0.75, 0.5, 1.75
    assertThat(rowOf(sum, "S")).isEqualTo("S = 7.4609375 (left, n = 4)");
    slide(n, 10);
    waitUntil(30, () -> pane().frame().rows().get(sum).text().endsWith("n = 10)"));
    // the method in its row
    fx(
        () -> {
          @SuppressWarnings("unchecked")
          javafx.scene.control.ChoiceBox<mathcanvas.engine.compile.Quadrature.Method> box =
              (javafx.scene.control.ChoiceBox<mathcanvas.engine.compile.Quadrature.Method>)
                  panel().row(sum).orElseThrow().lookup(".algebra-method");
          box.setValue(mathcanvas.engine.compile.Quadrature.Method.MIDPOINT);
        });
    waitUntil(30, () -> pane().frame().rows().get(sum).text().contains("(midpoint, n = 10)"));
    painted();
    screenshot("canvas-calculus-riemann.png");
  }

  @Test
  void theSecantToolTurnsIntoTheTangentAsHShrinks() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^2");
    chooseTool("canvas-tool-secant");
    clickAt(1, 1);
    ObjectId h = sliderOf("h");
    sliderOf("a");
    waitUntil(10, () -> objectsOf(mathcanvas.canvas.model.Line.class).size() == 2);
    List<mathcanvas.canvas.model.Line> lines = objectsOf(mathcanvas.canvas.model.Line.class);
    ObjectId secant = lines.get(0).id();
    ObjectId tangent = lines.get(1).id();
    // a = 1, h = 1: the secant through (1, 1) and (2, 4) has slope 3, the tangent 2
    waitUntil(30, () -> Math.abs(slopeOf(secant) - 3) < 1e-9);
    waitUntil(30, () -> Math.abs(slopeOf(tangent) - 2) < 1e-9);
    slide(h, 0.25);
    waitUntil(30, () -> Math.abs(slopeOf(secant) - 2.25) < 1e-9);
    painted();
    screenshot("canvas-calculus-secant.png");
  }

  /** A graph's ordinate at an abscissa as drawn, from its samples; NaN until it is. */
  double drawnY(ObjectId graph, double x) throws Exception {
    return get(
        () -> {
          if (!(pane().frame().state().geometryOf(graph) instanceof Geometry.Curve c)) {
            return Double.NaN;
          }
          for (Polyline b : c.branches()) {
            for (int k = 0; k + 1 < b.size(); k++) {
              if (b.x(k) <= x && x <= b.x(k + 1)) {
                double f = (x - b.x(k)) / (b.x(k + 1) - b.x(k));
                return b.y(k) + f * (b.y(k + 1) - b.y(k));
              }
            }
          }
          return Double.NaN;
        });
  }

  @Test
  void theTaylorToolOverlaysThePolynomialOfTheOrderN() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = sin(x)");
    chooseTool("canvas-tool-taylor");
    clickAt(1, Math.sin(1));
    typeNumber("0");
    ObjectId n = sliderOf("n");
    waitUntil(30, () -> view().getContent().curves().size() == 2);
    List<FunctionGraph> graphs = objectsOf(FunctionGraph.class);
    FunctionGraph sine = graphs.get(0);
    FunctionGraph taylor = graphs.get(1);
    // the overlay in the next series colour, its order the slider n (at 2: the polynomial x)
    assertThat(taylor.style().colour()).isNotEqualTo(sine.style().colour());
    waitUntil(30, () -> Math.abs(drawnY(taylor.id(), 1) - 1) < 1e-3);
    slide(n, 5);
    // x − x³/6 + x⁵/120 at 1
    waitUntil(30, () -> Math.abs(drawnY(taylor.id(), 1) - 0.8416666666666667) < 1e-3);
    painted();
    screenshot("canvas-calculus-taylor.png");
  }

  @Test
  void theIntersectionToolMarksWhereTwoCurvesMeet() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^2", "y = x + 2");
    chooseTool("canvas-tool-intersect");

    clickAt(0.5, 2.5); // the line first: the family is still the lower id's
    press(KeyCode.ESCAPE); // abandons the first curve
    clickAt(0.5, 2.5);
    clickAt(-1.5, 2.25);

    waitUntil(30, () -> objectsOf(mathcanvas.canvas.model.Intersection.class).size() == 2);
    List<mathcanvas.canvas.model.Intersection> found =
        objectsOf(mathcanvas.canvas.model.Intersection.class);
    assertThat(found).allSatisfy(i -> assertThat(i.first().value()).isEqualTo(1));
    assertThat(drawnAt(found.get(0).id()).x()).isCloseTo(-1, offset(1e-9));
    assertThat(drawnAt(found.get(1).id()).x()).isCloseTo(2, offset(1e-9));
    painted();
    screenshot("canvas-tools-intersections.png");

    // again: every one is already there
    clickAt(0.5, 2.5);
    clickAt(-1.5, 2.25);
    waitUntil(30, () -> pane().toolMessage().isVisible());
    assertThat(get(() -> pane().toolMessage().getText()))
        .isEqualTo("every one found is already on the canvas");
    fx(() -> document().undo());
    assertThat(objectsOf(mathcanvas.canvas.model.Intersection.class)).isEmpty();
  }

  @Test
  void theRootsAndExtremaToolsMarkAGraphsFeatures() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    graphs("y = x^3 - 3x");
    chooseTool("canvas-tool-roots");
    clickAt(0.5, 0.125 - 1.5);
    waitUntil(30, () -> objectsOf(mathcanvas.canvas.model.CriticalPoint.class).size() == 3);
    // the last root added is selected
    ObjectId lastRoot = objectsOf(mathcanvas.canvas.model.CriticalPoint.class).getLast().id();
    assertThat(get(() -> pane().selectionProperty().get())).isEqualTo(lastRoot);
    chooseTool("canvas-tool-extrema");
    clickAt(0.5, 0.125 - 1.5);
    waitUntil(30, () -> objectsOf(mathcanvas.canvas.model.CriticalPoint.class).size() == 5);

    List<mathcanvas.canvas.model.CriticalPoint> found =
        objectsOf(mathcanvas.canvas.model.CriticalPoint.class);
    double r = Math.sqrt(3);
    double[] xs = {-r, 0, r, 1, -1};
    for (int k = 0; k < xs.length; k++) {
      assertThat(drawnAt(found.get(k).id()).x()).isCloseTo(xs[k], offset(1e-9));
    }
    assertThat(get(() -> document().state().names().values()))
        .containsExactlyInAnyOrder("A", "B", "C", "D", "E");
    assertThat(get(() -> pane().selectionProperty().get())).isEqualTo(found.getLast().id());
    painted();
    screenshot("canvas-tools-roots.png");
  }

  @Test
  void theCoordinateEntryMovesAPointOnACurve() throws Exception {
    showCanvas();
    graphs("y = x^2");
    chooseTool("canvas-tool-on-curve");
    clickAt(1, 1);
    ObjectId point = objectsOf(PointOnPath.class).getFirst().id();
    waitUntil(10, () -> pane().coordinateEntry().isVisible());
    // the prompt shows the parameter the click landed on, a pixel or two from 1 on any platform
    String prompt = get(() -> pane().coordinateEntry().getPromptText());
    assertThat(prompt).startsWith("x = ");
    assertThat(Double.parseDouble(prompt.substring(prompt.indexOf('=') + 1).trim()))
        .isCloseTo(1, offset(0.1));

    fx(() -> pane().coordinateEntry().requestFocus());
    enter("3/2");
    waitUntil(30, () -> objectOf(point, PointOnPath.class).parameter() == 1.5);
    // the frame is painted a pulse after the document moves
    waitUntil(30, () -> Math.abs(drawnAt(point).y() - 2.25) < 1e-9);
    assertThat(drawnAt(point).y()).isCloseTo(2.25, offset(1e-9));

    fx(() -> pane().coordinateEntry().requestFocus());
    enter("q");
    waitUntil(30, () -> pane().toolMessage().isVisible());
    assertThat(get(() -> pane().toolMessage().getText()))
        .isEqualTo("a point on a path takes its parameter, a real number");
    chooseTool("canvas-tool-select");
    assertThat(get(() -> pane().coordinateEntry().isVisible())).isFalse();
  }

  // --- phase 7c: fields, solution curves and contour plots

  /** A field's or a contour plot's spinner on its row, set to a value. */
  void spin(ObjectId id, String kind, int value) throws Exception {
    fx(
        () -> {
          @SuppressWarnings("unchecked")
          javafx.scene.control.Spinner<Integer> spinner =
              (javafx.scene.control.Spinner<Integer>)
                  panel().row(id).orElseThrow().lookup("#algebra-" + kind + "-" + id.value());
          spinner.getValueFactory().setValue(value);
        });
  }

  /** The solution curve's polyline as drawn, once it is. */
  Polyline solutionOf(ObjectId curve) throws Exception {
    waitUntil(30, () -> pane().frame().state().geometryOf(curve) instanceof Geometry.Curve);
    return get(
        () -> ((Geometry.Curve) pane().frame().state().geometryOf(curve)).branches().getFirst());
  }

  /** Whether every vertex of a polyline within |x| ≤ 2 lies on y(x) to 1e-4. */
  static boolean on(Polyline p, java.util.function.DoubleUnaryOperator y) {
    boolean any = false;
    for (int k = 0; k < p.size(); k++) {
      if (Math.abs(p.x(k)) <= 2) {
        any = true;
        if (Math.abs(p.y(k) - y.applyAsDouble(p.x(k))) > 1e-4) {
          return false;
        }
      }
    }
    return any;
  }

  @Test
  void fieldsSolutionsAndContoursFollowTheirControlsAndAreSaved() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showCanvas();
    enterObject("dy/dx = x - y");
    waitUntil(30, () -> objectsOf(SlopeField.class).size() == 1);
    ObjectId slopes = objectsOf(SlopeField.class).getFirst().id();
    assertThat(rowOf(slopes, "y′")).isEqualTo("y′ = x - y");
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(slopes) instanceof Geometry.Field f
                && !f.marks().isEmpty());

    // the solution tool: a point and the solution through it, one undo step
    chooseTool("canvas-tool-solution");
    clickAt(0, 1);
    waitUntil(30, () -> objectsOf(SolutionCurve.class).size() == 1);
    SolutionCurve solution = objectsOf(SolutionCurve.class).getFirst();
    ObjectId point = solution.point();
    assertThat(rowOf(solution.id(), "solution")).isEqualTo("solution through A");
    // y' = x − y through (0, 1): y = x − 1 + 2e^(−x)
    waitUntil(30, () -> on(solutionOf(solution.id()), x -> x - 1 + 2 * Math.exp(-x)));

    // its point dragged, the curve follows: through (0, −1), y = x − 1
    chooseTool("canvas-tool-select");
    drag(0, 1, 0, -1);
    waitUntil(30, () -> objectOf(point, FreePoint.class).y() < -0.9);
    double y0 = objectOf(point, FreePoint.class).y();
    double x0 = objectOf(point, FreePoint.class).x();
    double c = (y0 - x0 + 1) * Math.exp(x0);
    waitUntil(30, () -> on(solutionOf(solution.id()), x -> x - 1 + c * Math.exp(-x)));
    fx(() -> document().undo());
    fx(() -> document().undo());
    waitUntil(30, () -> objectsOf(SolutionCurve.class).isEmpty());
    fx(() -> document().redo());
    fx(() -> document().redo());
    waitUntil(30, () -> objectOf(point, FreePoint.class).y() == y0);

    // a vector field and a contour plot, their density and level count on their rows
    enterObject("[-y, x]");
    enterObject("contours(x^2 + y^2, 4)");
    waitUntil(
        30,
        () -> objectsOf(VectorField.class).size() == 1 && objectsOf(ContourPlot.class).size() == 1);
    ObjectId arrows = objectsOf(VectorField.class).getFirst().id();
    ObjectId contours = objectsOf(ContourPlot.class).getFirst().id();
    assertThat(rowOf(arrows, "F")).isEqualTo("F = ⟨-y, x⟩");
    assertThat(rowOf(contours, "contours")).isEqualTo("contours of x² + y²");
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(contours) instanceof Geometry.Contours g
                && g.levels().size() == 4);
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(arrows) instanceof Geometry.Field f
                && !f.marks().isEmpty());
    int before =
        get(() -> ((Geometry.Field) pane().frame().state().geometryOf(arrows)).marks().size());
    spin(arrows, "density", 30);
    waitUntil(30, () -> objectOf(arrows, VectorField.class).density() == 30);
    waitUntil(
        30,
        () -> ((Geometry.Field) pane().frame().state().geometryOf(arrows)).marks().size() > before);
    spin(contours, "levels", 6);
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(contours) instanceof Geometry.Contours g
                && g.levels().size() == 6);
    // a change of count is one undo step
    fx(() -> document().undo());
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(contours) instanceof Geometry.Contours g
                && g.levels().size() == 4);
    painted();
    screenshot("canvas-fields-light.png");
    fx(() -> window.themeProperty().set(Theme.DARK));
    painted();
    screenshot("canvas-fields-dark.png");

    // saved with the session and read back as they were
    List<CanvasObject> objects = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains(
            "\"kind\": \"slopeField\"",
            "\"kind\": \"solutionCurve\"",
            "\"kind\": \"vectorField\"",
            "\"kind\": \"contourPlot\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(objects);
      waitUntil(
          30,
          () ->
              again.document().state().canvas().geometryOf(solution.id())
                  instanceof Geometry.Curve);
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  // --- Phase 8c: the statistical plots

  /** The text of a label of the algebra panel, once it shows one with the id. */
  String panelLabel(String id) throws Exception {
    waitUntil(30, () -> panel().lookup("#" + id) instanceof Label);
    return get(() -> ((Label) panel().lookup("#" + id)).getText());
  }

  @Test
  void theStatisticalPlotsFollowTheirCellsWithTheirSummariesAndAreSaved() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    // the keypad hidden, so the plane is wide enough to show the plots
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    showCanvas();
    enter("data := [0.5, 1, 1, 1.5, 2, 2, 2, 2.5, 3, 5]");
    enter("xs := [-5, -4.5, -4, -3, -2, -1.5]");
    enter("ys := [1, 1.5, 3, 2.5, 3.5, 4]");
    enterObject("histogram(data, 1, 0)");
    enterObject("boxplot(data, -2)");
    enterObject("scatter(xs, ys)");
    waitUntil(
        30,
        () ->
            objectsOf(Histogram.class).size() == 1
                && objectsOf(BoxPlot.class).size() == 1
                && objectsOf(ScatterPlot.class).size() == 1);
    ObjectId histogram = objectsOf(Histogram.class).getFirst().id();
    ObjectId box = objectsOf(BoxPlot.class).getFirst().id();
    ObjectId scatter = objectsOf(ScatterPlot.class).getFirst().id();
    assertThat(rowOf(histogram, "histogram")).isEqualTo("histogram of data, bins 1, from 0");
    assertThat(rowOf(box, "box plot")).isEqualTo("box plot of data at height -2");
    assertThat(rowOf(scatter, "scatter")).isEqualTo("scatter of (xs, ys)");
    // values on edges go up: 5 is in a bin of its own, after an empty one
    waitUntil(30, () -> pane().frame().state().geometryOf(histogram) instanceof Geometry.Bins);
    assertThat(
            get(
                () ->
                    ((Geometry.Bins) pane().frame().state().geometryOf(histogram))
                        .bars().stream().map(Geometry.Bar::height).toList()))
        .containsExactly(1.0, 3.0, 4.0, 1.0, 0.0, 1.0);
    waitUntil(30, () -> pane().frame().state().geometryOf(box) instanceof Geometry.Box);
    assertThat(get(() -> ((Geometry.Box) pane().frame().state().geometryOf(box)).outliers()))
        .containsExactly(5.0);

    // the Statistics heading above the objects, a summary per data cell the plots read
    assertThat(panelLabel("algebra-heading-statistics")).isEqualTo("Statistics");
    assertThat(panelLabel("algebra-statistics-data"))
        .isEqualTo("data: n = 10, mean 2.05, median 2, Q1 1, Q3 2.5, min 0.5, max 5, stdev 1.279");
    assertThat(panelLabel("algebra-statistics-xs")).startsWith("xs: n = 6, mean -3.333");
    assertThat(panelLabel("algebra-statistics-ys")).startsWith("ys: n = 6, mean 2.583");
    String top =
        get(
            () ->
                ((javafx.scene.Parent) panel().lookup("#algebra-rows"))
                    .getChildrenUnmodifiable()
                    .getFirst()
                    .getId());
    assertThat(top).isEqualTo("algebra-heading-statistics");

    // the bin-width spinner: a step to 2, one undo step back to 1
    fx(
        () -> {
          @SuppressWarnings("unchecked")
          javafx.scene.control.Spinner<Double> spinner =
              (javafx.scene.control.Spinner<Double>)
                  panel()
                      .row(histogram)
                      .orElseThrow()
                      .lookup("#algebra-binwidth-" + histogram.value());
          spinner.increment();
        });
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(histogram) instanceof Geometry.Bins b
                && b.width() == 2);
    assertThat(rowOf(histogram, "histogram")).isEqualTo("histogram of data, bins 2, from 0");
    fx(() -> document().undo());
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(histogram) instanceof Geometry.Bins b
                && b.width() == 1);
    painted();
    screenshot("stats-light.png");
    fx(() -> window.themeProperty().set(Theme.DARK));
    painted();
    screenshot("stats-dark.png");

    // a click inside a bar, far from its edges, selects the histogram, its halo drawn
    clickAt(1.5, 1.5);
    assertThat(get(() -> pane().selectionProperty().get())).isEqualTo(histogram);
    painted();
    fx(() -> pane().selectionProperty().set(null));

    // the cell changed, the plots and their summary follow
    fx(
        () -> {
          long first = document().state().cells().getFirst().id();
          document().execute(new Command.EditInput(first, "data := [3, 3, 9]"));
          document().execute(new Command.Submit(first));
        });
    waitUntil(30, () -> panelLabelNow("algebra-statistics-data").startsWith("data: n = 3,"));
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(box) instanceof Geometry.Box b
                && b.summary().n() == 3);

    // saved with the session and read back as they were
    List<CanvasObject> objects = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"histogram\"", "\"kind\": \"boxPlot\"", "\"kind\": \"scatterPlot\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(objects);
      waitUntil(
          30,
          () -> again.document().state().canvas().geometryOf(scatter) instanceof Geometry.Scatter);
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  /** Opens a row's editor by a double click and types a new text into it. */
  void editRow(ObjectId id, String text) throws Exception {
    Node r = get(() -> panel().row(id).orElseThrow());
    fx(
        () ->
            Event.fireEvent(
                r,
                new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    0,
                    0,
                    MouseButton.PRIMARY,
                    2,
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
                    null)));
    TextField editor = get(() -> (TextField) r.lookup(".text-field"));
    fx(editor::clear);
    enter(text);
  }

  @Test
  void aFitIsDrawnWithItsResidualsThatFollowTheCellsAndAreSaved() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    showCanvas();
    enter("x := [1, 2, 3, 4]");
    enter("y := [2.1, 3.9, 6.2, 7.8]");
    // the cells evaluated, so the entry's fit reads them
    waitUntil(
        30,
        () ->
            document().state().cells().size() >= 2
                && document().state().cells().get(1).output()
                    instanceof mathcanvas.app.document.Output.Done);
    enterObject("linreg(x, y)");
    waitUntil(30, () -> objectsOf(FunctionGraph.class).size() == 1);
    ObjectId line = objectsOf(FunctionGraph.class).getFirst().id();
    assertThat(rowOf(line, "y")).isEqualTo("y = 1.94x + 0.15");
    enterObject("residuals(x, y, linreg(x, y))");
    waitUntil(30, () -> objectsOf(ResidualPlot.class).size() == 1);
    ObjectId residuals = objectsOf(ResidualPlot.class).getFirst().id();
    assertThat(rowOf(residuals, "residuals")).isEqualTo("residuals of linreg(x, y)");
    waitUntil(30, () -> pane().frame().state().geometryOf(residuals) instanceof Geometry.Residuals);
    assertThat(
            get(
                () ->
                    ((Geometry.Residuals) pane().frame().state().geometryOf(residuals))
                        .points().stream().map(Geometry.Point::y).toList()))
        .satisfiesExactly(
            r -> assertThat(r).isCloseTo(0.01, within(1e-9)),
            r -> assertThat(r).isCloseTo(-0.13, within(1e-9)),
            r -> assertThat(r).isCloseTo(0.23, within(1e-9)),
            r -> assertThat(r).isCloseTo(-0.11, within(1e-9)));
    // the stems and the markers on the plane: a polyline from the axis per residual
    waitUntil(30, () -> view().getContent().points().size() == 4);

    // one undo per entry: the residuals go, then the line; redo brings both back
    fx(() -> document().undo());
    waitUntil(30, () -> objectsOf(ResidualPlot.class).isEmpty());
    assertThat(objectsOf(FunctionGraph.class)).hasSize(1);
    fx(() -> document().undo());
    waitUntil(30, () -> objectsOf(FunctionGraph.class).isEmpty());
    fx(() -> document().redo());
    fx(() -> document().redo());
    waitUntil(
        30,
        () ->
            objectsOf(FunctionGraph.class).size() == 1
                && objectsOf(ResidualPlot.class).size() == 1);
    waitUntil(30, () -> pane().frame().state().geometryOf(residuals) instanceof Geometry.Residuals);
    painted();
    screenshot("stats-fit-light.png");
    fx(() -> window.themeProperty().set(Theme.DARK));
    painted();
    screenshot("stats-fit-dark.png");

    // the rows read back: the line as its equation, the residuals of another fit
    editRow(line, "y = 1.94x + 0.15");
    editRow(residuals, "residuals of quadreg(x, y)");
    waitUntil(30, () -> rowNow(residuals).equals("residuals of quadreg(x, y)"));
    assertThat(rowOf(line, "y")).isEqualTo("y = 1.94x + 0.15");
    assertThat(get(() -> document().state().canvas().object(residuals).orElseThrow()))
        .isInstanceOfSatisfying(
            ResidualPlot.class,
            p -> assertThat(p.points()).isInstanceOf(ScatterPlot.Columns.class));

    // the cell changed: the residuals follow the data, the line keeps its coefficients
    fx(
        () -> {
          long second = document().state().cells().get(1).id();
          document().execute(new Command.EditInput(second, "y := [3, 5, 7, 9]"));
          document().execute(new Command.Submit(second));
        });
    waitUntil(
        30,
        () ->
            pane().frame().state().geometryOf(residuals) instanceof Geometry.Residuals r
                && r.points().stream().allMatch(p -> Math.abs(p.y()) < 1e-9));
    assertThat(rowOf(line, "y")).isEqualTo("y = 1.94x + 0.15");

    // saved with the session and read back as they were
    List<CanvasObject> objects = get(() -> document().state().canvas().objects());
    fx(() -> main.stop());
    assertThat(java.nio.file.Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"residualPlot\"", "\"fit\": \"quadreg(x, y)\"");
    Main again = new Main();
    javafx.stage.Stage[] second = new javafx.stage.Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new javafx.stage.Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().canvas().objects())).isEqualTo(objects);
      waitUntil(
          30,
          () ->
              again.document().state().canvas().geometryOf(residuals)
                  instanceof Geometry.Residuals);
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  /** A row's text on the FX thread, empty when there is none. */
  String rowNow(ObjectId id) {
    AlgebraRow r = pane().frame().rows().get(id);
    return r == null ? "" : r.text();
  }

  /** The text of a label of the algebra panel on the FX thread, empty when there is none. */
  String panelLabelNow(String id) {
    return panel().lookup("#" + id) instanceof Label l ? l.getText() : "";
  }
}
