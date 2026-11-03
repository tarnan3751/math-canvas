package mathcanvas.canvas.view;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mathcanvas.canvas.view.UiHarness.SCREENSHOTS;
import static mathcanvas.canvas.view.UiHarness.mouse;
import static mathcanvas.canvas.view.UiHarness.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.canvas.space.Camera;
import mathcanvas.canvas.space.SpaceScene;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The 3D view in a harness window: {@code z = x² − y²} drawn in the light and the dark theme, a
 * drag orbiting on the coarse mesh and the full mesh back on release, a Shift-drag panning, the
 * wheel zooming on the coarse mesh until it settles; writes {@code space-view-light.png} and {@code
 * space-view-dark.png}.
 */
@ExtendWith(ApplicationExtension.class)
class SpaceViewTest {

  private Stage stage;
  private StackPane root;
  private CanvasView plane;
  private SpaceView view;
  private final BlockingQueue<SpaceView.Frame> frames = new LinkedBlockingQueue<>();

  @Start
  void start(Stage stage) {
    this.stage = stage;
    plane = new CanvasView();
    plane.setVisible(false);
    view = new SpaceView();
    view.setFrameListener(frames::add);
    root = new StackPane(plane, view);
    stage.setScene(new Scene(root, 800, 600));
    stage.show();
  }

  static SpaceScene saddle(CanvasTheme theme, int n) {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new Surface(
                        i,
                        Style.DEFAULT,
                        Parser.parseExpression("x^2 - y^2", Names.NONE),
                        Surface.DEFAULT_DOMAIN,
                        Surface.DEFAULT_DOMAIN,
                        n))
            .recomputeAll(
                new Samplers().over(Environment.EMPTY, Settings.DEFAULT),
                new Window(-1, 1, -1, 1),
                Recomputes.standard(),
                Cancellation.NEVER);
    return SpaceScene.build(s, theme);
  }

  private SpaceView.Frame next() throws InterruptedException {
    SpaceView.Frame f = frames.poll(10, SECONDS);
    assertThat(f).as("a frame within 10 s").isNotNull();
    return f;
  }

  /** Waits for a frame drawn coarse or full, passing frames of the other kind left from before. */
  private void awaitFrame(boolean coarse) throws InterruptedException {
    long end = System.nanoTime() + SECONDS.toNanos(10);
    while (System.nanoTime() < end) {
      SpaceView.Frame f = frames.poll(1, SECONDS);
      if (f != null && f.coarse() == coarse) {
        return;
      }
    }
    throw new AssertionError("no " + (coarse ? "coarse" : "full") + " frame within 10 s");
  }

  @Test
  void theSaddleIsDrawnAndTheCameraFollowsThePointer() throws Exception {
    waitFor(
        10,
        SECONDS,
        asyncFx(() -> view.sceneContentProperty().set(saddle(view.themeProperty().get(), 100))));
    waitForFxEvents();
    frames.clear();
    for (String theme : new String[] {"light", "dark"}) {
      waitFor(
          10,
          SECONDS,
          asyncFx(
              () -> {
                if (theme.equals("dark")) {
                  root.getStyleClass().add("dark");
                  plane.applyCss();
                }
                view.themeProperty().set(plane.theme());
                view.sceneContentProperty().set(saddle(plane.theme(), 100));
              }));
      waitForFxEvents();
      next();
      WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
      write(image, SCREENSHOTS.resolve("space-view-" + theme + ".png"));
      Color corner = image.getPixelReader().getColor(2, 2);
      assertThat(corner.getBrightness())
          .as("the %s background", theme)
          .isCloseTo(theme.equals("dark") ? 0.11 : 1.0, within(0.05));
    }

    // a drag orbits on the coarse mesh, and the release draws the full mesh
    Camera before = view.getCamera();
    frames.clear();
    waitFor(
        10,
        SECONDS,
        asyncFx(
            () -> {
              mouse(view, MouseEvent.MOUSE_PRESSED, 400, 300);
              mouse(view, MouseEvent.MOUSE_DRAGGED, 450, 280);
            }));
    awaitFrame(true);
    assertThat(waitFor(10, SECONDS, asyncFx(view::isFocused)))
        .as("a press takes the focus")
        .isTrue();
    Camera orbited = view.getCamera();
    assertThat(orbited.yaw()).isCloseTo(before.yaw() - 0.5, within(1e-9));
    assertThat(orbited.pitch()).isCloseTo(before.pitch() - 0.2, within(1e-9));
    waitFor(10, SECONDS, asyncFx(() -> mouse(view, MouseEvent.MOUSE_RELEASED, 450, 280)));
    awaitFrame(false);

    // a Shift-drag pans the target
    waitFor(
        10,
        SECONDS,
        asyncFx(
            () -> {
              mouse(view, MouseEvent.MOUSE_PRESSED, 400, 300);
              view.fireEvent(shiftDrag(view, 440, 300));
              mouse(view, MouseEvent.MOUSE_RELEASED, 440, 300);
            }));
    Camera panned = view.getCamera();
    assertThat(panned.yaw()).isEqualTo(orbited.yaw());
    assertThat(Math.hypot(panned.targetX(), panned.targetY())).isGreaterThan(0.05);

    // the wheel zooms in on the coarse mesh, and the full one comes back once it stops
    waitFor(10, SECONDS, asyncFx(() -> view.fireEvent(scroll(view, 200))));
    awaitFrame(true);
    assertThat(view.getCamera().distance())
        .isCloseTo(panned.distance() / Math.pow(1.0015, 200), within(1e-9));
    awaitFrame(false);
    assertThat(view.movingProperty().get()).isFalse();
    asyncFx(() -> root.getStyleClass().remove("dark"));
  }

  private static MouseEvent shiftDrag(SpaceView node, double x, double y) {
    var scene = node.localToScene(x, y);
    var screen = node.localToScreen(x, y);
    return new MouseEvent(
        MouseEvent.MOUSE_DRAGGED,
        scene.getX(),
        scene.getY(),
        screen.getX(),
        screen.getY(),
        MouseButton.PRIMARY,
        1,
        true,
        false,
        false,
        false,
        true,
        false,
        false,
        true,
        false,
        false,
        null);
  }

  private static Event scroll(SpaceView node, double deltaY) {
    var scene = node.localToScene(400, 300);
    var screen = node.localToScreen(400, 300);
    return new ScrollEvent(
        ScrollEvent.SCROLL,
        scene.getX(),
        scene.getY(),
        screen.getX(),
        screen.getY(),
        false,
        false,
        false,
        false,
        false,
        false,
        0,
        deltaY,
        0,
        deltaY,
        ScrollEvent.HorizontalTextScrollUnits.NONE,
        0,
        ScrollEvent.VerticalTextScrollUnits.NONE,
        0,
        0,
        null);
  }
}
