package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/** The construction kinds of Phase 6: their records, their geometry, their sampling. */
class ConstructionsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new StubSampler(Map.of("r", 2.0, "n", 6.0));
  private static final double EPS = 1e-12;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /** Free points A(1) to D(4) at (0, 0), (4, 0), (0, 3), (5, 5). */
  private static CanvasState points() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 4, 0))
        .add(i -> new FreePoint(i, S, 0, 3))
        .add(i -> new FreePoint(i, S, 5, 5));
  }

  private static CanvasState with(CanvasObject... more) {
    CanvasState s = points();
    for (CanvasObject o : more) {
      s = s.add(i -> o);
    }
    return s.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Parametrisation.Linear linear(CanvasState s, long id) {
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(id));
    return (Parametrisation.Linear) ((Path.Parametrised) c.path()).parametrisation();
  }

  private static Parametrisation.Circular circular(CanvasState s, long id) {
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(id));
    return (Parametrisation.Circular) ((Path.Parametrised) c.path()).parametrisation();
  }

  /** Whether a point lies on a line, by the cross product with its direction. */
  private static double offLine(Parametrisation.Linear l, double x, double y) {
    return ((x - l.px()) * l.dy() - (y - l.py()) * l.dx()) / Math.hypot(l.dx(), l.dy());
  }

  @Test
  void theRecordsTakeTheirParentsAndRefuseRepeats() {
    assertThat(new Ray(id(5), S, id(1), id(2)).parents()).containsExactly(id(1), id(2));
    assertThat(new Midpoint(id(5), S, id(1), id(2)).isPoint()).isTrue();
    Circle radius = new Circle(id(5), S, new Circle.CenterRadius(id(1), Parse.expr("r + 1")));
    assertThat(radius.parents()).containsExactly(id(1));
    assertThat(radius.reads()).containsExactly("r");
    assertThat(radius.isPath()).isTrue();
    Polygon regular = new Polygon(id(5), S, new Polygon.Regular(id(1), id(2), Parse.expr("n")));
    assertThat(regular.reads()).containsExactly("n");
    assertThat(new Angle(id(5), S, id(1), id(2), id(3)).parents())
        .containsExactly(id(1), id(2), id(3));
    assertThat(new Line(id(5), S, new Line.CircleTangent(id(4), id(1), 1)).parents())
        .containsExactly(id(4), id(1));
    assertThatThrownBy(() -> new Ray(id(5), S, id(1), id(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Circle.ThreePoints(id(1), id(2), id(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Polygon.Vertices(List.of(id(1), id(2))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Polygon.Vertices(List.of(id(1), id(2), id(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Line.CircleTangent(id(4), id(1), 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Angle(id(5), S, id(1), id(1), id(3)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theStateChecksEachParentsRole() {
    CanvasState s = points();
    // a perpendicular to a point, a tangent to a line
    assertThatThrownBy(() -> s.add(i -> new Line(i, S, new Line.Perpendicular(id(1), id(2)))))
        .hasMessageContaining("needs a line, segment or ray");
    CanvasState line = s.add(i -> new Line(i, S, new Line.Through(id(1), id(2))));
    assertThatThrownBy(() -> line.add(i -> new Line(i, S, new Line.CircleTangent(id(5), id(4), 0))))
        .hasMessageContaining("needs a circle");
    assertThatThrownBy(() -> line.add(i -> new Midpoint(i, S, id(5), id(1))))
        .hasMessageContaining("needs a point");
  }

  @Test
  void aRayStartsAtItsPointAndIsDrawnToTheWindow() {
    CanvasState s = with(new Ray(id(5), S, id(1), id(2)));
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(5));
    Path.Parametrised p = (Path.Parametrised) c.path();
    assertThat(p.lo()).isZero();
    assertThat(p.hi()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(p.kind()).isEqualTo(ParamKind.LINE);
    Polyline b = c.branches().getFirst();
    assertThat(b.x(0)).isZero();
    assertThat(b.x(1)).isEqualTo(20); // the window widened by half
  }

  @Test
  void theThreeCirclesHaveTheirCentresAndRadii() {
    CanvasState s =
        with(
            new Circle(id(5), S, new Circle.CenterPoint(id(1), id(2))),
            new Circle(id(6), S, new Circle.CenterRadius(id(3), Parse.expr("r + 1"))),
            new Circle(id(7), S, new Circle.ThreePoints(id(1), id(2), id(3))));
    Parametrisation.Circular a = circular(s, 5);
    assertThat(a.r()).isEqualTo(4);
    Parametrisation.Circular b = circular(s, 6);
    assertThat(b.cy()).isEqualTo(3);
    assertThat(b.r()).isEqualTo(3);
    // the right triangle's circumcircle: about the hypotenuse's midpoint (2, 1.5), radius 2.5
    Parametrisation.Circular c = circular(s, 7);
    assertThat(c.cx()).isCloseTo(2, within(EPS));
    assertThat(c.cy()).isCloseTo(1.5, within(EPS));
    assertThat(c.r()).isCloseTo(2.5, within(EPS));
    Path.Parametrised p = (Path.Parametrised) ((Geometry.Curve) s.geometryOf(id(7))).path();
    assertThat(p.kind()).isEqualTo(ParamKind.ANGLE);
    assertThat(p.hi()).isEqualTo(2 * Math.PI);
  }

  @Test
  void aCircleInsideTheWindowIsSampledWholeAndClosed() {
    CanvasState s = with(new Circle(id(5), S, new Circle.CenterPoint(id(1), id(2))));
    List<Polyline> bs = ((Geometry.Curve) s.geometryOf(id(5))).branches();
    assertThat(bs).hasSize(1);
    Polyline b = bs.getFirst();
    assertThat(b.size()).isGreaterThanOrEqualTo(Shapes.MIN_SAMPLES);
    assertThat(b.x(0)).isCloseTo(b.x(b.size() - 1), within(EPS));
    assertThat(b.y(0)).isCloseTo(b.y(b.size() - 1), within(EPS));
    for (int k = 0; k < b.size(); k++) {
      assertThat(Math.hypot(b.x(k), b.y(k))).isCloseTo(4, within(EPS));
    }
  }

  @Test
  void aCircleFarLargerThanTheWindowIsSampledOnlyWhereItMeetsIt() {
    // centre (0, -1e6), radius 1e6 + 3: crosses the window near y = 3
    Parametrisation.Circular c = new Parametrisation.Circular(0, -1e6, 1e6 + 3, 0, 1);
    List<Polyline> bs = Shapes.circle(c, W, W.widened(0.5));
    assertThat(bs).hasSize(1);
    Polyline b = bs.getFirst();
    assertThat(b.size()).isLessThanOrEqualTo(Shapes.MAX_SAMPLES + 1);
    for (int k = 0; k < b.size(); k++) {
      assertThat(b.x(k)).isBetween(-20.0 - 1e-6, 20.0 + 1e-6);
    }
    // the chord between neighbouring samples stays within the sagitta of the window's diagonal
    for (int k = 0; k + 1 < b.size(); k++) {
      Sample mid = c.at((b.t(k) + b.t(k + 1)) / 2);
      double cx = (b.x(k) + b.x(k + 1)) / 2;
      double cy = (b.y(k) + b.y(k + 1)) / 2;
      assertThat(Math.hypot(mid.x() - cx, mid.y() - cy))
          .isLessThanOrEqualTo(Shapes.SAGITTA * W.diagonal() * 1.01);
    }
  }

  @Test
  void theMidpointIsHalfway() {
    CanvasState s = with(new Midpoint(id(5), S, id(2), id(4)));
    assertThat(s.geometryOf(id(5))).isEqualTo(new Geometry.Point(4.5, 2.5));
  }

  @Test
  void perpendicularsParallelsAndBisectors() {
    CanvasState s =
        with(
            new Line(id(5), S, new Line.Through(id(1), id(4))),
            new Line(id(6), S, new Line.Perpendicular(id(2), id(5))),
            new Line(id(7), S, new Line.Parallel(id(3), id(5))),
            new Line(id(8), S, new Line.PerpendicularBisector(id(1), id(2))),
            new Line(id(9), S, new Line.AngleBisector(id(2), id(1), id(3))));
    Parametrisation.Linear diagonal = linear(s, 5);
    Parametrisation.Linear perpendicular = linear(s, 6);
    assertThat(perpendicular.px()).isEqualTo(4);
    assertThat(perpendicular.dx() * diagonal.dx() + perpendicular.dy() * diagonal.dy())
        .isCloseTo(0, within(EPS));
    Parametrisation.Linear parallel = linear(s, 7);
    assertThat(offLine(parallel, 0, 3)).isCloseTo(0, within(EPS));
    assertThat(parallel.dx() * diagonal.dy() - parallel.dy() * diagonal.dx())
        .isCloseTo(0, within(EPS));
    // x = 2
    Parametrisation.Linear bisector = linear(s, 8);
    assertThat(offLine(bisector, 2, 0)).isCloseTo(0, within(EPS));
    assertThat(offLine(bisector, 2, 7)).isCloseTo(0, within(EPS));
    // the right angle at A halved: y = x
    Parametrisation.Linear angle = linear(s, 9);
    assertThat(offLine(angle, 3, 3)).isCloseTo(0, within(EPS));
  }

  @Test
  void theBisectorOfAStraightAngleIsThePerpendicular() {
    CanvasState s =
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, S, -1, 0))
            .add(i -> new FreePoint(i, S, 0, 0))
            .add(i -> new FreePoint(i, S, 2, 0))
            .add(i -> new Line(i, S, new Line.AngleBisector(id(1), id(2), id(3))))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    Parametrisation.Linear l = linear(s, 4);
    assertThat(offLine(l, 0, 5)).isCloseTo(0, within(EPS));
  }

  @Test
  void theTwoTangentsFromAPointTouchTheCircleOnTheirSides() {
    // the circle about A through B, radius 4; D at (5, 5) outside it
    CanvasState s =
        with(
            new Circle(id(5), S, new Circle.CenterPoint(id(1), id(2))),
            new Line(id(6), S, new Line.CircleTangent(id(5), id(4), 0)),
            new Line(id(7), S, new Line.CircleTangent(id(5), id(4), 1)));
    double d = Math.hypot(5, 5);
    double phi = Math.PI / 4;
    double alpha = Math.acos(4 / d);
    for (int index = 0; index < 2; index++) {
      Parametrisation.Linear l = linear(s, 6 + index);
      double at = index == 0 ? phi + alpha : phi - alpha;
      // from D toward the point at the angle on its side, a unit direction perpendicular to the
      // radius there, the point of contact √(d² − r²) along
      assertThat(l.px()).isEqualTo(5);
      assertThat(Math.hypot(l.dx(), l.dy())).isCloseTo(1, within(EPS));
      double reach = Math.sqrt(d * d - 16);
      double tx = l.px() + reach * l.dx();
      double ty = l.py() + reach * l.dy();
      assertThat(tx).isCloseTo(4 * Math.cos(at), within(1e-11));
      assertThat(ty).isCloseTo(4 * Math.sin(at), within(1e-11));
      assertThat(tx * l.dx() + ty * l.dy()).isCloseTo(0, within(1e-11));
    }
  }

  @Test
  void aPolygonIsItsClosedBoundaryRoundThePerimeter() {
    CanvasState s =
        with(new Polygon(id(5), S, new Polygon.Vertices(List.of(id(1), id(2), id(4), id(3)))));
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(5));
    Path.Parametrised p = (Path.Parametrised) c.path();
    assertThat(p.kind()).isEqualTo(ParamKind.PERIMETER);
    assertThat(p.hi()).isEqualTo(4);
    assertThat(p.at(2.5, SAMPLER)).isEqualTo(new Sample(2.5, 4));
    Polyline b = c.branches().getFirst();
    assertThat(b.size()).isEqualTo(5);
    assertThat(b.x(4)).isEqualTo(b.x(0));
  }

  @Test
  void aRegularHexagonLiesLeftOfItsSide() {
    CanvasState s = with(new Polygon(id(5), S, new Polygon.Regular(id(1), id(2), Parse.expr("n"))));
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(5));
    Parametrisation.Polygonal g =
        (Parametrisation.Polygonal) ((Path.Parametrised) c.path()).parametrisation();
    assertThat(g.size()).isEqualTo(6);
    double cx = 2;
    double cy = 2 * Math.sqrt(3);
    for (int k = 0; k < 6; k++) {
      assertThat(Math.hypot(g.x(k) - cx, g.y(k) - cy)).isCloseTo(4, within(1e-12));
      assertThat(Math.hypot(g.x(k + 1) - g.x(k), g.y(k + 1) - g.y(k))).isCloseTo(4, within(1e-12));
    }
    assertThat(g.y(3)).isCloseTo(4 * Math.sqrt(3), within(1e-12));
  }

  @Test
  void anAngleIsNeverReflexWhateverTheOrder() {
    CanvasState s =
        with(
            new Angle(id(5), S, id(2), id(1), id(3)),
            new Angle(id(6), S, id(3), id(1), id(2)),
            new Angle(id(7), S, id(4), id(1), id(2)));
    Geometry.Angle a = (Geometry.Angle) s.geometryOf(id(5));
    Geometry.Angle b = (Geometry.Angle) s.geometryOf(id(6));
    assertThat(a.sweep()).isCloseTo(Math.PI / 2, within(EPS));
    assertThat(b).isEqualTo(a);
    assertThat(a.start()).isZero(); // from the arm to B, counterclockwise to the arm to C
    Geometry.Angle c = (Geometry.Angle) s.geometryOf(id(7));
    assertThat(c.sweep()).isCloseTo(Math.PI / 4, within(EPS));
  }

  @Test
  void aPointOnACircleKeepsItsAngleAsTheCentreMoves() {
    CanvasState s =
        with(new Circle(id(5), S, new Circle.CenterPoint(id(1), id(2))))
            .add(i -> new PointOnPath(i, S, id(5), ParamKind.ANGLE, Math.PI / 2, 0, 4))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(((Geometry.Point) s.geometryOf(id(6))).y()).isCloseTo(4, within(EPS));
    FreePoint a = (FreePoint) s.object(id(1)).orElseThrow();
    s =
        s.replace(a.at(1, 1))
            .recompute(
                Set.of(id(1)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    // the radius is now |B − A| = √10, the point straight above the centre
    Geometry.Point p = (Geometry.Point) s.geometryOf(id(6));
    assertThat(p.x()).isCloseTo(1, within(EPS));
    assertThat(p.y()).isCloseTo(1 + Math.sqrt(10), within(EPS));
  }

  @Test
  void aProjectionOntoACircleIsExact() {
    CanvasState s = with(new Circle(id(5), S, new Circle.CenterPoint(id(1), id(2))));
    Geometry.Curve c = (Geometry.Curve) s.geometryOf(id(5));
    Paths.Projection p = Paths.project(c, 0, 10, SAMPLER, Cancellation.NEVER).orElseThrow();
    assertThat(p.t()).isCloseTo(Math.PI / 2, within(EPS));
    assertThat(p.y()).isCloseTo(4, within(EPS));
  }
}
