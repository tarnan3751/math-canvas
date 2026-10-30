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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The canvas view in a harness window, 800 by 600 pixels, 40 pixels to a unit: its three layers,
 * which layers each change repaints (a pan and a resize all three, new content the objects, a hover
 * the overlay alone), zoom about the pointer, the grid's crisp axis lines and the light and dark
 * palettes, with the screenshots {@code canvas-grid-light.png} and {@code canvas-grid-dark.png}.
 * Events are synthetic and fired at the view; screenshots are scene snapshots.
 */
@ExtendWith(ApplicationExtension.class)
class CanvasViewTest {

  private static final int WIDTH = 800;
  private static final int HEIGHT = 600;

  private Stage stage;
  private StackPane root;
  private CanvasView view;
  private final BlockingQueue<CanvasView.Frame> frames = new LinkedBlockingQueue<>();

  @Start
  void start(Stage stage) {
    this.stage = stage;
    view = new CanvasView();
    view.setFrameListener(frames::add);
    root = new StackPane(view);
    stage.setTitle("CanvasView");
    stage.setScene(new Scene(root, WIDTH, HEIGHT));
    // The primary stage is shared by the tests, and one of them resizes it.
    stage.sizeToScene();
    stage.show();
  }

  @BeforeEach
  void settle() throws Exception {
    waitFor(10, SECONDS, () -> waitFx(() -> view.frames() > 0));
    quiet();
  }

  /** Waits until no frame has been painted for a few pulses, then forgets the frames so far. */
  private void quiet() throws Exception {
    for (int i = 0; i < 5; i++) {
      waitForFxEvents();
    }
    frames.clear();
  }

  private static boolean waitFx(java.util.concurrent.Callable<Boolean> check) throws Exception {
    return waitFor(10, SECONDS, asyncFx(check));
  }

  private CanvasView.Frame nextFrame() throws InterruptedException {
    CanvasView.Frame f = frames.poll(10, SECONDS);
    assertThat(f).as("a frame within 10 s").isNotNull();
    return f;
  }

  private static PlotContent sine() {
    return sine(CanvasTheme.LIGHT);
  }

  /** A sine curve and a point at its first maximum, in a theme's first two series colours. */
  private static PlotContent sine(CanvasTheme theme) {
    int n = 400;
    double[] xs = new double[n];
    double[] ys = new double[n];
    for (int i = 0; i < n; i++) {
      xs[i] = -10 + 20.0 * i / (n - 1);
      ys[i] = Math.sin(xs[i]) * 2;
    }
    return PlotContent.of(
        List.of(new PlotContent.Curve(xs, ys, Stroke.solid(theme.series().get(0), 2))),
        List.of(new PlotContent.Marker(Math.PI / 2, 2, 4, theme.series().get(1))));
  }

  @Test
  void hasThreeLayersTheSizeOfTheView() throws Exception {
    List<Canvas> canvases =
        waitFor(
            10,
            SECONDS,
            asyncFx(
                () -> view.getChildrenUnmodifiable().stream().map(Canvas.class::cast).toList()));
    assertThat(canvases).hasSize(3);
    for (Canvas c : canvases) {
      assertThat(c.getWidth()).isEqualTo(WIDTH);
      assertThat(c.getHeight()).isEqualTo(HEIGHT);
    }
    Viewport v = waitFor(10, SECONDS, asyncFx(view::getViewport));
    assertThat(v.width()).isEqualTo(WIDTH);
    assertThat(v.pixelsPerUnitX()).isCloseTo(40, within(1e-9));
    assertThat(v.toScreenX(0)).isCloseTo(WIDTH / 2.0, within(1e-9));
  }

  @Test
  void aPanRepaintsEveryLayer() throws Exception {
    Viewport before = waitFor(10, SECONDS, asyncFx(view::getViewport));
    asyncFx(
        () -> {
          mouse(view, MouseEvent.MOUSE_PRESSED, 400, 300);
          mouse(view, MouseEvent.MOUSE_DRAGGED, 440, 280);
        });
    CanvasView.Frame f = nextFrame();
    assertThat(f.painted()).isEqualTo(EnumSet.allOf(Layer.class));
    Viewport after = waitFor(10, SECONDS, asyncFx(view::getViewport));
    assertThat(after.toScreenX(before.toWorldX(400))).isCloseTo(440, within(1e-9));
    assertThat(after.toScreenY(before.toWorldY(300))).isCloseTo(280, within(1e-9));
    asyncFx(() -> mouse(view, MouseEvent.MOUSE_RELEASED, 440, 280));
  }

  @Test
  void contentRepaintsTheObjectsAndAHoverOnlyTheOverlay() throws Exception {
    asyncFx(() -> view.setContent(sine()));
    assertThat(nextFrame().painted()).isEqualTo(EnumSet.of(Layer.OBJECTS));
    quiet();

    // (π/2, 2) is the marker: 40 px per unit about the centre.
    double x = 400 + Math.PI / 2 * 40 + 3;
    double y = 300 - 2 * 40;
    asyncFx(() -> mouse(view, MouseEvent.MOUSE_MOVED, x, y));
    assertThat(nextFrame().painted()).isEqualTo(EnumSet.of(Layer.OVERLAY));
    Hit hit = waitFor(10, SECONDS, asyncFx(view::getHoveredHit));
    assertThat(hit).isNotNull();
    assertThat(hit.kind()).isEqualTo(Hit.Kind.POINT);
    assertThat(hit.item()).isEqualTo(1);
    quiet();

    // On the curve at x = 0, away from the marker.
    asyncFx(() -> mouse(view, MouseEvent.MOUSE_MOVED, 400, 302));
    assertThat(nextFrame().painted()).isEqualTo(EnumSet.of(Layer.OVERLAY));
    hit = waitFor(10, SECONDS, asyncFx(view::getHoveredHit));
    assertThat(hit.kind()).isEqualTo(Hit.Kind.POLYLINE);
    assertThat(hit.item()).isEqualTo(0);
    quiet();

    asyncFx(() -> mouse(view, MouseEvent.MOUSE_MOVED, 400, 100));
    assertThat(nextFrame().painted()).isEqualTo(EnumSet.of(Layer.OVERLAY));
    assertThat(waitFor(10, SECONDS, asyncFx(view::getHoveredHit))).isNull();
    quiet();
    asyncFx(() -> view.setContent(PlotContent.EMPTY));
  }

