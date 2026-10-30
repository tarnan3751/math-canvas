package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

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
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Style;
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
 * algebra panel, the trace readout, the point tool and dragging, and Escape ({@link
 * WorksheetHarness} starts and drives the app). Pointer input is synthetic mouse events fired at
 * the canvas view, placed by world coordinates. Where a test needs drawn content or printed rows
 * the document has not built, it shows a frame of its own on the pane.
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
    assertThat(get(() -> window.statusLine().getText())).isEqualTo("x = 1.5, y = 2.25");
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
}
