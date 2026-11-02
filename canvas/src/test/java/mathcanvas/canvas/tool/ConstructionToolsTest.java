package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.Angle;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Circle;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.Midpoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Ray;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Segment;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

/** The construction tools over scripted clicks, on a canvas recomputed with the real sampler. */
class ConstructionToolsTest {

  private static final Window W = new Window(-10, 10, -10, 10);
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  private static ObjectId id(long v) {
    return new ObjectId(v);
  }

  private static CanvasState recomputed(CanvasState c) {
    return c.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** Free points A(1) at (0, 0), B(2) at (4, 0), C(3) at (0, 3); the segment AB (4). */
  private static CanvasState scene() {
    return recomputed(
        CanvasState.EMPTY
            .add(i -> new FreePoint(i, Style.DEFAULT, 0, 0))
            .add(i -> new FreePoint(i, Style.DEFAULT, 4, 0))
            .add(i -> new FreePoint(i, Style.DEFAULT, 0, 3))
            .add(i -> new Segment(i, Style.DEFAULT, id(1), id(2))));
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

  /** Applies an addition as the app does, and recomputes. */
  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(i -> o);
    }
    return recomputed(out);
  }

  /** Presses in turn; returns the edits the presses made. */
  private static List<Edit> clicks(Tool t, CanvasState c, Pointer... ps) {
    List<Edit> edits = new ArrayList<>();
    for (Pointer p : ps) {
      t.press(scene(c), p, edits::add);
    }
    return edits;
  }

  @Test
  void theTwoPointToolsMakeTheirPointsAndObjectInOneStep() {
    CanvasState c = scene();
    for (ConstructionTool tool :
        List.of(ConstructionTool.segment(), ConstructionTool.line(), ConstructionTool.ray())) {
      List<Edit> edits = clicks(tool, c, Pointer.at(1, 1), point(c, 3));
      assertThat(edits).hasSize(1);
      Edit.Add add = (Edit.Add) edits.getFirst();
      assertThat(add.objects()).extracting(Edit.New::named).containsExactly(true, false);
      CanvasState after = apply(c, add);
      CanvasObject o = after.object(id(6)).orElseThrow();
      assertThat(o.parents()).containsExactly(id(5), id(3));
      assertThat(after.geometryOf(id(6))).isInstanceOf(Geometry.Curve.class);
      assertThat(tool.inProgress()).isFalse();
    }
    assertThat(
            apply(c, clicks(ConstructionTool.ray(), c, point(c, 1), point(c, 2)).getFirst())
                .object(id(5)))
        .contains(new Ray(id(5), Style.DEFAULT, id(1), id(2)));
  }

  @Test
  void aPointPickedTwiceIsIgnoredAndEscapeAbandons() {
    CanvasState c = scene();
    ConstructionTool tool = ConstructionTool.angle();
    assertThat(clicks(tool, c, point(c, 2), point(c, 2), point(c, 1))).isEmpty();
    assertThat(tool.inProgress()).isTrue();
    tool.cancel();
    assertThat(tool.inProgress()).isFalse();
    List<Edit> edits = clicks(tool, c, point(c, 2), point(c, 1), point(c, 3));
    CanvasState after = apply(c, edits.getFirst());
    assertThat(after.object(id(5))).contains(new Angle(id(5), Style.DEFAULT, id(2), id(1), id(3)));
    assertThat(((Geometry.Angle) after.geometryOf(id(5))).sweep())
        .isCloseTo(Math.PI / 2, within(1e-12));
  }

  @Test
  void theCircleToolsTakePointsOrARadius() {
    CanvasState c = scene();
    CanvasState three =
        apply(
            c,
            clicks(ConstructionTool.circleThreePoints(), c, point(c, 1), point(c, 2), point(c, 3))
                .getFirst());
    assertThat(((Parametrisation.Circular) parametrisation(three, 5)).r())
        .isCloseTo(2.5, within(1e-12));

    ConstructionTool radius = ConstructionTool.circleCenterRadius();
    assertThat(clicks(radius, c, point(c, 1))).isEmpty();
    assertThat(radius.wanted()).contains("r");
    List<Edit> typed = new ArrayList<>();
    radius.typed(Num.of(2), 2, typed::add);
    CanvasState r2 = apply(c, typed.getFirst());
    assertThat(r2.object(id(5)))
        .contains(new Circle(id(5), Style.DEFAULT, new Circle.CenterRadius(id(1), Num.of(2))));
    // a click gives the distance from the centre
    List<Edit> clicked = clicks(radius, c, point(c, 1), Pointer.at(3, 4));
    assertThat(((Parametrisation.Circular) parametrisation(apply(c, clicked.getFirst()), 5)).r())
        .isCloseTo(5, within(1e-12));
  }

