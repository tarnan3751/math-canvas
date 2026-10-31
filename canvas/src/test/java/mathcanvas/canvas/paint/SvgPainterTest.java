package mathcanvas.canvas.paint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import mathcanvas.canvas.geom.Viewport;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class SvgPainterTest {

  private static final String GOLDEN = "small-drawing.svg";

  /**
   * The golden drawing: the grid, a polyline with a gap, a dashed line, a region, a point, text.
   */
  static String smallDrawing() {
    SvgPainter p = new SvgPainter(new Viewport(-2, 2, -1.5, 1.5, 300, 225, false));
    GridPainter.paint(p, CanvasTheme.LIGHT);
    double[] xs = {-2, -1.5, -1, -0.5, 0, 0.5, 1, 1.5, 2};
    double[] ys = {0, -1, 0, 1, Double.NaN, -1, 0, 1, 0};
    p.beginPath();
    p.polyline(xs, ys, xs.length);
    p.stroke(Stroke.solid(CanvasTheme.LIGHT.series().get(0), 2));
    p.beginPath();
    p.moveTo(-2, 1);
    p.lineTo(2, -1);
    p.stroke(Stroke.dashed(CanvasTheme.LIGHT.series().get(1), 1.5, 6, 3));
    p.beginPath();
    p.moveTo(0, 0);
    p.lineTo(1, 1);
    p.lineTo(1, 0);
    p.closePath();
    p.fill(CanvasTheme.LIGHT.series().get(2).withOpacity(0.25));
    p.point(1, 1, 4, CanvasTheme.LIGHT.series().get(3));
    p.text(
        "A<B & C",
        1,
        1,
        6,
        -6,
        new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BOTTOM),
        new TextStyle(14, CanvasTheme.LIGHT.text()));
    return p.document();
  }

  @Test
  void writesTheGoldenDrawing() throws IOException {
    String actual = smallDrawing();
    String expected = null;
    try (InputStream in = SvgPainterTest.class.getResourceAsStream(GOLDEN)) {
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
  void isWellFormedSvg() throws Exception {
    Document doc =
        DocumentBuilderFactory.newDefaultNSInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(smallDrawing().getBytes(StandardCharsets.UTF_8)));
    assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("http://www.w3.org/2000/svg");
    assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("svg");
    assertThat(doc.getElementsByTagNameNS("*", "circle").getLength()).isEqualTo(1);
    assertThat(
            doc.getElementsByTagNameNS("*", "text")
                .item(doc.getElementsByTagNameNS("*", "text").getLength() - 1)
                .getTextContent())
        .isEqualTo("A<B & C");
  }

  @Test
  void writesNumbersToTwoDecimals() {
    assertThat(SvgPainter.number(1.0)).isEqualTo("1");
    assertThat(SvgPainter.number(-0.001)).isEqualTo("0");
    assertThat(SvgPainter.number(12.345)).isEqualTo("12.35");
    assertThat(SvgPainter.number(-3.5)).isEqualTo("-3.5");
    assertThat(SvgPainter.number(1200)).isEqualTo("1200");
  }

  @Test
  void anEmptyPathWritesNothing() {
    SvgPainter p = new SvgPainter(new Viewport(0, 1, 0, 1, 10, 10, false));
    p.beginPath();
    p.moveTo(100, 100);
    p.lineTo(200, 100);
    p.stroke(Stroke.solid(CanvasTheme.LIGHT.axis(), 1));
    p.fill(CanvasTheme.LIGHT.axis());
    assertThat(p.document()).doesNotContain("<path");
  }

  @Test
  void aTextWithABackgroundSitsOnARoundedBox() {
    SvgPainter p = new SvgPainter(new Viewport(0, 1, 0, 1, 100, 100, false));
    p.text(
        "A = 1",
        0.5,
        0.5,
        0,
        0,
        new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE),
        new TextStyle(
            10, Rgba.rgb(0x000000), java.util.Optional.of(Rgba.rgb(0xffffff).withOpacity(0.75))));
    String svg = p.document();
    // five characters at 0.6 of 10 pixels, centred on (50, 50), padded by 3
    assertThat(svg)
        .contains(
            "<rect x=\"32\" y=\"42\" width=\"36\" height=\"16\" rx=\"3\" fill=\"#ffffff\""
                + " fill-opacity=\"0.75\"/>");
    assertThat(svg.indexOf("<rect x=\"32\"")).isLessThan(svg.indexOf(">A = 1</text>"));
  }

  @Test
  void aTextBoxIsClampedInsideTheView() {
    SvgPainter p = new SvgPainter(new Viewport(0, 1, 0, 1, 100, 100, false));
    // centred on the view's right edge, its box would reach 18 pixels past it
    p.text(
        "A = 1",
        1,
        0.5,
        0,
        0,
        new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE),
        new TextStyle(10, Rgba.rgb(0x000000), java.util.Optional.of(Rgba.rgb(0xffffff))));
    String svg = p.document();
    assertThat(svg).contains("<rect x=\"64\" y=\"42\" width=\"36\"");
    assertThat(svg).contains("<text x=\"82\" y=\"50\"");
  }
}
