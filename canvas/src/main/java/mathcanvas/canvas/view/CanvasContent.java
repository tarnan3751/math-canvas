package mathcanvas.canvas.view;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Summary;
import mathcanvas.canvas.model.Text;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.GridPainter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * Builds the view's content from the canvas's objects and their geometry (docs/reference/canvas.md,
 * <b>Graph kinds</b>), in drawing order: a curve's branches as one polyline, a region as a
 * translucent fill and its boundary, a point as a disc, a text at its anchor, a vector as an arrow
 * with its construction lines, a field as its marks and a contour plot as its levels with their
 * values, both in a ramp of the object's colour, a histogram's or a bar chart's bars filled at half
 * its colour's opacity, a box plot's box a fixed number of pixels thick with its whiskers and
 * outliers, the bars and the box hit anywhere inside them, a scatter plot's points as markers, a
 * residual plot's residuals as stems from the axis with a marker on each, and a label beside a
 * point whose label is shown. An object's colour is its series slot in the theme; its {@link Style}
 * becomes the {@link Stroke}. A hidden object is neither drawn nor hit; an undefined one and a
 * slider draw nothing. Pure computation over immutable values: built on the worker, rebuilt when
 * the theme changes.
 */
public final class CanvasContent {

  /** The dash and the gap of a dashed stroke, in multiples of its width. */
  static final double DASH = 3;

  /** The gap of a dashed stroke, in multiples of its width. */
  static final double GAP = 2;

  /** The opacity of a region's fill. */
  static final double FILL_OPACITY = 0.25;

  /** A point's radius beyond its stroke width, in pixels. */
  static final double POINT_RADIUS = 2;

  /** How far a label sits from its point, right and up, in pixels. */
  static final double LABEL_OFFSET = 6;

  /** An arrowhead's length beyond twice its stroke width, in pixels. */
  static final double HEAD_LENGTH = 8;

  /** The stroke width of a vector's construction lines, in pixels. */
  static final double GUIDE_WIDTH = 1;

  /** The radius of an angle's mark, in pixels. */
  static final double ANGLE_RADIUS = 24;

  /** How far beyond an angle's mark its measure is written, in pixels. */
  static final double ANGLE_LABEL_GAP = 8;

  /** The steps of a field's or a contour plot's colour ramp a field's marks are drawn in. */
  static final int RAMP_STEPS = 8;

  /**
   * How far the bottom of a ramp is faded toward the background: the least magnitude or the lowest
   * level is drawn this fraction of the way from its colour to the background, the top in its
   * colour.
   */
  static final double RAMP_FADE = 0.6;

  /** A field's marks' stroke width, a fraction of the object's width. */
  static final double FIELD_WIDTH = 0.75;

  /** A field arrow's head at the least magnitude, in pixels; twice it at the cap. */
  static final double FIELD_HEAD = 3;

  /** The most of a field arrow's shaft, on the screen, its head takes. */
  static final double FIELD_HEAD_SHAFT = 0.4;

  /** The size of a contour's label, in pixels. */
  static final double LEVEL_LABEL_SIZE = 11;

  /** The opacity of a bar's and a box plot's box's fill. */
  static final double BAR_OPACITY = 0.5;

  /** Half a box plot's box's thickness, in pixels; its whiskers' ends are half as long. */
  static final double BOX_HALF = 10;

  private CanvasContent() {}

  /**
   * The content and which object each of its hit items is.
   *
   * @param content the content
   * @param items the object of each polyline, point and hit area, by its item number in the hit
   *     index
   */
  public record Built(PlotContent content, Map<Integer, ObjectId> items) {

    /** Copies the map. */
    public Built {
      items = Map.copyOf(items);
    }
  }

