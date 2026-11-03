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
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.space.Camera;
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
                  false,
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
}
