package mathcanvas.canvas.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.model.Transform.Shape;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/** Reflections, rotations, translations and dilations of every transformable shape. */
class TransformsTest {

  private static final Style S = Style.DEFAULT;
  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER =
      new StubSampler(Map.of("q", Math.PI / 2, "k", 2.0, "h", -1.0, "z", 0.0));
  private static final double EPS = 1e-12;

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  /**
   * O(1) at the origin, A(2) at (3, 1), B(3) at (1, 0); the line y = x through O and D(4) at (1,
   * 1); the circle about A through B (5); the segment AB (6); the triangle O A B (7); the angle at
   * O between B and A (8); the arrow O to A (9); the graph y = x² (10).
   */
  private static CanvasState figure() {
    return CanvasState.EMPTY
        .add(i -> new FreePoint(i, S, 0, 0))
        .add(i -> new FreePoint(i, S, 3, 1))
        .add(i -> new FreePoint(i, S, 1, 0))
        .add(i -> new FreePoint(i, S, 1, 1))
        .add(i -> new Circle(i, S, new Circle.CenterPoint(id(2), id(3))))
        .add(i -> new Segment(i, S, id(2), id(3)))
        .add(i -> new Polygon(i, S, new Polygon.Vertices(List.of(id(1), id(2), id(3)))))
        .add(i -> new Angle(i, S, id(3), id(1), id(2)))
        .add(i -> new Arrow(i, S, Optional.of(id(1)), new Arrow.At(Optional.of(id(2)))))
        .add(
            i ->
                new FunctionGraph(
                    i,
                    S,
                    Parse.expr("x^2"),
                    mathcanvas.engine.expr.Symbol.of("x"),
                    FunctionGraph.ALL))
        .add(i -> new Line(i, S, new Line.Through(id(1), id(4))));
  }

