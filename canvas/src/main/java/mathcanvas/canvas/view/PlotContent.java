package mathcanvas.canvas.view;

import java.util.List;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.AngleArc;
import mathcanvas.canvas.paint.Arrowhead;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * What the view's objects layer draws: filled regions, angle marks, finished polylines, arrowheads,
 * points and labels, with the hit index over the polylines and the points. Built off the FX thread
 * (sampling and indexing are the expensive part) and handed to {@link CanvasView#setContent}. The
 * polylines are items 0 to {@code curves().size() - 1} of the index and the points the items after
 * them; fills, angle marks, arrowheads and labels are not hit. Immutable by convention: the arrays
 * are not copied, so the builder of a content leaves them alone once it is built.
 *
 * @param fills the filled regions, drawn first
 * @param arcs the angle marks, drawn after the fills
 * @param curves the polylines, drawn in order
 * @param heads the arrowheads, drawn after the polylines
 * @param points the points, drawn after the arrowheads
 * @param labels the labels, drawn last
 * @param index the hit index over the polylines and the points
 */
public record PlotContent(
    List<Fill> fills,
    List<Arc> arcs,
    List<Curve> curves,
    List<Head> heads,
    List<Marker> points,
    List<Label> labels,
    HitIndex index) {

  /** Nothing to draw. */
  public static final PlotContent EMPTY =
      new PlotContent(
          List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), HitIndex.EMPTY);

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
   * An arrowhead ({@link Arrowhead}): a filled triangle of a fixed size in pixels, whatever the
   * zoom (as a point's radius is), its tip at a world point and pointing along a world direction. A
   * direction of length zero draws nothing.
   *
   * @param x the tip's world x
   * @param y the tip's world y
   * @param dx the direction's world x
   * @param dy the direction's world y
   * @param length the length from the base to the tip in pixels
   * @param colour the colour
   * @param shaft when positive, the direction is the whole shaft and the head is at most this
   *     fraction of the shaft's length on the screen (a field's arrows); 0 for no such cap
   */
  public record Head(
      double x, double y, double dx, double dy, double length, Rgba colour, double shaft) {

    /**
     * A head of its length whatever its shaft.
     *
     * @param x the tip's world x
     * @param y the tip's world y
     * @param dx the direction's world x
     * @param dy the direction's world y
     * @param length the length from the base to the tip in pixels
     * @param colour the colour
     */
    public Head(double x, double y, double dx, double dy, double length, Rgba colour) {
      this(x, y, dx, dy, length, colour, 0);
    }
  }

  /**
   * An angle's mark, drawn by {@link AngleArc}: a wedge of a radius in pixels at the vertex, with
   * its label.
   *
   * @param x the vertex's world x
   * @param y the vertex's world y
   * @param start the first arm's world direction, in radians
   * @param sweep the angle counterclockwise to the second arm, 0 to π
   * @param radius the radius in pixels
   * @param stroke the arc's stroke
   * @param fill the wedge's fill
   * @param label the measure written beyond the arc on its bisector, empty for none
   * @param gap the label's distance beyond the arc, in pixels
   * @param style the label's style
   */
  public record Arc(
      double x,
      double y,
      double start,
      double sweep,
      double radius,
      Stroke stroke,
      Rgba fill,
      String label,
      double gap,
      TextStyle style) {}

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
    arcs = List.copyOf(arcs);
    curves = List.copyOf(curves);
    heads = List.copyOf(heads);
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
   * Content with no arrowheads, its hit index built over the polylines and the points.
   *
   * @param fills the filled regions
   * @param curves the polylines
   * @param points the points
   * @param labels the labels
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills, List<Curve> curves, List<Marker> points, List<Label> labels) {
    return of(fills, curves, List.of(), points, labels);
  }

  /**
   * Content with its hit index built over the polylines and the points.
   *
   * @param fills the filled regions
   * @param curves the polylines
   * @param heads the arrowheads
   * @param points the points
   * @param labels the labels
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills,
      List<Curve> curves,
      List<Head> heads,
      List<Marker> points,
      List<Label> labels) {
    return of(fills, List.of(), curves, heads, points, labels);
  }

  /**
   * Content with angle marks, its hit index built over the polylines and the points.
   *
   * @param fills the filled regions
   * @param arcs the angle marks
   * @param curves the polylines
   * @param heads the arrowheads
   * @param points the points
   * @param labels the labels
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills,
      List<Arc> arcs,
      List<Curve> curves,
      List<Head> heads,
      List<Marker> points,
      List<Label> labels) {
    HitIndex.Builder b = HitIndex.builder();
    for (int i = 0; i < curves.size(); i++) {
      Curve c = curves.get(i);
      b.polyline(i, c.xs(), c.ys(), c.xs().length);
    }
    for (int i = 0; i < points.size(); i++) {
      b.point(curves.size() + i, points.get(i).x(), points.get(i).y());
    }
    return new PlotContent(fills, arcs, curves, heads, points, labels, b.build());
  }

  /**
   * Draws the fills, the angle marks, the polylines, the arrowheads, the points, then the labels.
   *
   * @param p the painter
   */
  public void paint(Painter p) {
    for (Fill f : fills) {
      p.beginPath();
      p.polyline(f.xs(), f.ys(), f.xs().length);
      p.fill(f.colour());
    }
    for (Arc a : arcs) {
      AngleArc.paint(
          p,
          a.x(),
          a.y(),
          a.start(),
          a.sweep(),
          a.radius(),
          a.stroke(),
          a.fill(),
          a.label(),
          a.gap(),
          a.style());
    }
    for (Curve c : curves) {
      p.beginPath();
      p.polyline(c.xs(), c.ys(), c.xs().length);
      p.stroke(c.stroke());
    }
    for (Head h : heads) {
      double length = h.length();
      if (h.shaft() > 0) {
        double sx = h.dx() * p.viewport().pixelsPerUnitX();
        double sy = h.dy() * p.viewport().pixelsPerUnitY();
        length = Math.min(length, h.shaft() * Math.hypot(sx, sy));
      }
      Arrowhead.paint(p, h.x(), h.y(), h.dx(), h.dy(), length, h.colour());
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
