package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/**
 * The constructions dragged through every degenerate configuration and back: undefined there, never
 * an exception, defined again on the far side and on the way back, with the same objects.
 */
class DegenerateConstructionsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final double EPS = 1e-12;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** A(1) at (0, 0), B(2) at (4, 0), C(3) at (0, 3), the object under test (4) built on them. */
  private static CanvasState figure(Function<ObjectId, CanvasObject> object, Sampler sampler) {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 4, 0))
        .add(i -> new FreePoint(i, S, 0, 3))
        .add(object::apply)
        .recomputeAll(sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState move(CanvasState s, long point, double x, double y, Sampler sampler) {
    FreePoint p = (FreePoint) s.object(id(point)).orElseThrow();
    return s.replace(p.at(x, y))
        .recompute(
            Set.of(id(point)), Set.of(), sampler, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /**
   * Drags a point in a straight line from one place through another and back, in steps, and returns
   * whether each frame left the object defined, there and back.
   */
  private static List<Boolean> sweep(
      Function<ObjectId, CanvasObject> object,
      Sampler sampler,
      long point,
      double x0,
      double y0,
      double x1,
      double y1,
      long watched) {
    CanvasState s = figure(object, sampler);
    List<CanvasObject> before = s.objects();
    List<Boolean> defined = new ArrayList<>();
    int n = 10;
    for (int pass = 0; pass < 2; pass++) {
      for (int k = 0; k <= n; k++) {
        double u = pass == 0 ? (double) k / n : 1 - (double) k / n;
        s = move(s, point, x0 + u * (x1 - x0), y0 + u * (y1 - y0), sampler);
        defined.add(!(s.geometryOf(id(watched)) instanceof Geometry.Undefined));
      }
    }
    assertThat(s.objects().stream().map(CanvasObject::id))
        .containsExactlyElementsOf(before.stream().map(CanvasObject::id).toList());
    return defined;
  }

  /** Undefined exactly at the middle frame of each pass (k = 5, then 16), defined elsewhere. */
  private static void undefinedAtTheMiddle(List<Boolean> defined) {
    for (int k = 0; k < defined.size(); k++) {
      assertThat(defined.get(k)).as("frame " + k).isEqualTo(k != 5 && k != 16);
    }
  }

  private static final Sampler PLAIN = new StubSampler(Map.of());

  @Test
  void coincidentPointsLeaveLinesRaysBisectorsAndCirclesUndefined() {
    // B dragged from (8, 0) through A at the origin to (−8, 0)
    List<Function<ObjectId, CanvasObject>> kinds =
        List.of(
            i -> new Line(i, S, new Line.Through(id(1), id(2))),
            i -> new Ray(i, S, id(1), id(2)),
            i -> new Line(i, S, new Line.PerpendicularBisector(id(1), id(2))),
            i -> new Line(i, S, new Line.AngleBisector(id(2), id(1), id(3))),
            i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))),
            i -> new Angle(i, S, id(2), id(1), id(3)),
            i -> new Polygon(i, S, new Polygon.Regular(id(1), id(2), Parse.expr("5"))));
    for (Function<ObjectId, CanvasObject> kind : kinds) {
      undefinedAtTheMiddle(sweep(kind, PLAIN, 2, 8, 0, -8, 0, 4));
    }
  }

  @Test
  void aMidpointOfCoincidentPointsIsDefined() {
    List<Boolean> defined = sweep(i -> new Midpoint(i, S, id(1), id(2)), PLAIN, 2, 8, 0, -8, 0, 4);
    assertThat(defined).doesNotContain(false);
  }

  @Test
  void threeCollinearPointsLeaveTheCircleUndefined() {
    // C dragged from (2, 5) across the line AB to (2, −5): collinear at (2, 0)
    Function<ObjectId, CanvasObject> circle =
        i -> new Circle(i, S, new Circle.ThreePoints(id(1), id(2), id(3)));
    undefinedAtTheMiddle(sweep(circle, PLAIN, 3, 2, 5, 2, -5, 4));
    // and onto A itself
    undefinedAtTheMiddle(sweep(circle, PLAIN, 3, 0, 5, 0, -5, 4));
  }

  @Test
  void aZeroOrNegativeRadiusLeavesTheCircleUndefined() {
    CanvasState s =
        figure(
            i -> new Circle(i, S, new Circle.CenterRadius(id(1), Parse.expr("r"))),
            new StubSampler(Map.of("r", 1.0)));
    for (double r : new double[] {1, 0.5, 0, -0.5, 0, 0.5, 1}) {
      s =
          s.recompute(
              Set.of(),
              Set.of("r"),
              new StubSampler(Map.of("r", r)),
              W,
              Recomputes.standard(),
              Cancellation.NEVER);
      assertThat(s.geometryOf(id(4)) instanceof Geometry.Undefined).isEqualTo(r <= 0);
    }
  }

  @Test
  void aTangentFromInsideTheCircleIsUndefinedAndOnItTheTwoAreOneLineContinuously() {
    // the circle about A through B (radius 4); P(5) dragged from (8, 0) to (0, 0) and back: each
    // tangent's direction moves continuously onto the circle, index 0 upward and index 1 downward,
    // on the circle the one line run both ways, a unit direction throughout
    CanvasState s =
        figure(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))), PLAIN)
            .add(i -> new FreePoint(i, S, 8, 0))
            .add(i -> new Line(i, S, new Line.CircleTangent(id(4), id(5), 0)))
            .add(i -> new Line(i, S, new Line.CircleTangent(id(4), id(5), 1)))
            .recomputeAll(PLAIN, W, Recomputes.standard(), Cancellation.NEVER);
    double[] xs = {8, 6, 4.5, 4.001, 4, 2, 0, 2, 4, 4.001, 6, 8};
    double[] previous = null;
    for (double x : xs) {
      s = move(s, 5, x, 0, PLAIN);
      Geometry g0 = s.geometryOf(id(6));
      Geometry g1 = s.geometryOf(id(7));
      if (x < 4) {
        assertThat(g0).isEqualTo(Geometry.UNDEFINED);
        assertThat(g1).isEqualTo(Geometry.UNDEFINED);
        previous = null;
        continue;
      }
      Parametrisation.Linear l0 = linear((Geometry.Curve) g0);
      Parametrisation.Linear l1 = linear((Geometry.Curve) g1);
      assertThat(Math.hypot(l0.dx(), l0.dy())).isCloseTo(1, within(EPS));
      assertThat(Math.hypot(l1.dx(), l1.dy())).isCloseTo(1, within(EPS));
      assertThat(l0.dy()).isPositive();
      assertThat(l1.dy()).isNegative();
      if (x == 4) {
        assertThat(l0.dx()).isCloseTo(0, within(EPS));
        assertThat(l1.dy()).isCloseTo(-l0.dy(), within(EPS));
      }
      if (previous != null && Math.abs(x - 4) < 0.01 && Math.abs(previous[4] - 4) < 0.01) {
        // a thousandth from the circle and on it: the directions barely move
        assertThat(Math.hypot(l0.dx() - previous[0], l0.dy() - previous[1])).isLessThan(0.1);
        assertThat(Math.hypot(l1.dx() - previous[2], l1.dy() - previous[3])).isLessThan(0.1);
      }
      previous = new double[] {l0.dx(), l0.dy(), l1.dx(), l1.dy(), x};
    }
  }

  private static Parametrisation.Linear linear(Geometry.Curve c) {
    return (Parametrisation.Linear) ((Path.Parametrised) c.path()).parametrisation();
  }

  @Test
  void aPolygonOfFewerThanThreeDistinctVerticesIsUndefined() {
    // C dragged from (0, 3) through B to (8, −3)
    Function<ObjectId, CanvasObject> polygon =
        i -> new Polygon(i, S, new Polygon.Vertices(List.of(id(1), id(2), id(3))));
    undefinedAtTheMiddle(sweep(polygon, PLAIN, 3, 0, 3, 8, -3, 4));
  }

  @Test
  void aRegularPolygonNeedsAWholeNumberOfSidesFromThreeToTheMost() {
    for (double n : new double[] {2, 2.5, 3, 6, 100, 101, Double.NaN}) {
      CanvasState s =
          figure(
              i -> new Polygon(i, S, new Polygon.Regular(id(1), id(2), Parse.expr("n"))),
              new StubSampler(Map.of("n", n)));
      assertThat(s.geometryOf(id(4)) instanceof Geometry.Undefined)
          .as("n = " + n)
          .isEqualTo(!(n == 3 || n == 6 || n == 100));
    }
  }

  @Test
  void aPerpendicularToASegmentOfLengthZeroIsUndefined() {
    // the segment AB, B dragged through A; the perpendicular and the parallel through C
    for (boolean perpendicular : new boolean[] {true, false}) {
      CanvasState s =
          figure(i -> new Segment(i, S, id(1), id(2)), PLAIN)
              .add(
                  i ->
                      new Line(
                          i,
                          S,
                          perpendicular
                              ? new Line.Perpendicular(id(3), id(4))
                              : new Line.Parallel(id(3), id(4))))
              .recomputeAll(PLAIN, W, Recomputes.standard(), Cancellation.NEVER);
      for (double x : new double[] {4, 2, 0, -2, 0, 2}) {
        s = move(s, 2, x, 0, PLAIN);
        assertThat(s.geometryOf(id(4))).isInstanceOf(Geometry.Curve.class);
        assertThat(s.geometryOf(id(5)) instanceof Geometry.Undefined).isEqualTo(x == 0);
      }
    }
  }

  @Test
  void parallelLinesHaveNoIntersectionUntilOneTurns() {
    // the line AB, the parallel to it through C and their intersection, which stays undefined as
    // B turns the line; the perpendicular through C meets the line at C's foot all along
    CanvasState s =
        figure(i -> new Line(i, S, new Line.Through(id(1), id(2))), PLAIN)
            .add(i -> new Line(i, S, new Line.Parallel(id(3), id(4))))
            .add(i -> Intersection.of(i, S, id(4), id(5), 0))
            .add(i -> new Line(i, S, new Line.Perpendicular(id(3), id(4))))
            .add(i -> Intersection.of(i, S, id(4), id(7), 0))
            .recomputeAll(PLAIN, W, Recomputes.standard(), Cancellation.NEVER);
    for (double y : new double[] {0, 2, -2, 0}) {
      s = move(s, 2, 4, y, PLAIN);
      assertThat(s.geometryOf(id(6))).isEqualTo(Geometry.UNDEFINED);
      Geometry.Point foot = (Geometry.Point) s.geometryOf(id(8));
      // the foot of the perpendicular from C(0, 3) on the line through the origin and (4, y)
      double t = (3 * y) / (16 + y * y);
      assertThat(foot.x()).isCloseTo(4 * t, within(1e-9));
      assertThat(foot.y()).isCloseTo(y * t, within(1e-9));
    }
  }

  @Test
  void aPointOnAnAngleBisectorMovesContinuouslyThroughAStraightAngle() {
    // the bisector at O between A(1, 0) and C, C dragged under the axis through (−1, 0); the point
    // at parameter 2 on it never jumps across the vertex
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 1, 0))
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, -1, 0.2))
            .add(i -> new Line(i, S, new Line.AngleBisector(id(1), id(2), id(3))))
            .add(i -> new PointOnPath(i, S, id(4), ParamKind.LINE, 2, Double.NaN, Double.NaN))
            .recomputeAll(PLAIN, W, Recomputes.standard(), Cancellation.NEVER);
    Geometry.Point before = (Geometry.Point) s.geometryOf(id(5));
    for (double y : new double[] {0.1, 0.01, 1e-9, 0, -1e-9, -0.01, -0.1, -0.2}) {
      s = move(s, 3, -1, y, PLAIN);
      Geometry.Point p = (Geometry.Point) s.geometryOf(id(5));
      assertThat(Math.hypot(p.x() - before.x(), p.y() - before.y())).isLessThan(0.25);
      before = p;
    }
    // through the straight angle it is the perpendicular at the vertex, two above it
    s = move(s, 3, -1, 0, PLAIN);
    assertThat(((Geometry.Point) s.geometryOf(id(5))).y()).isCloseTo(2, within(EPS));
  }

  @Test
  void nothingThrowsAtLargeZoomsOrMixedScales() {
    // tiny and huge distances together, in windows from 20 units to 2·10⁸ wide: every construction
    // is defined or undefined, never an exception
    for (double half : new double[] {10, 1e5, 1e8}) {
      Window w = new Window(-half, half, -half, half);
      CanvasState s =
          CanvasState.EMPTY
              .add(i -> new FreePoint(i, S, 0, 0))
              .add(i -> new FreePoint(i, S, 1e-9, 0))
              .add(i -> new FreePoint(i, S, 1e4, 5))
              .add(i -> new Circle(i, S, new Circle.ThreePoints(id(1), id(2), id(3))))
              .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
              .add(i -> new Line(i, S, new Line.AngleBisector(id(2), id(1), id(3))))
              .add(i -> new Angle(i, S, id(2), id(1), id(3)))
              .add(i -> new Polygon(i, S, new Polygon.Regular(id(1), id(2), Parse.expr("5"))))
              .add(i -> new Ray(i, S, id(1), id(2)))
              .add(i -> new Line(i, S, new Line.Perpendicular(id(3), id(9))))
              .add(i -> new Line(i, S, new Line.PerpendicularBisector(id(1), id(2))))
              .add(i -> new Line(i, S, new Line.CircleTangent(id(5), id(3), 0)))
              .add(i -> new Line(i, S, new Line.CircleTangent(id(5), id(3), 1)))
              .recomputeAll(PLAIN, w, Recomputes.standard(), Cancellation.NEVER);
      // three points far from collinear at their own scale: a circle
      assertThat(s.geometryOf(id(4))).isInstanceOf(Geometry.Curve.class);
      assertThat(s.geometryOf(id(5))).isInstanceOf(Geometry.Curve.class);
    }
  }
}