  private static CanvasState with(CanvasObject... more) {
    CanvasState s = figure();
    for (CanvasObject o : more) {
      s = s.add(i -> o);
    }
    return s.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  private static Path.Parametrised path(CanvasState s, long id) {
    return (Path.Parametrised) ((Geometry.Curve) s.geometryOf(id(id))).path();
  }

  @Test
  void theRecordsTakeTheirSourceAndRefuseAMismatchedShape() {
    Rotation r = new Rotation(id(20), S, id(2), Shape.POINT, id(1), Parse.expr("q"));
    assertThat(r.parents()).containsExactly(id(2), id(1));
    assertThat(r.reads()).containsExactly("q");
    assertThat(r.isPoint()).isTrue();
    assertThat(new Reflection(id(20), S, id(5), Shape.CIRCLE, id(11)).isCircle()).isTrue();
    assertThat(
            new Translation(id(20), S, id(6), Shape.LINEAR, new Translation.Points(id(1), id(4)))
                .parents())
        .containsExactly(id(6), id(1), id(4));
    assertThat(
            Transform.shapeOf(
                new Slider(
                    id(1),
                    S,
                    "a",
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ZERO)))
        .isEmpty();
    assertThatThrownBy(() -> new Reflection(id(20), S, id(11), Shape.LINEAR, id(11)))
        .isInstanceOf(IllegalArgumentException.class);
    CanvasState s = figure();
    assertThatThrownBy(() -> s.add(i -> new Reflection(i, S, id(5), Shape.POINT, id(11))))
        .hasMessageContaining("is the image of a point");
    assertThatThrownBy(() -> s.add(i -> new Reflection(i, S, id(2), Shape.POINT, id(5))))
        .hasMessageContaining("needs a line, segment, ray or point");
  }

  @Test
  void aPointIsReflectedRotatedTranslatedAndDilated() {
    CanvasState s =
        with(
            new Reflection(id(12), S, id(2), Shape.POINT, id(11)),
            new Reflection(id(13), S, id(2), Shape.POINT, id(3)),
            new Rotation(id(14), S, id(2), Shape.POINT, id(1), Parse.expr("q")),
            new Translation(id(15), S, id(2), Shape.POINT, new Translation.Vector(id(9))),
            new Dilation(id(16), S, id(2), Shape.POINT, id(3), Parse.expr("k")),
            new Dilation(id(17), S, id(2), Shape.POINT, id(3), Parse.expr("z")));
    assertPoint(s, 12, 1, 3);
    assertPoint(s, 13, -1, -1);
    assertPoint(s, 14, -1, 3);
    assertPoint(s, 15, 6, 2);
    assertPoint(s, 16, 5, 2);
    assertPoint(s, 17, 1, 0);
  }

  private static void assertPoint(CanvasState s, long id, double x, double y) {
    Geometry.Point p = (Geometry.Point) s.geometryOf(id(id));
    assertThat(p.x()).isCloseTo(x, within(EPS));
    assertThat(p.y()).isCloseTo(y, within(EPS));
  }

  @Test
  void aCircleKeepsItsRadiusAndAReflectionReversesIt() {
    CanvasState s =
        with(
            new Reflection(id(12), S, id(5), Shape.CIRCLE, id(11)),
            new Dilation(id(13), S, id(5), Shape.CIRCLE, id(1), Parse.expr("h")),
            new Dilation(id(14), S, id(5), Shape.CIRCLE, id(1), Parse.expr("z")));
    Parametrisation.Circular source = (Parametrisation.Circular) path(s, 5).parametrisation();
    Parametrisation.Circular mirrored = (Parametrisation.Circular) path(s, 12).parametrisation();
    assertThat(mirrored.cx()).isCloseTo(1, within(EPS));
    assertThat(mirrored.cy()).isCloseTo(3, within(EPS));
    assertThat(mirrored.r()).isCloseTo(source.r(), within(EPS));
    assertThat(mirrored.dir()).isEqualTo(-1);
    // a point at a parameter on the image is the image of the point at it on the source
    for (double t : new double[] {0, 1, 2.5, 4}) {
      Sample a = source.at(t);
      Sample b = mirrored.at(t);
      assertThat(b.x()).isCloseTo(a.y(), within(EPS));
      assertThat(b.y()).isCloseTo(a.x(), within(EPS));
    }
    Parametrisation.Circular turned = (Parametrisation.Circular) path(s, 13).parametrisation();
    assertThat(turned.cx()).isCloseTo(-3, within(EPS));
    assertThat(turned.dir()).isEqualTo(1);
    assertThat(s.geometryOf(id(14))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void linesSegmentsPolygonsArrowsAndAnglesMapByTheirPoints() {
    CanvasState s =
        with(
            new Rotation(id(12), S, id(11), Shape.LINEAR, id(1), Parse.expr("q")),
            new Translation(id(13), S, id(6), Shape.LINEAR, new Translation.Points(id(1), id(4))),
            new Dilation(id(14), S, id(7), Shape.POLYGON, id(1), Parse.expr("k")),
            new Reflection(id(15), S, id(9), Shape.VECTOR, id(11)),
            new Reflection(id(16), S, id(8), Shape.ANGLE, id(11)));
    // y = x turned a quarter: y = −x, clipped to the window again
    Parametrisation.Linear l = (Parametrisation.Linear) path(s, 12).parametrisation();
    assertThat(l.dx() + l.dy()).isCloseTo(0, within(EPS));
    assertThat(((Geometry.Curve) s.geometryOf(id(12))).branches()).hasSize(1);
    Path.Parametrised segment = path(s, 13);
    assertThat(segment.lo()).isZero();
    assertThat(segment.at(0, SAMPLER).x()).isCloseTo(4, within(EPS));
    assertThat(segment.at(0, SAMPLER).y()).isCloseTo(2, within(EPS));
    Parametrisation.Polygonal big = (Parametrisation.Polygonal) path(s, 14).parametrisation();
    Parametrisation.Polygonal small = (Parametrisation.Polygonal) path(s, 7).parametrisation();
    assertThat(Constructions.area(big)).isCloseTo(4 * Constructions.area(small), within(EPS));
    Geometry.Arrow a = (Geometry.Arrow) s.geometryOf(id(15));
    assertThat(a.head().x()).isCloseTo(1, within(EPS));
    assertThat(a.head().y()).isCloseTo(3, within(EPS));
    Geometry.Angle source = (Geometry.Angle) s.geometryOf(id(8));
    Geometry.Angle image = (Geometry.Angle) s.geometryOf(id(16));
    assertThat(image.sweep()).isCloseTo(source.sweep(), within(EPS));
    // the arms mirrored: from (0, 1) to (1, 3)'s direction, counterclockwise
    assertThat(image.start()).isCloseTo(Math.atan2(3, 1), within(EPS));
  }

  @Test
  void aGraphsImageIsItsPointsMapped() {
    CanvasState s = with(new Reflection(id(12), S, id(10), Shape.CURVE, id(11)));
    Path.Parametrised p = path(s, 12);
    assertThat(p.parametrisation()).isInstanceOf(Parametrisation.Transformed.class);
    assertThat(p.kind()).isEqualTo(ParamKind.X);
    assertThat(p.at(2, SAMPLER).x()).isCloseTo(4, within(EPS));
    assertThat(p.at(2, SAMPLER).y()).isCloseTo(2, within(EPS));
    Polyline b = ((Geometry.Curve) s.geometryOf(id(12))).branches().getFirst();
    assertThat(b.x(0)).isCloseTo(b.t(0) * b.t(0), within(1e-9));
    // and an image of the image composes the maps
    CanvasState t =
        s.add(i -> new Translation(i, S, id(12), Shape.CURVE, new Translation.Points(id(1), id(3))))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(path(t, 13).at(2, SAMPLER).x()).isCloseTo(5, within(EPS));
    assertThat(path(t, 13).at(2, SAMPLER).y()).isCloseTo(2, within(EPS));
  }

  @Test
  void anImageFollowsItsSourceAndItsMapAndIsUndefinedWhileTheMapIs() {
    CanvasState s = with(new Rotation(id(12), S, id(2), Shape.POINT, id(1), Parse.expr("q")));
    FreePoint a = (FreePoint) s.object(id(2)).orElseThrow();
    s =
        s.replace(a.at(0, 2))
            .recompute(
                Set.of(id(2)), Set.of(), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertPoint(s, 12, -2, 0);
    StubSampler none = new StubSampler(Map.of());
    s = s.recompute(Set.of(), Set.of("q"), none, W, Recomputes.standard(), Cancellation.NEVER);
    assertThat(s.geometryOf(id(12))).isEqualTo(Geometry.UNDEFINED);
    s = s.recompute(Set.of(), Set.of("q"), SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    assertPoint(s, 12, -2, 0);
  }

  @Test
  void anImageCircleIsIntersectedInClosedForm() {
    CanvasState s =
        with(new Reflection(id(12), S, id(5), Shape.CIRCLE, id(11)))
            .add(i -> Intersection.of(i, S, id(11), id(12), 0))
            .add(i -> Intersection.of(i, S, id(11), id(12), 1))
            .recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
    // the mirrored circle about (1, 3) of radius √5 meets y = x where (x − 1)² + (x − 3)² = 5,
    // x = 2 ± √(3/2)
    double h = Math.sqrt(1.5);
    Geometry.Point p = (Geometry.Point) s.geometryOf(id(13));
    Geometry.Point q = (Geometry.Point) s.geometryOf(id(14));
    assertThat(Math.min(p.x(), q.x())).isCloseTo(2 - h, within(1e-12));
    assertThat(Math.max(p.x(), q.x())).isCloseTo(2 + h, within(1e-12));
  }
}
