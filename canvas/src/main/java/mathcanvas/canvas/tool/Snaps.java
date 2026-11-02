package mathcanvas.canvas.tool;

import java.util.List;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;

/** What the tools that place points share: where a snap is, and the new point it adds. */
final class Snaps {

  private Snaps() {}

  /**
   * Where a snap is.
   *
   * @param s the snap
   * @return its abscissa and ordinate
   */
  static double[] at(Snap s) {
    return switch (s) {
      case Snap.Existing e -> new double[] {e.x(), e.y()};
      case Snap.OnPath o -> new double[] {o.at().x(), o.at().y()};
      case Snap.Free f -> new double[] {f.x(), f.y()};
    };
  }

  /**
   * Adds the new point a snap makes, if any, named.
   *
   * @param s the snap
   * @param out the addition
   * @return its index in the addition, or −1 for an existing point
   */
  static int point(Snap s, List<Edit.New> out) {
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
}
