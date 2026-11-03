package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.env;
import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.Set;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Interval;
import org.junit.jupiter.api.Test;

/** Surfaces {@code z = f(x, y)} through the standard recompute with the real sampler. */
class SurfacesTest {

  private static final Window W = new Window(-5, 5, -5, 5);

  private static Sampler sampler(String a) {
    return new Samplers().over(env("a", a), Settings.DEFAULT);
  }

  private static Interval range(String lo, String hi) {
    return Interval.of(expr(lo), expr(hi), true, true);
  }

  private static CanvasState scene(String body, Interval x, Interval y, int n) {
    return CanvasState.EMPTY
        .add(
            i ->
                new Slider(
                    i,
                    Style.DEFAULT,
                    "a",
                    BigDecimal.ZERO,
                    BigDecimal.TEN,
                    BigDecimal.ONE,
                    BigDecimal.ONE))
        .add(i -> new Surface(i, Style.DEFAULT, expr(body), x, y, n));
  }

  @Test
  void aSurfaceIsItsGridOfHeights() {
    CanvasState s =
        scene("a x y + sin(x)", range("-1", "1"), range("0", "a + 1"), 50)
            .recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Mesh m = (Geometry.Mesh) s.geometryOf(new ObjectId(2));
    assertThat(m.nu()).isEqualTo(50);
    assertThat(m.nv()).isEqualTo(50);
    assertThat(m.zs()).hasSize(51 * 51);
    for (int j = 0; j <= 50; j += 7) {
      for (int i = 0; i <= 50; i += 5) {
        int k = j * 51 + i;
        double x = -1 + 2.0 * i / 50;
        double y = 2.0 * j / 50;
        assertThat(m.xs()[k]).isCloseTo((float) x, within(1e-6f));
        assertThat(m.ys()[k]).isCloseTo((float) y, within(1e-6f));
        assertThat(m.zs()[k]).isCloseTo((float) (x * y + Math.sin(x)), within(1e-5f));
      }
    }
    double[] b = m.bounds().orElseThrow();
    assertThat(b).startsWith(-1, 1, 0, 2);
    assertThat(b[4]).isCloseTo(-2 - Math.sin(1), within(1e-6));
    assertThat(b[5]).isCloseTo(2 + Math.sin(1), within(1e-6));
  }

  @Test
  void aSlidersChangeResamplesTheSurfaceAndAPanDoesNot() {
    CanvasState before =
        scene("a x", Surface.DEFAULT_DOMAIN, Surface.DEFAULT_DOMAIN, 100)
            .recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    CanvasState after =
        before.recompute(
            Set.of(), Set.of("a"), sampler("3"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Mesh m = (Geometry.Mesh) after.geometryOf(new ObjectId(2));
    assertThat(m.zs()[100]).isCloseTo(15f, within(1e-5f));
    CanvasState panned =
        after.resample(
            sampler("3"), new Window(5, 15, 5, 15), Recomputes.standard(), Cancellation.NEVER);
    assertThat(panned.geometryOf(new ObjectId(2))).isSameAs(m);
  }

  @Test
  void aSurfaceIsUndefinedOnAnEmptyOrUnboundedRange() {
    for (Interval x :
        new Interval[] {
          range("1", "1"), range("2", "1"), range("0", "b"), Surface.DEFAULT_DOMAIN
        }) {
      CanvasState s =
          scene("x y", x, range("0", "1/0"), 50)
              .recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
      assertThat(s.geometryOf(new ObjectId(2))).isEqualTo(Geometry.UNDEFINED);
    }
  }

  @Test
  void anUndefinedBodyIsAMeshWithNoFiniteVertexAndHolesAreNaN() {
    CanvasState s =
        scene("q x y", Surface.DEFAULT_DOMAIN, Surface.DEFAULT_DOMAIN, 50)
            .recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Mesh m = (Geometry.Mesh) s.geometryOf(new ObjectId(2));
    assertThat(m.bounds()).isEmpty();
    CanvasState holed =
        scene("sqrt(x)", Surface.DEFAULT_DOMAIN, Surface.DEFAULT_DOMAIN, 50)
            .recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Mesh h = (Geometry.Mesh) holed.geometryOf(new ObjectId(2));
    assertThat(h.zs()[0]).isNaN();
    assertThat(h.zs()[50]).isCloseTo((float) Math.sqrt(5), within(1e-6f));
    assertThat(h.bounds().orElseThrow()[0]).isEqualTo(0);
  }

  @Test
  void aGridStopsOnItsCancellation() {
    Sampler s = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);
    assertThatThrownBy(() -> s.grid(expr("x y"), Surface.X, 0, 1, Surface.Y, 0, 1, 200, () -> true))
        .isInstanceOf(CancelledException.class);
  }
}
