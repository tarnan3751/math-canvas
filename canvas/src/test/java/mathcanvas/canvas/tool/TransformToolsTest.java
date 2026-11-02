package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.Area;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.Distance;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.Locus;
import mathcanvas.canvas.model.Midpoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Reflection;
import mathcanvas.canvas.model.Rotation;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slope;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Transform;
import mathcanvas.canvas.model.Translation;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

/** The transformation, measurement and locus tools over scripted clicks. */
class TransformToolsTest {

  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState recomputed(CanvasState c) {
    return c.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /**
   * A(1) at (0, 0), B(2) at (4, 0), C(3) at (0, 3); the triangle ABC (4); the line x = −2 through
   * D(5) and E(6) (7); the circle about A through B (8); P(9) on it at angle π/2; M(10) the
   * midpoint of P and C.
   */
  private static CanvasState scene() {
    return recomputed(
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, Style.DEFAULT, 0, 0))
            .add(i -> new FreePoint(i, Style.DEFAULT, 4, 0))
            .add(i -> new FreePoint(i, Style.DEFAULT, 0, 3))
            .add(
                i ->
                    new Polygon(
                        i, Style.DEFAULT, new Polygon.Vertices(List.of(id(1), id(2), id(3)))))
            .add(i -> new FreePoint(i, Style.DEFAULT, -2, -1))
            .add(i -> new FreePoint(i, Style.DEFAULT, -2, 1))
            .add(i -> new Line(i, Style.DEFAULT, new Line.Through(id(5), id(6))))
            .add(i -> new Circle(i, Style.DEFAULT, new Circle.CenterPoint(id(1), id(2))))
            .add(
                i ->
                    new PointOnPath(
                        i,
                        Style.DEFAULT,
                        id(8),
                        ParamKind.ANGLE,
                        Math.PI / 2,
                        Double.NaN,
                        Double.NaN))
            .add(i -> new Midpoint(i, Style.DEFAULT, id(9), id(3))));
  }

  private static Scene scene(CanvasState c) {
    return new Scene(c, W, Scene.Slopes.NONE);
  }

  private static Pointer point(CanvasState c, long id) {
    Geometry.Point p = (Geometry.Point) c.geometryOf(id(id));
    Hit hit = new Hit(0, Hit.Kind.POINT, -1, 0, p.x(), p.y(), 1);
    return new Pointer(p.x(), p.y(), Optional.of(new Pointer.Picked(id(id), hit)));
  }

  private static Pointer curve(long id, double x, double y) {
    Hit hit = new Hit(0, Hit.Kind.POLYLINE, 0, 0, x, y, 1);
    return new Pointer(x, y, Optional.of(new Pointer.Picked(id(id), hit)));
  }

  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(i -> o);
    }
    return recomputed(out);
  }

  private static List<Edit> clicks(Tool t, CanvasState c, Pointer... ps) {
    List<Edit> edits = new ArrayList<>();
    for (Pointer p : ps) {
      t.press(scene(c), p, edits::add);
    }
    return edits;
  }

  @Test
  void aReflectionTakesTheSourcesShapeAndNamesAnImagePoint() {
    CanvasState c = scene();
    Edit.Add polygon =
        (Edit.Add)
            clicks(ConstructionTool.reflect(), c, curve(4, 2, 0), curve(7, -2, 0)).getFirst();
    assertThat(polygon.objects()).extracting(Edit.New::named).containsExactly(false);
    CanvasState after = apply(c, polygon);
    assertThat(after.object(id(11)))
        .contains(new Reflection(id(11), Style.DEFAULT, id(4), Transform.Shape.POLYGON, id(7)));
    Edit.Add point =
        (Edit.Add) clicks(ConstructionTool.reflect(), c, point(c, 3), point(c, 2)).getFirst();
    assertThat(point.objects()).extracting(Edit.New::named).containsExactly(true);
    assertThat(apply(c, point).geometryOf(id(11))).isEqualTo(new Geometry.Point(8, -3));
    // a mirror that is no mirror (a circle) passes the click on
    ConstructionTool tool = ConstructionTool.reflect();
    clicks(tool, c, point(c, 3));
    assertThat(tool.press(scene(c), curve(8, 4, 0), x -> {})).isFalse();
  }

  @Test
  void aRotationAndADilationWaitForTheirNumber() {
    CanvasState c = scene();
    ConstructionTool rotate = ConstructionTool.rotate();
    assertThat(clicks(rotate, c, point(c, 2), point(c, 1))).isEmpty();
    assertThat(rotate.wanted()).contains("θ");
    List<Edit> edits = new ArrayList<>();
    rotate.typed(mathcanvas.engine.expr.Constant.PI, Math.PI, edits::add);
    CanvasState after = apply(c, edits.getFirst());
    assertThat(after.object(id(11))).containsInstanceOf(Rotation.class);
    Geometry.Point p = (Geometry.Point) after.geometryOf(id(11));
    assertThat(p.x()).isCloseTo(-4, within(1e-12));
    ConstructionTool dilate = ConstructionTool.dilate();
    clicks(dilate, c, curve(8, 4, 0), point(c, 3));
    assertThat(dilate.wanted()).contains("k");
    edits.clear();
    dilate.typed(Num.of(0), 0, edits::add);
    // a circle dilated by 0 is undefined, not an exception
    assertThat(apply(c, edits.getFirst()).geometryOf(id(11))).isEqualTo(Geometry.UNDEFINED);
  }

  @Test
  void aTranslationByTwoPointsMakesTheNewPointsItNeeds() {
    CanvasState c = scene();
    Edit.Add add =
        (Edit.Add)
            clicks(
                    ConstructionTool.translateByPoints(),
                    c,
                    curve(4, 2, 0),
                    point(c, 1),
                    Pointer.at(1, 1))
                .getFirst();
    CanvasState after = apply(c, add);
    assertThat(after.object(id(12)).orElseThrow()).isInstanceOf(Translation.class);
    assertThat(((Translation) after.object(id(12)).orElseThrow()).by())
        .isEqualTo(new Translation.Points(id(1), id(11)));
  }

  @Test
  void theMeasurementTools() {
    CanvasState c = scene();
    CanvasState d =
        apply(c, clicks(ConstructionTool.distance(), c, point(c, 3), curve(7, -2, 0)).getFirst());
    assertThat(d.object(id(11))).contains(new Distance(id(11), Style.DEFAULT, id(3), id(7)));
    assertThat(((Geometry.Measure) d.geometryOf(id(11))).value()).isCloseTo(2, within(1e-12));
    CanvasState a = apply(c, clicks(ConstructionTool.area(), c, curve(4, 2, 0)).getFirst());
    assertThat(a.object(id(11))).contains(new Area(id(11), Style.DEFAULT, id(4)));
    assertThat(((Geometry.Measure) a.geometryOf(id(11))).value()).isCloseTo(6, within(1e-12));
    assertThat(clicks(ConstructionTool.area(), c, curve(7, -2, 0))).isEmpty();
    CanvasState s = apply(c, clicks(ConstructionTool.slope(), c, curve(7, -2, 0)).getFirst());
    assertThat(s.object(id(11))).contains(new Slope(id(11), Style.DEFAULT, id(7)));
    assertThat(s.geometryOf(id(11))).isEqualTo(Geometry.UNDEFINED); // vertical
  }

  @Test
  void theLocusToolNeedsADriverThePointFollows() {
    CanvasState c = scene();
    ConstructionTool tool = ConstructionTool.locus();
    clicks(tool, c, point(c, 10));
    // B is no point on a path; the circle's P is, and M follows it
    assertThat(tool.press(scene(c), point(c, 2), x -> {})).isFalse();
    tool.move(scene(c), point(c, 9));
    assertThat(tool.refusal()).isEmpty();
    // the preview traces nothing: no polyline of the locus is drawn on the overlay
    int[] vertices = {0};
    tool.paint(
        new mathcanvas.canvas.paint.Painter() {
          @Override
          public mathcanvas.canvas.geom.Viewport viewport() {
            return mathcanvas.canvas.geom.Viewport.centred(0, 0, 1 / 40.0, 800, 800, true);
          }

          @Override
          public void beginPath() {}

          @Override
          public void moveTo(double x, double y) {}

          @Override
          public void lineTo(double x, double y) {}

          @Override
          public void polyline(double[] xs, double[] ys, int n) {
            vertices[0] += n;
          }

          @Override
          public void closePath() {}

          @Override
          public void stroke(mathcanvas.canvas.paint.Stroke stroke) {}

          @Override
          public void fill(mathcanvas.canvas.paint.Rgba colour) {}

          @Override
          public void point(
              double x, double y, double radius, mathcanvas.canvas.paint.Rgba colour) {}

          @Override
          public void text(
              String text,
              double x,
              double y,
              double dx,
              double dy,
              mathcanvas.canvas.paint.Anchor anchor,
              mathcanvas.canvas.paint.TextStyle style) {}
        },
        mathcanvas.canvas.paint.CanvasTheme.LIGHT);
    assertThat(vertices[0]).isZero();
    CanvasState after = apply(c, clicks(tool, c, point(c, 9)).getFirst());
    assertThat(after.object(id(11))).contains(new Locus(id(11), Style.DEFAULT, id(10), id(9)));
    assertThat(after.geometryOf(id(11))).isInstanceOf(Geometry.Curve.class);
    // a point on the circle that M does not follow
    CanvasState other =
        recomputed(
            c.add(
                i ->
                    new PointOnPath(
                        i, Style.DEFAULT, id(8), ParamKind.ANGLE, 0, Double.NaN, Double.NaN)));
    ConstructionTool again = ConstructionTool.locus();
    clicks(again, other, point(other, 10));
    again.move(scene(other), point(other, 11));
    assertThat(again.refusal()).contains("notDriven");
    assertThat(clicks(again, other, point(other, 11))).isEmpty();
  }
}
