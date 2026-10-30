package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.expr.Derivative;

/**
 * The tangent line tool: one click on a function graph, or on a point on one, adds the tangent
 * there. A click on the graph adds a point on it (named) and the tangent through that point, as one
 * step; a click on an existing point on a graph adds the tangent alone. The tangent is a {@link
 * Line.PointSlope} whose slope is the graph's derivative left unevaluated, {@code diff(body, x)},
 * with {@code x} bound to the point's abscissa: the sampler's evaluation differentiates it on the
 * worker, in the environment, so the line follows the point as it is dragged and the graph as its
 * sliders move. It does not follow a later redefinition of the graph, whose body it copies. Where
 * the derivative is not finite (a vertical tangent) the line is undefined.
 *
 * <p>The preview shows the point the click would use and the tangent there, dashed, at the slope
 * the scene's evaluators give.
 */
public final class TangentTool implements Tool {

  private Scene scene;
  private Snap snap;

  /** Makes the tangent tool. */
  public TangentTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    Optional<Target> target = target(scene, snap);
    if (target.isEmpty()) {
      return false;
    }
    edits.accept(new Edit.Add(news(target.get())));
    return true;
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    this.snap = Snap.of(scene, pointer);
  }

  @Override
  public void cancel() {
    scene = null;
    snap = null;
  }

  @Override
  public boolean inProgress() {
    return false;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (snap == null) {
      return;
    }
    Optional<Target> target = target(scene, snap);
    if (target.isEmpty()) {
      return;
    }
    Target t = target.get();
    Preview.curve(painter, theme, scene.canvas(), t.graph().id());
    Preview.snap(painter, theme, snap);
    double slope = scene.slopes().at(t.graph().id(), t.x());
    Preview.line(
        painter, theme, t.x(), t.y(), slope, scene.window().diagonal(), Style.DEFAULT.width());
  }

  /**
   * Where a tangent would go.
   *
   * @param graph the graph
   * @param point the existing point on it, or empty for a new one
   * @param x the abscissa
   * @param y the ordinate
   */
  private record Target(FunctionGraph graph, Optional<ObjectId> point, double x, double y) {}

  private static Optional<Target> target(Scene scene, Snap snap) {
    return switch (snap) {
      case Snap.Existing e -> {
        Optional<CanvasObject> o = scene.canvas().object(e.point());
        if (o.isPresent()
            && o.get() instanceof PointOnPath p
            && p.kind() == ParamKind.X
            && scene.canvas().object(p.path()).orElse(null) instanceof FunctionGraph g) {
          yield Optional.of(new Target(g, Optional.of(p.id()), e.x(), e.y()));
        }
        yield Optional.empty();
      }
      case Snap.OnPath o ->
          scene.canvas().object(o.path()).orElse(null) instanceof FunctionGraph g
                  && o.at().kind() == ParamKind.X
              ? Optional.of(new Target(g, Optional.empty(), o.at().x(), o.at().y()))
              : Optional.empty();
      case Snap.Free f -> Optional.empty();
    };
  }

  /** The objects a click adds: the point when it is new, then the tangent through it. */
  private static List<Edit.New> news(Target t) {
    FunctionGraph g = t.graph();
    Edit.New line =
        new Edit.New(
            ids ->
                new Line(
                    ids.getLast(),
                    Style.DEFAULT,
                    new Line.PointSlope(
                        t.point().orElse(ids.getFirst()),
                        Derivative.of(g.body(), g.variable(), 1),
                        g.variable())),
            false);
    if (t.point().isPresent()) {
      return List.of(line);
    }
    Edit.New point =
        new Edit.New(
            ids ->
                new PointOnPath(
                    ids.getLast(), Style.DEFAULT, g.id(), ParamKind.X, t.x(), t.x(), t.y()),
            true);
    return List.of(point, line);
  }
}