  private static Parametrisation parametrisation(CanvasState c, long id) {
    return ((Path.Parametrised) ((Geometry.Curve) c.geometryOf(id(id))).path()).parametrisation();
  }

  @Test
  void aPerpendicularTakesALineThenAPointAndPassesOnNothing() {
    CanvasState c = scene();
    ConstructionTool tool = ConstructionTool.perpendicular();
    List<Edit> edits = new ArrayList<>();
    assertThat(tool.press(scene(c), Pointer.at(5, 5), edits::add)).isFalse();
    assertThat(tool.press(scene(c), point(c, 3), edits::add)).isFalse();
    assertThat(tool.press(scene(c), curve(4, 2, 0), edits::add)).isTrue();
    tool.press(scene(c), point(c, 3), edits::add);
    CanvasState after = apply(c, edits.getFirst());
    assertThat(after.object(id(5)))
        .contains(new Line(id(5), Style.DEFAULT, new Line.Perpendicular(id(3), id(4))));
  }

  @Test
  void aMidpointAndABisectorTakeASegmentAsItsEnds() {
    CanvasState c = scene();
    CanvasState m = apply(c, clicks(ConstructionTool.midpoint(), c, curve(4, 1, 0)).getFirst());
    assertThat(m.object(id(5))).contains(new Midpoint(id(5), Style.DEFAULT, id(1), id(2)));
    assertThat(m.geometryOf(id(5))).isEqualTo(new Geometry.Point(2, 0));
    Edit.Add add = (Edit.Add) clicks(ConstructionTool.midpoint(), c, curve(4, 1, 0)).getFirst();
    assertThat(add.objects()).extracting(Edit.New::named).containsExactly(true);
    CanvasState b =
        apply(c, clicks(ConstructionTool.perpendicularBisector(), c, curve(4, 1, 0)).getFirst());
    assertThat(b.object(id(5)))
        .contains(new Line(id(5), Style.DEFAULT, new Line.PerpendicularBisector(id(1), id(2))));
  }

  /** The scene with the circle about A through B (5), radius 4. */
  private static CanvasState withCircle() {
    CanvasState c = scene();
    return apply(
        c, clicks(ConstructionTool.circleCenterPoint(), c, point(c, 1), point(c, 2)).getFirst());
  }

  @Test
  void theTangentsAreTwoFromOutsideOneOnTheCircleAndNoneFromInside() {
    CanvasState c = withCircle();
    ConstructionTool tool = ConstructionTool.tangents();
    Edit.Add two = (Edit.Add) clicks(tool, c, curve(5, 0, -4), Pointer.at(6, 0)).getFirst();
    assertThat(two.objects()).hasSize(3); // the new point and two tangents
    CanvasState after = apply(c, two);
    assertThat(after.object(id(7)))
        .contains(new Line(id(7), Style.DEFAULT, new Line.CircleTangent(id(5), id(6), 0)));
    assertThat(after.object(id(8)))
        .contains(new Line(id(8), Style.DEFAULT, new Line.CircleTangent(id(5), id(6), 1)));
    // at B, on the circle: one
    Edit.Add one = (Edit.Add) clicks(tool, c, curve(5, 0, -4), point(c, 2)).getFirst();
    assertThat(one.objects()).hasSize(1);
    // from inside: nothing, and the tool says why
    List<Edit> none = new ArrayList<>();
    tool.press(scene(c), curve(5, 0, -4), none::add);
    tool.move(scene(c), Pointer.at(1, 1));
    assertThat(tool.refusal()).contains("inside");
    tool.press(scene(c), Pointer.at(1, 1), none::add);
    assertThat(none).isEmpty();
    assertThat(tool.inProgress()).isFalse();
    assertThat(tool.refusal()).isEmpty();
  }

  @Test
  void aRegularPolygonWaitsForItsNumberOfSides() {
    CanvasState c = scene();
    ConstructionTool tool = ConstructionTool.regularPolygon();
    assertThat(clicks(tool, c, point(c, 1), point(c, 2), Pointer.at(5, 5))).isEmpty();
    assertThat(tool.wanted()).contains("n");
    List<Edit> edits = new ArrayList<>();
    tool.typed(Num.of(4), 4, edits::add);
    CanvasState after = apply(c, edits.getFirst());
    Parametrisation.Polygonal square = (Parametrisation.Polygonal) parametrisation(after, 5);
    assertThat(square.x(2)).isCloseTo(4, within(1e-12));
    assertThat(square.y(2)).isCloseTo(4, within(1e-12));
  }

