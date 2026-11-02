package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/** Distances, areas and slopes updating live, and loci traced through their constructions. */
class MeasuresAndLociTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new StubSampler(Map.of());
  private static final double EPS = 1e-12;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** A(1) at (0, 0), B(2) at (4, 0), C(3) at (0, 3); the segment AB (4), the line AC (5). */
  private static CanvasState figure() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 4, 0))
        .add(i -> new FreePoint(i, S, 0, 3))
        .add(i -> new Segment(i, S, id(1), id(2)))
        .add(i -> new Line(i, S, new Line.Through(id(1), id(3))));
  }

  private static CanvasState recomputed(CanvasState s) {
    return s.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState move(CanvasState s, long point, double x, double y) {
    FreePoint p = (FreePoint) s.object(id(point)).orElseThrow();
    return s.replace(p.at(x, y))
        .recompute(
            Set.of(id(point)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static double value(CanvasState s, long id) {
    return ((Geometry.Measure) s.geometryOf(id(id))).value();
  }

  @Test
  void distancesToPointsLinesAndSegmentsUpdateLive() {
    CanvasState s =
        recomputed(
            figure()
                .add(i -> new Distance(i, S, id(2), id(3)))
                .add(i -> new Distance(i, S, id(2), id(5)))
                .add(i -> new Distance(i, S, id(3), id(4))));
    assertThat(value(s, 6)).isCloseTo(5, within(EPS));
    assertThat(((Geometry.Measure) s.geometryOf(id(6))).at()).isEqualTo(new Geometry.Point(2, 1.5));
    assertThat(value(s, 7)).isCloseTo(4, within(EPS));
    assertThat(value(s, 8)).isCloseTo(3, within(EPS));
    // C moved beyond B's end of the segment: the distance is to the end
    s = move(s, 3, 7, 4);
    assertThat(value(s, 8)).isCloseTo(5, within(EPS));
    assertThat(value(s, 6)).isCloseTo(Math.hypot(3, 4), within(EPS));
  }

  @Test
  void areasOfPolygonsAndCirclesAndSlopes() {
    CanvasState s =
        recomputed(
            figure()
                .add(i -> new Polygon(i, S, new Polygon.Vertices(List.of(id(1), id(2), id(3)))))
                .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
                .add(i -> new Area(i, S, id(6)))
                .add(i -> new Area(i, S, id(7)))
                .add(i -> new Slope(i, S, id(4)))
                .add(i -> new Slope(i, S, id(5)))
                .add(i -> new Line(i, S, new Line.Through(id(2), id(3))))
                .add(i -> new Slope(i, S, id(12))));
    assertThat(value(s, 8)).isCloseTo(6, within(EPS));
    assertThat(value(s, 9)).isCloseTo(16 * Math.PI, within(1e-12));
    assertThat(value(s, 10)).isZero();
    assertThat(s.geometryOf(id(11))).isEqualTo(Geometry.UNDEFINED); // AC is vertical
    assertThat(value(s, 13)).isCloseTo(-0.75, within(EPS));
    s = move(s, 3, 1, 3);
    assertThat(value(s, 11)).isCloseTo(3, within(EPS));
    assertThat(value(s, 8)).isCloseTo(6, within(EPS));
  }

  /**
   * The circle about O(1) through P0(2), radius 2; P(3) on it; A(4) at (6, 0); M(5) the midpoint of
   * A and P; the locus of M as P moves (6).
   */
  private static CanvasState midpointLocus() {
    return recomputed(
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 2, 0))
            .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
            .add(i -> new PointOnPath(i, S, id(3), ParamKind.ANGLE, 1, Double.NaN, Double.NaN))
            .add(i -> new FreePoint(i, S, 6, 0))
            .add(i -> new Midpoint(i, S, id(4), id(5)))
            .add(i -> new Locus(i, S, id(6), id(4))));
  }

  @Test
  void theLocusOfAMidpointIsACircleOfHalfTheRadius() {
    CanvasState s = midpointLocus();
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(7));
    assertThat(c.path()).isEqualTo(Path.POLYLINE_ONLY);
    int n = 0;
    for (Polyline b : c.branches()) {
      for (int k = 0; k < b.size(); k++) {
        assertThat(Math.hypot(b.x(k) - 3, b.y(k))).isCloseTo(1, within(1e-12));
        n++;
      }
    }
    assertThat(n).isGreaterThan(Loci.FIRST);
    // closed: it ends where it starts
    Polyline b = c.branches().getFirst();
    assertThat(b.x(0)).isCloseTo(b.x(b.size() - 1), within(1e-12));
    // the driver is where it was: the locus moved a copy
    assertThat(((PointOnPath) s.object(id(4)).orElseThrow()).parameter()).isEqualTo(1);
    // A dragged: the locus follows
    s = move(s, 5, 6, 4);
    for (Polyline p : ((Geometry.Curve) s.geometryOf(id(7))).branches()) {
      for (int k = 0; k < p.size(); k++) {
        assertThat(Math.hypot(p.x(k) - 3, p.y(k) - 2)).isCloseTo(1, within(1e-12));
      }
    }
  }

  @Test
  void aLocusNeedsItsPointToDependOnAFreeDriver() {
    CanvasState s =
        recomputed(
            midpointLocus()
                .add(i -> new Locus(i, S, id(2), id(4)))
                .add(
                    i ->
                        new PointOnPath(
                            i,
                            S,
                            id(3),
                            ParamKind.ANGLE,
                            0,
                            Double.NaN,
                            Double.NaN,
                            java.util.Optional.of(Parse.expr("1"))))
                .add(i -> new Midpoint(i, S, id(5), id(9)))
                .add(i -> new Locus(i, S, id(10), id(9))));
    assertThat(s.geometryOf(id(8))).isEqualTo(Geometry.UNDEFINED); // P0 does not follow P
    assertThat(s.geometryOf(id(11))).isEqualTo(Geometry.UNDEFINED); // a driver at an expression
  }

  @Test
  void aLocusThroughAnIntersectionKeepsItsIdentityAlongTheSweep() {
    // P on the line y = 1 (through Q(1) and R(2)), the line OP through O(4), its intersection with
    // the circle about O of radius 1 at the index on P's side: the locus is the upper half circle
    CanvasState s =
        recomputed(
            CanvasState.EMPTY
                .add(i -> new FreePoint(i, S, 0, 1))
                .add(i -> new FreePoint(i, S, 1, 1))
                .add(i -> new Line(i, S, new Line.Through(id(1), id(2))))
                .add(i -> new FreePoint(i, S, 0, 0))
                .add(i -> new FreePoint(i, S, 1, 0))
                .add(i -> new Circle(i, S, new Circle.CenterPoint(id(4), id(5))))
                .add(i -> new PointOnPath(i, S, id(3), ParamKind.LINE, 0, Double.NaN, Double.NaN))
                .add(i -> new Ray(i, S, id(4), id(7)))
                .add(i -> Intersection.of(i, S, id(6), id(8), 0))
                .add(i -> new Locus(i, S, id(9), id(7))));
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(10));
    assertThat(c.branches()).isNotEmpty();
    for (Polyline b : c.branches()) {
      for (int k = 0; k < b.size(); k++) {
        assertThat(Math.hypot(b.x(k), b.y(k))).isCloseTo(1, within(1e-9));
        assertThat(b.y(k)).isPositive();
      }
    }
  }

  @Test
  void aDistanceToASegmentAtMixedScalesIsDefinedNotAnError() {
    // a segment 10⁻⁹ long and a point 10⁴ away, in windows up to 2·10⁸ wide
    for (double half : new double[] {10, 1e5, 1e8}) {
      Window w = new Window(-half, half, -half, half);
      CanvasState s =
          CanvasState.EMPTY
              .add(i -> new FreePoint(i, S, 0, 0))
              .add(i -> new FreePoint(i, S, 1e-9, 0))
              .add(i -> new Segment(i, S, id(1), id(2)))
              .add(i -> new FreePoint(i, S, 1e4, 0))
              .add(i -> new Distance(i, S, id(4), id(3)))
              .add(i -> new Ray(i, S, id(1), id(2)))
              .add(i -> new Slope(i, S, id(6)))
              .add(i -> new Distance(i, S, id(4), id(6)))
              .recomputeAll(SAMPLER, w, Recomputes.standard(), Cancellation.NEVER);
      assertThat(((Geometry.Measure) s.geometryOf(id(5))).value())
          .isCloseTo(1e4 - 1e-9, within(1e-9));
      assertThat(((Geometry.Measure) s.geometryOf(id(7))).value()).isZero();
      // on the ray, to its rounding
      assertThat(((Geometry.Measure) s.geometryOf(id(8))).value()).isCloseTo(0, within(1e-9));
    }
  }
}
