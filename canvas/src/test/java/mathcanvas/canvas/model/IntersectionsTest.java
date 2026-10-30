package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

class IntersectionsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-3, 3, -3, 3);
  private static final Symbol X = Symbol.of("x");
  private static final StubSampler SAMPLER = new StubSampler(Map.of());

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState all(CanvasState s, Window w) {
    return s.recomputeAll(SAMPLER, w, Recomputes.standard(), Cancellation.NEVER);
  }

  /** Points 1, 2, a line 3 through them; the parabola 4; intersections 5 and 6 of 4 and 3. */
  private static CanvasState parabolaAndLine(double y) {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, -1, y))
        .add(i -> new FreePoint(i, S, 1, y))
        .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
        .add(i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL))
        .add(i -> Intersection.of(i, S, id(4), id(3), 0))
        .add(i -> Intersection.of(i, S, id(4), id(3), 1))
        .add(i -> Intersection.of(i, S, id(4), id(3), 2));
  }

  private static Geometry.Point point(CanvasState s, long id) {
    return (Geometry.Point) s.geometryOf(id(id));
  }

  @Test
  void aNewIntersectionTakesTheCandidateAtItsIndex() {
    CanvasState s = all(parabolaAndLine(1), W);
    assertThat(point(s, 5).x()).isCloseTo(-1, within(1e-12));
    assertThat(point(s, 5).y()).isCloseTo(1, within(1e-12));
    assertThat(point(s, 6).x()).isCloseTo(1, within(1e-12));
    assertThat(s.geometryOf(id(7))).isEqualTo(Geometry.UNDEFINED);
    Intersection i = (Intersection) s.object(id(6)).orElseThrow();
    assertThat(i.anchor()).isPresent();
    assertThat(i.anchor().get().t1()).isCloseTo(1, within(1e-12));
    Intersection never = (Intersection) s.object(id(7)).orElseThrow();
    assertThat(never.anchor()).isEmpty();
  }

  @Test
  void twoLinesMeetOnce() {
    CanvasState s =
        all(
            CanvasState.EMPTY
                .add(i -> new FreePoint(i, S, 0, 0))
                .add(i -> new FreePoint(i, S, 1, 1))
                .add(i -> new FreePoint(i, S, 0, 1))
                .add(i -> new FreePoint(i, S, 1, 0))
                .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
                .add(i -> new Line(i, S, new Line.Through(id(3), id(4))))
                .add(i -> Intersection.of(i, S, id(5), id(6), 0)),
            W);
    assertThat(point(s, 7).x()).isCloseTo(0.5, within(1e-12));
    assertThat(point(s, 7).y()).isCloseTo(0.5, within(1e-12));
  }

  @Test
  void parallelAndCoincidentLinesHaveNoIntersection() {
    CanvasState base =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 1, 0))
            .add(i -> new FreePoint(i, S, 0, 1))
            .add(i -> new FreePoint(i, S, 1, 1))
            .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
            .add(i -> new Line(i, S, new Line.Through(id(3), id(4))))
            .add(i -> Intersection.of(i, S, id(5), id(6), 0));
    assertThat(all(base, W).geometryOf(id(7))).isEqualTo(Geometry.UNDEFINED);
    CanvasState same =
        all(base.replace(new FreePoint(id(3), S, 2, 0)).replace(new FreePoint(id(4), S, 3, 0)), W);
    assertThat(same.geometryOf(id(7))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void identicalGraphsOverlap() {
    CanvasState s =
        all(
            CanvasState.EMPTY
                .add(i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL))
                .add(i -> new FunctionGraph(i, S, Parse.expr("x^2"), X, FunctionGraph.ALL))
                .add(i -> Intersection.of(i, S, id(1), id(2), 0)),
            W);
    assertThat(s.geometryOf(id(3))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aTrackedIntersectionOutsideTheSampledRangeIsStillFound() {
    CanvasState s = all(parabolaAndLine(1), W);
    Window away = new Window(100, 106, 100, 106);
    CanvasState t = s.resample(SAMPLER, away, Recomputes.standard(), Cancellation.NEVER);
    assertThat(((Geometry.Curve) t.geometryOf(id(4))).branches().getFirst().x(0)).isEqualTo(97);
    assertThat(point(t, 6).x()).isCloseTo(1, within(1e-12));
    assertThat(point(t, 5).x()).isCloseTo(-1, within(1e-12));
  }

  @Test
  void aTangencyIsOneCandidate() {
    Geometry.Curve parabola = (Geometry.Curve) all(parabolaAndLine(0), W).geometryOf(id(4));
    Geometry.Curve axis = (Geometry.Curve) all(parabolaAndLine(0), W).geometryOf(id(3));
    List<Families.Candidate> cs =
        Intersections.candidates(
                parabola, axis, List.of(), SAMPLER, W.diagonal(), Cancellation.NEVER)
            .orElseThrow();
    assertThat(cs).hasSize(1);
    assertThat(cs.getFirst().tangent()).isTrue();
    assertThat(cs.getFirst().x()).isCloseTo(0, within(1e-6));
  }

  @Test
  void anImplicitCurveMeetsALineAtTheCrossingsOfItsSamplesOnce() {
    int n = 256;
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
            .add(i -> new FreePoint(i, S, 0, 0.5))
            .add(i -> new FreePoint(i, S, 1, 0.5))
            .add(i -> new Line(i, S, new Line.Through(id(2), id(3))))
            .add(i -> Intersection.of(i, S, id(1), id(4), 0))
            .add(i -> Intersection.of(i, S, id(1), id(4), 1))
            .recomputeAll(circle, W, Recomputes.standard(), Cancellation.NEVER);
    double root = Math.sqrt(0.75);
    assertThat(point(s, 5).x()).isCloseTo(-root, within(1e-3));
    assertThat(point(s, 6).x()).isCloseTo(root, within(1e-3));
    assertThat(((Intersection) s.object(id(5)).orElseThrow()).anchor().get().t1()).isNaN();
  }

  @Test
  void anUndefinedParentLeavesTheAnchor() {
    CanvasState s = all(parabolaAndLine(1), W);
    Intersection before = (Intersection) s.object(id(6)).orElseThrow();
    CanvasState t =
        s.replace(new FreePoint(id(2), S, -1, 1))
            .recompute(
                Set.of(id(2)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(t.geometryOf(id(6))).isEqualTo(Geometry.UNDEFINED);
    assertThat(t.object(id(6))).contains(before);
  }

  /** A line through (−1, y0) and (1, y1) as objects 1 to 3, a graph 4, its intersections 5, 6. */
  private static CanvasState graphAndLine(String f, double y0, double y1, int count) {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, -1, y0))
            .add(i -> new FreePoint(i, S, 1, y1))
            .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
            .add(i -> new FunctionGraph(i, S, Parse.expr(f), X, FunctionGraph.ALL));
    for (int k = 0; k < count; k++) {
      int index = k;
      s = s.add(i -> Intersection.of(i, S, id(4), id(3), index));
    }
    return s;
  }

  @Test
  void nearlyParallelCurvesStillCrossOnceAtAnySampling() {
    for (int steps : new int[] {512, 2000, 4096}) {
      StubSampler fine = new StubSampler(Map.of(), steps, List.of(), List.of());
      CanvasState s =
          graphAndLine("x/1000", 0, 0, 1)
              .recomputeAll(fine, W, Recomputes.standard(), Cancellation.NEVER);
      assertThat(point(s, 5).x()).as("%d samples", steps).isCloseTo(0, within(1e-9));
    }
  }

  @Test
  void aTrackedIntersectionSurvivesAZoomOutOverManyRoots() {
    CanvasState s =
        graphAndLine("sin(x)", 0, 0, 2)
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    double x5 = point(s, 5).x();
    double x6 = point(s, 6).x();
    CanvasState far =
        s.resample(
            SAMPLER, new Window(-90, 90, -90, 90), Recomputes.standard(), Cancellation.NEVER);
    assertThat(point(far, 5).x()).isCloseTo(x5, within(1e-9));
    assertThat(point(far, 6).x()).isCloseTo(x6, within(1e-9));
  }

  @Test
  void tolerancesAreRelativeToASmallWindow() {
    Window tiny = new Window(-1e-7, 1e-7, -1e-7, 1e-7);
    CanvasState s =
        graphAndLine("10000000 x^2 - 0.000000025", 0, 0, 2)
            .recomputeAll(SAMPLER, tiny, Recomputes.standard(), Cancellation.NEVER);
    assertThat(point(s, 5).x()).isCloseTo(-5e-8, within(1e-12));
    assertThat(point(s, 6).x()).isCloseTo(5e-8, within(1e-12));
  }

  @Test
  void aFamilyNeverSharesACandidateWhileOneIsFree() {
    CanvasState s =
        graphAndLine("x^2", 1, 1, 2)
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    // move the line so both anchors are nearer the right-hand root
    CanvasState t =
        s.replace(new FreePoint(id(1), S, -1, 2.5))
            .replace(new FreePoint(id(2), S, 1, 2.5))
            .recompute(
                Set.of(id(1), id(2)),
                Set.of(),
                SAMPLER,
                W,
                Recomputes.standard(),
                Cancellation.NEVER);
    assertThat(point(t, 5).x()).isNotCloseTo(point(t, 6).x(), within(1e-3));
  }

  @Test
  void theOffsetIsCyclicOnAClosedCurve() {
    double p = 2 * Math.PI;
    assertThat(Families.offset(0.1, p - 0.1, p)).isCloseTo(0.2, within(1e-12));
    assertThat(Families.offset(p - 0.1, 0.1, p)).isCloseTo(-0.2, within(1e-12));
    assertThat(Families.offset(Math.PI, 0, p)).isCloseTo(Math.PI, within(1e-12));
    assertThat(Families.offset(3, 1, Double.NaN)).isEqualTo(2);
  }

  @Test
  void theMatchingKeepsOrderAndMatchesAsManyAsItCan() {
    double[] score = new double[2];
    // the cheapest pairs would cross (row 0 to column 1, row 1 to column 0); order forbids it
    assertThat(Families.monotone(new double[][] {{5, 1}, {1, 5}}, score)).containsExactly(0, 1);
    assertThat(score).containsExactly(2, 10);
    // three rows, two columns: one row stays unmatched, the cheapest way
    assertThat(Families.monotone(new double[][] {{1, 9}, {2, 3}, {9, 1}}, score))
        .containsExactly(0, -1, 1);
    assertThat(Families.monotone(new double[][] {{1}, {1}}, score)).containsExactly(0, -1);
  }

  @Test
  void aRequestedCancellationStopsTheSearch() {
    CanvasState s =
        graphAndLine("sin(x)", 0, 0, 1)
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Curve graph = (Geometry.Curve) s.geometryOf(id(4));
    Geometry.Curve line = (Geometry.Curve) s.geometryOf(id(3));
    Cancellation stop = () -> true;
    assertThatThrownBy(
            () -> Intersections.candidates(graph, line, List.of(), SAMPLER, W.diagonal(), stop))
        .isInstanceOf(CancelledException.class);
    assertThatThrownBy(() -> Paths.project(graph, 0, 1, SAMPLER, stop))
        .isInstanceOf(CancelledException.class);
  }

  /** Two graphs 1 and 2 and {@code count} intersections 3 onwards, recomputed. */
  private static CanvasState graphs(String f, String g, int count, Sampler sampler, Window w) {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FunctionGraph(i, S, Parse.expr(f), X, FunctionGraph.ALL))
            .add(i -> new FunctionGraph(i, S, Parse.expr(g), X, FunctionGraph.ALL));
    for (int k = 0; k < count; k++) {
      int index = k;
      s = s.add(i -> Intersection.of(i, S, id(1), id(2), index));
    }
    return s.recomputeAll(sampler, w, Recomputes.standard(), Cancellation.NEVER);
  }

  private static List<Families.Candidate> candidatesOf(CanvasState s, Window w, Sampler sm) {
    return Intersections.candidates(
            (Geometry.Curve) s.geometryOf(id(1)),
            (Geometry.Curve) s.geometryOf(id(2)),
            List.of(),
            sm,
            w.diagonal(),
            Cancellation.NEVER)
        .orElseThrow();
  }

  @Test
  void aTripleRootIsOneTangency() {
    StubSampler sampler = new StubSampler(Map.of("a", 0.0));
    List<Families.Candidate> one =
        candidatesOf(graphs("x^3 - a x", "0", 0, sampler, W), W, sampler);
    assertThat(one).hasSize(1);
    assertThat(one.getFirst().tangent()).isTrue();
    StubSampler apart = sampler.with("a", 0.01);
    assertThat(candidatesOf(graphs("x^3 - a x", "0", 0, apart, W), W, apart)).hasSize(3);
  }

  @Test
  void aContactOfHighOrderIsNotAnOverlap() {
    Window unit = new Window(-1, 1, -1, 1);
    for (String f : List.of("x^3", "x^4", "x^5", "x^9")) {
      for (int steps : new int[] {512, 2000}) {
        StubSampler sampler = new StubSampler(Map.of(), steps, List.of(), List.of());
        CanvasState s = graphs(f, "0", 1, sampler, unit);
        assertThat(at(s, 3)).as("%s at %d samples", f, steps).isNotNull();
        assertThat(at(s, 3).x()).as(f).isCloseTo(0, within(0.2));
      }
    }
    for (String f : List.of("x^7", "x^9")) {
      CanvasState s = graphs(f, "0", 1, SAMPLER, W);
      assertThat(at(s, 3)).as(f).isNotNull();
      assertThat(at(s, 3).x()).as(f).isCloseTo(0, within(0.2));
    }
    assertThat(graphs("x", "x", 1, SAMPLER, W).geometryOf(id(3))).isEqualTo(Geometry.UNDEFINED);
    assertThat(graphs("sin(x)", "sin(x) + 0", 1, SAMPLER, W).geometryOf(id(3)))
        .isEqualTo(Geometry.UNDEFINED);
  }

  private static Geometry.Point at(CanvasState s, long id) {
    return s.geometryOf(id(id)) instanceof Geometry.Point p ? p : null;
  }

  @Test
  void nearParallelCurvesCostLittle() {
    StubSampler fine = new StubSampler(Map.of(), 20000, List.of(), List.of());
    CanvasState s = graphs("x", "x + 0.000001", 0, fine, W);
    fine.points = 0;
    assertThat(candidatesOf(s, W, fine)).isEmpty();
    assertThat(fine.points).isLessThan(2000);
  }

  @Test
  void positionsOnACurveWithNoParameterAreArcLengths() {
    Polyline square =
        Polyline.unparametrised(new double[] {0, 1, 1, 0, 0}, new double[] {0, 0, 1, 1, 0});
    Geometry.Curve c = new Geometry.Curve(Path.POLYLINE_ONLY, List.of(square));
    Families.Positions pos = new Families.Positions(c, SAMPLER, W.diagonal(), Cancellation.NEVER);
    assertThat(pos.period).isEqualTo(4);
    assertThat(pos.of(Double.NaN, 1, 0.5)).isCloseTo(1.5, within(1e-12));
    assertThat(pos.of(Double.NaN, 0.2, -0.1)).isCloseTo(0.2, within(1e-12));
    // across the seam: from 3.9 to 0.1 is forward by 0.2
    assertThat(pos.offset(0.1, 3.9)).isCloseTo(0.2, within(1e-12));
  }

  @Test
  void anImplicitCurvesIntersectionsKeepTheirOrderThroughATangency() {
    int n = 256;
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
            .add(i -> new FreePoint(i, S, 0, 0.5))
            .add(i -> new FreePoint(i, S, 1, 0.5))
            .add(i -> new Line(i, S, new Line.Through(id(2), id(3))))
            .add(i -> Intersection.of(i, S, id(1), id(4), 0))
            .add(i -> Intersection.of(i, S, id(1), id(4), 1))
            .recomputeAll(circle, W, Recomputes.standard(), Cancellation.NEVER);
    // ordered by position: index 0 is the left-hand root
    for (double c : List.of(0.5, 0.9, 1.0, 1.1, 0.9, 0.5, -0.5, 0.5)) {
      s =
          s.replace(new FreePoint(id(2), S, 0, c))
              .replace(new FreePoint(id(3), S, 1, c))
              .recompute(
                  Set.of(id(2), id(3)),
                  Set.of(),
                  circle,
                  W,
                  Recomputes.standard(),
                  Cancellation.NEVER);
      if (c < 0.99) {
        double r = Math.sqrt(1 - c * c);
        assertThat(at(s, 5).x()).as("c=%s", c).isCloseTo(-r, within(1e-3));
        assertThat(at(s, 6).x()).as("c=%s", c).isCloseTo(r, within(1e-3));
      } else if (c > 1) {
        assertThat(at(s, 5)).isNull();
        assertThat(at(s, 6)).isNull();
      }
    }
  }

  @Test
  void curvesThatOnlyApproachEachOtherDoNotMeet() {
    for (int steps : new int[] {512, 2000}) {
      StubSampler sampler = new StubSampler(Map.of(), steps, List.of(), List.of());
      CanvasState s = graphs("x^2", "x^2 + 0.0000001", 1, sampler, W);
      assertThat(candidatesOf(s, W, sampler)).as("%d samples", steps).isEmpty();
      assertThat(s.geometryOf(id(3))).isEqualTo(Geometry.UNDEFINED);
    }
  }
}
