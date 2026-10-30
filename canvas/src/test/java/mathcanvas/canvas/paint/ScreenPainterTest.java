package mathcanvas.canvas.paint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.canvas.geom.Viewport;
import org.junit.jupiter.api.Test;

class ScreenPainterTest {

  /** Records the screen-space calls as text, coordinates rounded to two decimals. */
  private static final class Recorder extends ScreenPainter {
    final List<String> calls = new ArrayList<>();

    Recorder(Viewport v) {
      super(v);
    }

    @Override
    protected void beginScreenPath() {
      calls.add("begin");
    }

    @Override
    protected void moveToScreen(double x, double y) {
      calls.add("M " + SvgPainter.number(x) + " " + SvgPainter.number(y));
    }

    @Override
    protected void lineToScreen(double x, double y) {
      calls.add("L " + SvgPainter.number(x) + " " + SvgPainter.number(y));
    }

    @Override
    protected void closeScreenSubpath() {
      calls.add("Z");
    }

    @Override
    protected void strokeScreenPath(Stroke stroke) {
      calls.add("stroke");
    }

    @Override
    protected void fillScreenPath(Rgba colour) {
      calls.add("fill");
    }

    @Override
    protected void pointScreen(double x, double y, double radius, Rgba colour) {
      calls.add("point " + SvgPainter.number(x) + " " + SvgPainter.number(y));
    }

    @Override
    protected void textScreen(String text, double x, double y, Anchor anchor, TextStyle style) {
      calls.add("text " + text);
    }
  }

  // 100 x 100 pixels over [0, 10] on both axes: 10 pixels per unit, margin 64 pixels.
  private static final Viewport V = new Viewport(0, 10, 0, 10, 100, 100, false);
  private static final Stroke S = Stroke.solid(CanvasTheme.LIGHT.axis(), 1);

