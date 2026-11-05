package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.nio.file.Files;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import mathcanvas.app.document.Theme;
import mathcanvas.app.ui.WorksheetWindow;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.space.Box3;
import mathcanvas.canvas.space.Camera;
import mathcanvas.canvas.space.SpaceScene;
import mathcanvas.canvas.view.SpaceView;
import org.junit.jupiter.api.Test;

/**
 * The 3D view in the window (Phase 7d): the 3D toggle shows it in the canvas's place, a surface
 * typed in the algebra panel is drawn, a drag orbits and the wheel zooms, the surface's cells are
 * chosen on its row, and the camera survives a restart; screenshots {@code space-light.png} and
 * {@code space-dark.png}.
 */
class SpaceModeTest extends WorksheetHarness {

  SpaceView space() {
    return window.canvasPane().spaceView();
  }

  void showSpace() throws Exception {
    // the 3D view's toggle is a chip in the canvas card, shown with the canvas
    fx(() -> window.canvasShownProperty().set(true));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-space")).fire());
    waitUntil(10, () -> space().isVisible() && space().getWidth() > 1);
  }

  @SuppressWarnings("unchecked")
  ChoiceBox<Integer> cells(ObjectId id) {
    return (ChoiceBox<Integer>) stage.getScene().lookup("#algebra-cells-" + id.value());
  }

  /** Waits until the plane's place and viewport are the same across a few pulses. */
  void settleLayout() throws Exception {
    Object before = null;
    for (int k = 0; k < 50; k++) {
      for (int i = 0; i < 3; i++) {
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
      }
      Object now =
          get(
              () ->
                  java.util.List.of(
                      window
                          .canvasPane()
                          .view()
                          .localToScene(window.canvasPane().view().getLayoutBounds()),
                      window.canvasPane().view().getViewport()));
      if (now.equals(before)) {
        return;
      }
      before = now;
    }
    throw new AssertionError("the canvas never settled");
  }

  void enterObject(String text) throws Exception {
    fx(() -> window.canvasPane().algebraPanel().entry().requestFocus());
    enter(text);
  }

  /** Fires a mouse event at the 3D view, at a point of the view. */
  void mouseAt(javafx.event.EventType<MouseEvent> type, double sx, double sy) throws Exception {
    mouseAt(type, sx, sy, false);
  }

