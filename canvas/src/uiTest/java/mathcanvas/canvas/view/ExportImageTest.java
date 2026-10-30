package mathcanvas.canvas.view;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mathcanvas.canvas.view.UiHarness.SCREENSHOTS;
import static mathcanvas.canvas.view.UiHarness.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;

import java.util.List;
import java.util.function.ToDoubleFunction;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The plane exported as an image on a canvas of its own: the size is the viewport's times the
 * scale, the background and the objects are where the viewport puts them, scaled, and the picture
 * is {@code canvas-export-2x.png} beside the screenshots.
 */
@ExtendWith(ApplicationExtension.class)
class ExportImageTest {

  private static final ToDoubleFunction<String> ESTIMATE = Ticks.estimate(GridPainter.LABEL_SIZE);

  private static final Viewport VIEWPORT = new Viewport(-3, 3, -2, 2, 240, 160, true);

  @Start
  void start(Stage stage) {
    stage.setScene(new Scene(new StackPane(), 100, 100));
    stage.show();
  }

  private static PlotContent content() {
    double[] xs = {-2, -1, 0, 1, 2};
    double[] ys = {-1, -1, -1, -1, -1};
    return PlotContent.of(
        List.of(new PlotContent.Curve(xs, ys, Stroke.solid(CanvasTheme.LIGHT.series().get(0), 4))),
        List.of(new PlotContent.Marker(1, 1, 4, CanvasTheme.LIGHT.series().get(1))));
  }

  private static String hex(WritableImage image, int x, int y) {
    return String.format("#%06x", image.getPixelReader().getArgb(x, y) & 0xffffff);
  }

  @Test
  void drawsThePlaneAtTheViewportsSizeTimesTheScale() throws Exception {
    WritableImage one =
        waitFor(
            10,
            SECONDS,
            asyncFx(() -> Export.image(VIEWPORT, CanvasTheme.LIGHT, content(), ESTIMATE, 1)));
    WritableImage two =
        waitFor(
            10,
            SECONDS,
            asyncFx(() -> Export.image(VIEWPORT, CanvasTheme.LIGHT, content(), ESTIMATE, 2)));
    write(two, SCREENSHOTS.resolve("canvas-export-2x.png"));

    assertThat(one.getWidth()).isEqualTo(240);
    assertThat(one.getHeight()).isEqualTo(160);
    assertThat(two.getWidth()).isEqualTo(480);
    assertThat(two.getHeight()).isEqualTo(320);
    // the background, away from the grid and the labels; the point at world (1, 1); the line y = -1
    assertThat(hex(one, 20, 150)).isEqualTo("#ffffff");
    assertThat(hex(two, 40, 300)).isEqualTo("#ffffff");
    assertThat(hex(one, 160, 40)).isEqualTo(CanvasTheme.LIGHT.series().get(1).hex());
    assertThat(hex(two, 320, 80)).isEqualTo(CanvasTheme.LIGHT.series().get(1).hex());
    assertThat(hex(two, 220, 240)).isEqualTo(CanvasTheme.LIGHT.series().get(0).hex());
    assertThat(two.getPixelReader().getArgb(0, 0) >>> 24).isEqualTo(0xff);
  }

  @Test
  void fillsTheThemesBackground() throws Exception {
    CanvasTheme dark =
        new CanvasTheme(
            Rgba.rgb(0x1c1c1e),
            Rgba.rgb(0xc7c7cc),
            Rgba.rgb(0x3a3a3c),
            Rgba.rgb(0x2a2a2c),
            Rgba.rgb(0xc7c7cc),
            CanvasTheme.LIGHT.series());
    WritableImage image =
        waitFor(10, SECONDS, asyncFx(() -> Export.image(VIEWPORT, dark, content(), ESTIMATE, 1)));

    assertThat(hex(image, 20, 150)).isEqualTo("#1c1c1e");
  }
}
