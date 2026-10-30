package mathcanvas.canvas.sample;

import static mathcanvas.canvas.sample.SamplersTest.expr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.Set;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.ParametricCurve;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.PolarCurve;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/** The graph kinds through the standard recompute with the real sampler. */
class GraphKindsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-5, 5, -5, 5);
  private static final Symbol T = Symbol.of("t");

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState scene() {
    return CanvasState.EMPTY
        .add(
            i ->
                new Slider(
                    i,
                    S,
                    "a",
                    BigDecimal.ZERO,
                    BigDecimal.TWO,
                    new BigDecimal("0.1"),
                    BigDecimal.ONE))
        .add(i -> new FunctionGraph(i, S, expr("a sin(x)"), SamplersTest.X, FunctionGraph.ALL))
        .add(i -> new FunctionGraph(i, S, expr("x/2"), SamplersTest.X, FunctionGraph.ALL))
        .add(
            i ->
                new ParametricCurve(
                    i,
                    S,
                    expr("2cos(t)"),
                    expr("sin(t)"),
                    T,
                    Interval.of(expr("0"), expr("2π"), true, true)))
        .add(i -> new PolarCurve(i, S, expr("cos(3θ)"), Symbol.of("θ"), PolarCurve.TURN))
        .add(i -> new ImplicitCurve(i, S, (Relation) expr("x^2 + y^2 = 4")))
        .add(i -> new InequalityRegion(i, S, (Relation) expr("y < x - 1")))
        .add(i -> new PointOnPath(i, S, id(2), ParamKind.X, 1, Double.NaN, Double.NaN))
        .add(i -> Intersection.of(i, S, id(2), id(3), 0));
  }

  private static Sampler sampler(String a) {
    return new Samplers().over(SamplersTest.env("a", a), Settings.DEFAULT);
  }

  @Test
  void everyKindHasItsGeometry() {
    CanvasState s =
        scene().recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    for (long k = 2; k <= 6; k++) {
      assertThat(s.geometryOf(id(k))).isInstanceOf(Geometry.Curve.class);
      assertThat(((Geometry.Curve) s.geometryOf(id(k))).branches()).isNotEmpty();
    }
    assertThat(((Geometry.Region) s.geometryOf(id(7))).rings()).isNotEmpty();
    Geometry.Point on = (Geometry.Point) s.geometryOf(id(8));
    assertThat(on.y()).isCloseTo(Math.sin(1), within(1e-12));
    Geometry.Point cross = (Geometry.Point) s.geometryOf(id(9));
    assertThat(Math.sin(cross.x())).isCloseTo(cross.x() / 2, within(1e-9));
  }

  @Test
  void aPolarCurvesTurnFollowsTheAngleMode() {
    for (AngleMode mode : AngleMode.values()) {
      CanvasState s =
          CanvasState.EMPTY
              .add(
                  i -> new PolarCurve(i, S, expr("cos(3θ)"), Symbol.of("θ"), PolarCurve.turn(mode)))
              .recomputeAll(
                  new Samplers().over(Environment.EMPTY, Settings.DEFAULT.withAngleMode(mode)),
                  W,
                  Recomputes.standard(),
                  Cancellation.NEVER);
      Polyline rose = ((Geometry.Curve) s.geometryOf(id(1))).branches().getFirst();
      double minX = 0;
      double maxY = 0;
      for (int k = 0; k < rose.size(); k++) {
        minX = Math.min(minX, rose.x(k));
        maxY = Math.max(maxY, rose.y(k));
      }
      // three petals at 0°, 120° and 240°: the whole rose, as dense sampling in radians has it
      double wantMinX = 0;
      double wantMaxY = 0;
      for (int k = 0; k <= 100_000; k++) {
        double t = 2 * Math.PI * k / 100_000;
        wantMinX = Math.min(wantMinX, Math.cos(3 * t) * Math.cos(t));
        wantMaxY = Math.max(wantMaxY, Math.cos(3 * t) * Math.sin(t));
      }
      assertThat(minX).as("%s", mode).isCloseTo(wantMinX, within(1e-3));
      assertThat(maxY).as("%s", mode).isCloseTo(wantMaxY, within(1e-3));
      assertThat(rose.x(rose.size() - 1)).isCloseTo(rose.x(0), within(1e-9));
      assertThat(rose.y(rose.size() - 1)).isCloseTo(rose.y(0), within(1e-9));
    }
  }

  @Test
  void aSlidersChangeMovesItsReadersAndTheirDependents() {
    CanvasState before =
        scene().recomputeAll(sampler("1"), W, Recomputes.standard(), Cancellation.NEVER);
    CanvasState after =
        before.recompute(
            Set.of(), Set.of("a"), sampler("2"), W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Point on = (Geometry.Point) after.geometryOf(id(8));
    assertThat(on.y()).isCloseTo(2 * Math.sin(1), within(1e-12));
    Geometry.Point cross = (Geometry.Point) after.geometryOf(id(9));
    assertThat(2 * Math.sin(cross.x())).isCloseTo(cross.x() / 2, within(1e-9));
    assertThat(after.geometryOf(id(6))).isEqualTo(before.geometryOf(id(6)));
  }
}
