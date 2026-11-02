package mathcanvas.canvas.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polygon;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Stroke;

/**
 * The polygon tool: a click per vertex, each what a click would snap to (an existing point, else a
 * new named point on the curve under the pointer, else a new named free point), and a click on the
 * first vertex again, with three or more placed, closes the polygon and adds it with its new points
 * as one step. A click on a vertex already placed, other than the first, is ignored; Escape
 * abandons the polygon. The preview draws the polygon so far closed through the pointer, dashed.
 */
public final class PolygonTool implements Tool {

  /** How near the first vertex a click closes the polygon, as a fraction of the window's width. */
  static final double CLOSE = 0.01;

  private final List<Snap> vertices = new ArrayList<>();
  private final List<CanvasObject> snappedTo = new ArrayList<>();
  private Scene scene;
  private Snap snap;

  /** Makes the polygon tool, with no vertex placed. */
  public PolygonTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (closes()) {
      add(edits);
      return true;
    }
    if (snap instanceof Snap.Existing e
        && vertices.stream()
            .anyMatch(v -> v instanceof Snap.Existing x && x.point().equals(e.point()))) {
      return true;
    }
    vertices.add(snap);
    snappedTo.add(
        switch (snap) {
          case Snap.Existing e -> scene.canvas().object(e.point()).orElse(null);
          case Snap.OnPath o -> scene.canvas().object(o.path()).orElse(null);
          case Snap.Free f -> null;
        });
    return true;
  }

  /** Whether the pointer is on the first vertex, with three or more placed. */
  private boolean closes() {
    if (vertices.size() < 3) {
      return false;
    }
    Snap first = vertices.getFirst();
    if (first instanceof Snap.Existing a && snap instanceof Snap.Existing b) {
      return a.point().equals(b.point());
    }
    double[] f = Snaps.at(first);
    double[] p = Snaps.at(snap);
    return Math.hypot(f[0] - p[0], f[1] - p[1]) <= CLOSE * scene.window().width();
  }

  private void add(Consumer<Edit> edits) {
    List<Edit.New> out = new ArrayList<>();
    int[] at = new int[vertices.size()];
    for (int k = 0; k < at.length; k++) {
      at[k] = Snaps.point(vertices.get(k), out);
    }
    List<Snap> vs = List.copyOf(vertices);
    out.add(
        new Edit.New(
            ids -> {
              List<ObjectId> points = new ArrayList<>();
              for (int k = 0; k < vs.size(); k++) {
                points.add(vs.get(k) instanceof Snap.Existing e ? e.point() : ids.get(at[k]));
              }
              return new Polygon(ids.getLast(), Style.DEFAULT, new Polygon.Vertices(points));
            },
            false));
    cancel();
    edits.accept(new Edit.Add(out));
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    for (int k = 0; k < vertices.size(); k++) {
      CanvasObject then = snappedTo.get(k);
      Optional<CanvasObject> now =
          switch (vertices.get(k)) {
            case Snap.Existing e -> scene.canvas().object(e.point());
            case Snap.OnPath o -> scene.canvas().object(o.path());
            case Snap.Free f -> Optional.empty();
          };
      boolean there =
          then == null
              || now.isPresent() && (then.isPoint() ? now.get().isPoint() : now.get().equals(then));
      if (!there) {
        cancel(); // a vertex removed meanwhile by an undo, or its id taken since
        break;
      }
    }
    snap = Snap.of(scene, pointer);
  }

  @Override
  public void cancel() {
    vertices.clear();
    snappedTo.clear();
  }

  @Override
  public boolean inProgress() {
    return !vertices.isEmpty();
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    if (!vertices.isEmpty()) {
      int n = vertices.size() + (closes() ? 0 : 1);
      double[] xs = new double[n + 1];
      double[] ys = new double[n + 1];
      for (int k = 0; k <= n; k++) {
        double[] at =
            k == n
                ? Snaps.at(vertices.getFirst())
                : k < vertices.size() ? Snaps.at(vertices.get(k)) : Snaps.at(snap);
        xs[k] = at[0];
        ys[k] = at[1];
      }
      painter.beginPath();
      painter.polyline(xs, ys, xs.length);
      painter.fill(Preview.colour(theme).withOpacity(Preview.FILL_OPACITY));
      painter.beginPath();
      painter.polyline(xs, ys, xs.length);
      painter.stroke(Stroke.dashed(Preview.colour(theme), 2, 6, 4));
      for (Snap v : vertices) {
        Preview.snap(painter, theme, v);
      }
    }
    Preview.snap(painter, theme, snap);
  }
}
