package mathcanvas.canvas.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorSum;
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
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The vector tools over scripted clicks, on a canvas recomputed with the real sampler. */
class VectorToolsTest {

  private static final Window W = new Window(-4, 4, -4, 4);
  private static final Sampler SAMPLER = new Samplers().over(Environment.EMPTY, Settings.DEFAULT);
  private static final ObjectId ONE = new ObjectId(1);
  private static final ObjectId TWO = new ObjectId(2);

  private static CanvasState recomputed(CanvasState c) {
    return c.recomputeAll(SAMPLER, W, Recomputes.standard(), Cancellation.NEVER);
  }

  /** A canvas of {@code x²} (#1) and a free point (#2) at (3, −3). */
  private static CanvasState scene() {
    return recomputed(
        CanvasState.EMPTY
            .add(
                id ->
                    new FunctionGraph(
                        id,
                        Style.DEFAULT,
                        Parser.parseExpression("x^2", Names.NONE),
                        Symbol.of("x"),
                        FunctionGraph.ALL))
            .add(id -> new FreePoint(id, Style.DEFAULT, 3, -3)));
  }

  private static Scene scene(CanvasState c) {
    return new Scene(c, W, Scene.Slopes.NONE);
  }

  private static Pointer on(ObjectId id, Hit.Kind kind, double x, double y) {
    Hit hit = new Hit(0, kind, kind == Hit.Kind.POINT ? -1 : 0, 0, x, y, 1);
    return new Pointer(x, y, Optional.of(new Pointer.Picked(id, hit)));
  }

  /** Applies an addition as the app does, and recomputes. */
  private static CanvasState apply(CanvasState c, Edit e) {
    CanvasState out = c;
    for (CanvasObject o : ((Edit.Add) e).made(c.nextObjectId())) {
      out = out.add(id -> o);
    }
    return recomputed(out);
  }

  private static Edit press(Tool t, CanvasState c, Pointer p) {
    List<Edit> edits = new ArrayList<>();
    assertThat(t.press(scene(c), p, edits::add)).isTrue();
    return edits.isEmpty() ? null : edits.getFirst();
  }

  @Test
  void theVectorToolAddsAnArrowBetweenTwoClicksMakingThePointsItNeeds() {
    CanvasState c = scene();
    VectorTool tool = new VectorTool();
    assertThat(press(tool, c, Pointer.at(1, 1))).isNull();
    assertThat(tool.inProgress()).isTrue();
    Edit e = press(tool, c, on(TWO, Hit.Kind.POINT, 3, -3));
    assertThat(tool.inProgress()).isFalse();
    Edit.Add add = (Edit.Add) e;
    assertThat(add.objects()).extracting(Edit.New::named).containsExactly(true, false);
    c = apply(c, e);
    assertThat(c.object(new ObjectId(3))).containsInstanceOf(FreePoint.class);
    assertThat(c.object(new ObjectId(4)))
        .contains(
            new Arrow(
                new ObjectId(4),
                Style.DEFAULT,
                Optional.of(new ObjectId(3)),
                new Arrow.At(Optional.of(TWO))));
    Geometry.Arrow a = (Geometry.Arrow) c.geometryOf(new ObjectId(4));
    assertThat(a.tail()).isEqualTo(new Geometry.Point(1, 1));
    assertThat(a.head()).isEqualTo(new Geometry.Point(3, -3));

    // a tail on the curve, a free head; a click on the tail's own point keeps it
    tool.press(scene(c), on(ONE, Hit.Kind.POLYLINE, 1, 1), x -> {});
    c = apply(c, press(tool, c, Pointer.at(-2, 0)));
    assertThat(c.object(new ObjectId(5))).containsInstanceOf(PointOnPath.class);
    assertThat(c.object(new ObjectId(6))).containsInstanceOf(FreePoint.class);
    Geometry.Arrow b = (Geometry.Arrow) c.geometryOf(new ObjectId(7));
    // on the curve, near the click
    assertThat(b.tail().x()).isCloseTo(1, within(1e-3));
    assertThat(b.tail().y()).isCloseTo(b.tail().x() * b.tail().x(), within(1e-12));
    assertThat(b.head()).isEqualTo(new Geometry.Point(-2, 0));
    assertThat(press(tool, c, on(TWO, Hit.Kind.POINT, 3, -3))).isNull();
    assertThat(press(tool, c, on(TWO, Hit.Kind.POINT, 3, -3))).isNull();
    assertThat(tool.inProgress()).isTrue();
    tool.cancel();
    assertThat(tool.inProgress()).isFalse();
  }

  @Test
  void aTailOnACurveThatIsGoneOrReplacedIsDropped() {
    CanvasState c = scene();
    VectorTool tool = new VectorTool();
    tool.press(scene(c), on(ONE, Hit.Kind.POLYLINE, 1, 1), x -> {});
    assertThat(tool.inProgress()).isTrue();
    // the curve removed, by an undo: the next click is a new tail, not an arrow onto nothing
    CanvasState gone = recomputed(c.remove(ONE));
    assertThat(press(tool, gone, Pointer.at(2, 2))).isNull();
    assertThat(tool.inProgress()).isTrue();
    tool.cancel();

    // the id taken by another curve since: the stale snap is dropped too
    tool.press(scene(c), on(ONE, Hit.Kind.POLYLINE, 1, 1), x -> {});
    CanvasState reused =
        recomputed(
            CanvasState.EMPTY
                .add(
                    id ->
                        new FunctionGraph(
                            id,
                            Style.DEFAULT,
                            Parser.parseExpression("x^3", Names.NONE),
                            Symbol.of("x"),
                            FunctionGraph.ALL))
                .add(id -> new FreePoint(id, Style.DEFAULT, 3, -3)));
    assertThat(press(tool, reused, Pointer.at(2, 2))).isNull();
    assertThat(tool.inProgress()).isTrue();
    // the same curve, recomputed, keeps the tail
    tool.cancel();
    tool.press(scene(c), on(ONE, Hit.Kind.POLYLINE, 1, 1), x -> {});
    Edit e = press(tool, recomputed(c), Pointer.at(2, 2));
    assertThat(e).isNotNull();
    assertThat(apply(c, e).object(new ObjectId(5))).containsInstanceOf(Arrow.class);
  }

