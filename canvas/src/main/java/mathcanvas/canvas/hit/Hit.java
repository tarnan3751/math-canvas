package mathcanvas.canvas.hit;

/**
 * The nearest thing to a query: which item, and where on it.
 *
 * @param item the item's number, as given to {@link HitIndex.Builder}
 * @param kind a point, a polyline or an area
 * @param segment the polyline's segment from point {@code segment} to {@code segment + 1}, or -1
 *     for a point or an area
 * @param t where on the segment, from 0 at its first point to 1 at its second; 0 for a point or an
 *     area
 * @param x the world x of the nearest point
 * @param y the world y of the nearest point
 * @param distance the distance from the query in pixels
 */
public record Hit(int item, Kind kind, int segment, double t, double x, double y, double distance) {

  /** What was hit. */
  public enum Kind {
    /** A point. */
    POINT,
    /** A polyline. */
    POLYLINE,
    /** An area: a rectangle hit anywhere inside it (Phase 8c, a bar or a box plot's box). */
    AREA
  }
}
