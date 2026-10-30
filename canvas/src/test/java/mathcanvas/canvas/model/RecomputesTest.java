package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class RecomputesTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-2, 2, -2, 2);
  private static final Symbol X = Symbol.of("x");
  private static final Symbol T = Symbol.of("t");

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState all(CanvasState s, Sampler sampler) {
    return s.recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Geometry.Point point(CanvasState s, long id) {
    return (Geometry.Point) s.geometryOf(id(id));
  }

  private static Geometry.Curve curve(CanvasState s, long id) {
    return (Geometry.Curve) s.geometryOf(id(id));
  }

  @Test
  void pointsTextAndSliders() {
    CanvasState s =
        all(
            CanvasState.EMPTY
                .add(i -> new FreePoint(i, S, 1, 2))
                .add(i -> new Text(i, S, 3, 4, "hi"))
                .add(
                    i ->
                        new Slider(
                            i,
                            S,
                            "a",
                            BigDecimal.ZERO,
                            BigDecimal.ONE,
                            new BigDecimal("0.1"),
                            new BigDecimal("0.3"))),
            new StubSampler(Map.of()));
    assertThat(s.geometryOf(id(1))).isEqualTo(new Geometry.Point(1, 2));
    assertThat(s.geometryOf(id(2))).isEqualTo(new Geometry.Point(3, 4));
    assertThat(s.geometryOf(id(3))).isEqualTo(new Geometry.Value(0.3));
  }

  @Test
  void anUnboundedGraphIsSampledOverTheWidenedWindow() {
    CanvasState s =
        all(
            CanvasState.EMPTY.add(
                i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL)),
            new StubSampler(Map.of()));
    Geometry.Curve c = curve(s, 1);
    Polyline b = c.branches().getFirst();
    assertThat(b.x(0)).isEqualTo(-4);
    assertThat(b.x(b.size() - 1)).isEqualTo(4);
    assertThat(b.y(0)).isEqualTo(16);
    assertThat(c.path())
        .isInstanceOfSatisfying(
            Path.Parametrised.class,
            p -> {
              assertThat(p.kind()).isEqualTo(ParamKind.X);
              assertThat(p.lo()).isEqualTo(Double.NEGATIVE_INFINITY);
            });
  }

  @Test
  void aBoundedDomainReadsItsVariablesAndAnEmptyOneIsUndefined() {
    CanvasState s =
        CanvasState.EMPTY.add(
            i -> new FunctionGraph(i, S, Parse.expr("x"), X, Parse.interval("0", "b")));
    Geometry.Curve c = curve(all(s, new StubSampler(Map.of("b", 1.0))), 1);
    assertThat(c.branches().getFirst().x(0)).isZero();
    assertThat(c.branches().getFirst().x(c.branches().getFirst().size() - 1)).isEqualTo(1);
    assertThat(all(s, new StubSampler(Map.of("b", -1.0))).geometryOf(id(1)))
        .isEqualTo(Geometry.UNDEFINED);
    assertThat(all(s, new StubSampler(Map.of())).geometryOf(id(1))).isEqualTo(Geometry.UNDEFINED);
    CanvasState far =
        CanvasState.EMPTY.add(
            i -> new FunctionGraph(i, S, Parse.expr("x"), X, Parse.interval("10", "11")));
    assertThat(curve(all(far, new StubSampler(Map.of())), 1).branches()).isEmpty();
  }

  @Test
  void parametricAndPolarCurvesNeedFiniteDomains() {
    CanvasState s =
        CanvasState.EMPTY
            .add(
                i ->
                    new ParametricCurve(
                        i, S, Parse.expr("cos(t)"), Parse.expr("sin(t)"), T, FunctionGraph.ALL))
            .add(i -> new PolarCurve(i, S, Parse.expr("2"), T, PolarCurve.TURN));
    CanvasState r = all(s, new StubSampler(Map.of()));
    assertThat(r.geometryOf(id(1))).isEqualTo(Geometry.UNDEFINED);
    Geometry.Curve polar = curve(r, 2);
    Polyline b = polar.branches().getFirst();
    assertThat(b.x(0)).isEqualTo(2);
    assertThat(b.t(b.size() - 1)).isEqualTo(2 * Math.PI);
    assertThat(((Path.Parametrised) polar.path()).kind()).isEqualTo(ParamKind.THETA);
  }

  @Test
  void segmentsAndLines() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 1, 1))
            .add(i -> new Segment(i, S, id(1), id(2)))
            .add(i -> new Line(i, S, new Line.Through(id(1), id(2))));
    CanvasState r = all(s, new StubSampler(Map.of()));
    assertThat(curve(r, 3).branches().getFirst())
        .isEqualTo(new Polyline(new double[] {0, 1}, new double[] {0, 1}, new double[] {0, 1}));
    Polyline drawn = curve(r, 4).branches().getFirst();
    assertThat(drawn.x(0)).isEqualTo(-4);
    assertThat(drawn.x(1)).isEqualTo(4);
    assertThat(drawn.t(0)).isEqualTo(-4);

    CanvasState same = all(s.replace(new FreePoint(id(2), S, 0, 0)), new StubSampler(Map.of()));
    assertThat(same.geometryOf(id(4))).isEqualTo(Geometry.UNDEFINED);
    assertThat(curve(same, 3).branches().getFirst().size()).isEqualTo(2);

    CanvasState away =
        all(
            s.replace(new FreePoint(id(1), S, 0, 100)).replace(new FreePoint(id(2), S, 1, 100)),
            new StubSampler(Map.of()));
    assertThat(curve(away, 4).branches()).isEmpty();
  }

  @Test
  void aTangentLineTakesItsSlopeAtThePoint() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL))
            .add(i -> new PointOnPath(i, S, id(1), ParamKind.X, 1, Double.NaN, Double.NaN))
            .add(i -> new Line(i, S, new Line.PointSlope(id(2), Parse.expr("2 x"), X)))
            .add(i -> new Line(i, S, new Line.PointSlope(id(2), Parse.expr("1/0"), X)));
    CanvasState r = all(s, new StubSampler(Map.of()));
    Path.Parametrised p = (Path.Parametrised) curve(r, 3).path();
    assertThat(p.parametrisation()).isEqualTo(new Parametrisation.Linear(1, 1, 1, 2));
    assertThat(r.geometryOf(id(4))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aPointOnAGraphKeepsItsParameterOutsideTheDomain() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FunctionGraph(i, S, Parse.expr("a x"), X, Parse.interval("0", "b")))
            .add(i -> new PointOnPath(i, S, id(1), ParamKind.X, 0.8, Double.NaN, Double.NaN));
    StubSampler wide = new StubSampler(Map.of("a", 2.0, "b", 1.0));
    CanvasState r = all(s, wide);
    assertThat(r.geometryOf(id(2))).isEqualTo(new Geometry.Point(0.8, 1.6));
    CanvasState shrunk =
        r.recompute(
            Set.of(),
            Set.of("b"),
            wide.with("b", 0.5),
            W,
            Recomputes.standard(),
            Cancellation.NEVER);
    assertThat(shrunk.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
    assertThat(((PointOnPath) shrunk.object(id(2)).orElseThrow()).parameter()).isEqualTo(0.8);
    CanvasState back =
        shrunk.recompute(Set.of(), Set.of("b"), wide, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(back.geometryOf(id(2))).isEqualTo(new Geometry.Point(0.8, 1.6));
  }

  @Test
  void aPathRedefinedWithAnotherParameterProjectsTheLastPosition() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FunctionGraph(i, S, Parse.expr("0"), X, FunctionGraph.ALL))
            .add(i -> new PointOnPath(i, S, id(1), ParamKind.X, 1, Double.NaN, Double.NaN));
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState r = all(s, sampler);
    CanvasState polar =
        all(r.replace(new PolarCurve(id(1), S, Parse.expr("1"), T, PolarCurve.TURN)), sampler);
    PointOnPath p = (PointOnPath) polar.object(id(2)).orElseThrow();
    assertThat(p.kind()).isEqualTo(ParamKind.THETA);
    assertThat(p.parameter()).isCloseTo(0, within(1e-6));
    assertThat(point(polar, 2).x()).isCloseTo(1, within(1e-9));
  }

  @Test
  void aPointOnAnImplicitCurveIsProjected() {
    int n = 64;
    double[] xs = new double[n + 1];
    double[] ys = new double[n + 1];
    for (int k = 0; k <= n; k++) {
      xs[k] = Math.cos(2 * Math.PI * k / n);
      ys[k] = Math.sin(2 * Math.PI * k / n);
    }
    StubSampler circle =
        new StubSampler(Map.of(), 8, List.of(Polyline.unparametrised(xs, ys)), List.of());
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new ImplicitCurve(i, S, Parse.relation("x^2 + y^2 = 1")))
            .add(i -> new PointOnPath(i, S, id(1), ParamKind.NONE, Double.NaN, 3, 0));
    CanvasState r = all(s, circle);
    assertThat(point(r, 2)).isEqualTo(new Geometry.Point(1, 0));
    PointOnPath p = (PointOnPath) r.object(id(2)).orElseThrow();
    assertThat(p.lastX()).isEqualTo(1);
    assertThat(curve(r, 1).path()).isEqualTo(Path.POLYLINE_ONLY);
  }

  @Test
  void aRegionIsTheSamplersRings() {
    Polyline ring = Polyline.unparametrised(new double[] {0, 1, 1, 0}, new double[] {0, 0, 1, 0});
    CanvasState r =
        all(
            CanvasState.EMPTY.add(i -> new InequalityRegion(i, S, Parse.relation("y < x"))),
            new StubSampler(Map.of(), 8, List.of(), List.of(ring)));
    assertThat(r.geometryOf(id(1))).isEqualTo(new Geometry.Region(List.of(ring)));
  }
}