  @Test
  void scrollingZoomsAboutThePointer() throws Exception {
    Viewport before = waitFor(10, SECONDS, asyncFx(view::getViewport));
    asyncFx(
        () -> {
          var scene = view.localToScene(600, 150);
          var screen = view.localToScreen(600, 150);
          view.fireEvent(
              new ScrollEvent(
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
                  120,
                  0,
                  120,
                  ScrollEvent.HorizontalTextScrollUnits.NONE,
                  0,
                  ScrollEvent.VerticalTextScrollUnits.NONE,
                  0,
                  0,
                  null));
        });
    assertThat(nextFrame().painted()).isEqualTo(EnumSet.allOf(Layer.class));
    Viewport after = waitFor(10, SECONDS, asyncFx(view::getViewport));
    assertThat(after.pixelsPerUnitX()).isGreaterThan(before.pixelsPerUnitX());
    assertThat(after.toWorldX(600)).isCloseTo(before.toWorldX(600), within(1e-9));
    assertThat(after.toWorldY(150)).isCloseTo(before.toWorldY(150), within(1e-9));
    asyncFx(() -> view.setViewport(before));
  }

  @Test
  void aResizeRepaintsEveryLayerAndKeepsTheScale() throws Exception {
    asyncFx(() -> stage.setWidth(stage.getWidth() + 100));
    CanvasView.Frame f = nextFrame();
    assertThat(f.painted()).isEqualTo(EnumSet.allOf(Layer.class));
    Viewport v = waitFor(10, SECONDS, asyncFx(view::getViewport));
    assertThat(v.width()).isEqualTo(WIDTH + 100);
    assertThat(v.pixelsPerUnitX()).isCloseTo(40, within(1e-9));
  }

  @Test
  void drawsTheGridInBothThemesWithCrispAxes() throws Exception {
    asyncFx(() -> view.setContent(sine()));
    quiet();
    WritableImage light = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(light, SCREENSHOTS.resolve("canvas-grid-light.png"));
    CanvasTheme lightTheme = waitFor(10, SECONDS, asyncFx(view::theme));
    assertThat(hexes(lightTheme)).isEqualTo(hexes(CanvasTheme.LIGHT));
    assertCrispAxes(light, lightTheme.axis());

    // The dark palette comes from the user-agent stylesheet's .root.dark .canvas-view rule.
    asyncFx(() -> root.getStyleClass().add("dark"));
    CanvasView.Frame f = nextFrame();
    assertThat(f.painted()).isEqualTo(EnumSet.allOf(Layer.class));
    quiet();
    CanvasTheme darkTheme = waitFor(10, SECONDS, asyncFx(view::theme));
    assertThat(darkTheme.axis().hex()).isEqualTo("#c7c7cc");
    assertThat(darkTheme.series().getFirst().hex()).isEqualTo("#4ea3f0");
    asyncFx(() -> view.setContent(sine(darkTheme)));
    quiet();
    WritableImage dark = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(dark, SCREENSHOTS.resolve("canvas-grid-dark.png"));
    assertCrispAxes(dark, darkTheme.axis());
    assertThat(Path.of("build", "ui-screenshots", "canvas-grid-dark.png")).isNotEmptyFile();

    asyncFx(
        () -> {
          root.getStyleClass().remove("dark");
          view.setContent(PlotContent.EMPTY);
        });
    quiet();
  }

  /** A theme's colours as CSS writes them: JavaFX keeps colour components as floats. */
  private static List<String> hexes(CanvasTheme t) {
    List<String> out =
        new java.util.ArrayList<>(
            List.of(t.axis().hex(), t.grid().hex(), t.minorGrid().hex(), t.text().hex()));
    t.series().forEach(c -> out.add(c.hex()));
    return out;
  }

  /**
   * The x axis at y = 0, screen y 300, is snapped to 300.5 and so covers exactly pixel row 300 in
   * the axis colour, not two half-tinted rows; likewise the y axis and pixel column 400.
   */
  private static void assertCrispAxes(WritableImage image, Rgba axis) {
    int argb = 0xff000000 | Integer.parseInt(axis.hex().substring(1), 16);
    var pixels = image.getPixelReader();
    // Away from the labels and the curve: world x 7.5 on the x axis, world y 6.5 on the y axis.
    assertThat(Integer.toHexString(pixels.getArgb(700, 300))).isEqualTo(Integer.toHexString(argb));
    assertThat(Integer.toHexString(pixels.getArgb(400, 40))).isEqualTo(Integer.toHexString(argb));
    assertThat(pixels.getArgb(700, 299)).isNotEqualTo(argb);
    assertThat(pixels.getArgb(700, 301)).isNotEqualTo(argb);
    assertThat(pixels.getArgb(399, 40)).isNotEqualTo(argb);
    assertThat(pixels.getArgb(401, 40)).isNotEqualTo(argb);
  }
}
