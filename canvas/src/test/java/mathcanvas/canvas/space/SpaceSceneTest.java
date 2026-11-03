package mathcanvas.canvas.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.SvgPainter;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** The scene: its box, its depth order, its meshes and shades, its edges, its labels. */
class SpaceSceneTest {

  private static final CanvasTheme T = CanvasTheme.LIGHT;

  private static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  private static Interval range(String lo, String hi) {
    return Interval.of(expr(lo), expr(hi), true, true);
  }

  /** A canvas of surfaces, each {@code {body, x0, x1, colour, n}}, recomputed. */
  private static CanvasState canvas(String[]... surfaces) {
    CanvasState s = CanvasState.EMPTY;
    for (String[] f : surfaces) {
      s =
          s.add(
              i ->
                  new Surface(
                      i,
                      new Style(Integer.parseInt(f[3]), 2, false, true, false),
                      expr(f[0]),
                      range(f[1], f[2]),
                      Surface.DEFAULT_DOMAIN,
                      Integer.parseInt(f[4])));
    }
    return s.recomputeAll(
        new Samplers().over(Environment.EMPTY, Settings.DEFAULT),
        new Window(-1, 1, -1, 1),
        Recomputes.standard(),
        Cancellation.NEVER);
  }

  private static Recorder paint(SpaceScene scene, Camera camera, boolean coarse) {
    Recorder r = new Recorder(800, 600);
    scene.paint(r, camera, T, coarse, Ticks.estimate(12));
    return r;
  }

  private static boolean hue(Rgba shaded, Rgba series) {
    // a shade is the series colour scaled: the channels keep their ratios
    double f = shaded.red() / series.red();
    return Math.abs(shaded.green() - f * series.green()) < 1e-9
        && Math.abs(shaded.blue() - f * series.blue()) < 1e-9;
  }

  @Test
  void theBoxFitsTheSurfacesAndAHiddenOneIsLeftOut() {
    CanvasState c = canvas(new String[] {"x y", "-1", "2", "0", "50"});
    assertThat(SpaceScene.build(c, T).box()).isEqualTo(new Box3(-1, 2, -5, 5, -10, 10));
    CanvasState hidden =
        c.replace(c.objects().getFirst().withStyle(new Style(0, 2, false, false, false)));
    assertThat(SpaceScene.build(hidden, T).box()).isEqualTo(Box3.DEFAULT);
    assertThat(SpaceScene.build(hidden, T).quads(false)).isZero();
  }

  @Test
  void theNearerSurfaceIsPaintedAfterTheFartherOne() {
    // two strips at either end of x, seen from +x: the strip at x in [4, 5] is nearer
    SpaceScene scene =
        SpaceScene.build(
            canvas(
                new String[] {"y", "-5", "-4", "0", "50"}, new String[] {"y", "4", "5", "1", "50"}),
            T);
    Recorder r = paint(scene, new Camera(0, 0.4, 8, 0, 0, 0), false);
    assertThat(r.fills).hasSize(2 * 2500);
    int lastFar = -1;
    int firstNear = Integer.MAX_VALUE;
    for (int k = 0; k < r.fills.size(); k++) {
      if (hue(r.fills.get(k), T.series().get(0))) {
        lastFar = k;
      } else {
        firstNear = Math.min(firstNear, k);
      }
    }
    assertThat(lastFar).isLessThan(firstNear);
    // the order is the depth's, not the list's: the nearer strip listed first is painted last
    SpaceScene swapped =
        SpaceScene.build(
            canvas(
                new String[] {"y", "4", "5", "1", "50"}, new String[] {"y", "-5", "-4", "0", "50"}),
            T);
    Recorder s = paint(swapped, new Camera(0, 0.4, 8, 0, 0, 0), false);
    int lastFarSwapped = -1;
    int firstNearSwapped = Integer.MAX_VALUE;
    for (int k = 0; k < s.fills.size(); k++) {
      if (hue(s.fills.get(k), T.series().get(0))) {
        lastFarSwapped = k;
      } else {
        firstNearSwapped = Math.min(firstNearSwapped, k);
      }
    }
    assertThat(lastFarSwapped).isLessThan(firstNearSwapped);
  }

  @Test
  void aBumpIsPaintedFromItsFarSideToItsNearOne() {
    // from the default camera, at yaw −60° and above, the far side of the bump is higher on the
    // screen: the first quads painted are high, the last low, whatever the grid's own order
    SpaceScene scene =
        SpaceScene.build(canvas(new String[] {"3 exp(-(x^2 + y^2))", "-5", "5", "0", "50"}), T);
    Recorder r = paint(scene, Camera.DEFAULT, false);
    assertThat(r.fills).hasSize(2500);
    double first = 0;
    double last = 0;
    for (int k = 0; k < 200; k++) {
      first += r.centres.get(k)[1];
      last += r.centres.get(r.centres.size() - 1 - k)[1];
    }
    assertThat(first / 200).isGreaterThan(last / 200 + 100);
  }