  /** The scene with arrows u (#3) from the origin to (3, −3)... */
  private static CanvasState arrows() {
    return recomputed(
        scene()
            .add(i -> new Arrow(i, Style.DEFAULT, Optional.empty(), new Arrow.At(Optional.of(TWO))))
            .add(i -> new FreePoint(i, Style.DEFAULT, 1, 1))
            .add(
                i ->
                    new Arrow(
                        i,
                        Style.DEFAULT,
                        Optional.empty(),
                        new Arrow.At(Optional.of(new ObjectId(4))))));
  }

  @Test
  void theSumAndProjectionToolsPickTwoVectorsInOrder() {
    CanvasState c = arrows();
    ObjectId u = new ObjectId(3);
    ObjectId v = new ObjectId(5);
    VectorSumTool sum = new VectorSumTool();
    // a click on nothing, or on what is not a vector, passes to the view
    assertThat(sum.press(scene(c), Pointer.at(-3, 3), x -> {})).isFalse();
    assertThat(sum.press(scene(c), on(ONE, Hit.Kind.POLYLINE, 1, 1), x -> {})).isFalse();
    assertThat(press(sum, c, on(u, Hit.Kind.POLYLINE, 1, -1))).isNull();
    assertThat(press(sum, c, on(u, Hit.Kind.POLYLINE, 2, -2))).isNull();
    assertThat(sum.press(scene(c), Pointer.at(-3, 3), x -> {})).isFalse();
    assertThat(sum.inProgress()).isTrue();
    CanvasState s = apply(c, press(sum, c, on(v, Hit.Kind.POLYLINE, 0.5, 0.5)));
    assertThat(s.object(new ObjectId(6)))
        .contains(new VectorSum(new ObjectId(6), Style.DEFAULT, u, v));
    assertThat(((Geometry.Arrow) s.geometryOf(new ObjectId(6))).head())
        .isEqualTo(new Geometry.Point(4, -2));

    ProjectionTool proj = new ProjectionTool();
    press(proj, c, on(v, Hit.Kind.POLYLINE, 0.5, 0.5));
    CanvasState p = apply(c, press(proj, c, on(u, Hit.Kind.POLYLINE, 1, -1)));
    assertThat(p.object(new ObjectId(6)))
        .contains(new Projection(new ObjectId(6), Style.DEFAULT, v, u));
    // (1, 1) onto (3, −3) is zero
    assertThat(((Geometry.Arrow) p.geometryOf(new ObjectId(6))).head())
        .isEqualTo(new Geometry.Point(0, 0));
    // the sum is a vector the tools take in turn
    press(proj, s, on(new ObjectId(6), Hit.Kind.POLYLINE, 2, -1));
    assertThat(proj.inProgress()).isTrue();
    proj.cancel();
    assertThat(proj.inProgress()).isFalse();
  }

  @Test
  void thePreviewsDrawTheArrowAndHighlightTheVectorsInEitherTheme() {
    CanvasState c = arrows();
    VectorTool tool = new VectorTool();
    List<String> calls = new ArrayList<>();
    List<Stroke> strokes = new ArrayList<>();
    Painter painter = recorder(calls, strokes);
    tool.move(scene(c), Pointer.at(-1, 2));
    tool.paint(painter, CanvasTheme.LIGHT);
    assertThat(calls).containsExactly("point");
    tool.press(scene(c), Pointer.at(-1, 2), x -> {});
    tool.move(scene(c), Pointer.at(2, 2));
    for (CanvasTheme theme : List.of(CanvasTheme.LIGHT, darker())) {
      calls.clear();
      strokes.clear();
      tool.paint(painter, theme);
      assertThat(calls).contains("stroke", "fill", "point");
      assertThat(strokes.getFirst().dash()).isNotEmpty();
      assertThat(strokes.getFirst().colour()).isEqualTo(theme.series().getFirst());
    }
    VectorSumTool sum = new VectorSumTool();
    sum.move(scene(c), on(new ObjectId(3), Hit.Kind.POLYLINE, 1, -1));
    calls.clear();
    strokes.clear();
    sum.paint(painter, CanvasTheme.LIGHT);
    assertThat(calls).containsExactly("beginPath", "moveTo", "lineTo", "stroke");
    assertThat(strokes.getFirst().width()).isEqualTo(Preview.HALO_WIDTH);
  }

  private static CanvasTheme darker() {
    List<Rgba> series = new ArrayList<>(CanvasTheme.LIGHT.series());
    series.set(0, Rgba.rgb(0x4ea3e0));
    CanvasTheme l = CanvasTheme.LIGHT;
    return new CanvasTheme(Rgba.rgb(0x1e1e1e), l.axis(), l.grid(), l.minorGrid(), l.text(), series);
  }

  private static Painter recorder(List<String> calls, List<Stroke> strokes) {
    Viewport w = Viewport.centred(0, 0, 1 / 40.0, 320, 320, true);
    return new Painter() {
      @Override
      public Viewport viewport() {
        return w;
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
          String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {
        calls.add("text");
      }
    };
  }
}
