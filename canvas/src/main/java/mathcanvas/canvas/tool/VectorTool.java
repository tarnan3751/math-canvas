package mathcanvas.canvas.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * The vector tool: a click at the tail, then a click at the head, adds the arrow between them as
 * one step. Each click uses what it would snap to ({@link Snap}): an existing point, else a new
 * point on the curve under the pointer, else a new free point, the new points named. A click on the
 * tail's own point again keeps the tail. Every press is taken, so the view does not pan while the
 * tool is chosen. The preview marks what a click would use and, once the tail is placed, draws the
 * arrow to the pointer.
 */
public final class VectorTool implements Tool {

  private Scene scene;
  private Snap snap;

  /** The tail, once clicked, or null. */
  private Snap tail;

  /** The object the tail snapped to (its point, or the curve it is on), as it was then. */
  private CanvasObject tailOn;

  /** Makes the vector tool, with no tail placed. */
  public VectorTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (tail == null) {
      tail = snap;
      tailOn = snappedTo(scene, snap);
      return true;
    }
    if (tail instanceof Snap.Existing a
        && snap instanceof Snap.Existing b
        && a.point().equals(b.point())) {
      return true;
    }
    List<Edit.New> out = new ArrayList<>();
    int t = point(tail, out);
    int h = point(snap, out);
    Snap from = tail;
    Snap to = snap;
    out.add(
        new Edit.New(
            ids ->
                new Arrow(
                    ids.getLast(),
                    Style.DEFAULT,
                    Optional.of(id(from, t, ids)),
                    new Arrow.At(Optional.of(id(to, h, ids)))),
            false));
    tail = null;
    tailOn = null;
    edits.accept(new Edit.Add(out));
    return true;
  }

  /**
   * Adds the new point a snap makes, if any.
   *
   * @return its index in the addition, or −1 for an existing point
   */
  private static int point(Snap s, List<Edit.New> out) {
    switch (s) {
      case Snap.Existing e -> {
        return -1;
      }
      case Snap.OnPath o -> {
        PathProjection.Projection r = o.at();
        out.add(
            new Edit.New(
                ids ->
                    new PointOnPath(
                        ids.getLast(), Style.DEFAULT, o.path(), r.kind(), r.t(), r.x(), r.y()),
                true));
      }
      case Snap.Free f ->
          out.add(
              new Edit.New(ids -> new FreePoint(ids.getLast(), Style.DEFAULT, f.x(), f.y()), true));
    }
    return out.size() - 1;
  }

  /** The id of a snap's point: the existing one, or the one added at an index. */
  private static ObjectId id(Snap s, int index, List<ObjectId> ids) {
    return s instanceof Snap.Existing e ? e.point() : ids.get(index);
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    if (tail != null && !stillThere(scene)) {
      tail = null; // removed meanwhile by an undo, or its id taken by another object since
    }
    snap = Snap.of(scene, pointer);
  }

  /** The object a snap uses: its existing point, or the curve it is on; null for a free point. */
  private static CanvasObject snappedTo(Scene scene, Snap s) {
    return switch (s) {
      case Snap.Existing e -> scene.canvas().object(e.point()).orElse(null);
      case Snap.OnPath o -> scene.canvas().object(o.path()).orElse(null);
      case Snap.Free f -> null;
    };
  }

  /**
   * Whether what the tail snapped to is still there: its point still a point under its id (a point
   * on a path rewrites its record at every recompute, so the kind is what is compared), the curve
   * it is on the same record (an undo frees an id, which a new object may take).
   */
  private boolean stillThere(Scene scene) {
    CanvasObject now = snappedTo(scene, tail);
    return switch (tail) {
      case Snap.Existing e -> now != null && now.isPoint();
      case Snap.OnPath o -> now != null && now.equals(tailOn);
      case Snap.Free f -> true;
    };
  }

  @Override
  public void cancel() {
    scene = null;
    snap = null;
    tail = null;
    tailOn = null;
  }

  @Override
  public boolean inProgress() {
    return tail != null;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    if (tail != null) {
      double[] from = at(tail);
      double[] to = at(snap);
      Preview.arrow(painter, theme, from[0], from[1], to[0], to[1]);
      Preview.snap(painter, theme, tail);
    }
    Preview.snap(painter, theme, snap);
  }

  private static double[] at(Snap s) {
    return switch (s) {
      case Snap.Existing e -> new double[] {e.x(), e.y()};
      case Snap.OnPath o -> new double[] {o.at().x(), o.at().y()};
      case Snap.Free f -> new double[] {f.x(), f.y()};
    };
  }
}
