package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Quadrature.Method;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/** The shaded areas and the Riemann sums: their rings and values, their refusals, their edges. */
class AreasTest {

  private static final Style S = Style.DEFAULT;
  private static final Symbol X = Symbol.of("x");
  private static final Window W = new Window(-4, 4, -4, 4);

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState all(CanvasState s, Sampler sampler) {
    return s.recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Geometry.ValuedRegion valued(CanvasState s, long id) {
    return (Geometry.ValuedRegion) s.geometryOf(id(id));
  }

  private static CanvasState graphs(String... bodies) {
    CanvasState s = CanvasState.EMPTY;
    for (String b : bodies) {
      s = s.add(i -> new FunctionGraph(i, S, Parse.expr(b), X, FunctionGraph.ALL));
    }
    return s;
  }

  private static ShadedArea area(ObjectId id, long upper, Long lower, String from, String to) {
    return new ShadedArea(
        id,
        S,
        id(upper),
        lower == null ? Optional.empty() : Optional.of(id(lower)),
        Parse.expr(from),
        Parse.expr(to));
  }

  @Test
  void anAreaUnderAGraphIsItsIntegralAndItsRingClosesOnTheAxis() {
    CanvasState s =
        all(graphs("x^2").add(i -> area(i, 1, null, "0", "2")), new StubSampler(Map.of()));
    Geometry.ValuedRegion r = valued(s, 2);
    assertThat(r.value()).isCloseTo(8 / 3.0, within(1e-12));
    assertThat(r.rings()).hasSize(1);
    Polyline ring = r.rings().getFirst();
    // from (0, 0) along the graph to (2, 4), then back along the axis to the start
    assertThat(ring.x(0)).isZero();
    assertThat(ring.y(0)).isZero();
    assertThat(ring.x(ring.size() - 1)).isEqualTo(ring.x(0));
    assertThat(ring.y(ring.size() - 1)).isEqualTo(ring.y(0));
    int top = ring.size() / 2 - 1;
    assertThat(ring.x(top)).isEqualTo(2);
    assertThat(ring.y(top)).isCloseTo(4, within(1e-12));
    assertThat(ring.y(top + 1)).isZero();
  }

  @Test
  void anAreaBetweenCrossingCurvesIsOneRingPerLobe() {
    // x against x² − 2 on [−2, 3]: they cross at −1 and 2, three lobes, no ring crossing itself
    CanvasState s =
        all(graphs("x", "x^2 - 2").add(i -> area(i, 1, 2L, "-2", "3")), new StubSampler(Map.of()));
    Geometry.ValuedRegion r = valued(s, 3);
    // ∫(x − x² + 2) from −2 to 3: 5/2 − 35/3 + 10
    assertThat(r.value()).isCloseTo(5 / 6.0, within(1e-12));
    assertThat(r.rings()).hasSize(3);
    assertThat(r.rings().get(0).x(r.rings().get(0).size() / 2)).isCloseTo(-1, within(1e-2));
    for (Polyline ring : r.rings()) {
      for (int k = 0; k < ring.size(); k++) {
        assertThat(ring.y(k)).isBetween(-2.0, 7.0);
      }
    }
  }

  @Test
  void anAreaBetweenTwoGraphsIsTheSignedIntegralOfTheirDifference() {
    CanvasState s =
        all(graphs("x", "x^2").add(i -> area(i, 1, 2L, "0", "1")), new StubSampler(Map.of()));
    assertThat(valued(s, 3).value()).isCloseTo(1 / 6.0, within(1e-12));
    // below the axis the integral is negative
    CanvasState below =
        all(graphs("-1").add(i -> area(i, 1, null, "0", "3")), new StubSampler(Map.of()));
    assertThat(valued(below, 2).value()).isCloseTo(-3, within(1e-12));
  }

  @Test
  void theBoundsReadSlidersAndTheAreaFollowsThem() {
    CanvasState s = graphs("x").add(i -> area(i, 1, null, "0", "b"));
    assertThat(s.object(id(2)).orElseThrow().reads()).containsExactly("b");
    StubSampler sampler = new StubSampler(Map.of("b", 2.0));
    CanvasState r = all(s, sampler);
    assertThat(valued(r, 2).value()).isCloseTo(2, within(1e-12));
    CanvasState moved =
        r.recompute(
            Set.of(),
            Set.of("b"),
            sampler.with("b", 4),
            W,
            Recomputes.standard(),
            Cancellation.NEVER);
    assertThat(valued(moved, 2).value()).isCloseTo(8, within(1e-12));
  }

  @Test
  void anAreaIsUndefinedWhenItsBoundsAreNotOrderedNumbersInTheDomain() {
    StubSampler sampler = new StubSampler(Map.of());
    for (String[] b : new String[][] {{"1", "1"}, {"2", "1"}, {"0", "c"}}) {
      CanvasState s = all(graphs("x").add(i -> area(i, 1, null, b[0], b[1])), sampler);
      assertThat(s.geometryOf(id(2))).as("%s to %s", b[0], b[1]).isEqualTo(Geometry.UNDEFINED);
      assertThat(Areas.problem(s.object(id(2)).orElseThrow(), s.geometry(), sampler))
          .contains(Areas.Problem.BOUNDS);
    }
    CanvasState domain =
        all(
            CanvasState.EMPTY
                .add(i -> new FunctionGraph(i, S, Parse.expr("x"), X, Parse.interval("0", "1")))
                .add(i -> area(i, 1, null, "0", "2")),
            sampler);
    assertThat(domain.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void anAreaOverAPoleIsUndefined() {
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState s = all(graphs("1/x").add(i -> area(i, 1, null, "-1", "1")), sampler);
    assertThat(s.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
    assertThat(Areas.problem(s.object(id(2)).orElseThrow(), s.geometry(), sampler))
        .contains(Areas.Problem.NO_VALUE);
  }

  @Test
  void anAreaWhoseIntegralDoesNotConvergeIsUndefined() {
    StubSampler stub = new StubSampler(Map.of());
    Sampler unsettled =
        new Sampler() {
          @Override
          public double value(mathcanvas.engine.expr.Expr e, Map<Symbol, Double> locals) {
            return stub.value(e, locals);
          }

          @Override
          public Sample point(Parametrisation.Expressed p, double t) {
            return stub.point(p, t);
          }

          @Override
          public java.util.List<Polyline> sample(
              Parametrisation.Expressed p, double lo, double hi, Window w, Cancellation c) {
            return stub.sample(p, lo, hi, w, c);
          }

          @Override
          public java.util.List<Polyline> contour(
              mathcanvas.engine.expr.Relation e, Window w, Cancellation c) {
            return java.util.List.of();
          }

          @Override
          public java.util.List<Polyline> region(
              mathcanvas.engine.expr.Relation e, Window w, Cancellation c) {
            return java.util.List.of();
          }

          @Override
          public mathcanvas.engine.compile.Quadrature.Estimate integral(
              Parametrisation.Graph g, double a, double b, Cancellation c) {
            return new mathcanvas.engine.compile.Quadrature.Estimate(1, 1, false);
          }
        };
    CanvasState s = all(graphs("x").add(i -> area(i, 1, null, "0", "1")), unsettled);
    assertThat(s.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
    assertThat(Areas.problem(s.object(id(2)).orElseThrow(), s.geometry(), unsettled))
        .contains(Areas.Problem.NO_VALUE);
  }

  @Test
  void anAreaNeedsFunctionGraphsAndTwoDistinctOnes() {
    CanvasState points = CanvasState.EMPTY.add(i -> new FreePoint(i, S, 0, 0));
    assertThatThrownBy(() -> points.add(i -> area(i, 1, null, "0", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("function graph");
    assertThatThrownBy(() -> area(id(2), 1, 1L, "0", "1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RiemannSum sum(ObjectId id, String n, Method m) {
    return new RiemannSum(id, S, id(1), Parse.expr("0"), Parse.expr("1"), Parse.expr(n), m);
  }

  @Test
  void riemannSumsOfALineHaveTheirClosedForms() {
    StubSampler sampler = new StubSampler(Map.of());
    double[][] expected = {{3 / 8.0, 5 / 8.0, 0.5, 0.5}};
    Method[] methods = Method.values();
    for (int k = 0; k < methods.length; k++) {
      Method m = methods[k];
      CanvasState s = all(graphs("x").add(i -> sum(i, "4", m)), sampler);
      Geometry.ValuedRegion r = valued(s, 2);
      assertThat(r.value()).as("%s", m).isCloseTo(expected[0][k], within(1e-15));
      assertThat(r.rings()).hasSize(4);
    }
  }

  @Test
  void aRiemannSumsRectanglesStandOnTheAxisAtTheirHeights() {
    CanvasState s =
        all(graphs("x^2").add(i -> sum(i, "2", Method.RIGHT)), new StubSampler(Map.of()));
    Geometry.ValuedRegion r = valued(s, 2);
    Polyline second = r.rings().get(1);
    // (0.5, 0), (1, 0), (1, 1), (0.5, 1), back to (0.5, 0)
    assertThat(second.size()).isEqualTo(5);
    assertThat(second.x(0)).isEqualTo(0.5);
    assertThat(second.y(0)).isZero();
    assertThat(second.y(2)).isEqualTo(1);
    assertThat(second.y(3)).isEqualTo(1);
    assertThat(r.value()).isCloseTo((0.25 + 1) / 2, within(1e-15));
  }

  @Test
  void theNumberOfPiecesReadsASliderAndMustBeAWholeNumberInRange() {
    CanvasState s = graphs("x").add(i -> sum(i, "n", Method.LEFT));
    StubSampler sampler = new StubSampler(Map.of("n", 10.0));
    assertThat(valued(all(s, sampler), 2).rings()).hasSize(10);
    for (double n : new double[] {0, 2.5, 1001, Double.NaN}) {
      CanvasState r = all(s, sampler.with("n", n));
      assertThat(r.geometryOf(id(2))).as("n = %s", n).isEqualTo(Geometry.UNDEFINED);
      assertThat(Areas.problem(r.object(id(2)).orElseThrow(), r.geometry(), sampler.with("n", n)))
          .contains(Areas.Problem.PIECES);
    }
  }

  @Test
  void aSumWithAnUndefinedHeightIsUndefinedWithItsReason() {
    // 1/x on [−1, 1] by the left rule samples 0
    StubSampler sampler = new StubSampler(Map.of());
    CanvasState pole =
        all(
            graphs("1/x")
                .add(
                    i ->
                        new RiemannSum(
                            i,
                            S,
                            id(1),
                            Parse.expr("-1"),
                            Parse.expr("1"),
                            Parse.expr("4"),
                            Method.LEFT)),
            sampler);
    assertThat(pole.geometryOf(id(2))).isEqualTo(Geometry.UNDEFINED);
    assertThat(Areas.problem(pole.object(id(2)).orElseThrow(), pole.geometry(), sampler))
        .contains(Areas.Problem.HEIGHT);
  }

  @Test
  void bothKindsFollowTheirGraphAndKeepTheirRecords() {
    CanvasState s =
        graphs("a x")
            .add(i -> area(i, 1, null, "0", "1"))
            .add(i -> sum(i, "2", Method.MIDPOINT))
            .add(
                i ->
                    new Slider(
                        i,
                        S,
                        "a",
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        BigDecimal.ONE));
    StubSampler sampler = new StubSampler(Map.of("a", 1.0));
    CanvasState r = all(s, sampler);
    assertThat(valued(r, 2).value()).isCloseTo(0.5, within(1e-12));
    CanvasState moved =
        r.recompute(
            Set.of(),
            Set.of("a"),
            sampler.with("a", 4),
            W,
            Recomputes.standard(),
            Cancellation.NEVER);
    assertThat(valued(moved, 2).value()).isCloseTo(2, within(1e-12));
    assertThat(valued(moved, 3).value()).isCloseTo(2, within(1e-12));
    assertThat(moved.objects()).isEqualTo(s.objects());
    assertThat(((RiemannSum) s.object(id(3)).orElseThrow()).withMethod(Method.LEFT).method())
        .isEqualTo(Method.LEFT);
  }
}