  @Test
  void theCoarseMeshIsEveryNOver50thVertexAndItsEdgesAreTheLattice() {
    SpaceScene scene =
        SpaceScene.build(canvas(new String[] {"x^2 - y^2", "-5", "5", "0", "100"}), T);
    assertThat(scene.quads(true)).isEqualTo(2500);
    assertThat(scene.quads(false)).isEqualTo(10000);
    Recorder coarse = paint(scene, Camera.DEFAULT, true);
    Recorder full = paint(scene, Camera.DEFAULT, false);
    assertThat(coarse.fills).hasSize(2500);
    assertThat(full.fills).hasSize(10000);
    // every coarse quad strokes its lattice edges; a full quad only when it lies on the lattice
    // (an even row or column) or on the last row or column
    int onLattice = 0;
    for (int j = 0; j < 100; j++) {
      for (int i = 0; i < 100; i++) {
        if (j % 2 == 0 || i % 2 == 0 || j == 99 || i == 99) {
          onLattice++;
        }
      }
    }
    long edges = full.strokes.stream().filter(s -> s.width() == 1 && !isBox(s)).count();
    assertThat(edges).isEqualTo(onLattice);
    assertThat(coarse.strokes.stream().filter(s -> !isBox(s)).count()).isEqualTo(2500);
    assertThat(coarse.strokes.stream().filter(s -> !isBox(s)).allMatch(s -> s.dash().isEmpty()))
        .isTrue();
  }

  private static boolean isBox(mathcanvas.canvas.paint.Stroke s) {
    return s.colour().equals(T.axis()) || s.colour().equals(T.grid());
  }

  @Test
  void undefinedCellsAndASpikeOutsideTheBoxAreNotDrawn() {
    Recorder half =
        paint(
            SpaceScene.build(canvas(new String[] {"sqrt(x)", "-5", "5", "0", "50"}), T),
            Camera.DEFAULT,
            false);
    // the cells with a vertex at x < 0 are undefined: 25 columns of 50
    assertThat(half.fills).hasSize(25 * 50);
    SpaceScene pole =
        SpaceScene.build(canvas(new String[] {"1/(x^2 + y^2 + 0.0001)", "-5", "5", "0", "50"}), T);
    assertThat(pole.box().zMax()).isLessThan(100);
    Recorder r = paint(pole, Camera.DEFAULT, false);
    assertThat(r.fills.size()).isBetween(2400, 2499);
  }

  @Test
  void aFaceSeenFromBelowIsDarker() {
    SpaceScene scene = SpaceScene.build(canvas(new String[] {"0", "-5", "5", "0", "50"}), T);
    double above = brightness(paint(scene, new Camera(0.3, 0.6, 8, 0, 0, 0), false));
    double below = brightness(paint(scene, new Camera(0.3, -0.6, 8, 0, 0, 0), false));
    assertThat(below).isLessThan(above * 0.85);
  }

  private static double brightness(Recorder r) {
    return r.fills.stream().mapToDouble(c -> c.red() + c.green() + c.blue()).average().orElse(0);
  }

  @Test
  void theAxesAreLabelledAndNamed() {
    Recorder r =
        paint(
            SpaceScene.build(canvas(new String[] {"x y", "-5", "5", "0", "50"}), T),
            Camera.DEFAULT,
            true);
    assertThat(r.texts).contains("x", "y", "z", "0", "−5", "5", "−20", "20");
  }

  @Test
  void theSvgPainterDrawsTheSameScene() throws Exception {
    SpaceScene scene = SpaceScene.build(canvas(new String[] {"x y / 5", "-5", "5", "0", "50"}), T);
    Recorder r = paint(scene, Camera.DEFAULT, false);
    SvgPainter svg = new SvgPainter(r.viewport);
    scene.paint(svg, Camera.DEFAULT, T, false, Ticks.estimate(12));
    Document doc =
        DocumentBuilderFactory.newDefaultNSInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(svg.document().getBytes(StandardCharsets.UTF_8)));
    long filled =
        Arrays.stream(svg.document().split("\n")).filter(l -> l.contains("Z\" fill=")).count();
    assertThat(filled).isEqualTo(r.fills.size());
    assertThat(doc.getElementsByTagNameNS("*", "text").getLength()).isEqualTo(r.texts.size());
  }

  /**
   * The FX thread's share of an orbiting frame of the full 200 × 200 mesh: projecting, sorting and
   * recording 40,000 quads into a painter that keeps nothing. The bound is generous; measured on
   * the development machine (Apple M5, load about 3), the median is about 5 ms.
   */
  @Test
  void aFrameOfTheFullMeshIsQuickToRecord() {
    SpaceScene scene =
        SpaceScene.build(canvas(new String[] {"x^2 - y^2", "-5", "5", "0", "200"}), T);
    long[] nanos = new long[15];
    for (int k = 0; k < nanos.length; k++) {
      Recorder r = new Recorder(1200, 800);
      long start = System.nanoTime();
      scene.paint(r, Camera.DEFAULT.orbit(0.03 * k, 0), T, false, Ticks.estimate(12));
      nanos[k] = System.nanoTime() - start;
      assertThat(r.fills).hasSize(40_000);
    }
    Arrays.sort(nanos);
    assertThat(nanos[nanos.length / 2]).isLessThan(250_000_000L);
  }
}