  @Test
  void clipsAnAsymptoteToTheGrownWindow() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(5, 5);
    r.lineTo(5.000001, 1e12);
    r.stroke(S);
    assertThat(r.calls).hasSize(4);
    assertThat(r.calls.get(1)).isEqualTo("M 50 50");
    assertThat(r.calls.get(2)).startsWith("L 50 -64");
  }

  @Test
  void aSubpathLeavingAndReturningBecomesTwo() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.polyline(new double[] {1, 1, 1, 2}, new double[] {1, 100, 1, 1}, 4);
    r.stroke(S);
    assertThat(r.calls)
        .containsExactly(
            "begin", "M 10 90", "L 10 -64", "M 10 -64", "L 10 90", "L 20 90", "stroke");
  }

  @Test
  void aNonFiniteValueBreaksAPolyline() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.polyline(new double[] {1, 2, 3, 4}, new double[] {1, 2, Double.NaN, 4}, 4);
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M 10 90", "L 20 80", "stroke");
  }

  @Test
  void fillsAreClippedAsPolygons() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(0, 0);
    r.lineTo(1000, 0);
    r.lineTo(0, 1000);
    r.closePath();
    r.fill(CanvasTheme.LIGHT.axis());
    // The grown window's corner lies inside the triangle, so the clip is the window's part of
    // it: the rectangle from the bottom-left of the triangle to the grown top-right corner.
    assertThat(r.calls)
        .containsExactly("begin", "M 0 100", "L 164 100", "L 164 -64", "L 0 -64", "Z", "fill");
  }

  @Test
  void aFillCutByOneEdgeGainsAVertex() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(1, 1);
    r.lineTo(30, 1);
    r.lineTo(1, 2);
    r.closePath();
    r.fill(CanvasTheme.LIGHT.axis());
    // 1,1 -> 30,1 crosses x = 164 px (16.4 units); the hypotenuse crosses it too.
    assertThat(r.calls).hasSize(7);
    assertThat(r.calls.get(2)).isEqualTo("L 164 90");
  }

  @Test
  void aClosedPathInsideIsClosed() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(1, 1);
    r.lineTo(2, 1);
    r.lineTo(2, 2);
    r.closePath();
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M 10 90", "L 20 90", "L 20 80", "Z", "stroke");
  }

  @Test
  void pointsAndTextFarOutsideAreSkipped() {
    Recorder r = new Recorder(V);
    r.point(5, 5, 3, CanvasTheme.LIGHT.axis());
    r.point(50, 5, 3, CanvasTheme.LIGHT.axis());
    Anchor a = new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BASELINE);
    TextStyle t = new TextStyle(12, CanvasTheme.LIGHT.text());
    r.text("in", 5, 5, 0, 0, a, t);
    r.text("out", 5, -50, 0, 0, a, t);
    assertThat(r.calls).containsExactly("point 50 50", "text in");
  }

  /** The calls' coordinates in order, as parsed back from the recorder. */
  private static double[][] points(Recorder r) {
    return r.calls.stream()
        .filter(c -> c.startsWith("M") || c.startsWith("L"))
        .map(c -> c.substring(2).split(" "))
        .map(xy -> new double[] {Double.parseDouble(xy[0]), Double.parseDouble(xy[1])})
        .toArray(double[][]::new);
  }

  @Test
  void aFarFirstEndCostsTheNearEndNothing() {
    // From 10¹⁷ pixels above to (5, 5): the segment must end at the near point itself, and the
    // next one start there.
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(5.000001, 1e16);
    r.lineTo(5, 5);
    r.lineTo(6, 5);
    r.stroke(S);
    assertThat(r.calls).containsSubsequence("L 50 50", "L 60 50");
    assertThat(r.calls.get(1)).isEqualTo("M 50 -64");
  }

  @Test
  void anAsymptoteLeavesAndReentersAtTheEdge() {
    // tan sampled across π/2, its middle sample about +1.6e16 (the double nearest π/2 is just
    // below it): 0 to 3 across 600 px, -5 to 5 down 512 px. The curve leaves through the grown top
    // edge and comes back through it on the way down to tan(1.87), each clipped end on the edge
    // and each sample's own end exactly where it is.
    Viewport v = new Viewport(0, 3, -5, 5, 600, 512, false);
    Recorder r = new Recorder(v);
    double[] xs = {1.3, Math.PI / 2, 1.87};
    double[] ys = {Math.tan(xs[0]), Math.tan(xs[1]), Math.tan(xs[2])};
    r.beginPath();
    r.polyline(xs, ys, 3);
    r.stroke(S);
    double[][] p = points(r);
    assertThat(p).hasNumberOfRows(4);
    assertThat(p[0][0]).isEqualTo(Double.parseDouble(SvgPainter.number(v.toScreenX(1.3))));
    assertThat(p[1][1]).isEqualTo(-64);
    assertThat(p[1][0]).isBetween(p[0][0], v.toScreenX(Math.PI / 2) + 0.01);
    assertThat(p[2][1]).isEqualTo(-64);
    assertThat(p[2][0]).isBetween(v.toScreenX(Math.PI / 2) - 0.01, p[3][0]);
    assertThat(p[3][0]).isEqualTo(Double.parseDouble(SvgPainter.number(v.toScreenX(1.87))));
    assertThat(p[3][1]).isEqualTo(Double.parseDouble(SvgPainter.number(v.toScreenY(ys[2]))));
  }

  @Test
  void aFiniteSpikeBeyondThePixelRangeIsDrawn() {
    // 1e308 would overflow to an infinite pixel; it is clipped along its own segment, not a gap.
    Recorder r = new Recorder(V);
    r.beginPath();
    r.moveTo(0, 0);
    r.lineTo(0.01, 1e308);
    r.lineTo(0.02, 0);
    r.stroke(S);
    double[][] p = points(r);
    assertThat(p).hasNumberOfRows(4);
    assertThat(p[0]).containsExactly(0, 100);
    assertThat(p[1][1]).isEqualTo(-64);
    assertThat(p[2][1]).isEqualTo(-64);
    assertThat(p[3]).containsExactly(0.2, 100);
  }

  @Test
  void aNonFiniteWorldValueIsStillAGap() {
    Recorder r = new Recorder(V);
    r.beginPath();
    r.polyline(new double[] {1, 2, 3, 4}, new double[] {1, Double.POSITIVE_INFINITY, 3, 4}, 4);
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M 30 70", "L 40 60", "stroke");
  }

  // 40 pixels per unit, 800 by 600: the grown window is x in [-11.6, 11.6], y in [-9.1, 9.1].
  private static final Viewport WIDE = new Viewport(-10, 10, -7.5, 7.5, 800, 600, false);

  @Test
  void aConstructionLineKeepsItsDirection() {
    // y = x/2 between points 10⁸ away on both axes: it crosses the grown window from
    // (-11.6, -5.8) to (11.6, 5.8).
    Recorder r = new Recorder(WIDE);
    r.beginPath();
    r.moveTo(-1e8, -5e7);
    r.lineTo(1e8, 5e7);
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M -64 532", "L 864 68", "stroke");
  }

  @Test
  void aHalfPlaneWithFarVerticesFillsItsHalf() {
    // y ≤ x/2 as a quadrilateral with vertices 10⁸ away: the fill is the window below the line.
    Recorder r = new Recorder(WIDE);
    r.beginPath();
    r.moveTo(-1e8, -5e7);
    r.lineTo(1e8, 5e7);
    r.lineTo(1e8, -1e8);
    r.lineTo(-1e8, -1e8);
    r.closePath();
    r.fill(CanvasTheme.LIGHT.axis());
    assertThat(r.calls.stream().filter(c -> c.startsWith("M") || c.startsWith("L")).toList())
        .containsExactlyInAnyOrder("M -64 532", "L 864 68", "L 864 664", "L -64 664");
  }

  @Test
  void aLineBetweenFarEndsIsExact() {
    // y = x/2 between ±1e300: interpolating from either end would round it to y = 0.
    Recorder r = new Recorder(WIDE);
    r.beginPath();
    r.moveTo(-1e300, -5e299);
    r.lineTo(1e300, 5e299);
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M -64 532", "L 864 68", "stroke");
  }

  @Test
  void aHalfPlaneBetweenFarEndsIsExact() {
    Recorder r = new Recorder(WIDE);
    r.beginPath();
    r.moveTo(-1e300, -5e299);
    r.lineTo(1e300, 5e299);
    r.lineTo(1e300, -1e300);
    r.lineTo(-1e300, -1e300);
    r.closePath();
    r.fill(CanvasTheme.LIGHT.axis());
    assertThat(r.calls.stream().filter(c -> c.startsWith("M") || c.startsWith("L")).toList())
        .containsExactlyInAnyOrder("M -64 532", "L 864 68", "L 864 664", "L -64 664");
  }

  @Test
  void aVeeToFarEndsKeepsItsVertex() {
    // y = |x| from ±1e305 through the origin: in through the grown top edge at x = -9.1, down to
    // the origin itself, and out through the top edge at x = 9.1, as one subpath.
    Recorder r = new Recorder(WIDE);
    r.beginPath();
    r.polyline(new double[] {-1e305, 0, 1e305}, new double[] {1e305, 0, 1e305}, 3);
    r.stroke(S);
    assertThat(r.calls).containsExactly("begin", "M 36 -64", "L 400 300", "L 764 -64", "stroke");
  }
}
