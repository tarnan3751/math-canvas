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
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.SpaceCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Interval;
import org.junit.jupiter.api.Test;

/** Parametric surfaces, space curves and gradients through the recompute with the real sampler. */
class SpaceKindsTest {

  private static final Window W = new Window(-5, 5, -5, 5);
  private static final Style S = Style.DEFAULT;

  private static Sampler sampler(String a) {
    return new Samplers().over(env("a", a), Settings.DEFAULT);
  }

  private static CanvasState recomputed(CanvasState s, String a) {
    return s.recomputeAll(sampler(a), W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  @Test
  void aSphereIsOnTheUnitSphereEverywhere() {
    CanvasState s =
        recomputed(
            CanvasState.EMPTY.add(
                i ->
                    new ParametricSurface(
                        i,
                        S,
                        expr("cos(u) sin(v)"),
                        expr("sin(u) sin(v)"),
                        expr("cos(v)"),
                        ParametricSurface.U_DOMAIN,
                        ParametricSurface.V_DOMAIN,
                        50)),
            "1");
    Geometry.Mesh m = (Geometry.Mesh) s.geometryOf(id(1));
    assertThat(m.height()).isEmpty();
    for (int k = 0; k < m.xs().length; k += 37) {
      double r = Math.sqrt(m.xs()[k] * m.xs()[k] + m.ys()[k] * m.ys()[k] + m.zs()[k] * m.zs()[k]);
      assertThat(r).isCloseTo(1, within(1e-6));
    }
    // u runs along a row: the first row is the north pole, v = 0
    assertThat(m.zs()[0]).isCloseTo(1f, within(1e-6f));
    assertThat(m.zs()[50 * 51]).isCloseTo(-1f, within(1e-6f));
  }

  @Test
  void aHelixIsSampledAlongItsRangeAndASliderMovesIt() {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new Slider(
                        i, S, "a", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE))
            .add(
                i ->
                    new SpaceCurve(
                        i,
                        S,
                        expr("cos(t)"),
                        expr("sin(t)"),
                        expr("a t"),
                        Interval.of(expr("0"), expr("4pi"), true, true)));
    Geometry.Path3 helix = (Geometry.Path3) recomputed(s, "1").geometryOf(id(2));
    assertThat(helix.xs()).hasSize(SpaceCurve.SAMPLES);
    assertThat(helix.zs()[SpaceCurve.SAMPLES - 1]).isCloseTo((float) (4 * Math.PI), within(1e-5f));
    assertThat(helix.xs()[SpaceCurve.SAMPLES - 1]).isCloseTo(1f, within(1e-5f));
    CanvasState moved =
        recomputed(s, "1")
            .recompute(
                Set.of(), Set.of("a"), sampler("2"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Path3 steeper = (Geometry.Path3) moved.geometryOf(id(2));
    assertThat(steeper.zs()[SpaceCurve.SAMPLES - 1])
        .isCloseTo((float) (8 * Math.PI), within(1e-4f));
    CanvasState gap =
        recomputed(
            CanvasState.EMPTY.add(
                i ->
                    new SpaceCurve(
                        i,
                        S,
                        expr("t"),
                        expr("sqrt(t)"),
                        expr("0"),
                        Interval.of(expr("-1"), expr("1"), true, true))),
            "1");
    Geometry.Path3 half = (Geometry.Path3) gap.geometryOf(id(1));
    assertThat(half.ys()[0]).isNaN();
    // the samples at t < 0 are gaps; the first defined one is one step past 0
    assertThat(half.bounds().orElseThrow()[2]).isBetween(0.0, 0.03);
    CanvasState unbounded =
        recomputed(
            CanvasState.EMPTY
                .add(
                    i ->
                        new SpaceCurve(
                            i, S, expr("t"), expr("t"), expr("t"), Surface.DEFAULT_DOMAIN))
                .add(
                    i ->
                        new SpaceCurve(
                            i,
                            S,
                            expr("t"),
                            expr("t"),
                            expr("t"),
                            Interval.of(expr("1"), expr("b"), true, true))),
            "1");
    assertThat(unbounded.geometryOf(id(1))).isInstanceOf(Geometry.Path3.class);
    assertThat(unbounded.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
  }

  private static CanvasState gradient(String body, String x, String y) {
    return CanvasState.EMPTY
        .add(
            i ->
                new Slider(
                    i, S, "a", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE))
        .add(i -> new Surface(i, S, expr(body), Surface.DEFAULT_DOMAIN, Surface.DEFAULT_DOMAIN, 50))
        .add(i -> new GradientArrow(i, S, id(2), expr(x), expr(y)));
  }

  @Test
  void aGradientIsThePartialDerivativesAtThePoint() {
    Geometry.Gradient g =
        (Geometry.Gradient) recomputed(gradient("x^2 - y^2", "1", "2"), "1").geometryOf(id(3));
    assertThat(g.x()).isEqualTo(1);
    assertThat(g.y()).isEqualTo(2);
    assertThat(g.z()).isCloseTo(-3, within(1e-12));
    assertThat(g.fx()).isCloseTo(2, within(1e-12));
    assertThat(g.fy()).isCloseTo(-4, within(1e-12));
    // its point reads a slider, and follows it
    CanvasState s = recomputed(gradient("x y", "a", "3"), "1");
    CanvasState moved =
        s.recompute(
            Set.of(), Set.of("a"), sampler("2"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Gradient at2 = (Geometry.Gradient) moved.geometryOf(id(3));
    assertThat(at2.x()).isEqualTo(2);
    assertThat(at2.fx()).isCloseTo(3, within(1e-12));
    assertThat(at2.fy()).isCloseTo(2, within(1e-12));
  }

  @Test
  void theGradientAgreesWithDifferenceQuotients() {
    String[] bodies = {"sin(x) cos(y)", "x^3 y - 2 y^2", "exp(-(x^2 + y^2))", "log(1 + x^2 + y^2)"};
    double h = 1e-6;
    for (String body : bodies) {
      for (double[] p : new double[][] {{0.3, -0.7}, {1.1, 0.4}, {-1.5, 2.2}}) {
        Geometry.Gradient g =
            (Geometry.Gradient)
                recomputed(gradient(body, Double.toString(p[0]), Double.toString(p[1])), "1")
                    .geometryOf(id(3));
        Sampler s = sampler("1");
        java.util.function.BiFunction<Double, Double, Double> f =
            (x, y) -> s.value(expr(body), java.util.Map.of(Surface.X, x, Surface.Y, y));
        double fx = (f.apply(p[0] + h, p[1]) - f.apply(p[0] - h, p[1])) / (2 * h);
        double fy = (f.apply(p[0], p[1] + h) - f.apply(p[0], p[1] - h)) / (2 * h);
        assertThat(g.fx()).as("%s at %s", body, p[0]).isCloseTo(fx, within(1e-6));
        assertThat(g.fy()).as("%s at %s", body, p[1]).isCloseTo(fy, within(1e-6));
      }
    }
  }

  @Test
  void aGradientIsUndefinedWhereTheSurfaceIsAndOnlyOnASurfaceOfAHeight() {
    assertThat(recomputed(gradient("sqrt(x)", "-1", "0"), "1").geometryOf(id(3)))
        .isEqualTo(Geometry.UNDEFINED);
    assertThat(recomputed(gradient("x", "b", "0"), "1").geometryOf(id(3)))
        .isEqualTo(Geometry.UNDEFINED);
    CanvasState s =
        CanvasState.EMPTY.add(
            i ->
                new ParametricSurface(
                    i,
                    S,
                    expr("u"),
                    expr("v"),
                    expr("0"),
                    ParametricSurface.U_DOMAIN,
                    ParametricSurface.V_DOMAIN,
                    50));
    assertThatThrownBy(() -> s.add(i -> new GradientArrow(i, S, id(1), expr("0"), expr("0"))))
        .hasMessageContaining("surface z = f(x, y)");
    // removing the surface takes its gradient
    assertThat(gradient("x", "0", "0").remove(id(2)).objects()).hasSize(1);
  }
}
