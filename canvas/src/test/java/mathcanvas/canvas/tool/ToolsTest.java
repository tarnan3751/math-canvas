package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/**
 * The tools' state machines over scripted clicks, on a canvas recomputed with the real sampler: the
 * edits each makes, and what those edits make when applied.
 */
class ToolsTest {

  private static final Window W = new Window(-4, 4, -4, 4);
  private static final Symbol X = Symbol.of("x");
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);

  private static FunctionGraph graph(ObjectId id, String body) {
    return new FunctionGraph(
        id, Style.DEFAULT, Parser.parseExpression(body, Names.NONE), X, FunctionGraph.ALL);
  }

  private static CanvasState recomputed(CanvasState c) {
    return c.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** A canvas of {@code x²} (#1), {@code x + 2} (#2) and a free point (#3). */
  private static CanvasState scene() {
    return recomputed(
        CanvasState.EMPTY
            .add(id -> graph(id, "x^2"))
            .add(id -> graph(id, "x + 2"))
            .add(id -> new FreePoint(id, Style.DEFAULT, 3, -3)));
  }

  private static Scene scene(CanvasState c) {
    return new Scene(c, W, Scene.Slopes.NONE);
  }

  private static final ObjectId ONE = new ObjectId(1);
  private static final ObjectId TWO = new ObjectId(2);
  private static final ObjectId THREE = new ObjectId(3);

  /** The pointer on a curve at an abscissa, as the hit index reports it. */
  private static Pointer onCurve(CanvasState c, ObjectId id, double x) {
    Geometry.Curve g = (Geometry.Curve) c.geometryOf(id);
    double y = Double.NaN;
    for (var b : g.branches()) {
      for (int k = 0; k + 1 < b.size(); k++) {
        if (b.x(k) <= x && x <= b.x(k + 1)) {
          double u = (x - b.x(k)) / (b.x(k + 1) - b.x(k));
          y = b.y(k) + u * (b.y(k + 1) - b.y(k));
        }
      }
    }
    Hit hit = new Hit(0, Hit.Kind.POLYLINE, 0, 0, x, y, 1);
    return new Pointer(x, y + 0.01, Optional.of(new Pointer.Picked(id, hit)));
  }

  private static Pointer onPoint(CanvasState c, ObjectId id) {
    Geometry.Point p = (Geometry.Point) c.geometryOf(id);
    Hit hit = new Hit(0, Hit.Kind.POINT, 0, 0, p.x(), p.y(), 1);
    return new Pointer(p.x() + 0.01, p.y(), Optional.of(new Pointer.Picked(id, hit)));
  }

  /** Applies an addition as the app does: consecutive ids from the canvas's next one. */
  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(id -> o);
    }
    return out;
  }

  private static double slope(CanvasState c, ObjectId line) {
    Geometry.Curve g = (Geometry.Curve) c.geometryOf(line);
    var l =
        (mathcanvas.canvas.model.Parametrisation.Linear)
            ((Path.Parametrised) g.path()).parametrisation();
    return l.dy() / l.dx();
  }

  @Test
  void theSnapPrefersAPointThenACurveThenTheFreePlane() {
    CanvasState c = scene();
    assertThat(Snap.of(scene(c), onPoint(c, THREE))).isEqualTo(new Snap.Existing(THREE, 3, -3));
    assertThat(Snap.of(scene(c), onCurve(c, ONE, 1)))
        .isInstanceOfSatisfying(
            Snap.OnPath.class,
            s -> {
              assertThat(s.path()).isEqualTo(ONE);
              assertThat(s.at().kind()).isEqualTo(ParamKind.X);
              assertThat(s.at().t()).isCloseTo(1, within(1e-9));
            });
    assertThat(Snap.of(scene(c), Pointer.at(-3, 3))).isEqualTo(new Snap.Free(-3, 3));
  }

  @Test
  void theTangentAtAPointOfXSquaredHasSlopeTwoAndFollowsADrag() {
    CanvasState c = scene();
    TangentTool tool = new TangentTool();
    List<Edit> edits = new ArrayList<>();

    assertThat(tool.press(scene(c), onCurve(c, ONE, 1), edits::add)).isTrue();

    assertThat(edits).singleElement().isInstanceOf(Edit.Add.class);
    List<CanvasObject> made = ((Edit.Add) edits.getFirst()).made(new ObjectId(4));
    assertThat(made.get(0)).isInstanceOf(PointOnPath.class);
    assertThat(((Edit.Add) edits.getFirst()).objects().get(0).named()).isTrue();
    assertThat(((Edit.Add) edits.getFirst()).objects().get(1).named()).isFalse();
    Line line = (Line) made.get(1);
    assertThat(line.definition())
        .isEqualTo(
            new Line.PointSlope(
                new ObjectId(4),
                Derivative.of(Parser.parseExpression("x^2", Names.NONE), X, 1),
                X));
    CanvasState after = recomputed(apply(c, edits.getFirst()));
    ObjectId point = new ObjectId(4);
    ObjectId tangent = new ObjectId(5);
    assertThat(slope(after, tangent)).isCloseTo(2, within(1e-12));

    // a scripted drag of the point along the graph, as the point on curve tool makes it
    PointOnCurveTool drag = new PointOnCurveTool();
    List<Edit> moves = new ArrayList<>();
    assertThat(drag.press(scene(after), onPoint(after, point), moves::add)).isTrue();
    drag.drag(scene(after), Pointer.at(1.5, 2.25), moves::add);
    drag.drag(scene(after), Pointer.at(2, 4), moves::add);
    drag.release(scene(after), Pointer.at(2, 4), moves::add);
    Edit.Move last = (Edit.Move) moves.getLast();
    assertThat(last.parameter()).isCloseTo(2, within(1e-3));
    PointOnPath p = (PointOnPath) after.object(point).orElseThrow();
    CanvasState moved =
        after
            .replace(p.withParameter(p.kind(), 2).landedAt(last.x(), last.y()))
            .recompute(
                java.util.Set.of(point),
                java.util.Set.of(),
                SAMPLER,
                W,
                Recomputes.standard(),
                Cancellation.NEVER);
    assertThat(slope(moved, tangent)).isCloseTo(4, within(1e-12));
    assertThat(drag.inProgress()).isFalse();
  }

  @Test
  void theTangentAtAnExistingPointAddsTheLineAlone() {
    CanvasState c =
        recomputed(
            scene().add(id -> new PointOnPath(id, Style.DEFAULT, ONE, ParamKind.X, -1, -1, 1)));
    List<Edit> edits = new ArrayList<>();
    new TangentTool().press(scene(c), onPoint(c, new ObjectId(4)), edits::add);
    Edit.Add add = (Edit.Add) edits.getFirst();
    assertThat(add.objects()).hasSize(1);
    CanvasState after = recomputed(apply(c, add));
    assertThat(slope(after, new ObjectId(5))).isCloseTo(-2, within(1e-12));
  }

  @Test
  void theTangentToolIgnoresAFreePointAndTheEmptyPlane() {
    CanvasState c = scene();
    List<Edit> edits = new ArrayList<>();
    TangentTool tool = new TangentTool();
    assertThat(tool.press(scene(c), onPoint(c, THREE), edits::add)).isFalse();
    assertThat(tool.press(scene(c), Pointer.at(0, 3), edits::add)).isFalse();
    assertThat(edits).isEmpty();
  }

  @Test
  void theIntersectionToolTakesTwoPathsLowerIdFirst() {
    CanvasState c = scene();
    IntersectionTool tool = new IntersectionTool();
    List<Edit> edits = new ArrayList<>();

    assertThat(tool.press(scene(c), onCurve(c, TWO, 0), edits::add)).isTrue();
    assertThat(tool.inProgress()).isTrue();
    assertThat(tool.press(scene(c), onCurve(c, TWO, 1), edits::add)).isTrue();
    assertThat(tool.press(scene(c), Pointer.at(-3, -3), edits::add)).isFalse();
    assertThat(edits).isEmpty();
    assertThat(tool.press(scene(c), onCurve(c, ONE, 1), edits::add)).isTrue();

    assertThat(edits).containsExactly(new Edit.Intersect(ONE, TWO));
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void theIntersectionsOfXSquaredAndXPlusTwoAreAtTheirOrdinals() {
    CanvasState c = scene();
    // the app adds one member per place found, in order: here, two
    CanvasState after =
        recomputed(
            c.add(id -> Intersection.of(id, Style.DEFAULT, ONE, TWO, 0))
                .add(id -> Intersection.of(id, Style.DEFAULT, ONE, TWO, 1)));
    Geometry.Point a = (Geometry.Point) after.geometryOf(new ObjectId(4));
    Geometry.Point b = (Geometry.Point) after.geometryOf(new ObjectId(5));
    assertThat(a.x()).isCloseTo(-1, within(1e-9));
    assertThat(a.y()).isCloseTo(1, within(1e-9));
    assertThat(b.x()).isCloseTo(2, within(1e-9));
    assertThat(b.y()).isCloseTo(4, within(1e-9));
  }

  @Test
  void escapeAbandonsTheFirstPath() {
    CanvasState c = scene();
    IntersectionTool tool = new IntersectionTool();
    List<Edit> edits = new ArrayList<>();
    tool.press(scene(c), onCurve(c, ONE, 0), edits::add);
    tool.cancel();
    assertThat(tool.inProgress()).isFalse();
    tool.press(scene(c), onCurve(c, TWO, 0), edits::add);
    assertThat(edits).isEmpty();
    assertThat(tool.inProgress()).isTrue();
  }

  @Test
  void theFeatureToolsAskForTheirKindsOfAGraph() {
    CanvasState c = scene();
    List<Edit> edits = new ArrayList<>();
    assertThat(FeatureTool.roots().press(scene(c), onCurve(c, ONE, 1), edits::add)).isTrue();
    assertThat(FeatureTool.extrema().press(scene(c), onCurve(c, TWO, 1), edits::add)).isTrue();
    assertThat(FeatureTool.roots().press(scene(c), onPoint(c, THREE), edits::add)).isFalse();
    assertThat(edits)
        .containsExactly(
            new Edit.Find(ONE, List.of(Feature.ROOT)),
            new Edit.Find(TWO, List.of(Feature.MIN, Feature.MAX)));
  }

  @Test
  void thePointOnCurveToolPlacesANamedPointAndDragsIt() {
    CanvasState c = scene();
    PointOnCurveTool tool = new PointOnCurveTool();
    List<Edit> edits = new ArrayList<>();

    assertThat(tool.press(scene(c), Pointer.at(-3, 3), edits::add)).isFalse();
    assertThat(tool.press(scene(c), onPoint(c, THREE), edits::add)).isFalse();
    assertThat(tool.press(scene(c), onCurve(c, ONE, 0.5), edits::add)).isTrue();
    Edit.Add add = (Edit.Add) edits.getFirst();
    assertThat(add.objects().getFirst().named()).isTrue();
    CanvasState after = recomputed(apply(c, add));
    PointOnPath p = (PointOnPath) after.object(new ObjectId(4)).orElseThrow();
    assertThat(p.parameter()).isCloseTo(0.5, within(1e-6));

    tool.drag(scene(after), Pointer.at(-1, 1), edits::add);
    assertThat(edits.getLast()).isInstanceOf(Edit.Move.class);
    assertThat(((Edit.Move) edits.getLast()).id()).isEqualTo(new ObjectId(4));
    assertThat(((Edit.Move) edits.getLast()).parameter()).isCloseTo(-1, within(1e-3));
    assertThat(tool.inProgress()).isTrue();
    tool.cancel();
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void thePreviewsPaintInEitherTheme() {
    CanvasState c = scene();
    Scene s = new Scene(c, W, (g, x) -> 2 * x);
    List<String> calls = new ArrayList<>();
    var painter = new RecordingPainter(calls);
    TangentTool tangent = new TangentTool();
    tangent.move(s, onCurve(c, ONE, 1));
    for (CanvasTheme theme : List.of(CanvasTheme.LIGHT, DARK)) {
      calls.clear();
      tangent.paint(painter, theme);
      assertThat(calls).contains("point", "lineTo");
      assertThat(painter.strokes).last().satisfies(st -> assertThat(st.dash()).isNotEmpty());
      assertThat(painter.strokes.getLast().colour()).isEqualTo(theme.series().getFirst());
    }
    tangent.move(s, Pointer.at(-3, 3));
    calls.clear();
    tangent.paint(painter, CanvasTheme.LIGHT);
    assertThat(calls).isEmpty();
  }

  private static final CanvasTheme DARK =
      new CanvasTheme(
          Rgba.rgb(0x1e1e1e),
          Rgba.rgb(0xd0d0d4),
          Rgba.rgb(0x3a3a3e),
          Rgba.rgb(0x2c2c30),
          Rgba.rgb(0xd0d0d4),
          List.of(
              Rgba.rgb(0x4ea3e0),
              Rgba.rgb(0xff6b6b),
              Rgba.rgb(0x5cd65c),
              Rgba.rgb(0xb894e0),
              Rgba.rgb(0xffa040),
              Rgba.rgb(0x40d8e8),
              Rgba.rgb(0xc08a70),
              Rgba.rgb(0xf09ad8)));

  /** A painter that records the calls made and the strokes given. */
  private static final class RecordingPainter implements Painter {

    private final List<String> calls;
    final List<Stroke> strokes = new ArrayList<>();

    RecordingPainter(List<String> calls) {
      this.calls = calls;
    }

    @Override
    public mathcanvas.canvas.geom.Viewport viewport() {
      return mathcanvas.canvas.geom.Viewport.centred(0, 0, 1 / 40.0, 320, 320, true);
    }

    @Override
    public void beginPath() {
      calls.add("beginPath");
    }

    @Override
    public void moveTo(double x, double y) {
      calls.add("moveTo");
    }

    @Override
    public void lineTo(double x, double y) {
      calls.add("lineTo");
    }

    @Override
    public void polyline(double[] xs, double[] ys, int n) {
      calls.add("polyline");
    }

    @Override
    public void closePath() {
      calls.add("closePath");
    }

    @Override
    public void stroke(Stroke stroke) {
      calls.add("stroke");
      strokes.add(stroke);
    }

    @Override
    public void fill(Rgba colour) {
      calls.add("fill");
    }

    @Override
    public void point(double x, double y, double radius, Rgba colour) {
      calls.add("point");
    }

    @Override
    public void text(
        String text,
        double x,
        double y,
        double dx,
        double dy,
        mathcanvas.canvas.paint.Anchor anchor,
        mathcanvas.canvas.paint.TextStyle style) {
      calls.add("text");
    }
  }
}
