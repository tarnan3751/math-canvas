package mathcanvas.canvas.view;

import java.util.List;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * What the view's objects layer draws: filled regions, finished polylines, points and labels, with
 * the hit index over the polylines and the points. Built off the FX thread (sampling and indexing
 * are the expensive part) and handed to {@link CanvasView#setContent}. The polylines are items 0 to
 * {@code curves().size() - 1} of the index and the points the items after them; fills and labels
 * are not hit. Immutable by convention: the arrays are not copied, so the builder of a content
 * leaves them alone once it is built.
 *
 * @param fills the filled regions, drawn first
 * @param curves the polylines, drawn in order
 * @param points the points, drawn after the polylines
 * @param labels the labels, drawn last
 * @param index the hit index over the polylines and the points
 */
public record PlotContent(
    List<Fill> fills, List<Curve> curves, List<Marker> points, List<Label> labels, HitIndex index) {

  /** Nothing to draw. */
  public static final PlotContent EMPTY =
      new PlotContent(List.of(), List.of(), List.of(), List.of(), HitIndex.EMPTY);

  /**
   * A filled region: closed rings, each ended by a non-finite value, filled together by the
   * painter's non-zero winding rule. A region's rings are the inside of an even-odd region, and the
   * sampler turns outer rings counterclockwise and holes clockwise, so the two rules agree on them.
   *
   * @param xs the world xs
   * @param ys the world ys
   * @param colour the colour, usually translucent
   */
  public record Fill(double[] xs, double[] ys, Rgba colour) {
    /**
     * Checks the arrays.
     *
     * @throws IllegalArgumentException if their lengths differ
     */
    public Fill {
      if (xs.length != ys.length) {
        throw new IllegalArgumentException("xs and ys differ: " + xs.length + ", " + ys.length);
      }
    }
  }

  /**
   * A line of text beside a world point.
   *
   * @param text the text
   * @param x the world x
   * @param y the world y
   * @param dx the offset in pixels to the right
   * @param dy the offset in pixels down
   * @param anchor which point of the text sits at the offset point
   * @param style the size and colour
   */
  public record Label(
      String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {}

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
    fills = List.copyOf(fills);
    curves = List.copyOf(curves);
    points = List.copyOf(points);
    labels = List.copyOf(labels);
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
    return of(List.of(), curves, points, List.of());
  }

  /**
   * Content with its hit index built over the polylines and the points.
   *
   * @param fills the filled regions
   * @param curves the polylines
   * @param points the points
   * @param labels the labels
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills, List<Curve> curves, List<Marker> points, List<Label> labels) {
    HitIndex.Builder b = HitIndex.builder();
    for (int i = 0; i < curves.size(); i++) {
      Curve c = curves.get(i);
      b.polyline(i, c.xs(), c.ys(), c.xs().length);
    }
    for (int i = 0; i < points.size(); i++) {
      b.point(curves.size() + i, points.get(i).x(), points.get(i).y());
    }
    return new PlotContent(fills, curves, points, labels, b.build());
  }

  /**
   * Draws the fills, the polylines, the points, then the labels.
   *
   * @param p the painter
   */
  public void paint(Painter p) {
    for (Fill f : fills) {
      p.beginPath();
      p.polyline(f.xs(), f.ys(), f.xs().length);
      p.fill(f.colour());
    }
    for (Curve c : curves) {
      p.beginPath();
      p.polyline(c.xs(), c.ys(), c.xs().length);
      p.stroke(c.stroke());
    }
    for (Marker m : points) {
      p.point(m.x(), m.y(), m.radius(), m.colour());
    }
    for (Label l : labels) {
      p.text(l.text(), l.x(), l.y(), l.dx(), l.dy(), l.anchor(), l.style());
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