  @Test
  void thePolygonToolClosesOnItsFirstVertex() {
    CanvasState c = scene();
    PolygonTool tool = new PolygonTool();
    List<Edit> edits =
        clicks(tool, c, Pointer.at(-5, -5), point(c, 2), Pointer.at(5, 5), point(c, 3));
    assertThat(edits).isEmpty();
    assertThat(tool.inProgress()).isTrue();
    edits = clicks(tool, c, Pointer.at(-5.05, -5));
    Edit.Add add = (Edit.Add) edits.getFirst();
    assertThat(add.objects()).extracting(Edit.New::named).containsExactly(true, true, false);
    CanvasState after = apply(c, add);
    assertThat(after.object(id(7)))
        .contains(
            new Polygon(
                id(7), Style.DEFAULT, new Polygon.Vertices(List.of(id(5), id(2), id(6), id(3)))));
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void aPickRemovedByAnUndoStartsTheConstructionAgain() {
    CanvasState c = scene();
    ConstructionTool tool = ConstructionTool.perpendicular();
    clicks(tool, c, curve(4, 2, 0));
    assertThat(tool.inProgress()).isTrue();
    CanvasState gone = recomputed(c.remove(id(4)));
    tool.move(scene(gone), Pointer.at(1, 1));
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void thePreviewIsTheObjectAClickWouldAddInEitherTheme() {
    CanvasState c = withCircle();
    ConstructionTool circle = ConstructionTool.circleThreePoints();
    clicks(circle, c, point(c, 1), point(c, 2));
    circle.move(scene(c), Pointer.at(0, 3));
    for (CanvasTheme theme : List.of(CanvasTheme.LIGHT, darkTheme())) {
      Recorder r = new Recorder();
      circle.paint(r, theme);
      // the circle through the pointer, dashed, in the new object's colour
      assertThat(r.strokes).anySatisfy(s -> assertThat(s.dash()).isNotEmpty());
      assertThat(r.strokes.getLast().colour()).isEqualTo(theme.series().getFirst());
      assertThat(r.vertices).isGreaterThan(64);
    }
    ConstructionTool tangents = ConstructionTool.tangents();
    clicks(tangents, c, curve(5, 0, -4));
    tangents.move(scene(c), Pointer.at(8, 0));
    Recorder r = new Recorder();
    tangents.paint(r, CanvasTheme.LIGHT);
    assertThat(r.strokes.stream().filter(s -> !s.dash().isEmpty())).hasSize(2);
    tangents.move(scene(c), Pointer.at(1, 0));
    r = new Recorder();
    tangents.paint(r, CanvasTheme.LIGHT);
    assertThat(r.strokes.stream().filter(s -> !s.dash().isEmpty())).isEmpty();
  }

  private static CanvasTheme darkTheme() {
    List<Rgba> series = new ArrayList<>();
    for (int k = 0; k < 8; k++) {
      series.add(Rgba.rgb(0x4ea3e0 + k));
    }
    return new CanvasTheme(
        Rgba.rgb(0x1e1e1e),
        Rgba.rgb(0xd0d0d4),
        Rgba.rgb(0x3a3a3e),
        Rgba.rgb(0x2c2c30),
        Rgba.rgb(0xd0d0d4),
        series);
  }

  /** A painter that keeps the strokes and counts the vertices of polylines. */
  private static final class Recorder implements Painter {

    final List<Stroke> strokes = new ArrayList<>();
    int vertices;

    @Override
    public Viewport viewport() {
      return Viewport.centred(0, 0, 1 / 40.0, 800, 800, true);
    }

    @Override
    public void beginPath() {}

    @Override
    public void moveTo(double x, double y) {}

    @Override
    public void lineTo(double x, double y) {}

    @Override
    public void polyline(double[] xs, double[] ys, int n) {
      vertices += n;
    }

    @Override
    public void closePath() {}

    @Override
    public void stroke(Stroke stroke) {
      strokes.add(stroke);
    }

    @Override
    public void fill(Rgba colour) {}

    @Override
    public void point(double x, double y, double radius, Rgba colour) {}

    @Override
    public void text(
        String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {}
  }
}