  /** Fires a mouse event at the 3D view, at a point of the view, still since the press or not. */
  void mouseAt(javafx.event.EventType<MouseEvent> type, double sx, double sy, boolean still)
      throws Exception {
    fx(
        () -> {
          Point2D at = space().localToScene(sx, sy);
          boolean down = type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED;
          Event.fireEvent(
              space(),
              new MouseEvent(
                  type,
                  at.getX(),
                  at.getY(),
                  0,
                  0,
                  MouseButton.PRIMARY,
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

  @Test
  void aSurfaceIsDrawnOrbitedZoomedAndItsCameraKept() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showSpace();
    assertThat(get(() -> window.canvasShownProperty().get())).isTrue();
    assertThat(get(() -> window.canvasPane().view().isVisible())).isFalse();
    assertThat(get(() -> stage.getScene().lookup("#canvas-tool-point").isVisible())).isFalse();

    enterObject("z = x^2 - y^2");
    waitUntil(30, () -> space().sceneContentProperty().get().quads(false) == 100 * 100);
    ObjectId id = get(() -> main.document().state().canvas().objects().getFirst().id());
    assertThat(get(() -> main.document().state().canvas().objects().getFirst()))
        .isInstanceOf(Surface.class);
    assertThat(get(() -> window.canvasPane().algebraPanel().shownIds())).containsExactly(id);
    write(get(() -> stage.getScene().snapshot(null)), SCREENSHOTS.resolve("space-light.png"));

    // the row's cells a side: one command
    fx(() -> cells(id).setValue(200));
    waitUntil(30, () -> space().sceneContentProperty().get().quads(false) == 200 * 200);
    fx(() -> main.document().undo());
    waitUntil(30, () -> space().sceneContentProperty().get().quads(false) == 100 * 100);

    // a drag orbits and the camera goes to the document; the wheel zooms
    Camera before = get(() -> space().getCamera());
    mouseAt(MouseEvent.MOUSE_PRESSED, 300, 300);
    mouseAt(MouseEvent.MOUSE_DRAGGED, 340, 310);
    mouseAt(MouseEvent.MOUSE_RELEASED, 340, 310);
    Camera orbited = get(() -> main.document().state().camera());
    assertThat(orbited.yaw()).isCloseTo(before.yaw() - 0.4, offset(1e-9));
    assertThat(orbited.pitch()).isCloseTo(before.pitch() + 0.1, offset(1e-9));
    fx(
        () -> {
          Point2D at = space().localToScene(300, 300);
          Event.fireEvent(
              space(),
              new ScrollEvent(
                  ScrollEvent.SCROLL,
                  at.getX(),
                  at.getY(),
                  0,
                  0,
                  false,
                  false,
                  false,
                  false,
                  false,
                  false,
                  0,
                  100,
                  0,
                  100,
                  ScrollEvent.HorizontalTextScrollUnits.NONE,
                  0,
                  ScrollEvent.VerticalTextScrollUnits.NONE,
                  0,
                  0,
                  null));
        });
    Camera zoomed = get(() -> main.document().state().camera());
    assertThat(zoomed.distance()).isLessThan(orbited.distance());
    // a pan or an orbit is no undo step: the undo takes the cells' change back, not the camera
    assertThat(get(() -> main.document().canUndoProperty().get())).isTrue();

    fx(() -> window.themeProperty().set(Theme.DARK));
    waitUntil(10, () -> space().themeProperty().get().background().red() < 0.5);
    write(get(() -> stage.getScene().snapshot(null)), SCREENSHOTS.resolve("space-dark.png"));

    // the plane again, then a restart: the surface and the camera come back
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-space")).fire());
    waitUntil(10, () -> window.canvasPane().view().isVisible() && !space().isVisible());
    // the plane's tools are back, the toolbar wraps again and the plane is resized: let it settle
    settleLayout();
    waitUntil(
        10,
        () -> main.document().state().viewport().equals(window.canvasPane().view().getViewport()));
    fx(() -> main.stop());
    assertThat(Files.readString(directory.resolve("session.json")))
        .contains("\"kind\": \"surface\"", "\"space\": {");
    Main again = new Main();
    Stage[] second = new Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      assertThat(get(() -> again.document().state().camera())).isEqualTo(zoomed);
      assertThat(get(() -> reopened[0].canvasPane().spaceView().getCamera())).isEqualTo(zoomed);
      // the 3D view itself is not remembered across a restart
      assertThat(get(() -> reopened[0].canvasPane().spaceShown())).isFalse();
      fx(() -> ((ToggleButton) second[0].getScene().lookup("#toggle-space")).fire());
      waitUntil(
          30,
          () ->
              reopened[0].canvasPane().spaceView().sceneContentProperty().get().quads(false)
                  == 100 * 100);
      WritableImage image = get(() -> second[0].getScene().snapshot(null));
      write(image, SCREENSHOTS.resolve("space-reopened.png"));
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  // Phase 7d-2: gradients, a sphere and a helix, and the export.

  @Test
  void gradientsASphereAndAHelixAreDrawnAndExported() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    showSpace();
    enterObject("grad(z = x^2 - y^2, 1, 2)");
    waitUntil(30, () -> main.document().state().canvas().objects().size() == 2);
    ObjectId surface = get(() -> main.document().state().canvas().objects().get(0).id());
    ObjectId gradient = get(() -> main.document().state().canvas().objects().get(1).id());
    waitUntil(
        30,
        () ->
            main.document().canvasFrameProperty().get().rows().containsKey(gradient)
                && main.document()
                    .canvasFrameProperty()
                    .get()
                    .rows()
                    .get(gradient)
                    .text()
                    .contains("\u27e82"));
    assertThat(get(() -> main.document().canvasFrameProperty().get().rows().get(gradient).text()))
        .isEqualTo("\u2207f at (1.0, 2.0) = \u27e82.0, -4.0\u27e9");

    // a double click on the gradient's row edits its point, [x₀, y₀], which its row reads
    fx(
        () -> {
          javafx.scene.Node row = window.canvasPane().algebraPanel().row(gradient).orElseThrow();
          Point2D c = row.localToScene(10, 10);
          Event.fireEvent(
              row,
              new MouseEvent(
                  MouseEvent.MOUSE_CLICKED,
                  c.getX(),
                  c.getY(),
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
                  null));
        });
    javafx.scene.control.TextField editor =
        get(
            () ->
                (javafx.scene.control.TextField)
                    window
                        .canvasPane()
                        .algebraPanel()
                        .row(gradient)
                        .orElseThrow()
                        .lookup(".text-field"));
    String seeded = get(editor::getText);
    assertThat(seeded).isEqualTo("[1, 2]");
    fx(
        () -> {
          editor.setText("[3, 1]");
          editor.fireEvent(new javafx.event.ActionEvent());
        });
    waitUntil(
        30,
        () ->
            main.document().state().canvas().object(gradient).orElseThrow()
                    instanceof GradientArrow g
                && mathcanvas.engine.print.AsciiPrinter.INSTANCE.print(g.x()).equals("3"));

    // the Gradient tool: a click on the surface adds a gradient there, one step
    fx(() -> ((ToggleButton) stage.getScene().lookup("#canvas-tool-gradient")).fire());
    waitUntil(30, () -> space().sceneContentProperty().get().quads(false) == 100 * 100);
    double[] at =
        get(
            () -> {
              SpaceScene scene = space().sceneContentProperty().get();
              Box3 b = scene.box();
              double[] s = new double[3];
              space().projection().project(b.toCube(0, -2), b.toCube(1, 1), b.toCube(2, 4 - 1), s);
              return s;
            });
    mouseAt(MouseEvent.MOUSE_PRESSED, at[0], at[1]);
    mouseAt(MouseEvent.MOUSE_RELEASED, at[0], at[1]);
    mouseAt(MouseEvent.MOUSE_CLICKED, at[0], at[1], true);
    waitUntil(30, () -> main.document().state().canvas().objects().size() == 3);
    GradientArrow clicked =
        (GradientArrow) get(() -> main.document().state().canvas().objects().get(2));
    assertThat(clicked.surface()).isEqualTo(surface);
    double cx =
        Double.parseDouble(mathcanvas.engine.print.AsciiPrinter.INSTANCE.print(clicked.x()));
    double cy =
        Double.parseDouble(mathcanvas.engine.print.AsciiPrinter.INSTANCE.print(clicked.y()));
    assertThat(cx).isCloseTo(-2, offset(0.1));
    assertThat(cy).isCloseTo(1, offset(0.1));
    fx(() -> main.document().undo());
    waitUntil(30, () -> main.document().state().canvas().objects().size() == 2);

    // a sphere and a helix
    enterObject("[cos(u) sin(v), sin(u) sin(v), cos(v)]");
    enterObject("[cos(t), sin(t), t/4], t in [0, 6pi]");
    waitUntil(30, () -> main.document().state().canvas().objects().size() == 4);
    waitUntil(
        30,
        () ->
            main.document()
                    .state()
                    .canvas()
                    .geometryOf(main.document().state().canvas().objects().get(3).id())
                instanceof Geometry.Path3);
    write(get(() -> stage.getScene().snapshot(null)), SCREENSHOTS.resolve("space-kinds-light.png"));
    fx(() -> window.themeProperty().set(Theme.DARK));
    waitUntil(10, () -> space().themeProperty().get().background().red() < 0.5);
    write(get(() -> stage.getScene().snapshot(null)), SCREENSHOTS.resolve("space-kinds-dark.png"));

    // File > Export: the 3D view as a PNG twice its size and as an SVG in the light theme
    java.nio.file.Path folder = directory.resolve("exports");
    Files.createDirectories(folder);
    java.nio.file.Path png = folder.resolve("space.png");
    get(() -> window.canvasExport().png(png, 2)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
    double w = get(() -> space().getWidth());
    assertThat(image.getWidth()).isEqualTo((int) Math.ceil(Math.round(w) * 2.0));
    java.nio.file.Path svg = folder.resolve("space.svg");
    get(() -> window.canvasExport().svg(svg, false)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    String text = Files.readString(svg);
    assertThat(text).startsWith("<?xml").contains("<svg", "fill=\"#ffffff\"", ">x</text>");
    Files.copy(
        png,
        SCREENSHOTS.resolve("space-export.png"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Files.copy(
        svg,
        SCREENSHOTS.resolve("space-export.svg"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }
}