  /**
   * Builds the content of a canvas.
   *
   * @param state the objects and their geometry
   * @param labels the label of each object that has one: a point's name, the value text a shaded
   *     area or a Riemann sum is written with at its centroid, an angle's measure, written beyond
   *     its mark, and a measurement's text, written beside its place
   * @param theme the colours
   * @return the content
   */
  public static Built build(CanvasState state, Map<ObjectId, String> labels, CanvasTheme theme) {
    List<PlotContent.Fill> fills = new ArrayList<>();
    List<PlotContent.Box> boxes = new ArrayList<>();
    List<PlotContent.Arc> arcs = new ArrayList<>();
    List<PlotContent.Curve> curves = new ArrayList<>();
    List<ObjectId> curveIds = new ArrayList<>();
    List<PlotContent.Head> heads = new ArrayList<>();
    List<PlotContent.Marker> points = new ArrayList<>();
    List<ObjectId> pointIds = new ArrayList<>();
    List<PlotContent.Label> texts = new ArrayList<>();
    List<PlotContent.Area> areas = new ArrayList<>();
    List<ObjectId> areaIds = new ArrayList<>();
    for (CanvasObject o : state.objects()) {
      Style style = o.style();
      if (!style.visible()) {
        continue;
      }
      Rgba colour = theme.series().get(style.colour());
      Stroke stroke = stroke(colour, style.width(), style.dashed());
      switch (state.geometryOf(o.id())) {
        case Geometry.Point p when o instanceof Text t ->
            texts.add(
                new PlotContent.Label(
                    t.text(),
                    p.x(),
                    p.y(),
                    0,
                    0,
                    new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BASELINE),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
        case Geometry.Point p -> {
          points.add(new PlotContent.Marker(p.x(), p.y(), POINT_RADIUS + style.width(), colour));
          pointIds.add(o.id());
          String label = labels.get(o.id());
          if (style.labelVisible() && label != null) {
            texts.add(
                new PlotContent.Label(
                    label,
                    p.x(),
                    p.y(),
                    LABEL_OFFSET,
                    -LABEL_OFFSET,
                    new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BOTTOM),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
          }
        }
        case Geometry.Curve c -> {
          if (!c.branches().isEmpty()) {
            double[][] joined = joined(c.branches());
            if (o.isPolygon()) {
              fills.add(
                  new PlotContent.Fill(joined[0], joined[1], colour.withOpacity(FILL_OPACITY)));
            }
            curves.add(new PlotContent.Curve(joined[0], joined[1], stroke));
            curveIds.add(o.id());
          }
        }
        case Geometry.Region r -> {
          if (!r.rings().isEmpty()) {
            double[][] joined = joined(r.rings());
            fills.add(new PlotContent.Fill(joined[0], joined[1], colour.withOpacity(FILL_OPACITY)));
            boolean strict =
                o instanceof InequalityRegion q
                    && switch (q.inequality().op()) {
                      case LT, GT -> true;
                      default -> false;
                    };
            curves.add(
                new PlotContent.Curve(
                    joined[0], joined[1], stroke(colour, style.width(), strict || style.dashed())));
            curveIds.add(o.id());
          }
        }
        case Geometry.ValuedRegion r -> {
          if (!r.rings().isEmpty()) {
            double[][] joined = joined(r.rings());
            fills.add(new PlotContent.Fill(joined[0], joined[1], colour.withOpacity(FILL_OPACITY)));
            curves.add(new PlotContent.Curve(joined[0], joined[1], stroke));
            curveIds.add(o.id());
            String value = labels.get(o.id());
            double[] at = centroid(r.rings());
            if (value != null && at != null) {
              texts.add(
                  new PlotContent.Label(
                      value,
                      at[0],
                      at[1],
                      0,
                      0,
                      new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE),
                      new TextStyle(
                          GridPainter.LABEL_SIZE,
                          theme.text(),
                          Optional.of(theme.background().withOpacity(1 - FILL_OPACITY)))));
            }
          }
        }
        case Geometry.Arrow a -> {
          for (Geometry.Guide g : a.guides()) {
            curves.add(
                new PlotContent.Curve(
                    new double[] {g.from().x(), g.to().x()},
                    new double[] {g.from().y(), g.to().y()},
                    stroke(colour, GUIDE_WIDTH, !g.arrow())));
            curveIds.add(o.id());
            if (g.arrow()) {
              heads.add(head(g.from(), g.to(), GUIDE_WIDTH, colour));
            }
          }
          curves.add(
              new PlotContent.Curve(
                  new double[] {a.tail().x(), a.head().x()},
                  new double[] {a.tail().y(), a.head().y()},
                  stroke));
          curveIds.add(o.id());
          heads.add(head(a.tail(), a.head(), style.width(), colour));
        }
        case Geometry.Angle a ->
            arcs.add(
                new PlotContent.Arc(
                    a.vertex().x(),
                    a.vertex().y(),
                    a.start(),
                    a.sweep(),
                    ANGLE_RADIUS,
                    stroke,
                    colour.withOpacity(FILL_OPACITY),
                    labels.getOrDefault(o.id(), ""),
                    ANGLE_LABEL_GAP,
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
        case Geometry.Measure m -> {
          String label = labels.get(o.id());
          if (label != null) {
            texts.add(
                new PlotContent.Label(
                    label,
                    m.at().x(),
                    m.at().y(),
                    LABEL_OFFSET,
                    -LABEL_OFFSET,
                    new Anchor(Anchor.Horizontal.START, Anchor.Vertical.BOTTOM),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
          }
        }
        case Geometry.Field f -> {
          // one polyline per step of the ramp, its marks joined by gaps
          List<List<Geometry.Mark>> steps = new ArrayList<>();
          for (int k = 0; k < RAMP_STEPS; k++) {
            steps.add(new ArrayList<>());
          }
          for (Geometry.Mark m : f.marks()) {
            steps.get((int) Math.round(m.weight() * (RAMP_STEPS - 1))).add(m);
          }
          for (int k = 0; k < RAMP_STEPS; k++) {
            List<Geometry.Mark> marks = steps.get(k);
            if (marks.isEmpty()) {
              continue;
            }
            Rgba shade = ramp(colour, theme.background(), k / (RAMP_STEPS - 1.0));
            double[] xs = new double[3 * marks.size()];
            double[] ys = new double[3 * marks.size()];
            for (int j = 0; j < marks.size(); j++) {
              Geometry.Mark m = marks.get(j);
              xs[3 * j] = m.from().x();
              ys[3 * j] = m.from().y();
              xs[3 * j + 1] = m.to().x();
              ys[3 * j + 1] = m.to().y();
              xs[3 * j + 2] = Double.NaN;
              ys[3 * j + 2] = Double.NaN;
              if (f.arrows()) {
                heads.add(
                    new PlotContent.Head(
                        m.to().x(),
                        m.to().y(),
                        m.to().x() - m.from().x(),
                        m.to().y() - m.from().y(),
                        FIELD_HEAD + FIELD_HEAD * m.weight(),
                        shade,
                        FIELD_HEAD_SHAFT));
              }
            }
            curves.add(
                new PlotContent.Curve(
                    xs, ys, stroke(shade, FIELD_WIDTH * style.width(), style.dashed())));
            curveIds.add(o.id());
          }
        }
        case Geometry.Contours c -> {
          int n = c.levels().size();
          for (int k = 0; k < n; k++) {
            Geometry.Level level = c.levels().get(k);
            Rgba shade = ramp(colour, theme.background(), n == 1 ? 1 : k / (n - 1.0));
            if (!level.lines().isEmpty()) {
              double[][] joined = joined(level.lines());
              curves.add(
                  new PlotContent.Curve(
                      joined[0], joined[1], stroke(shade, style.width(), style.dashed())));
              curveIds.add(o.id());
            }
            level
                .label()
                .ifPresent(
                    at ->
                        texts.add(
                            new PlotContent.Label(
                                levelText(level.value()),
                                at.x(),
                                at.y(),
                                0,
                                0,
                                new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE),
                                new TextStyle(
                                    LEVEL_LABEL_SIZE,
                                    shade,
                                    Optional.of(
                                        theme.background().withOpacity(1 - FILL_OPACITY))))));
          }
        }
        // Phase 8c: the statistical plots
        case Geometry.Bins b ->
            bars(b.bars(), o.id(), colour, stroke, fills, curves, curveIds, areas, areaIds);
        case Geometry.Bars b -> {
          bars(b.bars(), o.id(), colour, stroke, fills, curves, curveIds, areas, areaIds);
          for (int k = 0; k < b.labels().size(); k++) {
            Geometry.Bar bar = b.bars().get(k);
            texts.add(
                new PlotContent.Label(
                    b.labels().get(k),
                    (bar.x0() + bar.x1()) / 2,
                    0,
                    0,
                    LABEL_OFFSET,
                    new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.TOP),
                    new TextStyle(GridPainter.LABEL_SIZE, colour)));
          }
        }
        case Geometry.Box b -> {
          Summary s = b.summary();
          boxes.add(
              new PlotContent.Box(
                  s.q1(),
                  s.q3(),
                  b.y(),
                  BOX_HALF,
                  stroke,
                  Optional.of(colour.withOpacity(BAR_OPACITY))));
          boxes.add(
              new PlotContent.Box(
                  s.median(), s.median(), b.y(), BOX_HALF, stroke, Optional.empty()));
          boxes.add(
              new PlotContent.Box(b.low(), b.low(), b.y(), BOX_HALF / 2, stroke, Optional.empty()));
          boxes.add(
              new PlotContent.Box(
                  b.high(), b.high(), b.y(), BOX_HALF / 2, stroke, Optional.empty()));
          // the whiskers; the box is hit anywhere inside it
          curves.add(
              new PlotContent.Curve(
                  new double[] {b.low(), s.q1(), Double.NaN, s.q3(), b.high()},
                  new double[] {b.y(), b.y(), Double.NaN, b.y(), b.y()},
                  stroke));
          curveIds.add(o.id());
          areas.add(new PlotContent.Area(s.q1(), s.q3(), b.y(), b.y(), BOX_HALF));
          areaIds.add(o.id());
          for (double x : b.outliers()) {
            points.add(new PlotContent.Marker(x, b.y(), POINT_RADIUS + style.width(), colour));
            pointIds.add(o.id());
          }
        }
        case Geometry.Scatter sc -> {
          for (Geometry.Point p : sc.points()) {
            points.add(new PlotContent.Marker(p.x(), p.y(), POINT_RADIUS + style.width(), colour));
            pointIds.add(o.id());
          }
        }
        case Geometry.Residuals r -> {
          // a stem from the axis to each residual, the stems one polyline, and a marker on each
          int n = r.points().size();
          if (n > 0) {
            double[] xs = new double[3 * n];
            double[] ys = new double[3 * n];
            for (int k = 0; k < n; k++) {
              Geometry.Point p = r.points().get(k);
              xs[3 * k] = p.x();
              ys[3 * k] = 0;
              xs[3 * k + 1] = p.x();
              ys[3 * k + 1] = p.y();
              xs[3 * k + 2] = Double.NaN;
              ys[3 * k + 2] = Double.NaN;
            }
            curves.add(new PlotContent.Curve(xs, ys, stroke));
            curveIds.add(o.id());
          }
          for (Geometry.Point p : r.points()) {
            points.add(new PlotContent.Marker(p.x(), p.y(), POINT_RADIUS + style.width(), colour));
            pointIds.add(o.id());
          }
        }
        case Geometry.Value v -> {}
        case Geometry.Spatial s -> {}
        case Geometry.Undefined u -> {}
      }
    }
    Map<Integer, ObjectId> items = new HashMap<>();
    for (int k = 0; k < curveIds.size(); k++) {
      items.put(k, curveIds.get(k));
    }
    for (int k = 0; k < pointIds.size(); k++) {
      items.put(curveIds.size() + k, pointIds.get(k));
    }
    for (int k = 0; k < areaIds.size(); k++) {
      items.put(curveIds.size() + pointIds.size() + k, areaIds.get(k));
    }
    return new Built(
        PlotContent.of(fills, boxes, arcs, curves, heads, points, texts, areas), items);
  }

  /**
   * A histogram's or a bar chart's bars: filled at {@link #BAR_OPACITY} of the object's colour and
   * outlined in its stroke, the outlines one hit item and each bar a hit area.
   */
  private static void bars(
      List<Geometry.Bar> bars,
      ObjectId id,
      Rgba colour,
      Stroke stroke,
      List<PlotContent.Fill> fills,
      List<PlotContent.Curve> curves,
      List<ObjectId> curveIds,
      List<PlotContent.Area> areas,
      List<ObjectId> areaIds) {
    if (bars.isEmpty()) {
      return;
    }
    double[] xs = new double[6 * bars.size()];
    double[] ys = new double[6 * bars.size()];
    for (int k = 0; k < bars.size(); k++) {
      Geometry.Bar b = bars.get(k);
      System.arraycopy(
          new double[] {b.x0(), b.x1(), b.x1(), b.x0(), b.x0(), Double.NaN}, 0, xs, 6 * k, 6);
      System.arraycopy(new double[] {0, 0, b.height(), b.height(), 0, Double.NaN}, 0, ys, 6 * k, 6);
      areas.add(new PlotContent.Area(b.x0(), b.x1(), 0, b.height(), 0));
      areaIds.add(id);
    }
    fills.add(new PlotContent.Fill(xs, ys, colour.withOpacity(BAR_OPACITY)));
    curves.add(new PlotContent.Curve(xs, ys, stroke));
    curveIds.add(id);
  }

  /** The arrowhead at the head of an arrow, sized by its stroke width. */
  private static PlotContent.Head head(
      Geometry.Point tail, Geometry.Point head, double width, Rgba colour) {
    return new PlotContent.Head(
        head.x(),
        head.y(),
        head.x() - tail.x(),
        head.y() - tail.y(),
        HEAD_LENGTH + 2 * width,
        colour);
  }

  /**
   * Where a valued region's value is written: the centroid of its rings by unsigned area (the
   * shoelace formula per ring, each weighted by its unsigned area, so a lobe below the axis counts
   * as much as one above; an area's rings are its lobes between crossings, none crossing itself),
   * or the middle of their bounds when they enclose less than a millionth of the bounds' area; null
   * when no ring has a finite vertex.
   */
  static double[] centroid(List<Polyline> rings) {
    double area = 0;
    double cx = 0;
    double cy = 0;
    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (Polyline ring : rings) {
      double a = 0;
      double rx = 0;
      double ry = 0;
      for (int k = 0; k + 1 < ring.size(); k++) {
        double x0 = ring.x(k);
        double y0 = ring.y(k);
        double x1 = ring.x(k + 1);
        double y1 = ring.y(k + 1);
        double cross = x0 * y1 - x1 * y0;
        a += cross;
        rx += (x0 + x1) * cross;
        ry += (y0 + y1) * cross;
        minX = Math.min(minX, x0);
        maxX = Math.max(maxX, x0);
        minY = Math.min(minY, y0);
        maxY = Math.max(maxY, y0);
      }
      // each ring weighted by its unsigned area, its centroid the same whatever its turn
      double weight = Math.abs(a);
      if (a != 0) {
        area += weight;
        cx += rx / (3 * a) * weight;
        cy += ry / (3 * a) * weight;
      }
    }
    // below a millionth of the bounds' area (a region thinner than a pixel at any useful zoom),
    // the middle of the bounds
    double box = (maxX - minX) * (maxY - minY);
    if (area > 1e-6 * box && Double.isFinite(cx / area) && Double.isFinite(cy / area)) {
      return new double[] {cx / area, cy / area};
    }
    return minX <= maxX && minY <= maxY
        ? new double[] {(minX + maxX) / 2, (minY + maxY) / 2}
        : null;
  }

  /**
   * A place on an object's colour ramp: its colour faded toward the background by {@link
   * #RAMP_FADE} of the way at 0, its colour at 1.
   */
  static Rgba ramp(Rgba colour, Rgba background, double weight) {
    double fade = RAMP_FADE * (1 - weight);
    return new Rgba(
        colour.red() + (background.red() - colour.red()) * fade,
        colour.green() + (background.green() - colour.green()) * fade,
        colour.blue() + (background.blue() - colour.blue()) * fade,
        colour.opacity());
  }

  /**
   * A contour's value as its label writes it: four significant digits at most, trailing zeros
   * dropped, a true minus sign, and scientific form at 10⁶ and above or below 10⁻⁴.
   */
  static String levelText(double value) {
    if (value == 0) {
      return "0";
    }
    BigDecimal d = new BigDecimal(value).round(new MathContext(4)).stripTrailingZeros();
    double magnitude = Math.abs(value);
    String text = magnitude >= 1e6 || magnitude < 1e-4 ? d.toString() : d.toPlainString();
    return text.replace('-', '−');
  }

  /** A stroke of an object's colour and width, dashed in proportion to the width. */
  static Stroke stroke(Rgba colour, double width, boolean dashed) {
    return dashed
        ? Stroke.dashed(colour, width, DASH * width, GAP * width)
        : Stroke.solid(colour, width);
  }

  /** Polylines end to end, each followed by a NaN that breaks the path there. */
  private static double[][] joined(List<Polyline> lines) {
    int n = 0;
    for (Polyline l : lines) {
      n += l.size() + 1;
    }
    double[] xs = new double[n];
    double[] ys = new double[n];
    int at = 0;
    for (Polyline l : lines) {
      System.arraycopy(l.xs(), 0, xs, at, l.size());
      System.arraycopy(l.ys(), 0, ys, at, l.size());
      at += l.size();
      xs[at] = Double.NaN;
      ys[at] = Double.NaN;
      at++;
    }
    return new double[][] {xs, ys};
  }
}
