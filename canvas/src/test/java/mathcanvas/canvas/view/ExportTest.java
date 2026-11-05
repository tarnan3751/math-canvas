package mathcanvas.canvas.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.ToDoubleFunction;
import javax.xml.parsers.DocumentBuilderFactory;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class ExportTest {

  private static final ToDoubleFunction<String> ESTIMATE = Ticks.estimate(GridPainter.LABEL_SIZE);

  private static final String GOLDEN = "plane.svg";

  private static final Viewport VIEWPORT = new Viewport(-3, 3, -2, 2, 240, 160, true);

  /** A parabola with a gap at its vertex and a point, in the light series colours. */
  static PlotContent content(CanvasTheme theme) {
    double[] xs = {-2, -1.5, -1, -0.5, 0, 0.5, 1, 1.5, 2};
    double[] ys = {4, 2.25, 1, 0.25, Double.NaN, 0.25, 1, 2.25, 4};
    return PlotContent.of(
        List.of(new PlotContent.Curve(xs, ys, Stroke.solid(theme.series().get(0), 2))),
        List.of(new PlotContent.Marker(1, 1, 4, theme.series().get(1))));
  }

  private static Document parse(String svg) throws Exception {
    return DocumentBuilderFactory.newDefaultNSInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void writesTheGoldenPlane() throws IOException {
    String actual = Export.svg(VIEWPORT, CanvasTheme.LIGHT, content(CanvasTheme.LIGHT), ESTIMATE);
    String expected = null;
    try (InputStream in = ExportTest.class.getResourceAsStream(GOLDEN)) {
      if (in != null) {
        expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    // A mismatch leaves the drawing in build/svg-actual/ to compare and, once checked, copy over.
    if (!actual.equals(expected)) {
      Path written = Path.of("build", "svg-actual", GOLDEN);
      Files.createDirectories(written.getParent());
      Files.writeString(written, actual, StandardCharsets.UTF_8);
    }
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void fillsTheBackgroundFirstThenDrawsTheGridThenTheObjects() throws Exception {
    CanvasTheme dark =
        new CanvasTheme(
            Rgba.rgb(0x1c1c1e),
            Rgba.rgb(0xc7c7cc),
            Rgba.rgb(0x3a3a3c),
            Rgba.rgb(0x2a2a2c),
            Rgba.rgb(0xc7c7cc),
            CanvasTheme.LIGHT.series());
    Document doc = parse(Export.svg(VIEWPORT, dark, content(dark), ESTIMATE));

    Element first = (Element) doc.getDocumentElement().getElementsByTagNameNS("*", "*").item(0);
    assertThat(first.getLocalName()).isEqualTo("path");
    assertThat(first.getAttribute("fill")).isEqualTo("#1c1c1e");
    assertThat(first.getAttribute("d")).isEqualTo("M0 160L240 160L240 0L0 0Z");
    var all = doc.getDocumentElement().getElementsByTagNameNS("*", "*");
    Element last = (Element) all.item(all.getLength() - 1);
    assertThat(last.getLocalName()).isEqualTo("circle");
    assertThat(last.getAttribute("fill")).isEqualTo(dark.series().get(1).hex());
    assertThat(doc.getElementsByTagNameNS("*", "text").getLength()).isPositive();
  }

  @Test
  void anImageFitsTheLargestTexture() {
    assertThat(Export.fits(VIEWPORT, 4)).isTrue();
    Viewport wide = new Viewport(-1, 1, -1, 1, 2049, 100, false);
    assertThat(Export.fits(wide, 4)).isFalse();
    assertThat(Export.fits(wide, 3)).isTrue();
  }

  // The 3D view's export.

  @Test
  void theSpaceViewIsExportedAtRestOverTheBackground() throws Exception {
    mathcanvas.canvas.model.CanvasState c =
        mathcanvas.canvas.model.CanvasState.EMPTY
            .add(
                i ->
                    new mathcanvas.canvas.model.Surface(
                        i,
                        mathcanvas.canvas.model.Style.DEFAULT,
                        mathcanvas.engine.parse.Parser.parseExpression(
                            "x y", mathcanvas.engine.parse.Names.NONE),
                        mathcanvas.canvas.model.Surface.DEFAULT_DOMAIN,
                        mathcanvas.canvas.model.Surface.DEFAULT_DOMAIN,
                        100))
            .recomputeAll(
                new mathcanvas.canvas.sample.Samplers()
                    .over(
                        mathcanvas.engine.eval.Environment.EMPTY,
                        mathcanvas.engine.eval.Settings.DEFAULT),
                new mathcanvas.canvas.model.Window(-1, 1, -1, 1),
                mathcanvas.canvas.model.Recomputes.standard(),
                mathcanvas.engine.Cancellation.NEVER);
    CanvasTheme dark =
        new CanvasTheme(
            Rgba.rgb(0x1c1c1e),
            CanvasTheme.LIGHT.axis(),
            CanvasTheme.LIGHT.grid(),
            CanvasTheme.LIGHT.minorGrid(),
            CanvasTheme.LIGHT.text(),
            CanvasTheme.LIGHT.series());
    mathcanvas.canvas.space.SpaceScene scene = mathcanvas.canvas.space.SpaceScene.build(c, dark);
    String svg =
        Export.svg(
            scene, mathcanvas.canvas.space.Camera.DEFAULT, dark, 640, 480, Ticks.estimate(12));
    Document doc =
        DocumentBuilderFactory.newDefaultNSInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    Element first = (Element) doc.getElementsByTagNameNS("*", "path").item(0);
    assertThat(first.getAttribute("fill")).isEqualTo("#1c1c1e");
    // the full mesh, not the coarse one: one fill per quad, after the background
    long fills = svg.lines().filter(l -> l.contains("Z\" fill=")).count();
    assertThat(fills).isEqualTo(1 + scene.quads(false));
    assertThat(doc.getDocumentElement().getAttribute("width")).isEqualTo("640");
  }
}
