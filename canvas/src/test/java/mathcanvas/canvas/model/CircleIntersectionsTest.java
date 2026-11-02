package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.model.Families.Candidate;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/**
 * Circles, polygons and lines intersected in closed form, and the identity of their intersections
 * through tangency, kept by the matching rules every family follows.
 */
class CircleIntersectionsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new StubSampler(Map.of());
  private static final double EPS = 1e-12;
  private static final double SCALE = W.diagonal();

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static Path.Parametrised circle(double cx, double cy, double r) {
    return new Path.Parametrised(
        new Parametrisation.Circular(cx, cy, r, 0, 1), 0, 2 * Math.PI, ParamKind.ANGLE);
  }

  private static Path.Parametrised line(double px, double py, double dx, double dy) {
    return new Path.Parametrised(
        new Parametrisation.Linear(px, py, dx, dy),
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        ParamKind.LINE);
  }

  private static List<Candidate> of(Path.Parametrised a, Path.Parametrised b) {
    return Analytic.candidates(a, b, SCALE).orElseThrow();
  }

  @Test
  void aLineMeetsACircleTwiceTouchesItOnceOrMissesIt() {
    List<Candidate> two = of(line(-5, 0.6, 1, 0), circle(0, 0, 1));
    assertThat(two).hasSize(2);
    assertThat(two.get(0).x()).isCloseTo(-0.8, within(EPS));
    assertThat(two.get(1).x()).isCloseTo(0.8, within(EPS));
    assertThat(two.get(0).t1()).isCloseTo(4.2, within(EPS));
    assertThat(two.get(0).t2()).isCloseTo(Math.PI - Math.asin(0.6), within(EPS));
    assertThat(two).noneMatch(Candidate::tangent);
    List<Candidate> one = of(line(-5, 1, 1, 0), circle(0, 0, 1));
    assertThat(one).hasSize(1);
    assertThat(one.getFirst().tangent()).isTrue();
    assertThat(one.getFirst().x()).isCloseTo(0, within(EPS));
    assertThat(one.getFirst().spread()).isPositive();
    assertThat(of(line(-5, 1.01, 1, 0), circle(0, 0, 1))).isEmpty();
    // the circle first: ordered by its angle
    List<Candidate> circleFirst = of(circle(0, 0, 1), line(-5, 0.6, 1, 0));
    assertThat(circleFirst.get(0).x()).isCloseTo(0.8, within(EPS));
  }

  @Test
  void aSegmentOrARayMeetsACircleOnlyWithinItsRange() {
    Path.Parametrised segment =
        new Path.Parametrised(new Parametrisation.Linear(0, 0.6, 2, 0), 0, 1, ParamKind.LINE);
    assertThat(of(segment, circle(0, 0, 1))).hasSize(1);
    Path.Parametrised ray =
        new Path.Parametrised(
            new Parametrisation.Linear(0, 0, -1, 0), 0, Double.POSITIVE_INFINITY, ParamKind.LINE);
    List<Candidate> hit = of(ray, circle(0, 0, 1));
    assertThat(hit).hasSize(1);
    assertThat(hit.getFirst().x()).isCloseTo(-1, within(EPS));
  }

  @Test
  void twoCirclesMeetTwiceTouchOutsideOrInsideOrOverlap() {
    List<Candidate> two = of(circle(0, 0, 5), circle(6, 0, 5));
    assertThat(two).hasSize(2);
    assertThat(two).allSatisfy(c -> assertThat(c.x()).isCloseTo(3, within(EPS)));
    assertThat(two.get(0).y()).isCloseTo(4, within(EPS));
    List<Candidate> outside = of(circle(0, 0, 2), circle(5, 0, 3));
    assertThat(outside).singleElement().satisfies(c -> assertThat(c.tangent()).isTrue());
    assertThat(outside.getFirst().x()).isCloseTo(2, within(EPS));
    List<Candidate> inside = of(circle(0, 0, 1), circle(2, 0, 3));
    assertThat(inside).singleElement().satisfies(c -> assertThat(c.x()).isCloseTo(-1, within(EPS)));
    assertThat(of(circle(0, 0, 1), circle(0, 0, 2))).isEmpty();
    assertThat(of(circle(0, 0, 1), circle(5, 0, 1))).isEmpty();
    assertThat(Analytic.candidates(circle(1, 1, 2), circle(1, 1, 2), SCALE)).isEmpty();
  }

  @Test
  void aPolygonsVertexOnALineIsOneCandidateAndASideAlongItAnOverlap() {
    Path.Parametrised square =
        new Path.Parametrised(
            new Parametrisation.Polygonal(new double[] {0, 2, 2, 0}, new double[] {0, 0, 2, 2}),
            0,
            4,
            ParamKind.PERIMETER);
    // the diagonal y = x through the vertices (0, 0) and (2, 2)
    List<Candidate> diagonal = of(square, line(-1, -1, 1, 1));
    assertThat(diagonal).hasSize(2);
    assertThat(diagonal.get(0).t1()).isCloseTo(0, within(EPS));
    assertThat(diagonal.get(1).t1()).isCloseTo(2, within(EPS));
    assertThat(Analytic.candidates(square, line(-1, 0, 1, 0), SCALE)).isEmpty();
    assertThat(of(square, circle(1, 1, 1))).hasSize(4);
  }

  /** A circle of radius 1 at the origin (via C(1), P(2)) and the line through A(3) and B(4). */
  private static CanvasState circleAndLine(double y) {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 1, 0))
        .add(i -> new FreePoint(i, S, -3, y))
        .add(i -> new FreePoint(i, S, 3, y))
        .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
        .add(i -> new Line(i, S, new Line.Through(id(3), id(4))))
        .add(i -> Intersection.of(i, S, id(5), id(6), 0))
        .add(i -> Intersection.of(i, S, id(5), id(6), 1))
        .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState lineAt(CanvasState s, double y) {
    FreePoint a = (FreePoint) s.object(id(3)).orElseThrow();
    FreePoint b = (FreePoint) s.object(id(4)).orElseThrow();
    return s.replace(a.at(-3, y))
        .replace(b.at(3, y))
        .recompute(
            Set.of(id(3), id(4)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Optional<Geometry.Point> at(CanvasState s, long id) {
    return s.geometryOf(id(id)) instanceof Geometry.Point p ? Optional.of(p) : Optional.empty();
  }

  @Test
  void aLineThroughATangencyAndBackKeepsEachIntersectionOnItsSide() {
    CanvasState s = circleAndLine(0);
    // on the circle first, ordered by angle: index 0 at angle 0 (x = 1), index 1 at π (x = −1)
    double first = at(s, 7).orElseThrow().x();
    double second = at(s, 8).orElseThrow().x();
    assertThat(first).isCloseTo(1, within(EPS));
    assertThat(second).isCloseTo(-1, within(EPS));
    for (int pass = 0; pass < 2; pass++) {
      int[] steps = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
      for (int k : steps) {
        double y = (pass == 0 ? 1 : -1) * k / 10.0;
        s = lineAt(s, y);
        if (k > 10) {
          assertThat(at(s, 7)).isEmpty();
          assertThat(at(s, 8)).isEmpty();
        } else if (k == 10) {
          // both at the tangency
          assertThat(at(s, 7).orElseThrow().x()).isCloseTo(0, within(1e-6));
          assertThat(at(s, 8).orElseThrow().x()).isCloseTo(0, within(1e-6));
        } else {
          double x = Math.sqrt(1 - y * y);
          assertThat(at(s, 7).orElseThrow().x()).isCloseTo(x, within(1e-9));
          assertThat(at(s, 8).orElseThrow().x()).isCloseTo(-x, within(1e-9));
        }
      }
    }
  }

  /** Circles about A(1) through B(2) (radius 2) and about C(3) through D(4) (radius 1). */
  private static CanvasState twoCircles(double cx) {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 2, 0))
        .add(i -> new FreePoint(i, S, cx, 0))
        .add(i -> new FreePoint(i, S, cx + 1, 0))
        .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
        .add(i -> new Circle(i, S, new Circle.CenterPoint(id(3), id(4))))
        .add(i -> Intersection.of(i, S, id(5), id(6), 0))
        .add(i -> Intersection.of(i, S, id(5), id(6), 1))
        .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static CanvasState secondAt(CanvasState s, double cx) {
    FreePoint c = (FreePoint) s.object(id(3)).orElseThrow();
    FreePoint d = (FreePoint) s.object(id(4)).orElseThrow();
    return s.replace(c.at(cx, 0))
        .replace(d.at(cx + 1, 0))
        .recompute(
            Set.of(id(3), id(4)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  @Test
  void twoCirclesThroughBothTangenciesAndBackKeepTheirSides() {
    // the small circle's centre from 2 out through the outer tangency at 3, in through the inner
    // one at 1, and back: the member above the axis stays above whenever it is defined
    CanvasState s = twoCircles(2);
    long above = at(s, 7).orElseThrow().y() > 0 ? 7 : 8;
    long below = 15 - above;
    double[] centres = {2.5, 3, 3.5, 3, 2.5, 2, 1.5, 1, 0.5, 1, 1.5, 2};
    for (double cx : centres) {
      s = secondAt(s, cx);
      boolean apart = cx > 3 || cx < 1;
      boolean touching = cx == 3 || cx == 1;
      if (apart) {
        assertThat(at(s, 7)).isEmpty();
        assertThat(at(s, 8)).isEmpty();
      } else if (touching) {
        assertThat(at(s, 7).orElseThrow().y()).isCloseTo(0, within(1e-6));
        assertThat(at(s, 8).orElseThrow().y()).isCloseTo(0, within(1e-6));
      } else {
        assertThat(at(s, above).orElseThrow().y()).isPositive();
        assertThat(at(s, below).orElseThrow().y()).isNegative();
      }
    }
  }

  @Test
  void aTangencyAtTheCirclesSeamKeepsTheSides() {
    // the vertical line x = c crossing the unit circle at its seam (angle 0, the point (1, 0))
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 1, 0))
            .add(i -> new FreePoint(i, S, 0.5, -3))
            .add(i -> new FreePoint(i, S, 0.5, 3))
            .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
            .add(i -> new Line(i, S, new Line.Through(id(3), id(4))))
            .add(i -> Intersection.of(i, S, id(5), id(6), 0))
            .add(i -> Intersection.of(i, S, id(5), id(6), 1))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    long above = at(s, 7).orElseThrow().y() > 0 ? 7 : 8;
    long below = 15 - above;
    for (int k : new int[] {6, 7, 8, 9, 10, 11, 10, 9, 8, 7, 6, 5}) {
      double c = k / 10.0;
      FreePoint a = (FreePoint) s.object(id(3)).orElseThrow();
      FreePoint b = (FreePoint) s.object(id(4)).orElseThrow();
      s =
          s.replace(a.at(c, -3))
              .replace(b.at(c, 3))
              .recompute(
                  Set.of(id(3), id(4)),
                  Set.of(),
                  SAMPLER,
                  W,
                  Recomputes.standard(),
                  Cancellation.NEVER);
      if (k < 10) {
        assertThat(at(s, above).orElseThrow().y()).isPositive();
        assertThat(at(s, below).orElseThrow().y()).isNegative();
      } else if (k > 10) {
        assertThat(at(s, 7)).isEmpty();
      }
    }
  }

  @Test
  void aCircleWithinTheToleranceIsAPointNotAnError() {
    // radius 1e-9 in a window 20 wide and 1e-5 in one 2·10⁵ wide: at or below the root tolerance
    for (double[] c : new double[][] {{1e-9, 20}, {1e-5, 2e5}}) {
      double scale = Math.hypot(c[1], c[1]);
      List<Candidate> through =
          Analytic.candidates(line(-1, 0, 1, 0), circle(0, 0, c[0]), scale).orElseThrow();
      assertThat(through).singleElement().satisfies(k -> assertThat(k.tangent()).isTrue());
      assertThat(through.getFirst().x()).isCloseTo(0, within(1e-6));
      assertThat(Analytic.candidates(line(-1, 3, 1, 0), circle(0, 0, c[0]), scale).orElseThrow())
          .isEmpty();
      // and through the state, whose window sets the scale
      Window w = new Window(-c[1] / 2, c[1] / 2, -c[1] / 2, c[1] / 2);
      CanvasState s =
          CanvasState.EMPTY
              .add(i -> new FreePoint(i, S, 0, 0))
              .add(i -> new FreePoint(i, S, c[0], 0))
              .add(i -> new Circle(i, S, new Circle.CenterPoint(id(1), id(2))))
              .add(i -> new FreePoint(i, S, -1, 0))
              .add(i -> new FreePoint(i, S, 1, 0))
              .add(i -> new Line(i, S, new Line.Through(id(4), id(5))))
              .add(i -> Intersection.of(i, S, id(3), id(6), 0))
              .add(i -> new FreePoint(i, S, c[0], 0))
              .add(i -> new FreePoint(i, S, 2 * c[0], 0))
              .add(i -> new Circle(i, S, new Circle.CenterPoint(id(8), id(9))))
              .add(i -> Intersection.of(i, S, id(3), id(10), 0))
              .recomputeAll(SAMPLER, w, Recomputes.standard(), Cancellation.NEVER);
      assertThat(s.geometryOf(id(7))).isInstanceOf(Geometry.Point.class);
    }
  }
}
