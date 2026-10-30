package mathcanvas.canvas.view;

import java.util.List;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;

/**
 * What the view's objects layer draws: finished polylines and points, with the hit index over them.
 * Built off the FX thread (sampling and indexing are the expensive part) and handed to {@link
 * CanvasView#setContent}. The polylines are items 0 to {@code curves().size() - 1} of the index and
 * the points the items after them. Immutable by convention: the arrays are not copied, so the
 * builder of a content leaves them alone once it is built.
 *
 * @param curves the polylines, drawn in order
 * @param points the points, drawn after the polylines
 * @param index the hit index over both
 */
public record PlotContent(List<Curve> curves, List<Marker> points, HitIndex index) {

  /** Nothing to draw. */
  public static final PlotContent EMPTY = new PlotContent(List.of(), List.of(), HitIndex.EMPTY);

  /**
   * A sampled polyline; a non-finite value marks a gap.
   *
   * @param xs the world xs
   * @param ys the world ys
   * @param stroke how it is drawn
   */
  public record Curve(double[] xs, double[] ys, Stroke stroke) {
    /**
     * Checks the arrays.
     *
     * @throws IllegalArgumentException if their lengths differ
     */
    public Curve {
      if (xs.length != ys.length) {
        throw new IllegalArgumentException("xs and ys differ: " + xs.length + ", " + ys.length);
      }
    }
  }

  /**
   * A point.
   *
   * @param x the world x
   * @param y the world y
   * @param radius the radius in pixels
   * @param colour the colour
   */
  public record Marker(double x, double y, double radius, Rgba colour) {}

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if an argument is null
   */
  public PlotContent {
    curves = List.copyOf(curves);
    points = List.copyOf(points);
    java.util.Objects.requireNonNull(index, "index");
  }

  /**
   * Content with its hit index built over the polylines and the points.
   *
   * @param curves the polylines
   * @param points the points
   * @return the content
   */
  public static PlotContent of(List<Curve> curves, List<Marker> points) {
    HitIndex.Builder b = HitIndex.builder();
    for (int i = 0; i < curves.size(); i++) {
      Curve c = curves.get(i);
      b.polyline(i, c.xs(), c.ys(), c.xs().length);
    }
    for (int i = 0; i < points.size(); i++) {
      b.point(curves.size() + i, points.get(i).x(), points.get(i).y());
    }
    return new PlotContent(curves, points, b.build());
  }

  /**
   * Draws the polylines, then the points.
   *
   * @param p the painter
   */
  public void paint(Painter p) {
    for (Curve c : curves) {
      p.beginPath();
      p.polyline(c.xs(), c.ys(), c.xs().length);
      p.stroke(c.stroke());
    }
    for (Marker m : points) {
      p.point(m.x(), m.y(), m.radius(), m.colour());
    }
  }

  /**
   * The colour an item is drawn in: a polyline's stroke or a point's fill.
   *
   * @param item the item's number in the index
   * @return the colour
   */
  public Rgba colour(int item) {
    return item < curves.size()
        ? curves.get(item).stroke().colour()
        : points.get(item - curves.size()).colour();
  }
}
