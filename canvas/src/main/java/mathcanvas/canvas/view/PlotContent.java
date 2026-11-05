package mathcanvas.canvas.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mathcanvas.canvas.hit.HitIndex;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.AngleArc;
import mathcanvas.canvas.paint.Arrowhead;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * What the view's objects layer draws: filled regions, boxes a fixed number of pixels thick, angle
 * marks, finished polylines, arrowheads, points and labels, with the hit index over the polylines,
 * the points and the hit areas. Built off the FX thread (sampling and indexing are the expensive
 * part) and handed to {@link CanvasView#setContent}. The polylines are items 0 to {@code
 * curves().size() - 1} of the index, the points the items after them and the hit areas the items
 * after those; fills, boxes, angle marks, arrowheads and labels are not hit, and the hit areas are
 * not drawn. Immutable by convention: the arrays are not copied, so the builder of a content leaves
 * them alone once it is built.
 *
 * @param fills the filled regions, drawn first
 * @param boxes the boxes, drawn after the fills
 * @param arcs the angle marks, drawn after the boxes
 * @param curves the polylines, drawn in order
 * @param heads the arrowheads, drawn after the polylines
 * @param points the points, drawn after the arrowheads
 * @param labels the labels, drawn last
 * @param areas the hit areas, not drawn: a statistical plot's bars and boxes
 * @param index the hit index over the polylines, the points and the hit areas
 */
public record PlotContent(
    List<Fill> fills,
    List<Box> boxes,
    List<Arc> arcs,
    List<Curve> curves,
    List<Head> heads,
    List<Marker> points,
    List<Label> labels,
    List<Area> areas,
    HitIndex index) {

  /** Nothing to draw. */
  public static final PlotContent EMPTY =
      new PlotContent(
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          HitIndex.EMPTY);

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
   * A rectangle hit anywhere inside it, reaching a number of pixels above and below its world
   * extent: a histogram's or a bar chart's bar, at no reach, and a box plot's box, its world height
   * none and its reach the box's half thickness.
   *
   * @param x0 the left side's world x
   * @param x1 the right side's world x
   * @param y0 the bottom's world y
   * @param y1 the top's world y
   * @param half how many pixels it reaches below its bottom and above its top
   */
  public record Area(double x0, double x1, double y0, double y1, double half) {}

  /**
   * A rectangle from one abscissa to another, a fixed number of pixels above and below a height
   * whatever the zoom (as a point's radius is): a box plot's box, and its median and whiskers' ends
   * at no width.
   *
   * @param x0 the left side's world x
   * @param x1 the right side's world x
   * @param y the middle's world y
   * @param half the half height in pixels
   * @param stroke how its sides are drawn
   * @param fill its fill, or empty for none
   */
  public record Box(
      double x0, double x1, double y, double half, Stroke stroke, Optional<Rgba> fill) {

    /**
     * Checks the fill.
     *
     * @throws NullPointerException if the stroke or the fill is null
     */
    public Box {
      Objects.requireNonNull(stroke, "stroke");
      Objects.requireNonNull(fill, "fill");
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
    boxes = List.copyOf(boxes);
    arcs = List.copyOf(arcs);
    curves = List.copyOf(curves);
    heads = List.copyOf(heads);
    points = List.copyOf(points);
    labels = List.copyOf(labels);
    Objects.requireNonNull(index, "index");
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
    return of(fills, List.of(), arcs, curves, heads, points, labels);
  }

  /**
   * Content with boxes and angle marks, its hit index built over the polylines and the points.
   *
   * @param fills the filled regions
   * @param boxes the boxes
   * @param arcs the angle marks
   * @param curves the polylines
   * @param heads the arrowheads
   * @param points the points
   * @param labels the labels
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills,
      List<Box> boxes,
      List<Arc> arcs,
      List<Curve> curves,
      List<Head> heads,
      List<Marker> points,
      List<Label> labels) {
    return of(fills, boxes, arcs, curves, heads, points, labels, List.of());
  }

  /**
   * Content with hit areas, its hit index built over the polylines, the points and the areas.
   *
   * @param fills the filled regions
   * @param boxes the boxes
   * @param arcs the angle marks
   * @param curves the polylines
   * @param heads the arrowheads
   * @param points the points
   * @param labels the labels
   * @param areas the hit areas
   * @return the content
   */
  public static PlotContent of(
      List<Fill> fills,
      List<Box> boxes,
      List<Arc> arcs,
      List<Curve> curves,
      List<Head> heads,
      List<Marker> points,
      List<Label> labels,
      List<Area> areas) {
    HitIndex.Builder b = HitIndex.builder();
    for (int i = 0; i < curves.size(); i++) {
      Curve c = curves.get(i);
      b.polyline(i, c.xs(), c.ys(), c.xs().length);
    }
    for (int i = 0; i < points.size(); i++) {
      b.point(curves.size() + i, points.get(i).x(), points.get(i).y());
    }
    for (int i = 0; i < areas.size(); i++) {
      Area a = areas.get(i);
      b.area(curves.size() + points.size() + i, a.x0(), a.y0(), a.x1(), a.y1(), a.half());
    }
    return new PlotContent(fills, boxes, arcs, curves, heads, points, labels, areas, b.build());
  }

  /**
   * Draws the fills, the boxes, the angle marks, the polylines, the arrowheads, the points, then
   * the labels.
   *
   * @param p the painter
   */
  public void paint(Painter p) {
    for (Fill f : fills) {
      p.beginPath();
      p.polyline(f.xs(), f.ys(), f.xs().length);
      p.fill(f.colour());
    }
    for (Box box : boxes) {
      double dy = box.half() / p.viewport().pixelsPerUnitY();
      double[] xs = {box.x0(), box.x1(), box.x1(), box.x0(), box.x0()};
      double[] ys = {box.y() - dy, box.y() - dy, box.y() + dy, box.y() + dy, box.y() - dy};
      if (box.fill().isPresent()) {
        p.beginPath();
        p.polyline(xs, ys, xs.length);
        p.fill(box.fill().get());
      }
      p.beginPath();
      p.polyline(xs, ys, xs.length);
      p.stroke(box.stroke());
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
   * @param item the item's number in the index, a polyline's or a point's (a hit area is not drawn)
   * @return the colour
   */
  public Rgba colour(int item) {
    return item < curves.size()
        ? curves.get(item).stroke().colour()
        : points.get(item - curves.size()).colour();
  }
}
